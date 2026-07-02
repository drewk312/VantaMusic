package com.audiophile.musicplayer.search

import com.audiophile.musicplayer.data.canonical.CanonicalTrack
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchIdentityScorerTest {

    private fun track(title: String, artist: String, album: String? = null) = CanonicalTrack(
        title = title,
        artist = artist,
        album = album,
        sourcePriority = 5
    )

    @Test
    fun badGuy_exactArtistWins() {
        val intent = UnifiedSearchEngine.parse("bad guy")
        val studio = track("bad guy", "Billie Eilish")
        val seo = track("Bad Guy Billie Eilish", "Aiden Yoo")

        val results = UnifiedSearchEngine.rank(intent, listOf(seo, studio))
        val top = results.firstOrNull { it.second.eligibleForTop }?.first ?: results.first().first
        assertEquals("Billie Eilish", top.artist)
    }

    @Test
    fun down_featuredArtistLogic() {
        val intent = UnifiedSearchEngine.parse("Down Jay Sean feat Lil Wayne")
        val correct = track("Down", "Jay Sean", album = "All or Nothing")
        val lilWayneWrong = track("That Ain't Me", "Lil Wayne")
        val cover = track("Down", "Random Cover Artist")

        val results = UnifiedSearchEngine.rank(intent, listOf(lilWayneWrong, cover, correct))
        val top = results.firstOrNull { it.second.eligibleForTop }?.first ?: results.first().first
        assertEquals("Jay Sean", top.artist)
        assertEquals("Down", top.title)
    }

    @Test
    fun artistInTitleDoesNotEqualArtistMatch() {
        val intent = UnifiedSearchEngine.SearchQueryIntent("bad guy", "bad guy", "Billie Eilish", emptyList())
        val evaluation = UnifiedSearchEngine.score(
            intent,
            track("Bad Guy Billie Eilish", "Aiden Yoo")
        )
        assertFalse("Should not be eligible if artist is in title but not primary artist", evaluation.eligibleForTop)
    }

    @Test
    fun wrongTitleCannotWinBecauseArtistMatches() {
        val intent = UnifiedSearchEngine.parse("Down Jay Sean")
        val evaluation = UnifiedSearchEngine.score(intent, track("That Ain't Me", "Lil Wayne"))
        assertFalse(evaluation.eligibleForTop)
    }

    @Test
    fun exactTitleArtistBeatsHigherQualityWrongArtist() {
        val intent = UnifiedSearchEngine.parse("bad guy")
        val correct = UnifiedSearchEngine.score(intent, track("bad guy", "Billie Eilish"))
        val wrong = UnifiedSearchEngine.score(intent, track("bad guy", "Aiden Yoo"))
        assertTrue(correct.finalScore > wrong.finalScore)
        assertTrue(correct.eligibleForTop)
        assertFalse(wrong.eligibleForTop)
    }

    @Test
    fun parseDownJaySean() {
        val intent = UnifiedSearchEngine.parse("Down Jay Sean feat Lil Wayne")
        assertEquals("down", intent.songTitle)
        assertEquals("jay sean", intent.primaryArtist)
        assertTrue(intent.featuredArtists.any { it.contains("lil wayne") })
    }

    @Test
    fun processDownJaySeanCarriesFeaturedArtistToResults() {
        val response = UnifiedSearchEngine.process(
            "Down Jay Sean feat Lil Wayne",
            listOf(
                track("That Ain't Me", "Lil Wayne"),
                track("Down", "Random Cover Artist"),
                track("Down", "Jay Sean", album = "All or Nothing")
            )
        )

        assertEquals("Jay Sean", response.topResult?.artist)
        assertEquals("Down", response.topResult?.title)
        assertTrue(response.topResult?.featuredArtists.orEmpty().any { it.contains("lil wayne") })
        assertTrue(response.songs.first().featuredArtists.any { it.contains("lil wayne") })
    }

    @Test
    fun desertRose_stingBeatsJazzCoverAndUploader() {
        val intent = UnifiedSearchEngine.parse("sting desert rose")
        val correct = track("Desert Rose", "Sting")
        val cover = track("Desert Rose", "The Jazz Quartet")
        val uploader = track("Desert Rose (Official Audio)", "Lyrics Channel")

        val results = UnifiedSearchEngine.rank(intent, listOf(uploader, cover, correct))
        val top = results.firstOrNull { it.second.eligibleForTop }?.first ?: results.first().first
        assertEquals("Sting", top.artist)
        assertEquals("Desert Rose", top.title)
    }

    @Test
    fun victoryLapFive_fredAgainBeatsWrongArtist() {
        val intent = UnifiedSearchEngine.parse("victory lap five")
        val correct = track("Victory Lap Five (feat. Skepta)", "Fred Again..")
        val wrong = track("Victory Lap", "Some Rapper")
        val cover = track("Victory Lap Five", "Cover Band")

        val results = UnifiedSearchEngine.rank(intent, listOf(cover, wrong, correct))
        val top = results.firstOrNull { it.second.eligibleForTop }?.first ?: results.first().first
        assertEquals("Fred Again..", top.artist)
    }

    @Test
    fun wrongArtistWithExactTitle_isRejected() {
        val intent = UnifiedSearchEngine.parse("desert rose sting")
        val evaluation = UnifiedSearchEngine.score(intent, track("Desert Rose", "Jazz Covers Weekly", album = "Smooth Jazz"))
        assertFalse("Uploader with exact title should not be eligible", evaluation.eligibleForTop)
    }

    @Test
    fun uploaderChannel_isHeavilyPenalized() {
        val intent = UnifiedSearchEngine.parse("bad guy")
        val seo = UnifiedSearchEngine.score(intent, track("bad guy", "Lyrics Channel"))
        val studio = UnifiedSearchEngine.score(intent, track("bad guy", "Billie Eilish"))
        assertTrue(studio.finalScore > seo.finalScore)
        assertFalse("Uploader channel should not be eligible for top", seo.eligibleForTop)
    }

    @Test
    fun processDeduplicatesIdenticalSongs() {
        val response = UnifiedSearchEngine.process(
            "sting desert rose",
            listOf(
                track("Desert Rose", "Sting"),
                track("Desert Rose", "Sting"),
                track("Desert Rose", "Sting"),
                track("Desert Rose", "Sting", album = "Brand New Day"),
                track("Desert Rose (Official Audio)", "Sting")
            )
        )
        assertTrue("Too many duplicate Sting results: ${response.songs.size}", response.songs.size <= 2)
    }

    @Test
    fun allOfTheLights_kanyeWestWins() {
        val intent = UnifiedSearchEngine.parse("all of the lights kanye west")
        val correct = track("All of the Lights", "Kanye West")
        val cover = track("All of the Lights", "Piano Tribute Players")
        val seo = track("All of the Lights (Lyrics)", "Lyrics World")

        val results = UnifiedSearchEngine.rank(intent, listOf(seo, cover, correct))
        val top = results.firstOrNull { it.second.eligibleForTop }?.first ?: results.first().first
        assertEquals("Kanye West", top.artist)
        assertEquals("All of the Lights", top.title)
    }
}
