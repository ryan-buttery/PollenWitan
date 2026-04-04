package com.ryan.pollenwitan.data.security

import android.content.Context
import android.util.Log
import java.io.File

/**
 * Handles one-time migration from an encrypted (SQLCipher) database to plain
 * Room.  SQLCipher was removed because on-disk file corruption on Android 16
 * secondary-user profiles caused the per-page HMAC to fail, turning minor
 * storage corruption into total data loss.
 *
 * On first launch after the update, if the existing DB file is still
 * SQLCipher-encrypted (can't be opened by framework SQLite), it is deleted so
 * Room can recreate it.  The [dbWasReset] flag tells the UI to warn the user.
 */
object DatabaseEncryption {

    private const val TAG = "DatabaseEncryption"
    private const val DB_NAME = "pollenwitan.db"

    /**
     * True if the database had to be deleted during migration from encrypted
     * to unencrypted.  UI should read this once and show a one-time warning.
     */
    @Volatile
    var dbWasReset = false
        private set

    /** Called by [AppDatabase] recovery logic when the DB had to be deleted. */
    fun markDbReset() {
        dbWasReset = true
    }

    /**
     * If the DB on disk is still SQLCipher-encrypted, delete it (and any
     * journal files) so Room can create a fresh unencrypted one.  Also cleans
     * up leftover encryption prefs files.
     *
     * Call once from Application.onCreate, before any Room access.
     */
    fun migrateAwayFromEncryption(context: Context) {
        val appContext = context.applicationContext
        val dbFile = appContext.getDatabasePath(DB_NAME)
        if (!dbFile.exists()) return

        if (isEncrypted(dbFile)) {
            Log.w(TAG, "Encrypted DB detected — deleting for unencrypted fresh start")
            dbFile.delete()
            File(dbFile.parent, "$DB_NAME-wal").delete()
            File(dbFile.parent, "$DB_NAME-shm").delete()
            File(dbFile.parent, "$DB_NAME-journal").delete()
            dbWasReset = true
        }

        // Clean up old encryption prefs regardless
        cleanupEncryptionPrefs(appContext)
    }

    /**
     * Try opening with framework SQLite.  If it fails the file is encrypted
     * (or corrupt — either way it needs to go).
     */
    private fun isEncrypted(dbFile: File): Boolean {
        return try {
            val db = android.database.sqlite.SQLiteDatabase.openDatabase(
                dbFile.absolutePath, null,
                android.database.sqlite.SQLiteDatabase.OPEN_READONLY
            )
            db.close()
            false
        } catch (_: Exception) {
            true
        }
    }

    private fun cleanupEncryptionPrefs(context: Context) {
        // Remove SharedPreferences files left by the old encryption code
        listOf("pollenwitan_db_key", "pollenwitan_db_key_backup").forEach { name ->
            try {
                val prefsFile = File(context.filesDir.parent, "shared_prefs/$name.xml")
                if (prefsFile.exists()) {
                    prefsFile.delete()
                    Log.d(TAG, "Cleaned up old prefs file: $name")
                }
            } catch (_: Exception) { /* best-effort */ }
        }
    }
}
