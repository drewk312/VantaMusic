package com.audiophile.musicplayer.playback

import com.audiophile.musicplayer.common.VantaLogger
import com.audiophile.musicplayer.data.local.entities.UnifiedTrackWithSources
import com.audiophile.musicplayer.data.repository.TrackRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Thin wrapper around [QueueManager] for use inside [PlaybackService].
 *
 * Responsibilities:
 * - Exposes queue navigation (next/previous) with history recording.
 * - Triggers [TrackRepository.updateLastPlayedAt] on track consumption.
 * - Provides snapshots without leaking [QueueManager] internals to the service.
 *
 * Does NOT own the ExoPlayer timeline; that stays in [PlaybackService].
 */
class QueueController(
    private val queueManager: QueueManager,
    private val trackRepository: TrackRepository,
    private val scope: CoroutineScope,
) {

    fun currentTrack(): UnifiedTrackWithSources? =
        queueManager.currentTrack

    suspend fun peekNext(): UnifiedTrackWithSources? {
        val snapshot = queueManager.snapshot()
        return snapshot.upNextQueue.firstOrNull() ?: snapshot.originalQueue.getOrNull(snapshot.currentOriginalIndex + 1)
    }

    suspend fun advance(): UnifiedTrackWithSources? {
        val consumed = queueManager.currentTrack
        val next = queueManager.getNextTrack()
        if (consumed != null) {
            scope.launch(Dispatchers.IO) {
                trackRepository.updateLastPlayedAt(consumed.track.trackId)
                VantaLogger.d(VantaLogger.Tag.QUEUE, "consumed trackId=${consumed.track.trackId} title='${consumed.track.title}'")
            }
        }
        return next
    }

    suspend fun back(): UnifiedTrackWithSources? = queueManager.getPreviousTrack()

    fun snapshot(): QueueSnapshot = queueManager.snapshot()

    suspend fun loadPlayedHistory(ids: List<String>) {
        queueManager.loadPlayedHistory(ids.mapNotNull { it.toLongOrNull() })
    }

    suspend fun setPlayQueue(
        tracks: List<UnifiedTrackWithSources>,
        startIndex: Int,
        mode: QueueMode = QueueMode.NORMAL_QUEUE
    ) {
        queueManager.setOriginalQueue(tracks, startIndex, mode)
        VantaLogger.d(
            VantaLogger.Tag.QUEUE,
            "queue_set size=${tracks.size} startIndex=$startIndex mode=$mode"
        )
    }
}
