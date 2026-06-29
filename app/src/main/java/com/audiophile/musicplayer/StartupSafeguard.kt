package com.audiophile.musicplayer

import android.content.Context
import android.util.Log
import com.audiophile.musicplayer.debug.VantaDiagnosticLog

/**
 * Detects repeated startup crashes and clears volatile playback prefs so a bad
 * queue/now-playing snapshot cannot brick the app across launches.
 */
object StartupSafeguard {
    private const val TAG = "StartupSafeguard"
    private const val PREFS = "startup_safeguard"
    private const val KEY_IN_STARTUP = "in_startup"
    private const val KEY_CRASH_COUNT = "crash_count"

    fun onApplicationCreate(context: Context) {
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (prefs.getBoolean(KEY_IN_STARTUP, false)) {
            val crashCount = prefs.getInt(KEY_CRASH_COUNT, 0) + 1
            prefs.edit().putInt(KEY_CRASH_COUNT, crashCount).apply()
            Log.w(TAG, "Previous startup did not finish cleanly (count=$crashCount)")
            VantaDiagnosticLog.warn(
                tag = "StartupSafeguard",
                message = "previous_startup_incomplete crashCount=$crashCount"
            )
            if (crashCount >= 2) {
                recoverVolatileState(context.applicationContext)
                VantaDiagnosticLog.warn(
                    tag = "StartupSafeguard",
                    message = "recovered_volatile_playback_state after $crashCount incomplete startups"
                )
                prefs.edit().putInt(KEY_CRASH_COUNT, 0).apply()
            }
        }
        prefs.edit().putBoolean(KEY_IN_STARTUP, true).apply()
    }

    fun onMainUiReady(context: Context) {
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_IN_STARTUP, false)
            .putInt(KEY_CRASH_COUNT, 0)
            .apply()
    }

    fun recoverVolatileState(context: Context) {
        Log.w(TAG, "Clearing volatile playback prefs after repeated startup failures")
        context.getSharedPreferences("playback_state", Context.MODE_PRIVATE).edit().clear().apply()
        context.getSharedPreferences("now_playing_state", Context.MODE_PRIVATE).edit().clear().apply()
    }
}
