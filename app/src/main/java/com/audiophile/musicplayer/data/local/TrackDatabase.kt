package com.audiophile.musicplayer.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.audiophile.musicplayer.data.local.dao.ResolutionDao
import com.audiophile.musicplayer.data.local.dao.TrackDao
import com.audiophile.musicplayer.data.local.entities.AddonProvider
import com.audiophile.musicplayer.data.local.entities.ResolutionCacheEntry
import com.audiophile.musicplayer.data.local.entities.TrackSource
import com.audiophile.musicplayer.data.local.entities.UnifiedTrack

import com.audiophile.musicplayer.data.local.entities.Playlist
import com.audiophile.musicplayer.data.local.entities.PlaylistTrackCrossRef
import android.util.Log
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.audiophile.musicplayer.data.local.entities.Album

@Database(
    entities = [
        UnifiedTrack::class, 
        TrackSource::class,
        Playlist::class,
        PlaylistTrackCrossRef::class,
        Album::class,
        AddonProvider::class,
        ResolutionCacheEntry::class
    ], 
    version = 11,
    exportSchema = false
)
@TypeConverters(RoomConverters::class)
abstract class TrackDatabase : RoomDatabase() {

    abstract fun trackDao(): TrackDao
    abstract fun resolutionDao(): ResolutionDao

    companion object {
        @Volatile
        private var INSTANCE: TrackDatabase? = null

        val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE unified_tracks ADD COLUMN explicit INTEGER DEFAULT NULL")
            }
        }

        private const val DB_NAME = "audiophile_music_db"
        private const val TAG = "TrackDatabase"

        fun getDatabase(context: Context): TrackDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: openDatabase(context.applicationContext).also { INSTANCE = it }
            }
        }

        private fun openDatabase(appContext: Context): TrackDatabase {
            return try {
                buildDatabase(appContext)
            } catch (e: Exception) {
                Log.e(TAG, "Database open failed, deleting and retrying", e)
                INSTANCE = null
                appContext.deleteDatabase(DB_NAME)
                buildDatabase(appContext)
            }
        }

        private fun buildDatabase(appContext: Context): TrackDatabase =
            Room.databaseBuilder(appContext, TrackDatabase::class.java, DB_NAME)
                .addMigrations(MIGRATION_10_11)
                .fallbackToDestructiveMigration()
                .build()
    }
}
