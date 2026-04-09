package com.ryan.pollenwitan.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "cached_forecasts")
data class CachedForecastEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val latitude: Double,
    val longitude: Double,
    val forecastDays: Int,
    val fetchedAtMillis: Long,
    val responseJson: String,
    // defaultValue must match MIGRATION_3_4 so Room's runtime schema check passes
    @ColumnInfo(defaultValue = ENDPOINT_AIR_QUALITY)
    val endpoint: String = ENDPOINT_AIR_QUALITY
) {
    companion object {
        const val ENDPOINT_AIR_QUALITY = "air_quality"
        const val ENDPOINT_WEATHER = "weather"
    }
}
