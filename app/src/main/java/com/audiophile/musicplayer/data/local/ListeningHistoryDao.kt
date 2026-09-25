package com.audiophile.musicplayer.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.audiophile.musicplayer.data.local.entities.ListeningHistoryEntity

data class ListeningArtistAggregate(
    val artist: String,
    val playCount: Int,
    val totalMsPlayed: Long
)

data class ListeningTrackAggregate(
    val title: String,
    val artist: String,
    val playCount: Int,
    val totalMsPlayed: Long
)

data class ListeningAlbumAggregate(
    val album: String,
    val artist: String,
    val playCount: Int,
    val totalMsPlayed: Long
)

/** Per-hour play counts for Wrapped-style "listening habits". */
data class ListeningHourAggregate(
    val hourOfDay: Int,
    val playCount: Int,
    val totalMsPlayed: Long
)

data class ListeningYearStats(
    val topArtists: List<ListeningArtistAggregate>,
    val topTracks: List<ListeningTrackAggregate>,
    val topAlbums: List<ListeningAlbumAggregate>,
    val hours: List<ListeningHourAggregate>,
    val totalPlayCount: Int,
    val totalMinutesPlayed: Long,
    val skippedCount: Int,
    val matchCount: Int
)

@Dao
interface ListeningHistoryDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: ListeningHistoryEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entries: List<ListeningHistoryEntity>)

    @Query("SELECT * FROM listening_history ORDER BY started_at DESC LIMIT :limit")
    suspend fun recent(limit: Int): List<ListeningHistoryEntity>

    @Query("""
        SELECT TRIM(artist) AS artist, SUM(play_count) AS playCount, SUM(ms_played) AS totalMsPlayed
        FROM listening_history
        WHERE TRIM(artist) != '' AND skipped = 0 AND ms_played >= 30000
        GROUP BY LOWER(TRIM(artist))
        ORDER BY SUM(ms_played) DESC, MAX(started_at) DESC
        LIMIT :limit
    """)
    suspend fun recommendationArtists(limit: Int): List<ListeningArtistAggregate>

    @Query("SELECT * FROM listening_history WHERE platform = :platform ORDER BY started_at DESC LIMIT :limit")
    suspend fun recentByPlatform(platform: String, limit: Int): List<ListeningHistoryEntity>

    @Query("DELETE FROM listening_history WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM listening_history WHERE platform = :platform")
    suspend fun deleteByPlatform(platform: String)

    @Query("DELETE FROM listening_history WHERE platform = :platform AND started_at >= :startAt AND started_at < :endAt")
    suspend fun deleteByPlatformRange(platform: String, startAt: Long, endAt: Long)

    @Query("SELECT COUNT(*) FROM listening_history")
    suspend fun count(): Int

    @Query("SELECT COUNT(*) FROM listening_history WHERE platform = :platform")
    suspend fun countByPlatform(platform: String): Int

    @Query(
        """
        SELECT * FROM listening_history
        WHERE started_at >= :startAt AND started_at < :endAt
        ORDER BY started_at ASC
        """
    )
    suspend fun rowsBetween(startAt: Long, endAt: Long): List<ListeningHistoryEntity>

    @Query(
        """
        SELECT artist, COUNT(*) AS playCount, SUM(ms_played) AS totalMsPlayed
        FROM listening_history
        WHERE started_at >= :startOfYear AND started_at < :startOfNextYear
        GROUP BY artist
        ORDER BY totalMsPlayed DESC
        LIMIT :limit
        """
    )
    suspend fun topArtists(startOfYear: Long, startOfNextYear: Long, limit: Int): List<ListeningArtistAggregate>

    @Query(
        """
        SELECT title, artist, COUNT(*) AS playCount, SUM(ms_played) AS totalMsPlayed
        FROM listening_history
        WHERE started_at >= :startOfYear AND started_at < :startOfNextYear
        GROUP BY title, artist
        ORDER BY totalMsPlayed DESC
        LIMIT :limit
        """
    )
    suspend fun topTracks(startOfYear: Long, startOfNextYear: Long, limit: Int): List<ListeningTrackAggregate>

    @Query(
        """
        SELECT album, artist, COUNT(*) AS playCount, SUM(ms_played) AS totalMsPlayed
        FROM listening_history
        WHERE started_at >= :startOfYear AND started_at < :startOfNextYear
          AND album IS NOT NULL AND album != ''
        GROUP BY album, artist
        ORDER BY totalMsPlayed DESC
        LIMIT :limit
        """
    )
    suspend fun topAlbums(startOfYear: Long, startOfNextYear: Long, limit: Int): List<ListeningAlbumAggregate>

    @Query(
        """
        SELECT CAST(strftime('%H', started_at / 1000.0, 'unixepoch', 'localtime') AS INTEGER) AS hourOfDay,
               COUNT(*) AS playCount, SUM(ms_played) AS totalMsPlayed
        FROM listening_history
        WHERE started_at >= :startOfYear AND started_at < :startOfNextYear
        GROUP BY hourOfDay
        ORDER BY hourOfDay ASC
        """
    )
    suspend fun playByHour(startOfYear: Long, startOfNextYear: Long): List<ListeningHourAggregate>

    @Query(
        """
        SELECT COUNT(*) AS totalPlayCount, SUM(ms_played) AS totalMsPlayed,
               SUM(CASE WHEN skipped = 1 THEN 1 ELSE 0 END) AS skippedCount
        FROM listening_history
        WHERE started_at >= :startOfYear AND started_at < :startOfNextYear
        """
    )
    suspend fun yearTotals(startOfYear: Long, startOfNextYear: Long): YearTotalsRow

    @Query(
        """
        SELECT COUNT(*) AS matchCount
        FROM listening_history
        WHERE started_at >= :startOfYear AND started_at < :startOfNextYear
          AND ms_played > 0
        """
    )
    suspend fun matchedCount(startOfYear: Long, startOfNextYear: Long): Int
}

data class YearTotalsRow(
    val totalPlayCount: Int,
    val totalMsPlayed: Long,
    val skippedCount: Int
)