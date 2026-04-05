package com.ryan.pollenwitan.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface SymptomEntryDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: SymptomEntryEntity)

    @Query("SELECT * FROM symptom_entries WHERE profileId = :profileId AND date = :date LIMIT 1")
    suspend fun getForDate(profileId: String, date: String): SymptomEntryEntity?

    @Query("SELECT * FROM symptom_entries WHERE profileId = :profileId AND date = :date LIMIT 1")
    fun observeForDate(profileId: String, date: String): Flow<SymptomEntryEntity?>

    @Query(
        """
        SELECT * FROM symptom_entries
        WHERE profileId = :profileId AND date BETWEEN :startDate AND :endDate
        ORDER BY date DESC
        """
    )
    fun getForDateRange(
        profileId: String,
        startDate: String,
        endDate: String
    ): Flow<List<SymptomEntryEntity>>

    @Query("DELETE FROM symptom_entries WHERE profileId = :profileId AND date = :date")
    suspend fun deleteForDate(profileId: String, date: String)

    @Query("DELETE FROM symptom_entries WHERE date < :beforeDate")
    suspend fun deleteOlderThan(beforeDate: String)

    @Query("SELECT * FROM symptom_entries ORDER BY date DESC")
    suspend fun getAll(): List<SymptomEntryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entities: List<SymptomEntryEntity>)

    @Query("DELETE FROM symptom_entries")
    suspend fun deleteAll()
}
