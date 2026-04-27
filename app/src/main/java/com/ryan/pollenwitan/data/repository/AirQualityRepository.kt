package com.ryan.pollenwitan.data.repository

import android.content.Context
import android.util.Log
import com.ryan.pollenwitan.data.local.AppDatabase
import com.ryan.pollenwitan.data.local.CachedForecastEntity
import com.ryan.pollenwitan.data.remote.AirQualityApi
import com.ryan.pollenwitan.data.remote.dto.AirQualityResponse
import com.ryan.pollenwitan.data.remote.dto.HourlyData
import com.ryan.pollenwitan.data.remote.dto.WeatherForecastResponse
import com.ryan.pollenwitan.data.remote.dto.WeatherHourlyData
import com.ryan.pollenwitan.domain.model.CurrentConditions
import com.ryan.pollenwitan.domain.model.DayPeriod
import com.ryan.pollenwitan.domain.model.ForecastDay
import com.ryan.pollenwitan.domain.model.ForecastResult
import com.ryan.pollenwitan.domain.model.HourlyReading
import com.ryan.pollenwitan.domain.model.PeriodSummary
import com.ryan.pollenwitan.domain.model.PollenReading
import com.ryan.pollenwitan.domain.model.PollenType
import com.ryan.pollenwitan.domain.model.SeverityClassifier
import com.ryan.pollenwitan.domain.model.WeatherConditions
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.Json
import java.time.LocalDate
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
        coroutineScope {
            val now = LocalDateTime.now().truncatedTo(ChronoUnit.HOURS)

            fun findIndex(hourly: HourlyData) = hourly.time.indexOfFirst { timeStr ->
                LocalDateTime.parse(timeStr, DateTimeFormatter.ISO_LOCAL_DATE_TIME) == now
            }

            // Run the air-quality fetch (required) and the weather fetch (best-effort)
            // in parallel. Weather failures degrade gracefully to a null context row.
            val airQualityDeferred = async {
                fetchOrCache(latitude, longitude, forecastDays = 1)
            }
            val weatherDeferred = async {
                runCatching { fetchWeatherOrCache(latitude, longitude, forecastDays = 1) }
                    .onFailure { Log.w(TAG, "Weather fetch failed; falling back to null", it) }
                    .getOrNull()
            }

            var (response, fetchedAt) = airQualityDeferred.await()
            var index = findIndex(response.hourly)

            if (index == -1) {
                // Cached data doesn't cover the current hour (e.g. day boundary crossed); force a live fetch
                val fresh = fetchOrCache(latitude, longitude, forecastDays = 1, forceRefresh = true)
                response = fresh.first
                fetchedAt = fresh.second
                index = findIndex(response.hourly)
            }

            if (index == -1) error("Current hour not found in API response")

            val hourly = response.hourly
            val readings = parseReadingsAtIndex(hourly, index)
            val aqiValue = hourly.europeanAqi?.getOrNull(index) ?: 0

            val weather = weatherDeferred.await()?.let { weatherResponse ->
                weatherConditionsAt(weatherResponse.hourly, now)
            }

            CurrentConditions(
                pollenReadings = readings,
                europeanAqi = aqiValue,
                pm25 = hourly.pm25?.getOrNull(index) ?: 0.0,
                pm10 = hourly.pm10?.getOrNull(index) ?: 0.0,
                aqiSeverity = SeverityClassifier.aqiSeverity(aqiValue),
                timestamp = now,
                fetchedAtMillis = fetchedAt,
                weather = weather
            )
        }
    }

    suspend fun getForecast(
        latitude: Double,
        longitude: Double,
        days: Int = 4
    ): Result<List<ForecastDay>> = runCatching {
        val (response, _) = fetchOrCache(latitude, longitude, forecastDays = days)
        buildForecastDays(response.hourly)
    }

    suspend fun getForecastWithTimestamp(
        latitude: Double,
        longitude: Double,
        days: Int = 4
    ): Result<ForecastResult> = runCatching {
        val (response, fetchedAt) = fetchOrCache(latitude, longitude, forecastDays = days)
        ForecastResult(
            days = buildForecastDays(response.hourly),
            fetchedAtMillis = fetchedAt
        )
    }

    suspend fun getHistoricalDayPeaks(
        latitude: Double,
        longitude: Double,
        targetDate: LocalDate
    ): Result<ForecastDay> = runCatching {
        val pastDays = ChronoUnit.DAYS.between(targetDate, LocalDate.now()).toInt()
        require(pastDays in 1..16) { "Historical data only available for 1-16 days ago" }

        val rawJson = api.getAirQualityRaw(latitude, longitude, forecastDays = 1, pastDays = pastDays)
        val response = json.decodeFromString<AirQualityResponse>(rawJson)
        val days = buildForecastDays(response.hourly)
        days.find { it.date == targetDate }
            ?: error("Target date $targetDate not found in API response")
    }

    private suspend fun fetchOrCache(
        latitude: Double,
        longitude: Double,
        forecastDays: Int,
        forceRefresh: Boolean = false
    ): Pair<AirQualityResponse, Long> {
        val roundedLat = roundCoord(latitude)
        val roundedLon = roundCoord(longitude)
        val now = System.currentTimeMillis()

        if (!forceRefresh) {
            val cached = dao.getLatest(
                roundedLat, roundedLon, forecastDays, CachedForecastEntity.ENDPOINT_AIR_QUALITY
            )
            if (cached != null && (now - cached.fetchedAtMillis) < CACHE_MAX_AGE_MS) {
                return json.decodeFromString<AirQualityResponse>(cached.responseJson) to cached.fetchedAtMillis
            }
        }

        val rawJson = api.getAirQualityRaw(latitude, longitude, forecastDays)
        val response = json.decodeFromString<AirQualityResponse>(rawJson)

        dao.insert(
            CachedForecastEntity(
                latitude = roundedLat,
                longitude = roundedLon,
                forecastDays = forecastDays,
                fetchedAtMillis = now,
                responseJson = rawJson,
                endpoint = CachedForecastEntity.ENDPOINT_AIR_QUALITY
            )
        )

        dao.deleteOlderThan(now - CACHE_CLEANUP_AGE_MS)

        return response to now
    }

    private suspend fun fetchWeatherOrCache(
        latitude: Double,
        longitude: Double,
        forecastDays: Int
    ): WeatherForecastResponse {
        val roundedLat = roundCoord(latitude)
        val roundedLon = roundCoord(longitude)
        val now = System.currentTimeMillis()

        val cached = dao.getLatest(
            roundedLat, roundedLon, forecastDays, CachedForecastEntity.ENDPOINT_WEATHER
        )
        if (cached != null && (now - cached.fetchedAtMillis) < CACHE_MAX_AGE_MS) {
            return json.decodeFromString<WeatherForecastResponse>(cached.responseJson)
        }

        val rawJson = api.getWeatherRaw(latitude, longitude, forecastDays)
        val response = json.decodeFromString<WeatherForecastResponse>(rawJson)

        dao.insert(
            CachedForecastEntity(
                latitude = roundedLat,
                longitude = roundedLon,
                forecastDays = forecastDays,
                fetchedAtMillis = now,
                responseJson = rawJson,
                endpoint = CachedForecastEntity.ENDPOINT_WEATHER
            )
        )

        return response
    }

    companion object {
        private const val TAG = "AirQualityRepository"
        internal const val CACHE_MAX_AGE_MS = 60 * 60 * 1000L
        internal const val CACHE_CLEANUP_AGE_MS = 24 * 60 * 60 * 1000L

        internal fun roundCoord(value: Double): Double =
            (value * 100).roundToInt() / 100.0

        /**
         * Resolve the wind/precipitation values that line up with [target] in the
         * weather forecast response. Falls back to null if the matching hour is
         * missing or all three fields are absent for that index.
         */
        internal fun weatherConditionsAt(
            hourly: WeatherHourlyData,
            target: LocalDateTime
        ): WeatherConditions? {
            val index = hourly.time.indexOfFirst { timeStr ->
                LocalDateTime.parse(timeStr, DateTimeFormatter.ISO_LOCAL_DATE_TIME) == target
            }
            if (index == -1) return null

            val windSpeed = hourly.windSpeed10m?.getOrNull(index)
            val windDir = hourly.windDirection10m?.getOrNull(index)
            val precipProb = hourly.precipitationProbability?.getOrNull(index)

            // If every field is null the row carries no useful info; degrade to null.
            if (windSpeed == null && windDir == null && precipProb == null) return null

            return WeatherConditions(
                windSpeedKmh = windSpeed ?: 0.0,
                windDirectionDegrees = windDir?.toInt()?.mod(360) ?: 0,
                precipitationProbabilityPercent = precipProb ?: 0
            )
        }

        internal fun parseReadingsAtIndex(hourly: HourlyData, index: Int): List<PollenReading> {
            val birchValue = hourly.birchPollen?.getOrNull(index) ?: 0.0
            val alderValue = hourly.alderPollen?.getOrNull(index) ?: 0.0
            val grassValue = hourly.grassPollen?.getOrNull(index) ?: 0.0
            val mugwortValue = hourly.mugwortPollen?.getOrNull(index) ?: 0.0
            val ragweedValue = hourly.ragweedPollen?.getOrNull(index) ?: 0.0
            val oliveValue = hourly.olivePollen?.getOrNull(index) ?: 0.0
            return listOf(
                PollenReading(PollenType.Birch, birchValue, SeverityClassifier.pollenSeverity(PollenType.Birch, birchValue)),
                PollenReading(PollenType.Alder, alderValue, SeverityClassifier.pollenSeverity(PollenType.Alder, alderValue)),
                PollenReading(PollenType.Grass, grassValue, SeverityClassifier.pollenSeverity(PollenType.Grass, grassValue)),
                PollenReading(PollenType.Mugwort, mugwortValue, SeverityClassifier.pollenSeverity(PollenType.Mugwort, mugwortValue)),
                PollenReading(PollenType.Ragweed, ragweedValue, SeverityClassifier.pollenSeverity(PollenType.Ragweed, ragweedValue)),
                PollenReading(PollenType.Olive, oliveValue, SeverityClassifier.pollenSeverity(PollenType.Olive, oliveValue))
            )
        }

        internal fun peakPollenReadings(readings: List<HourlyReading>): List<PollenReading> {
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

        internal fun buildForecastDays(hourly: HourlyData): List<ForecastDay> {
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

            return hourlyReadings
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
    }
}
