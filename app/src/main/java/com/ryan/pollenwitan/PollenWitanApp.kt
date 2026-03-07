package com.ryan.pollenwitan

import android.app.Application
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.ryan.pollenwitan.di.appModule
import com.ryan.pollenwitan.worker.NotificationHelper
import com.ryan.pollenwitan.worker.PollenCheckWorker
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin
import java.util.concurrent.TimeUnit

class PollenWitanApp : Application() {
    override fun onCreate() {
        super.onCreate()

        startKoin {
            androidLogger()
            androidContext(this@PollenWitanApp)
            modules(appModule)
        }

        NotificationHelper.createChannels(this)
        schedulePollenCheck()
    }

    private fun schedulePollenCheck() {
        val request = PeriodicWorkRequestBuilder<PollenCheckWorker>(
            1, TimeUnit.HOURS,
            15, TimeUnit.MINUTES
        ).build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            PollenCheckWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }
}
