package com.audiophile.musicplayer.ui

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.audiophile.musicplayer.playback.NowPlayingState
import com.audiophile.musicplayer.ui.nowplaying.CleanProgressSection
import com.audiophile.musicplayer.ui.theme.VantaTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SeekInteractionTest {
    @get:Rule val compose = createComposeRule()

    @Test fun draggingCommitsOneSeekOnRelease() {
        val seeks = mutableListOf<Long>()
        compose.setContent { VantaTheme {
            CleanProgressSection(NowPlayingState(trackId = "test", durationMs = 200_000, positionMs = 20_000),
                Color.White, onSeekTo = { seeks.add(it) })
        } }
        compose.onNodeWithContentDescription("Seek position").performTouchInput {
            swipe(Offset(width * .1f, centerY), Offset(width * .75f, centerY), 400)
        }
        compose.runOnIdle {
            assertEquals(1, seeks.size)
            assertTrue(seeks.single() in 140_000L..160_000L)
        }
        compose.onNodeWithContentDescription("Seek position").performTouchInput {
            click(Offset(width * .25f, centerY))
        }
        compose.runOnIdle { assertTrue(seeks.last() in 40_000L..60_000L) }
    }

    @Test fun unknownDurationDisablesSeeking() {
        compose.setContent { VantaTheme {
            CleanProgressSection(NowPlayingState(durationMs = 0), Color.White, onSeekTo = { fail("Unknown duration must not seek") })
        } }
        compose.onNodeWithContentDescription("Seek position").assertIsNotEnabled()
    }
}
