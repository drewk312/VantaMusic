package com.audiophile.musicplayer.search

import com.audiophile.musicplayer.data.canonical.CanonicalTrack
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchResultScorerTest {

    @Test
    fun blindingLights_weekndBeatsCovers() {
        val weeknd = track(
            "Blinding Lights",
            "The Weeknd",
            album = "After Hours",
            isrc = "USUG11904206",
            externalTrackId = "catalog:original"
        )
        val yori = track("Blinding Lights", "Yori")
        val piano = track("Blinding Lights Piano Version", "Flying Fingers")

        val ranked = UnifiedSearchEngine.process("blinding lights", listOf(yori, piano, weeknd)).songs

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

    @Test
    fun loseYourselfEminem_soundtrackTitleNotRejectedAndRanksTop() {
        val soundtrackVersion = track(
            title = "Lose Yourself - Music from and Inspired by the Motion Picture",
            artist = "Eminem",
            album = "8 Mile",
            isrc = "USIR20201099"
        )
        val cover = track("Lose Yourself", "Various Artists")

        val response = UnifiedSearchEngine.process("lose yourself eminem", listOf(cover, soundtrackVersion))
        assertTrue("Expected songs to not be empty", response.songs.isNotEmpty())
        assertEquals("Eminem", response.songs.first().artist)
        assertEquals("Eminem", response.topResult?.artist)
    }

    private fun track(
        title: String,
        artist: String,
        album: String? = null,
        isrc: String? = null,
        externalTrackId: String? = null
    ) = CanonicalTrack(
        title = title,
        artist = artist,
        album = album,
        isrc = isrc,
        externalTrackId = externalTrackId,
        sourcePriority = 5
    )
}
