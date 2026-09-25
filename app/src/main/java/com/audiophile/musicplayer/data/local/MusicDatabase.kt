package com.audiophile.musicplayer.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.audiophile.musicplayer.data.local.entities.ImportBatchEntity
import com.audiophile.musicplayer.data.local.entities.ImportMatchHistoryEntity
import com.audiophile.musicplayer.data.local.entities.ImportedTrackEntity
import com.audiophile.musicplayer.data.local.entities.ListeningHistoryEntity
import com.audiophile.musicplayer.data.local.entities.LocalSongEntity
import com.audiophile.musicplayer.data.local.entities.PlaylistEntity
import com.audiophile.musicplayer.data.local.entities.PlaylistSongCrossRef
import com.audiophile.musicplayer.data.local.entities.CachedMetadataEntity
import com.audiophile.musicplayer.data.local.entities.PersonalizedMixEntity
import com.audiophile.musicplayer.data.local.entities.PersonalizedMixTrackEntity
import com.audiophile.musicplayer.data.local.entities.PersonalizedMixHistoryEntity
import com.audiophile.musicplayer.data.local.entities.DiscoveryArtistCacheEntity
import com.audiophile.musicplayer.data.local.entities.DiscoveryTrackCacheEntity
import com.audiophile.musicplayer.data.canonical.AlbumExternalIdentityEntity
import com.audiophile.musicplayer.data.canonical.ArtistExternalIdentityEntity
import com.audiophile.musicplayer.data.canonical.CanonicalAlbumEntity
import com.audiophile.musicplayer.data.canonical.CanonicalArtistEntity
import com.audiophile.musicplayer.data.canonical.CanonicalGraphDao
import com.audiophile.musicplayer.data.canonical.CanonicalTrackEntity
import com.audiophile.musicplayer.data.canonical.TrackExternalIdentityEntity
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.audiophile.musicplayer.data.lyrics.LyricsCacheEntity

@Database(
    entities = [
        LocalSongEntity::class,
        ListeningHistoryEntity::class,
        PlaylistEntity::class,
        PlaylistSongCrossRef::class,
        ImportBatchEntity::class,
        ImportedTrackEntity::class,
        ImportMatchHistoryEntity::class,
        CachedMetadataEntity::class,
        LyricsCacheEntity::class,
        PersonalizedMixEntity::class,
        PersonalizedMixTrackEntity::class,
        PersonalizedMixHistoryEntity::class,
        DiscoveryArtistCacheEntity::class,
        DiscoveryTrackCacheEntity::class,
        CanonicalArtistEntity::class,
        ArtistExternalIdentityEntity::class,
        CanonicalAlbumEntity::class,
        AlbumExternalIdentityEntity::class,
        CanonicalTrackEntity::class,
        TrackExternalIdentityEntity::class,
        com.audiophile.musicplayer.data.local.entities.TrackFeatureEntity::class
    ],
    version = 11,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class MusicDatabase : RoomDatabase() {
    abstract fun libraryDao(): LibraryDao
    abstract fun listeningHistoryDao(): ListeningHistoryDao
    abstract fun lyricsCacheDao(): com.audiophile.musicplayer.data.lyrics.LyricsCacheDao
    abstract fun personalizedMixDao(): PersonalizedMixDao
    abstract fun canonicalGraphDao(): CanonicalGraphDao
    abstract fun trackFeatureDao(): TrackFeatureDao

    companion object {
        @Volatile
        private var INSTANCE: MusicDatabase? = null

        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE local_songs RENAME TO local_songs_old")
                db.execSQL("""
                    CREATE TABLE local_songs (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        title TEXT NOT NULL,
                        artist TEXT NOT NULL,
                        album TEXT,
                        durationMs INTEGER,
                        artworkUrl TEXT,
                        isrc TEXT,
                        explicit INTEGER,
                        genres TEXT NOT NULL,
                        quality TEXT,
                        sourceType TEXT NOT NULL,
                        streamUrl TEXT,
                        isFavorite INTEGER NOT NULL,
                        dateAdded INTEGER NOT NULL,
                        lastPlayedAt INTEGER,
                        playCount INTEGER NOT NULL,
                        importSource TEXT,
                        externalIdsJson TEXT,
                        createdAt INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL
                    )
                """.trimIndent())
                db.execSQL("INSERT INTO local_songs SELECT * FROM local_songs_old")
                db.execSQL("DROP TABLE local_songs_old")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_local_songs_artist_title ON local_songs (artist, title)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_local_songs_isrc ON local_songs (isrc)")
            }
        }

        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS personalized_mixes (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        kind TEXT NOT NULL,
                        variant TEXT NOT NULL,
                        name TEXT NOT NULL,
                        configJson TEXT NOT NULL,
                        trackCount INTEGER NOT NULL,
                        lastGeneratedAt INTEGER,
                        lastGenerationError TEXT,
                        isStale INTEGER NOT NULL,
                        createdAt INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL
                    )
                """.trimIndent())
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS index_personalized_mixes_kind_variant " +
                        "ON personalized_mixes (kind, variant)"
                )
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS personalized_mix_tracks (
                        mixId INTEGER NOT NULL,
                        position INTEGER NOT NULL,
                        track_id INTEGER,
                        title TEXT NOT NULL,
                        artist TEXT NOT NULL,
                        album TEXT,
                        artworkUrl TEXT,
                        providerId TEXT,
                        externalTrackId TEXT,
                        normKey TEXT NOT NULL,
                        PRIMARY KEY(mixId, position),
                        FOREIGN KEY(mixId) REFERENCES personalized_mixes(id) ON DELETE CASCADE
                    )
                """.trimIndent())
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_personalized_mix_tracks_track_id " +
                        "ON personalized_mix_tracks (track_id)"
                )
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS personalized_mix_history (
                        kind TEXT NOT NULL,
                        normKey TEXT NOT NULL,
                        servedAt INTEGER NOT NULL,
                        PRIMARY KEY(kind, normKey, servedAt)
                    )
                """.trimIndent())
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS discovery_artist_cache (
                        artistKey TEXT NOT NULL,
                        relatedArtistsJson TEXT NOT NULL,
                        genresJson TEXT NOT NULL,
                        fetchedAt INTEGER NOT NULL,
                        PRIMARY KEY(artistKey)
                    )
                """.trimIndent())
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS discovery_track_cache (
                        normKey TEXT NOT NULL,
                        title TEXT NOT NULL,
                        artist TEXT NOT NULL,
                        releaseYear INTEGER,
                        providerId TEXT,
                        externalId TEXT,
                        unifiedTrackId INTEGER,
                        fetchedAt INTEGER NOT NULL,
                        PRIMARY KEY(normKey)
                    )
                """.trimIndent())
            }
        }

        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS canonical_artists (
                        artistId INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        canonicalName TEXT NOT NULL,
                        normalized_name TEXT NOT NULL,
                        sortName TEXT,
                        artworkUrl TEXT,
                        heroArtworkUrl TEXT,
                        biography TEXT,
                        genresJson TEXT,
                        country TEXT,
                        formedYear INTEGER,
                        musicBrainzId TEXT,
                        metadataQuality INTEGER NOT NULL,
                        createdAt INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_canonical_artists_normalized_name ON canonical_artists (normalized_name)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_canonical_artists_canonical_name ON canonical_artists (canonicalName)")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS canonical_artist_external_ids (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        canonicalArtistId INTEGER NOT NULL,
                        providerId TEXT NOT NULL,
                        externalArtistId TEXT NOT NULL,
                        externalName TEXT,
                        confidence REAL NOT NULL,
                        lastVerifiedAt INTEGER NOT NULL,
                        FOREIGN KEY(canonicalArtistId) REFERENCES canonical_artists(artistId) ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS index_canonical_artist_external_ids_providerId_externalArtistId " +
                        "ON canonical_artist_external_ids (providerId, externalArtistId)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_canonical_artist_external_ids_canonicalArtistId " +
                        "ON canonical_artist_external_ids (canonicalArtistId)"
                )

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS canonical_albums (
                        albumId INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        title TEXT NOT NULL,
                        normalized_title TEXT NOT NULL,
                        canonicalArtistId INTEGER,
                        albumArtistName TEXT,
                        artworkUrl TEXT,
                        releaseDate TEXT,
                        releaseYear INTEGER,
                        albumType TEXT NOT NULL,
                        genre TEXT,
                        label TEXT,
                        copyright TEXT,
                        trackCount INTEGER,
                        discCount INTEGER,
                        upc TEXT,
                        metadataQuality INTEGER NOT NULL,
                        createdAt INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL,
                        FOREIGN KEY(canonicalArtistId) REFERENCES canonical_artists(artistId) ON DELETE SET NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS index_canonical_albums_normalized_title_canonicalArtistId " +
                        "ON canonical_albums (normalized_title, canonicalArtistId)"
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_canonical_albums_canonicalArtistId ON canonical_albums (canonicalArtistId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_canonical_albums_upc ON canonical_albums (upc)")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS canonical_album_external_ids (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        canonicalAlbumId INTEGER NOT NULL,
                        providerId TEXT NOT NULL,
                        externalAlbumId TEXT NOT NULL,
                        externalTitle TEXT,
                        confidence REAL NOT NULL,
                        lastVerifiedAt INTEGER NOT NULL,
                        FOREIGN KEY(canonicalAlbumId) REFERENCES canonical_albums(albumId) ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS index_canonical_album_external_ids_providerId_externalAlbumId " +
                        "ON canonical_album_external_ids (providerId, externalAlbumId)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_canonical_album_external_ids_canonicalAlbumId " +
                        "ON canonical_album_external_ids (canonicalAlbumId)"
                )

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS canonical_tracks (
                        trackId INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        title TEXT NOT NULL,
                        normalized_title TEXT NOT NULL,
                        artistDisplay TEXT NOT NULL,
                        albumDisplay TEXT,
                        canonicalArtistId INTEGER,
                        canonicalAlbumId INTEGER,
                        isrc TEXT,
                        durationMs INTEGER,
                        discNumber INTEGER,
                        trackNumber INTEGER,
                        explicit INTEGER,
                        releaseDate TEXT,
                        genre TEXT,
                        artworkUrl TEXT,
                        unifiedTrackId INTEGER,
                        localSongId INTEGER,
                        metadataQuality INTEGER NOT NULL,
                        createdAt INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL,
                        FOREIGN KEY(canonicalArtistId) REFERENCES canonical_artists(artistId) ON DELETE SET NULL,
                        FOREIGN KEY(canonicalAlbumId) REFERENCES canonical_albums(albumId) ON DELETE SET NULL
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_canonical_tracks_isrc ON canonical_tracks (isrc)")
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_canonical_tracks_normalized_title_canonicalArtistId " +
                        "ON canonical_tracks (normalized_title, canonicalArtistId)"
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_canonical_tracks_canonicalArtistId ON canonical_tracks (canonicalArtistId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_canonical_tracks_canonicalAlbumId ON canonical_tracks (canonicalAlbumId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_canonical_tracks_unifiedTrackId ON canonical_tracks (unifiedTrackId)")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS canonical_track_external_ids (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        canonicalTrackId INTEGER NOT NULL,
                        providerId TEXT NOT NULL,
                        externalTrackId TEXT NOT NULL,
                        externalArtistId TEXT,
                        externalAlbumId TEXT,
                        isrc TEXT,
                        confidence REAL NOT NULL,
                        lastVerifiedAt INTEGER NOT NULL,
                        FOREIGN KEY(canonicalTrackId) REFERENCES canonical_tracks(trackId) ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS index_canonical_track_external_ids_providerId_externalTrackId " +
                        "ON canonical_track_external_ids (providerId, externalTrackId)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_canonical_track_external_ids_canonicalTrackId " +
                        "ON canonical_track_external_ids (canonicalTrackId)"
                )
            }
        }

        val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE local_songs ADD COLUMN canonicalTrackId INTEGER")
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_local_songs_canonicalTrackId " +
                        "ON local_songs (canonicalTrackId)"
                )
            }
        }

        val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS listening_history (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        started_at INTEGER NOT NULL,
                        title TEXT NOT NULL,
                        artist TEXT NOT NULL,
                        album TEXT,
                        platform TEXT,
                        provider_id TEXT,
                        source_track_id TEXT,
                        ms_played INTEGER NOT NULL,
                        duration_ms INTEGER,
                        play_count INTEGER NOT NULL,
                        skipped INTEGER NOT NULL,
                        reason_start TEXT,
                        reason_end TEXT,
                        recorded_at INTEGER NOT NULL
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS index_listening_history_started_at ON listening_history (started_at)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_listening_history_artist ON listening_history (artist)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_listening_history_title_artist ON listening_history (title, artist)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_listening_history_provider_id ON listening_history (provider_id)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_listening_history_source_track_id ON listening_history (source_track_id)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_listening_history_platform_started_at ON listening_history (platform, started_at)")
            }
        }

        val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS track_features (
                        trackId TEXT PRIMARY KEY NOT NULL,
                        title TEXT NOT NULL,
                        artist TEXT NOT NULL,
                        sub_genres TEXT NOT NULL,
                        energy REAL NOT NULL,
                        valence REAL NOT NULL,
                        danceability REAL NOT NULL,
                        acousticness REAL NOT NULL,
                        updatedAt INTEGER NOT NULL
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS index_track_features_energy ON track_features (energy)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_track_features_danceability ON track_features (danceability)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_track_features_energy_valence ON track_features (energy, valence)")
            }
        }

        private const val DB_NAME = "audiophile_music_library.db"
        fun getDatabase(context: Context): MusicDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: buildDatabase(context.applicationContext).also { INSTANCE = it }
            }
        }

        private fun buildDatabase(appContext: Context): MusicDatabase =
            Room.databaseBuilder(appContext, MusicDatabase::class.java, DB_NAME)
                .addMigrations(MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11)
                .build()
    }
}
