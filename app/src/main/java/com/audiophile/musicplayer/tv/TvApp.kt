package com.audiophile.musicplayer.tv

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.QueueMusic
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Radio
import androidx.compose.material.icons.outlined.SkipNext
import androidx.compose.material.icons.outlined.SkipPrevious
import androidx.compose.material.icons.outlined.ThumbDown
import androidx.compose.material.icons.outlined.ThumbUp
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import com.audiophile.musicplayer.data.lyrics.LyricsData
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.audiophile.musicplayer.AppContainer
import com.audiophile.musicplayer.data.canonical.CanonicalAlbum
import com.audiophile.musicplayer.data.canonical.CanonicalArtist
import com.audiophile.musicplayer.data.canonical.CanonicalTrack
import com.audiophile.musicplayer.data.display.VantaQualityInfo
import com.audiophile.musicplayer.data.local.entities.LocalSongEntity
import com.audiophile.musicplayer.data.local.entities.PlaylistEntity
import com.audiophile.musicplayer.data.source.GatewayHomePlaylist
import com.audiophile.musicplayer.data.source.SourceSearchResult
import com.audiophile.musicplayer.data.source.debrandCuratorLabel
import com.audiophile.musicplayer.playback.NowPlayingViewModel
import com.audiophile.musicplayer.playback.QueueSnapshot
import com.audiophile.musicplayer.ui.FormatBadgeSize
import com.audiophile.musicplayer.ui.MainViewModel
import com.audiophile.musicplayer.ui.SearchViewModel
import com.audiophile.musicplayer.ui.VantaFormatBadgeRow
import com.audiophile.musicplayer.ui.VantaType
import com.audiophile.musicplayer.ui.theme.VantaSans
import com.audiophile.musicplayer.tv.jukebox.TvJukeboxStage
import com.audiophile.musicplayer.tv.jukebox.TvTurntablePlatter
import com.audiophile.musicplayer.tv.jukebox.TvVacuumTubes
import com.audiophile.musicplayer.tv.jukebox.TvVuMeters
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay

private enum class TvDestination {
    Discover, Radio, Search, Library, Settings, Queue, NowPlaying
}

private enum class TvDiscoverTab {
    Home, Playlists, Radio, ForYou
}

private enum class TvNowPlayingViewMode {
    Theater, Lyrics, Queue, Jukebox
}

@Composable
fun TvApp(
    container: AppContainer,
    mainViewModel: MainViewModel,
    searchViewModel: SearchViewModel,
    nowPlayingViewModel: NowPlayingViewModel,
    personalizedMixViewModel: com.audiophile.musicplayer.ui.PersonalizedMixViewModel
) {
    var destination by remember { mutableStateOf(TvDestination.Discover) }
    var previousDestination by remember { mutableStateOf(TvDestination.Discover) }
    var discoverTab by remember { mutableStateOf(TvDiscoverTab.Home) }
    var nowPlayingViewMode by remember { mutableStateOf(TvNowPlayingViewMode.Theater) }
    val mainState by mainViewModel.uiState.collectAsState()
    val radioDiscoveryMode by mainViewModel.radioDiscoveryMode.collectAsState()
    val searchState by searchViewModel.uiState.collectAsState()
    val nowPlaying by nowPlayingViewModel.state.collectAsState()
    val mixState by personalizedMixViewModel.uiState.collectAsState()
    val rawLyricsData by nowPlayingViewModel.lyrics.collectAsState()
    val lyricsLoading by nowPlayingViewModel.lyricsLoading.collectAsState()
    val sync = container.deviceLibrarySyncManager
    val pairCode by sync.pairCode.collectAsState()
    val syncStatus by sync.status.collectAsState()
    val likedSongs = remember(mainState.localSongs) { mainState.localSongs.filter { it.isFavorite } }
    val recentSongs = remember(mainState.localSongs) {
        mainState.localSongs.filter { it.lastPlayedAt != null }
            .sortedByDescending { it.lastPlayedAt }
            .take(40)
    }
    val moodStations = remember { com.audiophile.musicplayer.data.dj.MoodStationCatalog.allStations() }
    val presetStations = remember { com.audiophile.musicplayer.data.dj.JukeboxCatalog.stations }
    val genreStations = remember {
        com.audiophile.musicplayer.data.dj.JukeboxCatalog.genres.mapNotNull {
            com.audiophile.musicplayer.data.dj.JukeboxCatalog.stationFromGenre(it.id)
        }
    }
    val eraStations = remember {
        com.audiophile.musicplayer.data.dj.JukeboxCatalog.eras.mapNotNull {
            com.audiophile.musicplayer.data.dj.JukeboxCatalog.stationFromEra(it.id)
        }
    }
    val homeFocus = remember { FocusRequester() }
    val metrics = rememberTvMetrics()
    val scope = rememberCoroutineScope()
    val qualityLabel = tvQualityLabel(nowPlaying.qualityInfo)

    fun navigateTo(dest: TvDestination) {
        if (dest != TvDestination.NowPlaying) {
            previousDestination = dest
        }
        mainViewModel.clearHomePlaylist()
        destination = dest
    }

    fun goNowPlaying(mode: TvNowPlayingViewMode = TvNowPlayingViewMode.Theater) {
        if (destination != TvDestination.NowPlaying) {
            previousDestination = destination
        }
        nowPlayingViewMode = mode
        destination = TvDestination.NowPlaying
    }

    LaunchedEffect(Unit) {
        nowPlayingViewModel.activateDetailEnrichment()
        mainViewModel.loadHomeFeed(force = false)
        mainViewModel.loadCatalogForYou()
        mainViewModel.loadEditorialNewReleases(force = false)
        mainViewModel.refreshAll()
        personalizedMixViewModel.loadHomeCards()
        nowPlayingViewModel.restore()
        if (pairCode.length in 4..8) {
            runCatching { sync.pullToThisDevice() }
            mainViewModel.refreshAll()
        }
        runCatching { homeFocus.requestFocus() }
    }

    // Open Now Playing once audio is actually playing — keep Discover visible while resolving
    // so gateway failures stay readable in the status line.
    LaunchedEffect(nowPlaying.isPlaying, nowPlaying.title) {
        if (nowPlaying.isPlaying && !nowPlaying.title.isNullOrBlank() && destination != TvDestination.NowPlaying) {
            previousDestination = destination
            destination = TvDestination.NowPlaying
        }
    }

    BackHandler(enabled = destination != TvDestination.Discover || mainState.homePlaylist != null) {
        when {
            mainState.homePlaylist != null -> mainViewModel.clearHomePlaylist()
            destination == TvDestination.NowPlaying -> destination = previousDestination
            destination == TvDestination.Queue -> destination = TvDestination.Library
            else -> destination = TvDestination.Discover
        }
    }

    val currentNowPlayingArt = nowPlaying.artworkUrl
    val ambientArt = remember(destination, currentNowPlayingArt, mainState.homeFeed) {
        when (destination) {
            TvDestination.NowPlaying -> currentNowPlayingArt
            else -> mainState.homeFeed?.trendingNow?.firstOrNull()?.artworkUrl
                ?: mainState.homeFeed?.popularTracks?.firstOrNull()?.artworkUrl
                ?: currentNowPlayingArt
        }
    }

    Box(Modifier.fillMaxSize()) {
        TvLuxuryBackdrop(Modifier.fillMaxSize(), artworkUrl = ambientArt)

        if (destination == TvDestination.NowPlaying) {
            TvNowPlayingScreen(
                initialViewMode = nowPlayingViewMode,
                title = nowPlaying.title,
                artist = nowPlaying.artist,
                album = nowPlaying.album,
                artworkUrl = nowPlaying.artworkUrl,
                isPlaying = nowPlaying.isPlaying,
                isFavorite = nowPlaying.isFavorite,
                qualityInfo = nowPlaying.qualityInfo,
                acousticness = nowPlaying.acousticness,
                positionMs = nowPlaying.positionMs,
                durationMs = nowPlaying.durationMs,
                lyrics = rawLyricsData,
                lyricsLoading = lyricsLoading,
                queueSnapshot = mainState.queueSnapshot,
                metrics = metrics,
                onToggle = { mainViewModel.togglePlayPause() },
                onPrevious = { mainViewModel.playPreviousFromQueue() },
                onNext = { mainViewModel.playNextFromQueue() },
                onFavorite = { mainViewModel.toggleFavoriteForNowPlaying() },
                onThumbsUp = { mainViewModel.onGenomeThumbsUp() },
                onThumbsDown = { mainViewModel.onGenomeThumbsDown() },
                onOpenQueue = { destination = TvDestination.Queue },
                onSeek = { mainViewModel.seekTo(it) },
                onRetryLyrics = { nowPlayingViewModel.retryLyrics() },
                onPlayQueueIndex = { index ->
                    val queue = mainState.queueSnapshot.originalQueue
                    if (index in queue.indices) {
                        mainViewModel.playQueue(queue, index)
                    }
                },
                onBack = { destination = previousDestination },
                selectedDiscoveryMode = radioDiscoveryMode,
                onDiscoveryModeSelected = mainViewModel::setRadioDiscoveryMode
            )
        } else {
            Row(Modifier.fillMaxSize()) {
                TvNavRail(
                    selectedDestination = when (destination) {
                        TvDestination.Discover -> "Discover"
                        TvDestination.Radio -> "Radio"
                        TvDestination.Search -> "Search"
                        TvDestination.Library, TvDestination.Queue -> "Library"
                        TvDestination.Settings -> "Settings"
                        else -> "Discover"
                    },
                    onSelectDestination = { name ->
                        when (name) {
                            "Discover" -> navigateTo(TvDestination.Discover)
                            "Radio" -> navigateTo(TvDestination.Radio)
                            "Search" -> navigateTo(TvDestination.Search)
                            "Library" -> navigateTo(TvDestination.Library)
                            "Settings" -> navigateTo(TvDestination.Settings)
                        }
                    },
                    nowTitle = nowPlaying.title,
                    nowArtist = nowPlaying.artist,
                    nowArt = nowPlaying.artworkUrl,
                    isPlaying = nowPlaying.isPlaying,
                    qualityLabel = qualityLabel,
                    positionMs = nowPlaying.positionMs,
                    durationMs = nowPlaying.durationMs,
                    metrics = metrics,
                    firstFocus = homeFocus,
                    onTogglePlay = { mainViewModel.togglePlayPause() },
                    onNextTrack = { mainViewModel.playNextFromQueue() },
                    onOpenNowPlaying = { goNowPlaying() }
                )

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                ) {
                    // Auto-dismiss transient status messages after 3.5 seconds
                    LaunchedEffect(mainState.statusMessage) {
                        if (!mainState.statusMessage.isNullOrBlank()) {
                            delay(3500L)
                            mainViewModel.clearStatusMessage()
                        }
                    }

                    Box(Modifier.weight(1f).fillMaxWidth()) {
                        when (destination) {
                            TvDestination.Discover -> TvDiscoverScreen(
                                tab = discoverTab,
                                onTab = { discoverTab = it },
                                loading = mainState.homeFeedLoading || mainState.streamingStationLoading,
                                playlists = mainState.homeFeed?.playlists.orEmpty(),
                                popular = mainState.homeFeed?.popularTracks.orEmpty(),
                                fresh = mainState.homeFeed?.freshDrops.orEmpty().ifEmpty { mainState.editorialNewReleases },
                                trending = mainState.homeFeed?.trendingNow.orEmpty(),
                                forYou = mainState.catalogForYou,
                                forYouArtist = mainState.catalogForYouArtist,
                                liked = likedSongs,
                                recent = recentSongs,
                                mixCards = mixState.cards,
                                moodStations = moodStations,
                                presetStations = presetStations,
                                genreStations = genreStations,
                                eraStations = eraStations,
                                stationLoading = mainState.streamingStationLoading,
                                openPlaylist = mainState.homePlaylist,
                                playlistTracks = mainState.homePlaylistTracks,
                                playlistLoading = mainState.homePlaylistLoading,
                                playlistError = mainState.homePlaylistError,
                                metrics = metrics,
                                onPlayTrack = { mainViewModel.playHomeTrack(it) },
                                onPlayTrackAtIndex = { tracks, idx -> mainViewModel.playReleases(tracks, idx) },
                                onPlayLiked = { mainViewModel.playLocalSong(it) },
                                onOpenPlaylist = { mainViewModel.openHomePlaylist(it) },
                                onClosePlaylist = { mainViewModel.clearHomePlaylist() },
                                onPlayPlaylistAll = { mainViewModel.playHomePlaylist(0, false) },
                                onPlayPlaylistTrack = { index -> mainViewModel.playHomePlaylist(index, false) },
                                onStartStation = { stationId ->
                                    mainViewModel.startJukeboxStation(stationId, shuffle = true)
                                },
                                onPlayMix = { kind ->
                                    personalizedMixViewModel.playMix(kind)
                                }
                            )
                            TvDestination.Radio -> TvRadioScreen(
                                moodStations = moodStations,
                                presetStations = presetStations,
                                genreStations = genreStations,
                                eraStations = eraStations,
                                stationLoading = mainState.streamingStationLoading,
                                nowPlayingTitle = nowPlaying.title,
                                nowPlayingArtist = nowPlaying.artist,
                                nowPlayingArt = nowPlaying.artworkUrl,
                                isPlaying = nowPlaying.isPlaying,
                                metrics = metrics,
                                onStartStation = { stationId ->
                                    mainViewModel.startJukeboxStation(stationId, shuffle = true)
                                },
                                onStartSonicRadio = {
                                    mainViewModel.playSonicRadio()
                                },
                                onOpenJukebox = { goNowPlaying(TvNowPlayingViewMode.Jukebox) }
                            )
                            TvDestination.Library -> TvLibraryScreen(
                                pairCode = pairCode,
                                status = syncStatus,
                                liked = likedSongs,
                                recent = recentSongs,
                                allSongs = mainState.localSongs,
                                playlists = mainState.localPlaylists,
                                queueSize = mainState.queueSnapshot.queueSize,
                                metrics = metrics,
                                onEnsureCode = { sync.ensurePairCode() },
                                onCodeChanged = { sync.setPairCode(it) },
                                onPull = {
                                    scope.launch {
                                        sync.pullToThisDevice()
                                        mainViewModel.refreshAll()
                                    }
                                },
                                onPush = { scope.launch { sync.pushFromThisDevice() } },
                                onPlaySong = {
                                    mainViewModel.playLocalSong(it)
                                },
                                onPlayPlaylist = {
                                    mainViewModel.playPlaylist(it)
                                },
                                onOpenQueue = { destination = TvDestination.Queue }
                            )
                            TvDestination.Search -> TvSearchScreen(
                                query = searchState.query,
                                searching = searchState.isSearching,
                                songs = searchState.songs,
                                albums = searchState.albums,
                                artists = searchState.artists,
                                status = searchState.statusMessage,
                                metrics = metrics,
                                onQueryChanged = searchViewModel::onQueryChanged,
                                onSearch = { searchViewModel.search() },
                                onPlaySong = {
                                    mainViewModel.playSourceResult(it)
                                }
                            )
                            TvDestination.Settings -> TvSettingsScreen(
                                pairCode = pairCode,
                                syncStatus = syncStatus,
                                metrics = metrics,
                                onEnsureCode = { sync.ensurePairCode() },
                                onPullFromPhone = {
                                    scope.launch {
                                        sync.pullToThisDevice()
                                        mainViewModel.refreshAll()
                                    }
                                },
                                onPushToCloud = { scope.launch { sync.pushFromThisDevice() } }
                            )
                            TvDestination.Queue -> TvQueueScreen(
                                snapshot = mainState.queueSnapshot,
                                metrics = metrics,
                                onBack = { destination = TvDestination.Library },
                                onPlayIndex = { index ->
                                    val queue = mainState.queueSnapshot.originalQueue
                                    if (index in queue.indices) {
                                        mainViewModel.playQueue(queue, index)
                                    }
                                }
                            )
                            TvDestination.NowPlaying -> Unit
                        }

                        // Floating transient status toast (does not shift screen layout)
                        androidx.compose.animation.AnimatedVisibility(
                            visible = !mainState.statusMessage.isNullOrBlank() &&
                                !mainState.statusMessage!!.contains("Source error", ignoreCase = true),
                            enter = fadeIn() + slideInVertically { -it },
                            exit = fadeOut() + slideOutVertically { -it },
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(top = 16.dp, end = metrics.pagePadding)
                        ) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color(0xF21C1814))
                                    .border(1.dp, TvTheme.HiResGold.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                                    .padding(horizontal = 14.dp, vertical = 8.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Box(
                                        Modifier
                                            .size(6.dp)
                                            .clip(CircleShape)
                                            .background(TvTheme.HiResGoldBright)
                                    )
                                    Text(
                                        mainState.statusMessage.orEmpty(),
                                        color = TvTheme.Text,
                                        fontSize = 12.sp,
                                        fontFamily = VantaSans,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TvDiscoverScreen(
    tab: TvDiscoverTab,
    onTab: (TvDiscoverTab) -> Unit,
    loading: Boolean,
    playlists: List<GatewayHomePlaylist>,
    popular: List<SourceSearchResult>,
    fresh: List<SourceSearchResult>,
    trending: List<SourceSearchResult>,
    forYou: List<SourceSearchResult>,
    forYouArtist: String?,
    liked: List<LocalSongEntity>,
    recent: List<LocalSongEntity>,
    mixCards: List<com.audiophile.musicplayer.ui.PersonalizedMixCardState>,
    moodStations: List<com.audiophile.musicplayer.data.dj.JukeboxStation>,
    presetStations: List<com.audiophile.musicplayer.data.dj.JukeboxStation>,
    genreStations: List<com.audiophile.musicplayer.data.dj.JukeboxStation>,
    eraStations: List<com.audiophile.musicplayer.data.dj.JukeboxStation>,
    stationLoading: Boolean,
    openPlaylist: GatewayHomePlaylist?,
    playlistTracks: List<CanonicalTrack>,
    playlistLoading: Boolean,
    playlistError: String?,
    metrics: TvMetrics,
    onPlayTrack: (SourceSearchResult) -> Unit,
    onPlayTrackAtIndex: (List<SourceSearchResult>, Int) -> Unit = { list, idx -> onPlayTrack(list[idx]) },
    onPlayLiked: (LocalSongEntity) -> Unit,
    onOpenPlaylist: (GatewayHomePlaylist) -> Unit,
    onClosePlaylist: () -> Unit,
    onPlayPlaylistAll: () -> Unit,
    onPlayPlaylistTrack: (Int) -> Unit,
    onStartStation: (String) -> Unit,
    onPlayMix: (com.audiophile.musicplayer.discovery.personalized.PersonalizedMixKind) -> Unit
) {
    if (openPlaylist != null) {
        TvPlaylistDetail(
            playlist = openPlaylist,
            tracks = playlistTracks,
            loading = playlistLoading,
            error = playlistError,
            metrics = metrics,
            onBack = onClosePlaylist,
            onPlayAll = onPlayPlaylistAll,
            onPlayTrack = onPlayPlaylistTrack
        )
        return
    }
    if (loading && popular.isEmpty() && fresh.isEmpty() && playlists.isEmpty() && liked.isEmpty() &&
        moodStations.isEmpty()
    ) {
        TvLoadingScreen()
        return
    }

    val hero = remember(fresh, popular, trending, forYou) {
        trending.firstOrNull { it.status != com.audiophile.musicplayer.data.source.SearchItemStatus.METADATA_ONLY }
            ?: popular.firstOrNull { it.status != com.audiophile.musicplayer.data.source.SearchItemStatus.METADATA_ONLY }
            ?: fresh.firstOrNull { it.status != com.audiophile.musicplayer.data.source.SearchItemStatus.METADATA_ONLY }
            ?: forYou.firstOrNull()
            ?: trending.firstOrNull()
            ?: popular.firstOrNull()
            ?: fresh.firstOrNull()
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = metrics.pagePadding,
            end = metrics.pagePadding,
            bottom = metrics.pagePadding + 24.dp
        ),
        verticalArrangement = Arrangement.spacedBy(metrics.rowGap)
    ) {
        item {
            TvHeroEditorial(
                track = hero,
                playlist = playlists.firstOrNull(),
                metrics = metrics,
                onPlayTrack = onPlayTrack,
                onOpenPlaylist = onOpenPlaylist
            )
        }
        item {
            TvDiscoverTabRow(
                selectedHome = tab == TvDiscoverTab.Home,
                selectedPlaylists = tab == TvDiscoverTab.Playlists,
                selectedRadio = tab == TvDiscoverTab.Radio,
                selectedForYou = tab == TvDiscoverTab.ForYou,
                metrics = metrics,
                onHome = { onTab(TvDiscoverTab.Home) },
                onPlaylists = { onTab(TvDiscoverTab.Playlists) },
                onRadio = { onTab(TvDiscoverTab.Radio) },
                onForYou = { onTab(TvDiscoverTab.ForYou) }
            )
        }
        if (stationLoading) {
            item {
                Text(
                    "Building your station…",
                    color = TvTheme.HiResGoldBright,
                    fontSize = metrics.body,
                    fontFamily = VantaSans,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        when (tab) {
            TvDiscoverTab.Home -> {
                if (moodStations.isNotEmpty()) {
                    item {
                        TvStationRow(
                            title = "Mood radio",
                            subtitle = "Pick a vibe — VANTA keeps the queue going",
                            stations = moodStations.take(12),
                            metrics = metrics,
                            onStart = onStartStation
                        )
                    }
                }
                if (mixCards.isNotEmpty()) {
                    item {
                        TvMixRow(
                            title = "Made for you",
                            subtitle = "Discovery Weekly, Release Radar, and more",
                            cards = mixCards,
                            metrics = metrics,
                            onPlay = onPlayMix
                        )
                    }
                }
                if (playlists.isNotEmpty()) {
                    item {
                        TvPlaylistCardRow(
                            title = "Editorial playlists",
                            subtitle = "Hand-built listening for the living room",
                            playlists = playlists,
                            metrics = metrics,
                            onOpen = onOpenPlaylist
                        )
                    }
                }
                if (fresh.isNotEmpty() || popular.isNotEmpty()) {
                    item {
                        TvSourceTrackRow(
                            title = "New Releases",
                            subtitle = "Fresh drops, ready to play",
                            tracks = fresh.ifEmpty { popular },
                            metrics = metrics,
                            onPlay = onPlayTrack,
                            onPlayTrackAtIndex = onPlayTrackAtIndex,
                            featuredFirst = true
                        )
                    }
                }
                if (trending.isNotEmpty()) {
                    item {
                        TvSourceTrackRow(
                            title = "Trending",
                            subtitle = "What’s moving right now",
                            tracks = trending,
                            metrics = metrics,
                            onPlay = onPlayTrack,
                            onPlayTrackAtIndex = onPlayTrackAtIndex
                        )
                    }
                }
                if (liked.isNotEmpty()) {
                    item { TvLocalSongRow("Liked", "From your library", liked, metrics, onPlayLiked) }
                }
                if (recent.isNotEmpty()) {
                    item { TvLocalSongRow("Recently played", null, recent, metrics, onPlayLiked) }
                }
            }

            TvDiscoverTab.Playlists -> {
                if (playlists.isNotEmpty()) {
                    item {
                        TvPlaylistCardRow(
                            title = "Apple Music editorial",
                            subtitle = "${playlists.size} playlists from the home feed",
                            playlists = playlists,
                            metrics = metrics,
                            onOpen = onOpenPlaylist
                        )
                    }
                }
                if (popular.isNotEmpty()) {
                    item {
                        TvSourceTrackRow(
                            title = "Popular tracks",
                            subtitle = "Chart heat, ready to queue",
                            tracks = popular,
                            metrics = metrics,
                            onPlay = onPlayTrack,
                            onPlayTrackAtIndex = onPlayTrackAtIndex
                        )
                    }
                }
                if (playlists.isEmpty() && popular.isEmpty()) {
                    item {
                        Text(
                            "Playlists will appear here once the home feed loads.",
                            color = TvTheme.TextSecondary,
                            fontSize = metrics.body,
                            fontFamily = VantaSans
                        )
                    }
                }
            }

            TvDiscoverTab.Radio -> {
                item {
                    TvStationRow(
                        title = "Mood radio",
                        subtitle = "Tap a mood — VANTA builds an endless station",
                        stations = moodStations,
                        metrics = metrics,
                        onStart = onStartStation
                    )
                }
                item {
                    TvStationRow(
                        title = "Genre stations",
                        subtitle = "Rock, soul, hip-hop, jazz, and more",
                        stations = genreStations,
                        metrics = metrics,
                        onStart = onStartStation
                    )
                }
                item {
                    TvStationRow(
                        title = "Decades",
                        subtitle = "Time-machine radio by era",
                        stations = eraStations,
                        metrics = metrics,
                        onStart = onStartStation
                    )
                }
                item {
                    TvStationRow(
                        title = "Signature stations",
                        subtitle = "Yacht Rock, Motown, Disco, Quiet Storm…",
                        stations = presetStations,
                        metrics = metrics,
                        onStart = onStartStation
                    )
                }
            }

            TvDiscoverTab.ForYou -> {
                if (mixCards.isNotEmpty()) {
                    item {
                        TvMixRow(
                            title = "Your mixes",
                            subtitle = "Refreshed from your taste profile",
                            cards = mixCards,
                            metrics = metrics,
                            onPlay = onPlayMix
                        )
                    }
                }
                if (forYou.isNotEmpty()) {
                    item {
                        TvSourceTrackRow(
                            title = forYouArtist?.let { "Because you like $it" } ?: "For you",
                            subtitle = "Matched to what you actually play",
                            tracks = forYou,
                            metrics = metrics,
                            onPlay = onPlayTrack,
                            onPlayTrackAtIndex = onPlayTrackAtIndex
                        )
                    }
                }
                if (liked.isNotEmpty()) {
                    item { TvLocalSongRow("Liked songs", null, liked, metrics, onPlayLiked) }
                }
                if (recent.isNotEmpty()) {
                    item { TvLocalSongRow("Jump back in", null, recent, metrics, onPlayLiked) }
                }
                if (forYou.isEmpty() && mixCards.isEmpty() && liked.isEmpty()) {
                    item {
                        Text(
                            "Play a few tracks or pull your phone library to personalize this tab.",
                            color = TvTheme.TextSecondary,
                            fontSize = metrics.body,
                            fontFamily = VantaSans
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TvStationRow(
    title: String,
    subtitle: String?,
    stations: List<com.audiophile.musicplayer.data.dj.JukeboxStation>,
    metrics: TvMetrics,
    onStart: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        TvSectionHeader(title = title, metrics = metrics, subtitle = subtitle)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(18.dp), contentPadding = PaddingValues(vertical = 6.dp)) {
            items(stations, key = { it.id }) { station ->
                TvStationCard(
                    name = station.name,
                    description = station.description,
                    emoji = station.emoji,
                    metrics = metrics,
                    onClick = { onStart(station.id) }
                )
            }
        }
    }
}

@Composable
private fun TvMixRow(
    title: String,
    subtitle: String?,
    cards: List<com.audiophile.musicplayer.ui.PersonalizedMixCardState>,
    metrics: TvMetrics,
    onPlay: (com.audiophile.musicplayer.discovery.personalized.PersonalizedMixKind) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        TvSectionHeader(title = title, metrics = metrics, subtitle = subtitle)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(18.dp), contentPadding = PaddingValues(vertical = 6.dp)) {
            items(cards, key = { it.kind.id }) { card ->
                TvMixCard(
                    title = card.title,
                    subtitle = card.subtitle,
                    artworkUrl = card.artworkUrl,
                    metrics = metrics,
                    loading = card.isLoading,
                    onPlay = { onPlay(card.kind) }
                )
            }
        }
    }
}

@Composable
private fun TvHeroEditorial(
    track: SourceSearchResult?,
    playlist: GatewayHomePlaylist?,
    metrics: TvMetrics,
    onPlayTrack: (SourceSearchResult) -> Unit,
    onOpenPlaylist: (GatewayHomePlaylist) -> Unit
) {
    val title = track?.title ?: playlist?.name ?: "High fidelity, living room"
    val subtitle = track?.artist
        ?: playlist?.let { debrandCuratorLabel(it.curator) }
        ?: "Lossless · Atmos · your library"
    val art = track?.artworkUrl
        ?: playlist?.artworkUrl
        ?: track?.coverSeed?.takeIf { it.startsWith("http") }
    val badge = track?.let { sourceQualityBadge(it) } ?: "Hi-Res"
    val eyebrow = when {
        track != null -> "Featured tonight"
        playlist != null -> "Editorial"
        else -> "VANTA"
    }
    val shape = RoundedCornerShape(28.dp)
    val context = androidx.compose.ui.platform.LocalContext.current
    val heroRequest = remember(art) {
        coil.request.ImageRequest.Builder(context)
            .data(art)
            .crossfade(true)
            .build()
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(metrics.heroHeight)
            .clip(shape)
            .border(1.dp, TvTheme.Hairline, shape)
    ) {
        if (!art.isNullOrBlank()) {
            AsyncImage(
                model = heroRequest,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.linearGradient(
                            listOf(Color(0xFF2A241C), TvTheme.SurfaceSoft, TvTheme.Bg)
                        )
                    )
            )
            Box(
                Modifier
                    .align(Alignment.CenterEnd)
                    .fillMaxWidth(0.55f)
                    .fillMaxHeight()
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                TvTheme.HiResGold.copy(alpha = 0.18f),
                                Color.Transparent
                            )
                        )
                    )
            )
        }
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.horizontalGradient(
                        0f to TvTheme.Bg.copy(alpha = 0.94f),
                        0.38f to TvTheme.Bg.copy(alpha = 0.78f),
                        0.68f to TvTheme.Bg.copy(alpha = 0.35f),
                        1f to TvTheme.Bg.copy(alpha = 0.12f)
                    )
                )
        )
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to Color.Transparent,
                        0.55f to Color.Transparent,
                        1f to TvTheme.Bg.copy(alpha = 0.5f)
                    )
                )
        )
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 28.dp, vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(28.dp)
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            eyebrow.uppercase(),
                            color = TvTheme.HiResGoldBright,
                            fontSize = metrics.caption,
                            fontFamily = VantaSans,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 2.sp
                        )
                        TvQualityBadge(badge)
                    }
                    Text(
                        title,
                        style = VantaType.editorialHero.copy(
                            color = TvTheme.Text,
                            fontSize = metrics.heroTitle,
                            fontWeight = FontWeight.Medium,
                            lineHeight = metrics.heroTitle * 1.08f
                        ),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        subtitle,
                        color = TvTheme.TextSecondary,
                        fontSize = metrics.body,
                        fontFamily = VantaSans,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (track != null || playlist != null) {
                        TvFocusable(
                            onClick = {
                                when {
                                    track != null -> onPlayTrack(track)
                                    playlist != null -> onOpenPlaylist(playlist)
                                }
                            },
                            cornerRadius = 40,
                            focusScale = metrics.focusScale
                        ) { focused ->
                            Text(
                                "Play",
                                color = if (focused) TvTheme.HiResGoldDark else TvTheme.PillSelectedText,
                                fontFamily = VantaSans,
                                fontWeight = FontWeight.Bold,
                                fontSize = metrics.body,
                                modifier = Modifier
                                    .background(if (focused) TvTheme.HiResGold else TvTheme.PillSelected)
                                    .padding(horizontal = 36.dp, vertical = 14.dp)
                            )
                        }
                        if (playlist != null) {
                            TvFocusable(
                                onClick = { onOpenPlaylist(playlist) },
                                cornerRadius = 40,
                                focusScale = metrics.focusScale
                            ) { focused ->
                                Text(
                                    "Browse playlist",
                                    color = if (focused) TvTheme.Text else TvTheme.TextSecondary,
                                    fontFamily = VantaSans,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = metrics.body,
                                    modifier = Modifier
                                        .border(
                                            1.dp,
                                            if (focused) TvTheme.FocusRing else TvTheme.Hairline,
                                            RoundedCornerShape(40.dp)
                                        )
                                        .background(if (focused) TvTheme.SurfaceSoft else Color(0x331C1916))
                                        .padding(horizontal = 28.dp, vertical = 14.dp)
                                )
                            }
                        }
                    }
                }
            }
            TvArtwork(
                url = art,
                size = metrics.heroArt.coerceAtMost(metrics.heroHeight - 56.dp),
                radius = 20.dp,
                elevated = true
            )
        }
    }
}

private fun tvAudiophileHudText(qualityInfo: VantaQualityInfo?): String {
    if (qualityInfo == null) return "FLAC · 16-bit / 44.1 kHz · Bitstream Direct"
    val parts = mutableListOf<String>()
    val fmt = when {
        qualityInfo.isDolbyAtmos -> "Dolby Atmos (E-AC-3 JOC)"
        qualityInfo.isSony360RealityAudio -> "Sony 360 Reality Audio"
        qualityInfo.isHiRes == true -> "Hi-Res FLAC"
        qualityInfo.isLossless == true -> "Lossless FLAC"
        else -> qualityInfo.format?.uppercase() ?: "Lossless"
    }
    parts.add(fmt)
    if (qualityInfo.bitDepth != null && qualityInfo.sampleRateHz != null) {
        val srKhz = qualityInfo.sampleRateHz / 1000f
        val srStr = if (srKhz % 1f == 0f) "${srKhz.toInt()} kHz" else "${String.format("%.1f", srKhz)} kHz"
        parts.add("${qualityInfo.bitDepth}-bit / $srStr")
    } else if (qualityInfo.sampleRateHz != null) {
        val srKhz = qualityInfo.sampleRateHz / 1000f
        val srStr = if (srKhz % 1f == 0f) "${srKhz.toInt()} kHz" else "${String.format("%.1f", srKhz)} kHz"
        parts.add(srStr)
    }
    if (qualityInfo.channels != null && qualityInfo.channels > 2) {
        parts.add("${qualityInfo.channels}.0 Surround")
    } else {
        parts.add("Bitstream Direct")
    }
    return parts.joinToString(" · ")
}

@Composable
private fun TvRadioScreen(
    moodStations: List<com.audiophile.musicplayer.data.dj.JukeboxStation>,
    presetStations: List<com.audiophile.musicplayer.data.dj.JukeboxStation>,
    genreStations: List<com.audiophile.musicplayer.data.dj.JukeboxStation>,
    eraStations: List<com.audiophile.musicplayer.data.dj.JukeboxStation>,
    stationLoading: Boolean,
    nowPlayingTitle: String? = null,
    nowPlayingArtist: String? = null,
    nowPlayingArt: String? = null,
    isPlaying: Boolean = false,
    metrics: TvMetrics,
    onStartStation: (String) -> Unit,
    onStartSonicRadio: () -> Unit = {},
    onOpenJukebox: () -> Unit = {}
) {
    val oldiesStations = remember {
        com.audiophile.musicplayer.data.dj.JukeboxCatalog.stations.filter {
            it.id in listOf(
                "golden_oldies",
                "fifties_rock_roll",
                "sixties_invasion",
                "motown_soul",
                "doo_wop_classics",
                "rockabilly_stomp",
                "yacht_rock",
                "one_hit_wonders"
            )
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = metrics.pagePadding,
            end = metrics.pagePadding,
            top = 16.dp,
            bottom = metrics.pagePadding + 24.dp
        ),
        verticalArrangement = Arrangement.spacedBy(metrics.rowGap)
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    "RADIO & JUKEBOX",
                    color = TvTheme.HiResGoldBright,
                    fontSize = metrics.caption,
                    fontFamily = VantaSans,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.sp
                )
                Text(
                    "Living Room Radio",
                    style = VantaType.editorialHero.copy(
                        color = TvTheme.Text,
                        fontSize = metrics.pageTitle,
                        fontWeight = FontWeight.SemiBold
                    )
                )
                Text(
                    "Curated moods, vintage classics, and decades streaming seamlessly.",
                    color = TvTheme.TextMuted,
                    fontSize = metrics.body,
                    fontFamily = VantaSans
                )
            }
        }

        // Live Jukebox Theater Compact Hero Bar
        item {
            TvFocusable(
                onClick = onOpenJukebox,
                cornerRadius = 18,
                focusScale = 1.015f,
                modifier = Modifier.fillMaxWidth()
            ) { focused ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(
                            Brush.horizontalGradient(
                                if (focused) listOf(Color(0xFF2B2215), Color(0xFF1D160D), Color(0xFF120E08))
                                else listOf(Color(0xFF1A1612), Color(0xFF110F0C), Color(0xFF0C0A08))
                            )
                        )
                        .border(
                            width = if (focused) 2.dp else 1.dp,
                            color = if (focused) TvTheme.FocusRing else TvTheme.HiResGold.copy(alpha = 0.28f),
                            shape = RoundedCornerShape(18.dp)
                        )
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        TvTurntablePlatter(
                            artworkUrl = nowPlayingArt,
                            isPlaying = isPlaying,
                            size = 56.dp
                        )

                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(7.dp)
                                        .clip(CircleShape)
                                        .background(if (isPlaying) TvTheme.HiResGoldBright else TvTheme.HiResGold.copy(alpha = 0.6f))
                                )
                                Text(
                                    if (isPlaying) "NOW SPINNING" else "JUKEBOX THEATER",
                                    color = TvTheme.HiResGoldBright,
                                    fontSize = 10.sp,
                                    fontFamily = VantaSans,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.2.sp
                                )
                            }
                            Text(
                                if (!nowPlayingTitle.isNullOrBlank()) {
                                    if (!nowPlayingArtist.isNullOrBlank()) "$nowPlayingTitle — $nowPlayingArtist"
                                    else nowPlayingTitle
                                } else "Audiophile Turntable & Disc Changer",
                                color = TvTheme.Text,
                                fontSize = 16.sp,
                                fontFamily = VantaSans,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (focused) TvTheme.HiResGold else TvTheme.SurfaceSoft)
                            .border(1.dp, if (focused) TvTheme.FocusRing else TvTheme.Hairline, RoundedCornerShape(12.dp))
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Icon(
                            Icons.Outlined.Radio,
                            contentDescription = null,
                            tint = if (focused) Color(0xFF0A0908) else TvTheme.HiResGoldBright,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = "Open Jukebox",
                            color = if (focused) Color(0xFF0A0908) else TvTheme.Text,
                            fontSize = 13.sp,
                            fontFamily = VantaSans,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        if (stationLoading) {
            item {
                Text(
                    "Connecting station stream…",
                    color = TvTheme.HiResGoldBright,
                    fontSize = metrics.body,
                    fontFamily = VantaSans,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        if (moodStations.isNotEmpty()) {
            item {
                TvStationRow(
                    title = "Mood Radio",
                    subtitle = "Curated vibes for focus, late night, and relaxed listening",
                    stations = moodStations,
                    metrics = metrics,
                    onStart = onStartStation
                )
            }
        }

        if (oldiesStations.isNotEmpty()) {
            item {
                TvStationRow(
                    title = "Oldies & Vintage Classics",
                    subtitle = "Golden Oldies, Doo-Wop, Rockabilly, British Invasion, Motown & Yacht Rock",
                    stations = oldiesStations,
                    metrics = metrics,
                    onStart = onStartStation
                )
            }
        }

        if (eraStations.isNotEmpty()) {
            item {
                TvStationRow(
                    title = "Decade Time Capsules",
                    subtitle = "Iconic sounds through the eras in pristine master quality",
                    stations = eraStations,
                    metrics = metrics,
                    onStart = onStartStation
                )
            }
        }

        if (genreStations.isNotEmpty()) {
            item {
                TvStationRow(
                    title = "Genre Radio",
                    subtitle = "Deep dives into specialized audiophile styles",
                    stations = genreStations,
                    metrics = metrics,
                    onStart = onStartStation
                )
            }
        }

        if (presetStations.isNotEmpty()) {
            item {
                TvStationRow(
                    title = "Featured Jukebox Stations",
                    subtitle = "Continuous AI-managed playlists",
                    stations = presetStations,
                    metrics = metrics,
                    onStart = onStartStation
                )
            }
        }
    }
}

@Composable
private fun TvNowPlayingScreen(
    initialViewMode: TvNowPlayingViewMode = TvNowPlayingViewMode.Theater,
    title: String?,
    artist: String?,
    album: String?,
    artworkUrl: String?,
    isPlaying: Boolean,
    isFavorite: Boolean,
    qualityInfo: VantaQualityInfo?,
    acousticness: Double? = null,
    positionMs: Long,
    durationMs: Long,
    lyrics: LyricsData?,
    lyricsLoading: Boolean,
    queueSnapshot: QueueSnapshot?,
    metrics: TvMetrics,
    onToggle: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onFavorite: () -> Unit,
    onThumbsUp: (() -> Unit)? = null,
    onThumbsDown: (() -> Unit)? = null,
    onOpenQueue: () -> Unit,
    onSeek: (Long) -> Unit,
    onRetryLyrics: () -> Unit,
    onPlayQueueIndex: (Int) -> Unit,
    onBack: () -> Unit,
    selectedDiscoveryMode: com.audiophile.musicplayer.radio.RadioDiscoveryMode = com.audiophile.musicplayer.radio.RadioDiscoveryMode.HYBRID_MIX,
    onDiscoveryModeSelected: (com.audiophile.musicplayer.radio.RadioDiscoveryMode) -> Unit = {}
) {
    var viewMode by remember(initialViewMode) { mutableStateOf(initialViewMode) }
    val playFocus = remember { FocusRequester() }

    val progress = if (durationMs > 0L) {
        (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }

    LaunchedEffect(Unit) {
        runCatching { playFocus.requestFocus() }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = metrics.pagePadding, vertical = 14.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // Top Header Bar: Back Button | Mode Switcher (Theater, Lyrics, Queue)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                TvFocusable(
                    onClick = onBack,
                    cornerRadius = 14,
                    focusScale = 1.05f
                ) { focused ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier
                            .clip(RoundedCornerShape(14.dp))
                            .background(if (focused) TvTheme.SurfaceSoft else Color.Transparent)
                            .border(1.dp, if (focused) TvTheme.FocusRing else TvTheme.Hairline, RoundedCornerShape(14.dp))
                            .padding(horizontal = 14.dp, vertical = 8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = if (focused) TvTheme.Text else TvTheme.TextSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = "Back",
                            color = if (focused) TvTheme.Text else TvTheme.TextSecondary,
                            fontSize = metrics.caption,
                            fontFamily = VantaSans,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                // The Now Playing content (Theater/Lyrics/Queue/Jukebox) already carries
                // state, quality and track info in its own body — the header stays
                // to navigation chrome only (Back + mode pills).
            }

            // Mode switcher: Theater, Lyrics, Queue
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(RoundedCornerShape(14.dp))
                    .background(TvTheme.Surface.copy(alpha = 0.55f))
                    .padding(4.dp)
            ) {
                TvNavPill(
                    label = "Artwork",
                    selected = viewMode == TvNowPlayingViewMode.Theater,
                    metrics = metrics,
                    onClick = { viewMode = TvNowPlayingViewMode.Theater },
                    compact = true
                )
                TvNavPill(
                    label = "Live Lyrics",
                    selected = viewMode == TvNowPlayingViewMode.Lyrics,
                    metrics = metrics,
                    onClick = { viewMode = TvNowPlayingViewMode.Lyrics },
                    icon = Icons.Outlined.Mic,
                    compact = true
                )
                TvNavPill(
                    label = "Up Next",
                    selected = viewMode == TvNowPlayingViewMode.Queue,
                    metrics = metrics,
                    onClick = { viewMode = TvNowPlayingViewMode.Queue },
                    icon = Icons.AutoMirrored.Outlined.QueueMusic,
                    compact = true
                )
                TvNavPill(
                    label = "Jukebox",
                    selected = viewMode == TvNowPlayingViewMode.Jukebox,
                    metrics = metrics,
                    onClick = { viewMode = TvNowPlayingViewMode.Jukebox },
                    icon = Icons.Outlined.Radio,
                    compact = true
                )
            }
        }

        // Center Content Area based on View Mode
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(vertical = 10.dp)
        ) {
            when (viewMode) {
                TvNowPlayingViewMode.Theater -> {
                    Row(
                        modifier = Modifier.fillMaxSize(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(40.dp)
                    ) {
                        // Left: Elevated Artwork with subtle glow
                        Box(contentAlignment = Alignment.Center) {
                            if (!artworkUrl.isNullOrBlank()) {
                                AsyncImage(
                                    model = artworkUrl,
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .size(metrics.heroArt + 32.dp)
                                        .clip(RoundedCornerShape(24.dp))
                                        .background(TvTheme.Surface.copy(alpha = 0.35f))
                                )
                                Box(
                                    Modifier
                                        .size(metrics.heroArt + 32.dp)
                                        .clip(RoundedCornerShape(24.dp))
                                        .background(TvTheme.Bg.copy(alpha = 0.35f))
                                )
                            }
                            TvArtwork(
                                url = artworkUrl,
                                size = metrics.heroArt.coerceAtLeast(240.dp),
                                radius = 20.dp,
                                elevated = true
                            )
                        }

                        // Right: Title, Artist, Audiophile HUD, Progress, Transport Controls
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight(),
                            verticalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text(
                                    title ?: "Nothing playing",
                                    style = VantaType.editorialHero.copy(
                                        color = TvTheme.Text,
                                        fontSize = metrics.heroTitle,
                                        fontWeight = FontWeight.Medium,
                                        lineHeight = metrics.heroTitle * 1.06f
                                    ),
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )

                                Text(
                                    artist.orEmpty(),
                                    color = TvTheme.Text,
                                    fontSize = metrics.rowTitle,
                                    fontFamily = VantaSans,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )

                                album?.takeIf { it.isNotBlank() }?.let {
                                    Text(
                                        it,
                                        color = TvTheme.TextMuted,
                                        fontSize = metrics.body,
                                        fontFamily = VantaSans,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }

                                // Audiophile Visual Format Badges (Dolby Atmos, TrueHD, Hi-Res, FLAC, Pure Acoustic)
                                VantaFormatBadgeRow(
                                    qualityInfo = qualityInfo,
                                    acousticness = acousticness,
                                    size = FormatBadgeSize.Standard
                                )
                            }

                            // Progress & Transport Controls
                            TvPlaybackControls(
                                positionMs = positionMs,
                                durationMs = durationMs,
                                progress = progress,
                                isPlaying = isPlaying,
                                isFavorite = isFavorite,
                                metrics = metrics,
                                playFocus = playFocus,
                                onSeek = onSeek,
                                onPrevious = onPrevious,
                                onToggle = onToggle,
                                onNext = onNext,
                                onFavorite = onFavorite,
                                onThumbsUp = onThumbsUp,
                                onThumbsDown = onThumbsDown,
                                onOpenQueue = { viewMode = TvNowPlayingViewMode.Queue }
                            )
                        }
                    }
                }

                TvNowPlayingViewMode.Lyrics -> {
                    Column(Modifier.fillMaxSize()) {
                        Box(Modifier.weight(1f).fillMaxWidth()) {
                            TvLyricsStage(
                                lyrics = lyrics,
                                isLoading = lyricsLoading,
                                positionMs = positionMs,
                                durationMs = durationMs,
                                isPlaying = isPlaying,
                                metrics = metrics,
                                artworkUrl = artworkUrl,
                                accentColor = TvTheme.HiResGold,
                                onSeekToMs = onSeek,
                                onRetry = onRetryLyrics
                            )
                        }

                        // Compact bottom transport strip while reading lyrics
                        TvCompactTransportBar(
                            positionMs = positionMs,
                            durationMs = durationMs,
                            progress = progress,
                            isPlaying = isPlaying,
                            metrics = metrics,
                            onToggle = onToggle,
                            onPrevious = onPrevious,
                            onNext = onNext
                        )
                    }
                }

                TvNowPlayingViewMode.Queue -> {
                    Row(
                        modifier = Modifier.fillMaxSize(),
                        horizontalArrangement = Arrangement.spacedBy(32.dp)
                    ) {
                        // Left: Compact Track Info
                        Column(
                            modifier = Modifier.width(260.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            TvArtwork(url = artworkUrl, size = 160.dp, radius = 16.dp, elevated = true)
                            Text(title ?: "Playing", color = TvTheme.Text, fontSize = metrics.cardTitle, fontWeight = FontWeight.Bold, maxLines = 1)
                            Text(artist.orEmpty(), color = TvTheme.TextSecondary, fontSize = metrics.cardSubtitle, maxLines = 1)

                            TvCompactTransportBar(
                                positionMs = positionMs,
                                durationMs = durationMs,
                                progress = progress,
                                isPlaying = isPlaying,
                                metrics = metrics,
                                onToggle = onToggle,
                                onPrevious = onPrevious,
                                onNext = onNext
                            )
                        }

                        // Right: Queue List
                        val queue = queueSnapshot?.originalQueue.orEmpty()
                        if (queue.isEmpty()) {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text("No upcoming tracks in queue", color = TvTheme.TextSecondary, fontSize = metrics.body)
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier.weight(1f).fillMaxHeight(),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                item {
                                    Text(
                                        "Up Next (${queue.size})",
                                        color = TvTheme.HiResGoldBright,
                                        fontSize = metrics.caption,
                                        fontFamily = VantaSans,
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 1.5.sp
                                    )
                                }
                                itemsIndexed(queue, key = { idx, item -> "${item.track.trackId}_$idx" }) { index, item ->
                                    val isCurrent = index == queueSnapshot?.queueIndex
                                    TvFocusable(
                                        onClick = { onPlayQueueIndex(index) },
                                        cornerRadius = 12,
                                        focusScale = 1.02f,
                                        modifier = Modifier.fillMaxWidth()
                                    ) { focused ->
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(12.dp))
                                                .background(if (focused) TvTheme.SurfaceSoft else if (isCurrent) TvTheme.BgElevated else Color.Transparent)
                                                .border(1.dp, if (focused) TvTheme.FocusRing else Color.Transparent, RoundedCornerShape(12.dp))
                                                .padding(horizontal = 14.dp, vertical = 10.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                                        ) {
                                            Text(
                                                text = "${index + 1}",
                                                color = if (isCurrent) TvTheme.HiResGold else TvTheme.TextMuted,
                                                fontSize = metrics.caption,
                                                fontFamily = VantaSans,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.width(24.dp)
                                            )
                                            TvArtwork(url = item.track.coverArtUrl, size = 36.dp, radius = 6.dp)
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    item.track.title,
                                                    color = if (isCurrent) TvTheme.HiResGoldBright else TvTheme.Text,
                                                    fontSize = metrics.cardSubtitle,
                                                    fontFamily = VantaSans,
                                                    fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                Text(
                                                    item.track.artist,
                                                    color = TvTheme.TextSecondary,
                                                    fontSize = metrics.caption,
                                                    fontFamily = VantaSans,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                            if (isCurrent && isPlaying) {
                                                AnimatedEqualizerBars(color = TvTheme.HiResGold)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                TvNowPlayingViewMode.Jukebox -> {
                    TvJukeboxStage(
                        title = title,
                        artist = artist,
                        album = album,
                        artworkUrl = artworkUrl,
                        isPlaying = isPlaying,
                        qualityInfo = qualityInfo,
                        acousticness = acousticness,
                        positionMs = positionMs,
                        durationMs = durationMs,
                        queueSnapshot = queueSnapshot,
                        metrics = metrics,
                        onToggle = onToggle,
                        onPrevious = onPrevious,
                        onNext = onNext,
                        onThumbsUp = { onThumbsUp?.invoke() },
                        onThumbsDown = { onThumbsDown?.invoke() },
                        onPlayQueueIndex = onPlayQueueIndex,
                        isLiked = isFavorite,
                        onSeek = onSeek,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
    }
}

@Composable
private fun TvPlaybackControls(
    positionMs: Long,
    durationMs: Long,
    progress: Float,
    isPlaying: Boolean,
    isFavorite: Boolean,
    metrics: TvMetrics,
    playFocus: FocusRequester,
    onSeek: (Long) -> Unit,
    onPrevious: () -> Unit,
    onToggle: () -> Unit,
    onNext: () -> Unit,
    onFavorite: () -> Unit,
    onThumbsUp: (() -> Unit)? = null,
    onThumbsDown: (() -> Unit)? = null,
    onOpenQueue: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(5.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(TvTheme.ProgressTrack)
            ) {
                Box(
                    Modifier
                        .fillMaxWidth(progress)
                        .height(5.dp)
                        .background(
                            Brush.horizontalGradient(
                                listOf(TvTheme.HiResGold, TvTheme.ProgressFill)
                            )
                        )
                )
            }
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(formatMs(positionMs), color = TvTheme.TextSecondary, fontSize = metrics.caption, fontFamily = VantaSans)
                Text(formatMs(durationMs), color = TvTheme.TextSecondary, fontSize = metrics.caption, fontFamily = VantaSans)
            }
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TvNavPill(
                label = "−10s",
                selected = false,
                metrics = metrics,
                onClick = { onSeek((positionMs - 10_000L).coerceAtLeast(0L)) },
                compact = true
            )
            TvTransportButton(onClick = onPrevious, metrics = metrics) {
                Icon(Icons.Outlined.SkipPrevious, null, tint = it, modifier = Modifier.size(28.dp))
            }
            TvFocusable(
                onClick = onToggle,
                cornerRadius = 48,
                focusScale = 1.06f,
                focusRequester = playFocus
            ) { focused ->
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .background(
                            if (focused) TvTheme.HiResGold else TvTheme.PillSelected,
                            CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Outlined.Pause else Icons.Outlined.PlayArrow,
                        contentDescription = if (isPlaying) "Pause" else "Play",
                        tint = TvTheme.PillSelectedText,
                        modifier = Modifier.size(40.dp)
                    )
                }
            }
            TvTransportButton(onClick = onNext, metrics = metrics) {
                Icon(Icons.Outlined.SkipNext, null, tint = it, modifier = Modifier.size(28.dp))
            }
            TvNavPill(
                label = "+10s",
                selected = false,
                metrics = metrics,
                onClick = {
                    if (durationMs > 0L) onSeek((positionMs + 10_000L).coerceAtMost(durationMs))
                },
                compact = true
            )
            Spacer(Modifier.width(8.dp))
            TvTransportButton(onClick = onFavorite, metrics = metrics, selected = isFavorite) {
                Icon(
                    if (isFavorite) Icons.Outlined.Favorite else Icons.Outlined.FavoriteBorder,
                    null,
                    tint = if (isFavorite) TvTheme.HiResGold else it,
                    modifier = Modifier.size(24.dp)
                )
            }
            if (onThumbsDown != null) {
                TvTransportButton(onClick = onThumbsDown, metrics = metrics) {
                    Icon(
                        Icons.Outlined.ThumbDown,
                        contentDescription = "Thumbs Down",
                        tint = it,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
            if (onThumbsUp != null) {
                TvTransportButton(onClick = onThumbsUp, metrics = metrics) {
                    Icon(
                        Icons.Outlined.ThumbUp,
                        contentDescription = "Thumbs Up",
                        tint = it,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
            TvTransportButton(onClick = onOpenQueue, metrics = metrics) {
                Icon(Icons.AutoMirrored.Outlined.QueueMusic, null, tint = it, modifier = Modifier.size(24.dp))
            }
        }
    }
}

@Composable
private fun TvCompactTransportBar(
    positionMs: Long,
    durationMs: Long,
    progress: Float,
    isPlaying: Boolean,
    metrics: TvMetrics,
    onToggle: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(TvTheme.SurfaceGlass)
            .border(1.dp, TvTheme.Hairline, RoundedCornerShape(14.dp))
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            TvTransportButton(onClick = onPrevious, metrics = metrics) {
                Icon(Icons.Outlined.SkipPrevious, null, tint = it, modifier = Modifier.size(20.dp))
            }
            TvFocusable(onClick = onToggle, cornerRadius = 30, focusScale = 1.08f) { focused ->
                Icon(
                    imageVector = if (isPlaying) Icons.Outlined.Pause else Icons.Outlined.PlayArrow,
                    contentDescription = if (isPlaying) "Pause" else "Play",
                    tint = if (focused) TvTheme.HiResGoldDark else TvTheme.Text,
                    modifier = Modifier
                        .background(
                            if (focused) TvTheme.HiResGold else TvTheme.SurfaceSoft,
                            CircleShape
                        )
                        .padding(8.dp)
                        .size(18.dp)
                )
            }
            TvTransportButton(onClick = onNext, metrics = metrics) {
                Icon(Icons.Outlined.SkipNext, null, tint = it, modifier = Modifier.size(20.dp))
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.width(280.dp)
        ) {
            Text(formatMs(positionMs), color = TvTheme.TextSecondary, fontSize = 11.sp, fontFamily = VantaSans)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(TvTheme.ProgressTrack)
            ) {
                Box(
                    Modifier
                        .fillMaxWidth(progress)
                        .height(4.dp)
                        .background(TvTheme.HiResGold)
                )
            }
            Text(formatMs(durationMs), color = TvTheme.TextSecondary, fontSize = 11.sp, fontFamily = VantaSans)
        }
    }
}

@Composable
private fun TvTransportButton(
    onClick: () -> Unit,
    metrics: TvMetrics,
    selected: Boolean = false,
    content: @Composable (tint: androidx.compose.ui.graphics.Color) -> Unit
) {
    TvFocusable(onClick = onClick, cornerRadius = 40, focusScale = metrics.focusScale) { focused ->
        val tint = when {
            focused -> TvTheme.HiResGold
            selected -> TvTheme.HiResGold
            else -> TvTheme.Text
        }
        Box(
            modifier = Modifier
                .background(
                    when {
                        focused -> TvTheme.SurfaceSoft
                        selected -> TvTheme.Surface
                        else -> TvTheme.PillIdle
                    },
                    CircleShape
                )
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            content(tint)
        }
    }
}

@Composable
private fun TvPlaylistDetail(
    playlist: GatewayHomePlaylist,
    tracks: List<CanonicalTrack>,
    loading: Boolean,
    error: String?,
    metrics: TvMetrics,
    onBack: () -> Unit,
    onPlayAll: () -> Unit,
    onPlayTrack: (Int) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(metrics.pagePadding),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            TvNavPill(label = "Back", selected = false, metrics = metrics, onClick = onBack, compact = true)
            if (tracks.isNotEmpty()) {
                TvNavPill(label = "Play all", selected = true, metrics = metrics, onClick = onPlayAll, compact = true)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            TvArtwork(url = playlist.artworkUrl, size = metrics.artSize, radius = 18.dp, elevated = true)
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(playlist.name, color = TvTheme.Text, fontSize = metrics.pageTitle, fontWeight = FontWeight.Bold, maxLines = 2)
                Text(debrandCuratorLabel(playlist.curator), color = TvTheme.TextSecondary, fontSize = metrics.body)
                TvQualityBadge("Hi-Res AUDIO")
            }
        }
        when {
            loading -> Text("Loading…", color = TvTheme.TextSecondary, fontSize = metrics.body)
            !error.isNullOrBlank() -> Text(error, color = TvTheme.HiResGold, fontSize = metrics.body)
            tracks.isEmpty() -> Text("No tracks.", color = TvTheme.TextSecondary, fontSize = metrics.body)
            else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f)) {
                itemsIndexed(tracks, key = { i, t -> t.externalTrackId ?: "${t.title}-$i" }) { index, track ->
                    TvFocusable(
                        onClick = { onPlayTrack(index) },
                        cornerRadius = 14,
                        focusScale = metrics.focusScale,
                        modifier = Modifier.fillMaxWidth()
                    ) { focused ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .background(if (focused) TvTheme.SurfaceSoft else TvTheme.Surface.copy(alpha = 0.5f))
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TvArtwork(url = track.artworkUrl, size = metrics.miniArt)
                            Spacer(Modifier.width(14.dp))
                            Column(Modifier.weight(1f)) {
                                Text(track.title, color = TvTheme.Text, fontSize = metrics.cardTitle, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(track.artist, color = TvTheme.TextSecondary, fontSize = metrics.cardSubtitle, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            TvQualityBadge("Hi-Res", compact = true)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TvLibraryScreen(
    pairCode: String,
    status: String?,
    liked: List<LocalSongEntity>,
    recent: List<LocalSongEntity>,
    allSongs: List<LocalSongEntity>,
    playlists: List<PlaylistEntity>,
    queueSize: Int,
    metrics: TvMetrics,
    onEnsureCode: () -> Unit,
    onCodeChanged: (String) -> Unit,
    onPull: () -> Unit,
    onPush: () -> Unit,
    onPlaySong: (LocalSongEntity) -> Unit,
    onPlayPlaylist: (Long) -> Unit,
    onOpenQueue: () -> Unit
) {
    LaunchedEffect(Unit) { if (pairCode.isBlank()) onEnsureCode() }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(metrics.pagePadding),
        verticalArrangement = Arrangement.spacedBy(metrics.rowGap)
    ) {
        item {
            Text(
                "Library",
                style = VantaType.editorialHero.copy(color = TvTheme.Text, fontSize = metrics.pageTitle, fontWeight = FontWeight.Medium)
            )
            Spacer(Modifier.height(6.dp))
            Text("Liked songs, playlists, phone link", color = TvTheme.TextSecondary, fontSize = metrics.caption)
        }
        item {
            TvNavPill(
                label = if (queueSize > 0) "Queue · $queueSize" else "Queue",
                selected = false,
                metrics = metrics,
                onClick = onOpenQueue,
                icon = Icons.AutoMirrored.Outlined.QueueMusic,
                compact = true
            )
        }
        if (liked.isNotEmpty()) item { TvLocalSongRow("Liked (${liked.size})", null, liked, metrics, onPlaySong) }
        if (playlists.isNotEmpty()) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    TvSectionHeader(title = "Playlists", metrics = metrics)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                        items(playlists, key = { it.id }) { playlist ->
                            TvAlbumCard(
                                title = playlist.name,
                                artist = playlist.description ?: "Playlist",
                                artworkUrl = playlist.artworkUrl,
                                badge = null,
                                metrics = metrics,
                                onClick = { onPlayPlaylist(playlist.id) }
                            )
                        }
                    }
                }
            }
        }
        if (recent.isNotEmpty()) item { TvLocalSongRow("Recently played", null, recent, metrics, onPlaySong) }
        item {
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(22.dp))
                    .background(TvTheme.Surface)
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("Link phone & TV", color = TvTheme.Text, fontSize = metrics.rowTitle, fontWeight = FontWeight.SemiBold)
                Text(
                    "Sync your library and favorites with phone (Settings → Link phone & TV)",
                    color = TvTheme.TextSecondary,
                    fontSize = metrics.caption
                )
                Text(
                    if (pairCode.isBlank()) "————" else pairCode.chunked(3).joinToString(" "),
                    color = TvTheme.HiResGold,
                    fontSize = metrics.pageTitle,
                    fontWeight = FontWeight.Bold
                )
                BasicTextField(
                    value = pairCode,
                    onValueChange = onCodeChanged,
                    singleLine = true,
                    textStyle = TextStyle(color = TvTheme.Text, fontSize = metrics.body, letterSpacing = 3.sp),
                    cursorBrush = SolidColor(TvTheme.HiResGold),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(TvTheme.BgElevated)
                        .padding(14.dp)
                )
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    TvNavPill(label = "Pull from phone", selected = true, metrics = metrics, onClick = onPull, compact = true)
                    TvNavPill(label = "Push from TV", selected = false, metrics = metrics, onClick = onPush, compact = true)
                    TvNavPill(label = "New code", selected = false, metrics = metrics, onClick = onEnsureCode, compact = true)
                }
                status?.let { Text(it, color = TvTheme.HiResGold, fontSize = metrics.caption) }
            }
        }
        if (allSongs.isNotEmpty()) {
            item { TvLocalSongRow("All songs (${allSongs.size})", null, allSongs.take(80), metrics, onPlaySong) }
        }
        if (liked.isEmpty() && allSongs.isEmpty() && playlists.isEmpty()) {
            item {
                Text("Nothing here yet. Pull from your phone or Search.", color = TvTheme.TextSecondary, fontSize = metrics.body)
            }
        }
    }
}

@Composable
private fun TvSearchScreen(
    query: String,
    searching: Boolean,
    songs: List<CanonicalTrack>,
    albums: List<CanonicalAlbum>,
    artists: List<CanonicalArtist>,
    status: String?,
    metrics: TvMetrics,
    onQueryChanged: (String) -> Unit,
    onSearch: () -> Unit,
    onPlaySong: (CanonicalTrack) -> Unit
) {
    val fieldInteraction = remember { MutableInteractionSource() }
    val fieldFocused by fieldInteraction.collectIsFocusedAsState()
    val quickChips = remember {
        listOf(
            "Dolby Atmos",
            "Hi-Res FLAC",
            "Pink Floyd",
            "Radiohead",
            "Miles Davis",
            "Daft Punk",
            "Hans Zimmer",
            "Electronic",
            "Rock Classics",
            "Jazz Essentials",
            "Classical Masterpieces",
            "Ambient Lo-Fi"
        )
    }
    Column(
        Modifier.fillMaxSize().padding(metrics.pagePadding),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text(
            "Search",
            style = VantaType.editorialHero.copy(color = TvTheme.Text, fontSize = metrics.pageTitle, fontWeight = FontWeight.Medium)
        )
        BasicTextField(
            value = query,
            onValueChange = onQueryChanged,
            singleLine = true,
            textStyle = TextStyle(color = TvTheme.Text, fontSize = metrics.body),
            cursorBrush = SolidColor(TvTheme.HiResGold),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { onSearch() }),
            interactionSource = fieldInteraction,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .border(
                    width = if (fieldFocused) 2.dp else 1.dp,
                    color = if (fieldFocused) TvTheme.FocusRing else TvTheme.Text.copy(alpha = 0.08f),
                    shape = RoundedCornerShape(20.dp)
                )
                .background(if (fieldFocused) TvTheme.SurfaceSoft else TvTheme.Surface)
                .padding(horizontal = 22.dp, vertical = 16.dp)
                .focusable(interactionSource = fieldInteraction)
                .onPreviewKeyEvent { event ->
                    if (event.type == KeyEventType.KeyDown &&
                        (event.key == Key.Enter || event.key == Key.DirectionCenter)
                    ) {
                        onSearch()
                        true
                    } else {
                        false
                    }
                },
            decorationBox = { inner ->
                if (query.isBlank()) Text("Song, artist, album…", color = TvTheme.TextMuted, fontSize = metrics.body)
                inner()
            }
        )
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(vertical = 2.dp)
        ) {
            items(quickChips) { chip ->
                TvFocusable(
                    onClick = {
                        onQueryChanged(chip)
                        onSearch()
                    },
                    cornerRadius = 24,
                    focusScale = 1.05f
                ) { focused ->
                    Text(
                        text = chip,
                        color = if (focused) TvTheme.HiResGoldDark else TvTheme.Text,
                        fontSize = metrics.caption,
                        fontFamily = VantaSans,
                        fontWeight = if (focused) FontWeight.Bold else FontWeight.Medium,
                        modifier = Modifier
                            .clip(RoundedCornerShape(24.dp))
                            .background(if (focused) TvTheme.HiResGold else TvTheme.SurfaceSoft)
                            .border(
                                1.dp,
                                if (focused) TvTheme.FocusRing else TvTheme.Hairline,
                                RoundedCornerShape(24.dp)
                            )
                            .padding(horizontal = 14.dp, vertical = 7.dp)
                    )
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            TvNavPill(
                label = if (searching) "Searching…" else "Search",
                selected = true,
                metrics = metrics,
                onClick = onSearch,
                compact = true
            )
            status?.let { Text(it, color = TvTheme.TextSecondary, fontSize = metrics.caption) }
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(18.dp), modifier = Modifier.weight(1f)) {
            if (artists.isNotEmpty()) {
                item {
                    Text("Artists", color = TvTheme.Text, fontSize = metrics.rowTitle, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(10.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        items(artists.take(20), key = { it.id ?: it.name }) { artist ->
                            TvAlbumCard(
                                title = artist.name,
                                artist = "Artist",
                                artworkUrl = artist.artworkUrl,
                                badge = null,
                                metrics = metrics,
                                onClick = { onQueryChanged(artist.name); onSearch() }
                            )
                        }
                    }
                }
            }
            if (albums.isNotEmpty()) {
                item {
                    Text("Albums", color = TvTheme.Text, fontSize = metrics.rowTitle, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(10.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        items(albums.take(20), key = { it.id ?: "${it.title}-${it.artist}" }) { album ->
                            TvAlbumCard(
                                title = album.title,
                                artist = album.artist,
                                artworkUrl = album.artworkUrl,
                                badge = "Hi-Res AUDIO",
                                metrics = metrics,
                                onClick = { onQueryChanged("${album.artist} ${album.title}"); onSearch() }
                            )
                        }
                    }
                }
            }
            if (songs.isNotEmpty()) {
                item { Text("Songs", color = TvTheme.Text, fontSize = metrics.rowTitle, fontWeight = FontWeight.SemiBold) }
                itemsIndexed(songs, key = { i, t -> t.externalTrackId ?: "${t.title}-${t.artist}-$i" }) { _, track ->
                    TvFocusable(
                        onClick = { onPlaySong(track) },
                        cornerRadius = 14,
                        focusScale = metrics.focusScale,
                        modifier = Modifier.fillMaxWidth()
                    ) { focused ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .background(if (focused) TvTheme.SurfaceSoft else TvTheme.Surface.copy(alpha = 0.5f))
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TvArtwork(url = track.artworkUrl, size = metrics.miniArt)
                            Spacer(Modifier.width(18.dp))
                            Column(Modifier.weight(1f)) {
                                Text(track.title, color = TvTheme.Text, fontSize = metrics.cardTitle, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(track.artist, color = TvTheme.TextSecondary, fontSize = metrics.cardSubtitle, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            TvQualityBadge("Hi-Res", compact = true)
                        }
                    }
                }
            } else if (!searching && query.isNotBlank()) {
                item { Text("No results.", color = TvTheme.TextSecondary, fontSize = metrics.body) }
            } else if (!searching && query.isBlank() && songs.isEmpty() && albums.isEmpty() && artists.isEmpty()) {
                item {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(top = 28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("Explore Audiophile Master Catalog", color = TvTheme.Text, fontSize = metrics.rowTitle, fontWeight = FontWeight.SemiBold)
                        Text("Select a quick genre chip above or enter an artist or track name.", color = TvTheme.TextSecondary, fontSize = metrics.body)
                    }
                }
            }
        }
    }
}

@Composable
private fun TvQueueScreen(
    snapshot: QueueSnapshot,
    metrics: TvMetrics,
    onBack: () -> Unit,
    onPlayIndex: (Int) -> Unit
) {
    val queue = snapshot.originalQueue
    Column(
        Modifier.fillMaxSize().padding(metrics.pagePadding),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        TvNavPill(label = "Back", selected = false, metrics = metrics, onClick = onBack, compact = true)
        Text(
            "Queue",
            style = VantaType.editorialHero.copy(color = TvTheme.Text, fontSize = metrics.pageTitle, fontWeight = FontWeight.Medium)
        )
        Text(
            if (queue.isEmpty()) "Nothing queued" else "${snapshot.queueSize} tracks · now #${(snapshot.queueIndex + 1).coerceAtLeast(1)}",
            color = TvTheme.TextSecondary,
            fontSize = metrics.caption
        )
        if (queue.isEmpty()) {
            Text("Play something from Discover, Library, or Search.", color = TvTheme.TextSecondary, fontSize = metrics.body)
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                itemsIndexed(queue, key = { i, item -> "${item.track.trackId}-$i" }) { index, item ->
                    val isCurrent = index == snapshot.queueIndex
                    TvFocusable(
                        onClick = { onPlayIndex(index) },
                        cornerRadius = 12,
                        focusScale = metrics.focusScale,
                        modifier = Modifier.fillMaxWidth()
                    ) { focused ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .background(
                                    when {
                                        isCurrent -> TvTheme.HiResGold.copy(alpha = 0.14f)
                                        focused -> TvTheme.SurfaceSoft
                                        else -> TvTheme.Surface.copy(alpha = 0.5f)
                                    }
                                )
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("${index + 1}", color = if (isCurrent) TvTheme.HiResGold else TvTheme.TextSecondary, fontSize = metrics.caption, modifier = Modifier.width(36.dp))
                            TvArtwork(url = item.track.coverArtUrl, size = metrics.miniArt)
                            Spacer(Modifier.width(14.dp))
                            Column(Modifier.weight(1f)) {
                                Text(item.track.title, color = TvTheme.Text, fontSize = metrics.cardTitle, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(item.track.artist, color = TvTheme.TextSecondary, fontSize = metrics.cardSubtitle, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            if (isCurrent) Text("Now", color = TvTheme.HiResGold, fontSize = metrics.caption, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TvLocalSongRow(
    title: String,
    subtitle: String? = null,
    songs: List<LocalSongEntity>,
    metrics: TvMetrics,
    onPlay: (LocalSongEntity) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        TvSectionHeader(title = title, metrics = metrics, subtitle = subtitle)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(20.dp), contentPadding = PaddingValues(vertical = 6.dp)) {
            items(songs.take(40), key = { it.id }) { song ->
                TvAlbumCard(
                    title = song.title,
                    artist = song.artist,
                    artworkUrl = song.artworkUrl,
                    badge = "Hi-Res",
                    metrics = metrics,
                    onClick = { onPlay(song) }
                )
            }
        }
    }
}

@Composable
private fun TvSourceTrackRow(
    title: String,
    subtitle: String? = null,
    tracks: List<SourceSearchResult>,
    metrics: TvMetrics,
    onPlay: (SourceSearchResult) -> Unit,
    onPlayTrackAtIndex: ((List<SourceSearchResult>, Int) -> Unit)? = null,
    featuredFirst: Boolean = false
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        TvSectionHeader(title = title, metrics = metrics, subtitle = subtitle)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(20.dp), contentPadding = PaddingValues(vertical = 6.dp)) {
            itemsIndexed(tracks.take(24), key = { _, t -> t.id + t.providerId }) { index, track ->
                val isNew = track.discoveryKind == "new_release" || (track.releaseDate?.startsWith("2026") == true)
                val bannerText = when {
                    featuredFirst && index == 0 -> "FEATURED"
                    isNew -> "NEW DROP"
                    else -> null
                }
                TvAlbumCard(
                    title = track.title,
                    artist = track.artist,
                    artworkUrl = track.artworkUrl,
                    badge = sourceQualityBadge(track),
                    metrics = metrics,
                    onClick = {
                        if (onPlayTrackAtIndex != null) {
                            onPlayTrackAtIndex(tracks, index)
                        } else {
                            onPlay(track)
                        }
                    },
                    banner = bannerText
                )
            }
        }
    }
}

@Composable
private fun TvPlaylistCardRow(
    title: String,
    subtitle: String? = null,
    playlists: List<GatewayHomePlaylist>,
    metrics: TvMetrics,
    onOpen: (GatewayHomePlaylist) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        TvSectionHeader(title = title, metrics = metrics, subtitle = subtitle)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(20.dp), contentPadding = PaddingValues(vertical = 6.dp)) {
            items(playlists.take(24), key = { it.id }) { playlist ->
                TvAlbumCard(
                    title = playlist.name,
                    artist = debrandCuratorLabel(playlist.curator),
                    artworkUrl = playlist.artworkUrl,
                    badge = "Hi-Res",
                    metrics = metrics,
                    onClick = { onOpen(playlist) }
                )
            }
        }
    }
}

private fun sourceQualityBadge(track: SourceSearchResult): String = when {
    track.isDolbyAtmos || track.atmosMixAvailable -> if (track.hasTidal) "Tidal · Atmos" else "Dolby Atmos"
    track.isSony360RealityAudio -> "360 Reality"
    track.isHiRes || track.hasQobuz -> if (track.hasQobuz) "Qobuz · Hi-Res" else "Hi-Res"
    track.hasAmazon -> "Amazon HD"
    track.hasTidal -> "Tidal FLAC"
    else -> "Lossless"
}

private fun tvQualityLabel(info: VantaQualityInfo?): String? {
    if (info == null) return null
    info.label?.takeIf { it.isNotBlank() }?.let { return it }
    info.format?.takeIf { it.isNotBlank() }?.let { return it }
    return when {
        info.isDolbyAtmos -> "Dolby Atmos"
        info.isHiRes == true -> "Hi-Res AUDIO"
        info.isLossless == true -> "Hi-Res AUDIO"
        else -> null
    }
}

private fun formatMs(ms: Long): String {
    val totalSec = (ms / 1000L).coerceAtLeast(0L)
    val m = totalSec / 60L
    val s = totalSec % 60L
    return "%d:%02d".format(m, s)
}
