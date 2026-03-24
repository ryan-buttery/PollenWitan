package com.ryan.pollenwitan.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class AirQualityResponse(
    val latitude: Double,
    val longitude: Double,
    val hourly: HourlyData
)

@Serializable
data class HourlyData(
    val time: List<String>,
    @SerialName("birch_pollen") val birchPollen: List<Double?>? = null,
    @SerialName("alder_pollen") val alderPollen: List<Double?>? = null,
    @SerialName("grass_pollen") val grassPollen: List<Double?>? = null,
    @SerialName("mugwort_pollen") val mugwortPollen: List<Double?>? = null,
    @SerialName("ragweed_pollen") val ragweedPollen: List<Double?>? = null,
    @SerialName("olive_pollen") val olivePollen: List<Double?>? = null,
    @SerialName("pm2_5") val pm25: List<Double?>? = null,
    val pm10: List<Double?>? = null,
    @SerialName("european_aqi") val europeanAqi: List<Int?>? = null
)
