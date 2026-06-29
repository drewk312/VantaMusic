package com.audiophile.musicplayer.data.connectors

import com.audiophile.musicplayer.data.taste.TasteEvent
import com.audiophile.musicplayer.data.taste.TasteEventType
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConnectedTasteBuilderTest {

    @Test
    fun importedLibraryCreatesCompactTasteAnchorsWithoutProviderIds() {
        val tracks = listOf(
            imported("spotify_track_1", "Reminiscing", "Little River Band"),
            imported("spotify_track_2", "Cool Change", "Little River Band"),
            imported("apple_track_1", "The Less I Know The Better", "Tame Impala", ConnectedLibraryProvider.APPLE_MUSIC)
        )
        val summary = ConnectedLibraryTasteBuilder().build(
            importedTracks = tracks,
            playlists = listOf(
                ImportedPlaylist(
                    id = "p1",
                    provider = ConnectedLibraryProvider.SPOTIFY,
                    providerPlaylistId = "spotify_playlist_1",
                    name = "Night Drive",
                    trackCount = 40,
                    importedAt = 1L
                )
            ),
            vantaEvents = listOf(TasteEvent("Reminiscing", "Little River Band", event = TasteEventType.SKIPPED))
        )

        assertTrue(summary.signals.isNotEmpty())
        assertTrue(summary.llmSafeSummary.contains("little river band"))
        assertTrue(summary.llmSafeSummary.contains("night-drive"))
        assertFalse(summary.llmSafeSummary.contains("spotify_track_1"))
        assertFalse(summary.llmSafeSummary.contains("apple_track_1"))
    }

    private fun imported(
        id: String,
        title: String,
        artist: String,
        provider: ConnectedLibraryProvider = ConnectedLibraryProvider.SPOTIFY
    ): ImportedLibraryTrack =
        ImportedLibraryTrack(
            id = id,
            provider = provider,
            providerTrackId = id,
            title = title,
            artist = artist,
            importedAt = 1L
        )
}
