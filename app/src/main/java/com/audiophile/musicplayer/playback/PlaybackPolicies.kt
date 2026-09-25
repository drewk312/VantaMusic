package com.audiophile.musicplayer.playback

import com.audiophile.musicplayer.data.source.SourceRegistry
import com.audiophile.musicplayer.data.local.entities.UnifiedTrackWithSources
import com.audiophile.musicplayer.data.source.ResolvedStream

/** Pure playback policy helpers — unit-tested guards for stream recovery and pause-wins. */
object PlaybackPolicies {

    private const val HANDOFF_EXPIRY_BUFFER_MS = 15_000L

    /**
     * Accept a freshly resolved stream from the UI only when it is bound to the
     * exact persisted source loaded by the service. This removes a redundant
     * resolve without allowing an Intent URL to bypass database/source truth.
     */
    fun canUseResolvedStreamHandoff(
        track: UnifiedTrackWithSources,
        stream: ResolvedStream,
        nowMs: Long = System.currentTimeMillis(),
    ): Boolean {
        if (!PlaybackUrlPolicy.isAllowedRemoteStreamUrl(stream.streamUrl)) return false
        if (stream.streamUrl.contains("soundhelix", ignoreCase = true)) return false

        val persistedSource = track.sources.firstOrNull { it.streamUrl == stream.streamUrl }
            ?: return false
        if (!stream.providerId.isNullOrBlank() &&
            !persistedSource.externalProviderId.equals(stream.providerId, ignoreCase = true)
        ) {
            return false
        }

        val minimumExpiry = nowMs + HANDOFF_EXPIRY_BUFFER_MS
        val persistedExpiry = normalizeEpochMs(persistedSource.expiresAtMs)
        if (persistedExpiry != null && persistedExpiry <= minimumExpiry) return false
        val resolvedExpiry = normalizeEpochMs(stream.expiresAt)
        if (resolvedExpiry != null && resolvedExpiry <= minimumExpiry) return false
        return true
    }

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

    /**
     * Returns the track that must be re-resolved before Play can succeed.
     * A cold service start has no media item, while a failed CDN request leaves
     * a media item behind with a player error; both cases need a fresh stream.
     */
    fun trackIdToResolveForPlay(
        hasMediaItems: Boolean,
        hasPlayerError: Boolean,
        activeTrackId: Long?,
        stateTrackId: String?,
    ): Long? {
        if (hasMediaItems && !hasPlayerError) return null
        return activeTrackId ?: stateTrackId?.toLongOrNull()
    }

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
