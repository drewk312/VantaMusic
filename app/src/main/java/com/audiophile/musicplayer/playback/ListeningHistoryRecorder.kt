package com.audiophile.musicplayer.playback

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** Snapshot of what is currently playing, captured when a track starts. */
data class ListeningPlayRecord(
    val trackId: Long,
    val title: String,
    val artist: String,
    val album: String?,
    val platform: String?,
    val providerId: String?,
    val sourceTrackId: String?,
    val durationMs: Long?
)

/** Interface implemented by the repository sink so the recorder stays unit-testable. */
fun interface ListeningHistorySink {
    suspend fun write(
        record: ListeningPlayRecord,
        startedAt: Long,
        msPlayed: Long,
        skipped: Boolean,
        reasonEnd: String?
    )
}

/**
 * Records completed/partial plays and skips into a [ListeningHistorySink].
 *
 * Lifecycle:
 *  - [onPlaybackStarted] opens a pending entry (and finalizes any stale pending as skipped).
 *  - [onTrackEndedNaturally] closes the pending entry as a COMPLETE play (msPlayed = duration).
 *  - [onTrackSkipped] closes the pending entry as a SKIP with the position reached so far.
 *  - [onPlaybackStopped] closes a partial play (pause/quit mid-track) as a short play.
 *
 * All writes are fire-and-forget on [scope]; failures never affect playback.
 */
class ListeningHistoryRecorder(
    private val sink: ListeningHistorySink,
    private val scope: CoroutineScope
) {

    private class Pending(
        val record: ListeningPlayRecord,
        val startedAt: Long
    )

    @Volatile
    private var pending: Pending? = null

    fun onPlaybackStarted(record: ListeningPlayRecord, positionMs: Long = 0L) {
        finalize(positionMs, skipped = true, reasonEnd = "new_track")
        pending = Pending(record, System.currentTimeMillis())
    }

    fun onTrackEndedNaturally(durationMs: Long) {
        val current = pending ?: return
        if (durationMs > 0L) {
            write(current, durationMs, skipped = false, reasonEnd = "trackdone")
        }
        pending = null
    }

    fun onTrackSkipped(positionMs: Long, reasonEnd: String = "fwdbtn") {
        finalize(positionMs, skipped = true, reasonEnd = reasonEnd)
    }

    fun onPlaybackStopped(positionMs: Long) {
        val current = pending ?: return
        write(current, positionMs.coerceAtLeast(0L), skipped = false, reasonEnd = "stop")
        pending = null
    }

    fun clearPending() {
        pending = null
    }

    private fun finalize(positionMs: Long, skipped: Boolean, reasonEnd: String) {
        val current = pending ?: return
        write(current, positionMs.coerceAtLeast(0L), skipped, reasonEnd)
        pending = null
    }

    private fun write(pending: Pending, msPlayed: Long, skipped: Boolean, reasonEnd: String) {
        val record = pending.record
        val duration = record.durationMs
        val played = if (duration != null && duration > 0L) msPlayed.coerceAtMost(duration) else msPlayed
        scope.launch {
            runCatching {
                sink.write(record, pending.startedAt, played, skipped, reasonEnd)
            }
        }
    }
}