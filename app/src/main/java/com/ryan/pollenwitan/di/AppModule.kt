package com.ryan.pollenwitan.di

import com.ryan.pollenwitan.data.local.AppDatabase
import com.ryan.pollenwitan.data.location.GpsLocationProvider
import com.ryan.pollenwitan.data.remote.AirQualityApi
import com.ryan.pollenwitan.data.repository.AirQualityRepository
import com.ryan.pollenwitan.data.repository.AirQualityRepositoryImpl
import com.ryan.pollenwitan.data.repository.LocationRepository
import com.ryan.pollenwitan.data.repository.LocationRepositoryImpl
import com.ryan.pollenwitan.data.repository.NotificationPrefsRepository
import com.ryan.pollenwitan.data.repository.NotificationPrefsRepositoryImpl
import com.ryan.pollenwitan.data.repository.ProfileRepository
import com.ryan.pollenwitan.data.repository.ProfileRepositoryImpl
import com.ryan.pollenwitan.ui.screens.DashboardViewModel
import com.ryan.pollenwitan.ui.screens.ForecastViewModel
import com.ryan.pollenwitan.ui.screens.SettingsViewModel
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.dsl.module

val appModule = module {
    single {
        HttpClient(CIO) {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true })
            }
        }
    }

    single { AirQualityApi(get()) }

    single { AppDatabase.create(get()) }

    single { get<AppDatabase>().cachedForecastDao() }

    single<AirQualityRepository> { AirQualityRepositoryImpl(get(), get()) }

    single<ProfileRepository> { ProfileRepositoryImpl(get()) }

    single<LocationRepository> { LocationRepositoryImpl(get()) }

    single<NotificationPrefsRepository> { NotificationPrefsRepositoryImpl(get()) }

    single { GpsLocationProvider(get()) }

    viewModel { DashboardViewModel(get(), get(), get()) }

    viewModel { ForecastViewModel(get(), get(), get()) }

    viewModel { SettingsViewModel(get(), get(), get()) }
}
