package com.audiophile.musicplayer.playback

import com.audiophile.musicplayer.data.source.SourceRegistry

/** Pure playback policy helpers — unit-tested guards for stream recovery and pause-wins. */
object PlaybackPolicies {

    fun canAttemptStreamRecovery(
        providerId: String?,
        externalTrackId: String?,
        streamUrl: String,
        expired: Boolean,
    ): Boolean {
        if (providerId.isNullOrBlank() || externalTrackId.isNullOrBlank()) return false
        return streamUrl.isBlank() || expired
    }

    fun shouldAutoPlayAfterResolve(userPauseRequested: Boolean, wasPlayingBefore: Boolean): Boolean =
        !userPauseRequested && wasPlayingBefore

    fun shouldResumeAfterRenewal(userPauseRequested: Boolean, playWhenReady: Boolean): Boolean =
        !userPauseRequested && playWhenReady

    fun isStreamExpired(
        expiresAtMs: Long?,
        streamUrl: String,
        nowMs: Long = System.currentTimeMillis(),
    ): Boolean {
        val expiryMs = normalizeEpochMs(expiresAtMs)
        if (expiryMs != null) return expiryMs < nowMs

        val urlExpiryMs = normalizeEpochMs(SourceRegistry.extractExpiryFromUrl(streamUrl))
        if (urlExpiryMs != null) return urlExpiryMs < nowMs

        return SourceRegistry.inferTtlFromHost(streamUrl) != null
    }

    private fun normalizeEpochMs(value: Long?): Long? {
        if (value == null || value <= 0L) return null
        return if (value < 100_000_000_000L) value * 1000L else value
    }
}
