package com.ryan.pollenwitan.domain.model

enum class MedicineType(val displayName: String, val unitLabel: String) {
    Tablet("Tablet", "tablets"),
    Eyedrops("Eyedrops", "drops"),
    NasalSpray("Nasal Spray", "sprays"),
    Other("Other", "doses")
}

data class Medicine(
    val id: String,
    val name: String,
    val type: MedicineType
)

data class MedicineAssignment(
    val medicineId: String,
    val dose: Int,
    val timesPerDay: Int,
    val reminderHours: List<Int> // hours 0-23
)

data class DoseConfirmation(
    val medicineId: String,
    val slotIndex: Int
)
