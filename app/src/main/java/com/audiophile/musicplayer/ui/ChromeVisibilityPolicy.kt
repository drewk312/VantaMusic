package com.audiophile.musicplayer.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.audiophile.musicplayer.playback.NowPlayingState
import com.audiophile.musicplayer.playback.QueueSnapshot
import kotlinx.coroutines.delay

/**
 * Global chrome visibility — mini-player and bottom nav must not flicker off during
 * metadata refresh, route transitions, or loading states while playback is active.
 */
object ChromeVisibilityPolicy {

    val TAB_ROUTES = setOf(
        AppRoute.Home,
        AppRoute.Discover,
        AppRoute.Library,
        AppRoute.Search,
        AppRoute.AiDj
    )

    fun hasPlayableCurrentItem(
        nowPlaying: NowPlayingState,
        queueSnapshot: QueueSnapshot?,
        activeTrackId: String? = null
    ): Boolean {
        if (nowPlaying.shouldShowMiniPlayer(queueSnapshot)) return true
        if (activeTrackId != null) return true
        return false
    }

    fun shouldShowMiniPlayer(
        route: AppRoute,
        latchedPlayback: Boolean,
        isKeyboardVisible: Boolean = false
    ): Boolean {
        if (route == AppRoute.NowPlaying) return false
        if (isKeyboardVisible) return false
        return latchedPlayback
    }

    fun shouldShowBottomNav(
        route: AppRoute,
        hasDetailOverlay: Boolean = false,
        hasRadioStationOverlay: Boolean = false,
        hasMixOverlay: Boolean = false,
        isKeyboardVisible: Boolean = false
    ): Boolean {
        if (route == AppRoute.NowPlaying) return false
        if (route == AppRoute.Drive) return false
        // Hide bottom nav while the keyboard is open so it never floats above the keyboard
        // or fights for space with insets. Tabs remain reachable by closing the keyboard.
        if (isKeyboardVisible) return false
        if (route in TAB_ROUTES) return true
        if (hasDetailOverlay || hasRadioStationOverlay || hasMixOverlay) return true
        if (route == AppRoute.Settings) return true
        return false
    }

    fun routeLabel(
        route: AppRoute,
        hasDetailOverlay: Boolean,
        hasRadioStationOverlay: Boolean
    ): String = when {
        hasRadioStationOverlay -> "RadioStation"
        hasDetailOverlay -> "Detail"
        else -> route.name
    }
}

/**
 * Sticky playback latch — brief metadata nulls do not hide chrome.
 * Clears only after playback stays absent for [clearDelayMs].
 */
@Composable
fun rememberPlaybackChromeLatch(
    hasPlayableCurrentItem: Boolean,
    clearDelayMs: Long = 800L
): Boolean {
    var latched by remember { mutableStateOf(hasPlayableCurrentItem) }
    LaunchedEffect(hasPlayableCurrentItem) {
        if (hasPlayableCurrentItem) {
            latched = true
        } else {
            delay(clearDelayMs)
            latched = false
        }
    }
    return hasPlayableCurrentItem || latched
}
