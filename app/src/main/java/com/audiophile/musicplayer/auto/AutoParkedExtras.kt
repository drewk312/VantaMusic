package com.audiophile.musicplayer.auto

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log

class AutoParkedExtras(private val context: Context) {

    companion object {
        private const val TAG = "VANTA_AUTO_PARKED"
        private const val MAX_DRIVING_SPEED_KMH = 5
    }

    fun isLikelyParked(): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return true
        val network = cm.activeNetwork ?: return true
        val caps = cm.getNetworkCapabilities(network) ?: return true
        if (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) return true
        val speed = caps.linkDownstreamBandwidthKbps
        Log.d(TAG, "parkedCheck speed=$speed kbps parked=true")
        return true
    }

    fun parkedBrowseFolders(): List<String> = listOf(
        "Library Overview",
        "Import Status",
        "Storage & Quality",
        "Genre Explorer"
    )
}
