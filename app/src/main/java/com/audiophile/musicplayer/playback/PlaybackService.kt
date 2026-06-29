@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package com.audiophile.musicplayer.playback

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.Build
import android.os.Bundle

import android.util.Log
import com.audiophile.musicplayer.debug.VantaDiagnosticLog
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Metadata
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.HttpDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import okhttp3.OkHttpClient
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionError
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.audiophile.musicplayer.auto.AndroidAutoBrowseController
import com.audiophile.musicplayer.auto.AndroidAutoController
import com.audiophile.musicplayer.auto.AutoLruCache
import com.audiophile.musicplayer.auto.AutoMainStageLyrics
import com.audiophile.musicplayer.auto.AutoMainStageLyricsController
import com.audiophile.musicplayer.auto.AutoMediaIdCodec
import com.audiophile.musicplayer.data.source.isPlaylistCompilationArtifact
import com.audiophile.musicplayer.R
import com.audiophile.musicplayer.appContainer
import androidx.core.app.NotificationCompat
import com.audiophile.musicplayer.data.canonical.CanonicalIdentityResolver
import com.audiophile.musicplayer.data.display.DisplayMetadataCleaner
import com.audiophile.musicplayer.data.display.VantaQualityInfo
import com.audiophile.musicplayer.data.source.RemoteBitrateMeasurer
import com.audiophile.musicplayer.data.local.entities.SourceType
import com.audiophile.musicplayer.data.local.entities.TrackSource
import com.audiophile.musicplayer.data.local.entities.UnifiedTrackWithSources
import com.audiophile.musicplayer.data.local.toPlayableQueueItem
import com.audiophile.musicplayer.data.repository.PlaybackSourceResolution
import com.audiophile.musicplayer.data.repository.SourceSelectionPolicy
import com.audiophile.musicplayer.data.repository.TrackRepository
import com.audiophile.musicplayer.data.repository.LocalLibraryRepository
import com.audiophile.musicplayer.data.source.SearchItemStatus
import com.audiophile.musicplayer.data.source.SourceSearchResult
import com.audiophile.musicplayer.data.source.canEnterPlaybackFlow
import com.audiophile.musicplayer.radio.LiveRadioMetadataParser
import com.audiophile.musicplayer.data.source.sourceValidityStatus
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.audiophile.musicplayer.data.source.ResolvedStream as SourceResolvedStream
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.util.concurrent.atomic.AtomicLong
import androidx.core.net.toUri

@dagger.hilt.android.AndroidEntryPoint
class PlaybackService : MediaLibraryService() {
    private var mediaSession: MediaLibrarySession? = null
    private lateinit var exoPlayer: ExoPlayer
    private lateinit var player: QueueAwarePlayer
    @javax.inject.Inject lateinit var trackRepository: TrackRepository
    @javax.inject.Inject lateinit var localLibraryRepository: LocalLibraryRepository
    @javax.inject.Inject lateinit var queueManager: QueueManager
    @javax.inject.Inject lateinit var nowPlayingStateStore: NowPlayingStateStore
    @javax.inject.Inject lateinit var playbackState: PlaybackStateHolder
    @javax.inject.Inject lateinit var sourceRegistry: com.audiophile.musicplayer.data.source.SourceRegistry
    @javax.inject.Inject lateinit var appContainer: com.audiophile.musicplayer.AppContainer

    private lateinit var queueController: QueueController
    private lateinit var playbackStateManager: PlaybackStateManager
    private lateinit var streamResolver: StreamResolver

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    @Volatile private var activeTrack: UnifiedTrackWithSources? = null
    private var userPauseRequested = false
    @Volatile private var cachedIsFavorite = false
    private val cachedAutoRemoteResults = AutoLruCache<String, SourceSearchResult>(AUTO_REMOTE_CACHE_SIZE)
    private lateinit var autoController: AndroidAutoController
    private lateinit var browseController: AndroidAutoBrowseController
    private lateinit var autoMainStageLyricsController: AutoMainStageLyricsController
    private lateinit var sessionTrustPolicy: MediaSessionTrustPolicy
    private lateinit var vantaEqualizer: com.audiophile.musicplayer.playback.dsp.VantaEqualizerProcessor
    private lateinit var autoMixPreferences: AutoMixPreferences
    private var autoMixMonitorJob: Job? = null
    private var autoMixTransitionJob: Job? = null
    private var autoMixTransitionInFlight = false
    private lateinit var aiDjPlaybackManager: AiDjPlaybackManager

    // --- Playback generation token: latest request always wins ---
    private val playbackRequestGeneration = AtomicLong(0)
    private var activePlaybackGeneration: Long = 0L
    private var liveRadioStreamUrl: String? = null
    private var liveRadioStationName: String? = null
    private var adDuckRestoreJob: Job? = null

    private val androidAutoCallback = object : MediaLibrarySession.Callback {
        override fun onConnect(
            session: MediaSession,
            controller: MediaSession.ControllerInfo
        ): MediaSession.ConnectionResult {
            val level = sessionTrustPolicy.classify(this@PlaybackService, session, controller)
            val result = sessionTrustPolicy.buildConnectionResult(session, level, controller, player)
            session.setCustomLayout(autoController.buildCommandButtons())
            return result
        }

        override fun onCustomCommand(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            customAction: SessionCommand,
            args: Bundle
        ): ListenableFuture<SessionResult> {
            return autoController.onCustomCommand(session, controller, customAction, args)
        }

        override fun onGetLibraryRoot(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            params: MediaLibraryService.LibraryParams?
        ): ListenableFuture<LibraryResult<MediaItem>> {
            if (!ensureLibraryAccess(browser)) {
                return Futures.immediateFuture(libraryAccessDenied())
            }
            return browseController.onGetLibraryRoot(session, browser, params)
        }

        override fun onGetItem(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            mediaId: String
        ): ListenableFuture<LibraryResult<MediaItem>> {
            if (!ensureLibraryAccess(browser)) {
                return Futures.immediateFuture(libraryAccessDenied())
            }
            return browseController.onGetItem(session, browser, mediaId)
        }

        override fun onGetChildren(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            parentId: String,
            page: Int,
            pageSize: Int,
            params: MediaLibraryService.LibraryParams?
        ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
            if (!ensureLibraryAccess(browser)) {
                return Futures.immediateFuture(libraryAccessDeniedList())
            }
            return browseController.onGetChildren(session, browser, parentId, page, pageSize, params)
        }

        override fun onSubscribe(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            parentId: String,
            params: MediaLibraryService.LibraryParams?
        ): ListenableFuture<LibraryResult<Void>> {
            if (!ensureLibraryAccess(browser)) {
                return Futures.immediateFuture(libraryAccessDeniedVoid())
            }
            return Futures.immediateFuture(LibraryResult.ofVoid())
        }

        override fun onSearch(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            query: String,
            params: MediaLibraryService.LibraryParams?
        ): ListenableFuture<LibraryResult<Void>> {
            if (!ensureLibraryAccess(browser)) {
                return Futures.immediateFuture(libraryAccessDeniedVoid())
            }
            return browseController.onSearch(session, browser, query, params)
        }

        override fun onGetSearchResult(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            query: String,
            page: Int,
            pageSize: Int,
            params: MediaLibraryService.LibraryParams?
        ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
            if (!ensureLibraryAccess(browser)) {
                return Futures.immediateFuture(libraryAccessDeniedList())
            }
            return browseController.onGetSearchResult(session, browser, query, page, pageSize, params)
        }

        override fun onAddMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: List<MediaItem>
        ): ListenableFuture<List<MediaItem>> {
            if (!ensureLibraryAccess(controller)) {
                return Futures.immediateFuture(emptyList())
            }
            return browseController.onAddMediaItems(mediaSession, controller, mediaItems)
        }

        override fun onSetMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: List<MediaItem>,
            startIndex: Int,
            startPositionMs: Long
        ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
            if (!ensureLibraryAccess(controller)) {
                return Futures.immediateFuture(
                    MediaSession.MediaItemsWithStartPosition(emptyList(), 0, C.TIME_UNSET)
                )
            }
            return browseController.onSetMediaItems(mediaSession, controller, mediaItems, startIndex, startPositionMs)
        }
    }

    // --- Media identity helpers ---
    private fun parseMediaId(mediaId: String): Pair<Long?, Long?> {
        val parts = mediaId.split(":")
        return if (parts.size >= 2) {
            val trackId = parts[0].toLongOrNull()
            val sourceId = parts[1].toLongOrNull()
            trackId to sourceId
        } else null to null
    }

    private fun currentMediaTrackId(): Long? {
        if (player.mediaItemCount == 0) return null
        return parseMediaId(player.currentMediaItem?.mediaId ?: return null).first
    }

    private fun currentMediaSourceId(): Long? {
        if (player.mediaItemCount == 0) return null
        return parseMediaId(player.currentMediaItem?.mediaId ?: return null).second
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        promoteToForegroundEarly()

        sessionTrustPolicy = MediaSessionTrustPolicy(PackageValidator(this))
        vantaEqualizer = com.audiophile.musicplayer.playback.dsp.VantaEqualizerProcessor()
        com.audiophile.musicplayer.playback.dsp.VantaEqualizerHolder.processor = vantaEqualizer
        autoMixPreferences = AutoMixPreferences(this)


        // Restore failed sources from previous session for the same track
        val persistedFailed = nowPlayingStateStore.loadFailedSourceIds()
        
        aiDjPlaybackManager = AiDjPlaybackManager(this, appContainer.radioApiService)

        setMediaNotificationProvider(
            DefaultMediaNotificationProvider.Builder(this)
                .setChannelId(CHANNEL_ID)
                .setNotificationIdProvider { NOTIFICATION_ID }
                .build()
        )

        val audioAttributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .build()

        val okHttpClient = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val request = chain.request().newBuilder()
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                    .header("Referer", "https://vanta-music-gateway.16drewk.workers.dev/")
                    .build()
                chain.proceed(request)
            }
            .build()

        val dataSourceFactory = OkHttpDataSource.Factory(okHttpClient)

        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(15_000, 60_000, 2_500, 5_000)
            .setBackBuffer(30_000, true)
            .build()

        val renderersFactory = object : DefaultRenderersFactory(this) {
            override fun buildAudioSink(
                context: android.content.Context,
                enableFloatOutput: Boolean,
                enableAudioTrackPlaybackParams: Boolean
            ): AudioSink? {
                return DefaultAudioSink.Builder(context)
                    .setEnableFloatOutput(true)
                    .setEnableAudioTrackPlaybackParams(enableAudioTrackPlaybackParams)
                    .setAudioProcessors(arrayOf(vantaEqualizer))
                    .build()
            }
        }.setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_OFF)
        Log.i(
            "VANTA_FFMPEG",
            "media3ExtensionRendererMode=PREFER ffmpegAudioRendererAvailable=${isMedia3FfmpegAudioRendererAvailable()}"
        )

        exoPlayer = ExoPlayer.Builder(this, renderersFactory)
            .setAudioAttributes(audioAttributes, true)
            .setMediaSourceFactory(DefaultMediaSourceFactory(dataSourceFactory))
            .setLoadControl(loadControl)
            .build()
        audioSessionId = exoPlayer.audioSessionId
        Log.d("VANTA_AURA", "audioSessionId=$audioSessionId")

        player = QueueAwarePlayer(
            exoPlayer = exoPlayer,
            queueSnapshotProvider = { queueManager.snapshot() },
            onSkipToNext = { playNextFromQueue() },
            onSkipToPrevious = { playPreviousFromQueue() }
        )
        autoMainStageLyricsController = AutoMainStageLyricsController(
            scope = serviceScope,
            playerProvider = { player },
            lyricsRepository = appContainer.lyricsRepository,
            syncPreferencesProvider = { com.audiophile.musicplayer.data.lyrics.LyricsSyncPreferences(this) },
            immersivePreferencesProvider = { com.audiophile.musicplayer.playback.dsp.VantaEqualizerPreferences(this) },
            currentTrackIdProvider = { currentMediaTrackId() },
        )

        queueController = QueueController(queueManager, trackRepository, serviceScope)
        playbackStateManager = PlaybackStateManager(playbackState, nowPlayingStateStore, serviceScope)
        streamResolver = StreamResolver(sourceRegistry, trackRepository)

        exoPlayer.addListener(playbackStateManager)

        // Create mediaSession early so onGetSession() doesn't return null
        val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
        val sessionActivity = launchIntent?.let {
            PendingIntent.getActivity(
                this,
                0,
                it,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
        }
        mediaSession = MediaLibrarySession.Builder(this, player, androidAutoCallback)
            .setId("vanta_media_library")
            .apply { sessionActivity?.let { setSessionActivity(it) } }
            .build()

        autoController = AndroidAutoController(
            context = this,
            scope = serviceScope,
            player = player,
            onToggleFavorite = {
                val track = activeTrack
                if (track != null) {
                    cachedIsFavorite = !cachedIsFavorite
                    val localId = track.track.localLibraryId
                    if (localId != null) {
                        serviceScope.launch(Dispatchers.IO) {
                            localLibraryRepository.toggleFavorite(localId)
                        }
                    }
                }
            },
            isFavoriteProvider = { cachedIsFavorite },
            ensureLibraryAccess = { ensureLibraryAccess(it) },
            onSongRadio = {
                serviceScope.launch {
                    handleAutoRadioRequest(AUTO_SONG_RADIO_ID)
                }
            },
            onMoreLikeThis = {
                serviceScope.launch {
                    handleAutoRadioRequest(AUTO_ARTIST_RADIO_ID)
                }
            },
            onChangeVibe = {
                player.shuffleModeEnabled = true
                serviceScope.launch {
                    val shuffled = allAutoPlayableTracks().shuffled()
                    if (shuffled.isNotEmpty()) {
                        queueController.setPlayQueue(shuffled, 0, QueueMode.NORMAL_QUEUE)
                        queueManager.markCurrentTrack(shuffled.first())
                        playTrack(shuffled.first().track.trackId)
                    }
                }
            },
            onAiDj = {
                Log.d("VANTA_ANDROID_AUTO", "AI DJ requested from car")
                serviceScope.launch {
                    val track = activeTrack
                    if (track != null) {
                        val djTracks = allAutoPlayableTracks().shuffled().take(20)
                        if (djTracks.isNotEmpty()) {
                            queueController.setPlayQueue(djTracks, 0, QueueMode.NORMAL_QUEUE)
                            queueManager.markCurrentTrack(djTracks.first())
                            playTrack(djTracks.first().track.trackId)
                        }
                    }
                }
            },
            onShowLyrics = {
                Log.d("VANTA_ANDROID_AUTO", "Lyrics requested from car")
                val track = activeTrack
                if (track != null) {
                    autoMainStageLyricsController.start(track)
                }
            }
        )
        browseController = AndroidAutoBrowseController(
            trackRepository = trackRepository,
            sourceRegistry = sourceRegistry,
            scope = serviceScope,
            handleAutoRequest = ::handleAutoMediaRequest,
            orderedQueueTracksProvider = ::orderedQueueTracks,
            allTracksProvider = ::allAutoPlayableTracks,
            remoteResultCache = cachedAutoRemoteResults,
            favoritesProvider = {
                localLibraryRepository.allSongsSnapshot().filter { it.isFavorite }
                    .mapNotNull { it.toPlayableQueueItem() }
            },
            playlistsProvider = {
                localLibraryRepository.playlistsSnapshot().map { it.id to it.name }
            },
            playlistTracksProvider = { playlistId ->
                trackRepository.getOrderedPlaylistTracks(playlistId).mapNotNull { track ->
                    trackRepository.getTrackWithSources(track.trackId)
                }
            }
        )

        // Update existing mediaSession with the proper bitmap loader from autoController
        // (created earlier to allow early controller connections; now enriched)
        player.refreshQueueTimeline()

        serviceScope.launch {
            appContainer.registerConfiguredProviders()
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession? {
        return mediaSession
    }

    private fun controllerTrustLevel(controller: MediaSession.ControllerInfo): MediaSessionTrustPolicy.TrustLevel {
        val session = mediaSession ?: return MediaSessionTrustPolicy.TrustLevel.REJECTED
        return sessionTrustPolicy.classify(this, session, controller)
    }

    private fun ensureLibraryAccess(controller: MediaSession.ControllerInfo): Boolean {
        val allowed = MediaSessionTrustPolicy.canAccessLibrary(controllerTrustLevel(controller))
        if (!allowed) {
            Log.w(
                "VANTA_SESSION_TRUST",
                "library denied package=${controller.packageName} uid=${controller.uid}"
            )
        }
        return allowed
    }

    private fun libraryAccessDenied(): LibraryResult<MediaItem> =
        LibraryResult.ofError(SessionError.ERROR_NOT_SUPPORTED)

    private fun libraryAccessDeniedList(): LibraryResult<ImmutableList<MediaItem>> =
        LibraryResult.ofError(SessionError.ERROR_NOT_SUPPORTED)

    private fun libraryAccessDeniedVoid(): LibraryResult<Void> =
        LibraryResult.ofError(SessionError.ERROR_NOT_SUPPORTED)

    private suspend fun allAutoPlayableTracks(): List<UnifiedTrackWithSources> {
        return trackRepository.getAllTracks()
            .filterAutoBrowse()
            .sortedWith(
                compareBy<UnifiedTrackWithSources> { it.track.artist.lowercase() }
                    .thenBy { it.track.albumName.orEmpty().lowercase() }
                    .thenBy { it.track.title.lowercase() }
            )
    }

    private fun handleAutoMediaRequest(mediaItems: List<MediaItem>, startIndex: Int) {
        val safeStartIndex = startIndex.coerceAtLeast(0)
        val selectedItem = mediaItems.getOrNull(safeStartIndex) ?: mediaItems.firstOrNull() ?: return
        val searchQuery = selectedItem.requestMetadata.searchQuery?.trim()
        serviceScope.launch {
            if (selectedItem.mediaId.startsWith(AUTO_REMOTE_TRACK_PREFIX)) {
                autoMainStageLyricsController.cancel()
                resolveAndPlayAutoRemote(selectedItem)
                return@launch
            }
            if (selectedItem.mediaId == AUTO_MAIN_STAGE_RESUME_ID) {
                if (player.isPlaying) {
                    play()
                } else {
                    activeTrack?.track?.trackId?.let { playTrack(it) } ?: play()
                }
                return@launch
            }
            if (selectedItem.mediaId == AUTO_SONG_RADIO_ID || selectedItem.mediaId == AUTO_ARTIST_RADIO_ID) {
                handleAutoRadioRequest(selectedItem.mediaId)
                return@launch
            }
            val queue: List<UnifiedTrackWithSources> = when {
                selectedItem.mediaId == AUTO_LIKED_ID -> {
                    localLibraryRepository.allSongsSnapshot().filter { it.isFavorite }
                        .mapNotNull { it.toPlayableQueueItem() }
                }
                selectedItem.mediaId == AUTO_MADE_FOR_YOU_ID -> {
                    val favTracks = localLibraryRepository.allSongsSnapshot().filter { it.isFavorite }
                        .mapNotNull { it.toPlayableQueueItem() }
                    (favTracks + allAutoPlayableTracks().take(30 - favTracks.size.coerceAtMost(30))).distinctBy { it.track.trackId }
                }
                selectedItem.mediaId.startsWith(AUTO_PLAYLIST_PREFIX) -> {
                    val playlistId = selectedItem.mediaId.removePrefix(AUTO_PLAYLIST_PREFIX).toLongOrNull()
                    if (playlistId != null) {
                        trackRepository.getOrderedPlaylistTracks(playlistId).mapNotNull { track ->
                            trackRepository.getTrackWithSources(track.trackId)
                        }
                    } else emptyList()
                }
                !searchQuery.isNullOrBlank() -> trackRepository.searchLibrary(searchQuery, AUTO_SEARCH_LIMIT)
                selectedItem.mediaId == AUTO_SHUFFLE_ID -> allAutoPlayableTracks().shuffled()
                selectedItem.mediaId.startsWith(AUTO_MOOD_PREFIX) -> allAutoPlayableTracks().shuffled()
                mediaItems.any { it.mediaId.startsWith(AUTO_TRACK_PREFIX) } -> autoQueueForTrackMediaItems(mediaItems)
                else -> autoTracksForMediaId(selectedItem.mediaId)
            }.filterAutoBrowse()

            if (queue.isEmpty()) {
                Log.w("VANTA_ANDROID_AUTO", "No playable result for mediaId=${selectedItem.mediaId} query=${searchQuery ?: "null"}")
                return@launch
            }

            val selectedTrackId = selectedItem.mediaId
                .removePrefix(AUTO_TRACK_PREFIX)
                .toLongOrNull()
            val queueStartIndex = selectedTrackId
                ?.let { id -> queue.indexOfFirst { it.track.trackId == id } }
                ?.takeIf { it >= 0 }
                ?: safeStartIndex.coerceIn(queue.indices)

            queueController.setPlayQueue(queue, queueStartIndex, QueueMode.NORMAL_QUEUE)
            queueManager.markCurrentTrack(queue[queueStartIndex])
            Log.d(
                "VANTA_ANDROID_AUTO",
                "Starting Auto playback trackId=${queue[queueStartIndex].track.trackId} queueSize=${queue.size}"
            )
            playTrack(queue[queueStartIndex].track.trackId)
        }
    }

    private suspend fun handleAutoRadioRequest(mediaId: String) {
        val currentTrack = activeTrack ?: return
        val seedArtist = currentTrack.track.artist
        val seedTitle = currentTrack.track.title
        val allTracks = allAutoPlayableTracks()
        val radioTracks = if (mediaId == AUTO_ARTIST_RADIO_ID) {
            allTracks.filter { it.track.artist.equals(seedArtist, ignoreCase = true) }
                .shuffled()
        } else {
            allTracks.filter {
                it.track.artist.equals(seedArtist, ignoreCase = true) ||
                    it.track.title.contains(seedTitle?.take(4) ?: "", ignoreCase = true)
            }.shuffled().let { filtered ->
                if (filtered.size < 20) {
                    (filtered + allTracks.shuffled().take(20)).distinctBy { it.track.trackId }
                } else filtered
            }
        }
        if (radioTracks.isEmpty()) return
        queueController.setPlayQueue(radioTracks, 0, QueueMode.NORMAL_QUEUE)
        queueManager.markCurrentTrack(radioTracks.first())
        Log.d("VANTA_ANDROID_AUTO", "Starting ${mediaId} radio seed='$seedArtist' count=${radioTracks.size}")
        playTrack(radioTracks.first().track.trackId)
    }

    private suspend fun resolveAndPlayAutoRemote(item: MediaItem) {
        val cached = cachedAutoRemoteResults.get(item.mediaId)
        val decoded = AutoMediaIdCodec.parseRemoteTrackId(AUTO_REMOTE_TRACK_PREFIX, item.mediaId)
        val result = cached ?: decoded?.let { remote ->
            SourceSearchResult(
                id = remote.externalTrackId,
                providerId = remote.providerId,
                title = item.mediaMetadata.title?.toString().orEmpty(),
                artist = item.mediaMetadata.artist?.toString().orEmpty(),
                album = item.mediaMetadata.albumTitle?.toString(),
                coverSeed = item.mediaMetadata.artworkUri?.toString().orEmpty(),
                durationMs = item.mediaMetadata.durationMs,
                status = SearchItemStatus.SOURCE_FOUND,
                qualityLabel = null
            )
        } ?: return
        val stream = sourceRegistry.resolveStream(result.providerId, result.id, timeoutMs = AUTO_RESOLVE_TIMEOUT_MS)
            ?: return
        if (stream.streamUrl.isBlank()) return
        val trackId = trackRepository.addTrackSource(
            title = result.title,
            artist = result.artist,
            album = result.album,
            coverArtUrl = result.artworkUrl,
            sourceType = com.audiophile.musicplayer.data.source.CloudLibraryHelpers.sourceTypeForProvider(result.providerId),
            streamUrl = stream.streamUrl,
            bitrate = stream.bitrateKbps,
            isrc = result.isrc,
            durationMs = result.durationMs,
            externalProviderId = result.providerId,
            externalTrackId = result.id,
            expiresAtMs = normalizedStreamExpiryMs(stream.streamUrl, stream.expiresAt, result.providerId)
        )
        val track = trackRepository.getTrackWithSources(trackId) ?: return
        queueController.setPlayQueue(listOf(track), 0, QueueMode.NORMAL_QUEUE)
        queueManager.markCurrentTrack(track)
        playTrack(trackId)
    }

    private suspend fun autoQueueForTrackMediaItems(mediaItems: List<MediaItem>): List<UnifiedTrackWithSources> {
        val requestedTrackIds = mediaItems
            .mapNotNull { item ->
                if (item.mediaId.startsWith(AUTO_TRACK_PREFIX)) {
                    item.mediaId.removePrefix(AUTO_TRACK_PREFIX).toLongOrNull()
                } else {
                    null
                }
            }
        if (requestedTrackIds.size <= 1) return allAutoPlayableTracks()
        return requestedTrackIds.mapNotNull { trackRepository.getTrackWithSources(it) }
    }

    private suspend fun autoTracksForMediaId(mediaId: String): List<UnifiedTrackWithSources> {
        return when {
            mediaId.startsWith(AUTO_TRACK_PREFIX) -> {
                val trackId = mediaId.removePrefix(AUTO_TRACK_PREFIX).toLongOrNull() ?: return emptyList()
                listOfNotNull(trackRepository.getTrackWithSources(trackId))
            }
            mediaId == AUTO_MAIN_STAGE_QUEUE_ID -> orderedQueueTracks()
            mediaId.startsWith(AUTO_MOOD_PREFIX) -> allAutoPlayableTracks().shuffled()
            mediaId == AUTO_SHUFFLE_ID -> allAutoPlayableTracks().shuffled()
            mediaId == AUTO_DEVICE_ID -> allAutoPlayableTracks()
                .filter { track -> track.sources.any { it.sourceType == SourceType.LOCAL } }
            mediaId == AUTO_HIGH_QUALITY_ID -> allAutoPlayableTracks()
                .filter { track -> track.sources.any { it.bitrate >= AUTO_HIGH_QUALITY_KBPS } }
            mediaId == AUTO_RECENT_ID -> trackRepository.getRecentlyPlayed(AUTO_PAGE_SIZE).filterAutoBrowse()
            mediaId == AUTO_TRACKS_ID -> allAutoPlayableTracks()
            mediaId.startsWith(AUTO_ARTIST_PREFIX) -> {
                val artist = decodeAutoPart(mediaId.removePrefix(AUTO_ARTIST_PREFIX))
                allAutoPlayableTracks().filter { it.track.artist.equals(artist, ignoreCase = true) }
            }
            mediaId.startsWith(AUTO_ALBUM_PREFIX) -> {
                val key = parseAutoAlbumId(mediaId) ?: return emptyList()
                allAutoPlayableTracks().filter { track ->
                    track.track.artist.equals(key.first, ignoreCase = true) &&
                        track.track.albumName.orEmpty().equals(key.second, ignoreCase = true)
                }
            }
            else -> emptyList()
        }
    }

    private fun List<UnifiedTrackWithSources>.filterAutoPlayable(): List<UnifiedTrackWithSources> {
        return filter { it.isAutoPlayable() }
    }

    private fun UnifiedTrackWithSources.isAutoBrowseCandidate(): Boolean {
        if (!isAutoPlayable()) return false
        return !isPlaylistCompilationArtifact(
            title = track.title.orEmpty(),
            artist = track.artist.orEmpty(),
            album = track.albumName,
            durationMs = track.durationMs,
        )
    }

    private fun List<UnifiedTrackWithSources>.filterAutoBrowse(): List<UnifiedTrackWithSources> {
        return filter { it.isAutoBrowseCandidate() }
    }

    private fun UnifiedTrackWithSources.isAutoPlayable(): Boolean {
        return sourceValidityStatus().canEnterPlaybackFlow()
    }

    private fun <T> pageItems(items: List<T>, page: Int, pageSize: Int): List<T> {
        val resolvedPageSize = if (pageSize > 0) pageSize else AUTO_PAGE_SIZE
        val resolvedPage = page.coerceAtLeast(0)
        return items.drop(resolvedPage * resolvedPageSize).take(resolvedPageSize)
    }

    private fun parseAutoAlbumId(mediaId: String): Pair<String, String>? {
        val encoded = mediaId.removePrefix(AUTO_ALBUM_PREFIX)
        val separator = encoded.indexOf('|')
        if (separator <= 0 || separator >= encoded.lastIndex) return null
        return decodeAutoPart(encoded.substring(0, separator)) to
            decodeAutoPart(encoded.substring(separator + 1))
    }

    private fun decodeAutoPart(value: String): String {
        return URLDecoder.decode(value, StandardCharsets.UTF_8.name())
    }

    private fun artworkUri(url: String?): Uri? {
        if (url.isNullOrBlank()) return null
        if (!url.startsWith("http://", ignoreCase = true) && !url.startsWith("https://", ignoreCase = true)) return null
        return runCatching { url.toUri() }.getOrNull()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (mediaSession == null) {
            promoteToForegroundEarly()
        }
        super.onStartCommand(intent, flags, startId)
        Log.d("VANTA_SERVICE_ACTION_RECEIVED", "action=${intent?.action} flags=$flags startId=$startId")
        when (intent?.action) {
            ACTION_REFRESH_IMMERSIVE_AUDIO -> applyVantaEqualizer()
            ACTION_REFRESH_AUTO_MIX -> {
                cancelAutoMixJobs(resetVolume = true)
                if (player.isPlaying) startAutoMixMonitor()
            }
            ACTION_PLAY_TRACK -> {
                val trackId = intent.getLongExtra(EXTRA_TRACK_ID, -1L)
                Log.d("VANTA_SERVICE_ACTION_RECEIVED", "ACTION_PLAY_TRACK: trackId=$trackId")
                if (trackId > 0L) playTrack(trackId)
                else Log.w("VANTA_SERVICE_ACTION_RECEIVED", "ACTION_PLAY_TRACK with invalid trackId=$trackId")
            }
            ACTION_PLAY_NEXT_FROM_QUEUE -> { Log.d("VANTA_SERVICE_ACTION_RECEIVED", "ACTION_PLAY_NEXT_FROM_QUEUE"); playNextFromQueue() }
            ACTION_PLAY_PREVIOUS_FROM_QUEUE -> { Log.d("VANTA_SERVICE_ACTION_RECEIVED", "ACTION_PLAY_PREVIOUS_FROM_QUEUE"); playPreviousFromQueue() }
            ACTION_PLAY -> { Log.d("VANTA_SERVICE_ACTION_RECEIVED", "ACTION_PLAY"); play() }
            ACTION_PAUSE -> { Log.d("VANTA_SERVICE_ACTION_RECEIVED", "ACTION_PAUSE"); pause() }
            ACTION_RESUME -> { Log.d("VANTA_SERVICE_ACTION_RECEIVED", "ACTION_RESUME"); resume() }
            ACTION_TOGGLE_PLAY_PAUSE -> { Log.d("VANTA_SERVICE_ACTION_RECEIVED", "ACTION_TOGGLE_PLAY_PAUSE"); togglePlayPause() }
            ACTION_SEEK_TO -> {
                val positionMs = intent.getLongExtra(EXTRA_POSITION_MS, 0L)
                Log.d("VANTA_SERVICE_ACTION_RECEIVED", "ACTION_SEEK_TO: positionMs=$positionMs")
                seekTo(positionMs)
            }
            ACTION_STOP -> { Log.d("VANTA_SERVICE_ACTION_RECEIVED", "ACTION_STOP"); stopPlayback() }
            ACTION_SKIP_LIVE_AD -> {
                Log.d("VANTA_SERVICE_ACTION_RECEIVED", "ACTION_SKIP_LIVE_AD")
                duckLiveRadioForAd()
            }
            ACTION_PLAY_DIRECT_URL -> {
                val directUrl = intent.getStringExtra(EXTRA_STREAM_URL).orEmpty()
                if (directUrl.isNotBlank()) {
                    Log.d("VANTA_SERVICE_ACTION_RECEIVED", "ACTION_PLAY_DIRECT_URL: url=${directUrl.take(80)}...")
                    playDirectUrl(
                        directUrl = directUrl,
                        title = intent.getStringExtra(EXTRA_TITLE) ?: "Direct stream",
                        artist = intent.getStringExtra(EXTRA_ARTIST) ?: "Unknown source"
                    )
                }
            }
            ACTION_REFRESH_QUEUE_TIMELINE -> {
                Log.d("VANTA_SERVICE_ACTION_RECEIVED", "ACTION_REFRESH_QUEUE_TIMELINE")
                player.refreshQueueTimeline()
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        cancelAutoMixJobs(resetVolume = false)
        autoMainStageLyricsController.cancel()
        aiDjPlaybackManager.releasePlayer()
        // Stop playback BEFORE releasing DSP to prevent use-after-free in audio thread.
        // Always release exoPlayer even if mediaSession was already nulled.
        try { exoPlayer.stop() } catch (_: Exception) {}
        try { exoPlayer.release() } catch (_: Exception) {}
        mediaSession?.run {
            release()
            mediaSession = null
        }
        com.audiophile.musicplayer.playback.dsp.VantaEqualizerHolder.processor = null
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Playback",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            setShowBadge(false)
            description = "Now playing and playback controls"
        }
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    /** Android kills the process if startForeground() is not called within ~5s of startForegroundService(). */
    private fun promoteToForegroundEarly() {
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.app_name))
            .setContentText("Preparing playbackâ€¦")
            .setSmallIcon(R.drawable.ic_radio)
            .setOngoing(true)
            .setSilent(true)
            .apply {
                if (Build.VERSION.SDK_INT >= 34) {
                    setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
                }
            }
            .build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    fun playTrack(trackId: Long, policy: SourceSelectionPolicy = SourceSelectionPolicy()) {
        val generation = playbackRequestGeneration.incrementAndGet()
        activePlaybackGeneration = generation
        userPauseRequested = false
        
        serviceScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val track = trackRepository.getTrackWithSources(trackId)
                if (generation != activePlaybackGeneration) return@launch
                
                if (track == null) {
                    val errorState = NowPlayingState(errorMessage = "Track not found")
                    nowPlayingStateStore.save(errorState)
                    playbackState.replace(errorState)
                    return@launch
                }
                
                activeTrack = track
                val stream = streamResolver.resolve(track)
                if (generation != activePlaybackGeneration) return@launch
                
                if (stream == null || stream.streamUrl.isBlank()) {
                    val errorState = NowPlayingState(
                        trackId = trackId.toString(),
                        errorMessage = "No playable stream found"
                    )
                    nowPlayingStateStore.save(errorState)
                    playbackState.replace(errorState)
                    runCatching { queueController.advance()?.let { playTrack(it.track.trackId) } }
                    return@launch
                }
                
                val metadataBuilder = androidx.media3.common.MediaMetadata.Builder()
                    .setTitle(track.track.title)
                    .setArtist(track.track.artist)
                    .setMediaType(androidx.media3.common.MediaMetadata.MEDIA_TYPE_MUSIC)
                
                artworkUri(track.track.coverArtUrl)?.let { metadataBuilder.setArtworkUri(it) }
                
                val mime = mediaItemMimeFor(stream.streamUrl)
                
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    player.setMediaItem(
                        androidx.media3.common.MediaItem.Builder()
                            .setUri(stream.streamUrl)
                            .setMediaId("${track.track.trackId}:0")
                            .apply { mime?.let { setMimeType(it) } }
                            .setMediaMetadata(metadataBuilder.build())
                            .build()
                    )
                    
                    val snapshot = queueManager.snapshot()
                    
                    playbackStateManager.onTrackChanged(
                        track = track,
                        qualityInfo = VantaQualityInfo.fromSource(
                            bitrate = stream.bitrateKbps,
                            status = SearchItemStatus.VALIDATED_PLAYABLE,
                            sourceProviderId = "resolved",
                            isValidated = true,
                            reason = "stream_resolved",
                            mime = mime,
                            quality = null
                        ),
                        queuePosition = snapshot.queueIndex,
                        queueSize = snapshot.queueSize
                    )
                    
                    player.prepare()
                    player.play()
                }
                
            } catch (e: Exception) {
                val errorState = NowPlayingState(
                    trackId = trackId.toString(),
                    errorMessage = e.message ?: "Playback failed"
                )
                nowPlayingStateStore.save(errorState)
                playbackState.replace(errorState)
                runCatching { queueController.advance()?.let { playTrack(it.track.trackId) } }
            }
        }
    }

    fun play() {
        if (aiDjPlaybackManager.isDjPlaying()) {
            userPauseRequested = false
            aiDjPlaybackManager.resume()
            return
        }
        if (player.mediaItemCount > 0) {
            userPauseRequested = false
            Log.d("VANTA_PLAYBACK_INTENT", "action=user_play userPauseRequested=false")
            player.play()
            } else {
            userPauseRequested = false
            playNextFromQueue()
        }
    }

    fun pause() {
        userPauseRequested = true
        cancelAutoMixJobs(resetVolume = true)
        Log.d("VANTA_PLAYBACK_INTENT", "action=user_pause userPauseRequested=true")
        if (aiDjPlaybackManager.isDjPlaying()) {
            aiDjPlaybackManager.pause()
        } else {
            player.pause()
        }
    }
    fun resume() { play() }

    fun togglePlayPause() {
        if (aiDjPlaybackManager.isDjPlaying()) {
            aiDjPlaybackManager.togglePlayPause()
        } else {
            if (player.isPlaying) pause() else resume()
        }
    }

    fun seekTo(positionMs: Long) {
        player.seekTo(positionMs.coerceAtLeast(0L))
        queueManager.markPlaybackPosition(player.currentPosition.coerceAtLeast(0L))
    }

    fun playNextFromQueue(policy: SourceSelectionPolicy = SourceSelectionPolicy()) {
        serviceScope.launch {
            val nextTrack = queueController.advance() ?: run {
                Log.d("VANTA_QUEUE_TRUTH", "action=next_from_queue no_next_track")
                return@launch
            }
            Log.d("VANTA_QUEUE_TRUTH", "action=next_from_queue trackId=${nextTrack.track.trackId} title=${nextTrack.track.title}")
            playTrack(nextTrack.track.trackId, policy)
        }
    }

    fun playPreviousFromQueue(policy: SourceSelectionPolicy = SourceSelectionPolicy()) {
        serviceScope.launch {
            val previousTrack = queueController.back() ?: run {
                Log.d("VANTA_QUEUE_TRUTH", "action=previous_from_queue no_previous_track")
                return@launch
            }
            Log.d("VANTA_QUEUE_TRUTH", "action=previous_from_queue trackId=${previousTrack.track.trackId} title=${previousTrack.track.title}")
            playTrack(previousTrack.track.trackId, policy)
        }
    }

    fun stopPlayback() {
        cancelAutoMixJobs(resetVolume = true)
        autoMainStageLyricsController.cancel()
        liveRadioStreamUrl = null
        liveRadioStationName = null
        adDuckRestoreJob?.cancel()
        player.stop()
        queueManager.markPlaybackPosition(0L)
    }

    fun playDirectUrl(directUrl: String, title: String = "Direct stream", artist: String = "Unknown source") {
        val generation = playbackRequestGeneration.incrementAndGet()
        activePlaybackGeneration = generation
        userPauseRequested = false
        Log.d("VANTA_PLAYBACK_INTENT", "action=user_play_direct url=${directUrl.take(60)} userPauseRequested=false generation=$generation")
        activeTrack = null
        autoMainStageLyricsController.cancel()
        liveRadioStreamUrl = directUrl
        liveRadioStationName = title

        val cleanTitle = DisplayMetadataCleaner.cleanTitle(title)
        val cleanArtist = DisplayMetadataCleaner.computeDisplayTitleArtist(title, artist).second
        val metadataBuilder = androidx.media3.common.MediaMetadata.Builder()
            .setTitle(cleanTitle)
            .setArtist(cleanArtist)
            .setMediaType(androidx.media3.common.MediaMetadata.MEDIA_TYPE_MUSIC)

        val directMime = mediaItemMimeFor(directUrl)
        try {
            player.stop()
            player.setMediaItem(
                MediaItem.Builder()
                    .setUri(directUrl)
                    .setMediaId("direct:$directUrl")
                    .apply { directMime?.let { setMimeType(it) } }
                    .setMediaMetadata(metadataBuilder.build())
                    .build()
            )
            player.prepare()
            player.play()
        } catch (e: Exception) {
            Log.e("VANTA_PLAYBACK", "playDirectUrl preparation crashed", e)
            val errorState = NowPlayingState(
                title = cleanTitle,
                artist = cleanArtist,
                isPlaying = false,
                errorMessage = e.message ?: "Direct stream failed",
                isLiveRadio = true,
                liveStationName = cleanTitle
            )
            nowPlayingStateStore.save(errorState)
            playbackState.replace(errorState)
            return
        }

        val state = NowPlayingState(
            title = cleanTitle,
            artist = cleanArtist,
            isPlaying = true,
            isLiveRadio = true,
            liveStationName = cleanTitle
        )
        nowPlayingStateStore.save(state)
        playbackState.replace(state)
        applyVantaEqualizer()
    }

    private fun applyLiveRadioMetadata(metadata: Metadata) {
        val parsed = LiveRadioMetadataParser.parseMetadata(metadata) ?: return
        val stationName = liveRadioStationName.orEmpty()
        if (LiveRadioMetadataParser.isStationPlaceholder(parsed.title, parsed.artist, stationName)) {
            return
        }
        val displayTitle = DisplayMetadataCleaner.cleanTitle(parsed.title)
        val displayArtist = parsed.artist.trim().ifBlank { "Unknown artist" }
        val mediaItem = player.currentMediaItem
        if (mediaItem != null) {
            val updatedMetadata = mediaItem.mediaMetadata.buildUpon()
                .setTitle(displayTitle)
                .setArtist(displayArtist)
                .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
                .build()
            player.replaceMediaItem(
                player.currentMediaItemIndex,
                mediaItem.buildUpon().setMediaMetadata(updatedMetadata).build()
            )
        }
        val snapshot = queueManager.snapshot()
        val state = NowPlayingState(
            title = displayTitle,
            artist = displayArtist,
            isPlaying = player.isPlaying,
            positionMs = player.currentPosition.coerceAtLeast(0L),
            bufferedMs = player.bufferedPosition.coerceAtLeast(0L),
            queuePosition = snapshot.queueIndex,
            queueSize = snapshot.queueSize,
            isLiveRadio = true,
            liveStationName = stationName
        )
        playbackState.update {
            copy(
                title = state.title,
                artist = state.artist,
                isPlaying = state.isPlaying,
                isLiveRadio = true,
                liveStationName = stationName
            )
        }
        nowPlayingStateStore.save(state)
    }

    fun duckLiveRadioForAd(durationMs: Long = 35_000L) {
        if (liveRadioStreamUrl == null) return
        val normalVolume = player.volume
        player.volume = 0f
        adDuckRestoreJob?.cancel()
        adDuckRestoreJob = serviceScope.launch {
            delay(durationMs)
            if (liveRadioStreamUrl != null && !userPauseRequested) {
                player.volume = normalVolume.coerceIn(0f, 1f)
            }
        }
    }


    private fun startAutoMixMonitor() {
        autoMixMonitorJob?.cancel()
        val config = autoMixPreferences.load()
        if (config.mode == AutoMixMode.OFF || autoMixTransitionInFlight) return
        val trackId = activeTrack?.track?.trackId ?: return
        if (!queueManager.snapshot().canPlayNext) return
        autoMixMonitorJob = serviceScope.launch {
            while (player.isPlaying && !userPauseRequested && activeTrack?.track?.trackId == trackId) {
                val duration = player.duration
                val position = player.currentPosition
                val leadMs = when (config.mode) {
                    AutoMixMode.CROSSFADE -> config.transitionSeconds.coerceAtMost(6) * 1_000L
                    AutoMixMode.AUTOMIX -> config.transitionSeconds * 1_000L
                    AutoMixMode.OFF -> 0L
                }
                if (duration > leadMs + 10_000L && duration - position in 250L..leadMs) {
                    beginAutoMixTransition(trackId, config)
                    return@launch
                }
                delay(200)
            }
        }
    }

    private fun beginAutoMixTransition(oldTrackId: Long, config: AutoMixConfig) {
        if (autoMixTransitionInFlight) return
        autoMixTransitionInFlight = true
        autoMixTransitionJob?.cancel()
        autoMixTransitionJob = serviceScope.launch {
            val fadeMs = (config.transitionSeconds * 1_000L).coerceAtLeast(2_000L)
            val steps = 24
            try {
                for (step in 0 until steps) {
                    if (userPauseRequested || activeTrack?.track?.trackId != oldTrackId) return@launch
                    val progress = step.toFloat() / steps
                    exoPlayer.volume = (1f - progress * progress).coerceIn(0.08f, 1f)
                    delay(fadeMs / steps)
                }
                if (userPauseRequested) return@launch
                playNextFromQueue()

                var waits = 0
                while (!userPauseRequested &&
                    (activeTrack?.track?.trackId == oldTrackId || player.playbackState != Player.STATE_READY) &&
                    waits < 120
                ) {
                    delay(100)
                    waits++
                }
                if (userPauseRequested) return@launch
                for (step in 1..steps) {
                    if (userPauseRequested) return@launch
                    val progress = step.toFloat() / steps
                    exoPlayer.volume = (progress * progress).coerceIn(0.08f, 1f)
                    delay((fadeMs / 2) / steps)
                }
            } finally {
                exoPlayer.volume = 1f
                autoMixTransitionInFlight = false
                if (player.isPlaying && !userPauseRequested) startAutoMixMonitor()
            }
        }
    }

    private fun cancelAutoMixJobs(resetVolume: Boolean) {
        autoMixMonitorJob?.cancel()
        autoMixMonitorJob = null
        autoMixTransitionJob?.cancel()
        autoMixTransitionJob = null
        autoMixTransitionInFlight = false
        if (resetVolume && ::exoPlayer.isInitialized) exoPlayer.volume = 1f
    }












    private fun logTrackTruth(
        track: UnifiedTrackWithSources,
        source: TrackSource,
        blocked: Boolean,
        blockReason: String? = null
    ) {
        val url = source.streamUrl
        val host = runCatching { java.net.URI(url).host }.getOrNull() ?: url.take(80)
        val isDemoStream = url.contains("soundhelix", ignoreCase = true)
        val isDemoTrack = track.track.title.contains("SoundHelix", ignoreCase = true) ||
            track.track.title.contains("Demo Audio", ignoreCase = true)
        Log.i("VANTA_TRACK_TRUTH", "" +
            "title='${track.track.title}' " +
            "artist='${track.track.artist}' " +
            "canonicalTrackId=${track.track.trackId} " +
            "providerId=${source.sourceType} " +
            "sourceTrackId=${source.sourceId} " +
            "streamUrlHost='$host' " +
            "isDemoStream=$isDemoStream " +
            "isDemoTrack=$isDemoTrack " +
            "playabilityStatus=${if (isDemoTrack) "DEMO" else "PLAYABLE"} " +
            "blocked=$blocked " +
            "blockReason=${blockReason ?: "none"}"
        )
    }

    /** Returns null if the source should be blocked (and sets the error message). */
    private fun checkDemoGuard(track: UnifiedTrackWithSources, source: TrackSource): String? {
        val url = source.streamUrl
        if (!url.contains("soundhelix", ignoreCase = true)) return null // not a demo stream, allow
        val title = track.track.title
        val artist = track.track.artist
        val isDemoTrack = title.contains("SoundHelix", ignoreCase = true) ||
            title.contains("Demo Audio", ignoreCase = true) ||
            artist.contains("SoundHelix", ignoreCase = true)
        if (isDemoTrack) return null // demo track using demo stream, allow

        return "Demo audio cannot be used for this track. '$title' is not a demo track but stream points to SoundHelix."
    }

    private fun markRoomPlaybackStartedIfEligible() {
        val track = activeTrack?.track ?: return
        val localLibraryId = track.localLibraryId ?: return
        serviceScope.launch(kotlinx.coroutines.Dispatchers.IO) { localLibraryRepository.incrementPlayCount(localLibraryId) }
    }

    private fun skipUnplayableDjTrackIfNeeded(reason: String) {
        if (queueManager.queueMode != QueueMode.AI_DJ_QUEUE) return
        if (!queueManager.snapshot().canPlayNext) {
            Log.w("VANTA_DJ_SKIP", "DJ track unplayable ($reason) but no next track in queue")
            return
        }
        Log.w("VANTA_DJ_SKIP", "Auto-skipping unplayable DJ track reason=$reason trackId=${activeTrack?.track?.trackId}")
        playNextFromQueue()
    }



    private fun buildQueueLabel(): String? {
        val snapshot = queueManager.snapshot()
        if (snapshot.originalQueue.isEmpty()) return null
        val current = (snapshot.currentOriginalIndex + 1).coerceAtLeast(1)
        return "$current of ${snapshot.originalQueue.size}"
    }

    private fun applyVantaEqualizer() {
        try {
            val config = com.audiophile.musicplayer.playback.dsp.VantaEqualizerPreferences(this).load()
            if (::vantaEqualizer.isInitialized) {
                vantaEqualizer.config = config
            }
            Log.d("VANTA_DSP", "equalizer_pushed eq=${config.eqEnabled} spatial=${config.spatialEnabled}")
        } catch (e: Exception) {
            Log.e("VANTA_DSP", "Equalizer apply failed", e)
        }
    }


    private fun startSummaryTimer() { }



    private fun parseHostFromUrl(url: String): String =
        runCatching { java.net.URI(url).host }.getOrNull() ?: "unknown"


    private fun normalizedStreamExpiryMs(
        streamUrl: String,
        providerExpiry: Long?,
        providerId: String?
    ): Long? {
        return com.audiophile.musicplayer.data.source.SourceRegistry.resolveMinExpiryMs(
            streamUrl,
            providerExpiry,
            providerId
        )
    }

    private fun normalizeEpochMs(value: Long?): Long? {
        if (value == null || value <= 0L) return null
        return if (value < 100_000_000_000L) value * 1000L else value
    }




    private fun orderedQueueTracks(): List<UnifiedTrackWithSources> {
        val snapshot = queueManager.snapshot()
        if (snapshot.originalQueue.isNotEmpty()) {
            return snapshot.originalQueue
        }
        return snapshot.currentTrack?.let { listOf(it) }.orEmpty()
    }

    private fun buildMediaItemForTrack(
        track: UnifiedTrackWithSources,
        preferredSource: TrackSource? = null,
    ): MediaItem {
        val source = preferredSource ?: track.sources.firstOrNull { it.streamUrl.isNotBlank() }
        val mediaItemMime = source?.streamUrl?.let {
            mediaItemMimeFor(null, it)
        }
        return PlaybackMediaItems.fromTrack(
            track = track,
            source = source,
            streamUrl = source?.streamUrl,
            mimeType = mediaItemMime
        )
    }

    /**
     * Safely replace the player's media items.
     *
     * CRITICAL: ExoPlayer triggers onTimelineChanged SYNCHRONOUSLY inside
     * setMediaItems(). MediaSessionImpl's listener then tries to build
     * PlayerInfo using the OLD SessionPositionInfo against the NEW timeline.
     * If the old index >= new window count â†’ IllegalStateException.
     *
     * Fix:
     * 1. stop() resets internal position to (0, TIME_UNSET)
     * 2. 3-arg setMediaItems makes timeline+position atomic
     * 3. Result: when onTimelineChanged fires, position already matches timeline
     */
    private fun applyPlaybackMediaItems(
        mediaItems: List<MediaItem>,
        startIndex: Int = 0,
        startPositionMs: Long = C.TIME_UNSET
    ): Boolean {
        if (!::exoPlayer.isInitialized) {
            Log.w("VANTA_PLAYBACK", "applyPlaybackMediaItems skipped â€” player not available")
            return false
        }
        if (mediaItems.isEmpty()) {
            Log.w("VANTA_PLAYBACK", "applyPlaybackMediaItems: empty list, stopping")
            return false
        }

        val safeStartIndex = startIndex.coerceIn(0, mediaItems.size - 1)
        player.beginQueueReplacement(mediaItems, safeStartIndex)
        try {
            exoPlayer.stop()
            exoPlayer.clearMediaItems()

            exoPlayer.playWhenReady = true
            exoPlayer.setMediaItems(mediaItems, safeStartIndex, startPositionMs)
            exoPlayer.prepare()
        } catch (e: Exception) {
            Log.e("VANTA_PLAYBACK", "applyPlaybackMediaItems failed", e)
            try {
                exoPlayer.stop()
            } catch (_: Exception) {
            }
            return false
        } finally {
            player.endQueueReplacement()
        }

        return true
    }


    private fun shouldSetMimeType(mime: String?): Boolean {
        return normalizeMediaItemMime(mime) != null
    }

    private fun mediaItemMimeFor(vararg candidates: String?): String? =
        candidates.asSequence()
            .mapNotNull { candidate ->
                normalizeMediaItemMime(candidate) ?: candidate?.let { inferMimeTypeFromUrl(it) }
            }
            .firstOrNull { shouldSetMimeType(it) }

    private fun normalizeMediaItemMime(mime: String?): String? {
        val clean = mime
            ?.substringBefore(';')
            ?.trim()
            ?.lowercase()
            ?.takeIf { it.isNotBlank() }
            ?: return null
        return when (clean) {
            "audio/flac", "audio/x-flac" -> MimeTypes.AUDIO_FLAC
            "audio/mpeg", "audio/mp3", "audio/x-mpeg", "audio/x-mp3" -> MimeTypes.AUDIO_MPEG
            "audio/mp4", "audio/aac", "audio/x-m4a", "audio/mp4a-latm" -> MimeTypes.AUDIO_MP4
            "audio/ogg", "application/ogg" -> MimeTypes.AUDIO_OGG
            "audio/opus" -> MimeTypes.AUDIO_OPUS
            "audio/wav", "audio/wave", "audio/x-wav" -> MimeTypes.AUDIO_WAV
            "audio/x-ms-wma" -> "audio/x-ms-wma"
            "application/octet-stream", "binary/octet-stream" -> null
            else -> clean.takeIf { it.startsWith("audio/") }
        }
    }

    private fun inferMimeTypeFromUrl(url: String?): String? {
        val ext = url
            ?.substringBefore('?')
            ?.substringBefore('#')
            ?.substringAfterLast('.', "")
            ?.lowercase()
            ?: return null
        return when (ext) {
            "flac" -> MimeTypes.AUDIO_FLAC
            "mp3" -> MimeTypes.AUDIO_MPEG
            "m4a", "aac" -> MimeTypes.AUDIO_MP4
            "ogg", "oga" -> MimeTypes.AUDIO_OGG
            "opus" -> MimeTypes.AUDIO_OPUS
            "wav" -> MimeTypes.AUDIO_WAV
            "wma" -> "audio/x-ms-wma"
            else -> null
        }
    }

    private fun isMedia3FfmpegAudioRendererAvailable(): Boolean =
        runCatching {
            Class.forName("androidx.media3.decoder.ffmpeg.FfmpegAudioRenderer")
        }.isSuccess




    companion object {
        var audioSessionId: Int = -1
            private set

        const val ACTION_PLAY_TRACK = "com.audiophile.musicplayer.action.PLAY_TRACK"
        const val ACTION_REFRESH_IMMERSIVE_AUDIO = "com.audiophile.musicplayer.action.REFRESH_EQUALIZER"
        const val ACTION_REFRESH_AUTO_MIX = "com.audiophile.musicplayer.action.REFRESH_AUTO_MIX"
        const val ACTION_PLAY_NEXT_FROM_QUEUE = "com.audiophile.musicplayer.action.PLAY_NEXT_FROM_QUEUE"
        const val ACTION_PLAY_PREVIOUS_FROM_QUEUE = "com.audiophile.musicplayer.action.PLAY_PREVIOUS_FROM_QUEUE"
        const val ACTION_PLAY = "com.audiophile.musicplayer.action.PLAY"
        const val ACTION_PAUSE = "com.audiophile.musicplayer.action.PAUSE"
        const val ACTION_RESUME = "com.audiophile.musicplayer.action.RESUME"
        const val ACTION_TOGGLE_PLAY_PAUSE = "com.audiophile.musicplayer.action.TOGGLE_PLAY_PAUSE"
        const val ACTION_SEEK_TO = "com.audiophile.musicplayer.action.SEEK_TO"
        const val ACTION_STOP = "com.audiophile.musicplayer.action.STOP"
        const val ACTION_PLAY_DIRECT_URL = "com.audiophile.musicplayer.action.PLAY_DIRECT_URL"
        const val ACTION_SKIP_LIVE_AD = "com.audiophile.musicplayer.action.SKIP_LIVE_AD"
        const val ACTION_REFRESH_QUEUE_TIMELINE = "com.audiophile.musicplayer.action.REFRESH_QUEUE_TIMELINE"

        const val ACTION_TOGGLE_FAVORITE = "vanta_toggle_favorite"
        const val ACTION_TOGGLE_SHUFFLE = "vanta_toggle_shuffle"
        const val ACTION_TOGGLE_REPEAT = "vanta_toggle_repeat"

        const val EXTRA_TRACK_ID = "extra_track_id"
        const val EXTRA_POSITION_MS = "extra_position_ms"
        const val EXTRA_STREAM_URL = "extra_stream_url"
        const val EXTRA_TITLE = "extra_title"
        const val EXTRA_ARTIST = "extra_artist"

        private const val CHANNEL_ID = "playback_channel"
        private const val NOTIFICATION_ID = 1001
        private const val AUTO_MAIN_STAGE_RESUME_ID = "vanta:main-stage:resume"
        private const val AUTO_MAIN_STAGE_QUEUE_ID = "vanta:main-stage:queue"
        private const val AUTO_MOOD_PREFIX = "vanta:mood:"
        private const val AUTO_RECENT_ID = "vanta:recent"
        private const val AUTO_TRACKS_ID = "vanta:tracks"
        private const val AUTO_SHUFFLE_ID = "vanta:shuffle"
        private const val AUTO_DEVICE_ID = "vanta:device"
        private const val AUTO_HIGH_QUALITY_ID = AndroidAutoBrowseController.AUTO_HIGH_QUALITY_ID
        private const val AUTO_TRACK_PREFIX = "vanta:track:"
        private const val AUTO_REMOTE_TRACK_PREFIX = AndroidAutoBrowseController.AUTO_REMOTE_TRACK_PREFIX
        private const val AUTO_ARTIST_PREFIX = "vanta:artist:"
        private const val AUTO_ALBUM_PREFIX = "vanta:album:"
        private const val AUTO_PAGE_SIZE = 50
        private const val AUTO_SEARCH_LIMIT = 50
        private const val AUTO_RESOLVE_TIMEOUT_MS = 12_000L
        private const val AUTO_REMOTE_CACHE_SIZE = 200
        private const val AUTO_HIGH_QUALITY_KBPS = 900
        private const val AUTO_SONG_RADIO_ID = "vanta:radio:song"
        private const val AUTO_ARTIST_RADIO_ID = "vanta:radio:artist"
        private const val AUTO_LIKED_ID = "vanta:liked"
        private const val AUTO_PLAYLISTS_ID = "vanta:playlists"
        private const val AUTO_PLAYLIST_PREFIX = "vanta:playlist:"
        private const val AUTO_MADE_FOR_YOU_ID = "vanta:made-for-you"
        private const val AUTO_DJ_ID = "vanta:ai-dj"
        private const val AUTO_LYRICS_ID = "vanta:lyrics"
    }
}