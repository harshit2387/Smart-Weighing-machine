package com.example.tamper_detection.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.tamper_detection.network.ESP8266Client
import com.example.tamper_detection.ui.components.*
import com.example.tamper_detection.ui.theme.*
import com.example.tamper_detection.viewmodel.MainViewModel

@Composable
fun ConnectionScreen(
    viewModel: MainViewModel,
    onConnected: () -> Unit
) {
    val connectionState by viewModel.connectionState.collectAsState()
    val settings by viewModel.settings.collectAsState()
    val uiState by viewModel.uiState.collectAsState()
    
    var ipAddress by remember { mutableStateOf(settings.connectionConfig.ipAddress) }
    var port by remember { mutableStateOf(settings.connectionConfig.port.toString()) }
    var showAdvanced by remember { mutableStateOf(false) }
    
    // Auto-navigate when connected
    LaunchedEffect(connectionState) {
        if (connectionState == ESP8266Client.ConnectionState.CONNECTED) {
            onConnected()
        }
    }
    
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
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Logo/Icon
            Box(
                modifier = Modifier
                    .size(100.dp)
                    .background(
                        brush = Brush.linearGradient(
                            colors = listOf(Primary, Accent)
                        ),
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Scale,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(50.dp)
                )
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // Title
            Text(
                text = "WeightSmart",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
            
            Text(
                text = "Tamper Detection System",
                style = MaterialTheme.typography.bodyLarge,
                color = TextSecondary
            )
            
            Spacer(modifier = Modifier.height(40.dp))
            
            // Connection Card
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = DarkSurfaceVariant
                )
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Connect to ESP8266",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Text(
                        text = "Enter the IP address shown on your device's OLED display",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary,
                        textAlign = TextAlign.Center
                    )
                    
                    Spacer(modifier = Modifier.height(24.dp))
                    
                    // IP Address Input
                    OutlinedTextField(
                        value = ipAddress,
                        onValueChange = { ipAddress = it },
                        label = { Text("IP Address") },
                        placeholder = { Text("192.168.4.1") },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Router,
                                contentDescription = null
                            )
                        },
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Number
                        ),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Primary,
                            unfocusedBorderColor = TextTertiary,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        )
                    )
                    
                    // Advanced Options
                    AnimatedVisibility(visible = showAdvanced) {
                        Column {
                            Spacer(modifier = Modifier.height(12.dp))
                            
                            OutlinedTextField(
                                value = port,
                                onValueChange = { port = it },
                                label = { Text("Port") },
                                placeholder = { Text("80") },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.Numbers,
                                        contentDescription = null
                                    )
                                },
                                keyboardOptions = KeyboardOptions(
                                    keyboardType = KeyboardType.Number
                                ),
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = Primary,
                                    unfocusedBorderColor = TextTertiary,
                                    focusedTextColor = TextPrimary,
                                    unfocusedTextColor = TextPrimary
                                )
                            )
                        }
                    }
                    
                    // Show Advanced Toggle
                    TextButton(
                        onClick = { showAdvanced = !showAdvanced }
                    ) {
                        Text(
                            text = if (showAdvanced) "Hide Advanced" else "Show Advanced",
                            color = Primary
                        )
                        Icon(
                            imageVector = if (showAdvanced) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = null,
                            tint = Primary
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // Connection Status
                    AnimatedVisibility(
                        visible = connectionState != ESP8266Client.ConnectionState.DISCONNECTED
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(bottom = 16.dp)
                        ) {
                            when (connectionState) {
                                ESP8266Client.ConnectionState.CONNECTING,
                                ESP8266Client.ConnectionState.RECONNECTING -> {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(20.dp),
                                        strokeWidth = 2.dp,
                                        color = Connecting
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Connecting...",
                                        color = Connecting
                                    )
                                }
                                ESP8266Client.ConnectionState.ERROR -> {
                                    Icon(
                                        imageVector = Icons.Default.Error,
                                        contentDescription = null,
                                        tint = Error,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Connection failed",
                                        color = Error
                                    )
                                }
                                else -> {}
                            }
                        }
                    }
                    
                    // Connect Button
                    GradientButton(
                        text = when (connectionState) {
                            ESP8266Client.ConnectionState.CONNECTING -> "Connecting..."
                            ESP8266Client.ConnectionState.RECONNECTING -> "Reconnecting..."
                            else -> "Connect"
                        },
                        onClick = {
                            viewModel.updateConnectionSettings(
                                ipAddress = ipAddress,
                                port = port.toIntOrNull() ?: 80
                            )
                            viewModel.connect()
                        },
                        enabled = connectionState != ESP8266Client.ConnectionState.CONNECTING,
                        icon = Icons.Default.Wifi,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // Help Info
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Info.copy(alpha = 0.1f)
                )
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = Info,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "How to find IP Address:",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = TextPrimary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "1. Power on your ESP8266 device\n" +
                                    "2. Wait for WiFi connection\n" +
                                    "3. Check the IP shown on OLED display\n" +
                                    "4. Or connect to 'WeightSmart_ESP8266' AP",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary,
                            lineHeight = 18.sp
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // AP Mode Option
            OutlinedButton(
                onClick = {
                    ipAddress = "192.168.4.1"
                    port = "80"
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = Primary
                )
            ) {
                Icon(
                    imageVector = Icons.Default.WifiTethering,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Use Access Point Mode (192.168.4.1)")
            }
            
            // Skip Button
            TextButton(
                onClick = onConnected
            ) {
                Text(
                    text = "Skip for now",
                    color = TextTertiary
                )
            }
        }
        
        // Error message
        uiState.errorMessage?.let { error ->
            AlertBanner(
                message = error,
                severity = AlertBannerSeverity.ERROR,
                onDismiss = { viewModel.clearError() },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(16.dp)
            )
        }
    }
}

