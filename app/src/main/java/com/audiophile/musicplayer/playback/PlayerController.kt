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
import com.audiophile.musicplayer.data.local.entities.UnifiedTrackWithSources
import com.audiophile.musicplayer.data.source.canEnterPlaybackFlow
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
    private val appContext = context.applicationContext

    private val timelineRefreshHandler = Handler(Looper.getMainLooper())
    private val timelineRefreshRunnable = Runnable {
        appContext.startService(
            Intent(appContext, PlaybackService::class.java)
                .setAction(PlaybackService.ACTION_REFRESH_QUEUE_TIMELINE)
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
                        playbackState.update { copy(isPlaying = isPlaying) }
                        updatePosition()
                        if (isPlaying) startPolling() else stopPolling()
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

                playbackState.update { copy(isPlaying = mc.isPlaying) }
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
            Log.d("VANTA_PLAYBACK", "position loop started (100ms interval)")
            while (isActive) {
                updatePosition()
                delay(50)
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

        if (current.trackId != null && currentMediaTrackId != null && current.trackId != currentMediaTrackId) {
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
        appContext.startService(
            Intent(appContext, PlaybackService::class.java)
                .setAction(PlaybackService.ACTION_PLAY_DIRECT_URL)
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
            Intent(appContext, PlaybackService::class.java)
                .setAction(PlaybackService.ACTION_SKIP_LIVE_AD)
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
        if (seekableController != null) {
            seekableController.seekTo(clamped)
        } else {
            // Controller missing or its command grant lacks seek — route through the
            // service, which calls player.seekTo() directly and cannot be dropped.
            appContext.startService(
                Intent(appContext, PlaybackService::class.java)
                    .setAction(PlaybackService.ACTION_SEEK_TO)
                    .putExtra(PlaybackService.EXTRA_POSITION_MS, clamped)
            )
        }
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
            Log.d("VANTA_PLAY_TRACK_REQUEST", "  source[$i]: id=${s.sourceId} type=${s.sourceType} url=${s.streamUrl.take(60)}... bitrate=${s.bitrate}")
        }
        appContext.startService(
            Intent(appContext, PlaybackService::class.java)
                .setAction(PlaybackService.ACTION_PLAY_TRACK)
                .putExtra(PlaybackService.EXTRA_TRACK_ID, track.track.trackId)
        )
    }

    fun isDjQueueActive(): Boolean = queueManager.queueMode == QueueMode.AI_DJ_QUEUE

    fun playQueue(tracks: List<UnifiedTrackWithSources>, startIndex: Int = 0, mode: QueueMode = QueueMode.NORMAL_QUEUE) {
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

                Log.d("VANTA_PLAYBACK_TRACE", "step='queue_selected' trackId=${tracks[safeIndex].track.trackId} queueSize=${tracks.size} startIndex=$safeIndex generation=next")
                queueManager.setOriginalQueue(tracks, safeIndex, mode)
                queueManager.markCurrentTrack(tracks[safeIndex])
                playbackState.replace(
                    NowPlayingState.pendingPlayback(
                        track = tracks[safeIndex],
                        queuePosition = safeIndex,
                        queueSize = tracks.size
                    )
                )
                _trackTransition.tryEmit(Unit)
                Log.d(
                    "VANTA_NOWPLAYING_STATE",
                    "pendingPlayback trackId=${tracks[safeIndex].track.trackId} title='${tracks[safeIndex].track.title}' positionMs=0 queueIndex=$safeIndex"
                )

                appContext.startService(
                    Intent(appContext, PlaybackService::class.java)
                        .setAction(PlaybackService.ACTION_PLAY_TRACK)
                        .putExtra(PlaybackService.EXTRA_TRACK_ID, tracks[safeIndex].track.trackId)
                )
            }
        }
    }

    private fun sendAction(action: String) {
        appContext.startService(Intent(appContext, PlaybackService::class.java).setAction(action))
    }
}
