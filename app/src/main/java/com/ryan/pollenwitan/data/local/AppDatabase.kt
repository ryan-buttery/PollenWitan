package com.ryan.pollenwitan.data.local

import android.content.Context
import android.util.Log
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.ryan.pollenwitan.data.security.DatabaseEncryption
import java.io.File

@Database(
    entities = [CachedForecastEntity::class, DoseHistoryEntity::class, SymptomEntryEntity::class],
    version = 3,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun cachedForecastDao(): CachedForecastDao
    abstract fun doseHistoryDao(): DoseHistoryDao
    abstract fun symptomEntryDao(): SymptomEntryDao

    companion object {
        private const val TAG = "AppDatabase"
        private const val DB_NAME = "pollenwitan.db"

        @Volatile
        private var INSTANCE: AppDatabase? = null

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `dose_history` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `profileId` TEXT NOT NULL,
                        `medicineId` TEXT NOT NULL,
                        `slotIndex` INTEGER NOT NULL,
                        `date` TEXT NOT NULL,
                        `confirmedAtMillis` INTEGER NOT NULL,
                        `confirmed` INTEGER NOT NULL,
                        `medicineName` TEXT NOT NULL,
                        `dose` INTEGER NOT NULL,
                        `medicineType` TEXT NOT NULL,
                        `reminderHour` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE INDEX IF NOT EXISTS `index_dose_history_profileId_date`
                        ON `dose_history` (`profileId`, `date`)
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE UNIQUE INDEX IF NOT EXISTS `index_dose_history_profileId_date_medicineId_slotIndex`
                        ON `dose_history` (`profileId`, `date`, `medicineId`, `slotIndex`)
                    """.trimIndent()
                )
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `symptom_entries` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `profileId` TEXT NOT NULL,
                        `date` TEXT NOT NULL,
                        `ratingsJson` TEXT NOT NULL,
                        `loggedAtMillis` INTEGER NOT NULL,
                        `peakPollenJson` TEXT NOT NULL,
                        `peakAqi` INTEGER NOT NULL,
                        `peakPm25` REAL NOT NULL,
                        `peakPm10` REAL NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE UNIQUE INDEX IF NOT EXISTS `index_symptom_entries_profileId_date`
                        ON `symptom_entries` (`profileId`, `date`)
                    """.trimIndent()
                )
            }
        }

        fun getInstance(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: openWithRecovery(context).also { INSTANCE = it }
            }

        /**
         * Builds and force-opens the database, recovering from WAL corruption
         * or unreadable encrypted files if necessary.
         *
         * Recovery order:
         * 1. Normal open
         * 2. Delete WAL/SHM, retry (fixes partial-write WAL corruption)
         * 3. Delete entire DB, recreate (last resort — data lost, user warned)
         */
        private fun openWithRecovery(context: Context): AppDatabase {
            val appContext = context.applicationContext

            // Attempt 1: normal open
            val db = buildDb(appContext)
            try {
                db.openHelper.writableDatabase
                return db
            } catch (e: Exception) {
                Log.e(TAG, "Database open failed — attempting WAL cleanup", e)
                db.close()
            }

            // Attempt 2: delete WAL/SHM files and retry
            val dbFile = appContext.getDatabasePath(DB_NAME)
            File(dbFile.parent, "$DB_NAME-wal").delete()
            File(dbFile.parent, "$DB_NAME-shm").delete()

            val db2 = buildDb(appContext)
            try {
                db2.openHelper.writableDatabase
                Log.i(TAG, "Recovery successful after WAL cleanup")
                return db2
            } catch (e: Exception) {
                Log.e(TAG, "WAL cleanup insufficient — deleting database for fresh start", e)
                db2.close()
            }

            // Attempt 3: delete everything and let Room create fresh
            dbFile.delete()
            File(dbFile.parent, "$DB_NAME-wal").delete()
            File(dbFile.parent, "$DB_NAME-shm").delete()
            DatabaseEncryption.markDbReset()

            return buildDb(appContext)
        }

        private fun buildDb(context: Context): AppDatabase =
            Room.databaseBuilder(
                context,
                AppDatabase::class.java,
                DB_NAME
            )
                .setJournalMode(JournalMode.TRUNCATE)
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                .apply {
                    DatabaseEncryption.getSupportFactory()?.let {
                        openHelperFactory(it)
                    }
                }
                .build()
    }
}
