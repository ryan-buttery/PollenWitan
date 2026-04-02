package com.ryan.pollenwitan.data.security

import android.content.Context
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import java.io.File
import java.security.SecureRandom

/**
 * Manages SQLCipher encryption for the Room database.
 *
 * - Generates a random 32-byte passphrase, stored in EncryptedSharedPreferences
 *   (backed by Android Keystore AES256_GCM master key).
 * - Provides a [SupportOpenHelperFactory] for Room's `openHelperFactory()`.
 * - Handles one-time migration of an existing unencrypted DB to encrypted.
 * - Falls back to null (unencrypted Room) if encryption init fails.
 */
object DatabaseEncryption {

    private const val TAG = "DatabaseEncryption"
    private const val PREFS_FILE = "pollenwitan_db_key"
    private const val KEY_PASSPHRASE = "db_passphrase"
    private const val KEY_ENCRYPTED = "db_encrypted"
    private const val DB_NAME = "pollenwitan.db"

    @Volatile
    private var factory: SupportOpenHelperFactory? = null

    @Volatile
    private var initialized = false

    /**
     * Initialise encryption. Call once from Application.onCreate.
     * After this call, [getSupportFactory] returns the factory (or null on failure).
     */
    fun init(context: Context) {
        if (initialized) return
        synchronized(this) {
            if (initialized) return
            try {
                System.loadLibrary("sqlcipher")

                val appContext = context.applicationContext
                val prefs = getEncryptedPrefs(appContext)
                val passphrase = getOrCreatePassphrase(prefs)
                val dbIsEncrypted = ensureEncrypted(appContext, passphrase, prefs)

                if (dbIsEncrypted) {
                    // Verify we can actually open the DB with this passphrase
                    val dbFile = appContext.getDatabasePath(DB_NAME)
                    if (dbFile.exists() && !verifyEncryptedDb(dbFile, passphrase)) {
                        Log.e(TAG, "Cannot open encrypted DB with current passphrase — falling back to unencrypted")
                        prefs.edit().putBoolean(KEY_ENCRYPTED, false).apply()
                        factory = null
                        initialized = true
                        return
                    }
                    factory = SupportOpenHelperFactory(passphrase)
                    Log.i(TAG, "Database encryption active")
                } else {
                    Log.w(TAG, "Database remains unencrypted — migration failed or pending")
                    factory = null
                }
            } catch (e: Exception) {
                Log.e(TAG, "Encryption init failed — falling back to unencrypted DB", e)
                factory = null
            }
            initialized = true
        }
    }

    /**
     * Returns the [SupportOpenHelperFactory] for Room, or null if encryption is unavailable.
     */
    fun getSupportFactory(): SupportOpenHelperFactory? = factory

    private fun getEncryptedPrefs(context: Context) =
        EncryptedSharedPreferences.create(
            context,
            PREFS_FILE,
            MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build(),
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )

    private fun getOrCreatePassphrase(
        prefs: android.content.SharedPreferences
    ): ByteArray {
        val stored = prefs.getString(KEY_PASSPHRASE, null)
        if (stored != null) {
            return android.util.Base64.decode(stored, android.util.Base64.NO_WRAP)
        }

        val passphrase = ByteArray(32).also { SecureRandom().nextBytes(it) }
        prefs.edit()
            .putString(
                KEY_PASSPHRASE,
                android.util.Base64.encodeToString(passphrase, android.util.Base64.NO_WRAP)
            )
            .apply()
        return passphrase
    }

    /**
     * Ensures the database is encrypted. Returns true if the DB is (or is now) encrypted,
     * false if it remains unencrypted.
     */
    private fun ensureEncrypted(
        context: Context,
        passphrase: ByteArray,
        prefs: android.content.SharedPreferences
    ): Boolean {
        // Already confirmed encrypted on a previous launch
        if (prefs.getBoolean(KEY_ENCRYPTED, false)) return true

        val dbFile = context.getDatabasePath(DB_NAME)
        if (!dbFile.exists()) {
            // Fresh install — Room will create an encrypted DB via SupportOpenHelperFactory
            prefs.edit().putBoolean(KEY_ENCRYPTED, true).apply()
            return true
        }

        // DB exists — check if it's already encrypted
        if (isAlreadyEncrypted(dbFile)) {
            prefs.edit().putBoolean(KEY_ENCRYPTED, true).apply()
            return true
        }

        // DB exists and is unencrypted — attempt migration
        return migrateToEncrypted(dbFile, passphrase, prefs)
    }

    /**
     * Migrates an existing unencrypted DB to encrypted using sqlcipher_export.
     *
     * Creates the encrypted DB first with [openDatabase(path, byte[])] so the passphrase
     * goes through the same native [sqlite3_key] codepath that [SupportOpenHelperFactory]
     * uses later. The old plaintext DB is attached with an empty key and exported in.
     *
     * Returns true on success, false on failure (DB remains unencrypted).
     */
    private fun migrateToEncrypted(
        dbFile: File,
        passphrase: ByteArray,
        prefs: android.content.SharedPreferences
    ): Boolean {
        Log.i(TAG, "Migrating unencrypted database to encrypted...")

        val tempFile = File(dbFile.parent, "pollenwitan_encrypted.db")
        return try {
            // Create a new encrypted DB using byte[] passphrase — same native sqlite3_key
            // codepath that SupportOpenHelperFactory uses, avoiding encoding mismatches.
            val encryptedDb = net.zetetic.database.sqlcipher.SQLiteDatabase.openDatabase(
                tempFile.absolutePath, passphrase,
                null,
                net.zetetic.database.sqlcipher.SQLiteDatabase.OPEN_READWRITE or
                    net.zetetic.database.sqlcipher.SQLiteDatabase.CREATE_IF_NECESSARY,
                null, null
            )

            // Attach the old unencrypted DB with empty key (plaintext mode)
            encryptedDb.execSQL(
                "ATTACH DATABASE '${dbFile.absolutePath}' AS plaintext KEY ''"
            )

            // Export data from the plaintext attachment into the encrypted main DB
            encryptedDb.execSQL("SELECT sqlcipher_export('main', 'plaintext')")
            encryptedDb.execSQL("DETACH DATABASE plaintext")
            encryptedDb.close()

            // Swap files
            val backupFile = File(dbFile.parent, "pollenwitan_unencrypted.bak")
            dbFile.renameTo(backupFile)
            tempFile.renameTo(dbFile)
            backupFile.delete()

            // Clean up WAL/SHM files from the old unencrypted DB
            File(dbFile.parent, "$DB_NAME-wal").delete()
            File(dbFile.parent, "$DB_NAME-shm").delete()

            prefs.edit().putBoolean(KEY_ENCRYPTED, true).apply()
            Log.i(TAG, "Database migration to encrypted completed successfully")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Migration failed — DB remains unencrypted", e)
            tempFile.delete()
            false
        }
    }

    /**
     * Verify that the encrypted DB can actually be opened with the given passphrase.
     * Catches mismatches caused by passphrase loss (EncryptedSharedPreferences corruption)
     * or encoding bugs in prior migration code.
     */
    private fun verifyEncryptedDb(dbFile: File, passphrase: ByteArray): Boolean {
        return try {
            val db = net.zetetic.database.sqlcipher.SQLiteDatabase.openDatabase(
                dbFile.absolutePath, passphrase,
                null, net.zetetic.database.sqlcipher.SQLiteDatabase.OPEN_READONLY,
                null, null
            )
            db.close()
            true
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Quick check: try opening the DB with the standard Android SQLite API.
     * If it fails, the DB is likely already encrypted (or corrupt).
     */
    private fun isAlreadyEncrypted(dbFile: File): Boolean {
        return try {
            val db = android.database.sqlite.SQLiteDatabase.openDatabase(
                dbFile.absolutePath, null,
                android.database.sqlite.SQLiteDatabase.OPEN_READONLY
            )
            db.close()
            false // Opened fine with no passphrase → unencrypted
        } catch (_: Exception) {
            true // Can't open without passphrase → encrypted (or corrupt)
        }
    }
}
