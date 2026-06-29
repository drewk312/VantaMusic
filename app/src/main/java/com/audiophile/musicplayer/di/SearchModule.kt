package com.audiophile.musicplayer.di

import android.content.Context
import com.audiophile.musicplayer.data.source.SourceRegistry
import com.audiophile.musicplayer.search.SearchRepository
import com.audiophile.musicplayer.data.catalog.CatalogBrowseRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import javax.inject.Singleton

/**
 * Provides search-related singletons: [SearchRepository] and
 * [CatalogBrowseRepository].
 *
 * Both depend on [SourceRegistry] which is provided by [AppContainer]
 * until the full Hilt migration is complete.
 */
@Module
@InstallIn(SingletonComponent::class)
object SearchModule {

    @Provides
    @Singleton
    fun provideSearchRepository(
        trackRepository: com.audiophile.musicplayer.data.repository.TrackRepository,
        sourceRegistry: com.audiophile.musicplayer.data.source.SourceRegistry,
        nowPlayingStateStore: com.audiophile.musicplayer.playback.NowPlayingStateStore,
        metadataResolver: com.audiophile.musicplayer.data.metadata.MetadataResolver
    ): SearchRepository = SearchRepository(trackRepository, sourceRegistry, nowPlayingStateStore, metadataResolver)

    @Provides
    @Singleton
    fun provideCatalogBrowseRepository(): CatalogBrowseRepository = CatalogBrowseRepository()
}
