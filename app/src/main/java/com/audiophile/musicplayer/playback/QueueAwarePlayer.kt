package com.audiophile.musicplayer.playback

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.MediaItem
import androidx.media3.common.Metadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.common.Tracks
import androidx.media3.common.util.Clock
import androidx.media3.common.util.ListenerSet
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import com.audiophile.musicplayer.data.local.entities.UnifiedTrackWithSources

/**
 * Wraps [exoPlayer] for actual playback while exposing a multi-item queue timeline to
 * [androidx.media3.session.MediaSession] so notification / lock-screen controls show Next/Previous.
 */
@OptIn(UnstableApi::class)
class QueueAwarePlayer(
    private val exoPlayer: ExoPlayer,
    private val queueSnapshotProvider: () -> QueueSnapshot,
    private val onSkipToNext: () -> Unit,
    private val onSkipToPrevious: () -> Unit,
) : ForwardingPlayer(exoPlayer) {

    private val notificationListeners = ListenerSet<Player.Listener>(
        Looper.getMainLooper(),
        Clock.DEFAULT
    ) { _, _ -> }

    private val timelineHandler = Handler(Looper.getMainLooper())
    private val timelineRunnable = Runnable { executeTimelineRefresh() }
    private var lastTimelineSignature: Long = 0L
    private var lastTimelineExecuteAtMs: Long = 0L
    private var timelineNotifyDepth = 0
    @Volatile private var isReplacingQueue = false
    @Volatile private var pendingTimelineRefresh = false
    @Volatile private var replacementMediaItems: List<MediaItem> = emptyList()
    @Volatile private var replacementStartIndex: Int = 0

    @Volatile private var isRefreshingTimeline = false
    @Volatile private var isTimelineRefreshing = false

    private val exoEventBridge = object : Player.Listener {
        override fun onTimelineChanged(timeline: Timeline, reason: Int) {
            // Strict throttle: at most one external trigger per 500ms.
            // This prevents the observer loop: notifyQueueTimelineIfNeeded →
            // EVENT_TIMELINE_CHANGED → MediaSession → ExoPlayer →
            // onTimelineChanged → refreshQueueTimeline → ... (infinite).
            val now = SystemClock.uptimeMillis()
            if (reason == Player.TIMELINE_CHANGE_REASON_SOURCE_UPDATE &&
                now - lastTimelineExecuteAtMs > 500L &&
                !isTimelineRefreshing) {
                refreshQueueTimeline()
            }
        }

        override fun onAvailableCommandsChanged(availableCommands: Player.Commands) {
            notificationListeners.sendEvent(Player.EVENT_AVAILABLE_COMMANDS_CHANGED) {
                it.onAvailableCommandsChanged(this@QueueAwarePlayer.availableCommands)
            }
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            notificationListeners.sendEvent(Player.EVENT_PLAYBACK_STATE_CHANGED) {
                it.onPlaybackStateChanged(playbackState)
            }
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            notificationListeners.sendEvent(Player.EVENT_IS_PLAYING_CHANGED) {
                it.onIsPlayingChanged(isPlaying)
            }
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            notificationListeners.sendEvent(Player.EVENT_MEDIA_ITEM_TRANSITION) {
                it.onMediaItemTransition(mediaItem, reason)
            }
        }

        override fun onPlayerError(error: PlaybackException) {
            notificationListeners.sendEvent(Player.EVENT_PLAYER_ERROR) {
                it.onPlayerError(error)
            }
        }

        override fun onPositionDiscontinuity(
            oldPosition: Player.PositionInfo,
            newPosition: Player.PositionInfo,
            reason: Int
        ) {
            notificationListeners.sendEvent(Player.EVENT_POSITION_DISCONTINUITY) {
                it.onPositionDiscontinuity(oldPosition, newPosition, reason)
            }
        }

        override fun onTracksChanged(tracks: Tracks) {
            notificationListeners.sendEvent(Player.EVENT_TRACKS_CHANGED) {
                it.onTracksChanged(tracks)
            }
        }

        override fun onIsLoadingChanged(isLoading: Boolean) {
            notificationListeners.sendEvent(Player.EVENT_IS_LOADING_CHANGED) {
                it.onIsLoadingChanged(isLoading)
            }
        }

        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
            notificationListeners.sendEvent(Player.EVENT_PLAY_WHEN_READY_CHANGED) {
                it.onPlayWhenReadyChanged(playWhenReady, reason)
            }
        }

        override fun onMetadata(metadata: Metadata) {
            notificationListeners.sendEvent(Player.EVENT_METADATA) {
                it.onMetadata(metadata)
            }
        }
    }

    override fun addListener(listener: Player.Listener) {
        notificationListeners.add(listener)
    }

    override fun removeListener(listener: Player.Listener) {
        notificationListeners.remove(listener)
    }

    init {
        exoPlayer.addListener(exoEventBridge)
    }

    fun beginQueueReplacement(mediaItems: List<MediaItem> = emptyList(), startIndex: Int = 0) {
        replacementMediaItems = mediaItems.toList()
        replacementStartIndex = startIndex.coerceIn(0, (replacementMediaItems.size - 1).coerceAtLeast(0))
        isReplacingQueue = true
        pendingTimelineRefresh = true
        timelineHandler.removeCallbacks(timelineRunnable)
    }

    fun endQueueReplacement() {
        isReplacingQueue = false
        replacementMediaItems = emptyList()
        replacementStartIndex = 0
        refreshQueueTimeline()
    }

    fun refreshQueueTimeline() {
        if (isTimelineRefreshing) return
        isTimelineRefreshing = true
        try {
            if (isReplacingQueue) {
                pendingTimelineRefresh = true
                return
            }
            // ABSOLUTE DEBOUNCE: Remove pending runs, post new one 300ms from now.
            timelineHandler.removeCallbacks(timelineRunnable)
            timelineHandler.postDelayed(timelineRunnable, 300L)
        } finally {
            isTimelineRefreshing = false
        }
    }

    private fun executeTimelineRefresh() {
        if (isReplacingQueue) {
            pendingTimelineRefresh = true
            return
        }
        if (isRefreshingTimeline) return
        isRefreshingTimeline = true
        try {
            val now = SystemClock.uptimeMillis()
            val elapsed = now - lastTimelineExecuteAtMs
            if (elapsed < 300L) {
                timelineHandler.removeCallbacks(timelineRunnable)
                timelineHandler.postDelayed(timelineRunnable, 300L - elapsed)
                return
            }

            val snapshot = queueSnapshotProvider()
            val signature = snapshot.originalQueue.size.toLong() * 31L +
                snapshot.currentOriginalIndex * 17L +
                (snapshot.currentTrack?.track?.trackId ?: 0L)

            if (signature == lastTimelineSignature) return
            lastTimelineSignature = signature
            lastTimelineExecuteAtMs = now

            com.audiophile.musicplayer.debug.DebugSessionLogger.log(
                hypothesisId = "H5",
                location = "QueueAwarePlayer.executeTimelineRefresh",
                message = "timeline_refresh",
                data = mapOf(
                    "originalQueueSize" to snapshot.originalQueue.size,
                    "currentTrackId" to (snapshot.currentTrack?.track?.trackId ?: "null")
                ),
                runId = "handler-strict-v2"
            )

            notifyQueueTimelineIfNeeded(force = false)
        } catch (e: Exception) {
            android.util.Log.e("VANTA_TIMELINE", "Refresh failed", e)
        } finally {
            isRefreshingTimeline = false
        }
    }

    companion object {
        private const val TAG = "QueueAwarePlayer"
    }

    override fun getCurrentTimeline(): Timeline {
        replacementTimelineOrNull()?.let { return it }

        if (exoPlayer.mediaItemCount > 0) {
            val timeline = exoPlayer.currentTimeline
            val validation = TimelineInvariantGuard.validate(
                queueSize = queueSnapshotProvider().originalQueue.size,
                windowCount = timeline.windowCount,
                playerMediaItemCount = exoPlayer.mediaItemCount,
                currentIndex = exoPlayer.currentMediaItemIndex,
                currentTrackId = exoPlayer.currentMediaItem?.mediaId?.substringBefore(":")?.toLongOrNull()
            )
            if (!validation.blocked) return timeline

            val exoItems = exoMediaItemsSnapshot()
            if (exoItems.isNotEmpty() && validation.windowCount > 0) {
                return buildValidatedTimeline(
                    exoItems.take(validation.windowCount.coerceAtMost(exoItems.size)),
                    validation.safeIndex
                )
            }
            return if (validation.windowCount <= 0) Timeline.EMPTY else timeline
        }
        val snapshot = queueSnapshotProvider()
        val queueItems = buildQueueMediaItems(snapshot)
        if (queueItems.isEmpty()) {
            return exoPlayer.currentTimeline
        }
        if (queueItems.size <= 1) {
            return exoPlayer.currentTimeline
        }
        val rawIndex = resolveCurrentIndex(snapshot, queueItems.size)
        val validation = TimelineInvariantGuard.validate(
            queueSize = snapshot.originalQueue.size,
            windowCount = queueItems.size,
            playerMediaItemCount = exoPlayer.mediaItemCount,
            currentIndex = rawIndex,
            currentTrackId = snapshot.currentTrack?.track?.trackId
        )
        if (validation.blocked) {
            if (validation.windowCount <= 0) return exoPlayer.currentTimeline
            if (validation.windowCount != queueItems.size) {
                val trimmed = queueItems.take(validation.windowCount)
                if (trimmed.size <= 1) return exoPlayer.currentTimeline
                return buildValidatedTimeline(trimmed, validation.safeIndex)
            }
        }
        return buildValidatedTimeline(queueItems, validation.safeIndex)
    }

    private fun buildValidatedTimeline(
        queueItems: List<MediaItem>,
        currentIndex: Int
    ): Timeline {
        val safeIndex = currentIndex.coerceIn(0, queueItems.lastIndex)
        return QueueTimeline(
            mediaItems = queueItems,
            currentIndex = safeIndex,
            positionUs = exoPlayer.currentPosition.coerceAtLeast(0L) * 1_000L,
            durationUs = exoPlayer.duration.takeIf { it > 0 && it != C.TIME_UNSET }?.times(1_000L)
                ?: C.TIME_UNSET
        )
    }

    private fun replacementTimelineOrNull(): Timeline? {
        val items = replacementMediaItems
        if (!isReplacingQueue || items.isEmpty()) return null
        return buildValidatedTimeline(items, replacementStartIndex.coerceIn(0, items.lastIndex))
    }

    private fun exoMediaItemsSnapshot(): List<MediaItem> {
        val count = exoPlayer.mediaItemCount
        if (count <= 0) return emptyList()
        return (0 until count).mapNotNull { index ->
            runCatching { exoPlayer.getMediaItemAt(index) }.getOrNull()
        }
    }

    override fun hasNextMediaItem(): Boolean {
        if (exoPlayer.mediaItemCount > 1) {
            return exoPlayer.currentMediaItemIndex < exoPlayer.mediaItemCount - 1 ||
                queueSnapshotProvider().canPlayNext
        }
        val snapshot = queueSnapshotProvider()
        val queueItems = buildQueueMediaItems(snapshot)
        if (queueItems.size <= 1) return false
        val currentIndex = resolveCurrentIndex(snapshot, queueItems.size)
        return currentIndex < queueItems.lastIndex ||
            snapshot.priorityQueue.isNotEmpty() ||
            snapshot.upNextQueue.isNotEmpty()
    }

    override fun hasPreviousMediaItem(): Boolean {
        if (exoPlayer.mediaItemCount > 1) {
            return exoPlayer.currentMediaItemIndex > 0 ||
                queueSnapshotProvider().canPlayPrevious
        }
        val snapshot = queueSnapshotProvider()
        val queueItems = buildQueueMediaItems(snapshot)
        if (queueItems.size <= 1) return false
        val currentIndex = resolveCurrentIndex(snapshot, queueItems.size)
        return currentIndex > 0 || snapshot.canPlayPrevious
    }

    override fun isCommandAvailable(command: @Player.Command Int): Boolean {
        return when (command) {
            Player.COMMAND_SEEK_TO_NEXT,
            Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM -> hasNextMediaItem()
            Player.COMMAND_SEEK_TO_PREVIOUS,
            Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM -> hasPreviousMediaItem()
            else -> super.isCommandAvailable(command)
        }
    }

    override fun getAvailableCommands(): Player.Commands {
        return super.getAvailableCommands().buildUpon().apply {
            if (hasNextMediaItem()) {
                add(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
                add(Player.COMMAND_SEEK_TO_NEXT)
            }
            if (hasPreviousMediaItem()) {
                add(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
                add(Player.COMMAND_SEEK_TO_PREVIOUS)
            }
        }.build()
    }

    override fun getMediaItemCount(): Int {
        if (isReplacingQueue && replacementMediaItems.isNotEmpty()) return replacementMediaItems.size
        if (exoPlayer.mediaItemCount > 0) {
            val windowCount = exoPlayer.currentTimeline.windowCount
            return if (windowCount > 0) windowCount else exoPlayer.mediaItemCount
        }
        val queueItems = buildQueueMediaItems(queueSnapshotProvider())
        return if (queueItems.size > 1) queueItems.size else super.getMediaItemCount()
    }

    override fun getCurrentMediaItemIndex(): Int {
        if (isReplacingQueue && replacementMediaItems.isNotEmpty()) {
            return replacementStartIndex.coerceIn(0, replacementMediaItems.lastIndex)
        }
        val snapshot = queueSnapshotProvider()
        val queueItems = buildQueueMediaItems(snapshot)
        val rawIndex = if (exoPlayer.mediaItemCount > 0) {
            exoPlayer.currentMediaItemIndex
        } else {
            resolveCurrentIndex(snapshot, queueItems.size)
        }
        
        val windowCount = if (exoPlayer.mediaItemCount > 0) exoPlayer.mediaItemCount else queueItems.size
        return rawIndex.coerceIn(0, (windowCount - 1).coerceAtLeast(0))
    }

    override fun getMediaItemAt(index: Int): MediaItem {
        val replacementItems = replacementMediaItems
        if (isReplacingQueue && index in replacementItems.indices) {
            return replacementItems[index]
        }
        if (exoPlayer.mediaItemCount > 0 && index in 0 until exoPlayer.mediaItemCount) {
            return exoPlayer.getMediaItemAt(index)
        }
        val queueItems = buildQueueMediaItems(queueSnapshotProvider())
        if (queueItems.size > 1 && index in queueItems.indices) {
            return queueItems[index]
        }
        return super.getMediaItemAt(index)
    }

    override fun getPlaybackState(): Int {
        val state = super.getPlaybackState()
        if (state == Player.STATE_IDLE) {
            if (exoPlayer.mediaItemCount > 0) return Player.STATE_BUFFERING

            val snapshot = queueSnapshotProvider()
            val hasQueueItems = snapshot.originalQueue.isNotEmpty() ||
                snapshot.priorityQueue.isNotEmpty() ||
                snapshot.upNextQueue.isNotEmpty() ||
                exoPlayer.currentMediaItem != null

            if (hasQueueItems) return Player.STATE_BUFFERING
        }
        return state
    }

    override fun seekToNextMediaItem() {
        if (exoPlayer.mediaItemCount > 1 &&
            exoPlayer.currentMediaItemIndex < exoPlayer.mediaItemCount - 1 &&
            exoPlayer.getMediaItemAt(exoPlayer.currentMediaItemIndex + 1).localConfiguration?.uri != null
        ) {
            super.seekToNextMediaItem()
            return
        }
        if (queueSnapshotProvider().canPlayNext) {
            onSkipToNext()
        } else {
            super.seekToNextMediaItem()
        }
    }

    override fun seekToPreviousMediaItem() {
        if (queueSnapshotProvider().canPlayPrevious) {
            onSkipToPrevious()
        } else {
            super.seekToPreviousMediaItem()
        }
    }

    override fun seekToNext() {
        if (queueSnapshotProvider().canPlayNext) {
            onSkipToNext()
        } else {
            super.seekToNext()
        }
    }

    override fun seekToPrevious() {
        if (queueSnapshotProvider().canPlayPrevious) {
            onSkipToPrevious()
        } else {
            super.seekToPrevious()
        }
    }

    override fun seekTo(mediaItemIndex: Int, positionMs: Long) {
        val currentIndex = currentMediaItemIndex
        if (mediaItemIndex == currentIndex) {
            super.seekTo(exoPlayer.currentMediaItemIndex, positionMs)
        } else {
            super.seekTo(mediaItemIndex, positionMs)
        }
    }

    override fun seekToDefaultPosition(mediaItemIndex: Int) {
        val currentIndex = currentMediaItemIndex
        if (mediaItemIndex == currentIndex) {
            super.seekToDefaultPosition(exoPlayer.currentMediaItemIndex)
        } else {
            super.seekToDefaultPosition(mediaItemIndex)
        }
    }

    private var lastNotifiedSignature: Long = 0L

    private fun notifyQueueTimelineIfNeeded(force: Boolean = false) {
        if (isReplacingQueue) {
            pendingTimelineRefresh = true
            return
        }
        if (!force && timelineNotifyDepth > 0) return
        timelineNotifyDepth++
        try {
            val snapshot = queueSnapshotProvider()
            val currentSig = snapshot.originalQueue.size.toLong() * 31L +
                snapshot.currentOriginalIndex * 17L +
                (snapshot.currentTrack?.track?.trackId ?: 0L)
            if (!force && currentSig == lastNotifiedSignature) return
            lastNotifiedSignature = currentSig

            if (exoPlayer.mediaItemCount > 0) {
                notificationListeners.sendEvent(Player.EVENT_AVAILABLE_COMMANDS_CHANGED) { listener ->
                    listener.onAvailableCommandsChanged(availableCommands)
                }
                return
            }

            val queueItems = buildQueueMediaItems(snapshot)
            val rawIndex = if (exoPlayer.mediaItemCount > 0) {
                exoPlayer.currentMediaItemIndex
            } else if (queueItems.isNotEmpty()) {
                resolveCurrentIndex(snapshot, queueItems.size)
            } else {
                0
            }
            val windowCount = if (exoPlayer.mediaItemCount > 0) exoPlayer.mediaItemCount else queueItems.size
            val validation = TimelineInvariantGuard.validate(
                queueSize = snapshot.originalQueue.size,
                windowCount = windowCount,
                playerMediaItemCount = exoPlayer.mediaItemCount,
                currentIndex = rawIndex,
                currentTrackId = snapshot.currentTrack?.track?.trackId
            )
            if (validation.blocked && validation.windowCount <= 0) {
                Log.w(TAG, "Skipping timeline publish — empty queue with invalid index")
                return
            }
            val timeline = getCurrentTimeline()
            notificationListeners.sendEvent(Player.EVENT_TIMELINE_CHANGED) { listener ->
                listener.onTimelineChanged(timeline, Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED)
            }
            notificationListeners.sendEvent(Player.EVENT_AVAILABLE_COMMANDS_CHANGED) { listener ->
                listener.onAvailableCommandsChanged(availableCommands)
            }
        } catch (e: Exception) {
            Log.e("VANTA_TIMELINE", "notifyQueueTimelineIfNeeded failed", e)
        } finally {
            timelineNotifyDepth--
        }
    }

    private fun buildQueueMediaItems(snapshot: QueueSnapshot): List<MediaItem> {
        val orderedTracks = linkedMapOf<Long, UnifiedTrackWithSources>()
        snapshot.originalQueue.forEach { track ->
            orderedTracks.putIfAbsent(track.track.trackId, track)
        }
        snapshot.priorityQueue.forEach { track ->
            orderedTracks.putIfAbsent(track.track.trackId, track)
        }
        snapshot.upNextQueue.forEach { track ->
            orderedTracks.putIfAbsent(track.track.trackId, track)
        }
        snapshot.currentTrack?.let { track ->
            if (track.track.trackId !in orderedTracks) {
                orderedTracks[track.track.trackId] = track
            }
        }
        if (orderedTracks.isEmpty()) {
            exoPlayer.currentMediaItem?.let { return listOf(it) }
        }
        return orderedTracks.values.map { it.toQueueMediaItem() }
    }

    private fun resolveCurrentIndex(snapshot: QueueSnapshot, itemCount: Int): Int {
        if (itemCount <= 0) return 0
        val currentTrackId = snapshot.currentTrack?.track?.trackId
            ?: exoPlayer.currentMediaItem?.mediaId?.substringBefore(":")?.toLongOrNull()
        if (currentTrackId != null) {
            val tracks = buildQueueMediaItems(snapshot)
            val index = tracks.indexOfFirst { mediaItem ->
                mediaItem.mediaId.substringBefore(":").toLongOrNull() == currentTrackId
            }
            if (index >= 0) return index
        }
        return snapshot.currentOriginalIndex.coerceIn(0, itemCount - 1)
    }

    private class QueueTimeline(
        private val mediaItems: List<MediaItem>,
        private val currentIndex: Int,
        private val positionUs: Long,
        private val durationUs: Long,
    ) : Timeline() {

        override fun getWindowCount(): Int = mediaItems.size

        override fun getPeriodCount(): Int = mediaItems.size

        override fun getWindow(windowIndex: Int, window: Window, defaultPositionProjectionUs: Long): Window {
            val mediaItem = mediaItems[windowIndex]
            val isCurrent = windowIndex == currentIndex
            val windowDurationUs = when {
                isCurrent && durationUs > 0 && durationUs != C.TIME_UNSET -> durationUs
                else -> mediaItem.mediaMetadata.durationMs?.takeIf { it > 0 }?.let { it * 1_000L } ?: C.TIME_UNSET
            }
            window.set(
                windowIndex.toLong(),
                mediaItem,
                null,
                C.TIME_UNSET,
                C.TIME_UNSET,
                C.TIME_UNSET,
                true,
                false,
                null,
                if (isCurrent) positionUs else 0L,
                windowDurationUs,
                windowIndex,
                windowIndex,
                0L
            )
            return window
        }

        override fun getPeriod(periodIndex: Int, period: Period, setIds: Boolean): Period {
            val mediaItem = mediaItems[periodIndex]
            val isCurrent = periodIndex == currentIndex
            val periodDurationUs = when {
                isCurrent && durationUs > 0 && durationUs != C.TIME_UNSET -> durationUs
                else -> mediaItem.mediaMetadata.durationMs?.takeIf { it > 0 }?.let { it * 1_000L } ?: C.TIME_UNSET
            }
            period.set(
                periodIndex.toLong(),
                periodIndex.toLong(),
                periodIndex,
                periodDurationUs,
                0L
            )
            return period
        }

        override fun getIndexOfPeriod(uid: Any): Int {
            return (uid as? Long)?.toInt()?.takeIf { it in mediaItems.indices } ?: C.INDEX_UNSET
        }

        override fun getUidOfPeriod(periodIndex: Int): Any = periodIndex.toLong()

        override fun getFirstWindowIndex(shuffleModeEnabled: Boolean): Int = 0

        override fun getLastWindowIndex(shuffleModeEnabled: Boolean): Int = mediaItems.lastIndex

        override fun getNextWindowIndex(windowIndex: Int, repeatMode: @Player.RepeatMode Int, shuffleModeEnabled: Boolean): Int {
            return if (windowIndex < mediaItems.lastIndex) windowIndex + 1 else C.INDEX_UNSET
        }

        override fun getPreviousWindowIndex(windowIndex: Int, repeatMode: @Player.RepeatMode Int, shuffleModeEnabled: Boolean): Int {
            return if (windowIndex > 0) windowIndex - 1 else C.INDEX_UNSET
        }
    }
}

private fun UnifiedTrackWithSources.toQueueMediaItem(): MediaItem =
    PlaybackMediaItems.queuePlaceholder(this)
