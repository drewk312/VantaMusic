package com.audiophile.musicplayer

import android.app.Application
import android.util.Log
import com.audiophile.musicplayer.debug.VantaDiagnosticLog
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class AudiophileApp : Application() {
    override fun onCreate() {
        super.onCreate()
        com.audiophile.musicplayer.debug.DebugSessionLogger.init(this)
        StartupSafeguard.onApplicationCreate(this)
        // Restore user stream-quality preference before any source resolution happens.
        getSharedPreferences("vanta_settings", MODE_PRIVATE)
            .getString("stream_quality", null)
            ?.takeIf { it == "16" || it == "24" }
            ?.let { com.audiophile.musicplayer.data.source.external.SpotiFlacEndpoints.PREFERRED_STREAM_QUALITY = it }
        Log.d("VANTA_BUILD", "tag='radio-gate-runtime-proof' versionCode=${BuildConfig.VERSION_CODE} timestamp='2026-06-14'")
    }

    val appContainer: AppContainer by lazy {
        try {
            AppContainer(this)
        } catch (e: Exception) {
            Log.e("AudiophileApp", "AppContainer init failed, recovering volatile state", e)
            VantaDiagnosticLog.error("AppContainer", "init_failed", e)
            StartupSafeguard.recoverVolatileState(this)
            AppContainer(this)
        }
    }
}

val Application.appContainer: AppContainer
    get() = (this as AudiophileApp).appContainer
