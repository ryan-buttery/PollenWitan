package com.ryan.pollenwitan.data.repository

import com.ryan.pollenwitan.domain.model.AppLocation
import com.ryan.pollenwitan.domain.model.LocationMode
import kotlinx.coroutines.flow.Flow

interface LocationRepository {
    fun getLocation(): Flow<AppLocation>
    fun getLocationMode(): Flow<LocationMode>
    suspend fun setManualLocation(latitude: Double, longitude: Double, displayName: String)
    suspend fun setLocationMode(mode: LocationMode)
    suspend fun updateGpsLocation(latitude: Double, longitude: Double, displayName: String)
}
