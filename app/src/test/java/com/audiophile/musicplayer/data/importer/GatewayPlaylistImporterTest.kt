package com.audiophile.musicplayer.data.importer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GatewayPlaylistImporterTest {

    @Test
    fun parseSpotifyHttpsPlaylistUrl() {
        val link = GatewayPlaylistImporter.parsePlaylistUrl(
            "https://open.spotify.com/playlist/37i9dQZF1DXcBWIGoYBM5M?si=abc"
        )
        assertNotNull(link)
        assertEquals("spotify", link?.platform)
        assertEquals("37i9dQZF1DXcBWIGoYBM5M", link?.playlistId)
    }

    @Test
    fun parseSpotifyUri() {
        val link = GatewayPlaylistImporter.parsePlaylistUrl("spotify:playlist:37i9dQZF1DX0XUsuxWHRQd")
        assertEquals("spotify", link?.platform)
        assertEquals("37i9dQZF1DX0XUsuxWHRQd", link?.playlistId)
    }

    @Test
    fun parseApplePlaylistUrlWithSlug() {
        val link = GatewayPlaylistImporter.parsePlaylistUrl(
            "https://music.apple.com/us/playlist/todays-hits/pl.f4d106fed2bd41149aaacabb233eb5eb"
        )
        assertNotNull(link)
        assertEquals("apple", link?.platform)
        assertEquals("pl.f4d106fed2bd41149aaacabb233eb5eb", link?.playlistId)
        assertEquals("us", link?.storefront)
        assertEquals("Todays Hits", link?.displayNameHint)
    }

    @Test
    fun rejectsTrackUrls() {
        assertNull(
            GatewayPlaylistImporter.parsePlaylistUrl(
                "https://open.spotify.com/track/6dOtVTDdiauQNBQEDOtlAB"
            )
        )
        assertFalse(
            GatewayPlaylistImporter.isGatewayPlaylistUrl(
                "https://music.apple.com/us/album/hit-me-hard-and-soft/1743395748?i=1743395750"
            )
        )
    }

    @Test
    fun detectsGatewayPlaylistUrls() {
        assertTrue(
            GatewayPlaylistImporter.isGatewayPlaylistUrl(
                "https://open.spotify.com/playlist/37i9dQZF1DXcBWIGoYBM5M"
            )
        )
    }
}
