package com.ryan.pollenwitan.worker

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.ryan.pollenwitan.R
import com.ryan.pollenwitan.data.location.GpsLocationProvider
import com.ryan.pollenwitan.data.repository.AirQualityRepository
import com.ryan.pollenwitan.data.repository.DoseTrackingRepository
import com.ryan.pollenwitan.data.repository.LocationRepository
import com.ryan.pollenwitan.data.repository.MedicineRepository
import com.ryan.pollenwitan.data.repository.NotificationPrefsRepository
import com.ryan.pollenwitan.data.repository.ProfileRepository
import com.ryan.pollenwitan.data.repository.SymptomDiaryRepository
import com.ryan.pollenwitan.domain.model.CurrentConditions
import com.ryan.pollenwitan.domain.model.ForecastDay
import com.ryan.pollenwitan.domain.model.LocationMode
import com.ryan.pollenwitan.domain.model.SeverityClassifier
import com.ryan.pollenwitan.domain.model.UserProfile
import com.ryan.pollenwitan.domain.model.PollenSeasonCalendar
import com.ryan.pollenwitan.ui.navigation.Screen
import com.ryan.pollenwitan.ui.theme.localizedName
import androidx.glance.appwidget.updateAll
import com.ryan.pollenwitan.widget.PollenWidget
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
    private val symptomDiaryRepository = SymptomDiaryRepository(applicationContext)
    private val gpsLocationProvider = GpsLocationProvider(applicationContext)

    override suspend fun doWork(): Result {
        return try {
            doWorkInternal()
        } catch (e: Exception) {
            Log.e(TAG, "PollenCheckWorker failed — will retry", e)
            Result.retry()
        }
    }

    private suspend fun doWorkInternal(): Result {
        val ctx = applicationContext

        // Refresh GPS location if in GPS mode and enough time has elapsed
        refreshGpsIfDue(ctx)

        val prefs = notificationPrefsRepository.getPrefs().first()
        val globalLocation = locationRepository.getLocation().first()
        val profiles = profileRepository.getProfiles().first()

        // Group profiles by effective location to avoid redundant API calls
        val profilesByLocation = PollenCheckLogic.groupProfilesByLocation(profiles, globalLocation)

        val now = LocalDateTime.now()
        val multiProfile = profiles.size > 1

        // Morning briefing: send once per day, on the first run at or after the configured hour
        val today = now.toLocalDate()
        val lastBriefingDate = notificationPrefsRepository.getLastBriefingDate()
        val shouldSendBriefing = PollenCheckLogic.shouldSendMorningBriefing(
            enabled = prefs.morningBriefingEnabled,
            lastBriefingDate = lastBriefingDate,
            today = today,
            currentHour = now.hour,
            configuredHour = prefs.morningBriefingHour
        )

        if (shouldSendBriefing) {
            notificationPrefsRepository.setLastBriefingDate(today)
        }

        // Fetch conditions once per unique location
        val conditionsByLocation = mutableMapOf<Pair<Double, Double>, CurrentConditions>()
        val forecastByLocation = mutableMapOf<Pair<Double, Double>, ForecastDay?>()
        for ((coords, _) in profilesByLocation) {
            val result = airQualityRepository.getCurrentConditions(coords.first, coords.second)
            val conditions = result.getOrNull() ?: return Result.retry()
            conditionsByLocation[coords] = conditions

            if (shouldSendBriefing) {
                val forecastResult = airQualityRepository.getForecast(coords.first, coords.second, days = 1)
                forecastByLocation[coords] = forecastResult.getOrNull()
                    ?.find { it.date == today }
            }
        }

        var briefingCount = 0
        var thresholdCount = 0
        var compoundCount = 0

        profiles.forEachIndexed { index, profile ->
            val loc = ProfileRepository.resolveLocation(profile, globalLocation)
            val conditions = conditionsByLocation[loc.latitude to loc.longitude] ?: return@forEachIndexed

            // Morning briefing
            if (shouldSendBriefing) {
                val todayForecast = forecastByLocation[loc.latitude to loc.longitude]
                val summary = buildMorningSummary(ctx, profile, conditions, todayForecast)
                NotificationHelper.sendNotification(
                    context = ctx,
                    channelId = NotificationHelper.CHANNEL_MORNING_BRIEFING,
                    notificationId = MORNING_BRIEFING_BASE_ID + index,
                    title = ctx.getString(R.string.notif_good_morning, profile.displayName),
                    text = summary,
                    targetRoute = Screen.Dashboard.route,
                    groupKey = if (multiProfile) NotificationHelper.GROUP_MORNING_BRIEFING else null
                )
                briefingCount++
            }

            // Threshold breach alerts
            if (prefs.thresholdAlertsEnabled) {
                val breaches = PollenCheckLogic.findThresholdBreaches(profile, conditions)
                if (breaches.isNotEmpty()) {
                    val text = breaches.joinToString(". ") { breach ->
                        ctx.getString(R.string.notif_threshold_breach, breach.type.localizedName(ctx), breach.severity.toLabel(ctx), String.format("%.0f", breach.value))
                    }
                    NotificationHelper.sendNotification(
                        context = ctx,
                        channelId = NotificationHelper.CHANNEL_THRESHOLD_ALERT,
                        notificationId = THRESHOLD_ALERT_BASE_ID + index,
                        title = ctx.getString(R.string.notif_high_pollen_alert, profile.displayName),
                        text = text,
                        targetRoute = Screen.Dashboard.route,
                        groupKey = if (multiProfile) NotificationHelper.GROUP_THRESHOLD_ALERT else null
                    )
                    thresholdCount++
                }
            }

            // Compound risk alerts (asthma profiles only)
            if (prefs.compoundRiskAlertsEnabled && profile.hasAsthma) {
                val hasRisk = PollenCheckLogic.hasCompoundRisk(profile, conditions)
                if (hasRisk) {
                    val compoundRisk = formatCompoundRisk(ctx, profile, conditions)
                    NotificationHelper.sendNotification(
                        context = ctx,
                        channelId = NotificationHelper.CHANNEL_COMPOUND_RISK,
                        notificationId = COMPOUND_RISK_BASE_ID + index,
                        title = ctx.getString(R.string.notif_respiratory_risk, profile.displayName),
                        text = compoundRisk,
                        targetRoute = Screen.Dashboard.route,
                        groupKey = if (multiProfile) NotificationHelper.GROUP_COMPOUND_RISK else null
                    )
                    compoundCount++
                }
            }
        }

        // Send group summaries for multi-profile households
        if (multiProfile) {
            if (briefingCount > 1) {
                NotificationHelper.sendGroupSummary(
                    context = ctx,
                    channelId = NotificationHelper.CHANNEL_MORNING_BRIEFING,
                    groupKey = NotificationHelper.GROUP_MORNING_BRIEFING,
                    summaryId = NotificationHelper.GROUP_SUMMARY_MORNING_ID,
                    title = ctx.getString(R.string.notif_group_morning_briefing, briefingCount),
                    targetRoute = Screen.Dashboard.route
                )
            }
            if (thresholdCount > 1) {
                NotificationHelper.sendGroupSummary(
                    context = ctx,
                    channelId = NotificationHelper.CHANNEL_THRESHOLD_ALERT,
                    groupKey = NotificationHelper.GROUP_THRESHOLD_ALERT,
                    summaryId = NotificationHelper.GROUP_SUMMARY_THRESHOLD_ID,
                    title = ctx.getString(R.string.notif_group_threshold_alert, thresholdCount),
                    targetRoute = Screen.Dashboard.route
                )
            }
            if (compoundCount > 1) {
                NotificationHelper.sendGroupSummary(
                    context = ctx,
                    channelId = NotificationHelper.CHANNEL_COMPOUND_RISK,
                    groupKey = NotificationHelper.GROUP_COMPOUND_RISK,
                    summaryId = NotificationHelper.GROUP_SUMMARY_COMPOUND_ID,
                    title = ctx.getString(R.string.notif_group_compound_risk, compoundCount),
                    targetRoute = Screen.Dashboard.route
                )
            }
        }

        // Medication reminders
        val currentHour = now.hour
        val medicines = medicineRepository.getMedicines().first()
        var medicationCount = 0
        profiles.forEachIndexed { index, profile ->
            if (profile.medicineAssignments.isEmpty()) return@forEachIndexed
            val confirmations = doseTrackingRepository.getConfirmations(profile.id).first()
            val pending = PollenCheckLogic.pendingMedicationReminders(
                profile.medicineAssignments, confirmations, currentHour
            )
            for ((assignmentIndex, slotIndex, medicineId) in pending) {
                val assignment = profile.medicineAssignments[assignmentIndex]
                val medicine = medicines.find { it.id == medicineId } ?: continue
                val notifId = MEDICATION_REMINDER_BASE_ID + index * 100 + assignmentIndex * 10 + slotIndex
                NotificationHelper.sendMedicationReminder(
                    context = ctx,
                    notificationId = notifId,
                    title = ctx.getString(R.string.notif_medication_reminder, profile.displayName),
                    text = ctx.getString(R.string.notif_time_to_take, medicine.name, assignment.dose, medicine.type.localizedUnitLabel(ctx)),
                    profileId = profile.id,
                    medicineId = medicineId,
                    slotIndex = slotIndex,
                    medicineName = medicine.name,
                    dose = assignment.dose,
                    medicineType = medicine.type.name,
                    reminderHour = assignment.reminderHours[slotIndex],
                    profileIndex = index,
                    assignmentIndex = assignmentIndex,
                    targetRoute = Screen.Dashboard.route,
                    groupKey = if (multiProfile) NotificationHelper.GROUP_MEDICATION_REMINDER else null
                )
                // Schedule missed-dose escalation alarm
                if (prefs.missedDoseEscalationEnabled) {
                    MissedDoseAlarmReceiver.schedule(
                        context = ctx,
                        profileIndex = index,
                        profileId = profile.id,
                        profileName = profile.displayName,
                        medicineId = medicineId,
                        medicineName = medicine.name,
                        assignmentIndex = assignmentIndex,
                        slotIndex = slotIndex,
                        windowMinutes = prefs.missedDoseWindowMinutes
                    )
                }
                medicationCount++
            }
        }

        if (multiProfile && medicationCount > 1) {
            NotificationHelper.sendGroupSummary(
                context = ctx,
                channelId = NotificationHelper.CHANNEL_MEDICATION_REMINDER,
                groupKey = NotificationHelper.GROUP_MEDICATION_REMINDER,
                summaryId = NotificationHelper.GROUP_SUMMARY_MEDICATION_ID,
                title = ctx.getString(R.string.notif_group_medication, medicationCount),
                targetRoute = Screen.Dashboard.route
            )
        }

        // Pre-season medication alerts
        if (prefs.preSeasonAlertsEnabled) {
            profiles.forEachIndexed { index, profile ->
                if (profile.medicineAssignments.isEmpty()) return@forEachIndexed
                val alertTypes = PollenSeasonCalendar.preSeasonAlerts(
                    profile.trackedAllergens.keys, today
                )
                for (type in alertTypes) {
                    val lastYear = notificationPrefsRepository.getLastPreSeasonAlertYear(type)
                    if (!PollenCheckLogic.shouldSendPreSeasonAlert(lastYear, today.year)) continue
                    notificationPrefsRepository.setLastPreSeasonAlertYear(type, today.year)
                    val seasonDate = PollenSeasonCalendar.seasonStartDisplay(type, today)
                    NotificationHelper.sendNotification(
                        context = ctx,
                        channelId = NotificationHelper.CHANNEL_MEDICATION_REMINDER,
                        notificationId = PRE_SEASON_ALERT_BASE_ID + index * 10 + type.ordinal,
                        title = ctx.getString(R.string.notif_pre_season_title, profile.displayName),
                        text = ctx.getString(R.string.notif_pre_season_text, type.localizedName(ctx), seasonDate),
                        targetRoute = Screen.Dashboard.route
                    )
                }
            }
        }

        // Symptom check-in reminder
        var symptomCount = 0
        if (prefs.symptomReminderEnabled && currentHour == prefs.symptomReminderHour) {
            profiles.forEachIndexed { index, profile ->
                val todayEntry = symptomDiaryRepository.getEntryForDate(profile.id, today)
                if (todayEntry == null) {
                    NotificationHelper.sendNotification(
                        context = ctx,
                        channelId = NotificationHelper.CHANNEL_SYMPTOM_REMINDER,
                        notificationId = SYMPTOM_REMINDER_BASE_ID + index,
                        title = ctx.getString(R.string.notif_symptom_reminder_title, profile.displayName),
                        text = ctx.getString(R.string.notif_symptom_reminder_text),
                        targetRoute = Screen.SymptomCheckIn.createRoute(),
                        groupKey = if (multiProfile) NotificationHelper.GROUP_SYMPTOM_REMINDER else null
                    )
                    symptomCount++
                }
            }

            if (multiProfile && symptomCount > 1) {
                NotificationHelper.sendGroupSummary(
                    context = ctx,
                    channelId = NotificationHelper.CHANNEL_SYMPTOM_REMINDER,
                    groupKey = NotificationHelper.GROUP_SYMPTOM_REMINDER,
                    summaryId = NotificationHelper.GROUP_SUMMARY_SYMPTOM_ID,
                    title = ctx.getString(R.string.notif_group_symptom_reminder, symptomCount),
                    targetRoute = Screen.SymptomCheckIn.createRoute()
                )
            }
        }

        // Refresh home screen widgets with latest data
        PollenWidget().updateAll(applicationContext)

        return Result.success()
    }

    private fun buildMorningSummary(
        ctx: Context,
        profile: UserProfile,
        conditions: CurrentConditions,
        todayForecast: ForecastDay?
    ): String {
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

        // Peak daily values for tracked allergens
        if (todayForecast != null) {
            val peakParts = todayForecast.peakPollenReadings
                .filter { it.type in profile.trackedAllergens && it.value > 0 }
                .map { "${it.type.localizedName(ctx)} ${String.format("%.0f", it.value)}" }
            if (peakParts.isNotEmpty()) {
                parts.add(ctx.getString(R.string.notif_peak_today, peakParts.joinToString(", ")))
            }
        }

        parts.add(ctx.getString(R.string.notif_aqi_summary, conditions.europeanAqi, conditions.aqiSeverity.toLabel(ctx)))

        if (profile.hasAsthma && (conditions.pm25 > 25 || conditions.pm10 > 50)) {
            parts.add(ctx.getString(R.string.notif_elevated_particulates))
        }

        return parts.joinToString(". ") + "."
    }

    private fun formatCompoundRisk(
        ctx: Context,
        profile: UserProfile,
        conditions: CurrentConditions
    ): String {
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

    private suspend fun refreshGpsIfDue(ctx: Context) {
        val mode = locationRepository.getLocationMode().first()
        if (mode != LocationMode.Gps) return
        if (!locationRepository.isGpsRefreshDue(GPS_REFRESH_INTERVAL_MS)) return

        val hasPermission = ContextCompat.checkSelfPermission(
            ctx, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        if (!hasPermission) return

        val location = gpsLocationProvider.requestSingleUpdate() ?: return
        locationRepository.updateGpsLocation(location.latitude, location.longitude, location.displayName)
    }

    companion object {
        private const val TAG = "PollenCheckWorker"
        const val WORK_NAME = "pollen_check"
        private const val GPS_REFRESH_INTERVAL_MS = 6 * 60 * 60 * 1000L // 6 hours
        private const val MORNING_BRIEFING_BASE_ID = 1000
        private const val THRESHOLD_ALERT_BASE_ID = 2000
        private const val COMPOUND_RISK_BASE_ID = 3000
        private const val MEDICATION_REMINDER_BASE_ID = 4000
        private const val PRE_SEASON_ALERT_BASE_ID = 5000
        private const val SYMPTOM_REMINDER_BASE_ID = 6000
    }
}
