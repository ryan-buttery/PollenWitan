package com.ryan.pollenwitan.data.repository

import com.ryan.pollenwitan.domain.model.CurrentConditions
import com.ryan.pollenwitan.domain.model.ForecastDay

interface AirQualityRepository {
    suspend fun getCurrentConditions(latitude: Double, longitude: Double): Result<CurrentConditions>
    suspend fun getForecast(latitude: Double, longitude: Double, days: Int = 4): Result<List<ForecastDay>>
}
