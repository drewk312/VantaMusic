package com.audiophile.musicplayer.ui

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.audiophile.musicplayer.AppContainer
import com.audiophile.musicplayer.common.VantaLogger
import com.audiophile.musicplayer.data.local.entities.UnifiedTrackWithSources
import com.audiophile.musicplayer.playback.NowPlayingState
import com.audiophile.musicplayer.playback.QueueSnapshot
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class PlayerUiState(
    val nowPlaying: NowPlayingState? = null,
    val queueSnapshot: QueueSnapshot = QueueSnapshot(),
    val activeTrackId: String? = null,
    val statusMessage: String? = null,
)

sealed class PlayerUiEvent {
    data object TogglePlayPause : PlayerUiEvent()
    data class SeekTo(val positionMs: Long) : PlayerUiEvent()
    data object SkipNext : PlayerUiEvent()
    data object SkipPrevious : PlayerUiEvent()
    data object Stop : PlayerUiEvent()
    data class PlayTrack(val track: UnifiedTrackWithSources) : PlayerUiEvent()
    data class AddToQueue(val track: UnifiedTrackWithSources) : PlayerUiEvent()
    data class PlayNext(val track: UnifiedTrackWithSources) : PlayerUiEvent()
    data class RemoveUpNext(val index: Int) : PlayerUiEvent()
    data class MoveUpNext(val fromIndex: Int, val toIndex: Int) : PlayerUiEvent()
}

/**
 * Owns transport controls: play/pause, seek, skip, stop, queue manipulation.
 * Observes [PlaybackStateHolder] reactively to keep [PlayerUiState] in sync.
 *
 * Previously embedded in [MainViewModel]; extracted for single-responsibility.
 */
@HiltViewModel
class PlayerViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val container: AppContainer
) : ViewModel() {

    private val _uiState = MutableStateFlow(PlayerUiState())
    val uiState: StateFlow<PlayerUiState> = _uiState.asStateFlow()

    init {
        // Reactively sync queue snapshot and track-id when playback state changes
        viewModelScope.launch {
            container.playbackStateHolder.state.collect { state ->
                if (state.trackId != _uiState.value.activeTrackId) {
                    val qs = container.queueManager.snapshot()
                    _uiState.update {
                        it.copy(
                            nowPlaying = state,
                            activeTrackId = state.trackId,
                            queueSnapshot = qs
                        )
                    }
                }
                if (!state.errorMessage.isNullOrBlank()) {
                    _uiState.update { it.copy(statusMessage = state.errorMessage) }
                }
            }
        }
        // Restore recent history for radio exclusion
        viewModelScope.launch(Dispatchers.IO) {
            val recentIds = container.trackRepository.getRecentTrackIds(10)
            container.queueManager.loadPlayedHistory(recentIds)
            VantaLogger.d(VantaLogger.Tag.QUEUE, "history_restored count=${recentIds.size}")
        }
    }

    fun onEvent(event: PlayerUiEvent) {
        when (event) {
            is PlayerUiEvent.TogglePlayPause -> container.playerController.togglePlayPause()
            is PlayerUiEvent.SeekTo -> container.playerController.seekTo(event.positionMs)
            is PlayerUiEvent.SkipNext -> skipNext()
            is PlayerUiEvent.SkipPrevious -> container.playerController.previous()
            is PlayerUiEvent.Stop -> container.playerController.stop()
            is PlayerUiEvent.PlayTrack -> { /* delegate to MainViewModel for now */ }
            is PlayerUiEvent.AddToQueue -> addToQueue(event.track)
            is PlayerUiEvent.PlayNext -> playNext(event.track)
            is PlayerUiEvent.RemoveUpNext -> removeUpNext(event.index)
            is PlayerUiEvent.MoveUpNext -> moveUpNext(event.fromIndex, event.toIndex)
        }
    }

    private fun skipNext() {
        viewModelScope.launch {
            val nowPlaying = container.nowPlayingStateStore.load()
            if (nowPlaying != null && nowPlaying.artist != null) {
                container.aiDjRecommendationEngine.recordSkip(
                    trackId = nowPlaying.trackId?.toLongOrNull() ?: 0L,
                    artist = nowPlaying.artist ?: ""
                )
            }
            container.playerController.next()
        }
    }

    private fun addToQueue(track: UnifiedTrackWithSources) {
        viewModelScope.launch {
            container.queueManager.addToQueue(track)
            _uiState.update {
                it.copy(
                    queueSnapshot = container.queueManager.snapshot(),
                    statusMessage = "\"${track.track.title}\" added to queue"
                )
            }
        }
    }

    private fun playNext(track: UnifiedTrackWithSources) {
        viewModelScope.launch {
            container.queueManager.playNext(track)
            _uiState.update {
                it.copy(
                    queueSnapshot = container.queueManager.snapshot(),
                    statusMessage = "\"${track.track.title}\" will play next"
                )
            }
        }
    }

    private fun removeUpNext(index: Int) {
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

    private fun moveUpNext(fromIndex: Int, toIndex: Int) {
        viewModelScope.launch {
            container.queueManager.moveUpNextItem(fromIndex, toIndex)
            _uiState.update { it.copy(queueSnapshot = container.queueManager.snapshot()) }
        }
    }

    fun syncQueueSnapshot() {
        viewModelScope.launch {
            _uiState.update { it.copy(queueSnapshot = container.queueManager.snapshot()) }
        }
    }
}
