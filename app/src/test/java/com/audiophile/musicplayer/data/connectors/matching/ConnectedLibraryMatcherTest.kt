package com.audiophile.musicplayer.data.connectors.matching

import com.audiophile.musicplayer.data.connectors.ConnectedLibraryMatchStatus
import com.audiophile.musicplayer.data.connectors.ConnectedLibraryProvider
import com.audiophile.musicplayer.data.connectors.ImportedLibraryTrack
import com.audiophile.musicplayer.data.connectors.ProviderTrackLink
import com.audiophile.musicplayer.data.local.entities.UnifiedTrack
import com.audiophile.musicplayer.data.local.entities.UnifiedTrackWithSources
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ConnectedLibraryMatcherTest {

    private val matcher = ConnectedLibraryMatcher()

    @Test
    fun isrcExactMatchWins() {
        val result = matcher.match(
            imported = imported(isrc = "USUG11904206"),
            localCatalog = listOf(local("Blinding Lights", "The Weeknd", "USUG11904206"))
        )

        assertEquals(ConnectedLibraryMatchStatus.MATCHED_LOCAL, result.status)
        assertEquals(42L, result.vantaLocalTrackId)
    }

    @Test
    fun titleArtistDurationMatchLinksLocalTrack() {
        val result = matcher.match(
            imported = imported(isrc = null, durationMs = 200_000L),
            localCatalog = listOf(local("Blinding Lights", "The Weeknd", null, durationMs = 207_000L))
        )

        assertEquals(ConnectedLibraryMatchStatus.MATCHED_LOCAL, result.status)
    }

    @Test
    fun wrongVersionRejected() {
        val result = matcher.match(
            imported = imported(title = "Blinding Lights Karaoke Version", isrc = null),
            localCatalog = listOf(local("Blinding Lights", "The Weeknd", null))
        )

        assertEquals(ConnectedLibraryMatchStatus.REJECTED_WRONG_VERSION, result.status)
        assertNull(result.vantaLocalTrackId)
    }

    @Test
    fun providerLinkDoesNotCreatePlayableId() {
        val imported = imported(providerTrackId = "spotify_track_1")
        val result = matcher.match(
            imported = imported,
            localCatalog = emptyList(),
            existingLinks = listOf(
                ProviderTrackLink(
                    id = "SPOTIFY:spotify_track_1",
                    provider = ConnectedLibraryProvider.SPOTIFY,
                    providerTrackId = "spotify_track_1",
                    vantaCanonicalTrackId = "text:blinding lights:the weeknd",
                    matchConfidence = 0.99f,
                    linkedAt = 1L
                )
            )
        )

        assertEquals(ConnectedLibraryMatchStatus.MATCHED_CANONICAL, result.status)
        assertNull(result.vantaLocalTrackId)
    }

    private fun imported(
        title: String = "Blinding Lights",
        artist: String = "The Weeknd",
        isrc: String? = "USUG11904206",
        durationMs: Long? = 200_000L,
        providerTrackId: String = "spotify_1"
    ): ImportedLibraryTrack =
        ImportedLibraryTrack(
            id = providerTrackId,
            provider = ConnectedLibraryProvider.SPOTIFY,
            providerTrackId = providerTrackId,
            title = title,
            artist = artist,
            durationMs = durationMs,
            isrc = isrc,
            importedAt = 1L
        )

    private fun local(title: String, artist: String, isrc: String?, durationMs: Long? = 200_000L): UnifiedTrackWithSources =
        UnifiedTrackWithSources(
            track = UnifiedTrack(
                trackId = 42L,
                title = title,
                artist = artist,
                albumName = "After Hours",
                coverArtUrl = null,
                isrc = isrc,
                durationMs = durationMs
            ),
            sources = emptyList()
        )
}
