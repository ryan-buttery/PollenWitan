package com.ryan.pollenwitan.di

import com.ryan.pollenwitan.data.remote.AirQualityApi
import com.ryan.pollenwitan.data.repository.AirQualityRepository
import com.ryan.pollenwitan.data.repository.AirQualityRepositoryImpl
import com.ryan.pollenwitan.ui.screens.DashboardViewModel
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import org.koin.core.module.dsl.viewModel
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

    single<AirQualityRepository> { AirQualityRepositoryImpl(get()) }

    viewModel { DashboardViewModel(get()) }
}
