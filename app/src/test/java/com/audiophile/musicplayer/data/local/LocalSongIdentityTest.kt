package com.audiophile.musicplayer.data.local

import com.audiophile.musicplayer.data.local.entities.LocalSongEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalSongIdentityTest {

    @Test
    fun prefersIsrcOverTitleArtist() {
        val songs = listOf(
            LocalSongEntity(id = 1, title = "Blinding Lights", artist = "The Weeknd", isrc = "USUG11903892", isFavorite = true),
            LocalSongEntity(id = 2, title = "Blinding Lights", artist = "The Weeknd", isrc = "OTHER", isFavorite = false)
        )
        val match = LocalSongIdentity.findMatchingSong(
            songs = songs,
            isrc = "USUG11903892",
            title = "Wrong Title",
            artist = "Wrong Artist"
        )
        assertEquals(1L, match?.id)
        assertTrue(LocalSongIdentity.isFavorite(songs, "USUG11903892", "Wrong Title", "Wrong Artist"))
    }

    @Test
    fun fallsBackToTitleArtistWhenIsrcMissing() {
        val songs = listOf(
            LocalSongEntity(id = 3, title = "Genesis", artist = "Grimes", isFavorite = false)
        )
        val match = LocalSongIdentity.findMatchingSong(
            songs = songs,
            isrc = null,
            title = "Genesis",
            artist = "Grimes"
        )
        assertEquals(3L, match?.id)
        assertFalse(LocalSongIdentity.isFavorite(songs, null, "Genesis", "Grimes"))
        assertTrue(LocalSongIdentity.isInLibrary(songs, null, "Genesis", "Grimes"))
    }

    @Test
    fun returnsNullWhenNoStableMatch() {
        val songs = listOf(
            LocalSongEntity(id = 4, title = "Genesis", artist = "Grimes")
        )
        assertNull(
            LocalSongIdentity.findMatchingSong(
                songs = songs,
                isrc = "NOPE",
                title = "Different",
                artist = "Artist"
            )
        )
    }
}
