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
        val response = client.fetchLibrarySongs(offset = offset)

        val tracks = response.data.map { res ->
            ImportedLibraryTrack(
                id = UUID.randomUUID().toString(),
                provider = ConnectedLibraryProvider.APPLE_MUSIC,
                providerTrackId = res.id,
                title = res.attributes?.name ?: "Unknown",
                artist = res.attributes?.artistName ?: "Unknown",
                album = res.attributes?.albumName,
                durationMs = res.attributes?.durationInMillis,
                isrc = res.attributes?.isrc,
                artworkUrl = res.attributes?.artwork?.sizedUrl(),
                explicit = res.attributes?.contentRating == "explicit",
                importedAt = System.currentTimeMillis()
            )
        }

        val nextCursor = if (response.data.isNotEmpty()) (offset + response.data.size).toString() else null

        return ConnectedLibraryImportPage(
            tracks = tracks,
            nextCursor = nextCursor
        )
    }

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
