package com.example.tamper_detection.service

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.example.tamper_detection.MainActivity
import com.example.tamper_detection.R
import com.example.tamper_detection.data.models.*
import com.example.tamper_detection.ml.TamperDetector
import com.example.tamper_detection.network.ESP8266Client
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collect

/**
 * Foreground service for continuous weight monitoring and tamper detection
 */
class WeightMonitorService : Service() {
    
    companion object {
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "weight_monitor_channel"
        private const val CHANNEL_NAME = "Weight Monitor"
        
        const val ACTION_START = "com.example.tamper_detection.START_SERVICE"
        const val ACTION_STOP = "com.example.tamper_detection.STOP_SERVICE"
    }
    
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private lateinit var esp8266Client: ESP8266Client
    private lateinit var tamperDetector: TamperDetector
    private var monitoringJob: Job? = null
    
    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        esp8266Client = ESP8266Client()
        tamperDetector = TamperDetector(this)
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startMonitoring()
            ACTION_STOP -> stopMonitoring()
        }
        return START_STICKY
    }
    
    override fun onBind(intent: Intent?): IBinder? = null
    
    private fun startMonitoring() {
        val notification = createNotification("Monitoring weight sensor...")
        startForeground(NOTIFICATION_ID, notification)
        
        monitoringJob = scope.launch {
            // Connect to ESP8266
            esp8266Client.initialize(ConnectionConfig())
            esp8266Client.connect()
            
            // Monitor sensor data
            esp8266Client.sensorData.collect { data ->
                // Process through ML detector
                val prediction = tamperDetector.processSensorData(data)
                
                // Check for tampering
                if (prediction.isTampering) {
                    showTamperAlert(prediction)
                }
                
                // Update notification with current weight
                updateNotification(
                    "Weight: %.2f g | Light: %.0f lux".format(
                        data.weight.weight,
                        data.light.lightLevel
                    )
                )
            }
        }
    }
    
    private fun stopMonitoring() {
        monitoringJob?.cancel()
        esp8266Client.disconnect()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }
    
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Weight monitoring notifications"
                setShowBadge(false)
            }
            
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
            
            // Create alert channel
            val alertChannel = NotificationChannel(
                "tamper_alert_channel",
                "Tamper Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Tamper detection alerts"
                enableVibration(true)
            }
            notificationManager.createNotificationChannel(alertChannel)
        }
    }
    
    private fun createNotification(content: String): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        
        val stopIntent = PendingIntent.getService(
            this,
            0,
            Intent(this, WeightMonitorService::class.java).apply {
                action = ACTION_STOP
            },
            PendingIntent.FLAG_IMMUTABLE
        )
        
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("WeightSmart Active")
            .setContentText(content)
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setContentIntent(pendingIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop", stopIntent)
            .setOngoing(true)
            .build()
    }
    
    private fun updateNotification(content: String) {
        val notification = createNotification(content)
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, notification)
    }
    
    private fun showTamperAlert(prediction: TamperPrediction) {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        
        val notification = NotificationCompat.Builder(this, "tamper_alert_channel")
            .setContentTitle("⚠️ TAMPER ALERT")
            .setContentText("${prediction.tamperType.name.replace("_", " ")} detected!")
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()
        
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify((System.currentTimeMillis() % 10000).toInt(), notification)
    }
    
    override fun onDestroy() {
        super.onDestroy()
        monitoringJob?.cancel()
        scope.cancel()
        esp8266Client.cleanup()
    }
}
