package com.audiophile.musicplayer.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.audiophile.musicplayer.data.local.entities.TrackSource
import com.audiophile.musicplayer.data.local.entities.UnifiedTrack
import com.audiophile.musicplayer.data.local.entities.UnifiedTrackWithSources
import com.audiophile.musicplayer.data.local.entities.PlaylistTrackCrossRef

@Dao
interface TrackDao {

    // 1. Try to find the track by exact Artist and Title
    @Query("SELECT * FROM unified_tracks WHERE LOWER(artist_name) = LOWER(:artist) AND LOWER(track_title) = LOWER(:title) LIMIT 1")
    suspend fun findTrackByMetadata(artist: String, title: String): UnifiedTrack?

    @Query("SELECT * FROM unified_tracks WHERE local_library_id = :localLibraryId LIMIT 1")
    suspend fun findTrackByLocalLibraryId(localLibraryId: Long): UnifiedTrack?

    // 2. Insert a new track. Ignore if it somehow duplicates.
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertUnifiedTrack(track: UnifiedTrack): Long

    // 3. Insert the source (the stream link)
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTrackSource(source: TrackSource)

    @Update
    suspend fun updateTrackSource(source: TrackSource)

    @Query("SELECT * FROM track_sources WHERE stream_url = :streamUrl LIMIT 1")
    suspend fun findSourceByStreamUrl(streamUrl: String): TrackSource?

    @Query("SELECT COUNT(*) FROM unified_tracks")
    suspend fun getTrackCount(): Int

    // 4. Fetch the complete, deduplicated track with all its streaming options
    @Transaction
    @Query("SELECT * FROM unified_tracks")
    suspend fun getAllTracksWithSources(): List<UnifiedTrackWithSources>

    @Transaction
    @Query(
        """
        SELECT * FROM unified_tracks
        WHERE LOWER(track_title) LIKE '%' || LOWER(:query) || '%'
           OR LOWER(artist_name) LIKE '%' || LOWER(:query) || '%'
           OR LOWER(COALESCE(album_name, '')) LIKE '%' || LOWER(:query) || '%'
        ORDER BY artist_name ASC, track_title ASC
        LIMIT :limit
        """
    )
    suspend fun searchTracksWithSources(query: String, limit: Int): List<UnifiedTrackWithSources>
    
    @Transaction
    @Query("SELECT * FROM unified_tracks WHERE trackId = :trackId")
    suspend fun getTrackWithSourcesById(trackId: Long): UnifiedTrackWithSources?
    
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAlbum(album: com.audiophile.musicplayer.data.local.entities.Album): Long

    @Query("SELECT albumId FROM albums WHERE album_name = :albumName AND artist_name = :artistName LIMIT 1")
    suspend fun findAlbumId(albumName: String, artistName: String): Long?

    @Transaction
    @Query("SELECT * FROM albums")
    suspend fun getAllAlbumsWithTracks(): List<com.audiophile.musicplayer.data.local.entities.AlbumWithTracks>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPlaylistTrackCrossRef(crossRef: PlaylistTrackCrossRef)

    @Query("DELETE FROM playlist_track_cross_ref WHERE playlistId = :playlistId AND trackId = :trackId")
    suspend fun removeTrackFromPlaylist(playlistId: Long, trackId: Long)

    @Transaction
    @Query(
        """
        SELECT ut.* FROM unified_tracks ut
        INNER JOIN playlist_track_cross_ref pcr ON ut.trackId = pcr.trackId
        WHERE pcr.playlistId = :playlistId
        ORDER BY pcr.position ASC
        """
    )
    suspend fun getOrderedTracksForPlaylist(playlistId: Long): List<UnifiedTrack>

    @Query("UPDATE playlist_track_cross_ref SET position = :newPosition WHERE playlistId = :playlistId AND trackId = :trackId")
    suspend fun updatePlaylistTrackPosition(playlistId: Long, trackId: Long, newPosition: Int)

    @Query("SELECT COALESCE(MAX(position), -1) FROM playlist_track_cross_ref WHERE playlistId = :playlistId")
    suspend fun getLastPlaylistPosition(playlistId: Long): Int

    @Query("UPDATE unified_tracks SET cover_art_url = :coverArtUrl WHERE trackId = :trackId AND (cover_art_url IS NULL OR cover_art_url = '')")
    suspend fun updateCoverArtIfMissing(trackId: Long, coverArtUrl: String)

    @Query(
        """
        UPDATE unified_tracks SET
            album_name = CASE
                WHEN (album_name IS NULL OR album_name = '') AND :albumName IS NOT NULL AND :albumName != ''
                THEN :albumName ELSE album_name END,
            cover_art_url = CASE
                WHEN (cover_art_url IS NULL OR cover_art_url = '') AND :coverArtUrl IS NOT NULL AND :coverArtUrl != ''
                THEN :coverArtUrl ELSE cover_art_url END,
            genre = CASE
                WHEN (genre IS NULL OR genre = '') AND :genre IS NOT NULL AND :genre != ''
                THEN :genre ELSE genre END,
            isrc = CASE
                WHEN (isrc IS NULL OR isrc = '') AND :isrc IS NOT NULL AND :isrc != ''
                THEN :isrc ELSE isrc END,
            duration_ms = CASE
                WHEN duration_ms IS NULL AND :durationMs IS NOT NULL AND :durationMs > 0
                THEN :durationMs ELSE duration_ms END,
            local_library_id = CASE
                WHEN local_library_id IS NULL AND :localLibraryId IS NOT NULL
                THEN :localLibraryId ELSE local_library_id END
        WHERE trackId = :trackId
        """
    )
    suspend fun updateTrackMetadataIfMissing(
        trackId: Long,
        albumName: String?,
        coverArtUrl: String?,
        genre: String?,
        isrc: String?,
        durationMs: Long?,
        localLibraryId: Long?
    )

    @Query("UPDATE unified_tracks SET last_played_at = :timestamp WHERE trackId = :trackId")
    suspend fun updateLastPlayedAt(trackId: Long, timestamp: Long)

    @Transaction
    @Query("SELECT * FROM unified_tracks WHERE last_played_at IS NOT NULL ORDER BY last_played_at DESC LIMIT :limit")
    suspend fun getRecentlyPlayedWithSources(limit: Int): List<UnifiedTrackWithSources>

    @Query("SELECT trackId FROM unified_tracks WHERE last_played_at IS NOT NULL ORDER BY last_played_at DESC LIMIT :limit")
    suspend fun getRecentTrackIds(limit: Int): List<Long>

    @Query("SELECT COUNT(*) > 0 FROM unified_tracks WHERE trackId = :trackId")
    suspend fun trackExists(trackId: Long): Boolean

    // ── Poisoned data cleanup ──────────────────────────────────────────────

    /** Find track IDs whose stream URLs are SoundHelix but whose titles are not demo tracks. */
    @Query("""
        SELECT ts.parentTrackId FROM track_sources ts
        INNER JOIN unified_tracks ut ON ts.parentTrackId = ut.trackId
        WHERE ts.stream_url LIKE '%soundhelix%'
          AND ut.track_title NOT LIKE '%SoundHelix%'
          AND ut.track_title NOT LIKE '%Demo%'
    """)
    suspend fun findPoisonedTrackIds(): List<Long>

    /** Delete all TrackSource rows whose stream URLs point to SoundHelix but whose track is not a demo. */
    @Query("""
        DELETE FROM track_sources WHERE sourceId IN (
            SELECT ts.sourceId FROM track_sources ts
            INNER JOIN unified_tracks ut ON ts.parentTrackId = ut.trackId
            WHERE ts.stream_url LIKE '%soundhelix%'
              AND ut.track_title NOT LIKE '%SoundHelix%'
              AND ut.track_title NOT LIKE '%Demo%'
        )
    """)
    suspend fun deletePoisonedSources()

    /** Update source_type from LOCAL to ADDON for HTTP/HTTPS stream URLs (they are not local files). */
    @Query("UPDATE track_sources SET source_type = 'ADDON' WHERE source_type = 'LOCAL' AND (stream_url LIKE 'http://%' OR stream_url LIKE 'https://%')")
    suspend fun migrateLocalHttpToAddon()

    @Query("DELETE FROM track_sources WHERE parentTrackId = :trackId")
    suspend fun deleteSourcesForTrack(trackId: Long)

    @Query("DELETE FROM playlist_track_cross_ref WHERE trackId = :trackId")
    suspend fun deletePlaylistRefsForTrack(trackId: Long)

    @Query("DELETE FROM unified_tracks WHERE trackId = :trackId")
    suspend fun deleteUnifiedTrack(trackId: Long)
}
