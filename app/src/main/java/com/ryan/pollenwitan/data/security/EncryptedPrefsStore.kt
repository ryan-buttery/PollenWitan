package com.ryan.pollenwitan.data.security

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/**
 * Encrypted SharedPreferences wrapper with Flow-based observation.
 *
 * Provides reactive data access (like DataStore) backed by EncryptedSharedPreferences
 * (AES256-GCM encryption via Android Keystore). Use [data] to observe changes and
 * [edit] to modify values atomically.
 */
class EncryptedPrefsStore(context: Context, name: String) {

    private val masterKey = MasterKey.Builder(context.applicationContext)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    val prefs: SharedPreferences = EncryptedSharedPreferences.create(
        context.applicationContext,
        name,
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    private val version = MutableStateFlow(0)

    init {
        prefs.registerOnSharedPreferenceChangeListener { _, _ ->
            version.value++
        }
    }

    /** Flow that emits the SharedPreferences instance whenever any value changes. */
    val data: Flow<SharedPreferences> get() = version.map { prefs }

    /** Apply edits atomically. Reads within the block see the pre-edit state via [prefs]. */
    inline fun edit(block: SharedPreferences.Editor.() -> Unit) {
        val editor = prefs.edit()
        editor.block()
        editor.apply()
    }
}

/** Read a Double stored as a String. Returns [defValue] if absent or unparseable. */
fun SharedPreferences.getDouble(key: String, defValue: Double): Double =
    getString(key, null)?.toDoubleOrNull() ?: defValue

/** Read a Double stored as a String. Returns null if absent or unparseable. */
fun SharedPreferences.getDoubleOrNull(key: String): Double? =
    getString(key, null)?.toDoubleOrNull()

/** Store a Double as a String (SharedPreferences has no native Double support). */
fun SharedPreferences.Editor.putDouble(key: String, value: Double): SharedPreferences.Editor =
    putString(key, value.toString())
