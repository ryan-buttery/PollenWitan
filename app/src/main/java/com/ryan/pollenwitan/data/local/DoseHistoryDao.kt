package com.ryan.pollenwitan.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface DoseHistoryDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: DoseHistoryEntity)

    @Query(
        """
        SELECT * FROM dose_history
        WHERE profileId = :profileId AND date = :date AND confirmed = 1
        ORDER BY reminderHour ASC, slotIndex ASC
        """
    )
    suspend fun getConfirmedForDate(profileId: String, date: String): List<DoseHistoryEntity>

    @Query(
        """
        SELECT * FROM dose_history
        WHERE profileId = :profileId AND date BETWEEN :startDate AND :endDate
        ORDER BY date DESC, reminderHour ASC, slotIndex ASC
        """
    )
    suspend fun getForDateRange(
        profileId: String,
        startDate: String,
        endDate: String
    ): List<DoseHistoryEntity>

    @Query(
        """
        SELECT * FROM dose_history
        WHERE profileId = :profileId AND date BETWEEN :startDate AND :endDate AND confirmed = 1
        ORDER BY date DESC, reminderHour ASC, slotIndex ASC
        """
    )
    fun getConfirmedForDateRange(
        profileId: String,
        startDate: String,
        endDate: String
    ): Flow<List<DoseHistoryEntity>>

    @Query("DELETE FROM dose_history WHERE date < :beforeDate")
    suspend fun deleteOlderThan(beforeDate: String)

    @Query("SELECT * FROM dose_history ORDER BY date DESC, reminderHour ASC")
    suspend fun getAll(): List<DoseHistoryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entities: List<DoseHistoryEntity>)

    @Query("DELETE FROM dose_history")
    suspend fun deleteAll()
}
