package com.audiophile.musicplayer.search

import com.audiophile.musicplayer.data.canonical.CanonicalTrack
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchResultScorerTest {

    @Test
    fun blindingLights_weekndBeatsCovers() {
        val weeknd = track("Blinding Lights", "The Weeknd")
        val yori = track("Blinding Lights", "Yori")
        val piano = track("Blinding Lights Piano Version", "Flying Fingers")

        val intent = UnifiedSearchEngine.parse("blinding lights")
        val ranked = listOf(yori, piano, weeknd)
            .sortedByDescending { UnifiedSearchEngine.score(intent, it).finalScore }

        assertEquals("The Weeknd", ranked.first().artist)
    }

    @Test
    fun badGuy_billieEilishBeatsKaraoke() {
        val studio = track("bad guy", "Billie Eilish")
        val karaoke = track("bad guy karaoke", "Karaoke Band")
        val instrumental = track("bad guy instrumental", "Cover Artist")

        val intent = UnifiedSearchEngine.parse("bad guy")
        val ranked = listOf(karaoke, instrumental, studio)
            .sortedByDescending { UnifiedSearchEngine.score(intent, it).finalScore }

        assertEquals("Billie Eilish", ranked.first().artist)
    }

    @Test
    fun stayinAlive_studioBeatsKaraoke() {
        val studio = track("Stayin' Alive", "Bee Gees")
        val live = track("Stayin Alive live", "Bee Gees")
        val karaoke = track("Stayin Alive karaoke", "Karaoke Artist")

        val intent = UnifiedSearchEngine.parse("Stayin Alive")
        val ranked = listOf(karaoke, live, studio)
            .sortedByDescending { UnifiedSearchEngine.score(intent, it).finalScore }

        assertEquals("Bee Gees", ranked.first().artist)
        assertTrue(!ranked.first().title.lowercase().contains("karaoke"))
    }

    @Test
    fun explicitPianoQuery_allowsPianoVersion() {
        val weeknd = track("Blinding Lights", "The Weeknd")
        val piano = track("Blinding Lights Piano Version", "Flying Fingers")

        val intent = UnifiedSearchEngine.parse("blinding lights piano")
        val pianoScore = UnifiedSearchEngine.score(intent, piano).finalScore
        val weekndScore = UnifiedSearchEngine.score(intent, weeknd).finalScore

        assertTrue(pianoScore > weekndScore - 50)
    }

    @Test
    fun missingArtist_isHeavilyPenalized() {
        val bad = track("Blinding Lights", "")
        val good = track("Blinding Lights", "The Weeknd")
        val intent = UnifiedSearchEngine.parse("blinding lights")
        assertTrue(UnifiedSearchEngine.score(intent, good).finalScore > UnifiedSearchEngine.score(intent, bad).finalScore)
    }

    private fun track(title: String, artist: String) = CanonicalTrack(
        title = title,
        artist = artist,
        sourcePriority = 5
    )
}
