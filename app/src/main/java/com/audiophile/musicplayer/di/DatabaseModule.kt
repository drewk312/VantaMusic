package com.audiophile.musicplayer.di

import android.content.Context
import com.audiophile.musicplayer.data.local.MusicDatabase
import com.audiophile.musicplayer.data.local.TrackDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideTrackDatabase(@ApplicationContext context: Context): TrackDatabase {
        return TrackDatabase.getDatabase(context)
    }

    @Provides
    fun provideTrackDao(database: TrackDatabase) = database.trackDao()

    @Provides
    fun provideResolutionDao(database: TrackDatabase) = database.resolutionDao()

    @Provides
    @Singleton
    fun provideMusicDatabase(@ApplicationContext context: Context): MusicDatabase {
        return MusicDatabase.getDatabase(context)
    }

    @Provides
    fun provideLibraryDao(database: MusicDatabase) = database.libraryDao()

    @Provides
    fun provideLyricsCacheDao(database: MusicDatabase) = database.lyricsCacheDao()

    @Provides
    fun providePersonalizedMixDao(database: MusicDatabase) = database.personalizedMixDao()
}
