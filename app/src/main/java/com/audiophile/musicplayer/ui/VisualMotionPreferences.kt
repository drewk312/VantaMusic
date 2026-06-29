package com.audiophile.musicplayer.ui

import android.content.Context
import androidx.core.content.edit

object VisualMotionPreferences {
    private const val PREFS_NAME = "stream_mode_prefs"
    private const val KEY_ANIMATED_ARTWORK = "streamMode_animatedArtwork"

    fun isAnimatedArtworkEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_ANIMATED_ARTWORK, true)

    fun setAnimatedArtworkEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit {
                putBoolean(KEY_ANIMATED_ARTWORK, enabled)
            }
    }
}