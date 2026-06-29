package com.audiophile.musicplayer.di

import android.app.Application
import com.audiophile.musicplayer.AppContainer
import com.audiophile.musicplayer.appContainer
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Migration bridge: exposes the legacy [AppContainer] as a Hilt-managed singleton.
 * This lets ViewModels migrate incrementally off manual factories. Once every
 * dependency inside [AppContainer] is provided by dedicated Hilt modules, this
 * module (and the container) can be deleted.
 */
@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideAppContainer(application: Application): AppContainer {
        return application.appContainer
    }
}
