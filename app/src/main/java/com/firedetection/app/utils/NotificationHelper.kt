package com.firedetection.app.utils

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.firedetection.app.MainActivity
import com.firedetection.app.R

class NotificationHelper(private val context: Context) {

    private val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    companion object {
        const val FIRE_CHANNEL_ID = "fire_detection"
        const val SMOKE_CHANNEL_ID = "smoke_detection"
        const val FIRE_NOTIFICATION_ID = 1001
        const val SMOKE_NOTIFICATION_ID = 1002
    }

    init {
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val fireChannel = NotificationChannel(
                FIRE_CHANNEL_ID,
                "Fire Detection",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Fire detection alerts"
                enableVibration(true)
                enableLights(true)
            }

            val smokeChannel = NotificationChannel(
                SMOKE_CHANNEL_ID,
                "Smoke Detection",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Smoke detection alerts"
                enableVibration(true)
                enableLights(true)
            }

            notificationManager.createNotificationChannels(listOf(fireChannel, smokeChannel))
        }
    }

    fun showFireAlert() {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, FIRE_CHANNEL_ID)
            .setContentTitle("🔥 FIRE DETECTED!")
            .setContentText("Fire has been detected in the monitored area. Please check immediately!")
            .setSmallIcon(R.drawable.ic_fire_alert)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setVibrate(longArrayOf(0, 1000, 500, 1000, 500, 1000))
            .setLights(0xFF0000, 3000, 3000)
            .build()

        notificationManager.notify(FIRE_NOTIFICATION_ID, notification)
    }

    fun showSmokeAlert() {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, SMOKE_CHANNEL_ID)
            .setContentTitle("💨 SMOKE DETECTED!")
            .setContentText("Smoke has been detected in the monitored area. Please investigate!")
            .setSmallIcon(R.drawable.ic_smoke_alert)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setVibrate(longArrayOf(0, 1000, 500, 1000))
            .setLights(0xFFA500, 2000, 2000)
            .build()

        notificationManager.notify(SMOKE_NOTIFICATION_ID, notification)
    }

    fun cancelAllNotifications() {
        notificationManager.cancelAll()
    }
} 