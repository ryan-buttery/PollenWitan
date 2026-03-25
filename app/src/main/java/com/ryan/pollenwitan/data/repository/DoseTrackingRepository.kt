package com.ryan.pollenwitan.data.repository

import android.content.Context
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.ryan.pollenwitan.data.local.AppDatabase
import com.ryan.pollenwitan.data.local.DoseHistoryEntity
import com.ryan.pollenwitan.domain.model.DoseConfirmation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate

private val Context.doseTrackingDataStore by preferencesDataStore(name = "dose_tracking")

class DoseTrackingRepository(
    private val context: Context
) {

    private val dataStore get() = context.doseTrackingDataStore
    private val doseHistoryDao = AppDatabase.getInstance(context).doseHistoryDao()

    private object Keys {
        val TRACKING_DATE = stringPreferencesKey("tracking_date")
        fun confirmed(profileId: String, medicineId: String, slotIndex: Int) =
            booleanPreferencesKey("confirmed_${profileId}_${medicineId}_${slotIndex}")
    }

    fun getConfirmations(profileId: String): Flow<Set<DoseConfirmation>> = dataStore.data.map { prefs ->
        val today = LocalDate.now().toString()
        val storedDate = prefs[Keys.TRACKING_DATE]
        if (storedDate != today) {
            return@map emptySet()
        }
        prefs.asMap().entries
            .filter { (key, value) ->
                key.name.startsWith("confirmed_${profileId}_") && value == true
            }
            .mapNotNull { (key, _) ->
                // Parse confirmed_{profileId}_{medicineId}_{slotIndex}
                val parts = key.name.removePrefix("confirmed_${profileId}_").split("_")
                if (parts.size >= 2) {
                    val medicineId = parts.dropLast(1).joinToString("_")
                    val slotIndex = parts.last().toIntOrNull() ?: return@mapNotNull null
                    DoseConfirmation(medicineId, slotIndex)
                } else null
            }
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
        dataStore.edit { prefs ->
            resetIfNewDay(prefs)
            prefs[Keys.confirmed(profileId, medicineId, slotIndex)] = true
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
        dataStore.edit { prefs ->
            resetIfNewDay(prefs)
            prefs.remove(Keys.confirmed(profileId, medicineId, slotIndex))
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

    private fun resetIfNewDay(prefs: MutablePreferences) {
        val today = LocalDate.now().toString()
        val storedDate = prefs[Keys.TRACKING_DATE]
        if (storedDate != today) {
            // Clear all confirmed_ keys
            val keysToRemove = prefs.asMap().keys.filter { it.name.startsWith("confirmed_") }
            keysToRemove.forEach { prefs.remove(it) }
            prefs[Keys.TRACKING_DATE] = today
        }
    }
}
