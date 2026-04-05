package com.ryan.pollenwitan.data.remote

import com.ryan.pollenwitan.data.remote.dto.AirQualityResponse
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.statement.HttpResponse
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.delay
import kotlinx.serialization.json.Json
import java.io.IOException

/**
 * Exception thrown when the Open-Meteo API returns a non-success HTTP status.
 */
class ApiException(
    val statusCode: Int,
    override val message: String
) : Exception("HTTP $statusCode: $message")

class AirQualityApi {

    private val client get() = httpClient

    suspend fun getAirQuality(
        latitude: Double,
        longitude: Double,
        forecastDays: Int = 1
    ): AirQualityResponse {
        return executeRequest(latitude, longitude, forecastDays).body()
    }

    suspend fun getAirQualityRaw(
        latitude: Double,
        longitude: Double,
        forecastDays: Int = 1,
        pastDays: Int = 0
    ): String {
        return executeRequest(latitude, longitude, forecastDays, pastDays).body()
    }

    private suspend fun executeRequest(
        latitude: Double,
        longitude: Double,
        forecastDays: Int,
        pastDays: Int = 0
    ): HttpResponse {
        return retryWithBackoff {
            client.get(BASE_URL) {
                parameter("latitude", latitude)
                parameter("longitude", longitude)
                parameter("hourly", HOURLY_PARAMS)
                parameter("timezone", java.time.ZoneId.systemDefault().id)
                parameter("forecast_days", forecastDays)
                if (pastDays > 0) parameter("past_days", pastDays)
            }
        }
    }

    private suspend fun retryWithBackoff(
        maxRetries: Int = 3,
        initialDelayMs: Long = 1000,
        block: suspend () -> HttpResponse
    ): HttpResponse {
        var currentDelay = initialDelayMs
        var lastException: Exception? = null

        repeat(maxRetries) { attempt ->
            try {
                val response = block()
                val status = response.status.value

                when {
                    status in 200..299 -> return response
                    status == 429 || status in 500..599 -> {
                        // Transient failures: rate-limit or server error — retry
                        lastException = ApiException(status, "Transient error (attempt ${attempt + 1}/$maxRetries)")
                    }
                    else -> {
                        // Client errors (4xx except 429) are not retryable
                        val body = runCatching { response.body<String>() }.getOrDefault("")
                        throw ApiException(status, body)
                    }
                }
            } catch (e: IOException) {
                // Network failures (timeout, DNS, connection reset) — retry
                lastException = e
            }

            if (attempt < maxRetries - 1) {
                delay(currentDelay)
                currentDelay *= 2
            }
        }

        // Final attempt — let exceptions propagate
        return try {
            val response = block()
            val status = response.status.value
            if (status in 200..299) {
                response
            } else {
                val body = runCatching { response.body<String>() }.getOrDefault("")
                throw ApiException(status, body)
            }
        } catch (e: IOException) {
            throw lastException ?: e
        }
    }

    companion object {
        private const val BASE_URL = "https://air-quality-api.open-meteo.com/v1/air-quality"
        private const val HOURLY_PARAMS = "birch_pollen,alder_pollen,grass_pollen,mugwort_pollen,ragweed_pollen,olive_pollen,pm2_5,pm10,european_aqi"

        private const val CONNECT_TIMEOUT_MS = 15_000L
        private const val REQUEST_TIMEOUT_MS = 30_000L
        private const val SOCKET_TIMEOUT_MS = 15_000L

        private val httpClient by lazy {
            HttpClient(OkHttp) {
                install(ContentNegotiation) {
                    json(Json { ignoreUnknownKeys = true })
                }
                install(HttpTimeout) {
                    connectTimeoutMillis = CONNECT_TIMEOUT_MS
                    requestTimeoutMillis = REQUEST_TIMEOUT_MS
                    socketTimeoutMillis = SOCKET_TIMEOUT_MS
                }
            }
        }
    }
}
