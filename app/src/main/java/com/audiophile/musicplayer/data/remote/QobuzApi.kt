package com.audiophile.musicplayer.data.remote

import retrofit2.http.GET
import retrofit2.http.Query

interface QobuzApi {
    
    /**
     * Searches for a track on Qobuz.
     */
    @GET("track/search")
    suspend fun searchTrack(
        @Query("query") query: String,
        @Query("app_id") appId: String,
        @Query("limit") limit: Int = 1
    ): QobuzSearchResponse

    /**
     * Placeholder contract for a future user-authorized high-quality source resolver.
     */
    @GET("track/getFileUrl")
    suspend fun getTrackFileUrl(
        @Query("track_id") trackId: String,
        @Query("format_id") formatId: Int = 27, // 27 = FLAC
        @Query("app_id") appId: String,
        @Query("str") signature: String, // Calculated as MD5(track_id + format_id + app_id + intent + app_secret)
        @Query("intent") intent: String = "stream"
    ): QobuzStreamResponse
}

data class QobuzSearchResponse(val query: String, val tracks: QobuzTrackList?)
data class QobuzTrackList(val items: List<QobuzTrackItem>)
data class QobuzTrackItem(val id: Int, val title: String, val performer: QobuzPerformer)
data class QobuzPerformer(val name: String)

data class QobuzStreamResponse(val url: String?, val format_id: Int)
