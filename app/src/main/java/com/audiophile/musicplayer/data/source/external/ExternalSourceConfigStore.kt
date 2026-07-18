package com.audiophile.musicplayer.data.source.external

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import androidx.core.content.edit

class ExternalSourceConfigStore(context: Context) {
    private val prefs = context.getSharedPreferences("external_sources_config", Context.MODE_PRIVATE)
    private val gson = Gson()
    private val listType = object : TypeToken<List<ExternalSourceConfig>>() {}.type

    fun ensureDefaultSources() {
        val current = migrateBundledSources(getSources())
        if (current.isEmpty()) {
            saveSources(DEFAULT_SOURCES)
            logMergeResult(existingCount = 0, addedCount = DEFAULT_SOURCES.size, sources = DEFAULT_SOURCES)
            return
        }

        val existingIds = current.map { it.id }.toSet()
        val missingDefaults = DEFAULT_SOURCES.filterNot { it.id in existingIds }
        val merged = if (missingDefaults.isNotEmpty()) current + missingDefaults else current
        if (missingDefaults.isNotEmpty()) {
            saveSources(merged)
        }
        logMergeResult(existingCount = current.size, addedCount = missingDefaults.size, sources = merged)
    }

    /**
     * Bundled public gateways are conveniences, not user accounts. Remove retired
     * gateways during upgrades so a dead demo endpoint never looks "connected".
     * User-added providers are left untouched.
     */
    private fun migrateBundledSources(sources: List<ExternalSourceConfig>): List<ExternalSourceConfig> {
        val retiredIds = setOf(
            "all_in_one",
            "monochrome_tidal",
            "squidwtf_qobuz",
            "squidwtf_tidal",
            "squidwtf_amazon",
            "squidwtf_sc",
            "spotiflac_local",
            "music_assistant_local"
        )
        val migrated = sources
            .filterNot { it.id in retiredIds }
            .map { source ->
                when (source.id) {
                    "qobuz_tidal" -> source.copy(
                        displayName = "VANTA Gateway",
                        baseUrl = SpotiFlacEndpoints.DEFAULT_GATEWAY_BASE_URL,
                        providerKind = PlaybackProviderKind.ADDON,
                        searchBaseUrl = null,
                        streamEndpointUrl = SpotiFlacEndpoints.gatewayStreamEndpoint(),
                        enabled = true,
                        disabledByDefault = false
                    )
                    "qobuz_gateway" -> source.copy(
                        displayName = "Qobuz",
                        baseUrl = SpotiFlacEndpoints.DEFAULT_GATEWAY_BASE_URL,
                        searchBaseUrl = SpotiFlacEndpoints.DEFAULT_GATEWAY_BASE_URL,
                        providerKind = PlaybackProviderKind.QOBUZ,
                        streamEndpointUrl = source.streamEndpointUrl?.takeIf { it.isNotBlank() }
                            ?: SpotiFlacEndpoints.gatewayStreamEndpoint(),
                        enabled = true,
                        disabledByDefault = false
                    )
                    "tidal_gateway" -> source.copy(
                        displayName = "Tidal",
                        baseUrl = SpotiFlacEndpoints.DEFAULT_GATEWAY_BASE_URL,
                        searchBaseUrl = SpotiFlacEndpoints.DEFAULT_GATEWAY_BASE_URL,
                        providerKind = PlaybackProviderKind.TIDAL,
                        streamEndpointUrl = SpotiFlacEndpoints.gatewayStreamEndpoint(),
                        enabled = true,
                        disabledByDefault = false
                    )
                    "deezer_gateway" -> source.copy(
                        displayName = "Deezer",
                        baseUrl = SpotiFlacEndpoints.DEFAULT_GATEWAY_BASE_URL,
                        searchBaseUrl = SpotiFlacEndpoints.DEFAULT_GATEWAY_BASE_URL,
                        providerKind = PlaybackProviderKind.DEEZER,
                        streamEndpointUrl = SpotiFlacEndpoints.gatewayStreamEndpoint(),
                        enabled = true,
                        disabledByDefault = false
                    )
                    "pandora_gateway" -> source.copy(
                        displayName = "Pandora Radio",
                        baseUrl = SpotiFlacEndpoints.DEFAULT_GATEWAY_BASE_URL,
                        searchBaseUrl = SpotiFlacEndpoints.DEFAULT_GATEWAY_BASE_URL,
                        providerKind = PlaybackProviderKind.PANDORA,
                        streamEndpointUrl = SpotiFlacEndpoints.gatewayStreamEndpoint(),
                        enabled = true,
                        disabledByDefault = false
                    )
                    "amazon_gateway" -> source.copy(
                        displayName = "Amazon Music",
                        baseUrl = SpotiFlacEndpoints.DEFAULT_GATEWAY_BASE_URL,
                        searchBaseUrl = SpotiFlacEndpoints.DEFAULT_GATEWAY_BASE_URL,
                        providerKind = PlaybackProviderKind.AMAZON,
                        streamEndpointUrl = SpotiFlacEndpoints.gatewayStreamEndpoint(),
                        enabled = true,
                        disabledByDefault = false
                    )
                    else -> source
                }
            }
        if (migrated != sources) saveSources(migrated)
        return migrated
    }

    fun getSources(): List<ExternalSourceConfig> {
        val json = prefs.getString("sources_json", null) ?: return emptyList()
        return try {
            (gson.fromJson<List<ExternalSourceConfig>>(json, listType) ?: emptyList())
                .map { source ->
                    source.copy(providerKind = PlaybackProviderKind.normalize(source.providerKind))
                }
        } catch (e: com.google.gson.JsonSyntaxException) {
            Log.e("ExternalSourceConfig", "Failed to parse sources", e)
            emptyList()
        }
    }

    fun saveSources(sources: List<ExternalSourceConfig>) {
        val json = gson.toJson(sources)
        prefs.edit {
                putString("sources_json", json)
            }
    }

    fun addSource(source: ExternalSourceConfig) {
        synchronized(this) {
            val current = getSources().toMutableList()
            val index = current.indexOfFirst { it.id == source.id }
            if (index >= 0) {
                current[index] = source
            } else {
                current.add(source)
            }
            saveSources(current)
        }
    }

    fun removeSource(id: String) {
        synchronized(this) {
            val current = getSources().toMutableList()
            current.removeAll { it.id == id }
            saveSources(current)
        }
    }

    fun updateSourceEnabled(id: String, enabled: Boolean) {
        synchronized(this) {
            val current = getSources().toMutableList()
            val index = current.indexOfFirst { it.id == id }
            if (index >= 0) {
                current[index] = current[index].copy(enabled = enabled)
                saveSources(current)
            }
        }
    }

    fun updateSourceUrls(
        id: String,
        baseUrl: String,
        searchBaseUrl: String? = null,
        streamEndpointUrl: String? = null
    ) {
        synchronized(this) {
            val current = getSources().toMutableList()
            val index = current.indexOfFirst { it.id == id }
            if (index >= 0) {
                current[index] = current[index].copy(
                    baseUrl = baseUrl.trim(),
                    searchBaseUrl = searchBaseUrl?.trim()?.takeIf { it.isNotBlank() },
                    streamEndpointUrl = streamEndpointUrl?.trim()?.takeIf { it.isNotBlank() }
                )
                saveSources(current)
            }
        }
    }

    fun updateSourceHealth(id: String, healthStatus: String, lastError: String?, avgSearchMs: Long?, lastTestedAt: Long = System.currentTimeMillis()) {
        synchronized(this) {
            val current = getSources().toMutableList()
            val index = current.indexOfFirst { it.id == id }
            if (index >= 0) {
                current[index] = current[index].copy(
                    healthStatus = healthStatus,
                    lastError = lastError,
                    avgSearchMs = avgSearchMs,
                    lastTestedAt = lastTestedAt
                )
                saveSources(current)
            }
        }
    }

    fun updateSourceCapabilities(id: String, capabilities: List<String>, canSearch: Boolean, canStream: Boolean, canBrowse: Boolean, canAlbum: Boolean, canArtist: Boolean, canPlaylist: Boolean) {
        val current = getSources().toMutableList()
        val index = current.indexOfFirst { it.id == id }
        if (index >= 0) {
            current[index] = current[index].copy(
                capabilities = capabilities,
                canSearch = canSearch,
                canStream = canStream,
                canBrowse = canBrowse,
                canAlbum = canAlbum,
                canArtist = canArtist,
                canPlaylist = canPlaylist
            )
            saveSources(current)
        }
    }

    fun moveSource(id: String, direction: Int) {
        val current = getSources().toMutableList()
        val index = current.indexOfFirst { it.id == id }
        if (index < 0) return
        val target = index + direction
        if (target < 0 || target >= current.size) return
        val item = current.removeAt(index)
        current.add(target, item)
        val updated = current.mapIndexed { i, src -> src.copy(priority = current.size - i) }
        saveSources(updated)
    }

    fun clearFailedSources() {
        val current = getSources().toMutableList()
        val updated = current.map { it.copy(
            healthStatus = if (it.enabled) "unknown" else "Disabled",
            lastError = null,
            lastStatus = null,
            lastTestedAt = null
        ) }
        saveSources(updated)
    }

    companion object {
        val DEFAULT_SOURCES = listOf(
            ExternalSourceConfig(
                id = "qobuz_tidal",
                displayName = "VANTA Gateway",
                baseUrl = SpotiFlacEndpoints.DEFAULT_GATEWAY_BASE_URL,
                streamEndpointUrl = SpotiFlacEndpoints.gatewayStreamEndpoint(),
                providerKind = PlaybackProviderKind.ADDON,
                enabled = true,
                priority = 100,
                disabledByDefault = false,
                lastTestedAt = null,
                lastStatus = "Live"
            ),
            ExternalSourceConfig(
                id = "qobuz_gateway",
                displayName = "Qobuz",
                baseUrl = SpotiFlacEndpoints.DEFAULT_GATEWAY_BASE_URL,
                searchBaseUrl = SpotiFlacEndpoints.DEFAULT_GATEWAY_BASE_URL,
                streamEndpointUrl = SpotiFlacEndpoints.gatewayStreamEndpoint(),
                providerKind = PlaybackProviderKind.QOBUZ,
                enabled = true,
                priority = 8,
                disabledByDefault = false,
                lastTestedAt = null,
                lastStatus = null
            ),
            ExternalSourceConfig(
                id = "tidal_gateway",
                displayName = "Tidal",
                baseUrl = SpotiFlacEndpoints.DEFAULT_GATEWAY_BASE_URL,
                searchBaseUrl = SpotiFlacEndpoints.DEFAULT_GATEWAY_BASE_URL,
                streamEndpointUrl = SpotiFlacEndpoints.gatewayStreamEndpoint(),
                providerKind = PlaybackProviderKind.TIDAL,
                enabled = true,
                priority = 7,
                disabledByDefault = false,
                lastTestedAt = null,
                lastStatus = null
            ),
            ExternalSourceConfig(
                id = "deezer_gateway",
                displayName = "Deezer",
                baseUrl = SpotiFlacEndpoints.DEFAULT_GATEWAY_BASE_URL,
                searchBaseUrl = SpotiFlacEndpoints.DEFAULT_GATEWAY_BASE_URL,
                streamEndpointUrl = SpotiFlacEndpoints.gatewayStreamEndpoint(),
                providerKind = PlaybackProviderKind.DEEZER,
                enabled = true,
                priority = 5,
                disabledByDefault = false,
                lastTestedAt = null,
                lastStatus = null
            ),
            ExternalSourceConfig(
                id = "pandora_gateway",
                displayName = "Pandora Radio",
                baseUrl = SpotiFlacEndpoints.DEFAULT_GATEWAY_BASE_URL,
                searchBaseUrl = SpotiFlacEndpoints.DEFAULT_GATEWAY_BASE_URL,
                providerKind = PlaybackProviderKind.PANDORA,
                streamEndpointUrl = SpotiFlacEndpoints.gatewayStreamEndpoint(),
                enabled = true,
                priority = 4,
                disabledByDefault = false,
                lastTestedAt = null,
                lastStatus = null
            ),
            ExternalSourceConfig(
                id = "amazon_gateway",
                displayName = "Amazon Music",
                baseUrl = SpotiFlacEndpoints.DEFAULT_GATEWAY_BASE_URL,
                searchBaseUrl = SpotiFlacEndpoints.DEFAULT_GATEWAY_BASE_URL,
                providerKind = PlaybackProviderKind.AMAZON,
                streamEndpointUrl = SpotiFlacEndpoints.gatewayStreamEndpoint(),
                enabled = true,
                priority = 3,
                disabledByDefault = false,
                lastTestedAt = null,
                lastStatus = null
            )
        )
    }

    private fun logMergeResult(existingCount: Int, addedCount: Int, sources: List<ExternalSourceConfig>) {
        val skippedDuplicates = DEFAULT_SOURCES.size - addedCount
        Log.d(
            "VANTA_EXTERNAL_SOURCE_CONFIG",
            "action='merge_defaults' existing=$existingCount added=$addedCount skippedDuplicates=$skippedDuplicates"
        )
        sources.forEach { source ->
            Log.d(
                "VANTA_EXTERNAL_SOURCE_CONFIG",
                "providerId='${source.id}' kind=${source.providerKind} enabled=${source.enabled} baseUrlSet=${source.baseUrl.isNotBlank()} disabledByDefault=${source.disabledByDefault}"
            )
        }
    }
}