package com.audiophile.musicplayer.discovery.personalized

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class ReleaseDateGateTest {
    @Test
    fun releaseDayIsNotFuture() {
        val today = LocalDate.of(2026, 6, 21)
        assertFalse(ReleaseDateGate.isFutureRelease("2026-06-21", today))
    }

    @Test
    fun yearPrecisionFutureYearBlocks() {
        val today = LocalDate.of(2026, 6, 21)
        assertTrue(ReleaseDateGate.isFutureRelease("2027", today))
        assertFalse(ReleaseDateGate.isFutureRelease("2026", today))
    }

    @Test
    fun monthPrecisionFutureMonthBlocks() {
        val today = LocalDate.of(2026, 6, 21)
        assertTrue(ReleaseDateGate.isFutureRelease("2026-07", today))
        assertFalse(ReleaseDateGate.isFutureRelease("2026-06", today))
    }

    @Test
    fun fullDateFutureBlocks() {
        val today = LocalDate.of(2026, 6, 21)
        assertTrue(ReleaseDateGate.isFutureRelease("2026-06-22", today))
        assertFalse(ReleaseDateGate.isFutureRelease("2026-06-20", today))
    }

    @Test
    fun garbageDateDoesNotBlock() {
        val today = LocalDate.of(2026, 6, 21)
        assertFalse(ReleaseDateGate.isFutureRelease("not-a-date", today))
        assertFalse(ReleaseDateGate.isFutureRelease(null, today))
    }

    @Test
    fun invalidMonthFallsBackToYearPrecision() {
        val today = LocalDate.of(2026, 6, 21)
        assertTrue(ReleaseDateGate.isFutureRelease("2027-13-01", today))
    }

    @Test
    fun splitReleasedUnreleasedPartitionsCandidates() {
        val today = LocalDate.of(2026, 6, 21)
        val candidates = listOf(
            MixCandidate(title = "A", artist = "B", releaseDate = "2026-06-21"),
            MixCandidate(title = "C", artist = "D", releaseDate = "2027-01-01")
        )
        val (released, unreleased) = ReleaseDateGate.splitReleasedUnreleased(candidates, today)
        assertEquals(1, released.size)
        assertEquals(1, unreleased.size)
    }

    @Test
    fun remasterTitleIsDetected() {
        val candidate = MixCandidate(
            title = "Song (Remastered 2024)",
            artist = "Artist",
            releaseYear = 1975
        )
        assertTrue(ReleaseDateGate.isLikelyRemasterOrReupload(candidate, 2026))
    }
}
