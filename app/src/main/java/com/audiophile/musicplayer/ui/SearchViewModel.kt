package com.audiophile.musicplayer.ui

import android.content.Context
import android.util.Log
import androidx.core.content.edit
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.audiophile.musicplayer.AppContainer
import com.audiophile.musicplayer.common.VantaLogger
import com.audiophile.musicplayer.data.canonical.CanonicalAlbum
import com.audiophile.musicplayer.data.canonical.CanonicalArtist
import com.audiophile.musicplayer.data.canonical.CanonicalPlaylist
import com.audiophile.musicplayer.data.canonical.CanonicalTrack
import com.audiophile.musicplayer.data.dj.JukeboxStation
import com.audiophile.musicplayer.data.dj.StationSearchResolver
import com.audiophile.musicplayer.data.importer.PlatformLinkMetadata
import com.audiophile.musicplayer.data.importer.SoundiizTextParser
import com.audiophile.musicplayer.data.local.entities.UnifiedTrackWithSources
import com.audiophile.musicplayer.data.source.SearchItemStatus
import com.audiophile.musicplayer.data.source.isConfirmedPlayable
import com.audiophile.musicplayer.search.MusicTypeahead
import com.audiophile.musicplayer.search.SearchRequest
import com.audiophile.musicplayer.search.UnifiedSearchEngine
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

/** The complete state of search. No result state is mirrored in MainViewModel. */
data class SearchUiState(
    val query: String = "",
    val isSearching: Boolean = false,
    val libraryMatches: List<UnifiedTrackWithSources> = emptyList(),
    val topResult: CanonicalTrack? = null,
    val songs: List<CanonicalTrack> = emptyList(),
    val albums: List<CanonicalAlbum> = emptyList(),
    val artists: List<CanonicalArtist> = emptyList(),
    val playlists: List<CanonicalPlaylist> = emptyList(),
    val suggestions: List<String> = emptyList(),
    val matchedStations: List<JukeboxStation> = emptyList(),
    val searchHistory: List<String> = emptyList(),
    val activeBrowseCategory: String? = null,
    val statusMessage: String? = null
)

@OptIn(FlowPreview::class)
@HiltViewModel
class SearchViewModel @Inject constructor(
    @ApplicationContext appContext: Context,
    private val container: AppContainer
) : ViewModel() {

    private val historyPreferences =
        appContext.getSharedPreferences(SEARCH_HISTORY_PREFERENCES, Context.MODE_PRIVATE)
    private val _uiState = MutableStateFlow(
        SearchUiState(searchHistory = loadSearchHistory())
    )
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    private val searchQueryFlow = MutableStateFlow("")
    private var activeSearchGeneration = 0
    private var activeSearchJob: Job? = null
    private var activeSuggestionGeneration = 0
    private var activeSuggestionJob: Job? = null
    private var lastManualQuery = ""
    private var lastManualSubmissionAtMs = 0L
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .build()

    init {
        viewModelScope.launch {
            searchQueryFlow
                .debounce(SEARCH_DEBOUNCE_MS)
                .distinctUntilChanged()
                .collectLatest { rawQuery ->
                    if (_uiState.value.activeBrowseCategory != null) return@collectLatest
                    val query = sanitizeQuery(rawQuery)
                    if (query.equals(lastManualQuery, ignoreCase = true) &&
                        System.currentTimeMillis() - lastManualSubmissionAtMs < MANUAL_SEARCH_SUPPRESSION_MS
                    ) {
                        return@collectLatest
                    }
                    when {
                        query.length >= MIN_SEARCH_LENGTH -> {
                            _uiState.update { it.copy(isSearching = true) }
                            fetchSuggestions(query)
                            if (_uiState.value.activeBrowseCategory == null) performSearch(query, isManual = false)
                        }
                        query.length == MIN_TYPEAHEAD_LENGTH -> {
                            clearResults()
                            fetchSuggestions(query)
                        }
                        else -> clearResults(clearSuggestions = true)
                    }
                }
        }
    }

    fun onQueryChanged(value: String) {
        Log.d("VANTA_SEARCH_INPUT", "owner=SearchViewModel raw='$value' displayed='$value'")
        cancelInFlightWork()
        val matchedStations = if (value.trim().length >= MIN_TYPEAHEAD_LENGTH) {
            StationSearchResolver.resolveForSearch(value)
        } else {
            emptyList()
        }
        val prefix = sanitizeQuery(value)
        val keptSuggestions = if (prefix.length >= MIN_TYPEAHEAD_LENGTH) {
            (_uiState.value.suggestions + _uiState.value.searchHistory)
                .filter { MusicTypeahead.matchesPrefix(it, prefix) }
                .distinctBy { it.lowercase() }
                .take(8)
        } else {
            emptyList()
        }
        _uiState.update {
            it.copy(
                query = value,
                isSearching = false,
                libraryMatches = emptyList(),
                topResult = null,
                songs = emptyList(),
                albums = emptyList(),
                artists = emptyList(),
                playlists = emptyList(),
                suggestions = keptSuggestions,
                activeBrowseCategory = null,
                matchedStations = matchedStations,
                statusMessage = null
            )
        }
        searchQueryFlow.value = value
    }

    fun search() {
        val query = sanitizeQuery(_uiState.value.query)
        if (query.isBlank()) {
            clearResults(clearSuggestions = true)
            return
        }
        rememberSearch(query)
        markManualSubmission(query)
        performSearch(query, isManual = true)
    }

    fun searchFor(value: String) {
        val query = sanitizeQuery(value)
        if (query.isBlank()) return
        cancelInFlightWork()
        _uiState.update { it.copy(query = query, activeBrowseCategory = null, statusMessage = null) }
        searchQueryFlow.value = query
        rememberSearch(query)
        markManualSubmission(query)
        performSearch(query, isManual = true)
    }

    fun searchByTitleArtist(title: String, artist: String) {
        searchFor(listOf(title.trim(), artist.trim()).filter(String::isNotBlank).joinToString(" "))
    }

    fun rememberSearch(value: String) {
        val query = sanitizeQuery(value)
        if (query.isBlank()) return
        val history = (listOf(query) + _uiState.value.searchHistory.filterNot {
            it.equals(query, ignoreCase = true)
        }).take(MAX_HISTORY)
        persistSearchHistory(history)
        _uiState.update { it.copy(searchHistory = history) }
    }

    fun removeSearchHistory(value: String) {
        val history = _uiState.value.searchHistory.filterNot { it.equals(value, ignoreCase = true) }
        persistSearchHistory(history)
        _uiState.update { it.copy(searchHistory = history) }
    }

    fun clearSearchHistory() {
        persistSearchHistory(emptyList())
        _uiState.update { it.copy(searchHistory = emptyList()) }
    }

    fun searchCategory(category: String) {
        val cleanCategory = category.trim()
        if (cleanCategory.isBlank()) return

        cancelInFlightWork()
        val generation = ++activeSearchGeneration
        activeSearchJob = viewModelScope.launch {
            _uiState.update {
                it.copy(
                    query = "",
                    activeBrowseCategory = cleanCategory,
                    isSearching = true,
                    statusMessage = null,
                    libraryMatches = emptyList(),
                    topResult = null,
                    songs = emptyList(),
                    albums = emptyList(),
                    artists = emptyList(),
                    playlists = emptyList(),
                    suggestions = emptyList(),
                    matchedStations = emptyList()
                )
            }
            val catalogTracks = try {
                withContext(Dispatchers.IO) {
                    container.catalogBrowseRepository.browseCategory(cleanCategory, limit = 50)
                }
            } catch (_: CancellationException) {
                return@launch
            } catch (error: Exception) {
                VantaLogger.e(VantaLogger.Tag.SEARCH, "category_browse_failed category='$cleanCategory'", error)
                emptyList()
            }
            if (generation != activeSearchGeneration) return@launch

            // The Deezer-direct catalog path can come back empty while the gateway-
            // backed search engine still works (geo-blocks, rate limits, empty playlist
            // matches). Use real search as a last-resort so categories never dead-end.
            val fallbackTracks = if (catalogTracks.isEmpty()) {
                withContext(Dispatchers.IO) {
                    runCatching {
                        withTimeout(CATEGORY_FALLBACK_TIMEOUT_MS) {
                            container.searchRepository.search(SearchRequest(queryText = cleanCategory))
                        }
                    }.getOrNull()?.grouped?.songs.orEmpty()
                }
            } else {
                emptyList()
            }
            if (generation != activeSearchGeneration) return@launch

            val tracks = (catalogTracks + fallbackTracks)
                .distinctBy { "${it.title.trim().lowercase()}|${it.artist.trim().lowercase()}" }
                .filter { it.title.isNotBlank() && it.artist.isNotBlank() }
                .sortedWith(
                    compareByDescending<CanonicalTrack> { it.sourceStatus?.isConfirmedPlayable() == true }
                        .thenByDescending { it.sourcePriority }
                        .thenByDescending { it.qualityInfo?.bitrateKbps ?: 0 }
                )
                .take(40)
            val response = com.audiophile.musicplayer.data.catalog.CategoryBrowseResults.from(tracks)
            _uiState.update {
                it.copy(
                    isSearching = false,
                    topResult = response.songs.firstOrNull(),
                    songs = response.songs,
                    albums = response.albums,
                    artists = response.artists,
                    statusMessage = if (response.songs.isEmpty()) {
                        "No $cleanCategory tracks found"
                    } else {
                        "$cleanCategory: ${response.songs.size} tracks"
                    }
                )
            }
        }
    }

    private fun performSearch(rawQuery: String, isManual: Boolean) {
        val query = sanitizeQuery(rawQuery)
        com.audiophile.musicplayer.data.catalog.BrowseCatalog.parseGenreQuery(query)?.let { searchCategory(it); return }
        if (isSupportedPlatformUrl(query)) {
            performPlatformLinkSearch(query)
            return
        }
        val matchedStations = StationSearchResolver.resolveForSearch(query)
        if (query.length < MIN_SEARCH_LENGTH && matchedStations.isEmpty()) {
            _uiState.update {
                it.copy(
                    isSearching = false,
                    matchedStations = emptyList(),
                    statusMessage = if (isManual) "Keep typing to search music." else it.statusMessage
                )
            }
            return
        }

        activeSearchJob?.cancel()
        val generation = ++activeSearchGeneration
        val searchStartMs = System.currentTimeMillis()
        activeSearchJob = viewModelScope.launch {
            if (isManual) {
                _uiState.update {
                    it.copy(isSearching = true, statusMessage = null, matchedStations = matchedStations)
                }
            }
            if (query.length < MIN_SEARCH_LENGTH) {
                if (generation == activeSearchGeneration) {
                    clearResults()
                    _uiState.update { it.copy(matchedStations = matchedStations) }
                }
                return@launch
            }

            val localMatches = withContext(Dispatchers.IO) {
                container.searchRepository.searchLibraryOnly(query)
            }
            if (generation == activeSearchGeneration && localMatches.isNotEmpty()) {
                _uiState.update {
                    it.copy(
                        libraryMatches = localMatches,
                        isSearching = true,
                        matchedStations = matchedStations
                    )
                }
            }

            val result = try {
                withContext(Dispatchers.IO) {
                    withTimeout(SEARCH_TIMEOUT_MS) {
                        container.searchRepository.search(SearchRequest(queryText = query))
                    }
                }
            } catch (_: kotlinx.coroutines.TimeoutCancellationException) {
                if (generation == activeSearchGeneration) {
                    _uiState.update {
                        it.copy(
                            isSearching = false,
                            matchedStations = matchedStations,
                            statusMessage = "Search timed out. Try a simpler query."
                        )
                    }
                }
                return@launch
            } catch (_: CancellationException) {
                Log.d("VANTA_SEARCH_INPUT", "search_stale_cancelled query='$query' generation=$generation")
                return@launch
            } catch (error: Exception) {
                VantaLogger.e(VantaLogger.Tag.SEARCH, "search_failed query='$query'", error)
                if (generation == activeSearchGeneration) {
                    _uiState.update {
                        it.copy(
                            isSearching = false,
                            matchedStations = matchedStations,
                            statusMessage = "Search failed. Check your connection."
                        )
                    }
                }
                return@launch
            }
            if (generation != activeSearchGeneration) return@launch

            val totalMs = System.currentTimeMillis() - searchStartMs
            Log.d(
                "VANTA_SEARCH",
                "owner=SearchViewModel query='$query' local=${result.localMatches.size} " +
                    "source=${result.sourceResults.size} stations=${matchedStations.size} totalMs=$totalMs"
            )
            _uiState.update {
                it.copy(
                    libraryMatches = result.localMatches,
                    topResult = result.grouped.topResult,
                    songs = result.grouped.songs,
                    albums = result.grouped.albums,
                    artists = result.grouped.artists,
                    playlists = result.grouped.playlists,
                    matchedStations = matchedStations,
                    isSearching = false,
                    suggestions = it.suggestions,
                    statusMessage = if (isManual) {
                        when {
                            matchedStations.isNotEmpty() && result.grouped.songs.isEmpty() && result.localMatches.isEmpty() ->
                                "${matchedStations.first().name} station ready"
                            result.localMatches.isNotEmpty() -> "Found ${result.localMatches.size} library match(es)"
                            result.grouped.songs.isNotEmpty() -> "Found ${result.grouped.songs.size} source match(es)"
                            matchedStations.isNotEmpty() -> "${matchedStations.first().name} station ready"
                            else -> "No results found"
                        }
                    } else {
                        it.statusMessage
                    }
                )
            }
        }
    }

    private fun performPlatformLinkSearch(url: String) {
        activeSearchJob?.cancel()
        val generation = ++activeSearchGeneration
        activeSearchJob = viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isSearching = true,
                    statusMessage = "Resolving music link…",
                    libraryMatches = emptyList(),
                    topResult = null,
                    songs = emptyList(),
                    albums = emptyList(),
                    artists = emptyList(),
                    playlists = emptyList(),
                    suggestions = emptyList()
                )
            }
            val collection = runCatching {
                withTimeout(LINK_TIMEOUT_MS) { container.platformLinkResolver.resolveCollection(url) }
            }.getOrNull()
            if (generation != activeSearchGeneration) return@launch
            if (collection != null) {
                val tracks = collection.tracks.map(::canonicalTrack)
                _uiState.update {
                    it.copy(
                        isSearching = false,
                        topResult = tracks.firstOrNull(),
                        songs = tracks,
                        albums = if (collection.type == "album") {
                            listOf(
                                CanonicalAlbum(
                                    title = collection.title,
                                    artist = collection.creator.orEmpty(),
                                    artworkUrl = collection.artworkUrl,
                                    trackCount = tracks.size
                                )
                            )
                        } else {
                            emptyList()
                        },
                        statusMessage = "${collection.platform} ${collection.type}: " +
                            "${collection.title} · ${tracks.size} tracks"
                    )
                }
                return@launch
            }

            val metadata = runCatching {
                withTimeout(LINK_TIMEOUT_MS) {
                    container.platformLinkResolver.resolve(SoundiizTextParser.parseLine(url))
                }
            }.getOrNull()
            if (generation != activeSearchGeneration) return@launch
            val track = metadata?.takeIf { !it.artist.isNullOrBlank() }?.let(::canonicalTrack)
            _uiState.update {
                it.copy(
                    isSearching = false,
                    topResult = track,
                    songs = listOfNotNull(track),
                    statusMessage = if (track != null) {
                        "Resolved ${metadata.platform}: ${track.title} — ${track.artist}"
                    } else if (isApplePlaylistUrl(url)) {
                        "Apple playlist access requires a valid Apple Music developer token in Settings."
                    } else {
                        "This link could not be resolved. Check that the shared item is public."
                    }
                )
            }
        }
    }

    private fun fetchSuggestions(query: String) {
        activeSuggestionJob?.cancel()
        val generation = ++activeSuggestionGeneration
        activeSuggestionJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                val encoded = java.net.URLEncoder.encode(query, Charsets.UTF_8.name())
                val autocomplete = executeSuggestionRequest(
                    Request.Builder()
                        .url("https://api.deezer.com/search/autocomplete?q=$encoded")
                        .build()
                )
                val catalogBody = if (!autocomplete.isNullOrBlank()) {
                    autocomplete
                } else {
                    executeSuggestionRequest(
                        Request.Builder()
                            .url("https://api.deezer.com/search/track?q=$encoded&limit=25")
                            .build()
                    )
                }
                val libraryLabels = container.trackRepository.getAllTracks()
                    .asSequence()
                    .flatMap { sequenceOf(it.track.title, it.track.artist) }
                    .filter(String::isNotBlank)
                    .toList()
                val graphArtists = container.canonicalMusicResolver
                    .artistsMatchingPrefix(query, limit = 6)
                    .map { it.canonicalName }
                val graphAlbums = container.canonicalMusicResolver
                    .albumsMatchingPrefix(query, limit = 4)
                    .map { it.title }
                val suggestions = MusicTypeahead.suggestions(
                    query = query,
                    catalogResponse = catalogBody,
                    libraryLabels = libraryLabels,
                    graphArtistLabels = graphArtists,
                    graphAlbumLabels = graphAlbums
                )
                if (generation == activeSuggestionGeneration &&
                    _uiState.value.query.trim().equals(query, ignoreCase = true)
                ) {
                    Log.d(
                        "VANTA_SEARCH_INPUT",
                        "suggestions_applied owner=SearchViewModel query='$query' count=${suggestions.size} " +
                            "first='${suggestions.firstOrNull().orEmpty()}' generation=$generation"
                    )
                    _uiState.update { it.copy(suggestions = suggestions) }
                } else {
                    Log.d("VANTA_SEARCH_INPUT", "suggestions_stale_aborted query='$query' generation=$generation")
                }
            } catch (_: CancellationException) {
                Log.d("VANTA_SEARCH_INPUT", "suggestions_cancelled query='$query' generation=$generation")
            } catch (error: Exception) {
                VantaLogger.w(VantaLogger.Tag.SEARCH, "suggestions_failed query='$query' err='${error.message}'")
            }
        }
    }

    private suspend fun executeSuggestionRequest(request: Request): String? =
        suspendCancellableCoroutine { continuation ->
            val call = httpClient.newCall(request)
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: java.io.IOException) {
                    if (continuation.isActive) continuation.resumeWithException(e)
                }

                override fun onResponse(call: Call, response: Response) {
                    response.use {
                        val body = if (it.isSuccessful) it.body?.string() else null
                        if (continuation.isActive) continuation.resume(body)
                    }
                }
            })
        }

    private fun cancelInFlightWork() {
        activeSearchGeneration++
        activeSearchJob?.cancel()
        activeSuggestionGeneration++
        activeSuggestionJob?.cancel()
    }

    private fun markManualSubmission(query: String) {
        lastManualQuery = query
        lastManualSubmissionAtMs = System.currentTimeMillis()
    }

    private fun clearResults(clearSuggestions: Boolean = false) {
        _uiState.update {
            it.copy(
                isSearching = false,
                libraryMatches = emptyList(),
                topResult = null,
                songs = emptyList(),
                albums = emptyList(),
                artists = emptyList(),
                playlists = emptyList(),
                matchedStations = if (it.query.trim().length >= MIN_TYPEAHEAD_LENGTH) {
                    StationSearchResolver.resolveForSearch(it.query)
                } else {
                    emptyList()
                },
                suggestions = if (clearSuggestions) emptyList() else it.suggestions
            )
        }
    }

    private fun sanitizeQuery(raw: String): String {
        val lines = raw.trim().lines().map(String::trim).filter(String::isNotBlank)
        val deduplicated = if (lines.size > 1 && lines.distinct().size == 1) {
            listOf(lines.first())
        } else {
            lines
        }
        return deduplicated.joinToString(" ").replace(Regex("""\s+"""), " ").take(MAX_QUERY_LENGTH)
    }

    private fun loadSearchHistory(): List<String> =
        historyPreferences.getString(SEARCH_HISTORY_KEY, "")
            .orEmpty()
            .lineSequence()
            .map(String::trim)
            .filter(String::isNotBlank)
            .distinctBy(String::lowercase)
            .take(MAX_HISTORY)
            .toList()

    private fun persistSearchHistory(history: List<String>) {
        historyPreferences.edit { putString(SEARCH_HISTORY_KEY, history.joinToString("\n")) }
    }

    private fun canonicalTrack(metadata: PlatformLinkMetadata): CanonicalTrack = CanonicalTrack(
        title = metadata.title,
        artist = metadata.artist.orEmpty(),
        album = metadata.album,
        isrc = metadata.isrc,
        durationMs = metadata.durationMs,
        artworkUrl = metadata.artworkUrl,
        sourcePriority = 100,
        sourceStatus = SearchItemStatus.METADATA_ONLY,
        sourceProviderId = metadata.platform,
        externalTrackId = metadata.externalId
    )

    private fun isSupportedPlatformUrl(value: String): Boolean {
        val lower = value.trim().lowercase()
        if (!lower.startsWith("http://") && !lower.startsWith("https://")) return false
        return SUPPORTED_MUSIC_LINK_HOSTS.any(lower::contains)
    }

    private fun isApplePlaylistUrl(value: String): Boolean {
        val lower = value.lowercase()
        return "music.apple.com" in lower && "/playlist/" in lower
    }

    override fun onCleared() {
        cancelInFlightWork()
        httpClient.dispatcher.cancelAll()
        super.onCleared()
    }

    private companion object {
        const val SEARCH_DEBOUNCE_MS = 300L
        const val MANUAL_SEARCH_SUPPRESSION_MS = 1_000L
        const val SEARCH_TIMEOUT_MS = 10_000L
        const val LINK_TIMEOUT_MS = 18_000L
        const val CATEGORY_FALLBACK_TIMEOUT_MS = 10_000L
        const val MIN_TYPEAHEAD_LENGTH = 2
        const val MIN_SEARCH_LENGTH = 3
        const val MAX_QUERY_LENGTH = 200
        const val MAX_HISTORY = 20
        const val SEARCH_HISTORY_PREFERENCES = "vanta_search_history"
        const val SEARCH_HISTORY_KEY = "history_list"
        val SUPPORTED_MUSIC_LINK_HOSTS = listOf(
            "music.apple.com", "itunes.apple.com", "open.spotify.com", "spotify.link",
            "tidal.com", "listen.tidal.com", "qobuz.com", "play.qobuz.com",
            "deezer.com", "deezer.page.link", "music.amazon.com", "music.amazon.co",
            "amazon.com/music",
            "soundcloud.com", "pandora.com", "napster.com", "audiomack.com",
            "bandcamp.com", "song.link", "album.link", "odesli.co"
        )
    }
}
