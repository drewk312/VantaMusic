package com.audiophile.musicplayer.di

import android.app.Application
import com.audiophile.musicplayer.AppContainer
import com.audiophile.musicplayer.appContainer
import com.audiophile.musicplayer.account.AccountManager
import com.audiophile.musicplayer.data.dj.AiDjNarrationGenerator
import com.audiophile.musicplayer.data.dj.AiDjQueuePlanner
import com.audiophile.musicplayer.data.dj.AiDjSessionManager
import com.audiophile.musicplayer.data.lyrics.LyricsRepository
import com.audiophile.musicplayer.data.lyrics.LyricsTranslationProvider
import com.audiophile.musicplayer.data.llm.PulseAiBrain
import com.audiophile.musicplayer.discovery.personalized.PersonalizedMixManager
import com.audiophile.musicplayer.discovery.personalized.PersonalizedMixPlayback
import com.audiophile.musicplayer.discovery.personalized.PersonalizedMixRegistry
import com.audiophile.musicplayer.playback.NowPlayingStateStore
import com.audiophile.musicplayer.playback.PlaybackStateHolder
import com.audiophile.musicplayer.playback.PlayerController
import com.audiophile.musicplayer.data.voice.PulseVoiceEngine
import com.audiophile.musicplayer.radio.LiveRadioTrackLibrary
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Migration bridge: exposes the legacy [AppContainer] as a Hilt-managed singleton,
 * plus individual dependencies so that ViewModels can be migrated incrementally.
 */
@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideAppContainer(application: Application): AppContainer {
        return application.appContainer
    }

    @Provides
    fun providePlayerController(container: AppContainer): PlayerController = container.playerController

    @Provides
    fun provideNowPlayingStateStore(container: AppContainer): NowPlayingStateStore = container.nowPlayingStateStore

    @Provides
    fun providePlaybackStateHolder(container: AppContainer): PlaybackStateHolder = container.playbackStateHolder

    @Provides
    fun provideLyricsRepository(container: AppContainer): LyricsRepository = container.lyricsRepository

    @Provides
    fun provideLyricsTranslationProvider(container: AppContainer): LyricsTranslationProvider = container.lyricsTranslationProvider

    @Provides
    fun providePulseAiBrain(container: AppContainer): PulseAiBrain = container.pulseAiBrain

    @Provides
    fun provideAiDjSessionManager(container: AppContainer): AiDjSessionManager = container.aiDjSessionManager

    @Provides
    fun provideAiDjNarrationGenerator(container: AppContainer): AiDjNarrationGenerator = container.aiDjNarrationGenerator

    @Provides
    fun provideAiDjQueuePlanner(container: AppContainer): AiDjQueuePlanner = container.aiDjQueuePlanner

    @Provides
    fun provideAccountManager(container: AppContainer): AccountManager = container.accountManager

    @Provides
    fun providePulseVoiceEngine(container: AppContainer): PulseVoiceEngine = container.pulseVoiceEngine

    @Provides
    fun provideLiveRadioTrackLibrary(container: AppContainer): LiveRadioTrackLibrary = container.liveRadioTrackLibrary

    @Provides
    fun providePersonalizedMixManager(container: AppContainer): PersonalizedMixManager = container.personalizedMixManager

    @Provides
    fun providePersonalizedMixRegistry(container: AppContainer): PersonalizedMixRegistry = container.personalizedMixRegistry

    @Provides
    fun providePersonalizedMixPlayback(container: AppContainer): PersonalizedMixPlayback = container.personalizedMixPlayback

    @Provides
    fun provideQueueManager(container: AppContainer): com.audiophile.musicplayer.playback.QueueManager = container.queueManager

    @Provides
    fun provideTrackRepository(container: AppContainer): com.audiophile.musicplayer.data.repository.TrackRepository = container.trackRepository

    @Provides
    fun provideLocalLibraryRepository(container: AppContainer): com.audiophile.musicplayer.data.repository.LocalLibraryRepository = container.localLibraryRepository

    @Provides
    fun provideSourceRegistry(container: AppContainer): com.audiophile.musicplayer.data.source.SourceRegistry = container.sourceRegistry
}
