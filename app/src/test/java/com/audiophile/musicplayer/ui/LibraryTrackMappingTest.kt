package com.audiophile.musicplayer.ui

import com.audiophile.musicplayer.data.local.entities.LocalSongEntity
import com.audiophile.musicplayer.data.local.entities.UnifiedTrack
import com.audiophile.musicplayer.data.local.entities.UnifiedTrackWithSources
import org.junit.Assert.assertEquals
import org.junit.Test

class LibraryTrackMappingTest {
    @Test
    fun localLibraryIdMapsAcrossIndependentDatabasePrimaryKeys() {
        val song = LocalSongEntity(id = 42L, title = "Midnight City", artist = "M83")
        val playbackTrack = track(trackId = 7L, localLibraryId = 42L)

        assertEquals(7L, libraryTrackForSong(song, listOf(playbackTrack))?.track?.trackId)
    }

    @Test
    fun metadataFallbackSupportsLegacyRowsWithoutLocalLibraryId() {
        val song = LocalSongEntity(id = 42L, title = " Midnight City ", artist = "M83")
        val playbackTrack = track(trackId = 7L, localLibraryId = null)

        assertEquals(7L, libraryTrackForSong(song, listOf(playbackTrack))?.track?.trackId)
    }

    private fun track(trackId: Long, localLibraryId: Long?) = UnifiedTrackWithSources(
        track = UnifiedTrack(
            trackId = trackId,
            title = "Midnight City",
            artist = "m83",
            albumName = "Hurry Up, We're Dreaming",
            coverArtUrl = null,
            localLibraryId = localLibraryId
        ),
        sources = emptyList()
    )
}
