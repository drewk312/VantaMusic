package com.audiophile.musicplayer.ui

import android.content.Context
import android.app.DownloadManager
import android.util.Log
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.audiophile.musicplayer.AppContainer
import com.audiophile.musicplayer.debug.VantaDiagnosticLog
import com.audiophile.musicplayer.data.importer.ImportMatchStatus
import com.audiophile.musicplayer.data.voice.PulseVoiceProfile
import com.audiophile.musicplayer.data.local.LibraryCounts
import com.audiophile.musicplayer.data.local.entities.ImportBatchEntity
import com.audiophile.musicplayer.data.local.entities.ImportedTrackEntity
import com.audiophile.musicplayer.data.local.entities.LocalSongEntity
import com.audiophile.musicplayer.data.local.entities.SourceType
import com.audiophile.musicplayer.data.local.entities.UnifiedTrackWithSources
import com.audiophile.musicplayer.data.importer.MatchConfidence
import com.audiophile.musicplayer.data.importer.PlayabilityStatus
import com.audiophile.musicplayer.data.importer.PlatformLinkMetadata
import com.audiophile.musicplayer.data.importer.SoundiizTextParser
import com.audiophile.musicplayer.data.local.toPlayableQueueItem
import com.audiophile.musicplayer.data.remote.TorBoxImportCandidate
import com.audiophile.musicplayer.playback.NowPlayingState
import com.audiophile.musicplayer.playback.QueueSnapshot
import com.audiophile.musicplayer.playback.PlaybackQueueBuilder
import com.audiophile.musicplayer.search.SearchRequest
import com.audiophile.musicplayer.search.UnifiedSearchEngine
import com.audiophile.musicplayer.search.UnifiedSearchResponse
import com.audiophile.musicplayer.data.metadata.EnhancedMetadata
import com.audiophile.musicplayer.data.source.SourceSearchResult
import com.audiophile.musicplayer.data.source.SearchItemStatus
import com.audiophile.musicplayer.data.source.canEnterPlaybackFlow
import com.audiophile.musicplayer.data.source.canResolveStream
import com.audiophile.musicplayer.data.source.isConfirmedPlayable
import com.audiophile.musicplayer.data.source.isMetadataOnly
import com.audiophile.musicplayer.data.source.isUnavailable
import com.audiophile.musicplayer.data.source.isLikelyMusicTrack
import com.audiophile.musicplayer.data.source.isPlayableMusicCandidate
import com.audiophile.musicplayer.data.source.sourceValidityStatus
import com.audiophile.musicplayer.data.llm.AiProvider
import com.audiophile.musicplayer.data.source.external.ExternalSourceConfig
import com.audiophile.musicplayer.data.source.external.ExternalSourceProvider
import com.audiophile.musicplayer.data.dj.JukeboxCatalog
import com.audiophile.musicplayer.data.dj.JukeboxStation
import com.audiophile.musicplayer.data.dj.JukeboxTrackEligibility
import com.audiophile.musicplayer.data.dj.StationSearchResolver
import com.audiophile.musicplayer.data.dj.StreamingSeedParams
import com.audiophile.musicplayer.data.dj.toStreamingSeed
import com.audiophile.musicplayer.radio.toStationSeed
import com.audiophile.musicplayer.data.canonical.CanonicalAlbum
import com.audiophile.musicplayer.data.canonical.CanonicalArtist
import com.audiophile.musicplayer.data.canonical.CanonicalTrack
import com.audiophile.musicplayer.data.catalog.ArtistCatalog
import com.audiophile.musicplayer.data.catalog.AlbumCatalog
import com.audiophile.musicplayer.data.display.DisplayMetadataCleaner
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

data class ResolverConfigForm(
    val torBoxBaseUrl: String = "",
    val torBoxApiToken: String = "",
    val realDebridApiToken: String = "",
    val communityInstancesText: String = "",
    val llmProviderName: String = "",
    val llmApiKey: String = "",
    val llmProvidersWithKeys: Set<String> = emptySet(),
    val llmVerifiedProviders: Set<String> = emptySet(),
    val pulseVoiceRelayUrl: String = "",
    val pulseVoiceRelayToken: String = "",
    val pulseVoiceEngine: String = PulseVoiceProfile.GEMINI_ENGINE,
    val appleMusicDeveloperToken: String = "",
    val appleMusicStorefront: String = "us",
    val lastFmApiKey: String = "",
    val lastFmApiSecret: String = "",
    val lastFmUsername: String = "",
    val lastFmSessionKey: String = ""
)

private data class ImportedRowsSaveResult(
    val requestedCount: Int,
    val savedPlayableCount: Int,
    val savedMetadataCount: Int,
    val skippedDuplicates: Int,
    val unresolvedCount: Int
)

private data class LibraryRefreshSnapshot(
    val library: List<UnifiedTrackWithSources>,
    val localSongs: List<LocalSongEntity>,
    val torBoxCandidates: List<TorBoxImportCandidate>,
    val queueSnapshot: QueueSnapshot,
    val localLibraryCounts: LibraryCounts,
    val activeImportedTracks: List<ImportedTrackEntity>,
    val importBatches: List<ImportBatchEntity>,
    val activeTrackId: String?,
    val resolverConfig: ResolverConfigForm,
    val libraryAlbums: List<com.audiophile.musicplayer.data.local.entities.Album>
)

private data class ResolvedImportSource(
    val title: String,
    val artist: String,
    val album: String?,
    val artworkUrl: String?,
    val isrc: String?,
    val durationMs: Long?,
    val streamUrl: String,
    val bitrateKbps: Int,
    val providerId: String?,
    val externalTrackId: String?,
    val expiresAtMs: Long?,
    val qualityLabel: String?
)

data class MainUiState(
    val query: String = "",
    val localSongs: List<LocalSongEntity> = emptyList(),
    val library: List<UnifiedTrackWithSources> = emptyList(),
    val visibleTracks: List<UnifiedTrackWithSources> = emptyList(),
    val sourceResults: List<CanonicalTrack> = emptyList(),
    val searchSuggestions: List<String> = emptyList(),
    val torBoxCandidates: List<TorBoxImportCandidate> = emptyList(),
    val queueSnapshot: QueueSnapshot = QueueSnapshot(),
    val localLibraryCounts: LibraryCounts = LibraryCounts(
        songsCount = 0,
        favoritesCount = 0,
        recentlyPlayedCount = 0,
        playlistsCount = 0,
        importsCount = 0
    ),
    val activeImportBatchId: Long? = null,
    val activeImportedTracks: List<ImportedTrackEntity> = emptyList(),
    val importBatches: List<ImportBatchEntity> = emptyList(),
    val activeTrackId: String? = null,
    val isSearching: Boolean = false,
    val isScanningDeviceLibrary: Boolean = false,
    val deviceScanProgress: String? = null,
    val libraryNeedsRefresh: Boolean = false,
    val statusMessage: String? = null,
    val resolverConfig: ResolverConfigForm = ResolverConfigForm(),
    val activeTrackEnhancedMetadata: EnhancedMetadata? = null,
    val externalSources: List<ExternalSourceConfig> = emptyList(),
    val searchTopResult: com.audiophile.musicplayer.data.canonical.CanonicalTrack? = null,
    val searchSongs: List<com.audiophile.musicplayer.data.canonical.CanonicalTrack> = emptyList(),
    val searchAlbums: List<com.audiophile.musicplayer.data.canonical.CanonicalAlbum> = emptyList(),
    val searchArtists: List<com.audiophile.musicplayer.data.canonical.CanonicalArtist> = emptyList(),
    val activeBrowseCategory: String? = null,
    val sourceTestResults: List<SourceTestResult> = emptyList(),
    val sourceStreamTestResults: List<SourceStreamTestResult> = emptyList(),
    val sourceHealthStatuses: List<SourceHealthStatus> = emptyList(),
    val isTestingSource: Boolean = false,
    val libraryAlbums: List<com.audiophile.musicplayer.data.local.entities.Album> = emptyList(),
    val artistCatalog: ArtistCatalog? = null,
    val artistCatalogLoading: Boolean = false,
    val albumCatalog: AlbumCatalog? = null,
    val albumCatalogLoading: Boolean = false,
    val matchedStations: List<JukeboxStation> = emptyList(),
    val streamingStationLoading: Boolean = false
)

@OptIn(kotlinx.coroutines.FlowPreview::class)
class MainViewModel(
    private val appContext: Context,
    private val container: AppContainer
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        MainUiState(
            resolverConfig = loadResolverConfigForm(),
            externalSources = container.externalSourceConfigStore.getSources()
        )
    )
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    private val searchQueryFlow = MutableStateFlow("")
    private var activeSearchGeneration = 0

    private val httpClient = okhttp3.OkHttpClient.Builder()
        .connectTimeout(5, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(5, java.util.concurrent.TimeUnit.SECONDS)
        .build()

    private var lastRadioSeedKey: String? = null
    private var lastRadioSeedTimeMs: Long = 0L
    private val refreshMutex = Mutex()
    private var artistCatalogRequest: String? = null
    private var albumCatalogRequest: String? = null

    // ── Endless Station State ──
    private var activeStationSeed: StreamingSeedParams? = null
    private val playedStationTrackIds = ArrayDeque<String>(500)
    private var isRefillingStation = false
    private val REFILL_THRESHOLD = 5
    private var stationMonitorJob: kotlinx.coroutines.Job? = null

    fun loadArtistCatalog(artistName: String) {
        val cleanArtist = artistName.trim()
        if (cleanArtist.isBlank()) return
        if (artistCatalogRequest.equals(cleanArtist, ignoreCase = true) &&
            (_uiState.value.artistCatalogLoading || _uiState.value.artistCatalog?.artist?.name.equals(cleanArtist, ignoreCase = true))
        ) return
        artistCatalogRequest = cleanArtist
        _uiState.update { it.copy(artistCatalog = null, artistCatalogLoading = true) }
        viewModelScope.launch {
            val catalog = withContext(Dispatchers.IO) { container.catalogBrowseRepository.browseArtist(cleanArtist, limit = 50) }
            if (artistCatalogRequest.equals(cleanArtist, ignoreCase = true)) {
                _uiState.update { it.copy(artistCatalog = catalog, artistCatalogLoading = false) }
            }
        }
    }

    fun loadAlbumCatalog(albumName: String, artistName: String) {
        val request = "${albumName.trim().lowercase()}|${artistName.trim().lowercase()}"
        if (albumName.isBlank()) return
        if (albumCatalogRequest == request &&
            (_uiState.value.albumCatalogLoading || _uiState.value.albumCatalog?.album?.title.equals(albumName, ignoreCase = true))
        ) return
        albumCatalogRequest = request
        _uiState.update { it.copy(albumCatalog = null, albumCatalogLoading = true) }
        viewModelScope.launch {
            val catalog = withContext(Dispatchers.IO) { container.catalogBrowseRepository.browseAlbum(albumName, artistName) }
            if (albumCatalogRequest == request) {
                _uiState.update { it.copy(albumCatalog = catalog, albumCatalogLoading = false) }
            }
        }
    }

    private val sourceHealthHelper = SourceHealthHelper(
        container = container,
        appContext = appContext,
        scope = viewModelScope,
        _uiState = _uiState
    )

    init {
        refreshAll()
        resumeManagedDownloadImports()

        // Restore played history from DB for radio exclusion persistence
        viewModelScope.launch(Dispatchers.IO) {
            val recentIds = container.trackRepository.getRecentTrackIds(10)
            container.queueManager.loadPlayedHistory(recentIds)
            Log.d("VANTA_HISTORY", "restored recent count=${recentIds.size}")
        }

        // Reactively sync queue snapshot when track changes
        viewModelScope.launch {
            container.playbackStateHolder.state.collect { state: com.audiophile.musicplayer.playback.NowPlayingState ->
                if (state.trackId != _uiState.value.activeTrackId) {
                    val qs = container.queueManager.snapshot()
                    _uiState.update { it.copy(
                        activeTrackId = state.trackId,
                        queueSnapshot = qs,
                        activeTrackEnhancedMetadata = null
                    ) }
                }
                if (!state.errorMessage.isNullOrBlank()) {
                    _uiState.update { it.copy(statusMessage = state.errorMessage) }
                }
            }
        }

        viewModelScope.launch {
            searchQueryFlow
                .debounce(300)
                .distinctUntilChanged()
                .collectLatest { query ->
                    val trimmed = query.trim()
                    if (trimmed.length >= 3) {
                        _uiState.update { it.copy(isSearching = true) }
                        fetchSearchSuggestions(trimmed)
                        performSearch(trimmed, isManual = false)
                    } else if (trimmed.length < 3) {
                        _uiState.update {
                            it.copy(
                                isSearching = false,
                                sourceResults = emptyList(),
                                visibleTracks = emptyList(),
                                searchSuggestions = emptyList(),
                                searchTopResult = null,
                                searchSongs = emptyList(),
                                searchAlbums = emptyList(),
                                searchArtists = emptyList()
                            )
                        }
                    }
                }
        }
    }

    private fun fetchSearchSuggestions(query: String) {
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
                            val suggestionsArray = json.get(1).asJsonArray
                            val suggestions = mutableListOf<String>()
                            for (i in 0 until suggestionsArray.size()) {
                                suggestions.add(suggestionsArray.get(i).asString)
                            }
                            _uiState.update { it.copy(searchSuggestions = suggestions) }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("MainViewModel", "Failed to fetch search suggestions", e)
            }
        }
    }

    fun onQueryChanged(value: String) {
        Log.d("VANTA_SEARCH_INPUT", "raw='${value}' displayed='${value}'")
        val matchedStations = if (value.trim().length >= 2) {
            StationSearchResolver.resolve(value)
        } else {
            emptyList()
        }
        _uiState.update {
            it.copy(
                query = value,
                activeBrowseCategory = null,
                matchedStations = matchedStations
            )
        }
        searchQueryFlow.value = value
    }

    private fun sanitizeQuery(raw: String): String {
        val trimmed = raw.trim()
        if (trimmed.isBlank()) return ""
        val lines = trimmed.lines().map { it.trim() }.filter { it.isNotBlank() }
        val deduped = if (lines.size > 1 && lines.distinct().size == 1) {
            listOf(lines.first())
        } else lines
        val collapsed = deduped.joinToString(" ").replace(Regex("""\s+"""), " ")
        return collapsed.take(200)
    }

    fun searchByTitleArtist(title: String, artist: String) {
        viewModelScope.launch {
            val query = if (artist.isNotBlank()) "$title $artist".trim() else title.trim()
            onQueryChanged(query)
            _uiState.update { it.copy(statusMessage = "Searching for matches...") }
            if (query.isNotBlank()) {
                performSearch(query, isManual = true)
            }
        }
    }

    fun searchFor(query: String) {
        val trimmed = query.trim()
        _uiState.update { it.copy(query = trimmed, activeBrowseCategory = null) }
        if (trimmed.isNotBlank()) {
            performSearch(trimmed, isManual = true)
        }
    }

    fun quickPlayFromUrl(url: String) {
        val trimmed = url.trim()
        if (trimmed.isBlank()) return
        viewModelScope.launch {
            _uiState.update { it.copy(statusMessage = "Resolving link...") }
            val collection = withContext(Dispatchers.IO) { resolvePlatformCollection(trimmed) }
            if (collection != null) {
                val catalogTracks = collection.tracks.map { it.toCanonicalTrack() }
                _uiState.update {
                    it.copy(
                        sourceResults = catalogTracks,
                        searchSongs = catalogTracks,
                        searchAlbums = if (collection.type == "album") listOf(
                            CanonicalAlbum(
                                title = collection.title,
                                artist = collection.creator.orEmpty(),
                                artworkUrl = collection.artworkUrl,
                                trackCount = catalogTracks.size
                            )
                        ) else emptyList(),
                        statusMessage = "Finding playable sources for ${collection.title}..."
                    )
                }
                val playable = withContext(Dispatchers.IO) {
                    coroutineScope {
                        val gate = Semaphore(permits = 4)
                        catalogTracks.take(50).map { track ->
                            async {
                                gate.withPermit { resolveCanonicalTrack(track) }
                            }
                        }.awaitAll().filterNotNull()
                    }
                }
                if (playable.isNotEmpty()) {
                    container.playerController.playQueue(playable, 0)
                    _uiState.update {
                        it.copy(
                            queueSnapshot = container.queueManager.snapshot(),
                            statusMessage = "Playing ${collection.title} · ${playable.size} tracks"
                        )
                    }
                } else {
                    _uiState.update { it.copy(statusMessage = "No playable sources found for ${collection.title}.") }
                }
                return@launch
            }
            val metadata = withContext(Dispatchers.IO) { resolvePlatformLink(trimmed) }
            if (metadata?.artist != null) {
                val track = metadata.toCanonicalTrack()
                _uiState.update {
                    it.copy(statusMessage = "Resolved ${metadata.platform ?: "music"}: ${metadata.title} — ${metadata.artist}")
                }
                playSourceResult(track)
            } else {
                _uiState.update {
                    it.copy(statusMessage = if (isApplePlaylistUrl(trimmed)) {
                        "Apple playlist access requires a valid Apple Music developer token in Settings."
                    } else "Could not resolve link: $trimmed")
                }
            }
        }
    }

    fun searchCategory(category: String) {
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
        val seeds = browseCategorySeeds(cleanCategory)
        if (cleanCategory.isBlank() || seeds.isEmpty()) return
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    query = "",
                    activeBrowseCategory = cleanCategory,
                    isSearching = true,
                    statusMessage = null,
                    sourceResults = emptyList(),
                    visibleTracks = emptyList(),
                    searchTopResult = null,
                    searchSongs = emptyList(),
                    searchAlbums = emptyList(),
                    searchArtists = emptyList(),
                    searchSuggestions = emptyList()
                )
            }

            val combined = linkedMapOf<String, CanonicalTrack>()
            val catalogTracks = try {
                withContext(Dispatchers.IO) { container.catalogBrowseRepository.browseCategory(cleanCategory, limit = 50) }
            } catch (e: Exception) {
                Log.e("VANTA_BROWSE", "category='$cleanCategory' catalog browse failed", e)
                emptyList()
            }
            catalogTracks.forEach { track ->
                val key = "${track.title.trim().lowercase()}|${track.artist.trim().lowercase()}"
                combined.putIfAbsent(key, track)
            }

            if (combined.isEmpty()) {
                for (seed in seeds) {
                    val result = try {
                        withContext(Dispatchers.IO) { container.searchRepository.search(SearchRequest(queryText = seed)) }
                    } catch (e: Exception) {
                        Log.e("VANTA_BROWSE", "category='$cleanCategory' seed='$seed' failed", e)
                        null
                    } ?: continue
                    result.sourceResults
                        .filter { it.title.isNotBlank() && it.artist.isNotBlank() }
                        .filter { categoryResultAllowed(cleanCategory, it) }
                        .forEach { track ->
                            val key = "${track.title.trim().lowercase()}|${track.artist.trim().lowercase()}"
                            combined.putIfAbsent(key, track)
                        }
                }
            }

            val songs = combined.values
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
                    sourceResults = response.songs,
                    searchTopResult = response.topResult,
                    searchSongs = response.songs,
                    searchAlbums = response.albums,
                    searchArtists = response.artists,
                    statusMessage = if (response.songs.isEmpty()) "No $cleanCategory tracks found" else "$cleanCategory: ${response.songs.size} tracks"
                )
            }
        }
    }

    fun playByTitleArtist(title: String, artist: String) {
        viewModelScope.launch {
            val tapMs = System.currentTimeMillis()
            val result = withContext(Dispatchers.IO) {
                val results = container.sourceRegistry.searchAll("$title $artist")
                var resolved: com.audiophile.musicplayer.data.source.ResolvedStream? = null
                var playable: SourceSearchResult? = null
                val triedProviders = mutableSetOf<String>()
                for (candidate in results) {
                    if (!candidate.status.canResolveStream()) continue
                    if (!triedProviders.add(candidate.providerId)) continue
                    val stream = container.sourceRegistry.resolveStream(candidate.providerId, candidate.id)
                    if (stream != null && stream.streamUrl.isNotBlank()) {
                        playable = candidate
                        resolved = stream
                        break
                    }
                }
                if (playable != null && resolved != null) {
                    val trackId = container.trackRepository.addTrackSource(
                        title = playable.title,
                        artist = playable.artist,
                        album = playable.album,
                        coverArtUrl = null,
                        sourceType = com.audiophile.musicplayer.data.local.entities.SourceType.ADDON,
                        streamUrl = resolved.streamUrl,
                        bitrate = resolved.bitrateKbps,
                        isrc = playable.isrc,
                        durationMs = playable.durationMs,
                        externalProviderId = playable.providerId,
                        externalTrackId = playable.id,
                        expiresAtMs = resolved.expiresAt
                    )
                    val track = container.trackRepository.getTrackWithSources(trackId)
                    if (track != null) {
                        Pair(playable, track)
                    } else null
                } else null
            }
            if (result != null) {
                val (playable, track) = result
                val queue = playbackQueueFor(track)
                val startIndex = queue.indexOfFirst { it.track.trackId == track.track.trackId }.coerceAtLeast(0)
                container.playerController.playQueue(queue, startIndex)
                container.nowPlayingStateStore.save(
                    com.audiophile.musicplayer.playback.NowPlayingState(
                        trackId = track.track.trackId.toString(),
                        title = playable.title,
                        artist = playable.artist,
                        album = playable.album,
                        durationMs = playable.durationMs ?: 0L,
                        queuePosition = startIndex,
                        queueSize = queue.size
                    )
                )
                _uiState.update {
                    it.copy(
                        activeTrackId = track.track.trackId.toString(),
                        queueSnapshot = container.queueManager.snapshot(),
                        statusMessage = "Playing ${DisplayMetadataCleaner.cleanTitle(track.track.title)}"
                    )
                }
                return@launch
            }
            searchByTitleArtist(title, artist)
        }
    }

    private fun onResolverConfigFieldChanged(field: String, value: String) {
        _uiState.update { current ->
            current.copy(resolverConfig = when (field) {
                "torBoxBaseUrl" -> current.resolverConfig.copy(torBoxBaseUrl = value)
                "communityInstancesText" -> current.resolverConfig.copy(communityInstancesText = value)
                "torBoxApiToken" -> current.resolverConfig.copy(torBoxApiToken = value)
                "realDebridApiToken" -> current.resolverConfig.copy(realDebridApiToken = value)
                "llmProviderName" -> current.resolverConfig.copy(llmProviderName = value)
                "llmApiKey" -> current.resolverConfig.copy(llmApiKey = value)
                "appleMusicDeveloperToken" -> current.resolverConfig.copy(appleMusicDeveloperToken = value)
                "appleMusicStorefront" -> current.resolverConfig.copy(appleMusicStorefront = value)
                "pulseVoiceRelayUrl" -> current.resolverConfig.copy(pulseVoiceRelayUrl = value)
                "pulseVoiceRelayToken" -> current.resolverConfig.copy(pulseVoiceRelayToken = value)
                "pulseVoiceEngine" -> current.resolverConfig.copy(pulseVoiceEngine = value)
                "lastFmApiKey" -> current.resolverConfig.copy(lastFmApiKey = value)
                "lastFmApiSecret" -> current.resolverConfig.copy(lastFmApiSecret = value)
                "lastFmUsername" -> current.resolverConfig.copy(lastFmUsername = value)
                "lastFmSessionKey" -> current.resolverConfig.copy(lastFmSessionKey = value)
                else -> current.resolverConfig
            })
        }
    }

    fun onTorBoxBaseUrlChanged(value: String) = onResolverConfigFieldChanged("torBoxBaseUrl", value)
    fun onCommunityInstancesChanged(value: String) = onResolverConfigFieldChanged("communityInstancesText", value)
    fun onTorBoxApiTokenChanged(value: String) = onResolverConfigFieldChanged("torBoxApiToken", value)
    fun onRealDebridApiTokenChanged(value: String) = onResolverConfigFieldChanged("realDebridApiToken", value)
    fun onLlmProviderChanged(value: String) {
        val provider = runCatching { AiProvider.valueOf(value) }.getOrNull()
        val keyForProvider = provider?.let { container.resolverConfigStore.getLlmApiKey(it) }.orEmpty()
        _uiState.update { current ->
            current.copy(
                resolverConfig = current.resolverConfig.copy(
                    llmProviderName = value,
                    llmApiKey = keyForProvider
                )
            )
        }
    }
    fun onLlmApiKeyChanged(value: String) = onResolverConfigFieldChanged("llmApiKey", value)
    fun onAppleMusicDeveloperTokenChanged(value: String) = onResolverConfigFieldChanged("appleMusicDeveloperToken", value)
    fun onAppleMusicStorefrontChanged(value: String) = onResolverConfigFieldChanged("appleMusicStorefront", value)
    fun onPulseVoiceRelayUrlChanged(value: String) = onResolverConfigFieldChanged("pulseVoiceRelayUrl", value)
    fun onPulseVoiceRelayTokenChanged(value: String) = onResolverConfigFieldChanged("pulseVoiceRelayToken", value)
    fun onPulseVoiceEngineChanged(value: String) = onResolverConfigFieldChanged("pulseVoiceEngine", value)
    fun onLastFmApiKeyChanged(value: String) = onResolverConfigFieldChanged("lastFmApiKey", value)
    fun onLastFmApiSecretChanged(value: String) = onResolverConfigFieldChanged("lastFmApiSecret", value)
    fun onLastFmUsernameChanged(value: String) = onResolverConfigFieldChanged("lastFmUsername", value)
    fun onLastFmSessionKeyChanged(value: String) = onResolverConfigFieldChanged("lastFmSessionKey", value)

    fun refreshAll(skipRoomMaterialize: Boolean = false) {
        viewModelScope.launch {
            if (_uiState.value.isScanningDeviceLibrary) {
                // #region agent log
                com.audiophile.musicplayer.debug.DebugSessionLogger.log(
                    hypothesisId = "H10",
                    location = "MainViewModel.refreshAll",
                    message = "refresh_skipped_scan_active",
                    data = emptyMap(),
                    runId = "post-fix-v6"
                )
                // #endregion
                return@launch
            }
            refreshMutex.withLock {
                try {
                    val refreshStartMs = System.currentTimeMillis()
                    val refreshed = withContext(Dispatchers.IO) {
                        loadLibrarySnapshot(skipRoomMaterialize = skipRoomMaterialize)
                    }
                // #region agent log
                com.audiophile.musicplayer.debug.DebugSessionLogger.log(
                    hypothesisId = "H1",
                    location = "MainViewModel.refreshAll",
                    message = "library_refresh_complete",
                    data = mapOf(
                        "refreshMs" to (System.currentTimeMillis() - refreshStartMs),
                        "librarySize" to refreshed.library.size
                    ),
                    runId = "post-fix-v3"
                )
                // #endregion
                _uiState.update { current ->
                    current.copy(
                        library = refreshed.library,
                        localSongs = refreshed.localSongs,
                        visibleTracks = if (current.query.isBlank()) refreshed.library else current.visibleTracks,
                        torBoxCandidates = refreshed.torBoxCandidates,
                        queueSnapshot = refreshed.queueSnapshot,
                        localLibraryCounts = refreshed.localLibraryCounts,
                        activeImportedTracks = refreshed.activeImportedTracks,
                        importBatches = refreshed.importBatches,
                        activeTrackId = refreshed.activeTrackId,
                        resolverConfig = refreshed.resolverConfig,
                        libraryAlbums = refreshed.libraryAlbums,
                        libraryNeedsRefresh = false
                    )
                }
            } catch (e: Exception) {
                Log.e("MainViewModel", "refreshAll failed", e)
                _uiState.update {
                    it.copy(
                        statusMessage = "Library refresh failed: ${e.message ?: "unknown error"}"
                    )
                }
            }
            }
        }
    }

    private suspend fun refreshLibraryAfterScan() {
        try {
            val counts = withContext(Dispatchers.IO) {
                container.localLibraryRepository.libraryCountsSnapshot()
            }
            val trackCount = withContext(Dispatchers.IO) {
                container.trackRepository.getTrackCount()
            }
            _uiState.update { current ->
                current.copy(
                    localLibraryCounts = counts,
                    libraryNeedsRefresh = true
                )
            }
            // #region agent log
            com.audiophile.musicplayer.debug.DebugSessionLogger.log(
                hypothesisId = "H13",
                location = "MainViewModel.refreshLibraryAfterScan",
                message = "refresh_counts_deferred_full_load",
                data = mapOf(
                    "localSongCount" to counts.songsCount,
                    "trackCount" to trackCount
                ),
                runId = "post-fix-v9"
            )
            // #endregion
        } catch (e: Exception) {
            Log.e("MainViewModel", "refreshLibraryAfterScan failed", e)
            // #region agent log
            com.audiophile.musicplayer.debug.DebugSessionLogger.log(
                hypothesisId = "H13",
                location = "MainViewModel.refreshLibraryAfterScan",
                message = "refresh_failed",
                data = mapOf(
                    "errorType" to e.javaClass.simpleName,
                    "error" to (e.message ?: "unknown")
                ),
                runId = "post-fix-v9"
            )
            // #endregion
        }
    }

    fun ensureLibraryLoaded() {
        val state = _uiState.value
        if (state.library.isNotEmpty() && !state.libraryNeedsRefresh) return
        refreshAll(skipRoomMaterialize = true)
    }

    private suspend fun playbackQueueFor(startTrack: UnifiedTrackWithSources): List<UnifiedTrackWithSources> {
        val library = withContext(Dispatchers.IO) {
            container.trackRepository.getAllTracks()
        }.ifEmpty { _uiState.value.library.ifEmpty { _uiState.value.visibleTracks } }
        return PlaybackQueueBuilder.build(startTrack, library)
    }

    private suspend fun loadLibrarySnapshot(skipRoomMaterialize: Boolean = false): LibraryRefreshSnapshot {
        container.registerConfiguredProviders()
        val roomPlayableTracks = if (skipRoomMaterialize) {
            emptyList()
        } else {
            materializePlayableRoomSongs()
        }
        val localSongs = container.localLibraryRepository.allSongsSnapshot()
        val library = mergeLibraryTracks(
            container.trackRepository.getAllTracks(),
            roomPlayableTracks
        )
        val activeTrackId = container.nowPlayingStateStore.load()?.trackId
        val queueSnapshot = container.queueManager.snapshot()
        val torBoxCandidates = loadTorBoxCandidates()
        val localCounts = container.localLibraryRepository.libraryCountsSnapshot()
        val currentImportBatchId = _uiState.value.activeImportBatchId
        val importedTracks = currentImportBatchId
            ?.let { container.localLibraryRepository.importedTracksByBatchSnapshot(it) }
            .orEmpty()
        val importBatches = container.localLibraryRepository.importBatchesSnapshot()
        val libraryAlbums = container.trackRepository.getAllAlbumsWithTracks().map { it.album }

        val likedCount = localSongs.count { it.isFavorite }
        Log.d("VANTA_LIBRARY_TRUTH", "likedCount=$likedCount totalLibrary=${library.size} totalLocalSongs=${localSongs.size}")

        return LibraryRefreshSnapshot(
            library = library,
            localSongs = localSongs,
            torBoxCandidates = torBoxCandidates,
            queueSnapshot = queueSnapshot,
            localLibraryCounts = localCounts,
            activeImportedTracks = importedTracks,
            importBatches = importBatches,
            activeTrackId = activeTrackId,
            resolverConfig = loadResolverConfigForm(),
            libraryAlbums = libraryAlbums
        )
    }

    fun search() {
        val query = _uiState.value.query.trim()
        if (query.isNotBlank()) {
            _uiState.update { it.copy(activeBrowseCategory = null) }
            performSearch(query, isManual = true)
        } else {
            viewModelScope.launch {
                val library = withContext(Dispatchers.IO) { container.trackRepository.getAllTracks() }
                _uiState.update {
                    it.copy(
                        library = library,
                        visibleTracks = library,
                        sourceResults = emptyList(),
                        statusMessage = if (library.isEmpty()) {
                            "Library is empty. Add tracks or configure a resolver instance."
                        } else {
                            "Showing library"
                        }
                    )
                }
            }
        }
    }

    private fun performSearch(rawQuery: String, isManual: Boolean) {
        if (isSupportedPlatformUrl(rawQuery)) {
            performPlatformLinkSearch(rawQuery.trim())
            return
        }
        val sanitizedQuery = sanitizeQuery(rawQuery).let { q ->
            if (q.isBlank() && rawQuery.isNotBlank()) sanitizeQuery(rawQuery) // preserve exact match for single-char queries
            else q
        }
        val query = if (sanitizedQuery.isBlank() && rawQuery.isNotBlank()) rawQuery.trim().take(200) else sanitizedQuery
        val searchTrimmed = query.trim()
        val matchedStations = StationSearchResolver.resolve(searchTrimmed)
        Log.d("VANTA_SEARCH_INPUT", "raw='${rawQuery}' displayed='${_uiState.value.query}' normalized='${searchTrimmed}' stations=${matchedStations.size}")
        if (searchTrimmed.length < 3 && matchedStations.isEmpty()) {
            Log.d("VANTA_SEARCH_FILTER", "skipped_short_query query='$searchTrimmed'")
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
                        visibleTracks = emptyList(),
                        sourceResults = emptyList(),
                        searchTopResult = null,
                        searchSongs = emptyList(),
                        searchAlbums = emptyList(),
                        searchArtists = emptyList(),
                        matchedStations = matchedStations,
                        isSearching = false,
                        statusMessage = if (isManual && matchedStations.isNotEmpty()) {
                            "${matchedStations.first().name} station ready"
                        } else it.statusMessage
                    )
                }
                return@launch
            }

            val result = try {
                withContext(Dispatchers.IO) {
                    kotlinx.coroutines.withTimeout(28_000L) {
                        container.searchRepository.search(SearchRequest(queryText = query))
                    }
                }
            } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
                Log.e("VANTA_SEARCH_PERF", "query='$query' timeout=true totalDurationMs=28000 action=hard_timeout")
                if (generation != activeSearchGeneration) return@launch
                _uiState.update {
                    it.copy(
                        isSearching = false,
                        matchedStations = matchedStations,
                        statusMessage = "Search timed out. Try a simpler query."
                    )
                }
                return@launch
            } catch (e: Exception) {
                Log.e("VANTA_SEARCH", "query='$query' failed", e)
                if (generation != activeSearchGeneration) return@launch
                _uiState.update {
                    it.copy(
                        isSearching = false,
                        matchedStations = matchedStations,
                        statusMessage = "Search failed. Check your connection."
                    )
                }
                return@launch
            }

            if (generation != activeSearchGeneration) return@launch
            val totalMs = System.currentTimeMillis() - searchStartMs
            Log.d("VANTA_SEARCH", "MainViewModel query='$query' local=${result.localMatches.size} source=${result.sourceResults.size} stations=${matchedStations.size} totalMs=$totalMs")

            _uiState.update {
                it.copy(
                    visibleTracks = result.localMatches,
                    sourceResults = result.grouped.songs,
                    searchTopResult = result.grouped.topResult,
                    searchSongs = result.grouped.songs,
                    searchAlbums = result.grouped.albums,
                    searchArtists = result.grouped.artists,
                    matchedStations = matchedStations,
                    activeTrackId = result.activeNowPlayingTrackId,
                    queueSnapshot = container.queueManager.snapshot(),
                    isSearching = false,
                    searchSuggestions = if (result.grouped.allResults.isNotEmpty()) emptyList() else it.searchSuggestions,
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

    private fun performPlatformLinkSearch(url: String) {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isSearching = true,
                    statusMessage = "Resolving music link…",
                    visibleTracks = emptyList(),
                    sourceResults = emptyList(),
                    searchTopResult = null,
                    searchSongs = emptyList(),
                    searchAlbums = emptyList(),
                    searchArtists = emptyList(),
                    searchSuggestions = emptyList()
                )
            }

            val collection = withContext(Dispatchers.IO) { resolvePlatformCollection(url) }
            if (collection != null) {
                val tracks = collection.tracks.map { it.toCanonicalTrack() }
                _uiState.update {
                    it.copy(
                        visibleTracks = emptyList(),
                        sourceResults = tracks,
                        searchTopResult = tracks.firstOrNull(),
                        searchSongs = tracks,
                        searchAlbums = if (collection.type == "album") listOf(
                            CanonicalAlbum(
                                title = collection.title,
                                artist = collection.creator.orEmpty(),
                                artworkUrl = collection.artworkUrl,
                                trackCount = tracks.size
                            )
                        ) else emptyList(),
                        searchArtists = emptyList(),
                        isSearching = false,
                        statusMessage = "${collection.platform} ${collection.type}: ${collection.title} · ${tracks.size} tracks"
                    )
                }
                return@launch
            }

            val metadata = withContext(Dispatchers.IO) { resolvePlatformLink(url) }
            val artist = metadata?.artist
            if (metadata == null || artist.isNullOrBlank()) {
                _uiState.update {
                    it.copy(
                        isSearching = false,
                        statusMessage = if (isApplePlaylistUrl(url)) {
                            "Apple playlist access requires a valid Apple Music developer token in Settings."
                        } else "This link could not be resolved. Check that the shared item is public."
                    )
                }
                return@launch
            }

            val exactQuery = "${metadata.title} $artist"
            val searchResult = try {
                withContext(Dispatchers.IO) {
                    kotlinx.coroutines.withTimeout(15_000L) {
                        container.searchRepository.search(SearchRequest(queryText = exactQuery))
                    }
                }
            } catch (_: kotlinx.coroutines.TimeoutCancellationException) {
                null
            }
            val exactSources = searchResult?.sourceResults.orEmpty()
                .filter { sourceCandidateMatches(metadata, it.title, it.artist, it.isrc) }
            val exactLocal = searchResult?.localMatches.orEmpty()
                .filter {
                    sourceCandidateMatches(
                        metadata,
                        it.track.title,
                        it.track.artist,
                        it.track.isrc
                    )
                }
            val displayTrack = metadata.toCanonicalTrack(exactSources.firstOrNull())

            Log.d(
                "VANTA_LINK_RESOLVE",
                "platform=${metadata.platform} externalId=${metadata.externalId} title='${metadata.title}' " +
                    "artist='$artist' exactSources=${exactSources.size} exactLocal=${exactLocal.size}"
            )
            _uiState.update {
                it.copy(
                    visibleTracks = exactLocal,
                    sourceResults = listOf(displayTrack),
                    searchTopResult = displayTrack,
                    searchSongs = listOf(displayTrack),
                    searchAlbums = emptyList(),
                    searchArtists = emptyList(),
                    isSearching = false,
                    statusMessage = when {
                        exactSources.isNotEmpty() || exactLocal.isNotEmpty() ->
                            "Exact ${metadata.platform ?: "music"} match"
                        else -> "Link resolved exactly; no playable source is available yet."
                    }
                )
            }
        }
    }

    private suspend fun resolvePlatformLink(url: String): PlatformLinkMetadata? {
        val parsed = SoundiizTextParser.parseLine(url)
        return runCatching {
            kotlinx.coroutines.withTimeout(18_000L) {
                container.platformLinkResolver.resolve(parsed)
            }
        }.onFailure {
            Log.w("VANTA_LINK_RESOLVE", "url='${url.take(100)}' failed='${it.message}'")
        }.getOrNull()
    }

    private suspend fun resolvePlatformCollection(url: String): com.audiophile.musicplayer.data.importer.PlatformLinkCollection? =
        runCatching {
            kotlinx.coroutines.withTimeout(18_000L) {
                container.platformLinkResolver.resolveCollection(url)
            }
        }.onFailure {
            Log.w("VANTA_COLLECTION_RESOLVE", "url='${url.take(100)}' failed='${it.message}'")
        }.getOrNull()

    private fun isApplePlaylistUrl(url: String): Boolean {
        val lower = url.lowercase()
        return "music.apple.com" in lower && "/playlist/" in lower
    }

    private fun PlatformLinkMetadata.toCanonicalTrack(source: CanonicalTrack? = null): CanonicalTrack =
        CanonicalTrack(
            title = title,
            artist = artist.orEmpty(),
            album = album,
            isrc = isrc ?: source?.isrc,
            durationMs = durationMs ?: source?.durationMs,
            genre = source?.genre,
            releaseYear = source?.releaseYear,
            artworkUrl = artworkUrl ?: source?.artworkUrl,
            explicit = source?.explicit,
            sourcePriority = source?.sourcePriority ?: 100,
            sourceStatus = source?.sourceStatus ?: SearchItemStatus.METADATA_ONLY,
            sourceProviderId = source?.sourceProviderId,
            qualityInfo = source?.qualityInfo
        )

    private fun isSupportedPlatformUrl(value: String): Boolean {
        val lower = value.trim().lowercase()
        if (!lower.startsWith("http://") && !lower.startsWith("https://")) return false
        return listOf(
            // Apple
            "music.apple.com", "itunes.apple.com",
            // Spotify
            "open.spotify.com", "spotify.link",
            // Tidal
            "tidal.com", "listen.tidal.com",
            // Qobuz
            "qobuz.com", "play.qobuz.com",
            // Deezer
            "deezer.com", "deezer.page.link",
            // Amazon Music
            "music.amazon.com", "music.amazon.co", "amazon.com/music",
            // YouTube
            "music.youtube.com", "youtube.com/watch", "youtu.be",
            // SoundCloud
            "soundcloud.com",
            // Pandora
            "pandora.com",
            // Napster / Rhapsody
            "napster.com",
            // Audiomack
            "audiomack.com",
            // Bandcamp
            "bandcamp.com",
            // Songlink / Odesli (universal links)
            "song.link", "album.link", "odesli.co"
        ).any(lower::contains)
    }

    private fun sourceCandidateMatches(
        expected: PlatformLinkMetadata,
        candidateTitle: String,
        candidateArtist: String,
        candidateIsrc: String?
    ): Boolean {
        if (!expected.isrc.isNullOrBlank() && expected.isrc.equals(candidateIsrc, ignoreCase = true)) return true
        val expectedTitle = normalizeLinkMatch(expected.title)
        val actualTitle = normalizeLinkMatch(candidateTitle)
        val expectedArtist = normalizeLinkMatch(expected.artist.orEmpty())
        val actualArtist = normalizeLinkMatch(candidateArtist)
        if (expectedTitle.isBlank() || actualTitle.isBlank() || expectedArtist.isBlank() || actualArtist.isBlank()) return false

        val artistMatches = expectedArtist == actualArtist ||
            ((expectedArtist.contains(actualArtist) || actualArtist.contains(expectedArtist)) &&
            minOf(expectedArtist.length, actualArtist.length).toFloat() /
                maxOf(expectedArtist.length, actualArtist.length) >= 0.72f)
        if (!artistMatches) return false

        val expectedTokens = expectedTitle.split(' ').filter { it.isNotBlank() }.toSet()
        val actualTokens = actualTitle.split(' ').filter { it.isNotBlank() }.toSet()
        val overlap = expectedTokens.intersect(actualTokens).size.toFloat() /
            expectedTokens.union(actualTokens).size.coerceAtLeast(1)
        val versionMarkers = setOf(
            "remix", "acoustic", "live", "instrumental", "sped", "slowed",
            "karaoke", "tribute", "cover", "vocal"
        )
        val requiredVersions = expectedTokens.intersect(versionMarkers)
        val actualVersions = actualTokens.intersect(versionMarkers)
        val versionMatches = requiredVersions == actualVersions
        if (!versionMatches) return false

        val (variantRejected, _) = com.audiophile.musicplayer.data.source.VariantClassifier.isRejectedForStudioIntent(
            title = candidateTitle,
            artist = candidateArtist,
            album = null,
            userQuery = null
        )
        if (variantRejected) return false

        return versionMatches && (
            expectedTitle == actualTitle ||
                (expectedTitle.contains(actualTitle) || actualTitle.contains(expectedTitle)) &&
                minOf(expectedTitle.length, actualTitle.length).toFloat() / maxOf(expectedTitle.length, actualTitle.length) >= 0.72f ||
                overlap >= 0.62f
            )
    }

    private fun sourceCandidateMatches(expected: CanonicalTrack, candidate: SourceSearchResult): Boolean =
        sourceCandidateMatches(
            PlatformLinkMetadata(
                title = expected.title,
                artist = expected.artist,
                album = expected.album,
                isrc = expected.isrc,
                durationMs = expected.durationMs,
                matchReason = "Canonical source validation"
            ),
            candidate.title,
            candidate.artist,
            candidate.isrc
        )

    /** Tap-to-play: resolve the exact catalog row first, re-search only as fallback. */
    private suspend fun resolveCanonicalTrackForPlayback(
        result: CanonicalTrack
    ): Pair<SourceSearchResult, com.audiophile.musicplayer.data.source.ResolvedStream>? {
        val providerId = result.sourceProviderId?.trim().orEmpty()
        val externalId = result.externalTrackId?.trim().orEmpty()
        val isPreviewOnlyRow = result.sourceStatus == SearchItemStatus.PREVIEW ||
            providerId.equals("vanta_preview", ignoreCase = true) ||
            providerId.equals("itunes_preview", ignoreCase = true)

        if (providerId.isNotBlank() && externalId.isNotBlank() && !isPreviewOnlyRow) {
            Log.d("VANTA_PLAY_TRACK_REQUEST", "direct_resolve provider=$providerId id=$externalId title='${result.title}'")
            val directStream = container.sourceRegistry.resolveStream(
                providerId,
                externalId,
                timeoutMs = com.audiophile.musicplayer.data.source.CloudLibraryHelpers.CLOUD_RESOLVE_TIMEOUT_MS
            )
            if (directStream != null && isValidResolvedStream(directStream)) {
                Log.d("VANTA_PLAY_TRACK_REQUEST", "direct_resolve_ok provider=$providerId id=$externalId url=${directStream.streamUrl.take(80)}")
                return toSourceSearchResult(result) to directStream
            }
            Log.w("VANTA_PLAY_CLICK", "Direct resolve failed provider=$providerId id=$externalId — trying search fallback")
        } else if (isPreviewOnlyRow) {
            Log.d("VANTA_PLAY_TRACK_REQUEST", "preview_source_skipped title='${result.title}' provider=$providerId")
        }

        val simplifiedTitle = DisplayMetadataCleaner.cleanTitle(result.title)
        val queries = buildList {
            add("${result.title} ${result.artist}".trim())
            if (simplifiedTitle != result.title) add("$simplifiedTitle ${result.artist}".trim())
            add(result.title.trim())
            if (!result.isrc.isNullOrBlank()) add(result.isrc)
        }.distinct()

        for (query in queries) {
            val candidates = container.sourceRegistry.searchAll(
                query,
                timeoutMs = com.audiophile.musicplayer.data.source.CloudLibraryHelpers.CLOUD_SEARCH_TIMEOUT_MS
            )
            val identity = com.audiophile.musicplayer.data.source.SelectedRecordingIdentity(
                title = result.title,
                artist = result.artist,
                durationMs = result.durationMs,
                isrc = result.isrc,
                preferredProviderId = providerId.takeIf { it.isNotBlank() },
                preferredExternalTrackId = externalId.takeIf { it.isNotBlank() },
                userQuery = _uiState.value.query.takeIf { it.isNotBlank() }
            )
            val filtered = com.audiophile.musicplayer.data.source.SourceCandidateRanker.rankSearchResults(
                identity,
                candidates.filter { sourceCandidateMatches(result, it) }
                    .filterNot { it.status == SearchItemStatus.PREVIEW }
            )
            Log.d("VANTA_PLAY_CLICK", "fallback query='${query.take(60)}' raw=${candidates.size} filtered=${filtered.size}")
            val triedPerProvider = mutableMapOf<String, Int>()
            val maxPerProvider = 3
            for (candidate in filtered) {
                if (!candidate.status.canResolveStream()) continue
                val tried = triedPerProvider.getOrDefault(candidate.providerId, 0)
                if (tried >= maxPerProvider) continue
                triedPerProvider[candidate.providerId] = tried + 1
                val stream = container.sourceRegistry.resolveStream(
                    candidate.providerId,
                    candidate.id,
                    timeoutMs = com.audiophile.musicplayer.data.source.CloudLibraryHelpers.CLOUD_RESOLVE_TIMEOUT_MS
                )
                if (stream != null && isValidResolvedStream(stream)) {
                    com.audiophile.musicplayer.data.source.SourceIdentityGate.logSelected(
                        title = candidate.title,
                        artist = candidate.artist,
                        provider = candidate.providerId,
                        variantType = com.audiophile.musicplayer.data.source.VariantClassifier.classify(
                            candidate.title, candidate.artist, candidate.album, identity.userQuery
                        ).variantType,
                        score = 0,
                        identityConfidence = 0.9f
                    )
                    return candidate to stream
                }
            }
        }
        return null
    }

    private fun isValidResolvedStream(stream: com.audiophile.musicplayer.data.source.ResolvedStream?): Boolean =
        stream != null &&
            stream.streamUrl.isNotBlank() &&
            !stream.streamUrl.contains("soundhelix", ignoreCase = true)

    private fun playbackFallbackProviderIds(primaryProviderId: String): List<String> =
        listOf("qobuz_tidal", "tidal_gateway", "qobuz_gateway", "deezer_gateway", "amazon_gateway", "pandora_gateway")
            .filter { it != primaryProviderId }

    private fun zeroConfigResolveProviderIds(): List<String> =
        listOf("qobuz_tidal", "tidal_gateway", "qobuz_gateway", "deezer_gateway")

    private fun toSourceSearchResult(result: CanonicalTrack): SourceSearchResult =
        SourceSearchResult(
            id = result.externalTrackId ?: throw IllegalStateException("Track missing externalTrackId: ${result.title}"),
            providerId = result.sourceProviderId ?: throw IllegalStateException("Track missing sourceProviderId: ${result.title}"),
            title = result.title,
            artist = result.artist,
            album = result.album,
            coverSeed = result.artworkUrl ?: result.title,
            durationMs = result.durationMs,
            isrc = result.isrc,
            status = result.sourceStatus ?: SearchItemStatus.SOURCE_FOUND,
            qualityLabel = result.qualityInfo?.bestQualityLabel()
        )

    private fun normalizeLinkMatch(value: String): String =
        value.lowercase()
            .replace(Regex("""\b(official|audio|video|visualizer|lyrics?)\b"""), " ")
            .replace(Regex("""[^\p{L}\p{N}\s]+"""), " ")
            .replace(Regex("""\s+"""), " ")
            .trim()

    private fun browseCategorySeeds(category: String): List<String> {
        return when (category.trim().lowercase()) {
            "pop" -> listOf("Taylor Swift", "Dua Lipa", "Ariana Grande", "Billie Eilish", "The Weeknd", "Olivia Rodrigo")
            "alternative" -> listOf("Tame Impala", "Arctic Monkeys", "The Strokes", "Radiohead", "Cage The Elephant")
            "country" -> listOf("Morgan Wallen", "Zach Bryan", "Luke Combs", "Kacey Musgraves", "Chris Stapleton")
            "hits" -> listOf("top hits", "Billboard Hot 100", "global hits", "viral hits")
            "hip-hop", "hip hop" -> listOf("Kendrick Lamar", "Drake", "J Cole", "Travis Scott", "21 Savage")
            "dance" -> listOf("Calvin Harris", "Disclosure", "Fred again", "David Guetta", "Swedish House Mafia")
            "rock" -> listOf("Foo Fighters", "Nirvana", "The Killers", "Queen", "Red Hot Chili Peppers")
            "chill" -> listOf("Frank Ocean", "SZA", "Mac Miller", "Bon Iver", "chill indie")
            "sleep" -> listOf("ambient sleep", "sleep music", "Brian Eno", "Max Richter")
            "focus" -> listOf("lofi beats", "focus music", "instrumental study", "Tycho")
            "feel good" -> listOf("feel good songs", "Pharrell Williams", "Bruno Mars", "Daft Punk")
            "party" -> listOf("party hits", "Pitbull", "Rihanna", "LMFAO", "Black Eyed Peas")
            else -> listOf(category)
        }
    }

    private fun categoryResultAllowed(category: String, track: CanonicalTrack): Boolean {
        val title = track.title.lowercase()
        val artist = track.artist.lowercase()
        val album = track.album.orEmpty().lowercase()
        val haystack = "$title $artist $album"
        if ("karaoke" in haystack || "reaction" in haystack || "tutorial" in haystack) return false
        if (category.equals("pop", ignoreCase = true)) {
            if (title.startsWith("pop ") || title.contains(" pop dat ") || title.contains("pop dat")) return false
            val popArtists = listOf("taylor swift", "dua lipa", "ariana grande", "billie eilish", "the weeknd", "olivia rodrigo")
            return popArtists.any { it in artist } || "pop" in album || "pop" in track.genre.orEmpty().lowercase()
        }
        return true
    }

    suspend fun previewRadioStation(stationId: String): List<UnifiedTrackWithSources> {
        return container.radioStationPreviewLoader.previewTracks(stationId)
    }

    fun startJukeboxStation(stationId: String, shuffle: Boolean = false) {
        viewModelScope.launch {
            _uiState.update { it.copy(streamingStationLoading = true, statusMessage = "Curating station...") }

            try {
                val station = JukeboxCatalog.resolveStation(stationId, container.customStationStore)
                if (station == null) {
                    _uiState.update { it.copy(streamingStationLoading = false, statusMessage = "Station not found") }
                    return@launch
                }

                val seedParams = station.toStreamingSeed()

                // Reset endless loop state for the new station
                activeStationSeed = seedParams
                playedStationTrackIds.clear()
                stopStationMonitor()

                val stationSeed = seedParams.toStationSeed()
                val initialRequest = com.audiophile.musicplayer.radio.StreamingStationRequest(
                    seed = stationSeed,
                    targetCount = 30,
                    minPlayableToStart = 3
                )

                val result = withContext(Dispatchers.IO) {
                    container.radioQueueEngine.generateStreamingStation(initialRequest)
                }

                if (!result.canStartPlayback) {
                    _uiState.update {
                        it.copy(
                            streamingStationLoading = false,
                            statusMessage = "No tracks found for ${station.name}"
                        )
                    }
                    return@launch
                }

                val finalTracks = if (shuffle) result.candidates.shuffled() else result.candidates

                // Track these IDs so the refill doesn't duplicate them
                finalTracks.map { it.track.trackId.toString() }.forEach { id ->
                    if (id !in playedStationTrackIds) {
                        playedStationTrackIds.addLast(id)
                        if (playedStationTrackIds.size > 500) {
                            playedStationTrackIds.removeFirst()
                        }
                    }
                }

                withContext(Dispatchers.Main) {
                    try {
                        container.playerController.playQueue(
                            finalTracks,
                            0,
                            com.audiophile.musicplayer.playback.QueueMode.STREAMING_STATION
                        )
                        _uiState.update {
                            it.copy(
                                streamingStationLoading = false,
                                statusMessage = "Now playing: ${station.name}"
                            )
                        }
                    } catch (e: Exception) {
                        Log.e("VANTA_STATION", "playQueue failed", e)
                        VantaDiagnosticLog.error("Station", "playQueue_failed stationId=$stationId", e)
                        _uiState.update {
                            it.copy(streamingStationLoading = false, statusMessage = "Failed to start playback")
                        }
                    }
                }

                // Start the background refill monitor
                startStationMonitor()

            } catch (e: Exception) {
                Log.e("VANTA_STATION", "Failed to start station $stationId", e)
                VantaDiagnosticLog.error("Station", "start_failed stationId=$stationId", e)
                _uiState.update {
                    it.copy(streamingStationLoading = false, statusMessage = "Failed to start station: ${e.message}")
                }
            }
        }
    }

    // ── Endless Station Refill Monitor ──

    private fun startStationMonitor() {
        stationMonitorJob = viewModelScope.launch {
            while (isActive && activeStationSeed != null) {
                val upcomingSize = container.queueManager.upcomingOriginalQueue().size

                if (upcomingSize < REFILL_THRESHOLD && !isRefillingStation) {
                    isRefillingStation = true

                    Log.d("VANTA_STATION_REFILL", "Queue low ($upcomingSize tracks left). Fetching more...")

                    try {
                        val seedParams = activeStationSeed ?: break
                        val stationSeed = seedParams.toStationSeed()
                        val excludeIds = playedStationTrackIds.mapNotNull { it.toLongOrNull() }.toSet()

                        val newTracks = withContext(Dispatchers.IO) {
                            container.radioQueueEngine.refillStreamingStation(
                                request = com.audiophile.musicplayer.radio.StreamingStationRequest(
                                    seed = stationSeed,
                                    targetCount = 20,
                                    minPlayableToStart = 3
                                ),
                                existingTrackIds = excludeIds
                            )
                        }

                        if (newTracks.isNotEmpty()) {
                            val added = container.queueManager.appendToOriginalQueueIfAbsent(newTracks)
                            newTracks.map { it.track.trackId.toString() }.forEach { id ->
                                if (id !in playedStationTrackIds) {
                                    playedStationTrackIds.addLast(id)
                                    if (playedStationTrackIds.size > 500) {
                                        playedStationTrackIds.removeFirst()
                                    }
                                }
                            }
                            container.playerController.refreshQueueTimeline()
                            Log.d("VANTA_STATION_REFILL", "Added $added tracks. Queue is now endless.")
                        }
                    } catch (e: Exception) {
                        Log.w("VANTA_STATION_REFILL", "Refill failed, will retry later: ${e.message}")
                    } finally {
                        isRefillingStation = false
                    }
                }

                delay(3000)
            }
        }
    }

    private fun stopStationMonitor() {
        stationMonitorJob?.cancel()
        stationMonitorJob = null
        isRefillingStation = false
    }

    fun startStreamingStation(userInput: String, shuffle: Boolean = false) {
        val trimmed = userInput.trim()
        if (trimmed.isBlank()) return

        viewModelScope.launch {
            // Stop any previous jukebox station monitor
            stopStationMonitor()

            _uiState.update {
                it.copy(
                    streamingStationLoading = true,
                    statusMessage = "Building station…"
                )
            }

            val seed = withContext(Dispatchers.IO) { com.audiophile.musicplayer.radio.StreamingStationSeedResolver.fromUserInput(trimmed) }
            val taste = withContext(Dispatchers.IO) { container.aiDjRecommendationEngine.streamingTasteSignals() }
            val playedIds = withContext(Dispatchers.IO) { container.queueManager.playedHistory.toSet() }
            val currentQueueIds = withContext(Dispatchers.IO) { container.queueManager.originalQueue.map { it.track.trackId }.toSet() }
            val localBonus = withContext(Dispatchers.IO) { optionalLocalEnrichment(seed, _uiState.value.library) }

            Log.d("VANTA_STREAMING_STATION", "ui_start seed='${seed.displayName}' kind=${seed.kind}")

            val bootstrap = withContext(Dispatchers.IO) {
                container.radioQueueEngine.generateStreamingStation(
                    com.audiophile.musicplayer.radio.StreamingStationRequest(
                        seed = seed,
                        excludeTrackIds = currentQueueIds,
                        playedTrackIds = playedIds,
                        taste = taste,
                        localEnrichment = localBonus,
                        targetCount = 20,
                        minPlayableToStart = 4
                    )
                )
            }

            if (!bootstrap.canStartPlayback) {
                Log.w(
                    "VANTA_STREAMING_STATION",
                    "failed seed='${seed.displayName}' reason='${bootstrap.failureReason}' playable=${bootstrap.candidates.size}"
                )
                _uiState.update {
                    it.copy(
                        streamingStationLoading = false,
                        statusMessage = "Couldn't find playable tracks for ${seed.displayName}. Check your music sources in Settings."
                    )
                }
                return@launch
            }

            withContext(Dispatchers.IO) {
                container.queueManager.setStreamingStationSeed(seed)
            }
            val stationCandidates = bootstrap.candidates
                .filterNot { JukeboxTrackEligibility.shouldExcludeFromRadioQueue(it) }
                .let { clean ->
                    if (seed.kind == com.audiophile.musicplayer.radio.StreamingStationKind.ARTIST) {
                        val artistKey = seed.seedArtist?.trim().orEmpty().ifBlank {
                            seed.displayName.removeSuffix(" Radio").trim()
                        }
                        clean.sortedByDescending { track ->
                            if (track.track.artist.equals(artistKey, ignoreCase = true)) 1 else 0
                        }
                    } else {
                        clean
                    }
                }
            val playQueue = if (shuffle) {
                interleaveStationQueue(stationCandidates.shuffled())
            } else {
                interleaveStationQueue(stationCandidates)
            }
            if (playQueue.isEmpty()) {
                Log.w(
                    "VANTA_STREAMING_STATION",
                    "failed seed='${seed.displayName}' reason='filtered_empty' bootstrap=${bootstrap.candidates.size}"
                )
                withContext(Dispatchers.IO) {
                    container.queueManager.setStreamingStationSeed(null)
                }
                _uiState.update {
                    it.copy(
                        streamingStationLoading = false,
                        statusMessage = "Couldn't find playable tracks for ${seed.displayName}. Check your music sources in Settings."
                    )
                }
                return@launch
            }
            container.playerController.playQueue(
                playQueue,
                0,
                com.audiophile.musicplayer.playback.QueueMode.STREAMING_STATION
            )
            withContext(Dispatchers.IO) {
                val first = playQueue.first()
                container.nowPlayingStateStore.save(
                    NowPlayingState(
                        trackId = first.track.trackId.toString(),
                        title = first.track.title,
                        artist = first.track.artist,
                        album = first.track.albumName,
                        artworkUrl = first.track.coverArtUrl,
                        durationMs = first.track.durationMs ?: 0L,
                        isPlaying = true
                    )
                )
            }
            _uiState.update {
                it.copy(
                    queueSnapshot = container.queueManager.snapshot(),
                    streamingStationLoading = false,
                    statusMessage = "Playing ${seed.displayName}"
                )
            }

            launch(Dispatchers.IO) {
                val expanded = container.radioQueueEngine.generateStreamingStation(
                    com.audiophile.musicplayer.radio.StreamingStationRequest(
                        seed = seed,
                        excludeTrackIds = container.queueManager.originalQueue.map { it.track.trackId }.toSet(),
                        playedTrackIds = container.queueManager.playedHistory.toSet(),
                        taste = container.aiDjRecommendationEngine.streamingTasteSignals(),
                        localEnrichment = localBonus,
                        targetCount = 45,
                        minPlayableToStart = 8
                    )
                )
                if (expanded.candidates.isNotEmpty()) {
                    val added = container.queueManager.appendToOriginalQueueIfAbsent(expanded.candidates)
                    Log.d("VANTA_STREAMING_STATION", "background_expand added=$added total=${expanded.candidates.size}")
                    _uiState.update { state ->
                        state.copy(queueSnapshot = container.queueManager.snapshot())
                    }
                }
            }
        }
    }

    private fun interleaveStationQueue(tracks: List<UnifiedTrackWithSources>): List<UnifiedTrackWithSources> {
        if (tracks.size <= 2) return tracks
        val buckets = tracks
            .groupBy { it.track.artist?.trim()?.lowercase().orEmpty().ifBlank { "unknown" } }
            .mapValues { (_, items) -> items.toMutableList() }
            .toMutableMap()
        val order = buckets.keys.toList()
        val result = mutableListOf<UnifiedTrackWithSources>()
        while (buckets.values.any { it.isNotEmpty() }) {
            for (artist in order) {
                val bucket = buckets[artist] ?: continue
                if (bucket.isEmpty()) continue
                result.add(bucket.removeAt(0))
            }
        }
        return result
    }

    private fun optionalLocalEnrichment(
        seed: com.audiophile.musicplayer.radio.StreamingStationSeed,
        library: List<UnifiedTrackWithSources>
    ): List<UnifiedTrackWithSources> {
        if (library.isEmpty()) return emptyList()
        val words = (seed.queryPhrases + seed.hintKeywords + listOf(seed.displayName))
            .flatMap { it.lowercase().split(Regex("""\s+""")) }
            .filter { it.length >= 3 }
            .distinct()
        if (words.isEmpty()) return emptyList()
        return library.filter { track ->
            if (!track.sources.any { it.streamUrl.isNotBlank() }) return@filter false
            val haystack = "${track.track.title} ${track.track.artist} ${track.track.genre}".lowercase()
            words.any { haystack.contains(it) }
        }.take(5)
    }

    fun playTrack(track: UnifiedTrackWithSources) {
        Log.d("VANTA_PLAYBACK_TRACE", "step='ui_play_requested' trackId=${track.track.trackId} title='${track.track.title}' sources=${track.sources.size} hasStreamUrl=${track.sources.any { it.streamUrl.isNotBlank() }}")
        _uiState.update { it.copy(activeTrackEnhancedMetadata = null, activeTrackId = track.track.trackId.toString()) }
        container.nowPlayingStateStore.save(
            NowPlayingState(
                trackId = track.track.trackId.toString(),
                title = track.track.title,
                artist = track.track.artist,
                album = track.track.albumName,
                artworkUrl = track.track.coverArtUrl,
                durationMs = track.track.durationMs ?: 0L,
                isPlaying = false
            )
        )

        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                val localSong = track.track.localLibraryId?.let { container.localLibraryRepository.songById(it) }
                val metadata = container.metadataResolver.resolveRawMetadata(
                    title = localSong?.title ?: track.track.title,
                    artist = localSong?.artist ?: track.track.artist,
                    album = localSong?.album ?: track.track.albumName,
                    isrc = localSong?.isrc
                )
                val playableTrack = ensurePlayableSource(
                    track = track,
                    metadata = metadata,
                    statusPrefix = "Searching sources"
                ) ?: return@withContext null
                val tracks = playbackQueueFor(playableTrack)
                    .map { if (it.track.trackId == playableTrack.track.trackId) playableTrack else it }
                    .ifEmpty { listOf(playableTrack) }
                val startIndex = tracks.indexOfFirst { it.track.trackId == playableTrack.track.trackId }.coerceAtLeast(0)
                val artworkUrl = metadata?.artworkUrl ?: localSong?.artworkUrl ?: track.track.coverArtUrl

                container.playerController.playQueue(tracks, startIndex)
                container.nowPlayingStateStore.save(
                    NowPlayingState(
                        trackId = playableTrack.track.trackId.toString(),
                        title = metadata?.title ?: playableTrack.track.title,
                        artist = metadata?.artist ?: playableTrack.track.artist,
                        album = metadata?.album ?: playableTrack.track.albumName,
                        artworkUrl = artworkUrl,
                        durationMs = metadata?.durationMs ?: playableTrack.track.durationMs ?: 0L,
                        queuePosition = startIndex,
                        queueSize = tracks.size
                    )
                )
                Triple(playableTrack, metadata, startIndex)
            } ?: return@launch

            val (playableTrack, metadata, startIndex) = result
            val stillSameTrack = _uiState.value.activeTrackId == playableTrack.track.trackId.toString()
            if (!stillSameTrack) {
                Log.d("VANTA_METADATA", "Discarded stale metadata for trackId=${playableTrack.track.trackId} (active=${_uiState.value.activeTrackId})")
            }
            _uiState.update {
                it.copy(
                    queueSnapshot = container.queueManager.snapshot(),
                    activeTrackEnhancedMetadata = if (stillSameTrack) metadata else null,
                    statusMessage = "Playing ${DisplayMetadataCleaner.cleanTitle(playableTrack.track.title)}"
                )
            }
        }
    }

    fun playNext(track: UnifiedTrackWithSources) {
        viewModelScope.launch {
            val playableTrack = withContext(Dispatchers.IO) {
                ensureQueuePlayable(track, "Finding source")
            } ?: return@launch
            container.queueManager.playNext(playableTrack)
            _uiState.update {
                it.copy(
                    queueSnapshot = container.queueManager.snapshot(),
                    statusMessage = "\"${DisplayMetadataCleaner.cleanTitle(playableTrack.track.title)}\" will play next"
                )
            }
        }
    }

    private suspend fun ensurePlayableSource(
        track: UnifiedTrackWithSources,
        metadata: EnhancedMetadata?,
        statusPrefix: String
    ): UnifiedTrackWithSources? {
        val identitySource = track.sources.firstOrNull { source ->
            !source.externalProviderId.isNullOrBlank() && !source.externalTrackId.isNullOrBlank()
        }
        val hasUsableStream = track.sources.any(::isSourceUrlUsable)
        if (identitySource != null && !hasUsableStream) {
            val title = metadata?.title?.takeIf { it.isNotBlank() } ?: track.track.title
            val artist = metadata?.artist?.takeIf { it.isNotBlank() } ?: track.track.artist
            val cleanTitle = DisplayMetadataCleaner.cleanTitle(title)
            val providerId = identitySource.externalProviderId ?: return null
            val externalTrackId = identitySource.externalTrackId ?: return null
            _uiState.update { it.copy(statusMessage = "Resolving stream for $cleanTitle...") }
            val resolved = container.sourceRegistry.resolveStream(
                providerId,
                externalTrackId,
                timeoutMs = com.audiophile.musicplayer.data.source.CloudLibraryHelpers.CLOUD_RESOLVE_TIMEOUT_MS
            ) ?: container.sourceRegistry.resolveStreamParallelBest(
                externalTrackId,
                zeroConfigResolveProviderIds().filter { it != identitySource.externalProviderId },
                timeoutMs = com.audiophile.musicplayer.data.source.CloudLibraryHelpers.CLOUD_RESOLVE_TIMEOUT_MS
            )?.second
            if (resolved != null &&
                resolved.streamUrl.isNotBlank() &&
                !resolved.streamUrl.contains("soundhelix", ignoreCase = true)
            ) {
                val trackId = container.trackRepository.addTrackSource(
                    title = track.track.title,
                    artist = track.track.artist,
                    album = metadata?.album ?: track.track.albumName,
                    coverArtUrl = metadata?.artworkUrl ?: track.track.coverArtUrl,
                    sourceType = com.audiophile.musicplayer.data.source.CloudLibraryHelpers.sourceTypeForProvider(
                        providerId
                    ),
                    streamUrl = resolved.streamUrl,
                    bitrate = resolved.bitrateKbps,
                    genre = metadata?.genres?.firstOrNull() ?: track.track.genre,
                    isrc = metadata?.isrc ?: track.track.isrc,
                    durationMs = metadata?.durationMs ?: track.track.durationMs,
                    externalProviderId = identitySource.externalProviderId,
                    externalTrackId = identitySource.externalTrackId,
                    expiresAtMs = resolved.expiresAt
                )
                container.trackRepository.getTrackWithSources(trackId)?.let { updated ->
                    Log.d(
                        "VANTA_SOURCE_RESOLVE",
                        "direct_identity_resolve_ok trackId=$trackId provider=${identitySource.externalProviderId} externalId=${identitySource.externalTrackId}"
                    )
                    return updated
                }
            }
        }

        if (track.sources.any { it.streamUrl.isNotBlank() }) return track

        val title = metadata?.title?.takeIf { it.isNotBlank() } ?: track.track.title
        val artist = metadata?.artist?.takeIf { it.isNotBlank() } ?: track.track.artist
        val cleanTitle = DisplayMetadataCleaner.cleanTitle(title)
        _uiState.update { it.copy(statusMessage = "$statusPrefix for $cleanTitle...") }

        val queries = buildList {
            add(listOf(title, artist).joinToString(" ").trim())
            if (!metadata?.album.isNullOrBlank()) add(listOf(title, artist, metadata?.album).joinToString(" ").trim())
            if (!metadata?.isrc.isNullOrBlank()) add(metadata?.isrc ?: "")
            add(listOf(track.track.title, track.track.artist).joinToString(" ").trim())
        }.filter { it.isNotBlank() }.distinct()

        val candidates = mutableListOf<SourceSearchResult>()
        for (query in queries) {
            val results = container.sourceRegistry.searchAll(query, timeoutMs = 12_000L)
                .filter { it.status.canResolveStream() }
                .filter { it.isLikelyMusicTrack() }
                .filter {
                    sourceCandidateMatches(
                        expected = PlatformLinkMetadata(
                            title = title,
                            artist = artist,
                            album = metadata?.album ?: track.track.albumName,
                            isrc = metadata?.isrc ?: track.track.isrc,
                            durationMs = metadata?.durationMs ?: track.track.durationMs,
                            matchReason = "Playback source validation"
                        ),
                        candidateTitle = it.title,
                        candidateArtist = it.artist,
                        candidateIsrc = it.isrc
                    )
                }
            candidates += results
            if (candidates.isNotEmpty()) break
        }

        val ranked = com.audiophile.musicplayer.data.source.SourceCandidateRanker.rankSearchResults(
            selected = com.audiophile.musicplayer.data.source.SelectedRecordingIdentity(
                title = title,
                artist = artist,
                durationMs = metadata?.durationMs ?: track.track.durationMs,
                isrc = metadata?.isrc ?: track.track.isrc,
                userQuery = _uiState.value.query.takeIf { it.isNotBlank() }
            ),
            candidates = candidates.distinctBy { "${it.providerId}:${it.id}" }
        ).take(10)

        for (candidate in ranked) {
            val resolved = container.sourceRegistry.resolveStream(candidate.providerId, candidate.id, timeoutMs = 12_000L)
                ?: continue
            if (resolved.streamUrl.isBlank()) continue
            if (resolved.streamUrl.contains("soundhelix", ignoreCase = true)) continue

            val trackId = container.trackRepository.addTrackSource(
                title = track.track.title,
                artist = track.track.artist,
                album = metadata?.album ?: candidate.album ?: track.track.albumName,
                coverArtUrl = metadata?.artworkUrl ?: candidate.artworkUrl ?: track.track.coverArtUrl,
                sourceType = SourceType.ADDON,
                streamUrl = resolved.streamUrl,
                bitrate = resolved.bitrateKbps,
                genre = metadata?.genres?.firstOrNull() ?: track.track.genre,
                isrc = metadata?.isrc ?: candidate.isrc ?: track.track.isrc,
                durationMs = metadata?.durationMs ?: candidate.durationMs ?: track.track.durationMs,
                externalProviderId = candidate.providerId,
                externalTrackId = candidate.id,
                expiresAtMs = resolved.expiresAt
            )
            val updated = container.trackRepository.getTrackWithSources(trackId) ?: continue
            val currentLibrary = _uiState.value.library
            val nextLibrary = if (currentLibrary.any { it.track.trackId == updated.track.trackId }) {
                currentLibrary.map { if (it.track.trackId == updated.track.trackId) updated else it }
            } else {
                currentLibrary + updated
            }
            _uiState.update { current ->
                current.copy(
                    library = nextLibrary,
                    visibleTracks = if (current.query.isBlank()) {
                        nextLibrary
                    } else {
                        current.visibleTracks.map { if (it.track.trackId == updated.track.trackId) updated else it }
                    },
                    statusMessage = "Source found for ${DisplayMetadataCleaner.cleanTitle(updated.track.title)}"
                )
            }
            return updated
        }

        Log.w("VANTA_SOURCE_RESOLVE", "No playable source found for metadata-only track title='$title' artist='$artist'")
        _uiState.update { it.copy(statusMessage = "No playable source found for $cleanTitle") }
        return null
    }

    private fun isSourceUrlUsable(source: com.audiophile.musicplayer.data.local.entities.TrackSource): Boolean {
        if (source.streamUrl.isBlank()) return false
        val expiresAt = source.expiresAtMs ?: return true
        return expiresAt > System.currentTimeMillis() + 30_000L
    }

    fun addToQueue(track: UnifiedTrackWithSources) {
        viewModelScope.launch {
            val playableTrack = withContext(Dispatchers.IO) {
                ensureQueuePlayable(track, "Finding source")
            } ?: return@launch
            container.queueManager.addToQueue(playableTrack)
            _uiState.update {
                it.copy(
                    queueSnapshot = container.queueManager.snapshot(),
                    statusMessage = "\"${DisplayMetadataCleaner.cleanTitle(playableTrack.track.title)}\" added to queue"
                )
            }
        }
    }

    private suspend fun ensureQueuePlayable(
        track: UnifiedTrackWithSources,
        statusPrefix: String
    ): UnifiedTrackWithSources? {
        if (track.sourceValidityStatus().canEnterPlaybackFlow()) return track
        val metadata = container.metadataResolver.resolveRawMetadata(
            title = track.track.title,
            artist = track.track.artist,
            album = track.track.albumName,
            isrc = track.track.isrc
        )
        return ensurePlayableSource(track, metadata, statusPrefix)
    }

    fun removeUpNext(index: Int) {
        viewModelScope.launch {
            container.queueManager.removeUpNextItem(index)
            _uiState.update {
                it.copy(
                    queueSnapshot = container.queueManager.snapshot(),
                    statusMessage = "Removed item from queue"
                )
            }
        }
    }

    fun moveUpNext(fromIndex: Int, toIndex: Int) {
        viewModelScope.launch {
            container.queueManager.moveUpNextItem(fromIndex, toIndex)
            _uiState.update {
                it.copy(queueSnapshot = container.queueManager.snapshot())
            }
        }
    }

    fun playNextFromQueue() {
        viewModelScope.launch {
            val nowPlaying = container.nowPlayingStateStore.load()
            if (nowPlaying != null && nowPlaying.artist != null) {
                container.aiDjRecommendationEngine.recordSkip(
                    trackId = nowPlaying.trackId?.toLongOrNull() ?: 0L,
                    artist = nowPlaying.artist ?: ""
                )
            }
            container.playerController.next()
            // Queue snapshot syncs reactively via playbackState observer
        }
    }

    fun playFirstPlayableTrack() {
        viewModelScope.launch {
            val firstPlayable = _uiState.value.library
                .firstOrNull { track -> track.sources.any { it.streamUrl.isNotBlank() } }
                ?: withContext(Dispatchers.IO) {
                    container.trackRepository.getAllTracks()
                        .firstOrNull { track -> track.sources.any { it.streamUrl.isNotBlank() } }
                }

            if (firstPlayable == null) {
                setStatusMessage("No playable tracks found. Add music sources or search for songs.")
                return@launch
            }

            playTrack(firstPlayable)
        }
    }

    fun playPreviousFromQueue() {
        container.playerController.previous()
    }

    fun togglePlayPause() {
        container.playerController.togglePlayPause()
    }

    fun seekTo(positionMs: Long) {
        container.playerController.seekTo(positionMs)
    }

    fun stopPlayback() {
        container.playerController.stop()
    }

    fun downloadTrack(track: UnifiedTrackWithSources) {
        viewModelScope.launch {
            val bestSource = withContext(Dispatchers.IO) {
                container.trackRepository
                    .getBestQualitySourcesForTrack(track.track.trackId)
                    .orEmpty()
                    .firstOrNull()
            }

            if (bestSource == null) {
                _uiState.update { it.copy(statusMessage = "No downloadable source found for ${DisplayMetadataCleaner.cleanTitle(track.track.title)}") }
                return@launch
            }

            val result = withContext(Dispatchers.IO) { container.downloadManager.enqueueTrackDownload(track.track, bestSource) }
            _uiState.update {
                it.copy(statusMessage = "Downloading to VANTA Library: ${result.fileName}")
            }
            monitorManagedDownload(result.downloadId, result.fileName)
        }
    }

    private fun resumeManagedDownloadImports() {
        container.downloadManager.pendingDownloadIds().forEach { downloadId ->
            monitorManagedDownload(downloadId, "downloaded track")
        }
    }

    private fun monitorManagedDownload(downloadId: Long, displayName: String) {
        viewModelScope.launch(Dispatchers.IO) {
            while (true) {
                val snapshot = container.downloadManager.getDownloadSnapshot(downloadId)
                when (snapshot?.status) {
                    DownloadManager.STATUS_SUCCESSFUL -> {
                        val uri = container.downloadManager.getDownloadedFileUri(downloadId)
                        val imported = uri != null && container.localMediaImporter.importSingleUri(uri)
                        container.downloadManager.markDownloadHandled(downloadId)
                        if (imported) {
                            refreshAll()
                            _uiState.update { it.copy(statusMessage = "$displayName saved to VANTA Library") }
                        } else {
                            _uiState.update { it.copy(statusMessage = "$displayName downloaded, but library import failed") }
                        }
                        return@launch
                    }
                    DownloadManager.STATUS_FAILED -> {
                        container.downloadManager.markDownloadHandled(downloadId)
                        _uiState.update { it.copy(statusMessage = "Download failed for $displayName") }
                        return@launch
                    }
                    null -> {
                        // Android may need a moment to expose a newly queued request.
                    }
                }
                delay(1_500L)
            }
        }
    }

    fun importLocalMedia() {
        if (!com.audiophile.musicplayer.permissions.MediaPermissions.hasAudioPermission(appContext)) {
            _uiState.update {
                it.copy(statusMessage = "Storage permission required to scan device music.")
            }
            return
        }
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isScanningDeviceLibrary = true,
                    deviceScanProgress = "Scanning device library...",
                    statusMessage = null
                )
            }
            // #region agent log
            com.audiophile.musicplayer.debug.DebugSessionLogger.log(
                hypothesisId = "H13",
                location = "MainViewModel.importLocalMedia",
                message = "scan_started",
                data = mapOf(
                    "onMainThread" to (android.os.Looper.myLooper() == android.os.Looper.getMainLooper())
                ),
                runId = "post-fix-v9"
            )
            // #endregion
            try {
                val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())
                val result = withContext(Dispatchers.IO) {
                    container.localMediaImporter.importDeviceLibrary(
                        onProgress = { scanned, imported ->
                            // #region agent log
                            com.audiophile.musicplayer.debug.DebugSessionLogger.log(
                                hypothesisId = "H3",
                                location = "MainViewModel.importLocalMedia.onProgress",
                                message = "scan_progress",
                                data = mapOf(
                                    "scanned" to scanned,
                                    "imported" to imported,
                                    "onMainThread" to (android.os.Looper.myLooper() == android.os.Looper.getMainLooper())
                                ),
                                runId = "post-fix"
                            )
                            // #endregion
                            mainHandler.post {
                                _uiState.update {
                                    it.copy(deviceScanProgress = "Scanned $scanned songs, imported $imported...")
                                }
                            }
                        }
                    )
                }
                // #region agent log
                com.audiophile.musicplayer.debug.DebugSessionLogger.log(
                    hypothesisId = "H2",
                    location = "MainViewModel.importLocalMedia",
                    message = "scan_import_complete",
                    data = mapOf(
                        "scannedCount" to result.scannedCount,
                        "importedCount" to result.importedCount
                    ),
                    runId = "post-fix-v2"
                )
                // #endregion
                val statusAfterScan = if (result.scannedCount == 0 && result.importedCount == 0) {
                    "No device songs found. Check storage permission and try again."
                } else {
                    "Imported ${result.importedCount} tracks from ${result.scannedCount} scanned device songs"
                }
                _uiState.update {
                    it.copy(
                        isScanningDeviceLibrary = false,
                        deviceScanProgress = null,
                        statusMessage = statusAfterScan
                    )
                }
                // #region agent log
                com.audiophile.musicplayer.debug.DebugSessionLogger.log(
                    hypothesisId = "H1",
                    location = "MainViewModel.importLocalMedia",
                    message = "scan_complete_defer_refresh",
                    data = mapOf(
                        "scannedCount" to result.scannedCount,
                        "importedCount" to result.importedCount
                    ),
                    runId = "post-fix-v4"
                )
                // #endregion
                delay(400)
            } catch (e: SecurityException) {
                // #region agent log
                com.audiophile.musicplayer.debug.DebugSessionLogger.log(
                    hypothesisId = "H4",
                    location = "MainViewModel.importLocalMedia",
                    message = "scan_security_exception",
                    data = mapOf("error" to (e.message ?: "unknown"))
                )
                // #endregion
                Log.e("MainViewModel", "Device library scan denied", e)
                _uiState.update {
                    it.copy(
                        isScanningDeviceLibrary = false,
                        deviceScanProgress = null,
                        statusMessage = "Storage permission required to scan device music."
                    )
                }
            } catch (e: Exception) {
                // #region agent log
                com.audiophile.musicplayer.debug.DebugSessionLogger.log(
                    hypothesisId = "H3",
                    location = "MainViewModel.importLocalMedia",
                    message = "scan_exception",
                    data = mapOf(
                        "errorType" to e.javaClass.simpleName,
                        "error" to (e.message ?: "unknown")
                    )
                )
                // #endregion
                Log.e("MainViewModel", "Device library scan failed", e)
                _uiState.update {
                    it.copy(
                        isScanningDeviceLibrary = false,
                        deviceScanProgress = null,
                        statusMessage = "Scan failed: ${e.message ?: "unknown error"}"
                    )
                }
            }
            refreshLibraryAfterScan()
        }
    }

    fun importSingleLocalFile(uri: Uri) {
        viewModelScope.launch {
            _uiState.update { it.copy(isSearching = true, statusMessage = "Importing file...") }
            val success = withContext(Dispatchers.IO) { container.localMediaImporter.importSingleUri(uri) }
            refreshAll()
            _uiState.update {
                it.copy(
                    isSearching = false,
                    statusMessage = if (success) "Successfully imported local track" else "Failed to import local track"
                )
            }
        }
    }

    fun syncTorBoxLibrary() {
        viewModelScope.launch {
            val token = container.resolverConfigStore.getTorBoxApiToken().orEmpty()
            if (token.isBlank()) {
                setStatusMessage("Connect an advanced source token first")
                return@launch
            }

            val imported = withContext(Dispatchers.IO) {
                val candidates = container.torBoxRepository.importableAudioFiles(token)
                var count = 0
                candidates.forEach { candidate ->
                    val streamUrl = candidate.file.downloadUrl
                        ?: container.torBoxRepository.getDirectStreamUrlForTorrent(
                            bearerToken = token,
                            torrentId = candidate.torrentId,
                            fileId = candidate.file.fileId
                        )
                        ?: return@forEach

                    val (artist, title) = splitArtistAndTitle(candidate.file.name, candidate.torrentName)
                    container.trackRepository.addTrackSource(
                        title = title,
                        artist = artist,
                        album = candidate.torrentName,
                        coverArtUrl = null,
                        sourceType = SourceType.TORBOX,
                        streamUrl = streamUrl,
                        bitrate = estimateBitrate(candidate.file.name, candidate.file.mimeType)
                    )
                    count += 1
                }
                count
            }
            refreshAll()
            setStatusMessage("Imported $imported advanced source audio file(s)")
        }
    }

    fun playSourceResult(result: CanonicalTrack) {
        val tapMs = System.currentTimeMillis()
        Log.w("VANTA_PLAY_CLICK", "Tapped track: id=${result.externalTrackId}, provider=${result.sourceProviderId}")

        viewModelScope.launch {
            try {
            setStatusMessage("Resolving stream…")
            val simplifiedTitle = DisplayMetadataCleaner.cleanTitle(result.title)

            val resolved = withContext(Dispatchers.IO) {
                resolveCanonicalTrackForPlayback(result)
            } ?: run {
                Log.w("VANTA_PLAY_CLICK", "No playable source for '${result.title}'")
                setStatusMessage("No playable source found for $simplifiedTitle")
                return@launch
            }

            val (playable, resolvedStream) = resolved
            val sourceCleanTitle = simplifiedTitle
            Log.d("VANTA_PLAY_CLICK", "Using source: providerId=${playable.providerId} trackId=${playable.id} status=${playable.status}")
            Log.d("VANTA_PLAY_CLICK", "Stream resolved: url=${resolvedStream.streamUrl.take(80)}... bitrate=${resolvedStream.bitrateKbps}kbps")

            if (resolvedStream.streamUrl.isBlank()) {
                Log.e("VANTA_STREAM_VALIDATE", "Resolved stream URL is blank for '${result.title}'")
                setStatusMessage("Resolved stream URL is empty for $sourceCleanTitle")
                return@launch
            }
            if (resolvedStream.streamUrl.contains("soundhelix", ignoreCase = true)) {
                Log.e("VANTA_STREAM_VALIDATE", "Resolved stream is SoundHelix for '${result.title}' — blocking")
                setStatusMessage("Demo audio cannot be used for this track.")
                return@launch
            }

            val trackId = container.trackRepository.addTrackSource(
                title = result.title,
                artist = result.artist,
                album = result.album,
                coverArtUrl = result.artworkUrl,
                sourceType = com.audiophile.musicplayer.data.source.CloudLibraryHelpers.sourceTypeForProvider(playable.providerId),
                streamUrl = resolvedStream.streamUrl,
                bitrate = resolvedStream.bitrateKbps,
                genre = result.genre,
                isrc = result.isrc ?: playable.isrc,
                durationMs = result.durationMs ?: playable.durationMs,
                externalProviderId = playable.providerId,
                externalTrackId = playable.id,
                expiresAtMs = resolvedStream.expiresAt
            )
            Log.d("VANTA_PLAY_CLICK", "addTrackSource returned trackId=$trackId")

            val track = container.trackRepository.getTrackWithSources(trackId)
            if (track != null) {
                val totalPlayMs = System.currentTimeMillis() - tapMs
                Log.d("VANTA_PLAY_CLICK", "Track reloaded: trackId=${track.track.trackId} sources=${track.sources.size}")
                Log.d("VANTA_PLAY_PERF", "title='${track.track.title}' totalMs=$totalPlayMs")
                if (track.sources.none { it.streamUrl.isNotBlank() }) {
                    Log.e("VANTA_PLAY_CLICK", "Track has no valid stream URLs after addTrackSource!")
                    setStatusMessage("Track was added but has no playable stream URL")
                    return@launch
                }
                val queue = playbackQueueFor(track)
                val startIndex = queue.indexOfFirst { it.track.trackId == track.track.trackId }.coerceAtLeast(0)
                container.playerController.playQueue(queue, startIndex)
                container.nowPlayingStateStore.save(
                    NowPlayingState.pendingPlayback(
                        track = track,
                        queuePosition = startIndex,
                        queueSize = queue.size,
                        preferredProviderId = playable.providerId,
                        preferredExternalTrackId = playable.id,
                        userQuery = _uiState.value.query.takeIf { it.isNotBlank() }
                    ).copy(
                        title = result.title,
                        artist = result.artist,
                        album = result.album,
                        isrc = result.isrc ?: track.track.isrc ?: playable.isrc,
                        artworkUrl = result.artworkUrl,
                        durationMs = result.durationMs ?: track.track.durationMs ?: playable.durationMs ?: 0L,
                        qualityInfo = result.qualityInfo
                    )
                )
                _uiState.update {
                    it.copy(
                        activeTrackId = track.track.trackId.toString(),
                        activeTrackEnhancedMetadata = null,
                        queueSnapshot = container.queueManager.snapshot(),
                        statusMessage = "Playing ${DisplayMetadataCleaner.cleanTitle(track.track.title)}"
                    )
                }

                launch(Dispatchers.IO) {
                    try {
                        val recommendations = container.aiDjRecommendationEngine.getSimilarTracks(result.artist, null)
                        if (recommendations.isNotEmpty()) {
                            val existingIds = container.queueManager.originalQueue.map { it.track.trackId }.toSet()
                            var added = 0
                            for (t in recommendations) {
                                if (t.isPlayableMusicCandidate() && t.track.trackId !in existingIds) {
                                    container.queueManager.addToOriginalQueue(t)
                                    added++
                                }
                            }
                            if (added > 0) {
                                Log.d("VANTA_QUEUE_EXPAND", "search_play seed='${result.title}' added=$added queueSize=${container.queueManager.originalQueue.size}")
                                _uiState.update { it.copy(queueSnapshot = container.queueManager.snapshot()) }
                            }
                        }
                    } catch (e: Exception) {
                        Log.w("VANTA_QUEUE_EXPAND", "error=unexpected seed='${result.title}' reason='${e.message}'")
                    }
                }
            } else {
                Log.e("VANTA_PLAY_CLICK", "getTrackWithSources returned null for trackId=$trackId")
                setStatusMessage("Resolved track was added but could not be reloaded")
            }
            } catch (e: Exception) {
                Log.e("VANTA_PLAY_CLICK", "Unexpected crash in playSourceResult", e)
                setStatusMessage("Error playing track: ${e.message}")
            }
        }
    }

    fun saveSourceResultToLibrary(result: CanonicalTrack) {
        viewModelScope.launch {
            val cleanTitle = DisplayMetadataCleaner.cleanTitle(result.title)

            val resolved = withContext(Dispatchers.IO) {
                resolveCanonicalTrackForPlayback(result)
            } ?: run {
                setStatusMessage("No playable source found for $cleanTitle")
                return@launch
            }

            val (playable, resolvedStream) = resolved
            container.trackRepository.addTrackSource(
                title = result.title,
                artist = result.artist,
                album = result.album,
                coverArtUrl = result.artworkUrl,
                sourceType = com.audiophile.musicplayer.data.source.CloudLibraryHelpers.sourceTypeForProvider(playable.providerId),
                streamUrl = resolvedStream.streamUrl,
                bitrate = resolvedStream.bitrateKbps,
                genre = result.genre,
                isrc = result.isrc ?: playable.isrc,
                durationMs = result.durationMs ?: playable.durationMs,
                externalProviderId = playable.providerId,
                externalTrackId = playable.id,
                expiresAtMs = resolvedStream.expiresAt
            )
            refreshAll()
            _uiState.update {
                it.copy(statusMessage = "\"$cleanTitle\" saved to library")
            }
        }
    }

    fun testAppleMusicConnection() {
        val userToken = container.accountManager.profile.value.appleMusicUserToken
        val storefront = container.accountManager.profile.value.appleMusicStorefront ?: "us"
        val devToken = container.resolverConfigStore.getAppleMusicDeveloperToken()
        
        if (devToken.isNullOrBlank()) {
            setStatusMessage("Apple Music Developer Token not configured")
            return
        }
        
        if (userToken.isNullOrBlank()) {
            setStatusMessage("Apple Music not connected")
            return
        }

        viewModelScope.launch {
            try {
                val client = com.audiophile.musicplayer.data.metadata.apple.AppleMusicApiClient(devToken, userToken)
                val response = client.fetchLibrarySongs(offset = 0, limit = 1)
                setStatusMessage("Connected! Found ${response.data.size} songs in library.")
            } catch (e: Exception) {
                Log.e("VANTA_AM", "Connection test failed", e)
                setStatusMessage("Connection failed: ${e.message}")
            }
        }
    }

    fun importAppleMusicLibrary() {
        val userToken = container.accountManager.profile.value.appleMusicUserToken
        val devToken = container.resolverConfigStore.getAppleMusicDeveloperToken()
        val storefront = container.accountManager.profile.value.appleMusicStorefront ?: "us"

        if (devToken.isNullOrBlank() || userToken.isNullOrBlank()) {
            setStatusMessage("Connect Apple Music in settings first")
            return
        }

        val account = com.audiophile.musicplayer.data.connectors.ConnectedLibraryAccount(
            id = "apple_music_user",
            provider = com.audiophile.musicplayer.data.connectors.ConnectedLibraryProvider.APPLE_MUSIC,
            displayName = container.accountManager.profile.value.displayName,
            accountId = "apple_music_user",
            connectedAt = System.currentTimeMillis(),
            scopesGranted = setOf("dev:$devToken", "user:$userToken")
        )

        viewModelScope.launch {
            setStatusMessage("Starting Apple Music import...")
            try {
                var totalImported = 0
                var cursor: String? = null
                val request = com.audiophile.musicplayer.data.connectors.ConnectedLibraryImportRequest(
                    provider = com.audiophile.musicplayer.data.connectors.ConnectedLibraryProvider.APPLE_MUSIC
                )

                do {
                    val page = container.appleMusicLibraryConnector.fetchLibraryPage(account, request, cursor)
                    if (page.tracks.isNotEmpty()) {
                        val mapped = page.tracks.map {
                            com.audiophile.musicplayer.data.local.entities.ImportedTrackEntity(
                                batchId = 0,
                                rawText = "${it.title} - ${it.artist}",
                                parsedTitle = it.title,
                                parsedArtist = it.artist,
                                parsedAlbum = it.album,
                                sourceUrl = it.artworkUrl,
                                matchStatus = com.audiophile.musicplayer.data.importer.ImportMatchStatus.MATCHED,
                                playabilityStatus = com.audiophile.musicplayer.data.importer.PlayabilityStatus.METADATA_ONLY,
                                matchConfidence = com.audiophile.musicplayer.data.importer.MatchConfidence.MEDIUM,
                                matchReason = "Imported from Apple Music",
                                friendlySourceLabel = "Apple Music",
                                matchedSongId = null,
                                confidenceScore = 0.5f
                            )
                        }
                        saveImportedRowsToLibrary(mapped, allowMetadataFallback = true)
                        totalImported += mapped.size
                        setStatusMessage("Imported $totalImported tracks...")
                    }
                    cursor = page.nextCursor
                } while (cursor != null)

                setStatusMessage("Import complete! $totalImported tracks added.")
                refreshAll()
            } catch (e: Exception) {
                Log.e("VANTA_AM", "Import failed", e)
                setStatusMessage("Import failed: ${e.message}")
            }
        }
    }
    fun testTorBoxConnection() = sourceHealthHelper.testTorBoxConnection()
    fun testRealDebridConnection() = sourceHealthHelper.testRealDebridConnection()
    fun testLlmConnection() = sourceHealthHelper.testLlmConnection()

    fun testLastFmConnection() {
        viewModelScope.launch {
            val apiKey = _uiState.value.resolverConfig.lastFmApiKey.trim()
            if (apiKey.isBlank()) {
                _uiState.update { it.copy(statusMessage = "Enter a Last.fm API key first.") }
                return@launch
            }
            _uiState.update { it.copy(statusMessage = "Testing Last.fm…") }
            val error = withContext(Dispatchers.IO) { container.lastFmGenreResolver.validateApiKey(apiKey) }
            _uiState.update {
                it.copy(
                    statusMessage = error ?: "Last.fm API key OK — jukebox can enrich genres from artist tags."
                )
            }
        }
    }

    fun testPulseVoice() {
        viewModelScope.launch {
            _uiState.update { it.copy(statusMessage = "Testing Pulse voice…") }
            val result = withContext(Dispatchers.IO) {
                container.pulseVoiceEngine.synthesize(
                    "Let's go — the set is live and the energy is up."
                )
            }
            _uiState.update {
                it.copy(
                    statusMessage = when {
                        result == null -> "Pulse is ready with the energetic on-device voice. Select Gemini for generated AI speech."
                        result.fromCache -> "Pulse voice OK — cached audio ready."
                        else -> "Pulse voice OK — Gemini generated speech directly."
                    }
                )
            }
        }
    }
    fun testExternalSource(baseUrl: String) = sourceHealthHelper.testExternalSource(baseUrl)
    fun addExternalSource(baseUrl: String) = sourceHealthHelper.addExternalSource(baseUrl)
    fun removeExternalSource(id: String) = sourceHealthHelper.removeExternalSource(id)
    fun toggleExternalSource(id: String, enabled: Boolean) = sourceHealthHelper.toggleExternalSource(id, enabled)
    fun moveExternalSource(id: String, direction: Int) = sourceHealthHelper.moveExternalSource(id, direction)
    fun clearFailedSourceCache() = sourceHealthHelper.clearFailedSourceCache()
    fun testSourceHealth(providerId: String) = sourceHealthHelper.testSourceHealth(providerId)
    fun testAllSourceHealth() = sourceHealthHelper.testAllSourceHealth()
    fun testSourceSearch(id: String, query: String) = sourceHealthHelper.testSourceSearch(id, query)
    fun testSourceStream(id: String, trackId: String) = sourceHealthHelper.testSourceStream(id, trackId)

    fun updateExternalSourceUrls(
        id: String,
        baseUrl: String,
        searchBaseUrl: String?,
        streamEndpointUrl: String?
    ) = sourceHealthHelper.updateExternalSourceUrls(id, baseUrl, searchBaseUrl, streamEndpointUrl)

    fun saveResolverConfiguration() {
        viewModelScope.launch {
            val form = _uiState.value.resolverConfig
            container.resolverConfigStore.setTorBoxResolverBaseUrl(
                form.torBoxBaseUrl.trim().takeIf { it.isNotBlank() }
            )
            container.resolverConfigStore.setTorBoxApiToken(
                form.torBoxApiToken.trim().takeIf { it.isNotBlank() }
            )
            container.resolverConfigStore.setRealDebridApiToken(
                form.realDebridApiToken.trim().takeIf { it.isNotBlank() }
            )
            container.resolverConfigStore.setCommunityInstances(
                form.communityInstancesText
                    .lineSequence()
                    .map { it.trim() }
                    .filter { it.isNotBlank() }
                    .toSet()
            )
            val provider = form.llmProviderName.trim().takeIf { it.isNotBlank() }
                ?.let { runCatching { AiProvider.valueOf(it) }.getOrNull() }
            container.resolverConfigStore.setLlmProvider(provider)
            if (provider != null) {
                val newKey = form.llmApiKey.trim()
                val oldKey = container.resolverConfigStore.getLlmApiKey(provider)
                if (newKey != oldKey.orEmpty()) {
                    container.resolverConfigStore.clearLlmVerified(provider)
                }
                container.resolverConfigStore.setLlmApiKey(
                    provider,
                    newKey.takeIf { it.isNotBlank() }
                )
            }
            container.resolverConfigStore.setAppleMusicDeveloperToken(
                form.appleMusicDeveloperToken.trim().takeIf { it.isNotBlank() }
            )
            container.resolverConfigStore.setAppleMusicStorefront(
                form.appleMusicStorefront.trim()
            )
            container.resolverConfigStore.setPulseVoiceRelayUrl(
                form.pulseVoiceRelayUrl.trim().takeIf { it.isNotBlank() }
            )
            container.resolverConfigStore.setPulseVoiceRelayToken(
                form.pulseVoiceRelayToken.trim().takeIf { it.isNotBlank() }
            )
            container.resolverConfigStore.setPulseVoiceEngine(
                form.pulseVoiceEngine.trim().ifBlank { PulseVoiceProfile.GEMINI_ENGINE }
            )
            container.resolverConfigStore.setLastFmApiKey(
                form.lastFmApiKey.trim().takeIf { it.isNotBlank() }
            )
            container.resolverConfigStore.setLastFmApiSecret(
                form.lastFmApiSecret.trim().takeIf { it.isNotBlank() }
            )
            container.resolverConfigStore.setLastFmUsername(
                form.lastFmUsername.trim().takeIf { it.isNotBlank() }
            )
            container.resolverConfigStore.setLastFmSessionKey(
                form.lastFmSessionKey.trim().takeIf { it.isNotBlank() }
            )
            
            container.reloadResolverConfiguration()
            val torBoxCandidates = withContext(Dispatchers.IO) { loadTorBoxCandidates() }
            _uiState.update {
                it.copy(
                    statusMessage = "Resolver configuration saved",
                    resolverConfig = loadResolverConfigForm(),
                    torBoxCandidates = torBoxCandidates
                )
            }
            if (_uiState.value.query.isNotBlank()) {
                search()
            }
        }
    }

    fun playSongRadio(title: String, artist: String, album: String?, genre: String?) {
        val seedKey = "${title.lowercase()}|${artist.lowercase()}"
        val nowMs = System.currentTimeMillis()
        if (seedKey == lastRadioSeedKey && (nowMs - lastRadioSeedTimeMs) < 2000L) {
            Log.d("VANTA_RADIO_TRUTH", "ignored_duplicate seedTitle='${title}'")
            return
        }
        lastRadioSeedKey = seedKey
        lastRadioSeedTimeMs = nowMs

        viewModelScope.launch {
            _uiState.update { it.copy(statusMessage = "Building station...") }
            Log.d("VANTA_RADIO_TRUTH", "start seedTitle='${title}' seedArtist='${artist}'")

            val library = _uiState.value.library
            val playedIds = container.queueManager.playedHistory.toSet()
            val currentQueueIds = container.queueManager.originalQueue.map { it.track.trackId }.toSet()
            Log.d("VANTA_RADIO_ENGINE", "excluded_recent count=${playedIds.size} currentQueue=${currentQueueIds.size}")

            // 1) Generate via provider engine
            val engineResult = withContext(Dispatchers.IO) {
                container.radioQueueEngine.generate(
                    com.audiophile.musicplayer.radio.RadioSeed(title, artist, album, genre),
                    excludeTrackIds = currentQueueIds,
                    playedTrackIds = playedIds
                )
            }
            var candidates = engineResult.candidates
            Log.d("VANTA_RADIO_TRUTH", "engine_generated=${candidates.size}")

            // 2) Supplement from local library if engine returned few or none
            if (candidates.size < 10) {
                val seenNorm = candidates.mapTo(mutableSetOf()) {
                    "${it.track.title?.lowercase()}|${it.track.artist?.lowercase()}"
                }
                val seedNorm = seedKey
                val localMatches = library.filter { track ->
                    if (!track.sources.any { it.streamUrl.isNotBlank() }) return@filter false
                    val t = track.track
                    val norm = "${t.title.lowercase()}|${t.artist.lowercase()}"
                    if (norm == seedNorm) return@filter false
                    if (t.trackId in playedIds) return@filter false
                    if (t.trackId in currentQueueIds) return@filter false
                    if (norm in seenNorm) return@filter false
                    t.artist.equals(artist, ignoreCase = true) ||
                        (album != null && t.albumName.equals(album, ignoreCase = true)) ||
                        (genre != null && t.genre?.equals(genre, ignoreCase = true) == true)
                }
                Log.d("VANTA_RADIO_TRUTH", "local_supplement=${localMatches.size}")
                candidates = (candidates + localMatches).distinctBy { it.track.trackId }
            }

            val cleanPlayable = candidates.filter { it.sources.any { s -> s.streamUrl.isNotBlank() } }
            if (cleanPlayable.size < 5) {
                Log.d("VANTA_RADIO_TRUTH", "failed reason='not_enough_clean_tracks' total=${candidates.size} playable=${cleanPlayable.size} minRequired=5")
                _uiState.update { it.copy(statusMessage = "Radio is still learning from this song.") }
                lastRadioSeedKey = null
                return@launch
            }

            val selected = cleanPlayable.shuffled().take(30)
            Log.d("VANTA_RADIO_TRUTH", "about_to_start_queue branch='playSongRadio' count=${selected.size}")
            val startIndex = selected.indexOfFirst { it.track.artist.equals(artist, ignoreCase = true) }.coerceAtLeast(0)
            container.playerController.playQueue(selected, startIndex, com.audiophile.musicplayer.playback.QueueMode.RADIO_QUEUE)
            val first = selected.first()
            container.nowPlayingStateStore.save(
                NowPlayingState(
                    trackId = first.track.trackId.toString(),
                    title = first.track.title,
                    artist = first.track.artist,
                    album = first.track.albumName,
                    artworkUrl = first.track.coverArtUrl,
                    durationMs = first.track.durationMs ?: 0L,
                    isPlaying = true
                )
            )
            _uiState.update {
                it.copy(
                    queueSnapshot = container.queueManager.snapshot(),
                    statusMessage = "Song Radio: playing \"$title\" and similar tracks"
                )
            }
        }
    }

    fun playArtistRadio(artistName: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(statusMessage = "Loading songs by $artistName...") }
            Log.d("VANTA_RADIO_TRUTH", "start_artist artist='${artistName}'")

            withContext(Dispatchers.IO) {
                container.queueManager.setRadioArtistLock(artistName)
                container.queueManager.clearOriginalQueue()
            }

            val library = _uiState.value.library
            val playedIds = container.queueManager.playedHistory.toSet()
            val currentQueueIds = container.queueManager.originalQueue.map { it.track.trackId }.toSet()
            Log.d("VANTA_RADIO_ENGINE", "artist_excluded_recent count=${playedIds.size}")

            // 1) Generate via provider engine
            val engineResult = withContext(Dispatchers.IO) {
                container.radioQueueEngine.generate(
                    com.audiophile.musicplayer.radio.RadioSeed(title = "", artist = artistName, album = null, genre = null),
                    excludeTrackIds = currentQueueIds,
                    playedTrackIds = playedIds
                )
            }
            var candidates = engineResult.candidates
            Log.d("VANTA_RADIO_TRUTH", "artist_engine_generated=${candidates.size}")

            // 2) Supplement from local library
            if (candidates.count { it.track.artist.equals(artistName, ignoreCase = true) } < 5) {
                val seenNorm = candidates.mapTo(mutableSetOf()) {
                    "${it.track.title?.lowercase()}|${it.track.artist?.lowercase()}"
                }
                val localMatches = library.filter { track ->
                    track.sources.any { it.streamUrl.isNotBlank() } &&
                        track.track.artist.equals(artistName, ignoreCase = true) &&
                        "${track.track.title?.lowercase()}|${track.track.artist?.lowercase()}" !in seenNorm &&
                        track.track.trackId !in playedIds &&
                        track.track.trackId !in currentQueueIds
                }
                Log.d("VANTA_RADIO_TRUTH", "artist_local_supplement=${localMatches.size}")
                candidates = (candidates + localMatches).distinctBy { it.track.trackId }
            }

            // Artist pages are catalog-backed, so radio must not depend on saved songs.
            // Resolve exact catalog recordings before falling back to a loose source search.
            if (candidates.count { it.track.artist.equals(artistName, ignoreCase = true) } < 5) {
                val loadedCatalog = _uiState.value.artistCatalog
                    ?.takeIf { it.artist.name.equals(artistName, ignoreCase = true) }
                    ?: withContext(Dispatchers.IO) { container.catalogBrowseRepository.browseArtist(artistName, limit = 30) }
                val catalogSeeds = loadedCatalog.tracks
                    .filter { it.artist.equals(artistName, ignoreCase = true) }
                    .distinctBy { "${it.title.trim().lowercase()}|${it.artist.trim().lowercase()}" }
                    .take(16)
                if (catalogSeeds.isNotEmpty()) {
                    _uiState.update {
                        it.copy(
                            artistCatalog = loadedCatalog,
                            artistCatalogLoading = false,
                            statusMessage = "Finding playable versions of ${catalogSeeds.size} songs..."
                        )
                    }
                    val resolvedCatalog = withContext(Dispatchers.IO) { resolveCatalogTracks(catalogSeeds) }
                    if (resolvedCatalog.isNotEmpty()) {
                        candidates = (resolvedCatalog + candidates)
                            .distinctBy { "${it.track.title.trim().lowercase()}|${it.track.artist.trim().lowercase()}" }
                        Log.d("VANTA_RADIO_TRUTH", "artist_catalog_resolved=${resolvedCatalog.size} totalCandidates=${candidates.size}")
                    }
                }
            }

            if (candidates.size < 5) {
                val resolvedArtistTracks = withContext(Dispatchers.IO) { materializeSourceTracksForArtist(artistName, limit = 12) }
                if (resolvedArtistTracks.isNotEmpty()) {
                    candidates = (candidates + resolvedArtistTracks).distinctBy { it.track.trackId }
                    Log.d("VANTA_RADIO_TRUTH", "artist_source_materialized=${resolvedArtistTracks.size} totalCandidates=${candidates.size}")
                }
            }

            if (candidates.isEmpty()) {
                Log.d("VANTA_RADIO_TRUTH", "failed reason='no_artist_tracks' artist='${artistName}'")
                _uiState.update { it.copy(statusMessage = "No playable source found for $artistName.") }
                return@launch
            }

            val cleanCandidates = candidates.filterNot { JukeboxTrackEligibility.shouldExcludeFromRadioQueue(it) }
            if (cleanCandidates.isEmpty()) {
                Log.d("VANTA_RADIO_TRUTH", "failed reason='only_video_junk' artist='${artistName}'")
                _uiState.update { it.copy(statusMessage = "No studio tracks found for $artistName.") }
                return@launch
            }

            val exactArtistTracks = cleanCandidates
                .filter { it.track.artist.equals(artistName, ignoreCase = true) }
                .sortedByDescending { track ->
                    var score = 0
                    if (track.sources.any { it.streamUrl.isNotBlank() }) score += 2
                    val durationMs = track.track.durationMs
                    if (durationMs != null && durationMs in 120_000L..420_000L) score += 1
                    score
                }
            val selected = exactArtistTracks
                .distinctBy { it.track.trackId }
                .take(30)
            Log.d("VANTA_RADIO_TRUTH", "about_to_start_queue branch='playArtistRadio' count=${selected.size}")
            container.playerController.playQueue(selected, 0, com.audiophile.musicplayer.playback.QueueMode.RADIO_QUEUE)
            val first = selected.first()
            container.nowPlayingStateStore.save(
                NowPlayingState(
                    trackId = first.track.trackId.toString(),
                    title = first.track.title,
                    artist = first.track.artist,
                    album = first.track.albumName,
                    artworkUrl = first.track.coverArtUrl,
                    durationMs = first.track.durationMs ?: 0L,
                    isPlaying = true
                )
            )
            _uiState.update {
                it.copy(
                    queueSnapshot = container.queueManager.snapshot(),
                    statusMessage = "Artist Radio: $artistName"
                )
            }
        }
    }

    private suspend fun resolveCatalogTracks(tracks: List<CanonicalTrack>): List<UnifiedTrackWithSources> =
        coroutineScope {
            val gate = Semaphore(permits = 4)
            tracks.map { track ->
                async {
                    gate.withPermit { resolveCanonicalTrack(track) }
                }
            }.awaitAll().filterNotNull()
        }

    fun playQueue(tracks: List<UnifiedTrackWithSources>, startIndex: Int = 0) {
        if (tracks.isEmpty()) return
        val safeStartIndex = startIndex.coerceIn(tracks.indices)
        val selectedTrack = tracks[safeStartIndex]
        val interimSnapshot = QueueSnapshot(
            originalQueue = tracks,
            currentOriginalIndex = safeStartIndex,
            currentTrack = selectedTrack,
            queueIndex = safeStartIndex,
            queueSize = tracks.size,
            canPlayNext = safeStartIndex < tracks.lastIndex,
            canPlayPrevious = safeStartIndex > 0
        )
        _uiState.update {
            it.copy(
                activeTrackId = selectedTrack.track.trackId.toString(),
                queueSnapshot = interimSnapshot,
                activeTrackEnhancedMetadata = null,
                statusMessage = "Playing ${DisplayMetadataCleaner.cleanTitle(selectedTrack.track.title)}"
            )
        }
        container.playerController.playQueue(tracks, safeStartIndex)
    }

    private suspend fun materializeSourceTracksForArtist(
        artistName: String,
        limit: Int
    ): List<UnifiedTrackWithSources> {
        _uiState.update { it.copy(statusMessage = "Searching sources for $artistName...") }
        val queries = listOf(artistName, "$artistName songs", "$artistName music")
        val sourceResults = mutableListOf<SourceSearchResult>()
        for (query in queries) {
            sourceResults += container.sourceRegistry.searchAll(query, timeoutMs = 12_000L)
                .filter { it.status.canResolveStream() }
                .filter { it.isLikelyMusicTrack() }
            if (sourceResults.size >= limit) break
        }

        val ranked = sourceResults
            .distinctBy { "${it.providerId}:${it.id}" }
            .sortedByDescending { candidate ->
                var score = 0
                if (candidate.artist.contains(artistName, ignoreCase = true)) score += 80
                if (candidate.title.contains(artistName, ignoreCase = true)) score += 30
                if (!candidate.isrc.isNullOrBlank()) score += 10
                if (candidate.durationMs != null) score += 5
                score
            }
            .take(limit)

        val materialized = mutableListOf<UnifiedTrackWithSources>()
        for (candidate in ranked) {
            val resolved = container.sourceRegistry.resolveStream(candidate.providerId, candidate.id, timeoutMs = 12_000L)
                ?: continue
            if (resolved.streamUrl.isBlank()) continue
            if (resolved.streamUrl.contains("soundhelix", ignoreCase = true)) continue
            val trackId = container.trackRepository.addTrackSource(
                title = candidate.title,
                artist = candidate.artist.ifBlank { artistName },
                album = candidate.album,
                coverArtUrl = candidate.artworkUrl,
                sourceType = SourceType.ADDON,
                streamUrl = resolved.streamUrl,
                bitrate = resolved.bitrateKbps,
                isrc = candidate.isrc,
                durationMs = candidate.durationMs,
                externalProviderId = candidate.providerId,
                externalTrackId = candidate.id,
                expiresAtMs = resolved.expiresAt
            )
            container.trackRepository.getTrackWithSources(trackId)?.let { materialized += it }
        }

        if (materialized.isNotEmpty()) {
            val currentLibrary = _uiState.value.library
            val nextLibrary = (currentLibrary + materialized).distinctBy { it.track.trackId }
            _uiState.update { current ->
                current.copy(
                    library = nextLibrary,
                    visibleTracks = if (current.query.isBlank()) nextLibrary else current.visibleTracks,
                    statusMessage = "Found ${materialized.size} playable source(s) for $artistName"
                )
            }
        }
        return materialized
    }

    fun generateMoodMix(mood: String): List<UnifiedTrackWithSources> {
        val library = _uiState.value.library
        val moodKeywords = when (mood.lowercase()) {
            "chill", "chill vibes" -> listOf("chill", "ambient", "lofi", "lo-fi", "smooth", "relax", "calm", "slow", "acoustic")
            "focus" -> listOf("focus", "instrumental", "ambient", "classical", "piano", "study", "concentrate")
            "energy", "workout" -> listOf("energy", "upbeat", "dance", "workout", "power", "intense", "electronic", "rock")
            "late night" -> listOf("night", "late", "midnight", "moon", "dark", "deep", "r&b", "soul", "jazz")
            "happy" -> listOf("happy", "upbeat", "cheerful", "fun", "party", "pop", "dance")
            "deep focus", "deep cuts" -> listOf("deep", "ambient", "classical", "instrumental", "piano", "focus", "meditation")
            else -> emptyList()
        }
        val seenIds = mutableSetOf<Long>()
        return library.filter { track ->
            if (track.track.trackId in seenIds) return@filter false
            if (!track.sources.any { it.streamUrl.isNotBlank() }) return@filter false
            val title = track.track.title.lowercase()
            val artist = track.track.artist.lowercase()
            val genre = track.track.genre?.lowercase().orEmpty()
            val album = track.track.albumName?.lowercase().orEmpty()
            seenIds.add(track.track.trackId)
            moodKeywords.any { keyword ->
                title.contains(keyword) || genre.contains(keyword) || album.contains(keyword)
            }
        }.take(30)
    }

    fun saveNowPlayingToLibrary() {
        viewModelScope.launch {
            val nowPlaying = container.nowPlayingStateStore.load() ?: run {
                setStatusMessage("Nothing playing")
                return@launch
            }
            val title = nowPlaying.title ?: return@launch
            val artist = nowPlaying.artist ?: return@launch
            val existing = withContext(Dispatchers.IO) {
                container.localLibraryRepository.allSongsSnapshot().any {
                    it.title.equals(title, ignoreCase = true) && it.artist.equals(artist, ignoreCase = true)
                }
            }
            if (existing) {
                setStatusMessage("Already in library")
                return@launch
            }
            withContext(Dispatchers.IO) {
                container.localLibraryRepository.saveSongs(
                    listOf(
                        com.audiophile.musicplayer.data.local.entities.LocalSongEntity(
                            title = title,
                            artist = artist,
                            album = nowPlaying.album,
                            artworkUrl = nowPlaying.artworkUrl,
                            durationMs = nowPlaying.durationMs.takeIf { it > 0 }
                        )
                    )
                )
            }
            refreshAll()
            setStatusMessage("Saved to library")
        }
    }

    private var sleepTimerJob: kotlinx.coroutines.Job? = null

    fun startSleepTimer(minutes: Int) {
        sleepTimerJob?.cancel()
        val delayMs = minutes * 60_000L
        sleepTimerJob = viewModelScope.launch {
            delay(delayMs)
            container.playerController.pause()
            _uiState.update { it.copy(statusMessage = "Sleep timer: playback paused after ${minutes}m") }
            refreshQueueState()
        }
        setStatusMessage("Sleep timer set for ${minutes} minutes")
    }

    fun cancelSleepTimer() {
        sleepTimerJob?.cancel()
        sleepTimerJob = null
        setStatusMessage("Sleep timer cancelled")
    }

    fun addNowPlayingToPlaylist(playlistId: Long) {
        viewModelScope.launch {
            val nowPlaying = container.nowPlayingStateStore.load() ?: run {
                setStatusMessage("Nothing playing"); return@launch
            }
            val title = nowPlaying.title ?: run { setStatusMessage("No title"); return@launch }
            val artist = nowPlaying.artist ?: "Unknown Artist"
            val songId = withContext(Dispatchers.IO) {
                var id = container.localLibraryRepository.allSongsSnapshot().find {
                    it.title.equals(title, ignoreCase = true) && it.artist.equals(artist, ignoreCase = true)
                }?.id
                if (id == null) {
                    id = container.localLibraryRepository.saveSongs(
                        listOf(LocalSongEntity(
                            title = title, artist = artist,
                            album = nowPlaying.album,
                            artworkUrl = nowPlaying.artworkUrl,
                            durationMs = nowPlaying.durationMs.takeIf { it > 0 }
                        ))
                    ).firstOrNull()
                }
                if (id != null) {
                    container.localLibraryRepository.addSongsToPlaylist(playlistId, listOf(id))
                }
                id
            }
            if (songId != null) {
                setStatusMessage("Added to playlist")
                refreshAll()
            }
        }
    }

    fun loadPlaylists(callback: (List<com.audiophile.musicplayer.data.local.entities.PlaylistEntity>) -> Unit) {
        viewModelScope.launch {
            val playlists = withContext(Dispatchers.IO) { container.localLibraryRepository.playlistsSnapshot() }
            callback(playlists)
        }
    }

    fun createPlaylist(name: String) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { container.localLibraryRepository.createPlaylist(name = name) }
            refreshAll()
            setStatusMessage("Playlist \"$name\" created")
        }
    }

    fun openGeneratorUrl(url: String) {
        val trimmed = url.trim()
        if (trimmed.isBlank()) {
            setStatusMessage("No key page URL configured for this provider.")
            return
        }
        try {
            val intent = android.content.Intent(
                android.content.Intent.ACTION_VIEW,
                android.net.Uri.parse(trimmed)
            ).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            val chooser = android.content.Intent.createChooser(intent, "Open in browser")
                .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            appContext.startActivity(chooser)
        } catch (e: Exception) {
            setStatusMessage("Could not open browser. Copy this link: $trimmed")
        }
    }

    fun refreshQueueState() {
        viewModelScope.launch {
            _uiState.update {
                it.copy(queueSnapshot = container.queueManager.snapshot())
            }
        }
    }

    fun importPastedText(importName: String, pastedText: String) {
        viewModelScope.launch {
            if (pastedText.isBlank()) {
                setStatusMessage("Paste tracks before parsing")
                return@launch
            }
            val result = withContext(Dispatchers.IO) {
                val batchId = container.libraryImporter.importPastedText(importName, pastedText)
                val tracks = container.localLibraryRepository.importedTracksByBatchSnapshot(batchId)
                val counts = container.localLibraryRepository.libraryCountsSnapshot()
                val importBatches = container.localLibraryRepository.importBatchesSnapshot()
                Triple(batchId, tracks, counts to importBatches)
            }
            val (batchId, tracks, pair) = result
            val (counts, importBatches) = pair
            _uiState.update {
                it.copy(
                    activeImportBatchId = batchId,
                    activeImportedTracks = tracks,
                    localLibraryCounts = counts,
                    importBatches = importBatches,
                    statusMessage = "Parsed ${tracks.size} imported track(s)"
                )
            }
        }
    }

    fun removeImportedTrack(trackId: Long) {
        viewModelScope.launch {
            val batchId = _uiState.value.activeImportBatchId
            withContext(Dispatchers.IO) {
                container.localLibraryRepository.removeImportedTrack(trackId)
            }
            val updatedTracks = if (batchId != null) {
                withContext(Dispatchers.IO) { container.localLibraryRepository.importedTracksByBatchSnapshot(batchId) }
            } else {
                emptyList()
            }
            _uiState.update {
                it.copy(
                    activeImportedTracks = updatedTracks,
                    statusMessage = "Removed import row"
                )
            }
        }
    }

    fun saveMatchedImportTracks() {
        viewModelScope.launch {
            val batchId = _uiState.value.activeImportBatchId ?: run {
                setStatusMessage("No import batch selected")
                return@launch
            }
            val rows = withContext(Dispatchers.IO) {
                container.localLibraryRepository.importedTracksByBatchSnapshot(batchId)
                    .filter { it.matchStatus == ImportMatchStatus.MATCHED }
            }
            val result = withContext(Dispatchers.IO) { saveImportedRowsToLibrary(rows, allowMetadataFallback = false) }
            val counts = withContext(Dispatchers.IO) { container.localLibraryRepository.libraryCountsSnapshot() }
            val importedTracks = withContext(Dispatchers.IO) { container.localLibraryRepository.importedTracksByBatchSnapshot(batchId) }
            _uiState.update {
                it.copy(
                    localLibraryCounts = counts,
                    activeImportedTracks = importedTracks,
                    statusMessage = "Saved ${result.savedPlayableCount} playable track(s), skipped ${result.skippedDuplicates} duplicate(s), unresolved ${result.unresolvedCount}"
                )
            }
            refreshAll()
        }
    }

    fun saveAllImportMetadata() {
        viewModelScope.launch {
            val batchId = _uiState.value.activeImportBatchId ?: run {
                setStatusMessage("No import batch selected")
                return@launch
            }
            val rows = withContext(Dispatchers.IO) {
                container.localLibraryRepository.importedTracksByBatchSnapshot(batchId)
                    .filter { !(it.userEditedTitle ?: it.parsedTitle).isNullOrBlank() }
            }
            val result = withContext(Dispatchers.IO) { saveImportedRowsToLibrary(rows, allowMetadataFallback = true) }
            val localSongs = withContext(Dispatchers.IO) { container.localLibraryRepository.allSongsSnapshot() }
            val localCounts = withContext(Dispatchers.IO) { container.localLibraryRepository.libraryCountsSnapshot() }
            val localBatches = withContext(Dispatchers.IO) { container.localLibraryRepository.importBatchesSnapshot() }
            val localImportedTracks = withContext(Dispatchers.IO) { container.localLibraryRepository.importedTracksByBatchSnapshot(batchId) }
            _uiState.update {
                it.copy(
                    localSongs = localSongs,
                    localLibraryCounts = localCounts,
                    importBatches = localBatches,
                    activeImportedTracks = localImportedTracks,
                    statusMessage = "Saved ${result.savedPlayableCount} playable and ${result.savedMetadataCount} metadata track(s), skipped ${result.skippedDuplicates}, unresolved ${result.unresolvedCount}"
                )
            }
            refreshAll()
        }
    }

    private suspend fun saveImportedRowsToLibrary(
        rows: List<ImportedTrackEntity>,
        allowMetadataFallback: Boolean
    ): ImportedRowsSaveResult {
        container.registerConfiguredProviders()
        val existingSongs = container.localLibraryRepository.allSongsSnapshot().toMutableList()
        val updatedRows = mutableListOf<ImportedTrackEntity>()
        var savedPlayable = 0
        var savedMetadata = 0
        var skippedDuplicates = 0
        var unresolved = 0

        rows.forEach { row ->
            val title = (row.userEditedTitle ?: row.parsedTitle)?.trim().orEmpty()
            val artist = (row.userEditedArtist ?: row.parsedArtist)?.trim().orEmpty().ifBlank { "Unknown Artist" }
            if (title.isBlank()) {
                unresolved += 1
                updatedRows += row.copy(
                    matchStatus = ImportMatchStatus.NOT_FOUND,
                    playabilityStatus = PlayabilityStatus.NOT_FOUND,
                    matchReason = "Missing title",
                    updatedAt = System.currentTimeMillis()
                )
                return@forEach
            }

            val resolved = resolveImportedRowToPlayableSource(row, title, artist)
            val existing = existingSongs.firstOrNull {
                it.title.equals(title, ignoreCase = true) && it.artist.equals(artist, ignoreCase = true)
            }

            if (resolved != null) {
                val localSong = (existing ?: LocalSongEntity(
                    title = resolved.title,
                    artist = resolved.artist,
                    album = resolved.album,
                    artworkUrl = resolved.artworkUrl,
                    isrc = resolved.isrc,
                    durationMs = resolved.durationMs,
                    sourceType = SourceType.ADDON,
                    importSource = "Tracklist Import"
                )).copy(
                    title = existing?.title ?: resolved.title,
                    artist = existing?.artist ?: resolved.artist,
                    album = existing?.album ?: resolved.album,
                    artworkUrl = existing?.artworkUrl ?: resolved.artworkUrl,
                    isrc = existing?.isrc ?: resolved.isrc,
                    durationMs = existing?.durationMs ?: resolved.durationMs,
                    sourceType = SourceType.ADDON,
                    streamUrl = existing?.streamUrl?.takeIf { it.isNotBlank() } ?: resolved.streamUrl,
                    quality = existing?.quality ?: resolved.qualityLabel ?: "${resolved.bitrateKbps} kbps",
                    importSource = existing?.importSource ?: "Tracklist Import",
                    externalIdsJson = existing?.externalIdsJson ?: buildImportExternalIdsJson(resolved),
                    updatedAt = System.currentTimeMillis()
                )
                val savedIds = container.localLibraryRepository.saveSongs(listOf(localSong))
                val localId = existing?.id ?: savedIds.firstOrNull() ?: 0L
                if (existing == null) {
                    existingSongs += localSong.copy(id = localId)
                    savedPlayable += 1
                } else {
                    skippedDuplicates += 1
                }

                container.trackRepository.addTrackSource(
                    title = resolved.title,
                    artist = resolved.artist,
                    album = resolved.album,
                    coverArtUrl = resolved.artworkUrl,
                    sourceType = SourceType.ADDON,
                    streamUrl = resolved.streamUrl,
                    bitrate = resolved.bitrateKbps,
                    localLibraryId = localId.takeIf { it > 0L },
                    isrc = resolved.isrc,
                    durationMs = resolved.durationMs,
                    externalProviderId = resolved.providerId,
                    externalTrackId = resolved.externalTrackId,
                    expiresAtMs = resolved.expiresAtMs
                )

                updatedRows += row.copy(
                    parsedTitle = row.parsedTitle ?: resolved.title,
                    parsedArtist = row.parsedArtist ?: resolved.artist,
                    parsedAlbum = row.parsedAlbum ?: resolved.album,
                    sourceUrl = resolved.streamUrl,
                    matchStatus = ImportMatchStatus.MATCHED,
                    playabilityStatus = PlayabilityStatus.PLAYABLE,
                    matchConfidence = MatchConfidence.HIGH,
                    matchReason = "Resolved through ${resolved.providerId ?: "VANTA source"}",
                    friendlySourceLabel = resolved.qualityLabel ?: "${resolved.bitrateKbps} kbps",
                    confidenceScore = 0.9f,
                    updatedAt = System.currentTimeMillis()
                )
            } else if (allowMetadataFallback) {
                if (existing == null) {
                    val metadataSong = container.metadataResolver.resolveAndEnrich(
                        LocalSongEntity(
                            title = title,
                            artist = artist,
                            album = row.parsedAlbum,
                            sourceType = SourceType.ADDON,
                            importSource = "Tracklist Import",
                            externalIdsJson = buildImportRowExternalIdsJson(row)
                        )
                    )
                    val savedIds = container.localLibraryRepository.saveSongs(listOf(metadataSong))
                    existingSongs += metadataSong.copy(id = savedIds.firstOrNull() ?: 0L)
                    savedMetadata += 1
                } else {
                    skippedDuplicates += 1
                }
                updatedRows += row.copy(
                    matchStatus = ImportMatchStatus.MATCHED,
                    playabilityStatus = PlayabilityStatus.METADATA_ONLY,
                    matchConfidence = if (row.matchConfidence == MatchConfidence.NONE) MatchConfidence.MEDIUM else row.matchConfidence,
                    matchReason = row.matchReason ?: "Saved as metadata; no playable source resolved yet",
                    friendlySourceLabel = row.friendlySourceLabel ?: "Metadata",
                    confidenceScore = if (row.confidenceScore <= 0f) 0.68f else row.confidenceScore,
                    updatedAt = System.currentTimeMillis()
                )
            } else {
                unresolved += 1
                updatedRows += row.copy(
                    playabilityStatus = PlayabilityStatus.NOT_FOUND,
                    matchStatus = ImportMatchStatus.NOT_FOUND,
                    matchReason = "No playable source resolved",
                    updatedAt = System.currentTimeMillis()
                )
            }
        }

        if (updatedRows.isNotEmpty()) {
            container.localLibraryRepository.updateImportedTracks(updatedRows)
        }

        return ImportedRowsSaveResult(
            requestedCount = rows.size,
            savedPlayableCount = savedPlayable,
            savedMetadataCount = savedMetadata,
            skippedDuplicates = skippedDuplicates,
            unresolvedCount = unresolved
        )
    }

    private suspend fun resolveImportedRowToPlayableSource(
        row: ImportedTrackEntity,
        title: String,
        artist: String
    ): ResolvedImportSource? {
        val existingStream = row.sourceUrl?.takeIf { isPlayableImportStream(it) }
        if (existingStream != null) {
            return ResolvedImportSource(
                title = title,
                artist = artist,
                album = row.parsedAlbum,
                artworkUrl = null,
                isrc = null,
                durationMs = null,
                streamUrl = existingStream,
                bitrateKbps = estimateBitrateFromQuality(row.friendlySourceLabel),
                providerId = null,
                externalTrackId = row.matchedSongId?.toString(),
                expiresAtMs = null,
                qualityLabel = row.friendlySourceLabel
            )
        }

        val query = listOf(title, artist.takeIf { it != "Unknown Artist" }).filterNotNull().joinToString(" ")
        val candidates = container.sourceRegistry.searchAll(query, timeoutMs = 12_000L)
            .asSequence()
            .filter { it.status.canResolveStream() }
            .filter { it.isLikelyMusicTrack() }
            .sortedByDescending { scoreImportedSourceCandidate(title, artist, it) }
            .take(8)
            .toList()

        for (candidate in candidates) {
            val resolved = container.sourceRegistry.resolveStream(candidate.providerId, candidate.id, timeoutMs = 12_000L)
                ?: continue
            if (resolved.streamUrl.isBlank()) continue
            if (resolved.streamUrl.contains("soundhelix", ignoreCase = true)) continue
            return ResolvedImportSource(
                title = candidate.title.ifBlank { title },
                artist = candidate.artist.ifBlank { artist },
                album = candidate.album ?: row.parsedAlbum,
                artworkUrl = candidate.artworkUrl,
                isrc = candidate.isrc,
                durationMs = candidate.durationMs,
                streamUrl = resolved.streamUrl,
                bitrateKbps = resolved.bitrateKbps,
                providerId = candidate.providerId,
                externalTrackId = candidate.id,
                expiresAtMs = resolved.expiresAt,
                qualityLabel = resolved.qualityLabel ?: candidate.qualityLabel
            )
        }
        return null
    }

    private fun scoreImportedSourceCandidate(
        title: String,
        artist: String,
        candidate: SourceSearchResult
    ): Int = com.audiophile.musicplayer.data.source.SourceIdentityGate.evaluateSearchResult(
        selected = com.audiophile.musicplayer.data.source.SelectedRecordingIdentity(
            title = title,
            artist = artist,
            userQuery = _uiState.value.query.takeIf { it.isNotBlank() }
        ),
        candidate = candidate
    ).score

    private fun isPlayableImportStream(value: String): Boolean {
        val trimmed = value.trim()
        return trimmed.startsWith("content://", ignoreCase = true) ||
            trimmed.startsWith("file://", ignoreCase = true) ||
            ((trimmed.startsWith("http://", ignoreCase = true) || trimmed.startsWith("https://", ignoreCase = true)) &&
                !trimmed.contains("music.apple.com", ignoreCase = true) &&
                !trimmed.contains("itunes.apple.com", ignoreCase = true) &&
                !trimmed.contains("open.spotify.com", ignoreCase = true) &&
                !trimmed.contains("spotify.link", ignoreCase = true) &&
                !trimmed.contains("tidal.com", ignoreCase = true) &&
                !trimmed.contains("qobuz.com", ignoreCase = true) &&
                !trimmed.contains("deezer.com", ignoreCase = true) &&
                !trimmed.contains("music.amazon.", ignoreCase = true) &&
                !trimmed.contains("amazon.com/music", ignoreCase = true) &&
                !trimmed.contains("youtube.com", ignoreCase = true) &&
                !trimmed.contains("youtu.be", ignoreCase = true) &&
                !trimmed.contains("song.link", ignoreCase = true) &&
                !trimmed.contains("album.link", ignoreCase = true) &&
                !trimmed.contains("odesli.co", ignoreCase = true))
    }

    private fun buildImportExternalIdsJson(resolved: ResolvedImportSource): String? {
        val provider = resolved.providerId?.takeIf { it.isNotBlank() } ?: return null
        return JsonObject().apply {
            addProperty("providerId", provider)
            resolved.externalTrackId?.takeIf { it.isNotBlank() }?.let { addProperty("trackId", it) }
        }.toString()
    }

    private fun buildImportRowExternalIdsJson(row: ImportedTrackEntity): String? {
        val sourceUrl = row.sourceUrl
            ?.takeIf { it.isNotBlank() && !isPlayableImportStream(it) }
            ?: return null
        return JsonObject().apply {
            addProperty("sourceUrl", sourceUrl)
            row.friendlySourceLabel?.takeIf { it.isNotBlank() }?.let { addProperty("sourceLabel", it) }
            row.matchedSongId?.let { addProperty("matchedSongId", it) }
        }.toString()
    }

    fun resolveCurrentImportAgain() {
        viewModelScope.launch {
            val batchId = _uiState.value.activeImportBatchId ?: run {
                setStatusMessage("No import batch selected")
                return@launch
            }
            val tracks = withContext(Dispatchers.IO) { container.libraryImporter.rerunMatchingForBatch(batchId) }
            _uiState.update {
                it.copy(
                    activeImportedTracks = tracks,
                    statusMessage = "Re-ran matching for ${tracks.size} import row(s)"
                )
            }
        }
    }

    fun toggleFavoriteForNowPlaying(onComplete: (() -> Unit)? = null) {
        viewModelScope.launch {
            try {
            val nowPlaying = container.nowPlayingStateStore.load() ?: return@launch
            val trackTitle = nowPlaying.title ?: "Unknown"
            Log.d("VANTA_LIBRARY_ACTION", "tap displayedLiked=${nowPlaying.isFavorite}")
            Log.d("VANTA_LIBRARY_ACTION", "before persistedLiked=${nowPlaying.isFavorite}")
            if (nowPlaying.artist != null) {
                withContext(Dispatchers.IO) {
                    container.aiDjRecommendationEngine.recordFavorite(
                        trackId = nowPlaying.trackId?.toLongOrNull() ?: 0L,
                        artist = nowPlaying.artist ?: "",
                        genre = null
                    )
                }
            }
            val title = nowPlaying.title ?: run { setStatusMessage("Nothing playing"); return@launch }
            val artist = nowPlaying.artist ?: "Unknown Artist"
            val result = withContext(Dispatchers.IO) {
                val existing = container.localLibraryRepository.allSongsSnapshot().find {
                    it.title.equals(title, ignoreCase = true) && it.artist.equals(artist, ignoreCase = true)
                }
                val localLibraryId: Long
                val newFavorite: Boolean
                if (existing != null) {
                    localLibraryId = existing.id
                    container.localLibraryRepository.toggleFavorite(localLibraryId)
                    val updated = container.localLibraryRepository.songById(localLibraryId)
                    newFavorite = updated?.isFavorite ?: !existing.isFavorite
                    container.nowPlayingStateStore.save(
                        nowPlaying.copy(isFavorite = newFavorite)
                    )
                } else {
                    val savedIds = container.localLibraryRepository.saveSongs(
                        listOf(
                            LocalSongEntity(
                                title = title,
                                artist = artist,
                                album = nowPlaying.album,
                                artworkUrl = nowPlaying.artworkUrl,
                                durationMs = nowPlaying.durationMs.takeIf { it > 0 },
                                isFavorite = true
                            )
                        )
                    )
                    if (savedIds.isNotEmpty()) {
                        localLibraryId = savedIds.first()
                        newFavorite = true
                        container.nowPlayingStateStore.save(
                            nowPlaying.copy(isFavorite = newFavorite)
                        )
                    } else {
                        localLibraryId = -1L
                        newFavorite = false
                    }
                }
                newFavorite
            }
            refreshAll()
            val newFavorite = result
            setStatusMessage(if (newFavorite) "Liked" else "Unliked")
            Log.d("VANTA_LIBRARY_ACTION", "after persistedLiked=${newFavorite}")
            Log.d("VANTA_SNACKBAR", "message=${if (newFavorite) "Liked" else "Unliked"}")
            val msg = if (newFavorite) "Liked" else "Unliked"
            _uiState.update {
                it.copy(statusMessage = msg)
            }
            val snapshots = withContext(Dispatchers.IO) {
                container.localLibraryRepository.allSongsSnapshot() to container.localLibraryRepository.libraryCountsSnapshot()
            }
            _uiState.update {
                it.copy(
                    localSongs = snapshots.first,
                    localLibraryCounts = snapshots.second,
                    statusMessage = msg
                )
            }
            Log.d("VANTA_LIBRARY_ACTION", "after persistedLiked=${newFavorite}")
            Log.d("VANTA_SNACKBAR", "message=${msg}")
            } finally { onComplete?.invoke() }
        }
    }

    fun toggleFavoriteForSong(songId: Long) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { container.localLibraryRepository.toggleFavorite(songId) }
            val snapshots = withContext(Dispatchers.IO) {
                container.localLibraryRepository.allSongsSnapshot() to container.localLibraryRepository.libraryCountsSnapshot()
            }
            _uiState.update {
                it.copy(
                    localSongs = snapshots.first,
                    localLibraryCounts = snapshots.second,
                    statusMessage = "Favorite updated"
                )
            }
        }
    }

    suspend fun currentResolverSummary(): String = withContext(Dispatchers.IO) {
        val providers = container.resolutionCacheRepository.getEnabledProviders()
        when {
            providers.isEmpty() -> "No resolver instances enabled"
            else -> providers.joinToString { it.displayName }
        }
    }

    private fun loadResolverConfigForm(): ResolverConfigForm {
        val activeProvider = container.resolverConfigStore.getLlmProvider()
        return ResolverConfigForm(
            torBoxBaseUrl = container.resolverConfigStore.getTorBoxResolverBaseUrl().orEmpty(),
            torBoxApiToken = container.resolverConfigStore.getTorBoxApiToken().orEmpty(),
            realDebridApiToken = container.resolverConfigStore.getRealDebridApiToken().orEmpty(),
            communityInstancesText = container.resolverConfigStore.getCommunityInstances().joinToString("\n"),
            llmProviderName = activeProvider?.name.orEmpty(),
            llmApiKey = activeProvider?.let { container.resolverConfigStore.getLlmApiKey(it) }.orEmpty(),
            llmProvidersWithKeys = AiProvider.entries
                .filter { container.resolverConfigStore.getLlmApiKey(it) != null }
                .map { it.name }
                .toSet(),
            llmVerifiedProviders = AiProvider.entries
                .filter { container.resolverConfigStore.isLlmVerified(it) }
                .map { it.name }
                .toSet(),
            pulseVoiceRelayUrl = container.resolverConfigStore.getPulseVoiceRelayUrl().orEmpty(),
            pulseVoiceRelayToken = container.resolverConfigStore.getPulseVoiceRelayToken().orEmpty(),
            pulseVoiceEngine = container.resolverConfigStore.getPulseVoiceEngine(),
            appleMusicDeveloperToken = container.resolverConfigStore.getAppleMusicDeveloperToken().orEmpty(),
            appleMusicStorefront = container.resolverConfigStore.getAppleMusicStorefront(),
            lastFmApiKey = container.resolverConfigStore.getLastFmApiKey().orEmpty(),
            lastFmApiSecret = container.resolverConfigStore.getLastFmApiSecret().orEmpty(),
            lastFmUsername = container.resolverConfigStore.getLastFmUsername().orEmpty(),
            lastFmSessionKey = container.resolverConfigStore.getLastFmSessionKey().orEmpty()
        )
    }

    private fun displayedTracks(): List<UnifiedTrackWithSources> {
        val visible = _uiState.value.visibleTracks
        return if (visible.isNotEmpty()) visible else _uiState.value.library
    }

    private suspend fun materializePlayableRoomSongs(): List<UnifiedTrackWithSources> {
        val songs = container.localLibraryRepository.allSongsSnapshot()
        materializeSongsForPlayback(songs)
        val tracksByLocalId = container.trackRepository.getAllTracks()
            .asSequence()
            .mapNotNull { track -> track.track.localLibraryId?.let { id -> id to track } }
            .toMap()
        return songs.mapNotNull { song ->
            song.toPlayableQueueItem()?.let { tracksByLocalId[song.id] ?: it }
        }
    }

    private suspend fun materializeSongsForPlayback(songs: List<LocalSongEntity>): Int {
        val existingLocalIds = container.trackRepository.getAllTracks()
            .mapNotNull { it.track.localLibraryId }
            .toSet()
        var materialized = 0
        songs.forEachIndexed { index, song ->
            if (song.id in existingLocalIds) {
                return@forEachIndexed
            }
            val streamUrl = song.streamUrl?.takeIf { it.isNotBlank() } ?: return@forEachIndexed
            container.trackRepository.addTrackSource(
                title = song.title,
                artist = song.artist,
                album = song.album,
                coverArtUrl = song.artworkUrl,
                sourceType = song.sourceType,
                streamUrl = streamUrl,
                bitrate = estimateBitrateFromQuality(song.quality),
                localLibraryId = song.id,
                genre = song.genres.firstOrNull(),
                isrc = song.isrc,
                durationMs = song.durationMs
            )
            materialized += 1
            if (index > 0 && index % 50 == 0) {
                kotlinx.coroutines.yield()
            }
        }
        return materialized
    }

    private fun mergeLibraryTracks(
        existing: List<UnifiedTrackWithSources>,
        roomPlayableTracks: List<UnifiedTrackWithSources>
    ): List<UnifiedTrackWithSources> {
        return (existing + roomPlayableTracks)
            .distinctBy { it.track.localLibraryId?.let { id -> "local:$id" } ?: "track:${it.track.trackId}" }
    }

    private fun estimateBitrateFromQuality(quality: String?): Int {
        val normalized = quality.orEmpty().lowercase()
        return Regex("""\b(\d{2,4})\s*kbps\b""")
            .find(normalized)?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 0
    }

    private suspend fun loadTorBoxCandidates(): List<TorBoxImportCandidate> {
        val token = container.resolverConfigStore.getTorBoxApiToken().orEmpty()
        if (token.isBlank()) return emptyList()
        return container.torBoxRepository.importableAudioFiles(token, limit = 50)
    }

    fun setStatusMessage(message: String) {
        _uiState.update { it.copy(statusMessage = message) }
    }

    private fun parsePayloadJson(payloadJson: String?): JsonObject? {
        if (payloadJson.isNullOrBlank()) return null
        return runCatching { JsonParser.parseString(payloadJson).asJsonObject }.getOrNull()
    }

    private fun splitArtistAndTitle(fileName: String, fallbackAlbum: String): Pair<String, String> {
        val withoutExtension = fileName.substringBeforeLast('.').trim()
        val parts = withoutExtension.split(" - ", limit = 2)
        return if (parts.size == 2) {
            parts[0].trim().ifBlank { "Unknown Artist" } to parts[1].trim().ifBlank { withoutExtension }
        } else {
            "Unknown Artist" to withoutExtension.ifBlank { fallbackAlbum }
        }
    }
    private fun estimateBitrate(fileName: String, mimeType: String?): Int {
        return 0
    }

    private suspend fun resolveCanonicalTrack(result: CanonicalTrack): UnifiedTrackWithSources? {
        val providerId = result.sourceProviderId?.trim().orEmpty()
        val externalId = result.externalTrackId?.trim().orEmpty()
        if (providerId.isNotBlank() && externalId.isNotBlank()) {
            val direct = resolveCanonicalTrackForPlayback(result)
            if (direct != null) {
                val (playable, resolvedStream) = direct
                if (resolvedStream.streamUrl.isNotBlank() &&
                    !resolvedStream.streamUrl.contains("soundhelix", ignoreCase = true)
                ) {
                    val trackId = container.trackRepository.addTrackSource(
                        title = result.title,
                        artist = result.artist,
                        album = result.album,
                        coverArtUrl = result.artworkUrl,
                        sourceType = com.audiophile.musicplayer.data.source.CloudLibraryHelpers.sourceTypeForProvider(playable.providerId),
                        streamUrl = resolvedStream.streamUrl,
                        bitrate = resolvedStream.bitrateKbps,
                        genre = result.genre,
                        isrc = result.isrc ?: playable.isrc,
                        durationMs = result.durationMs ?: playable.durationMs,
                        externalProviderId = playable.providerId,
                        externalTrackId = playable.id,
                        expiresAtMs = resolvedStream.expiresAt
                    )
                    return container.trackRepository.getTrackWithSources(trackId)
                }
            }
        }

        val searchQuery = "${result.title} ${result.artist}".trim()
        val identity = com.audiophile.musicplayer.data.source.SelectedRecordingIdentity(
            title = result.title,
            artist = result.artist,
            durationMs = result.durationMs,
            isrc = result.isrc,
            preferredProviderId = providerId.takeIf { it.isNotBlank() },
            preferredExternalTrackId = externalId.takeIf { it.isNotBlank() },
            userQuery = _uiState.value.query.takeIf { it.isNotBlank() }
        )
        val sourceResults = com.audiophile.musicplayer.data.source.SourceCandidateRanker.rankSearchResults(
            identity,
            container.sourceRegistry.searchAll(searchQuery).filter { sourceCandidateMatches(result, it) }
        )
        var resolvedStream: com.audiophile.musicplayer.data.source.ResolvedStream? = null
        var playable: SourceSearchResult? = null
        val triedProviders = mutableSetOf<String>()
        for (candidate in sourceResults) {
            if (!candidate.status.canResolveStream()) continue
            if (!triedProviders.add(candidate.providerId)) continue
            val stream = container.sourceRegistry.resolveStream(candidate.providerId, candidate.id)
            if (stream != null && stream.streamUrl.isNotBlank()) {
                playable = candidate
                resolvedStream = stream
                break
            }
        }
        if (playable == null || resolvedStream == null || resolvedStream.streamUrl.isBlank()) {
            return null
        }
        if (resolvedStream.streamUrl.contains("soundhelix", ignoreCase = true)) {
            return null
        }
        val trackId = container.trackRepository.addTrackSource(
            title = result.title,
            artist = result.artist,
            album = result.album,
            coverArtUrl = result.artworkUrl,
            sourceType = SourceType.ADDON,
            streamUrl = resolvedStream.streamUrl,
            bitrate = resolvedStream.bitrateKbps,
            genre = result.genre,
            isrc = result.isrc ?: playable.isrc,
            durationMs = result.durationMs ?: playable.durationMs,
            externalProviderId = playable.providerId,
            externalTrackId = playable.id,
            expiresAtMs = resolvedStream.expiresAt
        )
        return container.trackRepository.getTrackWithSources(trackId)
    }

    fun handleAction(action: VantaActionSheetAction, context: VantaActionContext) {
        viewModelScope.launch {
            val contextName = when (context) {
                is VantaActionContext.Track -> "track"
                is VantaActionContext.Album -> "album"
                is VantaActionContext.Artist -> "artist"
                is VantaActionContext.Playlist -> "playlist"
                is VantaActionContext.QueueItem -> "queueitem"
                is VantaActionContext.SearchResult -> "searchresult"
                is VantaActionContext.Station -> "station"
            }
            
            val track: UnifiedTrackWithSources? = when (context) {
                is VantaActionContext.Track -> withContext(Dispatchers.IO) { container.trackRepository.getTrackWithSources(context.trackId.toLongOrNull() ?: 0L) }
                is VantaActionContext.QueueItem -> context.track
                is VantaActionContext.SearchResult -> {
                    val ct = CanonicalTrack(
                        title = context.title,
                        artist = context.artist,
                        album = context.album,
                        artworkUrl = context.artworkUrl,
                        sourceProviderId = context.sourceProviderId,
                        externalTrackId = context.externalTrackId,
                        explicit = context.explicit
                    )
                    withContext(Dispatchers.IO) { resolveCanonicalTrack(ct) }
                }
                else -> null
            }

            Log.d("VANTA_ACTION_HANDLE_START", "action='$action' context='$contextName' trackId=${track?.track?.trackId}")

            val trackIdString = track?.track?.trackId?.toString() ?: ""

            when (action) {
                VantaActionSheetAction.PLAY -> {
                    if (track != null) {
                        playTrack(track)
                        Log.d("VANTA_ACTION_HANDLE", "action='PLAY' trackId='$trackIdString' result='success'")
                        setStatusMessage("Playing ${track.track.title}")
                    } else if (context is VantaActionContext.Album) {
                        val albumTracks = uiState.value.library.filter { it.track.albumName.equals(context.albumName, ignoreCase = true) }
                        if (albumTracks.isNotEmpty()) {
                            playQueue(albumTracks, 0)
                            Log.d("VANTA_ACTION_HANDLE", "action='PLAY' album='${context.albumName}' result='success'")
                        }
                    } else if (context is VantaActionContext.Playlist) {
                        val playlistTracks = withContext(Dispatchers.IO) {
                            container.trackRepository.getOrderedPlaylistTracks(context.playlistId)
                        }
                        val mapped = withContext(Dispatchers.IO) {
                            playlistTracks.mapNotNull { ut -> container.trackRepository.getTrackWithSources(ut.trackId) }
                        }
                        if (mapped.isNotEmpty()) {
                            playQueue(mapped, 0)
                            Log.d("VANTA_ACTION_HANDLE", "action='PLAY' playlist='${context.name}' result='success'")
                        }
                    }
                }
                VantaActionSheetAction.PLAY_NEXT -> {
                    if (track != null) {
                        playNext(track)
                        Log.d("VANTA_ACTION_HANDLE", "action='PLAY_NEXT' trackId='$trackIdString' result='success'")
                        setStatusMessage("Play next: ${track.track.title}")
                    }
                }
                VantaActionSheetAction.ADD_TO_QUEUE -> {
                    if (track != null) {
                        addToQueue(track)
                        Log.d("VANTA_ACTION_HANDLE", "action='ADD_TO_QUEUE' trackId='$trackIdString' result='success'")
                        setStatusMessage("Added to queue: ${track.track.title}")
                    }
                }
                VantaActionSheetAction.START_RADIO -> {
                    if (track != null) {
                        Log.d("VANTA_ACTION_HANDLE", "action='START_RADIO' seed='${track.track.title}' result='success'")
                        playSongRadio(
                            title = track.track.title,
                            artist = track.track.artist,
                            album = track.track.albumName,
                            genre = track.track.genre
                        )
                    } else if (context is VantaActionContext.Artist) {
                        Log.d("VANTA_ACTION_HANDLE", "action='START_RADIO' artist='${context.artistName}' result='success'")
                        playArtistRadio(context.artistName)
                    }
                }
                VantaActionSheetAction.ADD_TO_LIBRARY -> {
                    val title = track?.track?.title ?: when (context) {
                        is VantaActionContext.Track -> context.title
                        is VantaActionContext.SearchResult -> context.title
                        else -> ""
                    }
                    val artist = track?.track?.artist ?: when (context) {
                        is VantaActionContext.Track -> context.artist
                        is VantaActionContext.SearchResult -> context.artist
                        else -> ""
                    }
                    if (title.isNotBlank()) {
                        val existing = withContext(Dispatchers.IO) {
                            container.localLibraryRepository.allSongsSnapshot().any {
                                it.title.equals(title, ignoreCase = true) && it.artist.equals(artist, ignoreCase = true)
                            }
                        }
                        if (!existing) {
                            withContext(Dispatchers.IO) {
                                container.localLibraryRepository.saveSongs(
                                    listOf(
                                        com.audiophile.musicplayer.data.local.entities.LocalSongEntity(
                                            title = title,
                                            artist = artist,
                                            album = track?.track?.albumName ?: (context as? VantaActionContext.Track)?.album ?: (context as? VantaActionContext.SearchResult)?.album,
                                            artworkUrl = track?.track?.coverArtUrl ?: (context as? VantaActionContext.Track)?.artworkUrl ?: (context as? VantaActionContext.SearchResult)?.artworkUrl,
                                            isFavorite = false
                                        )
                                    )
                                )
                            }
                            refreshAll()
                            setStatusMessage("Saved to library")
                            Log.d("VANTA_ACTION_HANDLE", "action='ADD_TO_LIBRARY' trackId='$trackIdString' result='success'")
                        } else {
                            Log.d("VANTA_ACTION_HANDLE", "action='ADD_TO_LIBRARY' trackId='$trackIdString' result='blocked' reason='already_exists'")
                        }
                    }
                }
                VantaActionSheetAction.REMOVE_FROM_LIBRARY -> {
                    val title = track?.track?.title ?: when (context) {
                        is VantaActionContext.Track -> context.title
                        is VantaActionContext.SearchResult -> context.title
                        else -> ""
                    }
                    val artist = track?.track?.artist ?: when (context) {
                        is VantaActionContext.Track -> context.artist
                        is VantaActionContext.SearchResult -> context.artist
                        else -> ""
                    }
                    if (title.isNotBlank()) {
                        val matching = withContext(Dispatchers.IO) {
                            container.localLibraryRepository.allSongsSnapshot().find {
                                it.title.equals(title, ignoreCase = true) && it.artist.equals(artist, ignoreCase = true)
                            }
                        }
                        if (matching != null) {
                            withContext(Dispatchers.IO) { container.localLibraryRepository.deleteSong(matching.id) }
                            refreshAll()
                            setStatusMessage("Removed from library")
                            Log.d("VANTA_ACTION_HANDLE", "action='REMOVE_FROM_LIBRARY' trackId='$trackIdString' result='success'")
                        } else {
                            Log.d("VANTA_ACTION_HANDLE", "action='REMOVE_FROM_LIBRARY' trackId='$trackIdString' result='failed' reason='not_found'")
                        }
                    }
                }
                VantaActionSheetAction.FAVORITE -> {
                    val title = track?.track?.title ?: when (context) {
                        is VantaActionContext.Track -> context.title
                        is VantaActionContext.SearchResult -> context.title
                        else -> ""
                    }
                    val artist = track?.track?.artist ?: when (context) {
                        is VantaActionContext.Track -> context.artist
                        is VantaActionContext.SearchResult -> context.artist
                        else -> ""
                    }
                    if (title.isNotBlank()) {
                        val matching = withContext(Dispatchers.IO) {
                            container.localLibraryRepository.allSongsSnapshot().find {
                                it.title.equals(title, ignoreCase = true) && it.artist.equals(artist, ignoreCase = true)
                            }
                        }
                        if (matching != null) {
                            if (!matching.isFavorite) {
                                withContext(Dispatchers.IO) { container.localLibraryRepository.toggleFavorite(matching.id) }
                                refreshAll()
                            }
                            setStatusMessage("Added to favorites")
                            Log.d("VANTA_ACTION_HANDLE", "action='FAVORITE' trackId='$trackIdString' result='success'")
                        } else {
                            withContext(Dispatchers.IO) {
                                container.localLibraryRepository.saveSongs(
                                    listOf(
                                        com.audiophile.musicplayer.data.local.entities.LocalSongEntity(
                                            title = title,
                                            artist = artist,
                                            album = track?.track?.albumName ?: (context as? VantaActionContext.Track)?.album ?: (context as? VantaActionContext.SearchResult)?.album,
                                            artworkUrl = track?.track?.coverArtUrl ?: (context as? VantaActionContext.Track)?.artworkUrl ?: (context as? VantaActionContext.SearchResult)?.artworkUrl,
                                            isFavorite = true
                                        )
                                    )
                                )
                            }
                            refreshAll()
                            setStatusMessage("Added to favorites")
                            Log.d("VANTA_ACTION_HANDLE", "action='FAVORITE' trackId='$trackIdString' result='success'")
                        }
                    }
                }
                VantaActionSheetAction.UNFAVORITE -> {
                    val title = track?.track?.title ?: when (context) {
                        is VantaActionContext.Track -> context.title
                        is VantaActionContext.SearchResult -> context.title
                        else -> ""
                    }
                    val artist = track?.track?.artist ?: when (context) {
                        is VantaActionContext.Track -> context.artist
                        is VantaActionContext.SearchResult -> context.artist
                        else -> ""
                    }
                    if (title.isNotBlank()) {
                        val matching = withContext(Dispatchers.IO) {
                            container.localLibraryRepository.allSongsSnapshot().find {
                                it.title.equals(title, ignoreCase = true) && it.artist.equals(artist, ignoreCase = true)
                            }
                        }
                        if (matching != null && matching.isFavorite) {
                            withContext(Dispatchers.IO) { container.localLibraryRepository.toggleFavorite(matching.id) }
                            refreshAll()
                            setStatusMessage("Removed from favorites")
                            Log.d("VANTA_ACTION_HANDLE", "action='UNFAVORITE' trackId='$trackIdString' result='success'")
                        }
                    }
                }
                VantaActionSheetAction.ADD_TO_PLAYLIST -> {
                    Log.d("VANTA_ACTION_HANDLE", "action='ADD_TO_PLAYLIST' trackId='$trackIdString' result='success'")
                }
                VantaActionSheetAction.VIEW_ALBUM -> {
                    Log.d("VANTA_ACTION_HANDLE", "action='VIEW_ALBUM' result='success'")
                }
                VantaActionSheetAction.VIEW_ARTIST -> {
                    Log.d("VANTA_ACTION_HANDLE", "action='VIEW_ARTIST' result='success'")
                }
                VantaActionSheetAction.SHARE_TRACK,
                VantaActionSheetAction.SHARE_ALBUM,
                VantaActionSheetAction.SHARE_ARTIST,
                VantaActionSheetAction.SHARE_PLAYLIST -> {
                    Log.d("VANTA_ACTION_HANDLE", "action='$action' trackId='$trackIdString' result='success'")
                }
                VantaActionSheetAction.DOWNLOAD_LOCAL -> {
                    if (track != null) {
                        downloadTrack(track)
                        Log.d("VANTA_ACTION_HANDLE", "action='DOWNLOAD_LOCAL' trackId='$trackIdString' result='success'")
                    }
                }
                VantaActionSheetAction.SLEEP_TIMER -> {
                    Log.d("VANTA_ACTION_HANDLE", "action='SLEEP_TIMER' result='success'")
                }
                VantaActionSheetAction.REMOVE_FROM_QUEUE -> {
                    if (context is VantaActionContext.QueueItem) {
                        removeUpNext(context.index)
                        Log.d("VANTA_ACTION_HANDLE", "action='REMOVE_FROM_QUEUE' index=${context.index} result='success'")
                    }
                }
                VantaActionSheetAction.MOVE_QUEUE_ITEM -> {
                    if (context is VantaActionContext.QueueItem) {
                        if (context.index > 0) {
                            moveUpNext(context.index, context.index - 1)
                            Log.d("VANTA_ACTION_HANDLE", "action='MOVE_QUEUE_ITEM' from=${context.index} to=${context.index - 1} result='success'")
                        }
                    }
                }
            }
        }
    }
}
class MainViewModelFactory(
    private val appContext: Context,
    private val container: AppContainer
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(MainViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return MainViewModel(appContext, container) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
