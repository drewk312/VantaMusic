package com.audiophile.musicplayer.data.connectors

import com.audiophile.musicplayer.data.connectors.matching.ConnectedLibraryMatcher
import com.audiophile.musicplayer.data.local.entities.UnifiedTrack
import com.audiophile.musicplayer.data.local.entities.UnifiedTrackWithSources
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class ConnectedLibraryImportTest {

    @Test
    fun importsSavedTracksMetadataAndPlaylistsWithPagination() = runBlocking {
        val firstTrack = imported("spotify:1", "Blinding Lights", "The Weeknd", "USUG11904206")
        val secondTrack = imported("spotify:2", "Bad Habits", "Ed Sheeran", null)
        val client = FakeClient(
            pages = listOf(
                ConnectedLibraryImportPage(
                    tracks = listOf(firstTrack),
                    playlists = listOf(playlist("night-drive")),
                    nextCursor = "next"
                ),
                ConnectedLibraryImportPage(
                    tracks = listOf(secondTrack),
                    playlists = listOf(playlist("road-trip")),
                    nextCursor = null
                )
            )
        )
        val manager = ConnectedLibraryImportManager(
            clients = mapOf(ConnectedLibraryProvider.SPOTIFY to client),
            matcher = ConnectedLibraryMatcher()
        )

        val result = manager.importLibrary(
            account = account(),
            request = ConnectedLibraryImportRequest(ConnectedLibraryProvider.SPOTIFY),
            localCatalog = listOf(localTrack("Blinding Lights", "The Weeknd", "USUG11904206")),
            nowMs = 10L
        )

        assertEquals(2, result.summary.tracksImported)
        assertEquals(2, result.summary.playlistsImported)
        assertEquals(1, result.summary.matchedToVanta)
        assertEquals(1, result.summary.unmatchedMetadataOnly)
        assertEquals(listOf(null, "next"), client.seenCursors)
    }

    @Test
    fun logRedactorRemovesTokens() {
        val redacted = ConnectedLibraryLogRedactor.redact(
            "access_token=abc refresh_token=def Authorization Bearer secret music_user_token=ghi"
        )

        assertFalse(redacted.contains("abc"))
        assertFalse(redacted.contains("def"))
        assertFalse(redacted.contains("secret"))
        assertFalse(redacted.contains("ghi"))
    }

    @Test
    fun importFailsExplicitlyWhenRequiredScopesAreMissing() = runBlocking {
        val manager = ConnectedLibraryImportManager(
            clients = mapOf(ConnectedLibraryProvider.SPOTIFY to FakeClient(pages = emptyList())),
            matcher = ConnectedLibraryMatcher()
        )

        val result = manager.importLibrary(
            account = account().copy(scopesGranted = setOf("user-library-read")),
            request = ConnectedLibraryImportRequest(ConnectedLibraryProvider.SPOTIFY),
            localCatalog = emptyList(),
            nowMs = 20L
        )

        assertEquals(0, result.summary.tracksImported)
        assertEquals(0, result.summary.playlistsImported)
        assertEquals(1, result.summary.errors.size)
        assertFalse(result.summary.errors.single().contains("token", ignoreCase = true))
    }

    private fun imported(id: String, title: String, artist: String, isrc: String?): ImportedLibraryTrack =
        ImportedLibraryTrack(
            id = id,
            provider = ConnectedLibraryProvider.SPOTIFY,
            providerTrackId = id.substringAfter(':'),
            title = title,
            artist = artist,
            isrc = isrc,
            importedAt = 1L
        )

    private fun playlist(id: String): ImportedPlaylist =
        ImportedPlaylist(
            id = id,
            provider = ConnectedLibraryProvider.SPOTIFY,
            providerPlaylistId = id,
            name = id,
            trackCount = 10,
            importedAt = 1L
        )

    private fun localTrack(title: String, artist: String, isrc: String): UnifiedTrackWithSources =
        UnifiedTrackWithSources(
            track = UnifiedTrack(trackId = 7L, title = title, artist = artist, albumName = null, coverArtUrl = null, isrc = isrc),
            sources = emptyList()
        )

    private fun account(): ConnectedLibraryAccount =
        ConnectedLibraryAccount(
            id = "acct",
            provider = ConnectedLibraryProvider.SPOTIFY,
            displayName = "Spotify Library",
            accountId = "user",
            connectedAt = 1L,
            tokenStatus = ConnectedLibraryTokenStatus.VALID,
            scopesGranted = setOf("user-library-read", "playlist-read-private", "playlist-read-collaborative")
        )

    private class FakeClient(
        private val pages: List<ConnectedLibraryImportPage>
    ) : ConnectedLibraryClient {
        val seenCursors = mutableListOf<String?>()
        private var index = 0
        override val provider = ConnectedLibraryProvider.SPOTIFY
        override val minimumImportScopes = setOf("user-library-read")
        override val minimumLikeSyncScopes = setOf("user-library-modify")
        override val minimumPlaylistSyncScopes = setOf("playlist-modify-private")

        override suspend fun fetchLibraryPage(
            account: ConnectedLibraryAccount,
            request: ConnectedLibraryImportRequest,
            cursor: String?
        ): ConnectedLibraryImportPage {
            seenCursors += cursor
            return pages[index++]
        }

        override suspend fun findProviderTrackByIdentity(
            account: ConnectedLibraryAccount,
            isrc: String?,
            title: String,
            artist: String
        ): String? = null

        override suspend fun saveLike(account: ConnectedLibraryAccount, providerTrackId: String) = Unit
    }
}
