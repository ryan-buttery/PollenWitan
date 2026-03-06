package com.ryan.pollenwitan.data.repository

import com.ryan.pollenwitan.data.remote.AirQualityApi
import com.ryan.pollenwitan.domain.model.CurrentConditions
import com.ryan.pollenwitan.domain.model.PollenReading
import com.ryan.pollenwitan.domain.model.PollenType
import com.ryan.pollenwitan.domain.model.SeverityClassifier
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

class AirQualityRepositoryImpl(
    private val api: AirQualityApi
) : AirQualityRepository {

    override suspend fun getCurrentConditions(
        latitude: Double,
        longitude: Double
    ): Result<CurrentConditions> = runCatching {
        val response = api.getAirQuality(latitude, longitude, forecastDays = 1)
        val hourly = response.hourly

        val now = LocalDateTime.now().truncatedTo(ChronoUnit.HOURS)
        val index = hourly.time.indexOfFirst { timeStr ->
            LocalDateTime.parse(timeStr, DateTimeFormatter.ISO_LOCAL_DATE_TIME) == now
        }

        if (index == -1) {
            error("Current hour not found in API response")
        }

        val birchValue = hourly.birchPollen?.getOrNull(index) ?: 0.0
        val alderValue = hourly.alderPollen?.getOrNull(index) ?: 0.0
        val grassValue = hourly.grassPollen?.getOrNull(index) ?: 0.0
        val pm25Value = hourly.pm25?.getOrNull(index) ?: 0.0
        val pm10Value = hourly.pm10?.getOrNull(index) ?: 0.0
        val aqiValue = hourly.europeanAqi?.getOrNull(index) ?: 0

        CurrentConditions(
            pollenReadings = listOf(
                PollenReading(PollenType.Birch, birchValue, SeverityClassifier.pollenSeverity(PollenType.Birch, birchValue)),
                PollenReading(PollenType.Alder, alderValue, SeverityClassifier.pollenSeverity(PollenType.Alder, alderValue)),
                PollenReading(PollenType.Grass, grassValue, SeverityClassifier.pollenSeverity(PollenType.Grass, grassValue))
            ),
            europeanAqi = aqiValue,
            pm25 = pm25Value,
            pm10 = pm10Value,
            aqiSeverity = SeverityClassifier.aqiSeverity(aqiValue),
            timestamp = now
        )
    }
}
