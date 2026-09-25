package com.audiophile.musicplayer.data.canonical

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update

@Dao
interface CanonicalGraphDao {
    // ── Artists ──────────────────────────────────────────────────────────────

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertArtist(artist: CanonicalArtistEntity): Long

    @Update
    suspend fun updateArtist(artist: CanonicalArtistEntity)

    @Query("SELECT * FROM canonical_artists WHERE artistId = :artistId LIMIT 1")
    suspend fun artistById(artistId: Long): CanonicalArtistEntity?

    @Query("SELECT * FROM canonical_artists WHERE normalized_name = :normalizedName LIMIT 1")
    suspend fun artistByNormalizedName(normalizedName: String): CanonicalArtistEntity?

    @Query(
        """
        SELECT * FROM canonical_artists
        WHERE normalized_name LIKE :prefix || '%'
           OR lower(canonicalName) LIKE :prefix || '%'
        ORDER BY length(canonicalName) ASC, canonicalName ASC
        LIMIT :limit
        """
    )
    suspend fun artistsMatchingPrefix(prefix: String, limit: Int = 8): List<CanonicalArtistEntity>

    @Query(
        """
        SELECT * FROM canonical_albums
        WHERE normalized_title LIKE :prefix || '%'
           OR lower(title) LIKE :prefix || '%'
        ORDER BY length(title) ASC, title ASC
        LIMIT :limit
        """
    )
    suspend fun albumsMatchingPrefix(prefix: String, limit: Int = 8): List<CanonicalAlbumEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertArtistExternal(identity: ArtistExternalIdentityEntity): Long

    @Query(
        """
        SELECT * FROM canonical_artist_external_ids
        WHERE providerId = :providerId AND externalArtistId = :externalArtistId
        LIMIT 1
        """
    )
    suspend fun artistExternal(providerId: String, externalArtistId: String): ArtistExternalIdentityEntity?

    @Query("SELECT * FROM canonical_artist_external_ids WHERE canonicalArtistId = :artistId")
    suspend fun artistExternals(artistId: Long): List<ArtistExternalIdentityEntity>

    // ── Albums ───────────────────────────────────────────────────────────────

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAlbum(album: CanonicalAlbumEntity): Long

    @Update
    suspend fun updateAlbum(album: CanonicalAlbumEntity)

    @Query("SELECT * FROM canonical_albums WHERE albumId = :albumId LIMIT 1")
    suspend fun albumById(albumId: Long): CanonicalAlbumEntity?

    @Query(
        """
        SELECT * FROM canonical_albums
        WHERE normalized_title = :normalizedTitle
          AND (
            (:artistId IS NOT NULL AND canonicalArtistId = :artistId)
            OR (:artistId IS NULL AND canonicalArtistId IS NULL)
          )
        LIMIT 1
        """
    )
    suspend fun albumByNormalizedTitleArtist(normalizedTitle: String, artistId: Long?): CanonicalAlbumEntity?

    @Query("SELECT * FROM canonical_albums WHERE upc = :upc LIMIT 1")
    suspend fun albumByUpc(upc: String): CanonicalAlbumEntity?

    @Query(
        """
        SELECT * FROM canonical_albums
        WHERE canonicalArtistId = :artistId
        ORDER BY releaseYear DESC, title ASC
        """
    )
    suspend fun albumsForArtist(artistId: Long): List<CanonicalAlbumEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAlbumExternal(identity: AlbumExternalIdentityEntity): Long

    @Query(
        """
        SELECT * FROM canonical_album_external_ids
        WHERE providerId = :providerId AND externalAlbumId = :externalAlbumId
        LIMIT 1
        """
    )
    suspend fun albumExternal(providerId: String, externalAlbumId: String): AlbumExternalIdentityEntity?

    @Query("SELECT * FROM canonical_album_external_ids WHERE canonicalAlbumId = :albumId")
    suspend fun albumExternals(albumId: Long): List<AlbumExternalIdentityEntity>

    // ── Tracks ───────────────────────────────────────────────────────────────

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertTrack(track: CanonicalTrackEntity): Long

    @Update
    suspend fun updateTrack(track: CanonicalTrackEntity)

    @Query("SELECT * FROM canonical_tracks WHERE trackId = :trackId LIMIT 1")
    suspend fun trackById(trackId: Long): CanonicalTrackEntity?

    @Query("SELECT * FROM canonical_tracks WHERE isrc = :isrc LIMIT 1")
    suspend fun trackByIsrc(isrc: String): CanonicalTrackEntity?

    @Query(
        """
        SELECT * FROM canonical_tracks
        WHERE normalized_title = :normalizedTitle
          AND canonicalArtistId = :artistId
        LIMIT 1
        """
    )
    suspend fun trackByNormalizedTitleArtist(normalizedTitle: String, artistId: Long): CanonicalTrackEntity?

    @Query(
        """
        SELECT * FROM canonical_tracks
        WHERE canonicalArtistId = :artistId
        ORDER BY
            CASE WHEN trackNumber IS NULL THEN 1 ELSE 0 END,
            discNumber ASC,
            trackNumber ASC,
            title ASC
        """
    )
    suspend fun tracksForArtist(artistId: Long): List<CanonicalTrackEntity>

    @Query(
        """
        SELECT * FROM canonical_tracks
        WHERE canonicalAlbumId = :albumId
        ORDER BY
            CASE WHEN discNumber IS NULL THEN 1 ELSE 0 END,
            discNumber ASC,
            CASE WHEN trackNumber IS NULL THEN 1 ELSE 0 END,
            trackNumber ASC,
            title ASC
        """
    )
    suspend fun tracksForAlbum(albumId: Long): List<CanonicalTrackEntity>

    @Query("SELECT * FROM canonical_tracks WHERE unifiedTrackId = :unifiedTrackId LIMIT 1")
    suspend fun trackByUnifiedId(unifiedTrackId: Long): CanonicalTrackEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertTrackExternal(identity: TrackExternalIdentityEntity): Long

    @Query(
        """
        SELECT * FROM canonical_track_external_ids
        WHERE providerId = :providerId AND externalTrackId = :externalTrackId
        LIMIT 1
        """
    )
    suspend fun trackExternal(providerId: String, externalTrackId: String): TrackExternalIdentityEntity?

    @Query("SELECT * FROM canonical_track_external_ids WHERE canonicalTrackId = :trackId")
    suspend fun trackExternals(trackId: Long): List<TrackExternalIdentityEntity>

    @Query("SELECT COUNT(*) FROM canonical_artists")
    suspend fun artistCount(): Int

    @Query("SELECT COUNT(*) FROM canonical_albums")
    suspend fun albumCount(): Int

    @Query("SELECT COUNT(*) FROM canonical_tracks")
    suspend fun trackCount(): Int

    @Transaction
    suspend fun upsertArtistExternal(
        canonicalArtistId: Long,
        providerId: String,
        externalArtistId: String,
        externalName: String?,
        confidence: Float
    ) {
        val existing = artistExternal(providerId, externalArtistId)
        if (existing == null) {
            insertArtistExternal(
                ArtistExternalIdentityEntity(
                    canonicalArtistId = canonicalArtistId,
                    providerId = providerId,
                    externalArtistId = externalArtistId,
                    externalName = externalName,
                    confidence = confidence
                )
            )
        }
    }
}
