package com.ryan.pollenwitan.data.repository

import android.content.Context
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
import kotlinx.coroutines.flow.onStart

private val Context.profileDataStore by preferencesDataStore(name = "profiles")

class ProfileRepositoryImpl(
    private val context: Context
) : ProfileRepository {

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

    override fun getProfiles(): Flow<List<UserProfile>> = dataStore.data
        .onStart { seedDefaultsIfNeeded() }
        .map { prefs ->
            val ids = prefs[Keys.PROFILE_IDS] ?: return@map emptyList()
            ids.mapNotNull { id -> readProfile(prefs, id) }
        }

    override fun getSelectedProfileId(): Flow<String> = dataStore.data.map { prefs ->
        prefs[Keys.SELECTED_PROFILE] ?: UserProfile.Ryan.id
    }

    override suspend fun selectProfile(profileId: String) {
        dataStore.edit { prefs ->
            prefs[Keys.SELECTED_PROFILE] = profileId
        }
    }

    private suspend fun seedDefaultsIfNeeded() {
        dataStore.edit { prefs ->
            if (prefs[Keys.PROFILE_IDS] != null) return@edit
            val defaults = listOf(UserProfile.Ryan, UserProfile.Olga)
            prefs[Keys.PROFILE_IDS] = defaults.map { it.id }.toSet()
            defaults.forEach { profile -> writeProfile(prefs, profile) }
            prefs[Keys.SELECTED_PROFILE] = UserProfile.Ryan.id
        }
    }

    private fun writeProfile(
        prefs: androidx.datastore.preferences.core.MutablePreferences,
        profile: UserProfile
    ) {
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
