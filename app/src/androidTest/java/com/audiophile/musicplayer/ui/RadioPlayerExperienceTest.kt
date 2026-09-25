package com.audiophile.musicplayer.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.audiophile.musicplayer.playback.NowPlayingState
import com.audiophile.musicplayer.ui.theme.VantaTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class RadioPlayerExperienceTest {
    @get:Rule val compose = createComposeRule()
    @Test fun radioProvidesSongDetailsAndReadablePlaybackState() {
        var opened = false
        compose.setContent { VantaTheme { Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            RadioNowPlayingMoment(NowPlayingState(title = "Blueberry Hill", artist = "Fats Domino", isBuffering = true), { opened = true })
        } } }
        compose.onNodeWithText("Blueberry Hill").assertExists()
        compose.onNodeWithText("Getting your song ready...").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Lyrics & song details").performScrollTo().performClick()
        compose.runOnIdle { assertTrue(opened) }
    }
}
