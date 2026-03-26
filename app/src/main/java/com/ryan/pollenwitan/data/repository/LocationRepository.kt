package com.ryan.pollenwitan.data.repository

import android.content.Context
import com.ryan.pollenwitan.data.security.EncryptedPrefsStore
import com.ryan.pollenwitan.data.security.getDouble
import com.ryan.pollenwitan.data.security.getDoubleOrNull
import com.ryan.pollenwitan.data.security.putDouble
import com.ryan.pollenwitan.domain.model.AppLocation
import com.ryan.pollenwitan.domain.model.LocationMode
import com.ryan.pollenwitan.util.DefaultLocation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class LocationRepository(
    context: Context
) {

    private val store = EncryptedPrefsStore(context, "location_encrypted")

    private object Keys {
        const val MODE = "location_mode"
        const val MANUAL_LAT = "manual_latitude"
        const val MANUAL_LON = "manual_longitude"
        const val MANUAL_NAME = "manual_display_name"
        const val GPS_LAT = "gps_latitude"
        const val GPS_LON = "gps_longitude"
        const val GPS_NAME = "gps_display_name"
    }

    fun getLocation(): Flow<AppLocation> = store.data.map { prefs ->
        val mode = LocationMode.valueOf(prefs.getString(Keys.MODE, null) ?: LocationMode.Manual.name)
        when (mode) {
            LocationMode.Manual -> AppLocation(
                latitude = prefs.getDouble(Keys.MANUAL_LAT, DefaultLocation.LATITUDE),
                longitude = prefs.getDouble(Keys.MANUAL_LON, DefaultLocation.LONGITUDE),
                displayName = prefs.getString(Keys.MANUAL_NAME, null) ?: DefaultLocation.DISPLAY_NAME
            )
            LocationMode.Gps -> {
                val lat = prefs.getDoubleOrNull(Keys.GPS_LAT)
                val lon = prefs.getDoubleOrNull(Keys.GPS_LON)
                if (lat != null && lon != null) {
                    AppLocation(
                        latitude = lat,
                        longitude = lon,
                        displayName = prefs.getString(Keys.GPS_NAME, null) ?: "GPS Location"
                    )
                } else {
                    AppLocation(
                        latitude = prefs.getDouble(Keys.MANUAL_LAT, DefaultLocation.LATITUDE),
                        longitude = prefs.getDouble(Keys.MANUAL_LON, DefaultLocation.LONGITUDE),
                        displayName = prefs.getString(Keys.MANUAL_NAME, null) ?: DefaultLocation.DISPLAY_NAME
                    )
                }
            }
        }
    }

    fun getLocationMode(): Flow<LocationMode> = store.data.map { prefs ->
        LocationMode.valueOf(prefs.getString(Keys.MODE, null) ?: LocationMode.Manual.name)
    }

    suspend fun setManualLocation(latitude: Double, longitude: Double, displayName: String) {
        store.edit {
            putDouble(Keys.MANUAL_LAT, latitude)
            putDouble(Keys.MANUAL_LON, longitude)
            putString(Keys.MANUAL_NAME, displayName)
        }
    }

    suspend fun setLocationMode(mode: LocationMode) {
        store.edit {
            putString(Keys.MODE, mode.name)
        }
    }

    suspend fun updateGpsLocation(latitude: Double, longitude: Double, displayName: String) {
        store.edit {
            putDouble(Keys.GPS_LAT, latitude)
            putDouble(Keys.GPS_LON, longitude)
            putString(Keys.GPS_NAME, displayName)
        }
    }
}
