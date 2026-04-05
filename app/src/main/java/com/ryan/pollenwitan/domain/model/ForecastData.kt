package com.ryan.pollenwitan.domain.model

import java.time.LocalDate
import java.time.LocalDateTime

data class HourlyReading(
    val hour: LocalDateTime,
    val pollenReadings: List<PollenReading>,
    val europeanAqi: Int,
    val pm25: Double,
    val pm10: Double,
    val aqiSeverity: SeverityLevel
)

enum class DayPeriod(val displayName: String, val hourRange: IntRange) {
    Morning("Morning", 6..11),
    Afternoon("Afternoon", 12..17),
    Evening("Evening", 18..23)
}

data class PeriodSummary(
    val period: DayPeriod,
    val peakPollenReadings: List<PollenReading>,
    val avgAqi: Int,
    val aqiSeverity: SeverityLevel
)

data class ForecastDay(
    val date: LocalDate,
    val periods: List<PeriodSummary>,
    val peakPollenReadings: List<PollenReading>,
    val peakAqi: Int,
    val peakAqiSeverity: SeverityLevel,
    val hourlyReadings: List<HourlyReading>
)

data class ForecastResult(
    val days: List<ForecastDay>,
    val fetchedAtMillis: Long
)
