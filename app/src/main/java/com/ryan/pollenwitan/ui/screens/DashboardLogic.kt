package com.ryan.pollenwitan.ui.screens

import com.ryan.pollenwitan.domain.model.DoseConfirmation
import com.ryan.pollenwitan.domain.model.Medicine
import com.ryan.pollenwitan.domain.model.UserProfile

internal object DashboardLogic {

    fun buildMedicineSlots(
        profile: UserProfile,
        medicines: List<Medicine>,
        confirmations: Set<DoseConfirmation>
    ): List<MedicineSlot> {
        val slots = mutableListOf<MedicineSlot>()
        profile.medicineAssignments.forEach { assignment ->
            val medicine = medicines.find { it.id == assignment.medicineId } ?: return@forEach
            assignment.reminderHours.forEachIndexed { slotIndex, hour ->
                slots.add(
                    MedicineSlot(
                        medicineId = assignment.medicineId,
                        medicineName = medicine.name,
                        dose = assignment.dose,
                        medicineType = medicine.type,
                        hour = hour,
                        slotIndex = slotIndex,
                        confirmed = DoseConfirmation(assignment.medicineId, slotIndex) in confirmations
                    )
                )
            }
        }
        return slots.sortedBy { it.hour }
    }
}
