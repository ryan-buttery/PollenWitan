package com.ryan.pollenwitan.data.repository

import android.content.Context
import android.content.SharedPreferences
import com.ryan.pollenwitan.data.security.EncryptedPrefsStore
import com.ryan.pollenwitan.domain.model.AllergenThreshold
import com.ryan.pollenwitan.domain.model.AppLocation
import com.ryan.pollenwitan.domain.model.MedicineAssignment
import com.ryan.pollenwitan.domain.model.PollenType
import com.ryan.pollenwitan.domain.model.ProfileLocation
import com.ryan.pollenwitan.domain.model.TrackedSymptom
import com.ryan.pollenwitan.domain.model.UserProfile
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class ProfileRepository(
    context: Context
) {

    private val store = EncryptedPrefsStore(context, "profiles_encrypted")

    private object Keys {
        const val PROFILE_IDS = "profile_ids"
        const val SELECTED_PROFILE = "selected_profile"
        const val WIDGET_PROFILE = "widget_profile"

        fun displayName(id: String) = "profile_${id}_name"
        fun hasAsthma(id: String) = "profile_${id}_asthma"
        fun trackedAllergens(id: String) = "profile_${id}_allergens"
        fun threshold(id: String, type: String, level: String) =
            "profile_${id}_threshold_${type}_${level}"
        fun locationLat(id: String) = "profile_${id}_location_lat"
        fun locationLon(id: String) = "profile_${id}_location_lon"
        fun locationName(id: String) = "profile_${id}_location_name"
        fun medicineIds(id: String) = "profile_${id}_medicine_ids"
        fun medDose(id: String, medId: String) = "profile_${id}_med_${medId}_dose"
        fun medTimesPerDay(id: String, medId: String) = "profile_${id}_med_${medId}_times_per_day"
        fun medReminderHours(id: String, medId: String) = "profile_${id}_med_${medId}_reminder_hours"
        fun symptomIds(id: String) = "profile_${id}_symptom_ids"
        fun symptomName(id: String, symptomId: String) = "profile_${id}_symptom_${symptomId}_name"
        fun symptomIsDefault(id: String, symptomId: String) = "profile_${id}_symptom_${symptomId}_default"
        fun discoveryMode(id: String) = "profile_${id}_discovery_mode"
    }

    fun getProfiles(): Flow<List<UserProfile>> = store.data
        .map { prefs ->
            val ids = prefs.getStringSet(Keys.PROFILE_IDS, null) ?: return@map emptyList()
            ids.mapNotNull { id -> readProfile(prefs, id) }
        }

    fun getSelectedProfileId(): Flow<String> = store.data.map { prefs ->
        prefs.getString(Keys.SELECTED_PROFILE, null) ?: ""
    }

    fun getWidgetProfileId(): Flow<String> = store.data.map { prefs ->
        prefs.getString(Keys.WIDGET_PROFILE, null) ?: ""
    }

    suspend fun setWidgetProfileId(profileId: String) {
        store.edit {
            putString(Keys.WIDGET_PROFILE, profileId)
        }
    }

    suspend fun selectProfile(profileId: String) {
        store.edit {
            putString(Keys.SELECTED_PROFILE, profileId)
        }
    }

    suspend fun addProfile(profile: UserProfile) {
        store.edit {
            val ids = store.prefs.getStringSet(Keys.PROFILE_IDS, null)?.toMutableSet() ?: mutableSetOf()
            ids.add(profile.id)
            putStringSet(Keys.PROFILE_IDS, ids)
            writeProfile(this, profile)
            if (ids.size == 1) {
                putString(Keys.SELECTED_PROFILE, profile.id)
            }
        }
    }

    suspend fun updateProfile(profile: UserProfile) {
        store.edit {
            clearProfileKeys(this, store.prefs, profile.id)
            writeProfile(this, profile)
        }
    }

    suspend fun deleteProfile(profileId: String) {
        store.edit {
            val ids = store.prefs.getStringSet(Keys.PROFILE_IDS, null)?.toMutableSet() ?: return@edit
            ids.remove(profileId)
            putStringSet(Keys.PROFILE_IDS, ids)
            clearProfileKeys(this, store.prefs, profileId)
            if (store.prefs.getString(Keys.SELECTED_PROFILE, null) == profileId) {
                putString(Keys.SELECTED_PROFILE, ids.firstOrNull() ?: "")
            }
            if (store.prefs.getString(Keys.WIDGET_PROFILE, null) == profileId) {
                putString(Keys.WIDGET_PROFILE, "")
            }
        }
    }

    private fun clearProfileKeys(editor: SharedPreferences.Editor, prefs: SharedPreferences, id: String) {
        editor.remove(Keys.displayName(id))
        editor.remove(Keys.hasAsthma(id))
        editor.remove(Keys.trackedAllergens(id))
        editor.remove(Keys.locationLat(id))
        editor.remove(Keys.locationLon(id))
        editor.remove(Keys.locationName(id))
        val levels = listOf("low", "moderate", "high", "veryHigh")
        for (type in PollenType.entries) {
            for (level in levels) {
                editor.remove(Keys.threshold(id, type.name, level))
            }
        }
        val medIds = prefs.getStringSet(Keys.medicineIds(id), null) ?: emptySet()
        for (medId in medIds) {
            editor.remove(Keys.medDose(id, medId))
            editor.remove(Keys.medTimesPerDay(id, medId))
            editor.remove(Keys.medReminderHours(id, medId))
        }
        editor.remove(Keys.medicineIds(id))
        val symptomIds = prefs.getStringSet(Keys.symptomIds(id), null) ?: emptySet()
        for (symptomId in symptomIds) {
            editor.remove(Keys.symptomName(id, symptomId))
            editor.remove(Keys.symptomIsDefault(id, symptomId))
        }
        editor.remove(Keys.symptomIds(id))
        editor.remove(Keys.discoveryMode(id))
    }

    private fun writeProfile(editor: SharedPreferences.Editor, profile: UserProfile) {
        editor.putString(Keys.displayName(profile.id), profile.displayName)
        editor.putBoolean(Keys.hasAsthma(profile.id), profile.hasAsthma)
        editor.putStringSet(
            Keys.trackedAllergens(profile.id),
            profile.trackedAllergens.keys.map { it.name }.toSet()
        )
        profile.trackedAllergens.forEach { (type, threshold) ->
            editor.putString(Keys.threshold(profile.id, type.name, "low"), threshold.low.toString())
            editor.putString(Keys.threshold(profile.id, type.name, "moderate"), threshold.moderate.toString())
            editor.putString(Keys.threshold(profile.id, type.name, "high"), threshold.high.toString())
            editor.putString(Keys.threshold(profile.id, type.name, "veryHigh"), threshold.veryHigh.toString())
        }
        val loc = profile.location
        if (loc != null) {
            editor.putString(Keys.locationLat(profile.id), loc.latitude.toString())
            editor.putString(Keys.locationLon(profile.id), loc.longitude.toString())
            editor.putString(Keys.locationName(profile.id), loc.displayName)
        } else {
            editor.remove(Keys.locationLat(profile.id))
            editor.remove(Keys.locationLon(profile.id))
            editor.remove(Keys.locationName(profile.id))
        }
        val medIds = profile.medicineAssignments.map { it.medicineId }.toSet()
        editor.putStringSet(Keys.medicineIds(profile.id), medIds)
        profile.medicineAssignments.forEach { assignment ->
            editor.putString(Keys.medDose(profile.id, assignment.medicineId), assignment.dose.toString())
            editor.putString(
                Keys.medTimesPerDay(profile.id, assignment.medicineId),
                assignment.timesPerDay.toString()
            )
            editor.putStringSet(
                Keys.medReminderHours(profile.id, assignment.medicineId),
                assignment.reminderHours.map { it.toString() }.toSet()
            )
        }
        val symptomIds = profile.trackedSymptoms.map { it.id }.toSet()
        editor.putStringSet(Keys.symptomIds(profile.id), symptomIds)
        profile.trackedSymptoms.forEach { symptom ->
            editor.putString(Keys.symptomName(profile.id, symptom.id), symptom.displayName)
            editor.putBoolean(Keys.symptomIsDefault(profile.id, symptom.id), symptom.isDefault)
        }
        editor.putBoolean(Keys.discoveryMode(profile.id), profile.discoveryMode)
    }

    private fun readProfile(prefs: SharedPreferences, id: String): UserProfile? {
        val name = prefs.getString(Keys.displayName(id), null) ?: return null
        val asthma = prefs.getBoolean(Keys.hasAsthma(id), false)
        val allergenNames = prefs.getStringSet(Keys.trackedAllergens(id), null) ?: emptySet()
        val trackedAllergens = allergenNames.mapNotNull { allergenName ->
            val type = PollenType.entries.find { it.name == allergenName } ?: return@mapNotNull null
            val threshold = AllergenThreshold(
                type = type,
                low = prefs.getString(Keys.threshold(id, allergenName, "low"), null)
                    ?.toDoubleOrNull() ?: return@mapNotNull null,
                moderate = prefs.getString(Keys.threshold(id, allergenName, "moderate"), null)
                    ?.toDoubleOrNull() ?: return@mapNotNull null,
                high = prefs.getString(Keys.threshold(id, allergenName, "high"), null)
                    ?.toDoubleOrNull() ?: return@mapNotNull null,
                veryHigh = prefs.getString(Keys.threshold(id, allergenName, "veryHigh"), null)
                    ?.toDoubleOrNull() ?: return@mapNotNull null
            )
            type to threshold
        }.toMap()

        val location = prefs.getString(Keys.locationLat(id), null)?.toDoubleOrNull()?.let { lat ->
            val lon = prefs.getString(Keys.locationLon(id), null)?.toDoubleOrNull() ?: return@let null
            val locName = prefs.getString(Keys.locationName(id), null) ?: ""
            ProfileLocation(lat, lon, locName)
        }

        val medIds = prefs.getStringSet(Keys.medicineIds(id), null) ?: emptySet()
        val medicineAssignments = medIds.mapNotNull { medId ->
            val dose = prefs.getString(Keys.medDose(id, medId), null)?.toIntOrNull()
                ?: return@mapNotNull null
            val timesPerDay = prefs.getString(Keys.medTimesPerDay(id, medId), null)?.toIntOrNull()
                ?: return@mapNotNull null
            val reminderHours = prefs.getStringSet(Keys.medReminderHours(id, medId), null)
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

        val trackedSymptoms = if (prefs.getStringSet(Keys.symptomIds(id), null) != null) {
            val sIds = prefs.getStringSet(Keys.symptomIds(id), null) ?: emptySet()
            sIds.mapNotNull { symptomId ->
                val sName = prefs.getString(Keys.symptomName(id, symptomId), null)
                    ?: return@mapNotNull null
                val isDefault = prefs.getBoolean(Keys.symptomIsDefault(id, symptomId), false)
                TrackedSymptom(id = symptomId, displayName = sName, isDefault = isDefault)
            }
        } else {
            UserProfile.defaultSymptoms()
        }

        val discoveryMode = prefs.getBoolean(Keys.discoveryMode(id), false)

        return UserProfile(
            id = id,
            displayName = name,
            trackedAllergens = trackedAllergens,
            hasAsthma = asthma,
            location = location,
            medicineAssignments = medicineAssignments,
            trackedSymptoms = trackedSymptoms,
            discoveryMode = discoveryMode
        )
    }

    companion object {
        fun resolveLocation(profile: UserProfile?, globalLocation: AppLocation): AppLocation {
            val loc = profile?.location ?: return globalLocation
            return AppLocation(loc.latitude, loc.longitude, loc.displayName)
        }
    }
}
