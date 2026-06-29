package com.audiophile.musicplayer.playback

import android.content.Context
import android.util.Log
import com.audiophile.musicplayer.data.local.entities.UnifiedTrackWithSources
import com.google.gson.Gson
import androidx.core.content.edit

/**
 * Simple JSON persistence for queue + now-playing state.
 *
 * This keeps mini-player context (track, queue, position) across app restarts.
 */
class SharedPreferencesQueuePersistence(
    context: Context,
    private val prefsName: String = "playback_state",
    private val key: String = "queue_snapshot",
    private val gson: Gson = Gson()
) : QueuePersistence {

    private val prefs = context.getSharedPreferences(prefsName, Context.MODE_PRIVATE)

    override fun save(snapshot: QueueSnapshot) {
        prefs.edit {
                putString(key, gson.toJson(snapshot))
            }
    }

    override fun load(): QueueSnapshot? {
        val raw = prefs.getString(key, null) ?: return null
        val snapshot = runCatching { gson.fromJson(raw, QueueSnapshot::class.java) }.getOrNull()
        if (snapshot == null || !isValidSnapshot(snapshot)) {
            Log.w("QueuePersistence", "Discarding corrupt queue snapshot")
            clear()
            return null
        }
        return snapshot
    }

    fun clear() {
        prefs.edit {
                remove(key)
            }
    }

    private fun isValidSnapshot(snapshot: QueueSnapshot): Boolean {
        fun validTrack(track: UnifiedTrackWithSources): Boolean = try {
            track.track.trackId > 0L
        } catch (_: Exception) {
            false
        }
        return snapshot.originalQueue.all(::validTrack) &&
            snapshot.priorityQueue.all(::validTrack) &&
            snapshot.upNextQueue.all(::validTrack) &&
            (snapshot.lastPlayedTrack?.let { validTrack(it) } ?: true) &&
            (snapshot.currentTrack?.let { validTrack(it) } ?: true)
    }
}