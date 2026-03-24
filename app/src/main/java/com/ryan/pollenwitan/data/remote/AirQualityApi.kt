package com.ryan.pollenwitan.data.remote

import com.ryan.pollenwitan.data.remote.dto.AirQualityResponse
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.statement.HttpResponse
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.delay
import kotlinx.serialization.json.Json

class AirQualityApi {

    private val client get() = httpClient

    suspend fun getAirQuality(
        latitude: Double,
        longitude: Double,
        forecastDays: Int = 1
    ): AirQualityResponse {
        return retryWithBackoff {
            client.get(BASE_URL) {
                parameter("latitude", latitude)
                parameter("longitude", longitude)
                parameter("hourly", HOURLY_PARAMS)
                parameter("timezone", "Europe/Warsaw")
                parameter("forecast_days", forecastDays)
            }
        }.body()
    }

    suspend fun getAirQualityRaw(
        latitude: Double,
        longitude: Double,
        forecastDays: Int = 1
    ): String {
        return retryWithBackoff {
            client.get(BASE_URL) {
                parameter("latitude", latitude)
                parameter("longitude", longitude)
                parameter("hourly", HOURLY_PARAMS)
                parameter("timezone", "Europe/Warsaw")
                parameter("forecast_days", forecastDays)
            }
        }.body()
    }

    private suspend fun retryWithBackoff(
        maxRetries: Int = 3,
        initialDelayMs: Long = 1000,
        block: suspend () -> HttpResponse
    ): HttpResponse {
        var currentDelay = initialDelayMs
        repeat(maxRetries) { attempt ->
            val response = block()
            if (response.status.value != 429) return response
            if (attempt < maxRetries - 1) {
                delay(currentDelay)
                currentDelay *= 2
            }
        }
        return block()
    }

    companion object {
        private const val BASE_URL = "https://air-quality-api.open-meteo.com/v1/air-quality"
        private const val HOURLY_PARAMS = "birch_pollen,alder_pollen,grass_pollen,pm2_5,pm10,european_aqi"

        private val httpClient by lazy {
            HttpClient(CIO) {
                install(ContentNegotiation) {
                    json(Json { ignoreUnknownKeys = true })
                }
            }
        }
    }
}
