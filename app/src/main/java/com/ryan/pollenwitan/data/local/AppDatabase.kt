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
    version = 5,
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

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Tag existing cached forecasts as the air-quality endpoint so the
                // new weather-forecast cache rows can coexist on the same table.
                db.execSQL(
                    "ALTER TABLE `cached_forecasts` ADD COLUMN `endpoint` TEXT NOT NULL DEFAULT 'air_quality'"
                )
            }
        }

        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Optional free-text observation field on diary entries.
                // Nullable column, no DEFAULT clause so the runtime schema matches
                // Room's entity-derived schema exactly (no @ColumnInfo defaultValue).
                db.execSQL(
                    "ALTER TABLE `symptom_entries` ADD COLUMN `notes` TEXT"
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
         * Builds and force-opens the database, recovering from corruption
         * if necessary.
         *
         * Recovery order:
         * 1. Normal open
         * 2. Delete journal files, retry
         * 3. Delete entire DB, recreate (last resort — data lost, user warned)
         */
        private fun openWithRecovery(context: Context): AppDatabase {
            val appContext = context.applicationContext
            val dbFile = appContext.getDatabasePath(DB_NAME)

            Log.i(TAG, "openWithRecovery: db exists=${dbFile.exists()}, size=${dbFile.length()}")

            // Attempt 1: normal open
            val db = buildDb(appContext)
            try {
                db.openHelper.writableDatabase
                Log.i(TAG, "Attempt 1 succeeded — normal open")
                return db
            } catch (e: Exception) {
                Log.e(TAG, "Attempt 1 failed — ${e.javaClass.simpleName}: ${e.message}")
                db.close()
            }

            // Attempt 2: delete journal files and retry
            File(dbFile.parent, "$DB_NAME-wal").delete()
            File(dbFile.parent, "$DB_NAME-shm").delete()
            File(dbFile.parent, "$DB_NAME-journal").delete()

            val db2 = buildDb(appContext)
            try {
                db2.openHelper.writableDatabase
                Log.i(TAG, "Attempt 2 succeeded — after journal cleanup")
                return db2
            } catch (e: Exception) {
                Log.e(TAG, "Attempt 2 failed — ${e.javaClass.simpleName}: ${e.message}")
                db2.close()
            }

            // Attempt 3: delete everything and let Room create fresh
            Log.e(TAG, "Attempt 3 — deleting database for fresh start")
            dbFile.delete()
            File(dbFile.parent, "$DB_NAME-wal").delete()
            File(dbFile.parent, "$DB_NAME-shm").delete()
            File(dbFile.parent, "$DB_NAME-journal").delete()
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
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
                .build()
    }
}
