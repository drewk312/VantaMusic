package com.audiophile.musicplayer.playback

import android.content.Context
import androidx.core.content.edit

enum class AutoMixMode(val label: String, val description: String) {
    OFF("Off", "Play each track through to the end"),
    CROSSFADE("Crossfade", "A consistent, gentle transition"),
    AUTOMIX("AutoMix", "A longer DJ-style transition shaped around the queue");

    fun next(): AutoMixMode = entries[(ordinal + 1) % entries.size]
}

data class AutoMixConfig(
    val mode: AutoMixMode = AutoMixMode.OFF,
    val transitionSeconds: Int = 7
)

class AutoMixPreferences(context: Context) {
    private val prefs = context.applicationContext
        .getSharedPreferences("vanta_auto_mix", Context.MODE_PRIVATE)

    fun load(): AutoMixConfig = AutoMixConfig(
        mode = runCatching {
            AutoMixMode.valueOf(prefs.getString("mode", AutoMixMode.OFF.name).orEmpty())
        }.getOrDefault(AutoMixMode.OFF),
        transitionSeconds = prefs.getInt("transition_seconds", 7).coerceIn(2, 12)
    )

    fun save(config: AutoMixConfig) {
        prefs.edit {
                putString("mode", config.mode.name)
                putInt("transition_seconds", config.transitionSeconds.coerceIn(2, 12))
            }
    }
}