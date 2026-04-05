package com.ryan.pollenwitan.domain.model

import java.time.LocalDateTime

data class PollenReading(
    val type: PollenType,
    val value: Double,
    val severity: SeverityLevel
)

data class CurrentConditions(
    val pollenReadings: List<PollenReading>,
    val europeanAqi: Int,
    val pm25: Double,
    val pm10: Double,
    val aqiSeverity: SeverityLevel,
    val timestamp: LocalDateTime,
    val fetchedAtMillis: Long = System.currentTimeMillis()
)
