package com.ryan.pollenwitan.ui.screens

sealed interface GpsStatus {
    data object Idle : GpsStatus
    data object Requesting : GpsStatus
    data class Success(val displayName: String) : GpsStatus
    data class Error(val message: String) : GpsStatus
}
