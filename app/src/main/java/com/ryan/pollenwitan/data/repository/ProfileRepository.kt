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
import com.ryan.pollenwitan.domain.model.PollenType
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
        val levels = listOf("low", "moderate", "high", "veryHigh")
        for (type in PollenType.entries) {
            for (level in levels) {
                prefs.remove(Keys.threshold(id, type.name, level))
            }
        }
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

        return UserProfile(
            id = id,
            displayName = name,
            trackedAllergens = trackedAllergens,
            hasAsthma = asthma
        )
    }
}
