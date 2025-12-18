package com.example.tamper_detection.data.models

import com.google.gson.annotations.SerializedName

/**
 * Weight data from HX711 sensor
 */
data class WeightData(
    @SerializedName("weight")
    val weight: Float = 0f,
    
    @SerializedName("unit")
    val unit: String = "kg",
    
    @SerializedName("raw_value")
    val rawValue: Long = 0L,
    
    @SerializedName("timestamp")
    val timestamp: Long = System.currentTimeMillis(),
    
    @SerializedName("is_stable")
    val isStable: Boolean = false,
    
    @SerializedName("calibration_factor")
    val calibrationFactor: Float = 1.0f
)

/**
 * Light sensor data for tamper detection
 */
data class LightSensorData(
    @SerializedName("light_level")
    val lightLevel: Float = 0f,
    
    @SerializedName("is_tampered")
    val isTampered: Boolean = false,
    
    @SerializedName("threshold")
    val threshold: Float = 100f,
    
    @SerializedName("timestamp")
    val timestamp: Long = System.currentTimeMillis(),
    
    @SerializedName("ambient_light")
    val ambientLight: Float = 0f
)

/**
 * Combined sensor data from ESP8266
 */
data class ESP8266SensorData(
    @SerializedName("weight")
    val weight: WeightData = WeightData(),
    
    @SerializedName("light")
    val light: LightSensorData = LightSensorData(),
    
    @SerializedName("device_id")
    val deviceId: String = "",
    
    @SerializedName("firmware_version")
    val firmwareVersion: String = "1.0.0",
    
    @SerializedName("wifi_rssi")
    val wifiRssi: Int = 0,
    
    @SerializedName("uptime")
    val uptime: Long = 0L,
    
    @SerializedName("free_heap")
    val freeHeap: Int = 0,
    
    @SerializedName("timestamp")
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * Device status
 */
data class DeviceStatus(
    @SerializedName("connected")
    val isConnected: Boolean = false,
    
    @SerializedName("ip_address")
    val ipAddress: String = "",
    
    @SerializedName("mac_address")
    val macAddress: String = "",
    
    @SerializedName("ssid")
    val ssid: String = "",
    
    @SerializedName("signal_strength")
    val signalStrength: Int = 0,
    
    @SerializedName("mode")
    val mode: String = "STA" // STA or AP
)

/**
 * Tamper alert event
 */
data class TamperAlert(
    val id: Long = System.currentTimeMillis(),
    val type: TamperType = TamperType.UNKNOWN,
    val severity: AlertSeverity = AlertSeverity.LOW,
    val message: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val lightLevel: Float = 0f,
    val weightChange: Float = 0f,
    val isAcknowledged: Boolean = false
)

enum class TamperType {
    LIGHT_DETECTED,
    WEIGHT_ANOMALY,
    DEVICE_MOVED,
    CONNECTION_LOST,
    UNKNOWN
}

enum class AlertSeverity {
    LOW,
    MEDIUM,
    HIGH,
    CRITICAL
}

/**
 * Weight history entry for ML analysis
 */
data class WeightHistoryEntry(
    val weight: Float,
    val timestamp: Long,
    val isAnomaly: Boolean = false,
    val confidence: Float = 1.0f
)

/**
 * ML prediction result
 */
data class TamperPrediction(
    val isTampering: Boolean = false,
    val confidence: Float = 0f,
    val tamperType: TamperType = TamperType.UNKNOWN,
    val features: List<Float> = emptyList(),
    val timestamp: Long = System.currentTimeMillis()
)

