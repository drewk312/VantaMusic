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

        // Relevance-ordered catalog evidence resolves the leading recording and
        // keeps unrelated same-title performers out of the artist/song rails.
        assertEquals(listOf("Jack Harlow"), response.artists.map { it.name })
        assertEquals(listOf("Jack Harlow"), response.songs.map { it.artist })
        assertTrue(response.artists.none { it.name.equals("Denver", ignoreCase = true) })
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

    @Test
    fun whiskyLullabySearch_resolvesCorrectSongAndTopResult() {
        val query = "whisky lullably by brad paisley"

        // Verify provider query typo correction & stripping of "by"
        val providerQ = UnifiedSearchEngine.providerQuery(query)
        assertEquals("whiskey lullaby brad paisley", providerQ)

        // Verify fallback queries
        val fallbacks = UnifiedSearchEngine.fallbackProviderQueries(query)
        assertTrue(fallbacks.contains("whiskey lullaby brad paisley"))
        assertTrue(fallbacks.contains("whiskey lullaby"))
        assertTrue(fallbacks.contains("brad paisley"))

        val results = listOf(
            CanonicalTrack(
                title = "Whiskey Lullaby (feat. Alison Krauss)",
                artist = "Brad Paisley",
                album = "Mud on the Tires",
                durationMs = 259_000L,
                sourceStatus = SearchItemStatus.SOURCE_FOUND,
                externalTrackId = "deezer:1"
            ),
            CanonicalTrack(
                title = "Mud on the Tires",
                artist = "Brad Paisley",
                album = "Mud on the Tires",
                durationMs = 210_000L,
                sourceStatus = SearchItemStatus.SOURCE_FOUND,
                externalTrackId = "deezer:2"
            ),
            CanonicalTrack(
                title = "Alcohol",
                artist = "Brad Paisley",
                album = "Time Well Wasted",
                durationMs = 290_000L,
                sourceStatus = SearchItemStatus.SOURCE_FOUND,
                externalTrackId = "deezer:3"
            )
        )

        val response = UnifiedSearchEngine.process(query, results)

        assertTrue("Expected songs to not be empty", response.songs.isNotEmpty())
        assertEquals("Whiskey Lullaby (feat. Alison Krauss)", response.songs.first().title)
        assertEquals("Brad Paisley", response.songs.first().artist)
        assertEquals("Whiskey Lullaby (feat. Alison Krauss)", response.topResult?.title)
    }
}
