package com.example.tamper_detection.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.tamper_detection.data.models.*
import com.example.tamper_detection.ui.components.*
import com.example.tamper_detection.ui.theme.*
import com.example.tamper_detection.viewmodel.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FirmwareScreen(
    viewModel: MainViewModel,
    onNavigateBack: () -> Unit
) {
    val firmwareInfo by viewModel.firmwareInfo.collectAsState()
    val updateStatus by viewModel.updateStatus.collectAsState()
    val availableFirmwares by viewModel.availableFirmwares.collectAsState()
    val uiState by viewModel.uiState.collectAsState()
    
    var showUpdateDialog by remember { mutableStateOf(false) }
    var showUrlDialog by remember { mutableStateOf(false) }
    var firmwareUrl by remember { mutableStateOf("") }
    var firmwareVersion by remember { mutableStateOf("") }
    
    LaunchedEffect(Unit) {
        viewModel.checkForUpdates()
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Firmware Update",
                        fontWeight = FontWeight.SemiBold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.checkForUpdates() }) {
                        Icon(
                            Icons.Default.Refresh,
                            contentDescription = "Check Updates",
                            tint = TextSecondary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = DarkBackground,
                    titleContentColor = TextPrimary,
                    navigationIconContentColor = TextPrimary
                )
            )
        },
        containerColor = DarkBackground
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Spacer(modifier = Modifier.height(8.dp))
                
                // Current firmware info
                CurrentFirmwareCard(firmwareInfo = firmwareInfo)
            }
            
            // Update status
            if (updateStatus.state != OTAState.IDLE) {
                item {
                    UpdateProgressCard(
                        updateStatus = updateStatus,
                        onCancel = { viewModel.cancelOTAUpdate() }
                    )
                }
            }
            
            // Available update
            firmwareInfo?.let { info ->
                if (info.updateAvailable) {
                    item {
                        AvailableUpdateCard(
                            firmwareInfo = info,
                            onUpdate = { showUpdateDialog = true }
                        )
                    }
                }
            }
            
            item {
                // Manual update section
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = DarkSurfaceVariant
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Text(
                            text = "Manual Update",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = TextPrimary
                        )
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        Text(
                            text = "Update firmware from URL or local file",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary
                        )
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            OutlinedButton(
                                onClick = { showUrlDialog = true },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = Primary
                                )
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Link,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("From URL")
                            }
                        }
                    }
                }
            }
            
            // Local firmware files
            if (availableFirmwares.isNotEmpty()) {
                item {
                    SectionHeader(title = "Local Firmware Files")
                }
                
                items(availableFirmwares) { firmware ->
                    LocalFirmwareCard(
                        firmware = firmware,
                        onUpload = { viewModel.uploadFirmware(firmware) },
                        onDelete = { viewModel.deleteFirmware(firmware) }
                    )
                }
            }
            
            item {
                // Device actions
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = DarkSurfaceVariant
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Text(
                            text = "Device Actions",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = TextPrimary
                        )
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        OutlinedButton(
                            onClick = { viewModel.restartDevice() },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = Warning
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Default.RestartAlt,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Restart Device")
                        }
                    }
                }
            }
            
            item {
                Spacer(modifier = Modifier.height(80.dp))
            }
        }
        
        // Update confirmation dialog
        if (showUpdateDialog) {
            firmwareInfo?.let { info ->
                AlertDialog(
                    onDismissRequest = { showUpdateDialog = false },
                    title = { Text("Update Firmware") },
                    text = {
                        Column {
                            Text("Update to version ${info.latestVersion}?")
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "The device will restart during the update process.",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary
                            )
                            if (info.changelog.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = "Changelog:",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = info.changelog,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextSecondary
                                )
                            }
                        }
                    },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                viewModel.startOTAUpdate(info.downloadUrl, info.latestVersion)
                                showUpdateDialog = false
                            }
                        ) {
                            Text("Update")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showUpdateDialog = false }) {
                            Text("Cancel")
                        }
                    },
                    containerColor = DarkSurface
                )
            }
        }
        
        // URL update dialog
        if (showUrlDialog) {
            AlertDialog(
                onDismissRequest = { showUrlDialog = false },
                title = { Text("Update from URL") },
                text = {
                    Column {
                        OutlinedTextField(
                            value = firmwareUrl,
                            onValueChange = { firmwareUrl = it },
                            label = { Text("Firmware URL") },
                            placeholder = { Text("https://...") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        OutlinedTextField(
                            value = firmwareVersion,
                            onValueChange = { firmwareVersion = it },
                            label = { Text("Version") },
                            placeholder = { Text("e.g., 2.0.0") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            if (firmwareUrl.isNotEmpty() && firmwareVersion.isNotEmpty()) {
                                viewModel.startOTAUpdate(firmwareUrl, firmwareVersion)
                                showUrlDialog = false
                                firmwareUrl = ""
                                firmwareVersion = ""
                            }
                        }
                    ) {
                        Text("Update")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showUrlDialog = false }) {
                        Text("Cancel")
                    }
                },
                containerColor = DarkSurface
            )
        }
        
        LoadingOverlay(isLoading = uiState.isLoading)
    }
}

@Composable
private fun CurrentFirmwareCard(
    firmwareInfo: FirmwareInfo?
) {
    GlowCard(glowColor = Primary) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .background(
                        brush = Brush.linearGradient(
                            colors = listOf(Primary, Accent)
                        ),
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Memory,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(32.dp)
                )
            }
            
            Spacer(modifier = Modifier.width(16.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Current Firmware",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )
                Text(
                    text = "v${firmwareInfo?.currentVersion ?: "Unknown"}",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
            }
            
            if (firmwareInfo?.updateAvailable == true) {
                Card(
                    shape = RoundedCornerShape(8.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = Success.copy(alpha = 0.2f)
                    )
                ) {
                    Text(
                        text = "Update Available",
                        style = MaterialTheme.typography.labelSmall,
                        color = Success,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun UpdateProgressCard(
    updateStatus: OTAUpdateStatus,
    onCancel: () -> Unit
) {
    val statusColor = when (updateStatus.state) {
        OTAState.COMPLETED -> Success
        OTAState.FAILED -> Error
        else -> Primary
    }
    
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = statusColor.copy(alpha = 0.15f)
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    when (updateStatus.state) {
                        OTAState.COMPLETED -> Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = Success
                        )
                        OTAState.FAILED -> Icon(
                            imageVector = Icons.Default.Error,
                            contentDescription = null,
                            tint = Error
                        )
                        else -> CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 2.dp,
                            color = Primary
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = updateStatus.state.name.replace("_", " "),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary
                    )
                }
                
                if (updateStatus.state !in listOf(OTAState.COMPLETED, OTAState.FAILED, OTAState.IDLE)) {
                    IconButton(onClick = onCancel) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Cancel",
                            tint = Error
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            Text(
                text = updateStatus.message,
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary
            )
            
            if (updateStatus.progress > 0) {
                Spacer(modifier = Modifier.height(12.dp))
                
                LinearProgressIndicator(
                    progress = { updateStatus.progress / 100f },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp),
                    color = statusColor,
                    trackColor = DarkSurface,
                )
                
                Spacer(modifier = Modifier.height(4.dp))
                
                Text(
                    text = "${updateStatus.progress}%",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextTertiary
                )
            }
            
            updateStatus.error?.let { error ->
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = error,
                    style = MaterialTheme.typography.bodySmall,
                    color = Error
                )
            }
        }
    }
}

@Composable
private fun AvailableUpdateCard(
    firmwareInfo: FirmwareInfo,
    onUpdate: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = Success.copy(alpha = 0.1f)
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "New Version Available",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = Success
                    )
                    Text(
                        text = "v${firmwareInfo.latestVersion}",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                }
                
                GradientButton(
                    text = "Update",
                    onClick = onUpdate,
                    icon = Icons.Default.Download,
                    gradient = Brush.horizontalGradient(
                        colors = listOf(Success, Primary)
                    )
                )
            }
            
            if (firmwareInfo.changelog.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                Divider(color = DarkSurfaceVariant)
                Spacer(modifier = Modifier.height(12.dp))
                
                Text(
                    text = "What's new:",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = TextSecondary
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = firmwareInfo.changelog,
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextPrimary
                )
            }
            
            if (firmwareInfo.firmwareSize > 0) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Size: ${formatFileSize(firmwareInfo.firmwareSize)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextTertiary
                )
            }
        }
    }
}

@Composable
private fun LocalFirmwareCard(
    firmware: FirmwareFile,
    onUpload: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = DarkSurfaceVariant
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(Accent.copy(alpha = 0.2f), RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.InsertDriveFile,
                    contentDescription = null,
                    tint = Accent,
                    modifier = Modifier.size(22.dp)
                )
            }
            
            Spacer(modifier = Modifier.width(12.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = firmware.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = TextPrimary
                )
                Row {
                    Text(
                        text = "v${firmware.version}",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                    Text(
                        text = " • ${formatFileSize(firmware.size)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextTertiary
                    )
                }
            }
            
            IconButton(onClick = onUpload) {
                Icon(
                    imageVector = Icons.Default.Upload,
                    contentDescription = "Upload",
                    tint = Primary
                )
            }
            
            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Delete",
                    tint = Error
                )
            }
        }
    }
}

private fun formatFileSize(bytes: Long): String {
    return when {
        bytes >= 1024 * 1024 -> "%.2f MB".format(bytes / (1024.0 * 1024.0))
        bytes >= 1024 -> "%.2f KB".format(bytes / 1024.0)
        else -> "$bytes bytes"
    }
}

