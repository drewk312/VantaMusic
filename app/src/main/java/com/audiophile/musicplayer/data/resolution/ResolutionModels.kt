package com.audiophile.musicplayer.data.resolution

import com.audiophile.musicplayer.data.local.entities.AddonProvider
import com.audiophile.musicplayer.data.local.entities.ResolutionCacheEntry

data class ResolutionRequest(
    val title: String,
    val artist: String,
    val album: String? = null,
    val isrc: String? = null,
    val durationMs: Long? = null
)

data class TrackLookupQuery(
    val title: String,
    val artist: String,
    val album: String?,
    val isrc: String?,
    val durationMs: Long?
) {
    val normalizedIsrc: String? get() = MetadataNormalizer.normalizeIsrc(isrc)
    val normalizedTitle: String get() = MetadataNormalizer.normalizeTitle(title)
    val normalizedArtist: String get() = MetadataNormalizer.normalizeArtist(artist)
    val normalizedAlbum: String get() = MetadataNormalizer.normalizeAlbum(album)
    val queryKey: String get() = MetadataNormalizer.buildQueryKey(title, artist, album, isrc)
}

data class ProviderResolutionRecord(
    val providerId: String,
    val providerTrackId: String,
    val providerAlbumId: String? = null,
    val displayTitle: String,
    val displayArtist: String,
    val displayAlbum: String? = null,
    val availabilityStatus: String = "available",
    val qualityLabel: String? = null,
    val bitrateKbps: Int? = null,
    val confidenceScore: Float = 0f,
    val ttlMs: Long = 6 * 60 * 60 * 1000L,
    val linkedTrackId: Long? = null,
    val negativeResult: Boolean = false,
    val streamEndpointHint: String? = null,
    val payloadJson: String? = null
)

data class ScoredCacheCandidate(
    val entry: ResolutionCacheEntry,
    val provider: AddonProvider?,
    val score: Int
)
