package com.ryan.pollenwitan.data.location

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Looper
import com.ryan.pollenwitan.domain.model.AppLocation
import java.util.Locale
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

class GpsLocationProvider(private val context: Context) {

    private val locationManager: LocationManager
        get() = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager

    @SuppressLint("MissingPermission")
    fun getLastKnownLocation(): AppLocation? {
        val lm = locationManager
        val location = lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
            ?: lm.getLastKnownLocation(LocationManager.GPS_PROVIDER)
        return location?.toAppLocation()
    }

    @SuppressLint("MissingPermission")
    suspend fun requestSingleUpdate(): AppLocation? {
        val lm = locationManager

        // Try network provider first (faster), fall back to GPS
        val providers = buildList {
            if (lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) add(LocationManager.NETWORK_PROVIDER)
            if (lm.isProviderEnabled(LocationManager.GPS_PROVIDER)) add(LocationManager.GPS_PROVIDER)
        }

        if (providers.isEmpty()) return getLastKnownLocation()

        return withTimeoutOrNull(10_000L) {
            suspendCancellableCoroutine { cont ->
                var resumed = false
                val listeners = mutableListOf<Pair<String, LocationListener>>()

                for (provider in providers) {
                    val listener = object : LocationListener {
                        override fun onLocationChanged(location: Location) {
                            if (!resumed) {
                                resumed = true
                                listeners.forEach { (_, l) -> lm.removeUpdates(l) }
                                cont.resume(location.toAppLocation())
                            }
                        }

                        @Deprecated("Required for older API levels")
                        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
                        override fun onProviderEnabled(provider: String) {}
                        override fun onProviderDisabled(provider: String) {}
                    }
                    listeners.add(provider to listener)
                    lm.requestSingleUpdate(provider, listener, Looper.getMainLooper())
                }

                cont.invokeOnCancellation {
                    listeners.forEach { (_, l) -> lm.removeUpdates(l) }
                }
            }
        } ?: getLastKnownLocation()
    }

    private fun Location.toAppLocation() = AppLocation(
        latitude = latitude,
        longitude = longitude,
        displayName = "%.4f, %.4f".format(Locale.ROOT, latitude, longitude)
    )
}
