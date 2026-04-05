package com.ryan.pollenwitan

import android.app.Application
import androidx.work.BackoffPolicy
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.ryan.pollenwitan.data.security.DatabaseEncryption
import com.ryan.pollenwitan.data.security.DataStoreMigration
import com.ryan.pollenwitan.worker.NotificationHelper
import com.ryan.pollenwitan.worker.PollenCheckWorker
import kotlinx.coroutines.runBlocking
import java.util.concurrent.TimeUnit

class PollenWitanApp : Application() {
    override fun onCreate() {
        super.onCreate()

        DatabaseEncryption.migrateAwayFromEncryption(this)
        runBlocking { DataStoreMigration.migrateIfNeeded(this@PollenWitanApp) }
        NotificationHelper.createChannels(this)
        schedulePollenCheck()
    }

    private fun schedulePollenCheck() {
        val request = PeriodicWorkRequestBuilder<PollenCheckWorker>(
            1, TimeUnit.HOURS,
            15, TimeUnit.MINUTES
        )
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            PollenCheckWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }
}
