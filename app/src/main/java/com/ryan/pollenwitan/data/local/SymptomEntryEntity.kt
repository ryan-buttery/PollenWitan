package com.ryan.pollenwitan.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "symptom_entries",
    indices = [
        Index(value = ["profileId", "date"], unique = true)
    ]
)
data class SymptomEntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val profileId: String,
    val date: String,
    val ratingsJson: String,
    val loggedAtMillis: Long,
    val peakPollenJson: String,
    val peakAqi: Int,
    val peakPm25: Double,
    val peakPm10: Double,
    val notes: String? = null
)
