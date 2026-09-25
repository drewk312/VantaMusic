package com.audiophile.musicplayer.ui

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.test.platform.app.InstrumentationRegistry
import com.audiophile.musicplayer.data.display.VantaQualityInfo
import com.audiophile.musicplayer.ui.nowplaying.QualityDetailsSheet
import com.audiophile.musicplayer.ui.theme.VantaTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

class AudioQualityExperienceTest {
    @get:Rule val compose = createComposeRule()
    private val quality = VantaQualityInfo(format = "flac", bitrateKbps = 2800,
        sampleRateHz = 96000, bitDepth = 24, isLossless = true, isHiRes = true,
        isPreview = false, isValidated = true, sourceProviderId = "cloudflare_gateway",
        reason = "decoder_measured", label = "Hi-Res Lossless", channels = 2, measured = true)

    @Test fun qualityExplainsTheRecordingAndDetailsAreOptional() {
        var dismissed = false
        compose.setContent { VantaTheme { Box(Modifier.fillMaxSize().background(AppBackgroundBottom)) {
            QualityDetailsSheet(quality, AppAccent, "Birds of a Feather", "Billie Eilish", { dismissed = true })
        } } }
        compose.onNodeWithText("Hi-Res Lossless").assertIsDisplayed()
        compose.onNodeWithText("false").assertDoesNotExist()
        compose.onNodeWithText("File format").assertDoesNotExist()
        capture("quality-overview")
        compose.onNodeWithContentDescription("Show recording details").performScrollTo().performClick()
        compose.onNodeWithText("Stereo").performScrollTo().assertIsDisplayed()
        compose.onNodeWithContentDescription("Close Audio quality").performScrollTo().performClick()
        compose.runOnIdle { assertTrue(dismissed) }
    }

    @Test fun largeTextKeepsTheQualityPanelReadableAndDismissible() {
        compose.setContent { VantaTheme {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 1.5f)) {
                Box(Modifier.fillMaxSize().background(AppBackgroundBottom)) {
                    QualityDetailsSheet(quality, AppAccent, "A long song title that wraps without losing the close control", "Your favorite artist", {})
                }
            }
        } }
        compose.onNodeWithContentDescription("Close Audio quality").assertIsDisplayed()
        capture("quality-large-text")
        compose.onNodeWithContentDescription("Show recording details").performScrollTo().performClick()
        compose.onNodeWithText("Stereo").performScrollTo().assertIsDisplayed()
    }

    @Test fun listeningPreferenceIsAnExplicitChoice() {
        var selection: String? = null
        compose.setContent { VantaTheme {
            AudioQualityPreferenceDialog("auto", { selection = it }, {})
        } }
        capture("quality-preferences")
        compose.onNodeWithText("Lossless", substring = false).performScrollTo().performClick()
        compose.runOnIdle { assertEquals("16", selection) }
    }

    private fun capture(name: String) {
        compose.waitForIdle()
        // Android's dialog-window animation runs outside the Compose test clock.
        Thread.sleep(500)
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val screenshot = instrumentation.uiAutomation.takeScreenshot()
        File(instrumentation.targetContext.getExternalFilesDir(null), "$name.png").outputStream().use {
            screenshot.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        screenshot.recycle()
    }
}
