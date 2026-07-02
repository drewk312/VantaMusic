package com.audiophile.musicplayer.search

import com.audiophile.musicplayer.data.canonical.CanonicalTrack
import com.audiophile.musicplayer.data.source.SearchItemStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchRankerSurgicalTest {

    private fun track(title: String, artist: String, status: SearchItemStatus? = null) = CanonicalTrack(
        title = title,
        artist = artist,
        sourceStatus = status
    )

    @Test
    fun denverSongSearch_doesNotCreateFakeArtist() {
        // Mock gateway results for "Denver" that only return tracks
        val results = listOf(
            track("Denver", "Jack Harlow", SearchItemStatus.SOURCE_FOUND),
            track("Denver", "Willie Nelson", SearchItemStatus.SOURCE_FOUND),
            track("Inside Denver's Homeless Crisis", "Tommy G", SearchItemStatus.SOURCE_FOUND)
        )

        val response = UnifiedSearchEngine.process("Denver", results)

        // Artists should include all unique artists from search results
        assertEquals("Artists should include Jack Harlow", "Jack Harlow", response.artists[0].name)
        assertEquals("Artists should include Willie Nelson", "Willie Nelson", response.artists[1].name)
        assertEquals("Songs section should have tracks", 3, response.songs.size)
    }

    @Test
    fun exactSongIntent_isShownBeforeAlbumTracks() {
        val results = listOf(
            CanonicalTrack(
                title = "Ain't No Crime",
                artist = "Billy Joel",
                album = "Piano Man",
                durationMs = 215_000L,
                sourceStatus = SearchItemStatus.SOURCE_FOUND
            ),
            CanonicalTrack(
                title = "Captain Jack",
                artist = "Billy Joel",
                album = "Piano Man",
                durationMs = 430_000L,
                sourceStatus = SearchItemStatus.SOURCE_FOUND
            ),
            CanonicalTrack(
                title = "Piano Man",
                artist = "Billy Joel",
                album = "Piano Man",
                durationMs = 336_000L,
                sourceStatus = SearchItemStatus.SOURCE_FOUND
            )
        )

        val response = UnifiedSearchEngine.process("piano man", results)

        assertEquals("Piano Man", response.songs.first().title)
        assertEquals("Billy Joel", response.songs.first().artist)
        assertEquals(response.songs.first(), response.topResult)
    }
}
