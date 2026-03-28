package com.ryan.pollenwitan.worker

import com.ryan.pollenwitan.data.repository.ProfileRepository
import com.ryan.pollenwitan.domain.model.AppLocation
import com.ryan.pollenwitan.domain.model.CurrentConditions
import com.ryan.pollenwitan.domain.model.DoseConfirmation
import com.ryan.pollenwitan.domain.model.MedicineAssignment
import com.ryan.pollenwitan.domain.model.PollenType
import com.ryan.pollenwitan.domain.model.SeverityClassifier
import com.ryan.pollenwitan.domain.model.SeverityLevel
import com.ryan.pollenwitan.domain.model.UserProfile
import java.time.LocalDate

internal object PollenCheckLogic {

    data class ThresholdBreach(
        val type: PollenType,
        val value: Double,
        val severity: SeverityLevel
    )

    /**
     * Groups profiles by their effective (lat, lon) to minimise API calls.
     */
    fun groupProfilesByLocation(
        profiles: List<UserProfile>,
        globalLocation: AppLocation
    ): Map<Pair<Double, Double>, List<UserProfile>> {
        return profiles.groupBy { profile ->
            val loc = ProfileRepository.resolveLocation(profile, globalLocation)
            loc.latitude to loc.longitude
        }
    }

    /**
     * Returns tracked allergens whose current reading reaches High or above.
     */
    fun findThresholdBreaches(
        profile: UserProfile,
        conditions: CurrentConditions
    ): List<ThresholdBreach> {
        return conditions.pollenReadings.mapNotNull { reading ->
            val threshold = profile.trackedAllergens[reading.type] ?: return@mapNotNull null
            val severity = SeverityClassifier.pollenSeverity(reading.value, threshold)
            if (severity >= SeverityLevel.High) {
                ThresholdBreach(reading.type, reading.value, severity)
            } else null
        }
    }

    /**
     * Returns true when an asthma profile faces both elevated pollen (>= Moderate
     * for any tracked allergen) and elevated particulates (PM2.5 > 25 or PM10 > 50).
     */
    fun hasCompoundRisk(
        profile: UserProfile,
        conditions: CurrentConditions
    ): Boolean {
        val hasElevatedPollen = conditions.pollenReadings.any { reading ->
            val threshold = profile.trackedAllergens[reading.type] ?: return@any false
            SeverityClassifier.pollenSeverity(reading.value, threshold) >= SeverityLevel.Moderate
        }
        val hasElevatedPm = conditions.pm25 > 25 || conditions.pm10 > 50
        return hasElevatedPollen && hasElevatedPm
    }

    /**
     * Decides whether the morning briefing should fire this run.
     */
    fun shouldSendMorningBriefing(
        enabled: Boolean,
        lastBriefingDate: LocalDate?,
        today: LocalDate,
        currentHour: Int,
        configuredHour: Int
    ): Boolean {
        return enabled && lastBriefingDate != today && currentHour >= configuredHour
    }

    /**
     * Returns true if a pre-season alert has not yet been sent this year for the given type.
     */
    fun shouldSendPreSeasonAlert(
        lastAlertYear: Int?,
        currentYear: Int
    ): Boolean {
        return lastAlertYear != currentYear
    }

    /**
     * Returns the medication reminder slots that should fire right now for a single profile.
     * Each result triple is (assignmentIndex, slotIndex, medicineId).
     */
    fun pendingMedicationReminders(
        assignments: List<MedicineAssignment>,
        confirmations: Set<DoseConfirmation>,
        currentHour: Int
    ): List<Triple<Int, Int, String>> {
        val pending = mutableListOf<Triple<Int, Int, String>>()
        assignments.forEachIndexed { assignmentIndex, assignment ->
            assignment.reminderHours.forEachIndexed { slotIndex, hour ->
                if (hour == currentHour) {
                    val isConfirmed = DoseConfirmation(assignment.medicineId, slotIndex) in confirmations
                    if (!isConfirmed) {
                        pending.add(Triple(assignmentIndex, slotIndex, assignment.medicineId))
                    }
                }
            }
        }
        return pending
    }
}
