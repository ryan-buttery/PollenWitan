package com.ryan.pollenwitan.data.security

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import java.io.File
import java.security.MessageDigest
import java.security.SecureRandom

/**
 * Manages SQLCipher encryption for the Room database.
 *
 * - Generates a random 32-byte passphrase, stored in EncryptedSharedPreferences
 *   (backed by Android Keystore AES256_GCM master key).
 * - Keeps a plaintext backup of the passphrase in regular SharedPreferences
 *   so that Android Keystore invalidation doesn't cause data loss.
 * - Provides a [SupportOpenHelperFactory] for Room's `openHelperFactory()`.
 * - Handles one-time migration of an existing unencrypted DB to encrypted.
 * - Falls back to null (unencrypted Room) if encryption init fails.
 */
object DatabaseEncryption {

    private const val TAG = "DatabaseEncryption"
    private const val PREFS_FILE = "pollenwitan_db_key"
    private const val BACKUP_PREFS_FILE = "pollenwitan_db_key_backup"
    private const val KEY_PASSPHRASE = "db_passphrase"
    private const val KEY_PASSPHRASE_FINGERPRINT = "db_passphrase_fp"
    private const val KEY_ENCRYPTED = "db_encrypted"
    private const val DB_NAME = "pollenwitan.db"

    @Volatile
    private var factory: SupportOpenHelperFactory? = null

    @Volatile
    private var initialized = false

    /**
     * True if the encrypted database had to be deleted because the passphrase was
     * unrecoverable or the file was corrupted beyond repair.  UI should read this
     * once and show a one-time warning to the user.
     */
    @Volatile
    var dbWasReset = false
        private set

    /** Called by [AppDatabase] recovery logic when the DB had to be deleted. */
    fun markDbReset() {
        dbWasReset = true
    }

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
                val backupPrefs = appContext.getSharedPreferences(BACKUP_PREFS_FILE, Context.MODE_PRIVATE)
                val espPrefs = getEncryptedPrefs(appContext)
                if (espPrefs == null) {
                    Log.e(TAG, "EncryptedSharedPreferences unavailable — trying backup passphrase")
                    // ESP is broken, but we might still have the backup
                    val passphrase = getPassphraseFromBackup(backupPrefs)
                    if (passphrase != null) {
                        Log.i(TAG, "Recovered passphrase from backup [fp=${fingerprint(passphrase)}]")
                        factory = SupportOpenHelperFactory(passphrase)
                    } else {
                        Log.e(TAG, "No backup passphrase available — falling back to unencrypted DB")
                        factory = null
                    }
                    initialized = true
                    return
                }

                val passphrase = getOrCreatePassphrase(espPrefs, backupPrefs)
                val dbIsEncrypted = ensureEncrypted(appContext, passphrase, espPrefs)

                if (dbIsEncrypted) {
                    val dbFile = appContext.getDatabasePath(DB_NAME)
                    if (dbFile.exists()) {
                        val walFile = File(dbFile.parent, "$DB_NAME-wal")
                        val shmFile = File(dbFile.parent, "$DB_NAME-shm")
                        Log.i(TAG, "DB verify: file=${dbFile.length()}b" +
                                ", wal=${if (walFile.exists()) "${walFile.length()}b" else "none"}" +
                                ", shm=${if (shmFile.exists()) "${shmFile.length()}b" else "none"}" +
                                ", fp=${fingerprint(passphrase)}")

                        if (!verifyEncryptedDb(dbFile, passphrase)) {
                            Log.w(TAG, "Verify failed with ESP passphrase — clearing WAL/SHM and retrying")
                            walFile.delete()
                            shmFile.delete()

                            if (!verifyEncryptedDb(dbFile, passphrase)) {
                                // ESP passphrase doesn't work — try backup passphrase
                                val backupPassphrase = getPassphraseFromBackup(backupPrefs)
                                if (backupPassphrase != null && !backupPassphrase.contentEquals(passphrase)) {
                                    Log.w(TAG, "ESP passphrase [fp=${fingerprint(passphrase)}] differs from backup [fp=${fingerprint(backupPassphrase)}] — trying backup")
                                    if (verifyEncryptedDb(dbFile, backupPassphrase)) {
                                        Log.i(TAG, "Backup passphrase works — re-syncing to ESP")
                                        resyncPassphrase(backupPassphrase, espPrefs, backupPrefs)
                                        factory = SupportOpenHelperFactory(backupPassphrase)
                                        Log.i(TAG, "Database encryption active (recovered from backup)")
                                        initialized = true
                                        return
                                    }
                                    Log.e(TAG, "Backup passphrase also failed")
                                }
                                // Both passphrases failed — data is unrecoverable
                                Log.e(TAG, "Cannot open encrypted DB — deleting for fresh start")
                                dbFile.delete()
                                File(dbFile.parent, "$DB_NAME-wal").delete()
                                File(dbFile.parent, "$DB_NAME-shm").delete()
                                dbWasReset = true
                            }
                        }
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

    // ---- Passphrase management ----

    private fun getOrCreatePassphrase(
        espPrefs: SharedPreferences,
        backupPrefs: SharedPreferences
    ): ByteArray {
        val stored = espPrefs.getString(KEY_PASSPHRASE, null)
        if (stored != null) {
            val passphrase = android.util.Base64.decode(stored, android.util.Base64.NO_WRAP)
            val fp = fingerprint(passphrase)
            val backupFp = backupPrefs.getString(KEY_PASSPHRASE_FINGERPRINT, null)

            if (backupFp != null && backupFp != fp) {
                // ESP returned a different passphrase than what we stored before.
                // The Keystore may have been re-keyed. Try the backup.
                Log.w(TAG, "ESP passphrase fingerprint mismatch: esp=$fp, backup=$backupFp")
                val backupPassphrase = getPassphraseFromBackup(backupPrefs)
                if (backupPassphrase != null && fingerprint(backupPassphrase) == backupFp) {
                    Log.i(TAG, "Using backup passphrase [fp=$backupFp] — ESP was re-keyed")
                    return backupPassphrase
                }
            }

            // Ensure backup is in sync
            savePassphraseBackup(passphrase, backupPrefs)
            Log.d(TAG, "Passphrase loaded from ESP [fp=$fp]")
            return passphrase
        }

        // No passphrase in ESP — check backup before generating new
        val backupPassphrase = getPassphraseFromBackup(backupPrefs)
        if (backupPassphrase != null) {
            Log.i(TAG, "ESP empty but backup exists [fp=${fingerprint(backupPassphrase)}] — restoring to ESP")
            resyncPassphrase(backupPassphrase, espPrefs, backupPrefs)
            return backupPassphrase
        }

        // Truly fresh — generate new passphrase
        val passphrase = ByteArray(32).also { SecureRandom().nextBytes(it) }
        val encoded = android.util.Base64.encodeToString(passphrase, android.util.Base64.NO_WRAP)
        espPrefs.edit().putString(KEY_PASSPHRASE, encoded).commit()
        savePassphraseBackup(passphrase, backupPrefs)
        Log.i(TAG, "Generated new passphrase [fp=${fingerprint(passphrase)}]")
        return passphrase
    }

    private fun savePassphraseBackup(passphrase: ByteArray, backupPrefs: SharedPreferences) {
        val encoded = android.util.Base64.encodeToString(passphrase, android.util.Base64.NO_WRAP)
        backupPrefs.edit()
            .putString(KEY_PASSPHRASE, encoded)
            .putString(KEY_PASSPHRASE_FINGERPRINT, fingerprint(passphrase))
            .commit()
    }

    private fun getPassphraseFromBackup(backupPrefs: SharedPreferences): ByteArray? {
        val stored = backupPrefs.getString(KEY_PASSPHRASE, null) ?: return null
        return try {
            android.util.Base64.decode(stored, android.util.Base64.NO_WRAP)
        } catch (_: Exception) {
            null
        }
    }

    private fun resyncPassphrase(
        passphrase: ByteArray,
        espPrefs: SharedPreferences,
        backupPrefs: SharedPreferences
    ) {
        val encoded = android.util.Base64.encodeToString(passphrase, android.util.Base64.NO_WRAP)
        espPrefs.edit().putString(KEY_PASSPHRASE, encoded).commit()
        savePassphraseBackup(passphrase, backupPrefs)
    }

    /** First 8 hex chars of SHA-256 — enough to detect changes, safe to log. */
    private fun fingerprint(passphrase: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(passphrase)
        return digest.take(4).joinToString("") { "%02x".format(it) }
    }

    // ---- Encrypted SharedPreferences ----

    private fun getEncryptedPrefs(context: Context): SharedPreferences? =
        try {
            EncryptedSharedPreferences.create(
                context,
                PREFS_FILE,
                MasterKey.Builder(context)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build(),
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open EncryptedSharedPreferences — Keystore may be corrupted", e)
            null
        }

    // ---- DB state management ----

    /**
     * Ensures the database is encrypted. Returns true if the DB is (or is now) encrypted,
     * false if it remains unencrypted.
     */
    private fun ensureEncrypted(
        context: Context,
        passphrase: ByteArray,
        prefs: SharedPreferences
    ): Boolean {
        // Already confirmed encrypted on a previous launch
        if (prefs.getBoolean(KEY_ENCRYPTED, false)) return true

        val dbFile = context.getDatabasePath(DB_NAME)
        if (!dbFile.exists()) {
            // Fresh install — Room will create an encrypted DB via SupportOpenHelperFactory
            prefs.edit().putBoolean(KEY_ENCRYPTED, true).commit()
            return true
        }

        // DB exists — check if it's already encrypted
        if (isAlreadyEncrypted(dbFile)) {
            prefs.edit().putBoolean(KEY_ENCRYPTED, true).commit()
            return true
        }

        // DB exists and is unencrypted — attempt migration
        return migrateToEncrypted(dbFile, passphrase, prefs)
    }

    /**
     * Migrates an existing unencrypted DB to encrypted using sqlcipher_export.
     */
    private fun migrateToEncrypted(
        dbFile: File,
        passphrase: ByteArray,
        prefs: SharedPreferences
    ): Boolean {
        Log.i(TAG, "Migrating unencrypted database to encrypted...")

        val tempFile = File(dbFile.parent, "pollenwitan_encrypted.db")
        return try {
            val encryptedDb = net.zetetic.database.sqlcipher.SQLiteDatabase.openDatabase(
                tempFile.absolutePath, passphrase,
                null,
                net.zetetic.database.sqlcipher.SQLiteDatabase.OPEN_READWRITE or
                    net.zetetic.database.sqlcipher.SQLiteDatabase.CREATE_IF_NECESSARY,
                null, null
            )

            encryptedDb.execSQL(
                "ATTACH DATABASE '${dbFile.absolutePath}' AS plaintext KEY ''"
            )
            encryptedDb.execSQL("SELECT sqlcipher_export('main', 'plaintext')")
            encryptedDb.execSQL("DETACH DATABASE plaintext")
            encryptedDb.close()

            val backupFile = File(dbFile.parent, "pollenwitan_unencrypted.bak")
            dbFile.renameTo(backupFile)
            tempFile.renameTo(dbFile)
            backupFile.delete()

            File(dbFile.parent, "$DB_NAME-wal").delete()
            File(dbFile.parent, "$DB_NAME-shm").delete()

            prefs.edit().putBoolean(KEY_ENCRYPTED, true).commit()
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
     */
    private fun verifyEncryptedDb(dbFile: File, passphrase: ByteArray): Boolean {
        return try {
            // Use OPEN_READWRITE to match Room's open behaviour — WAL recovery
            // only runs in read-write mode, so OPEN_READONLY can falsely pass.
            val db = net.zetetic.database.sqlcipher.SQLiteDatabase.openDatabase(
                dbFile.absolutePath, passphrase,
                null, net.zetetic.database.sqlcipher.SQLiteDatabase.OPEN_READWRITE,
                null, null
            )
            db.close()
            true
        } catch (e: Exception) {
            Log.w(TAG, "verifyEncryptedDb failed: ${e.javaClass.simpleName}: ${e.message}")
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
            false
        } catch (_: Exception) {
            true
        }
    }
}
