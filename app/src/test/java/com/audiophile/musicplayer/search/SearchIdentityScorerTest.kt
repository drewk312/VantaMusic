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
}
