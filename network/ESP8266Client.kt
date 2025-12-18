package com.example.tamper_detection.network

import android.util.Log
import com.example.tamper_detection.data.models.*
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * ESP8266 Client for managing connection and real-time data
 */
class ESP8266Client(
    private var config: ConnectionConfig = ConnectionConfig()
) {
    companion object {
        private const val TAG = "ESP8266Client"
    }
    
    private var retrofit: Retrofit? = null
    private var apiService: ESP8266ApiService? = null
    private var okHttpClient: OkHttpClient? = null
    private var webSocket: WebSocket? = null
    
    private val gson: Gson = GsonBuilder().create()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    // State flows
    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()
    
    private val _sensorData = MutableStateFlow(ESP8266SensorData())
    val sensorData: StateFlow<ESP8266SensorData> = _sensorData.asStateFlow()
    
    private val _weightData = MutableStateFlow(WeightData())
    val weightData: StateFlow<WeightData> = _weightData.asStateFlow()
    
    private val _lightData = MutableStateFlow(LightSensorData())
    val lightData: StateFlow<LightSensorData> = _lightData.asStateFlow()
    
    private val _deviceStatus = MutableStateFlow(DeviceStatus())
    val deviceStatus: StateFlow<DeviceStatus> = _deviceStatus.asStateFlow()
    
    private val _errors = MutableSharedFlow<String>()
    val errors: SharedFlow<String> = _errors.asSharedFlow()
    
    private var pollingJob: Job? = null
    private var reconnectJob: Job? = null
    
    enum class ConnectionState {
        DISCONNECTED,
        CONNECTING,
        CONNECTED,
        RECONNECTING,
        ERROR
    }
    
    /**
     * Initialize the client with configuration
     */
    fun initialize(newConfig: ConnectionConfig) {
        config = newConfig
        createHttpClient()
        createRetrofitService()
    }
    
    private fun createHttpClient() {
        okHttpClient = OkHttpClient.Builder()
            .connectTimeout(config.connectionTimeout.toLong(), TimeUnit.MILLISECONDS)
            .readTimeout(config.readTimeout.toLong(), TimeUnit.MILLISECONDS)
            .writeTimeout(config.readTimeout.toLong(), TimeUnit.MILLISECONDS)
            .retryOnConnectionFailure(config.autoReconnect)
            .addInterceptor { chain ->
                val request = chain.request().newBuilder()
                    .addHeader("Content-Type", "application/json")
                    .addHeader("Accept", "application/json")
                    .build()
                chain.proceed(request)
            }
            .build()
    }
    
    private fun createRetrofitService() {
        retrofit = Retrofit.Builder()
            .baseUrl(config.baseUrl + "/")
            .client(okHttpClient!!)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
        
        apiService = retrofit?.create(ESP8266ApiService::class.java)
    }
    
    /**
     * Connect to ESP8266
     */
    suspend fun connect(): Result<Boolean> {
        return try {
            _connectionState.value = ConnectionState.CONNECTING
            
            if (apiService == null) {
                createHttpClient()
                createRetrofitService()
            }
            
            // Test connection with ping
            val response = apiService?.ping()
            
            if (response?.isSuccessful == true) {
                _connectionState.value = ConnectionState.CONNECTED
                startPolling()
                connectWebSocket()
                Result.success(true)
            } else {
                _connectionState.value = ConnectionState.ERROR
                _errors.emit("Failed to connect: ${response?.message()}")
                Result.failure(IOException("Connection failed"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Connection error", e)
            _connectionState.value = ConnectionState.ERROR
            _errors.emit("Connection error: ${e.message}")
            
            if (config.autoReconnect) {
                scheduleReconnect()
            }
            Result.failure(e)
        }
    }
    
    /**
     * Disconnect from ESP8266
     */
    fun disconnect() {
        pollingJob?.cancel()
        reconnectJob?.cancel()
        webSocket?.close(1000, "Client disconnected")
        _connectionState.value = ConnectionState.DISCONNECTED
    }
    
    /**
     * Start polling sensor data
     */
    private fun startPolling() {
        pollingJob?.cancel()
        pollingJob = scope.launch {
            while (isActive && _connectionState.value == ConnectionState.CONNECTED) {
                try {
                    fetchSensorData()
                } catch (e: Exception) {
                    Log.e(TAG, "Polling error", e)
                    if (config.autoReconnect) {
                        _connectionState.value = ConnectionState.RECONNECTING
                        scheduleReconnect()
                        break
                    }
                }
                delay(config.pollingInterval)
            }
        }
    }
    
    /**
     * Schedule reconnection attempt
     */
    private fun scheduleReconnect() {
        reconnectJob?.cancel()
        reconnectJob = scope.launch {
            var retries = 0
            while (retries < config.maxRetries && _connectionState.value != ConnectionState.CONNECTED) {
                delay(2000L * (retries + 1)) // Exponential backoff
                Log.d(TAG, "Reconnection attempt ${retries + 1}/${config.maxRetries}")
                
                try {
                    val result = connect()
                    if (result.isSuccess) {
                        return@launch
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Reconnect failed", e)
                }
                retries++
            }
            
            if (_connectionState.value != ConnectionState.CONNECTED) {
                _connectionState.value = ConnectionState.ERROR
                _errors.emit("Failed to reconnect after $retries attempts")
            }
        }
    }
    
    /**
     * Connect WebSocket for real-time updates
     */
    private fun connectWebSocket() {
        val request = Request.Builder()
            .url(config.webSocketUrl)
            .build()
        
        webSocket = okHttpClient?.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.d(TAG, "WebSocket connected")
            }
            
            override fun onMessage(webSocket: WebSocket, text: String) {
                try {
                    val data = gson.fromJson(text, ESP8266SensorData::class.java)
                    scope.launch {
                        _sensorData.value = data
                        _weightData.value = data.weight
                        _lightData.value = data.light
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "WebSocket message parse error", e)
                }
            }
            
            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.e(TAG, "WebSocket error", t)
                // Continue with polling fallback
            }
            
            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                Log.d(TAG, "WebSocket closed: $reason")
            }
        })
    }
    
    /**
     * Fetch sensor data via HTTP
     */
    private suspend fun fetchSensorData() {
        val response = apiService?.getSensorData()
        if (response?.isSuccessful == true) {
            response.body()?.let { data ->
                _sensorData.value = data
                _weightData.value = data.weight
                _lightData.value = data.light
            }
        }
    }
    
    /**
     * Get current sensor data
     */
    suspend fun getSensorData(): Result<ESP8266SensorData> {
        return try {
            val response = apiService?.getSensorData()
            if (response?.isSuccessful == true && response.body() != null) {
                val data = response.body()!!
                _sensorData.value = data
                Result.success(data)
            } else {
                Result.failure(IOException("Failed to get sensor data"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Tare the scale
     */
    suspend fun tareScale(): Result<Boolean> {
        return try {
            val response = apiService?.tareScale()
            if (response?.isSuccessful == true) {
                Result.success(true)
            } else {
                Result.failure(IOException(response?.body()?.message ?: "Tare failed"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Calibrate with known weight
     */
    suspend fun calibrate(knownWeight: Float): Result<Boolean> {
        return try {
            val response = apiService?.calibrate(CalibrationRequest(knownWeight))
            if (response?.isSuccessful == true) {
                Result.success(true)
            } else {
                Result.failure(IOException(response?.body()?.message ?: "Calibration failed"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Set calibration factor
     */
    suspend fun setCalibrationFactor(factor: Float): Result<Boolean> {
        return try {
            val response = apiService?.setCalibrationFactor(CalibrationFactorRequest(factor))
            if (response?.isSuccessful == true) {
                Result.success(true)
            } else {
                Result.failure(IOException("Failed to set calibration factor"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Get device status
     */
    suspend fun getDeviceStatus(): Result<DeviceStatus> {
        return try {
            val response = apiService?.getDeviceStatus()
            if (response?.isSuccessful == true && response.body() != null) {
                val status = response.body()!!
                _deviceStatus.value = status
                Result.success(status)
            } else {
                Result.failure(IOException("Failed to get device status"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Get firmware info
     */
    suspend fun getFirmwareInfo(): Result<FirmwareInfo> {
        return try {
            val response = apiService?.getFirmwareInfo()
            if (response?.isSuccessful == true && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                Result.failure(IOException("Failed to get firmware info"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Start OTA update
     */
    suspend fun startOTAUpdate(firmwareUrl: String, version: String): Result<Boolean> {
        return try {
            val request = OTAUpdateRequest(firmwareUrl, version)
            val response = apiService?.startOTAUpdate(request)
            if (response?.isSuccessful == true && response.body()?.success == true) {
                Result.success(true)
            } else {
                Result.failure(IOException(response?.body()?.message ?: "OTA update failed"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Get OTA update status
     */
    suspend fun getOTAStatus(): Result<OTAUpdateResponse> {
        return try {
            val response = apiService?.getOTAStatus()
            if (response?.isSuccessful == true && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                Result.failure(IOException("Failed to get OTA status"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Upload firmware file
     */
    suspend fun uploadFirmware(firmwareBytes: ByteArray): Result<Boolean> {
        return try {
            val requestBody = okhttp3.RequestBody.create(
                "application/octet-stream".toMediaTypeOrNull(),
                firmwareBytes
            )
            val response = apiService?.uploadFirmware(requestBody)
            if (response?.isSuccessful == true && response.body()?.success == true) {
                Result.success(true)
            } else {
                Result.failure(IOException(response?.body()?.message ?: "Firmware upload failed"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Restart the device
     */
    suspend fun restartDevice(): Result<Boolean> {
        return try {
            val response = apiService?.restartDevice()
            if (response?.isSuccessful == true) {
                disconnect()
                Result.success(true)
            } else {
                Result.failure(IOException("Failed to restart device"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Set light sensor threshold
     */
    suspend fun setLightThreshold(threshold: Float): Result<Boolean> {
        return try {
            val response = apiService?.setLightThreshold(LightThresholdRequest(threshold))
            if (response?.isSuccessful == true) {
                Result.success(true)
            } else {
                Result.failure(IOException("Failed to set light threshold"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Update connection config
     */
    fun updateConfig(newConfig: ConnectionConfig) {
        val wasConnected = _connectionState.value == ConnectionState.CONNECTED
        disconnect()
        initialize(newConfig)
        if (wasConnected) {
            scope.launch { connect() }
        }
    }
    
    /**
     * Clean up resources
     */
    fun cleanup() {
        disconnect()
        scope.cancel()
        okHttpClient?.dispatcher?.executorService?.shutdown()
    }
}

