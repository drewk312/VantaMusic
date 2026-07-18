package com.audiophile.musicplayer.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class NowPlayingChromeStateTest {
    @Test
    fun `progress ticks collapse to the same chrome state`() {
        val first = NowPlayingState(
            trackId = "42",
            title = "Track",
            isPlaying = true,
            positionMs = 1_000,
            bufferedMs = 3_000,
            sleepTimerRemainingMs = 60_000
        )
        val nextTick = first.copy(
            positionMs = 1_100,
            bufferedMs = 3_500,
            sleepTimerRemainingMs = 59_900
        )

        assertNotEquals(first, nextTick)
        assertEquals(first.withoutProgressTicks(), nextTick.withoutProgressTicks())
    }

    @Test
    fun `metadata and transport changes remain visible to chrome`() {
        val playing = NowPlayingState(trackId = "42", title = "Track", isPlaying = true)
        val paused = playing.copy(isPlaying = false)

        assertNotEquals(playing.withoutProgressTicks(), paused.withoutProgressTicks())
    }
}
