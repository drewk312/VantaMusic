package com.audiophile.musicplayer.data.resolution

import com.audiophile.musicplayer.data.local.entities.AddonProvider
import com.audiophile.musicplayer.data.remote.CatalogResolverApi
import kotlin.math.abs

class CommunityInstanceResolverAddon(
    override val provider: AddonProvider,
    private val api: CatalogResolverApi,
    private val defaultTtlMs: Long = 6 * 60 * 60 * 1000L
) : ResolverAddon {

    override suspend fun resolve(query: TrackLookupQuery): ProviderResolutionRecord? {
        val response = api.searchTrack(
            title = query.title,
            artist = query.artist,
            album = query.album,
            isrc = query.normalizedIsrc
        )

        val best = response.results
            .maxByOrNull { scoreResult(query, it) }
            ?: return null

        return ProviderResolutionRecord(
            providerId = provider.providerId,
            providerTrackId = best.trackId,
            providerAlbumId = best.albumId,
            displayTitle = best.title,
            displayArtist = best.artist,
            displayAlbum = best.album,
            availabilityStatus = best.availabilityStatus,
            qualityLabel = best.qualityLabel,
            bitrateKbps = best.bitrateKbps,
            confidenceScore = scoreResult(query, best),
            ttlMs = defaultTtlMs,
            streamEndpointHint = best.streamEndpointHint,
            payloadJson = best.payloadJson
        )
    }

    private fun scoreResult(
        query: TrackLookupQuery,
        result: com.audiophile.musicplayer.data.remote.CatalogSearchResult
    ): Float {
        var score = 0f

        val resultTitle = MetadataNormalizer.normalizeTitle(result.title)
        val resultArtist = MetadataNormalizer.normalizeArtist(result.artist)
        val resultAlbum = MetadataNormalizer.normalizeAlbum(result.album)
        val resultIsrc = MetadataNormalizer.normalizeIsrc(result.isrc)

        if (query.normalizedIsrc != null && query.normalizedIsrc == resultIsrc) {
            score += 10f
        }
        if (query.normalizedTitle == resultTitle) {
            score += 4f
        }
        if (query.normalizedArtist == resultArtist) {
            score += 4f
        }
        if (query.normalizedAlbum.isNotBlank() && query.normalizedAlbum == resultAlbum) {
            score += 1.5f
        }

        val durationDelta = if (query.durationMs != null && result.durationMs != null) {
            abs(query.durationMs - result.durationMs)
        } else {
            null
        }

        durationDelta?.let { delta ->
            score += when {
                delta <= 2_000 -> 3f
                delta <= 10_000 -> 1.5f
                delta <= 20_000 -> 0.5f
                else -> -1f
            }
        }

        if (result.availabilityStatus == "available") {
            score += 1f
        }
        score += (result.bitrateKbps ?: 0) / 1000f

        return score
    }
}
