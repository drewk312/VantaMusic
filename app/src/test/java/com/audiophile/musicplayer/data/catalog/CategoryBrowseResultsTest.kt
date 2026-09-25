package com.audiophile.musicplayer.data.catalog

import com.audiophile.musicplayer.data.canonical.CanonicalTrack
import org.junit.Assert.*
import org.junit.Test

class CategoryBrowseResultsTest {
    @Test fun categoryKeepsSongsWhoseTitlesDoNotNameTheGenre() {
        val track = CanonicalTrack(title = "Birds of a Feather", artist = "Billie Eilish", album = "Hit Me Hard and Soft", genre = "Pop")
        val result = CategoryBrowseResults.from(listOf(track))
        assertEquals(listOf(track), result.songs)
        assertEquals("Hit Me Hard and Soft", result.albums.single().title)
        assertEquals("Billie Eilish", result.artists.single().name)
    }
    @Test fun catalogOrderAndDistinctAlbumIdentitiesArePreserved() {
        val first = CanonicalTrack(title = "One", artist = "A", album = "Home")
        val second = CanonicalTrack(title = "Two", artist = "B", album = "Home")
        val result = CategoryBrowseResults.from(listOf(first, first.copy(title = " one "), second))
        assertEquals(listOf(first, second), result.songs)
        assertEquals(2, result.albums.size)
        assertEquals(2, result.artists.size)
    }
    @Test fun missingAlbumDoesNotInventAnAlbumFromTheSongTitle() {
        val result = CategoryBrowseResults.from(listOf(CanonicalTrack(title = "Song", artist = "Artist"), CanonicalTrack(title = "", artist = "Artist")))
        assertEquals(1, result.songs.size)
        assertTrue(result.albums.isEmpty())
    }
}
