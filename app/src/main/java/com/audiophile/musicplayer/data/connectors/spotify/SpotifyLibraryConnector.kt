package com.audiophile.musicplayer.data.connectors.spotify

import com.audiophile.musicplayer.data.connectors.ConnectedLibraryAccount
import com.audiophile.musicplayer.data.connectors.ConnectedLibraryClient
import com.audiophile.musicplayer.data.connectors.ConnectedLibraryImportPage
import com.audiophile.musicplayer.data.connectors.ConnectedLibraryImportRequest
import com.audiophile.musicplayer.data.connectors.ConnectedLibraryProvider

/**
 * Spotify user-library connector.
 *
 * Uses OAuth Authorization Code with PKCE in the auth layer and imports metadata
 * only. Spotify IDs are never playable VANTA source IDs.
 */
class SpotifyLibraryConnector(
    private val api: SpotifyLibraryApi
) : ConnectedLibraryClient {
    override val provider: ConnectedLibraryProvider = ConnectedLibraryProvider.SPOTIFY
    override val minimumImportScopes: Set<String> = setOf("user-library-read", "playlist-read-private", "playlist-read-collaborative")
    override val minimumLikeSyncScopes: Set<String> = setOf("user-library-modify")
    override val minimumPlaylistSyncScopes: Set<String> = setOf("playlist-modify-private", "playlist-modify-public")

    override suspend fun fetchLibraryPage(
        account: ConnectedLibraryAccount,
        request: ConnectedLibraryImportRequest,
        cursor: String?
    ): ConnectedLibraryImportPage =
        api.fetchLibraryPage(account, request, cursor)

    override suspend fun findProviderTrackByIdentity(
        account: ConnectedLibraryAccount,
        isrc: String?,
        title: String,
        artist: String
    ): String? = api.findTrack(account, isrc, title, artist)

    override suspend fun saveLike(account: ConnectedLibraryAccount, providerTrackId: String) {
        api.saveTrack(account, providerTrackId)
    }
}

interface SpotifyLibraryApi {
    suspend fun fetchLibraryPage(
        account: ConnectedLibraryAccount,
        request: ConnectedLibraryImportRequest,
        cursor: String?
    ): ConnectedLibraryImportPage

    suspend fun findTrack(
        account: ConnectedLibraryAccount,
        isrc: String?,
        title: String,
        artist: String
    ): String?

    suspend fun saveTrack(account: ConnectedLibraryAccount, providerTrackId: String)
}
