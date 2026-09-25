package com.audiophile.musicplayer.ui

import android.graphics.Bitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.audiophile.musicplayer.MainActivity
import org.junit.Rule
import org.junit.Test
import java.io.File

class SearchBrowseExperienceTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun categoryOpensCatalogAndRecentCategoryStaysInBrowse() {
        compose.waitUntil(30_000) {
            compose.onAllNodesWithContentDescription("Search", substring = false).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onAllNodesWithContentDescription("Search", substring = false).onFirst().performClick()
        compose.onNodeWithText("Browse Categories").performScrollTo()
        compose.onAllNodesWithText("Pop", substring = false).onLast().performScrollTo().performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithContentDescription("Back to categories").fetchSemanticsNodes().isNotEmpty() }
        compose.waitUntil(40_000) {
            compose.onAllNodesWithText("Albums to explore").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("Albums to explore").assertIsDisplayed()
        capture("browse-pop")
        scrollToText("Artists to explore")
        scrollToText("Songs")
        compose.onAllNodes(hasScrollToNodeAction()).onFirst().performScrollToNode(hasContentDescription("Back to categories"))
        compose.onNodeWithContentDescription("Back to categories").performClick()
        compose.onAllNodesWithText("Pop", substring = false).onFirst().performScrollTo().performClick()
        capture("browse-recent")
        compose.onRoot().printToLog("BROWSE_TEST")
        compose.waitUntil(5_000) { compose.onAllNodesWithContentDescription("Back to categories").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithContentDescription("Radio", substring = false).performClick()
        compose.onNodeWithText("Live DJ").assertExists()
        capture("radio-home")
        scrollToText("Daily Rotation")
        scrollToText("Just Dropped")
        capture("radio-mixes")
    }

    private fun scrollToText(text: String) {
        compose.onAllNodes(hasScrollToNodeAction()).onFirst().performScrollToNode(hasText(text))
        compose.onNodeWithText(text).assertIsDisplayed()
    }

    private fun capture(name: String) {
        compose.waitForIdle()
        Thread.sleep(500)
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val bitmap = instrumentation.uiAutomation.takeScreenshot()
        File(instrumentation.targetContext.getExternalFilesDir(null), "$name.png").outputStream().use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        bitmap.recycle()
    }
}
