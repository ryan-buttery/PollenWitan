package com.ryan.pollenwitan.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface CachedForecastDao {

    @Query(
        """
        SELECT * FROM cached_forecasts
        WHERE latitude = :latitude
          AND longitude = :longitude
          AND forecastDays = :forecastDays
          AND endpoint = :endpoint
        ORDER BY fetchedAtMillis DESC
        LIMIT 1
        """
    )
    suspend fun getLatest(
        latitude: Double,
        longitude: Double,
        forecastDays: Int,
        endpoint: String
    ): CachedForecastEntity?

    @Insert
    suspend fun insert(entity: CachedForecastEntity)

    @Query("DELETE FROM cached_forecasts WHERE fetchedAtMillis < :olderThanMillis")
    suspend fun deleteOlderThan(olderThanMillis: Long)
}
