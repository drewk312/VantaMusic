package com.audiophile.musicplayer.data.remote

import com.google.gson.JsonElement
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface RealDebridApi {
    @GET("torrents/instantAvailability/{hash}")
    suspend fun checkInstantAvailability(
        @Header("Authorization") bearerToken: String,
        @Path("hash") infoHash: String
    ): JsonElement

    @POST("torrents/addMagnet")
    suspend fun addMagnet(
        @Header("Authorization") bearerToken: String,
        @Query("magnet") magnet: String
    ): RealDebridAddMagnetResponse

    @GET("torrents/info/{id}")
    suspend fun getTorrentInfo(
        @Header("Authorization") bearerToken: String,
        @Path("id") torrentId: String
    ): JsonElement

    @POST("torrents/selectFiles/{id}")
    suspend fun selectFiles(
        @Header("Authorization") bearerToken: String,
        @Path("id") torrentId: String,
        @Query("files") files: String
    ): Response<Unit>

    @POST("unrestrict/link")
    suspend fun unrestrictLink(
        @Header("Authorization") bearerToken: String,
        @Query("link") link: String
    ): RealDebridUnrestrictResponse

    @GET("user")
    suspend fun getUser(
        @Header("Authorization") bearerToken: String
    ): JsonElement
}

data class RealDebridAddMagnetResponse(
    val id: String? = null,
    val uri: String? = null,
    val error: String? = null
)

data class RealDebridUnrestrictResponse(
    val id: String? = null,
    val filename: String? = null,
    val mimeType: String? = null,
    val filesize: Long? = null,
    val link: String? = null,
    val host: String? = null,
    val download: String? = null,
    val error: String? = null
)
