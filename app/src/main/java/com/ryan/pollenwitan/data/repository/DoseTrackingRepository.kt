package com.ryan.pollenwitan.data.repository

import android.content.Context
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.ryan.pollenwitan.domain.model.DoseConfirmation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate

private val Context.doseTrackingDataStore by preferencesDataStore(name = "dose_tracking")

class DoseTrackingRepository(
    private val context: Context
) {

    private val dataStore get() = context.doseTrackingDataStore

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

    suspend fun confirmDose(profileId: String, medicineId: String, slotIndex: Int) {
        dataStore.edit { prefs ->
            resetIfNewDay(prefs)
            prefs[Keys.confirmed(profileId, medicineId, slotIndex)] = true
        }
    }

    suspend fun unconfirmDose(profileId: String, medicineId: String, slotIndex: Int) {
        dataStore.edit { prefs ->
            resetIfNewDay(prefs)
            prefs.remove(Keys.confirmed(profileId, medicineId, slotIndex))
        }
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
