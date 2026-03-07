package com.ryan.pollenwitan.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "cached_forecasts")
data class CachedForecastEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val latitude: Double,
    val longitude: Double,
    val forecastDays: Int,
    val fetchedAtMillis: Long,
    val responseJson: String
)
