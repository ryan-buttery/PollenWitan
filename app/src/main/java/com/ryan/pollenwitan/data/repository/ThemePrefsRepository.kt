package com.ryan.pollenwitan.data.repository

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.themePrefsDataStore by preferencesDataStore(name = "theme_prefs")

class ThemePrefsRepository(
    private val context: Context
) {

    private val dataStore get() = context.themePrefsDataStore

    private object Keys {
        val IS_DARK_THEME = booleanPreferencesKey("is_dark_theme")
    }

    fun isDarkTheme(): Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[Keys.IS_DARK_THEME] ?: true
    }

    suspend fun setDarkTheme(isDark: Boolean) {
        dataStore.edit { it[Keys.IS_DARK_THEME] = isDark }
    }
}
