package com.example.tamper_detection.ota

import android.content.Context
import android.util.Log
import com.example.tamper_detection.data.models.*
import com.example.tamper_detection.network.ESP8266Client
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest

/**
 * OTA (Over-The-Air) Firmware Update Manager
 */
class OTAManager(
    private val context: Context,
    private val esp8266Client: ESP8266Client
) {
    companion object {
        private const val TAG = "OTAManager"
        private const val FIRMWARE_DIR = "firmware"
        private const val CHUNK_SIZE = 4096
    }
    
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val httpClient = OkHttpClient.Builder().build()
    
    private val _updateStatus = MutableStateFlow(OTAUpdateStatus())
    val updateStatus: StateFlow<OTAUpdateStatus> = _updateStatus.asStateFlow()
    
    private val _firmwareInfo = MutableStateFlow<FirmwareInfo?>(null)
    val firmwareInfo: StateFlow<FirmwareInfo?> = _firmwareInfo.asStateFlow()
    
    private val _availableFirmwares = MutableStateFlow<List<FirmwareFile>>(emptyList())
    val availableFirmwares: StateFlow<List<FirmwareFile>> = _availableFirmwares.asStateFlow()
    
    private var updateJob: Job? = null
    
    init {
        loadLocalFirmwares()
    }
    
    /**
     * Check for firmware updates
     */
    suspend fun checkForUpdates(): Result<FirmwareInfo> {
        _updateStatus.value = _updateStatus.value.copy(
            state = OTAState.CHECKING,
            message = "Checking for updates..."
        )
        
        return try {
            val result = esp8266Client.getFirmwareInfo()
            if (result.isSuccess) {
                val info = result.getOrNull()!!
                _firmwareInfo.value = info
                _updateStatus.value = _updateStatus.value.copy(
                    state = OTAState.IDLE,
                    message = if (info.updateAvailable) {
                        "Update available: v${info.latestVersion}"
                    } else {
                        "Firmware is up to date"
                    }
                )
                Result.success(info)
            } else {
                _updateStatus.value = _updateStatus.value.copy(
                    state = OTAState.IDLE,
                    error = "Failed to check for updates"
                )
                Result.failure(result.exceptionOrNull() ?: Exception("Unknown error"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Check for updates failed", e)
            _updateStatus.value = _updateStatus.value.copy(
                state = OTAState.FAILED,
                error = e.message
            )
            Result.failure(e)
        }
    }
    
    /**
     * Download firmware from URL
     */
    suspend fun downloadFirmware(url: String, version: String): Result<FirmwareFile> {
        _updateStatus.value = _updateStatus.value.copy(
            state = OTAState.DOWNLOADING,
            progress = 0,
            message = "Downloading firmware..."
        )
        
        return try {
            val request = Request.Builder().url(url).build()
            val response = httpClient.newCall(request).execute()
            
            if (!response.isSuccessful) {
                throw Exception("Download failed: ${response.code}")
            }
            
            val body = response.body ?: throw Exception("Empty response")
            val contentLength = body.contentLength()
            
            // Create firmware directory
            val firmwareDir = File(context.filesDir, FIRMWARE_DIR)
            if (!firmwareDir.exists()) {
                firmwareDir.mkdirs()
            }
            
            val fileName = "firmware_v$version.bin"
            val firmwareFile = File(firmwareDir, fileName)
            
            // Download with progress
            FileOutputStream(firmwareFile).use { output ->
                val buffer = ByteArray(CHUNK_SIZE)
                var bytesRead: Int
                var totalBytesRead = 0L
                
                body.byteStream().use { input ->
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        totalBytesRead += bytesRead
                        
                        val progress = if (contentLength > 0) {
                            ((totalBytesRead * 100) / contentLength).toInt()
                        } else {
                            -1
                        }
                        
                        _updateStatus.value = _updateStatus.value.copy(
                            progress = progress,
                            message = "Downloading: $progress%"
                        )
                    }
                }
            }
            
            // Calculate checksum
            val checksum = calculateMD5(firmwareFile)
            
            val firmware = FirmwareFile(
                name = fileName,
                path = firmwareFile.absolutePath,
                size = firmwareFile.length(),
                version = version,
                checksum = checksum
            )
            
            _updateStatus.value = _updateStatus.value.copy(
                state = OTAState.IDLE,
                progress = 100,
                message = "Download complete"
            )
            
            loadLocalFirmwares()
            Result.success(firmware)
            
        } catch (e: Exception) {
            Log.e(TAG, "Download failed", e)
            _updateStatus.value = _updateStatus.value.copy(
                state = OTAState.FAILED,
                error = e.message
            )
            Result.failure(e)
        }
    }
    
    /**
     * Upload firmware to ESP8266
     */
    suspend fun uploadFirmware(firmwareFile: FirmwareFile): Result<Boolean> {
        updateJob = scope.launch {
            try {
                _updateStatus.value = OTAUpdateStatus(
                    state = OTAState.UPLOADING,
                    progress = 0,
                    message = "Preparing firmware...",
                    startTime = System.currentTimeMillis()
                )
                
                val file = File(firmwareFile.path)
                if (!file.exists()) {
                    throw Exception("Firmware file not found")
                }
                
                val firmwareBytes = file.readBytes()
                val totalSize = firmwareBytes.size
                
                _updateStatus.value = _updateStatus.value.copy(
                    message = "Uploading firmware to device..."
                )
                
                // Upload firmware
                val result = esp8266Client.uploadFirmware(firmwareBytes)
                
                if (result.isSuccess) {
                    _updateStatus.value = _updateStatus.value.copy(
                        state = OTAState.INSTALLING,
                        progress = 100,
                        message = "Installing firmware..."
                    )
                    
                    // Monitor installation progress
                    monitorInstallation()
                } else {
                    throw result.exceptionOrNull() ?: Exception("Upload failed")
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "Upload failed", e)
                _updateStatus.value = _updateStatus.value.copy(
                    state = OTAState.FAILED,
                    error = e.message
                )
            }
        }
        
        updateJob?.join()
        return if (_updateStatus.value.state == OTAState.COMPLETED) {
            Result.success(true)
        } else {
            Result.failure(Exception(_updateStatus.value.error))
        }
    }
    
    /**
     * Start OTA update from URL
     */
    suspend fun startOTAUpdate(firmwareUrl: String, version: String): Result<Boolean> {
        updateJob = scope.launch {
            try {
                _updateStatus.value = OTAUpdateStatus(
                    state = OTAState.UPLOADING,
                    message = "Starting OTA update...",
                    startTime = System.currentTimeMillis()
                )
                
                val result = esp8266Client.startOTAUpdate(firmwareUrl, version)
                
                if (result.isSuccess) {
                    _updateStatus.value = _updateStatus.value.copy(
                        state = OTAState.INSTALLING,
                        message = "Installing firmware on device..."
                    )
                    
                    // Monitor the update progress
                    monitorInstallation()
                } else {
                    throw result.exceptionOrNull() ?: Exception("OTA update failed")
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "OTA update failed", e)
                _updateStatus.value = _updateStatus.value.copy(
                    state = OTAState.FAILED,
                    error = e.message
                )
            }
        }
        
        updateJob?.join()
        return if (_updateStatus.value.state == OTAState.COMPLETED) {
            Result.success(true)
        } else {
            Result.failure(Exception(_updateStatus.value.error))
        }
    }
    
    /**
     * Monitor installation progress
     */
    private suspend fun monitorInstallation() {
        var attempts = 0
        val maxAttempts = 60 // 60 seconds timeout
        
        while (attempts < maxAttempts) {
            delay(1000)
            
            try {
                val result = esp8266Client.getOTAStatus()
                if (result.isSuccess) {
                    val status = result.getOrNull()!!
                    
                    _updateStatus.value = _updateStatus.value.copy(
                        progress = status.progress,
                        message = status.message
                    )
                    
                    when (status.state.lowercase()) {
                        "completed", "success" -> {
                            _updateStatus.value = _updateStatus.value.copy(
                                state = OTAState.COMPLETED,
                                progress = 100,
                                message = "Update completed successfully!"
                            )
                            return
                        }
                        "failed", "error" -> {
                            _updateStatus.value = _updateStatus.value.copy(
                                state = OTAState.FAILED,
                                error = status.message
                            )
                            return
                        }
                        "rebooting" -> {
                            _updateStatus.value = _updateStatus.value.copy(
                                state = OTAState.REBOOTING,
                                message = "Device is rebooting..."
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                // Device might be rebooting
                Log.d(TAG, "Status check failed (device may be rebooting): ${e.message}")
            }
            
            attempts++
        }
        
        // If we reach here, assume success if device was rebooting
        if (_updateStatus.value.state == OTAState.REBOOTING) {
            _updateStatus.value = _updateStatus.value.copy(
                state = OTAState.COMPLETED,
                message = "Update completed. Device rebooted."
            )
        } else {
            _updateStatus.value = _updateStatus.value.copy(
                state = OTAState.FAILED,
                error = "Update timed out"
            )
        }
    }
    
    /**
     * Cancel ongoing update
     */
    fun cancelUpdate() {
        updateJob?.cancel()
        _updateStatus.value = OTAUpdateStatus(
            state = OTAState.IDLE,
            message = "Update cancelled"
        )
    }
    
    /**
     * Load locally stored firmware files
     */
    private fun loadLocalFirmwares() {
        val firmwareDir = File(context.filesDir, FIRMWARE_DIR)
        if (!firmwareDir.exists()) {
            _availableFirmwares.value = emptyList()
            return
        }
        
        val firmwares = firmwareDir.listFiles()?.filter { it.extension == "bin" }?.map { file ->
            val version = file.nameWithoutExtension
                .replace("firmware_v", "")
                .replace("firmware_", "")
            
            FirmwareFile(
                name = file.name,
                path = file.absolutePath,
                size = file.length(),
                version = version,
                checksum = calculateMD5(file),
                createdAt = file.lastModified()
            )
        }?.sortedByDescending { it.createdAt } ?: emptyList()
        
        _availableFirmwares.value = firmwares
    }
    
    /**
     * Delete local firmware file
     */
    fun deleteFirmware(firmwareFile: FirmwareFile): Boolean {
        val file = File(firmwareFile.path)
        val deleted = file.delete()
        if (deleted) {
            loadLocalFirmwares()
        }
        return deleted
    }
    
    /**
     * Calculate MD5 checksum
     */
    private fun calculateMD5(file: File): String {
        val md = MessageDigest.getInstance("MD5")
        file.inputStream().use { input ->
            val buffer = ByteArray(8192)
            var bytesRead: Int
            while (input.read(buffer).also { bytesRead = it } != -1) {
                md.update(buffer, 0, bytesRead)
            }
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }
    
    /**
     * Verify firmware checksum
     */
    fun verifyChecksum(firmwareFile: FirmwareFile, expectedChecksum: String): Boolean {
        val file = File(firmwareFile.path)
        if (!file.exists()) return false
        
        val actualChecksum = calculateMD5(file)
        return actualChecksum.equals(expectedChecksum, ignoreCase = true)
    }
    
    /**
     * Get firmware file size formatted
     */
    fun formatFileSize(bytes: Long): String {
        return when {
            bytes >= 1024 * 1024 -> "%.2f MB".format(bytes / (1024.0 * 1024.0))
            bytes >= 1024 -> "%.2f KB".format(bytes / 1024.0)
            else -> "$bytes bytes"
        }
    }
    
    /**
     * Reset status
     */
    fun resetStatus() {
        _updateStatus.value = OTAUpdateStatus()
    }
    
    /**
     * Clean up resources
     */
    fun cleanup() {
        updateJob?.cancel()
        scope.cancel()
    }
}

