package com.audiophile.musicplayer.di

import android.content.Context
import com.audiophile.musicplayer.data.connectors.ConnectedLibraryTokenStore
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Provides encrypted storage for connected-library OAuth tokens.
 */
@Module
@InstallIn(SingletonComponent::class)
object ConnectedLibraryModule {

    @Provides
    @Singleton
    fun provideConnectedLibraryTokenStore(
        @ApplicationContext context: Context
    ): ConnectedLibraryTokenStore = ConnectedLibraryTokenStore(context)
}
