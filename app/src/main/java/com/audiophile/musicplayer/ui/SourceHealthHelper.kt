package com.audiophile.musicplayer.ui

import android.content.Context
import android.util.Log
import com.audiophile.musicplayer.AppContainer
import com.audiophile.musicplayer.data.source.SourceSearchResult
import com.audiophile.musicplayer.data.source.external.ExternalSourceConfig
import com.audiophile.musicplayer.data.source.external.PlaybackProviderFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SourceTestResult(
    val providerId: String,
    val providerName: String,
    val query: String,
    val resultCount: Int,
    val durationMs: Long,
    val error: String? = null,
    val sampleResults: List<SourceSearchResult> = emptyList()
)

data class SourceStreamTestResult(
    val providerId: String,
    val providerName: String,
    val trackId: String,
    val trackTitle: String? = null,
    val streamUrl: String? = null,
    val host: String? = null,
    val bitrateKbps: Int? = null,
    val mimeType: String? = null,
    val qualityLabel: String? = null,
    val validationPassed: Boolean = false,
    val validationReason: String = "",
    val durationMs: Long = 0,
    val error: String? = null
)

data class SourceHealthStatus(
    val providerId: String,
    val providerName: String,
    val status: String,
    val baseUrl: String,
    val manifestMs: Long,
    val searchMs: Long,
    val searchCount: Int,
    val streamMs: Long,
    val streamValid: Boolean,
    val totalMs: Long,
    val errorMessage: String? = null,
    val capabilities: List<String> = emptyList()
)

class SourceHealthHelper(
    private val container: AppContainer,
    private val appContext: Context,
    private val scope: CoroutineScope,
    private val _uiState: MutableStateFlow<MainUiState>
) {
    fun testAppleMusicConnection() {
        scope.launch {
            val token = _uiState.value.resolverConfig.appleMusicDeveloperToken.trim()
            val storefront = _uiState.value.resolverConfig.appleMusicStorefront.trim().ifBlank { "us" }
            if (token.isBlank()) {
                _uiState.update { it.copy(statusMessage = "Please enter an Apple Music Developer Token first.") }
                return@launch
            }
            _uiState.update { it.copy(statusMessage = "Testing Apple Music Connection...") }
            val result = kotlin.runCatching {
                val api = com.audiophile.musicplayer.data.metadata.apple.AppleMusicApiClient(token)
                api.searchSongs(storefront, "test", limit = 1)
            }
            if (result.isSuccess) {
                _uiState.update { it.copy(statusMessage = "Success: Apple Music connection verified.") }
            } else {
                _uiState.update { it.copy(statusMessage = "Failed: Could not connect to Apple Music. Check your token and storefront.") }
            }
        }
    }

    fun testTorBoxConnection() {
        scope.launch {
            val token = _uiState.value.resolverConfig.torBoxApiToken.trim()
            if (token.isBlank()) {
                _uiState.update { it.copy(statusMessage = "Enter your TorBox access token first.") }
                return@launch
            }
            _uiState.update { it.copy(statusMessage = "Testing TorBox connection...") }
            val result = kotlin.runCatching {
                container.torBoxRepository.importableAudioFiles(token, limit = 10)
            }
            val files = result.getOrNull()
            when {
                result.isFailure -> {
                    _uiState.update { it.copy(statusMessage = "TorBox connection failed. Check your access token.") }
                }
                files.isNullOrEmpty() -> {
                    _uiState.update { it.copy(statusMessage = "TorBox connected, but no audio files were found in your library yet.") }
                }
                else -> {
                    _uiState.update {
                        it.copy(
                            statusMessage = "TorBox connected: ${files.size} audio file(s) ready.",
                            torBoxCandidates = files
                        )
                    }
                }
            }
        }
    }

    fun testRealDebridConnection() {
        scope.launch {
            val token = _uiState.value.resolverConfig.realDebridApiToken.trim()
            if (token.isBlank()) {
                _uiState.update { it.copy(statusMessage = "Enter your Real-Debrid API token first.") }
                return@launch
            }
            _uiState.update { it.copy(statusMessage = "Testing Real-Debrid connection...") }
            val provider = com.audiophile.musicplayer.data.source.RealDebridMusicSourceProvider(token)
            val result = kotlin.runCatching {
                provider.search("flac")
            }
            val matches = result.getOrNull()
            when {
                result.isFailure -> {
                    _uiState.update { it.copy(statusMessage = "Real-Debrid connection failed. Check your API token.") }
                }
                matches.isNullOrEmpty() -> {
                    _uiState.update { it.copy(statusMessage = "Real-Debrid connected, but no downloaded audio torrents matched yet.") }
                }
                else -> {
                    _uiState.update { it.copy(statusMessage = "Real-Debrid connected: ${matches.size} playable match(es) found.") }
                }
            }
        }
    }

    fun testLlmConnection() {
        scope.launch {
            val form = _uiState.value.resolverConfig
            val providerName = form.llmProviderName.trim()
            if (providerName.isBlank()) {
                _uiState.update { it.copy(statusMessage = "Select a Pulse AI provider first.") }
                return@launch
            }
            val provider = kotlin.runCatching {
                com.audiophile.musicplayer.data.llm.AiProvider.valueOf(providerName)
            }.getOrNull()
            if (provider == null) {
                _uiState.update { it.copy(statusMessage = "Unknown provider: $providerName") }
                return@launch
            }
            val apiKey = form.llmApiKey.trim()
            if (apiKey.isBlank()) {
                _uiState.update { it.copy(statusMessage = "Enter your API key first.") }
                return@launch
            }
            _uiState.update { it.copy(statusMessage = "Testing ${provider.displayName} connection...") }
            val client = com.audiophile.musicplayer.data.llm.createLlmClient(provider, apiKey)
            if (client == null) {
                _uiState.update { it.copy(statusMessage = "Could not create LLM client.") }
                return@launch
            }
            val response = client.chat("You are a test assistant.", "Reply with exactly: OK")
            if (response?.trim()?.contains("OK", ignoreCase = true) == true) {
                container.resolverConfigStore.markLlmVerified(provider)
                _uiState.update {
                    val config = it.resolverConfig
                    it.copy(
                        statusMessage = "${provider.displayName} connected successfully.",
                        resolverConfig = config.copy(
                            llmVerifiedProviders = config.llmVerifiedProviders + provider.name,
                            llmProvidersWithKeys = config.llmProvidersWithKeys + provider.name
                        )
                    )
                }
            } else {
                _uiState.update { it.copy(statusMessage = "${provider.displayName} connection failed. Check your API key.") }
            }
        }
    }

    fun testExternalSource(baseUrl: String) {
        scope.launch {
            if (baseUrl.isBlank()) {
                _uiState.update { it.copy(statusMessage = "Please enter a valid base URL or manifest URL.") }
                return@launch
            }
            _uiState.update { it.copy(statusMessage = "Testing external source connection...") }
            val config = ExternalSourceConfig(
                id = "temp_test",
                displayName = "Test Source",
                baseUrl = baseUrl,
                enabled = true,
                priority = 0,
                lastTestedAt = System.currentTimeMillis(),
                lastStatus = null
            )
            val provider = PlaybackProviderFactory.createExternal(config)
            val manifest = provider.testConnection()
            if (manifest != null) {
                _uiState.update { it.copy(statusMessage = "Success: Found manifest for ${manifest.name}") }
            } else {
                _uiState.update { it.copy(statusMessage = "Failed: Could not load manifest from $baseUrl") }
            }
        }
    }

    fun addExternalSource(baseUrl: String) {
        scope.launch {
            if (baseUrl.isBlank()) return@launch
            val provider = PlaybackProviderFactory.createExternal(
                ExternalSourceConfig(
                    id = "temp",
                    displayName = "",
                    baseUrl = baseUrl,
                    enabled = true
                )
            )
            val manifest = provider.testConnection()
            if (manifest != null) {
                val current = container.externalSourceConfigStore.getSources()
                val maxPriority = current.maxOfOrNull { it.priority } ?: 0
                val config = ExternalSourceConfig(
                    id = manifest.id.ifBlank { java.util.UUID.randomUUID().toString() },
                    displayName = manifest.name,
                    baseUrl = baseUrl,
                    enabled = true,
                    priority = maxPriority + 1,
                    lastTestedAt = System.currentTimeMillis(),
                    lastStatus = "OK"
                )
                container.externalSourceConfigStore.addSource(config)
                container.reloadResolverConfiguration()
                _uiState.update { it.copy(externalSources = container.externalSourceConfigStore.getSources(), statusMessage = "Added external source: ${manifest.name}") }
            } else {
                _uiState.update { it.copy(statusMessage = "Cannot add source: Manifest test failed for $baseUrl") }
            }
        }
    }

    fun removeExternalSource(id: String) {
        scope.launch {
            container.externalSourceConfigStore.removeSource(id)
            container.reloadResolverConfiguration()
            _uiState.update { it.copy(externalSources = container.externalSourceConfigStore.getSources(), statusMessage = "Removed external source") }
        }
    }

    fun toggleExternalSource(id: String, enabled: Boolean) {
        scope.launch {
            container.externalSourceConfigStore.updateSourceEnabled(id, enabled)
            container.reloadResolverConfiguration()
            _uiState.update { it.copy(externalSources = container.externalSourceConfigStore.getSources()) }
        }
    }

    fun updateExternalSourceUrls(
        id: String,
        baseUrl: String,
        searchBaseUrl: String?,
        streamEndpointUrl: String?
    ) {
        scope.launch {
            container.externalSourceConfigStore.updateSourceUrls(
                id = id,
                baseUrl = baseUrl,
                searchBaseUrl = searchBaseUrl,
                streamEndpointUrl = streamEndpointUrl
            )
            container.reloadResolverConfiguration()
            _uiState.update {
                it.copy(
                    externalSources = container.externalSourceConfigStore.getSources(),
                    statusMessage = "Updated provider URLs"
                )
            }
        }
    }

    fun moveExternalSource(id: String, direction: Int) {
        scope.launch {
            container.externalSourceConfigStore.moveSource(id, direction)
            container.reloadResolverConfiguration()
            _uiState.update { it.copy(externalSources = container.externalSourceConfigStore.getSources()) }
        }
    }

    fun clearFailedSourceCache() {
        scope.launch {
            container.externalSourceConfigStore.clearFailedSources()
            container.reloadResolverConfiguration()
            _uiState.update {
                it.copy(
                    externalSources = container.externalSourceConfigStore.getSources(),
                    statusMessage = "Cleared source health cache"
                )
            }
        }
    }

    fun testSourceHealth(id: String) {
        scope.launch {
            val source = container.externalSourceConfigStore.getSources().find { it.id == id } ?: return@launch
            if (!source.enabled) {
                _uiState.update { it.copy(statusMessage = "Source ${source.displayName} is disabled") }
                return@launch
            }
            _uiState.update { it.copy(isTestingSource = true) }
            _uiState.update { it.copy(statusMessage = "Testing ${source.displayName}...") }
            val provider = PlaybackProviderFactory.createExternal(source)
            val result = provider.testHealth()

            container.externalSourceConfigStore.updateSourceHealth(
                id = id,
                healthStatus = result.status,
                lastError = result.errorMessage,
                avgSearchMs = result.searchMs,
                lastTestedAt = System.currentTimeMillis()
            )
            result.manifest?.let { manifest ->
                container.externalSourceConfigStore.updateSourceCapabilities(
                    id = id,
                    capabilities = manifest.capabilities ?: emptyList(),
                    canSearch = manifest.canSearch ?: true,
                    canStream = manifest.canStream ?: true,
                    canBrowse = manifest.canBrowse ?: false,
                    canAlbum = manifest.types?.contains("album") ?: false,
                    canArtist = manifest.types?.contains("artist") ?: false,
                    canPlaylist = manifest.types?.contains("playlist") ?: false
                )
            }
            container.reloadResolverConfiguration()

            val healthStatus = SourceHealthStatus(
                providerId = result.providerId,
                providerName = source.displayName,
                status = result.status,
                baseUrl = result.baseUrl,
                manifestMs = result.manifestMs,
                searchMs = result.searchMs,
                searchCount = result.searchResultCount,
                streamMs = result.streamMs,
                streamValid = result.streamValid,
                totalMs = result.totalMs,
                errorMessage = result.errorMessage,
                capabilities = result.manifest?.capabilities ?: emptyList()
            )

            _uiState.update {
                val updated = it.sourceHealthStatuses.filter { h -> h.providerId != id } + healthStatus
                it.copy(
                    externalSources = container.externalSourceConfigStore.getSources(),
                    sourceHealthStatuses = updated,
                    isTestingSource = false,
                    statusMessage = "${source.displayName}: ${result.status}"
                )
            }
        }
    }

    fun testAllSourceHealth() {
        scope.launch {
            val sources = container.externalSourceConfigStore.getSources().filter { it.enabled }
            _uiState.update { it.copy(isTestingSource = true) }
            _uiState.update { it.copy(statusMessage = "Testing ${sources.size} sources...") }
            val statuses = mutableListOf<SourceHealthStatus>()
            sources.forEach { source ->
                val provider = PlaybackProviderFactory.createExternal(source)
                val result = provider.testHealth()
                container.externalSourceConfigStore.updateSourceHealth(
                    id = source.id,
                    healthStatus = result.status,
                    lastError = result.errorMessage,
                    avgSearchMs = result.searchMs,
                    lastTestedAt = System.currentTimeMillis()
                )
                result.manifest?.let { manifest ->
                    container.externalSourceConfigStore.updateSourceCapabilities(
                        id = source.id,
                        capabilities = manifest.capabilities ?: emptyList(),
                        canSearch = manifest.canSearch ?: true,
                        canStream = manifest.canStream ?: true,
                        canBrowse = manifest.canBrowse ?: false,
                        canAlbum = manifest.types?.contains("album") ?: false,
                        canArtist = manifest.types?.contains("artist") ?: false,
                        canPlaylist = manifest.types?.contains("playlist") ?: false
                    )
                }
                statuses.add(SourceHealthStatus(
                    providerId = result.providerId,
                    providerName = source.displayName,
                    status = result.status,
                    baseUrl = result.baseUrl,
                    manifestMs = result.manifestMs,
                    searchMs = result.searchMs,
                    searchCount = result.searchResultCount,
                    streamMs = result.streamMs,
                    streamValid = result.streamValid,
                    totalMs = result.totalMs,
                    errorMessage = result.errorMessage,
                    capabilities = result.manifest?.capabilities ?: emptyList()
                ))
            }
            container.reloadResolverConfiguration()
            _uiState.update {
                it.copy(
                    externalSources = container.externalSourceConfigStore.getSources(),
                    sourceHealthStatuses = statuses,
                    isTestingSource = false,
                    statusMessage = "Health check complete: ${statuses.count { it.status == "Healthy" }} healthy, ${statuses.count { it.status != "Healthy" && it.status != "Disabled" }} issues"
                )
            }
        }
    }

    fun testSourceSearch(id: String, query: String) {
        scope.launch {
            val source = container.externalSourceConfigStore.getSources().find { it.id == id } ?: return@launch
            if (!source.enabled) {
                _uiState.update { it.copy(statusMessage = "Source ${source.displayName} is disabled") }
                return@launch
            }
            _uiState.update { it.copy(isTestingSource = true) }
            val provider = PlaybackProviderFactory.createExternal(source)
            val result = provider.testSearch(query)
            val testResult = SourceTestResult(
                providerId = result.providerId,
                providerName = source.displayName,
                query = result.query,
                resultCount = result.results.size,
                durationMs = result.durationMs,
                error = result.error,
                sampleResults = result.results.take(5)
            )
            _uiState.update {
                val updated = it.sourceTestResults.filter { r -> r.providerId != id } + testResult
                it.copy(
                    sourceTestResults = updated,
                    isTestingSource = false,
                    statusMessage = "${source.displayName} search: ${result.results.size} results in ${result.durationMs}ms"
                )
            }
        }
    }

    fun testSourceStream(id: String, trackId: String) {
        scope.launch {
            val source = container.externalSourceConfigStore.getSources().find { it.id == id } ?: return@launch
            if (!source.enabled) {
                _uiState.update { it.copy(statusMessage = "Source ${source.displayName} is disabled") }
                return@launch
            }
            _uiState.update { it.copy(isTestingSource = true) }
            val provider = PlaybackProviderFactory.createExternal(source)
            val result = provider.testStream(trackId)
            val streamResult = SourceStreamTestResult(
                providerId = result.providerId,
                providerName = source.displayName,
                trackId = result.trackId,
                streamUrl = result.resolved?.streamUrl,
                host = result.resolved?.streamUrl?.let { kotlin.runCatching { java.net.URI(it).host }.getOrNull() },
                bitrateKbps = result.resolved?.bitrateKbps,
                mimeType = result.resolved?.mimeType,
                qualityLabel = result.resolved?.qualityLabel,
                validationPassed = result.validationPassed,
                validationReason = result.validationReason,
                durationMs = result.resolveMs,
                error = result.error
            )
            _uiState.update {
                val updated = it.sourceStreamTestResults.filter { r -> r.providerId != id } + streamResult
                it.copy(
                    sourceStreamTestResults = updated,
                    isTestingSource = false,
                    statusMessage = "${source.displayName} stream: ${if (result.validationPassed) "Validated" else "Failed"}"
                )
            }
        }
    }
}
