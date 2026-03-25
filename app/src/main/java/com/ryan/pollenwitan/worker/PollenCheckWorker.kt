package com.ryan.pollenwitan.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.ryan.pollenwitan.R
import com.ryan.pollenwitan.data.repository.AirQualityRepository
import com.ryan.pollenwitan.data.repository.DoseTrackingRepository
import com.ryan.pollenwitan.data.repository.LocationRepository
import com.ryan.pollenwitan.data.repository.MedicineRepository
import com.ryan.pollenwitan.data.repository.NotificationPrefsRepository
import com.ryan.pollenwitan.data.repository.ProfileRepository
import com.ryan.pollenwitan.domain.model.CurrentConditions
import com.ryan.pollenwitan.domain.model.DoseConfirmation
import com.ryan.pollenwitan.domain.model.SeverityClassifier
import com.ryan.pollenwitan.domain.model.SeverityLevel
import com.ryan.pollenwitan.domain.model.UserProfile
import com.ryan.pollenwitan.ui.theme.localizedName
import com.ryan.pollenwitan.ui.theme.localizedUnitLabel
import com.ryan.pollenwitan.ui.theme.toLabel
import kotlinx.coroutines.flow.first
import java.time.LocalDateTime

class PollenCheckWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    private val airQualityRepository = AirQualityRepository(applicationContext)
    private val profileRepository = ProfileRepository(applicationContext)
    private val locationRepository = LocationRepository(applicationContext)
    private val notificationPrefsRepository = NotificationPrefsRepository(applicationContext)
    private val medicineRepository = MedicineRepository(applicationContext)
    private val doseTrackingRepository = DoseTrackingRepository(applicationContext)

    override suspend fun doWork(): Result {
        val ctx = applicationContext
        val prefs = notificationPrefsRepository.getPrefs().first()
        val globalLocation = locationRepository.getLocation().first()
        val profiles = profileRepository.getProfiles().first()

        // Group profiles by effective location to avoid redundant API calls
        val profilesByLocation = profiles.groupBy { profile ->
            val loc = ProfileRepository.resolveLocation(profile, globalLocation)
            loc.latitude to loc.longitude
        }

        // Fetch conditions once per unique location
        val conditionsByLocation = mutableMapOf<Pair<Double, Double>, CurrentConditions>()
        for ((coords, _) in profilesByLocation) {
            val result = airQualityRepository.getCurrentConditions(coords.first, coords.second)
            val conditions = result.getOrNull() ?: return Result.retry()
            conditionsByLocation[coords] = conditions
        }

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
            val loc = ProfileRepository.resolveLocation(profile, globalLocation)
            val conditions = conditionsByLocation[loc.latitude to loc.longitude] ?: return@forEachIndexed

            // Morning briefing
            if (shouldSendBriefing) {
                val summary = buildMorningSummary(ctx, profile, conditions)
                NotificationHelper.sendNotification(
                    context = ctx,
                    channelId = NotificationHelper.CHANNEL_MORNING_BRIEFING,
                    notificationId = MORNING_BRIEFING_BASE_ID + index,
                    title = ctx.getString(R.string.notif_good_morning, profile.displayName),
                    text = summary
                )
            }

            // Threshold breach alerts
            if (prefs.thresholdAlertsEnabled) {
                val breaches = findThresholdBreaches(ctx, profile, conditions)
                if (breaches.isNotEmpty()) {
                    val text = breaches.joinToString(". ") { breach ->
                        ctx.getString(R.string.notif_threshold_breach, breach.typeName, breach.severityLabel, String.format("%.0f", breach.value))
                    }
                    NotificationHelper.sendNotification(
                        context = ctx,
                        channelId = NotificationHelper.CHANNEL_THRESHOLD_ALERT,
                        notificationId = THRESHOLD_ALERT_BASE_ID + index,
                        title = ctx.getString(R.string.notif_high_pollen_alert, profile.displayName),
                        text = text
                    )
                }
            }

            // Compound risk alerts (asthma profiles only)
            if (prefs.compoundRiskAlertsEnabled && profile.hasAsthma) {
                val compoundRisk = checkCompoundRisk(ctx, profile, conditions)
                if (compoundRisk != null) {
                    NotificationHelper.sendNotification(
                        context = ctx,
                        channelId = NotificationHelper.CHANNEL_COMPOUND_RISK,
                        notificationId = COMPOUND_RISK_BASE_ID + index,
                        title = ctx.getString(R.string.notif_respiratory_risk, profile.displayName),
                        text = compoundRisk
                    )
                }
            }
        }

        // Medication reminders
        val currentHour = now.hour
        val medicines = medicineRepository.getMedicines().first()
        profiles.forEachIndexed { index, profile ->
            if (profile.medicineAssignments.isEmpty()) return@forEachIndexed
            val confirmations = doseTrackingRepository.getConfirmations(profile.id).first()
            profile.medicineAssignments.forEachIndexed { assignmentIndex, assignment ->
                val medicine = medicines.find { it.id == assignment.medicineId } ?: return@forEachIndexed
                assignment.reminderHours.forEachIndexed { slotIndex, hour ->
                    if (hour == currentHour) {
                        val isConfirmed = DoseConfirmation(assignment.medicineId, slotIndex) in confirmations
                        if (!isConfirmed) {
                            NotificationHelper.sendNotification(
                                context = ctx,
                                channelId = NotificationHelper.CHANNEL_MEDICATION_REMINDER,
                                notificationId = MEDICATION_REMINDER_BASE_ID + index * 100 + assignmentIndex * 10 + slotIndex,
                                title = ctx.getString(R.string.notif_medication_reminder, profile.displayName),
                                text = ctx.getString(R.string.notif_time_to_take, medicine.name, assignment.dose, medicine.type.localizedUnitLabel(ctx))
                            )
                        }
                    }
                }
            }
        }

        return Result.success()
    }

    private fun buildMorningSummary(ctx: Context, profile: UserProfile, conditions: CurrentConditions): String {
        val parts = mutableListOf<String>()

        val trackedReadings = conditions.pollenReadings.filter { it.type in profile.trackedAllergens }
        if (trackedReadings.isNotEmpty()) {
            val pollenParts = trackedReadings.map { reading ->
                val threshold = profile.trackedAllergens[reading.type]!!
                val severity = SeverityClassifier.pollenSeverity(reading.value, threshold)
                "${reading.type.localizedName(ctx)}: ${severity.toLabel(ctx)}"
            }
            parts.add(ctx.getString(R.string.notif_pollen_summary, pollenParts.joinToString(", ")))
        } else {
            parts.add(ctx.getString(R.string.notif_no_tracked_allergens))
        }

        parts.add(ctx.getString(R.string.notif_aqi_summary, conditions.europeanAqi, conditions.aqiSeverity.toLabel(ctx)))

        if (profile.hasAsthma && (conditions.pm25 > 25 || conditions.pm10 > 50)) {
            parts.add(ctx.getString(R.string.notif_elevated_particulates))
        }

        return parts.joinToString(". ") + "."
    }

    private data class ThresholdBreach(
        val typeName: String,
        val value: Double,
        val severityLabel: String
    )

    private fun findThresholdBreaches(
        ctx: Context,
        profile: UserProfile,
        conditions: CurrentConditions
    ): List<ThresholdBreach> {
        return conditions.pollenReadings.mapNotNull { reading ->
            val threshold = profile.trackedAllergens[reading.type] ?: return@mapNotNull null
            val severity = SeverityClassifier.pollenSeverity(reading.value, threshold)
            if (severity >= SeverityLevel.High) {
                ThresholdBreach(reading.type.localizedName(ctx), reading.value, severity.toLabel(ctx))
            } else null
        }
    }

    private fun checkCompoundRisk(
        ctx: Context,
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
                .joinToString(", ") { "${it.type.localizedName(ctx)}: ${String.format("%.0f", it.value)}" }
            return ctx.getString(
                R.string.notif_compound_risk_text,
                pollenSummary,
                String.format("%.1f", conditions.pm25),
                String.format("%.1f", conditions.pm10)
            )
        }
        return null
    }

    companion object {
        const val WORK_NAME = "pollen_check"
        private const val MORNING_BRIEFING_BASE_ID = 1000
        private const val THRESHOLD_ALERT_BASE_ID = 2000
        private const val COMPOUND_RISK_BASE_ID = 3000
        private const val MEDICATION_REMINDER_BASE_ID = 4000
    }
}
