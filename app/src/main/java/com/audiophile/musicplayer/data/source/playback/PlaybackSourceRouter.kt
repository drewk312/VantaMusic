package com.audiophile.musicplayer.data.source.playback

import com.audiophile.musicplayer.common.VantaLogger
import com.audiophile.musicplayer.data.local.entities.SourceType
import com.audiophile.musicplayer.data.local.entities.TrackSource
import com.audiophile.musicplayer.data.local.entities.UnifiedTrackWithSources
import com.audiophile.musicplayer.data.source.SourceIdentityGate
import com.audiophile.musicplayer.data.source.SourceRegistry
import com.audiophile.musicplayer.playback.PlaybackPolicies

/**
 * Tries local and direct media before any catalog adapter. A missing Tidal
 * or Amazon token cannot block a file the user already has.
 */
class PlaybackSourceRouter(
    private val local: PlaybackSourceAdapter,
    private val direct: PlaybackSourceAdapter,
    private val catalog: PlaybackSourceAdapter
) {
    suspend fun resolvePersisted(
        track: UnifiedTrackWithSources,
        requestedQuality: RequestedAudioQuality = RequestedAudioQuality.HI_RES_24,
        excludedStreamUrls: Set<String> = emptySet(),
        excludedProviderIds: Set<String> = emptySet()
    ): PlaybackSourceOutcome {
        val failures = mutableListOf<PlaybackSourceFailure>()
        var triedLocalOrDirect = false
        val isSpatialRequested = requestedQuality == RequestedAudioQuality.ATMOS ||
            requestedQuality == RequestedAudioQuality.SONY_360 ||
            requestedQuality == RequestedAudioQuality.AUTO_SPATIAL ||
            requestedQuality == RequestedAudioQuality.IAMF

        var stereoFallbackOutcome: PlaybackSourceOutcome.Ready? = null

        val localSources = track.sources.filter { source ->
            source.sourceType == SourceType.LOCAL &&
                source.streamUrl.isNotBlank() &&
                source.streamUrl !in excludedStreamUrls
        }
        for (source in localSources) {
            triedLocalOrDirect = true
            when (val outcome = local.resolve(requestFor(track, source, requestedQuality, localUri = source.streamUrl))) {
                is PlaybackSourceOutcome.Ready -> {
                    if (!isSpatialRequested || outcome.stream.isDolbyAtmos || outcome.stream.isSpatialAudio || outcome.stream.isSurround) {
                        return outcome
                    }
                    if (stereoFallbackOutcome == null) stereoFallbackOutcome = outcome
                }
                is PlaybackSourceOutcome.Failed -> failures += outcome.failure
            }
        }

        val directSources = track.sources.filter { source ->
            source.sourceType != SourceType.LOCAL &&
                source.streamUrl.isNotBlank() &&
                source.streamUrl !in excludedStreamUrls &&
                isDirectCandidate(source)
        }
        for (source in directSources) {
            if (!isReusablePersistedStream(source)) {
                VantaLogger.d(
                    VantaLogger.Tag.STREAM,
                    "persisted_direct_skip_reresolve type=${source.sourceType} " +
                        "host=${VantaLogger.urlHost(source.streamUrl)} " +
                        "expires=${source.expiresAtMs} title='${track.track.title}'"
                )
                continue
            }
            triedLocalOrDirect = true
            when (val outcome = direct.resolve(requestFor(track, source, requestedQuality, directUrl = source.streamUrl))) {
                is PlaybackSourceOutcome.Ready -> {
                    if (!isSpatialRequested || outcome.stream.isDolbyAtmos || outcome.stream.isSpatialAudio || outcome.stream.isSurround) {
                        return outcome
                    }
                    if (stereoFallbackOutcome == null) stereoFallbackOutcome = outcome
                }
                is PlaybackSourceOutcome.Failed -> failures += outcome.failure
            }
        }

        val catalogSources = track.sources.filter { source ->
            !source.externalProviderId.isNullOrBlank() &&
                !source.externalTrackId.isNullOrBlank() &&
                source.externalProviderId !in excludedProviderIds &&
                !SourceIdentityGate.isSupplementalPlaybackProvider(source.externalProviderId)
        }.sortedWith(
            compareByDescending<TrackSource> { source ->
                if (isSpatialRequested && SourceIdentityGate.isImmersivePlaybackProvider(source.externalProviderId)) 100 else 0
            }
        )

        for (source in catalogSources) {
            val request = requestFor(
                track = track,
                source = source,
                requestedQuality = requestedQuality,
                catalogProviderId = source.externalProviderId,
                catalogExternalId = source.externalTrackId
            )
            when (val outcome = catalog.resolve(request)) {
                is PlaybackSourceOutcome.Ready -> {
                    if (!isSpatialRequested || outcome.stream.isDolbyAtmos || outcome.stream.isSpatialAudio || outcome.stream.isSurround) {
                        return outcome
                    }
                    if (stereoFallbackOutcome == null) stereoFallbackOutcome = outcome
                }
                is PlaybackSourceOutcome.Failed -> failures += outcome.failure
            }
        }

        if (stereoFallbackOutcome != null) {
            return stereoFallbackOutcome
        }

        return PlaybackSourceOutcome.Failed(collapseFailures(failures, triedLocalOrDirect))
    }

    companion object {
        /**
         * A persisted stream URL is reusable only when it is NOT hosted on a
         * known short-TTL / single-use CDN. Akamai (Qobuz), CloudFront, Tidal,
         * Deezer and Spotify CDN URLs are consumed on first playback and answer
         * 401/403 on replay from the database, so they must always be
         * re-resolved fresh. Hosts without an inferred TTL fall back to the
         * explicit expiry timestamp.
         */
        private fun isReusablePersistedStream(source: TrackSource): Boolean {
            val url = source.streamUrl
            val isTorbox = source.sourceType == SourceType.TORBOX
            if (!isTorbox && !PlaybackStreamNormalizer.looksLikeDirectMediaUrl(url)) return false
            if (SourceRegistry.inferTtlFromHost(url) != null) return false
            return !PlaybackPolicies.isStreamExpired(source.expiresAtMs, url)
        }

        internal fun isDirectCandidate(source: TrackSource): Boolean {
            if (source.sourceType == SourceType.TORBOX) {
                return PlaybackStreamNormalizer.looksLikeDirectMediaUrl(source.streamUrl) ||
                    source.streamUrl.startsWith("http", ignoreCase = true)
            }
            return PlaybackStreamNormalizer.looksLikeDirectMediaUrl(source.streamUrl)
        }

        internal fun collapseFailures(
            failures: List<PlaybackSourceFailure>,
            triedLocalOrDirect: Boolean
        ): PlaybackSourceFailure {
            if (failures.isEmpty()) {
                return PlaybackSourceFailure.of(
                    code = PlaybackSourceErrorCode.NOT_FOUND,
                    sourceLabel = "Playback"
                )
            }
            val considered = if (triedLocalOrDirect) {
                val withoutAuth = failures.filter { it.code != PlaybackSourceErrorCode.AUTH_REQUIRED }
                if (withoutAuth.isNotEmpty()) withoutAuth else failures
            } else {
                failures
            }
            val ranked = considered.minBy { failure ->
                when (failure.code) {
                    PlaybackSourceErrorCode.ATMOS_UNAVAILABLE -> 0
                    PlaybackSourceErrorCode.QUALITY_UNAVAILABLE -> 1
                    PlaybackSourceErrorCode.AUTH_REQUIRED -> 2
                    PlaybackSourceErrorCode.SOURCE_OFFLINE -> 3
                    PlaybackSourceErrorCode.UNSUPPORTED -> 4
                    PlaybackSourceErrorCode.NOT_FOUND -> 5
                }
            }
            if (triedLocalOrDirect &&
                failures.any { it.code == PlaybackSourceErrorCode.AUTH_REQUIRED } &&
                ranked.code != PlaybackSourceErrorCode.AUTH_REQUIRED
            ) {
                return ranked.copy(
                    message = "${ranked.message} Another source requires authentication and was skipped."
                )
            }
            return ranked
        }

        private fun requestFor(
            track: UnifiedTrackWithSources,
            source: TrackSource,
            requestedQuality: RequestedAudioQuality,
            localUri: String? = null,
            directUrl: String? = null,
            catalogProviderId: String? = null,
            catalogExternalId: String? = null
        ) = PlaybackSourceRequest(
            title = track.track.title,
            artist = track.track.artist,
            localUri = localUri,
            directUrl = directUrl,
            catalogProviderId = catalogProviderId,
            catalogExternalId = catalogExternalId,
            requestedQuality = requestedQuality,
            bitrateKbps = source.bitrate,
            expiresAtMs = source.expiresAtMs
        )
    }
}
