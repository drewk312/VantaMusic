package com.audiophile.musicplayer.data.metadata.deezer

import com.google.gson.annotations.SerializedName
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

data class DeezerSearchResponse(
    val data: List<DeezerTrack> = emptyList(),
    val total: Int? = null,
    val next: String? = null
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
    @SerializedName("release_date") val releaseDate: String? = null,
    val artist: DeezerArtist? = null
)

data class DeezerPlaylist(
    val id: Long = 0,
    val title: String? = null,
    val description: String? = null,
    @SerializedName("nb_tracks") val trackCount: Int? = null,
    val picture: String? = null,
    @SerializedName("picture_medium") val pictureMedium: String? = null,
    @SerializedName("picture_xl") val pictureXl: String? = null,
    val user: DeezerArtist? = null
)
data class DeezerPlaylistResponse(
    val data: List<DeezerPlaylist> = emptyList(),
    val total: Int? = null,
    val next: String? = null
)

data class DeezerAlbumResponse(
    val data: List<DeezerAlbum> = emptyList(),
    val total: Int? = null,
    val next: String? = null
)
data class DeezerArtistResponse(val data: List<DeezerArtist> = emptyList())

class DeezerApiClient(baseUrl: String = "https://api.deezer.com/2.0/") {
    private val api: DeezerApi = Retrofit.Builder()
        .baseUrl(baseUrl)
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(DeezerApi::class.java)

    suspend fun searchTracks(query: String, limit: Int = 25): DeezerSearchResponse {
        return api.searchTracks(query = query, limit = limit)
    }

    suspend fun getChartTracks(chartId: Int = 0, limit: Int = 50): DeezerSearchResponse {
        return api.getChartTracks(chartId = chartId, limit = limit)
    }

    suspend fun searchPlaylists(query: String, limit: Int = 25) = api.searchPlaylists(query, limit)
    suspend fun playlistTracks(id: Long, limit: Int = 50, index: Int = 0) = api.playlistTracks(id, limit, index)

    suspend fun searchArtists(query: String, limit: Int = 25) = api.searchArtists(query, limit)
    suspend fun artistTopTracks(id: Long, limit: Int = 100) = api.artistTopTracks(id, limit)
    suspend fun artistAlbums(id: Long, limit: Int = 100, index: Int = 0) = api.artistAlbums(id, limit, index)

    suspend fun searchAlbums(query: String, limit: Int = 25) = api.searchAlbums(query, limit)
    suspend fun albumTracks(id: Long, limit: Int = 100) = api.albumTracks(id, limit)

    suspend fun getTrack(trackId: String): DeezerTrack {
        return api.getTrack(trackId)
    }

    suspend fun lookupByIsrc(isrc: String): DeezerTrack {
        return api.getTrack("isrc:${isrc.trim().uppercase()}")
    }
}

private interface DeezerApi {
    @GET("search/artist")
    suspend fun searchArtists(@Query("q") query: String, @Query("limit") limit: Int): DeezerArtistResponse
    @GET("artist/{id}/top")
    suspend fun artistTopTracks(@Path("id") id: Long, @Query("limit") limit: Int): DeezerSearchResponse
    @GET("artist/{id}/albums")
    suspend fun artistAlbums(@Path("id") id: Long, @Query("limit") limit: Int, @Query("index") index: Int): DeezerAlbumResponse

    @GET("search/album")
    suspend fun searchAlbums(@Query("q") query: String, @Query("limit") limit: Int): DeezerAlbumResponse
    @GET("album/{id}/tracks")
    suspend fun albumTracks(@Path("id") id: Long, @Query("limit") limit: Int): DeezerSearchResponse

    @GET("search/playlist")
    suspend fun searchPlaylists(@Query("q") query: String, @Query("limit") limit: Int = 25): DeezerPlaylistResponse
    @GET("playlist/{id}/tracks")
    suspend fun playlistTracks(
        @Path("id") id: Long,
        @Query("limit") limit: Int,
        @Query("index") index: Int = 0
    ): DeezerSearchResponse

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
