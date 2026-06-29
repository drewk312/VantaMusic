package com.audiophile.musicplayer.data.local.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Cache entry for metadata resolution. This intentionally stores match and
 * availability data, not protected stream tokens.
 */
@Entity(
    tableName = "resolution_cache_entries",
    indices = [
        Index(value = ["queryKey"]),
        Index(value = ["isrc"]),
        Index(value = ["providerId"]),
        Index(value = ["queryKey", "providerId", "providerTrackId"], unique = true)
    ]
)
data class ResolutionCacheEntry(
    @PrimaryKey(autoGenerate = true)
    val cacheEntryId: Long = 0,
    val queryKey: String,
    val normalizedTitle: String,
    val normalizedArtist: String,
    val normalizedAlbum: String? = null,
    val isrc: String? = null,
    val durationMs: Long? = null,
    val linkedTrackId: Long? = null,
    val providerId: String,
    val providerTrackId: String,
    val providerAlbumId: String? = null,
    val displayTitle: String,
    val displayArtist: String,
    val displayAlbum: String? = null,
    val availabilityStatus: String = "unknown",
    val qualityLabel: String? = null,
    val bitrateKbps: Int? = null,
    val confidenceScore: Float = 0f,
    val negativeResult: Boolean = false,
    val streamEndpointHint: String? = null,
    val payloadJson: String? = null,
    val resolvedAtEpochMs: Long,
    val expiresAtEpochMs: Long,
    val lastHitAtEpochMs: Long = resolvedAtEpochMs,
    val hitCount: Int = 0
)
