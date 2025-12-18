package com.example.tamper_detection.data.models

import com.google.gson.annotations.SerializedName

/**
 * Firmware information from ESP8266
 */
data class FirmwareInfo(
    @SerializedName("current_version")
    val currentVersion: String = "1.0.0",
    
    @SerializedName("latest_version")
    val latestVersion: String = "1.0.0",
    
    @SerializedName("update_available")
    val updateAvailable: Boolean = false,
    
    @SerializedName("firmware_size")
    val firmwareSize: Long = 0L,
    
    @SerializedName("changelog")
    val changelog: String = "",
    
    @SerializedName("download_url")
    val downloadUrl: String = "",
    
    @SerializedName("checksum")
    val checksum: String = "",
    
    @SerializedName("release_date")
    val releaseDate: String = ""
)

/**
 * OTA Update status
 */
data class OTAUpdateStatus(
    val state: OTAState = OTAState.IDLE,
    val progress: Int = 0,
    val message: String = "",
    val error: String? = null,
    val startTime: Long = 0L,
    val estimatedTimeRemaining: Long = 0L
)

enum class OTAState {
    IDLE,
    CHECKING,
    DOWNLOADING,
    UPLOADING,
    INSTALLING,
    VERIFYING,
    COMPLETED,
    FAILED,
    REBOOTING
}

/**
 * OTA Update request
 */
data class OTAUpdateRequest(
    @SerializedName("firmware_url")
    val firmwareUrl: String,
    
    @SerializedName("version")
    val version: String,
    
    @SerializedName("checksum")
    val checksum: String = "",
    
    @SerializedName("force_update")
    val forceUpdate: Boolean = false
)

/**
 * OTA Update response from ESP8266
 */
data class OTAUpdateResponse(
    @SerializedName("success")
    val success: Boolean = false,
    
    @SerializedName("message")
    val message: String = "",
    
    @SerializedName("progress")
    val progress: Int = 0,
    
    @SerializedName("state")
    val state: String = "idle"
)

/**
 * Firmware file for local storage
 */
data class FirmwareFile(
    val name: String,
    val path: String,
    val size: Long,
    val version: String,
    val checksum: String,
    val createdAt: Long = System.currentTimeMillis()
)

