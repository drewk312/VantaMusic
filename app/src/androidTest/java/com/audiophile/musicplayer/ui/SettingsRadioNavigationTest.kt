package com.audiophile.musicplayer.ui

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.audiophile.musicplayer.MainActivity
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Real navigation, including the outgoing AnimatedContent screen that caused the crash. */
@RunWith(AndroidJUnit4::class)
class SettingsRadioNavigationTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun settingsEqualizerAndRadioCanBeLeftAndReopened() {
        compose.waitUntil(30_000) {
            compose.onAllNodesWithContentDescription("Settings").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithContentDescription("Settings").performClick()
        compose.onNodeWithText("Equalizer").performScrollTo()
        compose.onNodeWithText("Equalizer").performClick()
        compose.waitUntil(5_000) {
            runCatching { compose.onNodeWithText("Reset sound").assertIsDisplayed() }.isSuccess
        }
        val prefs = com.audiophile.musicplayer.playback.dsp.VantaEqualizerPreferences(compose.activity)
        val original = prefs.load().eqEnabled
        compose.onNodeWithContentDescription("Enable equalizer").performScrollTo().performClick()
        compose.runOnIdle { org.junit.Assert.assertEquals(!original, prefs.load().eqEnabled) }
        compose.onNodeWithContentDescription("Enable equalizer").performClick()
        compose.runOnIdle { org.junit.Assert.assertEquals(original, prefs.load().eqEnabled) }
        compose.onNodeWithText("‹  Back").performScrollTo().performClick()
        compose.onNodeWithText("Equalizer").assertExists()
        compose.onNodeWithText("Done").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Radio", substring = false).performClick()
        compose.onNodeWithText("Live DJ").assertExists()
        compose.onNodeWithContentDescription("Home", substring = false).performClick()
        compose.onNodeWithContentDescription("Settings").assertIsDisplayed()
        compose.onNodeWithContentDescription("Radio", substring = false).performClick()
        compose.onNodeWithText("Live DJ").assertExists()
    }
}
