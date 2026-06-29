package com.audiophile.musicplayer.ui

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.audiophile.musicplayer.AppContainer
import com.audiophile.musicplayer.common.VantaLogger
import com.audiophile.musicplayer.common.VantaResult
import com.audiophile.musicplayer.data.canonical.CanonicalAlbum
import com.audiophile.musicplayer.data.canonical.CanonicalArtist
import com.audiophile.musicplayer.data.canonical.CanonicalTrack
import com.audiophile.musicplayer.data.dj.StationSearchResolver
import com.audiophile.musicplayer.data.dj.JukeboxStation
import com.audiophile.musicplayer.data.source.SearchItemStatus
import com.audiophile.musicplayer.data.source.isConfirmedPlayable
import com.audiophile.musicplayer.search.SearchRequest
import com.audiophile.musicplayer.search.UnifiedSearchEngine
import com.google.gson.JsonParser
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import javax.inject.Inject

/**
 * UiState for the search screen.
 * Replaces the search-related fields that were previously scattered
 * across [MainUiState].
 */
data class SearchUiState(
    val query: String = "",
    val isSearching: Boolean = false,
    val topResult: CanonicalTrack? = null,
    val songs: List<CanonicalTrack> = emptyList(),
    val albums: List<CanonicalAlbum> = emptyList(),
    val artists: List<CanonicalArtist> = emptyList(),
    val suggestions: List<String> = emptyList(),
    val matchedStations: List<JukeboxStation> = emptyList(),
    val searchHistory: List<String> = emptyList(),
    val activeBrowseCategory: String? = null,
    val statusMessage: String? = null,
)

/** User-initiated actions on the search screen. */
sealed class SearchUiEvent {
    data class QueryChanged(val query: String) : SearchUiEvent()
    data class SearchSubmitted(val query: String) : SearchUiEvent()
    data class CategorySelected(val category: String) : SearchUiEvent()
    data object ClearSearch : SearchUiEvent()
    data class HistoryItemSelected(val query: String) : SearchUiEvent()
    data object ClearHistory : SearchUiEvent()
}

/**
 * Owns all search state: query, suggestions, results (songs/albums/artists),
 * station matches, and search history.
 *
 * Previously embedded in [MainViewModel]; extracted for single-responsibility.
 */
@OptIn(FlowPreview::class)
@HiltViewModel
class SearchViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val container: AppContainer
) : ViewModel() {

    private val _uiState = MutableStateFlow(SearchUiState())
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    private val searchQueryFlow = MutableStateFlow("")
    private var activeSearchGeneration = 0

    /** Max history entries kept in memory. */
    private val MAX_HISTORY = 20

    private val httpClient = okhttp3.OkHttpClient.Builder()
        .connectTimeout(5, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(5, java.util.concurrent.TimeUnit.SECONDS)
        .build()

    init {
        viewModelScope.launch {
            searchQueryFlow
                .debounce(300)
                .distinctUntilChanged()
                .collectLatest { query ->
                    val trimmed = query.trim()
                    if (trimmed.length >= 3) {
                        _uiState.update { it.copy(isSearching = true) }
                        fetchSuggestions(trimmed)
                        performSearch(trimmed, isManual = false)
                    } else {
                        _uiState.update {
                            it.copy(
                                isSearching = false,
                                topResult = null,
                                songs = emptyList(),
                                albums = emptyList(),
                                artists = emptyList(),
                                suggestions = emptyList(),
                                matchedStations = emptyList()
                            )
                        }
                    }
                }
        }
    }

    fun onEvent(event: SearchUiEvent) {
        when (event) {
            is SearchUiEvent.QueryChanged -> onQueryChanged(event.query)
            is SearchUiEvent.SearchSubmitted -> submitSearch(event.query)
            is SearchUiEvent.CategorySelected -> searchCategory(event.category)
            is SearchUiEvent.ClearSearch -> clearSearch()
            is SearchUiEvent.HistoryItemSelected -> submitSearch(event.query)
            is SearchUiEvent.ClearHistory -> _uiState.update { it.copy(searchHistory = emptyList()) }
        }
    }

    private fun onQueryChanged(value: String) {
        val matchedStations = if (value.trim().length >= 2) {
            StationSearchResolver.resolve(value)
        } else emptyList()
        _uiState.update {
            it.copy(
                query = value,
                activeBrowseCategory = null,
                matchedStations = matchedStations
            )
        }
        searchQueryFlow.value = value
    }

    private fun submitSearch(rawQuery: String) {
        val trimmed = rawQuery.trim()
        _uiState.update { it.copy(query = trimmed, activeBrowseCategory = null) }
        if (trimmed.isNotBlank()) {
            addToHistory(trimmed)
            performSearch(trimmed, isManual = true)
        }
    }

    private fun clearSearch() {
        _uiState.update {
            SearchUiState(searchHistory = it.searchHistory)
        }
        searchQueryFlow.value = ""
    }

    private fun addToHistory(query: String) {
        _uiState.update { state ->
            val updated = (listOf(query) + state.searchHistory.filter { it != query })
                .take(MAX_HISTORY)
            state.copy(searchHistory = updated)
        }
    }

    private fun fetchSuggestions(query: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val encoded = java.net.URLEncoder.encode(query, "UTF-8")
                val url = "https://suggestqueries.google.com/complete/search?client=youtube&ds=yt&q=$encoded"
                val request = okhttp3.Request.Builder().url(url).build()
                httpClient.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val body = response.body?.string()
                        if (!body.isNullOrBlank()) {
                            val json = JsonParser.parseString(body).asJsonArray
                            val arr = json.get(1).asJsonArray
                            val suggestions = (0 until arr.size()).map { arr.get(it).asString }
                            _uiState.update { it.copy(suggestions = suggestions) }
                        }
                    }
                }
            } catch (e: Exception) {
                VantaLogger.w(VantaLogger.Tag.SEARCH, "suggestions_failed query='$query' err='${e.message}'")
            }
        }
    }

    private fun searchCategory(category: String) {
        val cleanCategory = category.trim()
        val stationMatch = StationSearchResolver.resolveSingle(cleanCategory)
        if (stationMatch != null) {
            _uiState.update {
                it.copy(
                    query = cleanCategory,
                    activeBrowseCategory = null,
                    matchedStations = listOf(stationMatch),
                    isSearching = false
                )
            }
            return
        }
        if (cleanCategory.isBlank()) return
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    query = "",
                    activeBrowseCategory = cleanCategory,
                    isSearching = true,
                    statusMessage = null,
                    topResult = null,
                    songs = emptyList(),
                    albums = emptyList(),
                    artists = emptyList(),
                    suggestions = emptyList()
                )
            }

            val catalogTracks = try {
                withContext(Dispatchers.IO) {
                    container.catalogBrowseRepository.browseCategory(cleanCategory, limit = 50)
                }
            } catch (e: Exception) {
                VantaLogger.e(VantaLogger.Tag.SEARCH, "category_browse_failed category='$cleanCategory'", e)
                emptyList()
            }

            val songs = catalogTracks
                .filter { it.title.isNotBlank() && it.artist.isNotBlank() }
                .sortedWith(
                    compareByDescending<CanonicalTrack> { it.sourceStatus?.isConfirmedPlayable() == true }
                        .thenByDescending { it.qualityInfo?.bitrateKbps ?: 0 }
                        .thenBy { it.artist.lowercase() }
                        .thenBy { it.title.lowercase() }
                )
                .take(40)

            val response = UnifiedSearchEngine.process(cleanCategory, songs)
            _uiState.update {
                it.copy(
                    isSearching = false,
                    topResult = response.topResult,
                    songs = response.songs,
                    albums = response.albums,
                    artists = response.artists,
                    statusMessage = if (response.songs.isEmpty()) "No $cleanCategory tracks found"
                                    else "$cleanCategory: ${response.songs.size} tracks"
                )
            }
        }
    }

    private fun performSearch(rawQuery: String, isManual: Boolean) {
        val query = rawQuery.trim()
        val matchedStations = StationSearchResolver.resolve(query)
        if (query.length < 3 && matchedStations.isEmpty()) {
            _uiState.update {
                it.copy(
                    isSearching = false,
                    matchedStations = emptyList(),
                    statusMessage = if (isManual) "Keep typing to search music." else it.statusMessage
                )
            }
            return
        }
        val searchStartMs = System.currentTimeMillis()
        val generation = ++activeSearchGeneration
        viewModelScope.launch {
            if (isManual) {
                _uiState.update { it.copy(isSearching = true, statusMessage = null, matchedStations = matchedStations) }
            }

            if (query.length < 3) {
                if (generation != activeSearchGeneration) return@launch
                _uiState.update {
                    it.copy(
                        topResult = null,
                        songs = emptyList(),
                        albums = emptyList(),
                        artists = emptyList(),
                        matchedStations = matchedStations,
                        isSearching = false
                    )
                }
                return@launch
            }

            val result = try {
                withContext(Dispatchers.IO) {
                    withTimeout(28_000L) {
                        container.searchRepository.search(SearchRequest(queryText = query))
                    }
                }
            } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
                VantaLogger.e(VantaLogger.Tag.SEARCH, "timeout query='$query'")
                if (generation != activeSearchGeneration) return@launch
                _uiState.update {
                    it.copy(isSearching = false, matchedStations = matchedStations, statusMessage = "Search timed out. Try a simpler query.")
                }
                return@launch
            } catch (e: Exception) {
                VantaLogger.e(VantaLogger.Tag.SEARCH, "search_failed query='$query'", e)
                if (generation != activeSearchGeneration) return@launch
                _uiState.update {
                    it.copy(isSearching = false, matchedStations = matchedStations, statusMessage = "Search failed. Check your connection.")
                }
                return@launch
            }

            if (generation != activeSearchGeneration) return@launch
            val totalMs = System.currentTimeMillis() - searchStartMs
            VantaLogger.d(VantaLogger.Tag.SEARCH, "query='$query' local=${result.localMatches.size} source=${result.sourceResults.size} stations=${matchedStations.size} totalMs=$totalMs")

            _uiState.update {
                it.copy(
                    topResult = result.grouped.topResult,
                    songs = result.grouped.songs,
                    albums = result.grouped.albums,
                    artists = result.grouped.artists,
                    matchedStations = matchedStations,
                    isSearching = false,
                    suggestions = if (result.grouped.allResults.isNotEmpty()) emptyList() else it.suggestions,
                    statusMessage = if (isManual) {
                        when {
                            matchedStations.isNotEmpty() && result.grouped.songs.isEmpty() && result.localMatches.isEmpty() ->
                                "${matchedStations.first().name} station ready"
                            result.localMatches.isNotEmpty() -> "Found ${result.localMatches.size} library match(es)"
                            result.grouped.songs.isNotEmpty() -> "Found ${result.grouped.songs.size} source match(es)"
                            matchedStations.isNotEmpty() -> "${matchedStations.first().name} station ready"
                            else -> "No results found"
                        }
                    } else it.statusMessage
                )
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        httpClient.dispatcher.cancelAll()
    }
}
