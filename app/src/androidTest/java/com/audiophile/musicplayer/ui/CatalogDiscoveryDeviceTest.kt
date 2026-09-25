package com.audiophile.musicplayer.ui

import android.graphics.Bitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.audiophile.musicplayer.MainActivity
import com.audiophile.musicplayer.data.catalog.CatalogBrowseRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.After
import org.junit.Rule
import org.junit.Test
import java.io.File

class CatalogDiscoveryDeviceTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    private var originalPrefs: Map<String, *> = emptyMap<String, Any>()
    private val prefs get() = InstrumentationRegistry.getInstrumentation().targetContext.getSharedPreferences("daily_discovery_v1", android.content.Context.MODE_PRIVATE)
    @Before fun preserveDiscoveryPreferences() { originalPrefs = prefs.all.toMap() }
    @After fun restoreDiscoveryPreferences() {
        val editor = prefs.edit().clear()
        originalPrefs.forEach { (key, value) -> when (value) {
            is String -> editor.putString(key, value)
            is Int -> editor.putInt(key, value)
            is Boolean -> editor.putBoolean(key, value)
            is Set<*> -> editor.putStringSet(key, value.filterIsInstance<String>().toSet())
        } }
        editor.commit()
    }

    @Test fun catalogCategoriesReturnDistinctRealMusic() = runBlocking {
        val repository = CatalogBrowseRepository()
        val sets = com.audiophile.musicplayer.data.catalog.BrowseCatalog.categories.map { it.title }.associateWith { name ->
            repository.browseCategory(name, 12).also { tracks ->
                println("Catalog category: $name, tracks: ${tracks.size}")
                assertTrue("No catalog results for $name", tracks.size >= 3)
                assertTrue(tracks.all { it.title.isNotBlank() && it.artist.isNotBlank() && it.externalTrackId != null })
                assertTrue("Browse labels must not become song genres", tracks.all { it.genre == null })
            }.map { it.artist }.toSet()
        }
        assertNotEquals(sets["Pop"], sets["Classical"])
        assertNotEquals(sets["Jazz"], sets["Hindi Bollywood"])
    }

    @Test fun dailyDiscoverLoadsAndRemembersFeedback() {
        compose.waitUntil(30_000) { compose.onAllNodesWithContentDescription("New", substring = false).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithContentDescription("New", substring = false).performClick()
        compose.onNodeWithText("Daily Discover").assertExists()
        if (compose.onAllNodesWithText("Tune your discovery").fetchSemanticsNodes().isNotEmpty()) {
            compose.onNodeWithText("Tune your discovery").performScrollTo().performClick()
        }
        compose.onNodeWithText("Hindi Bollywood", substring = false).performScrollTo().performClick()
        compose.waitUntil(65_000) { compose.onAllNodesWithText("More like this").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("daily-discovery-picks").performScrollTo()
        compose.onAllNodesWithText("More like this").onFirst().performScrollTo()
        capture("daily-before-feedback")
        compose.onAllNodesWithText("More like this").onFirst().performClick()
        capture("daily-after-like")
        compose.onAllNodesWithText("Not for me").onFirst().performScrollTo().performClick()
        capture("daily-after-hide")
        compose.waitUntil(5_000) { prefs.getStringSet("hidden_tracks", emptySet()).orEmpty().isNotEmpty() }
        compose.onNodeWithText("Got it. This song won't return to Daily Discover.").performScrollTo().assertIsDisplayed()
        capture("daily-discover")
    }

    private fun capture(name: String) {
        compose.waitForIdle()
        Thread.sleep(500)
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val bitmap = instrumentation.uiAutomation.takeScreenshot()
        File(instrumentation.targetContext.getExternalFilesDir(null), "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }
}
