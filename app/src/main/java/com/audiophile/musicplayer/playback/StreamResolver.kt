package com.audiophile.musicplayer.playback

import com.audiophile.musicplayer.common.VantaLogger
import com.audiophile.musicplayer.data.local.entities.SourceType
import com.audiophile.musicplayer.data.local.entities.TrackSource
import com.audiophile.musicplayer.data.local.entities.UnifiedTrackWithSources
import com.audiophile.musicplayer.data.repository.TrackRepository
import com.audiophile.musicplayer.data.source.SourceRegistry
import com.audiophile.musicplayer.data.source.ResolvedStream
import com.audiophile.musicplayer.data.source.SelectedRecordingIdentity
import com.audiophile.musicplayer.data.source.SourceCandidateRanker
import com.audiophile.musicplayer.data.source.SourceSearchResult
import com.audiophile.musicplayer.data.source.SourceIdentityGate
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
        val query = "${track.track.title} ${track.track.artist}".trim()
        val selectedIdentity = SelectedRecordingIdentity(
            title = track.track.title,
            artist = track.track.artist,
            durationMs = track.track.durationMs,
            isrc = track.track.isrc,
            userQuery = query
        )
        // Search once up front. The result validates persisted provider IDs and
        // is reused by fallback resolution, keeping one source of identity truth.
        val catalogCandidates = sourceRegistry.searchAll(query, timeoutMs)
            .filter { it.status.canResolveStream() }

        // Step 1: re-resolve via the persisted catalog identity.  A cached CDN
        // URL only proves that a URL once existed; it does not prove it still
        // serves the selected recording.  Resolving the provider+track id first
        // prevents an old/misrouted cached URL from silently playing another song.
        val identitySource = track.sources
            .filter {
            !it.externalProviderId.isNullOrBlank() && !it.externalTrackId.isNullOrBlank()
            }
            .maxWithOrNull(compareBy<TrackSource> { playbackSourceQualityRank(it) }.thenBy { it.sourceId })
        if (identitySource != null) {
            val providerId = identitySource.externalProviderId ?: return@withContext null
            val externalId = identitySource.externalTrackId ?: return@withContext null
            val verifiedCandidate = findVerifiedIdentityCandidate(
                selected = selectedIdentity.copy(
                    preferredProviderId = providerId,
                    preferredExternalTrackId = externalId
                ),
                providerId = providerId,
                externalId = externalId,
                candidates = catalogCandidates
            )
            val stream = verifiedCandidate?.let {
                sourceRegistry.resolveStream(it.providerId, it.id, timeoutMs)
            }
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
                "resolve_identity_failed provider=$providerId id=$externalId — falling back to cached URL/search"
            )
        }

        // Step 2: use a cached URL only when its catalog identity can no longer
        // be resolved. Local files are still preferred by their quality rank.
        val existing = track.sources
            .filter { isUsable(it) && it.sourceType == SourceType.LOCAL }
            .maxWithOrNull(compareBy<TrackSource> { playbackSourceQualityRank(it) }.thenBy { it.sourceId })
        if (existing != null && existing.streamUrl.isNotBlank()) {
            VantaLogger.d(
                VantaLogger.Tag.STREAM,
                "resolve_local_fallback trackId=${track.track.trackId} bitrate=${existing.bitrate} url=${existing.streamUrl.take(60)}"
            )
            return@withContext ResolvedStream(
                streamUrl = existing.streamUrl,
                bitrateKbps = existing.bitrate ?: 0,
                expiresAt = existing.expiresAtMs,
                providerId = existing.externalProviderId ?: existing.sourceType.name.lowercase()
            )
        }

        // Step 3: search fallback
        val candidates = rankFallbackStreamCandidates(
            track = track,
            candidates = catalogCandidates
        )
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

internal fun findVerifiedIdentityCandidate(
    selected: SelectedRecordingIdentity,
    providerId: String,
    externalId: String,
    candidates: List<SourceSearchResult>
): SourceSearchResult? {
    val expectedId = externalId.substringAfterLast(':')
    return candidates.firstOrNull { candidate ->
        val providerMatches = candidate.providerId.equals(providerId, ignoreCase = true)
        val idMatches = candidate.id == externalId || candidate.id.substringAfterLast(':') == expectedId
        providerMatches && idMatches && SourceIdentityGate.evaluateSearchResult(selected, candidate).accepted
    }
}

internal fun rankFallbackStreamCandidates(
    track: UnifiedTrackWithSources,
    candidates: List<SourceSearchResult>
): List<SourceSearchResult> {
    val query = "${track.track.title} ${track.track.artist}".trim()
    val identity = SelectedRecordingIdentity(
        title = track.track.title,
        artist = track.track.artist,
        durationMs = track.track.durationMs,
        isrc = track.track.isrc,
        userQuery = query
    )
    return SourceCandidateRanker.rankSearchResults(identity, candidates)
}

internal fun playbackSourceQualityRank(source: TrackSource): Int {
    val bitrate = source.bitrate.takeIf { it > 0 } ?: 0
    val providerBonus = when (source.externalProviderId?.lowercase()) {
        "qobuz" -> 80
        "tidal" -> 70
        "deezer" -> 40
        "amazon" -> 35
        "apple" -> 20
        else -> 0
    }
    val typeBonus = when (source.sourceType) {
        SourceType.LOCAL -> 100
        SourceType.TORBOX -> 90
        SourceType.ADDON -> 50
        SourceType.YOUTUBE_MUSIC -> 10
        SourceType.SPOTIFY,
        SourceType.APPLE_MUSIC -> 0
    }
    return bitrate + providerBonus + typeBonus
}
