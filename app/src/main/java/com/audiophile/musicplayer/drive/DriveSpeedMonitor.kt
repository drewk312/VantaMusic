package com.audiophile.musicplayer.drive

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Looper
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class DriveSpeedMonitor(private val context: Context) {
    private val _speedMph = MutableStateFlow<Float?>(null)
    val speedMph: StateFlow<Float?> = _speedMph.asStateFlow()

    private var locationManager: LocationManager? = null
    private val listener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            _speedMph.value = (location.speed.coerceAtLeast(0f) * 2.23694f)
        }

        @Deprecated("Deprecated in Java")
        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit

        override fun onProviderEnabled(provider: String) = Unit

        override fun onProviderDisabled(provider: String) {
            _speedMph.value = null
        }
    }

    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    fun start() {
        if (!hasPermission()) return
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        locationManager = lm
        try {
            lm.requestLocationUpdates(
                LocationManager.GPS_PROVIDER,
                1000L,
                0f,
                listener,
                Looper.getMainLooper()
            )
        } catch (_: SecurityException) {
            _speedMph.value = null
        }
    }

    fun stop() {
        locationManager?.removeUpdates(listener)
        locationManager = null
    }
}
