package com.ryan.pollenwitan.data.repository

import android.content.Context
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.ryan.pollenwitan.domain.model.AllergenThreshold
import com.ryan.pollenwitan.domain.model.AppLocation
import com.ryan.pollenwitan.domain.model.MedicineAssignment
import com.ryan.pollenwitan.domain.model.PollenType
import com.ryan.pollenwitan.domain.model.ProfileLocation
import com.ryan.pollenwitan.domain.model.UserProfile
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.profileDataStore by preferencesDataStore(name = "profiles")

class ProfileRepository(
    private val context: Context
) {

    private val dataStore get() = context.profileDataStore

    private object Keys {
        val PROFILE_IDS = stringSetPreferencesKey("profile_ids")
        val SELECTED_PROFILE = stringPreferencesKey("selected_profile")

        fun displayName(id: String) = stringPreferencesKey("profile_${id}_name")
        fun hasAsthma(id: String) = booleanPreferencesKey("profile_${id}_asthma")
        fun trackedAllergens(id: String) = stringSetPreferencesKey("profile_${id}_allergens")
        fun threshold(id: String, type: String, level: String) =
            stringPreferencesKey("profile_${id}_threshold_${type}_${level}")
        fun locationLat(id: String) = stringPreferencesKey("profile_${id}_location_lat")
        fun locationLon(id: String) = stringPreferencesKey("profile_${id}_location_lon")
        fun locationName(id: String) = stringPreferencesKey("profile_${id}_location_name")
        fun medicineIds(id: String) = stringSetPreferencesKey("profile_${id}_medicine_ids")
        fun medDose(id: String, medId: String) = stringPreferencesKey("profile_${id}_med_${medId}_dose")
        fun medTimesPerDay(id: String, medId: String) = stringPreferencesKey("profile_${id}_med_${medId}_times_per_day")
        fun medReminderHours(id: String, medId: String) = stringSetPreferencesKey("profile_${id}_med_${medId}_reminder_hours")
    }

    fun getProfiles(): Flow<List<UserProfile>> = dataStore.data
        .map { prefs ->
            val ids = prefs[Keys.PROFILE_IDS] ?: return@map emptyList()
            ids.mapNotNull { id -> readProfile(prefs, id) }
        }

    fun getSelectedProfileId(): Flow<String> = dataStore.data.map { prefs ->
        prefs[Keys.SELECTED_PROFILE] ?: ""
    }

    suspend fun selectProfile(profileId: String) {
        dataStore.edit { prefs ->
            prefs[Keys.SELECTED_PROFILE] = profileId
        }
    }

    suspend fun addProfile(profile: UserProfile) {
        dataStore.edit { prefs ->
            val ids = prefs[Keys.PROFILE_IDS]?.toMutableSet() ?: mutableSetOf()
            ids.add(profile.id)
            prefs[Keys.PROFILE_IDS] = ids
            writeProfile(prefs, profile)
            // Auto-select if this is the first profile
            if (ids.size == 1) {
                prefs[Keys.SELECTED_PROFILE] = profile.id
            }
        }
    }

    suspend fun updateProfile(profile: UserProfile) {
        dataStore.edit { prefs ->
            // Clear old threshold keys (allergens may have been removed)
            clearProfileKeys(prefs, profile.id)
            writeProfile(prefs, profile)
        }
    }

    suspend fun deleteProfile(profileId: String) {
        dataStore.edit { prefs ->
            val ids = prefs[Keys.PROFILE_IDS]?.toMutableSet() ?: return@edit
            ids.remove(profileId)
            prefs[Keys.PROFILE_IDS] = ids
            clearProfileKeys(prefs, profileId)
            // Reselect if the deleted profile was selected
            if (prefs[Keys.SELECTED_PROFILE] == profileId) {
                prefs[Keys.SELECTED_PROFILE] = ids.firstOrNull() ?: ""
            }
        }
    }

    private fun clearProfileKeys(prefs: MutablePreferences, id: String) {
        prefs.remove(Keys.displayName(id))
        prefs.remove(Keys.hasAsthma(id))
        prefs.remove(Keys.trackedAllergens(id))
        prefs.remove(Keys.locationLat(id))
        prefs.remove(Keys.locationLon(id))
        prefs.remove(Keys.locationName(id))
        val levels = listOf("low", "moderate", "high", "veryHigh")
        for (type in PollenType.entries) {
            for (level in levels) {
                prefs.remove(Keys.threshold(id, type.name, level))
            }
        }
        // Clear medicine assignment keys
        val medIds = prefs[Keys.medicineIds(id)] ?: emptySet()
        for (medId in medIds) {
            prefs.remove(Keys.medDose(id, medId))
            prefs.remove(Keys.medTimesPerDay(id, medId))
            prefs.remove(Keys.medReminderHours(id, medId))
        }
        prefs.remove(Keys.medicineIds(id))
    }

    private fun writeProfile(prefs: MutablePreferences, profile: UserProfile) {
        prefs[Keys.displayName(profile.id)] = profile.displayName
        prefs[Keys.hasAsthma(profile.id)] = profile.hasAsthma
        prefs[Keys.trackedAllergens(profile.id)] = profile.trackedAllergens.keys.map { it.name }.toSet()
        profile.trackedAllergens.forEach { (type, threshold) ->
            prefs[Keys.threshold(profile.id, type.name, "low")] = threshold.low.toString()
            prefs[Keys.threshold(profile.id, type.name, "moderate")] = threshold.moderate.toString()
            prefs[Keys.threshold(profile.id, type.name, "high")] = threshold.high.toString()
            prefs[Keys.threshold(profile.id, type.name, "veryHigh")] = threshold.veryHigh.toString()
        }
        val loc = profile.location
        if (loc != null) {
            prefs[Keys.locationLat(profile.id)] = loc.latitude.toString()
            prefs[Keys.locationLon(profile.id)] = loc.longitude.toString()
            prefs[Keys.locationName(profile.id)] = loc.displayName
        } else {
            prefs.remove(Keys.locationLat(profile.id))
            prefs.remove(Keys.locationLon(profile.id))
            prefs.remove(Keys.locationName(profile.id))
        }
        // Medicine assignments
        val medIds = profile.medicineAssignments.map { it.medicineId }.toSet()
        prefs[Keys.medicineIds(profile.id)] = medIds
        profile.medicineAssignments.forEach { assignment ->
            prefs[Keys.medDose(profile.id, assignment.medicineId)] = assignment.dose.toString()
            prefs[Keys.medTimesPerDay(profile.id, assignment.medicineId)] = assignment.timesPerDay.toString()
            prefs[Keys.medReminderHours(profile.id, assignment.medicineId)] = assignment.reminderHours.map { it.toString() }.toSet()
        }
    }

    private fun readProfile(prefs: Preferences, id: String): UserProfile? {
        val name = prefs[Keys.displayName(id)] ?: return null
        val asthma = prefs[Keys.hasAsthma(id)] ?: false
        val allergenNames = prefs[Keys.trackedAllergens(id)] ?: emptySet()
        val trackedAllergens = allergenNames.mapNotNull { allergenName ->
            val type = PollenType.entries.find { it.name == allergenName } ?: return@mapNotNull null
            val threshold = AllergenThreshold(
                type = type,
                low = prefs[Keys.threshold(id, allergenName, "low")]?.toDoubleOrNull() ?: return@mapNotNull null,
                moderate = prefs[Keys.threshold(id, allergenName, "moderate")]?.toDoubleOrNull() ?: return@mapNotNull null,
                high = prefs[Keys.threshold(id, allergenName, "high")]?.toDoubleOrNull() ?: return@mapNotNull null,
                veryHigh = prefs[Keys.threshold(id, allergenName, "veryHigh")]?.toDoubleOrNull() ?: return@mapNotNull null
            )
            type to threshold
        }.toMap()

        val location = prefs[Keys.locationLat(id)]?.toDoubleOrNull()?.let { lat ->
            val lon = prefs[Keys.locationLon(id)]?.toDoubleOrNull() ?: return@let null
            val locName = prefs[Keys.locationName(id)] ?: ""
            ProfileLocation(lat, lon, locName)
        }

        // Medicine assignments
        val medIds = prefs[Keys.medicineIds(id)] ?: emptySet()
        val medicineAssignments = medIds.mapNotNull { medId ->
            val dose = prefs[Keys.medDose(id, medId)]?.toIntOrNull() ?: return@mapNotNull null
            val timesPerDay = prefs[Keys.medTimesPerDay(id, medId)]?.toIntOrNull() ?: return@mapNotNull null
            val reminderHours = prefs[Keys.medReminderHours(id, medId)]
                ?.mapNotNull { it.toIntOrNull() }
                ?.sorted()
                ?: emptyList()
            MedicineAssignment(
                medicineId = medId,
                dose = dose,
                timesPerDay = timesPerDay,
                reminderHours = reminderHours
            )
        }

        return UserProfile(
            id = id,
            displayName = name,
            trackedAllergens = trackedAllergens,
            hasAsthma = asthma,
            location = location,
            medicineAssignments = medicineAssignments
        )
    }

    companion object {
        fun resolveLocation(profile: UserProfile?, globalLocation: AppLocation): AppLocation {
            val loc = profile?.location ?: return globalLocation
            return AppLocation(loc.latitude, loc.longitude, loc.displayName)
        }
    }
}
