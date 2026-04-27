package com.ryan.pollenwitan.domain.model

import java.time.LocalDate

data class SymptomRating(
    val symptomId: String,
    val symptomName: String,
    val severity: Int
)

data class SymptomDiaryEntry(
    val profileId: String,
    val date: LocalDate,
    val ratings: List<SymptomRating>,
    val loggedAtMillis: Long,
    val peakPollenJson: String,
    val peakAqi: Int,
    val peakPm25: Double,
    val peakPm10: Double,
    /**
     * Optional free-text observation for the day, intended for future PDF export
     * to medical providers. Null when the user didn't add a note.
     */
    val notes: String? = null
)
