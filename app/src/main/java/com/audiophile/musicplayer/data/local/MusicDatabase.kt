package com.audiophile.musicplayer.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.audiophile.musicplayer.data.local.entities.ImportBatchEntity
import com.audiophile.musicplayer.data.local.entities.ImportMatchHistoryEntity
import com.audiophile.musicplayer.data.local.entities.ImportedTrackEntity
import com.audiophile.musicplayer.data.local.entities.LocalSongEntity
import com.audiophile.musicplayer.data.local.entities.PlaylistEntity
import com.audiophile.musicplayer.data.local.entities.PlaylistSongCrossRef
import com.audiophile.musicplayer.data.local.entities.CachedMetadataEntity
import com.audiophile.musicplayer.data.local.entities.PersonalizedMixEntity
import com.audiophile.musicplayer.data.local.entities.PersonalizedMixTrackEntity
import com.audiophile.musicplayer.data.local.entities.PersonalizedMixHistoryEntity
import com.audiophile.musicplayer.data.local.entities.DiscoveryArtistCacheEntity
import com.audiophile.musicplayer.data.local.entities.DiscoveryTrackCacheEntity
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.audiophile.musicplayer.data.lyrics.LyricsCacheEntity

@Database(
    entities = [
        LocalSongEntity::class,
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
        DiscoveryTrackCacheEntity::class
    ],
    version = 7,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class MusicDatabase : RoomDatabase() {
    abstract fun libraryDao(): LibraryDao
    abstract fun lyricsCacheDao(): com.audiophile.musicplayer.data.lyrics.LyricsCacheDao
    abstract fun personalizedMixDao(): PersonalizedMixDao

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

        private const val DB_NAME = "audiophile_music_library.db"
        fun getDatabase(context: Context): MusicDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: buildDatabase(context.applicationContext).also { INSTANCE = it }
            }
        }

        private fun buildDatabase(appContext: Context): MusicDatabase =
            Room.databaseBuilder(appContext, MusicDatabase::class.java, DB_NAME)
                .addMigrations(MIGRATION_5_6, MIGRATION_6_7)
                .build()
    }
}
