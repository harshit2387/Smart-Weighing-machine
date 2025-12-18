package com.example.tamper_detection.network

import com.example.tamper_detection.data.models.*
import retrofit2.Response
import retrofit2.http.*

/**
 * Retrofit API interface for ESP8266 communication
 */
interface ESP8266ApiService {
    
    /**
     * Get current sensor data (weight + light)
     */
    @GET("/api/sensors")
    suspend fun getSensorData(): Response<ESP8266SensorData>
    
    /**
     * Get weight data only
     */
    @GET("/api/weight")
    suspend fun getWeightData(): Response<WeightData>
    
    /**
     * Get light sensor data only
     */
    @GET("/api/light")
    suspend fun getLightData(): Response<LightSensorData>
    
    /**
     * Get device status
     */
    @GET("/api/status")
    suspend fun getDeviceStatus(): Response<DeviceStatus>
    
    /**
     * Tare/Zero the scale
     */
    @POST("/api/tare")
    suspend fun tareScale(): Response<ApiResponse>
    
    /**
     * Calibrate with known weight
     */
    @POST("/api/calibrate")
    suspend fun calibrate(
        @Body request: CalibrationRequest
    ): Response<ApiResponse>
    
    /**
     * Set calibration factor
     */
    @POST("/api/calibration-factor")
    suspend fun setCalibrationFactor(
        @Body request: CalibrationFactorRequest
    ): Response<ApiResponse>
    
    /**
     * Get firmware info
     */
    @GET("/api/firmware")
    suspend fun getFirmwareInfo(): Response<FirmwareInfo>
    
    /**
     * Start OTA update
     */
    @POST("/api/ota/start")
    suspend fun startOTAUpdate(
        @Body request: OTAUpdateRequest
    ): Response<OTAUpdateResponse>
    
    /**
     * Get OTA update status
     */
    @GET("/api/ota/status")
    suspend fun getOTAStatus(): Response<OTAUpdateResponse>
    
    /**
     * Upload firmware file directly
     */
    @Multipart
    @POST("/api/ota/upload")
    suspend fun uploadFirmware(
        @Part("firmware") firmwareData: okhttp3.RequestBody
    ): Response<OTAUpdateResponse>
    
    /**
     * Restart ESP8266
     */
    @POST("/api/restart")
    suspend fun restartDevice(): Response<ApiResponse>
    
    /**
     * Set light sensor threshold
     */
    @POST("/api/light/threshold")
    suspend fun setLightThreshold(
        @Body request: LightThresholdRequest
    ): Response<ApiResponse>
    
    /**
     * Get sensor history
     */
    @GET("/api/history")
    suspend fun getSensorHistory(
        @Query("limit") limit: Int = 100
    ): Response<List<ESP8266SensorData>>
    
    /**
     * Ping device
     */
    @GET("/api/ping")
    suspend fun ping(): Response<ApiResponse>
    
    /**
     * Set WiFi configuration
     */
    @POST("/api/wifi/config")
    suspend fun setWiFiConfig(
        @Body config: WiFiConfigRequest
    ): Response<ApiResponse>
}

// Request/Response models for API
data class ApiResponse(
    val success: Boolean,
    val message: String = "",
    val data: Any? = null
)

data class CalibrationRequest(
    val knownWeight: Float
)

data class CalibrationFactorRequest(
    val factor: Float
)

data class LightThresholdRequest(
    val threshold: Float
)

data class WiFiConfigRequest(
    val ssid: String,
    val password: String,
    val useAP: Boolean = false
)

