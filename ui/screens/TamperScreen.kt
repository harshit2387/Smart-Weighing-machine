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
import com.example.tamper_detection.ml.TamperDetector
import com.example.tamper_detection.ui.components.*
import com.example.tamper_detection.ui.theme.*
import com.example.tamper_detection.viewmodel.MainViewModel
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TamperScreen(
    viewModel: MainViewModel,
    onNavigateBack: () -> Unit
) {
    val tamperState by viewModel.tamperState.collectAsState()
    val alerts by viewModel.alerts.collectAsState()
    val lightData by viewModel.lightData.collectAsState()
    val settings by viewModel.settings.collectAsState()
    
    var showThresholdDialog by remember { mutableStateOf(false) }
    var thresholdValue by remember { mutableStateOf(settings.lightSensorConfig.tamperThreshold.toString()) }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Security & Tamper Detection",
                        fontWeight = FontWeight.SemiBold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (alerts.isNotEmpty()) {
                        IconButton(onClick = { viewModel.clearAlerts() }) {
                            Icon(
                                Icons.Default.DeleteSweep,
                                contentDescription = "Clear All",
                                tint = TextSecondary
                            )
                        }
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
                
                // Status Card
                TamperStatusCard(
                    tamperState = tamperState,
                    lightData = lightData
                )
            }
            
            item {
                // Light Sensor Card
                LightSensorCard(
                    lightData = lightData,
                    threshold = settings.lightSensorConfig.tamperThreshold,
                    onEditThreshold = { showThresholdDialog = true }
                )
            }
            
            item {
                // ML Detection Status
                MLDetectionCard(
                    isEnabled = settings.enableMLDetection,
                    tamperState = tamperState,
                    onToggle = { viewModel.toggleMLDetection(it) }
                )
            }
            
            item {
                SectionHeader(
                    title = "Alert History",
                    action = {
                        Text(
                            text = "${alerts.size} alerts",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextTertiary
                        )
                    }
                )
            }
            
            if (alerts.isEmpty()) {
                item {
                    EmptyState(
                        icon = Icons.Default.Security,
                        title = "No Alerts",
                        message = "Your device is secure. No tamper alerts have been detected."
                    )
                }
            } else {
                items(alerts, key = { it.id }) { alert ->
                    AlertDetailCard(
                        alert = alert,
                        onAcknowledge = { viewModel.acknowledgeAlert(alert.id) }
                    )
                }
            }
            
            item {
                Spacer(modifier = Modifier.height(80.dp))
            }
        }
        
        // Threshold Dialog
        if (showThresholdDialog) {
            AlertDialog(
                onDismissRequest = { showThresholdDialog = false },
                title = { Text("Light Threshold") },
                text = {
                    Column {
                        Text(
                            text = "Set the light level threshold for tamper detection:",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        OutlinedTextField(
                            value = thresholdValue,
                            onValueChange = { thresholdValue = it },
                            label = { Text("Threshold (lux)") },
                            singleLine = true
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Lower values = more sensitive",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextTertiary
                        )
                    }
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            thresholdValue.toFloatOrNull()?.let {
                                viewModel.setLightThreshold(it)
                            }
                            showThresholdDialog = false
                        }
                    ) {
                        Text("Save")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showThresholdDialog = false }) {
                        Text("Cancel")
                    }
                },
                containerColor = DarkSurface
            )
        }
    }
}

@Composable
private fun TamperStatusCard(
    tamperState: TamperDetector.TamperState,
    lightData: LightSensorData
) {
    val isSecure = !tamperState.isTamperingDetected && !lightData.isTampered
    val statusColor = if (isSecure) Success else Error
    val statusIcon = if (isSecure) Icons.Default.Shield else Icons.Default.Warning
    val statusText = if (isSecure) "SECURE" else "ALERT"
    val statusMessage = if (isSecure) {
        "No tampering detected"
    } else {
        tamperState.message.ifEmpty { "Tampering detected!" }
    }
    
    GlowCard(glowColor = statusColor) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .background(
                        color = statusColor.copy(alpha = 0.2f),
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = statusIcon,
                    contentDescription = null,
                    tint = statusColor,
                    modifier = Modifier.size(36.dp)
                )
            }
            
            Spacer(modifier = Modifier.width(16.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = statusText,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = statusColor
                )
                Text(
                    text = statusMessage,
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )
                if (tamperState.isTamperingDetected) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Confidence: ${(tamperState.confidence * 100).toInt()}%",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextTertiary
                    )
                }
            }
        }
    }
}

@Composable
private fun LightSensorCard(
    lightData: LightSensorData,
    threshold: Float,
    onEditThreshold: () -> Unit
) {
    val progress = (lightData.lightLevel / threshold).coerceIn(0f, 1.5f)
    val progressColor = when {
        lightData.isTampered -> Error
        lightData.lightLevel > threshold * 0.7f -> Warning
        else -> Success
    }
    
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = DarkSurfaceVariant
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
                    Icon(
                        imageVector = Icons.Default.LightMode,
                        contentDescription = null,
                        tint = progressColor,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Light Sensor",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary
                    )
                }
                
                OutlinedButton(
                    onClick = onEditThreshold,
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = Primary
                    )
                ) {
                    Text("Threshold: ${threshold.toInt()}")
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Light level display
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                Column {
                    Text(
                        text = "Current Level",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextTertiary
                    )
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            text = "%.0f".format(lightData.lightLevel),
                            style = MaterialTheme.typography.headlineLarge,
                            fontWeight = FontWeight.Bold,
                            color = progressColor
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "lux",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                    }
                }
                
                if (lightData.isTampered) {
                    Card(
                        shape = RoundedCornerShape(8.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = Error.copy(alpha = 0.2f)
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = null,
                                tint = Error,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "TAMPERED",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = Error
                            )
                        }
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Progress bar
            LinearProgressIndicator(
                progress = { progress.coerceAtMost(1f) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp),
                color = progressColor,
                trackColor = DarkSurface,
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "0",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextTertiary
                )
                Text(
                    text = "Threshold: ${threshold.toInt()}",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextTertiary
                )
            }
        }
    }
}

@Composable
private fun MLDetectionCard(
    isEnabled: Boolean,
    tamperState: TamperDetector.TamperState,
    onToggle: (Boolean) -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = DarkSurfaceVariant
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
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .background(Accent.copy(alpha = 0.2f), RoundedCornerShape(10.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Psychology,
                            contentDescription = null,
                            tint = Accent,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "ML Detection",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = TextPrimary
                        )
                        Text(
                            text = "Anomaly detection using AI",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                    }
                }
                
                Switch(
                    checked = isEnabled,
                    onCheckedChange = onToggle,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = Accent
                    )
                )
            }
            
            if (isEnabled) {
                Spacer(modifier = Modifier.height(12.dp))
                Divider(color = DarkSurface)
                Spacer(modifier = Modifier.height(12.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceAround
                ) {
                    MLStatItem(
                        label = "Weight Anomaly",
                        isActive = tamperState.weightAnomaly,
                        color = if (tamperState.weightAnomaly) Error else Success
                    )
                    MLStatItem(
                        label = "Light Anomaly",
                        isActive = tamperState.lightAnomaly,
                        color = if (tamperState.lightAnomaly) Error else Success
                    )
                }
            }
        }
    }
}

@Composable
private fun MLStatItem(
    label: String,
    isActive: Boolean,
    color: Color
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .background(color.copy(alpha = 0.2f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (isActive) Icons.Default.Warning else Icons.Default.CheckCircle,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(18.dp)
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondary
        )
    }
}

@Composable
private fun AlertDetailCard(
    alert: TamperAlert,
    onAcknowledge: () -> Unit
) {
    val severityColor = when (alert.severity) {
        AlertSeverity.CRITICAL -> TamperCritical
        AlertSeverity.HIGH -> TamperHigh
        AlertSeverity.MEDIUM -> TamperMedium
        AlertSeverity.LOW -> TamperLow
    }
    
    val typeIcon = when (alert.type) {
        TamperType.LIGHT_DETECTED -> Icons.Default.LightMode
        TamperType.WEIGHT_ANOMALY -> Icons.Default.Scale
        TamperType.DEVICE_MOVED -> Icons.Default.Moving
        TamperType.CONNECTION_LOST -> Icons.Default.WifiOff
        TamperType.UNKNOWN -> Icons.Default.Help
    }
    
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
            verticalAlignment = Alignment.Top
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(severityColor.copy(alpha = 0.2f), RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = typeIcon,
                    contentDescription = null,
                    tint = severityColor,
                    modifier = Modifier.size(22.dp)
                )
            }
            
            Spacer(modifier = Modifier.width(12.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = alert.type.name.replace("_", " "),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary
                    )
                    
                    Card(
                        shape = RoundedCornerShape(6.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = severityColor.copy(alpha = 0.2f)
                        )
                    ) {
                        Text(
                            text = alert.severity.name,
                            style = MaterialTheme.typography.labelSmall,
                            color = severityColor,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(4.dp))
                
                Text(
                    text = alert.message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = formatTimestamp(alert.timestamp),
                        style = MaterialTheme.typography.bodySmall,
                        color = TextTertiary
                    )
                    
                    if (!alert.isAcknowledged) {
                        TextButton(
                            onClick = onAcknowledge,
                            colors = ButtonDefaults.textButtonColors(
                                contentColor = Primary
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Acknowledge")
                        }
                    } else {
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = Success,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Acknowledged",
                                style = MaterialTheme.typography.bodySmall,
                                color = Success
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun formatTimestamp(timestamp: Long): String {
    val sdf = SimpleDateFormat("MMM dd, HH:mm:ss", Locale.getDefault())
    return sdf.format(Date(timestamp))
}

