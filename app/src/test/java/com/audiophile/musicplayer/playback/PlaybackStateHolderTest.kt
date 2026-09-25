package com.audiophile.musicplayer.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackStateHolderTest {

    @Test
    fun replace_normalizesContradictoryPlaybackFlags() {
        val holder = PlaybackStateHolder()

        holder.replace(NowPlayingState(trackId = "42", isPlaying = true, isBuffering = true))

        assertFalse(holder.snapshot().isPlaying)
        assertTrue(holder.snapshot().isBuffering)
        assertEquals(PlaybackPhase.BUFFERING, holder.snapshot().phase)
    }

    @Test
    fun update_normalizesErrorsAndTimelineBounds() {
        val holder = PlaybackStateHolder()
        holder.replace(NowPlayingState(trackId = "42", durationMs = 1_000L))

        holder.update {
            copy(isPlaying = true, positionMs = 4_000L, bufferedMs = 9_000L, errorMessage = "network")
        }

        assertFalse(holder.snapshot().isPlaying)
        assertEquals(1_000L, holder.snapshot().positionMs)
        assertEquals(1_000L, holder.snapshot().bufferedMs)
        assertEquals(PlaybackPhase.ERROR, holder.snapshot().phase)
    }
}
