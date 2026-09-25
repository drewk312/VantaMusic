package com.audiophile.musicplayer.playback

import android.content.Context
import android.media.AudioManager
import android.os.Build
import android.util.Log

/**
 * Head-tracking preference for spatial headphones.
 *
 * The platform exposes NO public API to switch head tracking on/off (Spatializer only
 * reports availability on API 33+); the app's lever is the spatialization behavior it
 * asks the platform to use for each stream. When the user opts out, VANTA requests plain
 * stereo (NEVER) for software-spatial content so the platform never renders spatial audio /
 * head rotation. Hardware JOC Atmos passthrough is left untouched.
 */
object SpatialHeadTracking {
    private const val PREFS_NAME = "vanta_spatial"
    private const val KEY_ENABLED = "head_tracking_enabled"

    const val LOG_TAG = "VANTA_HEAD_TRACK"

    @Volatile
    private var enabled = true

    fun load(context: Context) {
        enabled = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_ENABLED, true)
        Log.d(LOG_TAG, "load enabled=$enabled sdk=${Build.VERSION.SDK_INT}")
    }

    fun isEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_ENABLED, true)

    /** API 33+ devices that expose a head tracker (Spatial Audio earbuds). */
    fun isHeadTrackerAvailable(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < 33) return false
        return runCatching {
            context.getSystemService(AudioManager::class.java)
                .spatializer.isHeadTrackerAvailable
        }.getOrDefault(false)
    }

    /** Whether the app should keep asking the platform for spatial processing. */
    fun spatializationEnabled(): Boolean = enabled

    fun setEnabled(context: Context, value: Boolean) {
        enabled = value
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_ENABLED, value)
            .apply()
        Log.d(LOG_TAG, "setEnabled=$value")
    }
}