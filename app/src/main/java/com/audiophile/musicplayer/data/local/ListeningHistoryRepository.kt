package com.audiophile.musicplayer.data.local

import com.audiophile.musicplayer.data.local.entities.ListeningHistoryEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneOffset

class ListeningHistoryRepository(
    private val dao: ListeningHistoryDao
) {

    suspend fun recordPlay(
        startedAt: Long,
        title: String,
        artist: String,
        album: String? = null,
        platform: String? = null,
        providerId: String? = null,
        sourceTrackId: String? = null,
        msPlayed: Long,
        durationMs: Long? = null,
        skipped: Boolean = false,
        reasonStart: String? = null,
        reasonEnd: String? = null
    ): Long = withContext(Dispatchers.IO) {
        dao.insert(
            ListeningHistoryEntity(
                startedAt = startedAt,
                title = title,
                artist = artist,
                album = album,
                platform = platform,
                providerId = providerId,
                sourceTrackId = sourceTrackId,
                msPlayed = msPlayed.coerceAtLeast(0L),
                durationMs = durationMs,
                playCount = 1,
                skipped = skipped,
                reasonStart = reasonStart,
                reasonEnd = reasonEnd
            )
        )
    }

    suspend fun recordAll(entries: List<ListeningHistoryEntity>) = withContext(Dispatchers.IO) {
        entries.chunked(500).forEach { dao.insertAll(it) }
    }

    suspend fun recent(limit: Int): List<ListeningHistoryEntity> = withContext(Dispatchers.IO) {
        dao.recent(limit)
    }

    suspend fun recommendationArtists(limit: Int = 12): List<String> = withContext(Dispatchers.IO) {
        dao.recommendationArtists(limit.coerceIn(1, 50)).map { it.artist }
    }

    suspend fun deleteByPlatform(platform: String) = withContext(Dispatchers.IO) {
        dao.deleteByPlatform(platform)
    }

    suspend fun count(platform: String? = null): Int = withContext(Dispatchers.IO) {
        if (platform == null) dao.count() else dao.countByPlatform(platform)
    }

    suspend fun yearStats(year: Int): ListeningYearStats = withContext(Dispatchers.IO) {
        val (start, end) = yearBounds(year)
        val totals = dao.yearTotals(start, end)
        ListeningYearStats(
            topArtists = dao.topArtists(start, end, 100),
            topTracks = dao.topTracks(start, end, 200),
            topAlbums = dao.topAlbums(start, end, 100),
            hours = dao.playByHour(start, end),
            totalPlayCount = totals.totalPlayCount,
            totalMinutesPlayed = totals.totalMsPlayed / 60_000L,
            skippedCount = totals.skippedCount,
            matchCount = dao.matchedCount(start, end)
        )
    }

    suspend fun topArtists(year: Int, limit: Int = 50): List<ListeningArtistAggregate> =
        withContext(Dispatchers.IO) {
            val (start, end) = yearBounds(year)
            dao.topArtists(start, end, limit)
        }

    suspend fun topTracks(year: Int, limit: Int = 100): List<ListeningTrackAggregate> =
        withContext(Dispatchers.IO) {
            val (start, end) = yearBounds(year)
            dao.topTracks(start, end, limit)
        }

    suspend fun deleteByPlatformRange(platform: String, startAt: Long, endAt: Long) =
        withContext(Dispatchers.IO) {
            dao.deleteByPlatformRange(platform, startAt, endAt)
        }

    private fun yearBounds(year: Int): Pair<Long, Long> {
        val start = Instant.parse("$year-01-01T00:00:00Z").atOffset(ZoneOffset.UTC)
        return start.toInstant().toEpochMilli() to
            start.plusYears(1).toInstant().toEpochMilli()
    }
}