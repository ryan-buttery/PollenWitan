package com.ryan.pollenwitan.data.export

import kotlinx.serialization.Serializable

/**
 * Top-level export envelope.
 * All app data is serialized into this structure for backup/restore.
 */
@Serializable
data class ExportData(
    val version: Int = 1,
    val exportedAt: String,
    val profiles: List<ExportProfile>,
    val medicines: List<ExportMedicine>,
    val doseHistory: List<ExportDoseEntry>,
    val symptomEntries: List<ExportSymptomEntry>,
    val locationSettings: ExportLocation,
    val notificationPrefs: ExportNotificationPrefs
)

@Serializable
data class ExportProfile(
    val id: String,
    val displayName: String,
    val hasAsthma: Boolean,
    val trackedAllergens: Map<String, ExportAllergenThreshold>,
    val location: ExportProfileLocation? = null,
    val medicineAssignments: List<ExportMedicineAssignment> = emptyList(),
    val trackedSymptoms: List<ExportTrackedSymptom> = emptyList()
)

@Serializable
data class ExportAllergenThreshold(
    val type: String,
    val low: Double,
    val moderate: Double,
    val high: Double,
    val veryHigh: Double
)

@Serializable
data class ExportProfileLocation(
    val latitude: Double,
    val longitude: Double,
    val displayName: String
)

@Serializable
data class ExportMedicineAssignment(
    val medicineId: String,
    val dose: Int,
    val timesPerDay: Int,
    val reminderHours: List<Int>
)

@Serializable
data class ExportTrackedSymptom(
    val id: String,
    val displayName: String,
    val isDefault: Boolean
)

@Serializable
data class ExportMedicine(
    val id: String,
    val name: String,
    val type: String
)

@Serializable
data class ExportDoseEntry(
    val profileId: String,
    val medicineId: String,
    val slotIndex: Int,
    val date: String,
    val confirmedAtMillis: Long,
    val confirmed: Boolean,
    val medicineName: String,
    val dose: Int,
    val medicineType: String,
    val reminderHour: Int
)

@Serializable
data class ExportSymptomEntry(
    val profileId: String,
    val date: String,
    val ratingsJson: String,
    val loggedAtMillis: Long,
    val peakPollenJson: String,
    val peakAqi: Int,
    val peakPm25: Double,
    val peakPm10: Double
)

@Serializable
data class ExportLocation(
    val mode: String,
    val manualLatitude: Double,
    val manualLongitude: Double,
    val manualDisplayName: String,
    val gpsLatitude: Double? = null,
    val gpsLongitude: Double? = null,
    val gpsDisplayName: String? = null
)

@Serializable
data class ExportNotificationPrefs(
    val morningBriefingEnabled: Boolean,
    val morningBriefingHour: Int,
    val thresholdAlertsEnabled: Boolean,
    val compoundRiskAlertsEnabled: Boolean,
    val preSeasonAlertsEnabled: Boolean,
    val symptomReminderEnabled: Boolean,
    val symptomReminderHour: Int
)
