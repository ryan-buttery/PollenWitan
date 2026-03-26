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
import com.ryan.pollenwitan.R

object NotificationHelper {

    const val CHANNEL_MORNING_BRIEFING = "morning_briefing"
    const val CHANNEL_THRESHOLD_ALERT = "threshold_alert"
    const val CHANNEL_COMPOUND_RISK = "compound_risk"
    const val CHANNEL_MEDICATION_REMINDER = "medication_reminder"
    const val CHANNEL_SYMPTOM_REMINDER = "symptom_reminder"

    fun createChannels(context: Context) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val channels = listOf(
            NotificationChannel(
                CHANNEL_MORNING_BRIEFING,
                context.getString(R.string.notif_channel_morning_briefing),
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = context.getString(R.string.notif_channel_morning_briefing_desc)
            },
            NotificationChannel(
                CHANNEL_THRESHOLD_ALERT,
                context.getString(R.string.notif_channel_threshold_alert),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = context.getString(R.string.notif_channel_threshold_alert_desc)
            },
            NotificationChannel(
                CHANNEL_COMPOUND_RISK,
                context.getString(R.string.notif_channel_compound_risk),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = context.getString(R.string.notif_channel_compound_risk_desc)
            },
            NotificationChannel(
                CHANNEL_MEDICATION_REMINDER,
                context.getString(R.string.notif_channel_medication_reminder),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = context.getString(R.string.notif_channel_medication_reminder_desc)
            },
            NotificationChannel(
                CHANNEL_SYMPTOM_REMINDER,
                context.getString(R.string.notif_channel_symptom_reminder),
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = context.getString(R.string.notif_channel_symptom_reminder_desc)
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
