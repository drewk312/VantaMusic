package com.audiophile.musicplayer.ui

import androidx.activity.compose.BackHandler
import com.audiophile.musicplayer.social.VantaSocialManager
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.zIndex
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.tween
import com.audiophile.musicplayer.ui.theme.VantaMotion
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import com.audiophile.musicplayer.data.dj.AiDjViewModel
import com.audiophile.musicplayer.data.dj.JukeboxCatalog
import com.audiophile.musicplayer.data.local.entities.UnifiedTrackWithSources
import com.audiophile.musicplayer.playback.NowPlayingViewModel
import com.audiophile.musicplayer.playback.PlaybackService
import kotlinx.coroutines.launch
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.audiophile.musicplayer.StartupSafeguard
import com.audiophile.musicplayer.debug.VantaDiagnosticLog
import com.audiophile.musicplayer.permissions.MediaPermissions
import com.audiophile.musicplayer.ui.visualizer.VantaVisualizerViewModel
import com.audiophile.musicplayer.data.display.VantaQualityInfo
import android.content.Intent
import android.util.Log
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import com.audiophile.musicplayer.data.source.sourceValidityStatus
import com.audiophile.musicplayer.data.source.canEnterPlaybackFlow
import com.audiophile.musicplayer.data.source.isConfirmedPlayable
import com.audiophile.musicplayer.data.canonical.CanonicalTrack

enum class AppRoute {
    Home, Discover, Library, Search, Drive, AiDj,
    Settings, NowPlaying,
    Account,
    Imports, ImportText, ImportPreview, ImportReview,
    AdvancedSettings,
    ParametricEq,
    RadioStation
}

data class DetailRoute(
    val type: Type,
    val name: String,
    val id: String?,
    val secondaryName: String? = null,
    val artworkUrl: String? = null,
    val releaseYear: Int? = null,
    val genre: String? = null,
    val explicit: Boolean? = null
) {
    enum class Type { Artist, Album }
}

private fun encodeDetailRoute(route: DetailRoute?): List<String> {
    if (route == null) return listOf("__null__")
    return listOf(
        route.type.name,
        route.name,
        route.id ?: "",
        route.secondaryName ?: "",
        route.artworkUrl ?: "",
        route.releaseYear?.toString() ?: "",
        route.genre ?: "",
        route.explicit?.toString() ?: ""
    )
}

private fun decodeDetailRoute(value: List<String>): DetailRoute? {
    if (value.isEmpty()) return null
    if (value[0] == "__null__") return null
    return try {
        DetailRoute(
            type = DetailRoute.Type.valueOf(value[0]),
            name = value[1],
            id = value[2].ifBlank { null },
            secondaryName = value.getOrNull(3)?.ifBlank { null },
            artworkUrl = value.getOrNull(4)?.ifBlank { null },
            releaseYear = value.getOrNull(5)?.toIntOrNull(),
            genre = value.getOrNull(6)?.ifBlank { null },
            explicit = value.getOrNull(7)?.toBooleanStrictOrNull()
        )
    } catch (_: Exception) {
        null
    }
}

private val DetailRouteSaver = listSaver<DetailRoute?, String>(
    save = { value -> encodeDetailRoute(value) },
    restore = { value -> decodeDetailRoute(value) }
)

private val DetailRouteListSaver = listSaver<List<DetailRoute>, String>(
    save = { list -> (list ?: emptyList()).flatMap { route -> encodeDetailRoute(route) } },
    restore = { value ->
        value.chunked(8).mapNotNull { chunk -> decodeDetailRoute(chunk) }
    }
)

@Composable
fun AppNavGraph(
    mainViewModel: MainViewModel,
    nowPlayingViewModel: NowPlayingViewModel,
    aiDjViewModel: AiDjViewModel,
    personalizedMixViewModel: PersonalizedMixViewModel,
    visualizerViewModel: VantaVisualizerViewModel,
    accountManager: com.audiophile.musicplayer.account.AccountManager,
    vantaSocialManager: VantaSocialManager,

    sharedImportPayload: SharedImportPayload? = null,
    onSharedImportConsumed: () -> Unit = {}
) {
    val uiState by mainViewModel.uiState.collectAsState()
    val personalizedMixState by personalizedMixViewModel.uiState.collectAsState()
    val nowPlayingState by nowPlayingViewModel.state.collectAsState()
    val rawLyricsData by nowPlayingViewModel.lyrics.collectAsState()
    val lyricsTrackId by nowPlayingViewModel.lyricsTrackId.collectAsState()
    val lyricsLoading by nowPlayingViewModel.lyricsLoading.collectAsState()
    val pulseInsight by nowPlayingViewModel.pulseInsight.collectAsState()
    val pulseInsightLoading by nowPlayingViewModel.pulseInsightLoading.collectAsState()
    val pulseDeepInsight by nowPlayingViewModel.pulseDeepInsight.collectAsState()
    val pulseDeepLoading by nowPlayingViewModel.pulseDeepLoading.collectAsState()
    val translationEnabled by nowPlayingViewModel.translationEnabled.collectAsState()
    val aiDjState by aiDjViewModel.state.collectAsState()
    // NowPlayingViewModel clears lyrics on every track change and rejects stale
    // async responses using the requested track id. Re-validating the cache key
    // here rejected legitimate pre-ISRC cache entries and ran on every position
    // tick, producing log spam and hiding valid lyrics.
    val lyricsData = rawLyricsData
    var route by rememberSaveable { mutableStateOf(AppRoute.Home) }
    var previousMainRoute by rememberSaveable { mutableStateOf(AppRoute.Home) }
    var paramEqReturnRoute by rememberSaveable { mutableStateOf(AppRoute.Home) }
    var nowPlayingReturnDetailRoute by rememberSaveable(stateSaver = DetailRouteSaver) { mutableStateOf<DetailRoute?>(null) }
    var nowPlayingReturnDetailHistory by rememberSaveable(stateSaver = DetailRouteListSaver) { mutableStateOf<List<DetailRoute>>(emptyList()) }
    var pendingImportName by rememberSaveable { mutableStateOf("Imported Playlist") }
    var pendingImportText by rememberSaveable { mutableStateOf("") }
    var detailRoute by rememberSaveable(stateSaver = DetailRouteSaver) { mutableStateOf<DetailRoute?>(null) }
    var detailHistory by rememberSaveable(stateSaver = DetailRouteListSaver) { mutableStateOf<List<DetailRoute>>(emptyList()) }
    var mixRoute by rememberSaveable { mutableStateOf<String?>(null) }
    var radioStationId by rememberSaveable { mutableStateOf<String?>(null) }
    var radioPreviewTracks by remember { mutableStateOf<List<UnifiedTrackWithSources>>(emptyList()) }
    var radioPreviewLoading by remember { mutableStateOf(false) }
    var pendingStationStart by remember { mutableStateOf<String?>(null) }
    var activeActionContext by remember { mutableStateOf<VantaActionContext?>(null) }
    var showSleepTimerSheet by rememberSaveable { mutableStateOf(false) }
    var showAddToPlaylistSheet by rememberSaveable { mutableStateOf(false) }
    var showSourceDetailsSheet by rememberSaveable { mutableStateOf(false) }
    var sleepTimerActiveMinutes by rememberSaveable { mutableStateOf<Int?>(null) }
    var playlistsForSheet by remember { mutableStateOf<List<com.audiophile.musicplayer.data.local.entities.PlaylistEntity>>(emptyList()) }
    val ctx = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var animatedArtworkEnabled by remember { mutableStateOf(VisualMotionPreferences.isAnimatedArtworkEnabled(ctx)) }
    val auraState by visualizerViewModel.auraState.collectAsState()
    val audioFrame by visualizerViewModel.audioFrame.collectAsState()

    var hasRequestedStartupPermissions by rememberSaveable { mutableStateOf(false) }
    val startupPermissions = remember { MediaPermissions.startupPermissions() }
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val audioGranted = results[MediaPermissions.requiredAudioPermission()] == true
        if (!audioGranted) {
            mainViewModel.setStatusMessage("Allow music access in Settings to scan your device library.")
        }
    }

    LaunchedEffect(Unit) {
        StartupSafeguard.onMainUiReady(ctx)
        if (!hasRequestedStartupPermissions) {
            hasRequestedStartupPermissions = true
            val missing = startupPermissions.filter {
                ContextCompat.checkSelfPermission(ctx, it) != PackageManager.PERMISSION_GRANTED
            }
            if (missing.isNotEmpty()) {
                permissionLauncher.launch(missing.toTypedArray())
            }
        }
    }

    LaunchedEffect(Unit) {
        nowPlayingViewModel.restore()
    }

    LaunchedEffect(route) {
        if (route == AppRoute.Library) {
            mainViewModel.ensureLibraryLoaded()
        }
    }

    LaunchedEffect(nowPlayingState.trackId, nowPlayingState.isPlaying) {
        repeat(60) {
            val sessionId = PlaybackService.audioSessionId
            if (sessionId > 0) {
                visualizerViewModel.attachToSession(sessionId)
                return@LaunchedEffect
            }
            kotlinx.coroutines.delay(250)
        }
    }

    var lastRoute by rememberSaveable { mutableStateOf<AppRoute?>(null) }
    LaunchedEffect(route) {
        if (route == AppRoute.AiDj && lastRoute != null && lastRoute != AppRoute.AiDj) {
            aiDjViewModel.prepareFreshEntry()
        }
        lastRoute = route
    }

    LaunchedEffect(sharedImportPayload) {
        val payload = sharedImportPayload ?: return@LaunchedEffect
        pendingImportName = payload.name.ifBlank { "Shared Import" }
        pendingImportText = payload.text
        route = AppRoute.ImportPreview
        onSharedImportConsumed()
    }

    fun openRadioStation(stationId: String) {
        radioStationId = stationId
        radioPreviewTracks = emptyList()
        radioPreviewLoading = true
    }

    LaunchedEffect(radioStationId) {
        val stationId = radioStationId ?: return@LaunchedEffect
        radioPreviewLoading = true
        try {
            radioPreviewTracks = mainViewModel.previewRadioStation(stationId)
        } catch (e: Exception) {
            Log.e("VANTA_RADIO", "Station preview crashed", e)
            VantaDiagnosticLog.error("RadioPreview", "stationId=$stationId preview_failed", e)
            radioPreviewTracks = emptyList()
        } finally {
            radioPreviewLoading = false
        }
    }

    val density = LocalDensity.current
    val isKeyboardVisible = WindowInsets.ime.getBottom(density) > 0
    val showBottomNav = ChromeVisibilityPolicy.shouldShowBottomNav(
        route = route,
        hasDetailOverlay = detailRoute != null,
        hasRadioStationOverlay = radioStationId != null,
        hasMixOverlay = mixRoute != null,
        isKeyboardVisible = isKeyboardVisible
    )

    var driveReturnRoute by rememberSaveable { mutableStateOf(AppRoute.Home) }
    fun openDrive(returnTo: AppRoute) {
        driveReturnRoute = returnTo
        route = AppRoute.Drive
    }

    fun openRoute(next: AppRoute) {
        if (next == AppRoute.NowPlaying) {
            nowPlayingReturnDetailRoute = detailRoute
            nowPlayingReturnDetailHistory = detailHistory
            if (route in setOf(AppRoute.Home, AppRoute.Library, AppRoute.Search, AppRoute.AiDj)) {
                previousMainRoute = route
            }
            detailRoute = null
            detailHistory = emptyList()
            radioStationId = null
            mixRoute = null
        }
        route = next
    }

    fun popNowPlaying() {
        route = previousMainRoute
        detailRoute = nowPlayingReturnDetailRoute
        detailHistory = nowPlayingReturnDetailHistory
    }

    LaunchedEffect(uiState.streamingStationLoading, pendingStationStart, uiState.statusMessage) {
        val pending = pendingStationStart ?: return@LaunchedEffect
        if (uiState.streamingStationLoading) return@LaunchedEffect
        pendingStationStart = null
        if (uiState.statusMessage?.startsWith("Playing") == true) {
            radioStationId = null
            openRoute(AppRoute.NowPlaying)
            nowPlayingViewModel.restore()
        }
    }

    fun openRootDetail(next: DetailRoute) {
        detailHistory = emptyList()
        detailRoute = next
    }

    fun openChildDetail(next: DetailRoute) {
        detailRoute?.let { detailHistory = detailHistory + it }
        detailRoute = next
    }

    fun closeDetail() {
        detailRoute = detailHistory.lastOrNull()
        if (detailHistory.isNotEmpty()) detailHistory = detailHistory.dropLast(1)
    }

    fun dismissDetails() {
        detailHistory = emptyList()
        detailRoute = null
    }

    BackHandler(enabled = activeActionContext != null) {
        activeActionContext = null
    }
    BackHandler(enabled = showSleepTimerSheet) {
        showSleepTimerSheet = false
    }
    BackHandler(enabled = showAddToPlaylistSheet) {
        showAddToPlaylistSheet = false
    }
    BackHandler(enabled = showSourceDetailsSheet) {
        showSourceDetailsSheet = false
    }
    BackHandler(enabled = mixRoute != null) {
        mixRoute = null
    }
    BackHandler(enabled = radioStationId != null) {
        radioStationId = null
    }
    BackHandler(enabled = detailRoute != null) {
        closeDetail()
    }
    BackHandler(enabled = route == AppRoute.NowPlaying) {
        popNowPlaying()
    }
    BackHandler(enabled = route == AppRoute.Settings || route == AppRoute.Account || route == AppRoute.Imports) {
        route = AppRoute.Home
    }
    BackHandler(enabled = route == AppRoute.AdvancedSettings || route == AppRoute.ParametricEq) {
        route = if (route == AppRoute.ParametricEq) paramEqReturnRoute else AppRoute.Settings
    }
    BackHandler(enabled = route == AppRoute.ImportText) {
        route = AppRoute.Imports
    }
    BackHandler(enabled = route == AppRoute.ImportPreview) {
        route = AppRoute.ImportText
    }
    BackHandler(enabled = route == AppRoute.ImportReview) {
        route = AppRoute.Imports
    }
    BackHandler(enabled = route == AppRoute.Drive) {
        route = driveReturnRoute
    }

    var trackSheetDismiss by remember { mutableStateOf(false) }
    val hasPlayableCurrentItem = ChromeVisibilityPolicy.hasPlayableCurrentItem(
        nowPlaying = nowPlayingState,
        queueSnapshot = uiState.queueSnapshot,
        activeTrackId = uiState.activeTrackId
    )
    val latchedPlayback = rememberPlaybackChromeLatch(hasPlayableCurrentItem)
    val miniPlayerVisible = ChromeVisibilityPolicy.shouldShowMiniPlayer(route, latchedPlayback)
    val chromeRouteLabel = ChromeVisibilityPolicy.routeLabel(
        route = route,
        hasDetailOverlay = detailRoute != null,
        hasRadioStationOverlay = radioStationId != null
    )

    androidx.compose.runtime.LaunchedEffect(
        chromeRouteLabel,
        miniPlayerVisible,
        showBottomNav,
        hasPlayableCurrentItem,
        nowPlayingState.isPlaying
    ) {
        Log.d(
            "VANTA_CHROME_VISIBILITY",
            "route=$chromeRouteLabel showMiniPlayer=$miniPlayerVisible showBottomNav=$showBottomNav " +
                "hasNowPlaying=${nowPlayingState.trackId != null} hasMediaItem=${nowPlayingState.title != null} " +
                "hasQueueItem=${uiState.queueSnapshot.currentTrack != null} isPlaying=${nowPlayingState.isPlaying} " +
                "reason=policy"
        )
        if (!miniPlayerVisible && hasPlayableCurrentItem && route != AppRoute.NowPlaying) {
            Log.w(
                "VANTA_CHROME_ANOMALY",
                "miniPlayerHiddenWithActivePlayback=true route=$chromeRouteLabel latched=$latchedPlayback"
            )
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        VantaAppBackground()
        Box(modifier = Modifier.fillMaxSize()) {
        val activeStationId = radioStationId
        val activeMood = mixRoute
        val activeDetail = detailRoute
        when {
            activeStationId != null -> {
                val stationId = activeStationId
                val station = JukeboxCatalog.resolveStation(stationId)
                if (station == null) {
                    LaunchedEffect(stationId) { radioStationId = null }
                } else {
                    val mpVisible = miniPlayerVisible
                    RadioStationDetailScreen(
                        station = station,
                        previewTracks = radioPreviewTracks,
                        isLoadingPreview = radioPreviewLoading,
                        isStarting = uiState.streamingStationLoading && pendingStationStart == stationId,
                        statusMessage = uiState.statusMessage,
                        miniPlayerVisible = mpVisible,
                        bottomNavVisible = showBottomNav,
                        onBack = { radioStationId = null },
                        onStartStation = {
                            pendingStationStart = stationId
                            mainViewModel.startStreamingStation(stationId)
                        },
                        onShuffleStation = {
                            pendingStationStart = stationId
                            mainViewModel.startStreamingStation(stationId, shuffle = true)
                        },
                        onPlayPreviewTrack = { track ->
                            mainViewModel.playTrack(track)
                            nowPlayingViewModel.restore()
                        }
                    )
                }
            }
            activeMood != null -> {
                val mood = activeMood
                val moodTracks = mainViewModel.generateMoodMix(mood)
                Log.d("VANTA_RADIO_ACTION", "action='mood_generated' mood='$mood' count=${moodTracks.size} result=${if (moodTracks.isNotEmpty()) "started" else "unavailable"}")
                if (moodTracks.isEmpty()) {
                    Log.d("VANTA_RADIO_ACTION", "result='unavailable' reason='no_tracks_match_mood' mood='$mood'")
                }
                MixDetailScreen(
                    moodName = mood,
                    tracks = moodTracks,
                    onBack = { mixRoute = null },
                    onPlayTrack = { track ->
                        mainViewModel.playTrack(track)
                        nowPlayingViewModel.restore()
                    },
                    onPlayAll = {
                        mixRoute = null
                        val tracks = mainViewModel.generateMoodMix(mood)
                        if (tracks.isNotEmpty()) {
                            mainViewModel.playQueue(tracks, 0)
                            nowPlayingViewModel.restore()
                        }
                    },
                    onShuffle = {
                        mixRoute = null
                        val tracks = mainViewModel.generateMoodMix(mood).shuffled()
                        if (tracks.isNotEmpty()) {
                            mainViewModel.playQueue(tracks, 0)
                            nowPlayingViewModel.restore()
                        }
                    },
                    onNavigateToArtist = { name, id ->
                        mixRoute = null
                        detailRoute = DetailRoute(DetailRoute.Type.Artist, name, id)
                    }
                )
            }
            activeDetail != null -> {
                val dr = activeDetail
                val mpVisible = miniPlayerVisible
                when (dr.type) {
                    DetailRoute.Type.Artist -> {
                        LaunchedEffect(dr.name) {
                            mainViewModel.loadArtistCatalog(dr.name)
                        }
                        ArtistDetailScreen(
                        artistName = dr.name,
                        artistId = dr.id,
                        tracks = uiState.library.filter { it.track.artist.equals(dr.name, ignoreCase = true) },
                        albums = uiState.libraryAlbums,
                        catalog = uiState.artistCatalog?.takeIf { it.artist.name.equals(dr.name, ignoreCase = true) },
                        catalogLoading = uiState.artistCatalogLoading,
                        searchTracks = uiState.sourceResults.filter { it.artist.equals(dr.name, ignoreCase = true) },
                        onBack = { closeDetail() },
                        onPlayArtistRadio = {
                            Log.d("VANTA_ACTION_TRUTH", "action='artist_radio' artist='${dr.name}'")
                            mainViewModel.playArtistRadio(dr.name)
                            nowPlayingViewModel.restore()
                        },
                        onShuffleTopSongs = {
                            dismissDetails()
                            val artistTracks = uiState.library.filter {
                                it.track.artist.equals(dr.name, ignoreCase = true)
                            }.shuffled()
                            Log.d("VANTA_ACTION_TRUTH", "action='artist_shuffle' artist='${dr.name}' trackCount=${artistTracks.size}")
                            if (artistTracks.isNotEmpty()) {
                                mainViewModel.playQueue(artistTracks, 0)
                                Log.d("VANTA_QUEUE_TRUTH", "queueSource='artist_shuffle' queueSize=${artistTracks.size}")
                                nowPlayingViewModel.restore()
                            }
                        },
                        onNavigateToAlbum = { albumName, artistName ->
                            openChildDetail(DetailRoute(DetailRoute.Type.Album, albumName, null, secondaryName = artistName))
                        },
                        onNavigateToTrackSheet = { track ->
                            activeActionContext = mapTrackToContext(track, uiState.localSongs)
                        },
                        onPlayCatalogTrack = { track ->
                            mainViewModel.playSourceResult(track)
                            nowPlayingViewModel.restore()
                        },
                        miniPlayerVisible = mpVisible,
                        bottomNavVisible = showBottomNav
                    )
                    }
                    DetailRoute.Type.Album -> {
                        LaunchedEffect(dr.name, dr.secondaryName) {
                            mainViewModel.loadAlbumCatalog(dr.name, dr.secondaryName.orEmpty())
                        }
                        AlbumDetailScreen(
                        albumName = dr.name,
                        artistName = dr.secondaryName ?: "",
                        artworkUrl = dr.artworkUrl,
                        releaseYear = dr.releaseYear,
                        genre = dr.genre,
                        explicit = dr.explicit,
                        tracks = uiState.library.filter { it.track.albumName.equals(dr.name, ignoreCase = true) },
                        catalog = uiState.albumCatalog?.takeIf {
                            it.album.title.equals(dr.name, ignoreCase = true) &&
                                (dr.secondaryName.isNullOrBlank() || it.album.artist.equals(dr.secondaryName, ignoreCase = true))
                        },
                        catalogLoading = uiState.albumCatalogLoading,
                        onBack = { closeDetail() },
                        onPlayAlbum = {
                            dismissDetails()
                            val albumTracks = uiState.library.filter {
                                it.track.albumName.equals(dr.name, ignoreCase = true)
                            }.filter {
                                it.isConfirmedPlayable()
                            }
                            Log.d("VANTA_ACTION_TRUTH", "action='album_play' album='${dr.name}' playableTrackCount=${albumTracks.size}")
                            if (albumTracks.isNotEmpty()) {
                                mainViewModel.playQueue(albumTracks, 0)
                                Log.d("VANTA_QUEUE_TRUTH", "queueSource='album' queueSize=${albumTracks.size}")
                                nowPlayingViewModel.restore()
                            }
                        },
                        onFindMatches = {
                            dismissDetails()
                            mainViewModel.searchByTitleArtist(dr.name, dr.secondaryName.orEmpty())
                            route = AppRoute.Search
                        },
                        onShuffleAlbum = {
                            dismissDetails()
                            val albumTracks = uiState.library.filter {
                                it.track.albumName.equals(dr.name, ignoreCase = true)
                            }.filter {
                                it.isConfirmedPlayable()
                            }
                            Log.d("VANTA_ACTION_TRUTH", "action='album_shuffle' album='${dr.name}' playableTrackCount=${albumTracks.size}")
                            if (albumTracks.isNotEmpty()) {
                                mainViewModel.playQueue(albumTracks.shuffled(), 0)
                                Log.d("VANTA_QUEUE_TRUTH", "queueSource='album_shuffled' queueSize=${albumTracks.size}")
                                nowPlayingViewModel.restore()
                            } else {
                                mainViewModel.searchByTitleArtist(dr.name, dr.secondaryName ?: "")
                                route = AppRoute.Search
                            }
                        },
                        onPlayTrack = { track ->
                            mainViewModel.playTrack(track)
                            nowPlayingViewModel.restore()
                        },
                        onPlayCatalogTrack = { track ->
                            mainViewModel.playSourceResult(track)
                            nowPlayingViewModel.restore()
                        },
                        onNavigateToArtist = { artistName, artistId ->
                            openChildDetail(DetailRoute(DetailRoute.Type.Artist, artistName, artistId))
                        },
                        onNavigateToTrackSheet = { track ->
                            activeActionContext = mapTrackToContext(track, uiState.localSongs)
                        },
                        miniPlayerVisible = mpVisible,
                        bottomNavVisible = showBottomNav
                    )
                    }
                }
            }
            else -> {
                val tabOrder = remember { mapOf(
                    AppRoute.Home to 0, AppRoute.Library to 1, AppRoute.Search to 2, AppRoute.AiDj to 3
                ) }
                fun slideDirection(old: AppRoute, new: AppRoute): Int {
                    val oi = tabOrder[old] ?: old.ordinal
                    val ni = tabOrder[new] ?: new.ordinal
                    return ni.compareTo(oi)
                }
                AnimatedContent(targetState = route, transitionSpec = {
                    val dir = slideDirection(initialState, targetState)
                    val fade = tween<Float>(VantaMotion.screenFadeMs, easing = VantaMotion.easeOutLuxury)
                    val exitFade = tween<Float>(VantaMotion.microMs, easing = VantaMotion.easeInOutCozy)
                    if (dir >= 0) {
                        (slideInHorizontally { w -> (w * 0.16f).toInt() } + fadeIn(fade)) togetherWith
                            (slideOutHorizontally { w -> (-w * 0.08f).toInt() } + fadeOut(exitFade))
                    } else {
                        (slideInHorizontally { w -> (-w * 0.16f).toInt() } + fadeIn(fade)) togetherWith
                            (slideOutHorizontally { w -> (w * 0.08f).toInt() } + fadeOut(exitFade))
                    }
                }, label = "screenTransition") { currentRoute ->
                    when (currentRoute) {
                        AppRoute.Home -> HomeScreen(
                    uiState = uiState,
                    nowPlayingState = nowPlayingState,
                    personalizedMixState = personalizedMixState,
                    miniPlayerVisible = miniPlayerVisible,
                    onOpenSettings = { route = AppRoute.Settings },
                    onOpenSearch = { route = AppRoute.Search },
                    onOpenRadio = { route = AppRoute.AiDj },
                    onOpenDrive = { openDrive(AppRoute.Home) },
                    onPlayFirstPlayable = {
                        mainViewModel.playFirstPlayableTrack()
                        nowPlayingViewModel.restore()
                    },
                    onPlayTrack = {
                        mainViewModel.playTrack(it)
                        nowPlayingViewModel.restore()
                    },
                    onPlayPersonalizedMix = { kind ->
                        personalizedMixViewModel.playMix(kind) {
                            nowPlayingViewModel.restore()
                        }
                    },
                    onRefreshPersonalizedMix = personalizedMixViewModel::refreshMix,
                    onQueryChanged = mainViewModel::onQueryChanged,
                    onNavigateToArtist = { name, id -> openRootDetail(DetailRoute(DetailRoute.Type.Artist, name, id)) },
                    onNavigateToAlbum = { albumName, artistName, artworkUrl ->
                        openRootDetail(DetailRoute(DetailRoute.Type.Album, albumName, null, secondaryName = artistName, artworkUrl = artworkUrl))
                    },
                    accountManager = accountManager,
                    vantaSocialManager = vantaSocialManager,

                    onOpenAccount = { route = AppRoute.Account }
                )
                AppRoute.Library -> LibraryScreen(
                    uiState = uiState,
                    miniPlayerVisible = miniPlayerVisible,
                    onOpenSettings = { route = AppRoute.Settings },
                    onOpenImports = { route = AppRoute.Imports },
                    onToggleFavoriteSong = mainViewModel::toggleFavoriteForSong,
                    onPlay = {
                        mainViewModel.playTrack(it)
                        nowPlayingViewModel.restore()
                    },
                    onPlayNext = mainViewModel::playNext,
                    onAddToQueue = mainViewModel::addToQueue,
                    onDownload = mainViewModel::downloadTrack,
                    onNavigateToArtist = { name, id -> openRootDetail(DetailRoute(DetailRoute.Type.Artist, name, id)) },
                    onNavigateToAlbum = { albumName, artistName, artworkUrl ->
                        openRootDetail(DetailRoute(DetailRoute.Type.Album, albumName, null, secondaryName = artistName, artworkUrl = artworkUrl))
                    },
                    onOpenTrackSheet = { track ->
                        activeActionContext = mapTrackToContext(track, uiState.localSongs)
                    }
                )
                AppRoute.Search -> SearchScreen(
                    uiState = uiState,
                    onQueryChanged = mainViewModel::onQueryChanged,
                    onSearch = mainViewModel::search,
                    onSearchQuery = mainViewModel::searchFor,
                    onBrowseCategory = mainViewModel::searchCategory,
                    onPlay = mainViewModel::playTrack,
                    onPlaySourceResult = { track ->
                        mainViewModel.playSourceResult(track)
                        nowPlayingViewModel.restore()
                    },
                    onSaveSourceResult = mainViewModel::saveSourceResultToLibrary,
                    onNavigateToArtist = { name, id -> openRootDetail(DetailRoute(DetailRoute.Type.Artist, name, id)) },
                    onNavigateToAlbum = { albumName, artistName, artworkUrl ->
                        openRootDetail(DetailRoute(DetailRoute.Type.Album, albumName, null, secondaryName = artistName, artworkUrl = artworkUrl))
                    },
                    onNavigateToStation = { stationId -> openRadioStation(stationId) },
                    onStartStation = { stationInput ->
                        openRoute(AppRoute.NowPlaying)
                        nowPlayingViewModel.restore()
                        coroutineScope.launch {
                            try {
                                mainViewModel.startStreamingStation(stationInput)
                            } catch (e: Exception) {
                                Log.e("VANTA_RADIO", "Station start crashed", e)
                                route = AppRoute.Search
                            }
                        }
                    },
                    onQuickPlay = mainViewModel::quickPlayFromUrl,
                    onImportEclipsePlaylist = mainViewModel::importEclipsePlaylist,

                    miniPlayerVisible = miniPlayerVisible,
                    bottomNavVisible = showBottomNav
                )
                AppRoute.Drive -> DriveModeScreen(
                    nowPlayingState = nowPlayingState,
                    lyricsData = lyricsData,
                    pulseInsight = pulseInsight,
                    pulseInsightLoading = pulseInsightLoading,
                    aiDjViewModel = aiDjViewModel,
                    animatedArtworkEnabled = animatedArtworkEnabled,
                    onTogglePlayPause = {
                        aiDjViewModel.togglePlayback()
                    },
                    onPrevious = { aiDjViewModel.previousTrack() },
                    onNext = { aiDjViewModel.nextTrack() },
                    onSeekTo = { mainViewModel.seekTo(it) },
                    onOpenNowPlaying = { openRoute(AppRoute.NowPlaying) },
                    onOpenRadio = { route = AppRoute.AiDj },
                    onExitDrive = { route = driveReturnRoute }
                )
                AppRoute.AiDj -> AiDjScreen(
                    viewModel = aiDjViewModel,
                    onBack = { route = previousMainRoute },
                    onOpenStation = { stationId -> openRadioStation(stationId) },
                    miniPlayerVisible = miniPlayerVisible,
                    bottomNavVisible = showBottomNav
                )
                AppRoute.Account -> AccountScreen(
                    accountManager = accountManager,
                    mainViewModel = mainViewModel,
                    onBack = { route = AppRoute.Home }
                )
                AppRoute.Settings -> SettingsScreen(
                    onBack = { route = previousMainRoute },
                    onOpenAdvanced = { route = AppRoute.AdvancedSettings },
                    onOpenEqualizer = { paramEqReturnRoute = route; route = AppRoute.ParametricEq },
                    miniPlayerVisible = miniPlayerVisible,
                    accountManager = accountManager,
                    onOpenAccount = { route = AppRoute.Account },
                    animatedArtworkEnabled = animatedArtworkEnabled,
                    onAnimatedArtworkEnabledChange = { enabled ->
                        animatedArtworkEnabled = enabled
                        VisualMotionPreferences.setAnimatedArtworkEnabled(ctx, enabled)
                    },
                    onOpenDrive = { openDrive(AppRoute.Settings) },
                    auraMode = auraState.mode,
                    onAuraModeChange = { visualizerViewModel.setMode(it) },
                    auraAudioReactive = auraState.audioReactiveEnabled,
                    onAuraAudioReactiveChange = { visualizerViewModel.setAudioReactive(it) },
                    auraReduceMotionCar = auraState.reduceMotionInCar,
                    onAuraReduceMotionCarChange = { visualizerViewModel.setReduceMotionInCar(it) }
                )
                AppRoute.AdvancedSettings -> AdvancedSettingsScreen(
                    form = uiState.resolverConfig,
                    uiState = uiState,
                    onBack = { route = AppRoute.Settings },
                    miniPlayerVisible = miniPlayerVisible,
                    onTorBoxApiTokenChange = mainViewModel::onTorBoxApiTokenChanged,
                    onRealDebridApiTokenChange = mainViewModel::onRealDebridApiTokenChanged,
                    onLlmProviderChange = mainViewModel::onLlmProviderChanged,
                    onLlmApiKeyChange = mainViewModel::onLlmApiKeyChanged,
                    onPulseVoiceRelayUrlChange = mainViewModel::onPulseVoiceRelayUrlChanged,
                    onPulseVoiceRelayTokenChange = mainViewModel::onPulseVoiceRelayTokenChanged,
                    onPulseVoiceEngineChange = mainViewModel::onPulseVoiceEngineChanged,
                    onTestPulseVoice = mainViewModel::testPulseVoice,
                    onLastFmApiKeyChange = mainViewModel::onLastFmApiKeyChanged,
                    onLastFmApiSecretChange = mainViewModel::onLastFmApiSecretChanged,
                    onLastFmUsernameChange = mainViewModel::onLastFmUsernameChanged,
                    onLastFmSessionKeyChange = mainViewModel::onLastFmSessionKeyChanged,
                    onTestLastFmConnection = mainViewModel::testLastFmConnection,
                    onAppleMusicDeveloperTokenChange = mainViewModel::onAppleMusicDeveloperTokenChanged,
                    onAppleMusicStorefrontChange = mainViewModel::onAppleMusicStorefrontChanged,
                    onTestAppleMusicConnection = mainViewModel::testAppleMusicConnection,
                    onTestTorBoxConnection = mainViewModel::testTorBoxConnection,
                    onTestRealDebridConnection = mainViewModel::testRealDebridConnection,
                    onTestLlmConnection = mainViewModel::testLlmConnection,
                    externalSources = uiState.externalSources,
                    onTestExternalSource = mainViewModel::testExternalSource,
                    onAddExternalSource = mainViewModel::addExternalSource,
                    onRemoveExternalSource = mainViewModel::removeExternalSource,
                    onToggleExternalSource = mainViewModel::toggleExternalSource,
                    onClearFailedSourceCache = mainViewModel::clearFailedSourceCache,
                    onTestAllSourceHealth = mainViewModel::testAllSourceHealth,
                    onTestSourceHealth = mainViewModel::testSourceHealth,
                    onUpdateExternalSourceUrls = mainViewModel::updateExternalSourceUrls,
                    onOpenGenerator = mainViewModel::openGeneratorUrl,
                    onSave = mainViewModel::saveResolverConfiguration
                )
                AppRoute.ParametricEq -> ParametricEqScreen(
                    onBack = { route = paramEqReturnRoute },
                    miniPlayerVisible = miniPlayerVisible,
                )
                AppRoute.NowPlaying -> NowPlayingScreen(
                    nowPlayingState = nowPlayingState,
                    enhancedMetadata = uiState.activeTrackEnhancedMetadata,
                    lyricsData = lyricsData,
                    lyricsTrackId = lyricsTrackId,
                    lyricsLoading = lyricsLoading,
                    translationEnabled = translationEnabled,
                    onToggleTranslation = nowPlayingViewModel::toggleTranslation,
                    onRetryLyrics = nowPlayingViewModel::retryLyrics,
                    queueSnapshot = uiState.queueSnapshot,
                    animatedArtworkEnabled = animatedArtworkEnabled,
                    statusMessage = uiState.statusMessage.orEmpty(),
                    onBack = { popNowPlaying() },
                    onToggleFavorite = {
                        mainViewModel.toggleFavoriteForNowPlaying {
                            nowPlayingViewModel.refreshFavorite()
                        }
                    },
                    onTogglePlayPause = {
                        mainViewModel.togglePlayPause()
                        nowPlayingViewModel.restore()
                    },
                    onPrevious = {
                        mainViewModel.playPreviousFromQueue()
                        nowPlayingViewModel.restore()
                    },
                    onNext = {
                        mainViewModel.playNextFromQueue()
                        nowPlayingViewModel.restore()
                    },
                    onSeekTo = { mainViewModel.seekTo(it) },
                    onPlayNextQueue = {
                        mainViewModel.playNextFromQueue()
                        nowPlayingViewModel.restore()
                    },
                    onMoveQueueItem = { index ->
                        if (index > 0) {
                            mainViewModel.moveUpNext(index, index - 1)
                        }
                    },
                    onRemoveQueueItem = mainViewModel::removeUpNext,
                    onNavigateToArtist = { name, id -> detailRoute = DetailRoute(DetailRoute.Type.Artist, name, id) },
                    onNavigateToAlbum = { albumName, artistName, artworkUrl, releaseYear, genre, explicit ->
                        detailRoute = DetailRoute(DetailRoute.Type.Album, albumName, null, secondaryName = artistName, artworkUrl = artworkUrl, releaseYear = releaseYear, genre = genre, explicit = explicit)
                    },
                    onOpenDj = { openRoute(AppRoute.AiDj) },
                    onOpenEqualizer = { paramEqReturnRoute = route; openRoute(AppRoute.ParametricEq) },
                    onOpenTrackSheet = {
                        val current = uiState.queueSnapshot.currentTrack
                        if (current != null) {
                            activeActionContext = mapTrackToContext(current, uiState.localSongs, isNowPlaying = true)
                        } else {
                            activeActionContext = VantaActionContext.Track(
                                trackId = nowPlayingState.trackId ?: "",
                                title = nowPlayingState.title ?: "Unknown",
                                artist = nowPlayingState.artist ?: "Unknown",
                                album = nowPlayingState.album,
                                artworkUrl = nowPlayingState.artworkUrl,
                                sourceProviderId = null,
                                externalTrackId = null,
                                isPlayable = nowPlayingState.isConfirmedPlayable(),
                                isInLibrary = nowPlayingState.isFavorite,
                                isFavorite = nowPlayingState.isFavorite,
                                hasAlbum = !nowPlayingState.album.isNullOrBlank(),
                                hasArtist = !nowPlayingState.artist.isNullOrBlank(),
                                canStartRadio = true,
                                canQueue = true,
                                canShare = true,
                                qualityLabel = nowPlayingState.qualityInfo?.bestQualityLabel(),
                                explicit = nowPlayingState.explicit ?: false,
                                isNowPlaying = true
                            )
                        }
                    },
                    onOpenQueueTrackSheet = { track ->
                        activeActionContext = mapTrackToContext(track, uiState.localSongs)
                    },
                    auraState = auraState,
                    audioFrame = audioFrame,
                    visualizerViewModel = visualizerViewModel
                )
                AppRoute.Imports -> ImportsScreen(
                    imports = uiState.importBatches,
                    isScanningDeviceLibrary = uiState.isScanningDeviceLibrary,
                    deviceScanProgress = uiState.deviceScanProgress,
                    statusMessage = uiState.statusMessage,
                    onBack = { route = AppRoute.Library },
                    onNewImport = {
                        pendingImportName = "Imported Playlist"
                        pendingImportText = ""
                        route = AppRoute.ImportText
                    },
                    onScanDevice = {
                        mainViewModel.importLocalMedia()
                    },
                    onImportFile = { uri ->
                        mainViewModel.importSingleLocalFile(uri)
                    },
                    onImportTracklistFile = { importName, text ->
                        pendingImportName = importName
                        pendingImportText = text
                        route = AppRoute.ImportPreview
                    }
                )
                AppRoute.ImportText -> ImportTextScreen(
                    onBack = { route = AppRoute.Imports },
                    initialImportName = pendingImportName,
                    initialPastedText = pendingImportText,
                    onTextChanged = { importName, pastedText ->
                        pendingImportName = importName
                        pendingImportText = pastedText
                    },
                    onPreview = { importName, pastedText ->
                        pendingImportName = importName
                        pendingImportText = pastedText
                        route = AppRoute.ImportPreview
                    }
                )
                AppRoute.ImportPreview -> ImportPreviewScreen(
                    pastedText = pendingImportText,
                    onBack = { route = AppRoute.ImportText },
                    onContinueToMatch = {
                        mainViewModel.importPastedText(pendingImportName, pendingImportText)
                        route = AppRoute.ImportReview
                    }
                )
                AppRoute.ImportReview -> ImportReviewScreen(
                    tracks = uiState.activeImportedTracks,
                    statusMessage = uiState.statusMessage,
                    onBack = { route = AppRoute.Imports },
                    onRemoveTrack = mainViewModel::removeImportedTrack,
                    onSaveMatched = mainViewModel::saveMatchedImportTracks,
                    onSaveAllMetadata = mainViewModel::saveAllImportMetadata,
                    onResolveAgain = mainViewModel::resolveCurrentImportAgain
                )
                else -> {}
                    }
                }
            }
        }
        }

        if (miniPlayerVisible || showBottomNav) {
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .zIndex(30f)
                    .fillMaxWidth()
                    .padding(
                        start = VantaChrome.overlayHorizontal,
                        end = VantaChrome.overlayHorizontal,
                        bottom = 0.dp
                    )
                    .safeDrawingPadding(),
                verticalArrangement = Arrangement.spacedBy(VantaChrome.overlayGap)
            ) {
                if (miniPlayerVisible) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        val djMoment = aiDjState.liveCommentary?.takeIf { it.isNotBlank() }
                        if (djMoment != null && route != AppRoute.AiDj) {
                            DjMomentCard(
                                message = djMoment,
                                accentLabel = aiDjState.companionMode.label.uppercase(),
                                onOpenDj = { openRoute(AppRoute.AiDj) },
                                onCycleMode = { aiDjViewModel.toggleDjVoice() }
                            )
                        }
                        MiniPlayer(
                            nowPlayingState = nowPlayingState,
                            queueSnapshot = uiState.queueSnapshot,
                            pulseHint = pulseInsight?.takeIf { !pulseInsightLoading },
                            onOpen = {
                                Log.d("VANTA_UI_ACTION", "control='miniplayer_open'")
                                openRoute(AppRoute.NowPlaying)
                                nowPlayingViewModel.restore()
                            },
                            onTogglePlayPause = {
                                Log.d("VANTA_UI_ACTION", "control='miniplayer_playpause'")
                                mainViewModel.togglePlayPause()
                                nowPlayingViewModel.restore()
                            },
                            onNext = {
                                Log.d("VANTA_UI_ACTION", "control='miniplayer_next'")
                                mainViewModel.playNextFromQueue()
                                nowPlayingViewModel.restore()
                            },
                            onPrevious = {
                                Log.d("VANTA_UI_ACTION", "control='miniplayer_previous'")
                                mainViewModel.playPreviousFromQueue()
                                nowPlayingViewModel.restore()
                            },
                            onToggleFavorite = {
                                Log.d("VANTA_UI_ACTION", "control='miniplayer_favorite'")
                                mainViewModel.toggleFavoriteForNowPlaying()
                            },
                            animatedArtworkEnabled = animatedArtworkEnabled,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
                if (showBottomNav) {
                    BottomNavBar(
                        currentRoute = route,
                        onRouteSelected = { route = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .zIndex(31f)
                    )
                }
            }
        }
    }

    activeActionContext?.let { context ->
        VantaActionSheet(
            context = context,
            onDismiss = { activeActionContext = null },
            onActionSelected = { action ->
                mainViewModel.handleAction(action, context)
                when (action) {
                    VantaActionSheetAction.VIEW_ALBUM -> {
                        val albumName = when (context) {
                            is VantaActionContext.Track -> context.album
                            is VantaActionContext.SearchResult -> context.album
                            else -> null
                        }
                        val artistName = when (context) {
                            is VantaActionContext.Track -> context.artist
                            is VantaActionContext.SearchResult -> context.artist
                            else -> null
                        }
                        val artworkUrl = when (context) {
                            is VantaActionContext.Track -> context.artworkUrl
                            is VantaActionContext.SearchResult -> context.artworkUrl
                            else -> null
                        }
                        if (!albumName.isNullOrBlank()) {
                            detailRoute = DetailRoute(
                                type = DetailRoute.Type.Album,
                                name = albumName,
                                id = null,
                                secondaryName = artistName,
                                artworkUrl = artworkUrl
                            )
                        }
                    }
                    VantaActionSheetAction.VIEW_ARTIST -> {
                        val artistName = when (context) {
                            is VantaActionContext.Track -> context.artist
                            is VantaActionContext.SearchResult -> context.artist
                            else -> null
                        }
                        if (!artistName.isNullOrBlank()) {
                            detailRoute = DetailRoute(
                                type = DetailRoute.Type.Artist,
                                name = artistName,
                                id = null
                            )
                        }
                    }
                    VantaActionSheetAction.ADD_TO_PLAYLIST -> {
                        mainViewModel.loadPlaylists { playlistsForSheet = it }
                        showAddToPlaylistSheet = true
                    }
                    VantaActionSheetAction.SLEEP_TIMER -> {
                        showSleepTimerSheet = true
                    }
                    VantaActionSheetAction.SHARE_TRACK,
                    VantaActionSheetAction.SHARE_ALBUM,
                    VantaActionSheetAction.SHARE_ARTIST,
                    VantaActionSheetAction.SHARE_PLAYLIST -> {
                        val text = when (context) {
                            is VantaActionContext.Track -> "Listening to ${context.title} by ${context.artist} on VANTA"
                            is VantaActionContext.Album -> "Listening to the album ${context.albumName} by ${context.artistName} on VANTA"
                            is VantaActionContext.Artist -> "Listening to ${context.artistName} on VANTA"
                            is VantaActionContext.Playlist -> "Listening to playlist ${context.name} on VANTA"
                            else -> "Listening to music on VANTA"
                        }
                        try {
                            val intent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, text)
                            }
                            ctx.startActivity(Intent.createChooser(intent, "Share").apply {
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            })
                        } catch (e: Exception) {
                            Log.e("VANTA_SHARE", "share_failed error='${e.message}'")
                            mainViewModel.setStatusMessage("Couldn't open share sheet.")
                        }
                    }
                    else -> {}
                }
            }
        )
    }

    if (showSleepTimerSheet) {
        SleepTimerSheet(
            activeMinutes = sleepTimerActiveMinutes,
            onSelectTimer = { minutes ->
                mainViewModel.startSleepTimer(minutes)
                sleepTimerActiveMinutes = minutes
                showSleepTimerSheet = false
            },
            onCancelTimer = {
                mainViewModel.cancelSleepTimer()
                sleepTimerActiveMinutes = null
                showSleepTimerSheet = false
            },
            onDismiss = { showSleepTimerSheet = false }
        )
    }

    if (showAddToPlaylistSheet) {
        AddToPlaylistSheet(
            playlists = playlistsForSheet,
            onCreatePlaylist = { name ->
                mainViewModel.createPlaylist(name)
                mainViewModel.loadPlaylists { playlistsForSheet = it }
            },
            onAddToPlaylist = { playlistId ->
                mainViewModel.addNowPlayingToPlaylist(playlistId)
                showAddToPlaylistSheet = false
            },
            onDismiss = { showAddToPlaylistSheet = false }
        )
    }

    if (showSourceDetailsSheet) {
        SourceDetailsSheet(
            nowPlayingState = nowPlayingState,
            enhancedMetadata = uiState.activeTrackEnhancedMetadata,
            onDismiss = { showSourceDetailsSheet = false }
        )
    }
}
 
fun mapTrackToContext(
    track: UnifiedTrackWithSources,
    localSongs: List<com.audiophile.musicplayer.data.local.entities.LocalSongEntity>,
    isNowPlaying: Boolean = false
): VantaActionContext.Track {
    val title = track.track.title
    val artist = track.track.artist
    val inLib = localSongs.any { it.title.equals(title, ignoreCase = true) && it.artist.equals(artist, ignoreCase = true) }
    val isFav = localSongs.any { it.title.equals(title, ignoreCase = true) && it.artist.equals(artist, ignoreCase = true) && it.isFavorite }
    val bestSource = track.sources.maxByOrNull { it.bitrate }
    val sourceStatus = track.sourceValidityStatus()
    val canEnterPlayback = sourceStatus.canEnterPlaybackFlow()
    val qualityLabel = bestSource?.let {
        VantaQualityInfo.fromTrackSource(it, sourceStatus)?.bestQualityLabel()
    }
    return VantaActionContext.Track(
        trackId = track.track.trackId.toString(),
        title = title,
        artist = artist,
        album = track.track.albumName,
        artworkUrl = track.track.coverArtUrl,
        sourceProviderId = bestSource?.externalProviderId,
        externalTrackId = bestSource?.externalTrackId,
        isPlayable = canEnterPlayback,
        isInLibrary = inLib,
        isFavorite = isFav,
        hasAlbum = !track.track.albumName.isNullOrBlank(),
        hasArtist = !track.track.artist.isNullOrBlank(),
        canStartRadio = true,
        canQueue = canEnterPlayback,
        canShare = true,
        qualityLabel = qualityLabel,
        explicit = track.track.explicit ?: false,
        isNowPlaying = isNowPlaying
    )
}



