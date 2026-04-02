package com.ryan.pollenwitan.worker

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.ryan.pollenwitan.MainActivity
import com.ryan.pollenwitan.R

object NotificationHelper {

    const val CHANNEL_MORNING_BRIEFING = "morning_briefing"
    const val CHANNEL_THRESHOLD_ALERT = "threshold_alert"
    const val CHANNEL_COMPOUND_RISK = "compound_risk"
    const val CHANNEL_MEDICATION_REMINDER = "medication_reminder"
    const val CHANNEL_SYMPTOM_REMINDER = "symptom_reminder"
    const val CHANNEL_MISSED_DOSE = "missed_dose"

    const val GROUP_MORNING_BRIEFING = "group_morning_briefing"
    const val GROUP_THRESHOLD_ALERT = "group_threshold_alert"
    const val GROUP_COMPOUND_RISK = "group_compound_risk"
    const val GROUP_MEDICATION_REMINDER = "group_medication_reminder"
    const val GROUP_SYMPTOM_REMINDER = "group_symptom_reminder"

    const val GROUP_SUMMARY_MORNING_ID = 900
    const val GROUP_SUMMARY_THRESHOLD_ID = 901
    const val GROUP_SUMMARY_COMPOUND_ID = 902
    const val GROUP_SUMMARY_MEDICATION_ID = 903
    const val GROUP_SUMMARY_SYMPTOM_ID = 904

    fun createChannels(context: Context) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val channels = listOf(
            NotificationChannel(
                CHANNEL_MORNING_BRIEFING,
                context.getString(R.string.notif_channel_morning_briefing),
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = context.getString(R.string.notif_channel_morning_briefing_desc)
                lockscreenVisibility = android.app.Notification.VISIBILITY_PRIVATE
            },
            NotificationChannel(
                CHANNEL_THRESHOLD_ALERT,
                context.getString(R.string.notif_channel_threshold_alert),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = context.getString(R.string.notif_channel_threshold_alert_desc)
                lockscreenVisibility = android.app.Notification.VISIBILITY_PRIVATE
            },
            NotificationChannel(
                CHANNEL_COMPOUND_RISK,
                context.getString(R.string.notif_channel_compound_risk),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = context.getString(R.string.notif_channel_compound_risk_desc)
                lockscreenVisibility = android.app.Notification.VISIBILITY_PRIVATE
            },
            NotificationChannel(
                CHANNEL_MEDICATION_REMINDER,
                context.getString(R.string.notif_channel_medication_reminder),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = context.getString(R.string.notif_channel_medication_reminder_desc)
                lockscreenVisibility = android.app.Notification.VISIBILITY_PRIVATE
            },
            NotificationChannel(
                CHANNEL_SYMPTOM_REMINDER,
                context.getString(R.string.notif_channel_symptom_reminder),
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = context.getString(R.string.notif_channel_symptom_reminder_desc)
                lockscreenVisibility = android.app.Notification.VISIBILITY_PRIVATE
            },
            NotificationChannel(
                CHANNEL_MISSED_DOSE,
                context.getString(R.string.notif_channel_missed_dose),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = context.getString(R.string.notif_channel_missed_dose_desc)
                lockscreenVisibility = android.app.Notification.VISIBILITY_PRIVATE
            }
        )

        channels.forEach { manager.createNotificationChannel(it) }
    }

    fun sendNotification(
        context: Context,
        channelId: String,
        notificationId: Int,
        title: String,
        text: String,
        targetRoute: String? = null,
        groupKey: String? = null
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setAutoCancel(true)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)

        if (targetRoute != null) {
            val intent = Intent(context, MainActivity::class.java).apply {
                putExtra(EXTRA_NAVIGATE_TO, targetRoute)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val pendingIntent = PendingIntent.getActivity(
                context,
                notificationId,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            builder.setContentIntent(pendingIntent)
        }

        if (groupKey != null) {
            builder.setGroup(groupKey)
        }

        NotificationManagerCompat.from(context).notify(notificationId, builder.build())
    }

    fun sendGroupSummary(
        context: Context,
        channelId: String,
        groupKey: String,
        summaryId: Int,
        title: String,
        targetRoute: String? = null
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setGroup(groupKey)
            .setGroupSummary(true)
            .setAutoCancel(true)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)

        if (targetRoute != null) {
            val intent = Intent(context, MainActivity::class.java).apply {
                putExtra(EXTRA_NAVIGATE_TO, targetRoute)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val pendingIntent = PendingIntent.getActivity(
                context,
                summaryId,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            builder.setContentIntent(pendingIntent)
        }

        NotificationManagerCompat.from(context).notify(summaryId, builder.build())
    }

    fun sendMedicationReminder(
        context: Context,
        notificationId: Int,
        title: String,
        text: String,
        profileId: String,
        medicineId: String,
        slotIndex: Int,
        medicineName: String,
        dose: Int,
        medicineType: String,
        reminderHour: Int,
        profileIndex: Int = -1,
        assignmentIndex: Int = -1,
        targetRoute: String? = null,
        groupKey: String? = null
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        val builder = NotificationCompat.Builder(context, CHANNEL_MEDICATION_REMINDER)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setAutoCancel(true)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)

        // "Mark as Taken" action
        val actionIntent = Intent(context, MedicationActionReceiver::class.java).apply {
            action = MedicationActionReceiver.ACTION_MARK_DOSE_TAKEN
            putExtra(MedicationActionReceiver.EXTRA_PROFILE_ID, profileId)
            putExtra(MedicationActionReceiver.EXTRA_MEDICINE_ID, medicineId)
            putExtra(MedicationActionReceiver.EXTRA_SLOT_INDEX, slotIndex)
            putExtra(MedicationActionReceiver.EXTRA_MEDICINE_NAME, medicineName)
            putExtra(MedicationActionReceiver.EXTRA_DOSE, dose)
            putExtra(MedicationActionReceiver.EXTRA_MEDICINE_TYPE, medicineType)
            putExtra(MedicationActionReceiver.EXTRA_REMINDER_HOUR, reminderHour)
            putExtra(MedicationActionReceiver.EXTRA_NOTIFICATION_ID, notificationId)
            putExtra(MedicationActionReceiver.EXTRA_PROFILE_INDEX, profileIndex)
            putExtra(MedicationActionReceiver.EXTRA_ASSIGNMENT_INDEX, assignmentIndex)
        }
        val actionPendingIntent = PendingIntent.getBroadcast(
            context,
            notificationId,
            actionIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        builder.addAction(
            0,
            context.getString(R.string.notif_action_mark_taken),
            actionPendingIntent
        )

        if (targetRoute != null) {
            val intent = Intent(context, MainActivity::class.java).apply {
                putExtra(EXTRA_NAVIGATE_TO, targetRoute)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val pendingIntent = PendingIntent.getActivity(
                context,
                notificationId + 50000, // offset to avoid collision with action PendingIntent
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            builder.setContentIntent(pendingIntent)
        }

        if (groupKey != null) {
            builder.setGroup(groupKey)
        }

        NotificationManagerCompat.from(context).notify(notificationId, builder.build())
    }

    const val EXTRA_NAVIGATE_TO = "navigate_to"
}
