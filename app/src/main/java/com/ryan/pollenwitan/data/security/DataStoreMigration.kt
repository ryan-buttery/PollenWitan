package com.ryan.pollenwitan.data.security

import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import java.io.File

/**
 * One-time migration from unencrypted DataStore Preferences to EncryptedSharedPreferences.
 *
 * Call [migrateIfNeeded] from Application.onCreate() before any repository is created.
 * Each store is migrated independently; partial migration is safe (idempotent per store).
 */
private val Context.oldProfilesStore by preferencesDataStore(name = "profiles")
private val Context.oldLocationStore by preferencesDataStore(name = "location_settings")
private val Context.oldMedicinesStore by preferencesDataStore(name = "medicines")
private val Context.oldDoseTrackingStore by preferencesDataStore(name = "dose_tracking")

object DataStoreMigration {

    private const val TAG = "DataStoreMigration"
    private const val MARKER_PREFS = "migration_marker"
    private const val KEY_DONE = "datastore_migration_v1"

    suspend fun migrateIfNeeded(context: Context) {
        val marker = EncryptedPrefsStore(context, MARKER_PREFS)
        if (marker.prefs.getBoolean(KEY_DONE, false)) return

        Log.i(TAG, "Checking for DataStore → EncryptedPrefs migration...")

        migrateStore(context, "profiles", context.oldProfilesStore, "profiles_encrypted")
        migrateStore(context, "location_settings", context.oldLocationStore, "location_encrypted")
        migrateStore(context, "medicines", context.oldMedicinesStore, "medicines_encrypted")
        migrateStore(context, "dose_tracking", context.oldDoseTrackingStore, "dose_tracking_encrypted")

        marker.edit { putBoolean(KEY_DONE, true) }
        Log.i(TAG, "DataStore migration complete")
    }

    private suspend fun migrateStore(
        context: Context,
        oldFileName: String,
        oldStore: DataStore<Preferences>,
        encryptedName: String
    ) {
        val dataStoreDir = File(context.filesDir, "datastore")
        val oldFile = File(dataStoreDir, "${oldFileName}.preferences_pb")
        if (!oldFile.exists()) return

        val target = EncryptedPrefsStore(context, encryptedName)
        if (target.prefs.all.isNotEmpty()) {
            // Already migrated — clean up old DataStore files
            deleteDataStoreFiles(dataStoreDir, oldFileName)
            Log.d(TAG, "Cleaned up old DataStore files: $oldFileName")
            return
        }

        try {
            val oldData = oldStore.data.first()
            target.edit {
                oldData.asMap().forEach { (key, value) ->
                    when (value) {
                        is String -> putString(key.name, value)
                        is Boolean -> putBoolean(key.name, value)
                        is Int -> putInt(key.name, value)
                        is Float -> putFloat(key.name, value)
                        is Long -> putLong(key.name, value)
                        is Double -> putString(key.name, value.toString())
                        is Set<*> -> {
                            @Suppress("UNCHECKED_CAST")
                            putStringSet(key.name, value as Set<String>)
                        }
                    }
                }
            }
            deleteDataStoreFiles(dataStoreDir, oldFileName)
            Log.i(TAG, "Migrated $oldFileName → $encryptedName (${oldData.asMap().size} keys)")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to migrate $oldFileName — data preserved in old format", e)
        }
    }

    /** Delete the DataStore .preferences_pb file and any .tmp/.bak files it may have created. */
    private fun deleteDataStoreFiles(dataStoreDir: File, fileName: String) {
        File(dataStoreDir, "${fileName}.preferences_pb").delete()
        File(dataStoreDir, "${fileName}.preferences_pb.tmp").delete()
        File(dataStoreDir, "${fileName}.preferences_pb.bak").delete()
    }
}
