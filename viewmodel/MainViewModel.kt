package com.example.tamper_detection.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.tamper_detection.data.models.*
import com.example.tamper_detection.ml.TamperDetector
import com.example.tamper_detection.network.ESP8266Client
import com.example.tamper_detection.ota.OTAManager
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {
    
    companion object {
        private const val TAG = "MainViewModel"
    }
    
    // Network client
    private val esp8266Client = ESP8266Client()
    
    // ML detector
    private val tamperDetector = TamperDetector(application)
    
    // OTA manager
    private val otaManager = OTAManager(application, esp8266Client)
    
    // UI State
    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()
    
    // Settings
    private val _settings = MutableStateFlow(AppSettings())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()
    
    // Expose flows from components
    val connectionState = esp8266Client.connectionState
    val sensorData = esp8266Client.sensorData
    val weightData = esp8266Client.weightData
    val lightData = esp8266Client.lightData
    val deviceStatus = esp8266Client.deviceStatus
    val tamperState = tamperDetector.tamperState
    val alerts = tamperDetector.alerts
    val updateStatus = otaManager.updateStatus
    val firmwareInfo = otaManager.firmwareInfo
    val availableFirmwares = otaManager.availableFirmwares
    
    // Weight history for charts
    private val _weightHistory = MutableStateFlow<List<WeightHistoryEntry>>(emptyList())
    val weightHistory: StateFlow<List<WeightHistoryEntry>> = _weightHistory.asStateFlow()
    
    init {
        // Initialize with default config
        esp8266Client.initialize(_settings.value.connectionConfig)
        
        // Observe sensor data for ML processing
        viewModelScope.launch {
            esp8266Client.sensorData.collect { data ->
                if (data.deviceId.isNotEmpty() || data.weight.weight != 0f) {
                    // Process through ML
                    if (_settings.value.enableMLDetection) {
                        tamperDetector.processSensorData(data)
                    }
                    
                    // Update weight history
                    updateWeightHistory(data.weight)
                }
            }
        }
        
        // Observe errors
        viewModelScope.launch {
            esp8266Client.errors.collect { error ->
                _uiState.update { it.copy(errorMessage = error) }
            }
        }
    }
    
    /**
     * Connect to ESP8266
     */
    fun connect() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            
            val result = esp8266Client.connect()
            
            _uiState.update { 
                it.copy(
                    isLoading = false,
                    errorMessage = if (result.isFailure) result.exceptionOrNull()?.message else null
                )
            }
        }
    }
    
    /**
     * Disconnect from ESP8266
     */
    fun disconnect() {
        esp8266Client.disconnect()
    }
    
    /**
     * Update connection settings
     */
    fun updateConnectionSettings(ipAddress: String, port: Int = 80) {
        val newConfig = _settings.value.connectionConfig.copy(
            ipAddress = ipAddress,
            port = port
        )
        _settings.update { it.copy(connectionConfig = newConfig) }
        esp8266Client.updateConfig(newConfig)
    }
    
    /**
     * Tare the scale
     */
    fun tareScale() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            
            val result = esp8266Client.tareScale()
            
            _uiState.update { 
                it.copy(
                    isLoading = false,
                    message = if (result.isSuccess) "Scale tared successfully" else null,
                    errorMessage = if (result.isFailure) result.exceptionOrNull()?.message else null
                )
            }
        }
    }
    
    /**
     * Calibrate with known weight
     */
    fun calibrate(knownWeight: Float) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            
            val result = esp8266Client.calibrate(knownWeight)
            
            _uiState.update { 
                it.copy(
                    isLoading = false,
                    message = if (result.isSuccess) "Calibration successful" else null,
                    errorMessage = if (result.isFailure) result.exceptionOrNull()?.message else null
                )
            }
        }
    }
    
    /**
     * Set calibration factor
     */
    fun setCalibrationFactor(factor: Float) {
        viewModelScope.launch {
            val result = esp8266Client.setCalibrationFactor(factor)
            
            if (result.isSuccess) {
                val newCalibration = _settings.value.calibrationConfig.copy(calibrationFactor = factor)
                _settings.update { it.copy(calibrationConfig = newCalibration) }
            }
        }
    }
    
    /**
     * Set light threshold
     */
    fun setLightThreshold(threshold: Float) {
        viewModelScope.launch {
            val result = esp8266Client.setLightThreshold(threshold)
            
            if (result.isSuccess) {
                val newLightConfig = _settings.value.lightSensorConfig.copy(tamperThreshold = threshold)
                _settings.update { it.copy(lightSensorConfig = newLightConfig) }
            }
        }
    }
    
    /**
     * Check for firmware updates
     */
    fun checkForUpdates() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            
            val result = otaManager.checkForUpdates()
            
            _uiState.update { 
                it.copy(
                    isLoading = false,
                    errorMessage = if (result.isFailure) result.exceptionOrNull()?.message else null
                )
            }
        }
    }
    
    /**
     * Download firmware
     */
    fun downloadFirmware(url: String, version: String) {
        viewModelScope.launch {
            otaManager.downloadFirmware(url, version)
        }
    }
    
    /**
     * Start OTA update
     */
    fun startOTAUpdate(firmwareUrl: String, version: String) {
        viewModelScope.launch {
            otaManager.startOTAUpdate(firmwareUrl, version)
        }
    }
    
    /**
     * Upload firmware file
     */
    fun uploadFirmware(firmwareFile: FirmwareFile) {
        viewModelScope.launch {
            otaManager.uploadFirmware(firmwareFile)
        }
    }
    
    /**
     * Cancel OTA update
     */
    fun cancelOTAUpdate() {
        otaManager.cancelUpdate()
    }
    
    /**
     * Delete local firmware
     */
    fun deleteFirmware(firmware: FirmwareFile) {
        otaManager.deleteFirmware(firmware)
    }
    
    /**
     * Acknowledge tamper alert
     */
    fun acknowledgeAlert(alertId: Long) {
        tamperDetector.acknowledgeAlert(alertId)
    }
    
    /**
     * Clear all alerts
     */
    fun clearAlerts() {
        tamperDetector.clearAlerts()
    }
    
    /**
     * Reset tamper detector
     */
    fun resetDetector() {
        tamperDetector.reset()
    }
    
    /**
     * Restart device
     */
    fun restartDevice() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            
            val result = esp8266Client.restartDevice()
            
            _uiState.update { 
                it.copy(
                    isLoading = false,
                    message = if (result.isSuccess) "Device restarting..." else null,
                    errorMessage = if (result.isFailure) result.exceptionOrNull()?.message else null
                )
            }
        }
    }
    
    /**
     * Update app settings
     */
    fun updateSettings(newSettings: AppSettings) {
        _settings.value = newSettings
        esp8266Client.updateConfig(newSettings.connectionConfig)
    }
    
    /**
     * Toggle ML detection
     */
    fun toggleMLDetection(enabled: Boolean) {
        _settings.update { it.copy(enableMLDetection = enabled) }
    }
    
    /**
     * Change weight unit
     */
    fun setWeightUnit(unit: WeightUnit) {
        _settings.update { it.copy(weightUnit = unit) }
    }
    
    /**
     * Update weight history for charts
     */
    private fun updateWeightHistory(weight: WeightData) {
        val newEntry = WeightHistoryEntry(
            weight = weight.weight,
            timestamp = weight.timestamp
        )
        
        _weightHistory.update { history ->
            (history + newEntry).takeLast(100)
        }
    }
    
    /**
     * Get formatted weight
     */
    fun getFormattedWeight(weightGrams: Float): String {
        val unit = _settings.value.weightUnit
        val value = weightGrams * unit.multiplier
        return "%.2f %s".format(value, unit.symbol)
    }
    
    /**
     * Clear error message
     */
    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }
    
    /**
     * Clear message
     */
    fun clearMessage() {
        _uiState.update { it.copy(message = null) }
    }
    
    /**
     * Set current screen
     */
    fun setCurrentScreen(screen: Screen) {
        _uiState.update { it.copy(currentScreen = screen) }
    }
    
    override fun onCleared() {
        super.onCleared()
        esp8266Client.cleanup()
        otaManager.cleanup()
    }
}

/**
 * UI State
 */
data class UiState(
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val message: String? = null,
    val currentScreen: Screen = Screen.Dashboard
)

/**
 * Navigation screens
 */
sealed class Screen(val route: String) {
    object Dashboard : Screen("dashboard")
    object Weight : Screen("weight")
    object Tamper : Screen("tamper")
    object Firmware : Screen("firmware")
    object Settings : Screen("settings")
}

