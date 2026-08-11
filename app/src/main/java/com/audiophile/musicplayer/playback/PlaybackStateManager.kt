package com.audiophile.musicplayer.playback

import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
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
    private val onPlaybackErrorObserved: (PlaybackException) -> Unit = {},
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
            val previous = playbackStateHolder.snapshot()
            val state = NowPlayingState.fromTrackChange(
                track = track,
                qualityInfo = qualityInfo,
                queuePosition = queuePosition,
                queueSize = queueSize,
                previous = previous
            )
            nowPlayingStateStore.save(state)
            playbackStateHolder.replace(state)
            VantaLogger.d(
                VantaLogger.Tag.PLAYBACK,
                "track_changed title='${track.track.title}' pos=$queuePosition/$queueSize " +
                    "preferred=${state.preferredProviderId}:${state.preferredExternalTrackId} " +
                    "isrc=${state.isrc}"
            )
        }
    }

    // ── Player.Listener ──────────────────────────────────────────────────────

    override fun onIsPlayingChanged(isPlaying: Boolean) {
        scope.launch {
            playbackStateHolder.update {
                copy(
                    isPlaying = isPlaying,
                    isBuffering = if (isPlaying) false else isBuffering,
                    // Media3 can successfully resume a buffered item after a
                    // transient CDN failure. Never leave a red error card over
                    // audio that is already playing.
                    errorMessage = if (isPlaying) null else errorMessage
                )
            }
        }
    }

    override fun onPlaybackStateChanged(playbackState: Int) {
        scope.launch {
            playbackStateHolder.update {
                copy(
                    isPlaying = if (playbackState == Player.STATE_IDLE || playbackState == Player.STATE_ENDED) false else isPlaying,
                    isBuffering = playbackState == Player.STATE_BUFFERING,
                    errorMessage = if (playbackState == Player.STATE_READY) null else errorMessage
                )
            }
        }
    }

    override fun onPlayerError(error: PlaybackException) {
        val info = ErrorTranslator.translate(error)
        scope.launch {
            playbackStateHolder.update {
                copy(isPlaying = false, isBuffering = false, errorMessage = info.message)
            }
            onPlaybackErrorObserved(error)
        }
    }

    fun clearError() {
        scope.launch {
            playbackStateHolder.update { copy(errorMessage = null) }
        }
    }
}
