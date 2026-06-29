package com.audiophile.musicplayer.ui

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.audiophile.musicplayer.AppContainer
import com.audiophile.musicplayer.common.VantaLogger
import com.audiophile.musicplayer.common.VantaResult
import com.audiophile.musicplayer.data.llm.AiProvider
import com.audiophile.musicplayer.data.source.external.ExternalSourceConfig
import com.audiophile.musicplayer.data.voice.PulseVoiceProfile
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

/**
 * UiState for the settings screen.
 * Owns [ResolverConfigForm] and the list of external sources.
 */
data class SettingsUiState(
    val config: ResolverConfigForm = ResolverConfigForm(),
    val externalSources: List<ExternalSourceConfig> = emptyList(),
    val isSaving: Boolean = false,
    val saveResult: VantaResult<Unit>? = null,
    val verifyResult: VantaResult<String>? = null,
)

/** User-initiated settings actions. */
sealed class SettingsUiEvent {
    // Resolver field changes
    data class TorBoxBaseUrlChanged(val value: String) : SettingsUiEvent()
    data class TorBoxApiTokenChanged(val value: String) : SettingsUiEvent()
    data class RealDebridApiTokenChanged(val value: String) : SettingsUiEvent()
    data class CommunityInstancesChanged(val value: String) : SettingsUiEvent()
    data class LlmProviderChanged(val value: String) : SettingsUiEvent()
    data class LlmApiKeyChanged(val value: String) : SettingsUiEvent()
    data class AppleMusicDeveloperTokenChanged(val value: String) : SettingsUiEvent()
    data class AppleMusicStorefrontChanged(val value: String) : SettingsUiEvent()
    data class PulseVoiceRelayUrlChanged(val value: String) : SettingsUiEvent()
    data class PulseVoiceRelayTokenChanged(val value: String) : SettingsUiEvent()
    data class PulseVoiceEngineChanged(val value: String) : SettingsUiEvent()
    data class LastFmApiKeyChanged(val value: String) : SettingsUiEvent()
    data class LastFmApiSecretChanged(val value: String) : SettingsUiEvent()
    data class LastFmUsernameChanged(val value: String) : SettingsUiEvent()
    data class LastFmSessionKeyChanged(val value: String) : SettingsUiEvent()
    // Actions
    data object SaveConfig : SettingsUiEvent()
    data object VerifyLlmKey : SettingsUiEvent()
    data object ReloadExternalSources : SettingsUiEvent()
    data class RemoveExternalSource(val sourceId: String) : SettingsUiEvent()
}

/**
 * Manages all app settings: resolver config, LLM provider, AI voice, Last.fm,
 * Apple Music, and external source management.
 *
 * Previously embedded in [MainViewModel]; extracted for single-responsibility.
 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val container: AppContainer
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        SettingsUiState(
            config = loadConfigForm(),
            externalSources = container.externalSourceConfigStore.getSources()
        )
    )
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    fun onEvent(event: SettingsUiEvent) {
        when (event) {
            is SettingsUiEvent.TorBoxBaseUrlChanged -> updateField { it.copy(torBoxBaseUrl = event.value) }
            is SettingsUiEvent.TorBoxApiTokenChanged -> updateField { it.copy(torBoxApiToken = event.value) }
            is SettingsUiEvent.RealDebridApiTokenChanged -> updateField { it.copy(realDebridApiToken = event.value) }
            is SettingsUiEvent.CommunityInstancesChanged -> updateField { it.copy(communityInstancesText = event.value) }
            is SettingsUiEvent.LlmProviderChanged -> onLlmProviderChanged(event.value)
            is SettingsUiEvent.LlmApiKeyChanged -> updateField { it.copy(llmApiKey = event.value) }
            is SettingsUiEvent.AppleMusicDeveloperTokenChanged -> updateField { it.copy(appleMusicDeveloperToken = event.value) }
            is SettingsUiEvent.AppleMusicStorefrontChanged -> updateField { it.copy(appleMusicStorefront = event.value) }
            is SettingsUiEvent.PulseVoiceRelayUrlChanged -> updateField { it.copy(pulseVoiceRelayUrl = event.value) }
            is SettingsUiEvent.PulseVoiceRelayTokenChanged -> updateField { it.copy(pulseVoiceRelayToken = event.value) }
            is SettingsUiEvent.PulseVoiceEngineChanged -> updateField { it.copy(pulseVoiceEngine = event.value) }
            is SettingsUiEvent.LastFmApiKeyChanged -> updateField { it.copy(lastFmApiKey = event.value) }
            is SettingsUiEvent.LastFmApiSecretChanged -> updateField { it.copy(lastFmApiSecret = event.value) }
            is SettingsUiEvent.LastFmUsernameChanged -> updateField { it.copy(lastFmUsername = event.value) }
            is SettingsUiEvent.LastFmSessionKeyChanged -> updateField { it.copy(lastFmSessionKey = event.value) }
            is SettingsUiEvent.SaveConfig -> saveConfig()
            is SettingsUiEvent.VerifyLlmKey -> verifyLlmKey()
            is SettingsUiEvent.ReloadExternalSources -> reloadSources()
            is SettingsUiEvent.RemoveExternalSource -> removeSource(event.sourceId)
        }
    }

    private fun updateField(transform: (ResolverConfigForm) -> ResolverConfigForm) {
        _uiState.update { it.copy(config = transform(it.config)) }
    }

    private fun onLlmProviderChanged(value: String) {
        val provider = runCatching { AiProvider.valueOf(value) }.getOrNull()
        val keyForProvider = provider?.let { container.resolverConfigStore.getLlmApiKey(it) }.orEmpty()
        _uiState.update { state ->
            state.copy(
                config = state.config.copy(
                    llmProviderName = value,
                    llmApiKey = keyForProvider
                )
            )
        }
    }

    private fun saveConfig() {
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }
            try {
                val cfg = _uiState.value.config
                withContext(Dispatchers.IO) {
                    container.resolverConfigStore.apply {
                        setTorBoxResolverBaseUrl(cfg.torBoxBaseUrl)
                        setTorBoxApiToken(cfg.torBoxApiToken)
                        setRealDebridApiToken(cfg.realDebridApiToken)
                        val instanceSet = cfg.communityInstancesText.split("\n").map { it.trim() }.filter { it.isNotBlank() }.toSet()
                        setCommunityInstances(instanceSet)
                        val provider = runCatching { AiProvider.valueOf(cfg.llmProviderName) }.getOrNull()
                        if (provider != null) setLlmApiKey(provider, cfg.llmApiKey)
                        setAppleMusicDeveloperToken(cfg.appleMusicDeveloperToken)
                        setAppleMusicStorefront(cfg.appleMusicStorefront)
                        setPulseVoiceRelayUrl(cfg.pulseVoiceRelayUrl)
                        setPulseVoiceRelayToken(cfg.pulseVoiceRelayToken)
                        setPulseVoiceEngine(cfg.pulseVoiceEngine)
                        setLastFmApiKey(cfg.lastFmApiKey)
                        setLastFmApiSecret(cfg.lastFmApiSecret)
                        setLastFmUsername(cfg.lastFmUsername)
                        setLastFmSessionKey(cfg.lastFmSessionKey)
                    }
                    container.registerConfiguredProviders()
                }
                _uiState.update { it.copy(isSaving = false, saveResult = VantaResult.Success(Unit)) }
                VantaLogger.i(VantaLogger.Tag.SETTINGS, "config_saved")
            } catch (e: Exception) {
                VantaLogger.e(VantaLogger.Tag.SETTINGS, "config_save_failed", e)
                _uiState.update {
                    it.copy(
                        isSaving = false,
                        saveResult = VantaResult.Error("Save failed: ${e.message}", e)
                    )
                }
            }
        }
    }

    private fun verifyLlmKey() {
        viewModelScope.launch {
            _uiState.update { it.copy(verifyResult = VantaResult.Loading) }
            try {
                val cfg = _uiState.value.config
                val provider = runCatching { AiProvider.valueOf(cfg.llmProviderName) }.getOrElse { AiProvider.GEMINI }
                val ok = withContext(Dispatchers.IO) {
                    val result = container.pulseAiBrain.testConnection()
                    result.first
                }
                _uiState.update {
                    it.copy(
                        verifyResult = if (ok) VantaResult.Success("API key verified")
                                       else VantaResult.Error("API key verification failed")
                    )
                }
                if (ok) {
                    _uiState.update { state ->
                        state.copy(config = state.config.copy(
                            llmProvidersWithKeys = state.config.llmProvidersWithKeys + cfg.llmProviderName,
                            llmVerifiedProviders = state.config.llmVerifiedProviders + cfg.llmProviderName
                        ))
                    }
                }
            } catch (e: Exception) {
                VantaLogger.e(VantaLogger.Tag.SETTINGS, "verify_failed", e)
                _uiState.update {
                    it.copy(verifyResult = VantaResult.Error("Verification error: ${e.message}", e))
                }
            }
        }
    }

    private fun reloadSources() {
        _uiState.update {
            it.copy(externalSources = container.externalSourceConfigStore.getSources())
        }
    }

    private fun removeSource(sourceId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            container.externalSourceConfigStore.removeSource(sourceId)
            reloadSources()
        }
    }

    private fun loadConfigForm(): ResolverConfigForm {
        val store = container.resolverConfigStore
        val defaultProvider = AiProvider.GEMINI
        return ResolverConfigForm(
            torBoxBaseUrl = store.getTorBoxResolverBaseUrl().orEmpty(),
            torBoxApiToken = store.getTorBoxApiToken().orEmpty(),
            realDebridApiToken = store.getRealDebridApiToken().orEmpty(),
            communityInstancesText = store.getCommunityInstances().joinToString("\n"),
            llmProviderName = defaultProvider.name,
            llmApiKey = store.getLlmApiKey(defaultProvider).orEmpty(),
            pulseVoiceRelayUrl = store.getPulseVoiceRelayUrl().orEmpty(),
            pulseVoiceRelayToken = store.getPulseVoiceRelayToken().orEmpty(),
            pulseVoiceEngine = store.getPulseVoiceEngine() ?: PulseVoiceProfile.GEMINI_ENGINE,
            appleMusicDeveloperToken = store.getAppleMusicDeveloperToken().orEmpty(),
            appleMusicStorefront = store.getAppleMusicStorefront(),
            lastFmApiKey = store.getLastFmApiKey().orEmpty(),
            lastFmApiSecret = store.getLastFmApiSecret().orEmpty(),
            lastFmUsername = store.getLastFmUsername().orEmpty(),
            lastFmSessionKey = store.getLastFmSessionKey().orEmpty()
        )
    }
}
