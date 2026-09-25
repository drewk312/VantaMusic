package com.audiophile.musicplayer.data.connectors.apple

import com.audiophile.musicplayer.data.connectors.ConnectedLibraryAccount
import com.audiophile.musicplayer.data.connectors.ConnectedLibraryClient
import com.audiophile.musicplayer.data.connectors.ConnectedLibraryImportPage
import com.audiophile.musicplayer.data.connectors.ConnectedLibraryImportRequest
import com.audiophile.musicplayer.data.connectors.ConnectedLibraryProvider
import com.audiophile.musicplayer.data.connectors.ImportedLibraryTrack
import com.audiophile.musicplayer.data.metadata.apple.AppleMusicApiClient
import com.audiophile.musicplayer.data.metadata.apple.AppleMusicMetadataProvider
import java.util.UUID

/**
 * Apple Music user-library connector.
 *
 * This is deliberately metadata-only. Music User Token and developer token handling
 * belongs in the auth/gateway layer; this class must never return stream URLs.
 */
class AppleMusicLibraryConnector(
    private val metadataProvider: AppleMusicMetadataProvider,
    private val clientFactory: (devToken: String, userToken: String) -> AppleMusicApiClient = { d, u -> AppleMusicApiClient(d, u) }
) : ConnectedLibraryClient {
    override val provider: ConnectedLibraryProvider = ConnectedLibraryProvider.APPLE_MUSIC
    override val minimumImportScopes: Set<String> = emptySet()
    override val minimumLikeSyncScopes: Set<String> = setOf("music-library-modify")
    override val minimumPlaylistSyncScopes: Set<String> = setOf("music-library-modify")

    override suspend fun fetchLibraryPage(
        account: ConnectedLibraryAccount,
        request: ConnectedLibraryImportRequest,
        cursor: String?
    ): ConnectedLibraryImportPage {
        // We use cursor as the offset for pagination
        val offset = cursor?.toIntOrNull() ?: 0
        val devToken = account.scopesGranted.firstOrNull { it.startsWith("dev:") }?.removePrefix("dev:")
            ?: throw IllegalStateException("Missing developer token in scopes")
        val userToken = account.scopesGranted.firstOrNull { it.startsWith("user:") }?.removePrefix("user:")
            ?: throw IllegalStateException("Missing user token in scopes")

        val client = clientFactory(devToken, userToken)
        val songs = if (request.importSavedTracks) client.fetchLibrarySongs(offset) else null
        val playlistsPage = if (request.importPlaylists) client.fetchLibraryPlaylists(offset) else null
        val tracks = songs?.data.orEmpty().map { it.toImportedTrack() }.toMutableList()
        val playlists = playlistsPage?.data.orEmpty().map { playlist ->
            val playlistTracks = mutableListOf<ImportedLibraryTrack>()
            if (request.importPlaylistTracks) {
                var trackOffset = 0
                do {
                    val page = client.fetchLibraryPlaylistTracks(playlist.id, trackOffset)
                    playlistTracks += page.data.map { it.toImportedTrack(listOf(playlist.id)) }
                    trackOffset += page.data.size
                } while (!page.next.isNullOrBlank() && page.data.isNotEmpty())
            }
            tracks += playlistTracks
            com.audiophile.musicplayer.data.connectors.ImportedPlaylist(
                id = "apple:${playlist.id}", provider = provider, providerPlaylistId = playlist.id,
                name = playlist.attributes?.name ?: "Apple Music playlist",
                ownerName = playlist.attributes?.curatorName,
                trackCount = playlistTracks.size, artworkUrl = playlist.attributes?.artwork?.sizedUrl(),
                importedAt = System.currentTimeMillis()
            )
        }
        return ConnectedLibraryImportPage(
            tracks = tracks, playlists = playlists,
            nextCursor = if (!songs?.next.isNullOrBlank() || !playlistsPage?.next.isNullOrBlank()) (offset + 100).toString() else null
        )
    }

    private fun com.audiophile.musicplayer.data.metadata.apple.AppleMusicSongResource.toImportedTrack(
        playlistIds: List<String> = emptyList()
    ) = ImportedLibraryTrack(
        id = "apple:$id", provider = provider, providerTrackId = id,
        title = attributes?.name ?: "Unknown", artist = attributes?.artistName ?: "Unknown",
        album = attributes?.albumName, durationMs = attributes?.durationInMillis,
        isrc = attributes?.isrc, artworkUrl = attributes?.artwork?.sizedUrl(),
        explicit = attributes?.contentRating == "explicit", playlistIds = playlistIds,
        importedAt = System.currentTimeMillis()
    )

    override suspend fun findProviderTrackByIdentity(
        account: ConnectedLibraryAccount,
        isrc: String?,
        title: String,
        artist: String
    ): String? {
        // Use the metadata provider to search the catalog
        val match = metadataProvider.searchByText(title, artist, null)
        return match?.externalIds?.get("AppleMusic")
    }

    override suspend fun saveLike(account: ConnectedLibraryAccount, providerTrackId: String) {
        val devToken = account.scopesGranted.firstOrNull { it.startsWith("dev:") }?.removePrefix("dev:")
            ?: return
        val userToken = account.scopesGranted.firstOrNull { it.startsWith("user:") }?.removePrefix("user:")
            ?: return
        
        val client = clientFactory(devToken, userToken)
        client.addTrackToLibrary(listOf(providerTrackId))
    }
}
