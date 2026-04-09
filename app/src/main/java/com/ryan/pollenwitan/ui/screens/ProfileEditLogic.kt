package com.ryan.pollenwitan.ui.screens

import com.ryan.pollenwitan.domain.model.AllergenThreshold
import com.ryan.pollenwitan.domain.model.MedicineAssignment
import com.ryan.pollenwitan.domain.model.ProfileLocation
import com.ryan.pollenwitan.domain.model.UserProfile

internal object ProfileEditLogic {

    /** Validation limits shared across UI entry points. */
    const val MAX_DISPLAY_NAME_LENGTH = 50
    const val MAX_MEDICINE_NAME_LENGTH = 100
    const val MAX_CUSTOM_SYMPTOM_NAME_LENGTH = 100
    const val MAX_LOCATION_DISPLAY_NAME_LENGTH = 100
    const val MIN_THRESHOLD = 0.1
    const val MAX_THRESHOLD = 10_000.0
    const val MAX_DOSE = 999
    const val MAX_TIMES_PER_DAY = 24

    sealed interface ValidationResult {
        data object Valid : ValidationResult
        data class Invalid(val reason: ValidationReason) : ValidationResult
    }

    enum class ValidationReason {
        NameEmpty,
        NameTooLong,
        NoAllergenOrDiscovery,
        InvalidLatitude,
        InvalidLongitude,
        InvalidThreshold,
        InvalidDose,
        TooManyReminderHours,
        InvalidReminderHour
    }

    fun validate(state: ProfileEditUiState): ValidationResult {
        if (state.displayName.isBlank()) return ValidationResult.Invalid(ValidationReason.NameEmpty)
        if (state.displayName.length > MAX_DISPLAY_NAME_LENGTH) {
            return ValidationResult.Invalid(ValidationReason.NameTooLong)
        }
        if (!state.discoveryMode && state.trackedAllergens.isEmpty()) {
            return ValidationResult.Invalid(ValidationReason.NoAllergenOrDiscovery)
        }
        if (state.useCustomLocation) {
            val lat = state.locationLatitude.toDoubleOrNull()
            val lon = state.locationLongitude.toDoubleOrNull()
            if (lat == null || lat !in -90.0..90.0) {
                return ValidationResult.Invalid(ValidationReason.InvalidLatitude)
            }
            if (lon == null || lon !in -180.0..180.0) {
                return ValidationResult.Invalid(ValidationReason.InvalidLongitude)
            }
        }
        for ((_, threshold) in state.thresholds) {
            if (!isValidThreshold(threshold)) {
                return ValidationResult.Invalid(ValidationReason.InvalidThreshold)
            }
        }
        for (assignment in state.medicineAssignments) {
            val dose = assignment.dose.toIntOrNull()
            val times = assignment.timesPerDay.toIntOrNull()
            if (dose == null || dose !in 1..MAX_DOSE || times == null || times !in 1..MAX_TIMES_PER_DAY) {
                return ValidationResult.Invalid(ValidationReason.InvalidDose)
            }
            if (assignment.reminderHours.any { it !in 0..23 }) {
                return ValidationResult.Invalid(ValidationReason.InvalidReminderHour)
            }
            if (assignment.reminderHours.size > times) {
                return ValidationResult.Invalid(ValidationReason.TooManyReminderHours)
            }
        }
        return ValidationResult.Valid
    }

    fun isValidLatitude(value: Double): Boolean = value in -90.0..90.0
    fun isValidLongitude(value: Double): Boolean = value in -180.0..180.0

    fun isValidThreshold(threshold: AllergenThreshold): Boolean {
        val values = listOf(threshold.low, threshold.moderate, threshold.high, threshold.veryHigh)
        return values.all { it in MIN_THRESHOLD..MAX_THRESHOLD } &&
                threshold.low < threshold.moderate &&
                threshold.moderate < threshold.high &&
                threshold.high < threshold.veryHigh
    }

    fun resolveLocation(state: ProfileEditUiState): ProfileLocation? {
        if (!state.useCustomLocation) return null
        val lat = state.locationLatitude.toDoubleOrNull() ?: return null
        val lon = state.locationLongitude.toDoubleOrNull() ?: return null
        if (!isValidLatitude(lat) || !isValidLongitude(lon)) return null
        val name = state.locationDisplayName.take(MAX_LOCATION_DISPLAY_NAME_LENGTH).trim()
        if (name.isBlank()) return null
        return ProfileLocation(lat, lon, name)
    }

    fun buildMedicineAssignments(assignments: List<MedicineAssignmentUiState>): List<MedicineAssignment> {
        return assignments.mapNotNull { assignment ->
            val dose = assignment.dose.toIntOrNull() ?: return@mapNotNull null
            val timesPerDay = assignment.timesPerDay.toIntOrNull() ?: return@mapNotNull null
            if (dose !in 1..MAX_DOSE || timesPerDay !in 1..MAX_TIMES_PER_DAY) return@mapNotNull null
            MedicineAssignment(
                medicineId = assignment.medicineId,
                dose = dose,
                timesPerDay = timesPerDay,
                reminderHours = assignment.reminderHours
            )
        }
    }

    fun buildProfile(state: ProfileEditUiState): UserProfile {
        val location = resolveLocation(state)
        val medicineAssignments = buildMedicineAssignments(state.medicineAssignments)
        return UserProfile(
            id = state.profileId,
            displayName = state.displayName.take(MAX_DISPLAY_NAME_LENGTH).trim(),
            trackedAllergens = state.thresholds.filterKeys { it in state.trackedAllergens },
            hasAsthma = state.hasAsthma,
            location = location,
            medicineAssignments = medicineAssignments,
            trackedSymptoms = state.trackedSymptoms,
            discoveryMode = state.discoveryMode
        )
    }
}
