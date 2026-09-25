package com.audiophile.musicplayer.data.display

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DisplayMetadataCleanerTest {

    @Test
    fun `feat artist is appended to display artist`() {
        val result = DisplayMetadataCleaner.computeDisplayMetadata(
            rawTitle = "Song Title (feat. Lil Wayne)",
            rawArtist = "The Artist",
            rawAlbum = null
        )
        assertEquals("Song Title", result.title)
        assertTrue("expected featured artist in display, got: ${result.artist}", result.artist.contains("Lil Wayne"))
        assertTrue("expected 'feat.' prefix, got: ${result.artist}", result.artist.contains("feat."))
        assertEquals(listOf("Lil Wayne"), result.featuredArtists)
    }

    @Test
    fun `multiple featured artists are appended`() {
        val result = DisplayMetadataCleaner.computeDisplayMetadata(
            rawTitle = "Track (feat. Jay-Z & Kanye West)",
            rawArtist = "Main Artist",
            rawAlbum = null
        )
        assertTrue(result.artist.contains("Jay-Z"))
        assertTrue(result.artist.contains("Kanye West"))
        assertEquals(listOf("Jay-Z", "Kanye West"), result.featuredArtists)
    }

    @Test
    fun `comma separated catalog credits become featured artists`() {
        val result = DisplayMetadataCleaner.computeDisplayMetadata(
            rawTitle = "Praise The Lord (Da Shine)",
            rawArtist = "A\$AP Rocky, Skepta",
            rawAlbum = null
        )
        assertTrue(result.featuredArtists.contains("Skepta"))
        assertTrue(result.artist.contains("Skepta"))
    }

    @Test
    fun `ampersand collab splits primary and featured like Apple Music`() {
        val result = DisplayMetadataCleaner.computeDisplayMetadata(
            rawTitle = "I Can't Love You Anymore",
            rawArtist = "Ella Langley & Morgan Wallen",
            rawAlbum = "Dandelion"
        )
        assertEquals(listOf("Morgan Wallen"), result.featuredArtists)
        assertTrue(result.artist.startsWith("Ella Langley"))
        assertTrue(result.artist.contains("feat."))
        assertTrue(result.artist.contains("Morgan Wallen"))
    }

    @Test
    fun `simon and garfunkel stay a single duo act`() {
        val result = DisplayMetadataCleaner.computeDisplayMetadata(
            rawTitle = "The Sound of Silence",
            rawArtist = "Simon & Garfunkel",
            rawAlbum = null
        )
        assertEquals("Simon & Garfunkel", result.artist)
        assertTrue(result.featuredArtists.isEmpty())
    }

    @Test
    fun `comma band names stay a single artist`() {
        val result = DisplayMetadataCleaner.computeDisplayMetadata(
            rawTitle = "September",
            rawArtist = "Earth, Wind & Fire",
            rawAlbum = null
        )
        assertEquals("Earth, Wind & Fire", result.artist)
        assertTrue(result.featuredArtists.isEmpty())
    }

    @Test
    fun `lil nas x is not split on x`() {
        val result = DisplayMetadataCleaner.computeDisplayMetadata(
            rawTitle = "Industry Baby (feat. Lil Nas X)",
            rawArtist = "Jack Harlow",
            rawAlbum = null
        )
        assertEquals(listOf("Lil Nas X"), result.featuredArtists)
    }

    @Test
    fun `underscores become spaces in title and artist`() {
        val result = DisplayMetadataCleaner.computeDisplayMetadata(
            rawTitle = "Some_Track_Title",
            rawArtist = "The_Artist_Name",
            rawAlbum = null
        )
        assertEquals("Some Track Title", result.title)
        assertEquals("The Artist Name", result.artist)
    }

    @Test
    fun `youtube channel name does not overwrite real artist`() {
        val result = DisplayMetadataCleaner.computeDisplayMetadata(
            rawTitle = "The Artist - Song Title (Official Audio)",
            rawArtist = "The Artist - Topic",
            rawAlbum = null
        )
        assertEquals("Song Title", result.title)
        assertEquals("The Artist", result.artist)
    }

    @Test
    fun `platform name parsed as artist is rejected`() {
        val result = DisplayMetadataCleaner.computeDisplayMetadata(
            rawTitle = "Spotify - Wrapped",
            rawArtist = "Spotify",
            rawAlbum = null
        )
        assertEquals("Spotify - Wrapped", result.title)
        assertEquals("Spotify", result.artist)
    }

    @Test
    fun `featured artist already in artist is not duplicated`() {
        val result = DisplayMetadataCleaner.computeDisplayMetadata(
            rawTitle = "Song (feat. The Artist)",
            rawArtist = "The Artist",
            rawAlbum = null
        )
        assertEquals("The Artist", result.artist)
        assertTrue(result.featuredArtists.contains("The Artist"))
    }

    @Test
    fun `cleanTitle removes suffixes and underscores`() {
        val title = DisplayMetadataCleaner.cleanTitle("My_Song (Official Audio)")
        assertEquals("My Song", title)
    }

    @Test
    fun `lyric video teaser is not treated as part of song title`() {
        val title = DisplayMetadataCleaner.cleanTitle(
            "Choosin' Texas (Lyrics) \"he's choosing texas i can tell\""
        )

        assertEquals("Choosin' Texas", title)
    }
}
