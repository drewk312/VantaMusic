package com.audiophile.musicplayer.ui

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.audiophile.musicplayer.AppContainer
import com.audiophile.musicplayer.common.VantaLogger
import com.audiophile.musicplayer.common.VantaResult
import com.audiophile.musicplayer.data.dj.JukeboxCatalog
import com.audiophile.musicplayer.data.dj.JukeboxTrackEligibility
import com.audiophile.musicplayer.data.dj.StreamingSeedParams
import com.audiophile.musicplayer.data.dj.toStreamingSeed
import com.audiophile.musicplayer.data.local.entities.UnifiedTrackWithSources
import com.audiophile.musicplayer.playback.NowPlayingState
import com.audiophile.musicplayer.playback.QueueMode
import com.audiophile.musicplayer.radio.StreamingStationRequest
import com.audiophile.musicplayer.radio.StreamingStationSeedResolver
import com.audiophile.musicplayer.radio.StreamingStationKind
import com.audiophile.musicplayer.radio.toStationSeed
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class StationUiState(
    val isLoading: Boolean = false,
    val statusMessage: String? = null,
    val activeStationName: String? = null,
    val error: String? = null,
)

sealed class StationUiEvent {
    data class StartJukeboxStation(val stationId: String, val shuffle: Boolean = false) : StationUiEvent()
    data class StartStreamingStation(val userInput: String, val shuffle: Boolean = false) : StationUiEvent()
    data object StopStation : StationUiEvent()
    data class PreviewStation(val stationId: String) : StationUiEvent()
}

/**
 * Owns AI station and radio playback: jukebox stations, streaming stations,
 * and the endless background refill monitor.
 *
 * Previously embedded in [MainViewModel]; extracted for single-responsibility.
 */
@HiltViewModel
class StationViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val container: AppContainer
) : ViewModel() {

    private val _uiState = MutableStateFlow(StationUiState())
    val uiState: StateFlow<StationUiState> = _uiState.asStateFlow()

    // -- Endless-station state -------------------------------------------------
    private var activeStationSeed: StreamingSeedParams? = null
    private val playedStationTrackIds = ArrayDeque<String>(500)
    private var isRefillingStation = false
    private var stationMonitorJob: Job? = null
    private val REFILL_THRESHOLD = 5

    fun onEvent(event: StationUiEvent) {
        when (event) {
            is StationUiEvent.StartJukeboxStation -> startJukeboxStation(event.stationId, event.shuffle)
            is StationUiEvent.StartStreamingStation -> startStreamingStation(event.userInput, event.shuffle)
            is StationUiEvent.StopStation -> stopStationMonitor()
            is StationUiEvent.PreviewStation -> { /* handled via suspend fun previewStation */ }
        }
    }

    suspend fun previewStation(stationId: String): List<UnifiedTrackWithSources> =
        container.radioStationPreviewLoader.previewTracks(stationId)

    fun startJukeboxStation(stationId: String, shuffle: Boolean = false) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, statusMessage = "Curating station...", error = null) }
            try {
                val station = JukeboxCatalog.resolveStation(stationId, container.customStationStore)
                if (station == null) {
                    _uiState.update { it.copy(isLoading = false, error = "Station not found") }
                    return@launch
                }
                val seedParams = station.toStreamingSeed()
                activeStationSeed = seedParams
                playedStationTrackIds.clear()
                stopStationMonitor()

                val stationSeed = seedParams.toStationSeed()
                val result = withContext(Dispatchers.IO) {
                    container.radioQueueEngine.generateStreamingStation(
                        StreamingStationRequest(
                            seed = stationSeed,
                            targetCount = 30,
                            minPlayableToStart = 3
                        )
                    )
                }

                if (!result.canStartPlayback) {
                    _uiState.update { it.copy(isLoading = false, error = "No tracks found for ${station.name}") }
                    return@launch
                }

                val finalTracks = if (shuffle) result.candidates.shuffled() else result.candidates
                finalTracks.map { it.track.trackId.toString() }.forEach { id ->
                    if (id !in playedStationTrackIds) {
                        playedStationTrackIds.addLast(id)
                        if (playedStationTrackIds.size > 500) playedStationTrackIds.removeFirst()
                    }
                }

                withContext(Dispatchers.Main) {
                    container.playerController.playQueue(finalTracks, 0, QueueMode.STREAMING_STATION)
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            activeStationName = station.name,
                            statusMessage = "Now playing: ${station.name}"
                        )
                    }
                }
                startStationMonitor()
            } catch (e: Exception) {
                VantaLogger.e(VantaLogger.Tag.STATION, "jukebox_start_failed stationId=$stationId", e)
                _uiState.update { it.copy(isLoading = false, error = "Failed to start station: ${e.message}") }
            }
        }
    }

    fun startStreamingStation(userInput: String, shuffle: Boolean = false) {
        val trimmed = userInput.trim()
        if (trimmed.isBlank()) return
        viewModelScope.launch {
            stopStationMonitor()
            _uiState.update { it.copy(isLoading = true, statusMessage = "Building station\u2026", error = null) }

            val seed = withContext(Dispatchers.IO) { StreamingStationSeedResolver.fromUserInput(trimmed) }
            val taste = withContext(Dispatchers.IO) { container.aiDjRecommendationEngine.streamingTasteSignals() }
            val playedIds = withContext(Dispatchers.IO) { container.queueManager.playedHistory.toSet() }
            val currentQueueIds = withContext(Dispatchers.IO) { container.queueManager.originalQueue.map { it.track.trackId }.toSet() }

            VantaLogger.d(VantaLogger.Tag.STATION, "start seed='${seed.displayName}' kind=${seed.kind}")

            val bootstrap = withContext(Dispatchers.IO) {
                container.radioQueueEngine.generateStreamingStation(
                    StreamingStationRequest(
                        seed = seed,
                        excludeTrackIds = currentQueueIds,
                        playedTrackIds = playedIds,
                        taste = taste,
                        targetCount = 20,
                        minPlayableToStart = 4
                    )
                )
            }

            if (!bootstrap.canStartPlayback) {
                VantaLogger.w(VantaLogger.Tag.STATION, "failed seed='${seed.displayName}' reason='${bootstrap.failureReason}'")
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = "Couldn't find playable tracks for ${seed.displayName}. Check your music sources in Settings."
                    )
                }
                return@launch
            }

            withContext(Dispatchers.IO) { container.queueManager.setStreamingStationSeed(seed) }

            val stationCandidates = bootstrap.candidates
                .filterNot { JukeboxTrackEligibility.shouldExcludeFromRadioQueue(it) }
                .let { clean ->
                    if (seed.kind == StreamingStationKind.ARTIST) {
                        val artistKey = seed.seedArtist?.trim().orEmpty().ifBlank {
                            seed.displayName.removeSuffix(" Radio").trim()
                        }
                        clean.sortedByDescending { track ->
                            if (track.track.artist.equals(artistKey, ignoreCase = true)) 1 else 0
                        }
                    } else clean
                }

            val playQueue = interleaveByArtist(if (shuffle) stationCandidates.shuffled() else stationCandidates)
            if (playQueue.isEmpty()) {
                withContext(Dispatchers.IO) { container.queueManager.setStreamingStationSeed(null) }
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = "Couldn't find playable tracks for ${seed.displayName}."
                    )
                }
                return@launch
            }

            container.playerController.playQueue(playQueue, 0, QueueMode.STREAMING_STATION)
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
                    isLoading = false,
                    activeStationName = seed.displayName,
                    statusMessage = "Playing ${seed.displayName}"
                )
            }

            // Background expand
            launch(Dispatchers.IO) {
                val expanded = container.radioQueueEngine.generateStreamingStation(
                    StreamingStationRequest(
                        seed = seed,
                        excludeTrackIds = container.queueManager.originalQueue.map { it.track.trackId }.toSet(),
                        playedTrackIds = container.queueManager.playedHistory.toSet(),
                        taste = taste,
                        targetCount = 45,
                        minPlayableToStart = 8
                    )
                )
                if (expanded.candidates.isNotEmpty()) {
                    val added = container.queueManager.appendToOriginalQueueIfAbsent(expanded.candidates)
                    VantaLogger.d(VantaLogger.Tag.STATION, "bg_expand added=$added total=${expanded.candidates.size}")
                }
            }
        }
    }

    private fun startStationMonitor() {
        stationMonitorJob = viewModelScope.launch {
            while (isActive && activeStationSeed != null) {
                val upcomingSize = container.queueManager.upcomingOriginalQueue().size
                if (upcomingSize < REFILL_THRESHOLD && !isRefillingStation) {
                    isRefillingStation = true
                    VantaLogger.d(VantaLogger.Tag.STATION, "refill_triggered upcoming=$upcomingSize")
                    try {
                        val seedParams = activeStationSeed ?: break
                        val stationSeed = seedParams.toStationSeed()
                        val excludeIds = playedStationTrackIds.mapNotNull { it.toLongOrNull() }.toSet()
                        val newTracks = withContext(Dispatchers.IO) {
                            container.radioQueueEngine.refillStreamingStation(
                                request = StreamingStationRequest(
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
                                    if (playedStationTrackIds.size > 500) playedStationTrackIds.removeFirst()
                                }
                            }
                            container.playerController.refreshQueueTimeline()
                            VantaLogger.d(VantaLogger.Tag.STATION, "refill_ok added=$added")
                        }
                    } catch (e: Exception) {
                        VantaLogger.w(VantaLogger.Tag.STATION, "refill_failed will_retry: ${e.message}")
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

    private fun interleaveByArtist(tracks: List<UnifiedTrackWithSources>): List<UnifiedTrackWithSources> {
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

    override fun onCleared() {
        super.onCleared()
        stopStationMonitor()
    }
}
