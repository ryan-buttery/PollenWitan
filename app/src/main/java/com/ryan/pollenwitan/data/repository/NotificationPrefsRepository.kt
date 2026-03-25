package com.ryan.pollenwitan.data.repository

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import com.ryan.pollenwitan.domain.model.PollenType
import java.time.LocalDate

data class NotificationPrefs(
    val morningBriefingEnabled: Boolean = true,
    val morningBriefingHour: Int = 7,
    val thresholdAlertsEnabled: Boolean = true,
    val compoundRiskAlertsEnabled: Boolean = true,
    val preSeasonAlertsEnabled: Boolean = true
)

private val Context.notificationPrefsDataStore by preferencesDataStore(name = "notification_prefs")

class NotificationPrefsRepository(
    private val context: Context
) {

    private val dataStore get() = context.notificationPrefsDataStore

    private object Keys {
        val MORNING_ENABLED = booleanPreferencesKey("morning_briefing_enabled")
        val MORNING_HOUR = intPreferencesKey("morning_briefing_hour")
        val THRESHOLD_ENABLED = booleanPreferencesKey("threshold_alerts_enabled")
        val COMPOUND_ENABLED = booleanPreferencesKey("compound_risk_enabled")
        val LAST_BRIEFING_DATE = stringPreferencesKey("last_briefing_date")
        val PRE_SEASON_ENABLED = booleanPreferencesKey("pre_season_alerts_enabled")
        fun preSeasonAlertYearKey(type: PollenType) =
            intPreferencesKey("preseason_alert_year_${type.name.lowercase()}")
    }

    fun getPrefs(): Flow<NotificationPrefs> = dataStore.data.map { prefs ->
        NotificationPrefs(
            morningBriefingEnabled = prefs[Keys.MORNING_ENABLED] ?: true,
            morningBriefingHour = prefs[Keys.MORNING_HOUR] ?: 7,
            thresholdAlertsEnabled = prefs[Keys.THRESHOLD_ENABLED] ?: true,
            compoundRiskAlertsEnabled = prefs[Keys.COMPOUND_ENABLED] ?: true,
            preSeasonAlertsEnabled = prefs[Keys.PRE_SEASON_ENABLED] ?: true
        )
    }

    suspend fun setMorningBriefingEnabled(enabled: Boolean) {
        dataStore.edit { it[Keys.MORNING_ENABLED] = enabled }
    }

    suspend fun setMorningBriefingHour(hour: Int) {
        dataStore.edit { it[Keys.MORNING_HOUR] = hour }
    }

    suspend fun setThresholdAlertsEnabled(enabled: Boolean) {
        dataStore.edit { it[Keys.THRESHOLD_ENABLED] = enabled }
    }

    suspend fun setCompoundRiskAlertsEnabled(enabled: Boolean) {
        dataStore.edit { it[Keys.COMPOUND_ENABLED] = enabled }
    }

    suspend fun getLastBriefingDate(): LocalDate? {
        val dateStr = dataStore.data.first()[Keys.LAST_BRIEFING_DATE] ?: return null
        return LocalDate.parse(dateStr)
    }

    suspend fun setLastBriefingDate(date: LocalDate) {
        dataStore.edit { it[Keys.LAST_BRIEFING_DATE] = date.toString() }
    }

    suspend fun setPreSeasonAlertsEnabled(enabled: Boolean) {
        dataStore.edit { it[Keys.PRE_SEASON_ENABLED] = enabled }
    }

    suspend fun getLastPreSeasonAlertYear(type: PollenType): Int? {
        return dataStore.data.first()[Keys.preSeasonAlertYearKey(type)]
    }

    suspend fun setLastPreSeasonAlertYear(type: PollenType, year: Int) {
        dataStore.edit { it[Keys.preSeasonAlertYearKey(type)] = year }
    }
}
