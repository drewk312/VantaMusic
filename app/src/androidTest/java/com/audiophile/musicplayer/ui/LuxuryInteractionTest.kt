package com.audiophile.musicplayer.ui

import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.audiophile.musicplayer.ui.theme.VantaTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LuxuryInteractionTest {
    @get:Rule val compose = createComposeRule()

    @Test fun playlistBulkSelectionAndDeleteCancellationPreserveUserIntent() {
        var added = emptyList<Long>()
        var deleted = false
        compose.setContent {
            VantaTheme {
                PlaylistDetailScreen(
                    playlistName = "Evening", playlistDescription = null, playlistArtworkUrl = null,
                    tracks = listOf(
                        com.audiophile.musicplayer.data.local.entities.LocalSongEntity(id = 1, title = "One", artist = "Artist"),
                        com.audiophile.musicplayer.data.local.entities.LocalSongEntity(id = 2, title = "Two", artist = "Artist")
                    ),
                    onBack = {}, onPlayAll = {}, onShuffle = {}, onPlayTrack = {}, onDeletePlaylist = { deleted = true },
                    onAddSelected = { ids, destination -> assertNull(destination); added = ids }
                )
            }
        }
        compose.onNodeWithText("Select").performClick()
        compose.onNodeWithText("Select all").performClick()
        compose.onNodeWithText("Add 2 to…").performClick()
        compose.onNodeWithText("Liked Songs").performClick()
        compose.runOnIdle { assertEquals(listOf(1L, 2L), added) }
        compose.onNodeWithContentDescription("Delete playlist").performClick()
        compose.onNodeWithText("Keep playlist").performClick()
        compose.runOnIdle { assertFalse(deleted) }
    }

    @Test fun searchSubmissionClearsFocusAndKeepsQuery() {
        var submitted = false
        compose.setContent {
            var query by remember { mutableStateOf("") }
            VantaTheme {
                SearchScreen(
                    uiState = SearchUiState(query = query), library = emptyList(),
                    onQueryChanged = { query = it }, onSearch = { submitted = true },
                    onSearchQuery = {}, onBrowseCategory = {}, onRememberSearch = {},
                    onRemoveRecentSearch = {}, onClearRecentSearches = {}, onPlay = {},
                    onPlaySourceResult = {}, onSaveSourceResult = {}
                )
            }
        }
        compose.onNode(hasSetTextAction()).performTextInput("Arijit Singh")
        compose.onNode(hasSetTextAction()).assertIsFocused().performImeAction()
        compose.onNode(hasSetTextAction()).assertIsNotFocused().assertTextEquals("Arijit Singh")
        compose.runOnIdle { assertTrue(submitted) }
    }

    @Test fun playlistKeyboardDoneCreatesTrimmedNameAndClosesInput() {
        var created: String? = null
        compose.setContent {
            VantaTheme {
                AddToPlaylistSheet(emptyList(), onCreatePlaylist = { created = it }, onAddToPlaylist = {}, onDismiss = {})
            }
        }
        compose.onNodeWithText("New Playlist").performClick()
        compose.onNode(hasSetTextAction()).performTextInput("  Evening sessions  ")
        compose.onNode(hasSetTextAction()).performImeAction()
        compose.onNode(hasSetTextAction()).assertDoesNotExist()
        compose.runOnIdle { assertEquals("Evening sessions", created) }
    }

    @Test fun navigationAnnouncesSelectedTab() {
        compose.setContent {
            var route by remember { mutableStateOf(AppRoute.Home) }
            VantaTheme { BottomNavBar(route, onRouteSelected = { route = it }) }
        }
        compose.onNodeWithText("Search").performClick()
        compose.onNodeWithText("Search").assertIsSelected()
        compose.onNodeWithText("Home").assertIsNotSelected()
    }
}
