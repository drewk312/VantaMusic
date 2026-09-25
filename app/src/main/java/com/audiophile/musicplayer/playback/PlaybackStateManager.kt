package com.audiophile.musicplayer.playback

import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.session.MediaSession
import com.audiophile.musicplayer.common.VantaLogger
import com.audiophile.musicplayer.data.display.VantaQualityInfo
import com.audiophile.musicplayer.data.local.entities.UnifiedTrackWithSources
import com.audiophile.musicplayer.data.source.SearchItemStatus
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

    fun applyMeasuredQuality(
        bitrateKbps: Int?,
        mime: String?,
        sampleRateHz: Int?,
        decoderClaimsAtmos: Boolean = false,
        bitDepth: Int? = null,
        channels: Int? = null,
        codec: String? = null,
        container: String? = null,
        pcmEncoding: String? = null,
        transcodingOccurred: Boolean = false
    ) {
        val previous = currentQualityInfo ?: return
        if (
            bitrateKbps == null &&
            mime.isNullOrBlank() &&
            sampleRateHz == null &&
            bitDepth == null &&
            channels == null &&
            !decoderClaimsAtmos
        ) return
        val sourceBitDepth = previous.bitDepth
        val crushedTo16 = sourceBitDepth != null && sourceBitDepth > 16 && bitDepth == 16
        val updated = VantaQualityInfo.fromSource(
            bitrate = bitrateKbps ?: previous.bitrateKbps,
            quality = previous.label,
            mime = mime ?: previous.mimeType,
            format = codec ?: previous.format,
            sampleRateHz = sampleRateHz ?: previous.sampleRateHz,
            bitDepth = previous.bitDepth ?: bitDepth,
            isLossless = previous.isLossless,
            isHiRes = previous.isHiRes,
            isSpatialAudio = decoderClaimsAtmos,
            isDolbyAtmos = decoderClaimsAtmos,
            isSurround = decoderClaimsAtmos,
            status = SearchItemStatus.VALIDATED_PLAYABLE,
            isValidated = true,
            sourceProviderId = previous.sourceProviderId,
            reason = "decoder_measured",
            bitrateIsMeasured = bitrateKbps != null || previous.measured,
            channels = channels ?: previous.channels,
            container = container ?: previous.container,
            pcmEncoding = pcmEncoding ?: previous.pcmEncoding,
            transcodingOccurred = transcodingOccurred || crushedTo16 || previous.transcodingOccurred
        )
        // Decoder details can change without changing the compact label or
        // bitrate (channels, PCM encoding, Atmos verification, or detected
        // down-conversion). Publish those changes to the details sheet too.
        if (updated == previous) return
        currentQualityInfo = updated
        scope.launch(Dispatchers.IO) {
            val state = playbackStateHolder.snapshot().copy(qualityInfo = updated)
            nowPlayingStateStore.save(state)
            playbackStateHolder.replace(state)
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
                    isBuffering = playbackState == Player.STATE_BUFFERING
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
