package com.audiophile.musicplayer.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.audiophile.musicplayer.discovery.personalized.PersonalizedMixKind
import com.audiophile.musicplayer.discovery.personalized.PersonalizedMixManager
import com.audiophile.musicplayer.discovery.personalized.PersonalizedMixPlayback
import com.audiophile.musicplayer.discovery.personalized.PersonalizedMixRecord
import com.audiophile.musicplayer.discovery.personalized.PersonalizedMixRegistry
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class PersonalizedMixCardState(
    val kind: PersonalizedMixKind,
    val title: String,
    val subtitle: String,
    val trackCount: Int,
    val lastRefreshedLabel: String?,
    val isLoading: Boolean = false,
    val error: String? = null,
    val isEmpty: Boolean = false,
    val isStale: Boolean = false
)

data class PersonalizedMixUiState(
    val cards: List<PersonalizedMixCardState> = emptyList(),
    val statusMessage: String? = null
)

class PersonalizedMixViewModel(
    private val manager: PersonalizedMixManager,
    private val registry: PersonalizedMixRegistry,
    private val playback: PersonalizedMixPlayback
) : ViewModel() {

    private val _uiState = MutableStateFlow(PersonalizedMixUiState())
    val uiState: StateFlow<PersonalizedMixUiState> = _uiState.asStateFlow()

    init {
        loadHomeCards()
    }

    fun loadHomeCards() {
        viewModelScope.launch {
            val cards = registry.homeKinds().mapNotNull { kind ->
                val spec = registry.get(kind) ?: return@mapNotNull null
                val record = runCatching { manager.ensureMix(kind) }.getOrNull()
                buildCard(kind, spec.displayName, spec.subtitle, record)
            }
            _uiState.update { it.copy(cards = cards) }
        }
    }

    fun refreshMix(kind: PersonalizedMixKind) {
        viewModelScope.launch {
            setCardLoading(kind, true)
            try {
                val record = manager.refreshMix(kind)
                updateCardFromRecord(kind, record)
            } catch (e: Exception) {
                setCardError(kind, e.message ?: "Refresh failed")
            }
        }
    }

    fun playMix(kind: PersonalizedMixKind, onStarted: () -> Unit = {}) {
        viewModelScope.launch {
            setCardLoading(kind, true)
            when (val result = playback.playMix(kind)) {
                is PersonalizedMixPlayback.PlayMixResult.Started -> {
                    _uiState.update { it.copy(statusMessage = "Playing ${kind.id}") }
                    onStarted()
                }
                is PersonalizedMixPlayback.PlayMixResult.Failed -> {
                    _uiState.update { it.copy(statusMessage = result.reason) }
                    setCardError(kind, result.reason)
                }
            }
            setCardLoading(kind, false)
            loadHomeCards()
        }
    }

    fun clearStatusMessage() {
        _uiState.update { it.copy(statusMessage = null) }
    }

    private fun buildCard(
        kind: PersonalizedMixKind,
        title: String,
        subtitle: String,
        record: PersonalizedMixRecord?
    ): PersonalizedMixCardState =
        PersonalizedMixCardState(
            kind = kind,
            title = title,
            subtitle = subtitle,
            trackCount = record?.trackCount ?: 0,
            lastRefreshedLabel = formatLastRefreshed(record?.lastGeneratedAt),
            error = record?.lastGenerationError,
            isEmpty = (record?.trackCount ?: 0) == 0,
            isStale = record?.isStale == true
        )

    private fun updateCardFromRecord(kind: PersonalizedMixKind, record: PersonalizedMixRecord) {
        _uiState.update { state ->
            state.copy(
                cards = state.cards.map { card ->
                    if (card.kind != kind) card else card.copy(
                        trackCount = record.trackCount,
                        lastRefreshedLabel = formatLastRefreshed(record.lastGeneratedAt),
                        error = record.lastGenerationError,
                        isLoading = false,
                        isEmpty = record.trackCount == 0,
                        isStale = record.isStale
                    )
                }
            )
        }
    }

    private fun setCardLoading(kind: PersonalizedMixKind, loading: Boolean) {
        _uiState.update { state ->
            state.copy(cards = state.cards.map { if (it.kind == kind) it.copy(isLoading = loading) else it })
        }
    }

    private fun setCardError(kind: PersonalizedMixKind, error: String) {
        _uiState.update { state ->
            state.copy(cards = state.cards.map {
                if (it.kind == kind) it.copy(isLoading = false, error = error) else it
            })
        }
    }

    private fun formatLastRefreshed(timestamp: Long?): String? {
        if (timestamp == null) return null
        val formatter = SimpleDateFormat("MMM d, h:mm a", Locale.getDefault())
        return "Updated ${formatter.format(Date(timestamp))}"
    }
}

class PersonalizedMixViewModelFactory(
    private val manager: PersonalizedMixManager,
    private val registry: PersonalizedMixRegistry,
    private val playback: PersonalizedMixPlayback
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(PersonalizedMixViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return PersonalizedMixViewModel(manager, registry, playback) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
