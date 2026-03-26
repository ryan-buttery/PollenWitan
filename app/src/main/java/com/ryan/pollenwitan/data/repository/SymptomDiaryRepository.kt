package com.ryan.pollenwitan.data.repository

import android.content.Context
import com.ryan.pollenwitan.data.local.AppDatabase
import com.ryan.pollenwitan.data.local.SymptomEntryEntity
import com.ryan.pollenwitan.domain.model.SymptomDiaryEntry
import com.ryan.pollenwitan.domain.model.SymptomRating
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.time.LocalDate

@Serializable
private data class RatingJson(
    val symptomId: String,
    val symptomName: String,
    val severity: Int
)

class SymptomDiaryRepository(private val context: Context) {

    private val dao = AppDatabase.getInstance(context).symptomEntryDao()
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun logEntry(entry: SymptomDiaryEntry) {
        dao.upsert(toEntity(entry))
    }

    fun observeTodayEntry(profileId: String): Flow<SymptomDiaryEntry?> {
        return dao.observeForDate(profileId, LocalDate.now().toString())
            .map { it?.let(::fromEntity) }
    }

    suspend fun getEntryForDate(profileId: String, date: LocalDate): SymptomDiaryEntry? {
        return dao.getForDate(profileId, date.toString())?.let(::fromEntity)
    }

    fun getHistory(
        profileId: String,
        startDate: LocalDate,
        endDate: LocalDate
    ): Flow<List<SymptomDiaryEntry>> {
        return dao.getForDateRange(profileId, startDate.toString(), endDate.toString())
            .map { entities -> entities.map(::fromEntity) }
    }

    private fun toEntity(entry: SymptomDiaryEntry): SymptomEntryEntity {
        val ratingsJsonStr = json.encodeToString(
            entry.ratings.map { RatingJson(it.symptomId, it.symptomName, it.severity) }
        )
        return SymptomEntryEntity(
            profileId = entry.profileId,
            date = entry.date.toString(),
            ratingsJson = ratingsJsonStr,
            loggedAtMillis = entry.loggedAtMillis,
            peakPollenJson = entry.peakPollenJson,
            peakAqi = entry.peakAqi,
            peakPm25 = entry.peakPm25,
            peakPm10 = entry.peakPm10
        )
    }

    private fun fromEntity(entity: SymptomEntryEntity): SymptomDiaryEntry {
        val ratings = try {
            json.decodeFromString<List<RatingJson>>(entity.ratingsJson)
                .map { SymptomRating(it.symptomId, it.symptomName, it.severity) }
        } catch (_: Exception) {
            emptyList()
        }
        return SymptomDiaryEntry(
            profileId = entity.profileId,
            date = LocalDate.parse(entity.date),
            ratings = ratings,
            loggedAtMillis = entity.loggedAtMillis,
            peakPollenJson = entity.peakPollenJson,
            peakAqi = entity.peakAqi,
            peakPm25 = entity.peakPm25,
            peakPm10 = entity.peakPm10
        )
    }
}
