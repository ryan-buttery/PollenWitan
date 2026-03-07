package com.ryan.pollenwitan.domain.model

data class AppLocation(
    val latitude: Double,
    val longitude: Double,
    val displayName: String
)

enum class LocationMode {
    Manual,
    Gps
}
