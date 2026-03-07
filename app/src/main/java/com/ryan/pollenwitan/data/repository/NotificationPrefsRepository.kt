package com.ryan.pollenwitan.data.repository

import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

data class NotificationPrefs(
    val morningBriefingEnabled: Boolean = true,
    val morningBriefingHour: Int = 7,
    val thresholdAlertsEnabled: Boolean = true,
    val compoundRiskAlertsEnabled: Boolean = true
)

interface NotificationPrefsRepository {
    fun getPrefs(): Flow<NotificationPrefs>
    suspend fun setMorningBriefingEnabled(enabled: Boolean)
    suspend fun setMorningBriefingHour(hour: Int)
    suspend fun setThresholdAlertsEnabled(enabled: Boolean)
    suspend fun setCompoundRiskAlertsEnabled(enabled: Boolean)
    suspend fun getLastBriefingDate(): LocalDate?
    suspend fun setLastBriefingDate(date: LocalDate)
}
