package com.example.tamper_detection.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.tamper_detection.network.ESP8266Client
import com.example.tamper_detection.ui.theme.*

/**
 * Gradient card with glow effect
 */
@Composable
fun GlowCard(
    modifier: Modifier = Modifier,
    glowColor: Color = Primary,
    content: @Composable ColumnScope.() -> Unit
) {
    Box(modifier = modifier) {
        // Glow effect background
        Box(
            modifier = Modifier
                .matchParentSize()
                .padding(8.dp)
                .blur(20.dp)
                .background(
                    color = glowColor.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(20.dp)
                )
        )
        
        // Main card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(
                containerColor = DarkCard
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                DarkSurfaceVariant.copy(alpha = 0.5f),
                                DarkCard
                            )
                        )
                    )
                    .padding(20.dp),
                content = content
            )
        }
    }
}

/**
 * Status indicator with pulse animation
 */
@Composable
fun StatusIndicator(
    status: ESP8266Client.ConnectionState,
    modifier: Modifier = Modifier
) {
    val color = when (status) {
        ESP8266Client.ConnectionState.CONNECTED -> Connected
        ESP8266Client.ConnectionState.CONNECTING -> Connecting
        ESP8266Client.ConnectionState.RECONNECTING -> Reconnecting
        ESP8266Client.ConnectionState.DISCONNECTED -> Disconnected
        ESP8266Client.ConnectionState.ERROR -> Error
    }
    
    val statusText = when (status) {
        ESP8266Client.ConnectionState.CONNECTED -> "Connected"
        ESP8266Client.ConnectionState.CONNECTING -> "Connecting..."
        ESP8266Client.ConnectionState.RECONNECTING -> "Reconnecting..."
        ESP8266Client.ConnectionState.DISCONNECTED -> "Disconnected"
        ESP8266Client.ConnectionState.ERROR -> "Error"
    }
    
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha"
    )
    
    Row(
        modifier = modifier
            .background(
                color = color.copy(alpha = 0.15f),
                shape = RoundedCornerShape(20.dp)
            )
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .background(
                    color = color.copy(alpha = if (status != ESP8266Client.ConnectionState.CONNECTED) alpha else 1f),
                    shape = CircleShape
                )
        )
        Text(
            text = statusText,
            color = color,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

/**
 * Large weight display with animation
 */
@Composable
fun WeightDisplay(
    weight: Float,
    unit: String,
    isStable: Boolean,
    modifier: Modifier = Modifier
) {
    val animatedWeight by animateFloatAsState(
        targetValue = weight,
        animationSpec = tween(300),
        label = "weight"
    )
    
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            verticalAlignment = Alignment.Bottom
        ) {
            Text(
                text = "%.2f".format(animatedWeight),
                style = MaterialTheme.typography.displayLarge,
                fontWeight = FontWeight.Bold,
                color = if (isStable) Primary else Warning
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = unit,
                style = MaterialTheme.typography.headlineMedium,
                color = TextSecondary,
                modifier = Modifier.padding(bottom = 12.dp)
            )
        }
        
        AnimatedVisibility(
            visible = isStable,
            enter = fadeIn() + slideInVertically(),
            exit = fadeOut() + slideOutVertically()
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = Success,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = "Stable",
                    color = Success,
                    fontSize = 14.sp
                )
            }
        }
    }
}

/**
 * Circular progress gauge
 */
@Composable
fun CircularGauge(
    value: Float,
    maxValue: Float,
    label: String,
    unit: String,
    color: Color = Primary,
    modifier: Modifier = Modifier
) {
    val progress = (value / maxValue).coerceIn(0f, 1f)
    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = tween(500),
        label = "progress"
    )
    
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(100.dp)
        ) {
            CircularProgressIndicator(
                progress = { 1f },
                modifier = Modifier.fillMaxSize(),
                color = DarkSurfaceVariant,
                strokeWidth = 8.dp,
            )
            CircularProgressIndicator(
                progress = { animatedProgress },
                modifier = Modifier.fillMaxSize(),
                color = color,
                strokeWidth = 8.dp,
            )
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "%.1f".format(value),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                Text(
                    text = unit,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary,
            textAlign = TextAlign.Center
        )
    }
}

/**
 * Info card with icon
 */
@Composable
fun InfoCard(
    icon: ImageVector,
    title: String,
    value: String,
    subtitle: String? = null,
    iconColor: Color = Primary,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
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
                    .size(48.dp)
                    .background(
                        color = iconColor.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(12.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconColor,
                    modifier = Modifier.size(24.dp)
                )
            }
            
            Spacer(modifier = Modifier.width(16.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
                Text(
                    text = value,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary
                )
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = TextTertiary
                    )
                }
            }
        }
    }
}

/**
 * Action button with gradient
 */
@Composable
fun GradientButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null,
    gradient: Brush = Brush.horizontalGradient(
        colors = listOf(Primary, Accent)
    )
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.height(52.dp),
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = Color.Transparent
        ),
        contentPadding = PaddingValues(0.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    brush = if (enabled) gradient else Brush.horizontalGradient(
                        colors = listOf(TextTertiary, TextTertiary)
                    ),
                    shape = RoundedCornerShape(16.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (icon != null) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = Color.White
                    )
                }
                Text(
                    text = text,
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

/**
 * Alert banner
 */
@Composable
fun AlertBanner(
    message: String,
    severity: AlertBannerSeverity,
    onDismiss: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val (backgroundColor, iconColor, icon) = when (severity) {
        AlertBannerSeverity.INFO -> Triple(Info.copy(alpha = 0.15f), Info, Icons.Default.Info)
        AlertBannerSeverity.SUCCESS -> Triple(Success.copy(alpha = 0.15f), Success, Icons.Default.CheckCircle)
        AlertBannerSeverity.WARNING -> Triple(Warning.copy(alpha = 0.15f), Warning, Icons.Default.Warning)
        AlertBannerSeverity.ERROR -> Triple(Error.copy(alpha = 0.15f), Error, Icons.Default.Error)
    }
    
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = backgroundColor
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconColor,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = TextPrimary,
                modifier = Modifier.weight(1f)
            )
            if (onDismiss != null) {
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Dismiss",
                        tint = TextSecondary,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

enum class AlertBannerSeverity {
    INFO, SUCCESS, WARNING, ERROR
}

/**
 * Loading overlay
 */
@Composable
fun LoadingOverlay(
    isLoading: Boolean,
    message: String = "Loading..."
) {
    AnimatedVisibility(
        visible = isLoading,
        enter = fadeIn(),
        exit = fadeOut()
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.7f)),
            contentAlignment = Alignment.Center
        ) {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = DarkSurface
                )
            ) {
                Column(
                    modifier = Modifier.padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CircularProgressIndicator(
                        color = Primary,
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodyLarge,
                        color = TextPrimary
                    )
                }
            }
        }
    }
}

/**
 * Section header
 */
@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    action: @Composable (() -> Unit)? = null
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = TextPrimary
        )
        action?.invoke()
    }
}

/**
 * Empty state
 */
@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    action: @Composable (() -> Unit)? = null
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(80.dp)
                .background(
                    color = Primary.copy(alpha = 0.1f),
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = Primary,
                modifier = Modifier.size(40.dp)
            )
        }
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            color = TextPrimary
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary,
            textAlign = TextAlign.Center
        )
        if (action != null) {
            Spacer(modifier = Modifier.height(24.dp))
            action()
        }
    }
}

