package com.ryan.pollenwitan.data.repository

import com.ryan.pollenwitan.domain.model.CurrentConditions

interface AirQualityRepository {
    suspend fun getCurrentConditions(latitude: Double, longitude: Double): Result<CurrentConditions>
}
