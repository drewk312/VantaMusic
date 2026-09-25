package com.audiophile.musicplayer.playback

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

/**
 * Playback-output options that affect how the audio pipeline hands PCM to the
 * platform sink. Mirrors the Qobuz/Tidal convention of letting float output be
 * chosen explicitly rather than silently forced either way.
 */
class PlaybackOutputPreferences(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /**
     * When true the sink writes ENCODING_PCM_FLOAT frames to AudioTrack (on
     * platforms that support float tracks), avoiding a float→PCM16 conversion
     * for hi-res decode pipelines. Defaults to false to preserve the existing
     * deliberate PCM output path; opt-in via Settings → Audio.
     */
    fun floatOutputEnabled(): Boolean = prefs.getBoolean(KEY_FLOAT_OUTPUT, false)

    fun setFloatOutputEnabled(enabled: Boolean) {
        prefs.edit { putBoolean(KEY_FLOAT_OUTPUT, enabled) }
    }

    companion object {
        private const val PREFS_NAME = "vanta_output"
        private const val KEY_FLOAT_OUTPUT = "float_output_enabled"
    }
}