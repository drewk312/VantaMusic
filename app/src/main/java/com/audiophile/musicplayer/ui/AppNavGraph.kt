package com.audiophile.musicplayer.ui

import androidx.activity.compose.BackHandler
import com.audiophile.musicplayer.common.AcceptanceTruth
import com.audiophile.musicplayer.social.VantaSocialManager
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.asPaddingValues
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
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.zIndex
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntOffset
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
import com.audiophile.musicplayer.data.dj.AiDjUiState
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
import com.audiophile.musicplayer.ui.visualizer.VantaVisualizerPreferences
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import com.audiophile.musicplayer.data.display.VantaQualityInfo
import android.content.Intent
import android.util.Log
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import com.audiophile.musicplayer.data.source.sourceValidityStatus
import com.audiophile.musicplayer.data.source.canEnterPlaybackFlow
import com.audiophile.musicplayer.data.source.isConfirmedPlayable
import com.audiophile.musicplayer.data.source.toGatewayHomePlaylist
import com.audiophile.musicplayer.data.canonical.CanonicalTrack

enum class AppRoute {
    Home, Discover, Library, Search, Drive, AiDj,
    Settings, NowPlaying,
    Account,
    Imports, ImportText, ImportPreview, ImportReview,
    AdvancedSettings,
    ParametricEq,
    RadioStation,
    PlaylistDetail, HomePlaylistDetail,
    FriendProfile,
    Wrapped
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
    save = { list -> list.flatMap { route -> encodeDetailRoute(route) } },
    restore = { value ->
        value.chunked(8).mapNotNull { chunk -> decodeDetailRoute(chunk) }
    }
)

@Composable
fun AppNavGraph(
    mainViewModel: MainViewModel,
    searchViewModel: SearchViewModel,
    nowPlayingViewModel: NowPlayingViewModel,
    container: com.audiophile.musicplayer.AppContainer,

    sharedImportPayload: SharedImportPayload? = null,
    onSharedImportConsumed: () -> Unit = {}
) {
    Log.i("VANTA_STARTUP", "nav_graph_enter")
    val dismissKeyboard = rememberKeyboardDismissal()
    val feedbackHost = remember { androidx.compose.material3.SnackbarHostState() }
    var route by rememberSaveable { mutableStateOf(AppRoute.Home) }
    // Keep dependencies alive after first use: AnimatedContent still composes outgoing routes.
    val visitedRoutes = remember { mutableSetOf<AppRoute>() }
    visitedRoutes.add(route)
    var accountServicesActivated by rememberSaveable { mutableStateOf(false) }
    val accountServicesRequired = accountServicesActivated ||
        visitedRoutes.any { it == AppRoute.Account || it == AppRoute.Settings || it == AppRoute.FriendProfile }
    val accountManager = if (accountServicesRequired) container.accountManager else null
    val vantaSocialManager = if (accountServicesRequired) container.vantaSocialManager else null

    // Construct expensive graphs lazily on first use, then retain them for
    // outgoing route animations and subsequent visits. Personalized Home
    // mixes remain active; MainActivity's protected phase 4 covers their setup.
    val aiDjRequired = visitedRoutes.any { it == AppRoute.AiDj || it == AppRoute.Drive }
    val aiDjViewModel: AiDjViewModel? = if (aiDjRequired) hiltViewModel() else null
    val visualizerRequired = visitedRoutes.any { it == AppRoute.NowPlaying || it == AppRoute.Drive || it == AppRoute.Settings }
    val visualizerViewModel: VantaVisualizerViewModel? = if (visualizerRequired) viewModel() else null
    val personalizedMixViewModel: PersonalizedMixViewModel = hiltViewModel()
    val dailyDiscoveryViewModel: DailyDiscoveryViewModel = hiltViewModel()

    val uiState by mainViewModel.uiState.collectAsState()
    val radioDiscoveryMode by mainViewModel.radioDiscoveryMode.collectAsState()
    val searchUiState by searchViewModel.uiState.collectAsState()
    val personalizedMixState by personalizedMixViewModel.uiState.collectAsState()
    val personalizedMixDetailState by personalizedMixViewModel.detailState.collectAsState()
    val nowPlayingState by nowPlayingViewModel.chromeState.collectAsState()
    val rawLyricsData by nowPlayingViewModel.lyrics.collectAsState()
    val lyricsTrackId by nowPlayingViewModel.lyricsTrackId.collectAsState()
    val lyricsLoading by nowPlayingViewModel.lyricsLoading.collectAsState()
    val lyricsIdentity by nowPlayingViewModel.lyricsIdentity.collectAsState()
    val pulseInsight by nowPlayingViewModel.pulseInsight.collectAsState()
    val pulseInsightLoading by nowPlayingViewModel.pulseInsightLoading.collectAsState()
    val pulseDeepInsight by nowPlayingViewModel.pulseDeepInsight.collectAsState()
    val pulseDeepLoading by nowPlayingViewModel.pulseDeepLoading.collectAsState()
    val translationEnabled by nowPlayingViewModel.translationEnabled.collectAsState()
    val aiDjState = aiDjViewModel?.state?.collectAsState()?.value ?: AiDjUiState()
    // NowPlayingViewModel clears lyrics on every track change and rejects stale
    // async responses using the requested track id. Re-validating the cache key
    // here rejected legitimate pre-ISRC cache entries and ran on every position
    // tick, producing log spam and hiding valid lyrics.
    val lyricsData = rawLyricsData
    var previousMainRoute by rememberSaveable { mutableStateOf(AppRoute.Home) }
    var paramEqReturnRoute by rememberSaveable { mutableStateOf(AppRoute.Home) }
    var playlistReturnRoute by rememberSaveable { mutableStateOf(AppRoute.Home) }
    var wrappedYear by rememberSaveable { mutableStateOf(java.time.Year.now().value) }
    var nowPlayingReturnDetailRoute by rememberSaveable(stateSaver = DetailRouteSaver) { mutableStateOf<DetailRoute?>(null) }
    var nowPlayingReturnDetailHistory by rememberSaveable(stateSaver = DetailRouteListSaver) { mutableStateOf<List<DetailRoute>>(emptyList()) }
    var pendingImportName by rememberSaveable { mutableStateOf("Imported Playlist") }
    var pendingImportPlaylistId by rememberSaveable { mutableStateOf<Long?>(null) }
    var pendingImportText by rememberSaveable { mutableStateOf("") }
    var detailRoute by rememberSaveable(stateSaver = DetailRouteSaver) { mutableStateOf<DetailRoute?>(null) }
    var detailHistory by rememberSaveable(stateSaver = DetailRouteListSaver) { mutableStateOf<List<DetailRoute>>(emptyList()) }
    var mixRoute by rememberSaveable { mutableStateOf<String?>(null) }
    var radioStationId by rememberSaveable { mutableStateOf<String?>(null) }
    var friendProfileId by rememberSaveable { mutableStateOf<String?>(null) }
    var radioPreviewTracks by remember { mutableStateOf<List<UnifiedTrackWithSources>>(emptyList()) }
    var radioPreviewLoading by remember { mutableStateOf(false) }
    var pendingStationStart by remember { mutableStateOf<String?>(null) }
    var activeActionContext by remember { mutableStateOf<VantaActionContext?>(null) }
    var showSleepTimerSheet by rememberSaveable { mutableStateOf(false) }
    var showAddToPlaylistSheet by rememberSaveable { mutableStateOf(false) }
    var showSourceDetailsSheet by rememberSaveable { mutableStateOf(false) }
    var fileInfoTarget by remember { mutableStateOf<FileInfoTarget?>(null) }
    var sleepTimerActiveMinutes by rememberSaveable { mutableStateOf<Int?>(null) }
    var playlistsForSheet by remember { mutableStateOf<List<com.audiophile.musicplayer.data.local.entities.PlaylistEntity>>(emptyList()) }
    val ctx = LocalContext.current
    LaunchedEffect(Unit) {
        if (com.audiophile.musicplayer.StartupSafeguard.shouldResetNavigationState(ctx)) {
            detailRoute = null
            detailHistory = emptyList()
            route = AppRoute.Home
            com.audiophile.musicplayer.StartupSafeguard.acknowledgeNavigationRecovery(ctx)
        }
    }
    LaunchedEffect(route, detailRoute, radioStationId, mixRoute, activeActionContext, showAddToPlaylistSheet, showSleepTimerSheet) {
        dismissKeyboard()
    }
    val coroutineScope = rememberCoroutineScope()
    var animatedArtworkEnabled by remember { mutableStateOf(VisualMotionPreferences.isAnimatedArtworkEnabled(ctx)) }
    val visualizerPreferences = visualizerViewModel?.preferences?.collectAsState()?.value
        ?: VantaVisualizerPreferences()

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
        if (route == AppRoute.Account || route == AppRoute.Settings || route == AppRoute.FriendProfile) {
            accountServicesActivated = true
        }
        if (route == AppRoute.NowPlaying) {
            nowPlayingViewModel.activateDetailEnrichment()
        }
        if (route == AppRoute.Library) {
            mainViewModel.ensureLibraryLoaded()
        }
    }

    LaunchedEffect(route, nowPlayingState.trackId, nowPlayingState.isPlaying) {
        if (route != AppRoute.NowPlaying) {
            visualizerViewModel?.releaseAnalyzer()
            return@LaunchedEffect
        }
        repeat(60) {
            val sessionId = PlaybackService.audioSessionId
            if (sessionId > 0) {
                visualizerViewModel?.attachToSession(sessionId)
                return@LaunchedEffect
            }
            kotlinx.coroutines.delay(250)
        }
    }

    var lastRoute by rememberSaveable { mutableStateOf<AppRoute?>(null) }
    LaunchedEffect(route) {
        if (route == AppRoute.AiDj && lastRoute != null && lastRoute != AppRoute.AiDj) {
            aiDjViewModel?.prepareFreshEntry()
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

    fun selectRootTab(next: AppRoute) {
        if (next !in ChromeVisibilityPolicy.TAB_ROUTES) {
            route = next
            return
        }
        if (route in ChromeVisibilityPolicy.TAB_ROUTES && route != next) {
            previousMainRoute = route
        }
        activeActionContext = null
        detailRoute = null
        detailHistory = emptyList()
        radioStationId = null
        mixRoute = null
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
        // Both station entry points report a successful start. Keep this
        // tolerant of their wording so playback never succeeds invisibly.
        if (uiState.statusMessage?.startsWith("playing", ignoreCase = true) == true ||
            uiState.statusMessage?.startsWith("now playing", ignoreCase = true) == true
        ) {
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
    BackHandler(enabled = fileInfoTarget != null) {
        fileInfoTarget = null
    }
    val transientBackActive = activeActionContext != null ||
        showSleepTimerSheet ||
        showAddToPlaylistSheet ||
        showSourceDetailsSheet ||
        fileInfoTarget != null
    BackHandler(enabled = !transientBackActive && radioStationId != null) {
        radioStationId = null
    }
    BackHandler(enabled = !transientBackActive && radioStationId == null && mixRoute != null) {
        mixRoute = null
    }
    BackHandler(enabled = !transientBackActive && radioStationId == null && mixRoute == null && personalizedMixDetailState.kind != null) {
        personalizedMixViewModel.closeMixDetail()
    }
    BackHandler(enabled = !transientBackActive && radioStationId == null && mixRoute == null && detailRoute != null) {
        closeDetail()
    }
    val routeBackAvailable = !transientBackActive &&
        mixRoute == null &&
        radioStationId == null &&
        detailRoute == null
    BackHandler(enabled = routeBackAvailable && route == AppRoute.NowPlaying) {
        popNowPlaying()
    }
    BackHandler(
        enabled = routeBackAvailable &&
            route in setOf(AppRoute.Discover, AppRoute.Library, AppRoute.Search, AppRoute.AiDj)
    ) {
        route = AppRoute.Home
    }
    BackHandler(enabled = routeBackAvailable && (route == AppRoute.Settings || route == AppRoute.Account || route == AppRoute.Imports)) {
        route = AppRoute.Home
    }
    BackHandler(enabled = routeBackAvailable && (route == AppRoute.AdvancedSettings || route == AppRoute.ParametricEq)) {
        route = if (route == AppRoute.ParametricEq) paramEqReturnRoute else AppRoute.Settings
    }
    BackHandler(enabled = routeBackAvailable && route == AppRoute.ImportText) {
        route = AppRoute.Imports
    }
    BackHandler(enabled = routeBackAvailable && route == AppRoute.ImportPreview) {
        route = AppRoute.ImportText
    }
    BackHandler(enabled = routeBackAvailable && route == AppRoute.ImportReview) {
        route = AppRoute.Imports
    }

    BackHandler(enabled = routeBackAvailable && route == AppRoute.HomePlaylistDetail) {
        route = playlistReturnRoute
    }
    BackHandler(enabled = routeBackAvailable && route == AppRoute.PlaylistDetail) {
        route = AppRoute.Library
    }
    BackHandler(enabled = routeBackAvailable && route == AppRoute.Wrapped) {
        route = AppRoute.Library
    }
    BackHandler(enabled = routeBackAvailable && route == AppRoute.FriendProfile) {
        friendProfileId = null
        route = AppRoute.Home
    }
    BackHandler(enabled = routeBackAvailable && route == AppRoute.Drive) {
        route = driveReturnRoute
    }

    var trackSheetDismiss by remember { mutableStateOf(false) }
    val hasPlayableCurrentItem = ChromeVisibilityPolicy.hasPlayableCurrentItem(
        nowPlaying = nowPlayingState,
        queueSnapshot = uiState.queueSnapshot,
        activeTrackId = uiState.activeTrackId
    )
    val latchedPlayback = rememberPlaybackChromeLatch(hasPlayableCurrentItem)
    // An active Pulse session owns its transport surface. Everywhere else,
    // including the Radio hub, the shared mini player remains available.
    val miniPlayerVisible = ChromeVisibilityPolicy.shouldShowMiniPlayer(route, latchedPlayback, isKeyboardVisible) &&
        !(route == AppRoute.AiDj && aiDjState.session.isStarted)
    val sharedChromeContentInset = if (miniPlayerVisible || showBottomNav) {
        appBottomContentPadding(
            isMiniPlayerVisible = miniPlayerVisible,
            isBottomNavVisible = showBottomNav
        )
    } else {
        0.dp
    }
    val chromeRouteLabel = ChromeVisibilityPolicy.routeLabel(
        route = route,
        hasDetailOverlay = detailRoute != null,
        hasRadioStationOverlay = radioStationId != null
    )
    LaunchedEffect(uiState.statusMessage) {
        val message = uiState.statusMessage?.trim().orEmpty()
        val actionable = listOf("added", "saved", "deleted", "could not", "couldn't", "unable", "failed", "no playable", "imported", "preparing", "error playing")
            .any { message.startsWith(it, ignoreCase = true) }
        if (actionable && route != AppRoute.NowPlaying) {
            feedbackHost.showSnackbar(message, withDismissAction = true, duration = androidx.compose.material3.SnackbarDuration.Short)
        }
    }

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
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clipToBounds()
        ) {
        val activeStationId = radioStationId
        val activeMood = mixRoute
        val activeMixDetailKind = personalizedMixDetailState.kind
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
                            mainViewModel.startJukeboxStation(stationId)
                        },
                        onShuffleStation = {
                            pendingStationStart = stationId
                            mainViewModel.startJukeboxStation(stationId, shuffle = true)
                        },
                        onPlayPreviewTrack = { track ->
                            mainViewModel.playTrack(track)
                            nowPlayingViewModel.restore()
                        },
                        selectedDiscoveryMode = radioDiscoveryMode,
                        onDiscoveryModeSelected = mainViewModel::setRadioDiscoveryMode
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
            activeMixDetailKind != null -> {
                val detail = personalizedMixDetailState
                MixDetailScreen(
                    moodName = detail.title,
                    tracks = detail.tracks,
                    onBack = { personalizedMixViewModel.closeMixDetail() },
                    onPlayTrack = { track ->
                        mainViewModel.playTrack(track)
                        nowPlayingViewModel.restore()
                    },
                    onPlayAll = {
                        personalizedMixViewModel.closeMixDetail()
                        if (detail.tracks.isNotEmpty()) {
                            mainViewModel.playQueue(detail.tracks, 0)
                            nowPlayingViewModel.restore()
                        }
                    },
                    onShuffle = {
                        personalizedMixViewModel.closeMixDetail()
                        val shuffled = detail.tracks.shuffled()
                        if (shuffled.isNotEmpty()) {
                            mainViewModel.playQueue(shuffled, 0)
                            nowPlayingViewModel.restore()
                        }
                    },
                    onNavigateToArtist = { name, id ->
                        personalizedMixViewModel.closeMixDetail()
                        detailRoute = DetailRoute(DetailRoute.Type.Artist, name, id)
                    }
                )
            }
            activeDetail != null -> {
                val dr = activeDetail
                val mpVisible = miniPlayerVisible
                when (dr.type) {
                    DetailRoute.Type.Artist -> {
                        LaunchedEffect(dr.name, dr.id) {
                            mainViewModel.loadArtistCatalog(
                                artistName = dr.name,
                                canonicalArtistId = dr.id?.toLongOrNull()
                            )
                        }
                        ArtistDetailScreen(
                        artistName = dr.name,
                        artistId = dr.id,
                        tracks = uiState.library.filter { dr.name.equals(it.track.artist, ignoreCase = true) },
                        albums = uiState.libraryAlbums,
                        catalog = uiState.artistCatalog?.takeIf { it.artist.name.equals(dr.name, ignoreCase = true) },
                        catalogLoading = uiState.artistCatalogLoading,
                        searchTracks = searchUiState.songs.filter { it.artist.equals(dr.name, ignoreCase = true) },
                        onBack = { closeDetail() },
                        onPlayArtistRadio = {
                            Log.d("VANTA_ACTION_TRUTH", "action='artist_radio' artist='${dr.name}'")
                            mainViewModel.playArtistRadio(dr.name)
                            nowPlayingViewModel.restore()
                        },
                        onPlayArtist = {
                            val songs = uiState.artistCatalog?.tracks.orEmpty().ifEmpty {
                                searchUiState.songs.filter { dr.name.equals(it.artist, ignoreCase = true) }
                            }
                            if (songs.isNotEmpty()) mainViewModel.playCatalogQueue(songs)
                            else mainViewModel.playArtistRadio(dr.name)
                        },
                        onShuffleTopSongs = {
                            val songs = uiState.artistCatalog?.tracks.orEmpty().ifEmpty {
                                searchUiState.songs.filter { dr.name.equals(it.artist, ignoreCase = true) }
                            }
                            if (songs.isNotEmpty()) mainViewModel.playCatalogQueue(songs, shuffle = true)
                            else {
                                val local = uiState.library.filter { dr.name.equals(it.track.artist, ignoreCase = true) }
                                    .filter { it.sourceValidityStatus().canEnterPlaybackFlow() }.shuffled()
                                if (local.isNotEmpty()) mainViewModel.playQueue(local, 0)
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
                        LaunchedEffect(dr.name, dr.secondaryName, dr.id) {
                            mainViewModel.loadAlbumCatalog(
                                albumName = dr.name,
                                artistName = dr.secondaryName.orEmpty(),
                                canonicalAlbumId = dr.id?.toLongOrNull()
                            )
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
                            val catalogTracks = uiState.albumCatalog?.tracks.orEmpty()
                            if (catalogTracks.isNotEmpty()) {
                                mainViewModel.playCatalogQueue(catalogTracks)
                            } else {
                                val albumTracks = uiState.library.filter {
                                    it.track.albumName.equals(dr.name, ignoreCase = true) &&
                                        (dr.secondaryName.isNullOrBlank() || it.track.artist.equals(dr.secondaryName, ignoreCase = true))
                                }.filter { it.sourceValidityStatus().canEnterPlaybackFlow() }
                                if (albumTracks.isNotEmpty()) mainViewModel.playQueue(albumTracks, 0)
                                else {
                                    dismissDetails()
                                    searchViewModel.searchByTitleArtist(dr.name, dr.secondaryName.orEmpty())
                                    route = AppRoute.Search
                                }
                            }
                        },
                        onFindMatches = {
                            dismissDetails()
                            searchViewModel.searchByTitleArtist(dr.name, dr.secondaryName.orEmpty())
                            route = AppRoute.Search
                        },
                        onShuffleAlbum = {
                            val catalogTracks = uiState.albumCatalog?.tracks.orEmpty()
                            if (catalogTracks.isNotEmpty()) mainViewModel.playCatalogQueue(catalogTracks, shuffle = true)
                            else {
                                val albumTracks = uiState.library.filter {
                                    it.track.albumName.equals(dr.name, ignoreCase = true) &&
                                        (dr.secondaryName.isNullOrBlank() || it.track.artist.equals(dr.secondaryName, ignoreCase = true))
                                }.filter { it.sourceValidityStatus().canEnterPlaybackFlow() }
                                if (albumTracks.isNotEmpty()) mainViewModel.playQueue(albumTracks.shuffled(), 0)
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
                // A route owns one full-size surface. Cross-route animation used
                // target chrome/insets on the outgoing screen, moving controls mid-transition.
                androidx.compose.runtime.key(route) {
                    val currentRoute = route
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
                    onToggleNowPlaying = {
                        mainViewModel.togglePlayPause()
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
                    onOpenPersonalizedMix = { kind ->
                        personalizedMixViewModel.openMixDetail(kind)
                    },
                    onRefreshPersonalizedMix = personalizedMixViewModel::refreshMix,
                    onLoadHomeFeed = { mainViewModel.loadHomeFeed() },
                    onLoadCatalogForYou = { mainViewModel.loadCatalogForYou() },
                    onRefreshHome = {
                        mainViewModel.loadHomeFeed(force = true)
                        mainViewModel.loadCatalogForYou(refresh = true)
                    },
                    onPlayHomeTrack = { mainViewModel.playHomeTrack(it) },
                    onOpenHomePlaylist = { playlist ->
                        playlistReturnRoute = AppRoute.Home
                        mainViewModel.openHomePlaylist(playlist)
                        route = AppRoute.HomePlaylistDetail
                    },
                    onNavigateToArtist = { name, id -> openRootDetail(DetailRoute(DetailRoute.Type.Artist, name, id)) },
                    onNavigateToAlbum = { albumName, artistName, artworkUrl ->
                        openRootDetail(DetailRoute(DetailRoute.Type.Album, albumName, null, secondaryName = artistName, artworkUrl = artworkUrl))
                    },
                    accountManager = accountManager,
                    vantaSocialManager = vantaSocialManager,

                    onOpenAccount = { route = AppRoute.Account },
                    onPlayFriendTrack = { title, artist ->
                        mainViewModel.playSourceResult(
                            com.audiophile.musicplayer.data.canonical.CanonicalTrack(
                                title = title,
                                artist = artist
                            )
                        )
                    },
                    onOpenFriendProfile = { friendId ->
                        friendProfileId = friendId
                        route = AppRoute.FriendProfile
                    }
                )
                AppRoute.Discover -> NewScreen(
                    dailyDiscovery = {
                        DailyDiscoveryPanel(dailyDiscoveryViewModel,
                            onPlay = { mainViewModel.playSourceResult(it); nowPlayingViewModel.restore() },
                            onSave = mainViewModel::saveSourceResultToLibrary)
                    },
                    localPlaylists = uiState.localPlaylists,
                    localSongs = uiState.localSongs,
                    editorialReleases = uiState.editorialNewReleases,
                    editorialReleasesLoading = uiState.editorialNewReleasesLoading,
                    editorialReleasesStatus = uiState.editorialNewReleasesStatus,
                    editorialReleasesError = uiState.editorialNewReleasesError,
                    editorialReleasesUpdatedAtMs = uiState.editorialNewReleasesUpdatedAtMs,
                    onLoadEditorialReleases = { mainViewModel.loadEditorialNewReleases(force = true) },
                    onOpenPlaylist = { playlistId ->
                        mainViewModel.loadPlaylistTracks(playlistId)
                        route = AppRoute.PlaylistDetail
                    },
                    onOpenImportFromLink = { route = AppRoute.ImportText },
                    onNavigateToAlbum = { albumName, artistName, artworkUrl ->
                        openRootDetail(DetailRoute(DetailRoute.Type.Album, albumName, null, secondaryName = artistName, artworkUrl = artworkUrl))
                    },
                    onPlayTodaysDrop = {
                        mainViewModel.playTodaysDrop()
                        nowPlayingViewModel.restore()
                    },
                    onShuffleTodaysDrop = {
                        mainViewModel.playTodaysDrop(shuffle = true)
                        nowPlayingViewModel.restore()
                    },
                    onSaveTodaysDrop = {
                        mainViewModel.saveTodaysDropToLibrary()
                    },
                    onPlayRelease = { release ->
                        mainViewModel.playSourceResult(
                            com.audiophile.musicplayer.data.canonical.CanonicalMapper.mapToCanonicalTrack(release)
                        )
                        nowPlayingViewModel.restore()
                    },
                    onPlayReleaseAtIndex = { releases, index ->
                        mainViewModel.playReleases(releases, index)
                        nowPlayingViewModel.restore()
                    },
                    miniPlayerVisible = miniPlayerVisible
                )
                AppRoute.Library -> LibraryScreen(
                    uiState = uiState,
                    miniPlayerVisible = miniPlayerVisible,
                    onOpenSettings = { route = AppRoute.Settings },
                    onOpenImports = { route = AppRoute.Imports },
                    onOpenWrapped = {
                        wrappedYear = java.time.Year.now().value
                        mainViewModel.refreshWrappedStats(wrappedYear)
                        route = AppRoute.Wrapped
                    },
                    onToggleFavoriteSong = { song -> mainViewModel.toggleFavoriteForSong(song.id) },
                    onPlay = {
                        mainViewModel.playTrack(it)
                        nowPlayingViewModel.restore()
                    },
                    onNavigateToArtist = { name, id -> openRootDetail(DetailRoute(DetailRoute.Type.Artist, name, id)) },
                    onNavigateToAlbum = { albumName, artistName, artworkUrl ->
                        openRootDetail(DetailRoute(DetailRoute.Type.Album, albumName, null, secondaryName = artistName, artworkUrl = artworkUrl))
                    },
                    onOpenTrackSheet = { track ->
                        activeActionContext = mapTrackToContext(track, uiState.localSongs)
                    },
                    onOpenPlaylist = { playlistId ->
                        mainViewModel.loadPlaylistTracks(playlistId)
                        route = AppRoute.PlaylistDetail
                    },
                    onPlayPlaylist = { playlistId ->
                        mainViewModel.playPlaylist(playlistId)
                        nowPlayingViewModel.restore()
                    },
                    onCreatePlaylist = { name -> mainViewModel.createPlaylist(name) }
                )
                AppRoute.Wrapped -> WrappedScreen(
                    year = wrappedYear,
                    stats = uiState.wrappedStats,
                    onBack = { route = AppRoute.Library }
                )
                AppRoute.Search -> SearchScreen(
                    uiState = searchUiState,
                    library = uiState.library,
                    onQueryChanged = searchViewModel::onQueryChanged,
                    onSearch = { dismissKeyboard(); searchViewModel.search() },
                    onSearchQuery = { dismissKeyboard(); searchViewModel.searchFor(it) },
                    onBrowseCategory = { dismissKeyboard(); searchViewModel.searchCategory(it) },
                    onRememberSearch = searchViewModel::rememberSearch,
                    onRemoveRecentSearch = searchViewModel::removeSearchHistory,
                    onClearRecentSearches = searchViewModel::clearSearchHistory,
                    onPlay = { dismissKeyboard(); mainViewModel.playTrack(it) },
                    onPlaySourceResult = { track ->
                        dismissKeyboard()
                        mainViewModel.playSourceResult(track)
                        nowPlayingViewModel.restore()
                    },
                    onPlaySourceResultList = { tracks, startIndex ->
                        dismissKeyboard()
                        mainViewModel.playSourceResults(tracks, startIndex)
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
                    onQuickPlay = { dismissKeyboard(); mainViewModel.quickPlayFromUrl(it) },
                    onImportEclipsePlaylist = mainViewModel::importEclipsePlaylist,
                    onOpenCatalogPlaylist = { playlist ->
                        playlistReturnRoute = AppRoute.Search
                        mainViewModel.openHomePlaylist(playlist.toGatewayHomePlaylist())
                        route = AppRoute.HomePlaylistDetail
                    },

                    miniPlayerVisible = miniPlayerVisible,
                    bottomNavVisible = showBottomNav,
                    isKeyboardVisible = isKeyboardVisible
                )
                AppRoute.Drive -> {
                    val liveNowPlayingState by nowPlayingViewModel.state.collectAsState()
                    DriveModeScreen(
                    nowPlayingState = liveNowPlayingState,
                    lyricsData = lyricsData,
                    pulseInsight = pulseInsight,
                    pulseInsightLoading = pulseInsightLoading,
                    aiDjViewModel = aiDjViewModel!!,
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
                }
                AppRoute.AiDj -> AiDjScreen(
                    onOpenPlayer = { openRoute(AppRoute.NowPlaying) },
                    viewModel = aiDjViewModel!!,
                    onBack = { route = previousMainRoute },
                    onOpenStation = { stationId -> openRadioStation(stationId) },
                    onStartStreamingStation = { input ->
                        // AI Radio uses the same station engine as the detail
                        // cards. Mark it pending so a completed station opens
                        // the player instead of silently leaving this screen.
                        pendingStationStart = "ai-radio"
                        mainViewModel.startStreamingStation(input)
                    },
                    onStartJukeboxStation = { stationId -> mainViewModel.startJukeboxStation(stationId) },
                    miniPlayerVisible = miniPlayerVisible,
                    bottomNavVisible = showBottomNav
                )
                AppRoute.Account -> AccountScreen(
                    accountManager = accountManager!!,
                    mainViewModel = mainViewModel,
                    vantaSocialManager = vantaSocialManager!!,
                    onBack = { route = AppRoute.Home },
                    onOpenFriendProfile = { friendId ->
                        friendProfileId = friendId
                        route = AppRoute.FriendProfile
                    }
                )
                AppRoute.Settings -> SettingsScreen(
                    onOpenImports = { route = AppRoute.Imports },
                    onLibraryChanged = { mainViewModel.refreshAll(skipRoomMaterialize = true) },
                    onBack = { route = previousMainRoute },
                    onOpenAdvanced = { route = AppRoute.AdvancedSettings },
                    onOpenEqualizer = { paramEqReturnRoute = route; route = AppRoute.ParametricEq },
                    miniPlayerVisible = miniPlayerVisible,
                    accountManager = accountManager!!,
                    onOpenAccount = { route = AppRoute.Account },
                    animatedArtworkEnabled = animatedArtworkEnabled,
                    onAnimatedArtworkEnabledChange = { enabled ->
                        animatedArtworkEnabled = enabled
                        VisualMotionPreferences.setAnimatedArtworkEnabled(ctx, enabled)
                    },
                    onOpenDrive = { openDrive(AppRoute.Settings) },
                    auraMode = visualizerPreferences.mode,
                    onAuraModeChange = { visualizerViewModel?.setMode(it) },
                    auraAudioReactive = visualizerPreferences.audioReactiveEnabled,
                    onAuraAudioReactiveChange = { visualizerViewModel?.setAudioReactive(it) },
                    auraReduceMotionCar = visualizerPreferences.reduceMotionInCar,
                    onAuraReduceMotionCarChange = { visualizerViewModel?.setReduceMotionInCar(it) }
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
                AppRoute.NowPlaying -> {
                    val liveNowPlayingState by nowPlayingViewModel.state.collectAsState()
                    NowPlayingScreen(
                    nowPlayingState = liveNowPlayingState,
                    enhancedMetadata = uiState.activeTrackEnhancedMetadata,
                    lyricsData = lyricsData,
                    lyricsTrackId = lyricsTrackId,
                    lyricsLoading = lyricsLoading,
                    lyricsIdentity = lyricsIdentity,
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
                    onToggleShuffle = mainViewModel::toggleShuffle,
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
                        // Prefer Now Playing identity over a stale queueSnapshot.currentTrack
                        // (large radio queues can diverge while NP title/artist stay correct).
                        val npTrackId = nowPlayingState.trackId?.toLongOrNull()
                        val queueCurrent = uiState.queueSnapshot.currentTrack
                        val current = when {
                            queueCurrent != null &&
                                (npTrackId == null || queueCurrent.track.trackId == npTrackId) -> queueCurrent
                            npTrackId != null -> {
                                uiState.queueSnapshot.originalQueue.firstOrNull { it.track.trackId == npTrackId }
                                    ?: uiState.library.firstOrNull { it.track.trackId == npTrackId }
                            }
                            else -> queueCurrent
                        }
                        if (current != null) {
                            activeActionContext = mapTrackToContext(current, uiState.localSongs, isNowPlaying = true)
                        } else {
                            activeActionContext = VantaActionContext.Track(
                                trackId = nowPlayingState.trackId ?: "",
                                title = nowPlayingState.title ?: "Unknown",
                                artist = nowPlayingState.artist ?: "Unknown",
                                album = nowPlayingState.album,
                                artworkUrl = nowPlayingState.artworkUrl,
                                sourceProviderId = nowPlayingState.preferredProviderId,
                                externalTrackId = nowPlayingState.preferredExternalTrackId,
                                isPlayable = nowPlayingState.isConfirmedPlayable(),
                                isInLibrary = nowPlayingState.isFavorite ||
                                    com.audiophile.musicplayer.data.local.LocalSongIdentity.isInLibrary(
                                        uiState.localSongs,
                                        nowPlayingState.isrc,
                                        nowPlayingState.title ?: "",
                                        nowPlayingState.artist ?: ""
                                    ),
                                isFavorite = nowPlayingState.isFavorite ||
                                    com.audiophile.musicplayer.data.local.LocalSongIdentity.isFavorite(
                                        uiState.localSongs,
                                        nowPlayingState.isrc,
                                        nowPlayingState.title ?: "",
                                        nowPlayingState.artist ?: ""
                                    ),
                                hasAlbum = !nowPlayingState.album.isNullOrBlank(),
                                hasArtist = !nowPlayingState.artist.isNullOrBlank(),
                                canStartRadio = true,
                                canQueue = true,
                                canShare = true,
                                qualityLabel = nowPlayingState.qualityInfo?.bestQualityLabel(),
                                explicit = nowPlayingState.explicit ?: false,
                                isNowPlaying = true,
                                qualityInfo = nowPlayingState.qualityInfo,
                                streamUrl = nowPlayingState.streamUrl,
                                durationMs = nowPlayingState.durationMs
                            )
                        }
                    },
                    onOpenQueueTrackSheet = { track ->
                        activeActionContext = mapTrackToContext(track, uiState.localSongs)
                    },
                    visualizerViewModel = visualizerViewModel,
                    selectedDiscoveryMode = radioDiscoveryMode,
                    onDiscoveryModeSelected = mainViewModel::setRadioDiscoveryMode
                    )
                }
                AppRoute.Imports -> ImportsScreen(
                    imports = uiState.importBatches,
                    isScanningDeviceLibrary = uiState.isScanningDeviceLibrary,
                    deviceScanProgress = uiState.deviceScanProgress,
                    statusMessage = uiState.statusMessage,
                    onBack = { route = AppRoute.Library },
                    onNewImport = {
                        pendingImportPlaylistId = null
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
                        pendingImportPlaylistId = null
                        pendingImportName = importName
                        pendingImportText = text
                        route = AppRoute.ImportPreview
                    },
                    onImportSpotifyHistory = { uri ->
                        mainViewModel.importSpotifyHistory(uri)
                    },
                    onImportAppleLibrary = { uri ->
                        mainViewModel.importAppleLibrary(uri)
                    },
                    onImportLikedCsv = { text ->
                        mainViewModel.importLikedSongsCsvText(text)
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
                        val isEclipse = com.audiophile.musicplayer.data.importer.SoundiizTextParser.isEclipsePlaylistUrl(pendingImportText)
                        mainViewModel.importPastedText(pendingImportName, pendingImportText, pendingImportPlaylistId)
                        if (isEclipse) {
                            route = AppRoute.Library
                        } else {
                            route = AppRoute.ImportReview
                        }
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
                AppRoute.HomePlaylistDetail -> HomePlaylistDetailScreen(
                    playlist = uiState.homePlaylist,
                    tracks = uiState.homePlaylistTracks,
                    loading = uiState.homePlaylistLoading,
                    error = uiState.homePlaylistError,
                    onBack = { route = playlistReturnRoute },
                    onRetry = { uiState.homePlaylist?.let(mainViewModel::openHomePlaylist) },
                    onPlay = { mainViewModel.playHomePlaylist() },
                    onShuffle = { mainViewModel.playHomePlaylist(shuffle = true) },
                    onPlayTrack = { index -> mainViewModel.playHomePlaylist(startIndex = index) },
                    miniPlayerVisible = miniPlayerVisible,
                    bottomNavVisible = showBottomNav
                )
                AppRoute.PlaylistDetail -> PlaylistDetailScreen(
                    playlists = uiState.localPlaylists,
                    onAddSelected = mainViewModel::addSelectedSongs,
                    onImportCsv = { text ->
                        pendingImportPlaylistId = uiState.activePlaylistId
                        pendingImportName = uiState.activePlaylistName
                        pendingImportText = text
                        route = AppRoute.ImportPreview
                    },
                    playlistName = uiState.activePlaylistName,
                    playlistDescription = uiState.activePlaylistDescription,
                    playlistArtworkUrl = uiState.activePlaylistArtworkUrl,
                    tracks = uiState.playlistDetailTracks,
                    onBack = { route = AppRoute.Library },
                    onPlayAll = {
                        mainViewModel.playPlaylist(uiState.activePlaylistId ?: return@PlaylistDetailScreen)
                        nowPlayingViewModel.restore()
                    },
                    onShuffle = {
                        mainViewModel.playPlaylist(uiState.activePlaylistId ?: return@PlaylistDetailScreen, shuffle = true)
                        nowPlayingViewModel.restore()
                    },
                    onPlayTrack = { song ->
                        mainViewModel.playLocalSong(song)
                        nowPlayingViewModel.restore()
                    },
                    onDeletePlaylist = {
                        mainViewModel.deletePlaylist(uiState.activePlaylistId ?: return@PlaylistDetailScreen)
                        route = AppRoute.Library
                    },
                    miniPlayerVisible = miniPlayerVisible,
                    bottomNavVisible = showBottomNav
                )
                AppRoute.FriendProfile -> {
                    val fpId = friendProfileId
                    if (fpId != null) {
                        FriendProfileScreen(
                            friendId = fpId,
                            vantaSocialManager = vantaSocialManager!!,
                            onBack = {
                                friendProfileId = null
                                route = AppRoute.Home
                            },
                            onPlayTrack = { title, artist ->
                                mainViewModel.playSourceResult(
                                    com.audiophile.musicplayer.data.canonical.CanonicalTrack(
                                        title = title,
                                        artist = artist
                                    )
                                )
                            },
                            miniPlayerVisible = miniPlayerVisible,
                            bottomNavVisible = showBottomNav
                        )
                    } else {
                        LaunchedEffect(Unit) { route = AppRoute.Home }
                    }
                }
                else -> {}
                    }
                }
            }
        }
        }

        androidx.compose.material3.SnackbarHost(
            hostState = feedbackHost,
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                .padding(start = 20.dp, end = 20.dp, bottom = sharedChromeContentInset + 12.dp).zIndex(60f)
        )
        Box(
            Modifier.align(Alignment.TopCenter).fillMaxWidth()
                .height(WindowInsets.statusBars.asPaddingValues().calculateTopPadding())
                .background(AppBackground.copy(alpha = 0.97f)).zIndex(40f)
        )
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
            AnimatedVisibility(
                visible = miniPlayerVisible,
                enter = slideInVertically(
                    animationSpec = tween(VantaMotion.chromeFadeMs, easing = VantaMotion.easeOutLuxury)
                ) { height -> height / 2 } + fadeIn(VantaMotion.chromeTween()),
                exit = slideOutVertically(
                    animationSpec = tween(VantaMotion.microMs, easing = VantaMotion.easeInOutCozy)
                ) { height -> height / 3 } + fadeOut(VantaMotion.microTween()),
                label = "miniPlayerVisibility"
            ) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        // Commentary stays in Radio; it must not change the shared player's height.
                        val liveNowPlayingState by nowPlayingViewModel.state.collectAsState()
                        MiniPlayer(
                            nowPlayingState = liveNowPlayingState,
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
            AnimatedVisibility(
                visible = showBottomNav,
                enter = slideInVertically(
                    animationSpec = tween(VantaMotion.chromeFadeMs, easing = VantaMotion.easeOutLuxury)
                ) { height -> height / 2 } + fadeIn(VantaMotion.chromeTween()),
                exit = slideOutVertically(
                    animationSpec = tween(VantaMotion.microMs, easing = VantaMotion.easeInOutCozy)
                ) { height -> height / 3 } + fadeOut(VantaMotion.microTween()),
                label = "bottomNavVisibility"
            ) {
                    BottomNavBar(
                        currentRoute = route,
                        onRouteSelected = { selectRootTab(it) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .zIndex(31f)
                    )
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
                        val seedTrackId = when (context) {
                            is VantaActionContext.Track -> context.trackId
                            else -> nowPlayingState.trackId
                        }
                        if (!albumName.isNullOrBlank()) {
                            val albumId = nowPlayingState.canonicalAlbumId
                            AcceptanceTruth.album(
                                album = albumName,
                                artist = artistName,
                                seedTrackId = seedTrackId,
                                navigationMode = if (albumId != null) "canonical" else "name",
                                albumId = albumId
                            )
                            detailRoute = DetailRoute(
                                type = DetailRoute.Type.Album,
                                name = albumName,
                                id = albumId,
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
                        val seedTrackId = when (context) {
                            is VantaActionContext.Track -> context.trackId
                            else -> nowPlayingState.trackId
                        }
                        if (!artistName.isNullOrBlank()) {
                            val artistId = nowPlayingState.canonicalArtistId
                            AcceptanceTruth.artist(
                                artist = artistName,
                                navigationMode = if (artistId != null) "canonical" else "name",
                                seedTrackId = seedTrackId,
                                activePlaybackTrackId = nowPlayingState.trackId,
                                artistId = artistId
                            )
                            detailRoute = DetailRoute(
                                type = DetailRoute.Type.Artist,
                                name = artistName,
                                id = artistId
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
                    VantaActionSheetAction.VIEW_FILE_INFO -> {
                        when (context) {
                            is VantaActionContext.Track -> {
                                fileInfoTarget = FileInfoTarget(
                                    title = context.title,
                                    artist = context.artist,
                                    album = context.album,
                                    streamUrl = context.streamUrl,
                                    qualityInfo = context.qualityInfo,
                                    durationMs = context.durationMs
                                )
                            }
                            else -> {}
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
        FileInfoSheet(
            title = nowPlayingState.title ?: "Unknown",
            artist = nowPlayingState.artist,
            album = nowPlayingState.album,
            streamUrl = nowPlayingState.streamUrl,
            qualityInfo = nowPlayingState.qualityInfo,
            durationMs = nowPlayingState.durationMs,
            onDismiss = { showSourceDetailsSheet = false }
        )
    }

    fileInfoTarget?.let { target ->
        FileInfoSheet(
            title = target.title,
            artist = target.artist,
            album = target.album,
            streamUrl = target.streamUrl,
            qualityInfo = target.qualityInfo,
            durationMs = target.durationMs,
            onDismiss = { fileInfoTarget = null }
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
    val matched = com.audiophile.musicplayer.data.local.LocalSongIdentity.findMatchingSong(
        songs = localSongs,
        isrc = track.track.isrc,
        title = title,
        artist = artist
    )
    val inLib = matched != null
    val isFav = matched?.isFavorite == true
    val bestSource = track.sources
        .filter { !it.externalProviderId.isNullOrBlank() && !it.externalTrackId.isNullOrBlank() }
        .maxByOrNull { it.bitrate }
        ?: track.sources.maxByOrNull { it.bitrate }
    val sourceStatus = track.sourceValidityStatus()
    val canEnterPlayback = sourceStatus.canEnterPlaybackFlow()
    val resolvedQualityInfo = bestSource?.let {
        VantaQualityInfo.fromTrackSource(it, sourceStatus)
    }
    val qualityLabel = resolvedQualityInfo?.bestQualityLabel()
    val streamUrl = bestSource?.streamUrl ?: matched?.streamUrl
    return VantaActionContext.Track(
        trackId = track.track.trackId.toString(),
        title = title,
        artist = artist,
        album = track.track.albumName,
        artworkUrl = track.track.coverArtUrl,
        sourceProviderId = bestSource?.externalProviderId ?: (if (matched != null) "local" else null),
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
        isNowPlaying = isNowPlaying,
        qualityInfo = resolvedQualityInfo,
        streamUrl = streamUrl,
        durationMs = track.track.durationMs
    )
}

private data class FileInfoTarget(
    val title: String,
    val artist: String?,
    val album: String?,
    val streamUrl: String?,
    val qualityInfo: VantaQualityInfo?,
    val durationMs: Long?
)



