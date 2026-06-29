package com.audiophile.musicplayer.playback

data class PlaybackStabilityCounters(
    var bufferingCount: Int = 0,
    var totalBufferingMs: Long = 0L,
    var reBufferCount: Int = 0,
    var reBufferTotalMs: Long = 0L,
    var readyCount: Int = 0,
    var errorCount: Int = 0,
    var mediaItemResetCount: Int = 0,
    var reResolveCount: Int = 0,
    var currentBitrateKbps: Int = 0,
    var currentMimeType: String? = null,
    var streamHost: String? = null,
    var lastPlaybackError: String? = null,
    var bufferingStartMs: Long = 0L,
    var trackStartTimeMs: Long = 0L,
    var startPositionMs: Long = 0L,
    var stallCount: Int = 0,
    var consecutiveStalledChecks: Int = 0,
    var lastCheckedPositionMs: Long = 0L,
    var lastCheckTimeMs: Long = 0L
) {
    fun reset() {
        bufferingCount = 0
        totalBufferingMs = 0L
        reBufferCount = 0
        reBufferTotalMs = 0L
        readyCount = 0
        errorCount = 0
        mediaItemResetCount = 0
        reResolveCount = 0
        currentBitrateKbps = 0
        currentMimeType = null
        streamHost = null
        lastPlaybackError = null
        bufferingStartMs = 0L
        trackStartTimeMs = 0L
        startPositionMs = 0L
        stallCount = 0
        consecutiveStalledChecks = 0
        lastCheckedPositionMs = 0L
        lastCheckTimeMs = 0L
    }

    fun toSummaryString(): String {
        val elapsed = if (trackStartTimeMs > 0L) System.currentTimeMillis() - trackStartTimeMs else 0L
        return "bufferingCount=$bufferingCount totalBufferingMs=${totalBufferingMs}ms " +
            "reBufferCount=$reBufferCount reBufferTotalMs=${reBufferTotalMs}ms " +
            "readyCount=$readyCount " +
            "errorCount=$errorCount mediaItemResetCount=$mediaItemResetCount reResolveCount=$reResolveCount " +
            "stallCount=$stallCount " +
            "bitrate=${currentBitrateKbps}kbps mimeType=${currentMimeType ?: "unknown"} " +
            "host=${streamHost ?: "unknown"} lastError='${lastPlaybackError ?: "none"}' " +
            "elapsed=${elapsed}ms"
    }
}
