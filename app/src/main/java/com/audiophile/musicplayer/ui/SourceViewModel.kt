package com.audiophile.musicplayer.ui

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.audiophile.musicplayer.AppContainer
import com.audiophile.musicplayer.common.VantaLogger
import com.audiophile.musicplayer.common.VantaResult
import com.audiophile.musicplayer.data.source.external.ExternalSourceConfig
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

/** Represents the health status of a single music source. */
data class SourceHealthEntry(
    val sourceId: String,
    val displayName: String,
    val result: VantaResult<String> = VantaResult.Loading
)

data class SourceViewUiState(
    val externalSources: List<ExternalSourceConfig> = emptyList(),
    val sourceHealth: List<SourceHealthEntry> = emptyList(),
    val isTestingAll: Boolean = false,
    val statusMessage: String? = null,
)

sealed class SourceUiEvent {
    data object Reload : SourceUiEvent()
    data object TestAll : SourceUiEvent()
    data class TestSingle(val sourceId: String) : SourceUiEvent()
    data class RemoveSource(val sourceId: String) : SourceUiEvent()
}

/**
 * Owns external source health tests and source lifecycle.
 * Previously mixed into [MainViewModel] alongside [SourceHealthHelper].
 */
@HiltViewModel
class SourceViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val container: AppContainer
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        SourceViewUiState(
            externalSources = container.externalSourceConfigStore.getSources()
        )
    )
    val uiState: StateFlow<SourceViewUiState> = _uiState.asStateFlow()

    fun onEvent(event: SourceUiEvent) {
        when (event) {
            is SourceUiEvent.Reload -> reload()
            is SourceUiEvent.TestAll -> testAll()
            is SourceUiEvent.TestSingle -> testSingle(event.sourceId)
            is SourceUiEvent.RemoveSource -> removeSource(event.sourceId)
        }
    }

    private fun reload() {
        _uiState.update {
            it.copy(externalSources = container.externalSourceConfigStore.getSources())
        }
    }

    private fun testAll() {
        val sources = container.externalSourceConfigStore.getSources()
        if (sources.isEmpty()) {
            _uiState.update { it.copy(statusMessage = "No sources configured") }
            return
        }
        _uiState.update {
            it.copy(
                isTestingAll = true,
                sourceHealth = sources.map { src ->
                    SourceHealthEntry(src.id, src.displayName, VantaResult.Loading)
                }
            )
        }
        sources.forEach { src -> testSingle(src.id) }
        viewModelScope.launch {
            _uiState.update { it.copy(isTestingAll = false) }
        }
    }

    private fun testSingle(sourceId: String) {
        viewModelScope.launch {
            try {
                val source = withContext(Dispatchers.IO) {
                    container.externalSourceConfigStore.getSources().find { it.id == sourceId }
                } ?: throw Exception("Source not found")
                
                val result = withContext(Dispatchers.IO) {
                    val provider = com.audiophile.musicplayer.data.source.external.PlaybackProviderFactory.createExternal(source)
                    val healthResult = provider.testHealth()
                    container.externalSourceConfigStore.updateSourceHealth(
                        id = sourceId,
                        healthStatus = healthResult.status,
                        lastError = healthResult.errorMessage,
                        avgSearchMs = healthResult.searchMs,
                        lastTestedAt = System.currentTimeMillis()
                    )
                    healthResult.manifest?.let { manifest ->
                        container.externalSourceConfigStore.updateSourceCapabilities(
                            id = sourceId,
                            capabilities = manifest.capabilities ?: emptyList(),
                            canSearch = manifest.canSearch ?: true,
                            canStream = manifest.canStream ?: true,
                            canBrowse = manifest.canBrowse ?: false,
                            canAlbum = manifest.types?.contains("album") ?: false,
                            canArtist = manifest.types?.contains("artist") ?: false,
                            canPlaylist = manifest.types?.contains("playlist") ?: false
                        )
                    }
                    healthResult.status == "healthy"
                }

                val msg = if (result) "OK" else "Failed"
                val vantaResult: VantaResult<String> = if (result) {
                    VantaResult.Success(msg)
                } else {
                    VantaResult.Error(msg)
                }
                updateHealthEntry(sourceId, vantaResult)
                VantaLogger.d(VantaLogger.Tag.SOURCE, "test_single sourceId=$sourceId result=$msg")
            } catch (e: Exception) {
                VantaLogger.e(VantaLogger.Tag.SOURCE, "test_failed sourceId=$sourceId", e)
                updateHealthEntry(sourceId, VantaResult.Error(e.message ?: "Error", e))
            }
        }
    }

    private fun updateHealthEntry(sourceId: String, result: VantaResult<String>) {
        _uiState.update { state ->
            val existing = state.sourceHealth
            val updated = if (existing.any { it.sourceId == sourceId }) {
                existing.map { if (it.sourceId == sourceId) it.copy(result = result) else it }
            } else {
                existing + SourceHealthEntry(sourceId, sourceId, result)
            }
            state.copy(sourceHealth = updated)
        }
    }

    private fun removeSource(sourceId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            container.externalSourceConfigStore.removeSource(sourceId)
            reload()
            VantaLogger.d(VantaLogger.Tag.SOURCE, "source_removed sourceId=$sourceId")
        }
    }
}
