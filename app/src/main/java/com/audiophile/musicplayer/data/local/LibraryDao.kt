package com.audiophile.musicplayer.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.audiophile.musicplayer.data.importer.ImportMatchStatus
import com.audiophile.musicplayer.data.local.entities.ImportBatchEntity
import com.audiophile.musicplayer.data.local.entities.ImportMatchHistoryEntity
import com.audiophile.musicplayer.data.local.entities.ImportedTrackEntity
import com.audiophile.musicplayer.data.local.entities.LocalSongEntity
import com.audiophile.musicplayer.data.local.entities.PlaylistEntity
import com.audiophile.musicplayer.data.local.entities.PlaylistSongCrossRef
import com.audiophile.musicplayer.data.local.entities.CachedMetadataEntity
import kotlinx.coroutines.flow.Flow

data class LibraryCounts(
    val songsCount: Int,
    val favoritesCount: Int,
    val recentlyPlayedCount: Int,
    val playlistsCount: Int,
    val importsCount: Int
)

@Dao
interface LibraryDao {
    @Query("SELECT * FROM local_songs ORDER BY artist COLLATE NOCASE ASC, title COLLATE NOCASE ASC")
    fun allSongs(): Flow<List<LocalSongEntity>>

    @Query("SELECT * FROM local_songs WHERE isFavorite = 1 ORDER BY updatedAt DESC")
    fun favorites(): Flow<List<LocalSongEntity>>

    @Query("SELECT * FROM local_songs WHERE lastPlayedAt IS NOT NULL ORDER BY lastPlayedAt DESC")
    fun recentlyPlayed(): Flow<List<LocalSongEntity>>

    @Query("SELECT * FROM local_playlists ORDER BY updatedAt DESC")
    fun playlists(): Flow<List<PlaylistEntity>>

    @Query("SELECT * FROM local_playlists ORDER BY updatedAt DESC")
    suspend fun playlistsOnce(): List<PlaylistEntity>

    @Query("SELECT * FROM import_batches ORDER BY importedAt DESC")
    fun importBatches(): Flow<List<ImportBatchEntity>>

    @Query("SELECT * FROM import_batches ORDER BY importedAt DESC")
    suspend fun importBatchesOnce(): List<ImportBatchEntity>

    @Query("SELECT * FROM imported_tracks WHERE batchId = :batchId ORDER BY id ASC")
    fun importedTracksByBatch(batchId: Long): Flow<List<ImportedTrackEntity>>

    @Query(
        """
        SELECT
            (SELECT COUNT(*) FROM local_songs) AS songsCount,
            (SELECT COUNT(*) FROM local_songs WHERE isFavorite = 1) AS favoritesCount,
            (SELECT COUNT(*) FROM local_songs WHERE lastPlayedAt IS NOT NULL) AS recentlyPlayedCount,
            (SELECT COUNT(*) FROM local_playlists) AS playlistsCount,
            (SELECT COUNT(*) FROM import_batches) AS importsCount
        """
    )
    fun libraryCounts(): Flow<LibraryCounts>

    @Query("SELECT * FROM local_songs ORDER BY artist COLLATE NOCASE ASC, title COLLATE NOCASE ASC")
    suspend fun allSongsOnce(): List<LocalSongEntity>

    @Query("SELECT * FROM local_songs WHERE id = :songId LIMIT 1")
    suspend fun songById(songId: Long): LocalSongEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSongs(songs: List<LocalSongEntity>): List<Long>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSong(song: LocalSongEntity): Long

    @Query("UPDATE local_songs SET isFavorite = NOT isFavorite, updatedAt = :updatedAt WHERE id = :songId")
    suspend fun toggleFavorite(songId: Long, updatedAt: Long = System.currentTimeMillis())

    @Query("UPDATE local_songs SET lastPlayedAt = :playedAt, updatedAt = :playedAt WHERE id = :songId")
    suspend fun updateRecentlyPlayed(songId: Long, playedAt: Long = System.currentTimeMillis())

    @Query("UPDATE local_songs SET playCount = playCount + 1, lastPlayedAt = :playedAt, updatedAt = :playedAt WHERE id = :songId")
    suspend fun incrementPlayCount(songId: Long, playedAt: Long = System.currentTimeMillis())

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylist(playlist: PlaylistEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylistSongs(crossRefs: List<PlaylistSongCrossRef>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertImportBatch(batch: ImportBatchEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertImportedTracks(tracks: List<ImportedTrackEntity>): List<Long>

    @Update
    suspend fun updateImportedTrack(track: ImportedTrackEntity)

    @Query("DELETE FROM imported_tracks WHERE id = :trackId")
    suspend fun deleteImportedTrack(trackId: Long)

    @Query("DELETE FROM local_songs WHERE id = :songId")
    suspend fun deleteSong(songId: Long)

    // --- Metadata Cache ---
    @Query("SELECT * FROM metadata_cache WHERE matchKey = :key LIMIT 1")
    suspend fun getCachedMetadata(key: String): CachedMetadataEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCachedMetadata(entity: CachedMetadataEntity)

    @Query("SELECT * FROM imported_tracks WHERE batchId = :batchId ORDER BY id ASC")
    suspend fun importedTracksByBatchOnce(batchId: Long): List<ImportedTrackEntity>

    @Query("SELECT * FROM imported_tracks WHERE batchId = :batchId AND matchStatus = :status ORDER BY id ASC")
    suspend fun importedTracksByBatchAndStatus(batchId: Long, status: ImportMatchStatus): List<ImportedTrackEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMatchHistory(history: ImportMatchHistoryEntity)

    @Query(
        """
        SELECT * FROM local_songs
        WHERE LOWER(title) = LOWER(:title)
          AND LOWER(artist) = LOWER(:artist)
        LIMIT 1
        """
    )
    suspend fun findSongByExactTitleArtist(title: String, artist: String): LocalSongEntity?

    @Query(
        """
        SELECT * FROM local_songs
        WHERE isrc IS NOT NULL
          AND LOWER(isrc) = LOWER(:isrc)
        LIMIT 1
        """
    )
    suspend fun findSongByIsrc(isrc: String): LocalSongEntity?

    @Transaction
    suspend fun addSongsToPlaylist(playlistId: Long, songIds: List<Long>) {
        insertPlaylistSongs(
            songIds.mapIndexed { index, songId ->
                PlaylistSongCrossRef(playlistId = playlistId, songId = songId, position = index)
            }
        )
    }

    @Query("""
        SELECT s.* FROM local_songs s
        INNER JOIN playlist_song_cross_ref ref ON ref.songId = s.id
        WHERE ref.playlistId = :playlistId
        ORDER BY ref.position ASC
    """)
    suspend fun getPlaylistSongs(playlistId: Long): List<LocalSongEntity>

    @Query("DELETE FROM local_playlists WHERE id = :playlistId")
    suspend fun deletePlaylist(playlistId: Long)
}
