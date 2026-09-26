package com.audiophile.musicplayer.ui



import android.content.Context

import android.app.DownloadManager

import android.util.Log

import android.net.Uri

import androidx.lifecycle.ViewModel

import dagger.hilt.android.lifecycle.HiltViewModel

import dagger.hilt.android.qualifiers.ApplicationContext

import javax.inject.Inject

import androidx.lifecycle.viewModelScope
import com.audiophile.musicplayer.AppContainer

import com.audiophile.musicplayer.debug.VantaDiagnosticLog

import com.audiophile.musicplayer.common.AcceptanceTruth

import com.audiophile.musicplayer.common.VantaLogger

import com.audiophile.musicplayer.data.importer.ImportMatchStatus

import com.audiophile.musicplayer.data.voice.PulseVoiceProfile

import com.audiophile.musicplayer.data.local.LibraryCounts

import com.audiophile.musicplayer.data.local.ListeningYearStats

import com.audiophile.musicplayer.data.local.entities.ImportBatchEntity

import com.audiophile.musicplayer.data.local.entities.ImportedTrackEntity

import com.audiophile.musicplayer.data.local.entities.LocalSongEntity

import com.audiophile.musicplayer.data.local.entities.SourceType

import com.audiophile.musicplayer.data.local.entities.UnifiedTrackWithSources

import com.audiophile.musicplayer.data.importer.MatchConfidence

import com.audiophile.musicplayer.data.importer.PlayabilityStatus

import com.audiophile.musicplayer.data.importer.PlatformLinkMetadata

import com.audiophile.musicplayer.data.importer.SoundiizTextParser

import com.audiophile.musicplayer.data.importer.readBoundedText

import com.audiophile.musicplayer.data.local.toPlayableQueueItem

import com.audiophile.musicplayer.data.remote.TorBoxImportCandidate

import com.audiophile.musicplayer.playback.NowPlayingState

import com.audiophile.musicplayer.playback.QueueSnapshot

import com.audiophile.musicplayer.playback.PlaybackQueueBuilder

import com.audiophile.musicplayer.data.metadata.EnhancedMetadata

import com.audiophile.musicplayer.data.source.SourceSearchResult

import com.audiophile.musicplayer.data.source.NewReleaseFeedCache

import com.audiophile.musicplayer.data.source.NewReleaseFeedPolicy

import com.audiophile.musicplayer.data.source.NewReleaseFeedSnapshot

import com.audiophile.musicplayer.data.source.NewReleaseFeedStatus

import com.audiophile.musicplayer.data.source.SearchItemStatus

import com.audiophile.musicplayer.data.source.SelectedRecordingIdentity

import com.audiophile.musicplayer.data.source.SourceCandidateRanker

import com.audiophile.musicplayer.data.source.SourceIdentityGate

import com.audiophile.musicplayer.data.source.canEnterPlaybackFlow

import com.audiophile.musicplayer.data.source.canResolveStream

import com.audiophile.musicplayer.data.source.isConfirmedPlayable

import com.audiophile.musicplayer.data.source.isMetadataOnly

import com.audiophile.musicplayer.data.source.isUnavailable

import com.audiophile.musicplayer.data.source.isLikelyMusicTrack

import com.audiophile.musicplayer.data.source.isPlayableMusicCandidate

import com.audiophile.musicplayer.data.source.isMusicContentAllowed

import com.audiophile.musicplayer.data.source.ContentPurityFilter

import com.audiophile.musicplayer.data.source.sourceValidityStatus

import com.audiophile.musicplayer.data.llm.AiProvider

import com.audiophile.musicplayer.data.source.external.ExternalSourceConfig

import com.audiophile.musicplayer.data.source.external.ExternalSourceProvider

import com.audiophile.musicplayer.data.dj.JukeboxCatalog

import com.audiophile.musicplayer.data.dj.JukeboxTrackEligibility

import com.audiophile.musicplayer.data.dj.StreamingSeedParams

import com.audiophile.musicplayer.data.dj.toStreamingSeed

import com.audiophile.musicplayer.radio.toStreamingSeedParams

import com.audiophile.musicplayer.radio.toStationSeed

import com.audiophile.musicplayer.data.canonical.CanonicalMapper

import com.audiophile.musicplayer.data.canonical.CanonicalTrack

import com.audiophile.musicplayer.data.catalog.ArtistCatalog

import com.audiophile.musicplayer.data.catalog.AlbumCatalog

import com.audiophile.musicplayer.data.connectors.ConnectedLibraryProvider

import com.audiophile.musicplayer.data.display.DisplayMetadataCleaner

import com.google.gson.JsonObject

import com.google.gson.JsonParser

import kotlinx.coroutines.CompletableDeferred

import kotlinx.coroutines.Dispatchers

import kotlinx.coroutines.async

import kotlinx.coroutines.awaitAll

import kotlinx.coroutines.coroutineScope

import kotlinx.coroutines.supervisorScope

import kotlinx.coroutines.withTimeout

import kotlinx.coroutines.withTimeoutOrNull

import kotlinx.coroutines.flow.MutableStateFlow

import kotlinx.coroutines.flow.StateFlow

import kotlinx.coroutines.flow.asStateFlow

import kotlinx.coroutines.sync.Mutex

import kotlinx.coroutines.sync.Semaphore

import kotlinx.coroutines.sync.withLock

import kotlinx.coroutines.sync.withPermit

import kotlinx.coroutines.flow.update

import kotlinx.coroutines.isActive

import kotlinx.coroutines.launch

import kotlinx.coroutines.delay

import kotlinx.coroutines.withContext

import androidx.core.net.toUri



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

    val libraryAlbums: List<com.audiophile.musicplayer.data.local.entities.Album>,

    val localPlaylists: List<com.audiophile.musicplayer.data.local.entities.PlaylistEntity> = emptyList()

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

    val localSongs: List<LocalSongEntity> = emptyList(),

    val editorialNewReleases: List<SourceSearchResult> = emptyList(),

    val editorialNewReleasesLoading: Boolean = false,

    val editorialNewReleasesStatus: NewReleaseFeedStatus = NewReleaseFeedStatus.IDLE,

    val editorialNewReleasesError: String? = null,

    val editorialNewReleasesUpdatedAtMs: Long? = null,

    val homeFeed: com.audiophile.musicplayer.data.source.GatewayHomeFeed? = null,

    val homeFeedLoading: Boolean = false,

    val homePlaylist: com.audiophile.musicplayer.data.source.GatewayHomePlaylist? = null,

    val homePlaylistTracks: List<CanonicalTrack> = emptyList(),

    val homePlaylistLoading: Boolean = false,

    val homePlaylistError: String? = null,

    val catalogForYou: List<SourceSearchResult> = emptyList(),

    val catalogForYouArtist: String? = null,

    val library: List<UnifiedTrackWithSources> = emptyList(),

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

    val isScanningDeviceLibrary: Boolean = false,

    val deviceScanProgress: String? = null,

    val libraryNeedsRefresh: Boolean = false,

    val statusMessage: String? = null,

    val resolverConfig: ResolverConfigForm = ResolverConfigForm(),

    val activeTrackEnhancedMetadata: EnhancedMetadata? = null,

    val externalSources: List<ExternalSourceConfig> = emptyList(),

    val sourceTestResults: List<SourceTestResult> = emptyList(),

    val sourceStreamTestResults: List<SourceStreamTestResult> = emptyList(),

    val sourceHealthStatuses: List<SourceHealthStatus> = emptyList(),

    val isTestingSource: Boolean = false,

    val libraryAlbums: List<com.audiophile.musicplayer.data.local.entities.Album> = emptyList(),

    val artistCatalog: ArtistCatalog? = null,

    val artistCatalogLoading: Boolean = false,

    val albumCatalog: AlbumCatalog? = null,

    val albumCatalogLoading: Boolean = false,

    val streamingStationLoading: Boolean = false,

    val localPlaylists: List<com.audiophile.musicplayer.data.local.entities.PlaylistEntity> = emptyList(),

    val playlistDetailTracks: List<LocalSongEntity> = emptyList(),

    val activePlaylistId: Long? = null,

    val activePlaylistName: String = "",

    val activePlaylistArtworkUrl: String? = null,

    val activePlaylistDescription: String? = null,

    val wrappedStats: ListeningYearStats? = null

)



@OptIn(kotlinx.coroutines.FlowPreview::class)

@HiltViewModel

class MainViewModel @Inject constructor(

    @ApplicationContext private val appContext: Context,

    private val container: AppContainer

) : ViewModel() {



    private val newReleaseFeedCache = NewReleaseFeedCache(appContext)

    private val initialNewReleaseFeed = NewReleaseFeedPolicy.fromCache(

        newReleaseFeedCache.load(),

        System.currentTimeMillis()

    )



    private val _uiState = MutableStateFlow(

        MainUiState(

            editorialNewReleases = initialNewReleaseFeed.tracks,

            editorialNewReleasesLoading = false,

            editorialNewReleasesStatus = initialNewReleaseFeed.status,

            editorialNewReleasesError = initialNewReleaseFeed.errorMessage,

            editorialNewReleasesUpdatedAtMs = initialNewReleaseFeed.lastUpdatedAtMs

        )

    )

    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()



    private var lastRadioSeedKey: String? = null

    private var lastRadioSeedTimeMs: Long = 0L

    private val refreshMutex = Mutex()

    private val editorialFeedMutex = Mutex()

    private var artistCatalogRequest: String? = null

    private var albumCatalogRequest: String? = null

    private var editorialReleasesLoaded = initialNewReleaseFeed.status == NewReleaseFeedStatus.CONTENT



    // ── Endless Station State ──

    private var activeStationSeed: StreamingSeedParams? = null

    private val playedStationTrackIds = ArrayDeque<String>(500)

    private var isRefillingStation = false

    private val REFILL_THRESHOLD = 15

    private var stationMonitorJob: kotlinx.coroutines.Job? = null



    fun loadArtistCatalog(artistName: String, canonicalArtistId: Long? = null) {

        val cleanArtist = artistName.trim()

        if (cleanArtist.isBlank() && canonicalArtistId == null) return

        val requestKey = canonicalArtistId?.let { "id:$it" } ?: cleanArtist

        if (artistCatalogRequest.equals(requestKey, ignoreCase = true) &&

            (_uiState.value.artistCatalogLoading ||

                _uiState.value.artistCatalog?.artist?.name.equals(cleanArtist, ignoreCase = true) ||

                _uiState.value.artistCatalog?.artist?.id == canonicalArtistId?.toString())

        ) return

        artistCatalogRequest = requestKey

        _uiState.update { it.copy(artistCatalog = null, artistCatalogLoading = true) }

        viewModelScope.launch {

            val catalog = try {
                withContext(Dispatchers.IO) {

                val resolved = if (canonicalArtistId != null) {

                    container.canonicalMusicResolver.artistById(canonicalArtistId)

                } else {

                    container.canonicalMusicResolver.resolveArtist(cleanArtist).entity

                }

                val browseName = resolved?.canonicalName ?: cleanArtist

                val browsed = container.catalogBrowseRepository.browseArtist(browseName, limit = 100)

                if (resolved == null) {

                    browsed

                } else {

                    val graphTracks = container.canonicalMusicResolver.tracksForArtist(resolved.artistId)

                        .map { entity ->

                            com.audiophile.musicplayer.data.canonical.CanonicalTrack(

                                title = entity.title,

                                artist = entity.artistDisplay,

                                album = entity.albumDisplay,

                                isrc = entity.isrc,

                                durationMs = entity.durationMs,

                                artworkUrl = entity.artworkUrl,

                                trackNumber = entity.trackNumber,

                                discNumber = entity.discNumber,

                                genre = entity.genre,

                                explicit = entity.explicit,

                                canonicalTrackId = entity.trackId,

                                canonicalArtistId = entity.canonicalArtistId,

                                canonicalAlbumId = entity.canonicalAlbumId

                            )

                        }

                    val graphAlbums = container.canonicalMusicResolver.albumsForArtist(resolved.artistId)

                        .map { container.canonicalMusicResolver.toDtoAlbum(it) }

                    val mergedTracks = (browsed.tracks + graphTracks)

                        .distinctBy {

                            "${it.title.trim().lowercase()}|${it.artist.trim().lowercase()}"

                        }

                    val mergedAlbums = (graphAlbums + browsed.albums)

                        .distinctBy { it.id ?: "${it.title.lowercase()}|${it.artist.lowercase()}" }

                    browsed.copy(

                        artist = browsed.artist.copy(

                            id = resolved.artistId.toString(),

                            name = resolved.canonicalName,

                            artworkUrl = resolved.artworkUrl ?: browsed.artist.artworkUrl

                        ),

                        tracks = mergedTracks,

                        albums = mergedAlbums

                    )

                }

            }

            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (e: Exception) {
                Log.w("VANTA_ARTIST_DETAIL", "loadArtistCatalog failed for '$artistName': ${e.message}")
                null
            }

            if (catalog == null) {
                _uiState.update { it.copy(artistCatalog = null, artistCatalogLoading = false) }
                return@launch
            }

            if (artistCatalogRequest.equals(requestKey, ignoreCase = true)) {

                _uiState.update { it.copy(artistCatalog = catalog, artistCatalogLoading = false) }

            }

        }

    }



    fun loadEditorialNewReleases(force: Boolean = false) {

        if (!force && (editorialReleasesLoaded || _uiState.value.editorialNewReleasesLoading)) return

        viewModelScope.launch {

            refreshEditorialNewReleases()

        }

    }



    private var homeFeedLoaded = false

    private val homeFeedMutex = kotlinx.coroutines.sync.Mutex()



    fun loadHomeFeed(force: Boolean = false) {

        if (!force && (homeFeedLoaded || _uiState.value.homeFeedLoading)) return

        viewModelScope.launch {

            _uiState.update { it.copy(homeFeedLoading = true) }

            val feed = withContext(Dispatchers.IO) {

                runCatching { container.newReleasesSource.homeFeed() }

                    .getOrElse { error ->

                        Log.w("VANTA_HOME_FEED", "Home feed load failed", error)

                        null

                    }

            }

            homeFeedLoaded = feed != null && !feed.isEmpty()

            Log.d(

                "VANTA_HOME_FEED",

                "loaded=$homeFeedLoaded playlists=${feed?.playlists?.size ?: 0} fresh=${feed?.freshDrops?.size ?: 0} popular=${feed?.popularTracks?.size ?: 0} trending=${feed?.trendingNow?.size ?: 0}"

            )

            _uiState.update { it.copy(homeFeed = feed ?: it.homeFeed, homeFeedLoading = false) }

        }

    }



    fun playHomeTrack(result: SourceSearchResult, fullList: List<SourceSearchResult> = emptyList()) {
        if (fullList.isNotEmpty()) {
            val idx = fullList.indexOfFirst { it.id == result.id && it.providerId == result.providerId }.coerceAtLeast(0)
            playReleases(fullList, idx)
        } else {
            playSourceResult(CanonicalMapper.mapToCanonicalTrack(result))
        }
    }

    fun playReleases(releases: List<SourceSearchResult>, startIndex: Int = 0, shuffle: Boolean = false) {
        if (releases.isEmpty()) return
        val canonical = releases.map { CanonicalMapper.mapToCanonicalTrack(it) }
        playSourceResults(canonical, startIndex, shuffle)
    }

    fun playSourceResults(tracks: List<CanonicalTrack>, startIndex: Int = 0, shuffle: Boolean = false) {
        if (tracks.isEmpty()) return
        val safeIndex = startIndex.coerceIn(tracks.indices)
        val ordered = if (shuffle) {
            tracks.shuffled()
        } else {
            listOf(tracks[safeIndex]) + tracks.filterIndexed { i, _ -> i != safeIndex }
        }
        playCatalogQueue(ordered, startIndex = 0, shuffle = false)
    }

    private var homePlaylistJob: kotlinx.coroutines.Job? = null



    fun openHomePlaylist(playlist: com.audiophile.musicplayer.data.source.GatewayHomePlaylist) {

        homePlaylistJob?.cancel()

        _uiState.update { it.copy(homePlaylist = playlist, homePlaylistTracks = emptyList(), homePlaylistLoading = true, homePlaylistError = null) }

        homePlaylistJob = viewModelScope.launch {

            try {

                val tracks = withContext(Dispatchers.IO) { loadCatalogPlaylistTracks(playlist) }

                _uiState.update { it.copy(homePlaylistTracks = tracks, homePlaylistLoading = false,

                    homePlaylistError = if (tracks.isEmpty()) "This playlist couldn’t be loaded. Please try again." else null) }

            } catch (cancelled: kotlinx.coroutines.CancellationException) {

                throw cancelled

            } catch (_: java.io.IOException) {

                _uiState.update { it.copy(homePlaylistLoading = false, homePlaylistError = "Check your connection and try again.") }

            }

        }

    }

    fun clearHomePlaylist() {
        homePlaylistJob?.cancel()
        _uiState.update {
            it.copy(
                homePlaylist = null,
                homePlaylistTracks = emptyList(),
                homePlaylistLoading = false,
                homePlaylistError = null
            )
        }
    }

    private suspend fun loadCatalogPlaylistTracks(
        playlist: com.audiophile.musicplayer.data.source.GatewayHomePlaylist
    ): List<CanonicalTrack> {
        if (playlist.isDeezerCatalog()) {
            val deezerId = playlist.id.toLongOrNull()
            if (deezerId != null) {
                val loaded = container.catalogBrowseRepository.browsePlaylist(deezerId).tracks
                if (loaded.isNotEmpty()) return loaded
            }
        } else if (playlist.source.equals("spotify", ignoreCase = true)) {
            val spotifyTracks = container.newReleasesSource.spotifyPlaylistTracks(playlist.id, limit = 100)
                .map { CanonicalMapper.mapToCanonicalTrack(it) }
            if (spotifyTracks.isNotEmpty()) return spotifyTracks
        } else {
            val appleTracks = container.newReleasesSource.applePlaylistTracks(playlist.id, limit = 100)
                .map { CanonicalMapper.mapToCanonicalTrack(it) }
            if (appleTracks.isNotEmpty()) return appleTracks
        }
        val match = container.catalogBrowseRepository.searchPlaylists(playlist.name)
            .firstOrNull { it.title.equals(playlist.name, ignoreCase = true) }
            ?: container.catalogBrowseRepository.searchPlaylists(playlist.name).firstOrNull()
        val fallbackId = match?.id?.toLongOrNull()
        return if (fallbackId != null) container.catalogBrowseRepository.browsePlaylist(fallbackId, match).tracks else emptyList()
    }



    private var homePlaylistPlaybackJob: kotlinx.coroutines.Job? = null



    fun playHomePlaylist(startIndex: Int = 0, shuffle: Boolean = false) {

        playCatalogQueue(_uiState.value.homePlaylistTracks, startIndex, shuffle)

    }



    fun playCatalogQueue(tracks: List<CanonicalTrack>, startIndex: Int = 0, shuffle: Boolean = false) {

        if (tracks.isEmpty()) return

        val ordered = if (shuffle) tracks.shuffled() else tracks.drop(startIndex.coerceIn(tracks.indices))

        homePlaylistPlaybackJob?.cancel()

        homePlaylistPlaybackJob = viewModelScope.launch {

            setStatusMessage("Preparing playlist…")

            var firstIndex = 0

            var first: UnifiedTrackWithSources? = null

            while (first == null && firstIndex < ordered.size) {

                first = withContext(Dispatchers.IO) { resolveCanonicalTrack(ordered[firstIndex]) }

                firstIndex++

            }

            val initial = first

            if (initial == null) {

                setStatusMessage("No playable sources are available for this playlist right now.")

                return@launch

            }

            playQueue(listOf(initial))

            setStatusMessage("Playing playlist")

            val queueStarted = kotlinx.coroutines.withTimeoutOrNull(2_000L) {

                while (container.queueManager.originalQueue.firstOrNull()?.track?.trackId != initial.track.trackId) {

                    kotlinx.coroutines.delay(25L)

                }

                true

            } ?: false

            if (!queueStarted) return@launch

            // Start the first available recording immediately; prepare the rest while it plays.

            for (batch in ordered.drop(firstIndex).chunked(4)) {

                while (container.queueManager.snapshot().let { it.currentOriginalIndex < it.originalQueue.size - 3 }) {

                    if (container.queueManager.originalQueue.firstOrNull()?.track?.trackId != initial.track.trackId) return@launch

                    delay(1_000L)

                }

                if (container.queueManager.originalQueue.firstOrNull()?.track?.trackId != initial.track.trackId) return@launch

                val resolved = withContext(Dispatchers.IO) { resolveCatalogTracks(batch) }

                if (container.queueManager.originalQueue.firstOrNull()?.track?.trackId != initial.track.trackId) return@launch

                for (track in resolved) container.queueManager.addToOriginalQueue(track)

                _uiState.update { it.copy(queueSnapshot = container.queueManager.snapshot()) }

            }

        }

    }





    private var catalogForYouLoaded = false



    fun loadCatalogForYou(refresh: Boolean = false) {

        if (!refresh && catalogForYouLoaded) return

        val library = _uiState.value.library

        val libraryTopArtist = library.asSequence()

            .filter { t -> t.isPlayableMusicCandidate() && t.track.artist.isNotBlank() && t.track.lastPlayedAt != null }

            .groupingBy { t -> t.track.artist.trim() }

            .eachCount()

            .filter { entry -> entry.value >= 2 }

            .maxByOrNull { it.value }

            ?.key

        viewModelScope.launch {

            // Preference order: local library listening feed, then the taste engine
            // (session + favorites + ListeningHistory, which absorbs plays from every
            // connected provider incl. Spotify). Never brand the surface with a source.
            val tasteTop = if (libraryTopArtist.isNullOrBlank()) tasteTopArtist() else null

            val topArtist = libraryTopArtist ?: tasteTop

            if (topArtist.isNullOrBlank()) {
                catalogForYouLoaded = true
                return@launch
            }

            _uiState.update { it.copy(catalogForYouArtist = topArtist) }

            val results = withContext(Dispatchers.IO) {

                runCatching { container.newReleasesSource.search(topArtist) }

                    .getOrElse { error ->

                        Log.w("VANTA_FOR_YOU", "Catalog-for-you search failed for '$topArtist'", error)

                        emptyList()

                    }

            }

            val picks = results.filter { it.title.isNotBlank() && it.artist.isNotBlank() }

                .distinctBy { (it.isrc ?: it.id).uppercase().let { key -> "key:$key" } }

                .take(12)

            catalogForYouLoaded = true

            _uiState.update { it.copy(catalogForYou = picks) }

        }

    }

    private suspend fun tasteTopArtist(): String? = withContext(Dispatchers.IO) {
        val profile = try {
            container.aiDjRecommendationEngine.getTasteProfile()
        } catch (e: kotlin.coroutines.cancellation.CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w("VANTA_FOR_YOU", "Taste-profile fallback failed", e)
            null
        } ?: return@withContext null
        profile.topArtists.firstOrNull { it.isNotBlank() }
    }



    private fun currentNewReleaseFeed(): NewReleaseFeedSnapshot = NewReleaseFeedSnapshot(

        tracks = _uiState.value.editorialNewReleases,

        status = _uiState.value.editorialNewReleasesStatus,

        lastUpdatedAtMs = _uiState.value.editorialNewReleasesUpdatedAtMs,

        errorMessage = _uiState.value.editorialNewReleasesError

    )



    private fun publishNewReleaseFeed(snapshot: NewReleaseFeedSnapshot) {

        _uiState.update {

            it.copy(

                editorialNewReleases = snapshot.tracks,

                editorialNewReleasesLoading = snapshot.status == NewReleaseFeedStatus.LOADING,

                editorialNewReleasesStatus = snapshot.status,

                editorialNewReleasesError = snapshot.errorMessage,

                editorialNewReleasesUpdatedAtMs = snapshot.lastUpdatedAtMs

            )

        }

    }



    private suspend fun refreshEditorialNewReleases(): List<SourceSearchResult> =

        editorialFeedMutex.withLock {

            val before = currentNewReleaseFeed()

            publishNewReleaseFeed(NewReleaseFeedPolicy.loading(before))

            val releases = withContext(Dispatchers.IO) {

                runCatching { container.newReleasesSource.editorialNewReleases() }

                    .getOrElse { error ->

                        Log.w("VANTA_NEW_FEED", "Release feed refresh failed", error)

                        emptyList()

                    }

            }

            val next = NewReleaseFeedPolicy.completed(before, releases, System.currentTimeMillis())

            editorialReleasesLoaded = next.status == NewReleaseFeedStatus.CONTENT

            if (next.status == NewReleaseFeedStatus.CONTENT) {

                withContext(Dispatchers.IO) { newReleaseFeedCache.save(next) }

            }

            publishNewReleaseFeed(next)

            next.tracks

        }



    fun playTodaysDrop(shuffle: Boolean = false) {
        viewModelScope.launch {
            val candidates = ensureTodaysDropCandidates()
            if (candidates.isEmpty()) {
                setStatusMessage("The live discovery queue is not available yet.")
                return@launch
            }

            setStatusMessage(if (shuffle) "Shuffling live discovery..." else "Playing live discovery...")
            playReleases(candidates, startIndex = 0, shuffle = shuffle)
        }
    }



    fun saveTodaysDropToLibrary() {

        viewModelScope.launch {

            val candidates = ensureTodaysDropCandidates().take(12)

            if (candidates.isEmpty()) {

                setStatusMessage("The live discovery queue is not available yet.")

                return@launch

            }

            setStatusMessage("Saving the current discovery queue...")

            var saved = 0

            candidates.forEach { release ->

                val resolved = withContext(Dispatchers.IO) {

                    resolveCanonicalTrack(CanonicalMapper.mapToCanonicalTrack(release))

                }

                if (resolved != null) saved += 1

            }

            refreshAll(skipRoomMaterialize = true)

            setStatusMessage(

                if (saved > 0) "Saved $saved discovery track(s) to your library"

                else "No playable Daily Edit tracks could be saved"

            )

        }

    }



    private suspend fun ensureTodaysDropCandidates(): List<SourceSearchResult> {

        if (_uiState.value.editorialNewReleases.isEmpty()) {

            _uiState.update { it.copy(statusMessage = "Finding today's drop...") }

            refreshEditorialNewReleases()

        }

        val candidates = _uiState.value.editorialNewReleases.filter { candidate ->

            candidate.title.isNotBlank() &&

                candidate.artist.isNotBlank() &&

                candidate.status != SearchItemStatus.PREVIEW

        }

        return NewReleaseFeedPolicy.dailyEdit(

            tracks = candidates,

            nowMs = System.currentTimeMillis(),

            limit = 12

        )

    }



    fun loadAlbumCatalog(albumName: String, artistName: String, canonicalAlbumId: Long? = null) {

        val request = canonicalAlbumId?.let { "id:$it" }

            ?: "${albumName.trim().lowercase()}|${artistName.trim().lowercase()}"

        if (albumName.isBlank() && canonicalAlbumId == null) return

        if (albumCatalogRequest == request &&

            (_uiState.value.albumCatalogLoading ||

                _uiState.value.albumCatalog?.album?.title.equals(albumName, ignoreCase = true) ||

                _uiState.value.albumCatalog?.album?.id == canonicalAlbumId?.toString())

        ) return

        albumCatalogRequest = request

        _uiState.update { it.copy(albumCatalog = null, albumCatalogLoading = true) }

        viewModelScope.launch {

            val catalog = withContext(Dispatchers.IO) {

                val resolved = if (canonicalAlbumId != null) {

                    container.canonicalMusicResolver.albumById(canonicalAlbumId)

                } else {

                    container.canonicalMusicResolver.resolveAlbum(albumName, artistName).entity

                }

                val browseAlbum = resolved?.title ?: albumName

                val browseArtist = resolved?.albumArtistName ?: artistName

                val browsed = container.catalogBrowseRepository.browseAlbum(browseAlbum, browseArtist)

                if (resolved == null) {

                    browsed

                } else {

                    val graphTracks = container.canonicalMusicResolver.tracksForAlbum(resolved.albumId)

                        .map { entity ->

                            com.audiophile.musicplayer.data.canonical.CanonicalTrack(

                                title = entity.title,

                                artist = entity.artistDisplay,

                                album = entity.albumDisplay,

                                isrc = entity.isrc,

                                durationMs = entity.durationMs,

                                artworkUrl = entity.artworkUrl,

                                trackNumber = entity.trackNumber,

                                discNumber = entity.discNumber,

                                genre = entity.genre,

                                explicit = entity.explicit,

                                canonicalTrackId = entity.trackId,

                                canonicalArtistId = entity.canonicalArtistId,

                                canonicalAlbumId = entity.canonicalAlbumId

                            )

                        }

                    val ordered = if (graphTracks.any { it.trackNumber != null || it.discNumber != null }) {

                        (browsed.tracks + graphTracks).distinctBy {

                            "${it.title.trim().lowercase()}|${it.artist.trim().lowercase()}"

                        }.sortedWith(

                            compareBy<com.audiophile.musicplayer.data.canonical.CanonicalTrack>(

                                { it.discNumber ?: Int.MAX_VALUE },

                                { it.trackNumber ?: Int.MAX_VALUE },

                                { it.title.lowercase() }

                            )

                        )

                    } else {

                        (browsed.tracks + graphTracks).distinctBy {

                            "${it.title.trim().lowercase()}|${it.artist.trim().lowercase()}"

                        }

                    }

                    browsed.copy(

                        album = browsed.album.copy(

                            id = resolved.albumId.toString(),

                            title = resolved.title,

                            artist = resolved.albumArtistName ?: browsed.album.artist,

                            artworkUrl = resolved.artworkUrl ?: browsed.album.artworkUrl,

                            releaseYear = resolved.releaseYear ?: browsed.album.releaseYear

                        ),

                        tracks = ordered

                    )

                }

            }

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

        refreshDueConnectedLibraries()



        // Preference-backed resolver/source configuration is not needed to draw

        // Home. Reading encrypted preferences here used to block ViewModel

        // construction and therefore the first usable frame.

        viewModelScope.launch(Dispatchers.IO) {

            val resolverConfig = loadResolverConfigForm()

            val externalSources = container.externalSourceConfigStore.getSources()

            _uiState.update {

                it.copy(

                    resolverConfig = resolverConfig,

                    externalSources = externalSources

                )

            }

        }



        // Restore played history from DB for radio exclusion persistence

        viewModelScope.launch(Dispatchers.IO) {

            val recentIds = container.trackRepository.getRecentTrackIds(10)

            container.queueManager.loadPlayedHistory(recentIds)

            Log.d("VANTA_HISTORY", "restored recent count=${recentIds.size}")

        }



        // Reactively sync queue snapshot when track changes

        viewModelScope.launch {

            // Allow the app shell to commit before constructing optional social

            // and queue-adjacent services on a cold debug install.

            delay(500L)

            var lastPublishedTrackId: String? = null

            container.playbackStateHolder.state.collect { state: com.audiophile.musicplayer.playback.NowPlayingState ->

                if (state.trackId != _uiState.value.activeTrackId) {

                    val qs = container.queueManager.snapshot()

                    _uiState.update { it.copy(

                        activeTrackId = state.trackId,

                        queueSnapshot = qs,

                        activeTrackEnhancedMetadata = null

                    ) }

                    state.trackId?.let { tid ->
                        launch(kotlinx.coroutines.Dispatchers.IO) {
                            val feature = container.trackFeatureDao.getByTrackId(tid)
                            if (feature != null) {
                                container.playbackStateHolder.update { copy(acousticness = feature.acousticness) }
                            }
                        }
                    }

                }

                if (state.trackId != null && state.trackId != lastPublishedTrackId) {

                    lastPublishedTrackId = state.trackId

                    container.vantaSocialManager.publishOwnActivity(state)

                }

                if (!state.errorMessage.isNullOrBlank()) {
                    val raw = state.errorMessage
                    val isInternalEngineNoise = raw.contains("Source error", ignoreCase = true) ||
                        raw.contains("BehindLiveWindowException", ignoreCase = true) ||
                        raw.contains("ExoPlaybackException", ignoreCase = true)
                    if (!isInternalEngineNoise) {
                        _uiState.update { it.copy(statusMessage = raw) }
                    }
                }

            }

        }



        // Keep the friends feed fresh while the app is open (gateway best-effort).

        viewModelScope.launch {

            delay(5_000L)

            while (true) {

                container.vantaSocialManager.refreshFriendFeed()

                delay(60_000L)

            }

        }



    }



    private fun refreshDueConnectedLibraries() {

        viewModelScope.launch {

            delay(2_000L)

            ConnectedLibraryProvider.entries.forEach { provider ->

                if (!container.connectedLibraryManager.isConnected(provider)) return@forEach

                if (!container.connectedLibraryManager.isAutoRefreshDue(provider)) return@forEach

                runCatching {

                    withContext(Dispatchers.IO) {

                        container.connectedLibraryManager.importLibrary(provider)

                    }

                }.onSuccess { result ->

                    Log.i(

                        "VANTA_CONNECTOR_AUTO_REFRESH",

                        "provider=$provider tracks=${result.summary.tracksImported} playlists=${result.summary.playlistsImported}"

                    )

                    refreshAll(skipRoomMaterialize = true)

                }.onFailure { error ->

                    Log.w(

                        "VANTA_CONNECTOR_AUTO_REFRESH",

                        "provider=$provider reason=${error.message ?: error.javaClass.simpleName}"

                    )

                }

            }

        }

    }



    fun quickPlayFromUrl(url: String) {

        val trimmed = url.trim()

        if (trimmed.isBlank()) return

        viewModelScope.launch {

            _uiState.update { it.copy(statusMessage = "Resolving link...") }

            val collection = withContext(Dispatchers.IO) {
                resolvePlatformCollection(trimmed)
                    ?: com.audiophile.musicplayer.data.importer.CollectionResolver().resolve(trimmed)?.let { resolved ->
                        com.audiophile.musicplayer.data.importer.PlatformLinkCollection(
                            title = resolved.collectionTitle,
                            creator = resolved.collectionArtist,
                            artworkUrl = resolved.artworkUrl,
                            platform = resolved.platform,
                            type = if (resolved.collectionType == com.audiophile.musicplayer.data.importer.CollectionType.PLAYLIST) {
                                "playlist"
                            } else {
                                "album"
                            },
                            externalId = null,
                            tracks = resolved.tracks.map { track ->
                                com.audiophile.musicplayer.data.importer.PlatformLinkMetadata(
                                    title = track.title,
                                    artist = track.artist,
                                    album = track.album,
                                    artworkUrl = track.artworkUrl,
                                    isrc = track.isrc,
                                    durationMs = track.durationMs,
                                    platform = resolved.platform,
                                    externalId = null,
                                    matchReason = "Gateway playlist track",
                                )
                            },
                        )
                    }
            }

            if (collection != null) {

                val catalogTracks = collection.tracks.map { it.toCanonicalTrack() }

                _uiState.update {

                    it.copy(

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

                    it.copy(statusMessage = if (com.audiophile.musicplayer.data.importer.GatewayPlaylistImporter.isGatewayPlaylistUrl(trimmed)) {

                        "Could not load that playlist. Make sure it's public, then try Import."

                    } else "Could not resolve link: $trimmed")

                }

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

                        Triple(playable, track, resolved)

                    } else null

                } else null

            }

            if (result != null) {

                val (playable, track, resolvedStream) = result

                val queue = playbackQueueFor(track)

                val startIndex = queue.indexOfFirst { it.track.trackId == track.track.trackId }.coerceAtLeast(0)

                container.playerController.playQueue(

                    tracks = queue,

                    startIndex = startIndex,

                    resolvedStream = resolvedStream

                )

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

            _uiState.update {

                it.copy(statusMessage = "No playable source found for $title — $artist")

            }

        }

    }

    fun clearStatusMessage() {
        _uiState.update { it.copy(statusMessage = null) }
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

    fun handleSpotifyAuthCallback(uri: Uri) {
        viewModelScope.launch {
            _uiState.update { it.copy(statusMessage = "Connecting to Spotify...") }
            val result = container.spotifyOAuthManager.handleCallback(uri)
            result.onSuccess {
                _uiState.update { it.copy(statusMessage = "Spotify connected! Syncing your library...") }
                runCatching {
                    container.connectedLibraryManager.importLibrary(ConnectedLibraryProvider.SPOTIFY)
                }.onSuccess { importResult ->
                    val summary = importResult.summary
                    _uiState.update {
                        it.copy(
                            statusMessage = "Spotify sync complete: ${summary.tracksImported} tracks, ${summary.playlistsImported} playlists imported.",
                            libraryNeedsRefresh = true
                        )
                    }
                    refreshAll(skipRoomMaterialize = true)
                }.onFailure { err ->
                    Log.e("MainViewModel", "Spotify auto-sync failed after login", err)
                    _uiState.update { it.copy(statusMessage = "Spotify connected, but initial sync encountered an issue: ${err.message}") }
                }
            }.onFailure { err ->
                Log.e("MainViewModel", "Spotify OAuth callback failed", err)
                _uiState.update { it.copy(statusMessage = "Spotify login failed: ${err.message}") }
            }
        }
    }

    fun syncSpotifyLibrary() {
        viewModelScope.launch {
            _uiState.update { it.copy(statusMessage = "Syncing Spotify library...") }
            runCatching {
                container.connectedLibraryManager.importLibrary(ConnectedLibraryProvider.SPOTIFY)
            }.onSuccess { importResult ->
                val summary = importResult.summary
                _uiState.update {
                    it.copy(
                        statusMessage = "Spotify sync complete: ${summary.tracksImported} tracks, ${summary.playlistsImported} playlists imported.",
                        libraryNeedsRefresh = true
                    )
                }
                refreshAll(skipRoomMaterialize = true)
            }.onFailure { err ->
                Log.e("MainViewModel", "Spotify manual sync failed", err)
                _uiState.update { it.copy(statusMessage = "Spotify sync failed: ${err.message}") }
            }
        }
    }

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

                        torBoxCandidates = refreshed.torBoxCandidates,

                        queueSnapshot = refreshed.queueSnapshot,

                        localLibraryCounts = refreshed.localLibraryCounts,

                        activeImportedTracks = refreshed.activeImportedTracks,

                        importBatches = refreshed.importBatches,

                        activeTrackId = refreshed.activeTrackId,

                        resolverConfig = refreshed.resolverConfig,

                        libraryAlbums = refreshed.libraryAlbums,

                        localPlaylists = refreshed.localPlaylists,

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

        }.ifEmpty { _uiState.value.library }

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

        val localPlaylists = container.localLibraryRepository.playlistsSnapshot()



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

            libraryAlbums = libraryAlbums,

            localPlaylists = localPlaylists

        )

    }



    private suspend fun resolvePlatformLink(url: String): PlatformLinkMetadata? {

        val parsed = SoundiizTextParser.parseLine(url)

        return runCatching {

            kotlinx.coroutines.withTimeout(18_000L) {

                container.platformLinkResolver.resolve(parsed)

            }

        }.onFailure {

            Log.w("VANTA_LINK_RESOLVE", "host='${VantaLogger.urlHost(url)}' failed='${it.message}'")

        }.getOrNull()

    }



    private suspend fun resolvePlatformCollection(url: String): com.audiophile.musicplayer.data.importer.PlatformLinkCollection? =

        runCatching {

            kotlinx.coroutines.withTimeout(18_000L) {

                container.platformLinkResolver.resolveCollection(url)

            }

        }.onFailure {

            Log.w("VANTA_COLLECTION_RESOLVE", "host='${VantaLogger.urlHost(url)}' failed='${it.message}'")

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



        val expectedArtistCompact = expectedArtist.replace(" ", "")

        val actualArtistCompact = actualArtist.replace(" ", "")

        val artistMatches = expectedArtist == actualArtist || expectedArtistCompact == actualArtistCompact ||

            ((expectedArtist.contains(actualArtist) || actualArtist.contains(expectedArtist)) &&

            minOf(expectedArtist.length, actualArtist.length).toFloat() /

                maxOf(expectedArtist.length, actualArtist.length) >= 0.85f)

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

                minOf(expectedTitle.length, actualTitle.length).toFloat() / maxOf(expectedTitle.length, actualTitle.length) >= 0.85f ||

                overlap >= 0.75f

            )

    }



    private fun sourceCandidateMatches(expected: CanonicalTrack, candidate: SourceSearchResult): Boolean {

        if (!sourceCandidateMatches(

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

        ) {

            return false

        }

        val expectedMs = expected.durationMs

        val actualMs = candidate.durationMs

        if (expectedMs != null && expectedMs > 0L && actualMs != null && actualMs > 0L) {

            val delta = kotlin.math.abs(actualMs - expectedMs)

            if (delta > 45_000L) return false

        }

        return true

    }



    /** Tap-to-play: resolve the exact catalog row first, re-search only as fallback. */

    private suspend fun resolveCanonicalTrackForPlayback(

        result: CanonicalTrack

    ): Pair<SourceSearchResult, com.audiophile.musicplayer.data.source.ResolvedStream>? {

        val providerId = result.sourceProviderId?.trim().orEmpty()

        val externalId = result.externalTrackId?.trim().orEmpty()

        val tapStartedAtMs = System.currentTimeMillis()

        fun remainingMs(): Long =

            com.audiophile.musicplayer.data.source.CloudLibraryHelpers.remainingTapBudgetMs(tapStartedAtMs)

        val requiresSearchFallback = result.sourceStatus == SearchItemStatus.PREVIEW ||

            result.sourceStatus == SearchItemStatus.METADATA_ONLY ||

            providerId.equals("vanta_preview", ignoreCase = true) ||

            providerId.equals("itunes_preview", ignoreCase = true)

        val supplementalProvider = SourceIdentityGate.isSupplementalPlaybackProvider(providerId)



        if (providerId.isNotBlank() && externalId.isNotBlank() && !requiresSearchFallback && !supplementalProvider) {

            Log.d("VANTA_PLAY_CLICK", "direct_resolve provider=$providerId id=$externalId title='${result.title}'")

            val directBudgetMs = remainingMs()

            when (

                val outcome = container.sourceRegistry.resolvePlayback(

                    providerId,

                    externalId,

                    timeoutMs = directBudgetMs,

                    requestedQuality = com.audiophile.musicplayer.data.source.playback.requestedAudioQualityFromPreference(

                        com.audiophile.musicplayer.data.source.external.SpotiFlacEndpoints.PREFERRED_STREAM_QUALITY

                    )

                )

            ) {

                is com.audiophile.musicplayer.data.source.playback.PlaybackSourceOutcome.Ready -> {

                    val directStream = outcome.stream

                    if (isValidResolvedStream(directStream)) {
                        Log.d("VANTA_PLAY_CLICK", "direct_resolve_ok provider=$providerId fulfillment=${directStream.providerId} id=$externalId host=${VantaLogger.urlHost(directStream.streamUrl)}")
                        return toSourceSearchResult(result) to directStream
                    }

                }

                is com.audiophile.musicplayer.data.source.playback.PlaybackSourceOutcome.Failed -> {

                    Log.w(

                        "VANTA_PLAY_CLICK",

                        "direct_resolve_failed provider=$providerId id=$externalId code=${outcome.failure.code}"

                    )

                }

            }

            Log.w("VANTA_PLAY_CLICK", "Direct resolve failed provider=$providerId id=$externalId leftoverMs=${remainingMs()} — trying search fallback")

        } else if (requiresSearchFallback) {

            Log.d("VANTA_PLAY_CLICK", "direct_source_skipped title='${result.title}' provider=$providerId status=${result.sourceStatus}")

        } else if (supplementalProvider) {

            Log.d(

                "VANTA_PLAY_CLICK",

                "direct_resolve_deferred supplemental provider=$providerId id=$externalId — catalog search first"

            )

        }



        val simplifiedTitle = DisplayMetadataCleaner.cleanTitle(result.title)

        val query = "${result.title} ${result.artist}".trim().ifBlank { simplifiedTitle }

        val catalogPreferredProvider = providerId.takeIf {

            it.isNotBlank() && !SourceIdentityGate.isSupplementalPlaybackProvider(it)

        }



        val leftoverAfterDirectMs = remainingMs()

        if (leftoverAfterDirectMs >= 1_000L) {

            val candidates = container.sourceRegistry.searchAll(

                query,

                timeoutMs = leftoverAfterDirectMs.coerceAtMost(

                    com.audiophile.musicplayer.data.source.CloudLibraryHelpers.TAP_PLAY_SEARCH_TIMEOUT_MS

                ),

                includeSupplemental = false

            )

            val identity = com.audiophile.musicplayer.data.source.SelectedRecordingIdentity(

                title = result.title,

                artist = result.artist,

                durationMs = result.durationMs,

                isrc = result.isrc,

                preferredProviderId = catalogPreferredProvider,

                preferredExternalTrackId = externalId.takeIf { it.isNotBlank() },

                userQuery = "${result.title} ${result.artist}".trim()

            )

            val filtered = com.audiophile.musicplayer.data.source.SourceCandidateRanker.rankSearchResults(

                identity,

                candidates.filter { sourceCandidateMatches(result, it) }

                    .filterNot { it.status == SearchItemStatus.PREVIEW }

            ).filter { !SourceIdentityGate.isSupplementalPlaybackProvider(it.providerId) }

            Log.d("VANTA_PLAY_CLICK", "fallback query='${query.take(60)}' raw=${candidates.size} filtered=${filtered.size}")

            val raced = resolveFirstPlayableCandidate(

                filtered.filter { candidate ->

                    candidate.status.canResolveStream() &&

                        com.audiophile.musicplayer.data.source.external.providerAcceptsCatalogTrackId(

                            candidate.providerId,

                            candidate.id

                        )

                }
                    // Prefer currently-healthy gateway fulfillments (deezer) ahead of
                    // expired community-session providers (qobuz/tidal/amazon).
                    .sortedBy { candidate ->
                        val id = candidate.id.lowercase()
                        when {
                            id.startsWith("deezer:") -> 0
                            id.startsWith("qobuz:") -> 1
                            id.startsWith("tidal:") -> 2
                            id.startsWith("amazon:") -> 3
                            else -> 4
                        }
                    }
                    .take(6),

                timeoutMs = remainingMs().coerceAtLeast(1_500L)

            )

            if (raced != null) {

                val (candidate, stream) = raced

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

        } else {

            Log.w(

                "VANTA_PLAY_CLICK",

                "direct_timeout_skipping_catalog_search leftoverMs=$leftoverAfterDirectMs — engaging safety net directly"

            )

        }



        if (supplementalProvider) {
            Log.w(
                "VANTA_PLAY_CLICK",
                "skipping_removed_supplemental_source provider=$providerId title='${result.title}'"
            )
            return null
        }

        Log.w(
            "VANTA_PLAY_CLICK",
            "lossless_exhausted title='${result.title}' query='$query' — no YouTube fallback"
        )
        return null

    }



    private fun publishOptimisticNowPlaying(result: CanonicalTrack) {

        val pending = NowPlayingState(

            // Only latch a Room/canonical numeric id here. External ids like
            // "deezer:123" must not become trackId — PlayerController treats that
            // as a live/media mismatch and skips every position update.
            trackId = result.canonicalTrackId?.toString(),

            title = result.title,

            artist = result.artist,

            album = result.album,

            isrc = result.isrc,

            canonicalTrackId = result.canonicalTrackId?.toString(),

            canonicalArtistId = result.canonicalArtistId?.toString(),

            canonicalAlbumId = result.canonicalAlbumId?.toString(),

            artworkUrl = result.artworkUrl,

            isBuffering = true,

            durationMs = result.durationMs ?: 0L,

            qualityInfo = result.qualityInfo,

            preferredProviderId = result.sourceProviderId,

            preferredExternalTrackId = result.externalTrackId,

            userQuery = "${result.title} ${result.artist}".trim(),

            featuredArtists = result.featuredArtists

        )

        container.playbackStateHolder.replace(pending)

    }



    private suspend fun resolveFirstPlayableCandidate(

        candidates: List<SourceSearchResult>,

        timeoutMs: Long

    ): Pair<SourceSearchResult, com.audiophile.musicplayer.data.source.ResolvedStream>? {

        if (candidates.isEmpty() || timeoutMs < 500L) return null

        // Try the highest-priority candidate alone with the FULL remaining budget
        // (usually deezer). Racing expired community providers burns time and
        // previously cancelled a late Deezer win at the timeout boundary.
        val primary = candidates.first()
        val primaryBudget = timeoutMs.coerceAtLeast(2_500L)
        val primaryStream = runCatching {
            container.sourceRegistry.resolveStream(
                primary.providerId,
                primary.id,
                timeoutMs = primaryBudget
            )
        }.getOrNull()
        if (primaryStream != null && isValidResolvedStream(primaryStream)) {
            Log.d(
                "VANTA_PLAY_CLICK",
                "race_primary_ok provider=${primary.providerId} id=${primary.id}"
            )
            return primary to primaryStream
        }

        // Skip community-session providers for the secondary race — they 401 when
        // sessions are expired and only waste the leftover budget.
        val remaining = candidates.drop(1).filterNot { candidate ->
            val id = candidate.id.lowercase()
            id.startsWith("amazon:") || id.startsWith("tidal:") || id.startsWith("qobuz:")
        }
        if (remaining.isEmpty()) return null

        val raceBudget = 4_000L.coerceAtMost(timeoutMs).coerceAtLeast(1_500L)
        return supervisorScope {
            val winner =
                CompletableDeferred<Pair<SourceSearchResult, com.audiophile.musicplayer.data.source.ResolvedStream>>()
            val workers = remaining.map { candidate ->
                launch(Dispatchers.IO) {
                    val stream = runCatching {
                        container.sourceRegistry.resolveStream(
                            candidate.providerId,
                            candidate.id,
                            timeoutMs = raceBudget
                        )
                    }.getOrNull()
                    if (stream != null && isValidResolvedStream(stream)) {
                        winner.complete(candidate to stream)
                    }
                }
            }
            try {
                // If a worker finishes in the same tick as the timeout, keep the win.
                val raced = withTimeoutOrNull(raceBudget) { winner.await() }
                raced ?: if (winner.isCompleted) winner.await() else null
            } finally {
                workers.forEach { it.cancel() }
            }
        }
    }



    private suspend fun enrichNowPlayingIdentity(

        result: CanonicalTrack,

        playable: SourceSearchResult,

        track: UnifiedTrackWithSources

    ) {

        val graphResolve = container.canonicalMusicResolver.resolveTrack(

            com.audiophile.musicplayer.data.canonical.CanonicalMusicResolver.TrackInput(

                title = result.title,

                artist = result.artist,

                album = result.album,

                isrc = result.isrc ?: track.track.isrc ?: playable.isrc,

                durationMs = result.durationMs ?: track.track.durationMs ?: playable.durationMs,

                artworkUrl = result.artworkUrl,

                genre = result.genre,

                trackNumber = result.trackNumber,

                discNumber = result.discNumber,

                releaseYear = result.releaseYear,

                explicit = result.explicit,

                providerId = playable.providerId,

                externalTrackId = playable.id,

                unifiedTrackId = track.track.trackId

            )

        )

        val graphTrack = graphResolve.entity

        AcceptanceTruth.graph(

            event = if (graphResolve.created) "track_created" else "track_resolved",

            title = graphTrack?.title ?: result.title,

            artist = graphTrack?.artistDisplay ?: result.artist,

            album = graphTrack?.albumDisplay ?: result.album,

            canonicalTrackId = graphTrack?.trackId,

            canonicalArtistId = graphTrack?.canonicalArtistId,

            canonicalAlbumId = graphTrack?.canonicalAlbumId,

            method = graphResolve.method.name

        )

        val isFavorite = com.audiophile.musicplayer.data.local.LocalSongIdentity.isFavorite(

            songs = container.localLibraryRepository.allSongsSnapshot(),

            isrc = graphTrack?.isrc ?: result.isrc ?: track.track.isrc ?: playable.isrc,

            title = graphTrack?.title ?: result.title,

            artist = graphTrack?.artistDisplay ?: result.displayArtist,

            canonicalTrackId = graphTrack?.trackId ?: result.canonicalTrackId

        )

        val snapshot = container.playbackStateHolder.snapshot()

        if (snapshot.trackId != track.track.trackId.toString()) return

        val updated = snapshot.copy(

            isFavorite = isFavorite,

            canonicalTrackId = graphTrack?.trackId?.toString() ?: snapshot.canonicalTrackId,

            canonicalArtistId = graphTrack?.canonicalArtistId?.toString() ?: snapshot.canonicalArtistId,

            canonicalAlbumId = graphTrack?.canonicalAlbumId?.toString() ?: snapshot.canonicalAlbumId,

            album = graphTrack?.albumDisplay ?: snapshot.album,

            artworkUrl = graphTrack?.artworkUrl ?: snapshot.artworkUrl,

            isrc = graphTrack?.isrc ?: snapshot.isrc

        )

        container.nowPlayingStateStore.save(updated)

        container.playbackStateHolder.replace(updated)

    }



    private fun isValidResolvedStream(stream: com.audiophile.musicplayer.data.source.ResolvedStream?): Boolean {

        if (stream == null || stream.streamUrl.isBlank()) return false

        val url = stream.streamUrl.lowercase()

        if (url.contains("soundhelix", ignoreCase = true)) return false

        if (url.contains("audio-ssl.itunes.apple.com") || url.contains("itunes.apple.com")) return false

        if (url.contains("cdns-preview") || url.contains(".dzcdn.net/stream/")) return false

        if (url.contains("/preview/") || url.contains("preview.mpd") || url.contains("preview.m4a")) return false

        if (url.contains("range=0-") || url.contains("range=0%2d")) return false

        return true

    }



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

            .replace(Regex("""\b(official|audio|video|visualizer|lyrics?|topic|vevo)\b"""), " ")

            .replace(Regex("""[^\p{L}\p{N}\s]+"""), " ")

            .replace(Regex("""\s+"""), " ")

            .normalizeDigitWords()

            .trim()



    private fun String.normalizeDigitWords(): String {

        var result = this

        LINK_DIGIT_WORD_MAP.forEach { (word, digit) ->

            result = result.replace(word, digit)

        }

        return result

    }



    private val LINK_DIGIT_WORD_MAP = mapOf(

        "zero" to "0", "one" to "1", "two" to "2", "three" to "3", "four" to "4",

        "five" to "5", "six" to "6", "seven" to "7", "eight" to "8", "nine" to "9",

        "ten" to "10", "eleven" to "11", "twelve" to "12", "thirteen" to "13",

        "fourteen" to "14", "fifteen" to "15", "sixteen" to "16", "seventeen" to "17",

        "eighteen" to "18", "nineteen" to "19", "twenty" to "20", "thirty" to "30",

        "forty" to "40", "fifty" to "50", "sixty" to "60", "seventy" to "70",

        "eighty" to "80", "ninety" to "90"

    )



    suspend fun previewRadioStation(stationId: String): List<UnifiedTrackWithSources> {

        return container.radioStationPreviewLoader.previewTracks(stationId)

    }



    private var stationStartJob: kotlinx.coroutines.Job? = null
    var activeGenomeEngine: com.audiophile.musicplayer.radio.genome.MusicGenomeEngine? = null
        private set

    private val _radioDiscoveryMode = kotlinx.coroutines.flow.MutableStateFlow(com.audiophile.musicplayer.radio.RadioDiscoveryMode.HYBRID_MIX)
    val radioDiscoveryMode: kotlinx.coroutines.flow.StateFlow<com.audiophile.musicplayer.radio.RadioDiscoveryMode> = _radioDiscoveryMode

    fun setRadioDiscoveryMode(mode: com.audiophile.musicplayer.radio.RadioDiscoveryMode) {
        _radioDiscoveryMode.value = mode
        viewModelScope.launch {
            container.queueManager.setDiscoveryMode(mode)
            val favoriteIds = _uiState.value.localSongs.filter { it.isFavorite }.map { it.id.toString() }.toSet()
            val history = container.queueManager.playedHistory.map { it.toString() }.toSet()
            container.queueManager.reshapeUpcomingQueue(
                mode = mode,
                favoritesIds = favoriteIds,
                recentHistoryIds = history
            )
            _uiState.update { it.copy(queueSnapshot = container.queueManager.snapshot()) }
            setStatusMessage("Station tuned: ${mode.label}")
        }
    }

    fun startJukeboxStation(stationId: String, shuffle: Boolean = false) {
        stationStartJob?.cancel()
        stationStartJob = viewModelScope.launch {
            _uiState.update { it.copy(streamingStationLoading = true, statusMessage = "Building station…") }

            try {
                val station = JukeboxCatalog.resolveStation(stationId, container.customStationStore)
                if (station == null) {
                    _uiState.update { it.copy(streamingStationLoading = false, statusMessage = "Station not found") }
                    return@launch
                }

                activeGenomeEngine = com.audiophile.musicplayer.radio.genome.MusicGenomeEngine(
                    seedTrackTitle = station.name,
                    seedTrackArtist = station.seedArtists.firstOrNull() ?: station.name,
                    seedGenre = station.genreKeywords.firstOrNull(),
                    initialMode = station.genomeMode ?: com.audiophile.musicplayer.radio.genome.PandoraStationMode.BALANCED
                )

                val seedParams = station.toStreamingSeed()

                // Reset endless loop state for the new station
                activeStationSeed = seedParams
                playedStationTrackIds.clear()
                stopStationMonitor()

                val stationSeed = seedParams.toStationSeed()
                withContext(Dispatchers.IO) {
                    container.queueManager.setStreamingStationSeed(stationSeed)
                }
                val initialRequest = com.audiophile.musicplayer.radio.StreamingStationRequest(
                    seed = stationSeed,
                    targetCount = 30,
                    minPlayableToStart = 1,
                    steerGenome = activeGenomeEngine?.currentStationGenome,
                    discoveryMode = _radioDiscoveryMode.value
                )

                val result = withContext(Dispatchers.IO) {
                    withTimeoutOrNull(50_000L) {
                        container.radioQueueEngine.generateStreamingStation(initialRequest)
                    }
                }

                if (result == null) {
                    _uiState.update {
                        it.copy(
                            streamingStationLoading = false,
                            statusMessage = "Station took too long — try another mood"
                        )
                    }
                    return@launch
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

            } catch (e: kotlinx.coroutines.CancellationException) {
                _uiState.update { it.copy(streamingStationLoading = false) }
                throw e
            } catch (e: Exception) {
                Log.e("VANTA_STATION", "Failed to start station $stationId", e)
                VantaDiagnosticLog.error("Station", "start_failed stationId=$stationId", e)
                _uiState.update {
                    it.copy(streamingStationLoading = false, statusMessage = "Failed to start station: ${e.message}")
                }
            }
        }
    }

    fun onGenomeThumbsUp() {
        val currentTrack = _uiState.value.queueSnapshot.currentTrack
        if (currentTrack != null) {
            val sonicSession = container.queueManager.activeSonicRadioSession
            if (sonicSession != null || container.queueManager.queueMode == com.audiophile.musicplayer.playback.QueueMode.SONIC_RADIO) {
                viewModelScope.launch {
                    val sonic = container.sonicRadioService.resolveSonicTrack(currentTrack)
                    container.queueManager.onSonicThumbsUp(sonic)
                    setStatusMessage("Tuned station toward \"${currentTrack.track.title}\"")
                    _uiState.update { it.copy(queueSnapshot = container.queueManager.snapshot()) }
                }
            } else {
                activeGenomeEngine?.onThumbsUp(currentTrack)
                setStatusMessage("Tuned station toward \"${currentTrack.track.title}\"")
            }
        } else {
            setStatusMessage("Station tuned toward this sound")
        }
    }

    fun onGenomeThumbsDown() {
        val currentTrack = _uiState.value.queueSnapshot.currentTrack
        if (currentTrack != null) {
            val sonicSession = container.queueManager.activeSonicRadioSession
            if (sonicSession != null || container.queueManager.queueMode == com.audiophile.musicplayer.playback.QueueMode.SONIC_RADIO) {
                viewModelScope.launch {
                    val sonic = container.sonicRadioService.resolveSonicTrack(currentTrack)
                    container.queueManager.onSonicThumbsDown(sonic)
                    setStatusMessage("Skipping — avoiding this sound")
                    _uiState.update { it.copy(queueSnapshot = container.queueManager.snapshot()) }
                    container.playerController.next()
                }
                return
            } else {
                activeGenomeEngine?.onThumbsDown(currentTrack)
                setStatusMessage("Skipping — avoiding this sound")
            }
        } else {
            setStatusMessage("Skipping track")
        }

        viewModelScope.launch {
            val upcoming = container.queueManager.upcomingOriginalQueue()
            if (upcoming.isEmpty() && activeStationSeed != null) {
                try {
                    val seedParams = activeStationSeed ?: return@launch
                    val stationSeed = seedParams.toStationSeed()
                    val excludeIds = playedStationTrackIds.mapNotNull { it.toLongOrNull() }.toSet()
                    val newTracks = withContext(Dispatchers.IO) {
                        container.radioQueueEngine.refillStreamingStation(
                            request = com.audiophile.musicplayer.radio.StreamingStationRequest(
                                seed = stationSeed,
                                targetCount = 10,
                                minPlayableToStart = 1,
                                steerGenome = activeGenomeEngine?.currentStationGenome
                            ),
                            existingTrackIds = excludeIds
                        )
                    }
                    if (newTracks.isNotEmpty()) {
                        val rotatedTracks = applyGenomeRotation(newTracks)
                        container.queueManager.appendToOriginalQueueIfAbsent(rotatedTracks)
                        rotatedTracks.map { it.track.trackId.toString() }.forEach { id ->
                            if (id !in playedStationTrackIds) {
                                playedStationTrackIds.addLast(id)
                                if (playedStationTrackIds.size > 500) {
                                    playedStationTrackIds.removeFirst()
                                }
                            }
                        }
                        container.playerController.refreshQueueTimeline()
                        _uiState.update { it.copy(queueSnapshot = container.queueManager.snapshot()) }
                    }
                } catch (e: Exception) {
                    Log.w("VANTA_GENOME", "Emergency refill on thumbs down failed: ${e.message}")
                }
            }
            playNextFromQueue()
        }
    }



    /**
     * Passes freshly generated station tracks through the live genome engine's
     * Pandora-style rotation (artist separation, seeded share cap, thumb bans)
     * and records them as played so the next refill cannot repeat them. Without
     * this the engine's rotation rules were dead code.
     */
    private fun applyGenomeRotation(
        tracks: List<UnifiedTrackWithSources>
    ): List<UnifiedTrackWithSources> {
        val engine = activeGenomeEngine ?: return tracks
        if (tracks.isEmpty()) return tracks
        val rotated = engine.applyPandoraRotation(tracks, maxTracks = tracks.size)
        if (rotated.isEmpty()) return tracks
        rotated.forEach { track ->
            engine.recordTrackPlayed(track.track.title, track.track.artist, track.track.isrc)
        }
        return rotated
    }

    // ── Endless Station Refill Monitor ──



    private fun startStationMonitor() {
        stationMonitorJob = viewModelScope.launch {
            var consecutiveFailures = 0
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
                                    minPlayableToStart = 3,
                                    steerGenome = activeGenomeEngine?.currentStationGenome,
                                    discoveryMode = _radioDiscoveryMode.value
                                ),
                                existingTrackIds = excludeIds
                            )
                        }

                        if (newTracks.isNotEmpty()) {
                            consecutiveFailures = 0
                            val rotatedTracks = applyGenomeRotation(newTracks)
                            val added = container.queueManager.appendToOriginalQueueIfAbsent(rotatedTracks)
                            rotatedTracks.map { it.track.trackId.toString() }.forEach { id ->
                                if (id !in playedStationTrackIds) {
                                    playedStationTrackIds.addLast(id)
                                    if (playedStationTrackIds.size > 500) {
                                        playedStationTrackIds.removeFirst()
                                    }
                                }
                            }
                            container.playerController.refreshQueueTimeline()
                            _uiState.update { it.copy(queueSnapshot = container.queueManager.snapshot()) }
                            Log.d("VANTA_STATION_REFILL", "Added $added tracks. Queue is now endless.")
                        } else {
                            consecutiveFailures++
                            Log.d("VANTA_STATION_REFILL", "Refill returned 0 tracks, backoff level=$consecutiveFailures")
                        }
                    } catch (e: Exception) {
                        consecutiveFailures++
                        Log.w("VANTA_STATION_REFILL", "Refill failed, will retry later: ${e.message}")
                    } finally {
                        isRefillingStation = false
                    }
                }

                val sleepMs = when (consecutiveFailures) {
                    0 -> 2000L
                    1 -> 4000L
                    else -> 8000L
                }
                delay(sleepMs)
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

            activeStationSeed = seed.toStreamingSeedParams()

            playedStationTrackIds.clear()

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



            startStationMonitor()



            launch(Dispatchers.IO) {

                val expanded = container.radioQueueEngine.generateStreamingStation(

                    com.audiophile.musicplayer.radio.StreamingStationRequest(

                        seed = seed,

                        excludeTrackIds = container.queueManager.originalQueue.map { it.track.trackId }.toSet(),

                        playedTrackIds = container.queueManager.playedHistory.toSet(),

                        taste = container.aiDjRecommendationEngine.streamingTasteSignals(),

                        localEnrichment = localBonus,

                        targetCount = 45,

                        minPlayableToStart = 8,

                        steerGenome = activeGenomeEngine?.currentStationGenome

                    )

                )

                if (expanded.candidates.isNotEmpty()) {

                    val added = container.queueManager.appendToOriginalQueueIfAbsent(expanded.candidates)

                    newTracksFromExpansion(expanded.candidates)

                    Log.d("VANTA_STREAMING_STATION", "background_expand added=$added total=${expanded.candidates.size}")

                    _uiState.update { state ->

                        state.copy(queueSnapshot = container.queueManager.snapshot())

                    }

                }

            }

        }

    }



    private fun newTracksFromExpansion(tracks: List<UnifiedTrackWithSources>) {

        tracks.map { it.track.trackId.toString() }.forEach { id ->

            if (id !in playedStationTrackIds) {

                playedStationTrackIds.addLast(id)

                if (playedStationTrackIds.size > 500) {

                    playedStationTrackIds.removeFirst()

                }

            }

        }

    }



    private fun interleaveStationQueue(tracks: List<UnifiedTrackWithSources>): List<UnifiedTrackWithSources> {

        if (tracks.size <= 2) return tracks

        val buckets = tracks

            .groupBy { it.track.artist.trim().lowercase().ifBlank { "unknown" } }

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

        if (!track.isMusicContentAllowed()) {

            Log.w("VANTA_PLAYBACK_GUARD", "ui_blocked_non_music trackId=${track.track.trackId} title='${track.track.title}'")

            setStatusMessage("That result is not music and was removed.")

            return

        }

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



                container.playerController.playQueue(tracks, startIndex, com.audiophile.musicplayer.playback.QueueMode.NORMAL_QUEUE)

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

                Triple(playableTrack, metadata, tracks.size)

            } ?: return@launch



            val (playableTrack, metadata, queueSize) = result

            val stillSameTrack = _uiState.value.activeTrackId == playableTrack.track.trackId.toString()

            if (!stillSameTrack) {

                Log.d("VANTA_METADATA", "Discarded stale metadata for trackId=${playableTrack.track.trackId} (active=${_uiState.value.activeTrackId})")

            }

            _uiState.update {

                it.copy(
                    queueSnapshot = container.queueManager.snapshot().copy(isRadio = false),
                    activeTrackEnhancedMetadata = if (stillSameTrack) metadata else null,
                    statusMessage = "Playing ${DisplayMetadataCleaner.cleanTitle(playableTrack.track.title)}"
                )
            }

            if (queueSize <= 1) {
                autoFormUpcomingQueue(playableTrack)
            }
        }
    }

    private fun autoFormUpcomingQueue(seedTrack: UnifiedTrackWithSources) {
        viewModelScope.launch(Dispatchers.IO) {
            val title = seedTrack.track.title
            val artist = seedTrack.track.artist
            Log.d("VANTA_QUEUE_FORMING", "auto_form_start seedTitle='$title' seedArtist='$artist'")
            val seed = com.audiophile.musicplayer.radio.RadioSeed(
                title = title,
                artist = artist,
                album = seedTrack.track.albumName,
                genre = seedTrack.track.genre
            )
            val result = container.radioQueueEngine.generate(
                seed = seed,
                excludeTrackIds = setOf(seedTrack.track.trackId),
                playedTrackIds = emptySet()
            )
            if (result.candidates.isNotEmpty()) {
                val added = container.queueManager.appendToOriginalQueueIfAbsent(result.candidates.take(25))
                Log.d("VANTA_QUEUE_FORMING", "auto_form_success added=$added totalCandidates=${result.candidates.size}")
                withContext(Dispatchers.Main) {
                    _uiState.update {
                        it.copy(queueSnapshot = container.queueManager.snapshot())
                    }
                }
            } else {
                Log.w("VANTA_QUEUE_FORMING", "auto_form_empty reason='${result.failureReason}'")
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

            // Only resolve on the *owning* provider. Its externalTrackId lives in that

            // provider's ID namespace; reusing it against other providers (gateways strip the

            // prefix and look the bare id up in a different catalog) can resolve a *different*

            // recording and poison the stored externalProviderId/externalTrackId. When the

            // owning provider cannot resolve, we fall through to the metadata search below,

            // which verifies identity by title/artist/ISRC.

            val resolved = container.sourceRegistry.resolveStream(

                providerId,

                externalTrackId,

                timeoutMs = com.audiophile.musicplayer.data.source.CloudLibraryHelpers.CLOUD_RESOLVE_TIMEOUT_MS

            )

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

            if (!metadata?.album.isNullOrBlank()) add(listOf(title, artist, metadata.album).joinToString(" ").trim())

            metadata?.isrc?.takeIf { it.isNotBlank() }?.let(::add)

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

                userQuery = "$title $artist".trim()

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

                    statusMessage = "Source found for ${DisplayMetadataCleaner.cleanTitle(updated.track.title)}"

                )

            }

            return updated

        }



        // YouTube Music is no longer a playback source.

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

                    artist = nowPlaying.artist

                )

            }

            container.playerController.next()

            // Queue snapshot syncs reactively via playbackState observer

        }

    }

    fun toggleShuffle() {
        container.playerController.toggleShuffle()
        _uiState.update {
            it.copy(queueSnapshot = container.queueManager.snapshot())
        }
    }

    fun shuffleQueue() {
        container.playerController.shuffleQueue()
        _uiState.update {
            it.copy(queueSnapshot = container.queueManager.snapshot())
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

        if (container.playerController.hasCurrentMediaItem()) {

            container.playerController.togglePlayPause()

            return

        }



        viewModelScope.launch {

            val rememberedTrackId = container.nowPlayingStateStore.load()

                ?.trackId

                ?.toLongOrNull()

            val rememberedTrack = rememberedTrackId?.let { trackId ->

                withContext(Dispatchers.IO) {

                    container.trackRepository.getTrackWithSources(trackId)

                }

            }



            if (rememberedTrack != null) {

                Log.d(

                    "VANTA_PLAYBACK_TRACE",

                    "step='cold_resume_rehydrate' trackId=${rememberedTrack.track.trackId} title='${rememberedTrack.track.title}'"

                )

                playTrack(rememberedTrack)

            } else {

                Log.d(

                    "VANTA_PLAYBACK_TRACE",

                    "step='cold_resume_fallback' rememberedTrackId=$rememberedTrackId"

                )

                playFirstPlayableTrack()

            }

        }

    }



    fun seekTo(positionMs: Long) {

        android.util.Log.d("VANTA_SEEK_CHAIN", "MainViewModel.seekTo positionMs=$positionMs")

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

            _uiState.update { it.copy(statusMessage = "Importing file...") }

            val success = withContext(Dispatchers.IO) { container.localMediaImporter.importSingleUri(uri) }

            refreshAll()

            _uiState.update {

                it.copy(

                    statusMessage = if (success) "Successfully imported local track" else "Failed to import local track"

                )

            }

        }

    }



    /** Imports Spotify Extended Streaming History into ListeningHistory (local only). */

    fun importSpotifyHistory(uri: Uri) {

        viewModelScope.launch {

            setStatusMessage("Reading Spotify streaming history...")

            val (read, saved) = withContext(Dispatchers.IO) {

                val rows = appContext.contentResolver.openInputStream(uri)?.use {

                    com.audiophile.musicplayer.data.importer.SpotifyExportImporter.extractHistoryRows(it)

                }.orEmpty()

                val entries = rows.mapNotNull { row ->

                    runCatching {

                        com.audiophile.musicplayer.data.local.entities.ListeningHistoryEntity(

                            startedAt = row.ts,

                            title = row.trackName,

                            artist = row.artistName,

                            album = row.albumName,

                            platform = "SPOTIFY",

                            sourceTrackId = row.spotifyTrackUri,

                            msPlayed = row.msPlayed,

                            skipped = row.skipped,

                            reasonStart = row.reasonStart,

                            reasonEnd = row.reasonEnd

                        )

                    }.getOrNull()

                }

                container.listeningHistoryRepository.recordAll(entries)

                rows.size to entries.size

            }

            setStatusMessage(

                if (read == 0) {

                    "No streaming history found in that file. Import the Extended Streaming History ZIP from your Spotify account (Stored streaming)."

                } else {

                    "Imported $saved listening rows from Spotify history ($read parsed)"

                }

            )

        }

    }



    /** Imports an exported Apple Music / iTunes library (library.xml plist or library.json) into ListeningHistory. */

    fun importAppleLibrary(uri: Uri) {

        viewModelScope.launch {

            setStatusMessage("Reading Apple Music library export...")

            val (parsedTracks, saved, source) = withContext(Dispatchers.IO) {

                val text = appContext.contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use {

                    it.readBoundedText(50_000_000)

                }.orEmpty()

                val library = com.audiophile.musicplayer.data.importer.AppleLibraryImporter.parseLibrary(text)

                val entries = com.audiophile.musicplayer.data.importer.AppleLibraryImporter.toHistoryRows(library)

                container.listeningHistoryRepository.recordAll(entries)

                Triple(library?.tracks?.size ?: 0, entries.size, library?.source.orEmpty())

            }

            setStatusMessage(

                if (saved == 0) {

                    "No played tracks found in that Apple Music library export. Import a library.xml (iTunes) or library.json (Music) file with a play count."

                } else {

                    "Imported $saved listening rows from Apple Music ($parsedTracks tracks parsed from $source)"

                }

            )

        }

    }



    /** Imports a CSV/text list of loved songs and auto-likes them in the local library. */

    fun importLikedSongsCsvText(text: String) {

        viewModelScope.launch {

            if (text.isBlank()) {

                setStatusMessage("That file was empty. Export your loved songs as a CSV with Title and Artist columns.")

                return@launch

            }

            setStatusMessage("Reading loved-songs list...")

            val result = withContext(Dispatchers.IO) {

                com.audiophile.musicplayer.data.importer.LikedSongsCsvImporter(

                    container.localLibraryRepository

                ).importLikedCsv(text)

            }

            setStatusMessage(when {

                result.totalRows == 0 -> "No song rows found in that file. Expected a CSV with Title and Artist columns."

                else -> "Liked ${result.autoLikedCount} songs from CSV (${result.likedExisting} matched your library, ${result.addedNew} added as new favorites)"

            })

            refreshAll(skipRoomMaterialize = true)

        }

    }

    /** Loads yearly listening aggregates for the Wrapped surface. */

    fun refreshWrappedStats(year: Int) {

        viewModelScope.launch {

            val stats = withContext(Dispatchers.IO) {

                container.listeningHistoryRepository.yearStats(year)

            }

            _uiState.update { it.copy(wrappedStats = stats) }

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



    private var playSourceJob: kotlinx.coroutines.Job? = null

    fun playSourceResult(result: CanonicalTrack) {

        homePlaylistPlaybackJob?.cancel()
        playSourceJob?.cancel()

        if (ContentPurityFilter.isClearlyNonMusicContent(

                result.title,

                result.artist,

                result.album,

                result.durationMs

            )

        ) {

            Log.w("VANTA_PLAYBACK_GUARD", "catalog_blocked_non_music title='${result.title}' artist='${result.artist}'")

            setStatusMessage("That result is not music and was removed.")

            return

        }

        val tapMs = System.currentTimeMillis()

        val handoffGeneration = AcceptanceTruth.nextGeneration()

        val searchQuery = "${result.title} ${result.artist}".trim()

        Log.w("VANTA_PLAY_CLICK", "Tapped track: id=${result.externalTrackId}, provider=${result.sourceProviderId}")

        AcceptanceTruth.search(

            query = searchQuery,

            title = result.title,

            artist = result.artist,

            album = result.album,

            isrc = result.isrc,

            providerId = result.sourceProviderId,

            externalTrackId = result.externalTrackId,

            artwork = result.artworkUrl,

            durationMs = result.durationMs,

            canonicalTrackId = result.canonicalTrackId?.toString(),

            canonicalArtistId = result.canonicalArtistId?.toString(),

            canonicalAlbumId = result.canonicalAlbumId?.toString()

        )



        playSourceJob = viewModelScope.launch {

            try {

            val simplifiedTitle = DisplayMetadataCleaner.cleanTitle(result.title)

            setStatusMessage("Playing $simplifiedTitle")

            publishOptimisticNowPlaying(result)



            if (result.sourceProviderId.equals("local", ignoreCase = true) ||

                result.sourceStatus == SearchItemStatus.LOCAL_PLAYABLE

            ) {

                val localTrack = withContext(Dispatchers.IO) {

                    container.trackRepository.searchLibrary(result.title).firstOrNull { candidate ->

                        candidate.track.title.equals(result.title, ignoreCase = true) &&

                            candidate.track.artist.equals(result.artist, ignoreCase = true) &&

                            candidate.sources.any { source ->

                                source.sourceType == SourceType.LOCAL && source.streamUrl.isNotBlank()

                            }

                    }

                }

                if (localTrack != null) {

                    Log.d(

                        "VANTA_PLAY_CLICK",

                        "local_reuse trackId=${localTrack.track.trackId} title='${localTrack.track.title}'"

                    )

                    playTrack(localTrack)

                    return@launch

                }

            }



            val resolved = withContext(Dispatchers.IO) {

                resolveCanonicalTrackForPlayback(result)

            } ?: run {

                Log.w("VANTA_PLAY_CLICK", "No playable source for '${result.title}'")

                setStatusMessage("No playable source found for $simplifiedTitle")

                container.playbackStateHolder.update { copy(isBuffering = false, errorMessage = "Could not play $simplifiedTitle. Try again.") }

                return@launch

            }



            val (playable, resolvedStream) = resolved

            val sourceCleanTitle = simplifiedTitle

            Log.d("VANTA_PLAY_CLICK", "Using source: providerId=${playable.providerId} trackId=${playable.id} status=${playable.status}")

            Log.d("VANTA_PLAY_CLICK", "Stream resolved: host=${VantaLogger.urlHost(resolvedStream.streamUrl)} bitrate=${resolvedStream.bitrateKbps}kbps")



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

                artist = result.displayArtist,

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

                val userQuery = "${result.title} ${result.artist}".trim()

                AcceptanceTruth.handoff(

                    generation = handoffGeneration,

                    searchTitle = result.title,

                    searchArtist = result.artist,

                    searchIsrc = result.isrc ?: playable.isrc,

                    preferredProvider = playable.providerId,

                    preferredExternalId = playable.id,

                    resolvedTrackId = track.track.trackId,

                    album = result.album

                )

                container.playerController.playQueue(

                    tracks = queue,

                    startIndex = startIndex,

                    mode = com.audiophile.musicplayer.playback.QueueMode.NORMAL_QUEUE,

                    resolvedStream = resolvedStream,

                    preferredProviderId = playable.providerId,

                    preferredExternalTrackId = playable.id,

                    userQuery = userQuery,

                    isFavorite = false,

                    canonicalTrackId = result.canonicalTrackId?.toString()

                )

                val pending = NowPlayingState.pendingPlayback(

                    track = track,

                    queuePosition = startIndex,

                    queueSize = queue.size,

                    preferredProviderId = playable.providerId,

                    preferredExternalTrackId = playable.id,

                    userQuery = userQuery,

                    isFavorite = false,

                    canonicalTrackId = result.canonicalTrackId?.toString(),

                    canonicalArtistId = result.canonicalArtistId?.toString(),

                    canonicalAlbumId = result.canonicalAlbumId?.toString()

                ).copy(

                    title = result.title,

                    artist = result.artist,

                    album = result.album,

                    isrc = result.isrc ?: track.track.isrc ?: playable.isrc,

                    artworkUrl = result.artworkUrl,

                    durationMs = result.durationMs ?: track.track.durationMs ?: playable.durationMs ?: 0L,

                    qualityInfo = result.qualityInfo

                )

                container.nowPlayingStateStore.save(pending)

                container.playbackStateHolder.replace(pending)

                _uiState.update {

                    it.copy(

                        activeTrackId = track.track.trackId.toString(),

                        activeTrackEnhancedMetadata = null,

                        queueSnapshot = container.queueManager.snapshot().copy(isRadio = false),

                        statusMessage = "Playing ${DisplayMetadataCleaner.cleanTitle(track.track.title)}"

                    )

                }



                launch(Dispatchers.IO) {

                    enrichNowPlayingIdentity(result, playable, track)

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

                    } catch (e: java.io.IOException) {

                        Log.w("VANTA_QUEUE_EXPAND", "error=io seed='${result.title}' reason='${e.message}'")

                    } catch (e: android.database.SQLException) {

                        Log.w("VANTA_QUEUE_EXPAND", "error=sql seed='${result.title}' reason='${e.message}'")

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

                artist = result.displayArtist,

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

        val userToken = container.connectedLibraryTokenStore.musicUserToken(

            com.audiophile.musicplayer.data.connectors.ConnectedLibraryProvider.APPLE_MUSIC

        )

        val storefront = container.resolverConfigStore.getAppleMusicStorefront()

        val devToken = container.connectedLibraryTokenStore.accessToken(

            com.audiophile.musicplayer.data.connectors.ConnectedLibraryProvider.APPLE_MUSIC

        ) ?: container.resolverConfigStore.getAppleMusicDeveloperToken()

        

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

        val userToken = container.connectedLibraryTokenStore.musicUserToken(

            com.audiophile.musicplayer.data.connectors.ConnectedLibraryProvider.APPLE_MUSIC

        )

        val devToken = container.connectedLibraryTokenStore.accessToken(

            com.audiophile.musicplayer.data.connectors.ConnectedLibraryProvider.APPLE_MUSIC

        ) ?: container.resolverConfigStore.getAppleMusicDeveloperToken()

        val storefront = container.resolverConfigStore.getAppleMusicStorefront()



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

        }

    }



    fun playSongRadio(

        title: String,

        artist: String,

        album: String?,

        genre: String?,

        seedTrack: UnifiedTrackWithSources? = null

    ) {

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

            val resolvedSeed = seedTrack

                ?.takeIf { it.sources.any { source -> source.streamUrl.isNotBlank() } }

                ?: library.firstOrNull { track ->

                    track.sources.any { it.streamUrl.isNotBlank() } &&

                        track.track.title.equals(title, ignoreCase = true) &&

                        track.track.artist.equals(artist, ignoreCase = true)

                }

            val seedId = resolvedSeed?.track?.trackId

            // Fresh Song Radio must not inherit a giant prior queue as exclusions —

            // that starves the engine (engine_generated=0) after long radio sessions.

            withContext(Dispatchers.IO) {

                container.queueManager.clearOriginalQueue()

            }

            val playedIds = container.queueManager.playedHistory.toSet()

            val excludeIds = playedIds + setOfNotNull(seedId)

            Log.d(

                "VANTA_RADIO_ENGINE",

                "excluded_recent count=${playedIds.size} currentQueue=0 seedId=$seedId"

            )



            // 1) Generate via provider engine

            val engineResult = withContext(Dispatchers.IO) {

                container.radioQueueEngine.generate(

                    com.audiophile.musicplayer.radio.RadioSeed(title, artist, album, genre),

                    excludeTrackIds = excludeIds,

                    playedTrackIds = playedIds

                )

            }

            var candidates = engineResult.candidates

            Log.d("VANTA_RADIO_TRUTH", "engine_generated=${candidates.size}")



            // 2) Supplement from local library if engine returned few or none

            if (candidates.size < 10) {

                val seenNorm = candidates.mapTo(mutableSetOf()) {

                    "${it.track.title.lowercase()}|${it.track.artist.lowercase()}"

                }

                val seedNorm = seedKey

                val localMatches = library.filter { track ->

                    if (!track.sources.any { it.streamUrl.isNotBlank() }) return@filter false

                    val t = track.track

                    val norm = "${t.title.lowercase()}|${t.artist.lowercase()}"

                    if (norm == seedNorm) return@filter false

                    if (seedId != null && t.trackId == seedId) return@filter false

                    if (t.trackId in playedIds) return@filter false

                    if (norm in seenNorm) return@filter false

                    if (com.audiophile.musicplayer.radio.PlaybackIdentityGate.verify(track) is

                        com.audiophile.musicplayer.radio.GateVerdict.Failed

                    ) {

                        return@filter false

                    }

                    t.artist.equals(artist, ignoreCase = true) ||

                        (album != null && t.albumName.equals(album, ignoreCase = true)) ||

                        (genre != null && t.genre?.equals(genre, ignoreCase = true) == true)

                }

                Log.d("VANTA_RADIO_TRUTH", "local_supplement=${localMatches.size}")

                candidates = (candidates + localMatches).distinctBy { it.track.trackId }

            }



            val cleanPlayable = candidates

                .filter { it.sources.any { s -> s.streamUrl.isNotBlank() } }

                .filter { seedId == null || it.track.trackId != seedId }

                .filter {

                    com.audiophile.musicplayer.radio.PlaybackIdentityGate.verify(it) !is

                        com.audiophile.musicplayer.radio.GateVerdict.Failed

                }

                .filter { track ->

                    !com.audiophile.musicplayer.radio.SongRadioRelatedness.isArtistNameCollision(

                        artist,

                        track.track.title,

                        track.track.artist,

                        track.track.albumName

                    ) &&

                        !com.audiophile.musicplayer.radio.SongRadioRelatedness.isWeakTitleTokenSpam(

                            title,

                            artist,

                            track.track.title,

                            track.track.artist

                        ) &&

                        !com.audiophile.musicplayer.radio.SongRadioRelatedness.isForeignHitCover(

                            title,

                            track.track.title,

                            track.track.artist,

                            artist

                        ) &&

                        !com.audiophile.musicplayer.radio.SongRadioRelatedness.isListicleOrCompilationAlbum(

                            track.track.title,

                            track.track.artist,

                            track.track.albumName

                        )

                }

            val similarRequired = if (resolvedSeed != null) 4 else 5

            if (cleanPlayable.size < similarRequired) {

                Log.d(

                    "VANTA_RADIO_TRUTH",

                    "failed reason='not_enough_clean_tracks' total=${candidates.size} " +

                        "playable=${cleanPlayable.size} minRequired=$similarRequired seedPresent=${resolvedSeed != null}"

                )

                _uiState.update { it.copy(statusMessage = "Radio is still learning from this song.") }

                lastRadioSeedKey = null

                return@launch

            }



            val selected = buildList {

                if (resolvedSeed != null) add(resolvedSeed)

                addAll(cleanPlayable.distinctBy { it.track.trackId })

            }.distinctBy { it.track.trackId }.take(30)

            Log.d(

                "VANTA_RADIO_TRUTH",

                "about_to_start_queue branch='playSongRadio' count=${selected.size} " +

                    "seedFirst=${resolvedSeed != null} first='${selected.firstOrNull()?.track?.title}'"

            )

            AcceptanceTruth.radioSeed(

                seedTrackId = seedId,

                seedTitle = title,

                seedArtist = artist,

                queueSize = selected.size,

                queueIndex = 0,

                firstTitle = selected.firstOrNull()?.track?.title

            )

            val seedSource = resolvedSeed?.sources

                ?.filter { !it.externalProviderId.isNullOrBlank() && !it.externalTrackId.isNullOrBlank() }

                ?.maxByOrNull { it.bitrate }

            container.playerController.playQueue(

                tracks = selected,

                startIndex = 0,

                mode = com.audiophile.musicplayer.playback.QueueMode.RADIO_QUEUE,

                preferredProviderId = seedSource?.externalProviderId,

                preferredExternalTrackId = seedSource?.externalTrackId,

                userQuery = "$title $artist".trim()

            )

            val first = selected.first()

            container.nowPlayingStateStore.save(

                NowPlayingState.pendingPlayback(

                    track = first,

                    queuePosition = 0,

                    queueSize = selected.size,

                    preferredProviderId = seedSource?.externalProviderId,

                    preferredExternalTrackId = seedSource?.externalTrackId,

                    userQuery = "$title $artist".trim()

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

    fun playSonicRadio(
        seedTrack: UnifiedTrackWithSources? = null,
        seedTitle: String? = null,
        seedArtist: String? = null
    ) {
        viewModelScope.launch {
            _uiState.update { it.copy(statusMessage = "Matching Sonic DNA...") }
            val library = _uiState.value.library
            val resolvedSeed = seedTrack ?: library.firstOrNull {
                it.track.title.equals(seedTitle, ignoreCase = true) &&
                (seedArtist.isNullOrBlank() || it.track.artist.equals(seedArtist, ignoreCase = true))
            } ?: library.firstOrNull()

            if (resolvedSeed == null) {
                _uiState.update { it.copy(statusMessage = "No seed track available for Sonic Radio") }
                return@launch
            }

            val sonicSeed = container.sonicRadioService.resolveSonicTrack(resolvedSeed)

            val librarySonic = library.map { track ->
                val genres = track.track.genre?.split(",", ";", "/")?.map { it.trim() }?.filter { it.isNotEmpty() }?.toSet() ?: emptySet()
                val vector = com.audiophile.musicplayer.radio.sonic.AudioFeatureExtractor().estimateFromMetadata(
                    title = track.track.title,
                    artist = track.track.artist,
                    genres = genres
                )
                com.audiophile.musicplayer.radio.sonic.SonicTrack.fromUnifiedTrack(track, vector, genres)
            }

            val session = container.sonicRadioService.createRadioSession(sonicSeed, librarySonic)
            withContext(Dispatchers.IO) {
                container.queueManager.setSonicRadioSession(session)
            }

            val upcoming = session.getUpcomingQueue()
            val initialTracks = buildList {
                add(resolvedSeed)
                for (sonic in upcoming) {
                    val unified = sonic.rawUnifiedTrack ?: library.firstOrNull { it.track.trackId.toString() == sonic.id }
                    if (unified != null && unified.track.trackId != resolvedSeed.track.trackId) {
                        add(unified)
                    }
                }
            }.distinctBy { it.track.trackId }

            withContext(Dispatchers.IO) {
                container.queueManager.setOriginalQueue(
                    initialTracks,
                    startIndex = 0,
                    mode = com.audiophile.musicplayer.playback.QueueMode.SONIC_RADIO
                )
            }

            val seedSource = resolvedSeed.sources
                .filter { !it.externalProviderId.isNullOrBlank() && !it.externalTrackId.isNullOrBlank() }
                .maxByOrNull { it.bitrate }

            container.playerController.playQueue(
                tracks = initialTracks,
                startIndex = 0,
                mode = com.audiophile.musicplayer.playback.QueueMode.SONIC_RADIO,
                preferredProviderId = seedSource?.externalProviderId,
                preferredExternalTrackId = seedSource?.externalTrackId,
                userQuery = "${resolvedSeed.track.title} ${resolvedSeed.track.artist}".trim()
            )

            container.nowPlayingStateStore.save(
                NowPlayingState.pendingPlayback(
                    track = resolvedSeed,
                    queuePosition = 0,
                    queueSize = initialTracks.size,
                    preferredProviderId = seedSource?.externalProviderId,
                    preferredExternalTrackId = seedSource?.externalTrackId,
                    userQuery = "${resolvedSeed.track.title} ${resolvedSeed.track.artist}".trim()
                )
            )

            _uiState.update {
                it.copy(
                    queueSnapshot = container.queueManager.snapshot(),
                    statusMessage = "Sonic Radio: playing \"${resolvedSeed.track.title}\" by sonic DNA"
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

                    "${it.track.title.lowercase()}|${it.track.artist.lowercase()}"

                }

                val localMatches = library.filter { track ->

                    track.sources.any { it.streamUrl.isNotBlank() } &&

                        track.track.artist.equals(artistName, ignoreCase = true) &&

                        "${track.track.title.lowercase()}|${track.track.artist.lowercase()}" !in seenNorm &&

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

            val relatedTracks = cleanCandidates

                .filterNot { it.track.artist.equals(artistName, ignoreCase = true) }

            val selected = (exactArtistTracks + relatedTracks)

                .distinctBy { it.track.trackId }

                .take(30)

            if (selected.isEmpty()) {

                Log.d("VANTA_RADIO_TRUTH", "failed reason='no_ranked_artist_tracks' artist='${artistName}'")

                _uiState.update { it.copy(statusMessage = "No studio tracks found for $artistName.") }

                return@launch

            }

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

                    statusMessage = if (exactArtistTracks.isEmpty()) {

                        "Artist Radio: $artistName and similar tracks"

                    } else {

                        "Artist Radio: $artistName"

                    }

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

        val musicTracks = tracks.filter { it.isMusicContentAllowed() }

        if (musicTracks.isEmpty()) {

            setStatusMessage("No music tracks were available in that selection.")

            return

        }

        val requested = tracks.getOrNull(startIndex)?.takeIf { it.isMusicContentAllowed() }

        val safeStartIndex = requested?.let { selected ->

            musicTracks.indexOfFirst { it.track.trackId == selected.track.trackId }.coerceAtLeast(0)

        } ?: 0

        val selectedTrack = musicTracks[safeStartIndex]

        val interimSnapshot = QueueSnapshot(

            originalQueue = musicTracks,

            currentOriginalIndex = safeStartIndex,

            currentTrack = selectedTrack,

            queueIndex = safeStartIndex,

            queueSize = musicTracks.size,

            canPlayNext = safeStartIndex < musicTracks.lastIndex,

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

        container.playerController.playQueue(musicTracks, safeStartIndex)
        if (musicTracks.size <= 1) {
            autoFormUpcomingQueue(selectedTrack)
        }
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

                trimmed.toUri()

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



    private val importPlaylistDestinations = java.util.concurrent.ConcurrentHashMap<Long, Long>()



    fun importPastedText(importName: String, pastedText: String, targetPlaylistId: Long? = null) {

        viewModelScope.launch {

            if (pastedText.isBlank()) {

                setStatusMessage("Paste tracks before parsing")

                return@launch

            }

            if (com.audiophile.musicplayer.data.importer.SoundiizTextParser.isEclipsePlaylistUrl(pastedText)) {

                importEclipsePlaylist(pastedText.trim())

                return@launch

            }

            if (com.audiophile.musicplayer.data.importer.GatewayPlaylistImporter.isGatewayPlaylistUrl(pastedText.trim())) {

                importGatewayPlaylist(pastedText.trim())

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

            if (targetPlaylistId != null) importPlaylistDestinations[batchId] = targetPlaylistId

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





    fun importEclipsePlaylist(url: String) {

        viewModelScope.launch {

            _uiState.update { it.copy(statusMessage = "Importing playlist…") }

            val result = withContext(Dispatchers.IO) {

                container.eclipsePlaylistImporter.import(url)

            }

            result.onSuccess { importResult ->

                val counts = withContext(Dispatchers.IO) { container.localLibraryRepository.libraryCountsSnapshot() }

                _uiState.update {

                    it.copy(

                        localLibraryCounts = counts,

                        statusMessage = "Imported ${importResult.playlistName}: ${importResult.matched} matched, ${importResult.unmatched} to resolve"

                    )

                }

                refreshAll()

            }.onFailure { error ->

                Log.e("VANTA_ECLIPSE_IMPORT", "Import failed", error)

                _uiState.update {

                    it.copy(

                        statusMessage = "Could not import playlist: ${error.localizedMessage ?: error.javaClass.simpleName}"

                    )

                }

            }

        }

    }

    fun importGatewayPlaylist(url: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(statusMessage = "Importing playlist link…") }
            val result = withContext(Dispatchers.IO) {
                container.gatewayPlaylistImporter.import(url)
            }
            result.onSuccess { importResult ->
                val counts = withContext(Dispatchers.IO) { container.localLibraryRepository.libraryCountsSnapshot() }
                _uiState.update {
                    it.copy(
                        localLibraryCounts = counts,
                        statusMessage = "Imported ${importResult.playlistName}: ${importResult.matched} matched, ${importResult.unmatched} to resolve",
                    )
                }
                refreshAll()
            }.onFailure { error ->
                Log.e("VANTA_GATEWAY_IMPORT", "Import failed", error)
                _uiState.update {
                    it.copy(
                        statusMessage = "Could not import playlist: ${error.localizedMessage ?: error.javaClass.simpleName}",
                    )
                }
            }
        }
    }



    fun playPlaylist(playlistId: Long, shuffle: Boolean = false) {

        viewModelScope.launch {

            _uiState.update { it.copy(statusMessage = "Loading playlist…") }

            val (localSongs, playableTracks) = withContext(Dispatchers.IO) {

                val songs = container.localLibraryRepository.playlistSongsSnapshot(playlistId)

                songs to buildPlayableLocalSongQueue(songs)

            }

            if (localSongs.isEmpty()) {

                _uiState.update { it.copy(statusMessage = "Playlist is empty") }

                return@launch

            }

            if (playableTracks.isEmpty()) {

                _uiState.update { it.copy(statusMessage = "No playable tracks in playlist. Repair the unmatched tracks first.") }

                return@launch

            }

            val ordered = if (shuffle) playableTracks.shuffled() else playableTracks

            val skippedCount = (localSongs.size - ordered.size).coerceAtLeast(0)

            playQueue(ordered, 0)

            _uiState.update {

                it.copy(

                    statusMessage = if (skippedCount > 0) {

                        "Playing playlist (${ordered.size}/${localSongs.size} playable)"

                    } else {

                        "Playing playlist (${ordered.size} tracks)"

                    }

                )

            }

        }

    }



    fun playLocalSong(song: LocalSongEntity) {

        viewModelScope.launch {

            val playableTrack = withContext(Dispatchers.IO) {

                buildPlayableLocalSongQueue(listOf(song)).firstOrNull()

            }

            if (playableTrack == null) {

                _uiState.update {

                    it.copy(

                        statusMessage = "\"${DisplayMetadataCleaner.cleanTitle(song.title)}\" is unavailable. Repair the track or add a playable source."

                    )

                }

                return@launch

            }

            playTrack(playableTrack)

        }

    }



    fun deletePlaylist(playlistId: Long) {

        viewModelScope.launch {

            withContext(Dispatchers.IO) {

                container.localLibraryRepository.deletePlaylist(playlistId)

            }

            refreshAll()

            _uiState.update { it.copy(statusMessage = "Playlist deleted") }

        }

    }



    fun addSelectedSongs(songIds: List<Long>, playlistId: Long?) {

        viewModelScope.launch {

            if (playlistId == null) container.localLibraryRepository.likeSongs(songIds)

            else container.localLibraryRepository.addSongsToPlaylist(playlistId, songIds)

            refreshAll()

            setStatusMessage("Added ${songIds.distinct().size} songs to ${if (playlistId == null) "Liked Songs" else "playlist"}")

        }

    }



    fun loadPlaylistTracks(playlistId: Long) {

        viewModelScope.launch {

            val playlist = withContext(Dispatchers.IO) {

                container.localLibraryRepository.playlistsSnapshot().find { it.id == playlistId }

            }

            val tracks = withContext(Dispatchers.IO) {

                container.localLibraryRepository.playlistSongsSnapshot(playlistId)

            }

            _uiState.update {

                it.copy(

                    playlistDetailTracks = tracks,

                    activePlaylistId = playlistId,

                    activePlaylistName = playlist?.name ?: "",

                    activePlaylistArtworkUrl = playlist?.artworkUrl,

                    activePlaylistDescription = playlist?.description

                )

            }

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

                importPlaylistDestinations[row.batchId]?.let { destination ->

                    if (localId > 0L) container.localLibraryRepository.addSongsToPlaylist(destination, listOf(localId))

                }

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

                    importPlaylistDestinations[row.batchId]?.let { destination ->

                        container.localLibraryRepository.addSongsToPlaylist(destination, savedIds.filter { it > 0L })

                    }

                    existingSongs += metadataSong.copy(id = savedIds.firstOrNull() ?: 0L)

                    savedMetadata += 1

                } else {

                    skippedDuplicates += 1

                }

                updatedRows += row.copy(

                    matchStatus = ImportMatchStatus.NEEDS_REVIEW,

                    playabilityStatus = PlayabilityStatus.NEEDS_REVIEW,

                    matchConfidence = if (row.matchConfidence == MatchConfidence.NONE) MatchConfidence.LOW else row.matchConfidence,

                    matchReason = row.matchReason ?: "Saved as metadata only; playable source still needs review",

                    friendlySourceLabel = row.friendlySourceLabel ?: "Metadata",

                    confidenceScore = if (row.confidenceScore <= 0f) 0.45f else row.confidenceScore.coerceAtMost(0.6f),

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

        val identity = SelectedRecordingIdentity(

            title = title,

            artist = artist

        )

        val candidates = SourceCandidateRanker.rankSearchResults(

            identity,

            container.sourceRegistry.searchAll(query, timeoutMs = 12_000L)

                .filter { it.status.canResolveStream() }

                .filter { it.isLikelyMusicTrack() }

        ).take(8)



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

            val live = container.playbackStateHolder.snapshot()

            val nowPlaying = if (live.trackId != null || !live.title.isNullOrBlank()) {

                live

            } else {

                container.nowPlayingStateStore.load()

            } ?: return@launch

            val title = nowPlaying.title ?: run { setStatusMessage("Nothing playing"); return@launch }

            val artist = nowPlaying.artist ?: "Unknown Artist"

            val beforeLiked = nowPlaying.isFavorite

            val requestedAction = if (beforeLiked) "unlike" else "like"

            if (nowPlaying.artist != null) {

                withContext(Dispatchers.IO) {

                    container.aiDjRecommendationEngine.recordFavorite(

                        trackId = nowPlaying.trackId?.toLongOrNull() ?: 0L,

                        artist = nowPlaying.artist,

                        genre = null

                    )

                }

            }

            val result = withContext(Dispatchers.IO) {

                val songs = container.localLibraryRepository.allSongsSnapshot()

                val canonicalTrackId = nowPlaying.canonicalTrackId?.toLongOrNull()

                val resolved = com.audiophile.musicplayer.data.local.LocalSongIdentity.resolveMatch(

                    songs = songs,

                    isrc = nowPlaying.isrc,

                    title = title,

                    artist = artist,

                    canonicalTrackId = canonicalTrackId

                )

                val existing = resolved.song

                    ?: canonicalTrackId?.let { id ->

                        songs.firstOrNull { it.canonicalTrackId == id }

                    }

                    ?: nowPlaying.isrc?.let { container.localLibraryRepository.findSongByIsrc(it) }

                val matchedBy = when {

                    resolved.method != com.audiophile.musicplayer.data.local.LocalSongIdentity.MatchMethod.NONE ->

                        resolved.method.name

                    existing != null && canonicalTrackId != null && existing.canonicalTrackId == canonicalTrackId ->

                        "CANONICAL_TRACK_ID"

                    existing != null && !nowPlaying.isrc.isNullOrBlank() -> "ISRC"

                    existing != null -> "TITLE_ARTIST"

                    // First-time like inserts by graph/ISRC/title+artist — not a failed match.

                    canonicalTrackId != null -> "CANONICAL_TRACK_ID"

                    !nowPlaying.isrc.isNullOrBlank() -> "ISRC"

                    else -> "TITLE_ARTIST"

                }

                AcceptanceTruth.libraryAction(

                    trackId = nowPlaying.trackId,

                    isrc = nowPlaying.isrc,

                    title = title,

                    artist = artist,

                    before = beforeLiked,

                    requestedAction = requestedAction,

                    after = null,

                    matchedBy = matchedBy

                )

                val localLibraryId: Long

                val newFavorite: Boolean

                if (existing != null) {

                    localLibraryId = existing.id

                    if (existing.canonicalTrackId == null && canonicalTrackId != null) {

                        container.localLibraryRepository.saveSongs(

                            listOf(existing.copy(canonicalTrackId = canonicalTrackId, updatedAt = System.currentTimeMillis()))

                        )

                    }

                    container.localLibraryRepository.toggleFavorite(localLibraryId)

                    val updated = container.localLibraryRepository.songById(localLibraryId)

                    newFavorite = updated?.isFavorite ?: !existing.isFavorite

                } else {

                    val savedIds = container.localLibraryRepository.saveSongs(

                        listOf(

                            LocalSongEntity(

                                title = title,

                                artist = artist,

                                album = nowPlaying.album,

                                artworkUrl = nowPlaying.artworkUrl,

                                durationMs = nowPlaying.durationMs.takeIf { it > 0 },

                                isrc = nowPlaying.isrc,

                                canonicalTrackId = canonicalTrackId,

                                isFavorite = true

                            )

                        )

                    )

                    if (savedIds.isNotEmpty()) {

                        localLibraryId = savedIds.first()

                        newFavorite = true

                    } else {

                        localLibraryId = -1L

                        newFavorite = false

                    }

                }

                val updatedState = nowPlaying.copy(isFavorite = newFavorite)

                container.nowPlayingStateStore.save(updatedState)

                container.playbackStateHolder.replace(updatedState)

                Triple(localLibraryId, newFavorite, matchedBy)

            }

            refreshAll()

            val (localLibraryId, newFavorite, matchedBy) = result

            if (newFavorite) {

                val isrc = nowPlaying.isrc ?: container.playbackStateHolder.snapshot().isrc

                container.connectedLibraryManager.syncLike(

                    localTrackId = localLibraryId,

                    title = title,

                    artist = artist,

                    isrc = isrc

                )

            }

            setStatusMessage(if (newFavorite) "Liked" else "Unliked")

            AcceptanceTruth.libraryAction(

                trackId = nowPlaying.trackId,

                isrc = nowPlaying.isrc,

                title = title,

                artist = artist,

                before = beforeLiked,

                requestedAction = requestedAction,

                after = newFavorite,

                matchedBy = matchedBy

            )

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

            // Keep TV library fresh when devices are linked.
            if (container.deviceLibrarySyncManager.pairCode.value.length in 4..8) {
                viewModelScope.launch(Dispatchers.IO) {
                    runCatching { container.deviceLibrarySyncManager.pushFromThisDevice() }
                }
            }

            Log.d("VANTA_SNACKBAR", "message=${msg}")

            } finally { onComplete?.invoke() }

        }

    }



    fun toggleFavoriteForSong(songId: Long) {

        viewModelScope.launch {

            val song = withContext(Dispatchers.IO) { container.localLibraryRepository.songById(songId) }

            withContext(Dispatchers.IO) { container.localLibraryRepository.toggleFavorite(songId) }

            if (song != null) {

                container.connectedLibraryManager.syncLike(

                    localTrackId = songId,

                    title = song.title,

                    artist = song.artist,

                    isrc = song.isrc

                )

            }

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

        return _uiState.value.library

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



    private suspend fun buildPlayableLocalSongQueue(songs: List<LocalSongEntity>): List<UnifiedTrackWithSources> {

        materializeSongsForPlayback(songs)

        val tracksByLocalId = container.trackRepository.getAllTracks()

            .asSequence()

            .mapNotNull { track -> track.track.localLibraryId?.let { id -> id to track } }

            .toMap()

        return songs.mapNotNull { song ->

            val materialized = tracksByLocalId[song.id]

            when {

                materialized?.sourceValidityStatus()?.canEnterPlaybackFlow() == true -> materialized

                else -> song.toPlayableQueueItem()?.takeIf { it.sourceValidityStatus().canEnterPlaybackFlow() }

            }

        }.distinctBy { it.track.localLibraryId?.let { id -> "local:$id" } ?: "track:${it.track.trackId}" }

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

        if (ContentPurityFilter.isClearlyNonMusicContent(

                result.title,

                result.artist,

                result.album,

                result.durationMs

            )

        ) return null

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

            userQuery = searchQuery

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

                            genre = track.track.genre,

                            seedTrack = track

                        )

                    } else if (context is VantaActionContext.Artist) {

                        Log.d("VANTA_ACTION_HANDLE", "action='START_RADIO' artist='${context.artistName}' result='success'")

                        playArtistRadio(context.artistName)

                    }

                }

                VantaActionSheetAction.START_SONIC_RADIO -> {

                    if (track != null) {

                        Log.d("VANTA_ACTION_HANDLE", "action='START_SONIC_RADIO' seed='${track.track.title}' result='success'")

                        playSonicRadio(seedTrack = track)

                    } else if (context is VantaActionContext.Artist) {

                        Log.d("VANTA_ACTION_HANDLE", "action='START_SONIC_RADIO' artist='${context.artistName}' result='success'")

                        playSonicRadio(seedArtist = context.artistName)

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

                VantaActionSheetAction.SHUFFLE_QUEUE -> {
                    shuffleQueue()
                    setStatusMessage("Queue shuffled")
                    Log.d("VANTA_ACTION_HANDLE", "action='SHUFFLE_QUEUE' result='success'")
                }
                VantaActionSheetAction.VIEW_FILE_INFO -> {
                    // Handled in UI
                }
            }

        }

    }

}

