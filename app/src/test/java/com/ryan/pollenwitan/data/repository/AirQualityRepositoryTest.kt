package com.ryan.pollenwitan.data.repository

import com.ryan.pollenwitan.data.remote.dto.HourlyData
import com.ryan.pollenwitan.data.remote.dto.WeatherHourlyData
import com.ryan.pollenwitan.data.repository.AirQualityRepository.Companion.buildForecastDays
import com.ryan.pollenwitan.data.repository.AirQualityRepository.Companion.parseReadingsAtIndex
import com.ryan.pollenwitan.data.repository.AirQualityRepository.Companion.peakPollenReadings
import com.ryan.pollenwitan.data.repository.AirQualityRepository.Companion.roundCoord
import com.ryan.pollenwitan.data.repository.AirQualityRepository.Companion.weatherConditionsAt
import com.ryan.pollenwitan.data.repository.AirQualityRepository.Companion.CACHE_MAX_AGE_MS
import com.ryan.pollenwitan.data.repository.AirQualityRepository.Companion.CACHE_CLEANUP_AGE_MS
import com.ryan.pollenwitan.domain.model.DayPeriod
import com.ryan.pollenwitan.domain.model.HourlyReading
import com.ryan.pollenwitan.domain.model.PollenReading
import com.ryan.pollenwitan.domain.model.PollenType
import com.ryan.pollenwitan.domain.model.SeverityClassifier
import com.ryan.pollenwitan.domain.model.SeverityLevel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

class AirQualityRepositoryTest {

    // ── Coordinate rounding ─────────────────────────────────────────────

    @Test
    fun `roundCoord rounds to two decimal places`() {
        assertEquals(52.41, roundCoord(52.4064), 0.0001)
    }

    @Test
    fun `roundCoord rounds down when third decimal below 5`() {
        assertEquals(16.93, roundCoord(16.9252), 0.0001)
    }

    @Test
    fun `roundCoord exact two decimals unchanged`() {
        assertEquals(10.50, roundCoord(10.50), 0.0001)
    }

    @Test
    fun `roundCoord negative coordinate rounds correctly`() {
        assertEquals(-33.87, roundCoord(-33.8688), 0.0001)
    }

    @Test
    fun `roundCoord zero returns zero`() {
        assertEquals(0.0, roundCoord(0.0), 0.0001)
    }

    @Test
    fun `roundCoord half-up rounding at boundary`() {
        // 52.405 -> 52.41 (roundToInt rounds half-up for positive)
        assertEquals(52.41, roundCoord(52.405), 0.0001)
    }

    @Test
    fun `nearby coordinates round to same cache key`() {
        val coord1 = roundCoord(52.4064)
        val coord2 = roundCoord(52.4092)
        assertEquals(coord1, coord2, 0.0001)
    }

    @Test
    fun `distant coordinates round to different cache keys`() {
        val coord1 = roundCoord(52.40)
        val coord2 = roundCoord(52.50)
        assertTrue(coord1 != coord2)
    }

    // ── Cache expiry constants ──────────────────────────────────────────

    @Test
    fun `cache max age is one hour`() {
        assertEquals(3_600_000L, CACHE_MAX_AGE_MS)
    }

    @Test
    fun `cache cleanup age is 24 hours`() {
        assertEquals(86_400_000L, CACHE_CLEANUP_AGE_MS)
    }

    // ── parseReadingsAtIndex ────────────────────────────────────────────

    @Test
    fun `parseReadingsAtIndex returns all six pollen types`() {
        val hourly = makeHourlyData(
            birch = listOf(10.0),
            alder = listOf(20.0),
            grass = listOf(5.0),
            mugwort = listOf(15.0),
            ragweed = listOf(8.0),
            olive = listOf(12.0)
        )

        val readings = parseReadingsAtIndex(hourly, 0)

        assertEquals(6, readings.size)
        assertEquals(PollenType.Birch, readings[0].type)
        assertEquals(10.0, readings[0].value, 0.001)
        assertEquals(PollenType.Alder, readings[1].type)
        assertEquals(20.0, readings[1].value, 0.001)
        assertEquals(PollenType.Grass, readings[2].type)
        assertEquals(5.0, readings[2].value, 0.001)
        assertEquals(PollenType.Mugwort, readings[3].type)
        assertEquals(15.0, readings[3].value, 0.001)
        assertEquals(PollenType.Ragweed, readings[4].type)
        assertEquals(8.0, readings[4].value, 0.001)
        assertEquals(PollenType.Olive, readings[5].type)
        assertEquals(12.0, readings[5].value, 0.001)
    }

    @Test
    fun `parseReadingsAtIndex assigns correct severity`() {
        val hourly = makeHourlyData(birch = listOf(55.0)) // High for birch (51-200)

        val readings = parseReadingsAtIndex(hourly, 0)

        assertEquals(SeverityLevel.High, readings.first { it.type == PollenType.Birch }.severity)
    }

    @Test
    fun `parseReadingsAtIndex defaults null lists to zero`() {
        val hourly = HourlyData(
            time = listOf("2026-03-28T10:00"),
            birchPollen = null,
            alderPollen = null,
            grassPollen = null,
            mugwortPollen = null,
            ragweedPollen = null,
            olivePollen = null,
            pm25 = null,
            pm10 = null,
            europeanAqi = null
        )

        val readings = parseReadingsAtIndex(hourly, 0)

        readings.forEach { reading ->
            assertEquals("${reading.type} should default to 0.0", 0.0, reading.value, 0.001)
            assertEquals("${reading.type} should be None", SeverityLevel.None, reading.severity)
        }
    }

    @Test
    fun `parseReadingsAtIndex defaults null entry within list to zero`() {
        val hourly = makeHourlyData(birch = listOf(null))

        val readings = parseReadingsAtIndex(hourly, 0)

        assertEquals(0.0, readings.first { it.type == PollenType.Birch }.value, 0.001)
    }

    @Test
    fun `parseReadingsAtIndex out of bounds index defaults to zero`() {
        val hourly = makeHourlyData(birch = listOf(100.0))

        val readings = parseReadingsAtIndex(hourly, 5) // index beyond list size

        readings.forEach { reading ->
            assertEquals(0.0, reading.value, 0.001)
        }
    }

    @Test
    fun `parseReadingsAtIndex picks correct index from multi-hour data`() {
        val hourly = makeHourlyData(
            birch = listOf(10.0, 50.0, 200.0),
            alder = listOf(5.0, 25.0, 105.0)
        )

        val readingsAt0 = parseReadingsAtIndex(hourly, 0)
        val readingsAt2 = parseReadingsAtIndex(hourly, 2)

        assertEquals(10.0, readingsAt0.first { it.type == PollenType.Birch }.value, 0.001)
        assertEquals(200.0, readingsAt2.first { it.type == PollenType.Birch }.value, 0.001)
        assertEquals(5.0, readingsAt0.first { it.type == PollenType.Alder }.value, 0.001)
        assertEquals(105.0, readingsAt2.first { it.type == PollenType.Alder }.value, 0.001)
    }

    // ── peakPollenReadings ──────────────────────────────────────────────

    @Test
    fun `peakPollenReadings empty list returns zeros for all types`() {
        val peaks = peakPollenReadings(emptyList())

        assertEquals(PollenType.entries.size, peaks.size)
        peaks.forEach { reading ->
            assertEquals(0.0, reading.value, 0.001)
            assertEquals(SeverityLevel.None, reading.severity)
        }
    }

    @Test
    fun `peakPollenReadings single reading returns that reading's values`() {
        val readings = listOf(
            makeHourlyReading(
                hour = 10,
                birch = 45.0,
                grass = 70.0
            )
        )

        val peaks = peakPollenReadings(readings)

        assertEquals(45.0, peaks.first { it.type == PollenType.Birch }.value, 0.001)
        assertEquals(70.0, peaks.first { it.type == PollenType.Grass }.value, 0.001)
    }

    @Test
    fun `peakPollenReadings selects max across multiple readings`() {
        val readings = listOf(
            makeHourlyReading(hour = 8, birch = 10.0, grass = 70.0),
            makeHourlyReading(hour = 9, birch = 50.0, grass = 30.0),
            makeHourlyReading(hour = 10, birch = 25.0, grass = 5.0)
        )

        val peaks = peakPollenReadings(readings)

        assertEquals(50.0, peaks.first { it.type == PollenType.Birch }.value, 0.001)
        assertEquals(70.0, peaks.first { it.type == PollenType.Grass }.value, 0.001)
    }

    @Test
    fun `peakPollenReadings assigns severity based on peak value`() {
        val readings = listOf(
            makeHourlyReading(hour = 8, birch = 5.0),   // Low
            makeHourlyReading(hour = 9, birch = 210.0)   // VeryHigh
        )

        val peaks = peakPollenReadings(readings)

        assertEquals(SeverityLevel.VeryHigh, peaks.first { it.type == PollenType.Birch }.severity)
    }

    // ── Period summarisation ────────────────────────────────────────────

    @Test
    fun `DayPeriod morning covers hours 6 to 11`() {
        assertEquals(6..11, DayPeriod.Morning.hourRange)
    }

    @Test
    fun `DayPeriod afternoon covers hours 12 to 17`() {
        assertEquals(12..17, DayPeriod.Afternoon.hourRange)
    }

    @Test
    fun `DayPeriod evening covers hours 18 to 23`() {
        assertEquals(18..23, DayPeriod.Evening.hourRange)
    }

    @Test
    fun `buildForecastDays groups readings into correct periods`() {
        // Create hourly data spanning morning, afternoon, and evening
        val times = (6..23).map { h -> "2026-03-28T%02d:00".format(h) }
        val birchValues = (6..23).map { h ->
            when (h) {
                in 6..11 -> 10.0   // morning
                in 12..17 -> 50.0  // afternoon
                else -> 20.0      // evening
            }
        }

        val hourly = HourlyData(
            time = times,
            birchPollen = birchValues,
            alderPollen = times.map { 0.0 },
            grassPollen = times.map { 0.0 },
            mugwortPollen = times.map { 0.0 },
            ragweedPollen = times.map { 0.0 },
            olivePollen = times.map { 0.0 },
            pm25 = times.map { 5.0 },
            pm10 = times.map { 10.0 },
            europeanAqi = times.map { 30 }
        )

        val days = buildForecastDays(hourly)

        assertEquals(1, days.size)
        val day = days[0]
        assertEquals(LocalDate.of(2026, 3, 28), day.date)
        assertEquals(3, day.periods.size)

        val morning = day.periods.first { it.period == DayPeriod.Morning }
        val afternoon = day.periods.first { it.period == DayPeriod.Afternoon }
        val evening = day.periods.first { it.period == DayPeriod.Evening }

        // Peak birch in morning = 10.0
        assertEquals(10.0, morning.peakPollenReadings.first { it.type == PollenType.Birch }.value, 0.001)
        // Peak birch in afternoon = 50.0
        assertEquals(50.0, afternoon.peakPollenReadings.first { it.type == PollenType.Birch }.value, 0.001)
        // Peak birch in evening = 20.0
        assertEquals(20.0, evening.peakPollenReadings.first { it.type == PollenType.Birch }.value, 0.001)
    }

    @Test
    fun `buildForecastDays computes average AQI per period`() {
        val times = listOf("2026-03-28T06:00", "2026-03-28T07:00", "2026-03-28T08:00")
        val aqiValues = listOf(20, 40, 60)

        val hourly = makeHourlyDataForTimes(times, europeanAqi = aqiValues)

        val days = buildForecastDays(hourly)
        val morning = days[0].periods.first { it.period == DayPeriod.Morning }

        // Average of 20, 40, 60 = 40.0 -> truncated to 40
        assertEquals(40, morning.avgAqi)
    }

    @Test
    fun `buildForecastDays period AQI severity uses peak not average`() {
        val times = listOf("2026-03-28T06:00", "2026-03-28T07:00")
        val aqiValues = listOf(30, 75) // avg = 52 (Moderate), peak = 75 (High)

        val hourly = makeHourlyDataForTimes(times, europeanAqi = aqiValues)

        val days = buildForecastDays(hourly)
        val morning = days[0].periods.first { it.period == DayPeriod.Morning }

        assertEquals(SeverityLevel.High, morning.aqiSeverity) // based on peak (75)
    }

    @Test
    fun `buildForecastDays empty period gets zero AQI and zero pollen`() {
        // Only morning hours — afternoon and evening will be empty
        val times = listOf("2026-03-28T06:00", "2026-03-28T07:00")
        val hourly = makeHourlyDataForTimes(times, europeanAqi = listOf(30, 40))

        val days = buildForecastDays(hourly)
        val afternoon = days[0].periods.first { it.period == DayPeriod.Afternoon }

        assertEquals(0, afternoon.avgAqi)
        assertEquals(SeverityLevel.None, afternoon.aqiSeverity)
        afternoon.peakPollenReadings.forEach { reading ->
            assertEquals(0.0, reading.value, 0.001)
        }
    }

    @Test
    fun `buildForecastDays peak AQI is day-wide max`() {
        val times = listOf(
            "2026-03-28T08:00", // morning
            "2026-03-28T14:00", // afternoon
            "2026-03-28T20:00"  // evening
        )
        val aqiValues = listOf(30, 85, 50)

        val hourly = makeHourlyDataForTimes(times, europeanAqi = aqiValues)

        val days = buildForecastDays(hourly)

        assertEquals(85, days[0].peakAqi)
        assertEquals(SeverityLevel.VeryHigh, days[0].peakAqiSeverity)
    }

    @Test
    fun `buildForecastDays peak pollen is day-wide max`() {
        val times = listOf(
            "2026-03-28T08:00", // morning: birch 10
            "2026-03-28T14:00", // afternoon: birch 200
            "2026-03-28T20:00"  // evening: birch 5
        )
        val hourly = HourlyData(
            time = times,
            birchPollen = listOf(10.0, 200.0, 5.0),
            alderPollen = times.map { 0.0 },
            grassPollen = times.map { 0.0 },
            mugwortPollen = times.map { 0.0 },
            ragweedPollen = times.map { 0.0 },
            olivePollen = times.map { 0.0 },
            pm25 = times.map { 0.0 },
            pm10 = times.map { 0.0 },
            europeanAqi = times.map { 0 }
        )

        val days = buildForecastDays(hourly)

        assertEquals(200.0, days[0].peakPollenReadings.first { it.type == PollenType.Birch }.value, 0.001)
    }

    // ── Multi-day grouping ──────────────────────────────────────────────

    @Test
    fun `buildForecastDays splits readings across multiple dates`() {
        val times = listOf(
            "2026-03-28T10:00",
            "2026-03-28T14:00",
            "2026-03-29T10:00",
            "2026-03-29T14:00"
        )
        val hourly = makeHourlyDataForTimes(times)

        val days = buildForecastDays(hourly)

        assertEquals(2, days.size)
        assertEquals(LocalDate.of(2026, 3, 28), days[0].date)
        assertEquals(LocalDate.of(2026, 3, 29), days[1].date)
    }

    @Test
    fun `buildForecastDays sorts dates chronologically`() {
        // Provide out-of-order times (grouped by date, but dates reversed)
        val times = listOf(
            "2026-03-30T10:00",
            "2026-03-28T10:00",
            "2026-03-29T10:00"
        )
        val hourly = makeHourlyDataForTimes(times)

        val days = buildForecastDays(hourly)

        assertEquals(LocalDate.of(2026, 3, 28), days[0].date)
        assertEquals(LocalDate.of(2026, 3, 29), days[1].date)
        assertEquals(LocalDate.of(2026, 3, 30), days[2].date)
    }

    @Test
    fun `buildForecastDays hours outside period ranges excluded from periods`() {
        // Hours 0-5 are before Morning (6-11), should not appear in any period
        val times = listOf("2026-03-28T03:00", "2026-03-28T04:00")
        val hourly = makeHourlyDataForTimes(times, europeanAqi = listOf(50, 60))

        val days = buildForecastDays(hourly)

        // All periods should be empty (hours 3 and 4 are not in any DayPeriod range)
        days[0].periods.forEach { period ->
            assertEquals(0, period.avgAqi)
        }
        // But day-level peak should still reflect the data
        assertEquals(60, days[0].peakAqi)
    }

    @Test
    fun `buildForecastDays preserves hourly readings on ForecastDay`() {
        val times = listOf("2026-03-28T10:00", "2026-03-28T14:00")
        val hourly = makeHourlyDataForTimes(times)

        val days = buildForecastDays(hourly)

        assertEquals(2, days[0].hourlyReadings.size)
        assertEquals(LocalDateTime.of(2026, 3, 28, 10, 0), days[0].hourlyReadings[0].hour)
        assertEquals(LocalDateTime.of(2026, 3, 28, 14, 0), days[0].hourlyReadings[1].hour)
    }

    // ── weatherConditionsAt ─────────────────────────────────────────────

    @Test
    fun `weatherConditionsAt picks the matching hour`() {
        val target = LocalDateTime.of(2026, 4, 9, 14, 0)
        val hourly = WeatherHourlyData(
            time = listOf(
                "2026-04-09T13:00",
                "2026-04-09T14:00",
                "2026-04-09T15:00"
            ),
            windSpeed10m = listOf(5.0, 12.5, 20.0),
            windDirection10m = listOf(45.0, 180.0, 270.0),
            precipitationProbability = listOf(0, 60, 90)
        )

        val result = weatherConditionsAt(hourly, target)

        assertNotNull(result)
        assertEquals(12.5, result!!.windSpeedKmh, 0.001)
        assertEquals(180, result.windDirectionDegrees)
        assertEquals(60, result.precipitationProbabilityPercent)
    }

    @Test
    fun `weatherConditionsAt returns null when target hour absent`() {
        val target = LocalDateTime.of(2026, 4, 9, 14, 0)
        val hourly = WeatherHourlyData(
            time = listOf("2026-04-09T13:00", "2026-04-09T15:00"),
            windSpeed10m = listOf(5.0, 20.0),
            windDirection10m = listOf(45.0, 270.0),
            precipitationProbability = listOf(10, 90)
        )

        assertNull(weatherConditionsAt(hourly, target))
    }

    @Test
    fun `weatherConditionsAt returns null when all fields null at index`() {
        val target = LocalDateTime.of(2026, 4, 9, 14, 0)
        val hourly = WeatherHourlyData(
            time = listOf("2026-04-09T14:00"),
            windSpeed10m = listOf(null),
            windDirection10m = listOf(null),
            precipitationProbability = listOf(null)
        )

        assertNull(weatherConditionsAt(hourly, target))
    }

    @Test
    fun `weatherConditionsAt fills missing fields with zero when at least one present`() {
        val target = LocalDateTime.of(2026, 4, 9, 14, 0)
        val hourly = WeatherHourlyData(
            time = listOf("2026-04-09T14:00"),
            windSpeed10m = listOf(null),
            windDirection10m = listOf(null),
            precipitationProbability = listOf(75)
        )

        val result = weatherConditionsAt(hourly, target)

        assertNotNull(result)
        assertEquals(0.0, result!!.windSpeedKmh, 0.001)
        assertEquals(0, result.windDirectionDegrees)
        assertEquals(75, result.precipitationProbabilityPercent)
    }

    @Test
    fun `weatherConditionsAt normalises wind direction modulo 360`() {
        val target = LocalDateTime.of(2026, 4, 9, 14, 0)
        val hourly = WeatherHourlyData(
            time = listOf("2026-04-09T14:00"),
            windSpeed10m = listOf(10.0),
            windDirection10m = listOf(450.0),
            precipitationProbability = listOf(0)
        )

        val result = weatherConditionsAt(hourly, target)

        assertEquals(90, result!!.windDirectionDegrees)
    }

    // ── Helpers ─────────────────────────────────────────────────────────

    private fun makeHourlyData(
        birch: List<Double?> = listOf(0.0),
        alder: List<Double?> = listOf(0.0),
        grass: List<Double?> = listOf(0.0),
        mugwort: List<Double?> = listOf(0.0),
        ragweed: List<Double?> = listOf(0.0),
        olive: List<Double?> = listOf(0.0)
    ): HourlyData {
        val size = maxOf(birch.size, alder.size, grass.size, mugwort.size, ragweed.size, olive.size)
        return HourlyData(
            time = (0 until size).map { "2026-03-28T%02d:00".format(it + 6) },
            birchPollen = birch,
            alderPollen = alder,
            grassPollen = grass,
            mugwortPollen = mugwort,
            ragweedPollen = ragweed,
            olivePollen = olive,
            pm25 = (0 until size).map { 0.0 },
            pm10 = (0 until size).map { 0.0 },
            europeanAqi = (0 until size).map { 0 }
        )
    }

    private fun makeHourlyDataForTimes(
        times: List<String>,
        europeanAqi: List<Int> = times.map { 0 }
    ): HourlyData = HourlyData(
        time = times,
        birchPollen = times.map { 0.0 },
        alderPollen = times.map { 0.0 },
        grassPollen = times.map { 0.0 },
        mugwortPollen = times.map { 0.0 },
        ragweedPollen = times.map { 0.0 },
        olivePollen = times.map { 0.0 },
        pm25 = times.map { 0.0 },
        pm10 = times.map { 0.0 },
        europeanAqi = europeanAqi
    )

    private fun makeHourlyReading(
        hour: Int,
        birch: Double = 0.0,
        alder: Double = 0.0,
        grass: Double = 0.0,
        mugwort: Double = 0.0,
        ragweed: Double = 0.0,
        olive: Double = 0.0,
        aqi: Int = 0
    ): HourlyReading {
        val readings = listOf(
            PollenReading(PollenType.Birch, birch, SeverityClassifier.pollenSeverity(PollenType.Birch, birch)),
            PollenReading(PollenType.Alder, alder, SeverityClassifier.pollenSeverity(PollenType.Alder, alder)),
            PollenReading(PollenType.Grass, grass, SeverityClassifier.pollenSeverity(PollenType.Grass, grass)),
            PollenReading(PollenType.Mugwort, mugwort, SeverityClassifier.pollenSeverity(PollenType.Mugwort, mugwort)),
            PollenReading(PollenType.Ragweed, ragweed, SeverityClassifier.pollenSeverity(PollenType.Ragweed, ragweed)),
            PollenReading(PollenType.Olive, olive, SeverityClassifier.pollenSeverity(PollenType.Olive, olive))
        )
        return HourlyReading(
            hour = LocalDateTime.of(2026, 3, 28, hour, 0),
            pollenReadings = readings,
            europeanAqi = aqi,
            pm25 = 0.0,
            pm10 = 0.0,
            aqiSeverity = SeverityClassifier.aqiSeverity(aqi)
        )
    }
}
