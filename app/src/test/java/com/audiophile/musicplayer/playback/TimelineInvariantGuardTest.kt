package com.audiophile.musicplayer.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TimelineInvariantGuardTest {

    @Test
    fun emptyQueue_blocksNonZeroIndex() {
        val result = TimelineInvariantGuard.validate(
            queueSize = 0,
            windowCount = 0,
            playerMediaItemCount = 0,
            currentIndex = 8,
            currentTrackId = null
        )
        assertTrue(result.blocked)
        assertEquals(0, result.safeIndex)
    }

    @Test
    fun queueSize1_index1_isClamped() {
        val result = TimelineInvariantGuard.validate(
            queueSize = 1,
            windowCount = 1,
            playerMediaItemCount = 1,
            currentIndex = 1,
            currentTrackId = 42L
        )
        assertTrue(result.blocked)
        assertEquals(0, result.safeIndex)
    }

    @Test
    fun replacingQueue69_startIndex8_isValid() {
        val result = TimelineInvariantGuard.validate(
            queueSize = 69,
            windowCount = 69,
            playerMediaItemCount = 69,
            currentIndex = 8,
            currentTrackId = 100L
        )
        assertFalse(result.blocked)
        assertEquals(8, result.safeIndex)
    }

    @Test
    fun oldQueue6_newIndex8_isBlocked() {
        val result = TimelineInvariantGuard.validate(
            queueSize = 6,
            windowCount = 6,
            playerMediaItemCount = 69,
            currentIndex = 8,
            currentTrackId = 100L
        )
        assertTrue(result.blocked)
        assertEquals(5, result.safeIndex)
    }

    @Test
    fun indexBeyondQueue_isClampedToLast() {
        val result = TimelineInvariantGuard.validate(
            queueSize = 66,
            windowCount = 69,
            playerMediaItemCount = 1,
            currentIndex = 68,
            currentTrackId = null
        )
        assertTrue(result.blocked)
        assertEquals(65, result.safeIndex)
    }
}
