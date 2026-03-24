package com.ryan.pollenwitan.data.repository

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.ryan.pollenwitan.domain.model.Medicine
import com.ryan.pollenwitan.domain.model.MedicineType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.medicineDataStore by preferencesDataStore(name = "medicines")

class MedicineRepository(
    private val context: Context
) {

    private val dataStore get() = context.medicineDataStore

    private object Keys {
        val MEDICINE_IDS = stringSetPreferencesKey("medicine_ids")
        fun name(id: String) = stringPreferencesKey("medicine_${id}_name")
        fun type(id: String) = stringPreferencesKey("medicine_${id}_type")
    }

    fun getMedicines(): Flow<List<Medicine>> = dataStore.data.map { prefs ->
        val ids = prefs[Keys.MEDICINE_IDS] ?: return@map emptyList()
        ids.mapNotNull { id ->
            val name = prefs[Keys.name(id)] ?: return@mapNotNull null
            val typeName = prefs[Keys.type(id)] ?: return@mapNotNull null
            val type = MedicineType.entries.find { it.name == typeName } ?: return@mapNotNull null
            Medicine(id = id, name = name, type = type)
        }.sortedBy { it.name }
    }

    suspend fun addMedicine(medicine: Medicine) {
        dataStore.edit { prefs ->
            val ids = prefs[Keys.MEDICINE_IDS]?.toMutableSet() ?: mutableSetOf()
            ids.add(medicine.id)
            prefs[Keys.MEDICINE_IDS] = ids
            prefs[Keys.name(medicine.id)] = medicine.name
            prefs[Keys.type(medicine.id)] = medicine.type.name
        }
    }

    suspend fun updateMedicine(medicine: Medicine) {
        dataStore.edit { prefs ->
            prefs[Keys.name(medicine.id)] = medicine.name
            prefs[Keys.type(medicine.id)] = medicine.type.name
        }
    }

    suspend fun deleteMedicine(medicineId: String) {
        dataStore.edit { prefs ->
            val ids = prefs[Keys.MEDICINE_IDS]?.toMutableSet() ?: return@edit
            ids.remove(medicineId)
            prefs[Keys.MEDICINE_IDS] = ids
            prefs.remove(Keys.name(medicineId))
            prefs.remove(Keys.type(medicineId))
        }
    }
}
