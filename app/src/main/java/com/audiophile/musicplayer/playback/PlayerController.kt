@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package com.audiophile.musicplayer.playback

import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.audiophile.musicplayer.common.AcceptanceTruth
import com.audiophile.musicplayer.data.local.entities.UnifiedTrackWithSources
import com.audiophile.musicplayer.data.source.canEnterPlaybackFlow
import com.audiophile.musicplayer.data.source.ResolvedStream
import com.audiophile.musicplayer.data.source.sourceValidityStatus
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class PlayerController(
    private val context: Context,
    private val queueManager: QueueManager,
    private val playbackState: PlaybackStateHolder
) {
    companion object {
        private const val POSITION_POLL_INTERVAL_MS = 100L
    }

    private val appContext = context.applicationContext

    private val timelineRefreshHandler = Handler(Looper.getMainLooper())
    private val timelineRefreshRunnable = Runnable {
        appContext.startService(
            PlaybackCommandAuth.createIntent(appContext, PlaybackService.ACTION_REFRESH_QUEUE_TIMELINE)
        )
    }

    private val _trackTransition = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val trackTransition: SharedFlow<Unit> = _trackTransition.asSharedFlow()

    private var mediaController: MediaController? = null
    private var controllerFuture: com.google.common.util.concurrent.ListenableFuture<MediaController>? = null
    private var playerListener: androidx.media3.common.Player.Listener? = null

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val playbackMutex = Mutex()
    private var pollingJob: Job? = null

    // While a seek is in flight the real player briefly reports the old position;
    // pin the UI to the seek target until the player lands (or the window expires).
    @Volatile private var pendingSeekTargetMs: Long = -1L
    @Volatile private var pendingSeekDeadlineMs: Long = 0L

    init {
        val sessionToken = SessionToken(appContext, android.content.ComponentName(appContext, PlaybackService::class.java))
        val future = MediaController.Builder(appContext, sessionToken).buildAsync()
        controllerFuture = future
        future.addListener({
            try {
                val mc = future.get()
                mediaController = mc
                val listener = object : androidx.media3.common.Player.Listener {
                    override fun onIsPlayingChanged(isPlaying: Boolean) {
                        Log.d("VANTA_PLAYER_STATE", "MEDIA_TRANSITION isPlaying=$isPlaying")
                        val currentMediaTrackId = mediaItemTrackId(mc.currentMediaItem?.mediaId)
                        val current = playbackState.snapshot()
                        if (current.trackId != null && currentMediaTrackId != null && current.trackId != currentMediaTrackId) {
                            Log.d(
                                "VANTA_PLAYBACK",
                                "skip stale isPlaying mediaId=${mc.currentMediaItem?.mediaId} liveTrackId=${current.trackId}"
                            )
                            updatePosition()
                            return
                        }
                        playbackState.update {
                            copy(
                                isPlaying = isPlaying,
                                isBuffering = if (isPlaying) false else isBuffering
                            )
                        }
                        updatePosition()
                        if (isPlaying) startPolling() else stopPolling()
                    }
                    override fun onVideoSizeChanged(videoSize: androidx.media3.common.VideoSize) {
                        val hasVideo = videoSize.width > 0 && videoSize.height > 0
                        playbackState.update { copy(hasVideo = hasVideo) }
                        Log.d("VANTA_TV_VIDEO", "videoSize=${videoSize.width}x${videoSize.height} hasVideo=$hasVideo")
                    }
                    override fun onTracksChanged(tracks: androidx.media3.common.Tracks) {
                        val hasVideo = tracks.groups.any { group ->
                            group.mediaTrackGroup.length > 0 &&
                                (0 until group.mediaTrackGroup.length).any { i ->
                                    val format = group.mediaTrackGroup.getFormat(i)
                                    format.sampleMimeType?.startsWith("video/") == true
                                }
                        }
                        playbackState.update { copy(hasVideo = hasVideo) }
                    }
                    override fun onPlaybackStateChanged(state: Int) {
                        val stateLabel = when (state) {
                            androidx.media3.common.Player.STATE_IDLE -> "IDLE"
                            androidx.media3.common.Player.STATE_BUFFERING -> "BUFFERING"
                            androidx.media3.common.Player.STATE_READY -> "READY"
                            androidx.media3.common.Player.STATE_ENDED -> "ENDED"
                            else -> "UNKNOWN"
                        }
                        Log.d("VANTA_PLAYER_STATE", "PLAYBACK_STATE state=$stateLabel")
                        playbackState.update {
                            copy(
                                isPlaying = mc.isPlaying,
                                isBuffering = state == androidx.media3.common.Player.STATE_BUFFERING
                            )
                        }
                        updatePosition()
                    }
                    override fun onPositionDiscontinuity(
                        oldPosition: androidx.media3.common.Player.PositionInfo,
                        newPosition: androidx.media3.common.Player.PositionInfo,
                        reason: Int
                    ) {
                        Log.d("VANTA_PLAYER_STATE", "MEDIA_TRANSITION old=${oldPosition.mediaItem?.mediaId} new=${newPosition.mediaItem?.mediaId} reason=$reason")
                        updatePosition()
                        _trackTransition.tryEmit(Unit)
                    }
                }
                playerListener = listener
                mc.addListener(listener)

                playbackState.update {
                    copy(
                        isPlaying = mc.isPlaying,
                        isBuffering = mc.playbackState == androidx.media3.common.Player.STATE_BUFFERING
                    )
                }
                updatePosition()
                if (mc.isPlaying) startPolling()
            } catch (e: Exception) {
                Log.e("VANTA_PLAYER", "MediaSession connection rejected (service not ready yet), will retry on play action", e)
                mediaController = null
            }
        }, ContextCompat.getMainExecutor(appContext))
    }

    private fun startPolling() {
        pollingJob?.cancel()
        pollingJob = scope.launch {
            Log.d("VANTA_PLAYBACK", "position loop started (${POSITION_POLL_INTERVAL_MS}ms interval)")
            while (isActive) {
                updatePosition()
                delay(POSITION_POLL_INTERVAL_MS)
            }
        }
    }

    private fun stopPolling() {
        Log.d("VANTA_PLAYBACK", "position loop stopped")
        pollingJob?.cancel()
        pollingJob = null
    }

    private fun updatePosition() {
        val mc = mediaController ?: return
        val newPosition = mc.currentPosition.coerceAtLeast(0L)
        val current = playbackState.snapshot()
        val currentMediaTrackId = mediaItemTrackId(mc.currentMediaItem?.mediaId)

        // Only compare when live trackId is a Room id. Optimistic UI may briefly
        // hold null (or historically an external id); don't starve the progress bar.
        val liveDbTrackId = current.trackId?.toLongOrNull()?.toString()
        if (liveDbTrackId != null && currentMediaTrackId != null && liveDbTrackId != currentMediaTrackId) {
            Log.d(
                "VANTA_PLAYBACK",
                "skip stale position mediaId=${mc.currentMediaItem?.mediaId} liveTrackId=${current.trackId}"
            )
            return
        }
        
        val isTransientZeroDuringBuffering =
            newPosition == 0L &&
            current.positionMs > 1000L &&
            mc.playbackState == androidx.media3.common.Player.STATE_BUFFERING

        if (isTransientZeroDuringBuffering) return

        val pendingSeek = pendingSeekTargetMs
        if (pendingSeek >= 0L) {
            if (System.currentTimeMillis() > pendingSeekDeadlineMs ||
                kotlin.math.abs(newPosition - pendingSeek) <= 1_500L
            ) {
                pendingSeekTargetMs = -1L
            } else {
                // Player hasn't landed on the seek target yet — keep UI pinned.
                return
            }
        }

        val buffered = mc.bufferedPosition.coerceAtLeast(0L)
        val duration = mc.duration.coerceAtLeast(0L)
        val bufferedPct = if (duration > 0) (buffered * 100 / duration).toInt() else 0
        playbackState.update {
            copy(
                positionMs = newPosition,
                durationMs = duration,
                bufferedMs = buffered
            )
        }
        AcceptanceTruth.position(
            trackId = current.trackId ?: currentMediaTrackId,
            positionMs = newPosition,
            durationMs = duration,
            isPlaying = mc.isPlaying,
            reason = "poll"
        )
        // Log stability metrics only when buffering or significant change
        if (mc.playbackState == androidx.media3.common.Player.STATE_BUFFERING) {
            Log.d("VANTA_BUFFERING", "poll pos=$newPosition buffered=$buffered bufferedPct=$bufferedPct% duration=$duration")
        }
    }

    private fun mediaItemTrackId(mediaId: String?): String? {
        val candidate = mediaId
            ?.substringBefore(":")
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: return null
        return candidate.takeIf { it.toLongOrNull() != null }
    }

    fun play() { sendAction(PlaybackService.ACTION_PLAY) }
    fun pause() { sendAction(PlaybackService.ACTION_PAUSE) }
    fun resume() { sendAction(PlaybackService.ACTION_RESUME) }
    fun togglePlayPause() { sendAction(PlaybackService.ACTION_TOGGLE_PLAY_PAUSE) }
    fun next() { sendAction(PlaybackService.ACTION_PLAY_NEXT_FROM_QUEUE) }
    fun previous() { sendAction(PlaybackService.ACTION_PLAY_PREVIOUS_FROM_QUEUE) }
    fun stop() { sendAction(PlaybackService.ACTION_STOP) }

    fun playDirectUrl(url: String, title: String, artist: String = "Live Radio") {
        if (!PlaybackUrlPolicy.isAllowedRemoteStreamUrl(url)) {
            Log.w("VANTA_STREAM_SECURITY", "Rejected non-public or non-HTTPS direct stream")
            playbackState.update {
                copy(isPlaying = false, isBuffering = false, errorMessage = "This stream is not available over a secure connection.")
            }
            return
        }
        appContext.startService(
            PlaybackCommandAuth.createIntent(appContext, PlaybackService.ACTION_PLAY_DIRECT_URL)
                .putExtra(PlaybackService.EXTRA_STREAM_URL, url)
                .putExtra(PlaybackService.EXTRA_TITLE, title)
                .putExtra(PlaybackService.EXTRA_ARTIST, artist)
        )
    }

    private var isTimelineRefreshing = false

    fun refreshQueueTimeline() {
        if (isTimelineRefreshing) return
        isTimelineRefreshing = true
        try {
            timelineRefreshHandler.removeCallbacks(timelineRefreshRunnable)
            timelineRefreshHandler.postDelayed(timelineRefreshRunnable, 300L)
        } finally {
            isTimelineRefreshing = false
        }
    }

    fun refreshQueueTimelineIfChanged() {
        val snapshot = queueManager.snapshot()
        val signature = snapshot.originalQueue.size.toLong() * 31L +
            snapshot.currentOriginalIndex * 17L +
            (snapshot.currentTrack?.track?.trackId ?: 0L)
        if (signature != lastControllerTimelineSig) {
            lastControllerTimelineSig = signature
            refreshQueueTimeline()
        }
    }

    private var lastControllerTimelineSig: Long = 0L

    fun clearDjSession() {
        scope.launch {
            playbackMutex.withLock {
                queueManager.clearOriginalQueue()
                stop()
            }
        }
    }

    fun skipLiveRadioAd() {
        appContext.startService(
            PlaybackCommandAuth.createIntent(appContext, PlaybackService.ACTION_SKIP_LIVE_AD)
        )
    }

    fun seekTo(positionMs: Long) {
        val clamped = positionMs.coerceAtLeast(0L)
        val mc = mediaController
        val seekableController = mc?.takeIf {
            it.isCommandAvailable(androidx.media3.common.Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM)
        }
        android.util.Log.d(
            "VANTA_SEEK_CHAIN",
            "PlayerController.seekTo clamped=$clamped mc=${mc != null} canSeekViaController=${seekableController != null}"
        )
        pendingSeekTargetMs = clamped
        pendingSeekDeadlineMs = System.currentTimeMillis() + 2_000L
        appContext.startService(
            PlaybackCommandAuth.createIntent(appContext, PlaybackService.ACTION_SEEK_TO)
                .putExtra(PlaybackService.EXTRA_POSITION_MS, clamped)
        )
        queueManager.markPlaybackPosition(clamped)
        playbackState.update { copy(positionMs = clamped) }
    }

    fun setVolume(volume: Float) {
        mediaController?.volume = volume.coerceIn(0f, 1f)
    }

    fun release() {
        scope.cancel()
        stopPolling()
        val mc = mediaController
        val listener = playerListener
        if (mc != null && listener != null) {
            mc.removeListener(listener)
        }
        playerListener = null
        mediaController = null
        controllerFuture?.let { MediaController.releaseFuture(it) }
        controllerFuture = null
    }

    fun isPlaying(): Boolean = mediaController?.isPlaying == true

    /** Media3 controller for attaching a [androidx.media3.ui.PlayerView] (TV video). */
    fun mediaControllerOrNull(): MediaController? = mediaController

    /**
     * A restored now-playing card is only UI state. After a process restart the
     * MediaController can be connected while its timeline is still empty, in
     * which case a plain play/pause command has nothing to resume.
     */
    fun hasCurrentMediaItem(): Boolean = mediaController?.currentMediaItem != null

    fun addToOriginalQueue(track: UnifiedTrackWithSources) {
        scope.launch { queueManager.addToOriginalQueue(track) }
    }

    suspend fun appendDjTracks(tracks: List<UnifiedTrackWithSources>): Int {
        return queueManager.appendToOriginalQueueIfAbsent(tracks)
    }

    suspend fun removeUpcomingDjTracks(predicate: (UnifiedTrackWithSources) -> Boolean): Int {
        return queueManager.removeUpcomingTracksMatching(predicate)
    }

    fun upcomingDjQueue(): List<UnifiedTrackWithSources> =
        queueManager.upcomingOriginalQueue()

    fun playTrack(track: UnifiedTrackWithSources) {
        Log.d("VANTA_PLAYBACK_TRACE", "step='controller_send_intent' trackId=${track.track.trackId} title='${track.track.title}' sources=${track.sources.size}")
        Log.d("VANTA_PLAY_TRACK_REQUEST", "Sending ACTION_PLAY_TRACK for trackId=${track.track.trackId} title='${track.track.title}' sources=${track.sources.size}")
        track.sources.forEachIndexed { i, s ->
            Log.d("VANTA_PLAY_TRACK_REQUEST", "  source[$i]: id=${s.sourceId} type=${s.sourceType} host=${com.audiophile.musicplayer.common.VantaLogger.urlHost(s.streamUrl)} bitrate=${s.bitrate}")
        }
        appContext.startService(
            PlaybackCommandAuth.createIntent(appContext, PlaybackService.ACTION_PLAY_TRACK)
                .putExtra(PlaybackService.EXTRA_TRACK_ID, track.track.trackId)
        )
    }

    fun isDjQueueActive(): Boolean = queueManager.queueMode == QueueMode.AI_DJ_QUEUE

    fun playQueue(
        tracks: List<UnifiedTrackWithSources>,
        startIndex: Int = 0,
        mode: QueueMode = QueueMode.NORMAL_QUEUE,
        resolvedStream: ResolvedStream? = null,
        preferredProviderId: String? = null,
        preferredExternalTrackId: String? = null,
        userQuery: String? = null,
        isFavorite: Boolean = false,
        canonicalTrackId: String? = null,
    ) {
        if (tracks.isEmpty()) {
            Log.w("VANTA_PLAY_QUEUE_REQUEST", "playQueue called with empty track list")
            return
        }

        scope.launch {
            playbackMutex.withLock {
                Log.d("VANTA_PLAY_QUEUE_REQUEST", "playQueue: ${tracks.size} tracks, startIndex=$startIndex mode=$mode")
                val requestedTrack = tracks.getOrNull(startIndex) ?: tracks.first()
                if (!requestedTrack.sourceValidityStatus().canEnterPlaybackFlow()) {
                    Log.d("VANTA_PLAYBACK_TRACE", "step='playback_blocked' reason='start_track_not_playable' trackId=${requestedTrack.track.trackId}")
                    Log.w("VANTA_PLAY_QUEUE_REQUEST", "Start track cannot enter playback flow")
                    return@withLock
                }
                val safeIndex = tracks.indexOfFirst { it.track.trackId == requestedTrack.track.trackId }.coerceAtLeast(0)
                val playableCount = tracks.count { it.sourceValidityStatus().canEnterPlaybackFlow() }
                val startTrack = tracks[safeIndex]
                val resolvedPreferredProvider = preferredProviderId
                    ?: resolvedStream?.providerId
                    ?: resolvedStream?.fulfillmentProviderId
                val resolvedPreferredExternal = preferredExternalTrackId
                    ?: startTrack.sources
                        .firstOrNull {
                            !it.externalProviderId.isNullOrBlank() &&
                                !it.externalTrackId.isNullOrBlank() &&
                                (
                                    resolvedPreferredProvider == null ||
                                        it.externalProviderId.equals(resolvedPreferredProvider, ignoreCase = true)
                                    )
                        }
                        ?.externalTrackId

                com.audiophile.musicplayer.debug.DebugSessionLogger.log(
                    hypothesisId = "H11",
                    location = "PlayerController.playQueue",
                    message = "queue_preserved",
                    data = mapOf(
                        "totalTracks" to tracks.size,
                        "playableTracks" to playableCount,
                        "startIndex" to safeIndex,
                        "mode" to mode.name
                    ),
                    runId = "mutex-fix-v1"
                )

                Log.d("VANTA_PLAYBACK_TRACE", "step='queue_selected' trackId=${startTrack.track.trackId} queueSize=${tracks.size} startIndex=$safeIndex generation=next")
                queueManager.setOriginalQueue(tracks, safeIndex, mode)
                queueManager.markCurrentTrack(startTrack)
                playbackState.replace(
                    NowPlayingState.pendingPlayback(
                        track = startTrack,
                        queuePosition = safeIndex,
                        queueSize = tracks.size,
                        preferredProviderId = resolvedPreferredProvider,
                        preferredExternalTrackId = resolvedPreferredExternal,
                        userQuery = userQuery,
                        isFavorite = isFavorite,
                        canonicalTrackId = canonicalTrackId
                    )
                )
                _trackTransition.tryEmit(Unit)
                Log.d(
                    "VANTA_NOWPLAYING_STATE",
                    "pendingPlayback trackId=${startTrack.track.trackId} title='${startTrack.track.title}' " +
                        "preferred=$resolvedPreferredProvider:$resolvedPreferredExternal positionMs=0 queueIndex=$safeIndex"
                )

                val playIntent = PlaybackCommandAuth.createIntent(appContext, PlaybackService.ACTION_PLAY_TRACK)
                    .putExtra(PlaybackService.EXTRA_TRACK_ID, tracks[safeIndex].track.trackId)
                resolvedStream?.let { stream ->
                    val headers = android.os.Bundle().apply {
                        stream.requestHeaders.forEach { (name, value) -> putString(name, value) }
                    }
                    val drmHeaders = android.os.Bundle().apply {
                        stream.drm?.licenseRequestHeaders?.forEach { (name, value) -> putString(name, value) }
                    }
                    playIntent
                        .putExtra(PlaybackService.EXTRA_RESOLVED_STREAM_URL, stream.streamUrl)
                        .putExtra(PlaybackService.EXTRA_RESOLVED_STREAM_BITRATE, stream.bitrateKbps)
                        .putExtra(PlaybackService.EXTRA_RESOLVED_STREAM_MIME, stream.mimeType)
                        .putExtra(PlaybackService.EXTRA_RESOLVED_STREAM_EXPIRES_AT, stream.expiresAt ?: -1L)
                        .putExtra(PlaybackService.EXTRA_RESOLVED_STREAM_QUALITY, stream.qualityLabel)
                        .putExtra(PlaybackService.EXTRA_RESOLVED_STREAM_FORMAT, stream.format)
                        .putExtra(PlaybackService.EXTRA_RESOLVED_STREAM_BIT_DEPTH, stream.bitDepth ?: -1)
                        .putExtra(PlaybackService.EXTRA_RESOLVED_STREAM_SAMPLE_RATE, stream.sampleRateHz ?: -1)
                        .putExtra(PlaybackService.EXTRA_RESOLVED_STREAM_CHANNELS, stream.channelCount ?: -1)
                        .putExtra(PlaybackService.EXTRA_RESOLVED_STREAM_CODEC, stream.codec)
                        .putExtra(PlaybackService.EXTRA_RESOLVED_STREAM_CONTAINER, stream.container)
                        .putExtra(PlaybackService.EXTRA_RESOLVED_STREAM_LOSSLESS, stream.isLossless)
                        .putExtra(PlaybackService.EXTRA_RESOLVED_STREAM_SOURCE_LABEL, stream.sourceLabel)
                        .putExtra(PlaybackService.EXTRA_RESOLVED_STREAM_ECLIPSA, stream.isEclipsaAudio)
                        .putExtra(PlaybackService.EXTRA_RESOLVED_STREAM_PROVIDER, stream.providerId)
                        .putExtra(PlaybackService.EXTRA_RESOLVED_STREAM_FULFILLED_BY, stream.fulfillmentProviderId)
                        .putExtra(PlaybackService.EXTRA_RESOLVED_STREAM_SPATIAL, stream.isSpatialAudio)
                        .putExtra(PlaybackService.EXTRA_RESOLVED_STREAM_ATMOS, stream.isDolbyAtmos)
                        .putExtra(PlaybackService.EXTRA_RESOLVED_STREAM_SURROUND, stream.isSurround)
                        .putExtra(PlaybackService.EXTRA_RESOLVED_STREAM_HEADERS, headers)
                        .putExtra(PlaybackService.EXTRA_RESOLVED_STREAM_DRM_SCHEME, stream.drm?.scheme)
                        .putExtra(PlaybackService.EXTRA_RESOLVED_STREAM_DRM_LICENSE_URL, stream.drm?.licenseUrl)
                        .putExtra(PlaybackService.EXTRA_RESOLVED_STREAM_DRM_FORCE_DEFAULT, stream.drm?.forceDefaultLicenseUri ?: true)
                        .putExtra(PlaybackService.EXTRA_RESOLVED_STREAM_DRM_HEADERS, drmHeaders)
                }
                appContext.startService(playIntent)
            }
        }
    }

    private fun sendAction(action: String) {
        appContext.startService(PlaybackCommandAuth.createIntent(appContext, action))
    }

    fun speakDjVoice(audioPath: String) {
        if (audioPath.isBlank()) return
        val intent = PlaybackCommandAuth.createIntent(appContext, PlaybackService.ACTION_SPEAK_DJ_VOICE)
        intent.putExtra(PlaybackService.EXTRA_DJ_VOICE_AUDIO_PATH, audioPath)
        appContext.startService(intent)
    }

    fun toggleShuffle() {
        scope.launch {
            val enabled = queueManager.toggleShuffle()
            mediaController?.shuffleModeEnabled = enabled
            refreshQueueTimeline()
        }
    }

    fun shuffleQueue() {
        scope.launch {
            queueManager.shuffleUpNext()
            mediaController?.shuffleModeEnabled = true
            refreshQueueTimeline()
        }
    }
}
