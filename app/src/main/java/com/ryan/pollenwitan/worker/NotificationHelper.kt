package com.ryan.pollenwitan.worker

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

object NotificationHelper {

    const val CHANNEL_MORNING_BRIEFING = "morning_briefing"
    const val CHANNEL_THRESHOLD_ALERT = "threshold_alert"
    const val CHANNEL_COMPOUND_RISK = "compound_risk"

    fun createChannels(context: Context) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val channels = listOf(
            NotificationChannel(
                CHANNEL_MORNING_BRIEFING,
                "Morning Briefing",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Daily pollen and air quality summary"
            },
            NotificationChannel(
                CHANNEL_THRESHOLD_ALERT,
                "Threshold Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Alerts when pollen levels exceed your thresholds"
            },
            NotificationChannel(
                CHANNEL_COMPOUND_RISK,
                "Compound Risk",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Alerts when pollen and air quality combine to increase respiratory risk"
            }
        )

        channels.forEach { manager.createNotificationChannel(it) }
    }

    fun sendNotification(
        context: Context,
        channelId: String,
        notificationId: Int,
        title: String,
        text: String
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setAutoCancel(true)
            .build()

        NotificationManagerCompat.from(context).notify(notificationId, notification)
    }
}
