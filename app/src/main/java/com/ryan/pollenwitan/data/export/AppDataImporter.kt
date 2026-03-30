package com.ryan.pollenwitan.data.export

import android.content.Context
import com.ryan.pollenwitan.data.local.AppDatabase
import com.ryan.pollenwitan.data.local.DoseHistoryEntity
import com.ryan.pollenwitan.data.local.SymptomEntryEntity
import com.ryan.pollenwitan.data.repository.LocationRepository
import com.ryan.pollenwitan.data.repository.MedicineRepository
import com.ryan.pollenwitan.data.repository.NotificationPrefsRepository
import com.ryan.pollenwitan.data.repository.ProfileRepository
import com.ryan.pollenwitan.domain.model.AllergenThreshold
import com.ryan.pollenwitan.domain.model.LocationMode
import com.ryan.pollenwitan.domain.model.Medicine
import com.ryan.pollenwitan.domain.model.MedicineAssignment
import com.ryan.pollenwitan.domain.model.MedicineType
import com.ryan.pollenwitan.domain.model.PollenType
import com.ryan.pollenwitan.domain.model.ProfileLocation
import com.ryan.pollenwitan.domain.model.TrackedSymptom
import com.ryan.pollenwitan.domain.model.UserProfile
import com.ryan.pollenwitan.ui.screens.ProfileEditLogic
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json
import java.io.File
import java.io.InputStream

/**
 * Reads JSON from an [InputStream], validates, and replaces all app data.
 * Import is destructive — existing data is cleared before restoring.
 */
class AppDataImporter(private val context: Context) {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    companion object {
        private const val ROLLBACK_FILENAME = "import_rollback.json"
    }

    private val rollbackFile get() = File(context.filesDir, ROLLBACK_FILENAME)

    /**
     * @return a summary string (e.g. "Imported 2 profiles, 3 medicines, 45 dose entries, 12 symptom entries")
     * @throws IllegalArgumentException if the JSON is invalid or version is unsupported
     * @throws ImportValidationException if the import data fails schema validation
     */
    suspend fun import(inputStream: InputStream): String {
        val text = inputStream.bufferedReader().use { it.readText() }
        val data = json.decodeFromString<ExportData>(text)

        require(data.version == 1) {
            "Unsupported export version: ${data.version}"
        }

        validateImportData(data)

        // Auto-backup current state before destructive import
        AppDataExporter(context).export(rollbackFile.outputStream())

        try {
            restoreData(data)
        } catch (e: Exception) {
            // Rollback: restore previous state from backup
            try {
                val backupText = rollbackFile.readText()
                val backupData = json.decodeFromString<ExportData>(backupText)
                restoreData(backupData)
            } catch (_: Exception) {
                // Rollback itself failed — nothing more we can do
            }
            rollbackFile.delete()
            throw e
        }

        rollbackFile.delete()

        return buildString {
            append("Imported ${data.profiles.size} profiles")
            append(", ${data.medicines.size} medicines")
            append(", ${data.doseHistory.size} dose entries")
            append(", ${data.symptomEntries.size} symptom entries")
        }
    }

    private suspend fun restoreData(data: ExportData) {
        val profileRepo = ProfileRepository(context)
        val medicineRepo = MedicineRepository(context)
        val locationRepo = LocationRepository(context)
        val notifRepo = NotificationPrefsRepository(context)
        val db = AppDatabase.getInstance(context)

        // Delete existing profiles
        val existingProfiles = profileRepo.getProfiles().first()
        for (profile in existingProfiles) {
            profileRepo.deleteProfile(profile.id)
        }

        // Delete existing medicines
        val existingMedicines = medicineRepo.getMedicines().first()
        for (medicine in existingMedicines) {
            medicineRepo.deleteMedicine(medicine.id)
        }

        // Clear Room tables
        db.doseHistoryDao().deleteAll()
        db.symptomEntryDao().deleteAll()

        // Restore medicines first (profiles reference them)
        for (med in data.medicines) {
            val type = MedicineType.entries.find { it.name == med.type } ?: MedicineType.Other
            medicineRepo.addMedicine(Medicine(id = med.id, name = med.name, type = type))
        }

        // Restore profiles
        for (exportProfile in data.profiles) {
            profileRepo.addProfile(exportProfile.toDomain())
        }

        // Restore dose history
        if (data.doseHistory.isNotEmpty()) {
            db.doseHistoryDao().insertAll(
                data.doseHistory.map { entry ->
                    DoseHistoryEntity(
                        profileId = entry.profileId,
                        medicineId = entry.medicineId,
                        slotIndex = entry.slotIndex,
                        date = entry.date,
                        confirmedAtMillis = entry.confirmedAtMillis,
                        confirmed = entry.confirmed,
                        medicineName = entry.medicineName,
                        dose = entry.dose,
                        medicineType = entry.medicineType,
                        reminderHour = entry.reminderHour
                    )
                }
            )
        }

        // Restore symptom entries
        if (data.symptomEntries.isNotEmpty()) {
            db.symptomEntryDao().insertAll(
                data.symptomEntries.map { entry ->
                    SymptomEntryEntity(
                        profileId = entry.profileId,
                        date = entry.date,
                        ratingsJson = entry.ratingsJson,
                        loggedAtMillis = entry.loggedAtMillis,
                        peakPollenJson = entry.peakPollenJson,
                        peakAqi = entry.peakAqi,
                        peakPm25 = entry.peakPm25,
                        peakPm10 = entry.peakPm10
                    )
                }
            )
        }

        // Restore location settings
        val loc = data.locationSettings
        val mode = try {
            LocationMode.valueOf(loc.mode)
        } catch (_: Exception) {
            LocationMode.Manual
        }
        locationRepo.setLocationMode(mode)
        locationRepo.setManualLocation(loc.manualLatitude, loc.manualLongitude, loc.manualDisplayName)
        if (loc.gpsLatitude != null && loc.gpsLongitude != null) {
            locationRepo.updateGpsLocation(
                loc.gpsLatitude, loc.gpsLongitude, loc.gpsDisplayName ?: "GPS Location"
            )
        }

        // Restore notification prefs
        val np = data.notificationPrefs
        notifRepo.setMorningBriefingEnabled(np.morningBriefingEnabled)
        notifRepo.setMorningBriefingHour(np.morningBriefingHour)
        notifRepo.setThresholdAlertsEnabled(np.thresholdAlertsEnabled)
        notifRepo.setCompoundRiskAlertsEnabled(np.compoundRiskAlertsEnabled)
        notifRepo.setPreSeasonAlertsEnabled(np.preSeasonAlertsEnabled)
        notifRepo.setSymptomReminderEnabled(np.symptomReminderEnabled)
        notifRepo.setSymptomReminderHour(np.symptomReminderHour)
    }

    private fun validateImportData(data: ExportData) {
        val errors = mutableListOf<String>()
        val validPollenTypes = PollenType.entries.map { it.name }.toSet()
        val validMedicineTypes = MedicineType.entries.map { it.name }.toSet()
        val validLocationModes = LocationMode.entries.map { it.name }.toSet()
        val thresholdRange = ProfileEditLogic.MIN_THRESHOLD..ProfileEditLogic.MAX_THRESHOLD

        // Profile validation
        for ((i, profile) in data.profiles.withIndex()) {
            val prefix = "Profile[${i}] (${profile.id})"

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

        // Medicine validation
        for ((i, med) in data.medicines.withIndex()) {
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

        // Location settings validation
        val loc = data.locationSettings
        if (loc.mode !in validLocationModes) {
            errors += "Location settings: unknown mode '${loc.mode}'"
        }
        if (loc.manualLatitude !in -90.0..90.0) {
            errors += "Location settings: manualLatitude ${loc.manualLatitude} out of range -90..90"
        }
        if (loc.manualLongitude !in -180.0..180.0) {
            errors += "Location settings: manualLongitude ${loc.manualLongitude} out of range -180..180"
        }

        if (errors.isNotEmpty()) {
            throw ImportValidationException(errors)
        }
    }

    private fun ExportProfile.toDomain() = UserProfile(
        id = id,
        displayName = displayName,
        hasAsthma = hasAsthma,
        trackedAllergens = trackedAllergens.mapNotNull { (typeName, threshold) ->
            val type = PollenType.entries.find { it.name == typeName } ?: return@mapNotNull null
            type to AllergenThreshold(
                type = type,
                low = threshold.low,
                moderate = threshold.moderate,
                high = threshold.high,
                veryHigh = threshold.veryHigh
            )
        }.toMap(),
        location = location?.let {
            ProfileLocation(it.latitude, it.longitude, it.displayName)
        },
        medicineAssignments = medicineAssignments.map { assignment ->
            MedicineAssignment(
                medicineId = assignment.medicineId,
                dose = assignment.dose,
                timesPerDay = assignment.timesPerDay,
                reminderHours = assignment.reminderHours
            )
        },
        trackedSymptoms = trackedSymptoms.map { symptom ->
            TrackedSymptom(
                id = symptom.id,
                displayName = symptom.displayName,
                isDefault = symptom.isDefault
            )
        }
    )
}

/**
 * Thrown when import data fails schema validation.
 * Contains all validation errors found (not just the first).
 */
class ImportValidationException(
    val errors: List<String>
) : Exception("Import validation failed with ${errors.size} error(s):\n${errors.joinToString("\n") { "  • $it" }}")
