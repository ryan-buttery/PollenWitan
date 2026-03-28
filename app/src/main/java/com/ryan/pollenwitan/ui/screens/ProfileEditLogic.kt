package com.ryan.pollenwitan.ui.screens

import com.ryan.pollenwitan.domain.model.MedicineAssignment
import com.ryan.pollenwitan.domain.model.ProfileLocation
import com.ryan.pollenwitan.domain.model.UserProfile

internal object ProfileEditLogic {

    sealed interface ValidationResult {
        data object Valid : ValidationResult
        data class Invalid(val reason: ValidationReason) : ValidationResult
    }

    enum class ValidationReason {
        NameEmpty,
        NoAllergenOrDiscovery
    }

    fun validate(state: ProfileEditUiState): ValidationResult {
        if (state.displayName.isBlank()) return ValidationResult.Invalid(ValidationReason.NameEmpty)
        if (!state.discoveryMode && state.trackedAllergens.isEmpty()) {
            return ValidationResult.Invalid(ValidationReason.NoAllergenOrDiscovery)
        }
        return ValidationResult.Valid
    }

    fun resolveLocation(state: ProfileEditUiState): ProfileLocation? {
        if (!state.useCustomLocation) return null
        val lat = state.locationLatitude.toDoubleOrNull() ?: return null
        val lon = state.locationLongitude.toDoubleOrNull() ?: return null
        if (state.locationDisplayName.isBlank()) return null
        return ProfileLocation(lat, lon, state.locationDisplayName.trim())
    }

    fun buildMedicineAssignments(assignments: List<MedicineAssignmentUiState>): List<MedicineAssignment> {
        return assignments.mapNotNull { assignment ->
            val dose = assignment.dose.toIntOrNull() ?: return@mapNotNull null
            val timesPerDay = assignment.timesPerDay.toIntOrNull() ?: return@mapNotNull null
            if (dose <= 0 || timesPerDay <= 0) return@mapNotNull null
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
            displayName = state.displayName.trim(),
            trackedAllergens = state.thresholds.filterKeys { it in state.trackedAllergens },
            hasAsthma = state.hasAsthma,
            location = location,
            medicineAssignments = medicineAssignments,
            trackedSymptoms = state.trackedSymptoms,
            discoveryMode = state.discoveryMode
        )
    }
}
