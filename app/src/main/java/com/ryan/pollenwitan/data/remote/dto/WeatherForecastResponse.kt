package com.ryan.pollenwitan.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class WeatherForecastResponse(
    val latitude: Double,
    val longitude: Double,
    val hourly: WeatherHourlyData
)

@Serializable
data class WeatherHourlyData(
    val time: List<String>,
    @SerialName("wind_speed_10m") val windSpeed10m: List<Double?>? = null,
    @SerialName("wind_direction_10m") val windDirection10m: List<Double?>? = null,
    @SerialName("precipitation_probability") val precipitationProbability: List<Int?>? = null
)
