# WeightSmart ESP8266 Firmware

## Hardware Requirements

### Components:
- **ESP8266** (NodeMCU/Wemos D1 Mini)
- **HX711** Load Cell Amplifier
- **Load Cell** (Weight Sensor)
- **OLED Display** 128x64 (SSD1306, I2C)
- **LDR Sensor** (Light Dependent Resistor)
- **10K Resistor** (for LDR)

## Wiring Diagram

### OLED Display (I2C):
| OLED Pin | ESP8266 Pin |
|----------|-------------|
| VCC      | 3.3V        |
| GND      | GND         |
| SDA      | D2 (GPIO4)  |
| SCL      | D1 (GPIO5)  |

### HX711 Load Cell:
| HX711 Pin | ESP8266 Pin |
|-----------|-------------|
| VCC       | 3.3V        |
| GND       | GND         |
| DT (Data) | D5 (GPIO14) |
| SCK       | D6 (GPIO12) |

### LDR Sensor (Tamper Detection):
| Component | ESP8266 Pin |
|-----------|-------------|
| LDR + 10K | D8 (GPIO15) |
| LDR       | A0 (Analog) |

```
LDR Connection:
     +3.3V
        |
       LDR
        |
        +-----> D8 (Digital) & A0 (Analog)
        |
       10K
        |
       GND
```

## WiFi Configuration

### Station Mode (Connects to your WiFi):
```cpp
const char* STA_SSID = "levi";
const char* STA_PASSWORD = "74499495";
```

### Access Point Mode (ESP8266 creates WiFi):
```cpp
const char* AP_SSID = "WeightSmart_ESP8266";
const char* AP_PASSWORD = "weight123";
```

## Installation

### 1. Install Arduino IDE Libraries:

Open Arduino IDE → Tools → Manage Libraries and install:
- `ESP8266WiFi` (built-in)
- `ArduinoJson` by Benoit Blanchon
- `Adafruit GFX Library`
- `Adafruit SSD1306`
- `HX711` by Bogdan Necula
- `WebSockets` by Markus Sattler

### 2. Board Configuration:

1. Add ESP8266 board URL in Arduino IDE:
   - File → Preferences → Additional Board URLs
   - Add: `http://arduino.esp8266.com/stable/package_esp8266com_index.json`

2. Install ESP8266 board:
   - Tools → Board → Boards Manager
   - Search "ESP8266" and install

3. Select Board:
   - Tools → Board → ESP8266 Boards → NodeMCU 1.0 (ESP-12E Module)

4. Settings:
   - Flash Size: 4MB (FS:2MB OTA:~1019KB)
   - Upload Speed: 115200

### 3. Upload Firmware:

1. Connect ESP8266 via USB
2. Select correct COM port
3. Click Upload

## API Endpoints

| Endpoint | Method | Description |
|----------|--------|-------------|
| `/` | GET | Web dashboard |
| `/api/sensors` | GET | All sensor data (JSON) |
| `/api/weight` | GET | Weight data only |
| `/api/light` | GET | Light sensor data |
| `/api/tare` | POST | Zero/Tare the scale |
| `/api/calibrate` | POST | Calibrate with known weight |
| `/api/calibration-factor` | POST | Set calibration factor |
| `/api/status` | GET | Device status |
| `/api/firmware` | GET | Firmware info |
| `/api/light/threshold` | POST | Set tamper threshold |
| `/api/restart` | POST | Restart device |
| `/api/ping` | GET | Connection test |
| `/api/ota/upload` | POST | Upload firmware file |

## WebSocket

Connect to `ws://<IP>:81` for real-time data streaming.

Data format (sent every 500ms):
```json
{
  "weight": {
    "weight": 123.45,
    "unit": "g",
    "raw_value": 123456,
    "is_stable": true,
    "calibration_factor": 420.0
  },
  "light": {
    "light_level": 250,
    "is_tampered": false,
    "threshold": 500
  },
  "device_id": "ABC123",
  "firmware_version": "1.0.0",
  "wifi_rssi": -55,
  "uptime": 123456,
  "free_heap": 45000
}
```

## OLED Display

The display shows:
- **Line 1**: IP Address
- **Line 2-3**: Current Weight (large)
- **Line 4**: Light Level + Tamper Status
- **Line 5**: WiFi Signal + Uptime
- **Footer**: "WeightSmart"

## Calibration

### Method 1: Via App
1. Open Android app → Weight → Calibrate
2. Place known weight on scale
3. Enter weight value and confirm

### Method 2: Via Web Interface
1. Navigate to `http://<IP>/`
2. Use the calibration option
3. Or send POST to `/api/calibrate` with `{"knownWeight": 1000}`

### Method 3: Manual Factor
Send POST to `/api/calibration-factor`:
```json
{"factor": 420.0}
```

## Tamper Detection

The LDR sensor detects light inside the enclosure:
- Normal state: Dark (low light level)
- Tampered: Light detected (enclosure opened)

Adjust sensitivity via `/api/light/threshold`:
```json
{"threshold": 500}
```

## OTA Updates

Upload firmware via web at `http://<IP>/api/ota/upload`

## Troubleshooting

### OLED Not Working:
- Check I2C address (0x3C or 0x3D)
- Verify SDA/SCL connections
- Try running I2C scanner sketch

### HX711 Not Reading:
- Check DT and SCK connections
- Verify load cell wiring (Red→E+, Black→E-, White→A-, Green→A+)
- Adjust calibration factor

### WiFi Issues:
- ESP8266 creates its own AP if cannot connect
- Connect to "WeightSmart_ESP8266" with password "weight123"
- Check IP on OLED display

### Weight Unstable:
- Increase averaging samples
- Check load cell mounting
- Reduce vibrations

