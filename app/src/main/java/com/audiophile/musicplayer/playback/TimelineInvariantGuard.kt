package com.audiophile.musicplayer.playback

import android.util.Log

/**
 * Validates timeline / current-index consistency before Media3 session publication.
 * Prevents IllegalStateException in PlayerInfo.copyWithTimeline / mergePlayerInfo.
 */
object TimelineInvariantGuard {

    data class ValidationResult(
        val safeIndex: Int,
        val windowCount: Int,
        val blocked: Boolean,
        val reason: String?
    )

    fun validate(
        queueSize: Int,
        windowCount: Int,
        playerMediaItemCount: Int,
        currentIndex: Int,
        currentTrackId: Long?
    ): ValidationResult {
        if (windowCount <= 0) {
            val blocked = currentIndex != 0
            if (blocked) {
                logBlocked(queueSize, windowCount, playerMediaItemCount, currentIndex, currentTrackId, "empty_timeline_nonzero_index")
            }
            return ValidationResult(
                safeIndex = 0,
                windowCount = 0,
                blocked = blocked,
                reason = if (blocked) "empty_timeline_nonzero_index" else null
            )
        }

        if (currentIndex < 0 || currentIndex >= windowCount) {
            val clamped = currentIndex.coerceIn(0, (windowCount - 1).coerceAtLeast(0))
            logBlocked(queueSize, windowCount, playerMediaItemCount, currentIndex, currentTrackId, "index_out_of_bounds")
            return ValidationResult(
                safeIndex = clamped,
                windowCount = windowCount,
                blocked = true,
                reason = "index_out_of_bounds"
            )
        }

        if (playerMediaItemCount > 1 && playerMediaItemCount != windowCount) {
            logBlocked(queueSize, windowCount, playerMediaItemCount, currentIndex, currentTrackId, "exo_queue_size_mismatch")
            return ValidationResult(
                safeIndex = currentIndex.coerceIn(0, minOf(windowCount, playerMediaItemCount) - 1),
                windowCount = minOf(windowCount, playerMediaItemCount),
                blocked = true,
                reason = "exo_queue_size_mismatch"
            )
        }

        if (queueSize > 0 && windowCount > queueSize && currentIndex >= queueSize) {
            logBlocked(queueSize, windowCount, playerMediaItemCount, currentIndex, currentTrackId, "index_beyond_queue")
            return ValidationResult(
                safeIndex = (queueSize - 1).coerceAtLeast(0),
                windowCount = queueSize,
                blocked = true,
                reason = "index_beyond_queue"
            )
        }

        return ValidationResult(
            safeIndex = currentIndex,
            windowCount = windowCount,
            blocked = false,
            reason = null
        )
    }

    private fun logBlocked(
        queueSize: Int,
        windowCount: Int,
        playerMediaItemCount: Int,
        currentIndex: Int,
        currentTrackId: Long?,
        reason: String
    ) {
        Log.w(
            "VANTA_TIMELINE_GUARD",
            "blocked invalid timeline: queueSize=$queueSize windowCount=$windowCount " +
                "playerMediaItemCount=$playerMediaItemCount currentIndex=$currentIndex " +
                "currentTrackId=${currentTrackId ?: "null"} reason=$reason"
        )
    }
}
