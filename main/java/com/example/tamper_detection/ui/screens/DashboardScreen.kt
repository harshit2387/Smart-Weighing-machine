package com.example.tamper_detection.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.tamper_detection.data.models.*
import com.example.tamper_detection.ml.TamperDetector
import com.example.tamper_detection.network.ESP8266Client
import com.example.tamper_detection.ui.components.*
import com.example.tamper_detection.ui.theme.*
import com.example.tamper_detection.viewmodel.MainViewModel

@Composable
fun DashboardScreen(
    viewModel: MainViewModel,
    onNavigateToWeight: () -> Unit,
    onNavigateToTamper: () -> Unit,
    onNavigateToFirmware: () -> Unit,
    onNavigateToSettings: () -> Unit
) {
    val connectionState by viewModel.connectionState.collectAsState()
    val sensorData by viewModel.sensorData.collectAsState()
    val weightData by viewModel.weightData.collectAsState()
    val lightData by viewModel.lightData.collectAsState()
    val tamperState by viewModel.tamperState.collectAsState()
    val alerts by viewModel.alerts.collectAsState()
    val uiState by viewModel.uiState.collectAsState()
    val settings by viewModel.settings.collectAsState()
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(DarkBackground, DarkSurface)
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            // Header
            DashboardHeader(
                connectionState = connectionState,
                onSettingsClick = onNavigateToSettings
            )
            
            Spacer(modifier = Modifier.height(20.dp))
            
            // Tamper Alert Banner
            AnimatedVisibility(
                visible = tamperState.isTamperingDetected,
                enter = slideInVertically() + fadeIn(),
                exit = slideOutVertically() + fadeOut()
            ) {
                TamperAlertCard(
                    tamperState = tamperState,
                    onClick = onNavigateToTamper
                )
                Spacer(modifier = Modifier.height(16.dp))
            }
            
            // Main Weight Display
            MainWeightCard(
                weight = weightData.weight,
                unit = settings.weightUnit.symbol,
                isStable = weightData.isStable,
                isConnected = connectionState == ESP8266Client.ConnectionState.CONNECTED,
                onTare = { viewModel.tareScale() },
                onClick = onNavigateToWeight
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Quick Stats Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                QuickStatCard(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Default.LightMode,
                    title = "Light Level",
                    value = "%.0f".format(lightData.lightLevel),
                    unit = "lux",
                    color = if (lightData.isTampered) Error else Primary
                )
                
                QuickStatCard(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Default.Wifi,
                    title = "Signal",
                    value = "${sensorData.wifiRssi}",
                    unit = "dBm",
                    color = getSignalColor(sensorData.wifiRssi)
                )
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Device Info Card
            DeviceInfoCard(
                sensorData = sensorData,
                connectionState = connectionState,
                onConnect = { viewModel.connect() },
                onDisconnect = { viewModel.disconnect() }
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Quick Actions
            SectionHeader(title = "Quick Actions")
            Spacer(modifier = Modifier.height(12.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                ActionButton(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Default.Scale,
                    label = "Weight",
                    onClick = onNavigateToWeight
                )
                ActionButton(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Default.Security,
                    label = "Security",
                    badgeCount = alerts.count { !it.isAcknowledged },
                    onClick = onNavigateToTamper
                )
                ActionButton(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Default.SystemUpdate,
                    label = "Firmware",
                    onClick = onNavigateToFirmware
                )
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // Recent Alerts
            if (alerts.isNotEmpty()) {
                SectionHeader(
                    title = "Recent Alerts",
                    action = {
                        TextButton(onClick = onNavigateToTamper) {
                            Text("View All", color = Primary)
                        }
                    }
                )
                Spacer(modifier = Modifier.height(12.dp))
                
                alerts.take(3).forEach { alert ->
                    AlertItem(
                        alert = alert,
                        onAcknowledge = { viewModel.acknowledgeAlert(alert.id) }
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
            
            Spacer(modifier = Modifier.height(80.dp))
        }
        
        // Loading overlay
        LoadingOverlay(isLoading = uiState.isLoading)
    }
}

@Composable
private fun DashboardHeader(
    connectionState: ESP8266Client.ConnectionState,
    onSettingsClick: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(
                text = "WeightSmart",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
            Text(
                text = "Tamper Detection System",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary
            )
        }
        
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            StatusIndicator(status = connectionState)
            
            IconButton(
                onClick = onSettingsClick,
                modifier = Modifier
                    .size(40.dp)
                    .background(
                        color = DarkSurfaceVariant,
                        shape = CircleShape
                    )
            ) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = "Settings",
                    tint = TextSecondary
                )
            }
        }
    }
}

@Composable
private fun TamperAlertCard(
    tamperState: TamperDetector.TamperState,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = Error.copy(alpha = 0.15f)
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
                    .size(48.dp)
                    .background(Error.copy(alpha = 0.2f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    tint = Error,
                    modifier = Modifier.size(28.dp)
                )
            }
            
            Spacer(modifier = Modifier.width(16.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "TAMPER ALERT",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = Error
                )
                Text(
                    text = tamperState.message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextPrimary
                )
            }
            
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = Error
            )
        }
    }
}

@Composable
private fun MainWeightCard(
    weight: Float,
    unit: String,
    isStable: Boolean,
    isConnected: Boolean,
    onTare: () -> Unit,
    onClick: () -> Unit
) {
    GlowCard(
        glowColor = if (isStable) Primary else Warning
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Current Weight",
                    style = MaterialTheme.typography.titleMedium,
                    color = TextSecondary
                )
                
                if (isConnected) {
                    FilledTonalButton(
                        onClick = onTare,
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = Primary.copy(alpha = 0.2f)
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Tare")
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            WeightDisplay(
                weight = weight,
                unit = unit,
                isStable = isStable
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            if (!isConnected) {
                Text(
                    text = "Connect to device to see weight",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextTertiary
                )
            }
        }
    }
}

@Composable
private fun QuickStatCard(
    modifier: Modifier = Modifier,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    value: String,
    unit: String,
    color: Color
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = DarkSurfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                verticalAlignment = Alignment.Bottom
            ) {
                Text(
                    text = value,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = unit,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
            }
        }
    }
}

@Composable
private fun DeviceInfoCard(
    sensorData: ESP8266SensorData,
    connectionState: ESP8266Client.ConnectionState,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit
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
                Text(
                    text = "Device Status",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary
                )
                
                when (connectionState) {
                    ESP8266Client.ConnectionState.CONNECTED -> {
                        TextButton(onClick = onDisconnect) {
                            Text("Disconnect", color = Error)
                        }
                    }
                    ESP8266Client.ConnectionState.DISCONNECTED,
                    ESP8266Client.ConnectionState.ERROR -> {
                        TextButton(onClick = onConnect) {
                            Text("Connect", color = Primary)
                        }
                    }
                    else -> {}
                }
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                DeviceInfoItem(
                    label = "Firmware",
                    value = sensorData.firmwareVersion
                )
                DeviceInfoItem(
                    label = "Uptime",
                    value = formatUptime(sensorData.uptime)
                )
                DeviceInfoItem(
                    label = "Free Memory",
                    value = "${sensorData.freeHeap / 1024} KB"
                )
            }
        }
    }
}

@Composable
private fun DeviceInfoItem(
    label: String,
    value: String
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = TextTertiary
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = TextPrimary
        )
    }
}

@Composable
private fun ActionButton(
    modifier: Modifier = Modifier,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    badgeCount: Int = 0,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = DarkSurfaceVariant
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .background(Primary.copy(alpha = 0.15f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = Primary,
                        modifier = Modifier.size(24.dp)
                    )
                }
                
                if (badgeCount > 0) {
                    Badge(
                        modifier = Modifier.align(Alignment.TopEnd),
                        containerColor = Error
                    ) {
                        Text(
                            text = badgeCount.toString(),
                            color = Color.White,
                            fontSize = 10.sp
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = TextPrimary
            )
        }
    }
}

@Composable
private fun AlertItem(
    alert: TamperAlert,
    onAcknowledge: () -> Unit
) {
    val severityColor = when (alert.severity) {
        AlertSeverity.CRITICAL -> TamperCritical
        AlertSeverity.HIGH -> TamperHigh
        AlertSeverity.MEDIUM -> TamperMedium
        AlertSeverity.LOW -> TamperLow
    }
    
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = severityColor.copy(alpha = 0.1f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(severityColor, CircleShape)
            )
            
            Spacer(modifier = Modifier.width(12.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = alert.type.name.replace("_", " "),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = TextPrimary
                )
                Text(
                    text = formatTimestamp(alert.timestamp),
                    style = MaterialTheme.typography.bodySmall,
                    color = TextTertiary
                )
            }
            
            if (!alert.isAcknowledged) {
                IconButton(
                    onClick = onAcknowledge,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "Acknowledge",
                        tint = Primary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

private fun getSignalColor(rssi: Int): Color {
    return when {
        rssi >= -50 -> Success
        rssi >= -60 -> Primary
        rssi >= -70 -> Warning
        else -> Error
    }
}

private fun formatUptime(uptimeMs: Long): String {
    val seconds = uptimeMs / 1000
    val minutes = seconds / 60
    val hours = minutes / 60
    val days = hours / 24
    
    return when {
        days > 0 -> "${days}d ${hours % 24}h"
        hours > 0 -> "${hours}h ${minutes % 60}m"
        minutes > 0 -> "${minutes}m"
        else -> "${seconds}s"
    }
}

private fun formatTimestamp(timestamp: Long): String {
    val now = System.currentTimeMillis()
    val diff = now - timestamp
    val seconds = diff / 1000
    val minutes = seconds / 60
    val hours = minutes / 60
    
    return when {
        minutes < 1 -> "Just now"
        minutes < 60 -> "${minutes}m ago"
        hours < 24 -> "${hours}h ago"
        else -> "${hours / 24}d ago"
    }
}

