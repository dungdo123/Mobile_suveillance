package com.firedetection.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.firedetection.app.R
import com.firedetection.app.utils.NotificationHelper

class FireDetectionService : Service() {

    private lateinit var notificationHelper: NotificationHelper
    private val serviceId = 1000

    override fun onCreate() {
        super.onCreate()
        notificationHelper = NotificationHelper(this)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(serviceId, createNotification())
        
        // Start continuous monitoring
        startMonitoring()
        
        return START_STICKY
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "fire_detection_service",
                "Fire Detection Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Background fire detection monitoring"
            }
            
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, "fire_detection_service")
            .setContentTitle("Fire Detection Active")
                            .setContentText("Monitoring for fire...")
            .setSmallIcon(R.drawable.ic_fire_alert)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun startMonitoring() {
        // TODO: Implement continuous monitoring logic
        // This could involve periodic camera checks or network monitoring
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onDestroy() {
        super.onDestroy()
        notificationHelper.cancelAllNotifications()
    }
} 