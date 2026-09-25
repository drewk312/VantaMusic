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
import com.audiophile.musicplayer.data.source.CloudLibraryHelpers
import com.audiophile.musicplayer.data.source.canResolveStream
import com.audiophile.musicplayer.data.source.playback.CatalogPlaybackAdapter
import com.audiophile.musicplayer.data.source.playback.DirectMediaPlaybackAdapter
import com.audiophile.musicplayer.data.source.playback.FileMediaProbe
import com.audiophile.musicplayer.data.source.playback.LocalHiResPlaybackAdapter
import com.audiophile.musicplayer.data.source.playback.MediaProbe
import com.audiophile.musicplayer.data.source.playback.PlaybackSourceErrorCode
import com.audiophile.musicplayer.data.source.playback.PlaybackSourceFailure
import com.audiophile.musicplayer.data.source.playback.PlaybackSourceOutcome
import com.audiophile.musicplayer.data.source.playback.PlaybackSourceRouter
import com.audiophile.musicplayer.data.source.playback.RequestedAudioQuality
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Resolves a playable stream URL for a [UnifiedTrackWithSources].
 *
 * Resolution order:
 * 1. Local files and direct/self-hosted media (no catalog token required).
 * 2. Catalog identities (Qobuz/Tidal/Amazon/gateway).
 * 3. Search catalog providers by title+artist.
 *
 * Catalog auth failures stay structured and never block local hi-res.
 */
class StreamResolver(
    private val sourceRegistry: SourceRegistry,
    private val trackRepository: TrackRepository,
    mediaProbe: MediaProbe = FileMediaProbe,
    private val router: PlaybackSourceRouter = PlaybackSourceRouter(
        local = LocalHiResPlaybackAdapter(mediaProbe),
        direct = DirectMediaPlaybackAdapter(),
        catalog = CatalogPlaybackAdapter(sourceRegistry)
    )
) {

    suspend fun resolve(
        track: UnifiedTrackWithSources,
        timeoutMs: Long = CloudLibraryHelpers.TAP_PLAY_TOTAL_BUDGET_MS,
        excludedStreamUrls: Set<String> = emptySet(),
        excludedProviderIds: Set<String> = emptySet(),
    ): ResolvedStream? = when (val outcome = resolveWithOutcome(track, timeoutMs, excludedStreamUrls, excludedProviderIds)) {
        is PlaybackSourceOutcome.Ready -> outcome.stream
        is PlaybackSourceOutcome.Failed -> null
    }

    suspend fun resolveWithOutcome(
        track: UnifiedTrackWithSources,
        timeoutMs: Long = CloudLibraryHelpers.TAP_PLAY_TOTAL_BUDGET_MS,
        excludedStreamUrls: Set<String> = emptySet(),
        excludedProviderIds: Set<String> = emptySet(),
        requestedQuality: RequestedAudioQuality =
            SpatialDecoderCapabilities.playableQuality(
            com.audiophile.musicplayer.data.source.playback.requestedAudioQualityFromPreference(
                com.audiophile.musicplayer.data.source.external.SpotiFlacEndpoints.PREFERRED_STREAM_QUALITY
            )
            )
    ): PlaybackSourceOutcome = withContext(Dispatchers.IO) {
        val query = "${track.track.title} ${track.track.artist}".trim()
        val failures = mutableListOf<PlaybackSourceFailure>()
        val startedAtMs = System.currentTimeMillis()
        fun remainingMs(): Long = CloudLibraryHelpers.remainingTapBudgetMs(startedAtMs, timeoutMs)

        when (
            val persisted = router.resolvePersisted(
                track = track,
                requestedQuality = requestedQuality,
                excludedStreamUrls = excludedStreamUrls,
                excludedProviderIds = excludedProviderIds
            )
        ) {
            is PlaybackSourceOutcome.Ready -> {
                if (isValidStream(persisted.stream, excludedStreamUrls, requestedQuality)) {
                    VantaLogger.d(
                        VantaLogger.Tag.STREAM,
                        "resolve_ready provider=${persisted.stream.providerId} " +
                            "label=${persisted.stream.sourceLabel} " +
                            "codec=${persisted.stream.codec} bitDepth=${persisted.stream.bitDepth} " +
                            "sampleRate=${persisted.stream.sampleRateHz} shortfall=${persisted.qualityShortfall}"
                    )
                    persistResolvedIfCatalog(track, persisted.stream)
                    return@withContext persisted
                }
                VantaLogger.w(
                    VantaLogger.Tag.STREAM,
                    "resolve_skipped_excluded_stream provider=${persisted.stream.providerId} " +
                        "host=${VantaLogger.urlHost(persisted.stream.streamUrl)}"
                )
            }
            is PlaybackSourceOutcome.Failed -> {
                if (persisted.failure.code != PlaybackSourceErrorCode.NOT_FOUND) {
                    failures += persisted.failure
                }
            }
        }

        val identitySources = track.sources
            .filter {
                !it.externalProviderId.isNullOrBlank() &&
                    !it.externalTrackId.isNullOrBlank() &&
                    it.externalProviderId !in excludedProviderIds
            }
            .sortedWith(
                compareByDescending<TrackSource> { playbackSourceQualityRank(it, requestedQuality) }
                    .thenBy { it.sourceId }
            )
        val identitySource = identitySources.firstOrNull {
            !SourceIdentityGate.isSupplementalPlaybackProvider(it.externalProviderId)
        } ?: identitySources.firstOrNull()

        val searchBudgetMs = remainingMs().coerceAtMost(CloudLibraryHelpers.TAP_PLAY_SEARCH_TIMEOUT_MS)
        if (searchBudgetMs < 500L) {
            VantaLogger.w(
                VantaLogger.Tag.STREAM,
                "resolve_search_skipped_budget leftoverMs=${remainingMs()} title='${track.track.title}'"
            )
        }
        val catalogCandidates = if (searchBudgetMs < 500L) {
            emptyList()
        } else {
            sourceRegistry.searchAll(
                query,
                timeoutMs = searchBudgetMs,
                includeSupplemental = false
            ).filter { it.status.canResolveStream() }
        }
        val candidates = rankFallbackStreamCandidates(
            track = track,
            candidates = catalogCandidates,
            requestedQuality = requestedQuality
        )
            .filter { candidate -> candidate.providerId !in excludedProviderIds }
            .take(3)

        for (candidate in candidates) {
            val resolveBudgetMs = remainingMs()
            if (resolveBudgetMs < 500L) break
            val outcome = sourceRegistry.resolvePlayback(
                providerId = candidate.providerId,
                trackId = candidate.id,
                timeoutMs = resolveBudgetMs,
                requestedQuality = requestedQuality
            )
            when (outcome) {
                is PlaybackSourceOutcome.Ready -> {
                    if (!isValidStream(outcome.stream, excludedStreamUrls, requestedQuality)) continue
                    if (identitySource != null &&
                        !identitySource.externalProviderId.equals(candidate.providerId, ignoreCase = true)
                    ) {
                        VantaLogger.w(
                            VantaLogger.Tag.STREAM,
                            "resolve_search_identity_diverged persisted=${identitySource.externalProviderId}:${identitySource.externalTrackId} actual=${candidate.providerId}:${candidate.id} title='${track.track.title}'"
                        )
                    }
                    VantaLogger.d(
                        VantaLogger.Tag.STREAM,
                        "resolve_search_ok provider=${candidate.providerId} id=${candidate.id}"
                    )
                    val persistIdentity = persistIdentityForResolvedCandidate(
                        candidateProviderId = candidate.providerId,
                        candidateTrackId = candidate.id
                    )
                    persistStream(
                        track = track,
                        providerId = persistIdentity.first,
                        externalTrackId = persistIdentity.second,
                        stream = outcome.stream
                    )
                    return@withContext outcome
                }
                is PlaybackSourceOutcome.Failed -> failures += outcome.failure
            }
        }

        val supplementalBudgetMs = remainingMs()
        val supplementalIdentities = identitySources.filter {
            SourceIdentityGate.isSupplementalPlaybackProvider(it.externalProviderId)
        }
        when (
            val supplemental = if (supplementalBudgetMs < 500L) {
                null
            } else {
                resolveFromIdentitySources(
                    track = track,
                    sources = supplementalIdentities,
                    timeoutMs = supplementalBudgetMs,
                    excludedStreamUrls = excludedStreamUrls,
                    requestedQuality = requestedQuality
                )
            }
        ) {
            is PlaybackSourceOutcome.Ready -> return@withContext supplemental
            is PlaybackSourceOutcome.Failed -> {
                if (supplemental.failure.code != PlaybackSourceErrorCode.NOT_FOUND) {
                    failures += supplemental.failure
                }
            }
            null -> Unit
        }

        VantaLogger.w(
            VantaLogger.Tag.STREAM,
            "resolve_failed title='${track.track.title}' artist='${track.track.artist}' " +
                "codes=${failures.map { it.code }.distinct()}"
        )
        PlaybackSourceOutcome.Failed(
            PlaybackSourceRouter.collapseFailures(
                failures = failures,
                triedLocalOrDirect = track.sources.any {
                    it.sourceType == SourceType.LOCAL || PlaybackSourceRouter.isDirectCandidate(it)
                }
            )
        )
    }

    private suspend fun resolveFromIdentitySources(
        track: UnifiedTrackWithSources,
        sources: List<TrackSource>,
        timeoutMs: Long,
        excludedStreamUrls: Set<String>,
        requestedQuality: RequestedAudioQuality
    ): PlaybackSourceOutcome? {
        val failures = mutableListOf<PlaybackSourceFailure>()
        for (identitySource in sources) {
            val providerId = identitySource.externalProviderId ?: continue
            val externalId = identitySource.externalTrackId ?: continue
            when (
                val outcome = sourceRegistry.resolvePlayback(
                    providerId = providerId,
                    trackId = externalId,
                    timeoutMs = timeoutMs,
                    requestedQuality = requestedQuality
                )
            ) {
                is PlaybackSourceOutcome.Ready -> {
                    if (isValidStream(outcome.stream, excludedStreamUrls, requestedQuality)) {
                        VantaLogger.d(
                            VantaLogger.Tag.STREAM,
                            "resolve_identity_ok provider=$providerId id=$externalId route=direct"
                        )
                        persistStream(
                            track = track,
                            providerId = identitySource.externalProviderId,
                            externalTrackId = identitySource.externalTrackId,
                            stream = outcome.stream
                        )
                        return outcome
                    }
                }
                is PlaybackSourceOutcome.Failed -> {
                    failures += outcome.failure
                    VantaLogger.w(
                        VantaLogger.Tag.STREAM,
                        "resolve_identity_failed provider=$providerId id=$externalId code=${outcome.failure.code}"
                    )
                }
            }
        }
        if (failures.isEmpty()) return null
        return PlaybackSourceOutcome.Failed(
            PlaybackSourceRouter.collapseFailures(failures, triedLocalOrDirect = false)
        )
    }

    private fun isValidStream(
        stream: ResolvedStream,
        excludedStreamUrls: Set<String>,
        requestedQuality: RequestedAudioQuality
    ): Boolean =
        stream.streamUrl.isNotBlank() &&
            (!(requestedQuality == RequestedAudioQuality.LOSSLESS_16 || requestedQuality == RequestedAudioQuality.HI_RES_24) ||
                (!stream.isDolbyAtmos && !stream.isEclipsaAudio && !stream.isSpatialAudio)) &&
            SpatialDecoderCapabilities.supportsAtmosStream(stream) &&
            !stream.streamUrl.contains("soundhelix", ignoreCase = true) &&
            !CloudLibraryHelpers.isSampleOrPreviewUrl(stream.streamUrl) &&
            stream.streamUrl !in excludedStreamUrls

    private suspend fun persistResolvedIfCatalog(
        track: UnifiedTrackWithSources,
        stream: ResolvedStream
    ) {
        val providerId = stream.providerId?.lowercase().orEmpty()
        if (providerId == "local" || providerId == "direct") return
        val identity = track.sources.firstOrNull { source ->
            source.externalProviderId.equals(stream.providerId, ignoreCase = true) &&
                !source.externalTrackId.isNullOrBlank()
        } ?: return
        persistStream(
            track = track,
            providerId = identity.externalProviderId,
            externalTrackId = identity.externalTrackId,
            stream = stream
        )
    }

    private suspend fun persistStream(
        track: UnifiedTrackWithSources,
        providerId: String?,
        externalTrackId: String?,
        stream: ResolvedStream
    ) {
        if (providerId.isNullOrBlank() || externalTrackId.isNullOrBlank()) return
        try {
            trackRepository.addTrackSource(
                title = track.track.title,
                artist = track.track.artist,
                album = track.track.albumName,
                coverArtUrl = track.track.coverArtUrl,
                sourceType = SourceType.ADDON,
                streamUrl = stream.streamUrl,
                bitrate = stream.bitrateKbps,
                externalProviderId = providerId,
                externalTrackId = externalTrackId,
                expiresAtMs = stream.expiresAt
            )
        } catch (e: java.io.IOException) {
            VantaLogger.w(VantaLogger.Tag.STREAM, "persist_stream_failed", e)
        } catch (e: android.database.SQLException) {
            VantaLogger.w(VantaLogger.Tag.STREAM, "persist_stream_failed", e)
        } catch (e: IllegalStateException) {
            VantaLogger.w(VantaLogger.Tag.STREAM, "persist_stream_failed", e)
        } catch (e: IllegalArgumentException) {
            VantaLogger.w(VantaLogger.Tag.STREAM, "persist_stream_failed", e)
        }
    }
}

/** Persist the stream that actually resolved, never the failed catalog identity. */
internal fun persistIdentityForResolvedCandidate(
    candidateProviderId: String,
    candidateTrackId: String
): Pair<String, String> = candidateProviderId to candidateTrackId

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
    candidates: List<SourceSearchResult>,
    requestedQuality: RequestedAudioQuality = com.audiophile.musicplayer.data.source.playback.requestedAudioQualityFromPreference(
        com.audiophile.musicplayer.data.source.external.SpotiFlacEndpoints.PREFERRED_STREAM_QUALITY
    )
): List<SourceSearchResult> {
    val query = "${track.track.title} ${track.track.artist}".trim()
    val identity = SelectedRecordingIdentity(
        title = track.track.title,
        artist = track.track.artist,
        durationMs = track.track.durationMs,
        isrc = track.track.isrc,
        userQuery = query
    )
    val isSpatial = requestedQuality == RequestedAudioQuality.ATMOS ||
        requestedQuality == RequestedAudioQuality.SONY_360 ||
        requestedQuality == RequestedAudioQuality.AUTO_SPATIAL ||
        requestedQuality == RequestedAudioQuality.IAMF

    return SourceCandidateRanker.rankSearchResults(identity, candidates)
        .sortedWith(
            compareByDescending<SourceSearchResult> { candidate ->
                if (isSpatial && SourceIdentityGate.isImmersivePlaybackProvider(candidate.providerId)) 1000 else 0
            }
        )
        .partition { !SourceIdentityGate.isSupplementalPlaybackProvider(it.providerId) }
        .let { (catalog, supplemental) -> catalog + supplemental }
}

internal fun playbackSourceQualityRank(
    source: TrackSource,
    requestedQuality: RequestedAudioQuality = com.audiophile.musicplayer.data.source.playback.requestedAudioQualityFromPreference(
        com.audiophile.musicplayer.data.source.external.SpotiFlacEndpoints.PREFERRED_STREAM_QUALITY
    )
): Int {
    val bitrate = source.bitrate.takeIf { it > 0 } ?: 0
    val providerId = source.externalProviderId?.lowercase().orEmpty()
    val isSpatialPreferred = requestedQuality == RequestedAudioQuality.ATMOS ||
        requestedQuality == RequestedAudioQuality.SONY_360 ||
        requestedQuality == RequestedAudioQuality.AUTO_SPATIAL ||
        requestedQuality == RequestedAudioQuality.IAMF
    val providerBonus = when {
        isSpatialPreferred && "tidal" in providerId -> 160 // #1 PRIORITY FOR DOLBY ATMOS & SONY 360
        isSpatialPreferred && "amazon" in providerId -> 140 // #1 PRIORITY FOR DOLBY ATMOS & SONY 360
        "qobuz" in providerId -> 80
        "tidal" in providerId -> 70
        "deezer" in providerId -> 40
        "amazon" in providerId -> 35
        "apple" in providerId -> 20
        "youtube" in providerId -> 0
        else -> SourceIdentityGate.playbackProviderRank(source.externalProviderId, isSpatialPreferred) / 5
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
