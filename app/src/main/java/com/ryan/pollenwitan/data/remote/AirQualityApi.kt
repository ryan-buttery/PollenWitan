package com.ryan.pollenwitan.data.remote

import com.ryan.pollenwitan.data.remote.dto.AirQualityResponse
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter

class AirQualityApi(private val client: HttpClient) {

    suspend fun getAirQuality(
        latitude: Double,
        longitude: Double,
        forecastDays: Int = 1
    ): AirQualityResponse {
        return client.get(BASE_URL) {
            parameter("latitude", latitude)
            parameter("longitude", longitude)
            parameter("hourly", HOURLY_PARAMS)
            parameter("timezone", "Europe/Warsaw")
            parameter("forecast_days", forecastDays)
        }.body()
    }

    companion object {
        private const val BASE_URL = "https://air-quality-api.open-meteo.com/v1/air-quality"
        private const val HOURLY_PARAMS = "birch_pollen,alder_pollen,grass_pollen,pm2_5,pm10,european_aqi"
    }
}
