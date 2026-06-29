package com.audiophile.musicplayer.data.source

import com.audiophile.musicplayer.data.local.entities.SourceType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CloudLibraryHelpersTest {

    @Test
    fun sourceTypeForProvider_mapsKnownProvidersWithoutCrossMixing() {
        assertEquals(SourceType.TORBOX, CloudLibraryHelpers.sourceTypeForProvider(CloudLibraryHelpers.TORBOX_PROVIDER_ID))
        assertEquals(SourceType.YOUTUBE_MUSIC, CloudLibraryHelpers.sourceTypeForProvider("youtube_music"))
        assertEquals(SourceType.ADDON, CloudLibraryHelpers.sourceTypeForProvider("deezer"))
        assertEquals(SourceType.ADDON, CloudLibraryHelpers.sourceTypeForProvider("qobuz"))
        assertEquals(SourceType.ADDON, CloudLibraryHelpers.sourceTypeForProvider("tidal_gateway"))
    }

    @Test
    fun isAudioFile_detectsCommonExtensionsAndMime() {
        assertTrue(CloudLibraryHelpers.isAudioFile("track.flac", null))
        assertTrue(CloudLibraryHelpers.isAudioFile("track.mp3", "audio/mpeg"))
        assertFalse(CloudLibraryHelpers.isAudioFile("notes.txt", "text/plain"))
    }

    @Test
    fun parseArtistTitle_splitsDashSeparatedNames() {
        val (artist, title) = CloudLibraryHelpers.parseArtistTitle("Bee Gees - Stayin' Alive.flac", "Album")
        assertEquals("Bee Gees", artist)
        assertEquals("Stayin' Alive", title)
    }
}
