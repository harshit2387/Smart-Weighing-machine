package com.example.tamper_detection.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.example.tamper_detection.data.models.*
import com.example.tamper_detection.ui.components.*
import com.example.tamper_detection.ui.theme.*
import com.example.tamper_detection.viewmodel.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: MainViewModel,
    onNavigateBack: () -> Unit
) {
    val settings by viewModel.settings.collectAsState()
    
    var showConnectionDialog by remember { mutableStateOf(false) }
    var ipAddress by remember { mutableStateOf(settings.connectionConfig.ipAddress) }
    var port by remember { mutableStateOf(settings.connectionConfig.port.toString()) }
    
    var showWifiDialog by remember { mutableStateOf(false) }
    var wifiSsid by remember { mutableStateOf(settings.connectionConfig.wifiSsid) }
    var wifiPassword by remember { mutableStateOf(settings.connectionConfig.wifiPassword) }
    var passwordVisible by remember { mutableStateOf(false) }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Settings",
                        fontWeight = FontWeight.SemiBold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
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
            item { Spacer(modifier = Modifier.height(8.dp)) }
            
            // Connection Settings
            item {
                SettingsSection(title = "Connection") {
                    SettingsItem(
                        icon = Icons.Default.Router,
                        title = "Device IP Address",
                        subtitle = "${settings.connectionConfig.ipAddress}:${settings.connectionConfig.port}",
                        onClick = { showConnectionDialog = true }
                    )
                    
                    SettingsItem(
                        icon = Icons.Default.Wifi,
                        title = "WiFi Configuration",
                        subtitle = "SSID: ${settings.connectionConfig.wifiSsid}",
                        onClick = { showWifiDialog = true }
                    )
                    
                    SettingsToggle(
                        icon = Icons.Default.WifiFind,
                        title = "Auto Reconnect",
                        subtitle = "Automatically reconnect on disconnect",
                        checked = settings.connectionConfig.autoReconnect,
                        onCheckedChange = { 
                            val newConfig = settings.connectionConfig.copy(autoReconnect = it)
                            viewModel.updateSettings(settings.copy(connectionConfig = newConfig))
                        }
                    )
                }
            }
            
            // Weight Unit
            item {
                SettingsSection(title = "Measurement") {
                    SettingsDropdown(
                        icon = Icons.Default.Scale,
                        title = "Weight Unit",
                        selectedValue = settings.weightUnit.symbol,
                        options = WeightUnit.entries.map { it.symbol },
                        onOptionSelected = { symbol ->
                            WeightUnit.entries.find { it.symbol == symbol }?.let {
                                viewModel.setWeightUnit(it)
                            }
                        }
                    )
                }
            }
            
            // Detection Settings
            item {
                SettingsSection(title = "Tamper Detection") {
                    SettingsToggle(
                        icon = Icons.Default.Psychology,
                        title = "ML Detection",
                        subtitle = "Use machine learning for anomaly detection",
                        checked = settings.enableMLDetection,
                        onCheckedChange = { viewModel.toggleMLDetection(it) }
                    )
                    
                    SettingsItem(
                        icon = Icons.Default.LightMode,
                        title = "Light Threshold",
                        subtitle = "${settings.lightSensorConfig.tamperThreshold.toInt()} lux",
                        onClick = { }
                    )
                }
            }
            
            // Notifications
            item {
                SettingsSection(title = "Notifications") {
                    SettingsToggle(
                        icon = Icons.Default.Notifications,
                        title = "Push Notifications",
                        subtitle = "Receive alerts when tampering is detected",
                        checked = settings.enableNotifications,
                        onCheckedChange = {
                            viewModel.updateSettings(settings.copy(enableNotifications = it))
                        }
                    )
                    
                    SettingsToggle(
                        icon = Icons.Default.Vibration,
                        title = "Vibration",
                        subtitle = "Vibrate on alerts",
                        checked = settings.enableVibration,
                        onCheckedChange = {
                            viewModel.updateSettings(settings.copy(enableVibration = it))
                        }
                    )
                    
                    SettingsToggle(
                        icon = Icons.Default.VolumeUp,
                        title = "Sound",
                        subtitle = "Play sound on alerts",
                        checked = settings.enableSound,
                        onCheckedChange = {
                            viewModel.updateSettings(settings.copy(enableSound = it))
                        }
                    )
                }
            }
            
            // About
            item {
                SettingsSection(title = "About") {
                    SettingsItem(
                        icon = Icons.Default.Info,
                        title = "App Version",
                        subtitle = "1.0.0",
                        onClick = { }
                    )
                    
                    SettingsItem(
                        icon = Icons.Default.Code,
                        title = "ESP8266 Firmware",
                        subtitle = "WeightSmart v1.0",
                        onClick = { }
                    )
                }
            }
            
            item { Spacer(modifier = Modifier.height(80.dp)) }
        }
        
        // Connection Dialog
        if (showConnectionDialog) {
            AlertDialog(
                onDismissRequest = { showConnectionDialog = false },
                title = { Text("Device Connection") },
                text = {
                    Column {
                        OutlinedTextField(
                            value = ipAddress,
                            onValueChange = { ipAddress = it },
                            label = { Text("IP Address") },
                            placeholder = { Text("192.168.1.1") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        OutlinedTextField(
                            value = port,
                            onValueChange = { port = it },
                            label = { Text("Port") },
                            placeholder = { Text("80") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            viewModel.updateConnectionSettings(
                                ipAddress = ipAddress,
                                port = port.toIntOrNull() ?: 80
                            )
                            showConnectionDialog = false
                        }
                    ) {
                        Text("Save")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showConnectionDialog = false }) {
                        Text("Cancel")
                    }
                },
                containerColor = DarkSurface
            )
        }
        
        // WiFi Dialog
        if (showWifiDialog) {
            AlertDialog(
                onDismissRequest = { showWifiDialog = false },
                title = { Text("WiFi Configuration") },
                text = {
                    Column {
                        Text(
                            text = "Configure the WiFi network for ESP8266",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        OutlinedTextField(
                            value = wifiSsid,
                            onValueChange = { wifiSsid = it },
                            label = { Text("SSID") },
                            leadingIcon = {
                                Icon(Icons.Default.Wifi, contentDescription = null)
                            },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        OutlinedTextField(
                            value = wifiPassword,
                            onValueChange = { wifiPassword = it },
                            label = { Text("Password") },
                            leadingIcon = {
                                Icon(Icons.Default.Lock, contentDescription = null)
                            },
                            trailingIcon = {
                                IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                    Icon(
                                        imageVector = if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                        contentDescription = null
                                    )
                                }
                            },
                            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        Card(
                            shape = RoundedCornerShape(8.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = Info.copy(alpha = 0.1f)
                            )
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Info,
                                    contentDescription = null,
                                    tint = Info,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "This will be sent to the ESP8266 device",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextSecondary
                                )
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            val newConfig = settings.connectionConfig.copy(
                                wifiSsid = wifiSsid,
                                wifiPassword = wifiPassword
                            )
                            viewModel.updateSettings(settings.copy(connectionConfig = newConfig))
                            showWifiDialog = false
                        }
                    ) {
                        Text("Save")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showWifiDialog = false }) {
                        Text("Cancel")
                    }
                },
                containerColor = DarkSurface
            )
        }
    }
}

@Composable
private fun SettingsSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Column {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = Primary,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = DarkSurfaceVariant
            )
        ) {
            Column(
                modifier = Modifier.padding(4.dp)
            ) {
                content()
            }
        }
    }
}

@Composable
private fun SettingsItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        color = Color.Transparent,
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(Primary.copy(alpha = 0.1f), RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = Primary,
                    modifier = Modifier.size(22.dp)
                )
            }
            
            Spacer(modifier = Modifier.width(12.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    color = TextPrimary
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
            }
            
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = TextTertiary
            )
        }
    }
}

@Composable
private fun SettingsToggle(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .background(Primary.copy(alpha = 0.1f), RoundedCornerShape(10.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = Primary,
                modifier = Modifier.size(22.dp)
            )
        }
        
        Spacer(modifier = Modifier.width(12.dp))
        
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = TextPrimary
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary
            )
        }
        
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = Primary
            )
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsDropdown(
    icon: ImageVector,
    title: String,
    selectedValue: String,
    options: List<String>,
    onOptionSelected: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(Primary.copy(alpha = 0.1f), RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = Primary,
                    modifier = Modifier.size(22.dp)
                )
            }
            
            Spacer(modifier = Modifier.width(12.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    color = TextPrimary
                )
                Text(
                    text = selectedValue,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
            }
            
            ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
        }
        
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            containerColor = DarkSurface
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option) },
                    onClick = {
                        onOptionSelected(option)
                        expanded = false
                    }
                )
            }
        }
    }
}

