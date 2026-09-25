package com.audiophile.musicplayer.ui

import androidx.activity.compose.BackHandler
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.foundation.background
import androidx.compose.ui.res.vectorResource
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.graphics.Brush
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.remember
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.Canvas
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.audiophile.musicplayer.data.dj.StationSearchResolver
import com.audiophile.musicplayer.data.canonical.CanonicalAlbum
import com.audiophile.musicplayer.data.canonical.CanonicalArtist
import com.audiophile.musicplayer.data.canonical.CanonicalPlaylist
import com.audiophile.musicplayer.data.canonical.CanonicalTrack
import com.audiophile.musicplayer.data.display.DisplayMetadataCleaner
import com.audiophile.musicplayer.data.display.TrackDisplayResolver
import com.audiophile.musicplayer.data.display.VantaQualityInfo
import com.audiophile.musicplayer.data.local.entities.UnifiedTrackWithSources
import com.audiophile.musicplayer.data.source.SearchItemStatus
import com.audiophile.musicplayer.data.source.canEnterPlaybackFlow
import com.audiophile.musicplayer.data.source.isConfirmedPlayable
import com.audiophile.musicplayer.data.source.isUnavailable
import com.audiophile.musicplayer.data.source.sourceValidityStatus
import com.audiophile.musicplayer.data.source.userFacingLabel
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.ui.draw.clipToBounds

@Composable
fun SearchScreen(
    uiState: SearchUiState,
    library: List<UnifiedTrackWithSources>,
    onQueryChanged: (String) -> Unit,
    onSearch: () -> Unit,
    onSearchQuery: (String) -> Unit,
    onBrowseCategory: (String) -> Unit,
    onRememberSearch: (String) -> Unit,
    onRemoveRecentSearch: (String) -> Unit,
    onClearRecentSearches: () -> Unit,
    onPlay: (UnifiedTrackWithSources) -> Unit,
    onPlaySourceResult: (CanonicalTrack) -> Unit,
    onPlaySourceResultList: ((List<CanonicalTrack>, Int) -> Unit)? = null,
    onSaveSourceResult: (CanonicalTrack) -> Unit,
    onNavigateToArtist: (String, String?) -> Unit = { _, _ -> },
    onNavigateToAlbum: (String, String, String?) -> Unit = { _, _, _ -> },
    onNavigateToStation: (String) -> Unit = {},
    onStartStation: (String) -> Unit = {},
    onOpenTrackSheet: ((CanonicalTrack) -> Unit)? = null,
    onOpenCatalogPlaylist: (CanonicalPlaylist) -> Unit = {},
    onQuickPlay: (String) -> Unit = {},
    onImportEclipsePlaylist: (String) -> Unit = {},

    miniPlayerVisible: Boolean = false,
    bottomNavVisible: Boolean = false,
    isKeyboardVisible: Boolean = false
) {
    val recentSearches = uiState.searchHistory
    val dismissKeyboard = rememberKeyboardDismissal()
    val mergedSearchSuggestions = remember(
        uiState.query,
        recentSearches,
        uiState.suggestions
    ) {
        val normalizedQuery = uiState.query.trim()
        if (normalizedQuery.length < 2) {
            emptyList()
        } else {
            (
                recentSearches.filter { term ->
                    com.audiophile.musicplayer.search.MusicTypeahead.matchesPrefix(term, normalizedQuery)
                } + uiState.suggestions
            ).distinctBy { it.lowercase() }
                .filterNot { it.equals(normalizedQuery, ignoreCase = true) }
                .take(6)
        }
    }
    val predictionRemainder = remember(uiState.query, mergedSearchSuggestions) {
        mergedSearchSuggestions.firstNotNullOfOrNull { suggestion ->
            com.audiophile.musicplayer.search.MusicTypeahead.completionRemainder(uiState.query, suggestion)
        }
    }

    val addRecentSearch = onRememberSearch
    val removeRecentSearch = onRemoveRecentSearch
    val clearAllRecentSearches = onClearRecentSearches

    Column(
        modifier = Modifier
            .fillMaxSize()
            .dismissKeyboardOnScroll()
            .imePadding()
            .padding(horizontal = 24.dp)
            .padding(
                top = appTopContentPadding(),
                bottom = if (isKeyboardVisible) 0.dp
                else appBottomContentPadding(isMiniPlayerVisible = miniPlayerVisible, isBottomNavVisible = bottomNavVisible)
            ),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        if (uiState.query.isBlank() && uiState.activeBrowseCategory == null) Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = "EXPLORE VANTA",
                style = VantaType.caption.copy(
                    color = AppAccentSecondary,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.8.sp
                )
            )
            Text(
                text = "Search",
                style = VantaType.pageTitle.copy(fontSize = 30.sp, lineHeight = 34.sp)
            )
            Text(
                text = "Find the song. Follow the feeling.",
                style = VantaType.subtitle.copy(color = AppTextSecondary.copy(alpha = 0.76f))
            )
        }

        VantaSearchField(
            query = uiState.query,
            predictionRemainder = predictionRemainder,
            onQueryChanged = onQueryChanged,
            onSearch = {
                dismissKeyboard()
                if (uiState.query.isNotBlank()) {
                    addRecentSearch(uiState.query)
                }
                onSearch()
            },
            onAcceptPrediction = {
                val completed = uiState.query.trimStart() + (predictionRemainder ?: "")
                if (completed.isNotBlank()) onSearchQuery(completed)
            },
            modifier = Modifier.fillMaxWidth()
        )

        if (mergedSearchSuggestions.isNotEmpty() &&
            uiState.query.trim().isNotEmpty() &&
            uiState.activeBrowseCategory == null
        ) {
            SearchPredictiveList(
                query = uiState.query,
                suggestions = mergedSearchSuggestions,
                onSelect = onSearchQuery
            )
        }

        if (isKeyboardVisible) {
            androidx.compose.material3.TextButton(
                onClick = dismissKeyboard,
                modifier = Modifier.align(Alignment.End)
            ) { Text("Done", color = AppAccent) }
        }

        val isUrl = remember(uiState.query) {
            val trimmed = uiState.query.trim()
            trimmed.startsWith("http://") || trimmed.startsWith("https://")
        }
        if (isUrl) {
            androidx.compose.material3.Button(
                onClick = {
                    addRecentSearch(uiState.query)
                    onQuickPlay(uiState.query.trim())
                },
                modifier = Modifier.fillMaxWidth().height(44.dp),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(14.dp),
                colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                    containerColor = AppAccent.copy(alpha = 0.25f)
                )
            ) {
                androidx.compose.material3.Text(
                    "Quick Play from Link",
                    color = AppAccent,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                    fontSize = 14.sp
                )
            }
        }

        val trimmedQuery = uiState.query.trim()
        val activeBrowseCategory = uiState.activeBrowseCategory

        val matchedArtist = remember(trimmedQuery, uiState.artists, uiState.songs) {
            com.audiophile.musicplayer.search.SearchPresentation.artistMatch(trimmedQuery, uiState.artists, uiState.songs)
        }

        BackHandler(enabled = activeBrowseCategory != null) {
            onQueryChanged("")
        }

        when {
            trimmedQuery.isEmpty() && activeBrowseCategory == null -> {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    // Recent Searches
                    if (recentSearches.isNotEmpty()) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Recent Searches",
                                    color = AppText,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Clear All",
                                    color = AppAccent,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.clickable { clearAllRecentSearches() }
                                )
                            }
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                items(recentSearches) { term ->
                                    Row(
                                        modifier = Modifier.clip(RoundedCornerShape(24.dp))
                                            .background(AppSurfaceSoft)
                                            .border(0.5.dp, AppOutline, RoundedCornerShape(24.dp)),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            modifier = Modifier.heightIn(min = 48.dp).widthIn(min = 64.dp)
                                                .clickable {
                                                    addRecentSearch(term)
                                                    val station = StationSearchResolver.resolveForSearch(term).firstOrNull()
                                                    if (isBrowseCategory(term)) onBrowseCategory(term)
                                                    else if (station != null) onNavigateToStation(station.id)
                                                    else onSearchQuery(term)
                                                }.padding(start = 16.dp, end = 8.dp),
                                            contentAlignment = Alignment.Center
                                        ) { Text(term, color = AppText, fontSize = 13.sp) }
                                        IconButton(onClick = { removeRecentSearch(term) }) {
                                            Icon(Icons.Filled.Close, contentDescription = "Remove $term from recent searches",
                                                tint = AppTextSecondary, modifier = Modifier.size(18.dp))
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Browse Categories
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            text = "Browse Categories",
                            color = AppText,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                        
                        val groups = remember { com.audiophile.musicplayer.data.catalog.BrowseCatalog.categories.groupBy { it.group } }
                        groups.entries.forEachIndexed { groupIndex, (group, categories) ->
                            Text(group, color = AppTextSecondary, fontSize = 16.sp, fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(top = 14.dp))
                            categories.chunked(2).forEachIndexed { rowIndex, row ->
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    row.forEachIndexed { index, category ->
                                        val colors = listOf(Color(0xFFAF526F), Color(0xFF497F85), Color(0xFF8268A6), Color(0xFFAE7943))
                                        val color = colors[(groupIndex + rowIndex + index) % colors.size]
                                        CategoryCard(SearchCategory(category.title, color, color.copy(alpha = 0.8f)),
                                            onClick = { addRecentSearch(category.title); onBrowseCategory(category.title) },
                                            modifier = Modifier.weight(1f))
                                    }
                                    if (row.size == 1) Spacer(Modifier.weight(1f))
                                }
                            }
                        }
                    }
                }
            }
            uiState.isSearching &&
                trimmedQuery.isNotEmpty() &&
                uiState.songs.isEmpty() &&
                uiState.libraryMatches.isEmpty() &&
                uiState.matchedStations.isEmpty() &&
                activeBrowseCategory == null -> {
                Column(
                    modifier = Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    SearchSectionLabel("Searching Sources")
                    SearchShimmer()
                }
            }
            activeBrowseCategory != null -> {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    BrowseCategoryResults(
                        category = activeBrowseCategory,
                        songs = uiState.songs,
                        albums = uiState.albums,
                        artists = uiState.artists,
                        isLoading = uiState.isSearching,
                        onRetry = { onBrowseCategory(activeBrowseCategory) },
                        onBack = { onQueryChanged("") },
                        onPlaySourceResult = onPlaySourceResult,
                        onSaveSourceResult = onSaveSourceResult,
                        onNavigateToArtist = onNavigateToArtist,
                        onNavigateToAlbum = onNavigateToAlbum,
                        onOpenTrackSheet = onOpenTrackSheet,
                        onStartStation = onStartStation
                    )
                }
            }
            uiState.songs.isEmpty() &&
                uiState.albums.isEmpty() &&
                uiState.artists.isEmpty() &&
                uiState.playlists.isEmpty() &&
                uiState.libraryMatches.isEmpty() &&
                mergedSearchSuggestions.isEmpty() &&
                uiState.matchedStations.isEmpty() -> {
                VantaEmptyState(
                    title = "No Results",
                    description = if (isUrl && !uiState.statusMessage.isNullOrBlank()) {
                        uiState.statusMessage
                    } else "No full-track matches found for \"$trimmedQuery\". Check your source connections or try a different query.",
                    icon = Icons.Filled.Search
                )
            }
            else -> {
                val songs = remember(uiState.songs) { com.audiophile.musicplayer.search.SearchPresentation.songs(uiState.songs) }
                val localTracks = uiState.libraryMatches
                val albums = remember(uiState.albums, songs, matchedArtist) {
                    com.audiophile.musicplayer.search.SearchPresentation.albums(uiState.albums, songs, matchedArtist)
                }
                val artists = (listOfNotNull(matchedArtist) + uiState.artists)
                    .distinctBy { com.audiophile.musicplayer.search.SearchPresentation.key(it.name) }
                val playlists = remember(uiState.playlists) {
                    com.audiophile.musicplayer.search.SearchPresentation.playlists(uiState.playlists)
                }
                val matchedStations = uiState.matchedStations
                var selectedSearchTab by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf("All") }
                val searchTabs = listOf("All", "Artists", "Albums", "Songs", "Playlists")

                androidx.compose.runtime.LaunchedEffect(trimmedQuery) {
                    selectedSearchTab = "All"
                }

                val searchListState = rememberLazyListState()
                androidx.compose.runtime.LaunchedEffect(trimmedQuery, selectedSearchTab, uiState.isSearching) {
                    if (!uiState.isSearching) searchListState.scrollToItem(0)
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 28.dp)
                ) {
                    if (trimmedQuery.isNotEmpty() && (songs.isNotEmpty() || artists.isNotEmpty() || albums.isNotEmpty() || playlists.isNotEmpty())) {
                        androidx.compose.material3.ScrollableTabRow(
                            selectedTabIndex = searchTabs.indexOf(selectedSearchTab),
                            containerColor = Color.Transparent,
                            contentColor = AppAccent,
                            edgePadding = 0.dp,
                            divider = {},
                            indicator = { tabPositions ->
                                if (searchTabs.indexOf(selectedSearchTab) < tabPositions.size) {
                                    androidx.compose.material3.TabRowDefaults.SecondaryIndicator(
                                        Modifier.tabIndicatorOffset(tabPositions[searchTabs.indexOf(selectedSearchTab)]),
                                        height = 2.dp,
                                        color = AppAccent
                                    )
                                }
                            }
                        ) {
                            searchTabs.forEach { tab ->
                                androidx.compose.material3.Tab(
                                    selected = selectedSearchTab == tab,
                                    onClick = { selectedSearchTab = tab },
                                    text = { Text(tab, color = if (selectedSearchTab == tab) AppAccent else AppTextSecondary, fontWeight = if (selectedSearchTab == tab) FontWeight.Bold else FontWeight.Medium) }
                                )
                            }
                        }
                    }

                    LazyColumn(
                        state = searchListState,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        contentPadding = PaddingValues(bottom = 24.dp)
                    ) {
                    // Suggestions sit under the search field while typing.

                    if (selectedSearchTab == "All" && matchedArtist != null) {
                        item(key = "artist_hero") {
                            SearchArtistHero(matchedArtist,
                                onOpen = { onNavigateToArtist(matchedArtist.name, matchedArtist.id) },
                                onRadio = { onStartStation(matchedArtist.name) })
                        }
                    } else if (selectedSearchTab == "All" && uiState.topResult != null) {
                        item(key = "top_result") {
                            val track = uiState.topResult
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                SearchSectionLabel("Top result")
                                TopResultCard(
                                    track = track,
                                    onPlay = {
                                        if (onPlaySourceResultList != null) {
                                            onPlaySourceResultList(listOf(track) + songs.filter { it.externalTrackId != track.externalTrackId }, 0)
                                        } else {
                                            onPlaySourceResult(track)
                                        }
                                    },
                                    onSave = onSaveSourceResult,
                                    onNavigateToArtist = onNavigateToArtist,
                                    onNavigateToAlbum = onNavigateToAlbum,
                                    onStartStation = { onStartStation("${track.title} by ${track.artist}") }
                                )
                            }
                        }
                    }

                    if ((selectedSearchTab == "All" && matchedArtist != null || selectedSearchTab == "Albums") && albums.isNotEmpty()) {
                        item(key = "artist_albums") {
                            SearchResultsHeading("Albums & singles", if (selectedSearchTab == "All") "See all" else null,
                                onMore = { selectedSearchTab = "Albums" })
                            Spacer(Modifier.height(12.dp))
                            if (selectedSearchTab == "All") LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                                items(albums.take(6)) { album ->
                                    SearchAlbumCard(album, onNavigateToAlbum = { onNavigateToAlbum(album.title, album.artist, album.artworkUrl) },
                                        showStationAction = false)
                                }
                            }
                        }
                    }

                    if (selectedSearchTab == "Albums") {
                        item(key = "release_count") { Text("${albums.size} releases", color = AppTextSecondary, fontSize = 12.sp) }
                        items(albums.chunked(2)) { row ->
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                row.forEach { album ->
                                    SearchAlbumCard(album, onNavigateToAlbum = { onNavigateToAlbum(album.title, album.artist, album.artworkUrl) },
                                        showStationAction = false)
                                }
                            }
                        }
                    }

                    if ((selectedSearchTab == "All" || selectedSearchTab == "Playlists") && playlists.isNotEmpty()) {
                        item(key = "playlists") {
                            SearchResultsHeading(
                                "Playlists",
                                if (selectedSearchTab == "All" && playlists.size > 6) "See all" else null,
                                onMore = { selectedSearchTab = "Playlists" }
                            )
                            Spacer(Modifier.height(12.dp))
                            if (selectedSearchTab == "All") {
                                LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                                    items(playlists.take(6), key = { it.id ?: it.title }) { playlist ->
                                        SearchPlaylistCard(playlist, onOpen = { onOpenCatalogPlaylist(playlist) })
                                    }
                                }
                            }
                        }
                        if (selectedSearchTab == "Playlists") {
                            items(playlists.chunked(2), key = { row -> row.joinToString("|") { it.id ?: it.title } }) { row ->
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                                    row.forEach { playlist ->
                                        SearchPlaylistCard(
                                            playlist,
                                            onOpen = { onOpenCatalogPlaylist(playlist) },
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                    if (row.size == 1) Spacer(Modifier.weight(1f))
                                }
                            }
                        }
                    }

                    if ((selectedSearchTab == "All" || selectedSearchTab == "Songs") && songs.isNotEmpty()) {
                        item(key = "songs_label") {
                            SearchResultsHeading(if (matchedArtist != null) "Top songs" else "Songs",
                                if (selectedSearchTab == "All" && songs.size > 5) "See all" else null,
                                onMore = { selectedSearchTab = "Songs" })
                        }
                        val visibleSongs = if (selectedSearchTab == "All") songs.take(5) else songs
                        items(visibleSongs.size, key = { "song_$it" }) { index ->
                            val track = visibleSongs[index]
                            SearchSongRow(
                                track = track,
                                onPlay = {
                                    if (onPlaySourceResultList != null) {
                                        onPlaySourceResultList(visibleSongs, index)
                                    } else {
                                        onPlaySourceResult(track)
                                    }
                                },
                                onNavigateToArtist = onNavigateToArtist,
                                onNavigateToAlbum = onNavigateToAlbum,
                                onSave = onSaveSourceResult,
                                onOpenTrackSheet = onOpenTrackSheet?.let { fn -> { fn(track) } },
                                onStartStation = { onStartStation("${track.title} by ${track.artist}") }
                            )
                        }
                    }

                    if ((selectedSearchTab == "All" && matchedArtist == null || selectedSearchTab == "Artists") && artists.isNotEmpty()) {
                        item(key = "artists") {
                            SearchResultsHeading("Artists", if (selectedSearchTab == "All") "See all" else null,
                                onMore = { selectedSearchTab = "Artists" })
                            Spacer(Modifier.height(12.dp))
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                items(if (selectedSearchTab == "All") artists.take(5) else artists) { artist ->
                                    SearchArtistCard(artist, onNavigateToArtist = { onNavigateToArtist(artist.name, artist.id) },
                                        showStationAction = false)
                                }
                            }
                        }
                    }
                    if (selectedSearchTab == "All" && matchedArtist == null && albums.isNotEmpty()) {
                        item(key = "albums") {
                            SearchResultsHeading("Albums & singles", "See all", onMore = { selectedSearchTab = "Albums" })
                            Spacer(Modifier.height(12.dp))
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                                items(albums.take(6)) { album ->
                                    SearchAlbumCard(album, onNavigateToAlbum = { onNavigateToAlbum(album.title, album.artist, album.artworkUrl) },
                                        showStationAction = false)
                                }
                            }
                        }
                    }

                    // Library Matches
                    if ((selectedSearchTab == "All" || selectedSearchTab == "Songs") && localTracks.isNotEmpty()) {
                        item(key = "library_label") {
                            SearchSectionLabel("Library")
                        }
                        localTracks.take(5).forEachIndexed { idx, track ->
                            item(key = "library_$idx") {
                                LibrarySongRow(
                                    track = track,
                                    onPlay = { onPlay(track) },
                                    onNavigateToArtist = onNavigateToArtist,
                                    onNavigateToAlbum = onNavigateToAlbum,
                                    onStartStation = { onStartStation("${track.track.title} by ${track.track.artist}") }
                                )
                            }
                        }
                    }

                    if (selectedSearchTab == "All" && !uiState.isSearching && trimmedQuery.isNotBlank()) {
                        item(key = "search_station_action") {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                SearchSectionLabel("Explore More")
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .searchResultSurface(featured = false)
                                        .clickable { onStartStation(trimmedQuery) }
                                        .padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(48.dp)
                                            .clip(CircleShape)
                                            .background(AppAccent.copy(alpha = 0.12f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            ImageVector.vectorResource(id = com.audiophile.musicplayer.R.drawable.ic_radio),
                                            contentDescription = null,
                                            tint = AppAccent,
                                            modifier = Modifier.size(24.dp)
                                        )
                                    }
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            "Start a station from $trimmedQuery",
                                            color = AppText,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 16.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            "Build a related mix from this search",
                                            color = AppTextSecondary,
                                            fontSize = 13.sp
                                        )
                                    }
                                    Icon(
                                        Icons.Filled.PlayArrow,
                                        contentDescription = "Start station",
                                        tint = AppAccent,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    }

                    if (selectedSearchTab == "All" && !uiState.isSearching && matchedStations.isNotEmpty()) {
                        item(key = "matching_stations") {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                SearchSectionLabel("Matching Stations")
                                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    matchedStations.forEach { station ->
                                        RadioStationSearchCard(
                                            station = station,
                                            onOpen = { onNavigateToStation(station.id) },
                                            onStart = { onStartStation(station.id) }
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
        }
    }
}

@Composable
private fun BrowseCategoryResults(
    category: String,
    songs: List<CanonicalTrack>,
    albums: List<CanonicalAlbum>,
    artists: List<CanonicalArtist>,
    isLoading: Boolean,
    onRetry: () -> Unit,
    onBack: () -> Unit,
    onPlaySourceResult: (CanonicalTrack) -> Unit,
    onSaveSourceResult: (CanonicalTrack) -> Unit,
    onNavigateToArtist: (String, String?) -> Unit,
    onNavigateToAlbum: (String, String, String?) -> Unit,
    onOpenTrackSheet: ((CanonicalTrack) -> Unit)?,
    onStartStation: (String) -> Unit
) {
    androidx.compose.runtime.key(category) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp))
                        .background(Brush.linearGradient(listOf(AppAccent.copy(alpha = 0.22f), AppSurfaceRaised)))
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to categories", tint = AppText)
                    }
                    Text(category, color = AppText, fontSize = 32.sp, fontWeight = FontWeight.Bold)
                    Text("Find your next favorite", color = AppTextSecondary, fontSize = 15.sp)
                }
            }
            if (isLoading) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Finding the music...", color = AppTextSecondary)
                        SearchShimmer()
                    }
                }
            } else if (songs.isEmpty()) {
                item {
                    VantaEmptyState(title = "Couldn’t load this category",
                        description = "We couldn't load $category right now. Try again in a moment.",
                        icon = Icons.Filled.Search)
                    androidx.compose.material3.TextButton(onClick = onRetry) { Text("Try again", color = AppAccent) }
                }
            } else {
                if (albums.isNotEmpty()) item {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        SearchSectionLabel("Albums to explore")
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                            items(albums) { album ->
                                SearchAlbumCard(album, onNavigateToAlbum = {
                                    onNavigateToAlbum(album.title, album.artist, album.artworkUrl)
                                }, showStationAction = false)
                            }
                        }
                    }
                }
                if (artists.isNotEmpty()) item {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        SearchSectionLabel("Artists to explore")
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                            items(artists) { artist ->
                                SearchArtistCard(artist, onNavigateToArtist = {
                                    onNavigateToArtist(artist.name, artist.id)
                                }, showStationAction = false)
                            }
                        }
                    }
                }
                item { SearchSectionLabel("Songs") }
                items(songs) { track ->
                    SearchSongRow(track = track, onPlay = onPlaySourceResult,
                        onSave = onSaveSourceResult, onNavigateToArtist = onNavigateToArtist,
                        onNavigateToAlbum = onNavigateToAlbum,
                        onOpenTrackSheet = onOpenTrackSheet?.let { fn -> { fn(track) } },
                        onStartStation = { onStartStation("${track.title} by ${track.artist}") })
                }
            }
        }
    }
}

@Composable
private fun VantaSearchField(
    query: String,
    predictionRemainder: String?,
    onQueryChanged: (String) -> Unit,
    onSearch: () -> Unit,
    onAcceptPrediction: () -> Unit,
    modifier: Modifier = Modifier
) {
    val hasText = query.isNotBlank()
    val fieldStyle = TextStyle(
        fontFamily = com.audiophile.musicplayer.ui.theme.VantaSans,
        color = AppText,
        fontSize = 16.sp,
        fontWeight = FontWeight.Medium
    )
    BasicTextField(
        value = query,
        onValueChange = onQueryChanged,
        modifier = modifier,
        singleLine = true,
        textStyle = fieldStyle,
        cursorBrush = SolidColor(AppAccent),
        keyboardOptions = KeyboardOptions(
            imeAction = ImeAction.Search,
            keyboardType = androidx.compose.ui.text.input.KeyboardType.Text
        ),
        keyboardActions = KeyboardActions(
            onSearch = {
                onSearch()
            }
        ),
        decorationBox = { innerTextField ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp)
                    .shadow(
                        elevation = if (hasText) 10.dp else 4.dp,
                        shape = RoundedCornerShape(VantaRadius.searchField),
                        ambientColor = if (hasText) AppAccentGlow else Color.Transparent,
                        spotColor = Color.Black.copy(alpha = 0.25f)
                    )
                    .velvetChrome(RoundedCornerShape(VantaRadius.searchField))
                    .border(
                        width = if (hasText) 0.75.dp else 0.5.dp,
                        brush = Brush.verticalGradient(
                            colors = if (hasText) {
                                listOf(AppAccent.copy(alpha = 0.45f), AppOutline)
                            } else {
                                listOf(AppOutline, AppOutline.copy(alpha = 0.5f))
                            }
                        ),
                        shape = RoundedCornerShape(VantaRadius.searchField)
                    )
                    .padding(start = 18.dp, end = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    Icons.Filled.Search,
                    contentDescription = null,
                    tint = AppTextSecondary.copy(alpha = 0.86f),
                    modifier = Modifier.size(21.dp)
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .padding(vertical = 15.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    if (!hasText) {
                        Text(
                            "Artists, songs, albums…",
                            color = AppTextMuted,
                            fontSize = 15.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    } else if (!predictionRemainder.isNullOrEmpty()) {
                        Text(
                            text = androidx.compose.ui.text.buildAnnotatedString {
                                pushStyle(androidx.compose.ui.text.SpanStyle(color = Color.Transparent))
                                append(query)
                                pop()
                                pushStyle(androidx.compose.ui.text.SpanStyle(color = AppTextMuted.copy(alpha = 0.55f)))
                                append(predictionRemainder)
                                pop()
                            },
                            style = fieldStyle,
                            maxLines = 1,
                            overflow = TextOverflow.Clip
                        )
                    }
                    innerTextField()
                }
                if (hasText) {
                    IconButton(
                        onClick = { onQueryChanged("") },
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            Icons.Filled.Close,
                            contentDescription = "Clear",
                            tint = AppTextSecondary,
                            modifier = Modifier.size(19.dp)
                        )
                    }
                }
            }
        }
    )
}

@Composable
private fun SearchPredictiveList(
    query: String,
    suggestions: List<String>,
    onSelect: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(VantaRadius.card))
            .background(AppSurfaceRaised.copy(alpha = 0.82f))
            .padding(vertical = 4.dp)
    ) {
        suggestions.forEach { suggestion ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSelect(suggestion) }
                    .padding(horizontal = 14.dp, vertical = 11.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Search,
                    contentDescription = null,
                    tint = AppTextSecondary,
                    modifier = Modifier.size(18.dp)
                )
                val remainder = com.audiophile.musicplayer.search.MusicTypeahead.completionRemainder(query, suggestion)
                if (remainder != null) {
                    Text(
                        text = androidx.compose.ui.text.buildAnnotatedString {
                            append(query)
                            pushStyle(androidx.compose.ui.text.SpanStyle(color = AppTextMuted))
                            append(remainder)
                            pop()
                        },
                        color = AppText,
                        fontSize = 15.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                } else {
                    Text(
                        suggestion,
                        color = AppText,
                        fontSize = 15.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun SearchSectionLabel(title: String) {
    Text(
        text = title,
        color = AppText,
        fontSize = 18.sp,
        fontWeight = FontWeight.SemiBold
    )
}

private fun Modifier.searchResultSurface(featured: Boolean = false): Modifier {
    val shape = RoundedCornerShape(if (featured) VantaRadius.largeCard else VantaRadius.card)
    return if (featured) {
        this
            .shadow(
                elevation = 12.dp,
                shape = shape,
                ambientColor = AppAccentGlow,
                spotColor = Color.Black.copy(alpha = 0.35f)
            )
            .luxuryCard(shape)
    } else {
        this
            .clip(shape)
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        AppSurfaceRaised.copy(alpha = 0.72f),
                        AppSurface.copy(alpha = 0.55f)
                    )
                )
            )
            .border(0.5.dp, AppOutline, shape)
    }
}

private fun artistLineWithFeatures(primaryArtist: String, featuredArtists: List<String>): String {
    val primary = primaryArtist.trim()
    val normalizedPrimary = primary.lowercase()
    val features = featuredArtists
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .distinctBy { it.lowercase() }
        .filterNot { feature ->
            val normalizedFeature = feature.lowercase()
            normalizedFeature == normalizedPrimary || normalizedPrimary.contains(normalizedFeature)
        }
        .map { it.toDisplayArtistName() }

    return if (features.isEmpty()) primary else "$primary feat. ${features.joinToString(", ")}"
}

private fun String.toDisplayArtistName(): String {
    return trim()
        .split(Regex("\\s+"))
        .filter { it.isNotBlank() }
        .joinToString(" ") { part ->
            part.replaceFirstChar { char ->
                if (char.isLowerCase()) char.titlecase() else char.toString()
            }
        }
}

@Composable
private fun SearchSubtitleLine(
    artist: String,
    album: String?,
    onArtistClick: () -> Unit,
    onAlbumClick: (() -> Unit)?
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = artist,
            color = AppTextSecondary,
            fontSize = 13.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f, fill = false)
                .clickable(onClick = onArtistClick)
        )
        if (!album.isNullOrBlank() && !album.equals(artist, ignoreCase = true) && onAlbumClick != null) {
            Text(
                text = "\u2022",
                color = AppTextMuted,
                fontSize = 13.sp
            )
            Text(
                text = album,
                color = AppTextSecondary,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f, fill = false)
                    .clickable(onClick = onAlbumClick)
            )
        }
    }
}

/** A free catalog can identify a spatial release, but it cannot prove the relay's output codec. */
private fun spatialBadgeLabel(base: String, evidence: String?): String = when (evidence?.lowercase()) {
    "verified" -> base
    "rendered" -> "Rendered Spatial"
    "stereo" -> "Stereo"
    "catalog" -> "$base catalog"
    else -> "$base metadata"
}

@Composable
private fun SearchIconAction(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    tint: Color = AppTextSecondary,
    emphasized: Boolean = false,
    enabled: Boolean = true
) {
    val shape = CircleShape
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(shape)
            .background(
                if (emphasized) {
                    AppAccent.copy(alpha = if (enabled) 0.14f else 0.08f)
                } else {
                    Color.White.copy(alpha = if (enabled) 0.035f else 0.02f)
                }
            )
            .border(
                width = 0.5.dp,
                color = if (emphasized) {
                    AppAccent.copy(alpha = if (enabled) 0.18f else 0.10f)
                } else {
                    Color.White.copy(alpha = if (enabled) 0.06f else 0.03f)
                },
                shape = shape
            )
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = if (enabled) tint else tint.copy(alpha = 0.45f),
            modifier = Modifier.size(if (emphasized) 21.dp else 18.dp)
        )
    }
}

@Composable
private fun TopResultCard(
    track: CanonicalTrack,
    onPlay: (CanonicalTrack) -> Unit,
    onNavigateToArtist: (String, String?) -> Unit,
    onNavigateToAlbum: (String, String, String?) -> Unit,
    onSave: (CanonicalTrack) -> Unit = {},
    onStartStation: () -> Unit = {}
) {
    val sourceStatus = remember(track.sourceStatus) { track.sourceStatus ?: SearchItemStatus.METADATA_ONLY }
    val canPlay = remember(sourceStatus) { sourceStatus.canEnterPlaybackFlow() }
    val statusLabel = remember(sourceStatus) {
        sourceStatus
            .takeIf { !it.canEnterPlaybackFlow() || it.isUnavailable() }
            ?.userFacingLabel()
    }
    val cleaned = remember(track.title, track.artist) {
        DisplayMetadataCleaner.computeDisplayMetadata(track.title, track.artist, track.album, explicit = track.explicit)
    }
    val dTitle = cleaned.title.ifBlank { track.displayTitle }
    val dArtist = cleaned.artist.ifBlank { track.displayArtist }
    val dArtistLine = remember(dArtist, track.featuredArtists) {
        artistLineWithFeatures(dArtist, track.featuredArtists)
    }
    val dAlbum = track.album

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .searchResultSurface(featured = true)
            .clickable(enabled = canPlay) { onPlay(track) }
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(58.dp)
                .clip(RoundedCornerShape(13.dp))
                .border(0.5.dp, AppOutline.copy(alpha = 0.65f), RoundedCornerShape(13.dp))
        ) {
            NetworkArtwork(
                artworkUrl = track.artworkUrl,
                seed = dTitle,
                modifier = Modifier.fillMaxSize()
            )
        }

        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    dTitle,
                    color = AppText,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (cleaned.explicit == true) VantaExplicitBadge()
                TitleSpatialIndicator(
                    qualityInfo = track.qualityInfo,
                    atmosMixAvailable = track.atmosMixAvailable
                )
                if (statusLabel != null) {
                    track.qualityInfo?.let { q ->
                        if (spatialIdentityKind(q) == null && !track.atmosMixAvailable) {
                            if (q.isSpatialAudio) VantaStatusBadge(spatialBadgeLabel("Spatial", q.spatialEvidence), AppAccent)
                            else if (q.isSurround) VantaStatusBadge(spatialBadgeLabel("Surround", q.spatialEvidence), AppAccent)
                            if (q.isHiRes == true) VantaStatusBadge("Hi-Res", AppAccent)
                        }
                    }
                    VantaStatusBadge(
                        text = statusLabel,
                        color = if (sourceStatus.isUnavailable()) AppWarning else AppTextSecondary
                    )
                } else if (sourceStatus != SearchItemStatus.PREVIEW) {
                    val q = track.qualityInfo
                    if ((q == null || spatialIdentityKind(q) == null) && !track.atmosMixAvailable) {
                        VantaQualityBadge(q?.compactQualityLabel())
                    }
                }
            }
            SearchSubtitleLine(
                artist = dArtistLine,
                album = dAlbum,
                onArtistClick = { onNavigateToArtist(dArtist, null) },
                onAlbumClick = dAlbum?.let { { onNavigateToAlbum(it, dArtist, track.artworkUrl) } }
            )
            if (!track.matchedLyricSnippet.isNullOrBlank()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.padding(top = 2.dp)
                ) {
                    VantaStatusBadge("Lyrics match", Color(0xFFFFD54F))
                    Text(
                        text = "\"${track.matchedLyricSnippet}\"",
                        color = Color(0xFFFFD54F).copy(alpha = 0.85f),
                        fontSize = 11.sp,
                        fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        SearchOverflowMenu(onSave = { onSave(track) }, onStartStation = onStartStation)
        SearchPlayAction(track = track, onPlay = onPlay, enabled = canPlay)
    }
}

@Composable
private fun SearchSongRow(
    track: CanonicalTrack,
    onPlay: (CanonicalTrack) -> Unit,
    onNavigateToArtist: (String, String?) -> Unit,
    onNavigateToAlbum: (String, String, String?) -> Unit,
    onSave: (CanonicalTrack) -> Unit = {},
    onOpenTrackSheet: (() -> Unit)? = null,
    onStartStation: () -> Unit = {}
) {
    val sourceStatus = remember(track.sourceStatus) { track.sourceStatus ?: SearchItemStatus.METADATA_ONLY }
    val canPlay = remember(sourceStatus) { sourceStatus.canEnterPlaybackFlow() }
    val statusLabel = remember(sourceStatus) {
        sourceStatus
            .takeIf { !it.canEnterPlaybackFlow() || it.isUnavailable() }
            ?.userFacingLabel()
    }
    val cleaned = remember(track.title, track.artist) {
        DisplayMetadataCleaner.computeDisplayMetadata(track.title, track.artist, track.album, explicit = track.explicit)
    }
    val dTitle = cleaned.title.ifBlank { track.displayTitle }
    val dArtist = cleaned.artist.ifBlank { track.displayArtist }
    val dArtistLine = remember(dArtist, track.featuredArtists) {
        artistLineWithFeatures(dArtist, track.featuredArtists)
    }
    val dAlbum = track.album

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = canPlay) { onPlay(track) }
            .padding(horizontal = 2.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(50.dp)
                .clickable { if (dAlbum != null) onNavigateToAlbum(dAlbum, dArtist, track.artworkUrl) }
                .clip(RoundedCornerShape(12.dp))
                .border(0.5.dp, AppOutline.copy(alpha = 0.60f), RoundedCornerShape(12.dp))
        ) {
            NetworkArtwork(
                artworkUrl = track.artworkUrl,
                seed = dTitle,
                modifier = Modifier.fillMaxSize()
            )
        }

        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    dTitle,
                    color = AppText,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    fontSize = 15.sp,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (cleaned.explicit == true) VantaExplicitBadge()
                TitleSpatialIndicator(
                    qualityInfo = track.qualityInfo,
                    atmosMixAvailable = track.atmosMixAvailable
                )
                if (statusLabel != null) {
                    track.qualityInfo?.let { q ->
                        if (spatialIdentityKind(q) == null && !track.atmosMixAvailable) {
                            if (q.isSpatialAudio) VantaStatusBadge(spatialBadgeLabel("Spatial", q.spatialEvidence), AppAccent)
                            else if (q.isSurround) VantaStatusBadge(spatialBadgeLabel("Surround", q.spatialEvidence), AppAccent)
                            if (q.isHiRes == true) VantaStatusBadge("Hi-Res", AppAccent)
                        }
                    }
                    VantaStatusBadge(
                        text = statusLabel,
                        color = if (sourceStatus.isUnavailable()) AppWarning else AppTextSecondary
                    )
                } else if (sourceStatus != SearchItemStatus.PREVIEW) {
                    val q = track.qualityInfo
                    if ((q == null || spatialIdentityKind(q) == null) && !track.atmosMixAvailable) {
                        VantaQualityBadge(q?.compactQualityLabel())
                    }
                }
            }
            Text(dArtistLine, color = AppTextSecondary, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (!track.matchedLyricSnippet.isNullOrBlank()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.padding(top = 1.dp)
                ) {
                    VantaStatusBadge("Lyrics match", Color(0xFFFFD54F))
                    Text(
                        text = "\"${track.matchedLyricSnippet}\"",
                        color = Color(0xFFFFD54F).copy(alpha = 0.85f),
                        fontSize = 11.sp,
                        fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        SearchOverflowMenu(onSave = { onSave(track) }, onStartStation = onStartStation, onDetails = onOpenTrackSheet)
        SearchPlayAction(track = track, onPlay = onPlay, enabled = canPlay)
    }
}

@Composable
private fun SearchOverflowMenu(onSave: () -> Unit, onStartStation: () -> Unit, onDetails: (() -> Unit)? = null) {
    var expanded by remember { mutableStateOf(false) }
    val dismissKeyboard = rememberKeyboardDismissal()
    Box {
        SearchIconAction(icon = Icons.Filled.MoreVert, contentDescription = "More options", onClick = {
            dismissKeyboard()
            expanded = true
        })
        androidx.compose.material3.DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            androidx.compose.material3.DropdownMenuItem(text = { Text("Save to library") }, onClick = { expanded = false; onSave() })
            androidx.compose.material3.DropdownMenuItem(text = { Text("Start radio") }, onClick = { expanded = false; onStartStation() })
            if (onDetails != null) androidx.compose.material3.DropdownMenuItem(text = { Text("Track details") }, onClick = { expanded = false; onDetails() })
        }
    }
}

@Composable
private fun SearchPlayAction(
    track: CanonicalTrack,
    onPlay: (CanonicalTrack) -> Unit,
    enabled: Boolean
) {
    SearchIconAction(
        icon = Icons.Filled.PlayArrow,
        contentDescription = if (enabled) "Play" else "Unavailable",
        onClick = { onPlay(track) },
        tint = AppAccent,
        emphasized = true,
        enabled = enabled
    )
}

@Composable
private fun LibrarySongRow(
    track: UnifiedTrackWithSources,
    onPlay: () -> Unit,
    onNavigateToArtist: (String, String?) -> Unit,
    onNavigateToAlbum: (String, String, String?) -> Unit,
    onStartStation: () -> Unit = {}
) {
    val display = remember(track.track) {
        TrackDisplayResolver.resolve(track.track)
    }
    val qualityLabel = remember(track.sources) {
        VantaQualityInfo.fromTrackSource(
            source = track.sources.maxByOrNull { it.bitrate },
            status = track.sourceValidityStatus()
        )?.compactQualityLabel()
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .searchResultSurface()
            .clickable { onPlay() }
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(50.dp)
                .clip(RoundedCornerShape(12.dp))
                .border(0.5.dp, AppOutline.copy(alpha = 0.60f), RoundedCornerShape(12.dp))
        ) {
            NetworkArtwork(
                artworkUrl = display.artworkUrl,
                seed = display.title,
                modifier = Modifier.fillMaxSize()
            )
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    display.title,
                    color = AppText,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    fontSize = 15.sp,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (display.explicit == true) VantaExplicitBadge()
                VantaQualityBadge(qualityLabel)
            }
            SearchSubtitleLine(
                artist = display.artist,
                album = display.album,
                onArtistClick = { onNavigateToArtist(display.artist, null) },
                onAlbumClick = display.album?.let { { onNavigateToAlbum(it, display.artist, display.artworkUrl) } }
            )
        }
        SearchIconAction(
            icon = ImageVector.vectorResource(id = com.audiophile.musicplayer.R.drawable.ic_radio),
            contentDescription = "Start station",
            onClick = onStartStation
        )
        SearchIconAction(
            icon = Icons.Filled.PlayArrow,
            contentDescription = "Play",
            onClick = onPlay,
            tint = AppAccent,
            emphasized = true
        )
    }
}

@Composable
private fun SearchAlbumCard(
    album: CanonicalAlbum,
    onNavigateToAlbum: () -> Unit,
    onStartStation: () -> Unit = {},
    showStationAction: Boolean = true
) {
    val isMetadataOnly = album.trackCount == null || album.trackCount == 0
    Column(
        modifier = Modifier
            .width(140.dp)
            .clickable { onNavigateToAlbum() }
    ) {
        Box(
            modifier = Modifier
                .size(140.dp)
                .clip(RoundedCornerShape(VantaRadius.artwork))
                .border(0.5.dp, AppOutline, RoundedCornerShape(VantaRadius.artwork))
        ) {
            NetworkArtwork(
                artworkUrl = album.artworkUrl,
                seed = album.title,
                modifier = Modifier.fillMaxSize()
            )
            if (isMetadataOnly && showStationAction) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(6.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(AppTextMuted.copy(alpha = 0.9f))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text("View", color = Color.Black, fontSize = 9.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.weight(1f)) {
                Text(DisplayMetadataCleaner.cleanDisplayName(album.title).ifBlank { album.title }, color = AppText, fontWeight = FontWeight.SemiBold, maxLines = 1, fontSize = 13.sp, overflow = TextOverflow.Ellipsis)
                Text(DisplayMetadataCleaner.cleanDisplayName(album.artist).ifBlank { album.artist }, color = AppTextSecondary, maxLines = 1, fontSize = 12.sp, overflow = TextOverflow.Ellipsis)
            }
            if (showStationAction) Icon(
                ImageVector.vectorResource(id = com.audiophile.musicplayer.R.drawable.ic_radio),
                contentDescription = "Start station",
                tint = AppTextSecondary,
                modifier = Modifier.size(16.dp).clickable(onClick = onStartStation).padding(2.dp)
            )
        }
    }
}

@Composable
private fun SearchArtistCard(
    artist: CanonicalArtist,
    onNavigateToArtist: () -> Unit,
    onStartStation: () -> Unit = {},
    showStationAction: Boolean = true
) {
    Column(
        modifier = Modifier
            .width(100.dp)
            .clickable { onNavigateToArtist() },
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(90.dp)
                .clip(CircleShape)
                .border(0.5.dp, AppOutline, CircleShape)
        ) {
            NetworkArtwork(
                artworkUrl = artist.artworkUrl,
                seed = artist.name,
                modifier = Modifier.fillMaxSize()
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            DisplayMetadataCleaner.cleanDisplayName(artist.name).ifBlank { artist.name },
            color = AppText,
            fontWeight = FontWeight.Medium,
            maxLines = 2,
            fontSize = 12.sp,
            textAlign = TextAlign.Center,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth()
        )
        Text(
            "Artist",
            color = AppTextSecondary,
            fontSize = 11.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center
        ) {
            if (showStationAction) Icon(
                ImageVector.vectorResource(id = com.audiophile.musicplayer.R.drawable.ic_radio),
                contentDescription = "Start station",
                tint = AppTextSecondary,
                modifier = Modifier.size(14.dp).clickable(onClick = onStartStation)
            )
        }
    }
}

@Composable
private fun SearchPlaylistCard(
    playlist: CanonicalPlaylist,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier.width(140.dp)
) {
    Column(
        modifier = modifier.clickable(onClick = onOpen)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(VantaRadius.artwork))
                .border(0.5.dp, AppOutline, RoundedCornerShape(VantaRadius.artwork))
        ) {
            NetworkArtwork(
                artworkUrl = playlist.artworkUrl,
                seed = playlist.title,
                modifier = Modifier.fillMaxSize()
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            DisplayMetadataCleaner.cleanDisplayName(playlist.title).ifBlank { playlist.title },
            color = AppText,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            fontSize = 13.sp,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            listOfNotNull(
                playlist.curator?.takeIf { it.isNotBlank() }?.let { com.audiophile.musicplayer.data.source.debrandCuratorLabel(it) },
                playlist.trackCount?.takeIf { it > 0 }?.let { "$it songs" }
            ).joinToString(" · ").ifBlank { "Playlist" },
            color = AppTextSecondary,
            maxLines = 1,
            fontSize = 12.sp,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun SearchResultsHeading(title: String, more: String?, onMore: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        Text(title, color = AppText, fontSize = 19.sp, fontWeight = FontWeight.Bold)
        if (more != null) androidx.compose.material3.TextButton(onClick = onMore) { Text(more, color = AppAccent) }
    }
}

@Composable
private fun SearchArtistHero(artist: CanonicalArtist, onOpen: () -> Unit, onRadio: () -> Unit) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp))
        .background(Brush.linearGradient(listOf(AppAccent.copy(alpha = 0.18f), AppSurfaceSoft)))
        .clickable(onClick = onOpen).padding(20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            NetworkArtwork(artworkUrl = artist.artworkUrl, seed = artist.name,
                modifier = Modifier.size(96.dp).clip(CircleShape))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("ARTIST", color = AppAccent, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
                Text(artist.name, color = AppText, fontSize = 26.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text("Songs, albums and more", color = AppTextSecondary, fontSize = 12.sp)
            }
        }
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            androidx.compose.material3.Button(onClick = onOpen, modifier = Modifier.weight(1f)) { Text("View artist") }
            androidx.compose.material3.OutlinedButton(onClick = onRadio, modifier = Modifier.weight(1f)) { Text("Artist radio") }
        }
    }
}

private data class SearchCategory(
    val title: String,
    val startColor: Color,
    val endColor: Color
)

private fun isBrowseCategory(title: String): Boolean =
    com.audiophile.musicplayer.data.catalog.BrowseCatalog.find(title) != null


@Composable
private fun CategoryCard(
    category: SearchCategory,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(110.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(
                Brush.linearGradient(
                    colors = listOf(category.startColor, category.endColor)
                )
            )
            .border(0.5.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(16.dp),
        contentAlignment = Alignment.BottomStart
    ) {
        // Glowing overlay
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(Color.White.copy(alpha = 0.12f), Color.Transparent),
                        radius = 200f
                    )
                )
        )
        
        // Custom programmatic vector illustrations drawn on the right half using Canvas
        Canvas(modifier = Modifier.fillMaxSize()) {
            val width = size.width
            val height = size.height
            val rightCenter = Offset(width * 0.8f, height * 0.5f)
            
            when (category.title) {
                "Alternative" -> {
                    val radius1 = height * 0.4f
                    val radius2 = height * 0.25f
                    val radius3 = height * 0.12f
                    drawCircle(
                        color = Color.White.copy(alpha = 0.06f),
                        radius = radius1,
                        center = rightCenter,
                        style = Stroke(width = 2.dp.toPx())
                    )
                    drawCircle(
                        color = Color.White.copy(alpha = 0.10f),
                        radius = radius2,
                        center = rightCenter,
                        style = Stroke(width = 1.5.dp.toPx())
                    )
                    drawCircle(
                        color = Color.White.copy(alpha = 0.14f),
                        radius = radius3,
                        center = rightCenter,
                        style = Stroke(width = 1.dp.toPx())
                    )
                    drawLine(
                        color = Color.White.copy(alpha = 0.08f),
                        start = Offset(width * 0.55f, height * 0.15f),
                        end = Offset(width * 0.95f, height * 0.85f),
                        strokeWidth = 1.5.dp.toPx()
                    )
                    drawCircle(
                        color = Color.White.copy(alpha = 0.25f),
                        radius = 4.dp.toPx(),
                        center = Offset(width * 0.72f, height * 0.45f)
                    )
                }
                "Pop" -> {
                    drawCircle(
                        color = Color.White.copy(alpha = 0.10f),
                        radius = height * 0.38f,
                        center = Offset(width * 0.78f, height * 0.38f)
                    )
                    drawCircle(
                        color = Color.White.copy(alpha = 0.14f),
                        radius = height * 0.26f,
                        center = Offset(width * 0.86f, height * 0.68f)
                    )
                    drawCircle(
                        color = Color.White.copy(alpha = 0.22f),
                        radius = height * 0.09f,
                        center = Offset(width * 0.62f, height * 0.65f)
                    )
                }
                "Country" -> {
                    val sunRadius = height * 0.28f
                    val sunCenter = Offset(width * 0.82f, height * 0.65f)
                    drawCircle(
                        color = Color.White.copy(alpha = 0.10f),
                        radius = sunRadius,
                        center = sunCenter
                    )
                    for (i in 0 until 5) {
                        val angle = Math.toRadians(-25.0 - i * 32.0)
                        val startX = sunCenter.x + (sunRadius + 4.dp.toPx()) * Math.cos(angle).toFloat()
                        val startY = sunCenter.y + (sunRadius + 4.dp.toPx()) * Math.sin(angle).toFloat()
                        val endX = sunCenter.x + (sunRadius + 20.dp.toPx()) * Math.cos(angle).toFloat()
                        val endY = sunCenter.y + (sunRadius + 20.dp.toPx()) * Math.sin(angle).toFloat()
                        drawLine(
                            color = Color.White.copy(alpha = 0.12f),
                            start = Offset(startX, startY),
                            end = Offset(endX, endY),
                            strokeWidth = 1.8.dp.toPx()
                        )
                    }
                    val path = Path().apply {
                        moveTo(width * 0.48f, height)
                        quadraticBezierTo(
                            width * 0.68f, height * 0.62f,
                            width, height * 0.82f
                        )
                        lineTo(width, height)
                        close()
                    }
                    drawPath(
                        path = path,
                        color = Color.White.copy(alpha = 0.08f)
                    )
                }
                "Hits" -> {
                    val starCenter = Offset(width * 0.8f, height * 0.42f)
                    val drawStar = { center: Offset, size: Float, alpha: Float ->
                        val path = Path().apply {
                            moveTo(center.x, center.y - size)
                            quadraticBezierTo(center.x, center.y, center.x + size, center.y)
                            quadraticBezierTo(center.x, center.y, center.x, center.y + size)
                            quadraticBezierTo(center.x, center.y, center.x - size, center.y)
                            quadraticBezierTo(center.x, center.y, center.x, center.y - size)
                        }
                        drawPath(path = path, color = Color.White.copy(alpha = alpha))
                    }
                    drawStar(starCenter, height * 0.32f, 0.20f)
                    drawStar(Offset(width * 0.66f, height * 0.7f), height * 0.16f, 0.14f)
                    drawStar(Offset(width * 0.9f, height * 0.18f), height * 0.12f, 0.16f)
                }
                "Hip-Hop" -> {
                    val barWidth = 7.dp.toPx()
                    val barGap = 5.dp.toPx()
                    val startX = width * 0.58f
                    val barHeights = listOf(0.35f, 0.72f, 0.52f, 0.86f, 0.58f, 0.32f)
                    
                    barHeights.forEachIndexed { index, heightPercent ->
                        val x = startX + index * (barWidth + barGap)
                        val barHeight = height * heightPercent
                        val y = height - barHeight - 10.dp.toPx()
                        drawRoundRect(
                            color = Color.White.copy(alpha = 0.15f),
                            topLeft = Offset(x, y),
                            size = Size(barWidth, barHeight),
                            cornerRadius = CornerRadius(barWidth / 2)
                        )
                    }
                }
                "Dance" -> {
                    val ringCenter = Offset(width * 0.84f, height * 0.48f)
                    for (i in 1..4) {
                        drawCircle(
                            color = Color.White.copy(alpha = 0.05f * (5 - i)),
                            radius = height * 0.17f * i,
                            center = ringCenter,
                            style = Stroke(width = 1.8.dp.toPx())
                        )
                    }
                    drawCircle(
                        color = Color.White.copy(alpha = 0.25f),
                        radius = 2.5.dp.toPx(),
                        center = Offset(ringCenter.x - height * 0.34f, ringCenter.y)
                    )
                }
                "Rock" -> {
                    val path = Path().apply {
                        moveTo(width * 0.74f, 0f)
                        lineTo(width * 0.61f, height * 0.46f)
                        lineTo(width * 0.76f, height * 0.46f)
                        lineTo(width * 0.67f, height)
                        lineTo(width * 0.88f, height * 0.50f)
                        lineTo(width * 0.73f, height * 0.50f)
                        close()
                    }
                    drawPath(
                        path = path,
                        color = Color.White.copy(alpha = 0.12f)
                    )
                    val pathBg = Path().apply {
                        moveTo(width * 0.88f, height * 0.12f)
                        lineTo(width * 0.78f, height * 0.88f)
                        lineTo(width * 0.96f, height * 0.78f)
                        close()
                    }
                    drawPath(
                        path = pathBg,
                        color = Color.White.copy(alpha = 0.07f)
                    )
                }
                "Chill" -> {
                    val wavePath1 = Path().apply {
                        moveTo(width * 0.46f, height * 0.58f)
                        cubicTo(
                            width * 0.62f, height * 0.38f,
                            width * 0.76f, height * 0.78f,
                            width, height * 0.48f
                        )
                        lineTo(width, height)
                        lineTo(width * 0.46f, height)
                        close()
                    }
                    val wavePath2 = Path().apply {
                        moveTo(width * 0.56f, height * 0.72f)
                        cubicTo(
                            width * 0.72f, height * 0.52f,
                            width * 0.86f, height * 0.88f,
                            width, height * 0.62f
                        )
                        lineTo(width, height)
                        lineTo(width * 0.56f, height)
                        close()
                    }
                    drawPath(path = wavePath1, color = Color.White.copy(alpha = 0.08f))
                    drawPath(path = wavePath2, color = Color.White.copy(alpha = 0.11f))
                }
                "Sleep" -> {
                    val moonCenter = Offset(width * 0.76f, height * 0.46f)
                    val moonRadius = height * 0.26f
                    
                    val crescentPath = Path().apply {
                        moveTo(moonCenter.x + moonRadius * 0.5f, moonCenter.y - moonRadius * 0.866f)
                        cubicTo(
                            moonCenter.x - moonRadius * 0.5f, moonCenter.y - moonRadius * 0.866f,
                            moonCenter.x - moonRadius * 0.8f, moonCenter.y + moonRadius * 0.866f,
                            moonCenter.x + moonRadius * 0.5f, moonCenter.y + moonRadius * 0.866f
                        )
                        cubicTo(
                            moonCenter.x - moonRadius * 0.2f, moonCenter.y + moonRadius * 0.6f,
                            moonCenter.x - moonRadius * 0.2f, moonCenter.y - moonRadius * 0.6f,
                            moonCenter.x + moonRadius * 0.5f, moonCenter.y - moonRadius * 0.866f
                        )
                        close()
                    }
                    drawPath(path = crescentPath, color = Color.White.copy(alpha = 0.16f))
                    
                    drawCircle(Color.White.copy(alpha = 0.22f), radius = 1.8.dp.toPx(), center = Offset(width * 0.6f, height * 0.32f))
                    drawCircle(Color.White.copy(alpha = 0.18f), radius = 1.2.dp.toPx(), center = Offset(width * 0.9f, height * 0.62f))
                    drawCircle(Color.White.copy(alpha = 0.25f), radius = 1.8.dp.toPx(), center = Offset(width * 0.68f, height * 0.72f))
                }
                "Focus" -> {
                    for (i in 0 until 5) {
                        val offset = i * 18.dp.toPx()
                        drawLine(
                            color = Color.White.copy(alpha = 0.10f),
                            start = Offset(width * 0.52f + offset, 0f),
                            end = Offset(width * 0.32f + offset, height),
                            strokeWidth = 2.2.dp.toPx()
                        )
                    }
                }
                "Feel Good" -> {
                    val bubbleCenter1 = Offset(width * 0.74f, height * 0.66f)
                    val bubbleCenter2 = Offset(width * 0.85f, height * 0.34f)
                    val bubbleCenter3 = Offset(width * 0.64f, height * 0.4f)
                    
                    drawCircle(
                        color = Color.White.copy(alpha = 0.10f),
                        radius = height * 0.2f,
                        center = bubbleCenter1,
                        style = Stroke(width = 1.2.dp.toPx())
                    )
                    drawCircle(
                        color = Color.White.copy(alpha = 0.13f),
                        radius = height * 0.15f,
                        center = bubbleCenter2,
                        style = Stroke(width = 1.2.dp.toPx())
                    )
                    drawCircle(
                        color = Color.White.copy(alpha = 0.07f),
                        radius = height * 0.11f,
                        center = bubbleCenter3,
                        style = Stroke(width = 0.8.dp.toPx())
                    )
                    drawCircle(Color.White.copy(alpha = 0.04f), radius = height * 0.2f, center = bubbleCenter1)
                    drawCircle(Color.White.copy(alpha = 0.04f), radius = height * 0.15f, center = bubbleCenter2)
                }
                "Party" -> {
                    val shapes = listOf(
                        Offset(width * 0.66f, height * 0.32f) to 5.dp.toPx(),
                        Offset(width * 0.78f, height * 0.28f) to 7.dp.toPx(),
                        Offset(width * 0.74f, height * 0.62f) to 4.5.dp.toPx(),
                        Offset(width * 0.86f, height * 0.66f) to 6.5.dp.toPx(),
                        Offset(width * 0.61f, height * 0.68f) to 3.8.dp.toPx()
                    )
                    
                    shapes.forEach { (center, size) ->
                        val path = Path().apply {
                            moveTo(center.x, center.y - size)
                            lineTo(center.x + size, center.y)
                            lineTo(center.x, center.y + size)
                            lineTo(center.x - size, center.y)
                            close()
                        }
                        drawPath(path = path, color = Color.White.copy(alpha = 0.15f))
                    }
                }
                else -> {
                    drawCircle(
                        color = Color.White.copy(alpha = 0.05f),
                        radius = height * 0.38f,
                        center = rightCenter,
                        style = Stroke(width = 1.8.dp.toPx())
                    )
                }
            }
        }

        // Title Text
        Text(
            text = category.title,
            color = Color.White,
            fontSize = 16.sp,
            fontWeight = FontWeight.Black,
            style = TextStyle(
                shadow = androidx.compose.ui.graphics.Shadow(
                    color = Color.Black.copy(alpha = 0.3f),
                    blurRadius = 4f
                )
            )
        )
    }
}

@Composable
private fun SearchShimmer(modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition(label = "shimmer")
    val shimmerAlpha by infiniteTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.75f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "shimmerAlpha"
    )
    val shimmerBase = Color.White.copy(alpha = shimmerAlpha)
    val surfaceColor = AppSurfaceRaised
    val shape = RoundedCornerShape(12.dp)
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        repeat(5) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(shape)
                    .background(surfaceColor)
                    .padding(10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(modifier = Modifier.size(48.dp).clip(shape).background(shimmerBase))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f)) {
                    Box(modifier = Modifier.fillMaxWidth(0.7f).height(12.dp).clip(RoundedCornerShape(4.dp)).background(shimmerBase))
                    Box(modifier = Modifier.fillMaxWidth(0.45f).height(10.dp).clip(RoundedCornerShape(4.dp)).background(shimmerBase))
                }
            }
        }
    }
}
