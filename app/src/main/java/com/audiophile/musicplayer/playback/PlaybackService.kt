@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package com.audiophile.musicplayer.playback

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.Bundle
import java.io.File

import android.util.Log
import kotlin.math.pow
import com.audiophile.musicplayer.debug.VantaDiagnosticLog
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Metadata
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.DataSource
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
import androidx.media3.exoplayer.source.FilteringMediaSource
import androidx.media3.exoplayer.source.MergingMediaSource
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionError
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.audiophile.musicplayer.auto.AndroidAutoBrowseController
import com.audiophile.musicplayer.audio.visualizer.VantaAudioAnalyzerHolder
import com.audiophile.musicplayer.auto.AndroidAutoController
import com.audiophile.musicplayer.auto.AutoLruCache
import com.audiophile.musicplayer.auto.AutoMainStageLyrics
import com.audiophile.musicplayer.auto.AutoMainStageLyricsController
import com.audiophile.musicplayer.playback.dsp.forBuiltInSpeaker
import com.audiophile.musicplayer.playback.dsp.forUsbPassthrough
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
    private lateinit var mediaSourceFactory: DefaultMediaSourceFactory
    private val musicVideoCompanionResolver by lazy {
        com.audiophile.musicplayer.data.source.MusicVideoCompanionResolver(sourceRegistry)
    }

    private val listeningHistoryRecorder by lazy {
        val repository = appContainer.listeningHistoryRepository
        ListeningHistoryRecorder(
            sink = ListeningHistorySink { record, startedAt, msPlayed, skipped, reasonEnd ->
                repository.recordPlay(
                    startedAt = startedAt,
                    title = record.title,
                    artist = record.artist,
                    album = record.album,
                    platform = record.platform,
                    providerId = record.providerId,
                    sourceTrackId = record.sourceTrackId,
                    msPlayed = msPlayed,
                    durationMs = record.durationMs,
                    skipped = skipped,
                    reasonEnd = reasonEnd
                )
            },
            scope = serviceScope
        )
    }

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
    private var djVoicePlayer: MediaPlayer? = null
    private var djVoiceDuckJob: Job? = null
    private val streamHeadersByUrl = java.util.concurrent.ConcurrentHashMap<String, Map<String, String>>()
    @Volatile private var activeStreamHeaders: Map<String, String> = emptyMap()

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
        vantaEqualizer.spectrumListener = { mags ->
            VantaAudioAnalyzerHolder.analyzer?.updateFromDspSpectrum(mags)
        }
        autoMixPreferences = AutoMixPreferences(this)
        SpatialHeadTracking.load(this)
        OutputSwitchController.startWatching(this) { applyVantaEqualizer() }


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
            .addInterceptor(GatewayApiKeyInterceptor)
            .addInterceptor { chain ->
                val orig = chain.request()
                val urlString = orig.url.toString()
                val host = orig.url.host.lowercase()
                val builder = orig.newBuilder()

                val customHeaders = streamHeadersByUrl[urlString] ?: activeStreamHeaders
                customHeaders.forEach { (key, value) ->
                    builder.header(key, value)
                }

                val cdnHeaders = CdnPlaybackHeaders.forUrl(urlString)
                cdnHeaders.forEach { (key, value) ->
                    builder.header(key, value)
                }

                if (host.contains("googlevideo.com")) {
                    builder.header("User-Agent", customHeaders["User-Agent"] ?: "com.google.ios.youtube/21.02.3 (iPhone16,2; U; CPU iOS 18_3_2 like Mac OS X;)")
                    builder.removeHeader("Referer")
                } else if (orig.header("User-Agent") == null &&
                    !customHeaders.containsKey("User-Agent") &&
                    !cdnHeaders.containsKey("User-Agent")
                ) {
                    builder.header("User-Agent", CdnPlaybackHeaders.CHROME_UA)
                }

                val finalReq = builder.build()
                Log.w("VANTA_EXO_HTTP", "REQ url=${finalReq.url} range=${finalReq.header("Range")} ua=${finalReq.header("User-Agent")} referer=${finalReq.header("Referer")}")
                val resp = chain.proceed(finalReq)
                Log.w("VANTA_EXO_HTTP", "RESP code=${resp.code} msg=${resp.message} range=${resp.header("Content-Range")} len=${resp.header("Content-Length")}")
                resp
            }
            .build()

        val baseDataSourceFactory = OkHttpDataSource.Factory(okHttpClient)
        val httpDataSourceFactory = DataSource.Factory {
            GoogleVideoChunkingDataSource(
                upstream = baseDataSourceFactory.createDataSource(),
                okHttpClient = okHttpClient,
                headersProvider = { url -> streamHeadersByUrl[url] ?: activeStreamHeaders }
            )
        }
        val dataSourceFactory = androidx.media3.datasource.DefaultDataSource.Factory(this, httpDataSourceFactory)
        mediaSourceFactory = DefaultMediaSourceFactory(dataSourceFactory)

        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(15_000, 60_000, 2_500, 5_000)
            .setBackBuffer(30_000, true)
            .build()

        val renderersFactory = VantaSpatialRenderersFactory(this, vantaEqualizer)
            .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON)
        Log.i(
            "VANTA_FFMPEG",
            "media3ExtensionRendererMode=ON ffmpegAudioRendererAvailable=${isMedia3FfmpegAudioRendererAvailable()}"
        )

        exoPlayer = ExoPlayer.Builder(this, renderersFactory)
            .setAudioAttributes(audioAttributes, true)
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .setMediaSourceFactory(mediaSourceFactory)
            .setLoadControl(loadControl)
            .build()
        audioSessionId = exoPlayer.audioSessionId
        Log.d("VANTA_AURA", "audioSessionId=$audioSessionId")
        exoPlayer.addListener(object : Player.Listener {
            override fun onAudioSessionIdChanged(audioSessionId: Int) {
                PlaybackService.audioSessionId = audioSessionId
                Log.d("VANTA_AURA", "audioSessionIdChanged=$audioSessionId")
            }
        })

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
        playbackStateManager = PlaybackStateManager(playbackState, nowPlayingStateStore, serviceScope) { error ->
            recoverAudioDelivery(error.message ?: "Audio delivery failed.")
        }
        // Qobuz-style sink truth: what the pipeline actually hands to AudioTrack,
        // captured from AudioSink.configure(). Feed the real rate/depth/channels
        // into applyMeasuredQuality so "Adapted for playback" appears whenever the
        // DAC/route truncates or resamples (e.g. 24-bit → 16-bit).
        PlaybackOutputTruth.listener = { truth ->
            val truthBitDepth = truth.bitDepth
            val truthRate = truth.sampleRateHz
            val truthChannels = truth.channels
            val truthPcm = truth.pcmEncodingName
            Log.i(
                "VANTA_TRACK_TRUTH",
                "sink_output rate=${truthRate ?: "?"} depth=${truthBitDepth ?: "?"} " +
                    "channels=${truthChannels ?: "?"} pcm=${truthPcm ?: "?"}"
            )
            playbackStateManager.applyMeasuredQuality(
                bitrateKbps = null,
                mime = null,
                sampleRateHz = truthRate,
                bitDepth = truthBitDepth,
                channels = truthChannels,
                pcmEncoding = truthPcm
            )
        }
        streamResolver = StreamResolver(sourceRegistry, trackRepository)

        exoPlayer.addListener(playbackStateManager)
        exoPlayer.addListener(object : Player.Listener {
            override fun onTracksChanged(tracks: androidx.media3.common.Tracks) {
                val format = tracks.groups.firstOrNull { it.type == C.TRACK_TYPE_AUDIO && it.isSelected }
                    ?.let { group -> (0 until group.length).firstOrNull { group.isTrackSelected(it) }
                        ?.let { group.getTrackFormat(it) } } ?: return
                val measured = Media3AudioFormatReader.read(format)
                val explicitSpatial = measured.dolbyAtmos || measured.eclipsaAudio || measured.codec == "mpeg-h"
                val multichannel = (measured.channels ?: 0) > 2
                val hardwareAtmos = measured.dolbyAtmos && SpatialDecoderCapabilities.supportsAtmosOutput()
                val softwareHeadphones = measured.eclipsaAudio || measured.codec == "mpeg-h" ||
                    (measured.dolbyAtmos && !hardwareAtmos)
                // Track spatial state for format-aware loudness normalization / Sound Check
                vantaEqualizer.isCurrentTrackSpatial = explicitSpatial
                // Only bypass the stereo DSP chain when audio is sent as direct multichannel PCM (5.1/7.1)
                // or passthrough bitstream to an external AVR/soundbar where stereo DSP would corrupt channel routing.
                // When spatial audio is decoded to 2-channel stereo for headphones, allow the DSP chain
                // (EQ, Bass Cannon, Treble Boost, Loudness Normalization) to enhance the stereo PCM!
                vantaEqualizer.spatialTrackBypass = multichannel && !softwareHeadphones
                exoPlayer.setAudioAttributes(
                    AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).setSpatializationBehavior(
                        when {
                            hardwareAtmos -> C.SPATIALIZATION_BEHAVIOR_AUTO
                            // User opted out of head-tracking / platform spatial processing.
                            !SpatialHeadTracking.spatializationEnabled() -> C.SPATIALIZATION_BEHAVIOR_NEVER
                            softwareHeadphones -> C.SPATIALIZATION_BEHAVIOR_NEVER
                            else -> C.SPATIALIZATION_BEHAVIOR_AUTO
                        }
                    ).build(), true
                )
                playbackStateManager.applyMeasuredQuality(
                    measured.bitrateKbps, measured.mimeType, measured.sampleRateHz,
                    decoderClaimsAtmos = measured.dolbyAtmos, bitDepth = measured.bitDepth,
                    channels = measured.channels, codec = measured.codec, container = measured.container
                )
                Log.i("VANTA_SPATIAL", "input=${measured.mimeType} channels=${measured.channels ?: "?"} eqBypass=${vantaEqualizer.spatialTrackBypass} output=" + when {
                    measured.eclipsaAudio -> "iamf_binaural"
                    hardwareAtmos -> "atmos_compatible_hw"
                    measured.codec == "mpeg-h" -> "mpeg_h_headphones"
                    measured.dolbyAtmos && softwareHeadphones -> "joc_headphones"
                    else -> "standard"
                })
            }
        })

        exoPlayer.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED) {
                    val duration = if (exoPlayer.duration > 0L) exoPlayer.duration else 0L
                    listeningHistoryRecorder.onTrackEndedNaturally(duration)
                    resumeIfEndedAndQueueHasNext("natural_end")
                }
            }

            override fun onAudioSessionIdChanged(audioSessionId: Int) {
                if (audioSessionId != C.AUDIO_SESSION_ID_UNSET) {
                    try {
                        val openSession = Intent("android.media.action.OPEN_AUDIO_EFFECT_CONTROL_SESSION").apply {
                            putExtra("android.media.extra.AUDIO_SESSION_ID", audioSessionId)
                            putExtra("android.media.extra.PACKAGE_NAME", packageName)
                            putExtra("android.media.extra.CONTENT_TYPE", 0) // CONTENT_TYPE_MUSIC
                        }
                        sendBroadcast(openSession)
                    } catch (e: Exception) {
                        Log.w("VANTA_AUDIO_EFFECT", "Failed broadcasting audio session open: ${e.message}")
                    }
                }
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                try {
                    val current = activeTrack?.track
                    val pIntent = Intent("com.maxmpz.audioplayer.TRACK_CHANGED").apply {
                        putExtra("track", Bundle().apply {
                            putString("title", current?.title ?: mediaItem?.mediaMetadata?.title?.toString())
                            putString("artist", current?.artist ?: mediaItem?.mediaMetadata?.artist?.toString())
                            putString("album", current?.albumName ?: mediaItem?.mediaMetadata?.albumTitle?.toString())
                        })
                    }
                    sendBroadcast(pIntent)
                } catch (_: Exception) {}
            }

            override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
                Log.d("VANTA_SHUFFLE", "exoplayer_shuffle_mode_changed enabled=$shuffleModeEnabled")
                playbackState.update { copy(shuffleEnabled = shuffleModeEnabled) }
                serviceScope.launch { queueManager.updateShuffleEnabled(shuffleModeEnabled) }
            }
        })

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
            .setBitmapLoader(createAutoBitmapLoader())
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
            canToggleFavoriteProvider = { activeTrack?.track?.localLibraryId != null },
            hasActiveTrackProvider = { activeTrack != null },
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
            currentTrackProvider = { activeTrack },
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
        // Virtual queue timeline removed; ExoPlayer drives MediaSession timeline now.

        applyVantaEqualizer()
        serviceScope.launch {
            appContainer.registerConfiguredProviders()
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession? {
        return mediaSession
    }

    private fun createAutoBitmapLoader(): androidx.media3.common.util.BitmapLoader {
        return object : androidx.media3.common.util.BitmapLoader {
            override fun supportsMimeType(mimeType: String) = true

            override fun decodeBitmap(data: ByteArray): ListenableFuture<android.graphics.Bitmap> {
                val future = com.google.common.util.concurrent.SettableFuture.create<android.graphics.Bitmap>()
                serviceScope.launch(Dispatchers.IO) {
                    try {
                        val bitmap = android.graphics.BitmapFactory.decodeByteArray(data, 0, data.size)
                        if (bitmap != null) future.set(bitmap)
                        else future.setException(IllegalArgumentException("Failed to decode bitmap"))
                    } catch (e: Exception) {
                        future.setException(e)
                    }
                }
                return future
            }

            override fun loadBitmap(uri: android.net.Uri): ListenableFuture<android.graphics.Bitmap> {
                val future = com.google.common.util.concurrent.SettableFuture.create<android.graphics.Bitmap>()
                serviceScope.launch(Dispatchers.IO) {
                    try {
                        val imageRequest = coil.request.ImageRequest.Builder(this@PlaybackService)
                            .data(uri)
                            .size(320, 320)
                            .allowHardware(false)
                            .build()
                        val imageResult = coil.Coil.imageLoader(this@PlaybackService).execute(imageRequest)
                        val successResult = imageResult as? coil.request.SuccessResult
                        val drawable = successResult?.drawable
                        if (drawable != null) {
                            val width = drawable.intrinsicWidth.coerceAtLeast(1)
                            val height = drawable.intrinsicHeight.coerceAtLeast(1)
                            val bmp = android.graphics.Bitmap.createBitmap(
                                width, height, android.graphics.Bitmap.Config.ARGB_8888
                            )
                            android.graphics.Canvas(bmp).apply {
                                drawable.setBounds(0, 0, width, height)
                                drawable.draw(this)
                            }
                            future.set(bmp)
                        } else {
                            future.setException(IllegalArgumentException("Failed to load $uri"))
                        }
                    } catch (e: Exception) {
                        future.setException(e)
                    }
                }
                return future
            }
        }
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

    private fun refreshAutoCustomLayout() {
        mediaSession?.setCustomLayout(autoController.buildCommandButtons())
    }

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
            if (selectedItem.mediaId == AUTO_DJ_ID) {
                Log.d("VANTA_ANDROID_AUTO", "AI DJ requested from browse tree")
                val djTracks = allAutoPlayableTracks().shuffled()
                if (djTracks.isNotEmpty()) {
                    queueController.setPlayQueue(djTracks, 0, QueueMode.NORMAL_QUEUE)
                    queueManager.markCurrentTrack(djTracks.first())
                    playTrack(djTracks.first().track.trackId)
                }
                return@launch
            }
            if (selectedItem.mediaId == AUTO_LYRICS_ID) {
                val track = activeTrack
                if (track != null) {
                    Log.d("VANTA_ANDROID_AUTO", "Lyrics requested from browse tree")
                    autoMainStageLyricsController.start(track)
                } else {
                    Log.w("VANTA_ANDROID_AUTO", "Lyrics requested without an active track")
                }
                return@launch
            }
            val queue: List<UnifiedTrackWithSources> = when {
                selectedItem.mediaId == AUTO_LIKED_ID -> {
                    localLibraryRepository.allSongsSnapshot().filter { it.isFavorite }
                        .mapNotNull { it.toPlayableQueueItem() }
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
                    it.track.title.contains(seedTitle.take(4), ignoreCase = true)
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
            ACTION_REFRESH_HEAD_TRACKING -> {
                Log.d("VANTA_SERVICE_ACTION_RECEIVED", "ACTION_REFRESH_HEAD_TRACKING")
                SpatialHeadTracking.load(this)
            }
            ACTION_FORCE_SPEAKER_OUTPUT -> {
                Log.d("VANTA_SERVICE_ACTION_RECEIVED", "ACTION_FORCE_SPEAKER_OUTPUT")
                forceSpeakerOutput()
            }
            ACTION_REFRESH_AUTO_MIX -> {
                cancelAutoMixJobs(resetVolume = true)
                if (player.isPlaying) startAutoMixMonitor()
            }
            ACTION_PLAY_TRACK -> {
                val trackId = intent.getLongExtra(EXTRA_TRACK_ID, -1L)
                val preResolvedUrl = intent.getStringExtra(EXTRA_RESOLVED_STREAM_URL)
                val preResolvedHeaders = intent.getBundleExtra(EXTRA_RESOLVED_STREAM_HEADERS)
                if (!preResolvedUrl.isNullOrBlank() && preResolvedHeaders != null) {
                    val map = preResolvedHeaders.keySet().mapNotNull { k -> preResolvedHeaders.getString(k)?.let { k to it } }.toMap()
                    if (map.isNotEmpty()) {
                        streamHeadersByUrl[preResolvedUrl] = map
                        activeStreamHeaders = map
                    }
                }
                Log.d("VANTA_SERVICE_ACTION_RECEIVED", "ACTION_PLAY_TRACK: trackId=$trackId preResolvedUrl=${preResolvedUrl != null}")
                val readyStream = if (PlaybackCommandAuth.isTrusted(intent)) resolvedStreamFromIntent(intent) else null
                if (trackId > 0L) playTrack(trackId, preResolvedStream = readyStream)
                else Log.w("VANTA_SERVICE_ACTION_RECEIVED", "ACTION_PLAY_TRACK with invalid trackId=$trackId")
            }
            ACTION_PLAY_NEXT_FROM_QUEUE -> { Log.d("VANTA_SERVICE_ACTION_RECEIVED", "ACTION_PLAY_NEXT_FROM_QUEUE"); playNextFromQueue() }
            ACTION_PLAY_PREVIOUS_FROM_QUEUE -> { Log.d("VANTA_SERVICE_ACTION_RECEIVED", "ACTION_PLAY_PREVIOUS_FROM_QUEUE"); playPreviousFromQueue() }
            ACTION_PLAY -> { Log.d("VANTA_SERVICE_ACTION_RECEIVED", "ACTION_PLAY"); play() }
            ACTION_PAUSE -> { Log.d("VANTA_SERVICE_ACTION_RECEIVED", "ACTION_PAUSE"); pause() }
            ACTION_RESUME -> { Log.d("VANTA_SERVICE_ACTION_RECEIVED", "ACTION_RESUME"); resume() }
            ACTION_TOGGLE_PLAY_PAUSE -> { Log.d("VANTA_SERVICE_ACTION_RECEIVED", "ACTION_TOGGLE_PLAY_PAUSE"); togglePlayPause() }
            ACTION_TOGGLE_FAVORITE -> {
                Log.d("VANTA_SERVICE_ACTION_RECEIVED", "ACTION_TOGGLE_FAVORITE")
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
            }
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
            ACTION_SPEAK_DJ_VOICE -> {
                val path = intent.getStringExtra(EXTRA_DJ_VOICE_AUDIO_PATH).orEmpty()
                if (path.isNotBlank() && PlaybackCommandAuth.isTrusted(intent)) {
                    Log.d("VANTA_SERVICE_ACTION_RECEIVED", "ACTION_SPEAK_DJ_VOICE path=${path.take(60)}")
                    speakDjVoice(path)
                }
            }
            ACTION_ATTACH_MUSIC_VIDEO -> {
                Log.d("VANTA_SERVICE_ACTION_RECEIVED", "ACTION_ATTACH_MUSIC_VIDEO")
                attachMusicVideoCompanion()
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
                // Virtual queue timeline removed; ExoPlayer drives MediaSession timeline now.
                // The single media item finished while the station refill was still
                // appending tracks. Resume into the newly queued track.
                resumeIfEndedAndQueueHasNext("refresh_queue_timeline")
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        cancelAutoMixJobs(resetVolume = false)
        OutputSwitchController.stopWatching(this)
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
            .setContentText("Preparing playback...")
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

    private var playbackDeliveryWatch: kotlinx.coroutines.Job? = null
    private var currentDeliveryStream: SourceResolvedStream? = null
    private var deliveryRecoveryAttempted = false

    private fun watchAudioDelivery(generation: Long) {
        playbackDeliveryWatch?.cancel()
        playbackDeliveryWatch = serviceScope.launch {
            var stalledSince = android.os.SystemClock.elapsedRealtime()
            var lastPosition = exoPlayer.currentPosition
            while (generation == activePlaybackGeneration && !userPauseRequested) {
                delay(1_000)
                val position = exoPlayer.currentPosition
                if (!shouldRecoverStalledPlayback(exoPlayer.playWhenReady,
                        exoPlayer.playbackSuppressionReason != Player.PLAYBACK_SUPPRESSION_REASON_NONE,
                        exoPlayer.playbackState == Player.STATE_ENDED, lastPosition, position)) {
                    stalledSince = android.os.SystemClock.elapsedRealtime()
                }
                lastPosition = position
                if (android.os.SystemClock.elapsedRealtime() - stalledSince >= 20_000) {
                    recoverAudioDelivery("The audio source stopped responding.")
                    break
                }
            }
        }
    }

    private fun recoverAudioDelivery(message: String) {
        if (userPauseRequested) return
        val track = activeTrack ?: return
        val failed = currentDeliveryStream ?: return
        com.audiophile.musicplayer.common.VantaLogger.w(
            com.audiophile.musicplayer.common.VantaLogger.Tag.PLAYBACK,
            "delivery_recovery_start failedHost=${com.audiophile.musicplayer.common.VantaLogger.urlHost(failed.streamUrl)} " +
                "url=${failed.streamUrl.take(140)} msg='$message' title='${track.track.title}'"
        )
        val generation = activePlaybackGeneration
        val resumePositionMs = exoPlayer.currentPosition.coerceAtLeast(0L)
        playbackDeliveryWatch?.cancel()
        if (deliveryRecoveryAttempted) {
            exoPlayer.stop()
            playbackState.update { copy(isPlaying = false, isBuffering = false, errorMessage = message) }
            return
        }
        deliveryRecoveryAttempted = true
        exoPlayer.pause()
        playbackState.update { copy(isPlaying = false, isBuffering = true, errorMessage = "Trying another source for this recording…") }
        serviceScope.launch {
            val replacement = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                runCatching { streamResolver.resolveWithOutcome(track, timeoutMs = 35_000,
                    excludedStreamUrls = setOf(failed.streamUrl),
                    requestedQuality = com.audiophile.musicplayer.data.source.playback.RequestedAudioQuality.LOSSLESS_16)
                }.getOrNull().let { (it as? com.audiophile.musicplayer.data.source.playback.PlaybackSourceOutcome.Ready)?.stream }
            }
            if (generation != activePlaybackGeneration || userPauseRequested) return@launch
            if (replacement != null) {
                com.audiophile.musicplayer.common.VantaLogger.i(
                    com.audiophile.musicplayer.common.VantaLogger.Tag.TRACK_TRUTH,
                    "delivery_retry_resolved host=${com.audiophile.musicplayer.common.VantaLogger.urlHost(replacement.streamUrl)} " +
                        "provider=${replacement.providerId} url=${replacement.streamUrl.take(120)}"
                )
                playTrack(track.track.trackId, preResolvedStream = replacement, isDeliveryRetry = true, startPositionMs = resumePositionMs)
            } else {
                exoPlayer.stop()
                playbackState.update { copy(isPlaying = false, isBuffering = false,
                    errorMessage = "This recording is unavailable from the current sources. Try another song.") }
            }
        }
    }

    fun playTrack(
        trackId: Long,
        policy: SourceSelectionPolicy = SourceSelectionPolicy(),
        preResolvedStream: SourceResolvedStream? = null,
        isDeliveryRetry: Boolean = false,
        startPositionMs: Long = 0L
    ) {
        playbackDeliveryWatch?.cancel()
        deliveryRecoveryAttempted = isDeliveryRetry
        currentDeliveryStream = null
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
                cachedIsFavorite = track.track.localLibraryId
                    ?.let { localLibraryRepository.songById(it)?.isFavorite }
                    ?: false
                val cachedStream = preResolvedStream?.takeIf {
                    it.streamUrl.isNotBlank() &&
                        !PlaybackPolicies.isStreamExpired(it.expiresAt, it.streamUrl) &&
                        SpatialDecoderCapabilities.supportsAtmosStream(it)
                }
                val outcome = if (cachedStream == null) streamResolver.resolveWithOutcome(track) else null
                val stream = cachedStream ?: (outcome as? com.audiophile.musicplayer.data.source.playback.PlaybackSourceOutcome.Ready)?.stream
                if (generation != activePlaybackGeneration) return@launch
                
                if (stream == null || stream.streamUrl.isBlank()) {
                    val errorState = NowPlayingState(
                        trackId = trackId.toString(),
                        errorMessage = (outcome as? com.audiophile.musicplayer.data.source.playback.PlaybackSourceOutcome.Failed)
                            ?.failure?.let {
                                if (it.code == com.audiophile.musicplayer.data.source.playback.PlaybackSourceErrorCode.AUTH_REQUIRED)
                                    "Music source needs reconnection. Complete provider verification to resume streaming."
                                else it.userMessage()
                            } ?: "No playable stream found"
                    )
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                        player.stop()
                        player.clearMediaItems()
                    }
                    nowPlayingStateStore.save(errorState)
                    playbackState.replace(errorState)
                    return@launch
                }
                
                currentDeliveryStream = stream
                com.audiophile.musicplayer.common.VantaLogger.d(
                    com.audiophile.musicplayer.common.VantaLogger.Tag.TRACK_TRUTH,
                    "play_url host=${com.audiophile.musicplayer.common.VantaLogger.urlHost(stream.streamUrl)} " +
                        "provider=${stream.providerId} mime=${stream.mimeType} expires=${stream.expiresAt} " +
                        "fromPreResolved=${cachedStream != null} url=${stream.streamUrl.take(120)}"
                )
                if (stream.isDolbyAtmos) {
                    // Let ExoPlayer select multi-channel E-AC-3 JOC renditions instead of
                    // forcing the stereo default, so 5.1+/Atmos reaches the device's
                    // native Dolby engine.
                    exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters
                        .buildUpon()
                        .setMaxAudioChannelCount(6)
                        .build()
                    Log.d("VANTA_DSP", "atmos_track_selection maxAudioChannelCount=6 ${com.audiophile.musicplayer.common.VantaLogger.urlHost(stream.streamUrl)}")
                }
                val playbackHeaders = stream.requestHeaders + CdnPlaybackHeaders.forUrl(stream.streamUrl)
                activeStreamHeaders = playbackHeaders
                if (playbackHeaders.isNotEmpty()) {
                    streamHeadersByUrl[stream.streamUrl] = playbackHeaders
                }
                
                val mime = PlaybackMediaType.forStream(stream.mimeType, stream.streamUrl)
                
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    playAudioMediaItem(track, stream, mime, startPositionMs)
                    
                    val snapshot = queueManager.snapshot()
                    
                    playbackStateManager.onTrackChanged(
                        track = track,
                        qualityInfo = VantaQualityInfo.fromSource(
                            bitrate = stream.bitrateKbps,
                            status = SearchItemStatus.VALIDATED_PLAYABLE,
                            sourceProviderId = stream.providerId ?: "resolved",
                            isValidated = true,
                            reason = "stream_resolved",
                            mime = mime,
                            quality = stream.qualityLabel,
                            format = stream.format,
                            isSpatialAudio = stream.isSpatialAudio,
                            isDolbyAtmos = stream.isDolbyAtmos,
                            isEclipsaAudio = stream.isEclipsaAudio,
                            isSony360RealityAudio = stream.isSony360RealityAudio,
                            isSurround = stream.isSurround
                        ),
                        queuePosition = snapshot.queueIndex,
                        queueSize = snapshot.queueSize
                    )

                    refreshAutoCustomLayout()
                    beginListeningHistoryEntry(track, stream, startPositionMs)
                    watchAudioDelivery(generation)
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

    private fun resolvedStreamFromIntent(intent: Intent): SourceResolvedStream? {
        val url = intent.getStringExtra(EXTRA_RESOLVED_STREAM_URL)?.takeIf { it.isNotBlank() } ?: return null
        fun headers(key: String): Map<String, String> = intent.getBundleExtra(key)?.let { bundle ->
            bundle.keySet().mapNotNull { name -> bundle.getString(name)?.let { name to it } }.toMap()
        }.orEmpty()
        val licenseUrl = intent.getStringExtra(EXTRA_RESOLVED_STREAM_DRM_LICENSE_URL)
        val drm = licenseUrl?.let {
            com.audiophile.musicplayer.data.source.StreamDrmConfiguration(
                scheme = intent.getStringExtra(EXTRA_RESOLVED_STREAM_DRM_SCHEME).orEmpty(),
                licenseUrl = it,
                licenseRequestHeaders = headers(EXTRA_RESOLVED_STREAM_DRM_HEADERS),
                forceDefaultLicenseUri = intent.getBooleanExtra(EXTRA_RESOLVED_STREAM_DRM_FORCE_DEFAULT, true)
            )
        }
        return SourceResolvedStream(
            streamUrl = url,
            bitrateKbps = intent.getIntExtra(EXTRA_RESOLVED_STREAM_BITRATE, 0),
            mimeType = intent.getStringExtra(EXTRA_RESOLVED_STREAM_MIME),
            expiresAt = intent.getLongExtra(EXTRA_RESOLVED_STREAM_EXPIRES_AT, -1L).takeIf { it > 0 },
            qualityLabel = intent.getStringExtra(EXTRA_RESOLVED_STREAM_QUALITY),
            format = intent.getStringExtra(EXTRA_RESOLVED_STREAM_FORMAT),
            bitDepth = intent.getIntExtra(EXTRA_RESOLVED_STREAM_BIT_DEPTH, -1).takeIf { it > 0 },
            sampleRateHz = intent.getIntExtra(EXTRA_RESOLVED_STREAM_SAMPLE_RATE, -1).takeIf { it > 0 },
            channelCount = intent.getIntExtra(EXTRA_RESOLVED_STREAM_CHANNELS, -1).takeIf { it > 0 },
            codec = intent.getStringExtra(EXTRA_RESOLVED_STREAM_CODEC),
            container = intent.getStringExtra(EXTRA_RESOLVED_STREAM_CONTAINER),
            isLossless = intent.getBooleanExtra(EXTRA_RESOLVED_STREAM_LOSSLESS, false),
            sourceLabel = intent.getStringExtra(EXTRA_RESOLVED_STREAM_SOURCE_LABEL),
            isEclipsaAudio = intent.getBooleanExtra(EXTRA_RESOLVED_STREAM_ECLIPSA, false),
            providerId = intent.getStringExtra(EXTRA_RESOLVED_STREAM_PROVIDER),
            fulfillmentProviderId = intent.getStringExtra(EXTRA_RESOLVED_STREAM_FULFILLED_BY),
            isDolbyAtmos = intent.getBooleanExtra(EXTRA_RESOLVED_STREAM_ATMOS, false),
            isSpatialAudio = intent.getBooleanExtra(EXTRA_RESOLVED_STREAM_SPATIAL, false),
            isSurround = intent.getBooleanExtra(EXTRA_RESOLVED_STREAM_SURROUND, false),
            requestHeaders = headers(EXTRA_RESOLVED_STREAM_HEADERS),
            drm = drm
        )
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
        val target = positionMs.coerceAtLeast(0L).let {
            if (exoPlayer.duration > 0L) it.coerceAtMost(exoPlayer.duration) else it
        }
        exoPlayer.seekTo(target)
        if (exoPlayer.playbackState == Player.STATE_IDLE && exoPlayer.mediaItemCount > 0) {
            exoPlayer.prepare()
        }
        queueManager.markPlaybackPosition(target)
        playbackState.update { copy(positionMs = target) }
        watchAudioDelivery(activePlaybackGeneration)
    }

    fun playNextFromQueue(policy: SourceSelectionPolicy = SourceSelectionPolicy()) {
        listeningHistoryRecorder.onTrackSkipped(if (::player.isInitialized) player.currentPosition else 0L)
        serviceScope.launch {
            val nextTrack = queueController.advance() ?: run {
                Log.d("VANTA_QUEUE_TRUTH", "action=next_from_queue no_next_track")
                return@launch
            }
            Log.d("VANTA_QUEUE_TRUTH", "action=next_from_queue trackId=${nextTrack.track.trackId} title=${nextTrack.track.title}")
            playTrack(nextTrack.track.trackId, policy)
        }
    }

    /**
     * THE missing natural-end auto-advance. ExoPlayer only ever holds ONE
     * media item (playAudioMediaItem/setMediaItem), so when the single item
     * finishes ExoPlayer enters STATE_ENDED and sits there forever: nothing
     * materializes the app-side queue into ExoPlayer's timeline and no code
     * was advancing to the next queue track. The station monitor can refill
     * endlessly but playback stays frozen at the end of the first track.
     *
     * This helper resumes exactly when the player is stopped at ENDED and the
     * app-side queue now has an upcoming track to play. It is called from:
     *  1. The STATE_ENDED listener (natural completion when tracks are queued)
     *  2. ACTION_REFRESH_QUEUE_TIMELINE (a refill landed while stopped)
     *
     * Guarded so it only advances while really at ENDED (not paused mid-track,
     * not actively playing, not recovering), avoiding any double-next.
     */
    private fun resumeIfEndedAndQueueHasNext(source: String) {
        if (!::exoPlayer.isInitialized) return
        val snapshot = queueManager.snapshot()
        if (!snapshot.canPlayNext) {
            Log.d("VANTA_QUEUE_TRUTH", "action=resume_ended source=$source skipped=no_next")
            return
        }
        if (exoPlayer.playbackState != Player.STATE_ENDED) {
            Log.d("VANTA_QUEUE_TRUTH", "action=resume_ended source=$source skipped=not_ended state=${exoPlayer.playbackState}")
            return
        }
        if (autoMixTransitionInFlight) {
            Log.d("VANTA_QUEUE_TRUTH", "action=resume_ended source=$source skipped=automix_in_flight")
            return
        }
        if (userPauseRequested) {
            Log.d("VANTA_QUEUE_TRUTH", "action=resume_ended source=$source skipped=user_paused")
            return
        }
        Log.d("VANTA_QUEUE_TRUTH", "action=resume_ended source=$source advancing_to_next title='${snapshot.upNextQueue.firstOrNull()?.track?.title ?: snapshot.originalQueue.getOrNull(snapshot.currentOriginalIndex + 1)?.track?.title}'")
        playNextFromQueue()
    }

    fun playPreviousFromQueue(policy: SourceSelectionPolicy = SourceSelectionPolicy()) {
        listeningHistoryRecorder.onTrackSkipped(if (::player.isInitialized) player.currentPosition else 0L, reasonEnd = "backbtn")
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
        listeningHistoryRecorder.onPlaybackStopped(if (::player.isInitialized) player.currentPosition else 0L)
        player.stop()
        queueManager.markPlaybackPosition(0L)
    }

    fun playDirectUrl(directUrl: String, title: String = "Direct stream", artist: String = "Unknown source") {
        val generation = playbackRequestGeneration.incrementAndGet()
        activePlaybackGeneration = generation
        userPauseRequested = false
        Log.d("VANTA_PLAYBACK_INTENT", "action=user_play_direct url=${directUrl.take(60)} userPauseRequested=false generation=$generation")
        activeTrack = null
        cachedIsFavorite = false
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
            refreshAutoCustomLayout()
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


    /**
     * Plays a synthesized DJ voice line over the current track with a smooth
     * exponential duck (fast initial drop toward a 18% floor so perceived
     * loudness ramps evenly), then restores volume along the mirrored curve.
     * Voice audio runs on a parallel MediaPlayer that does not request audio
     * focus, so it mixes with the active ExoPlayer instead of pausing it, and
     * is deliberately NOT routed through the DSP chain (clear speech).
     */
    fun speakDjVoice(audioPath: String) {
        if (audioPath.isBlank()) return
        if (!File(audioPath).exists()) {
            Log.w("VANTA_DJ_VOICE", "speakDjVoice missing file=$audioPath")
            return
        }
        if (userPauseRequested || !::exoPlayer.isInitialized) return
        val normalVolume = exoPlayer.volume.coerceIn(0.02f, 1f)
        val floor = 0.18f
        val steps = 20
        val duckStepMs = 400L / steps
        val restoreStepMs = 550L / steps

        releaseDjVoicePlayer()
        djVoiceDuckJob?.cancel()

        djVoiceDuckJob = serviceScope.launch {
            val prepared = kotlinx.coroutines.CompletableDeferred<Boolean>()
            val finished = kotlinx.coroutines.CompletableDeferred<Unit>()
            val mp = android.media.MediaPlayer()
            mp.setAudioAttributes(
                android.media.AudioAttributes.Builder()
                    .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
                    .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            mp.setOnPreparedListener { it.start(); prepared.complete(true) }
            mp.setOnErrorListener { _, what, extra ->
                Log.w("VANTA_DJ_VOICE", "media error what=$what extra=$extra")
                prepared.complete(false)
                true
            }
            mp.setOnCompletionListener { gone ->
                djVoicePlayer = null
                gone.release()
                finished.complete(Unit)
            }
            try {
                for (step in 0..steps) {
                    if (userPauseRequested) return@launch
                    val p = step.toFloat() / steps
                    exoPlayer.volume = (normalVolume * (floor / normalVolume).pow(p)).coerceIn(0f, 1f)
                    delay(duckStepMs)
                }
                mp.setDataSource(audioPath)
                mp.prepareAsync()
                if (!prepared.await()) {
                    exoPlayer.volume = normalVolume
                    return@launch
                }
                djVoicePlayer = mp
                finished.await()
                for (step in 1..steps) {
                    if (djVoicePlayer != mp) return@launch
                    val p = step.toFloat() / steps
                    exoPlayer.volume = (normalVolume * (floor / normalVolume).pow(1f - p)).coerceIn(0f, 1f)
                    delay(restoreStepMs)
                }
            } catch (e: Exception) {
                Log.w("VANTA_DJ_VOICE", "speakDjVoice failed", e)
                runCatching { mp.release() }
            } finally {
                exoPlayer.volume = normalVolume
                if (djVoicePlayer == mp) djVoicePlayer = null
            }
        }
    }

    private fun releaseDjVoicePlayer() {
        runCatching { djVoicePlayer?.release() }
        djVoicePlayer = null
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
        djVoiceDuckJob?.cancel()
        djVoiceDuckJob = null
        releaseDjVoicePlayer()
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

    private fun beginListeningHistoryEntry(
        track: UnifiedTrackWithSources,
        stream: SourceResolvedStream,
        startPositionMs: Long
    ) {
        val unified = track.track
        val platform = when {
            unified.localLibraryId != null -> "LOCAL"
            stream.providerId != null -> stream.providerId
            else -> null
        }
        val sourceTrackId = track.sources
            .firstOrNull { it.externalProviderId != null && it.externalProviderId == stream.providerId }
            ?.externalTrackId ?: unified.trackId.toString()
        listeningHistoryRecorder.onPlaybackStarted(
            ListeningPlayRecord(
                trackId = unified.trackId,
                title = unified.title,
                artist = unified.artist,
                album = unified.albumName,
                platform = platform,
                providerId = stream.providerId,
                sourceTrackId = sourceTrackId,
                durationMs = unified.durationMs
            ),
            positionMs = startPositionMs.coerceAtLeast(0L)
        )
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
            val stored = com.audiophile.musicplayer.playback.dsp.VantaEqualizerPreferences(this).load()
            val speakerRoute = OutputSwitchController.isBuiltInSpeakerRoute(this)
            val usbRoute = OutputSwitchController.isUsbDacRoute(this)
            val config = when {
                speakerRoute -> stored.forBuiltInSpeaker()
                usbRoute -> stored.forUsbPassthrough()
                else -> stored
            }
            if (::vantaEqualizer.isInitialized) {
                vantaEqualizer.config = config
                // Publish changes for the next audio buffer. Native filter work must
                // stay serialized with processing, including when playback resumes.
                vantaEqualizer.flushAndApplyConfig()
            }
            val holder = com.audiophile.musicplayer.playback.dsp.VantaEqualizerHolder.processor
            Log.d(
                "VANTA_DSP",
                "equalizer_pushed speakerSafe=$speakerRoute usbPassthrough=$usbRoute eq=${config.eqEnabled} " +
                    "spatial=${config.spatialEnabled} immersive=${config.immersiveMode.label} " +
                    "bassCannon=${config.bassCannonEnabled} tube=${config.tubeEnabled} " +
                    "bypass=${config.eqBypassEnabled} holder=${holder != null} " +
                    "nativeAvailable=${com.audiophile.musicplayer.playback.dsp.VantaEqualizerNative.isAvailable}"
            )
        } catch (e: Exception) {
            Log.e("VANTA_DSP", "equalizer apply failed", e)
        }
    }

    private fun forceSpeakerOutput() {
        val speaker = OutputSwitchController.listOutputs(this)
            .firstOrNull { it.type == android.media.AudioDeviceInfo.TYPE_BUILTIN_SPEAKER }
        if (speaker != null) {
            OutputSwitchController.select(this, speaker)
            applyVantaEqualizer()
        } else {
            Log.w("VANTA_SERVICE_ACTION_RECEIVED", "FORCE_SPEAKER_OUTPUT: no built-in speaker device found")
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
            Log.w("VANTA_PLAYBACK", "applyPlaybackMediaItems skipped - player not available")
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
            "audio/eac3", "audio/e-ac3", "audio/x-eac3", "audio/eac3-joc" -> MimeTypes.AUDIO_E_AC3
            "audio/ac3", "audio/x-ac3" -> MimeTypes.AUDIO_AC3
            "audio/ogg", "application/ogg" -> MimeTypes.AUDIO_OGG
            "audio/opus" -> MimeTypes.AUDIO_OPUS
            "audio/wav", "audio/wave", "audio/x-wav" -> MimeTypes.AUDIO_WAV
            "audio/x-ms-wma" -> "audio/x-ms-wma"
            "application/octet-stream", "binary/octet-stream" -> null
            else -> clean.takeIf { it.startsWith("audio/") }
        }
    }

    private fun inferMimeTypeFromUrl(url: String?): String? {
        val clean = url?.trim().orEmpty()
        if (clean.isBlank()) return null
        val decoded = runCatching { android.net.Uri.decode(clean) }.getOrDefault(clean)
        val ext = decoded
            .substringBefore('?')
            .substringBefore('#')
            .substringAfterLast('.', "")
            .lowercase()
        return when (ext) {
            "flac" -> MimeTypes.AUDIO_FLAC
            "mp3" -> MimeTypes.AUDIO_MPEG
            "m4a", "mp4", "aac" -> MimeTypes.AUDIO_MP4
            "eac3", "ec3" -> MimeTypes.AUDIO_E_AC3
            "ac3" -> MimeTypes.AUDIO_AC3
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

    /**
     * Keep the current FLAC / Atmos audio URL and merge a picture-only music video
     * (Tidal MV preferred, YouTube video-only fallback). Video audio is filtered out.
     */
    private fun attachMusicVideoCompanion() {
        val track = activeTrack
        val audio = currentDeliveryStream
        if (track == null || audio == null || audio.streamUrl.isBlank()) {
            playbackState.update {
                copy(errorMessage = "Play a song first, then attach the music video")
            }
            return
        }
        val generation = activePlaybackGeneration
        serviceScope.launch(Dispatchers.IO) {
            playbackState.update { copy(isBuffering = true, errorMessage = null) }
            val video = musicVideoCompanionResolver.resolve(track)
            if (generation != activePlaybackGeneration) return@launch
            if (video == null || video.streamUrl.isBlank()) {
                playbackState.update {
                    copy(
                        isBuffering = false,
                        errorMessage = "No music video found (Tidal MV / YouTube). Audio stays FLAC/Atmos."
                    )
                }
                return@launch
            }
            val videoHeaders = video.requestHeaders + CdnPlaybackHeaders.forUrl(video.streamUrl)
            if (videoHeaders.isNotEmpty()) {
                streamHeadersByUrl[video.streamUrl] = videoHeaders
            }
            withContext(Dispatchers.Main) {
                if (generation != activePlaybackGeneration) return@withContext
                val positionMs = exoPlayer.currentPosition.coerceAtLeast(0L)
                val wasPlaying = exoPlayer.isPlaying || exoPlayer.playWhenReady
                playMergedAudioAndVideo(track, audio, video, positionMs, wasPlaying)
                Log.i(
                    "VANTA_TV_VIDEO",
                    "attached video=${video.providerId} audio=${audio.providerId} " +
                        "atmos=${audio.isDolbyAtmos} lossless=${audio.isLossless} pos=$positionMs"
                )
            }
        }
    }

    private fun playAudioMediaItem(
        track: UnifiedTrackWithSources,
        stream: SourceResolvedStream,
        mime: String?,
        startPositionMs: Long
    ) {
        val metadataBuilder = MediaMetadata.Builder()
            .setTitle(track.track.title)
            .setArtist(track.track.artist)
            .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
        artworkUri(track.track.coverArtUrl)?.let { metadataBuilder.setArtworkUri(it) }

        player.setMediaItem(
            MediaItem.Builder()
                .setUri(stream.streamUrl)
                .setMediaId("${track.track.trackId}:0")
                .apply { mime?.let { setMimeType(it) } }
                .apply {
                    PlaybackDrmPolicy.validated(stream.drm)?.let { drm ->
                        setDrmConfiguration(
                            MediaItem.DrmConfiguration.Builder(C.WIDEVINE_UUID)
                                .setLicenseUri(drm.licenseUrl)
                                .setLicenseRequestHeaders(drm.licenseRequestHeaders)
                                .setForceDefaultLicenseUri(drm.forceDefaultLicenseUri)
                                .build()
                        )
                    }
                }
                .setMediaMetadata(metadataBuilder.build())
                .build()
        )
        if (startPositionMs > 0L) exoPlayer.seekTo(startPositionMs)
        player.prepare()
        player.play()
        playbackState.update { copy(hasVideo = false) }
    }

    private fun playMergedAudioAndVideo(
        track: UnifiedTrackWithSources,
        audio: SourceResolvedStream,
        video: SourceResolvedStream,
        startPositionMs: Long,
        playWhenReady: Boolean
    ) {
        val metadataBuilder = MediaMetadata.Builder()
            .setTitle(track.track.title)
            .setArtist(track.track.artist)
            .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
        artworkUri(track.track.coverArtUrl)?.let { metadataBuilder.setArtworkUri(it) }
        val metadata = metadataBuilder.build()

        val audioMime = PlaybackMediaType.forStream(audio.mimeType, audio.streamUrl)
        val videoMime = PlaybackMediaType.forStream(video.mimeType, video.streamUrl)
            ?: video.mimeType

        val audioItem = MediaItem.Builder()
            .setUri(audio.streamUrl)
            .setMediaId("${track.track.trackId}:audio")
            .apply { audioMime?.let { setMimeType(it) } }
            .apply {
                PlaybackDrmPolicy.validated(audio.drm)?.let { drm ->
                    setDrmConfiguration(
                        MediaItem.DrmConfiguration.Builder(C.WIDEVINE_UUID)
                            .setLicenseUri(drm.licenseUrl)
                            .setLicenseRequestHeaders(drm.licenseRequestHeaders)
                            .setForceDefaultLicenseUri(drm.forceDefaultLicenseUri)
                            .build()
                    )
                }
            }
            .setMediaMetadata(metadata)
            .build()

        val videoItem = MediaItem.Builder()
            .setUri(video.streamUrl)
            .setMediaId("${track.track.trackId}:video")
            .apply { videoMime?.let { setMimeType(it) } }
            .setMediaMetadata(metadata)
            .build()

        val videoSource = FilteringMediaSource(
            mediaSourceFactory.createMediaSource(videoItem),
            C.TRACK_TYPE_AUDIO
        )
        val audioSource = mediaSourceFactory.createMediaSource(audioItem)
        val merged = MergingMediaSource(videoSource, audioSource)

        if (audio.isDolbyAtmos) {
            exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters
                .buildUpon()
                .setMaxAudioChannelCount(8)
                .build()
        }

        exoPlayer.setMediaSource(merged, startPositionMs)
        exoPlayer.prepare()
        exoPlayer.playWhenReady = playWhenReady
        playbackState.update {
            copy(
                isBuffering = false,
                hasVideo = true,
                errorMessage = null,
                isPlaying = playWhenReady
            )
        }
    }

    companion object {
        var audioSessionId: Int = -1
            private set

        const val ACTION_PLAY_TRACK = "com.audiophile.musicplayer.action.PLAY_TRACK"
        const val ACTION_REFRESH_IMMERSIVE_AUDIO = "com.audiophile.musicplayer.action.REFRESH_EQUALIZER"
        const val ACTION_REFRESH_HEAD_TRACKING = "com.audiophile.musicplayer.action.REFRESH_HEAD_TRACKING"
        const val ACTION_FORCE_SPEAKER_OUTPUT = "com.audiophile.musicplayer.action.FORCE_SPEAKER_OUTPUT"
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
        const val ACTION_SPEAK_DJ_VOICE = "com.audiophile.musicplayer.action.SPEAK_DJ_VOICE"
        const val EXTRA_DJ_VOICE_AUDIO_PATH = "extra_dj_voice_audio_path"
        const val ACTION_ATTACH_MUSIC_VIDEO = "com.audiophile.musicplayer.action.ATTACH_MUSIC_VIDEO"

        const val ACTION_TOGGLE_FAVORITE = "vanta_toggle_favorite"
        const val ACTION_TOGGLE_SHUFFLE = "vanta_toggle_shuffle"
        const val ACTION_TOGGLE_REPEAT = "vanta_toggle_repeat"

        const val EXTRA_TRACK_ID = "extra_track_id"
        const val EXTRA_POSITION_MS = "extra_position_ms"
        const val EXTRA_STREAM_URL = "extra_stream_url"
        const val EXTRA_TITLE = "extra_title"
        const val EXTRA_ARTIST = "extra_artist"
        const val EXTRA_RESOLVED_STREAM_URL = "extra_resolved_stream_url"
        const val EXTRA_RESOLVED_STREAM_BITRATE = "extra_resolved_stream_bitrate"
        const val EXTRA_RESOLVED_STREAM_MIME = "extra_resolved_stream_mime"
        const val EXTRA_RESOLVED_STREAM_EXPIRES_AT = "extra_resolved_stream_expires_at"
        const val EXTRA_RESOLVED_STREAM_QUALITY = "extra_resolved_stream_quality"
        const val EXTRA_RESOLVED_STREAM_FORMAT = "extra_resolved_stream_format"
        const val EXTRA_RESOLVED_STREAM_BIT_DEPTH = "extra_resolved_stream_bit_depth"
        const val EXTRA_RESOLVED_STREAM_SAMPLE_RATE = "extra_resolved_stream_sample_rate"
        const val EXTRA_RESOLVED_STREAM_CHANNELS = "extra_resolved_stream_channels"
        const val EXTRA_RESOLVED_STREAM_CODEC = "extra_resolved_stream_codec"
        const val EXTRA_RESOLVED_STREAM_CONTAINER = "extra_resolved_stream_container"
        const val EXTRA_RESOLVED_STREAM_LOSSLESS = "extra_resolved_stream_lossless"
        const val EXTRA_RESOLVED_STREAM_SOURCE_LABEL = "extra_resolved_stream_source_label"
        const val EXTRA_RESOLVED_STREAM_ECLIPSA = "extra_resolved_stream_eclipsa"
        const val EXTRA_RESOLVED_STREAM_PROVIDER = "extra_resolved_stream_provider"
        const val EXTRA_RESOLVED_STREAM_FULFILLED_BY = "extra_resolved_stream_fulfilled_by"
        const val EXTRA_RESOLVED_STREAM_SPATIAL = "extra_resolved_stream_spatial"
        const val EXTRA_RESOLVED_STREAM_ATMOS = "extra_resolved_stream_atmos"
        const val EXTRA_RESOLVED_STREAM_SURROUND = "extra_resolved_stream_surround"
        const val EXTRA_RESOLVED_STREAM_HEADERS = "extra_resolved_stream_headers"
        const val EXTRA_RESOLVED_STREAM_DRM_SCHEME = "extra_resolved_stream_drm_scheme"
        const val EXTRA_RESOLVED_STREAM_DRM_LICENSE_URL = "extra_resolved_stream_drm_license_url"
        const val EXTRA_RESOLVED_STREAM_DRM_FORCE_DEFAULT = "extra_resolved_stream_drm_force_default"
        const val EXTRA_RESOLVED_STREAM_DRM_HEADERS = "extra_resolved_stream_drm_headers"

        private const val CHANNEL_ID = "playback_channel"
        private const val NOTIFICATION_ID = 1001
        private const val AUTO_MAIN_STAGE_RESUME_ID = "vanta:main-stage:resume"
        private const val AUTO_MOOD_PREFIX = "vanta:mood:"
        private const val AUTO_RESOLVE_TIMEOUT_MS = 12_000L
        private const val AUTO_REMOTE_CACHE_SIZE = 200
        private val AUTO_MAIN_STAGE_QUEUE_ID = AndroidAutoBrowseController.AUTO_QUEUE_ID
        private val AUTO_RECENT_ID = AndroidAutoBrowseController.AUTO_RECENT_ID
        private val AUTO_TRACKS_ID = AndroidAutoBrowseController.AUTO_TRACKS_ID
        private val AUTO_SHUFFLE_ID = AndroidAutoBrowseController.AUTO_SHUFFLE_ID
        private val AUTO_DEVICE_ID = AndroidAutoBrowseController.AUTO_DEVICE_ID
        private val AUTO_HIGH_QUALITY_ID = AndroidAutoBrowseController.AUTO_HIGH_QUALITY_ID
        private val AUTO_TRACK_PREFIX = AndroidAutoBrowseController.AUTO_TRACK_PREFIX
        private val AUTO_REMOTE_TRACK_PREFIX = AndroidAutoBrowseController.AUTO_REMOTE_TRACK_PREFIX
        private val AUTO_ARTIST_PREFIX = AndroidAutoBrowseController.AUTO_ARTIST_PREFIX
        private val AUTO_ALBUM_PREFIX = AndroidAutoBrowseController.AUTO_ALBUM_PREFIX
        private val AUTO_PAGE_SIZE = AndroidAutoBrowseController.AUTO_PAGE_SIZE
        private val AUTO_SEARCH_LIMIT = AndroidAutoBrowseController.AUTO_SEARCH_LIMIT
        private val AUTO_HIGH_QUALITY_KBPS = AndroidAutoBrowseController.AUTO_HIGH_QUALITY_KBPS
        private val AUTO_SONG_RADIO_ID = AndroidAutoBrowseController.AUTO_SONG_RADIO_ID
        private val AUTO_ARTIST_RADIO_ID = AndroidAutoBrowseController.AUTO_ARTIST_RADIO_ID
        private val AUTO_LIKED_ID = AndroidAutoBrowseController.AUTO_LIKED_ID
        private val AUTO_PLAYLISTS_ID = AndroidAutoBrowseController.AUTO_PLAYLISTS_ID
        private val AUTO_PLAYLIST_PREFIX = AndroidAutoBrowseController.AUTO_PLAYLIST_PREFIX
        private val AUTO_DJ_ID = AndroidAutoBrowseController.AUTO_DJ_ID
        private val AUTO_LYRICS_ID = AndroidAutoBrowseController.AUTO_LYRICS_ID
    }
}




