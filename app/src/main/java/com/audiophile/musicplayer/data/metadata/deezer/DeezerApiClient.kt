package com.audiophile.musicplayer.data.metadata.deezer

import com.google.gson.annotations.SerializedName
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

data class DeezerSearchResponse(
    val data: List<DeezerTrack> = emptyList()
)

data class DeezerTrack(
    val id: Long = 0,
    val title: String? = null,
    @SerializedName("title_short") val titleShort: String? = null,
    val duration: Long? = null,
    val link: String? = null,
    val isrc: String? = null,
    @SerializedName("track_position") val trackPosition: Int? = null,
    @SerializedName("disk_number") val diskNumber: Int? = null,
    @SerializedName("explicit_lyrics") val explicitLyrics: Boolean? = null,
    val artist: DeezerArtist? = null,
    val album: DeezerAlbum? = null,
    val contributors: List<DeezerArtist> = emptyList()
)

data class DeezerArtist(
    val id: Long = 0,
    val name: String? = null,
    val link: String? = null,
    val picture: String? = null,
    @SerializedName("picture_xl") val pictureXl: String? = null
)

data class DeezerAlbum(
    val id: Long = 0,
    val title: String? = null,
    val cover: String? = null,
    @SerializedName("cover_xl") val coverXl: String? = null,
    @SerializedName("release_date") val releaseDate: String? = null
)

class DeezerApiClient {
    private val api: DeezerApi = Retrofit.Builder()
        .baseUrl("https://api.deezer.com/2.0/")
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(DeezerApi::class.java)

    suspend fun searchTracks(query: String, limit: Int = 25): DeezerSearchResponse {
        return api.searchTracks(query = query, limit = limit)
    }

    suspend fun getChartTracks(chartId: Int = 0, limit: Int = 50): DeezerSearchResponse {
        return api.getChartTracks(chartId = chartId, limit = limit)
    }

    suspend fun getTrack(trackId: String): DeezerTrack {
        return api.getTrack(trackId)
    }

    suspend fun lookupByIsrc(isrc: String): DeezerTrack {
        return api.getTrack("isrc:${isrc.trim().uppercase()}")
    }
}

private interface DeezerApi {
    @GET("search/track")
    suspend fun searchTracks(
        @Query("q") query: String,
        @Query("limit") limit: Int = 25
    ): DeezerSearchResponse

    @GET("chart/{chartId}/tracks")
    suspend fun getChartTracks(
        @Path("chartId") chartId: Int,
        @Query("limit") limit: Int = 50
    ): DeezerSearchResponse

    @GET("track/{trackId}")
    suspend fun getTrack(
        @Path("trackId", encoded = true) trackId: String
    ): DeezerTrack
}
