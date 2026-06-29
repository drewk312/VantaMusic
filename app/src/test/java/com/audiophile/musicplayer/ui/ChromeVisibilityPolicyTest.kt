package com.audiophile.musicplayer.ui

import com.audiophile.musicplayer.data.local.entities.UnifiedTrack
import com.audiophile.musicplayer.data.local.entities.UnifiedTrackWithSources
import com.audiophile.musicplayer.playback.NowPlayingState
import com.audiophile.musicplayer.playback.QueueSnapshot
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChromeVisibilityPolicyTest {

    @Test
    fun radioRouteWithCurrentTrack_showsChrome() {
        val has = ChromeVisibilityPolicy.hasPlayableCurrentItem(
            NowPlayingState(trackId = "1", title = "Blinding Lights", artist = "The Weeknd"),
            QueueSnapshot()
        )
        assertTrue(has)
        assertTrue(ChromeVisibilityPolicy.shouldShowMiniPlayer(AppRoute.AiDj, latchedPlayback = true))
        assertTrue(ChromeVisibilityPolicy.shouldShowBottomNav(AppRoute.AiDj))
    }

    @Test
    fun radioRouteLoadingWithCurrentTrack_showsChrome() {
        val has = ChromeVisibilityPolicy.hasPlayableCurrentItem(
            NowPlayingState(isPlaying = true, bufferedMs = 1),
            QueueSnapshot(currentTrack = sampleTrack())
        )
        assertTrue(has)
        assertTrue(ChromeVisibilityPolicy.shouldShowMiniPlayer(AppRoute.AiDj, latchedPlayback = true))
        assertTrue(ChromeVisibilityPolicy.shouldShowBottomNav(AppRoute.AiDj))
    }

    @Test
    fun artistDetailOverlayWithCurrentTrack_showsChrome() {
        assertTrue(
            ChromeVisibilityPolicy.shouldShowMiniPlayer(AppRoute.Home, latchedPlayback = true)
        )
        assertTrue(
            ChromeVisibilityPolicy.shouldShowBottomNav(
                route = AppRoute.Home,
                hasDetailOverlay = true
            )
        )
    }

    @Test
    fun searchRouteWithCurrentTrack_showsChrome() {
        assertTrue(ChromeVisibilityPolicy.shouldShowMiniPlayer(AppRoute.Search, latchedPlayback = true))
        assertTrue(ChromeVisibilityPolicy.shouldShowBottomNav(AppRoute.Search))
    }

    @Test
    fun fullNowPlaying_hidesChrome() {
        assertFalse(ChromeVisibilityPolicy.shouldShowMiniPlayer(AppRoute.NowPlaying, latchedPlayback = true))
        assertFalse(ChromeVisibilityPolicy.shouldShowBottomNav(AppRoute.NowPlaying))
    }

    @Test
    fun noCurrentTrack_hidesMiniPlayer_keepsBottomNavOnMainRoute() {
        assertFalse(
            ChromeVisibilityPolicy.hasPlayableCurrentItem(NowPlayingState(), QueueSnapshot())
        )
        assertFalse(ChromeVisibilityPolicy.shouldShowMiniPlayer(AppRoute.Home, latchedPlayback = false))
        assertTrue(ChromeVisibilityPolicy.shouldShowBottomNav(AppRoute.Home))
    }

    @Test
    fun metadataNullButQueueCurrent_showsMiniPlayer() {
        val has = ChromeVisibilityPolicy.hasPlayableCurrentItem(
            NowPlayingState(),
            QueueSnapshot(currentTrack = sampleTrack(), queueSize = 1)
        )
        assertTrue(has)
        assertTrue(ChromeVisibilityPolicy.shouldShowMiniPlayer(AppRoute.Library, latchedPlayback = true))
    }

    @Test
    fun pausedCurrentTrack_showsMiniPlayer() {
        val has = ChromeVisibilityPolicy.hasPlayableCurrentItem(
            NowPlayingState(trackId = "9", isPlaying = false, title = "bad guy", artist = "Billie Eilish"),
            QueueSnapshot()
        )
        assertTrue(has)
        assertTrue(ChromeVisibilityPolicy.shouldShowMiniPlayer(AppRoute.AiDj, latchedPlayback = true))
    }

    @Test
    fun radioStationOverlay_keepsBottomNav() {
        assertTrue(
            ChromeVisibilityPolicy.shouldShowBottomNav(
                route = AppRoute.AiDj,
                hasRadioStationOverlay = true
            )
        )
    }

    private fun sampleTrack() = UnifiedTrackWithSources(
        track = UnifiedTrack(
            trackId = 1L,
            title = "Blinding Lights",
            artist = "The Weeknd",
            albumName = null,
            coverArtUrl = null
        ),
        sources = emptyList()
    )
}
