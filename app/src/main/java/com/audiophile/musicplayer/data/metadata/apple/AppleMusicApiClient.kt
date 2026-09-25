package com.audiophile.musicplayer.data.metadata.apple

import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

class AppleMusicApiClient(
    developerToken: String,
    userToken: String? = null
) {
    private val api: AppleMusicCatalogApi
    private val libraryApi: AppleMusicLibraryApiInternal

    init {
        val client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val builder = chain.request()
                    .newBuilder()
                    .addHeader("Authorization", "Bearer $developerToken")
                
                userToken?.let {
                    builder.addHeader("Music-User-Token", it)
                }

                chain.proceed(builder.build())
            }
            .build()

        val retrofit = Retrofit.Builder()
            .baseUrl("https://api.music.apple.com/")
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()

        api = retrofit.create(AppleMusicCatalogApi::class.java)
        libraryApi = retrofit.create(AppleMusicLibraryApiInternal::class.java)
    }

    suspend fun searchSongs(storefront: String, term: String, limit: Int = 10): AppleMusicSearchResponse {
        return api.search(storefront = storefront, term = term, types = "songs", limit = limit)
    }

    suspend fun lookupSongsByIsrc(storefront: String, isrc: String, limit: Int = 10): AppleMusicSongsResponse {
        return api.songsByIsrc(storefront = storefront, isrc = isrc, limit = limit)
    }

    suspend fun lookupSongById(storefront: String, id: String): AppleMusicSongsResponse {
        return api.songById(storefront = storefront, id = id)
    }

    suspend fun lookupPlaylistById(storefront: String, id: String): AppleMusicPlaylistResponse =
        api.playlistById(storefront, id, "tracks")

    suspend fun lookupAlbumById(storefront: String, id: String): AppleMusicAlbumResponse =
        api.albumById(storefront, id, "tracks")

    // Library methods
    suspend fun fetchLibrarySongs(offset: Int, limit: Int = 100): AppleMusicSongsResponse =
        libraryApi.getLibrarySongs(offset, limit)

    suspend fun fetchLibraryPlaylists(offset: Int, limit: Int = 100): AppleMusicPlaylistResponse =
        libraryApi.getLibraryPlaylists(offset, limit)

    suspend fun fetchLibraryPlaylistTracks(id: String, offset: Int, limit: Int = 100): AppleMusicSongsResponse =
        libraryApi.getLibraryPlaylistTracks(id, offset, limit)

    suspend fun addTrackToLibrary(ids: List<String>) =
        libraryApi.addTracksToLibrary(ids)
}

private interface AppleMusicCatalogApi {
    @GET("v1/catalog/{storefront}/playlists/{id}")
    suspend fun playlistById(
        @Path("storefront") storefront: String,
        @Path("id") id: String,
        @Query("include") include: String
    ): AppleMusicPlaylistResponse

    @GET("v1/catalog/{storefront}/albums/{id}")
    suspend fun albumById(
        @Path("storefront") storefront: String,
        @Path("id") id: String,
        @Query("include") include: String
    ): AppleMusicAlbumResponse

    @GET("v1/catalog/{storefront}/search")
    suspend fun search(
        @Path("storefront") storefront: String,
        @Query("term") term: String,
        @Query("types") types: String,
        @Query("limit") limit: Int
    ): AppleMusicSearchResponse

    @GET("v1/catalog/{storefront}/songs")
    suspend fun songsByIsrc(
        @Path("storefront") storefront: String,
        @Query("filter[isrc]") isrc: String,
        @Query("limit") limit: Int
    ): AppleMusicSongsResponse

    @GET("v1/catalog/{storefront}/songs/{id}")
    suspend fun songById(
        @Path("storefront") storefront: String,
        @Path("id") id: String
    ): AppleMusicSongsResponse
}

private interface AppleMusicLibraryApiInternal {
    @GET("v1/me/library/playlists")
    suspend fun getLibraryPlaylists(@Query("offset") offset: Int, @Query("limit") limit: Int): AppleMusicPlaylistResponse

    @GET("v1/me/library/playlists/{id}/tracks")
    suspend fun getLibraryPlaylistTracks(@Path("id") id: String, @Query("offset") offset: Int, @Query("limit") limit: Int): AppleMusicSongsResponse

    @GET("v1/me/library/songs")
    suspend fun getLibrarySongs(
        @Query("offset") offset: Int,
        @Query("limit") limit: Int
    ): AppleMusicSongsResponse

    @retrofit2.http.POST("v1/me/library")
    suspend fun addTracksToLibrary(
        @Query("ids[songs]") ids: List<String>
    )
}
