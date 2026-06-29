package com.audiophile.musicplayer.data.remote

import retrofit2.http.GET
import retrofit2.http.Query

/**
 * Generic metadata search interface for community instances or your own server.
 *
 * Expected semantics:
 * - search by title/artist and optionally ISRC
 * - return metadata match candidates, not protected stream blobs
 */
interface CatalogResolverApi {
    @GET("search")
    suspend fun searchTrack(
        @Query("title") title: String,
        @Query("artist") artist: String,
        @Query("album") album: String? = null,
        @Query("isrc") isrc: String? = null
    ): CatalogSearchResponse
}

data class CatalogSearchResponse(
    val results: List<CatalogSearchResult> = emptyList()
)

data class CatalogSearchResult(
    val trackId: String,
    val albumId: String? = null,
    val title: String,
    val artist: String,
    val album: String? = null,
    val isrc: String? = null,
    val durationMs: Long? = null,
    val availabilityStatus: String = "available",
    val qualityLabel: String? = null,
    val bitrateKbps: Int? = null,
    val streamEndpointHint: String? = null,
    val payloadJson: String? = null
)
