/*
 * WeightSmart ESP8266 Firmware
 * 
 * Features:
 * - HX711 Weight Sensor
 * - LDR Tamper Detection (Pin D8)
 * - OLED Display (I2C)
 * - WiFi AP + Station Mode
 * - REST API for Android App
 * - OTA Firmware Updates
 * 
 * Author: WeightSmart Team
 * Version: 1.0.0
 */

#include <ESP8266WiFi.h>
#include <ESP8266WebServer.h>
#include <ESP8266HTTPUpdateServer.h>
#include <ArduinoJson.h>
#include <Wire.h>
#include <Adafruit_GFX.h>
#include <Adafruit_SSD1306.h>
#include <HX711.h>
#include <EEPROM.h>
#include <WebSocketsServer.h>

// ==================== PIN DEFINITIONS ====================
#define HX711_DOUT_PIN  D5    // HX711 Data pin
#define HX711_SCK_PIN   D6    // HX711 Clock pin
#define LDR_PIN         A0    // LDR sensor pin (Digital)
#define LED_PIN         D4    // Built-in LED

// ==================== OLED CONFIGURATION ====================
#define SCREEN_WIDTH    128
#define SCREEN_HEIGHT   64
#define OLED_RESET      -1
#define OLED_ADDRESS    0x3C  // I2C address (0x3C or 0x3D)

// ==================== WIFI CONFIGURATION ====================
// Station Mode (connect to existing WiFi)
const char* STA_SSID = "levi";
const char* STA_PASSWORD = "74499495";

// Access Point Mode (ESP8266 creates its own WiFi)
const char* AP_SSID = "WeightSmart_ESP8266";
const char* AP_PASSWORD = "weight123";

// ==================== SETTINGS ====================
#define EEPROM_SIZE           512
#define CALIBRATION_ADDR      0
#define LIGHT_THRESHOLD_ADDR  10
#define WIFI_CONFIG_ADDR      20

// ==================== GLOBAL OBJECTS ====================
Adafruit_SSD1306 display(SCREEN_WIDTH, SCREEN_HEIGHT, &Wire, OLED_RESET);
HX711 scale;
ESP8266WebServer server(80);
ESP8266HTTPUpdateServer httpUpdater;
WebSocketsServer webSocket(81);

// ==================== GLOBAL VARIABLES ====================
// Weight variables
float currentWeight = 0.0;
float calibrationFactor = 420.0;
long zeroOffset = 0;
bool weightStable = false;
float lastWeight = 0.0;
int stableCount = 0;

// Light sensor variables
int lightLevel = 0;
int lightThreshold = 500;  // Tamper detection threshold
bool isTampered = false;
int ambientLight = 0;

// Device info
String deviceId = "";
String firmwareVersion = "1.0.0";
unsigned long startTime = 0;
bool wifiConnected = false;
String currentIP = "";
int wifiRSSI = 0;

// Timing
unsigned long lastSensorRead = 0;
unsigned long lastDisplayUpdate = 0;
unsigned long lastWebSocketBroadcast = 0;
const int SENSOR_READ_INTERVAL = 100;    // 100ms
const int DISPLAY_UPDATE_INTERVAL = 250; // 250ms
const int WEBSOCKET_BROADCAST_INTERVAL = 500; // 500ms

// ==================== FUNCTION DECLARATIONS ====================
void setupWiFi();
void setupOLED();
void setupHX711();
void setupWebServer();
void setupWebSocket();
void readSensors();
void updateDisplay();
void broadcastSensorData();
void handleRoot();
void handleSensorData();
void handleWeightData();
void handleLightData();
void handleTare();
void handleCalibrate();
void handleCalibrationFactor();
void handleDeviceStatus();
void handleFirmwareInfo();
void handleLightThreshold();
void handleRestart();
void handlePing();
void handleWifiConfig();
void handleNotFound();
void webSocketEvent(uint8_t num, WStype_t type, uint8_t * payload, size_t length);
void saveCalibration();
void loadCalibration();
String getSensorDataJSON();

// ==================== SETUP ====================
void setup() {
  Serial.begin(115200);
  Serial.println("\n\n=== WeightSmart ESP8266 Starting ===");
  
  // Initialize pins
  pinMode(LDR_PIN, INPUT);
  pinMode(LED_PIN, OUTPUT);
  digitalWrite(LED_PIN, HIGH); // LED off (active low)
  
  // Initialize EEPROM
  EEPROM.begin(EEPROM_SIZE);
  loadCalibration();
  
  // Generate unique device ID
  deviceId = String(ESP.getChipId(), HEX);
  deviceId.toUpperCase();
  
  // Initialize components
  setupOLED();
  setupHX711();
  setupWiFi();
  setupWebServer();
  setupWebSocket();
  
  // Calibrate ambient light
  delay(100);
  ambientLight = analogRead(A0); // Use analog pin for ambient reading if available
  
  startTime = millis();
  Serial.println("=== Setup Complete ===\n");
}

// ==================== MAIN LOOP ====================
void loop() {
  // Handle web requests
  server.handleClient();
  webSocket.loop();
  
  unsigned long currentTime = millis();
  
  // Read sensors
  if (currentTime - lastSensorRead >= SENSOR_READ_INTERVAL) {
    readSensors();
    lastSensorRead = currentTime;
  }
  
  // Update display
  if (currentTime - lastDisplayUpdate >= DISPLAY_UPDATE_INTERVAL) {
    updateDisplay();
    lastDisplayUpdate = currentTime;
  }
  
  // Broadcast to WebSocket clients
  if (currentTime - lastWebSocketBroadcast >= WEBSOCKET_BROADCAST_INTERVAL) {
    broadcastSensorData();
    lastWebSocketBroadcast = currentTime;
  }
  
  // Blink LED if tampered
  if (isTampered) {
    digitalWrite(LED_PIN, (millis() / 200) % 2);
  } else {
    digitalWrite(LED_PIN, HIGH); // LED off
  }
  
  yield(); // Allow WiFi stack to run
}

// ==================== WIFI SETUP ====================
void setupWiFi() {
  display.clearDisplay();
  display.setTextSize(1);
  display.setTextColor(SSD1306_WHITE);
  display.setCursor(0, 0);
  display.println("Setting up WiFi...");
  display.display();
  
  // Start Access Point
  Serial.println("Starting Access Point...");
  WiFi.softAP(AP_SSID, AP_PASSWORD);
  IPAddress apIP = WiFi.softAPIP();
  Serial.print("AP IP: ");
  Serial.println(apIP);
  
  display.println();
  display.println("AP Mode Active:");
  display.print("SSID: ");
  display.println(AP_SSID);
  display.print("IP: ");
  display.println(apIP);
  display.display();
  delay(2000);
  
  // Try to connect to existing WiFi
  Serial.print("Connecting to ");
  Serial.println(STA_SSID);
  
  WiFi.mode(WIFI_AP_STA);
  WiFi.begin(STA_SSID, STA_PASSWORD);
  
  display.clearDisplay();
  display.setCursor(0, 0);
  display.println("Connecting to WiFi:");
  display.println(STA_SSID);
  display.display();
  
  int attempts = 0;
  while (WiFi.status() != WL_CONNECTED && attempts < 30) {
    delay(500);
    Serial.print(".");
    display.print(".");
    display.display();
    attempts++;
  }
  
  display.clearDisplay();
  display.setCursor(0, 0);
  
  if (WiFi.status() == WL_CONNECTED) {
    wifiConnected = true;
    currentIP = WiFi.localIP().toString();
    wifiRSSI = WiFi.RSSI();
    
    Serial.println();
    Serial.print("Connected! IP: ");
    Serial.println(currentIP);
    
    display.println("WiFi Connected!");
    display.println();
    display.print("SSID: ");
    display.println(STA_SSID);
    display.println();
    display.setTextSize(2);
    display.print("IP:");
    display.println(currentIP);
    display.setTextSize(1);
  } else {
    Serial.println("\nFailed to connect to WiFi");
    Serial.println("Using AP mode only");
    
    currentIP = WiFi.softAPIP().toString();
    
    display.println("AP Mode Only");
    display.println();
    display.print("SSID: ");
    display.println(AP_SSID);
    display.print("Pass: ");
    display.println(AP_PASSWORD);
    display.println();
    display.setTextSize(2);
    display.print("IP:");
    display.println(currentIP);
    display.setTextSize(1);
  }
  
  display.display();
  delay(3000);
}

// ==================== OLED SETUP ====================
void setupOLED() {
  Serial.println("Initializing OLED...");
  
  Wire.begin(D2, D1); // SDA = D2, SCL = D1
  
  if (!display.begin(SSD1306_SWITCHCAPVCC, OLED_ADDRESS)) {
    Serial.println("SSD1306 OLED failed!");
    return;
  }
  
  display.clearDisplay();
  display.setTextSize(2);
  display.setTextColor(SSD1306_WHITE);
  display.setCursor(10, 10);
  display.println("WeightSmart");
  display.setTextSize(1);
  display.setCursor(30, 40);
  display.print("v");
  display.println(firmwareVersion);
  display.display();
  delay(1500);
  
  Serial.println("OLED initialized");
}

// ==================== HX711 SETUP ====================
void setupHX711() {
  Serial.println("Initializing HX711...");
  
  scale.begin(HX711_DOUT_PIN, HX711_SCK_PIN);
  
  if (scale.is_ready()) {
    scale.set_scale(calibrationFactor);
    scale.tare();
    zeroOffset = scale.get_offset();
    Serial.println("HX711 initialized");
    Serial.print("Calibration factor: ");
    Serial.println(calibrationFactor);
  } else {
    Serial.println("HX711 not found!");
  }
}

// ==================== WEB SERVER SETUP ====================
void setupWebServer() {
  Serial.println("Setting up Web Server...");
  
  // API endpoints
  server.on("/", HTTP_GET, handleRoot);
  server.on("/api/sensors", HTTP_GET, handleSensorData);
  server.on("/api/weight", HTTP_GET, handleWeightData);
  server.on("/api/light", HTTP_GET, handleLightData);
  server.on("/api/tare", HTTP_POST, handleTare);
  server.on("/api/calibrate", HTTP_POST, handleCalibrate);
  server.on("/api/calibration-factor", HTTP_POST, handleCalibrationFactor);
  server.on("/api/status", HTTP_GET, handleDeviceStatus);
  server.on("/api/firmware", HTTP_GET, handleFirmwareInfo);
  server.on("/api/light/threshold", HTTP_POST, handleLightThreshold);
  server.on("/api/restart", HTTP_POST, handleRestart);
  server.on("/api/ping", HTTP_GET, handlePing);
  server.on("/api/wifi/config", HTTP_POST, handleWifiConfig);
  server.on("/api/ota/status", HTTP_GET, handleOTAStatus);
  
  // Enable CORS
  server.enableCORS(true);
  
  // OTA update handler
  httpUpdater.setup(&server, "/api/ota/upload");
  
  server.onNotFound(handleNotFound);
  server.begin();
  
  Serial.println("Web Server started on port 80");
}

// ==================== WEBSOCKET SETUP ====================
void setupWebSocket() {
  Serial.println("Setting up WebSocket...");
  webSocket.begin();
  webSocket.onEvent(webSocketEvent);
  Serial.println("WebSocket started on port 81");
}

// ==================== SENSOR READING ====================
void readSensors() {
  // Read weight from HX711
  if (scale.is_ready()) {
    currentWeight = scale.get_units(5); // Average of 5 readings
    if (currentWeight < 0) currentWeight = 0;
    
    // Check stability
    if (abs(currentWeight - lastWeight) < 0.5) {
      stableCount++;
      if (stableCount > 10) {
        weightStable = true;
      }
    } else {
      stableCount = 0;
      weightStable = false;
    }
    lastWeight = currentWeight;
  }
  
  // Read LDR sensor (Digital on D8)
  int ldrDigital = digitalRead(LDR_PIN);
  
  // Also try analog reading if available (A0)
  lightLevel = analogRead(A0);
  
  // Tamper detection: If light level suddenly increases or digital pin goes HIGH
  if (lightLevel > lightThreshold || ldrDigital == HIGH) {
    isTampered = true;
  } else {
    isTampered = false;
  }
  
  // Update RSSI
  if (wifiConnected) {
    wifiRSSI = WiFi.RSSI();
  }
}

// ==================== DISPLAY UPDATE ====================
void updateDisplay() {
  display.clearDisplay();
  
  // Header
  display.setTextSize(1);
  display.setTextColor(SSD1306_WHITE);
  display.setCursor(0, 0);
  display.print("IP: ");
  display.println(currentIP);
  
  // Draw separator line
  display.drawLine(0, 10, 128, 10, SSD1306_WHITE);
  
  // Weight display
  display.setTextSize(2);
  display.setCursor(0, 15);
  display.print(currentWeight, 1);
  display.setTextSize(1);
  display.print(" g");
  
  // Stability indicator
  display.setCursor(100, 15);
  if (weightStable) {
    display.print("OK");
  } else {
    display.print("...");
  }
  
  // Light level
  display.setCursor(0, 35);
  display.print("Light: ");
  display.print(lightLevel);
  
  // Tamper status
  display.setCursor(70, 35);
  if (isTampered) {
    display.setTextColor(SSD1306_BLACK, SSD1306_WHITE);
    display.print(" TAMPER! ");
    display.setTextColor(SSD1306_WHITE);
  } else {
    display.print("SECURE");
  }
  
  // WiFi status
  display.setCursor(0, 48);
  display.print("WiFi: ");
  if (wifiConnected) {
    display.print(wifiRSSI);
    display.print("dBm");
  } else {
    display.print("AP Mode");
  }
  
  // Uptime
  display.setCursor(80, 48);
  unsigned long uptime = (millis() - startTime) / 1000;
  int hours = uptime / 3600;
  int mins = (uptime % 3600) / 60;
  display.print(hours);
  display.print("h");
  display.print(mins);
  display.print("m");
  
  // Footer line
  display.drawLine(0, 58, 128, 58, SSD1306_WHITE);
  display.setCursor(30, 60);
  display.print("WeightSmart");
  
  display.display();
}

// ==================== WEBSOCKET BROADCAST ====================
void broadcastSensorData() {
  if (webSocket.connectedClients() > 0) {
    String json = getSensorDataJSON();
    webSocket.broadcastTXT(json);
  }
}

// ==================== API HANDLERS ====================

void handleRoot() {
  String html = "<!DOCTYPE html><html><head>";
  html += "<meta name='viewport' content='width=device-width, initial-scale=1'>";
  html += "<title>WeightSmart ESP8266</title>";
  html += "<style>";
  html += "body{font-family:Arial;margin:20px;background:#1a1a2e;color:#eee;}";
  html += ".card{background:#16213e;padding:20px;border-radius:10px;margin:10px 0;}";
  html += ".value{font-size:2em;color:#00bfa5;}";
  html += ".alert{color:#ff5252;font-weight:bold;}";
  html += ".ok{color:#00e676;}";
  html += "h1{color:#00bfa5;}";
  html += "</style></head><body>";
  html += "<h1>WeightSmart ESP8266</h1>";
  html += "<div class='card'><h2>Weight</h2>";
  html += "<p class='value'>" + String(currentWeight, 2) + " g</p>";
  html += "<p>Status: " + String(weightStable ? "<span class='ok'>Stable</span>" : "Measuring...") + "</p>";
  html += "</div>";
  html += "<div class='card'><h2>Tamper Detection</h2>";
  html += "<p>Light Level: " + String(lightLevel) + "</p>";
  html += "<p>Threshold: " + String(lightThreshold) + "</p>";
  html += "<p>Status: " + String(isTampered ? "<span class='alert'>TAMPERED!</span>" : "<span class='ok'>Secure</span>") + "</p>";
  html += "</div>";
  html += "<div class='card'><h2>Device Info</h2>";
  html += "<p>IP: " + currentIP + "</p>";
  html += "<p>Device ID: " + deviceId + "</p>";
  html += "<p>Firmware: v" + firmwareVersion + "</p>";
  html += "<p>WiFi RSSI: " + String(wifiRSSI) + " dBm</p>";
  html += "<p>Free Heap: " + String(ESP.getFreeHeap()) + " bytes</p>";
  html += "</div>";
  html += "<p><small>API: /api/sensors | WebSocket: ws://" + currentIP + ":81</small></p>";
  html += "<script>setTimeout(()=>location.reload(),2000);</script>";
  html += "</body></html>";
  
  server.send(200, "text/html", html);
}

void handleSensorData() {
  String json = getSensorDataJSON();
  server.send(200, "application/json", json);
}

void handleWeightData() {
  StaticJsonDocument<256> doc;
  doc["weight"] = currentWeight;
  doc["unit"] = "g";
  doc["raw_value"] = scale.read();
  doc["timestamp"] = millis();
  doc["is_stable"] = weightStable;
  doc["calibration_factor"] = calibrationFactor;
  
  String json;
  serializeJson(doc, json);
  server.send(200, "application/json", json);
}

void handleLightData() {
  StaticJsonDocument<256> doc;
  doc["light_level"] = lightLevel;
  doc["is_tampered"] = isTampered;
  doc["threshold"] = lightThreshold;
  doc["timestamp"] = millis();
  doc["ambient_light"] = ambientLight;
  
  String json;
  serializeJson(doc, json);
  server.send(200, "application/json", json);
}

void handleTare() {
  scale.tare();
  zeroOffset = scale.get_offset();
  
  StaticJsonDocument<128> doc;
  doc["success"] = true;
  doc["message"] = "Scale tared successfully";
  
  String json;
  serializeJson(doc, json);
  server.send(200, "application/json", json);
}

void handleCalibrate() {
  if (server.hasArg("plain")) {
    StaticJsonDocument<128> reqDoc;
    deserializeJson(reqDoc, server.arg("plain"));
    
    float knownWeight = reqDoc["knownWeight"];
    if (knownWeight > 0) {
      scale.set_scale();
      float reading = scale.get_units(10);
      calibrationFactor = reading / knownWeight;
      scale.set_scale(calibrationFactor);
      saveCalibration();
      
      StaticJsonDocument<128> doc;
      doc["success"] = true;
      doc["message"] = "Calibration successful";
      doc["calibration_factor"] = calibrationFactor;
      
      String json;
      serializeJson(doc, json);
      server.send(200, "application/json", json);
      return;
    }
  }
  
  server.send(400, "application/json", "{\"success\":false,\"message\":\"Invalid weight\"}");
}

void handleCalibrationFactor() {
  if (server.hasArg("plain")) {
    StaticJsonDocument<128> reqDoc;
    deserializeJson(reqDoc, server.arg("plain"));
    
    float factor = reqDoc["factor"];
    if (factor != 0) {
      calibrationFactor = factor;
      scale.set_scale(calibrationFactor);
      saveCalibration();
      
      server.send(200, "application/json", "{\"success\":true,\"message\":\"Factor updated\"}");
      return;
    }
  }
  
  server.send(400, "application/json", "{\"success\":false,\"message\":\"Invalid factor\"}");
}

void handleDeviceStatus() {
  StaticJsonDocument<256> doc;
  doc["connected"] = wifiConnected;
  doc["ip_address"] = currentIP;
  doc["mac_address"] = WiFi.macAddress();
  doc["ssid"] = wifiConnected ? STA_SSID : AP_SSID;
  doc["signal_strength"] = wifiRSSI;
  doc["mode"] = wifiConnected ? "STA" : "AP";
  
  String json;
  serializeJson(doc, json);
  server.send(200, "application/json", json);
}

void handleFirmwareInfo() {
  StaticJsonDocument<256> doc;
  doc["current_version"] = firmwareVersion;
  doc["latest_version"] = firmwareVersion;
  doc["update_available"] = false;
  doc["firmware_size"] = 0;
  doc["changelog"] = "";
  doc["download_url"] = "";
  
  String json;
  serializeJson(doc, json);
  server.send(200, "application/json", json);
}

void handleLightThreshold() {
  if (server.hasArg("plain")) {
    StaticJsonDocument<128> reqDoc;
    deserializeJson(reqDoc, server.arg("plain"));
    
    int threshold = reqDoc["threshold"];
    if (threshold > 0) {
      lightThreshold = threshold;
      EEPROM.put(LIGHT_THRESHOLD_ADDR, lightThreshold);
      EEPROM.commit();
      
      server.send(200, "application/json", "{\"success\":true,\"message\":\"Threshold updated\"}");
      return;
    }
  }
  
  server.send(400, "application/json", "{\"success\":false,\"message\":\"Invalid threshold\"}");
}

void handleRestart() {
  server.send(200, "application/json", "{\"success\":true,\"message\":\"Restarting...\"}");
  delay(500);
  ESP.restart();
}

void handlePing() {
  StaticJsonDocument<64> doc;
  doc["success"] = true;
  doc["message"] = "pong";
  
  String json;
  serializeJson(doc, json);
  server.send(200, "application/json", json);
}

void handleWifiConfig() {
  if (server.hasArg("plain")) {
    StaticJsonDocument<256> reqDoc;
    deserializeJson(reqDoc, server.arg("plain"));
    
    String ssid = reqDoc["ssid"];
    String password = reqDoc["password"];
    
    if (ssid.length() > 0) {
      // Save and attempt connection
      server.send(200, "application/json", "{\"success\":true,\"message\":\"WiFi config saved. Reconnecting...\"}");
      delay(500);
      
      WiFi.begin(ssid.c_str(), password.c_str());
      return;
    }
  }
  
  server.send(400, "application/json", "{\"success\":false,\"message\":\"Invalid config\"}");
}

void handleOTAStatus() {
  StaticJsonDocument<128> doc;
  doc["success"] = true;
  doc["state"] = "idle";
  doc["progress"] = 0;
  doc["message"] = "Ready for update";
  
  String json;
  serializeJson(doc, json);
  server.send(200, "application/json", json);
}

void handleNotFound() {
  server.send(404, "application/json", "{\"error\":\"Not found\"}");
}

// ==================== WEBSOCKET EVENT ====================
void webSocketEvent(uint8_t num, WStype_t type, uint8_t * payload, size_t length) {
  switch(type) {
    case WStype_DISCONNECTED:
      Serial.printf("[%u] Disconnected!\n", num);
      break;
    case WStype_CONNECTED:
      {
        IPAddress ip = webSocket.remoteIP(num);
        Serial.printf("[%u] Connected from %s\n", num, ip.toString().c_str());
        
        // Send initial data
        String json = getSensorDataJSON();
        webSocket.sendTXT(num, json);
      }
      break;
    case WStype_TEXT:
      Serial.printf("[%u] Received: %s\n", num, payload);
      // Handle commands from app if needed
      break;
  }
}

// ==================== HELPER FUNCTIONS ====================

String getSensorDataJSON() {
  StaticJsonDocument<512> doc;
  
  // Weight object
  JsonObject weight = doc.createNestedObject("weight");
  weight["weight"] = currentWeight;
  weight["unit"] = "g";
  weight["raw_value"] = scale.is_ready() ? scale.read() : 0;
  weight["timestamp"] = millis();
  weight["is_stable"] = weightStable;
  weight["calibration_factor"] = calibrationFactor;
  
  // Light object
  JsonObject light = doc.createNestedObject("light");
  light["light_level"] = lightLevel;
  light["is_tampered"] = isTampered;
  light["threshold"] = lightThreshold;
  light["timestamp"] = millis();
  light["ambient_light"] = ambientLight;
  
  // Device info
  doc["device_id"] = deviceId;
  doc["firmware_version"] = firmwareVersion;
  doc["wifi_rssi"] = wifiRSSI;
  doc["uptime"] = millis() - startTime;
  doc["free_heap"] = ESP.getFreeHeap();
  doc["timestamp"] = millis();
  
  String json;
  serializeJson(doc, json);
  return json;
}

void saveCalibration() {
  EEPROM.put(CALIBRATION_ADDR, calibrationFactor);
  EEPROM.commit();
  Serial.print("Saved calibration factor: ");
  Serial.println(calibrationFactor);
}

void loadCalibration() {
  float savedFactor;
  EEPROM.get(CALIBRATION_ADDR, savedFactor);
  
  if (savedFactor > 0 && savedFactor < 10000) {
    calibrationFactor = savedFactor;
    Serial.print("Loaded calibration factor: ");
    Serial.println(calibrationFactor);
  }
  
  int savedThreshold;
  EEPROM.get(LIGHT_THRESHOLD_ADDR, savedThreshold);
  
  if (savedThreshold > 0 && savedThreshold < 1024) {
    lightThreshold = savedThreshold;
    Serial.print("Loaded light threshold: ");
    Serial.println(lightThreshold);
  }
}

