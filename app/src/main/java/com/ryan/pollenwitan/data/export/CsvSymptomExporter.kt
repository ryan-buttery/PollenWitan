package com.ryan.pollenwitan.data.export

import android.content.Context
import com.ryan.pollenwitan.data.local.AppDatabase
import kotlinx.serialization.json.Json
import java.io.OutputStream

/**
 * Exports one profile's symptom diary as CSV for medical practitioners.
 *
 * Columns are dynamic based on available data:
 * Date, [Symptom1], [Symptom2], ..., Peak AQI, Peak PM2.5, Peak PM10, [Birch Pollen], ...
 */
class CsvSymptomExporter(private val context: Context) {

    private val json = Json { ignoreUnknownKeys = true }

    suspend fun export(profileId: String, outputStream: OutputStream) {
        val db = AppDatabase.getInstance(context)
        val entries = db.symptomEntryDao().getAll()
            .filter { it.profileId == profileId }
            .sortedBy { it.date }

        if (entries.isEmpty()) {
            outputStream.bufferedWriter().use { it.write("No symptom data found for this profile.\n") }
            return
        }

        // Collect all unique symptom names and pollen types across all entries
        val allSymptomNames = mutableListOf<String>()
        val allPollenTypes = mutableListOf<String>()
        val parsedEntries = entries.map { entity ->
            val ratings = parseRatings(entity.ratingsJson)
            val pollen = parsePollen(entity.peakPollenJson)
            for (name in ratings.keys) {
                if (name !in allSymptomNames) allSymptomNames.add(name)
            }
            for (type in pollen.keys) {
                if (type !in allPollenTypes) allPollenTypes.add(type)
            }
            Triple(entity, ratings, pollen)
        }

        outputStream.bufferedWriter().use { writer ->
            // Header
            val headers = buildList {
                add("Date")
                addAll(allSymptomNames)
                add("Peak AQI")
                add("Peak PM2.5")
                add("Peak PM10")
                addAll(allPollenTypes.map { "$it Pollen" })
            }
            writer.write(headers.joinToString(",") { escapeCsv(it) })
            writer.newLine()

            // Data rows
            for ((entity, ratings, pollen) in parsedEntries) {
                val row = buildList {
                    add(entity.date)
                    for (name in allSymptomNames) {
                        add((ratings[name] ?: "").toString())
                    }
                    add(entity.peakAqi.toString())
                    add(entity.peakPm25.toString())
                    add(entity.peakPm10.toString())
                    for (type in allPollenTypes) {
                        add((pollen[type] ?: "").toString())
                    }
                }
                writer.write(row.joinToString(",") { escapeCsv(it) })
                writer.newLine()
            }
        }
    }

    private fun parseRatings(ratingsJson: String): Map<String, Int> {
        return try {
            @kotlinx.serialization.Serializable
            data class Rating(
                val symptomId: String = "",
                val symptomName: String,
                val severity: Int
            )
            json.decodeFromString<List<Rating>>(ratingsJson)
                .associate { it.symptomName to it.severity }
        } catch (_: Exception) {
            emptyMap()
        }
    }

    private fun parsePollen(peakPollenJson: String): Map<String, Double> {
        return try {
            json.decodeFromString<Map<String, Double>>(peakPollenJson)
        } catch (_: Exception) {
            emptyMap()
        }
    }

    private fun escapeCsv(value: String): String {
        return if (value.contains(',') || value.contains('"') || value.contains('\n')) {
            "\"${value.replace("\"", "\"\"")}\""
        } else {
            value
        }
    }
}
