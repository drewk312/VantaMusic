package com.audiophile.musicplayer.ui

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.audiophile.musicplayer.playback.NowPlayingState
import com.audiophile.musicplayer.ui.nowplaying.LuxuryControlsRow
import com.audiophile.musicplayer.ui.theme.VantaTheme
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class CompactPlayerRepairTest {
    @get:Rule val compose = createComposeRule()

    @Test fun narrowTransportKeepsEveryPlaybackButtonUsable() {
        var previous = 0; var play = 0; var next = 0
        compose.setContent { VantaTheme {
            Box(Modifier.width(280.dp).background(AppBackgroundBottom).testTag("transport")) {
                LuxuryControlsRow(false, true, true,
                    onPrevious = { previous++ }, onTogglePlayPause = { play++ }, onNext = { next++ })
            }
        } }
        for (label in listOf("Previous track", "Play", "Next track")) {
            compose.onNodeWithContentDescription(label).assertIsDisplayed().assertWidthIsAtLeast(48.dp).performClick()
        }
        compose.runOnIdle { assertEquals(1, previous); assertEquals(1, play); assertEquals(1, next) }
        capture("transport", "compact-transport")
    }

    @Test fun miniPlayerKeepsMetadataSeparateFromFunctionalControls() {
        var opened = 0; var play = 0; var next = 0
        compose.setContent { VantaTheme {
            Box(Modifier.width(320.dp).testTag("mini")) {
                MiniPlayer(NowPlayingState(title = "Kryptonite", artist = "3 Doors Down",
                    album = "The Better Life", isPlaying = false, positionMs = 60000, durationMs = 230000),
                    onOpen = { opened++ }, onTogglePlayPause = { play++ }, onNext = { next++ })
            }
        } }
        compose.onNodeWithText("Kryptonite").assertIsDisplayed().performClick()
        compose.onNodeWithText("3 Doors Down").assertIsDisplayed()
        compose.onNodeWithContentDescription("Play").assertIsDisplayed().performClick()
        compose.onNodeWithContentDescription("Next").assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals(1, opened); assertEquals(1, play); assertEquals(1, next) }
        capture("mini", "compact-mini-player")
    }

    @Test fun resetSoundClearsSavedEffectsAndEqSwitchPersists() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val prefs = com.audiophile.musicplayer.playback.dsp.VantaEqualizerPreferences(context)
        val original = prefs.load()
        try {
            prefs.save(original.copy(eqEnabled = true, spatialEnabled = true, tubeEnabled = true,
                bassCannonEnabled = true, trebleEnabled = true, reverbEnabled = true,
                eqBands = List(31) { 6f }))
            compose.setContent { VantaTheme { ParametricEqScreen(onBack = {}) } }
            compose.onNodeWithText("Reset sound").performClick()
            compose.runOnIdle {
                val reset = prefs.load()
                org.junit.Assert.assertFalse(reset.eqEnabled || reset.spatialEnabled || reset.tubeEnabled ||
                    reset.bassCannonEnabled || reset.trebleEnabled || reset.reverbEnabled)
                org.junit.Assert.assertTrue(reset.eqBands.all { it == 0f })
            }
            compose.onNodeWithContentDescription("Enable equalizer").performClick()
            compose.runOnIdle { org.junit.Assert.assertTrue(prefs.load().eqEnabled) }
        } finally { prefs.save(original) }
    }

    private fun capture(tag: String, name: String) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val directory = File(context.getExternalFilesDir(null), "audio-ui-checks").apply { mkdirs() }
        File(directory, "$name.png").outputStream().use {
            compose.onNodeWithTag(tag).captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }
}
