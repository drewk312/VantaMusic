package com.audiophile.musicplayer.data.dj

import com.audiophile.musicplayer.data.local.entities.UnifiedTrackWithSources
import com.audiophile.musicplayer.radio.RadioQueueEngine
import com.audiophile.musicplayer.radio.StreamingStationRequest
import com.audiophile.musicplayer.radio.StreamingStationSeedResolver
import com.audiophile.musicplayer.radio.StreamingStationTasteSignals
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

class RadioStationPreviewLoader(
    private val radioQueueEngine: RadioQueueEngine,
    private val tasteSignals: () -> StreamingStationTasteSignals = { StreamingStationTasteSignals() }
) {
    suspend fun previewTracks(
        station: JukeboxStation,
        limit: Int = 12
    ): List<UnifiedTrackWithSources> = withContext(Dispatchers.IO) {
        val seed = StreamingStationSeedResolver.fromJukeboxStation(station)
        loadPreview(seed, limit)
    }

    suspend fun previewTracks(stationId: String, limit: Int = 12): List<UnifiedTrackWithSources> {
        val station = JukeboxCatalog.resolveStation(stationId)
        if (station != null) return previewTracks(station, limit)
        val seed = StreamingStationSeedResolver.fromUserInput(stationId)
        return loadPreview(seed, limit)
    }

    suspend fun previewFromQuery(query: String, limit: Int = 12): List<UnifiedTrackWithSources> {
        val seed = StreamingStationSeedResolver.fromUserInput(query)
        return loadPreview(seed, limit)
    }

    private suspend fun loadPreview(
        seed: com.audiophile.musicplayer.radio.StreamingStationSeed,
        limit: Int
    ): List<UnifiedTrackWithSources> = withContext(Dispatchers.IO) {
        try {
            withTimeout(PREVIEW_TIMEOUT_MS) {
                val result = radioQueueEngine.generateStreamingStation(
                    StreamingStationRequest(
                        seed = seed,
                        taste = tasteSignals(),
                        targetCount = limit.coerceIn(1, 20),
                        minPlayableToStart = 1,
                        fastPreview = true
                    )
                )
                result.candidates.take(limit)
            }
        } catch (_: kotlinx.coroutines.TimeoutCancellationException) {
            emptyList()
        }
    }

    companion object {
        private const val PREVIEW_TIMEOUT_MS = 12_000L
    }
}
