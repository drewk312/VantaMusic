package com.audiophile.musicplayer.discovery.personalized

import android.util.Log
import com.audiophile.musicplayer.data.local.entities.UnifiedTrackWithSources
import com.audiophile.musicplayer.playback.NowPlayingState
import com.audiophile.musicplayer.playback.NowPlayingStateStore
import com.audiophile.musicplayer.playback.PlayerController
import com.audiophile.musicplayer.playback.QueueManager
import com.audiophile.musicplayer.playback.QueueMode
import com.audiophile.musicplayer.radio.StreamingStationKind
import com.audiophile.musicplayer.radio.StreamingStationSeed
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

class PersonalizedMixPlayback(
    private val manager: PersonalizedMixManager,
    private val queueManager: QueueManager,
    private val playerController: PlayerController,
    private val nowPlayingStateStore: NowPlayingStateStore,
    private val scope: CoroutineScope,
    private val tasteSignals: () -> com.audiophile.musicplayer.radio.StreamingStationTasteSignals
) {
    companion object {
        private const val TAG = "PersonalizedMixPlayback"
        private const val MIN_BOOTSTRAP = 3
        private const val BOOTSTRAP_TARGET = 12
        private const val EXPAND_TARGET = 30
    }

    suspend fun playMix(kind: PersonalizedMixKind): PlayMixResult {
        var tracks = manager.loadPlayableTracks(kind)
        if (tracks.size < MIN_BOOTSTRAP) {
            manager.refreshMix(kind)
            tracks = manager.loadPlayableTracks(kind)
        }
        if (tracks.size < MIN_BOOTSTRAP) {
            Log.w(TAG, "play_failed kind=${kind.id} playable=${tracks.size}")
            return PlayMixResult.Failed("Not enough playable tracks for ${kind.id}")
        }

        val bootstrap = tracks.take(BOOTSTRAP_TARGET)
        val seed = streamingSeedFor(kind)
        queueManager.setStreamingStationSeed(seed)
        playerController.playQueue(bootstrap, 0, QueueMode.STREAMING_STATION)

        val first = bootstrap.first()
        nowPlayingStateStore.save(
            NowPlayingState(
                trackId = first.track.trackId.toString(),
                title = first.track.title,
                artist = first.track.artist,
                album = first.track.albumName,
                artworkUrl = first.track.coverArtUrl,
                durationMs = first.track.durationMs ?: 0L,
                isPlaying = true
            )
        )

        if (tracks.size > bootstrap.size) {
            val added = queueManager.appendToOriginalQueueIfAbsent(tracks.drop(bootstrap.size))
            Log.d(TAG, "immediate_expand kind=${kind.id} added=$added")
        } else {
            scope.launch {
                expandInBackground(kind, seed)
            }
        }

        return PlayMixResult.Started(bootstrap.size, kind)
    }

    private suspend fun expandInBackground(kind: PersonalizedMixKind, seed: StreamingStationSeed) {
        manager.refreshMix(kind)
        val expanded = manager.loadPlayableTracks(kind)
        if (expanded.isEmpty()) return
        val added = queueManager.appendToOriginalQueueIfAbsent(expanded)
        Log.d(TAG, "background_expand kind=${kind.id} added=$added taste=${tasteSignals().favoriteArtists.size}")
    }

    private fun streamingSeedFor(kind: PersonalizedMixKind): StreamingStationSeed =
        when (kind) {
            PersonalizedMixKind.DISCOVERY_WEEKLY -> StreamingStationSeed(
                id = "discovery_weekly",
                displayName = "Discovery Weekly",
                kind = StreamingStationKind.GENRE,
                hintKeywords = listOf("discovery", "weekly")
            )
            PersonalizedMixKind.RELEASE_RADAR -> StreamingStationSeed(
                id = "release_radar",
                displayName = "Release Radar",
                kind = StreamingStationKind.GENRE,
                hintKeywords = listOf("new", "releases")
            )
            else -> StreamingStationSeed(
                id = kind.id,
                displayName = kind.id,
                kind = StreamingStationKind.GENRE
            )
        }

    sealed class PlayMixResult {
        data class Started(val trackCount: Int, val kind: PersonalizedMixKind) : PlayMixResult()
        data class Failed(val reason: String) : PlayMixResult()
    }
}
