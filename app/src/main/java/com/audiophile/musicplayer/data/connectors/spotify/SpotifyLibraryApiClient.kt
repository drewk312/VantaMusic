package com.audiophile.musicplayer.data.connectors.spotify

import com.audiophile.musicplayer.data.connectors.ConnectedLibraryAccount
import com.audiophile.musicplayer.data.connectors.ConnectedLibraryImportPage
import com.audiophile.musicplayer.data.connectors.ConnectedLibraryImportRequest
import com.audiophile.musicplayer.data.connectors.ImportedLibraryTrack
import com.audiophile.musicplayer.data.connectors.ImportedPlaylist
import okhttp3.OkHttpClient
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.PUT
import retrofit2.http.Query
import java.util.UUID

/**
 * Live Spotify Web API client for VANTA connected-library import.
 *
 * Reads saved tracks, playlists, and playlist tracks metadata only.
 * No stream URLs are ever returned.
 */
class SpotifyLibraryApiClient(
    private val accessToken: String,
    baseUrl: String = "https://api.spotify.com/"
) : SpotifyLibraryApi {

    private val service: SpotifyWebApi

    init {
        val client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val request = chain.request()
                    .newBuilder()
                    .addHeader("Authorization", "Bearer $accessToken")
                    .build()
                chain.proceed(request)
            }
            .build()

        service = Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(SpotifyWebApi::class.java)
    }

    override suspend fun fetchLibraryPage(
        account: ConnectedLibraryAccount,
        request: ConnectedLibraryImportRequest,
        cursor: String?
    ): ConnectedLibraryImportPage {
        val offset = cursor?.toIntOrNull() ?: 0
        val limit = 50

        val tracks = mutableListOf<ImportedLibraryTrack>()
        val playlists = mutableListOf<ImportedPlaylist>()
        var hasNext = false

        if (request.importSavedTracks) {
            val saved = service.getSavedTracks(limit, offset).requiredBody()
            hasNext = hasNext || !saved.next.isNullOrBlank()
            saved.items?.mapNotNull { item ->
                item.track?.toImportedLibraryTrack(item.added_at)
            }?.mapTo(tracks) { it }
        }

        if (request.importPlaylists) {
            val userPlaylists = service.getCurrentUserPlaylists(limit, offset).requiredBody()
            hasNext = hasNext || !userPlaylists.next.isNullOrBlank()
            userPlaylists.items?.mapTo(playlists) { playlist ->
                ImportedPlaylist(
                    id = UUID.randomUUID().toString(),
                    provider = account.provider,
                    providerPlaylistId = playlist.id,
                    name = playlist.name,
                    ownerName = playlist.owner?.display_name,
                    trackCount = playlist.tracks?.total ?: 0,
                    artworkUrl = playlist.images?.firstOrNull()?.url,
                    importedAt = System.currentTimeMillis(),
                    isEditableByUser = playlist.owner?.id == account.accountId || playlist.owner?.id == "me"
                )
            }
        }

        if (request.importPlaylistTracks && request.importPlaylists) {
            playlists.forEach { playlist ->
                run {
                    var playlistOffset = 0
                    while (true) {
                        val page = service.getPlaylistTracks(playlist.providerPlaylistId, 50, playlistOffset).requiredBody()
                        val pageItems = page.items.orEmpty()
                        pageItems
                            .mapNotNull { it.track }
                            .mapTo(tracks) { track ->
                                track.toImportedLibraryTrack(addedAt = null, playlistIds = listOf(playlist.providerPlaylistId))
                            }
                        if (page.next.isNullOrBlank() || pageItems.isEmpty()) break
                        playlistOffset += pageItems.size
                    }
                }
            }
        }

        val nextCursor = if (hasNext) (offset + limit).toString() else null

        return ConnectedLibraryImportPage(
            tracks = tracks,
            playlists = playlists,
            nextCursor = nextCursor
        )
    }

    override suspend fun findTrack(
        account: ConnectedLibraryAccount,
        isrc: String?,
        title: String,
        artist: String
    ): String? {
        val query = buildString {
            append("track:\"").append(title).append("\"")
            append(" artist:\"").append(artist).append("\"")
            isrc?.let { append(" isrc:").append(it) }
        }
        return runCatching {
            service.search(query, "track", 1).body()
                ?.tracks
                ?.items
                ?.firstOrNull()
                ?.id
        }.getOrNull()
    }

    override suspend fun saveTrack(account: ConnectedLibraryAccount, providerTrackId: String) {
        val response = service.saveTracks(SpotifyIdsBody(ids = listOf(providerTrackId)))
        if (!response.isSuccessful) throw retrofit2.HttpException(response)
    }

    private fun <T> Response<T>.requiredBody(): T {
        if (!isSuccessful) throw retrofit2.HttpException(this)
        return body() ?: throw java.io.IOException("Spotify returned an empty response")
    }

    private fun SpotifyTrack.toImportedLibraryTrack(
        addedAt: String?,
        playlistIds: List<String> = emptyList()
    ): ImportedLibraryTrack = ImportedLibraryTrack(
        id = UUID.randomUUID().toString(),
        provider = com.audiophile.musicplayer.data.connectors.ConnectedLibraryProvider.SPOTIFY,
        providerTrackId = id,
        title = name,
        artist = artists?.firstOrNull()?.name ?: "Unknown",
        album = album?.name,
        durationMs = duration_ms,
        isrc = external_ids?.isrc,
        artworkUrl = album?.images?.firstOrNull()?.url,
        explicit = explicit,
        addedAt = addedAt?.let { parseSpotifyDate(it) },
        playlistIds = playlistIds,
        importedAt = System.currentTimeMillis()
    )

    private fun parseSpotifyDate(value: String): Long? = runCatching {
        java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", java.util.Locale.US)
            .apply { timeZone = java.util.TimeZone.getTimeZone("UTC") }
            .parse(value)
            ?.time
    }.getOrNull()
}

private interface SpotifyWebApi {
    @GET("v1/me/tracks")
    suspend fun getSavedTracks(
        @Query("limit") limit: Int,
        @Query("offset") offset: Int
    ): Response<SpotifySavedTracksResponse>

    @GET("v1/me/playlists")
    suspend fun getCurrentUserPlaylists(
        @Query("limit") limit: Int,
        @Query("offset") offset: Int
    ): Response<SpotifyPlaylistsResponse>

    @GET("v1/playlists/{playlist_id}/items")
    suspend fun getPlaylistTracks(
        @retrofit2.http.Path("playlist_id") playlistId: String,
        @Query("limit") limit: Int,
        @Query("offset") offset: Int
    ): Response<SpotifyPlaylistTracksResponse>

    @GET("v1/search")
    suspend fun search(
        @Query("q") query: String,
        @Query("type") type: String,
        @Query("limit") limit: Int
    ): Response<SpotifySearchResponse>

    @PUT("v1/me/tracks")
    suspend fun saveTracks(@Body body: SpotifyIdsBody): Response<Unit>
}

private data class SpotifyIdsBody(
    val ids: List<String>
)

private data class SpotifySavedTracksResponse(
    val items: List<SpotifySavedTrackItem>? = null,
    val next: String? = null,
    val total: Int = 0
)

private data class SpotifySavedTrackItem(
    val added_at: String? = null,
    val track: SpotifyTrack? = null
)

private data class SpotifyPlaylistsResponse(
    val items: List<SpotifyPlaylist>? = null,
    val next: String? = null,
    val total: Int = 0
)

private data class SpotifyPlaylist(
    val id: String,
    val name: String,
    val owner: SpotifyOwner? = null,
    val tracks: SpotifyPlaylistTracksMeta? = null,
    val images: List<SpotifyImage>? = null
)

private data class SpotifyPlaylistTracksMeta(
    val total: Int = 0
)

private data class SpotifyOwner(
    val id: String? = null,
    val display_name: String? = null
)

private data class SpotifyPlaylistTracksResponse(
    val items: List<SpotifyPlaylistTrackItem>? = null,
    val next: String? = null
)

private data class SpotifyPlaylistTrackItem(
    @com.google.gson.annotations.SerializedName(value = "item", alternate = ["track"]) val track: SpotifyTrack? = null
)

private data class SpotifySearchResponse(
    val tracks: SpotifyTracksPage? = null
)

private data class SpotifyTracksPage(
    val items: List<SpotifyTrack>? = null
)

private data class SpotifyTrack(
    val id: String,
    val name: String,
    val artists: List<SpotifyArtist>? = null,
    val album: SpotifyAlbum? = null,
    val duration_ms: Long? = null,
    val explicit: Boolean? = null,
    val external_ids: SpotifyExternalIds? = null
)

private data class SpotifyArtist(
    val id: String,
    val name: String
)

private data class SpotifyAlbum(
    val id: String,
    val name: String,
    val images: List<SpotifyImage>? = null
)

private data class SpotifyExternalIds(
    val isrc: String? = null
)

private data class SpotifyImage(
    val url: String,
    val height: Int? = null,
    val width: Int? = null
)
