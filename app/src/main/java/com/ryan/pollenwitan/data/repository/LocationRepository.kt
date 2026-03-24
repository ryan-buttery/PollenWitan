package com.ryan.pollenwitan.data.repository

import android.content.Context
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.ryan.pollenwitan.domain.model.AppLocation
import com.ryan.pollenwitan.domain.model.LocationMode
import com.ryan.pollenwitan.util.DefaultLocation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.locationDataStore by preferencesDataStore(name = "location_settings")

class LocationRepository(
    private val context: Context
) {

    private val dataStore get() = context.locationDataStore

    private object Keys {
        val MODE = stringPreferencesKey("location_mode")
        val MANUAL_LAT = doublePreferencesKey("manual_latitude")
        val MANUAL_LON = doublePreferencesKey("manual_longitude")
        val MANUAL_NAME = stringPreferencesKey("manual_display_name")
        val GPS_LAT = doublePreferencesKey("gps_latitude")
        val GPS_LON = doublePreferencesKey("gps_longitude")
        val GPS_NAME = stringPreferencesKey("gps_display_name")
    }

    fun getLocation(): Flow<AppLocation> = dataStore.data.map { prefs ->
        val mode = LocationMode.valueOf(prefs[Keys.MODE] ?: LocationMode.Manual.name)
        when (mode) {
            LocationMode.Manual -> AppLocation(
                latitude = prefs[Keys.MANUAL_LAT] ?: DefaultLocation.LATITUDE,
                longitude = prefs[Keys.MANUAL_LON] ?: DefaultLocation.LONGITUDE,
                displayName = prefs[Keys.MANUAL_NAME] ?: DefaultLocation.DISPLAY_NAME
            )
            LocationMode.Gps -> {
                val lat = prefs[Keys.GPS_LAT]
                val lon = prefs[Keys.GPS_LON]
                if (lat != null && lon != null) {
                    AppLocation(
                        latitude = lat,
                        longitude = lon,
                        displayName = prefs[Keys.GPS_NAME] ?: "GPS Location"
                    )
                } else {
                    // Fall back to manual if no GPS fix yet
                    AppLocation(
                        latitude = prefs[Keys.MANUAL_LAT] ?: DefaultLocation.LATITUDE,
                        longitude = prefs[Keys.MANUAL_LON] ?: DefaultLocation.LONGITUDE,
                        displayName = prefs[Keys.MANUAL_NAME] ?: DefaultLocation.DISPLAY_NAME
                    )
                }
            }
        }
    }

    fun getLocationMode(): Flow<LocationMode> = dataStore.data.map { prefs ->
        LocationMode.valueOf(prefs[Keys.MODE] ?: LocationMode.Manual.name)
    }

    suspend fun setManualLocation(latitude: Double, longitude: Double, displayName: String) {
        dataStore.edit { prefs ->
            prefs[Keys.MANUAL_LAT] = latitude
            prefs[Keys.MANUAL_LON] = longitude
            prefs[Keys.MANUAL_NAME] = displayName
        }
    }

    suspend fun setLocationMode(mode: LocationMode) {
        dataStore.edit { prefs ->
            prefs[Keys.MODE] = mode.name
        }
    }

    suspend fun updateGpsLocation(latitude: Double, longitude: Double, displayName: String) {
        dataStore.edit { prefs ->
            prefs[Keys.GPS_LAT] = latitude
            prefs[Keys.GPS_LON] = longitude
            prefs[Keys.GPS_NAME] = displayName
        }
    }
}
