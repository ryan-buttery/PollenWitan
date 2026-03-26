package com.ryan.pollenwitan.data.repository

import android.content.Context
import com.ryan.pollenwitan.data.security.EncryptedPrefsStore
import com.ryan.pollenwitan.domain.model.Medicine
import com.ryan.pollenwitan.domain.model.MedicineType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class MedicineRepository(
    context: Context
) {

    private val store = EncryptedPrefsStore(context, "medicines_encrypted")

    private object Keys {
        const val MEDICINE_IDS = "medicine_ids"
        fun name(id: String) = "medicine_${id}_name"
        fun type(id: String) = "medicine_${id}_type"
    }

    fun getMedicines(): Flow<List<Medicine>> = store.data.map { prefs ->
        val ids = prefs.getStringSet(Keys.MEDICINE_IDS, null) ?: return@map emptyList()
        ids.mapNotNull { id ->
            val name = prefs.getString(Keys.name(id), null) ?: return@mapNotNull null
            val typeName = prefs.getString(Keys.type(id), null) ?: return@mapNotNull null
            val type = MedicineType.entries.find { it.name == typeName } ?: return@mapNotNull null
            Medicine(id = id, name = name, type = type)
        }.sortedBy { it.name }
    }

    suspend fun addMedicine(medicine: Medicine) {
        store.edit {
            val ids = store.prefs.getStringSet(Keys.MEDICINE_IDS, null)?.toMutableSet() ?: mutableSetOf()
            ids.add(medicine.id)
            putStringSet(Keys.MEDICINE_IDS, ids)
            putString(Keys.name(medicine.id), medicine.name)
            putString(Keys.type(medicine.id), medicine.type.name)
        }
    }

    suspend fun updateMedicine(medicine: Medicine) {
        store.edit {
            putString(Keys.name(medicine.id), medicine.name)
            putString(Keys.type(medicine.id), medicine.type.name)
        }
    }

    suspend fun deleteMedicine(medicineId: String) {
        store.edit {
            val ids = store.prefs.getStringSet(Keys.MEDICINE_IDS, null)?.toMutableSet() ?: return@edit
            ids.remove(medicineId)
            putStringSet(Keys.MEDICINE_IDS, ids)
            remove(Keys.name(medicineId))
            remove(Keys.type(medicineId))
        }
    }
}
