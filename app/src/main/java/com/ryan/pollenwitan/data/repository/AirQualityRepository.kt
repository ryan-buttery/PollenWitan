package com.ryan.pollenwitan.data.repository

import android.content.Context
import com.ryan.pollenwitan.data.local.AppDatabase
import com.ryan.pollenwitan.data.local.CachedForecastEntity
import com.ryan.pollenwitan.data.remote.AirQualityApi
import com.ryan.pollenwitan.data.remote.dto.AirQualityResponse
import com.ryan.pollenwitan.data.remote.dto.HourlyData
import com.ryan.pollenwitan.domain.model.CurrentConditions
import com.ryan.pollenwitan.domain.model.DayPeriod
import com.ryan.pollenwitan.domain.model.ForecastDay
import com.ryan.pollenwitan.domain.model.HourlyReading
import com.ryan.pollenwitan.domain.model.PeriodSummary
import com.ryan.pollenwitan.domain.model.PollenReading
import com.ryan.pollenwitan.domain.model.PollenType
import com.ryan.pollenwitan.domain.model.SeverityClassifier
import kotlinx.serialization.json.Json
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import kotlin.math.roundToInt

class AirQualityRepository(context: Context) {

    private val api = AirQualityApi()
    private val dao = AppDatabase.getInstance(context).cachedForecastDao()
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun getCurrentConditions(
        latitude: Double,
        longitude: Double
    ): Result<CurrentConditions> = runCatching {
        val response = fetchOrCache(latitude, longitude, forecastDays = 1)
        val hourly = response.hourly

        val now = LocalDateTime.now().truncatedTo(ChronoUnit.HOURS)
        val index = hourly.time.indexOfFirst { timeStr ->
            LocalDateTime.parse(timeStr, DateTimeFormatter.ISO_LOCAL_DATE_TIME) == now
        }

        if (index == -1) {
            error("Current hour not found in API response")
        }

        val readings = parseReadingsAtIndex(hourly, index)
        val aqiValue = hourly.europeanAqi?.getOrNull(index) ?: 0

        CurrentConditions(
            pollenReadings = readings,
            europeanAqi = aqiValue,
            pm25 = hourly.pm25?.getOrNull(index) ?: 0.0,
            pm10 = hourly.pm10?.getOrNull(index) ?: 0.0,
            aqiSeverity = SeverityClassifier.aqiSeverity(aqiValue),
            timestamp = now
        )
    }

    suspend fun getForecast(
        latitude: Double,
        longitude: Double,
        days: Int = 4
    ): Result<List<ForecastDay>> = runCatching {
        val response = fetchOrCache(latitude, longitude, forecastDays = days)
        val hourly = response.hourly

        val hourlyReadings = hourly.time.indices.map { i ->
            val time = LocalDateTime.parse(hourly.time[i], DateTimeFormatter.ISO_LOCAL_DATE_TIME)
            val readings = parseReadingsAtIndex(hourly, i)
            val aqi = hourly.europeanAqi?.getOrNull(i) ?: 0
            HourlyReading(
                hour = time,
                pollenReadings = readings,
                europeanAqi = aqi,
                pm25 = hourly.pm25?.getOrNull(i) ?: 0.0,
                pm10 = hourly.pm10?.getOrNull(i) ?: 0.0,
                aqiSeverity = SeverityClassifier.aqiSeverity(aqi)
            )
        }

        hourlyReadings
            .groupBy { it.hour.toLocalDate() }
            .entries
            .sortedBy { it.key }
            .map { (date, readings) ->
                val periods = DayPeriod.entries.map { period ->
                    val periodReadings = readings.filter { it.hour.hour in period.hourRange }
                    PeriodSummary(
                        period = period,
                        peakPollenReadings = peakPollenReadings(periodReadings),
                        avgAqi = if (periodReadings.isNotEmpty()) periodReadings.map { it.europeanAqi }.average().toInt() else 0,
                        aqiSeverity = SeverityClassifier.aqiSeverity(
                            if (periodReadings.isNotEmpty()) periodReadings.maxOf { it.europeanAqi } else 0
                        )
                    )
                }

                val peakAqi = readings.maxOfOrNull { it.europeanAqi } ?: 0

                ForecastDay(
                    date = date,
                    periods = periods,
                    peakPollenReadings = peakPollenReadings(readings),
                    peakAqi = peakAqi,
                    peakAqiSeverity = SeverityClassifier.aqiSeverity(peakAqi),
                    hourlyReadings = readings
                )
            }
    }

    private suspend fun fetchOrCache(
        latitude: Double,
        longitude: Double,
        forecastDays: Int
    ): AirQualityResponse {
        val roundedLat = roundCoord(latitude)
        val roundedLon = roundCoord(longitude)
        val now = System.currentTimeMillis()

        val cached = dao.getLatest(roundedLat, roundedLon, forecastDays)
        if (cached != null && (now - cached.fetchedAtMillis) < CACHE_MAX_AGE_MS) {
            return json.decodeFromString<AirQualityResponse>(cached.responseJson)
        }

        val rawJson = api.getAirQualityRaw(latitude, longitude, forecastDays)
        val response = json.decodeFromString<AirQualityResponse>(rawJson)

        dao.insert(
            CachedForecastEntity(
                latitude = roundedLat,
                longitude = roundedLon,
                forecastDays = forecastDays,
                fetchedAtMillis = now,
                responseJson = rawJson
            )
        )

        dao.deleteOlderThan(now - CACHE_CLEANUP_AGE_MS)

        return response
    }

    private fun roundCoord(value: Double): Double =
        (value * 100).roundToInt() / 100.0

    private fun parseReadingsAtIndex(hourly: HourlyData, index: Int): List<PollenReading> {
        val birchValue = hourly.birchPollen?.getOrNull(index) ?: 0.0
        val alderValue = hourly.alderPollen?.getOrNull(index) ?: 0.0
        val grassValue = hourly.grassPollen?.getOrNull(index) ?: 0.0
        return listOf(
            PollenReading(PollenType.Birch, birchValue, SeverityClassifier.pollenSeverity(PollenType.Birch, birchValue)),
            PollenReading(PollenType.Alder, alderValue, SeverityClassifier.pollenSeverity(PollenType.Alder, alderValue)),
            PollenReading(PollenType.Grass, grassValue, SeverityClassifier.pollenSeverity(PollenType.Grass, grassValue))
        )
    }

    private fun peakPollenReadings(readings: List<HourlyReading>): List<PollenReading> {
        if (readings.isEmpty()) {
            return PollenType.entries.map { PollenReading(it, 0.0, SeverityClassifier.pollenSeverity(it, 0.0)) }
        }
        return PollenType.entries.map { type ->
            val peakValue = readings.maxOf { hourly ->
                hourly.pollenReadings.find { it.type == type }?.value ?: 0.0
            }
            PollenReading(type, peakValue, SeverityClassifier.pollenSeverity(type, peakValue))
        }
    }

    companion object {
        private const val CACHE_MAX_AGE_MS = 60 * 60 * 1000L
        private const val CACHE_CLEANUP_AGE_MS = 24 * 60 * 60 * 1000L
    }
}
