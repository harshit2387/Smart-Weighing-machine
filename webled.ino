#include <ESP8266WiFi.h>

const char* ssid = "levi";
const char* password = "74499495";

WiFiServer server(80);

#define LED_PIN 2   // Built-in LED (GPIO2)

void setup() {
  Serial.begin(115200);

  pinMode(LED_PIN, OUTPUT);
  digitalWrite(LED_PIN, HIGH); // LED OFF (active LOW)

  // Connect to WiFi
  WiFi.begin(ssid, password);
  Serial.print("Connecting to WiFi");

  while (WiFi.status() != WL_CONNECTED) {
    delay(500);
    Serial.print(".");
  }

  Serial.println("\nWiFi connected");
  Serial.print("IP Address: ");
  Serial.println(WiFi.localIP());

  server.begin();
}

void loop() {
  WiFiClient client = server.available();
  if (!client) return;

  String request = client.readStringUntil('\r');
  Serial.println(request);
  client.flush();

  // LED control
  if (request.indexOf("/on") != -1) {
    digitalWrite(LED_PIN, LOW);   // LED ON
  }
  if (request.indexOf("/off") != -1) {
    digitalWrite(LED_PIN, HIGH);  // LED OFF
  }

  // Web response
  client.println("HTTP/1.1 200 OK");
  client.println("Content-Type: text/html");
  client.println();
  client.println("<html><body>");
  client.println("<h2>ESP8266 LED Control</h2>");
  client.println("<a href=\"/on\"><button>ON</button></a>");
  client.println("<a href=\"/off\"><button>OFF</button></a>");
  client.println("</body></html>");

  delay(1);
}
