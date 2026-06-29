package com.audiophile.musicplayer.data.lyrics

import android.content.Context
import androidx.core.content.edit

/** Per-track manual lyrics timing offset (positive = lyrics lead audio). */
class LyricsSyncPreferences(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("vanta_lyrics_sync", Context.MODE_PRIVATE)

    fun getOffsetMs(trackKey: String): Long = prefs.getLong(safeKey(trackKey), 0L)

    fun setOffsetMs(trackKey: String, offsetMs: Long) {
        prefs.edit {
                putLong(safeKey(trackKey), offsetMs.coerceIn(-15_000L, 15_000L))
            }
    }

    private fun safeKey(trackKey: String): String =
        "offset_${trackKey.replace(Regex("""[^\w.-]"""), "_").take(180)}"
}