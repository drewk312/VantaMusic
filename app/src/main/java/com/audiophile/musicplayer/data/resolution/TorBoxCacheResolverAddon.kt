package com.audiophile.musicplayer.data.resolution

import com.audiophile.musicplayer.data.local.entities.AddonProvider

/**
 * TorBox-specific cache addon.
 *
 * This class is intentionally structured around a small client interface so you
 * can plug in a real server-backed resolver without rewriting the cache layer.
 */
class TorBoxCacheResolverAddon(
    override val provider: AddonProvider,
    private val client: TorBoxMetadataResolverClient,
    private val defaultTtlMs: Long = 24 * 60 * 60 * 1000L
) : ResolverAddon {

    override suspend fun resolve(query: TrackLookupQuery): ProviderResolutionRecord? {
        val match = client.resolve(query) ?: return null
        return ProviderResolutionRecord(
            providerId = provider.providerId,
            providerTrackId = match.providerTrackId,
            providerAlbumId = match.providerAlbumId,
            displayTitle = match.displayTitle,
            displayArtist = match.displayArtist,
            displayAlbum = match.displayAlbum,
            availabilityStatus = match.availabilityStatus,
            qualityLabel = match.qualityLabel,
            bitrateKbps = match.bitrateKbps,
            confidenceScore = match.confidenceScore,
            ttlMs = defaultTtlMs,
            linkedTrackId = match.linkedTrackId,
            payloadJson = match.payloadJson,
            streamEndpointHint = match.streamEndpointHint
        )
    }
}

interface TorBoxMetadataResolverClient {
    suspend fun resolve(query: TrackLookupQuery): TorBoxMetadataMatch?
}

data class TorBoxMetadataMatch(
    val providerTrackId: String,
    val providerAlbumId: String? = null,
    val displayTitle: String,
    val displayArtist: String,
    val displayAlbum: String? = null,
    val availabilityStatus: String = "available",
    val qualityLabel: String? = "FLAC",
    val bitrateKbps: Int? = 1411,
    val confidenceScore: Float,
    val linkedTrackId: Long? = null,
    val payloadJson: String? = null,
    val streamEndpointHint: String? = null
)
