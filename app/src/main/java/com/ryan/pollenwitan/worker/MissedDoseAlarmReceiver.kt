package com.ryan.pollenwitan.worker

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.ryan.pollenwitan.R
import com.ryan.pollenwitan.data.repository.DoseTrackingRepository
import com.ryan.pollenwitan.ui.navigation.Screen
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * Fires after the missed-dose escalation window. Checks whether the dose
 * has been confirmed in the meantime; if not, sends a follow-up notification.
 */
class MissedDoseAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_MISSED_DOSE_CHECK) return

        val profileId = intent.getStringExtra(EXTRA_PROFILE_ID) ?: return
        val medicineId = intent.getStringExtra(EXTRA_MEDICINE_ID) ?: return
        val slotIndex = intent.getIntExtra(EXTRA_SLOT_INDEX, -1)
        if (slotIndex < 0) return
        val profileName = intent.getStringExtra(EXTRA_PROFILE_NAME) ?: return
        val medicineName = intent.getStringExtra(EXTRA_MEDICINE_NAME) ?: return
        val notificationId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, 0)
        val reminderDateStr = intent.getStringExtra(EXTRA_REMINDER_DATE)

        // Use the date the reminder was originally scheduled for, not "today",
        // to avoid false positives when the alarm fires after midnight.
        val reminderDate = try {
            reminderDateStr?.let { LocalDate.parse(it) } ?: LocalDate.now()
        } catch (_: Exception) {
            LocalDate.now()
        }

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val repository = DoseTrackingRepository(context.applicationContext)
                val confirmedHistory = repository.getHistoryForDate(profileId, reminderDate)
                val isConfirmed = confirmedHistory.any {
                    it.medicineId == medicineId && it.slotIndex == slotIndex
                }
                if (!isConfirmed) {
                    NotificationHelper.sendNotification(
                        context = context,
                        channelId = NotificationHelper.CHANNEL_MISSED_DOSE,
                        notificationId = notificationId,
                        title = context.getString(R.string.notif_missed_dose_title, profileName),
                        text = context.getString(R.string.notif_missed_dose_text, profileName, medicineName),
                        targetRoute = Screen.Dashboard.route
                    )
                }
            } catch (e: Exception) {
                Log.e("MissedDoseAlarm", "Failed to check dose confirmation", e)
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        const val ACTION_MISSED_DOSE_CHECK = "com.ryan.pollenwitan.ACTION_MISSED_DOSE_CHECK"

        const val EXTRA_PROFILE_ID = "extra_profile_id"
        const val EXTRA_MEDICINE_ID = "extra_medicine_id"
        const val EXTRA_SLOT_INDEX = "extra_slot_index"
        const val EXTRA_PROFILE_NAME = "extra_profile_name"
        const val EXTRA_MEDICINE_NAME = "extra_medicine_name"
        const val EXTRA_NOTIFICATION_ID = "extra_notification_id"
        const val EXTRA_REMINDER_DATE = "extra_reminder_date"

        private const val MISSED_DOSE_NOTIF_BASE_ID = 7000

        /**
         * Generates a unique, deterministic request code for a given profile/assignment/slot
         * combination so that the alarm can be cancelled later by re-creating the same PendingIntent.
         */
        private fun requestCode(profileIndex: Int, assignmentIndex: Int, slotIndex: Int): Int =
            MISSED_DOSE_NOTIF_BASE_ID + profileIndex * 100 + assignmentIndex * 10 + slotIndex

        fun notificationId(profileIndex: Int, assignmentIndex: Int, slotIndex: Int): Int =
            requestCode(profileIndex, assignmentIndex, slotIndex)

        /**
         * Schedule a one-shot alarm that fires after [windowMinutes] to check
         * whether a dose has been confirmed.
         */
        fun schedule(
            context: Context,
            profileIndex: Int,
            profileId: String,
            profileName: String,
            medicineId: String,
            medicineName: String,
            assignmentIndex: Int,
            slotIndex: Int,
            windowMinutes: Int
        ) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val intent = buildIntent(
                context, profileIndex, profileId, profileName,
                medicineId, medicineName, assignmentIndex, slotIndex
            )
            val reqCode = requestCode(profileIndex, assignmentIndex, slotIndex)
            val pi = PendingIntent.getBroadcast(
                context, reqCode, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val triggerAt = System.currentTimeMillis() + windowMinutes * 60_000L
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
        }

        /**
         * Cancel a previously scheduled escalation alarm. Called when the user
         * confirms a dose via [MedicationActionReceiver].
         */
        fun cancel(
            context: Context,
            profileIndex: Int,
            assignmentIndex: Int,
            slotIndex: Int
        ) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            // Re-create the same PendingIntent (extras don't matter for matching — only requestCode does)
            val intent = Intent(context, MissedDoseAlarmReceiver::class.java).apply {
                setPackage(context.packageName)
                action = ACTION_MISSED_DOSE_CHECK
            }
            val reqCode = requestCode(profileIndex, assignmentIndex, slotIndex)
            val pi = PendingIntent.getBroadcast(
                context, reqCode, intent,
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
            )
            if (pi != null) {
                alarmManager.cancel(pi)
                pi.cancel()
            }
        }

        private fun buildIntent(
            context: Context,
            profileIndex: Int,
            profileId: String,
            profileName: String,
            medicineId: String,
            medicineName: String,
            assignmentIndex: Int,
            slotIndex: Int
        ): Intent = Intent(context, MissedDoseAlarmReceiver::class.java).apply {
            setPackage(context.packageName)
            action = ACTION_MISSED_DOSE_CHECK
            putExtra(EXTRA_PROFILE_ID, profileId)
            putExtra(EXTRA_MEDICINE_ID, medicineId)
            putExtra(EXTRA_SLOT_INDEX, slotIndex)
            putExtra(EXTRA_PROFILE_NAME, profileName)
            putExtra(EXTRA_MEDICINE_NAME, medicineName)
            putExtra(EXTRA_NOTIFICATION_ID, notificationId(profileIndex, assignmentIndex, slotIndex))
            putExtra(EXTRA_REMINDER_DATE, LocalDate.now().toString())
        }
    }
}
