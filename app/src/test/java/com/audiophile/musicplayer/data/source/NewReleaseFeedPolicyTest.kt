package com.audiophile.musicplayer.data.source

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId

class NewReleaseFeedPolicyTest {
    private val track = SourceSearchResult(
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

    @Test
    fun freshCache_isContent() {
        val now = 10_000_000L
        val state = NewReleaseFeedPolicy.fromCache(
            NewReleaseCacheEntry(now - 1_000L, listOf(track)),
            now
        )

        assertEquals(NewReleaseFeedStatus.CONTENT, state.status)
        assertNull(state.errorMessage)
    }

    @Test
    fun emptyRefresh_preservesLastSuccessfulFeedAsStale() {
        val current = NewReleaseFeedSnapshot(
            tracks = listOf(track),
            status = NewReleaseFeedStatus.LOADING,
            lastUpdatedAtMs = 5_000L
        )

        val state = NewReleaseFeedPolicy.completed(current, emptyList(), nowMs = 20_000L)

        assertEquals(NewReleaseFeedStatus.STALE, state.status)
        assertEquals(listOf(track), state.tracks)
        assertEquals(5_000L, state.lastUpdatedAtMs)
        assertTrue(state.errorMessage.orEmpty().contains("last successful"))
    }

    @Test
    fun successfulRefresh_deduplicatesAndBecomesContent() {
        val duplicate = track.copy(id = "deezer:2")

        val state = NewReleaseFeedPolicy.completed(
            NewReleaseFeedSnapshot(status = NewReleaseFeedStatus.LOADING),
            listOf(track, duplicate),
            nowMs = 30_000L
        )

        assertEquals(NewReleaseFeedStatus.CONTENT, state.status)
        assertEquals(1, state.tracks.size)
        assertEquals(30_000L, state.lastUpdatedAtMs)
    }

    @Test
    fun emptyRefreshWithoutCache_isError() {
        val state = NewReleaseFeedPolicy.completed(
            NewReleaseFeedSnapshot(status = NewReleaseFeedStatus.LOADING),
            emptyList(),
            nowMs = 30_000L
        )

        assertEquals(NewReleaseFeedStatus.ERROR, state.status)
        assertTrue(state.tracks.isEmpty())
    }

    @Test
    fun dailyEdit_isStableWithinDayAndChangesNextDay() {
        val tracks = (1..12).map { index ->
            track.copy(id = "deezer:$index", title = "Fresh Song $index")
        }
        val utc = ZoneId.of("UTC")
        val firstDay = 1_725_955_200_000L
        val morning = NewReleaseFeedPolicy.dailyEdit(tracks, firstDay, limit = 5, zoneId = utc)
        val evening = NewReleaseFeedPolicy.dailyEdit(tracks, firstDay + 12 * 60 * 60 * 1_000L, limit = 5, zoneId = utc)
        val nextDay = NewReleaseFeedPolicy.dailyEdit(tracks, firstDay + 24 * 60 * 60 * 1_000L, limit = 5, zoneId = utc)

        assertEquals(morning.map { it.id }, evening.map { it.id })
        assertTrue(morning.map { it.id } != nextDay.map { it.id })
    }

    @Test
    fun onlyDatedEditorialRows_areVerifiedRecentReleases() {
        val now = 1_725_955_200_000L
        val verified = track.copy(
            releaseDate = "2024-09-06",
            discoveryKind = "new_release"
        )
        val chart = verified.copy(discoveryKind = "chart")

        assertTrue(NewReleaseFeedPolicy.isVerifiedRecentRelease(verified, now, zoneId = ZoneId.of("UTC")))
        assertTrue(!NewReleaseFeedPolicy.isVerifiedRecentRelease(chart, now, zoneId = ZoneId.of("UTC")))
    }
}
