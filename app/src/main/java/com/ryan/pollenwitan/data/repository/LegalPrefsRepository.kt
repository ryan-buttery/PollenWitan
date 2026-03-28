package com.ryan.pollenwitan.data.repository

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.legalPrefsDataStore by preferencesDataStore(name = "legal_prefs")

class LegalPrefsRepository(
    private val context: Context
) {

    private val dataStore get() = context.legalPrefsDataStore

    private object Keys {
        val LEGAL_ACCEPTED = booleanPreferencesKey("legal_accepted")
        val LEGAL_ACCEPTED_VERSION = intPreferencesKey("legal_accepted_version")
    }

    fun isDisclaimerAccepted(): Flow<Boolean> = dataStore.data.map { prefs ->
        val accepted = prefs[Keys.LEGAL_ACCEPTED] ?: false
        val version = prefs[Keys.LEGAL_ACCEPTED_VERSION] ?: 0
        accepted && version == CURRENT_DISCLAIMER_VERSION
    }

    suspend fun acceptDisclaimer() {
        dataStore.edit {
            it[Keys.LEGAL_ACCEPTED] = true
            it[Keys.LEGAL_ACCEPTED_VERSION] = CURRENT_DISCLAIMER_VERSION
        }
    }

    companion object {
        const val CURRENT_DISCLAIMER_VERSION = 1
    }
}
