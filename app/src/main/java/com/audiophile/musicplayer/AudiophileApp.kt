package com.audiophile.musicplayer

import android.app.Application
import android.util.Log
import com.audiophile.musicplayer.debug.VantaDiagnosticLog
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.launch

@HiltAndroidApp
class AudiophileApp : Application() {
    override fun onCreate() {
        super.onCreate()
        com.audiophile.musicplayer.debug.DebugSessionLogger.init(this)
        StartupSafeguard.onApplicationCreate(this)
        com.audiophile.musicplayer.playback.SpatialDecoderCapabilities.initialize(this)
        val spatialPreferences = getSharedPreferences("vanta_settings", MODE_PRIVATE)
        if (!spatialPreferences.getBoolean("spatial_first_v2", false)) {
            spatialPreferences.edit().putBoolean("spatial_first_v2", true)
                .putBoolean("spatial_auto_v1", true)
                .putString("stream_quality", "auto").apply()
        }
        // Restore user stream-quality preference before any source resolution happens.
        getSharedPreferences("vanta_settings", MODE_PRIVATE)
            .getString("stream_quality", null)
            ?.takeIf { it in setOf("16", "24", "atmos", "auto", "iamf", "360") }
            ?.let { com.audiophile.musicplayer.data.source.external.SpotiFlacEndpoints.PREFERRED_STREAM_QUALITY = it }
        Log.d("VANTA_BUILD", "tag='radio-gate-runtime-proof' versionCode=${BuildConfig.VERSION_CODE} timestamp='2026-06-14'")

        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main + kotlinx.coroutines.SupervisorJob()).launch {
            appContainer.playbackStateHolder.state.collect { state ->
                com.audiophile.musicplayer.widget.VantaWidgetUpdater.updateAllWidgets(this@AudiophileApp, state)
            }
        }
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
