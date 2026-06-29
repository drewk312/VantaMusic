package com.audiophile.musicplayer.data.remote

import com.google.gson.JsonElement
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Query

interface TorBoxApi {
    @GET("api/torrents/mylist")
    suspend fun getMyList(
        @Header("Authorization") bearerToken: String,
        @Query("bypass_cache") bypassCache: String = "false",
        @Query("offset") offset: Int = 0,
        @Query("limit") limit: Int = 100
    ): TorBoxListResponse

    @GET("api/torrents/torrentinfo")
    suspend fun getTorrentInfo(
        @Header("Authorization") bearerToken: String,
        @Query("torrent_id") torrentId: Long? = null,
        @Query("hash") infoHash: String? = null
    ): TorBoxInfoResponse

    @GET("api/torrents/checkcached")
    suspend fun checkCache(
        @Header("Authorization") bearerToken: String,
        @Query("hash") infoHash: String,
        @Query("format") format: String = "list"
    ): TorBoxCacheResponse

    @GET("api/torrents/requestdl")
    suspend fun getDownloadLink(
        @Header("Authorization") bearerToken: String,
        @Query("torrent_id") torrentId: Long? = null,
        @Query("hash") infoHash: String? = null,
        @Query("file_id") fileId: Int? = null
    ): TorBoxDownloadResponse
}

data class TorBoxListResponse(
    val success: Boolean = false,
    val data: JsonElement? = null,
    val detail: String? = null,
    val error: String? = null
)

data class TorBoxInfoResponse(
    val success: Boolean = false,
    val data: JsonElement? = null,
    val detail: String? = null,
    val error: String? = null
)

data class TorBoxCacheResponse(
    val success: Boolean = false,
    val data: JsonElement? = null,
    val detail: String? = null,
    val error: String? = null
)

data class TorBoxDownloadResponse(
    val success: Boolean = false,
    val data: String? = null,
    val detail: String? = null,
    val error: String? = null
)

data class TorBoxTorrentSummary(
    val torrentId: Long,
    val name: String,
    val infoHash: String?,
    val status: String?,
    val files: List<TorBoxFileSummary> = emptyList()
)

data class TorBoxFileSummary(
    val fileId: Int,
    val name: String,
    val sizeBytes: Long?,
    val mimeType: String?,
    val downloadUrl: String? = null
)
