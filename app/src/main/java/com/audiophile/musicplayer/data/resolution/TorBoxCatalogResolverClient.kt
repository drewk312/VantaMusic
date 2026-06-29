package com.audiophile.musicplayer.data.resolution

import com.audiophile.musicplayer.data.remote.CatalogResolverApi
import kotlin.math.abs

/**
 * Adapter for your own TorBox-backed resolver service.
 *
 * The resolver backend is expected to search cached metadata by title/artist/ISRC
 * and return stable metadata plus a hint the app can later use for playback.
 */
class TorBoxCatalogResolverClient(
    private val api: CatalogResolverApi
) : TorBoxMetadataResolverClient {

    override suspend fun resolve(query: TrackLookupQuery): TorBoxMetadataMatch? {
        val response = api.searchTrack(
            title = query.title,
            artist = query.artist,
            album = query.album,
            isrc = query.normalizedIsrc
        )

        val best = response.results
            .maxByOrNull { score(query, it) }
            ?: return null

        return TorBoxMetadataMatch(
            providerTrackId = best.trackId,
            providerAlbumId = best.albumId,
            displayTitle = best.title,
            displayArtist = best.artist,
            displayAlbum = best.album,
            availabilityStatus = best.availabilityStatus,
            qualityLabel = best.qualityLabel,
            bitrateKbps = best.bitrateKbps,
            confidenceScore = score(query, best),
            payloadJson = best.payloadJson,
            streamEndpointHint = best.streamEndpointHint
        )
    }

    private fun score(
        query: TrackLookupQuery,
        result: com.audiophile.musicplayer.data.remote.CatalogSearchResult
    ): Float {
        var score = 0f

        if (query.normalizedIsrc != null && query.normalizedIsrc == MetadataNormalizer.normalizeIsrc(result.isrc)) {
            score += 10f
        }
        if (query.normalizedTitle == MetadataNormalizer.normalizeTitle(result.title)) {
            score += 4f
        }
        if (query.normalizedArtist == MetadataNormalizer.normalizeArtist(result.artist)) {
            score += 4f
        }
        if (query.normalizedAlbum.isNotBlank() && query.normalizedAlbum == MetadataNormalizer.normalizeAlbum(result.album)) {
            score += 1.5f
        }

        if (query.durationMs != null && result.durationMs != null) {
            val delta = abs(query.durationMs - result.durationMs)
            score += when {
                delta <= 2_000 -> 2f
                delta <= 10_000 -> 1f
                else -> -0.5f
            }
        }

        if (result.availabilityStatus == "available") {
            score += 1f
        }
        score += ((result.bitrateKbps ?: 0) / 1000f)

        return score
    }
}
