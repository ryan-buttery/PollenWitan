package com.ryan.pollenwitan.widget

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import com.ryan.pollenwitan.data.location.GpsLocationProvider
import com.ryan.pollenwitan.data.repository.LocationRepository
import com.ryan.pollenwitan.domain.model.LocationMode
import kotlinx.coroutines.flow.first

class WidgetRefreshAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        // Refresh GPS location if in GPS mode
        val locationRepository = LocationRepository(context)
        val mode = locationRepository.getLocationMode().first()
        if (mode == LocationMode.Gps) {
            val hasPermission = ContextCompat.checkSelfPermission(
                context, Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED

            if (hasPermission) {
                val location = GpsLocationProvider(context).requestSingleUpdate()
                if (location != null) {
                    locationRepository.updateGpsLocation(
                        location.latitude, location.longitude, location.displayName
                    )
                }
            }
        }

        PollenWidget().update(context, glanceId)
    }
}
