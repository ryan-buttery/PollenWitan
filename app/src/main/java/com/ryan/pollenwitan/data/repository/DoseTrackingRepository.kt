package com.ryan.pollenwitan.data.repository

import android.content.Context
import android.content.SharedPreferences
import com.ryan.pollenwitan.data.local.AppDatabase
import com.ryan.pollenwitan.data.local.DoseHistoryEntity
import com.ryan.pollenwitan.data.security.EncryptedPrefsStore
import com.ryan.pollenwitan.domain.model.DoseConfirmation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate

class DoseTrackingRepository(
    context: Context
) {

    private val store = EncryptedPrefsStore(context, "dose_tracking_encrypted")
    private val doseHistoryDao = AppDatabase.getInstance(context).doseHistoryDao()

    internal object Keys {
        const val TRACKING_DATE = "tracking_date"
        fun confirmed(profileId: String, medicineId: String, slotIndex: Int) =
            "confirmed_${profileId}_${medicineId}_${slotIndex}"

        fun parseConfirmation(profileId: String, key: String): DoseConfirmation? {
            val prefix = "confirmed_${profileId}_"
            if (!key.startsWith(prefix)) return null
            val parts = key.removePrefix(prefix).split("_")
            if (parts.size < 2) return null
            val medicineId = parts.dropLast(1).joinToString("_")
            val slotIndex = parts.last().toIntOrNull() ?: return null
            return DoseConfirmation(medicineId, slotIndex)
        }
    }

    fun getConfirmations(profileId: String): Flow<Set<DoseConfirmation>> = store.data.map { prefs ->
        val today = LocalDate.now().toString()
        val storedDate = prefs.getString(Keys.TRACKING_DATE, null)
        if (storedDate != today) {
            return@map emptySet()
        }
        prefs.all.entries
            .filter { (key, value) ->
                key.startsWith("confirmed_${profileId}_") && value == true
            }
            .mapNotNull { (key, _) -> Keys.parseConfirmation(profileId, key) }
            .toSet()
    }

    suspend fun confirmDose(
        profileId: String,
        medicineId: String,
        slotIndex: Int,
        medicineName: String,
        dose: Int,
        medicineType: String,
        reminderHour: Int
    ) {
        store.edit {
            resetIfNewDay(this)
            putBoolean(Keys.confirmed(profileId, medicineId, slotIndex), true)
        }
        doseHistoryDao.upsert(
            DoseHistoryEntity(
                profileId = profileId,
                medicineId = medicineId,
                slotIndex = slotIndex,
                date = LocalDate.now().toString(),
                confirmedAtMillis = System.currentTimeMillis(),
                confirmed = true,
                medicineName = medicineName,
                dose = dose,
                medicineType = medicineType,
                reminderHour = reminderHour
            )
        )
    }

    suspend fun unconfirmDose(
        profileId: String,
        medicineId: String,
        slotIndex: Int,
        medicineName: String,
        dose: Int,
        medicineType: String,
        reminderHour: Int
    ) {
        store.edit {
            resetIfNewDay(this)
            remove(Keys.confirmed(profileId, medicineId, slotIndex))
        }
        doseHistoryDao.upsert(
            DoseHistoryEntity(
                profileId = profileId,
                medicineId = medicineId,
                slotIndex = slotIndex,
                date = LocalDate.now().toString(),
                confirmedAtMillis = System.currentTimeMillis(),
                confirmed = false,
                medicineName = medicineName,
                dose = dose,
                medicineType = medicineType,
                reminderHour = reminderHour
            )
        )
    }

    suspend fun getHistoryForDate(profileId: String, date: LocalDate): List<DoseHistoryEntity> {
        return doseHistoryDao.getConfirmedForDate(profileId, date.toString())
    }

    fun getHistoryForDateRange(
        profileId: String,
        startDate: LocalDate,
        endDate: LocalDate
    ): Flow<List<DoseHistoryEntity>> {
        return doseHistoryDao.getConfirmedForDateRange(
            profileId,
            startDate.toString(),
            endDate.toString()
        )
    }

    private fun resetIfNewDay(editor: SharedPreferences.Editor) {
        val today = LocalDate.now().toString()
        val storedDate = store.prefs.getString(Keys.TRACKING_DATE, null)
        if (storedDate != today) {
            val keysToRemove = store.prefs.all.keys.filter { it.startsWith("confirmed_") }
            keysToRemove.forEach { editor.remove(it) }
            editor.putString(Keys.TRACKING_DATE, today)
        }
    }
}
