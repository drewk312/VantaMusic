package com.audiophile.musicplayer.ui

import androidx.activity.compose.BackHandler
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.foundation.background
import androidx.compose.ui.res.vectorResource
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
import androidx.compose.material3.Divider
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
import androidx.compose.ui.platform.LocalContext
import android.content.Context
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
import com.audiophile.musicplayer.data.canonical.CanonicalTrack
import com.audiophile.musicplayer.data.display.DisplayMetadataCleaner
import com.audiophile.musicplayer.data.display.TrackDisplayResolver
import com.audiophile.musicplayer.data.display.VantaQualityInfo
import com.audiophile.musicplayer.data.local.entities.UnifiedTrackWithSources
import com.audiophile.musicplayer.data.source.SearchItemStatus
import com.audiophile.musicplayer.data.source.isConfirmedPlayable
import com.audiophile.musicplayer.data.source.sourceValidityStatus
import androidx.core.content.edit

@Composable
fun SearchScreen(
    uiState: MainUiState,
    onQueryChanged: (String) -> Unit,
    onSearch: () -> Unit,
    onSearchQuery: (String) -> Unit,
    onBrowseCategory: (String) -> Unit,
    onPlay: (UnifiedTrackWithSources) -> Unit,
    onPlaySourceResult: (CanonicalTrack) -> Unit,
    onSaveSourceResult: (CanonicalTrack) -> Unit,
    onNavigateToArtist: (String, String?) -> Unit = { _, _ -> },
    onNavigateToAlbum: (String, String, String?) -> Unit = { _, _, _ -> },
    onNavigateToStation: (String) -> Unit = {},
    onStartStation: (String) -> Unit = {},
    onOpenTrackSheet: ((CanonicalTrack) -> Unit)? = null,
    onQuickPlay: (String) -> Unit = {},
    miniPlayerVisible: Boolean = false
) {
    val context = LocalContext.current
    val sharedPrefs = remember(context) {
        context.getSharedPreferences("vanta_search_history", Context.MODE_PRIVATE)
    }
    
    var recentSearches by remember {
        val saved = sharedPrefs.getString("history_list", "") ?: ""
        mutableStateOf(
            if (saved.isBlank()) emptyList<String>() else saved.split("\n").filter { it.isNotBlank() }
        )
    }

    val addRecentSearch = { term: String ->
        val trimmed = term.trim()
        if (trimmed.isNotEmpty()) {
            val updated = listOf(trimmed) + recentSearches.filter { !it.equals(trimmed, ignoreCase = true) }
            val limited = updated.take(8)
            sharedPrefs.edit {
                    putString("history_list", limited.joinToString("\n"))
                }
            recentSearches = limited
        }
    }

    val removeRecentSearch = { term: String ->
        val updated = recentSearches.filter { !it.equals(term, ignoreCase = true) }
        sharedPrefs.edit {
                putString("history_list", updated.joinToString("\n"))
            }
        recentSearches = updated
    }

    val clearAllRecentSearches = {
        sharedPrefs.edit {
                remove("history_list")
            }
        recentSearches = emptyList()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .imePadding()
            .padding(horizontal = 24.dp)
            .padding(top = 22.dp, bottom = appBottomContentPadding(isMiniPlayerVisible = miniPlayerVisible)),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text("Search", color = AppText, fontSize = 28.sp, fontWeight = FontWeight.Bold)

        VantaSearchField(
            query = uiState.query,
            onQueryChanged = onQueryChanged,
            onSearch = {
                if (uiState.query.isNotBlank()) {
                    addRecentSearch(uiState.query)
                }
                onSearch()
            },
            modifier = Modifier.fillMaxWidth()
        )

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

        val searchIntent = remember(trimmedQuery, uiState.searchArtists, uiState.searchAlbums) {
            detectSearchIntent(trimmedQuery, uiState.searchArtists, uiState.searchAlbums)
        }

        BackHandler(enabled = activeBrowseCategory != null) {
            onQueryChanged("")
        }

        when {
            trimmedQuery.isEmpty() && activeBrowseCategory == null -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
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
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(20.dp))
                                            .background(AppSurfaceSoft)
                                            .border(0.5.dp, AppOutline, RoundedCornerShape(20.dp))
                                            .clickable {
                                                addRecentSearch(term)
                                                val station = StationSearchResolver.resolveSingle(term)
                                                if (station != null) {
                                                    onNavigateToStation(station.id)
                                                } else if (isBrowseCategory(term)) {
                                                    onBrowseCategory(term)
                                                } else {
                                                    onSearchQuery(term)
                                                }
                                            }
                                            .padding(horizontal = 12.dp, vertical = 6.dp)
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Text(term, color = AppText, fontSize = 13.sp)
                                            Icon(
                                                imageVector = Icons.Filled.Close,
                                                contentDescription = "Remove",
                                                tint = AppTextSecondary,
                                                modifier = Modifier
                                                    .size(14.dp)
                                                    .clickable { removeRecentSearch(term) }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Trending Now
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "AI Radio Presets",
                            color = AppText,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                        val presets = remember {
                            listOf("80s Synth Pop", "Late Night Drive", "Focus & Code", "Rainy Day Jazz", "Workout Hype", "90s R&B")
                        }
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            items(presets) { preset ->
                                Row(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(20.dp))
                                        .background(AppAccent.copy(alpha = 0.08f))
                                        .border(0.5.dp, AppAccent.copy(alpha = 0.24f), RoundedCornerShape(20.dp))
                                        .clickable {
                                            addRecentSearch(preset)
                                            onStartStation(preset)
                                        }
                                        .padding(horizontal = 14.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        ImageVector.vectorResource(id = com.audiophile.musicplayer.R.drawable.ic_radio),
                                        contentDescription = null,
                                        tint = AppAccent,
                                        modifier = Modifier.size(13.dp)
                                    )
                                    Text(preset, color = AppText, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                                }
                            }
                        }
                    }

                    // Browse Categories
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            text = "Browse Categories",
                            color = AppTextSecondary,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                        
                        val categories = remember {
                            listOf(
                                SearchCategory("Alternative", Color(0xFFC5A059), Color(0xFFD4A574)),
                                SearchCategory("Pop", Color(0xFFE05275), Color(0xFFF78FA7)),
                                SearchCategory("Country", Color(0xFFD37C44), Color(0xFFE89E6C)),
                                SearchCategory("Hits", Color(0xFFE5B83B), Color(0xFFF7D565)),
                                SearchCategory("Hip-Hop", Color(0xFF4C7CE5), Color(0xFF7AA2F7)),
                                SearchCategory("Dance", Color(0xFF2CB07B), Color(0xFF5ED8A5)),
                                SearchCategory("Rock", Color(0xFFDF523E), Color(0xFFF58A78)),
                                SearchCategory("Chill", Color(0xFF1E8D91), Color(0xFF4DB6AC)),
                                SearchCategory("Sleep", Color(0xFF6B48B3), Color(0xFF9E7CDB)),
                                SearchCategory("Focus", Color(0xFF8C5D3A), Color(0xFFB3835C)),
                                SearchCategory("Feel Good", Color(0xFFE09025), Color(0xFFF7B956)),
                                SearchCategory("Party", Color(0xFFC03C9E), Color(0xFFE874C8))
                            )
                        }

                        val chunked = categories.chunked(2)
                        chunked.forEach { rowCategories ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                rowCategories.forEach { cat ->
                                    CategoryCard(
                                        category = cat,
                                        onClick = {
                                            addRecentSearch(cat.title)
                                            val station = StationSearchResolver.resolveSingle(cat.title)
                                            if (station != null) {
                                                onNavigateToStation(station.id)
                                            } else {
                                                onBrowseCategory(cat.title)
                                            }
                                        },
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                                if (rowCategories.size < 2) {
                                    Spacer(modifier = Modifier.weight(1f))
                                }
                            }
                        }
                    }
                }
            }
            uiState.isSearching &&
                trimmedQuery.isNotEmpty() &&
                uiState.searchSongs.isEmpty() &&
                uiState.visibleTracks.isEmpty() &&
                uiState.matchedStations.isEmpty() &&
                activeBrowseCategory == null -> {
                VantaEmptyState(
                    title = "Searching...",
                    description = "Querying your connected sources for results.",
                    icon = Icons.Filled.Search
                )
            }
            activeBrowseCategory != null -> {
                BrowseCategoryResults(
                    category = activeBrowseCategory,
                    songs = uiState.searchSongs,
                    onBack = { onQueryChanged("") },
                    onPlaySourceResult = onPlaySourceResult,
                    onSaveSourceResult = onSaveSourceResult,
                    onNavigateToArtist = onNavigateToArtist,
                    onNavigateToAlbum = onNavigateToAlbum,
                    onOpenTrackSheet = onOpenTrackSheet,
                    onStartStation = onStartStation
                )
            }
            uiState.searchSongs.isEmpty() &&
                uiState.searchAlbums.isEmpty() &&
                uiState.searchArtists.isEmpty() &&
                uiState.visibleTracks.isEmpty() &&
                uiState.searchSuggestions.isEmpty() &&
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
                val songs = uiState.searchSongs
                val localTracks = uiState.visibleTracks
                val albums = uiState.searchAlbums
                val artists = uiState.searchArtists
                val matchedStations = uiState.matchedStations
                var selectedSearchTab by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf("All") }
                val searchTabs = listOf("All", "Artists", "Albums", "Songs")

                androidx.compose.runtime.LaunchedEffect(trimmedQuery) {
                    selectedSearchTab = "All"
                }

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(bottom = 28.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    if (trimmedQuery.isNotEmpty() && (songs.isNotEmpty() || artists.isNotEmpty() || albums.isNotEmpty())) {
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

                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                    // Search Suggestions
                    if (selectedSearchTab == "All" && uiState.searchSuggestions.isNotEmpty()) {
                        Text(
                            "Suggestions",
                            color = AppTextSecondary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                        Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
                            uiState.searchSuggestions.forEach { suggestion ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            onSearchQuery(suggestion)
                                        }
                                        .padding(vertical = 12.dp, horizontal = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Search,
                                        contentDescription = null,
                                        tint = AppTextSecondary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(Modifier.width(12.dp))
                                    Text(suggestion, color = AppText, fontSize = 16.sp)
                                }
                            }
                        }
                        Spacer(Modifier.height(0.5.dp).fillMaxWidth().background(AppOutline.copy(alpha = 0.72f)))
                    }

                    // Intent badge
                    val intentLabel = when {
                        trimmedQuery.trim().lowercase().startsWith("artist ") ||
                            trimmedQuery.trim().lowercase().startsWith("artist:") -> "Artist Search"
                        trimmedQuery.trim().lowercase().startsWith("album ") ||
                            trimmedQuery.trim().lowercase().startsWith("album:") -> "Album Search"
                        trimmedQuery.trim().lowercase().startsWith("song ") ||
                            trimmedQuery.trim().lowercase().startsWith("song:") -> "Song Search"
                        else -> null
                    }
                    if (intentLabel != null) {
                        VantaStatusBadge(intentLabel, AppAccent)
                    }

                    // AI Radio Prompt Card
                    if (selectedSearchTab == "All" && trimmedQuery.isNotBlank()) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            SearchSectionLabel("AI Radio")
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .searchResultSurface(featured = true)
                                    .clickable { onStartStation(trimmedQuery) }
                                    .padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(48.dp)
                                        .clip(CircleShape)
                                        .background(AppAccent.copy(alpha = 0.15f)),
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
                                        "Start Station: $trimmedQuery",
                                        color = AppText,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 16.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        "Stream music from your connected sources",
                                        color = AppTextSecondary,
                                        fontSize = 13.sp
                                    )
                                }
                                Icon(
                                    Icons.Filled.PlayArrow,
                                    contentDescription = null,
                                    tint = AppAccent,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }

                    if (selectedSearchTab == "All" && matchedStations.isNotEmpty()) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            SearchSectionLabel("Stations")
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

                    // Top result — first catalog hit, same order the API returned
                    val topResult = uiState.searchTopResult
                    if (selectedSearchTab == "All" && topResult != null) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            SearchSectionLabel("Top Result")
                            TopResultCard(
                                track = topResult,
                                onPlay = onPlaySourceResult,
                                onSave = onSaveSourceResult,
                                onNavigateToArtist = onNavigateToArtist,
                                onNavigateToAlbum = onNavigateToAlbum,
                                onStartStation = { onStartStation("${topResult.title} by ${topResult.artist}") }
                            )
                        }
                    }

                    // Artists
                    if ((selectedSearchTab == "All" || selectedSearchTab == "Artists") && artists.isNotEmpty()) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            SearchSectionLabel("Artists")
                            if (searchIntent == SearchIntent.ARTIST) {
                                artists.take(3).forEach { artist ->
                                    RichArtistCard(
                                        artist = artist,
                                        library = uiState.library,
                                        onNavigateToArtist = { onNavigateToArtist(artist.name, artist.id) },
                                        onStartStation = { onStartStation(artist.name) }
                                    )
                                    Spacer(Modifier.height(12.dp))
                                }
                            } else {
                                LazyRow(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                    items(artists.take(10)) { artist ->
                                        SearchArtistCard(
                                            artist = artist,
                                            onNavigateToArtist = { onNavigateToArtist(artist.name, artist.id) },
                                            onStartStation = { onStartStation(artist.name) }
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Albums
                    if ((selectedSearchTab == "All" || selectedSearchTab == "Albums") && albums.isNotEmpty()) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            SearchSectionLabel("Albums")
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                items(albums.take(10)) { album ->
                                    SearchAlbumCard(
                                        album = album,
                                        onNavigateToAlbum = { onNavigateToAlbum(album.title, album.artist, album.artworkUrl) },
                                        onStartStation = { onStartStation("${album.title} by ${album.artist}") }
                                    )
                                }
                            }
                        }
                    }

                    // Songs — catalog order
                    if ((selectedSearchTab == "All" || selectedSearchTab == "Songs") && songs.isNotEmpty() && searchIntent != SearchIntent.ARTIST) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            SearchSectionLabel("Songs")
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                songs.take(20).forEach { track ->
                                    SearchSongRow(
                                        track = track,
                                        onPlay = onPlaySourceResult,
                                        onSave = onSaveSourceResult,
                                        onNavigateToArtist = onNavigateToArtist,
                                        onNavigateToAlbum = onNavigateToAlbum,
                                        onOpenTrackSheet = onOpenTrackSheet?.let { fn -> { fn(track) } },
                                        onStartStation = { onStartStation("${track.title} by ${track.artist}") }
                                    )
                                }
                            }
                        }
                    }

                    // Library Matches
                    if ((selectedSearchTab == "All" || selectedSearchTab == "Songs") && localTracks.isNotEmpty() && searchIntent != SearchIntent.ARTIST) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            SearchSectionLabel("Library")
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                localTracks.take(5).forEach { track ->
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
                    }

                    Spacer(Modifier.height(24.dp))
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
    onBack: () -> Unit,
    onPlaySourceResult: (CanonicalTrack) -> Unit,
    onSaveSourceResult: (CanonicalTrack) -> Unit,
    onNavigateToArtist: (String, String?) -> Unit,
    onNavigateToAlbum: (String, String, String?) -> Unit,
    onOpenTrackSheet: ((CanonicalTrack) -> Unit)?,
    onStartStation: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(AppSurfaceRaised.copy(alpha = 0.70f))
                    .border(0.5.dp, AppOutline.copy(alpha = 0.72f), CircleShape)
                    .clickable(onClick = onBack),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = AppText, modifier = Modifier.size(20.dp))
            }
            Column {
                Text(category, color = AppText, fontSize = 24.sp, fontWeight = FontWeight.Black)
                Text("Browse", color = AppTextSecondary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            }
        }

        if (songs.isEmpty()) {
            VantaEmptyState(
                title = "Nothing Found",
                description = "No clean $category results came back from the connected sources.",
                icon = Icons.Filled.Search
            )
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SearchSectionLabel("Songs")
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    songs.take(30).forEach { track ->
                        SearchSongRow(
                            track = track,
                            onPlay = onPlaySourceResult,
                            onSave = onSaveSourceResult,
                            onNavigateToArtist = onNavigateToArtist,
                            onNavigateToAlbum = onNavigateToAlbum,
                            onOpenTrackSheet = onOpenTrackSheet?.let { fn -> { fn(track) } },
                            onStartStation = { onStartStation("${track.title} by ${track.artist}") }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun VantaSearchField(
    query: String,
    onQueryChanged: (String) -> Unit,
    onSearch: () -> Unit,
    modifier: Modifier = Modifier
) {
    val hasText = query.isNotBlank()
    BasicTextField(
        value = query,
        onValueChange = onQueryChanged,
        modifier = modifier,
        singleLine = true,
        textStyle = TextStyle(
            color = AppText,
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium
        ),
        cursorBrush = SolidColor(AppAccent),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { onSearch() }),
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
                            "Search lossless music, artists, stations…",
                            color = AppTextMuted,
                            fontSize = 15.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
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
private fun SearchSectionLabel(title: String) {
    Text(
        text = title.uppercase(),
        color = AppTextSecondary.copy(alpha = 0.74f),
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        maxLines = 1
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

@Composable
private fun SearchIconAction(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    tint: Color = AppTextSecondary,
    emphasized: Boolean = false
) {
    val shape = CircleShape
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(shape)
            .background(if (emphasized) AppAccent.copy(alpha = 0.14f) else Color.White.copy(alpha = 0.035f))
            .border(
                width = 0.5.dp,
                color = if (emphasized) AppAccent.copy(alpha = 0.18f) else Color.White.copy(alpha = 0.06f),
                shape = shape
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = tint,
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
    val cleaned = remember(track.title, track.artist) {
        DisplayMetadataCleaner.computeDisplayMetadata(track.title, track.artist, track.album, explicit = track.explicit)
    }
    val dTitle = cleaned.title.ifBlank { track.displayTitle }
    val dArtist = cleaned.artist.ifBlank { track.displayArtist }
    val dAlbum = track.album

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .searchResultSurface(featured = true)
            .clickable { onPlay(track) }
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
                if (track.sourceStatus != SearchItemStatus.PREVIEW) {
                    VantaQualityBadge(track.qualityInfo?.bestQualityLabel())
                }
            }
            SearchSubtitleLine(
                artist = dArtist,
                album = dAlbum,
                onArtistClick = { onNavigateToArtist(dArtist, null) },
                onAlbumClick = dAlbum?.let { { onNavigateToAlbum(it, dArtist, track.artworkUrl) } }
            )
        }

        SearchIconAction(
            icon = ImageVector.vectorResource(id = com.audiophile.musicplayer.R.drawable.ic_radio),
            contentDescription = "Start station",
            onClick = onStartStation
        )
        SearchIconAction(
            icon = Icons.Filled.Add,
            contentDescription = "Save to library",
            onClick = { onSave(track) }
        )
        SearchPlayAction(track = track, onPlay = onPlay)
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
    val cleaned = remember(track.title, track.artist) {
        DisplayMetadataCleaner.computeDisplayMetadata(track.title, track.artist, track.album, explicit = track.explicit)
    }
    val dTitle = cleaned.title.ifBlank { track.displayTitle }
    val dArtist = cleaned.artist.ifBlank { track.displayArtist }
    val dAlbum = track.album

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .searchResultSurface()
            .clickable { onPlay(track) }
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
                if (track.sourceStatus != SearchItemStatus.PREVIEW) {
                    VantaQualityBadge(track.qualityInfo?.bestQualityLabel())
                }
            }
            SearchSubtitleLine(
                artist = dArtist,
                album = dAlbum,
                onArtistClick = { onNavigateToArtist(dArtist, null) },
                onAlbumClick = dAlbum?.let { { onNavigateToAlbum(it, dArtist, track.artworkUrl) } }
            )
        }

        if (onOpenTrackSheet != null) {
            SearchIconAction(
                icon = Icons.Filled.MoreVert,
                contentDescription = "More options",
                onClick = onOpenTrackSheet
            )
        }
        SearchIconAction(
            icon = ImageVector.vectorResource(id = com.audiophile.musicplayer.R.drawable.ic_radio),
            contentDescription = "Start station",
            onClick = onStartStation
        )
        SearchIconAction(
            icon = Icons.Filled.Add,
            contentDescription = "Save to library",
            onClick = { onSave(track) }
        )
        SearchPlayAction(track = track, onPlay = onPlay)
    }
}

@Composable
private fun SearchPlayAction(
    track: CanonicalTrack,
    onPlay: (CanonicalTrack) -> Unit
) {
    SearchIconAction(
        icon = Icons.Filled.PlayArrow,
        contentDescription = "Play",
        onClick = { onPlay(track) },
        tint = AppAccent,
        emphasized = true
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
        )?.bestQualityLabel()
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
    onStartStation: () -> Unit = {}
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
            if (isMetadataOnly) {
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
                Text(album.title, color = AppText, fontWeight = FontWeight.SemiBold, maxLines = 1, fontSize = 13.sp, overflow = TextOverflow.Ellipsis)
                Text(album.artist, color = AppTextSecondary, maxLines = 1, fontSize = 12.sp, overflow = TextOverflow.Ellipsis)
            }
            Icon(
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
    onStartStation: () -> Unit = {}
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
            artist.name,
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
            Icon(
                ImageVector.vectorResource(id = com.audiophile.musicplayer.R.drawable.ic_radio),
                contentDescription = "Start station",
                tint = AppTextSecondary,
                modifier = Modifier.size(14.dp).clickable(onClick = onStartStation)
            )
        }
    }
}

private enum class SearchIntent { ARTIST, ALBUM, SONG, GENERAL }

private fun detectSearchIntent(
    query: String,
    artists: List<CanonicalArtist>,
    albums: List<CanonicalAlbum>
): SearchIntent {
    val q = query.trim().lowercase()
    if (q.isBlank()) return SearchIntent.GENERAL

    if (q.startsWith("artist ") || q.startsWith("artist:")) return SearchIntent.ARTIST
    if (q.startsWith("album ") || q.startsWith("album:")) return SearchIntent.ALBUM
    if (q.startsWith("song ") || q.startsWith("song:")) return SearchIntent.SONG

    return SearchIntent.GENERAL
}

@Composable
private fun RichArtistCard(
    artist: CanonicalArtist,
    library: List<UnifiedTrackWithSources>,
    onNavigateToArtist: () -> Unit,
    onStartStation: () -> Unit = {}
) {
    val topTracks = remember(artist.name, library) {
        library
            .filter { it.track.artist.equals(artist.name, ignoreCase = true) }
            .filter { it.isConfirmedPlayable() }
            .distinctBy { it.track.title.trim().lowercase() }
            .take(5)
    }

    VantaCard(onClick = onNavigateToArtist) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(RoundedCornerShape(VantaRadius.artwork))
                    .border(0.5.dp, AppOutline, RoundedCornerShape(VantaRadius.artwork))
            ) {
                NetworkArtwork(
                    artworkUrl = artist.artworkUrl,
                    seed = artist.name,
                    modifier = Modifier.fillMaxSize()
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(artist.name, color = AppText, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                if (artist.genre != null) {
                    Text(artist.genre, color = AppTextSecondary, fontSize = 13.sp)
                }
                Text(
                    "${library.count { it.track.artist.equals(artist.name, ignoreCase = true) }} tracks in library",
                    color = AppTextMuted,
                    fontSize = 12.sp
                )
            }
            Icon(
                ImageVector.vectorResource(id = com.audiophile.musicplayer.R.drawable.ic_radio),
                contentDescription = "Start Station",
                tint = AppAccent,
                modifier = Modifier.size(24.dp).clickable(onClick = onStartStation)
            )
        }
        if (topTracks.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            Divider(color = AppOutline, thickness = 0.5.dp)
            Spacer(Modifier.height(8.dp))
            topTracks.forEachIndexed { i, track ->
                val display = remember(track.track) { TrackDisplayResolver.resolve(track.track) }
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        "${i + 1}",
                        color = AppTextMuted,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.width(20.dp)
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(display.title, color = AppText, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}

private data class SearchCategory(
    val title: String,
    val startColor: Color,
    val endColor: Color
)

private fun isBrowseCategory(title: String): Boolean {
    return title.trim().lowercase() in setOf(
        "alternative",
        "pop",
        "country",
        "hits",
        "hip-hop",
        "hip hop",
        "dance",
        "rock",
        "chill",
        "sleep",
        "focus",
        "feel good",
        "party"
    )
}

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
                      HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = Color.White.copy(alpha = 0.1f))
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