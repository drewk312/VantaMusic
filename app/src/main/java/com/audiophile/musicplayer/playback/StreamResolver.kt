package com.audiophile.musicplayer.playback

import com.audiophile.musicplayer.common.VantaLogger
import com.audiophile.musicplayer.data.local.entities.SourceType
import com.audiophile.musicplayer.data.local.entities.TrackSource
import com.audiophile.musicplayer.data.local.entities.UnifiedTrackWithSources
import com.audiophile.musicplayer.data.repository.TrackRepository
import com.audiophile.musicplayer.data.source.SourceRegistry
import com.audiophile.musicplayer.data.source.ResolvedStream
import com.audiophile.musicplayer.data.source.canResolveStream
import com.audiophile.musicplayer.data.source.sourceValidityStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Resolves a playable stream URL for a [UnifiedTrackWithSources].
 *
 * Resolution order:
 * 1. Use an existing valid stream URL directly (not expired, not soundhelix).
 * 2. Re-resolve via [SourceRegistry] using the stored externalProviderId/Id.
 * 3. Search all providers by title+artist and pick the highest-ranked match.
 *
 * Returns null if no playable stream can be found.
 */
class StreamResolver(
    private val sourceRegistry: SourceRegistry,
    private val trackRepository: TrackRepository,
) {

    /** Minimum validity window before a stream URL is considered expired. */
    private val EXPIRY_BUFFER_MS = 60_000L

    /**
     * Returns a ready-to-play URL for [track], or null if resolution fails.
     *
     * @param timeoutMs Per-source resolution timeout.
     */
    suspend fun resolve(
        track: UnifiedTrackWithSources,
        timeoutMs: Long = 15_000L
    ): ResolvedStream? = withContext(Dispatchers.IO) {

        // Step 1: valid existing URL
        val existing = track.sources.firstOrNull { isUsable(it) }
        if (existing != null && existing.streamUrl.isNotBlank()) {
            VantaLogger.d(
                VantaLogger.Tag.STREAM,
                "resolve_existing trackId=${track.track.trackId} url=${existing.streamUrl.take(60)}"
            )
            return@withContext ResolvedStream(
                streamUrl = existing.streamUrl,
                bitrateKbps = existing.bitrate ?: 0,
                expiresAt = existing.expiresAtMs
            )
        }

        // Step 2: re-resolve via stored identity
        val identitySource = track.sources.firstOrNull {
            !it.externalProviderId.isNullOrBlank() && !it.externalTrackId.isNullOrBlank()
        }
        if (identitySource != null) {
            val providerId = identitySource.externalProviderId ?: return@withContext null
            val externalId = identitySource.externalTrackId ?: return@withContext null
            val stream = sourceRegistry.resolveStream(providerId, externalId, timeoutMs)
            if (stream != null && isValidStream(stream)) {
                VantaLogger.d(
                    VantaLogger.Tag.STREAM,
                    "resolve_identity_ok provider=$providerId id=$externalId"
                )
                persistStream(track, identitySource, stream)
                return@withContext stream
            }
            VantaLogger.w(
                VantaLogger.Tag.STREAM,
                "resolve_identity_failed provider=$providerId id=$externalId — falling back to search"
            )
        }

        // Step 3: search fallback
        val query = "${track.track.title} ${track.track.artist}".trim()
        val candidates = sourceRegistry.searchAll(query, timeoutMs)
            .filter { it.status.canResolveStream() }
            .take(5)

        for (candidate in candidates) {
            val stream = sourceRegistry.resolveStream(candidate.providerId, candidate.id, timeoutMs)
                ?: continue
            if (!isValidStream(stream)) continue
            VantaLogger.d(
                VantaLogger.Tag.STREAM,
                "resolve_search_ok provider=${candidate.providerId} id=${candidate.id}"
            )
            return@withContext stream
        }

        VantaLogger.w(
            VantaLogger.Tag.STREAM,
            "resolve_failed title='${track.track.title}' artist='${track.track.artist}'"
        )
        null
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private fun isUsable(source: TrackSource): Boolean {
        if (source.streamUrl.isBlank()) return false
        if (source.streamUrl.contains("soundhelix", ignoreCase = true)) return false
        val expiresAt = source.expiresAtMs ?: return true
        return expiresAt > System.currentTimeMillis() + EXPIRY_BUFFER_MS
    }

    private fun isValidStream(stream: ResolvedStream): Boolean =
        stream.streamUrl.isNotBlank() &&
            !stream.streamUrl.contains("soundhelix", ignoreCase = true)

    private suspend fun persistStream(
        track: UnifiedTrackWithSources,
        identitySource: TrackSource,
        stream: ResolvedStream
    ) {
        try {
            trackRepository.addTrackSource(
                title = track.track.title,
                artist = track.track.artist,
                album = track.track.albumName,
                coverArtUrl = track.track.coverArtUrl,
                sourceType = SourceType.ADDON,
                streamUrl = stream.streamUrl,
                bitrate = stream.bitrateKbps,
                externalProviderId = identitySource.externalProviderId,
                externalTrackId = identitySource.externalTrackId,
                expiresAtMs = stream.expiresAt
            )
        } catch (e: Exception) {
            VantaLogger.w(VantaLogger.Tag.STREAM, "persist_stream_failed", e)
        }
    }
}
