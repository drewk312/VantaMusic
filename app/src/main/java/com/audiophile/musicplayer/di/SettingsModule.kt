package com.audiophile.musicplayer.di

import android.content.Context
import com.audiophile.musicplayer.AppContainer
import com.audiophile.musicplayer.ResolverConfigStore
import com.audiophile.musicplayer.data.source.external.ExternalSourceConfigStore
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Provides settings-related singletons: [ResolverConfigStore] and
 * [ExternalSourceConfigStore].
 *
 * These are light SharedPreferences wrappers and safe to construct
 * without additional dependencies.
 */
@Module
@InstallIn(SingletonComponent::class)
object SettingsModule {

    @Provides
    @Singleton
    fun provideResolverConfigStore(
        @ApplicationContext context: Context
    ): ResolverConfigStore = ResolverConfigStore(context)

    @Provides
    @Singleton
    fun provideExternalSourceConfigStore(
        @ApplicationContext context: Context
    ): ExternalSourceConfigStore = ExternalSourceConfigStore(context)
}
