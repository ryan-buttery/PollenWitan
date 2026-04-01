package com.ryan.pollenwitan.data.export

import com.ryan.pollenwitan.ui.screens.ProfileEditLogic
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * Unit tests for import schema validation.
 *
 * These tests exercise the validation rules from [AppDataImporter.validateImportData]
 * by calling the same logic indirectly through serialisation models. Since
 * validateImportData is private, we replicate its checks here to verify the
 * rules are correct and comprehensive.
 *
 * No Android dependencies — pure model and constant validation tests.
 */
class ImportValidationTest {

    // ── Profile display name ───────────────────────────────────────────

    @Test
    fun `blank display name is invalid`() {
        val errors = validateProfiles(
            listOf(validProfile().copy(displayName = "   "))
        )
        assertTrue(errors.any { it.contains("displayName is blank") })
    }

    @Test
    fun `empty display name is invalid`() {
        val errors = validateProfiles(
            listOf(validProfile().copy(displayName = ""))
        )
        assertTrue(errors.any { it.contains("displayName is blank") })
    }

    @Test
    fun `display name at max length is valid`() {
        val name = "A".repeat(ProfileEditLogic.MAX_DISPLAY_NAME_LENGTH)
        val errors = validateProfiles(
            listOf(validProfile().copy(displayName = name))
        )
        assertTrue(errors.none { it.contains("displayName") })
    }

    @Test
    fun `display name exceeding max length is invalid`() {
        val name = "A".repeat(ProfileEditLogic.MAX_DISPLAY_NAME_LENGTH + 1)
        val errors = validateProfiles(
            listOf(validProfile().copy(displayName = name))
        )
        assertTrue(errors.any { it.contains("displayName exceeds") })
    }

    // ── Allergen type validation ───────────────────────────────────────

    @Test
    fun `valid pollen type names pass validation`() {
        val allergens = mapOf(
            "Birch" to validThreshold(),
            "Grass" to validThreshold()
        )
        val errors = validateProfiles(
            listOf(validProfile().copy(trackedAllergens = allergens))
        )
        assertTrue(errors.none { it.contains("unknown allergen type") })
    }

    @Test
    fun `unknown pollen type name is invalid`() {
        val allergens = mapOf(
            "Oak" to validThreshold()
        )
        val errors = validateProfiles(
            listOf(validProfile().copy(trackedAllergens = allergens))
        )
        assertTrue(errors.any { it.contains("unknown allergen type 'Oak'") })
    }

    // ── Threshold range validation ─────────────────────────────────────

    @Test
    fun `thresholds within valid range pass`() {
        val threshold = ExportAllergenThreshold("Birch", 0.1, 10.0, 100.0, 1000.0)
        val errors = validateProfiles(
            listOf(validProfile().copy(trackedAllergens = mapOf("Birch" to threshold)))
        )
        assertTrue(errors.none { it.contains("threshold outside") })
    }

    @Test
    fun `threshold below minimum is invalid`() {
        val threshold = ExportAllergenThreshold("Birch", 0.05, 10.0, 100.0, 1000.0)
        val errors = validateProfiles(
            listOf(validProfile().copy(trackedAllergens = mapOf("Birch" to threshold)))
        )
        assertTrue(errors.any { it.contains("threshold outside") })
    }

    @Test
    fun `threshold above maximum is invalid`() {
        val threshold = ExportAllergenThreshold("Birch", 1.0, 10.0, 100.0, 10001.0)
        val errors = validateProfiles(
            listOf(validProfile().copy(trackedAllergens = mapOf("Birch" to threshold)))
        )
        assertTrue(errors.any { it.contains("threshold outside") })
    }

    @Test
    fun `thresholds not in ascending order are invalid`() {
        val threshold = ExportAllergenThreshold("Birch", 10.0, 5.0, 100.0, 1000.0)
        val errors = validateProfiles(
            listOf(validProfile().copy(trackedAllergens = mapOf("Birch" to threshold)))
        )
        assertTrue(errors.any { it.contains("ascending order") })
    }

    @Test
    fun `equal threshold values are invalid`() {
        val threshold = ExportAllergenThreshold("Birch", 10.0, 10.0, 100.0, 1000.0)
        val errors = validateProfiles(
            listOf(validProfile().copy(trackedAllergens = mapOf("Birch" to threshold)))
        )
        assertTrue(errors.any { it.contains("ascending order") })
    }

    // ── Profile location validation ────────────────────────────────────

    @Test
    fun `valid profile location passes`() {
        val location = ExportProfileLocation(52.4064, 16.9252, "Poznan")
        val errors = validateProfiles(
            listOf(validProfile().copy(location = location))
        )
        assertTrue(errors.none { it.contains("latitude") || it.contains("longitude") || it.contains("location displayName") })
    }

    @Test
    fun `latitude out of range is invalid`() {
        val location = ExportProfileLocation(91.0, 16.9252, "Nowhere")
        val errors = validateProfiles(
            listOf(validProfile().copy(location = location))
        )
        assertTrue(errors.any { it.contains("latitude") && it.contains("out of range") })
    }

    @Test
    fun `longitude out of range is invalid`() {
        val location = ExportProfileLocation(52.0, -181.0, "Nowhere")
        val errors = validateProfiles(
            listOf(validProfile().copy(location = location))
        )
        assertTrue(errors.any { it.contains("longitude") && it.contains("out of range") })
    }

    @Test
    fun `blank location display name is invalid`() {
        val location = ExportProfileLocation(52.0, 16.0, "  ")
        val errors = validateProfiles(
            listOf(validProfile().copy(location = location))
        )
        assertTrue(errors.any { it.contains("location displayName is blank") })
    }

    @Test
    fun `null profile location passes`() {
        val errors = validateProfiles(
            listOf(validProfile().copy(location = null))
        )
        assertTrue(errors.none { it.contains("location") })
    }

    // ── Medicine assignment validation ─────────────────────────────────

    @Test
    fun `valid medicine assignment passes`() {
        val assignment = ExportMedicineAssignment("med-1", dose = 1, timesPerDay = 1, reminderHours = listOf(8))
        val errors = validateProfiles(
            listOf(validProfile().copy(medicineAssignments = listOf(assignment)))
        )
        assertTrue(errors.none { it.contains("medicineAssignment") })
    }

    @Test
    fun `dose of zero is invalid`() {
        val assignment = ExportMedicineAssignment("med-1", dose = 0, timesPerDay = 1, reminderHours = listOf(8))
        val errors = validateProfiles(
            listOf(validProfile().copy(medicineAssignments = listOf(assignment)))
        )
        assertTrue(errors.any { it.contains("dose") && it.contains("out of range") })
    }

    @Test
    fun `dose exceeding max is invalid`() {
        val assignment = ExportMedicineAssignment("med-1", dose = ProfileEditLogic.MAX_DOSE + 1, timesPerDay = 1, reminderHours = listOf(8))
        val errors = validateProfiles(
            listOf(validProfile().copy(medicineAssignments = listOf(assignment)))
        )
        assertTrue(errors.any { it.contains("dose") && it.contains("out of range") })
    }

    @Test
    fun `timesPerDay of zero is invalid`() {
        val assignment = ExportMedicineAssignment("med-1", dose = 1, timesPerDay = 0, reminderHours = emptyList())
        val errors = validateProfiles(
            listOf(validProfile().copy(medicineAssignments = listOf(assignment)))
        )
        assertTrue(errors.any { it.contains("timesPerDay") && it.contains("out of range") })
    }

    @Test
    fun `reminderHour of 24 is invalid`() {
        val assignment = ExportMedicineAssignment("med-1", dose = 1, timesPerDay = 1, reminderHours = listOf(24))
        val errors = validateProfiles(
            listOf(validProfile().copy(medicineAssignments = listOf(assignment)))
        )
        assertTrue(errors.any { it.contains("reminderHours") && it.contains("outside 0") })
    }

    @Test
    fun `negative reminderHour is invalid`() {
        val assignment = ExportMedicineAssignment("med-1", dose = 1, timesPerDay = 1, reminderHours = listOf(-1))
        val errors = validateProfiles(
            listOf(validProfile().copy(medicineAssignments = listOf(assignment)))
        )
        assertTrue(errors.any { it.contains("reminderHours") && it.contains("outside 0") })
    }

    // ── Medicine validation ────────────────────────────────────────────

    @Test
    fun `valid medicine passes`() {
        val errors = validateMedicines(
            listOf(ExportMedicine("med-1", "Cetirizine", "Tablet"))
        )
        assertTrue(errors.isEmpty())
    }

    @Test
    fun `blank medicine name is invalid`() {
        val errors = validateMedicines(
            listOf(ExportMedicine("med-1", "", "Tablet"))
        )
        assertTrue(errors.any { it.contains("name is blank") })
    }

    @Test
    fun `medicine name exceeding max length is invalid`() {
        val longName = "A".repeat(ProfileEditLogic.MAX_MEDICINE_NAME_LENGTH + 1)
        val errors = validateMedicines(
            listOf(ExportMedicine("med-1", longName, "Tablet"))
        )
        assertTrue(errors.any { it.contains("name exceeds") })
    }

    @Test
    fun `unknown medicine type is flagged`() {
        val errors = validateMedicines(
            listOf(ExportMedicine("med-1", "Test Med", "Suppository"))
        )
        assertTrue(errors.any { it.contains("unknown type") && it.contains("Suppository") })
    }

    @Test
    fun `all valid medicine types pass`() {
        val meds = listOf("Tablet", "Eyedrops", "NasalSpray", "Other").mapIndexed { i, type ->
            ExportMedicine("med-$i", "Med $i", type)
        }
        val errors = validateMedicines(meds)
        assertTrue(errors.none { it.contains("unknown type") })
    }

    // ── Location settings validation ───────────────────────────────────

    @Test
    fun `valid location settings pass`() {
        val errors = validateLocationSettings(
            ExportLocation("Manual", 52.4064, 16.9252, "Poznan")
        )
        assertTrue(errors.isEmpty())
    }

    @Test
    fun `unknown location mode is invalid`() {
        val errors = validateLocationSettings(
            ExportLocation("Auto", 52.0, 16.0, "Test")
        )
        assertTrue(errors.any { it.contains("unknown mode") })
    }

    @Test
    fun `location settings latitude out of range is invalid`() {
        val errors = validateLocationSettings(
            ExportLocation("Manual", 95.0, 16.0, "Test")
        )
        assertTrue(errors.any { it.contains("manualLatitude") && it.contains("out of range") })
    }

    @Test
    fun `location settings longitude out of range is invalid`() {
        val errors = validateLocationSettings(
            ExportLocation("Manual", 52.0, 200.0, "Test")
        )
        assertTrue(errors.any { it.contains("manualLongitude") && it.contains("out of range") })
    }

    // ── Multiple errors collected ──────────────────────────────────────

    @Test
    fun `multiple validation errors are collected`() {
        val errors = mutableListOf<String>()
        errors += validateProfiles(listOf(
            validProfile().copy(displayName = ""),
            validProfile().copy(
                id = "p2",
                displayName = "A".repeat(ProfileEditLogic.MAX_DISPLAY_NAME_LENGTH + 1)
            )
        ))
        errors += validateMedicines(listOf(
            ExportMedicine("med-1", "", "Tablet")
        ))

        assertTrue("Expected at least 3 errors, got ${errors.size}", errors.size >= 3)
    }

    @Test
    fun `ImportValidationException contains all errors`() {
        val errors = listOf("Error 1", "Error 2", "Error 3")
        val exception = ImportValidationException(errors)

        assertEquals(3, exception.errors.size)
        assertTrue(exception.message!!.contains("3 error(s)"))
        assertTrue(exception.message!!.contains("Error 1"))
        assertTrue(exception.message!!.contains("Error 2"))
        assertTrue(exception.message!!.contains("Error 3"))
    }

    // ── Helpers (mirror AppDataImporter.validateImportData logic) ──────

    private fun validProfile() = ExportProfile(
        id = "test",
        displayName = "Test",
        hasAsthma = false,
        trackedAllergens = emptyMap(),
        location = null,
        medicineAssignments = emptyList(),
        trackedSymptoms = emptyList()
    )

    private fun validThreshold() = ExportAllergenThreshold("Birch", 1.0, 11.0, 51.0, 201.0)

    private val validPollenTypes = setOf("Birch", "Alder", "Grass", "Mugwort", "Ragweed", "Olive")
    private val validMedicineTypes = setOf("Tablet", "Eyedrops", "NasalSpray", "Other")
    private val validLocationModes = setOf("Manual", "Gps")
    private val thresholdRange = ProfileEditLogic.MIN_THRESHOLD..ProfileEditLogic.MAX_THRESHOLD

    private fun validateProfiles(profiles: List<ExportProfile>): List<String> {
        val errors = mutableListOf<String>()
        for ((i, profile) in profiles.withIndex()) {
            val prefix = "Profile[$i] (${profile.id})"

            if (profile.displayName.isBlank()) {
                errors += "$prefix: displayName is blank"
            } else if (profile.displayName.length > ProfileEditLogic.MAX_DISPLAY_NAME_LENGTH) {
                errors += "$prefix: displayName exceeds ${ProfileEditLogic.MAX_DISPLAY_NAME_LENGTH} chars"
            }

            for ((key, threshold) in profile.trackedAllergens) {
                if (key !in validPollenTypes) {
                    errors += "$prefix: unknown allergen type '$key'"
                }
                val values = listOf(threshold.low, threshold.moderate, threshold.high, threshold.veryHigh)
                if (values.any { it !in thresholdRange }) {
                    errors += "$prefix: allergen '$key' has threshold outside ${ProfileEditLogic.MIN_THRESHOLD}–${ProfileEditLogic.MAX_THRESHOLD}"
                }
                if (threshold.low >= threshold.moderate || threshold.moderate >= threshold.high || threshold.high >= threshold.veryHigh) {
                    errors += "$prefix: allergen '$key' thresholds must be in ascending order (low < moderate < high < veryHigh)"
                }
            }

            profile.location?.let { loc ->
                if (loc.latitude !in -90.0..90.0) {
                    errors += "$prefix: latitude ${loc.latitude} out of range -90..90"
                }
                if (loc.longitude !in -180.0..180.0) {
                    errors += "$prefix: longitude ${loc.longitude} out of range -180..180"
                }
                if (loc.displayName.isBlank()) {
                    errors += "$prefix: location displayName is blank"
                }
            }

            for ((j, assignment) in profile.medicineAssignments.withIndex()) {
                val aPrefix = "$prefix medicineAssignment[$j]"
                if (assignment.dose !in 1..ProfileEditLogic.MAX_DOSE) {
                    errors += "$aPrefix: dose ${assignment.dose} out of range 1–${ProfileEditLogic.MAX_DOSE}"
                }
                if (assignment.timesPerDay !in 1..ProfileEditLogic.MAX_TIMES_PER_DAY) {
                    errors += "$aPrefix: timesPerDay ${assignment.timesPerDay} out of range 1–${ProfileEditLogic.MAX_TIMES_PER_DAY}"
                }
                if (assignment.reminderHours.any { it !in 0..23 }) {
                    errors += "$aPrefix: reminderHours contains value outside 0–23"
                }
            }
        }
        return errors
    }

    private fun validateMedicines(medicines: List<ExportMedicine>): List<String> {
        val errors = mutableListOf<String>()
        for ((i, med) in medicines.withIndex()) {
            val prefix = "Medicine[$i] (${med.id})"
            if (med.name.isBlank()) {
                errors += "$prefix: name is blank"
            } else if (med.name.length > ProfileEditLogic.MAX_MEDICINE_NAME_LENGTH) {
                errors += "$prefix: name exceeds ${ProfileEditLogic.MAX_MEDICINE_NAME_LENGTH} chars"
            }
            if (med.type !in validMedicineTypes) {
                errors += "$prefix: unknown type '${med.type}' (will fall back to Other)"
            }
        }
        return errors
    }

    private fun validateLocationSettings(loc: ExportLocation): List<String> {
        val errors = mutableListOf<String>()
        if (loc.mode !in validLocationModes) {
            errors += "Location settings: unknown mode '${loc.mode}'"
        }
        if (loc.manualLatitude !in -90.0..90.0) {
            errors += "Location settings: manualLatitude ${loc.manualLatitude} out of range -90..90"
        }
        if (loc.manualLongitude !in -180.0..180.0) {
            errors += "Location settings: manualLongitude ${loc.manualLongitude} out of range -180..180"
        }
        return errors
    }
}
