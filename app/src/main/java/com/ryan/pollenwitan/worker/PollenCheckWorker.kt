package com.ryan.pollenwitan.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.ryan.pollenwitan.data.repository.AirQualityRepository
import com.ryan.pollenwitan.data.repository.LocationRepository
import com.ryan.pollenwitan.data.repository.NotificationPrefsRepository
import com.ryan.pollenwitan.data.repository.ProfileRepository
import com.ryan.pollenwitan.domain.model.CurrentConditions
import com.ryan.pollenwitan.domain.model.SeverityClassifier
import com.ryan.pollenwitan.domain.model.SeverityLevel
import com.ryan.pollenwitan.domain.model.UserProfile
import kotlinx.coroutines.flow.first
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.time.LocalDate
import java.time.LocalDateTime

class PollenCheckWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params), KoinComponent {

    private val airQualityRepository: AirQualityRepository by inject()
    private val profileRepository: ProfileRepository by inject()
    private val locationRepository: LocationRepository by inject()
    private val notificationPrefsRepository: NotificationPrefsRepository by inject()

    override suspend fun doWork(): Result {
        val prefs = notificationPrefsRepository.getPrefs().first()
        val location = locationRepository.getLocation().first()
        val profiles = profileRepository.getProfiles().first()

        val conditions = airQualityRepository.getCurrentConditions(
            location.latitude, location.longitude
        ).getOrNull() ?: return Result.retry()

        val now = LocalDateTime.now()

        // Morning briefing: send once per day, on the first run at or after the configured hour
        val today = now.toLocalDate()
        val lastBriefingDate = notificationPrefsRepository.getLastBriefingDate()
        val shouldSendBriefing = prefs.morningBriefingEnabled &&
            lastBriefingDate != today &&
            now.hour >= prefs.morningBriefingHour

        if (shouldSendBriefing) {
            notificationPrefsRepository.setLastBriefingDate(today)
        }

        profiles.forEachIndexed { index, profile ->
            // Morning briefing
            if (shouldSendBriefing) {
                val summary = buildMorningSummary(profile, conditions)
                NotificationHelper.sendNotification(
                    context = applicationContext,
                    channelId = NotificationHelper.CHANNEL_MORNING_BRIEFING,
                    notificationId = MORNING_BRIEFING_BASE_ID + index,
                    title = "Good morning, ${profile.displayName}",
                    text = summary
                )
            }

            // Threshold breach alerts
            if (prefs.thresholdAlertsEnabled) {
                val breaches = findThresholdBreaches(profile, conditions)
                if (breaches.isNotEmpty()) {
                    val text = breaches.joinToString(". ") { breach ->
                        "${breach.typeName}: ${breach.severityLabel} (${String.format("%.0f", breach.value)} grains/m\u00B3)"
                    }
                    NotificationHelper.sendNotification(
                        context = applicationContext,
                        channelId = NotificationHelper.CHANNEL_THRESHOLD_ALERT,
                        notificationId = THRESHOLD_ALERT_BASE_ID + index,
                        title = "${profile.displayName}: High pollen alert",
                        text = text
                    )
                }
            }

            // Compound risk alerts (asthma profiles only)
            if (prefs.compoundRiskAlertsEnabled && profile.hasAsthma) {
                val compoundRisk = checkCompoundRisk(profile, conditions)
                if (compoundRisk != null) {
                    NotificationHelper.sendNotification(
                        context = applicationContext,
                        channelId = NotificationHelper.CHANNEL_COMPOUND_RISK,
                        notificationId = COMPOUND_RISK_BASE_ID + index,
                        title = "${profile.displayName}: Respiratory risk",
                        text = compoundRisk
                    )
                }
            }
        }

        return Result.success()
    }

    private fun buildMorningSummary(profile: UserProfile, conditions: CurrentConditions): String {
        val parts = mutableListOf<String>()

        val trackedReadings = conditions.pollenReadings.filter { it.type in profile.trackedAllergens }
        if (trackedReadings.isNotEmpty()) {
            val pollenParts = trackedReadings.map { reading ->
                val threshold = profile.trackedAllergens[reading.type]!!
                val severity = SeverityClassifier.pollenSeverity(reading.value, threshold)
                "${reading.type.displayName}: ${severity.label}"
            }
            parts.add("Pollen: ${pollenParts.joinToString(", ")}")
        } else {
            parts.add("No tracked allergens today")
        }

        parts.add("AQI: ${conditions.europeanAqi} (${conditions.aqiSeverity.label})")

        if (profile.hasAsthma && (conditions.pm25 > 25 || conditions.pm10 > 50)) {
            parts.add("Elevated particulates \u2014 consider precautions")
        }

        return parts.joinToString(". ") + "."
    }

    private data class ThresholdBreach(
        val typeName: String,
        val value: Double,
        val severityLabel: String
    )

    private fun findThresholdBreaches(
        profile: UserProfile,
        conditions: CurrentConditions
    ): List<ThresholdBreach> {
        return conditions.pollenReadings.mapNotNull { reading ->
            val threshold = profile.trackedAllergens[reading.type] ?: return@mapNotNull null
            val severity = SeverityClassifier.pollenSeverity(reading.value, threshold)
            if (severity >= SeverityLevel.High) {
                ThresholdBreach(reading.type.displayName, reading.value, severity.label)
            } else null
        }
    }

    private fun checkCompoundRisk(
        profile: UserProfile,
        conditions: CurrentConditions
    ): String? {
        val hasElevatedPollen = conditions.pollenReadings.any { reading ->
            val threshold = profile.trackedAllergens[reading.type] ?: return@any false
            SeverityClassifier.pollenSeverity(reading.value, threshold) >= SeverityLevel.Moderate
        }
        val hasElevatedPm = conditions.pm25 > 25 || conditions.pm10 > 50

        if (hasElevatedPollen && hasElevatedPm) {
            val pollenSummary = conditions.pollenReadings
                .filter { it.type in profile.trackedAllergens }
                .joinToString(", ") { "${it.type.displayName}: ${String.format("%.0f", it.value)}" }
            return "Elevated pollen ($pollenSummary) combined with poor air quality " +
                "(PM2.5: ${String.format("%.1f", conditions.pm25)}, PM10: ${String.format("%.1f", conditions.pm10)}). " +
                "Take extra precautions."
        }
        return null
    }

    companion object {
        const val WORK_NAME = "pollen_check"
        private const val MORNING_BRIEFING_BASE_ID = 1000
        private const val THRESHOLD_ALERT_BASE_ID = 2000
        private const val COMPOUND_RISK_BASE_ID = 3000
    }
}

private val SeverityLevel.label: String
    get() = when (this) {
        SeverityLevel.None -> "None"
        SeverityLevel.Low -> "Low"
        SeverityLevel.Moderate -> "Moderate"
        SeverityLevel.High -> "High"
        SeverityLevel.VeryHigh -> "Very High"
    }
