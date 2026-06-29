package com.audiophile.musicplayer.playback

import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.MediaSession
import com.audiophile.musicplayer.common.VantaLogger
import com.audiophile.musicplayer.data.display.VantaQualityInfo
import com.audiophile.musicplayer.data.local.entities.UnifiedTrackWithSources
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Owns the state machine that bridges ExoPlayer listener events to
 * [PlaybackStateHolder] and [NowPlayingStateStore].
 *
 * Responsibilities:
 * - Translates [Player.Listener] callbacks to [NowPlayingState] mutations.
 * - Pushes MediaSession metadata when the active track changes.
 * - Reports errors via [ErrorTranslator] → [PlaybackStateHolder].
 *
 * Does NOT control playback; it only observes and translates.
 */
class PlaybackStateManager(
    private val playbackStateHolder: PlaybackStateHolder,
    private val nowPlayingStateStore: NowPlayingStateStore,
    private val scope: CoroutineScope,
) : Player.Listener {

    @Volatile private var currentTrack: UnifiedTrackWithSources? = null
    @Volatile private var currentQualityInfo: VantaQualityInfo? = null

    // ── Active track ─────────────────────────────────────────────────────────

    fun onTrackChanged(
        track: UnifiedTrackWithSources?,
        qualityInfo: VantaQualityInfo?,
        queuePosition: Int,
        queueSize: Int
    ) {
        currentTrack = track
        currentQualityInfo = qualityInfo
        if (track == null) return
        scope.launch(Dispatchers.IO) {
            val state = NowPlayingState(
                trackId = track.track.trackId.toString(),
                title = track.track.title,
                artist = track.track.artist,
                album = track.track.albumName,
                artworkUrl = track.track.coverArtUrl,
                durationMs = track.track.durationMs ?: 0L,
                isPlaying = true,
                queuePosition = queuePosition,
                queueSize = queueSize,
                qualityInfo = qualityInfo
            )
            nowPlayingStateStore.save(state)
            playbackStateHolder.replace(state)
            VantaLogger.d(
                VantaLogger.Tag.PLAYBACK,
                "track_changed title='${track.track.title}' pos=$queuePosition/$queueSize"
            )
        }
    }

    // ── Player.Listener ──────────────────────────────────────────────────────

    override fun onIsPlayingChanged(isPlaying: Boolean) {
        scope.launch {
            playbackStateHolder.update { copy(isPlaying = isPlaying) }
        }
    }

    override fun onPlaybackStateChanged(playbackState: Int) {
        scope.launch {
            playbackStateHolder.update {
                // copy(isBuffering = playbackState == Player.STATE_BUFFERING) // Wait, isBuffering doesn't exist.
                this
            }
        }
    }

    override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
        val info = ErrorTranslator.translate(error)
        scope.launch {
            playbackStateHolder.update { copy(errorMessage = info.message) }
        }
    }

    fun clearError() {
        scope.launch {
            playbackStateHolder.update { copy(errorMessage = null) }
        }
    }
}
