package com.audiophile.musicplayer.data.source.external

import com.audiophile.musicplayer.data.source.MusicSourceProvider
import com.audiophile.musicplayer.data.source.ResolvedStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext

class QobuzGatewayMusicSourceProvider(config: ExternalSourceConfig) : ExternalSourceProvider(config) {
    override suspend fun resolveStream(trackId: String): ResolvedStream? = coroutineScope {
        if (!config.enabled) return@coroutineScope null
        withContext(Dispatchers.IO) { super.resolveStream(trackId) }?.let { return@coroutineScope it }
        val id = trackId.removePrefix("qobuz:").trim()
        val jobs = buildList {
            if (SpotiFlacEndpoints.QOBUZ_WJHE_STREAM_API_URL.isNotBlank()) {
                add(async(Dispatchers.IO) {
                    GatewayStreamResolver.resolveGetUrlsParallel(
                        this@QobuzGatewayMusicSourceProvider,
                        listOf(SpotiFlacEndpoints.buildQobuzWjheStreamUrl(id)),
                        "wjhe_stream"
                    )
                })
            }
            add(async(Dispatchers.IO) {
                GatewayStreamResolver.resolveGetUrlsParallel(
                    this@QobuzGatewayMusicSourceProvider,
                    SpotiFlacEndpoints.buildAddonStreamUrls(catalogBaseUrl, id),
                    "addon_stream"
                )
            })
            config.streamEndpointUrl?.let { endpoint ->
                add(async(Dispatchers.IO) {
                    GatewayStreamResolver.resolveCommunityStream(
                        this@QobuzGatewayMusicSourceProvider,
                        endpoint,
                        id,
                        "qobuz"
                    )
                })
            }
        }
        GatewayStreamResolver.firstReadyStream(jobs)
    }
}

class TidalGatewayMusicSourceProvider(config: ExternalSourceConfig) : ExternalSourceProvider(config) {
    override suspend fun resolveStream(trackId: String): ResolvedStream? = coroutineScope {
        if (!config.enabled) return@coroutineScope null
        withContext(Dispatchers.IO) { super.resolveStream(trackId) }?.let { return@coroutineScope it }
        val mappedTidalId = if (trackId.startsWith("tidal:", ignoreCase = true)) {
            null
        } else {
            GatewayStreamResolver.resolveTidalTrackIdFromQobuz(trackId.removePrefix("qobuz:").trim())
        }
        val tidalId = tidalIdForTidalProvider(trackId, mappedTidalId) ?: return@coroutineScope null
        android.util.Log.d(
            "VANTA_TIDAL_ATMOS",
            "resolveStream trackId=$trackId tidalId=$tidalId " +
            "quality=${com.audiophile.musicplayer.data.source.external.SpotiFlacEndpoints.PREFERRED_STREAM_QUALITY}"
        )
        val jobs = buildList {
            config.streamEndpointUrl?.let { endpoint ->
                add(async(Dispatchers.IO) {
                    android.util.Log.d(
                        "VANTA_TIDAL_ATMOS",
                        "Strategy A: community POST endpoint=${endpoint.take(80)} tidalId=$tidalId"
                    )
                    GatewayStreamResolver.resolveCommunityStream(
                        this@TidalGatewayMusicSourceProvider,
                        endpoint,
                        tidalId,
                        "tidal"
                    )
                })
            }
            add(async(Dispatchers.IO) {
                android.util.Log.d(
                    "VANTA_TIDAL_ATMOS",
                    "Strategy B: addon GET tidalId=$tidalId baseUrl=$catalogBaseUrl"
                )
                GatewayStreamResolver.resolveGetUrlsParallel(
                    this@TidalGatewayMusicSourceProvider,
                    SpotiFlacEndpoints.buildAddonStreamUrls(catalogBaseUrl, tidalId, preferTidal = true),
                    "tidal_addon_stream"
                )
            })
        }
        val result = GatewayStreamResolver.firstReadyStream(jobs)
        android.util.Log.d(
            "VANTA_TIDAL_ATMOS",
            "resolveStream RESULT: ${if (result != null) "SUCCESS url=${result.streamUrl.take(120)}... isAtmos=${result.isDolbyAtmos}" else "FAILED"}"
        )
        result
    }
}

class PandoraGatewayMusicSourceProvider(config: ExternalSourceConfig) : ExternalSourceProvider(config) {
    override suspend fun resolveStream(trackId: String): ResolvedStream? = coroutineScope {
        if (!config.enabled) return@coroutineScope null
        withContext(Dispatchers.IO) { super.resolveStream(trackId) }?.let { return@coroutineScope it }
        val id = trackId.removePrefix("pandora:").trim()
        val jobs = buildList {
            config.streamEndpointUrl?.let { endpoint ->
                add(async(Dispatchers.IO) {
                    GatewayStreamResolver.resolveCommunityStream(
                        this@PandoraGatewayMusicSourceProvider,
                        endpoint,
                        id,
                        "pandora"
                    )
                })
            }
            add(async(Dispatchers.IO) {
                GatewayStreamResolver.resolveGetUrlsParallel(
                    this@PandoraGatewayMusicSourceProvider,
                    SpotiFlacEndpoints.buildAddonStreamUrls(catalogBaseUrl, id),
                    "pandora_addon_stream"
                )
            })
        }
        GatewayStreamResolver.firstReadyStream(jobs)
    }
}

class AmazonGatewayMusicSourceProvider(config: ExternalSourceConfig) : ExternalSourceProvider(config) {
    override suspend fun resolveStream(trackId: String): ResolvedStream? = coroutineScope {
        if (!config.enabled) return@coroutineScope null
        withContext(Dispatchers.IO) { super.resolveStream(trackId) }?.let { return@coroutineScope it }
        val id = trackId.removePrefix("amazon:").trim()
        val jobs = buildList {
            config.streamEndpointUrl?.let { endpoint ->
                add(async(Dispatchers.IO) {
                    GatewayStreamResolver.resolveCommunityStream(
                        this@AmazonGatewayMusicSourceProvider,
                        endpoint,
                        id,
                        "amazon"
                    )
                })
            }
            add(async(Dispatchers.IO) {
                GatewayStreamResolver.resolveGetUrlsParallel(
                    this@AmazonGatewayMusicSourceProvider,
                    SpotiFlacEndpoints.buildAddonStreamUrls(catalogBaseUrl, id),
                    "amazon_addon_stream"
                )
            })
        }
        GatewayStreamResolver.firstReadyStream(jobs)
    }
}

class AddonGatewayMusicSourceProvider(config: ExternalSourceConfig) : ExternalSourceProvider(config) {
    override suspend fun resolveStream(trackId: String): ResolvedStream? = coroutineScope {
        if (!config.enabled) return@coroutineScope null
        withContext(Dispatchers.IO) { super.resolveStream(trackId) }?.let { return@coroutineScope it }
        val endpoint = config.streamEndpointUrl?.takeIf { it.isNotBlank() }
            ?: SpotiFlacEndpoints.gatewayStreamEndpoint(config.baseUrl)
        val attempts = GatewayStreamResolver.buildStreamAttempts(trackId)
        val jobs = buildList {
            add(async(Dispatchers.IO) {
                GatewayStreamResolver.resolveFirstCommunityStream(
                    this@AddonGatewayMusicSourceProvider,
                    endpoint,
                    attempts
                )
            })
            add(async(Dispatchers.IO) {
                GatewayStreamResolver.resolveDirectMirrors(
                    this@AddonGatewayMusicSourceProvider,
                    trackId,
                    catalogBaseUrl
                )
            })
            add(async(Dispatchers.IO) {
                GatewayStreamResolver.resolveGetUrlsParallel(
                    this@AddonGatewayMusicSourceProvider,
                    SpotiFlacEndpoints.buildAddonStreamUrls(catalogBaseUrl, trackId),
                    "addon_max_stream"
                )
            })
        }
        GatewayStreamResolver.firstReadyStream(jobs)
    }
}

class DeezerGatewayMusicSourceProvider(config: ExternalSourceConfig) : ExternalSourceProvider(config) {
    override suspend fun resolveStream(trackId: String): ResolvedStream? = coroutineScope {
        if (!config.enabled) return@coroutineScope null
        withContext(Dispatchers.IO) { super.resolveStream(trackId) }?.let { return@coroutineScope it }
        val endpoint = config.streamEndpointUrl ?: return@coroutineScope null
        val attempts = GatewayStreamResolver.buildStreamAttempts(trackId)
        val id = trackId.removePrefix("deezer:").trim()
        val deezerUrls = SpotiFlacEndpoints.buildAddonStreamUrls(catalogBaseUrl, id).map { url ->
            if (url.contains("provider=")) url else if (url.contains("?")) "$url&provider=deezer" else "$url?provider=deezer"
        }
        val jobs = buildList {
            add(async(Dispatchers.IO) {
                GatewayStreamResolver.resolveFirstCommunityStream(
                    this@DeezerGatewayMusicSourceProvider,
                    endpoint,
                    attempts
                )
            })
            add(async(Dispatchers.IO) {
                GatewayStreamResolver.resolveDirectMirrors(
                    this@DeezerGatewayMusicSourceProvider,
                    trackId,
                    catalogBaseUrl
                )
            })
            add(async(Dispatchers.IO) {
                GatewayStreamResolver.resolveGetUrlsParallel(
                    this@DeezerGatewayMusicSourceProvider,
                    deezerUrls,
                    "deezer_addon_stream"
                )
            })
        }
        GatewayStreamResolver.firstReadyStream(jobs)
    }
}

object PlaybackProviderFactory {
    fun create(config: ExternalSourceConfig): MusicSourceProvider =
        when (PlaybackProviderKind.normalize(config.providerKind)) {
            PlaybackProviderKind.QOBUZ -> QobuzGatewayMusicSourceProvider(config)
            PlaybackProviderKind.TIDAL -> TidalGatewayMusicSourceProvider(config)
            PlaybackProviderKind.DEEZER -> DeezerGatewayMusicSourceProvider(config)
            PlaybackProviderKind.PANDORA -> PandoraGatewayMusicSourceProvider(config)
            PlaybackProviderKind.AMAZON -> AmazonGatewayMusicSourceProvider(config)
            PlaybackProviderKind.ADDON -> AddonGatewayMusicSourceProvider(config)
            else -> ExternalSourceProvider(config)
        }

    fun createExternal(config: ExternalSourceConfig): ExternalSourceProvider =
        create(config) as ExternalSourceProvider
}
