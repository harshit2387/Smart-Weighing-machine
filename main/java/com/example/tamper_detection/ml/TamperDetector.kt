package com.example.tamper_detection.ml

import android.content.Context
import android.util.Log
import com.example.tamper_detection.data.models.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.LinkedList
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Machine Learning based Tamper Detection
 * Uses statistical analysis and anomaly detection for weight and light sensor data
 */
class TamperDetector(private val context: Context) {
    
    companion object {
        private const val TAG = "TamperDetector"
        private const val WEIGHT_HISTORY_SIZE = 100
        private const val ANOMALY_THRESHOLD = 3.0f // Standard deviations
        private const val SUDDEN_CHANGE_THRESHOLD = 50f // grams
        private const val LIGHT_SPIKE_THRESHOLD = 200f
        private const val MINIMUM_SAMPLES_FOR_DETECTION = 10
    }
    
    // Weight history for ML analysis
    private val weightHistory = LinkedList<WeightHistoryEntry>()
    private val lightHistory = LinkedList<LightSensorData>()
    
    // Running statistics
    private var weightMean = 0f
    private var weightStdDev = 0f
    private var lightBaseline = 0f
    
    // Detection state
    private val _tamperState = MutableStateFlow(TamperState())
    val tamperState: StateFlow<TamperState> = _tamperState.asStateFlow()
    
    private val _alerts = MutableStateFlow<List<TamperAlert>>(emptyList())
    val alerts: StateFlow<List<TamperAlert>> = _alerts.asStateFlow()
    
    private val alertsList = mutableListOf<TamperAlert>()
    
    data class TamperState(
        val isTamperingDetected: Boolean = false,
        val tamperType: TamperType = TamperType.UNKNOWN,
        val confidence: Float = 0f,
        val lastWeight: Float = 0f,
        val weightAnomaly: Boolean = false,
        val lightAnomaly: Boolean = false,
        val message: String = ""
    )
    
    /**
     * Process new sensor data and detect tampering
     */
    fun processSensorData(data: ESP8266SensorData): TamperPrediction {
        val weightData = data.weight
        val lightData = data.light
        
        // Add to history
        addWeightToHistory(weightData)
        addLightToHistory(lightData)
        
        // Run detection algorithms
        val weightAnomaly = detectWeightAnomaly(weightData)
        val lightAnomaly = detectLightAnomaly(lightData)
        val suddenChange = detectSuddenWeightChange(weightData)
        
        // Combine detection results
        val isTampering = weightAnomaly.first || lightAnomaly.first || suddenChange.first
        val tamperType = determineTamperType(weightAnomaly, lightAnomaly, suddenChange)
        val confidence = calculateConfidence(weightAnomaly, lightAnomaly, suddenChange)
        
        // Update state
        val newState = TamperState(
            isTamperingDetected = isTampering,
            tamperType = tamperType,
            confidence = confidence,
            lastWeight = weightData.weight,
            weightAnomaly = weightAnomaly.first,
            lightAnomaly = lightAnomaly.first,
            message = generateMessage(tamperType, confidence)
        )
        _tamperState.value = newState
        
        // Generate alert if tampering detected
        if (isTampering) {
            generateAlert(tamperType, confidence, lightData.lightLevel, weightAnomaly.second)
        }
        
        return TamperPrediction(
            isTampering = isTampering,
            confidence = confidence,
            tamperType = tamperType,
            features = extractFeatures(weightData, lightData),
            timestamp = System.currentTimeMillis()
        )
    }
    
    /**
     * Add weight reading to history
     */
    private fun addWeightToHistory(data: WeightData) {
        val entry = WeightHistoryEntry(
            weight = data.weight,
            timestamp = data.timestamp,
            isAnomaly = false
        )
        
        weightHistory.addLast(entry)
        if (weightHistory.size > WEIGHT_HISTORY_SIZE) {
            weightHistory.removeFirst()
        }
        
        // Update running statistics
        updateWeightStatistics()
    }
    
    /**
     * Add light reading to history
     */
    private fun addLightToHistory(data: LightSensorData) {
        lightHistory.addLast(data)
        if (lightHistory.size > WEIGHT_HISTORY_SIZE) {
            lightHistory.removeFirst()
        }
        
        // Update light baseline
        updateLightBaseline()
    }
    
    /**
     * Update running mean and standard deviation
     */
    private fun updateWeightStatistics() {
        if (weightHistory.isEmpty()) return
        
        val weights = weightHistory.map { it.weight }
        weightMean = weights.average().toFloat()
        
        val variance = weights.map { (it - weightMean) * (it - weightMean) }.average()
        weightStdDev = sqrt(variance).toFloat()
    }
    
    /**
     * Update light sensor baseline
     */
    private fun updateLightBaseline() {
        if (lightHistory.isEmpty()) return
        
        // Use median for robustness against outliers
        val sortedLevels = lightHistory.map { it.lightLevel }.sorted()
        lightBaseline = sortedLevels[sortedLevels.size / 2]
    }
    
    /**
     * Detect weight anomaly using z-score
     */
    private fun detectWeightAnomaly(data: WeightData): Pair<Boolean, Float> {
        if (weightHistory.size < MINIMUM_SAMPLES_FOR_DETECTION) {
            return Pair(false, 0f)
        }
        
        if (weightStdDev == 0f) {
            return Pair(false, 0f)
        }
        
        val zScore = abs(data.weight - weightMean) / weightStdDev
        val isAnomaly = zScore > ANOMALY_THRESHOLD
        
        return Pair(isAnomaly, data.weight - weightMean)
    }
    
    /**
     * Detect light sensor anomaly
     */
    private fun detectLightAnomaly(data: LightSensorData): Pair<Boolean, Float> {
        // Direct check if marked as tampered by ESP8266
        if (data.isTampered) {
            return Pair(true, data.lightLevel)
        }
        
        // Check for sudden light level increase
        val lightDelta = data.lightLevel - lightBaseline
        val isAnomaly = lightDelta > LIGHT_SPIKE_THRESHOLD || data.lightLevel > data.threshold
        
        return Pair(isAnomaly, lightDelta)
    }
    
    /**
     * Detect sudden weight change
     */
    private fun detectSuddenWeightChange(data: WeightData): Pair<Boolean, Float> {
        if (weightHistory.size < 2) {
            return Pair(false, 0f)
        }
        
        val previousWeight = weightHistory[weightHistory.size - 2].weight
        val weightChange = abs(data.weight - previousWeight)
        val isSuddenChange = weightChange > SUDDEN_CHANGE_THRESHOLD
        
        return Pair(isSuddenChange, weightChange)
    }
    
    /**
     * Determine the type of tampering
     */
    private fun determineTamperType(
        weightAnomaly: Pair<Boolean, Float>,
        lightAnomaly: Pair<Boolean, Float>,
        suddenChange: Pair<Boolean, Float>
    ): TamperType {
        return when {
            lightAnomaly.first -> TamperType.LIGHT_DETECTED
            suddenChange.first -> TamperType.DEVICE_MOVED
            weightAnomaly.first -> TamperType.WEIGHT_ANOMALY
            else -> TamperType.UNKNOWN
        }
    }
    
    /**
     * Calculate overall confidence score
     */
    private fun calculateConfidence(
        weightAnomaly: Pair<Boolean, Float>,
        lightAnomaly: Pair<Boolean, Float>,
        suddenChange: Pair<Boolean, Float>
    ): Float {
        var confidence = 0f
        var factors = 0
        
        if (lightAnomaly.first) {
            confidence += minOf(lightAnomaly.second / LIGHT_SPIKE_THRESHOLD, 1f)
            factors++
        }
        
        if (weightAnomaly.first && weightStdDev > 0) {
            confidence += minOf(abs(weightAnomaly.second) / (ANOMALY_THRESHOLD * weightStdDev), 1f)
            factors++
        }
        
        if (suddenChange.first) {
            confidence += minOf(suddenChange.second / SUDDEN_CHANGE_THRESHOLD, 1f)
            factors++
        }
        
        return if (factors > 0) confidence / factors else 0f
    }
    
    /**
     * Generate human-readable message
     */
    private fun generateMessage(type: TamperType, confidence: Float): String {
        val confidencePercent = (confidence * 100).toInt()
        return when (type) {
            TamperType.LIGHT_DETECTED -> "⚠️ Light detected inside enclosure! ($confidencePercent% confidence)"
            TamperType.WEIGHT_ANOMALY -> "⚠️ Unusual weight pattern detected! ($confidencePercent% confidence)"
            TamperType.DEVICE_MOVED -> "⚠️ Sudden weight change - possible tampering! ($confidencePercent% confidence)"
            TamperType.CONNECTION_LOST -> "⚠️ Connection lost - possible tampering!"
            TamperType.UNKNOWN -> ""
        }
    }
    
    /**
     * Generate and store alert
     */
    private fun generateAlert(
        type: TamperType,
        confidence: Float,
        lightLevel: Float,
        weightChange: Float
    ) {
        val severity = when {
            confidence > 0.8f -> AlertSeverity.CRITICAL
            confidence > 0.6f -> AlertSeverity.HIGH
            confidence > 0.4f -> AlertSeverity.MEDIUM
            else -> AlertSeverity.LOW
        }
        
        val alert = TamperAlert(
            id = System.currentTimeMillis(),
            type = type,
            severity = severity,
            message = generateMessage(type, confidence),
            timestamp = System.currentTimeMillis(),
            lightLevel = lightLevel,
            weightChange = weightChange
        )
        
        alertsList.add(0, alert)
        if (alertsList.size > 50) {
            alertsList.removeLast()
        }
        _alerts.value = alertsList.toList()
    }
    
    /**
     * Extract features for ML model
     */
    private fun extractFeatures(weightData: WeightData, lightData: LightSensorData): List<Float> {
        return listOf(
            weightData.weight,
            weightData.rawValue.toFloat(),
            weightMean,
            weightStdDev,
            lightData.lightLevel,
            lightBaseline,
            lightData.threshold,
            if (weightHistory.size >= 2) {
                weightHistory[weightHistory.size - 2].weight - weightData.weight
            } else 0f
        )
    }
    
    /**
     * Acknowledge an alert
     */
    fun acknowledgeAlert(alertId: Long) {
        val index = alertsList.indexOfFirst { it.id == alertId }
        if (index >= 0) {
            alertsList[index] = alertsList[index].copy(isAcknowledged = true)
            _alerts.value = alertsList.toList()
        }
    }
    
    /**
     * Clear all alerts
     */
    fun clearAlerts() {
        alertsList.clear()
        _alerts.value = emptyList()
    }
    
    /**
     * Reset detector state
     */
    fun reset() {
        weightHistory.clear()
        lightHistory.clear()
        weightMean = 0f
        weightStdDev = 0f
        lightBaseline = 0f
        _tamperState.value = TamperState()
        clearAlerts()
    }
    
    /**
     * Get weight statistics
     */
    fun getWeightStatistics(): Triple<Float, Float, Int> {
        return Triple(weightMean, weightStdDev, weightHistory.size)
    }
    
    /**
     * Calibrate light baseline manually
     */
    fun calibrateLightBaseline(baseline: Float) {
        lightBaseline = baseline
    }
    
    /**
     * Set custom thresholds
     */
    fun setThresholds(
        anomalyThreshold: Float = ANOMALY_THRESHOLD,
        suddenChangeThreshold: Float = SUDDEN_CHANGE_THRESHOLD,
        lightSpikeThreshold: Float = LIGHT_SPIKE_THRESHOLD
    ) {
        // Store custom thresholds (would be used in future ML model configuration)
        Log.d(TAG, "Thresholds updated: anomaly=$anomalyThreshold, sudden=$suddenChangeThreshold, light=$lightSpikeThreshold")
    }
}

