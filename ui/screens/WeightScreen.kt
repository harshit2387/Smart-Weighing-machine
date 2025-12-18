package com.example.tamper_detection.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.tamper_detection.data.models.WeightHistoryEntry
import com.example.tamper_detection.network.ESP8266Client
import com.example.tamper_detection.ui.components.*
import com.example.tamper_detection.ui.theme.*
import com.example.tamper_detection.viewmodel.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WeightScreen(
    viewModel: MainViewModel,
    onNavigateBack: () -> Unit
) {
    val connectionState by viewModel.connectionState.collectAsState()
    val weightData by viewModel.weightData.collectAsState()
    val weightHistory by viewModel.weightHistory.collectAsState()
    val settings by viewModel.settings.collectAsState()
    val uiState by viewModel.uiState.collectAsState()
    
    var showCalibrationDialog by remember { mutableStateOf(false) }
    var calibrationWeight by remember { mutableStateOf("1000") }
    var showCalibrationFactorDialog by remember { mutableStateOf(false) }
    var calibrationFactor by remember { mutableStateOf(settings.calibrationConfig.calibrationFactor.toString()) }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Weight Monitor",
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            // Main weight display card
            GlowCard(
                glowColor = if (weightData.isStable) Primary else Warning
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Live Weight Reading",
                        style = MaterialTheme.typography.titleMedium,
                        color = TextSecondary
                    )
                    
                    Spacer(modifier = Modifier.height(24.dp))
                    
                    WeightDisplay(
                        weight = weightData.weight * settings.weightUnit.multiplier,
                        unit = settings.weightUnit.symbol,
                        isStable = weightData.isStable
                    )
                    
                    Spacer(modifier = Modifier.height(24.dp))
                    
                    // Raw value
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(DarkSurfaceVariant, RoundedCornerShape(12.dp))
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                text = "Raw Value",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextTertiary
                            )
                            Text(
                                text = weightData.rawValue.toString(),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                color = TextPrimary
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = "Calibration Factor",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextTertiary
                            )
                            Text(
                                text = "%.2f".format(weightData.calibrationFactor),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                color = TextPrimary
                            )
                        }
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Weight chart
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
                        text = "Weight History",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    if (weightHistory.isNotEmpty()) {
                        WeightChart(
                            data = weightHistory,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(200.dp)
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(200.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "No data yet",
                                color = TextTertiary
                            )
                        }
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Control buttons
            SectionHeader(title = "Scale Controls")
            Spacer(modifier = Modifier.height(12.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                GradientButton(
                    text = "Tare",
                    onClick = { viewModel.tareScale() },
                    icon = Icons.Default.Refresh,
                    enabled = connectionState == ESP8266Client.ConnectionState.CONNECTED,
                    modifier = Modifier.weight(1f)
                )
                
                GradientButton(
                    text = "Calibrate",
                    onClick = { showCalibrationDialog = true },
                    icon = Icons.Default.Tune,
                    enabled = connectionState == ESP8266Client.ConnectionState.CONNECTED,
                    gradient = Brush.horizontalGradient(
                        colors = listOf(Secondary, Accent)
                    ),
                    modifier = Modifier.weight(1f)
                )
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Calibration settings
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
                        text = "Calibration Settings",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Current Factor",
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextSecondary
                            )
                            Text(
                                text = settings.calibrationConfig.calibrationFactor.toString(),
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = Primary
                            )
                        }
                        
                        OutlinedButton(
                            onClick = { showCalibrationFactorDialog = true },
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = Primary
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Edit")
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    Divider(color = DarkSurface)
                    
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    InfoRow(
                        label = "Max Weight",
                        value = "${settings.calibrationConfig.maxWeight}g"
                    )
                    InfoRow(
                        label = "Stability Threshold",
                        value = "±${settings.calibrationConfig.stabilityThreshold}g"
                    )
                    InfoRow(
                        label = "Average Samples",
                        value = settings.calibrationConfig.averageSamples.toString()
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(80.dp))
        }
        
        // Calibration Dialog
        if (showCalibrationDialog) {
            AlertDialog(
                onDismissRequest = { showCalibrationDialog = false },
                title = { Text("Calibrate Scale") },
                text = {
                    Column {
                        Text(
                            text = "Place a known weight on the scale and enter its value:",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        OutlinedTextField(
                            value = calibrationWeight,
                            onValueChange = { calibrationWeight = it },
                            label = { Text("Weight (grams)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true
                        )
                    }
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            calibrationWeight.toFloatOrNull()?.let {
                                viewModel.calibrate(it)
                            }
                            showCalibrationDialog = false
                        }
                    ) {
                        Text("Calibrate")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showCalibrationDialog = false }) {
                        Text("Cancel")
                    }
                },
                containerColor = DarkSurface
            )
        }
        
        // Calibration Factor Dialog
        if (showCalibrationFactorDialog) {
            AlertDialog(
                onDismissRequest = { showCalibrationFactorDialog = false },
                title = { Text("Set Calibration Factor") },
                text = {
                    Column {
                        Text(
                            text = "Enter the calibration factor manually:",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        OutlinedTextField(
                            value = calibrationFactor,
                            onValueChange = { calibrationFactor = it },
                            label = { Text("Calibration Factor") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true
                        )
                    }
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            calibrationFactor.toFloatOrNull()?.let {
                                viewModel.setCalibrationFactor(it)
                            }
                            showCalibrationFactorDialog = false
                        }
                    ) {
                        Text("Save")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showCalibrationFactorDialog = false }) {
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
private fun WeightChart(
    data: List<WeightHistoryEntry>,
    modifier: Modifier = Modifier
) {
    if (data.isEmpty()) return
    
    val minWeight = data.minOf { it.weight }.coerceAtLeast(0f)
    val maxWeight = data.maxOf { it.weight }.coerceAtLeast(minWeight + 1f)
    val range = maxWeight - minWeight
    
    Canvas(modifier = modifier) {
        val width = size.width
        val height = size.height
        val padding = 40f
        val chartWidth = width - padding * 2
        val chartHeight = height - padding * 2
        
        // Draw grid lines
        for (i in 0..4) {
            val y = padding + (chartHeight / 4) * i
            drawLine(
                color = ChartGrid,
                start = Offset(padding, y),
                end = Offset(width - padding, y),
                strokeWidth = 1f
            )
        }
        
        if (data.size < 2) return@Canvas
        
        // Draw line chart
        val path = Path()
        val fillPath = Path()
        
        data.forEachIndexed { index, entry ->
            val x = padding + (chartWidth / (data.size - 1)) * index
            val normalizedWeight = (entry.weight - minWeight) / range
            val y = padding + chartHeight * (1 - normalizedWeight)
            
            if (index == 0) {
                path.moveTo(x, y)
                fillPath.moveTo(x, height - padding)
                fillPath.lineTo(x, y)
            } else {
                path.lineTo(x, y)
                fillPath.lineTo(x, y)
            }
        }
        
        // Complete fill path
        fillPath.lineTo(width - padding, height - padding)
        fillPath.close()
        
        // Draw fill
        drawPath(
            path = fillPath,
            brush = Brush.verticalGradient(
                colors = listOf(
                    ChartLine.copy(alpha = 0.3f),
                    ChartLine.copy(alpha = 0.0f)
                )
            )
        )
        
        // Draw line
        drawPath(
            path = path,
            color = ChartLine,
            style = Stroke(width = 3f)
        )
        
        // Draw points
        data.forEachIndexed { index, entry ->
            val x = padding + (chartWidth / (data.size - 1)) * index
            val normalizedWeight = (entry.weight - minWeight) / range
            val y = padding + chartHeight * (1 - normalizedWeight)
            
            drawCircle(
                color = if (entry.isAnomaly) Error else ChartLine,
                radius = 4f,
                center = Offset(x, y)
            )
        }
    }
}

@Composable
private fun InfoRow(
    label: String,
    value: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = TextPrimary
        )
    }
}

