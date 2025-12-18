package com.example.tamper_detection.data.models

/**
 * ESP8266 Connection Configuration
 */
data class ConnectionConfig(
    val ipAddress: String = "192.168.1.1",
    val port: Int = 80,
    val wifiSsid: String = "levi",
    val wifiPassword: String = "74499495",
    val apSsid: String = "WeightSmart_ESP8266",
    val apPassword: String = "weight123",
    val useAccessPoint: Boolean = false,
    val connectionTimeout: Int = 10000,
    val readTimeout: Int = 30000,
    val pollingInterval: Long = 500L, // ms
    val autoReconnect: Boolean = true,
    val maxRetries: Int = 3
) {
    val baseUrl: String
        get() = "http://$ipAddress:$port"
        
    val webSocketUrl: String
        get() = "ws://$ipAddress:$port/ws"
}

/**
 * Calibration settings for HX711
 */
data class CalibrationConfig(
    val calibrationFactor: Float = 420.0f,
    val zeroOffset: Long = 0L,
    val knownWeight: Float = 1000f, // grams
    val maxWeight: Float = 5000f, // grams
    val minWeight: Float = 0f,
    val stabilityThreshold: Float = 0.5f,
    val averageSamples: Int = 10
)

/**
 * Light sensor settings
 */
data class LightSensorConfig(
    val tamperThreshold: Float = 100f,
    val ambientCalibration: Float = 0f,
    val sensitivityLevel: Int = 5, // 1-10
    val debounceTime: Long = 1000L // ms
)

/**
 * App settings
 */
data class AppSettings(
    val connectionConfig: ConnectionConfig = ConnectionConfig(),
    val calibrationConfig: CalibrationConfig = CalibrationConfig(),
    val lightSensorConfig: LightSensorConfig = LightSensorConfig(),
    val enableNotifications: Boolean = true,
    val enableVibration: Boolean = true,
    val enableSound: Boolean = true,
    val darkMode: Boolean = true,
    val weightUnit: WeightUnit = WeightUnit.GRAMS,
    val dataRetentionDays: Int = 30,
    val enableMLDetection: Boolean = true
)

enum class WeightUnit(val symbol: String, val multiplier: Float) {
    GRAMS("g", 1f),
    KILOGRAMS("kg", 0.001f),
    POUNDS("lb", 0.00220462f),
    OUNCES("oz", 0.035274f)
}

