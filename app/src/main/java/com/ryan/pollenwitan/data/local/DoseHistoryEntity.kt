package com.ryan.pollenwitan.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "dose_history",
    indices = [
        Index(value = ["profileId", "date"]),
        Index(
            value = ["profileId", "date", "medicineId", "slotIndex"],
            unique = true
        )
    ]
)
data class DoseHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
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
