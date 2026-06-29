package com.audiophile.musicplayer.playback

import android.content.Context
import android.util.Log
import com.audiophile.musicplayer.data.display.VantaQualityInfo
import com.audiophile.musicplayer.data.source.SearchItemStatus
import com.google.gson.Gson

class NowPlayingStateStore(
    context: Context,
    private val prefsName: String = "now_playing_state",
    private val key: String = "snapshot",
    private val failedSourcesKey: String = "failed_source_ids",
    private val gson: Gson = Gson()
) {
    private val prefs = context.getSharedPreferences(prefsName, Context.MODE_PRIVATE)
    @Volatile private var lastSavedJson: String? = null
    @Volatile private var lastSaveTimeMs: Long = 0L
    private val minSaveIntervalMs = 5_000L // Throttle: max one SharedPreferences write per 5 seconds
    private val saveLock = Any()

    fun save(state: NowPlayingState) {
        val json = gson.toJson(state)
        // Throttle: skip if identical and within interval
        synchronized(saveLock) {
            if (json == lastSavedJson && (System.currentTimeMillis() - lastSaveTimeMs) < minSaveIntervalMs) {
                return
            }
            lastSavedJson = json
            lastSaveTimeMs = System.currentTimeMillis()
        }
        prefs.edit().putString(key, json).apply()
        Log.d("VANTA_PLAYBACK_STABILITY", "NowPlayingStateStore.save() persisted trackId=${state.trackId} pos=${state.positionMs} isPlaying=${state.isPlaying}")
    }

    fun load(): NowPlayingState? {
        val raw = prefs.getString(key, null) ?: return null
        val state = runCatching { gson.fromJson(raw, NowPlayingState::class.java) }.getOrNull()
        if (state == null || state.trackId.isNullOrBlank()) {
            if (raw.isNotBlank()) {
                Log.w("NowPlayingStateStore", "Discarding corrupt now-playing snapshot")
                clear()
            }
            return null
        }
        return state.copy(qualityInfo = sanitizeLoadedQualityInfo(state.qualityInfo))
    }

    fun clear() {
        lastSavedJson = null
        prefs.edit().remove(key).remove(failedSourcesKey).apply()
    }

    fun saveFailedSourceIds(sourceIds: Set<Long>) {
        prefs.edit().putString(failedSourcesKey, gson.toJson(sourceIds)).apply()
    }

    fun loadFailedSourceIds(): Set<Long> {
        val raw = prefs.getString(failedSourcesKey, null) ?: return emptySet()
        return runCatching {
            val arr = gson.fromJson(raw, Array<Long>::class.java)
            arr?.toSet() ?: emptySet()
        }.getOrDefault(emptySet())
    }

    private fun sanitizeLoadedQualityInfo(info: VantaQualityInfo?): VantaQualityInfo? {
        info ?: return null
        return VantaQualityInfo.fromSource(
            bitrate = info.bitrateKbps,
            quality = null,
            mime = null,
            format = info.format,
            sampleRateHz = info.sampleRateHz,
            bitDepth = info.bitDepth,
            status = if (info.isPreview) SearchItemStatus.PREVIEW else null,
            isValidated = info.isValidated,
            sourceProviderId = info.sourceProviderId,
            reason = info.reason
        ).takeIf { it.bestQualityLabel() != null }
    }
}
