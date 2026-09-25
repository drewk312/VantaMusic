package com.audiophile.musicplayer.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.audiophile.musicplayer.data.source.NewReleaseFeedStatus
import com.audiophile.musicplayer.data.source.SearchItemStatus
import com.audiophile.musicplayer.data.source.SourceSearchResult
import com.audiophile.musicplayer.ui.theme.VantaTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CriticalPlaybackUiTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun newFeedError_isVisibleAndRetryable() {
        var retried = false
        compose.setContent {
            VantaTheme {
                NewScreen(
                    localPlaylists = emptyList(),
                    localSongs = emptyList(),
                    editorialReleases = emptyList(),
                    editorialReleasesLoading = false,
                    editorialReleasesStatus = NewReleaseFeedStatus.ERROR,
                    editorialReleasesError = "The live release feed is temporarily unavailable.",
                    editorialReleasesUpdatedAtMs = null,
                    onLoadEditorialReleases = { retried = true },
                    onOpenPlaylist = {},
                    onOpenImportFromLink = {},
                    onNavigateToAlbum = { _, _, _ -> },
                    onPlayRelease = {},
                    onPlayTodaysDrop = {},
                    onShuffleTodaysDrop = {},
                    onSaveTodaysDrop = {}
                )
            }
        }

        compose.onNodeWithText("The release feed missed a beat").assertIsDisplayed()
        compose.onNodeWithText("Try again").performClick()
        compose.runOnIdle { assertTrue(retried) }
    }

    @Test
    fun newFeedContent_exposesPlayableTrack() {
        compose.setContent {
            VantaTheme {
                NewScreen(
                    localPlaylists = emptyList(),
                    localSongs = emptyList(),
                    editorialReleases = listOf(sampleRelease()),
                    editorialReleasesLoading = false,
                    editorialReleasesStatus = NewReleaseFeedStatus.CONTENT,
                    editorialReleasesError = null,
                    editorialReleasesUpdatedAtMs = System.currentTimeMillis(),
                    onLoadEditorialReleases = {},
                    onOpenPlaylist = {},
                    onOpenImportFromLink = {},
                    onNavigateToAlbum = { _, _, _ -> },
                    onPlayRelease = {},
                    onPlayTodaysDrop = {},
                    onShuffleTodaysDrop = {},
                    onSaveTodaysDrop = {}
                )
            }
        }

        compose.onNodeWithText("Discovery Queue").assertIsDisplayed()
        compose.onNodeWithContentDescription("Play Fresh Song").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun bufferingTransport_announcesPreparationInsteadOfPause() {
        compose.setContent {
            PremiumTransportButton(
                isPrimary = true,
                isPlaying = false,
                isLoading = true,
                onClick = {},
                contentDescription = "Preparing track"
            )
        }

        compose.onNodeWithContentDescription("Preparing track").assertIsDisplayed()
    }

    private fun sampleRelease() = SourceSearchResult(
        id = "deezer:1",
        providerId = "cloudflare_gateway",
        title = "Fresh Song",
        artist = "New Artist",
        album = "Fresh Album",
        coverSeed = "fresh-song",
        durationMs = 180_000L,
        status = SearchItemStatus.SOURCE_FOUND,
        qualityLabel = "Catalog metadata"
    )
}
