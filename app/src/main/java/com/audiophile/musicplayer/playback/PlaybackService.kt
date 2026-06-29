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

class PlaybackService : MediaLibraryService() {
    private var mediaSession: MediaLibrarySession? = null
    private lateinit var exoPlayer: ExoPlayer
    private lateinit var player: QueueAwarePlayer
    private lateinit var trackRepository: TrackRepository
    private lateinit var localLibraryRepository: LocalLibraryRepository
    private lateinit var queueManager: QueueManager
    private lateinit var nowPlayingStateStore: NowPlayingStateStore
    private lateinit var playbackState: PlaybackStateHolder
    private lateinit var sourceRegistry: com.audiophile.musicplayer.data.source.SourceRegistry

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    @Volatile private var activeTrack: UnifiedTrackWithSources? = null
    @Volatile private var activeResolution: PlaybackSourceResolution? = null
    private var failedSourceIds = linkedSetOf<Long>()
    @Volatile private var currentSource: TrackSource? = null
    private var currentQualityInfo: VantaQualityInfo? = null
    private var countedPlaybackTrackId: Long? = null
    private val stabilityCounters = PlaybackStabilityCounters()
    private var summaryJob: Job? = null
    private var stallMonitorJob: Job? = null
    private var streamRenewalJob: Job? = null
    private var prefetchNextTrackJob: Job? = null
    private var currentStreamExpiresAtMs: Long = 0L
    private var expiredHosts = mutableSetOf<String>()
    private var hasReResolvedOnCurrentReBuffer = false
    private var unsupportedSourceIds = linkedSetOf<Long>()
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

        vantaEqualizer = com.audiophile.musicplayer.playback.dsp.VantaEqualizerProcessor()
        com.audiophile.musicplayer.playback.dsp.VantaEqualizerHolder.processor = vantaEqualizer
        autoMixPreferences = AutoMixPreferences(this)

        val container = application.appContainer
        sessionTrustPolicy = MediaSessionTrustPolicy(PackageValidator(this))
        trackRepository = container.trackRepository
        localLibraryRepository = container.localLibraryRepository
        queueManager = container.queueManager
        nowPlayingStateStore = container.nowPlayingStateStore
        playbackState = container.playbackStateHolder
        sourceRegistry = container.sourceRegistry

        // Restore failed sources from previous session for the same track
        val persistedFailed = nowPlayingStateStore.loadFailedSourceIds()
        failedSourceIds = LinkedHashSet(persistedFailed)

        aiDjPlaybackManager = AiDjPlaybackManager(this, container.radioApiService)

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
            lyricsRepository = container.lyricsRepository,
            syncPreferencesProvider = { com.audiophile.musicplayer.data.lyrics.LyricsSyncPreferences(this) },
            immersivePreferencesProvider = { com.audiophile.musicplayer.playback.dsp.VantaEqualizerPreferences(this) },
            currentTrackIdProvider = { currentMediaTrackId() },
        )

        exoPlayer.addListener(
                    object : Player.Listener {
                        override fun onPlayerError(error: PlaybackException) {
                            try {
                            stabilityCounters.errorCount++
                            stabilityCounters.lastPlaybackError = error.message ?: "Playback error"
                            emitPlaybackSummary("player error")
                            Log.e("VANTA_PLAYER_ERROR", "errorCode=${error.errorCode} msg='${error.message}' currentSource=${currentSource?.sourceId} activeTrack=${activeTrack?.track?.trackId}")
                            VantaDiagnosticLog.error(
                                "Playback",
                                "player_error code=${error.errorCode} track=${activeTrack?.track?.trackId} msg=${error.message}"
                            )
                            Log.w("VANTA_SOURCE_REJECTED", "sourceType=${currentSource?.sourceType} track=${activeTrack?.track?.title} errorCode=${error.errorCode}")
                            logDeepHttpError(error)
                            // Check for FLAC decoder hardware failure â€” marks source unsupported
                            // rather than failed so we try alternative codec paths
                            val isFlacDecoderFailure = error.message?.contains("c2.android.flac.decoder", ignoreCase = true) == true ||
                                error.message?.contains("MediaCodecAudioRenderer", ignoreCase = true) == true ||
                                error.message?.contains("Decoder init failed", ignoreCase = true) == true ||
                                error.message?.contains("codec reported an error", ignoreCase = true) == true
                            if (isFlacDecoderFailure) {
                                Log.w("VANTA_QUALITY_FALLBACK", "FLAC decoder failure detected â€” marking source unsupported")
                                emitPlaybackSummary("flac decoder failed")
                                currentSource?.let {
                                    unsupportedSourceIds += it.sourceId
                                    // Don't add to failedSourceIds â€” the source is fine, the decoder can't handle it
                                    // Also mark expiredHosts so we skip re-resolving to the same host
                                    markCurrentHostAsExpired()
                                }
                                persistCurrentState(playbackError = "FLAC decoder failed")
                                retryNextSource()
                                return
                            }
                            currentSource?.let {
                                failedSourceIds += it.sourceId
                                nowPlayingStateStore.saveFailedSourceIds(failedSourceIds)
                                markCurrentHostAsExpired()
                            }
                            persistCurrentState(playbackError = error.message ?: "Playback error")
                            retryNextSource()
                            } catch (e: Exception) {
                                Log.e("VANTA_PLAYBACK", "onPlayerError handler crashed", e)
                                activeTrack?.let {
                                    publishNowPlayingSnapshot(it, isPlaying = false, playbackError = error.message ?: "Playback error", reason = "Playback error")
                                }
                                runCatching { retryNextSource() }
                            }
                        }

                        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                            super.onMediaItemTransition(mediaItem, reason)
                            stabilityCounters.mediaItemResetCount++
                            Log.d("VANTA_PLAYBACK", "PlaybackService received mediaItem transition: ${mediaItem?.mediaId} reason=$reason")
                            persistCurrentState()
                        }

                        override fun onIsPlayingChanged(isPlaying: Boolean) {
                            Log.d("VANTA_PLAYBACK_STABILITY", "onIsPlayingChanged isPlaying=$isPlaying playWhenReady=${player.playWhenReady} playbackSuppressionReason=${player.playbackSuppressionReason} pos=${player.currentPosition} buffered=${player.bufferedPosition} duration=${player.duration} currentSource=${currentSource?.sourceId}")
                            if (isPlaying) {
                                startAutoMixMonitor()
                                markRoomPlaybackStartedIfEligible()
                                if (stabilityCounters.trackStartTimeMs == 0L) {
                                    stabilityCounters.trackStartTimeMs = System.currentTimeMillis()
                                    stabilityCounters.startPositionMs = player.currentPosition.coerceAtLeast(0L)
                                    
                                    // Trigger AI DJ prefetch for the NEXT track when current track actually starts
                                    activeTrack?.let { current ->
                                        serviceScope.launch {
                                            val snapshot = queueManager.snapshot()
                                            val next = queueManager.getNextTrack()
                                            val recent = queueManager.getRecentTracks(5)
                                            aiDjPlaybackManager.onTrackStarted(
                                                recentTracks = recent,
                                                nextTrack = next,
                                                playbackGeneration = activePlaybackGeneration
                                            )
                                        }
                                    }
                                }
                                startSummaryTimer()
                                val sourceSnapshot = currentSource
                                if (sourceSnapshot != null) {
                                    Log.d("VANTA_STREAM_RENEW", "initStreamExpiryScheduling via onIsPlayingChanged")
                                    initStreamExpiryScheduling(sourceSnapshot)
                                } else {
                                    Log.w("VANTA_STREAM_RENEW", "onIsPlayingChanged(true) skipped: currentSource is null")
                                }
                            } else {
                                if (player.playbackState == Player.STATE_ENDED || player.playbackState == Player.STATE_IDLE) {
                                    emitPlaybackSummary("playback stopped")
                                    cancelSummaryTimer()
                                }
                            }
                            persistCurrentState()
                        }

                        override fun onPlaybackStateChanged(playbackState: Int) {
                            val stateName = when (playbackState) {
                                Player.STATE_IDLE -> "IDLE"
                                Player.STATE_BUFFERING -> "BUFFERING"
                                Player.STATE_READY -> "READY"
                                Player.STATE_ENDED -> "ENDED"
                                else -> "UNKNOWN($playbackState)"
                            }
                            val bufferedPct = if (player.duration > 0) (player.bufferedPosition * 100 / player.duration).toInt() else 0
                            Log.d("VANTA_PLAYER_STATE", "state=$stateName isPlaying=${player.isPlaying} pos=${player.currentPosition} buffered=${player.bufferedPosition} bufferedPct=$bufferedPct% duration=${player.duration} mediaId=${player.currentMediaItem?.mediaId}")
                            Log.d("VANTA_PLAYBACK_TRACE", "step='player_state' state='$stateName' isPlaying=${player.isPlaying} pos=${player.currentPosition} buffered=${player.bufferedPosition} duration=${player.duration}")
                            if (playbackState == Player.STATE_BUFFERING) {
                                Log.w("VANTA_BUFFERING", "BUFFERING START pos=${player.currentPosition} buffered=${player.bufferedPosition} duration=${player.duration} mediaId=${player.currentMediaItem?.mediaId}")
                                stabilityCounters.bufferingCount++
                                // If we've already started playing audio (trackStartTimeMs set), this is a re-buffer
                                if (stabilityCounters.trackStartTimeMs > 0L) {
                                    stabilityCounters.reBufferCount++
                                }
                                if (stabilityCounters.bufferingStartMs == 0L) {
                                    stabilityCounters.bufferingStartMs = System.currentTimeMillis()
                                }
                            }
                            if (playbackState == Player.STATE_READY && player.isPlaying) {
                                Log.i("VANTA_BUFFERING", "BUFFERING END / READY pos=${player.currentPosition} buffered=${player.bufferedPosition} duration=${player.duration}")
                                if (stabilityCounters.bufferingStartMs > 0L) {
                                    val bufferingDuration = System.currentTimeMillis() - stabilityCounters.bufferingStartMs
                                    stabilityCounters.totalBufferingMs += bufferingDuration
                                    // If this was a re-buffer (audio had already started), track separately
                                    if (stabilityCounters.trackStartTimeMs > 0L) {
                                        stabilityCounters.reBufferTotalMs += bufferingDuration
                                    }
                                    stabilityCounters.bufferingStartMs = 0L
                                }
                                stabilityCounters.readyCount++
                                checkQualityFallback()
                                refreshMeasuredPlaybackQuality()
                                prefetchNextQueueTrack()
                            }
                            if (playbackState == Player.STATE_ENDED && !autoMixTransitionInFlight) {
                                emitPlaybackSummary("track ended")
                                cancelSummaryTimer()
                                cancelStreamRenewal()
                                Log.d("VANTA_QUEUE_TRUTH", "action=auto_advance track_ended trackId=${activeTrack?.track?.trackId}")
                                activeTrack?.let { track ->
                                    application.appContainer.aiDjRecommendationEngine.recordFullPlay(
                                        trackId = track.track.trackId,
                                        artist = track.track.artist,
                                        genre = track.track.genre
                                    )
                                    val duration = player.duration.coerceAtLeast(0L)
                                    val position = player.currentPosition.coerceAtLeast(0L)
                                    application.appContainer.lastFmScrobbler.scrobbleIfConfigured(
                                        com.audiophile.musicplayer.data.lastfm.LastFmScrobbler.ScrobbleCandidate(
                                            title = track.track.title.orEmpty(),
                                            artist = track.track.artist.orEmpty(),
                                            album = track.track.albumName,
                                            durationMs = duration,
                                            positionMs = position
                                        )
                                    )
                                }
                                
                                if (aiDjPlaybackManager.playDjSegment(activePlaybackGeneration) {
                                    playNextFromQueue()
                                }) {
                                    Log.d("VANTA_AI_DJ", "Delaying next track for DJ narration.")
                                } else {
                                    playNextFromQueue()
                                }
                                return
                            }
                            persistCurrentState()
                        }

                        override fun onIsLoadingChanged(isLoading: Boolean) {
                            Log.d("VANTA_AUDIO_STALL", "onIsLoadingChanged isLoading=$isLoading pos=${player.currentPosition} buffered=${player.bufferedPosition} duration=${player.duration}")
                        }

                        override fun onMetadata(metadata: Metadata) {
                            if (liveRadioStreamUrl == null) return
                            applyLiveRadioMetadata(metadata)
                        }

                        override fun onPositionDiscontinuity(
                            oldPosition: Player.PositionInfo,
                            newPosition: Player.PositionInfo,
                            reason: Int
                        ) {
                            val reasonName = when (reason) {
                                Player.DISCONTINUITY_REASON_AUTO_TRANSITION -> "AUTO_TRANSITION"
                                Player.DISCONTINUITY_REASON_SEEK -> "SEEK"
                                Player.DISCONTINUITY_REASON_SEEK_ADJUSTMENT -> "SEEK_ADJUSTMENT"
                                Player.DISCONTINUITY_REASON_INTERNAL -> "INTERNAL"
                                else -> "UNKNOWN($reason)"
                            }
                            Log.d("VANTA_PLAYBACK_STABILITY", "onPositionDiscontinuity reason=$reasonName oldPos=${oldPosition.positionMs} newPos=${newPosition.positionMs}")
                            persistCurrentState()
                        }
                    }
                )

        val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
        val sessionActivity = launchIntent?.let {
            PendingIntent.getActivity(
                this,
                0,
                it,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
        }
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
                        queueManager.setOriginalQueue(shuffled, 0, QueueMode.NORMAL_QUEUE)
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
                            queueManager.setOriginalQueue(djTracks, 0, QueueMode.NORMAL_QUEUE)
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

        mediaSession = MediaLibrarySession.Builder(this, player, androidAutoCallback)
            .setId("vanta_media_library")
            .apply { sessionActivity?.let { setSessionActivity(it) } }
            .setBitmapLoader(autoController.createBitmapLoader())
            .build()
        player.refreshQueueTimeline()

        serviceScope.launch {
            container.registerConfiguredProviders()
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

            queueManager.setOriginalQueue(queue, queueStartIndex, QueueMode.NORMAL_QUEUE)
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
        queueManager.setOriginalQueue(radioTracks, 0, QueueMode.NORMAL_QUEUE)
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
        queueManager.setOriginalQueue(listOf(track), 0, QueueMode.NORMAL_QUEUE)
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
        return runCatching { Uri.parse(url) }.getOrNull()
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
        persistCurrentState()
        cancelAutoMixJobs(resetVolume = false)
        prefetchNextTrackJob?.cancel()
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
        // Guard: if the same track is already active and playing, don't reset/reschedule
        if (activeTrack?.track?.trackId == trackId && player.playbackState == Player.STATE_READY && player.isPlaying) {
            Log.d("VANTA_PLAYBACK", "playTrack() skipped: track $trackId already active and playing")
            return
        }

        // Eagerly detect and record manual skip on the previous track
        val prevTrack = activeTrack
        if (prevTrack != null) {
            val currentPos = player.currentPosition
            val duration = player.duration
            if (currentPos in 1000..29999L && (duration <= 0L || currentPos < duration * 0.3f)) {
                Log.d("VANTA_PLAYBACK_STABILITY", "Detecting skip for track ${prevTrack.track.title} at pos $currentPos")
                application.appContainer.aiDjRecommendationEngine.recordSkip(
                    trackId = prevTrack.track.trackId,
                    artist = prevTrack.track.artist
                )
            }
        }

        val generation = playbackRequestGeneration.incrementAndGet()
        activePlaybackGeneration = generation
        val prevTrackId = activeTrack?.track?.trackId
        userPauseRequested = false
        Log.d("VANTA_PLAYBACK_TRACE", "step='service_received_play' trackId=$trackId generation=$generation")
        Log.d("VANTA_PLAYBACK_INTENT", "action=user_play_track trackId=$trackId userPauseRequested=false generation=$generation")
        Log.d("VANTA_QUEUE_TRUTH", "action=play_track prevTrackId=$prevTrackId nextTrackId=$trackId generation=$generation")
        // Reset stability counters eagerly on main thread before any async work
        // to prevent old-track ExoPlayer callbacks from polluting new track counters
        stabilityCounters.reset()
        cancelSummaryTimer()
        cancelStreamRenewal()
        expiredHosts.clear()
        hasReResolvedOnCurrentReBuffer = false
        currentStreamExpiresAtMs = 0L
        currentQualityInfo = null
        serviceScope.launch {
            try {
            Log.d("VANTA_PLAYBACK_GENERATION", "source_resolve_start trackId=$trackId generation=$generation")
            val track = trackRepository.getTrackWithSources(trackId)
            if (generation != activePlaybackGeneration) {
                Log.d("VANTA_PLAYBACK_TRACE", "step='playback_blocked' reason='stale_generation' generation=$generation active=$activePlaybackGeneration")
                Log.w("VANTA_PLAYBACK_GENERATION", "abort stale operation reason=stale_generation generation=$generation active=$activePlaybackGeneration")
                return@launch
            }
            if (track == null) {
                Log.d("VANTA_PLAYBACK_TRACE", "step='playback_blocked' reason='track_not_found' trackId=$trackId")
                val errorState = NowPlayingState(errorMessage = "Track not found")
                nowPlayingStateStore.save(errorState)
                playbackState.replace(errorState)
                skipUnplayableDjTrackIfNeeded("track_not_found")
                return@launch
            }

            Log.d("VANTA_PLAYBACK_TRACE", "step='sources_loaded' trackId=$trackId sourceCount=${track.sources.size} sourcesWithUrl=${track.sources.count { it.streamUrl.isNotBlank() }}")
            track.sources.forEachIndexed { i, s ->
                val hasProviderId = !s.externalProviderId.isNullOrBlank()
                val hasExtTrackId = !s.externalTrackId.isNullOrBlank()
                val canRenew = hasProviderId && hasExtTrackId
                Log.d("VANTA_SOURCE_TRUTH", "trackId=$trackId sourceId=${s.sourceId} providerId=${s.externalProviderId ?: "null"} externalTrackId=${s.externalTrackId ?: "null"} hasStreamUrl=${s.streamUrl.isNotBlank()} expiresAtMs=${s.expiresAtMs ?: "null"} canRenew=$canRenew")
                if (s.streamUrl.isNotBlank() && !canRenew) {
                    Log.w("VANTA_SOURCE_TRUTH", "missing_provider_identity trackId=$trackId sourceId=${s.sourceId} canRenew=false â€” expired URLs cannot be recovered")
                }
                Log.d("VANTA_PLAYBACK_TRACE", "step='source_candidate' sourceId=${s.sourceId} type=${s.sourceType} failed=${s.sourceId in failedSourceIds} unsupported=${s.sourceId in unsupportedSourceIds} hasUrl=${s.streamUrl.isNotBlank()}")
            }

            activeTrack = track
            publishNowPlayingSnapshot(track, isPlaying = false, reason = "Track stopped")
            liveRadioStreamUrl = null
            liveRadioStationName = null
            val savedState = nowPlayingStateStore.load()
            val effectivePolicy = policy.copy(
                selectedTitle = policy.selectedTitle ?: savedState?.title ?: track.track.title,
                selectedArtist = policy.selectedArtist ?: savedState?.artist ?: track.track.artist,
                selectedDurationMs = policy.selectedDurationMs ?: savedState?.durationMs ?: track.track.durationMs,
                selectedIsrc = policy.selectedIsrc ?: track.track.isrc,
                preferredProviderId = policy.preferredProviderId ?: savedState?.preferredProviderId,
                preferredExternalTrackId = policy.preferredExternalTrackId ?: savedState?.preferredExternalTrackId,
                userQuery = policy.userQuery ?: savedState?.userQuery
            )
            // Load favorite status asynchronously so persistCurrentState doesn't block the main thread
            cachedIsFavorite = track.track.localLibraryId?.let { localLibraryRepository.songById(it)?.isFavorite } ?: false
            activeResolution = trackRepository.resolvePlaybackSourcesForTrack(trackId, effectivePolicy)
            if (generation != activePlaybackGeneration) {
                Log.w("VANTA_PLAYBACK_GENERATION", "abort stale operation reason=stale_generation_after_resolve generation=$generation active=$activePlaybackGeneration")
                return@launch
            }
            Log.d("VANTA_PLAYBACK_TRACE", "step='resolve_result' trackId=$trackId orderedCandidates=${activeResolution?.orderedCandidates?.size ?: 0}")
            Log.d("VANTA_PLAYBACK_GENERATION", "source_resolve_end trackId=$trackId generation=$generation")
            failedSourceIds.clear()
            nowPlayingStateStore.saveFailedSourceIds(failedSourceIds)
            countedPlaybackTrackId = null
            // Record bitrate and host from primary source
            val primary = activeResolution?.primary
            stabilityCounters.currentBitrateKbps = primary?.bitrate ?: 0
            primary?.streamUrl?.let { url ->
                stabilityCounters.streamHost = runCatching { java.net.URI(url).host }.getOrDefault("unknown")
            }
            playNextAvailableSource(generation = generation)
            } catch (e: Exception) {
                Log.e("VANTA_PLAYBACK", "playTrack crashed trackId=$trackId", e)
                activeTrack?.let {
                    publishNowPlayingSnapshot(it, isPlaying = false, playbackError = e.message ?: "Playback failed", reason = "Playback failed exception")
                } ?: run {
                    val errorState = NowPlayingState(
                        trackId = trackId.toString(),
                        errorMessage = e.message ?: "Playback failed"
                    )
                    playbackState.replace(errorState)
                    nowPlayingStateStore.save(errorState)
                }
                skipUnplayableDjTrackIfNeeded("play_track_exception")
                runCatching { playNextFromQueue() }
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
            persistCurrentState()
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
        persistCurrentState()
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
        persistCurrentState()
    }

    fun playNextFromQueue(policy: SourceSelectionPolicy = SourceSelectionPolicy()) {
        serviceScope.launch {
            val nextTrack = queueManager.getNextTrack() ?: run {
                Log.d("VANTA_QUEUE_TRUTH", "action=next_from_queue no_next_track")
                return@launch
            }
            Log.d("VANTA_QUEUE_TRUTH", "action=next_from_queue trackId=${nextTrack.track.trackId} title=${nextTrack.track.title}")
            playTrack(nextTrack.track.trackId, policy)
        }
    }

    fun playPreviousFromQueue(policy: SourceSelectionPolicy = SourceSelectionPolicy()) {
        serviceScope.launch {
            val previousTrack = queueManager.getPreviousTrack() ?: run {
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
        emitPlaybackSummary("user stop")
        cancelSummaryTimer()
        cancelStreamRenewal()
        liveRadioStreamUrl = null
        liveRadioStationName = null
        adDuckRestoreJob?.cancel()
        player.stop()
        queueManager.markPlaybackPosition(0L)
        persistCurrentState()
    }

    fun playDirectUrl(directUrl: String, title: String = "Direct stream", artist: String = "Unknown source") {
        val generation = playbackRequestGeneration.incrementAndGet()
        activePlaybackGeneration = generation
        userPauseRequested = false
        Log.d("VANTA_PLAYBACK_INTENT", "action=user_play_direct url=${directUrl.take(60)} userPauseRequested=false generation=$generation")
        currentSource = null
        currentQualityInfo = null
        activeTrack = null
        activeResolution = null
        autoMainStageLyricsController.cancel()
        liveRadioStreamUrl = directUrl
        liveRadioStationName = title
        failedSourceIds.clear()
        unsupportedSourceIds.clear()
        nowPlayingStateStore.saveFailedSourceIds(failedSourceIds)
        countedPlaybackTrackId = null
        stabilityCounters.reset()
        cancelSummaryTimer()

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

    private fun retryNextSource() {
        val trackIdAtError = activeTrack?.track?.trackId
        val wasPlaying = !userPauseRequested
        val generation = activePlaybackGeneration
        serviceScope.launch {
            if (generation != activePlaybackGeneration) {
                Log.w("VANTA_PLAYBACK_GENERATION", "abort stale operation reason=stale_generation_retryNextSource generation=$generation active=$activePlaybackGeneration")
                return@launch
            }
            if (activeTrack?.track?.trackId != trackIdAtError) {
                Log.w("VANTA_PLAYBACK", "retryNextSource() aborted: active track changed since error (was=$trackIdAtError now=${activeTrack?.track?.trackId})")
                return@launch
            }
            playNextAvailableSource(isErrorRecovery = true, wasPlayingBefore = wasPlaying, generation = generation)
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

    private sealed class StreamValidationResult {
        data class Valid(val contentType: String? = null) : StreamValidationResult()
        data class Invalid(val reason: String) : StreamValidationResult()
    }

    private suspend fun validateStream(url: String): StreamValidationResult {
        return withContext(Dispatchers.IO) {
            try {
                var connection = java.net.URL(url).openConnection() as java.net.HttpURLConnection
                connection.setRequestProperty("User-Agent", "VANTA/1.0 (Android 14; en-US)")
                connection.connectTimeout = 5000
                connection.readTimeout = 5000
                connection.instanceFollowRedirects = true

                val host = java.net.URI(url).host ?: "unknown"
                var responseCode = -1
                var usedHead = true

                // YouTube CDN (googlevideo.com) blocks HEAD but serves GET — skip HEAD entirely
                val skipHead = host.contains("googlevideo.com", ignoreCase = true)

                if (skipHead) {
                    connection.disconnect()
                    connection = java.net.URL(url).openConnection() as java.net.HttpURLConnection
                    connection.setRequestProperty("User-Agent", "VANTA/1.0 (Android 14; en-US)")
                    connection.setRequestProperty("Range", "bytes=0-1")
                    connection.connectTimeout = 5000
                    connection.readTimeout = 5000
                    connection.instanceFollowRedirects = true
                    connection.connect()
                    responseCode = connection.responseCode
                    usedHead = false
                } else {
                    connection.requestMethod = "HEAD"
                    connection.connect()
                    responseCode = connection.responseCode
                    usedHead = true

                    if (responseCode == 405 || responseCode == 400 || responseCode == 403) {
                        connection.disconnect()
                        connection = java.net.URL(url).openConnection() as java.net.HttpURLConnection
                        connection.setRequestProperty("User-Agent", "VANTA/1.0 (Android 14; en-US)")
                        connection.setRequestProperty("Range", "bytes=0-1")
                        connection.connectTimeout = 5000
                        connection.readTimeout = 5000
                        connection.instanceFollowRedirects = true
                        connection.connect()
                        responseCode = connection.responseCode
                        usedHead = false
                    }
                }

                val contentType = connection.contentType
                val contentLength = connection.contentLengthLong
                val acceptsRanges = connection.getHeaderField("Accept-Ranges")

                val passed = responseCode in 200..299
                val requireFlacProbe = shouldRequireFlacProbe(url, contentType)
                val sniffPossibleFlac = shouldSniffPossibleFlacPayload(contentType)
                val flacProbe = if (passed && (requireFlacProbe || sniffPossibleFlac)) {
                    probeFlacPayload(url)
                } else null
                val finalPassed = passed && (!requireFlacProbe || flacProbe?.valid != false)
                val resolvedContentType = if (flacProbe?.valid == true) {
                    MimeTypes.AUDIO_FLAC
                } else {
                    normalizeMediaItemMime(contentType) ?: contentType
                }
                Log.d("VANTA_STREAM_VALIDATE",
                    "host=$host method=${if (usedHead) "HEAD" else "GET+Range"} statusCode=$responseCode " +
                    "contentType=$contentType contentLength=$contentLength acceptsRanges=$acceptsRanges " +
                    "resolvedContentType=${resolvedContentType ?: "unknown"} " +
                    "validation=${if (finalPassed) "PASS" else "FAIL"}" +
                    if (flacProbe != null) " flacProbe=${if (flacProbe.valid) "PASS" else "FAIL"} flacReason='${flacProbe.reason}'" else "" +
                    if (!passed) " reason=HTTP_${responseCode}" else ""
                )

                connection.disconnect()
                when {
                    !passed -> StreamValidationResult.Invalid("HTTP $responseCode")
                    requireFlacProbe && flacProbe?.valid == false -> StreamValidationResult.Invalid("FLAC probe failed: ${flacProbe.reason}")
                    else -> StreamValidationResult.Valid(resolvedContentType)
                }
            } catch (e: Exception) {
                val host = runCatching { java.net.URI(url).host }.getOrDefault("unknown")
                Log.e("VANTA_STREAM_VALIDATE", "host=$host method=HEAD/GET+Range reason='${e.message}' validation=FAIL")
                StreamValidationResult.Invalid(e.message ?: "Unknown error")
            }
        }
    }

    private data class FlacProbeResult(val valid: Boolean, val reason: String)

    private fun shouldRequireFlacProbe(url: String, contentType: String?): Boolean {
        val lowerUrl = url.lowercase()
        val lowerType = contentType.orEmpty().lowercase()
        return lowerType.contains("audio/flac") ||
            lowerType.contains("audio/x-flac") ||
            lowerUrl.substringBefore('?').endsWith(".flac")
    }

    private fun shouldSniffPossibleFlacPayload(contentType: String?): Boolean {
        val lowerType = contentType.orEmpty().lowercase()
        return lowerType.contains("application/octet-stream") ||
            lowerType.contains("binary/octet-stream")
    }

    private fun probeFlacPayload(url: String): FlacProbeResult {
        var connection: java.net.HttpURLConnection? = null
        return try {
            connection = java.net.URL(url).openConnection() as java.net.HttpURLConnection
            connection.setRequestProperty("User-Agent", "VANTA/1.0 (Android 14; en-US)")
            connection.setRequestProperty("Range", "bytes=0-4095")
            connection.connectTimeout = 5000
            connection.readTimeout = 5000
            connection.instanceFollowRedirects = true
            connection.connect()

            val responseCode = connection.responseCode
            if (responseCode !in 200..299) {
                return FlacProbeResult(false, "HTTP_$responseCode")
            }

            val bytes = ByteArray(4096)
            var offset = 0
            connection.inputStream.use { input ->
                while (offset < bytes.size) {
                    val read = input.read(bytes, offset, bytes.size - offset)
                    if (read <= 0) break
                    offset += read
                }
            }

            if (offset < 8) {
                return FlacProbeResult(false, "too_few_bytes_$offset")
            }

            val markerOffset = findFlacMarker(bytes, offset)
            if (markerOffset < 0) {
                return FlacProbeResult(false, "missing_flac_marker")
            }
            if (markerOffset + 8 > offset) {
                return FlacProbeResult(false, "missing_metadata_header")
            }

            val blockType = bytes[markerOffset + 4].toInt() and 0x7F
            val blockLength = ((bytes[markerOffset + 5].toInt() and 0xFF) shl 16) or
                ((bytes[markerOffset + 6].toInt() and 0xFF) shl 8) or
                (bytes[markerOffset + 7].toInt() and 0xFF)

            if (blockType != 0) {
                return FlacProbeResult(false, "missing_streaminfo_block")
            }
            if (blockLength != 34) {
                return FlacProbeResult(false, "invalid_streaminfo_length_$blockLength")
            }
            FlacProbeResult(true, "streaminfo_ok_offset_$markerOffset")
        } catch (e: Exception) {
            FlacProbeResult(false, e.message ?: "probe_error")
        } finally {
            connection?.disconnect()
        }
    }

    private fun findFlacMarker(bytes: ByteArray, limit: Int): Int {
        val max = minOf(limit - 4, 512)
        for (i in 0..max) {
            if (bytes[i] == 'f'.code.toByte() &&
                bytes[i + 1] == 'L'.code.toByte() &&
                bytes[i + 2] == 'a'.code.toByte() &&
                bytes[i + 3] == 'C'.code.toByte()
            ) {
                return i
            }
        }
        return -1
    }

    private fun updateCurrentQualityInfo(
        source: TrackSource,
        resolved: SourceResolvedStream? = null,
        validation: StreamValidationResult.Valid? = null,
        isValidated: Boolean,
        reason: String
    ) {
        val providerId = source.externalProviderId ?: source.sourceType.name.lowercase()
        val status = when {
            source.sourceType == SourceType.LOCAL -> SearchItemStatus.LOCAL_PLAYABLE
            isValidated -> SearchItemStatus.VALIDATED_PLAYABLE
            else -> SearchItemStatus.SOURCE_FOUND
        }
        val info = VantaQualityInfo.fromSource(
            bitrate = resolved?.bitrateKbps ?: source.bitrate,
            quality = resolved?.qualityLabel,
            mime = resolved?.mimeType ?: validation?.contentType ?: stabilityCounters.currentMimeType,
            status = status,
            isValidated = isValidated,
            sourceProviderId = providerId,
            reason = reason
        )
        currentQualityInfo = info.takeIf { it.bestQualityLabel() != null }
        if (isValidated) {
            VantaQualityInfo.logValidated(activeTrack?.track?.trackId?.toString(), currentQualityInfo)
        }
    }

    private fun refreshMeasuredPlaybackQuality() {
        val source = currentSource ?: return
        val audioFormat = exoPlayer.audioFormat
        val extractorBitrate = audioFormat?.averageBitrate
            ?.takeIf { it > 0 }
            ?.div(1000)
        if (extractorBitrate != null) {
            currentQualityInfo = VantaQualityInfo.fromSource(
                bitrate = extractorBitrate,
                quality = null,
                mime = audioFormat.sampleMimeType,
                format = null,
                sampleRateHz = audioFormat.sampleRate.takeIf { it > 0 },
                bitDepth = audioFormat.pcmEncoding.toBitDepth(),
                status = SearchItemStatus.VALIDATED_PLAYABLE,
                isValidated = true,
                sourceProviderId = source.externalProviderId ?: source.sourceType.name.lowercase(),
                reason = "measured_media_format",
                bitrateIsMeasured = true
            )
            persistCurrentState()
            return
        }

        val durationMs = player.duration.takeIf { it > 0 }
            ?: activeTrack?.track?.durationMs?.takeIf { it > 0 }
            ?: return
        val sourceId = source.sourceId
        serviceScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val measured = RemoteBitrateMeasurer.measureAverageKbps(source.streamUrl, durationMs) ?: return@launch
            if (currentSource?.sourceId != sourceId) return@launch
            val measuredInfo = VantaQualityInfo.fromSource(
                bitrate = measured,
                quality = null,
                mime = audioFormat?.sampleMimeType ?: stabilityCounters.currentMimeType,
                format = null,
                sampleRateHz = audioFormat?.sampleRate?.takeIf { it > 0 },
                bitDepth = audioFormat?.pcmEncoding?.toBitDepth(),
                status = SearchItemStatus.VALIDATED_PLAYABLE,
                isValidated = true,
                sourceProviderId = source.externalProviderId ?: source.sourceType.name.lowercase(),
                reason = "measured_content_length_duration",
                bitrateIsMeasured = true
            )
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                if (currentSource?.sourceId == sourceId) {
                    currentQualityInfo = measuredInfo
                    persistCurrentState()
                }
            }
        }
    }

    private fun Int.toBitDepth(): Int? = when (this) {
        androidx.media3.common.C.ENCODING_PCM_16BIT -> 16
        androidx.media3.common.C.ENCODING_PCM_24BIT -> 24
        androidx.media3.common.C.ENCODING_PCM_32BIT,
        androidx.media3.common.C.ENCODING_PCM_FLOAT -> 32
        else -> null
    }

    private fun logDeepHttpError(error: PlaybackException) {
        val sb = StringBuilder()
        sb.appendLine("errorCodeName=${error.errorCodeName} errorCode=${error.errorCode} msg='${error.message}'")
        var cause = error.cause
        var depth = 0
        while (cause != null && depth < 10) {
            val className = cause::class.qualifiedName ?: cause::class.simpleName ?: "Unknown"
            sb.append("  cause[$depth]: $className msg='${cause.message}'")
            if (cause is HttpDataSource.InvalidResponseCodeException) {
                sb.appendLine("")
                sb.appendLine("    responseCode=${cause.responseCode} responseMessage='${cause.responseMessage}'")
                sb.append("    headerFields=${cause.headerFields}")
                sb.appendLine("")
                sb.append("    uri=${cause.dataSpec.uri}")
            } else if (cause is HttpDataSource.HttpDataSourceException) {
                sb.appendLine("")
                sb.append("    type=${cause.type} uri=${cause.dataSpec.uri}")
            }
            sb.appendLine("")
            cause = cause.cause
            depth++
        }
        Log.e("VANTA_HTTP_ERROR", sb.toString())
    }

    private suspend fun playNextAvailableSource(
        isErrorRecovery: Boolean = false,
        wasPlayingBefore: Boolean = true,
        forceTrackSwitch: Boolean = false,
        generation: Long = activePlaybackGeneration
    ) {
        // Anti-re-resolve guard: skip only if the SAME track+source is already playing
        if (!isErrorRecovery && !forceTrackSwitch && player.playbackState == Player.STATE_READY && player.isPlaying) {
            val requestedTrackId = activeTrack?.track?.trackId
            val mediaTrackId = currentMediaTrackId()
            if (requestedTrackId != null && mediaTrackId != null && requestedTrackId == mediaTrackId) {
                Log.d("VANTA_MEDIA_ITEM_SET", "playNextAvailableSource() skipped: same track already playing trackId=$requestedTrackId")
                return
            }
            Log.w("VANTA_MEDIA_ITEM_SET", "playNextAvailableSource() proceeding: requested=$requestedTrackId media=$mediaTrackId (forceTrackSwitch=$forceTrackSwitch)")
        }
        Log.d("VANTA_PLAYBACK_GENERATION", "playNextAvailableSource generation=$generation active=$activePlaybackGeneration")
        while (true) {
            if (generation != activePlaybackGeneration) {
                Log.w("VANTA_PLAYBACK_GENERATION", "abort stale operation reason=stale_generation generation=$generation active=$activePlaybackGeneration")
                return
            }
            val track = activeTrack ?: run {
                Log.w("VANTA_MEDIA_ITEM_SET", "activeTrack is null")
                return
            }
            val resolution = activeResolution ?: run {
                Log.w("VANTA_MEDIA_ITEM_SET", "activeResolution is null for track=${track.track.trackId}")
                return
            }
            var nextSource = trackRepository.getNextFallbackSource(resolution, failedSourceIds, unsupportedSourceIds)
            if (nextSource == null) {
                Log.d("VANTA_PLAYBACK_TRACE", "step='playback_blocked' reason='no_fallback_source' trackId=${track.track.trackId} failedSourceIds=$failedSourceIds unsupportedSourceIds=$unsupportedSourceIds totalCandidates=${resolution.orderedCandidates.size}")
                Log.e("VANTA_MEDIA_ITEM_SET", "No fallback source available. failedSourceIds=$failedSourceIds expiredHosts=$expiredHosts generation=$generation")
                // Terminal state: requested track has no playable source
                // Stop old media so it doesn't continue playing under the failed track's identity
                val mediaTrackId = currentMediaTrackId()
                val hadOldMedia = mediaTrackId != null && mediaTrackId != track.track.trackId
                if (hadOldMedia) {
                    Log.w("VANTA_NOWPLAYING_TRUTH", "no_playable_source generation=$generation trackId=${track.track.trackId} stoppedOldMedia=true oldMediaTrackId=$mediaTrackId")
                    player.stop()
                    player.clearMediaItems()
                } else {
                    Log.w("VANTA_NOWPLAYING_TRUTH", "no_playable_source generation=$generation trackId=${track.track.trackId} stoppedOldMedia=false")
                }
                cancelStreamRenewal()
                currentSource = null
                currentQualityInfo = null
                val allAddonMissingIdentity = resolution.orderedCandidates.all {
                    it.sourceType == com.audiophile.musicplayer.data.local.entities.SourceType.ADDON &&
                    (it.externalProviderId.isNullOrBlank() || it.externalTrackId.isNullOrBlank())
                }
                val onlyProviderIsYouTube = resolution.orderedCandidates.all {
                    it.externalProviderId == "youtube_music"
                }
                val errorMessage = when {
                    resolution.orderedCandidates.isEmpty() ->
                        "No sources available. Add a source in Settings to play this track."
                    allAddonMissingIdentity ->
                        "Source expired. Re-search and try again."
                    onlyProviderIsYouTube ->
                        "YouTube stream unavailable. The track may be blocked or require sign-in."
                    else ->
                        "No playable source. Try a different quality or source."
                }
                val unavailableState = NowPlayingState(
                    trackId = track.track.trackId.toString(),
                    title = track.track.title,
                    artist = track.track.artist,
                    album = track.track.albumName,
                    artworkUrl = track.track.coverArtUrl,
                    durationMs = track.track.durationMs ?: 0L,
                    isPlaying = false,
                    errorMessage = errorMessage
                )
                playbackState.update {
                    copy(
                        trackId = unavailableState.trackId,
                        title = unavailableState.title,
                        artist = unavailableState.artist,
                        album = unavailableState.album,
                        artworkUrl = unavailableState.artworkUrl,
                        isPlaying = false,
                        errorMessage = unavailableState.errorMessage
                    )
                }
                nowPlayingStateStore.save(unavailableState)
                Log.w("VANTA_PLAYBACK_GENERATION", "persist_unavailable_state generation=$generation trackId=${track.track.trackId}")
                skipUnplayableDjTrackIfNeeded("no_playable_source")
                return
            }

            // Skip sources from hosts that have already been exhausted (same CDN, same expiry)
            if (nextSource.sourceType == com.audiophile.musicplayer.data.local.entities.SourceType.ADDON) {
                val host = parseHostFromUrl(nextSource.streamUrl)
                if (host in expiredHosts) {
                    Log.w("VANTA_MEDIA_ITEM_SET", "Skipping source ${nextSource.sourceId}: host $host already expired")
                    failedSourceIds += nextSource.sourceId
                    nowPlayingStateStore.saveFailedSourceIds(failedSourceIds)
                    continue
                }
            }

            val blockReason = checkDemoGuard(track, nextSource)
            logTrackTruth(track, nextSource, blocked = blockReason != null, blockReason = blockReason)
            if (blockReason != null) {
                Log.d("VANTA_PLAYBACK_TRACE", "step='playback_blocked' reason='demo_guard' trackId=${track.track.trackId} sourceId=${nextSource.sourceId} detail='$blockReason'")
                Log.e("VANTA_TRACK_TRUTH", "BLOCKED: $blockReason generation=$generation")
                // Stop old media so it doesn't continue playing under the blocked track's identity
                val mediaTrackId = currentMediaTrackId()
                val hadOldMedia = mediaTrackId != null && mediaTrackId != track.track.trackId
                if (hadOldMedia) {
                    Log.w("VANTA_NOWPLAYING_TRUTH", "blocked_source generation=$generation trackId=${track.track.trackId} stoppedOldMedia=true oldMediaTrackId=$mediaTrackId")
                    player.stop()
                    player.clearMediaItems()
                }
                cancelStreamRenewal()
                currentSource = null
                currentQualityInfo = null
                val blockedState = NowPlayingState(
                    trackId = track.track.trackId.toString(),
                    title = track.track.title,
                    artist = track.track.artist,
                    album = track.track.albumName,
                    artworkUrl = track.track.coverArtUrl,
                    durationMs = track.track.durationMs ?: 0L,
                    isPlaying = false,
                    errorMessage = blockReason
                )
                playbackState.update {
                    copy(
                        trackId = blockedState.trackId,
                        title = blockedState.title,
                        artist = blockedState.artist,
                        album = blockedState.album,
                        artworkUrl = blockedState.artworkUrl,
                        isPlaying = false,
                        errorMessage = blockedState.errorMessage
                    )
                }
                nowPlayingStateStore.save(blockedState)
                return
            }

            var resolvedQualityStream: SourceResolvedStream? = null

            if (nextSource.streamUrl.isBlank()) {
                val pId = nextSource.externalProviderId
                val eId = nextSource.externalTrackId
                if (!pId.isNullOrBlank() && !eId.isNullOrBlank()) {
                    Log.d("VANTA_SOURCE_RECOVER", "Attempting fresh resolve for blank URL providerId=$pId trackId=$eId")
                    val fresh = withContext(Dispatchers.IO) { sourceRegistry.resolveStream(pId, eId) }
                    if (generation != activePlaybackGeneration) {
                        Log.w("VANTA_PLAYBACK_GENERATION", "abort stale operation reason=stale_generation_after_blank_resolve generation=$generation active=$activePlaybackGeneration")
                        return
                    }
                    if (fresh != null && fresh.streamUrl.isNotBlank()) {
                        Log.i("VANTA_SOURCE_RECOVER", "Blank URL resolve succeeded for source ${nextSource.sourceId}")
                        val refreshedSource = nextSource.copy(
                            streamUrl = fresh.streamUrl,
                            bitrate = fresh.bitrateKbps,
                            expiresAtMs = normalizedStreamExpiryMs(fresh.streamUrl, fresh.expiresAt, pId)
                        )
                        withContext(Dispatchers.IO) { trackRepository.updateSource(refreshedSource) }
                        nextSource = refreshedSource
                        resolvedQualityStream = fresh
                        stabilityCounters.currentBitrateKbps = refreshedSource.bitrate
                        stabilityCounters.streamHost = parseHostFromUrl(refreshedSource.streamUrl)
                        fresh.mimeType?.let { stabilityCounters.currentMimeType = it }
                    } else {
                        Log.w("VANTA_SOURCE_RECOVER", "Blank URL resolve failed for source ${nextSource.sourceId}")
                        failedSourceIds += nextSource.sourceId
                        nowPlayingStateStore.saveFailedSourceIds(failedSourceIds)
                        continue
                    }
                } else {
                    Log.d("VANTA_PLAYBACK_TRACE", "step='playback_blocked' reason='blank_stream_url' sourceId=${nextSource.sourceId}")
                    Log.w("VANTA_STREAM_VALIDATE", "Stream URL is blank for source ${nextSource.sourceId}, skipping")
                    failedSourceIds += nextSource.sourceId
                    nowPlayingStateStore.saveFailedSourceIds(failedSourceIds)
                    continue
                }
            }

            val alreadyFailedUrls = resolution.orderedCandidates
                .filter { it.sourceId in failedSourceIds }
                .map { it.streamUrl }
                .toSet()
            if (nextSource.streamUrl in alreadyFailedUrls) {
                Log.w("VANTA_MEDIA_ITEM_SET", "Skipping source ${nextSource.sourceId}: duplicate URL of already-failed source")
                failedSourceIds += nextSource.sourceId
                nowPlayingStateStore.saveFailedSourceIds(failedSourceIds)
                expiredHosts += parseHostFromUrl(nextSource.streamUrl)
                continue
            }

            // Check URL expiry â€” re-resolve whenever provider identity is available
            if (isSourceExpired(nextSource)) {
                Log.w("VANTA_STREAM_RENEW", "Source ${nextSource.sourceId} URL appears expired â€” trying fresh resolve")
                val pId = nextSource.externalProviderId
                val eId = nextSource.externalTrackId
                if (!pId.isNullOrBlank() && !eId.isNullOrBlank()) {
                    val fresh = withContext(Dispatchers.IO) { sourceRegistry.resolveStream(pId, eId) }
                    if (generation != activePlaybackGeneration) {
                        Log.w("VANTA_PLAYBACK_GENERATION", "abort stale operation reason=stale_generation_after_resolve generation=$generation active=$activePlaybackGeneration")
                        return
                    }
                    if (fresh != null && fresh.streamUrl.isNotBlank()) {
                        Log.i("VANTA_STREAM_RENEW", "Fresh resolve succeeded for source ${nextSource.sourceId}: ${fresh.streamUrl.isNotBlank()}")
                        val refreshedSource = nextSource.copy(
                            streamUrl = fresh.streamUrl,
                            bitrate = fresh.bitrateKbps,
                            expiresAtMs = normalizedStreamExpiryMs(fresh.streamUrl, fresh.expiresAt, pId)
                        )
                        withContext(Dispatchers.IO) { trackRepository.updateSource(refreshedSource) }
                        nextSource = refreshedSource
                        resolvedQualityStream = fresh
                        stabilityCounters.currentBitrateKbps = refreshedSource.bitrate
                        stabilityCounters.streamHost = parseHostFromUrl(refreshedSource.streamUrl)
                        fresh.mimeType?.let { stabilityCounters.currentMimeType = it }
                        // Fall through to validation + playback with fresh URL
                    } else {
                        Log.w("VANTA_STREAM_RENEW", "Fresh resolve failed for expired source ${nextSource.sourceId}, marking failed")
                        failedSourceIds += nextSource.sourceId
                        nowPlayingStateStore.saveFailedSourceIds(failedSourceIds)
                        expiredHosts += parseHostFromUrl(nextSource.streamUrl)
                        continue
                    }
                } else {
                    // Expired ADDON with no provider info â€” can't renew, skip
                    Log.w("VANTA_STREAM_RENEW", "Expired source ${nextSource.sourceId} has no provider ID, skipping")
                    failedSourceIds += nextSource.sourceId
                    nowPlayingStateStore.saveFailedSourceIds(failedSourceIds)
                    expiredHosts += parseHostFromUrl(nextSource.streamUrl)
                    continue
                }
            }

            currentSource = nextSource
            currentQualityInfo = null
            queueManager.markCurrentTrack(track, player.currentPosition.coerceAtLeast(0L))
            // Update stability counters with source metadata
            stabilityCounters.currentBitrateKbps = nextSource.bitrate
            nextSource.streamUrl.let { url ->
                stabilityCounters.streamHost = runCatching { java.net.URI(url).host }.getOrDefault("unknown")
                stabilityCounters.currentMimeType = inferMimeTypeFromUrl(url)

            }

            Log.d("VANTA_PLAYBACK_TRACE", "step='validate_start' sourceId=${nextSource.sourceId} hasUrl=${nextSource.streamUrl.isNotBlank()}")
            val validation = withContext(Dispatchers.IO) { validateStream(nextSource.streamUrl) }
            if (generation != activePlaybackGeneration) {
                Log.w("VANTA_PLAYBACK_GENERATION", "abort stale operation reason=stale_generation_after_validate generation=$generation active=$activePlaybackGeneration")
                return
            }
            if (validation !is StreamValidationResult.Valid) {
                val reason = (validation as StreamValidationResult.Invalid).reason
                Log.d("VANTA_PLAYBACK_TRACE", "step='validate_result' sourceId=${nextSource.sourceId} pass=false reason='$reason'")
                Log.e("VANTA_STREAM_VALIDATE", "Validation FAILED for source ${nextSource.sourceId}: $reason")

                // Try to recover stream when provider identity is available
                var recovered = false
                val providerId = nextSource.externalProviderId
                val trackId = nextSource.externalTrackId
                if (!providerId.isNullOrBlank() && !trackId.isNullOrBlank()) {
                    stabilityCounters.reResolveCount++
                    Log.d("VANTA_PLAYBACK_TRACE", "step='renewal_possible' providerId=$providerId externalTrackId=$trackId sourceId=${nextSource.sourceId}")
                    Log.d("VANTA_STREAM_RECOVER", "Attempting to recover stream for providerId=$providerId trackId=$trackId")
                    val resolved = withContext(Dispatchers.IO) {
                        sourceRegistry.resolveStream(providerId, trackId)
                    }
                    if (generation != activePlaybackGeneration) {
                        Log.w("VANTA_PLAYBACK_GENERATION", "abort stale operation reason=stale_generation_after_recovery_resolve generation=$generation active=$activePlaybackGeneration")
                        return
                    }
                    if (resolved != null && resolved.streamUrl.isNotBlank()) {
                            Log.d("VANTA_STREAM_RECOVER", "Stream recovered successfully")
                            // Update source in database
                            val updatedSource = nextSource.copy(
                                streamUrl = resolved.streamUrl,
                                bitrate = resolved.bitrateKbps,
                                externalProviderId = providerId,
                                externalTrackId = trackId,
                                expiresAtMs = normalizedStreamExpiryMs(resolved.streamUrl, resolved.expiresAt, providerId)
                            )
                            trackRepository.updateSource(updatedSource)
                            // Retry validation with new URL
                            val revalidation = withContext(Dispatchers.IO) { validateStream(resolved.streamUrl) }
                            if (generation != activePlaybackGeneration) {
                                Log.w("VANTA_PLAYBACK_GENERATION", "abort stale operation reason=stale_generation_after_revalidation generation=$generation active=$activePlaybackGeneration")
                                return
                            }
                            if (revalidation is StreamValidationResult.Valid) {
                                Log.d("VANTA_STREAM_RECOVER", "Re-validation PASSED for recovered stream")
                                recovered = true
                                // Use recovered source for playback
                                currentSource = updatedSource
                                queueManager.markCurrentTrack(track, player.currentPosition.coerceAtLeast(0L))
                                stabilityCounters.currentBitrateKbps = resolved.bitrateKbps
                                resolved.mimeType?.let { stabilityCounters.currentMimeType = it }
                                resolved.streamUrl.let { url ->
                                    stabilityCounters.streamHost = runCatching { java.net.URI(url).host }.getOrDefault("unknown")
                                }
                                updateCurrentQualityInfo(
                                    source = updatedSource,
                                    resolved = resolved,
                                    validation = revalidation,
                                    isValidated = true,
                                    reason = "recovered_stream"
                                )
                                val mediaItemMime = mediaItemMimeFor(
                                    resolved.mimeType,
                                    revalidation.contentType,
                                    updatedSource.streamUrl
                                )
                                val mediaItem = PlaybackMediaItems.fromTrack(
                                    track = track,
                                    source = updatedSource,
                                    streamUrl = updatedSource.streamUrl,
                                    mimeType = mediaItemMime
                                )
                                if (generation != activePlaybackGeneration) {
                                    Log.w("VANTA_PLAYBACK_GENERATION", "abort stale operation reason=stale_generation_before_setMediaItem_recovery generation=$generation active=$activePlaybackGeneration")
                                    return
                                }
                                Log.d("VANTA_PLAYBACK_GENERATION", "setMediaItem generation=$generation trackId=${track.track.trackId} sourceId=${updatedSource.sourceId}")
                                val recoveryQueueTracks = orderedQueueTracks()
                                val recoveryMediaItems = recoveryQueueTracks.map { qt ->
                                    if (qt.track.trackId == track.track.trackId) mediaItem
                                    else buildMediaItemForTrack(qt)
                                }
                                val recoveryStartIndex = recoveryMediaItems.indexOfFirst { item ->
                                    item.mediaId.substringBefore(":").toLongOrNull() == track.track.trackId
                                }.coerceAtLeast(0)
                                val prepared = applyPlaybackMediaItems(recoveryMediaItems, recoveryStartIndex, 0L)
                                if (!prepared) {
                                    Log.e("VANTA_PLAYBACK", "Recovery stream preparation failed sourceId=${updatedSource.sourceId}")
                                    failedSourceIds += updatedSource.sourceId
                                    nowPlayingStateStore.saveFailedSourceIds(failedSourceIds)
                                    publishNowPlayingSnapshot(track, updatedSource, isPlaying = false, playbackError = "Playback preparation failed", reason = "Source preparation failed")
                                    continue
                                }
                                if (PlaybackPolicies.shouldAutoPlayAfterResolve(userPauseRequested, wasPlayingBefore)) {
                                    player.play()
                                    Log.d("VANTA_MEDIA_ITEM_SET", "player.play() called with recovered stream (userPauseRequested=false wasPlayingBefore=true)")
                                } else {
                                    player.pause()
                                    player.playWhenReady = false
                                    Log.d("VANTA_PLAYBACK_INTENT", "suppressing auto-play after recovery: userPauseRequested=$userPauseRequested wasPlayingBefore=$wasPlayingBefore")
                                }

                                serviceScope.launch {
                                    var attempts = 0
                                    while (!player.isPlaying && player.playbackState != Player.STATE_READY && attempts < 300) {
                                        delay(100)
                                        attempts++
                                    }
                                    if (player.isPlaying) {
                                        Log.d("VANTA_STREAM_RENEW", "initStreamExpiryScheduling via isPlaying polling (recovery, attempts=$attempts)")
                                        currentSource?.let { initStreamExpiryScheduling(it) }
                                    }
                                }

                                persistCurrentState()
                                applyVantaEqualizer()
                                return
                        } else {
                            val revalidationReason = (revalidation as StreamValidationResult.Invalid).reason
                            Log.w("VANTA_STREAM_RECOVER", "Re-validation FAILED for recovered stream: $revalidationReason")
                        }
                    } else {
                        Log.w("VANTA_STREAM_RECOVER", "Stream recovery failed: resolveStream returned null")
                    }
                } else {
                    Log.d("VANTA_PLAYBACK_TRACE", "step='playback_blocked' reason='missing_provider_identity' sourceId=${nextSource.sourceId} providerId=${providerId ?: "null"} externalTrackId=${trackId ?: "null"} â€” cannot renew expired URL")
                    Log.w("VANTA_SOURCE_TRUTH", "missing_provider_identity trackId=${track.track.trackId} sourceId=${nextSource.sourceId} canRenew=false â€” expired URLs cannot be recovered. Search and save the track again.")
                    Log.w("VANTA_STREAM_RECOVER", "Cannot recover: externalProviderId=$providerId externalTrackId=$trackId")
                }

                if (!recovered) {
                    failedSourceIds += nextSource.sourceId
                    nowPlayingStateStore.saveFailedSourceIds(failedSourceIds)
                    expiredHosts += parseHostFromUrl(nextSource.streamUrl)
                    continue
                }
            }

            val validValidation = validation as? StreamValidationResult.Valid ?: continue
            validValidation.contentType?.let {
                stabilityCounters.currentMimeType = normalizeMediaItemMime(it) ?: it
            }
            updateCurrentQualityInfo(
                source = nextSource,
                resolved = resolvedQualityStream,
                validation = validValidation,
                isValidated = true,
                reason = "validated_stream"
            )

            withContext(Dispatchers.Main.immediate) {
                try {
                if (!isActive) {
                    Log.w("VANTA_PLAYBACK", "playNextAvailableSource â€” cancelled before applyPlaybackMediaItems")
                    return@withContext
                }
                val mediaItemMime = mediaItemMimeFor(stabilityCounters.currentMimeType, nextSource.streamUrl)
                val mediaItem = PlaybackMediaItems.fromTrack(
                    track = track,
                    source = nextSource,
                    streamUrl = nextSource.streamUrl,
                    mimeType = mediaItemMime
                )

                val nowPlayingCanonicalId = CanonicalIdentityResolver.generateCanonicalId(
                    track.track.isrc, null, track.track.title, track.track.artist,
                    track.track.albumName, track.track.durationMs
                )
                Log.w("VANTA_NOWPLAYING_TRUTH",
                    "aboutToPlay mediaId=${mediaItem.mediaId} " +
                    "trackId=${track.track.trackId} " +
                    "canonicalTrackId=$nowPlayingCanonicalId " +
                    "isrc=${track.track.isrc ?: "null"} " +
                    "title='${track.track.title}' " +
                    "artist='${track.track.artist}' " +
                    "album='${track.track.albumName ?: "null"}' " +
                    "artworkUrl='${track.track.coverArtUrl?.take(60) ?: "null"}' " +
                    "sourceType=${nextSource.sourceType} " +
                    "sourceId=${nextSource.sourceId} " +
                    "streamUrlHost='${runCatching { java.net.URI(nextSource.streamUrl).host }.getOrDefault("unknown")}' " +
                    "mismatch=false")

                if (generation != activePlaybackGeneration) {
                    Log.w("VANTA_PLAYBACK_GENERATION", "abort stale operation reason=stale_generation_aboutToPlay generation=$generation active=$activePlaybackGeneration")
                    return@withContext
                }

                Log.d("VANTA_PLAYBACK_TRACE", "step='validate_result' sourceId=${nextSource.sourceId} pass=true")
                Log.d("VANTA_PLAYBACK_GENERATION", "setMediaItem generation=$generation trackId=${track.track.trackId} sourceId=${nextSource.sourceId}")
                Log.d("VANTA_PLAYBACK_TRACE", "step='media_item_set' trackId=${track.track.trackId} sourceId=${nextSource.sourceId} urlHost='${runCatching { java.net.URI(nextSource.streamUrl).host }.getOrDefault("unknown")}'")
                val queueTracks = orderedQueueTracks()
                val playbackMediaItems = queueTracks.map { qt ->
                    if (qt.track.trackId == track.track.trackId) mediaItem
                    else buildMediaItemForTrack(qt)
                }
                val playbackStartIndex = playbackMediaItems.indexOfFirst { item ->
                    item.mediaId.substringBefore(":").toLongOrNull() == track.track.trackId
                }.coerceAtLeast(0)
                val prepared = applyPlaybackMediaItems(playbackMediaItems, playbackStartIndex, 0L)
                if (!prepared) {
                    Log.e("VANTA_PLAYBACK", "Playback preparation failed trackId=${track.track.trackId} sourceId=${nextSource.sourceId}")
                    failedSourceIds += nextSource.sourceId
                    nowPlayingStateStore.saveFailedSourceIds(failedSourceIds)
                    publishNowPlayingSnapshot(track, nextSource, isPlaying = false, playbackError = "Playback preparation failed", reason = "Source preparation failed")
                    return@withContext
                }
                if (PlaybackPolicies.shouldAutoPlayAfterResolve(userPauseRequested, wasPlayingBefore)) {
                    player.play()
                    Log.d("VANTA_PLAYBACK_TRACE", "step='play_called' trackId=${track.track.trackId} userPauseRequested=$userPauseRequested playWhenReady=${player.playWhenReady}")
                    Log.d("VANTA_MEDIA_ITEM_SET", "player.play() called (userPauseRequested=false wasPlayingBefore=$wasPlayingBefore)")
                } else {
                    player.pause()
                    player.playWhenReady = false
                    Log.d("VANTA_PLAYBACK_TRACE", "step='playback_blocked' reason='auto_play_suppressed' trackId=${track.track.trackId} userPauseRequested=$userPauseRequested wasPlayingBefore=$wasPlayingBefore")
                    Log.d("VANTA_PLAYBACK_INTENT", "suppressing auto-play: userPauseRequested=$userPauseRequested wasPlayingBefore=$wasPlayingBefore")
                }

                // Schedule stream expiry renewal via polling because onIsPlayingChanged(true)
                // may not fire for the initial playback start in some scenarios.
                serviceScope.launch {
                    var attempts = 0
                    while (!player.isPlaying && player.playbackState != Player.STATE_READY && attempts < 300) {
                        delay(100)
                        attempts++
                    }
                    if (player.isPlaying) {
                        Log.d("VANTA_STREAM_RENEW", "initStreamExpiryScheduling via isPlaying polling (attempts=$attempts)")
                        currentSource?.let { initStreamExpiryScheduling(it) }
                    } else {
                        Log.w("VANTA_STREAM_RENEW", "Polling timeout: player never started (playbackState=${player.playbackState})")
                    }
                }

                persistCurrentState()
                applyVantaEqualizer()
                } catch (e: Exception) {
                    Log.e("VANTA_PLAYBACK", "Playback preparation crashed trackId=${track.track.trackId}", e)
                    failedSourceIds += nextSource.sourceId
                    nowPlayingStateStore.saveFailedSourceIds(failedSourceIds)
                    publishNowPlayingSnapshot(track, nextSource, isPlaying = false, playbackError = e.message ?: "Playback failed", reason = "Source exception")
                }
            }
            if (failedSourceIds.contains(nextSource.sourceId)) {
                continue
            }
            return
        }
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
        val source = currentSource ?: return
        val localLibraryId = track.localLibraryId ?: return
        if (source.streamUrl.isBlank()) return
        if (countedPlaybackTrackId == track.trackId) return
        countedPlaybackTrackId = track.trackId
        serviceScope.launch(Dispatchers.IO) { localLibraryRepository.incrementPlayCount(localLibraryId) }
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

    private fun persistCurrentState(playbackError: String? = null) {
        val track = activeTrack?.track
        val source = currentSource
        val positionMs = player.currentPosition.coerceAtLeast(0L)
        queueManager.markPlaybackPosition(positionMs)

        if (track == null && liveRadioStreamUrl != null) {
            val mediaMeta = player.currentMediaItem?.mediaMetadata
            val stationName = liveRadioStationName.orEmpty()
            val title = mediaMeta?.title?.toString()?.takeIf { it.isNotBlank() } ?: stationName
            val artist = mediaMeta?.artist?.toString()?.takeIf { it.isNotBlank() } ?: "Live broadcast"
            val snapshot = queueManager.snapshot()
            val liveState = NowPlayingState(
                title = title,
                artist = artist,
                isPlaying = player.isPlaying,
                positionMs = positionMs,
                bufferedMs = player.bufferedPosition.coerceAtLeast(0L),
                queuePosition = snapshot.queueIndex,
                queueSize = snapshot.queueSize,
                errorMessage = playbackError,
                isLiveRadio = true,
                liveStationName = stationName
            )
            playbackState.update {
                copy(
                    title = liveState.title,
                    artist = liveState.artist,
                    isPlaying = liveState.isPlaying,
                    queuePosition = liveState.queuePosition,
                    queueSize = liveState.queueSize,
                    errorMessage = liveState.errorMessage,
                    isLiveRadio = true,
                    liveStationName = stationName
                )
            }
            nowPlayingStateStore.save(liveState)
            return
        }

        var isFavorite = cachedIsFavorite
        val snapshot = queueManager.snapshot()
        Log.d("VANTA_QUEUE_TRUTH", "action=persist_state trackId=${track?.trackId} queueIdx=${snapshot.queueIndex} queueSize=${snapshot.queueSize} playing=${player.isPlaying} generation=$activePlaybackGeneration")

        // Media identity truth: verify activeTrack matches current player mediaId
        val mediaTrackId = currentMediaTrackId()
        val mediaSourceId = currentMediaSourceId()
        val identityMismatch = track != null && mediaTrackId != null && track.trackId != mediaTrackId
        if (identityMismatch) {
            // activeTrack is stale (auto-advanced to next track without explicit playTrack).
            // Look up the actual current track from the queue snapshot.
            val qs = queueManager.snapshot()
            val actualTrack = qs.currentTrack
            if (actualTrack != null) {
                Log.w("VANTA_NOWPLAYING_TRUTH", "mismatch=true activeTrack=${track.trackId} mediaTrackId=$mediaTrackId mediaSourceId=$mediaSourceId â€” publishing queue track ${actualTrack.track.trackId} generation=$activePlaybackGeneration")
                publishNowPlayingSnapshot(actualTrack, source, isPlaying = player.isPlaying, playbackError = playbackError, reason = "State mismatch resolution")
            } else {
                Log.w("VANTA_NOWPLAYING_TRUTH", "mismatch=true activeTrack=${track.trackId} mediaTrackId=$mediaTrackId â€” no queue track available, publishing stale activeTrack generation=$activePlaybackGeneration")
                activeTrack?.let { publishNowPlayingSnapshot(it, source, isPlaying = player.isPlaying, playbackError = playbackError, reason = "State mismatch resolution fallback") }
            }
            return
        }

        val nowPlayingCanonicalId = if (track != null) {
            CanonicalIdentityResolver.generateCanonicalId(
                track.isrc, null, track.title, track.artist,
                track.albumName, track.durationMs
            )
        } else null

        val currentQuality = if (playbackError == null) {
            currentQualityInfo ?: source?.let {
                val sourceValidated = player.isPlaying || player.playbackState == Player.STATE_READY
                VantaQualityInfo.fromTrackSource(
                    source = it,
                    status = when {
                        it.sourceType == SourceType.LOCAL -> SearchItemStatus.LOCAL_PLAYABLE
                        sourceValidated -> SearchItemStatus.VALIDATED_PLAYABLE
                        else -> SearchItemStatus.SOURCE_FOUND
                    },
                    isValidated = sourceValidated || it.sourceType == SourceType.LOCAL
                )?.takeIf { quality -> quality.bestQualityLabel() != null }
            }
        } else {
            null
        }
        val playbackDisplay = if (track != null) {
            com.audiophile.musicplayer.data.display.DisplayMetadataCleaner.computePlaybackDisplay(
                rawTitle = track.title.orEmpty(),
                rawArtist = track.artist.orEmpty(),
                rawAlbum = track.albumName,
                streamHint = source?.streamUrl
            )
        } else null
        val newState = NowPlayingState(
            trackId = track?.trackId?.toString(),
            canonicalTrackId = nowPlayingCanonicalId,
            isrc = track?.isrc,
            title = playbackDisplay?.title?.ifBlank { track?.title } ?: track?.title,
            artist = playbackDisplay?.artist?.ifBlank { track?.artist } ?: track?.artist,
            versionLabel = playbackDisplay?.versionLabel,
            album = track?.albumName,
            artworkUrl = track?.coverArtUrl,
            positionMs = positionMs,
            durationMs = player.duration.takeIf { it > 0 } ?: 0L,
            bufferedMs = player.bufferedPosition.takeIf { it > 0 } ?: 0L,
            isPlaying = player.isPlaying,
            queuePosition = snapshot.queueIndex,
            queueSize = snapshot.queueSize,
            isFavorite = isFavorite,
            errorMessage = playbackError,
            qualityInfo = currentQuality
        )
        // Merge into shared state without overwriting position/bufferedMs/durationMs (owned by PlayerController polling)
        playbackState.update {
            copy(
                trackId = newState.trackId,
                canonicalTrackId = newState.canonicalTrackId,
                isrc = newState.isrc,
                title = newState.title,
                artist = newState.artist,
                versionLabel = newState.versionLabel,
                album = newState.album,
                artworkUrl = newState.artworkUrl,
                durationMs = if (durationMs > 0) durationMs else newState.durationMs,
                isPlaying = newState.isPlaying,
                queuePosition = newState.queuePosition,
                queueSize = newState.queueSize,
                isFavorite = newState.isFavorite,
                errorMessage = newState.errorMessage,
                qualityInfo = newState.qualityInfo,
                isLiveRadio = false,
                liveStationName = null
            )
        }
        nowPlayingStateStore.save(newState)
    }

    private fun publishNowPlayingSnapshot(
        track: UnifiedTrackWithSources,
        source: TrackSource? = currentSource,
        isPlaying: Boolean = player.isPlaying,
        playbackError: String? = null,
        reason: String
    ) {
        val unified = track.track
        val playbackDisplay = DisplayMetadataCleaner.computePlaybackDisplay(
            rawTitle = unified.title.orEmpty(),
            rawArtist = unified.artist.orEmpty(),
            rawAlbum = unified.albumName,
            streamHint = source?.streamUrl
        )
        val snapshot = queueManager.snapshot()
        
        Log.d("VANTA_NOWPLAYING_TRUTH", "publishNowPlayingSnapshot reason='$reason' trackId=${unified.trackId} title='${playbackDisplay.title}'")

        val metadataState = NowPlayingState(
            trackId = unified.trackId.toString(),
            title = playbackDisplay.title.ifBlank { unified.title }.ifBlank { "Unknown Track" },
            artist = playbackDisplay.artist.ifBlank { unified.artist }.ifBlank { "Unknown Artist" },
            versionLabel = playbackDisplay.versionLabel,
            album = unified.albumName,
            isrc = unified.isrc,
            artworkUrl = unified.coverArtUrl,
            isPlaying = isPlaying,
            positionMs = player.currentPosition.coerceAtLeast(0L),
            durationMs = player.duration.coerceAtLeast(0L).takeIf { it > 0 } ?: unified.durationMs ?: 0L,
            bufferedMs = player.bufferedPosition.coerceAtLeast(0L),
            queuePosition = snapshot.queueIndex,
            queueSize = snapshot.queueSize,
            isFavorite = cachedIsFavorite,
            errorMessage = playbackError
        )
        playbackState.update {
            copy(
                trackId = metadataState.trackId,
                title = metadataState.title,
                artist = metadataState.artist,
                versionLabel = metadataState.versionLabel,
                album = metadataState.album,
                isrc = metadataState.isrc,
                artworkUrl = metadataState.artworkUrl,
                isPlaying = metadataState.isPlaying,
                positionMs = metadataState.positionMs,
                durationMs = metadataState.durationMs,
                bufferedMs = metadataState.bufferedMs,
                queuePosition = metadataState.queuePosition,
                queueSize = metadataState.queueSize,
                isFavorite = metadataState.isFavorite,
                errorMessage = metadataState.errorMessage
            )
        }
        nowPlayingStateStore.save(metadataState)
        if (!unified.coverArtUrl.isNullOrBlank() && unified.coverArtUrl.startsWith("http")) {
            serviceScope.launch {
                trackRepository.updateCoverArtIfMissing(unified.trackId, unified.coverArtUrl)
            }
        }
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

    private fun emitPlaybackSummary(reason: String) {
        Log.i("VANTA_PLAYBACK_SUMMARY", "reason=$reason ${stabilityCounters.toSummaryString()}")
    }

    private fun startSummaryTimer() {
        summaryJob?.cancel()
        stallMonitorJob?.cancel()
        summaryJob = serviceScope.launch {
            delay(60_000L)
            val reason = when {
                userPauseRequested -> "60s session elapsed (user paused)"
                !player.isPlaying -> "60s session elapsed (not playing)"
                else -> "60s continuous play"
            }
            emitPlaybackSummary(reason)
        }
        // Stall monitor: detects position freezes that ExoPlayer doesn't report as STATE_BUFFERING
        // This catches micro-stutters where audio drops for <2500ms (bufferForPlaybackMs)
        stallMonitorJob = serviceScope.launch {
            stabilityCounters.lastCheckedPositionMs = player.currentPosition.coerceAtLeast(0L)
            stabilityCounters.lastCheckTimeMs = System.currentTimeMillis()
            while (true) {
                delay(250L)
                if (!player.isPlaying || player.playbackState != Player.STATE_READY) {
                    stabilityCounters.consecutiveStalledChecks = 0
                    stabilityCounters.lastCheckedPositionMs = player.currentPosition.coerceAtLeast(0L)
                    stabilityCounters.lastCheckTimeMs = System.currentTimeMillis()
                    continue
                }
                val currentPos = player.currentPosition.coerceAtLeast(0L)
                val now = System.currentTimeMillis()
                // If position hasn't advanced by at least 20ms in 250ms, count as stalled check
                if (currentPos <= stabilityCounters.lastCheckedPositionMs + 20) {
                    stabilityCounters.consecutiveStalledChecks++
                    if (stabilityCounters.consecutiveStalledChecks >= 3) {
                        // 750ms with no audio progress = definite stall
                        stabilityCounters.stallCount++
                        Log.w("VANTA_AUDIO_STALL",
                            "STALL #${stabilityCounters.stallCount} " +
                            "pos=$currentPos lastCheckedPos=${stabilityCounters.lastCheckedPositionMs} " +
                            "duration=${player.duration}")
                        stabilityCounters.consecutiveStalledChecks = 0
                        // Trigger quality fallback on 2nd+ stall
                        if (stabilityCounters.stallCount >= 2) {
                            checkQualityFallback()
                        }
                    }
                } else {
                    stabilityCounters.consecutiveStalledChecks = 0
                }
                stabilityCounters.lastCheckedPositionMs = currentPos
                stabilityCounters.lastCheckTimeMs = now
            }
        }
    }

    private fun cancelSummaryTimer() {
        summaryJob?.cancel()
        summaryJob = null
        stallMonitorJob?.cancel()
        stallMonitorJob = null
    }

    private fun cancelStreamRenewal() {
        streamRenewalJob?.cancel()
        streamRenewalJob = null
    }

    private fun parseHostFromUrl(url: String): String =
        runCatching { java.net.URI(url).host }.getOrNull() ?: "unknown"

    private fun isSourceExpired(source: TrackSource): Boolean {
        val now = System.currentTimeMillis()
        val storedExpiryMs = normalizeEpochMs(source.expiresAtMs)
        if (storedExpiryMs != null) return storedExpiryMs < now

        val urlExpiryMs = normalizeEpochMs(
            com.audiophile.musicplayer.data.source.SourceRegistry.extractExpiryFromUrl(source.streamUrl)
        )
        if (urlExpiryMs != null) return urlExpiryMs < now

        if (source.sourceType == com.audiophile.musicplayer.data.local.entities.SourceType.ADDON &&
            com.audiophile.musicplayer.data.source.SourceRegistry.inferTtlFromHost(source.streamUrl) != null
        ) {
            Log.w("VANTA_STREAM_RENEW", "Persisted temporary CDN source has no expiry; forcing fresh resolve sourceId=${source.sourceId} host=${parseHostFromUrl(source.streamUrl)}")
            return true
        }

        return false
    }

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

    private fun scheduleStreamRenewal(expiresAtMs: Long) {
        streamRenewalJob?.cancel()
        val now = System.currentTimeMillis()
        val ttl = expiresAtMs - now
        if (ttl <= 5_000L) return
        val delayMs = maxOf(1_000L, minOf(ttl - 30_000L, (ttl * 7) / 10))
        Log.d("VANTA_STREAM_RENEW", "Scheduling renewal in ${delayMs}ms (TTL=${ttl}ms expiresAt=$expiresAtMs)")
        streamRenewalJob = serviceScope.launch {
            delay(delayMs)
            renewCurrentStream()
        }
    }

    private suspend fun renewCurrentStream(): Boolean {
        val track = activeTrack?.track ?: return false
        val resolution = activeResolution ?: return false
        val source = currentSource ?: return false
        if (source.sourceType != com.audiophile.musicplayer.data.local.entities.SourceType.ADDON) return false
        val generation = activePlaybackGeneration

        // Media identity mismatch: user switched tracks while renewal was pending
        val mediaTrackId = currentMediaTrackId()
        if (mediaTrackId != null && mediaTrackId != track.trackId) {
            Log.w("VANTA_STREAM_RENEW", "abort reason=media_identity_mismatch renewalTrack=${track.trackId} currentMedia=$mediaTrackId")
            return false
        }

        // Derive provider/track identity from the ACTIVE resolution, not from currentSource,
        // to avoid resolving the wrong track if currentSource was set by a stale retryNextSource()
        // coroutine that raced with playTrack().
        val renewalSource = resolution.orderedCandidates.firstOrNull {
            it.sourceId == source.sourceId && it.sourceType == com.audiophile.musicplayer.data.local.entities.SourceType.ADDON
        } ?: resolution.orderedCandidates.firstOrNull {
            it.sourceType == com.audiophile.musicplayer.data.local.entities.SourceType.ADDON &&
            it.externalProviderId == source.externalProviderId && !it.externalProviderId.isNullOrBlank()
        } ?: source

        val providerId = renewalSource.externalProviderId ?: return false
        val externalTrackId = renewalSource.externalTrackId ?: return false

        Log.d("VANTA_STREAM_RENEW", "Renewing stream for providerId=$providerId trackId=$externalTrackId (renewalSource.sourceId=${renewalSource.sourceId} source.sourceId=${source.sourceId}) generation=$generation")
        val resolved = withContext(Dispatchers.IO) {
            sourceRegistry.resolveStream(providerId, externalTrackId)
        }
        if (generation != activePlaybackGeneration) {
            Log.w("VANTA_PLAYBACK_GENERATION", "abort stale operation reason=stale_generation_after_renewal_resolve renewalGeneration=$generation active=$activePlaybackGeneration")
            return false
        }
        if (resolved == null || resolved.streamUrl.isBlank()) {
            Log.w("VANTA_STREAM_RENEW", "Renewal failed: resolveStream returned null/blank")
            return false
        }

        val updatedSource = renewalSource.copy(
            streamUrl = resolved.streamUrl,
            bitrate = resolved.bitrateKbps,
            expiresAtMs = normalizedStreamExpiryMs(resolved.streamUrl, resolved.expiresAt, providerId)
        )
        currentSource = updatedSource

        withContext(Dispatchers.IO) { trackRepository.updateSource(updatedSource) }

        stabilityCounters.currentBitrateKbps = updatedSource.bitrate
        stabilityCounters.streamHost = parseHostFromUrl(updatedSource.streamUrl)
        resolved.mimeType?.let { stabilityCounters.currentMimeType = it }
        updateCurrentQualityInfo(
            source = updatedSource,
            resolved = resolved,
            validation = null,
            isValidated = true,
            reason = "renewed_stream"
        )

        val mediaItemMime = mediaItemMimeFor(stabilityCounters.currentMimeType, resolved.streamUrl)
        val newMediaItem = MediaItem.Builder()
            .setUri(resolved.streamUrl)
            .setMediaId("${track.trackId}:${updatedSource.sourceId}")
            .apply { mediaItemMime?.let { setMimeType(it) } }
            .setMediaMetadata(
                AutoMainStageLyrics.buildPlaybackMetadata(
                    title = track.title,
                    artist = track.artist,
                    album = track.albumName,
                    artworkUrl = track.coverArtUrl,
                )
            )
            .build()

        val shouldResumeAfterRenewal = PlaybackPolicies.shouldResumeAfterRenewal(userPauseRequested, player.playWhenReady)
        val currentPos = player.currentPosition.coerceAtLeast(0L)
        player.replaceMediaItem(player.currentMediaItemIndex, newMediaItem)
        if (currentPos > 0L) player.seekTo(currentPos)
        if (shouldResumeAfterRenewal) {
            player.play()
            Log.d("VANTA_PLAYBACK_INTENT", "renewal resume: player.play() called (userPauseRequested=false wasPlaying=true)")
        } else {
            player.pause()
            player.playWhenReady = false
            Log.d("VANTA_PLAYBACK_INTENT", "suppressing renewal auto-play: userPauseRequested=$userPauseRequested wasPlaying=${player.playWhenReady}")
        }

        Log.i("VANTA_STREAM_RENEW", "Stream renewed: host=${parseHostFromUrl(resolved.streamUrl)} expiresAt=${resolved.expiresAt} pos=$currentPos")

        // Reset stability counters so old re-buffer/stall stats don't trigger immediate fallback
        // on the hot-swap re-buffer that follows replaceMediaItem()
        stabilityCounters.reBufferCount = 0
        stabilityCounters.reBufferTotalMs = 0L
        stabilityCounters.stallCount = 0
        stabilityCounters.consecutiveStalledChecks = 0
        stabilityCounters.bufferingCount = 0
        stabilityCounters.totalBufferingMs = 0L
        stabilityCounters.readyCount = 0
        stabilityCounters.bufferingStartMs = 0L
        stabilityCounters.trackStartTimeMs = 0L
        hasReResolvedOnCurrentReBuffer = false

        persistCurrentState()

        val minExpiry = com.audiophile.musicplayer.data.source.SourceRegistry.resolveMinExpiryMs(
            resolved.streamUrl,
            resolved.expiresAt,
            providerId
        )
        if (minExpiry != null) {
            currentStreamExpiresAtMs = minExpiry
            scheduleStreamRenewal(minExpiry)
        }
        return true
    }

    private fun initStreamExpiryScheduling(source: TrackSource) {
        // Media identity check: don't schedule renewal if activeTrack doesn't match current media
        val activeTrackId = activeTrack?.track?.trackId
        val mediaTrackId = currentMediaTrackId()
        if (activeTrackId != null && mediaTrackId != null && activeTrackId != mediaTrackId) {
            Log.w("VANTA_STREAM_RENEW", "skip reason=media_identity_mismatch activeTrackId=$activeTrackId mediaTrackId=$mediaTrackId sourceId=${source.sourceId}")
            return
        }
        val minExpiry = com.audiophile.musicplayer.data.source.SourceRegistry.resolveMinExpiryMs(
            source.streamUrl,
            source.expiresAtMs,
            source.externalProviderId
        )
        Log.d("VANTA_STREAM_RENEW", "initStreamExpiryScheduling: sourceId=${source.sourceId} source.expiresAtMs=${source.expiresAtMs} minExpiry=$minExpiry urlHost=${runCatching { java.net.URI(source.streamUrl).host }.getOrNull()}")
        if (minExpiry != null) {
            currentStreamExpiresAtMs = minExpiry
            scheduleStreamRenewal(minExpiry)
        } else {
            currentStreamExpiresAtMs = 0L
            Log.w("VANTA_STREAM_RENEW", "initStreamExpiryScheduling: no expiry could be resolved for source ${source.sourceId}")
        }
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
            mediaItemMimeFor(stabilityCounters.currentMimeType, it)
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

    private fun prefetchNextQueueTrack() {
        prefetchNextTrackJob?.cancel()
        prefetchNextTrackJob = serviceScope.launch(Dispatchers.IO) {
            val next = queueManager.getNextTrack() ?: return@launch
            runCatching {
                trackRepository.resolvePlaybackSourcesForTrack(next.track.trackId)
            }.onFailure { e ->
                Log.d("VANTA_PREFETCH", "prefetch failed trackId=${next.track.trackId}: ${e.message}")
            }
        }
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

    private fun markCurrentHostAsExpired() {
        currentSource?.streamUrl?.let { url ->
            expiredHosts += parseHostFromUrl(url)
        }
    }

    private fun checkQualityFallback() {
        val elapsed = if (stabilityCounters.trackStartTimeMs > 0L) System.currentTimeMillis() - stabilityCounters.trackStartTimeMs else 0L
        // Triggers (only after audio has actually started playing â€” trackStartTimeMs set):
        // 1. Any re-buffer event (buffering AFTER audio started) = stream can't sustain
        // 2. 3+ position stalls within 60s (micro-stutters invisible to ExoPlayer)
        // 3. 3+ total buffering events within 60s (initial buffer + re-buffers)
        // 4. Total re-buffer time > 5s
        val hasPlaybackStarted = stabilityCounters.trackStartTimeMs > 0L
        val shouldFallback = hasPlaybackStarted && (
            (stabilityCounters.reBufferCount >= 1 && elapsed < 30_000L) ||
            (stabilityCounters.reBufferTotalMs > 5_000L) ||
            (stabilityCounters.stallCount >= 3 && elapsed < 60_000L) ||
            (stabilityCounters.bufferingCount >= 3 && elapsed < 60_000L)
        )
        if (shouldFallback) {
            val source = currentSource
            if (source != null) {
                // Before cycling to fallback sources, try one re-resolve attempt
                // to handle stream URL expiry gracefully
                if (!hasReResolvedOnCurrentReBuffer) {
                    hasReResolvedOnCurrentReBuffer = true
                    Log.w("VANTA_STREAM_RENEW", "Re-buffer detected â€” attempting stream renewal before fallback")
                    serviceScope.launch {
                        val renewed = renewCurrentStream()
                        if (!renewed) {
                            Log.w("VANTA_STREAM_RENEW", "Renewal failed â€” falling back to next source")
                            proceedWithQualityFallback(source, elapsed)
                        }
                        // If renewed, the hot-swap already happened; no further action needed
                    }
                    return
                }

                proceedWithQualityFallback(source, elapsed)
                hasReResolvedOnCurrentReBuffer = false
            }
        }
    }

    private fun proceedWithQualityFallback(source: TrackSource, elapsed: Long) {
        Log.w("VANTA_QUALITY_FALLBACK",
            "triggered: reBufferCount=${stabilityCounters.reBufferCount} reBufferTotalMs=${stabilityCounters.reBufferTotalMs}ms " +
            "bufferingCount=${stabilityCounters.bufferingCount} totalBufferingMs=${stabilityCounters.totalBufferingMs}ms " +
            "stallCount=${stabilityCounters.stallCount} elapsed=${elapsed}ms " +
            "sourceId=${source.sourceId} bitrate=${source.bitrate}kbps " +
            "host=${stabilityCounters.streamHost} mime=${stabilityCounters.currentMimeType ?: "unknown"}")
        failedSourceIds += source.sourceId
        nowPlayingStateStore.saveFailedSourceIds(failedSourceIds)
        markCurrentHostAsExpired()
        emitPlaybackSummary("quality fallback")
        val wasPlaying = !userPauseRequested
        val generation = activePlaybackGeneration
        serviceScope.launch { playNextAvailableSource(isErrorRecovery = true, wasPlayingBefore = wasPlaying, generation = generation) }
    }

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
