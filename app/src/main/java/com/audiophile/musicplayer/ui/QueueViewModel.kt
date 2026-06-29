package com.audiophile.musicplayer.ui

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.audiophile.musicplayer.AppContainer
import com.audiophile.musicplayer.common.VantaLogger
import com.audiophile.musicplayer.playback.QueueSnapshot
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class QueueUiState(
    val queueSnapshot: QueueSnapshot = QueueSnapshot(),
    val statusMessage: String? = null,
)

sealed class QueueUiEvent {
    data object Sync : QueueUiEvent()
    data class Remove(val index: Int) : QueueUiEvent()
    data class Move(val fromIndex: Int, val toIndex: Int) : QueueUiEvent()
    data object Clear : QueueUiEvent()
}

/**
 * Owns the queue state observable for screens that only need queue display.
 * Transport controls live in [PlayerViewModel]; this ViewModel focuses on
 * queue display and manipulation (reorder, remove).
 */
@HiltViewModel
class QueueViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val container: AppContainer
) : ViewModel() {

    private val _uiState = MutableStateFlow(QueueUiState())
    val uiState: StateFlow<QueueUiState> = _uiState.asStateFlow()

    init { sync() }

    fun onEvent(event: QueueUiEvent) {
        when (event) {
            is QueueUiEvent.Sync -> sync()
            is QueueUiEvent.Remove -> remove(event.index)
            is QueueUiEvent.Move -> move(event.fromIndex, event.toIndex)
            is QueueUiEvent.Clear -> clear()
        }
    }

    private fun sync() {
        viewModelScope.launch {
            _uiState.update { it.copy(queueSnapshot = container.queueManager.snapshot()) }
        }
    }

    private fun remove(index: Int) {
        viewModelScope.launch {
            container.queueManager.removeUpNextItem(index)
            _uiState.update {
                it.copy(
                    queueSnapshot = container.queueManager.snapshot(),
                    statusMessage = "Removed item from queue"
                )
            }
            VantaLogger.d(VantaLogger.Tag.QUEUE, "remove_upnext index=$index")
        }
    }

    private fun move(fromIndex: Int, toIndex: Int) {
        viewModelScope.launch {
            container.queueManager.moveUpNextItem(fromIndex, toIndex)
            _uiState.update { it.copy(queueSnapshot = container.queueManager.snapshot()) }
            VantaLogger.d(VantaLogger.Tag.QUEUE, "move_upnext from=$fromIndex to=$toIndex")
        }
    }

    private fun clear() {
        viewModelScope.launch {
            container.queueManager.clearOriginalQueue()
            _uiState.update { it.copy(queueSnapshot = QueueSnapshot(), statusMessage = "Queue cleared") }
            VantaLogger.d(VantaLogger.Tag.QUEUE, "queue_cleared")
        }
    }
}
