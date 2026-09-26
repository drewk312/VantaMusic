package com.audiophile.musicplayer

import android.content.Context
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.audiophile.musicplayer.security.FailClosedSharedPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicReference
import com.audiophile.musicplayer.data.dj.AiDjNarrationGenerator
import com.audiophile.musicplayer.data.dj.AiDjQueuePlanner
import com.audiophile.musicplayer.data.dj.AiDjRecommendationEngine
import com.audiophile.musicplayer.data.dj.AiDjSessionManager
import com.audiophile.musicplayer.data.dj.CustomStationStore
import com.audiophile.musicplayer.data.dj.DjPersonaMemory
import com.audiophile.musicplayer.data.dj.RadioStationPreviewLoader
import com.audiophile.musicplayer.data.dj.StationTasteMemory
import com.audiophile.musicplayer.data.dj.TrackReleaseYearResolver
import com.audiophile.musicplayer.data.lastfm.LastFmGenreResolver
import com.audiophile.musicplayer.data.lastfm.LastFmScrobbler
import com.audiophile.musicplayer.radio.LiveRadioTrackLibrary
import com.audiophile.musicplayer.data.llm.PulseAiBrain
import com.audiophile.musicplayer.data.importer.LibraryImporter
import com.audiophile.musicplayer.data.importer.EclipsePlaylistImporter
import com.audiophile.musicplayer.data.remote.eclipse.EclipsePlaylistApi
import com.audiophile.musicplayer.data.importer.PlatformLinkResolver
import com.audiophile.musicplayer.data.catalog.CatalogBrowseRepository
import com.audiophile.musicplayer.data.local.DeviceMediaMetadataReader
import com.audiophile.musicplayer.data.local.LocalMediaImporter
import com.audiophile.musicplayer.data.local.MusicDatabase
import com.audiophile.musicplayer.data.canonical.CanonicalMusicResolver
import com.audiophile.musicplayer.data.local.TrackDatabase
import com.audiophile.musicplayer.data.metadata.CompositeMetadataProvider
import com.audiophile.musicplayer.data.remote.ResolverRetrofitFactory
import com.audiophile.musicplayer.data.remote.TorBoxApi
import com.audiophile.musicplayer.data.remote.TorBoxRepository
import com.audiophile.musicplayer.data.repository.LocalLibraryRepository
import com.audiophile.musicplayer.data.repository.TrackRepository
import com.audiophile.musicplayer.data.metadata.MetadataCache
import com.audiophile.musicplayer.data.metadata.MetadataResolver
import com.audiophile.musicplayer.data.metadata.apple.AppleMusicConfig
import com.audiophile.musicplayer.data.metadata.apple.AppleMusicMetadataProvider
import com.audiophile.musicplayer.data.metadata.deezer.DeezerMetadataProvider
import com.audiophile.musicplayer.data.metadata.itunes.ITunesSearchMetadataProvider
import com.audiophile.musicplayer.data.resolution.CachedResolverService
import com.audiophile.musicplayer.data.resolution.ResolutionCacheRepository
import com.audiophile.musicplayer.data.resolution.ResolverBootstrap
import com.audiophile.musicplayer.data.resolution.TorBoxCatalogResolverClient
import com.audiophile.musicplayer.download.AndroidTrackDownloadManager
import com.audiophile.musicplayer.playback.NowPlayingStateStore
import com.audiophile.musicplayer.account.AccountManager
import com.audiophile.musicplayer.data.connectors.ConnectedLibraryManager
import com.audiophile.musicplayer.data.connectors.ConnectedLibraryTokenStore
import com.audiophile.musicplayer.data.connectors.apple.AppleMusicLibraryConnector
import com.audiophile.musicplayer.data.connectors.ConnectedLibraryProvider
import com.audiophile.musicplayer.social.FriendActivityLocalSource
import com.audiophile.musicplayer.social.FriendActivityRepository
import com.audiophile.musicplayer.social.VantaSocialManager
import com.audiophile.musicplayer.sync.SyncIdentityStore
import com.audiophile.musicplayer.sync.VantaSyncManager
import com.audiophile.musicplayer.playback.UpnpCastingManager
import com.audiophile.musicplayer.playback.PlaybackStateHolder
import com.audiophile.musicplayer.playback.PlayerController
import com.audiophile.musicplayer.playback.QueueManager
import com.audiophile.musicplayer.playback.SharedPreferencesQueuePersistence
import com.audiophile.musicplayer.search.SearchRepository
import com.audiophile.musicplayer.data.source.RealDebridMusicSourceProvider
import com.audiophile.musicplayer.data.source.SourceRegistry
import com.audiophile.musicplayer.data.source.TorBoxMusicSourceProvider
import com.audiophile.musicplayer.data.source.external.ExternalSourceConfigStore
import com.audiophile.musicplayer.data.source.external.PlaybackProviderFactory
import com.audiophile.musicplayer.data.rpc.RadioRpcClient
import com.audiophile.musicplayer.data.rpc.RadioRpcService
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class AppContainer(
    context: Context
) {
    private val appContext = context.applicationContext
    private val containerScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val database by lazy { TrackDatabase.getDatabase(appContext) }
    private val musicDatabase by lazy { MusicDatabase.getDatabase(appContext) }
    val resolverConfigStore by lazy { ResolverConfigStore(appContext) }
    val externalSourceConfigStore by lazy {
        ExternalSourceConfigStore(appContext).also { it.ensureDefaultSources() }
    }
    val byoaCredentialStore by lazy { com.audiophile.musicplayer.data.source.external.ByoaCredentialStore(appContext) }

    val trackRepository by lazy { TrackRepository(database.trackDao()) }
    val resolutionCacheRepository by lazy { ResolutionCacheRepository(database.resolutionDao()) }

    private val sourceRegistryRef by lazy { AtomicReference(createSourceRegistry()) }
    private val resolverServiceRef by lazy { AtomicReference(createResolverService()) }
    
    val metadataCache by lazy { MetadataCache(musicDatabase.libraryDao()) }
    val appleMusicMetadataProvider by lazy {
        AppleMusicMetadataProvider(
            configProvider = {
                val token = resolverConfigStore.getAppleMusicDeveloperToken()
                if (token.isNullOrBlank()) {
                    AppleMusicConfig.disabled(resolverConfigStore.getAppleMusicStorefront())
                } else {
                    AppleMusicConfig(developerToken = token, storefront = resolverConfigStore.getAppleMusicStorefront())
                }
            }
        )
    }
    val metadataProvider by lazy {
        CompositeMetadataProvider(
            listOf(
                appleMusicMetadataProvider,
                DeezerMetadataProvider(),
                ITunesSearchMetadataProvider()
            )
        )
    }
    val metadataResolver by lazy { MetadataResolver(metadataProvider, metadataCache) }
    val platformLinkResolver by lazy { PlatformLinkResolver(appleMusicProvider = appleMusicMetadataProvider) }
    val catalogBrowseRepository by lazy { CatalogBrowseRepository() }

    val localLibraryRepository by lazy { LocalLibraryRepository(musicDatabase.libraryDao(), metadataResolver) }
    val listeningHistoryRepository by lazy {
        com.audiophile.musicplayer.data.local.ListeningHistoryRepository(musicDatabase.listeningHistoryDao())
    }
    val canonicalMusicResolver by lazy { CanonicalMusicResolver(musicDatabase.canonicalGraphDao()) }
    val connectedLibraryTokenStore by lazy { ConnectedLibraryTokenStore(appContext) }
    val spotifyOAuthManager by lazy {
        com.audiophile.musicplayer.data.connectors.spotify.SpotifyOAuthManager(
            context = appContext,
            tokenStore = connectedLibraryTokenStore
        )
    }
    val connectedLibraryManager by lazy {
        ConnectedLibraryManager(
            context = appContext,
            tokenStore = connectedLibraryTokenStore,
            trackRepository = trackRepository,
            localLibraryRepository = localLibraryRepository,
            spotifyOAuthManager = spotifyOAuthManager
        )
    }


    val libraryImporter by lazy {
        LibraryImporter(
            localLibraryRepository,
            metadataResolver = metadataResolver,
            appleMusicMetadataProvider = appleMusicMetadataProvider
        )
    }

    init {
        // Wire BYOA vault into gateway resolver for header forwarding + custom gateway
        com.audiophile.musicplayer.data.source.external.GatewayStreamResolver.byoaStore = byoaCredentialStore
        containerScope.launch {
            // First frame and transport controls take priority over maintenance.
            delay(5_000L)
            trackRepository.cleanupPoisonedData()
            runCatching { backfillCanonicalGraph() }
                .onFailure { Log.w("VANTA_TRACK_GRAPH", "backfill_failed reason='${it.message}'") }
        }
    }

    private suspend fun backfillCanonicalGraph() {
        val songs = localLibraryRepository.allSongsSnapshot()
        var linked = 0
        for (song in songs) {
            val resolved = canonicalMusicResolver.resolveTrack(
                CanonicalMusicResolver.TrackInput(
                    title = song.title,
                    artist = song.artist,
                    album = song.album,
                    isrc = song.isrc,
                    durationMs = song.durationMs,
                    artworkUrl = song.artworkUrl,
                    explicit = song.explicit,
                    localSongId = song.id
                )
            ).entity
            if (resolved != null) linked++
        }
        Log.i(
            "VANTA_TRACK_GRAPH",
            "backfill complete songs=${songs.size} linked=$linked " +
                "artists=${musicDatabase.canonicalGraphDao().artistCount()} " +
                "albums=${musicDatabase.canonicalGraphDao().albumCount()} " +
                "tracks=${musicDatabase.canonicalGraphDao().trackCount()}"
        )
    }
    
    var sourceRegistry: SourceRegistry
        get() = sourceRegistryRef.get()
        private set(value) = sourceRegistryRef.set(value)
    val newReleasesSource by lazy { com.audiophile.musicplayer.data.source.CloudflareGatewaySource() }
    val pulseAiBrain by lazy { PulseAiBrain(resolverConfigStore) }
    val pulseVoiceEngine by lazy {
        com.audiophile.musicplayer.data.voice.PulseVoiceEngine(appContext, resolverConfigStore)
    }
    val aiDjRecommendationEngine by lazy {
        AiDjRecommendationEngine(
            sourceRegistry = sourceRegistry,
            trackRepository = trackRepository,
            localLibraryRepository = localLibraryRepository,
            pulseAiBrain = pulseAiBrain,
            listeningHistory = listeningHistoryRepository
        )
    }
    val appleMusicLibraryConnector by lazy {
        AppleMusicLibraryConnector(metadataProvider = appleMusicMetadataProvider)
    }

    val radioApiService: com.audiophile.musicplayer.radio.RadioApiService by lazy {
        val configuredUrl = resolverConfigStore.getStationBackendUrl()
        val baseUrl = configuredUrl?.takeIf { it.isNotBlank() } ?: BuildConfig.STATION_BACKEND_URL.takeIf { it.isNotBlank() } ?: throw IllegalStateException("No station backend URL configured. Set STATION_BACKEND_URL in local.properties or via Settings.")
        val client = okhttp3.OkHttpClient.Builder()
            .addInterceptor(com.audiophile.musicplayer.account.FirebaseIdTokenInterceptor())
            .addInterceptor(com.audiophile.musicplayer.playback.GatewayApiKeyInterceptor)
            .build()
        Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(com.audiophile.musicplayer.radio.RadioApiService::class.java)
    }

    val radioRpcClient by lazy {
        RadioRpcClient(containerScope)
    }

    val radioRpcService by lazy {
        RadioRpcService(radioRpcClient)
    }

    val radioQueueEngine by lazy {
        com.audiophile.musicplayer.radio.RadioQueueEngine(
            sourceRegistry = sourceRegistry,
            trackRepository = trackRepository,
            radioApiService = radioApiService
        )
    }

    val trackFeatureDao by lazy { musicDatabase.trackFeatureDao() }

    val sonicRadioService by lazy {
        com.audiophile.musicplayer.radio.sonic.SonicRadioService(
            trackFeatureDao = trackFeatureDao
        )
    }

    val queueManager by lazy {
        QueueManager(
            recommendationEngineProvider = { aiDjRecommendationEngine },
            radioQueueEngineProvider = { radioQueueEngine },
            persistence = SharedPreferencesQueuePersistence(appContext),
            onTrackConsumed = { trackId ->
                containerScope.launch {
                    trackRepository.updateLastPlayedAt(trackId)
                }
            }
        )
    }
    val nowPlayingStateStore by lazy { NowPlayingStateStore(appContext) }
    val playbackStateHolder by lazy { PlaybackStateHolder() }
    val playerController by lazy { PlayerController(appContext, queueManager, playbackStateHolder) }
    val upnpCastingManager by lazy {
        UpnpCastingManager(appContext).also { com.audiophile.musicplayer.playback.UpnpCastingHolder.manager = it }
    }
    val accountManager by lazy { AccountManager(appContext) }
    val continuityCoordinator by lazy {
        com.audiophile.musicplayer.continuity.ContinuityCoordinator(
            context = appContext,
            accountManager = accountManager,
            playbackState = playbackStateHolder,
            playerController = playerController,
            trackRepository = trackRepository
        )
    }
    val deviceLibrarySyncManager by lazy {
        com.audiophile.musicplayer.sync.DeviceLibrarySyncManager(
            context = appContext,
            trackRepository = trackRepository,
            localLibraryRepository = localLibraryRepository
        )
    }
    val friendActivityLocalSource by lazy { FriendActivityLocalSource(appContext) }
    val friendActivityRepository by lazy {
        FriendActivityRepository(localSource = friendActivityLocalSource, accountManager = accountManager)
    }
    val vantaSyncManager by lazy {
        VantaSyncManager(
            context = appContext,
            accountManager = accountManager,
            trackRepository = trackRepository,
            connectedLibraryTokenStore = connectedLibraryTokenStore
        )
    }
    val vantaSocialManager by lazy {
        VantaSocialManager(
            context = appContext,
            accountManager = accountManager,
            repository = friendActivityRepository,
            syncManager = vantaSyncManager
        )
    }

    val downloadManager by lazy { AndroidTrackDownloadManager(appContext) }
    val localMediaImporter by lazy { LocalMediaImporter(appContext, trackRepository, localLibraryRepository) }

    val knownWebLyricsProvider by lazy { com.audiophile.musicplayer.data.lyrics.KnownWebLyricsProvider() }
    val lrclibLyricsProvider by lazy { com.audiophile.musicplayer.data.lyrics.LRCLibLyricsProvider() }
    val lyricsRepository by lazy {
        com.audiophile.musicplayer.data.lyrics.LyricsRepository(
            providers = listOf(knownWebLyricsProvider, lrclibLyricsProvider),
            lyricsCacheDao = musicDatabase.lyricsCacheDao()
        )
    }
    val lyricsTranslationProvider by lazy {
        com.audiophile.musicplayer.data.lyrics.LyricsTranslationProvider(pulseAiBrain)
    }

    private val torBoxApi: TorBoxApi by lazy {
        Retrofit.Builder()
            .baseUrl(BuildConfig.TORBOX_BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(TorBoxApi::class.java)
    }

    val torBoxRepository by lazy { TorBoxRepository(torBoxApi) }

    var resolverService: CachedResolverService
        get() = resolverServiceRef.get()
        private set(value) = resolverServiceRef.set(value)

    @Volatile private var searchRepositoryOverride: SearchRepository? = null
    var searchRepository: SearchRepository
        get() = searchRepositoryOverride ?: synchronized(this) {
            searchRepositoryOverride ?: SearchRepository(
                trackRepository = trackRepository,
                sourceRegistry = sourceRegistry,
                nowPlayingStateStore = nowPlayingStateStore,
                metadataResolver = metadataResolver,
                canonicalMusicResolver = canonicalMusicResolver,
                catalogBrowseRepository = catalogBrowseRepository,
                gatewaySource = newReleasesSource,
                lyricsRepository = lyricsRepository
            ).also { searchRepositoryOverride = it }
        }
        private set(value) {
            searchRepositoryOverride = value
        }

    val eclipsePlaylistApi by lazy { EclipsePlaylistApi(okHttpClient = okhttp3.OkHttpClient()) }
    val eclipsePlaylistImporter by lazy {
        EclipsePlaylistImporter(
            eclipseApi = eclipsePlaylistApi,
            trackRepository = trackRepository,
            localLibraryRepository = localLibraryRepository,
            sourceRegistry = sourceRegistry
        )
    }
    val gatewayPlaylistImporter by lazy {
        com.audiophile.musicplayer.data.importer.GatewayPlaylistImporter(
            gateway = newReleasesSource,
            trackRepository = trackRepository,
            localLibraryRepository = localLibraryRepository,
            sourceRegistry = sourceRegistry,
        )
    }


    val djPersonaMemory by lazy { DjPersonaMemory(appContext) }
    val stationTasteMemory by lazy { StationTasteMemory(appContext) }
    val customStationStore by lazy { CustomStationStore(appContext) }
    val deviceMediaMetadataReader by lazy { DeviceMediaMetadataReader(appContext) }
    val trackReleaseYearResolver by lazy {
        TrackReleaseYearResolver(trackRepository, deviceMediaMetadataReader, metadataResolver)
    }
    val lastFmGenreResolver by lazy { LastFmGenreResolver(appContext, resolverConfigStore) }
    val lastFmScrobbler by lazy { LastFmScrobbler(resolverConfigStore) }
    val aiDjQueuePlanner by lazy {
        AiDjQueuePlanner(
            trackRepository = trackRepository,
            localLibraryRepository = localLibraryRepository,
            sourceRegistry = sourceRegistry,
            personaMemory = djPersonaMemory,
            stationMemory = stationTasteMemory,
            releaseYearResolver = trackReleaseYearResolver,
            deviceMediaMetadataReader = deviceMediaMetadataReader,
            lastFmGenreResolver = lastFmGenreResolver
        )
    }
    val liveRadioTrackLibrary by lazy {
        LiveRadioTrackLibrary(
            trackRepository = trackRepository,
            metadataResolver = metadataResolver,
            sourceRegistry = sourceRegistry
        )
    }
    val aiDjNarrationGenerator by lazy { AiDjNarrationGenerator(pulseAiBrain) }
    val aiDjSessionManager by lazy {
        AiDjSessionManager(
            trackRepository = trackRepository,
            localLibraryRepository = localLibraryRepository,
            sourceRegistry = sourceRegistry,
            queuePlanner = aiDjQueuePlanner,
            narrationGenerator = aiDjNarrationGenerator,
            recommendationEngine = aiDjRecommendationEngine,
            customStationStore = customStationStore,
            listeningHistory = listeningHistoryRepository
        )
    }
    val radioStationPreviewLoader by lazy {
        RadioStationPreviewLoader(
            radioQueueEngine = radioQueueEngine,
            tasteSignals = { aiDjRecommendationEngine.streamingTasteSignals() }
        )
    }

    val personalizedMixRegistry by lazy {
        com.audiophile.musicplayer.discovery.personalized.PersonalizedMixRegistry()
    }
    private val discoveryCandidatePipeline by lazy {
        com.audiophile.musicplayer.discovery.personalized.DiscoveryCandidatePipeline(
            sourceRegistry = sourceRegistry,
            trackRepository = trackRepository
        )
    }
    val discoveryArtistResolver by lazy {
        com.audiophile.musicplayer.discovery.personalized.DiscoveryArtistResolver(
            dao = musicDatabase.personalizedMixDao(),
            configStore = resolverConfigStore,
            genreResolver = lastFmGenreResolver
        )
    }
    val personalizedMixDeps by lazy {
        com.audiophile.musicplayer.discovery.personalized.PersonalizedMixDeps(
            sourceRegistry = sourceRegistry,
            trackRepository = trackRepository,
            tasteEngine = aiDjRecommendationEngine,
            genreResolver = lastFmGenreResolver,
            localLibraryRepository = localLibraryRepository,
            artistResolver = discoveryArtistResolver,
            candidatePipeline = discoveryCandidatePipeline,
            listeningHistory = listeningHistoryRepository
        )
    }
    val personalizedMixManager by lazy {
        com.audiophile.musicplayer.discovery.personalized.PersonalizedMixManager(
            dao = musicDatabase.personalizedMixDao(),
            registry = personalizedMixRegistry,
            deps = personalizedMixDeps,
            trackRepository = trackRepository
        )
    }
    val personalizedMixPlayback by lazy {
        com.audiophile.musicplayer.discovery.personalized.PersonalizedMixPlayback(
            manager = personalizedMixManager,
            queueManager = queueManager,
            playerController = playerController,
            nowPlayingStateStore = nowPlayingStateStore,
            scope = containerScope,
            tasteSignals = { aiDjRecommendationEngine.streamingTasteSignals() }
        )
    }

    private fun buildConfiguredAddons(): List<com.audiophile.musicplayer.data.resolution.ResolverAddon> {
        return buildList {
            resolverConfigStore.getTorBoxResolverBaseUrl()?.let { baseUrl ->
                add(
                    ResolverBootstrap.createTorBoxAddon(
                        client = TorBoxCatalogResolverClient(
                            api = ResolverRetrofitFactory.createCatalogApi(baseUrl)
                        )
                    )
                )
            }

            resolverConfigStore.getCommunityInstances().forEachIndexed { index, baseUrl ->
                add(
                    ResolverBootstrap.createCommunityAddon(
                        providerId = "community_$index",
                        displayName = "Community ${index + 1}",
                        baseUrl = baseUrl
                    )
                )
            }
        }
    }

    private fun createResolverService(): CachedResolverService {
        return CachedResolverService(
            cacheRepository = resolutionCacheRepository,
            addons = buildConfiguredAddons()
        )
    }

    suspend fun reloadResolverConfiguration() {
        val newRegistry = createSourceRegistry()
        val newService = createResolverService()
        resolverServiceRef.set(newService)
        sourceRegistryRef.set(newRegistry)
        searchRepository = SearchRepository(
            trackRepository = trackRepository,
            sourceRegistry = newRegistry,
            nowPlayingStateStore = nowPlayingStateStore,
            metadataResolver = metadataResolver,
            canonicalMusicResolver = canonicalMusicResolver,
            catalogBrowseRepository = catalogBrowseRepository,
            gatewaySource = newReleasesSource,
            lyricsRepository = lyricsRepository
        )
        registerConfiguredProviders()
    }

    private fun createSourceRegistry(): SourceRegistry {
        return try {
            val providers = mutableListOf<com.audiophile.musicplayer.data.source.MusicSourceProvider>()
            providers.add(newReleasesSource)

            val torBoxToken = resolverConfigStore.getTorBoxApiToken()
            if (!torBoxToken.isNullOrBlank()) {
                providers.add(TorBoxMusicSourceProvider(torBoxToken, torBoxRepository))
            }
            val realDebridToken = resolverConfigStore.getRealDebridApiToken()
            if (!realDebridToken.isNullOrBlank()) {
                providers.add(RealDebridMusicSourceProvider(realDebridToken))
            }
            val externalSources = externalSourceConfigStore.getSources()
                .filter { it.enabled }
                .sortedByDescending { it.priority }
            externalSources.forEach { config ->
                providers.add(PlaybackProviderFactory.create(config))
            }
            Log.d("VANTA_SEARCH", "createSourceRegistry: providerCount=${providers.size} providerIds=${providers.map { it.providerId }}")
            SourceRegistry(providers)
        } catch (error: RuntimeException) {
            Log.e("AppContainer", "createSourceRegistry failed, keeping catalog gateway", error)
            SourceRegistry(
                listOf(
                    newReleasesSource
                )
            )
        } catch (error: java.io.IOException) {
            Log.e("AppContainer", "createSourceRegistry failed, keeping catalog gateway", error)
            SourceRegistry(
                listOf(
                    newReleasesSource
                )
            )
        }
    }

    suspend fun registerConfiguredProviders() {
        resolverService.registerConfiguredProviders()
    }

    /**
     * Used for testing or manual component teardown.
     * Note: Android does not call onTerminate() on production devices,
     * so this is not relied upon for process lifecycle cleanup.
     * The PlayerController safely lives for the duration of the application process.
     */
    fun dispose() {
        try {
            playerController.release()
        } catch (e: Exception) {
            Log.e("AppContainer", "Error disposing player controller", e)
        }
    }
}

class ResolverConfigStore(context: Context) {
    private val appContext = context.applicationContext
    private val prefs = createSecurePrefs(appContext)

    private fun createSecurePrefs(context: Context) = try {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            "resolver_config_secure",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    } catch (e: Exception) {
        Log.e("ResolverConfigStore", "Secure prefs unavailable; credential persistence disabled", e)
        FailClosedSharedPreferences
    }

    init {
        if (prefs !== FailClosedSharedPreferences) {
            migrateLegacyPrefs(appContext)
        }
    }

    private fun migrateLegacyPrefs(context: Context) {
        val legacy = context.getSharedPreferences("resolver_config", Context.MODE_PRIVATE)
        if (legacy.all.isEmpty()) return
        val editor = prefs.edit()
        legacy.all.forEach { (key, value) ->
            when (value) {
                is String -> editor.putString(key, value)
                is Boolean -> editor.putBoolean(key, value)
                is Int -> editor.putInt(key, value)
                is Long -> editor.putLong(key, value)
                is Float -> editor.putFloat(key, value)
                is Set<*> -> {
                    @Suppress("UNCHECKED_CAST")
                    editor.putStringSet(key, value as Set<String>)
                }
            }
        }
        editor.apply()
        legacy.edit().clear().apply()
    }

    fun getTorBoxResolverBaseUrl(): String? {
        return prefs.getString("torbox_resolver_base_url", null)?.takeIf { it.isNotBlank() }
    }

    fun getCommunityInstances(): List<String> {
        return prefs.getStringSet("community_instance_urls", emptySet())
            ?.filter { it.isNotBlank() }
            ?.sorted()
            .orEmpty()
    }

    fun getTorBoxApiToken(): String? {
        return prefs.getString("torbox_api_token", null)?.takeIf { it.isNotBlank() }
    }

    fun setTorBoxResolverBaseUrl(baseUrl: String?) {
        prefs.edit().putString("torbox_resolver_base_url", baseUrl).apply()
    }

    fun setCommunityInstances(baseUrls: Set<String>) {
        prefs.edit().putStringSet("community_instance_urls", baseUrls).apply()
    }

    fun setTorBoxApiToken(token: String?) {
        prefs.edit().putString("torbox_api_token", token).apply()
    }

    fun getRealDebridApiToken(): String? {
        return prefs.getString("realdebrid_api_token", null)?.takeIf { it.isNotBlank() }
    }

    fun setRealDebridApiToken(token: String?) {
        prefs.edit().putString("realdebrid_api_token", token).apply()
    }

    fun getLlmProvider(): com.audiophile.musicplayer.data.llm.AiProvider? {
        val name = prefs.getString("llm_provider", null) ?: return null
        return try { com.audiophile.musicplayer.data.llm.AiProvider.valueOf(name) } catch (e: IllegalArgumentException) { null }
    }

    fun setLlmProvider(provider: com.audiophile.musicplayer.data.llm.AiProvider?) {
        prefs.edit().putString("llm_provider", provider?.name).apply()
    }

    fun getLlmApiKey(provider: com.audiophile.musicplayer.data.llm.AiProvider): String? {
        migrateLegacyLlmKeyIfNeeded()
        return prefs.getString(llmKeyPref(provider), null)?.takeIf { it.isNotBlank() }
    }

    /** Active provider's key — used by Pulse AI at runtime. */
    fun getLlmApiKey(): String? {
        val provider = getLlmProvider() ?: return null
        return getLlmApiKey(provider)
    }

    fun setLlmApiKey(provider: com.audiophile.musicplayer.data.llm.AiProvider, apiKey: String?) {
        val editor = prefs.edit()
        if (apiKey.isNullOrBlank()) {
            editor.remove(llmKeyPref(provider))
            clearLlmVerified(provider)
        } else {
            editor.putString(llmKeyPref(provider), apiKey)
        }
        editor.apply()
        prefs.edit().remove("llm_api_key").apply()
    }

    fun setLlmApiKey(apiKey: String?) {
        val provider = getLlmProvider()
        if (provider != null) {
            setLlmApiKey(provider, apiKey)
        } else if (apiKey.isNullOrBlank()) {
            prefs.edit().remove("llm_api_key").apply()
        } else {
            prefs.edit().putString("llm_api_key", apiKey).apply()
        }
    }

    fun markLlmVerified(provider: com.audiophile.musicplayer.data.llm.AiProvider) {
        synchronized(this) {
            val current = prefs.getStringSet("llm_verified_providers", emptySet())?.toMutableSet() ?: mutableSetOf()
            current.add(provider.name)
            prefs.edit().putStringSet("llm_verified_providers", current).apply()
        }
    }

    fun clearLlmVerified(provider: com.audiophile.musicplayer.data.llm.AiProvider) {
        synchronized(this) {
            val current = prefs.getStringSet("llm_verified_providers", emptySet())?.toMutableSet() ?: mutableSetOf()
            current.remove(provider.name)
            prefs.edit().putStringSet("llm_verified_providers", current).apply()
        }
    }

    fun isLlmVerified(provider: com.audiophile.musicplayer.data.llm.AiProvider): Boolean {
        return prefs.getStringSet("llm_verified_providers", emptySet())?.contains(provider.name) == true
    }

    private fun llmKeyPref(provider: com.audiophile.musicplayer.data.llm.AiProvider): String =
        "llm_api_key_${provider.name.lowercase()}"

    private fun migrateLegacyLlmKeyIfNeeded() {
        val legacy = prefs.getString("llm_api_key", null)?.takeIf { it.isNotBlank() } ?: return
        val provider = getLlmProvider()
        if (provider != null) {
            val key = llmKeyPref(provider)
            if (prefs.getString(key, null).isNullOrBlank()) {
                prefs.edit().putString(key, legacy).apply()
            }
        }
        prefs.edit().remove("llm_api_key").apply()
    }

    fun getAppleMusicDeveloperToken(): String? {
        return prefs.getString("apple_music_developer_token", null)?.takeIf { it.isNotBlank() }
    }

    fun setAppleMusicDeveloperToken(token: String?) {
        prefs.edit().putString("apple_music_developer_token", token).apply()
    }

    fun getAppleMusicStorefront(): String {
        return prefs.getString("apple_music_storefront", "us")?.trim()?.lowercase()?.takeIf { it.isNotBlank() } ?: "us"
    }

    fun setAppleMusicStorefront(storefront: String) {
        val sf = storefront.trim().lowercase().takeIf { it.isNotBlank() } ?: "us"
        prefs.edit().putString("apple_music_storefront", sf).apply()
    }

    fun getPulseVoiceRelayUrl(): String? =
        prefs.getString("pulse_voice_relay_url", null)?.trim()?.takeIf { it.isNotBlank() }

    fun setPulseVoiceRelayUrl(url: String?) {
        prefs.edit().putString("pulse_voice_relay_url", url?.trim()).apply()
    }

    fun getPulseVoiceRelayToken(): String? =
        prefs.getString("pulse_voice_relay_token", null)?.trim()?.takeIf { it.isNotBlank() }

    fun setPulseVoiceRelayToken(token: String?) {
        prefs.edit().putString("pulse_voice_relay_token", token?.trim()).apply()
    }

    fun getPulseVoiceEngine(): String =
        prefs.getString("pulse_voice_engine", com.audiophile.musicplayer.data.voice.PulseVoiceProfile.GEMINI_ENGINE)
            ?: com.audiophile.musicplayer.data.voice.PulseVoiceProfile.GEMINI_ENGINE

    fun setPulseVoiceEngine(engine: String) {
        prefs.edit().putString("pulse_voice_engine", engine.trim()).apply()
    }

    fun getLastFmApiKey(): String? =
        prefs.getString("lastfm_api_key", null)?.trim()?.takeIf { it.isNotBlank() }

    fun setLastFmApiKey(key: String?) {
        prefs.edit().putString("lastfm_api_key", key?.trim()).apply()
    }

    fun getLastFmApiSecret(): String? =
        prefs.getString("lastfm_api_secret", null)?.trim()?.takeIf { it.isNotBlank() }

    fun setLastFmApiSecret(secret: String?) {
        prefs.edit().putString("lastfm_api_secret", secret?.trim()).apply()
    }

    fun getLastFmUsername(): String? =
        prefs.getString("lastfm_username", null)?.trim()?.takeIf { it.isNotBlank() }

    fun setLastFmUsername(username: String?) {
        prefs.edit().putString("lastfm_username", username?.trim()).apply()
    }

    fun getLastFmSessionKey(): String? =
        prefs.getString("lastfm_session_key", null)?.trim()?.takeIf { it.isNotBlank() }

    fun setLastFmSessionKey(sessionKey: String?) {
        prefs.edit().putString("lastfm_session_key", sessionKey?.trim()).apply()
    }

    fun getStationBackendUrl(): String? =
        prefs.getString("station_backend_url", null)?.trim()?.takeIf { it.isNotBlank() }

    fun setStationBackendUrl(url: String?) {
        prefs.edit().putString("station_backend_url", url?.trim()).apply()
    }

    fun getRpcWebSocketUrl(): String? =
        prefs.getString("rpc_websocket_url", null)?.trim()?.takeIf { it.isNotBlank() }

    fun setRpcWebSocketUrl(url: String?) {
        prefs.edit().putString("rpc_websocket_url", url?.trim()).apply()
    }

    fun getStationAuthToken(): String? =
        prefs.getString("station_auth_token", null)?.trim()?.takeIf { it.isNotBlank() }

    fun setStationAuthToken(token: String?) {
        prefs.edit().putString("station_auth_token", token?.trim()).apply()
    }
}




