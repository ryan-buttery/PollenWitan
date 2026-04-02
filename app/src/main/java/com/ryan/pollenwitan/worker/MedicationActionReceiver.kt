package com.ryan.pollenwitan.worker

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat
import com.ryan.pollenwitan.data.repository.DoseTrackingRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Handles the "Mark as Taken" action from medication reminder notifications.
 * Confirms the dose via [DoseTrackingRepository], dismisses the notification,
 * and cancels any pending missed-dose escalation alarm.
 */
class MedicationActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_MARK_DOSE_TAKEN) return

        val profileId = intent.getStringExtra(EXTRA_PROFILE_ID) ?: return
        val medicineId = intent.getStringExtra(EXTRA_MEDICINE_ID) ?: return
        val slotIndex = intent.getIntExtra(EXTRA_SLOT_INDEX, -1)
        if (slotIndex < 0) return

        val medicineName = intent.getStringExtra(EXTRA_MEDICINE_NAME) ?: return
        val dose = intent.getIntExtra(EXTRA_DOSE, 0)
        val medicineType = intent.getStringExtra(EXTRA_MEDICINE_TYPE) ?: return
        val reminderHour = intent.getIntExtra(EXTRA_REMINDER_HOUR, 0)
        val notificationId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, 0)
        val profileIndex = intent.getIntExtra(EXTRA_PROFILE_INDEX, -1)
        val assignmentIndex = intent.getIntExtra(EXTRA_ASSIGNMENT_INDEX, -1)

        // Cancel the missed-dose escalation alarm if indices are available
        if (profileIndex >= 0 && assignmentIndex >= 0) {
            MissedDoseAlarmReceiver.cancel(context, profileIndex, assignmentIndex, slotIndex)
        }

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val repository = DoseTrackingRepository(context.applicationContext)
                repository.confirmDose(
                    profileId = profileId,
                    medicineId = medicineId,
                    slotIndex = slotIndex,
                    medicineName = medicineName,
                    dose = dose,
                    medicineType = medicineType,
                    reminderHour = reminderHour
                )
                NotificationManagerCompat.from(context).cancel(notificationId)
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        const val ACTION_MARK_DOSE_TAKEN = "com.ryan.pollenwitan.ACTION_MARK_DOSE_TAKEN"

        const val EXTRA_PROFILE_ID = "extra_profile_id"
        const val EXTRA_MEDICINE_ID = "extra_medicine_id"
        const val EXTRA_SLOT_INDEX = "extra_slot_index"
        const val EXTRA_MEDICINE_NAME = "extra_medicine_name"
        const val EXTRA_DOSE = "extra_dose"
        const val EXTRA_MEDICINE_TYPE = "extra_medicine_type"
        const val EXTRA_REMINDER_HOUR = "extra_reminder_hour"
        const val EXTRA_NOTIFICATION_ID = "extra_notification_id"
        const val EXTRA_PROFILE_INDEX = "extra_profile_index"
        const val EXTRA_ASSIGNMENT_INDEX = "extra_assignment_index"
    }
}
