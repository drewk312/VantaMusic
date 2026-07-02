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
}
