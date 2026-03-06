package com.ryan.pollenwitan

import android.app.Application
import com.ryan.pollenwitan.di.appModule
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin

class PollenWitanApp : Application() {
    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidLogger()
            androidContext(this@PollenWitanApp)
            modules(appModule)
        }
    }
}
