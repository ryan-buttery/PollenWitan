package com.ryan.pollenwitan.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.ryan.pollenwitan.data.security.DatabaseEncryption

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
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "pollenwitan.db"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                    .apply {
                        DatabaseEncryption.getSupportFactory()?.let {
                            openHelperFactory(it)
                        }
                    }
                    .build()
                    .also { INSTANCE = it }
            }
    }
}
