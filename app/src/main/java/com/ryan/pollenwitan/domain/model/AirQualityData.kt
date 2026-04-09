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
    val fetchedAtMillis: Long = System.currentTimeMillis(),
    val weather: WeatherConditions? = null
)

/**
 * Meteorological context for the current hour, sourced from the Open-Meteo
 * forecast endpoint. Nullable on [CurrentConditions] so the dashboard degrades
 * gracefully if the secondary fetch fails.
 */
data class WeatherConditions(
    /** km/h */
    val windSpeedKmh: Double,
    /** Compass bearing in degrees (0 = N, 90 = E, 180 = S, 270 = W). */
    val windDirectionDegrees: Int,
    /** Probability of precipitation, 0-100. */
    val precipitationProbabilityPercent: Int
)
