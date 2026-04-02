package com.ryan.pollenwitan.ui.screens

import com.ryan.pollenwitan.data.local.DoseHistoryEntity
import com.ryan.pollenwitan.domain.model.MedicineAssignment
import com.ryan.pollenwitan.domain.model.SymptomDiaryEntry
import kotlinx.serialization.json.Json
import java.time.LocalDate

internal object SymptomTrendsLogic {

    fun expectedDosesPerDay(assignments: List<MedicineAssignment>): Int =
        assignments.sumOf { it.reminderHours.size }

    fun buildSnapshots(
        entries: List<SymptomDiaryEntry>,
        doseHistory: List<DoseHistoryEntity>,
        expectedDosesPerDay: Int,
        startDate: LocalDate,
        rangeDays: Long,
        json: Json
    ): List<DaySnapshot> {
        val entryByDate = entries.associateBy { it.date }
        val dosesByDate = doseHistory.groupBy { it.date }

        return (0 until rangeDays).map { offset ->
            val date = startDate.plusDays(offset)
            val entry = entryByDate[date]
            val doses = dosesByDate[date.toString()] ?: emptyList()

            val pollenLevels = if (entry != null) {
                try {
                    json.decodeFromString<Map<String, Double>>(entry.peakPollenJson)
                } catch (_: Exception) {
                    emptyMap()
                }
            } else emptyMap()

            DaySnapshot(
                date = date,
                symptomRatings = entry?.ratings?.associate { it.symptomName to it.severity }
                    ?: emptyMap(),
                pollenLevels = pollenLevels,
                peakAqi = entry?.peakAqi ?: 0,
                peakPm25 = entry?.peakPm25 ?: 0.0,
                peakPm10 = entry?.peakPm10 ?: 0.0,
                dosesConfirmed = doses.size,
                dosesExpected = expectedDosesPerDay
            )
        }
    }
}
