package com.audiophile.musicplayer.data.importer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SoundiizTextParserTest {
    private val sampleBlock = """
        Blinding Lights - The Weeknd
        Taylor Swift - Anti-Hero
        Good 4 U, Olivia Rodrigo
        Dua Lipa, Levitating
        Take On Me by a-ha
        this is a messy line from Soundiiz that should be preserved
    """.trimIndent()

    @Test
    fun parsesKnownSoundiizLineShapesAndPreservesMessyLines() {
        val parsed = SoundiizTextParser.parseBlock(sampleBlock)

        assertEquals(6, parsed.size)

        assertParsedLine(parsed[0], "The Weeknd", "Blinding Lights")
        assertParsedLine(parsed[1], "Taylor Swift", "Anti-Hero")
        assertParsedLine(parsed[2], "Olivia Rodrigo", "Good 4 U")
        assertParsedLine(parsed[3], "Dua Lipa", "Levitating")
        assertParsedLine(parsed[4], "a-ha", "Take On Me")

        assertEquals(
            "this is a messy line from Soundiiz that should be preserved",
            parsed[5].rawLine
        )
        assertTrue(parsed[5].preserved)
        assertNull(parsed[5].artist)
        assertNull(parsed[5].title)
    }

    @Test
    fun parsesTitleDashArtist() {
        val parsed = SoundiizTextParser.parseLine("Song Title - Artist Name")

        assertParsedLine(parsed, "Artist Name", "Song Title")
    }

    @Test
    fun parsesArtistDashTitle() {
        val parsed = SoundiizTextParser.parseLine("Artist Name - Song Title")

        assertParsedLine(parsed, "Artist Name", "Song Title")
    }

    @Test
    fun parsesTitleCommaArtist() {
        val parsed = SoundiizTextParser.parseLine("Song Title, Artist Name")

        assertParsedLine(parsed, "Artist Name", "Song Title")
    }

    @Test
    fun parsesArtistCommaTitle() {
        val parsed = SoundiizTextParser.parseLine("Artist Name, Song Title")

        assertParsedLine(parsed, "Artist Name", "Song Title")
    }

    @Test
    fun parsesTitleByArtist() {
        val parsed = SoundiizTextParser.parseLine("Song Title by Artist Name")

        assertParsedLine(parsed, "Artist Name", "Song Title")
    }

    @Test
    fun preservesMessyLineAsRawText() {
        val parsed = SoundiizTextParser.parseLine("this is a messy line from Soundiiz that should be preserved")

        assertEquals("this is a messy line from Soundiiz that should be preserved", parsed.rawLine)
        assertTrue(parsed.preserved)
        assertNull(parsed.artist)
        assertNull(parsed.title)
    }

    @Test
    fun parsesNumberedAndBulletedListsAndSkipsBlankLines() {
        val parsed = SoundiizTextParser.parseBlock(
            """
            1. Song Title - Artist Name

            - Another Song, Artist Name
            * Take On Me by a-ha
            """.trimIndent()
        )

        assertEquals(3, parsed.size)
        assertParsedLine(parsed[0], "Artist Name", "Song Title")
        assertParsedLine(parsed[1], "Artist Name", "Another Song")
        assertParsedLine(parsed[2], "a-ha", "Take On Me")
    }

    @Test
    fun parsesCommaAlbumColumn() {
        val parsed = SoundiizTextParser.parseLine("Song Title, Artist Name, Album Name")

        assertParsedLine(parsed, "Artist Name", "Song Title")
        assertEquals("Album Name", parsed.album)
    }

    @Test
    fun trimsExtraWhitespace() {
        val parsed = SoundiizTextParser.parseLine("   Song Title   -   Artist Name   ")

        assertParsedLine(parsed, "Artist Name", "Song Title")
    }

    private fun assertParsedLine(line: ParsedPlaylistLine, artist: String, title: String) {
        assertEquals(artist, line.artist)
        assertEquals(title, line.title)
        assertFalse(line.preserved)
    }
}
