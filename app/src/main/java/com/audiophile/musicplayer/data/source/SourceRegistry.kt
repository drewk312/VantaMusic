package com.audiophile.musicplayer.data.source

import android.util.Log
import com.audiophile.musicplayer.data.source.ContentPurityFilter
import com.audiophile.musicplayer.data.source.external.ExternalSourceProvider
import kotlinx.coroutines.async
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeout

class SourceRegistry(
    private val providers: List<MusicSourceProvider>
) {
    suspend fun searchAll(
        query: String,
        timeoutMs: Long = 8_000L,
        includeSupplemental: Boolean = false
    ): List<SourceSearchResult> = coroutineScope {
        val searchable = if (includeSupplemental) {
            providers
        } else {
            providers.filterNot { SourceIdentityGate.isSupplementalPlaybackProvider(it.providerId) }
        }
        val results = mutableListOf<SourceSearchResult>()
        val searchGroups = searchable.groupBy { searchGroupKey(it) }
        Log.d(
            "VANTA_SEARCH",
            "SourceRegistry query='$query' providerCount=${searchable.size} searchGroups=${searchGroups.size} " +
                "timeoutMs=$timeoutMs includeSupplemental=$includeSupplemental"
        )

        val deferred = searchGroups.entries
            .sortedBy { (_, groupProviders) -> searchGroupPriority(groupProviders.first()) }
            .map { (groupKey, groupProviders) ->
            async {
                val representative = groupProviders.first()
                val startMs = System.currentTimeMillis()
                try {
                    val providerResults = withTimeout(timeoutMs) { representative.search(query) }
                    val durationMs = System.currentTimeMillis() - startMs
                    Log.d(
                        "VANTA_SEARCH_PERF",
                        "query='$query' groupKey=$groupKey representative=${representative.providerId} " +
                            "groupSize=${groupProviders.size} resultCount=${providerResults.size} " +
                            "durationMs=$durationMs timeout=false"
                    )
                    providerResults.filter { result ->
                        ContentPurityFilter.isAllowed(
                            title = result.title,
                            artist = result.artist,
                            album = result.album,
                            durationMs = result.durationMs,
                            source = result.providerId,
                            userQuery = query
                        )
                    }
                } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
                    val durationMs = System.currentTimeMillis() - startMs
                    Log.w(
                        "VANTA_SEARCH_PERF",
                        "query='$query' groupKey=$groupKey representative=${representative.providerId} " +
                            "durationMs=$durationMs timeout=true"
                    )
                    emptyList()
                } catch (e: CancellationException) {
                    Log.d(
                        "VANTA_SEARCH_INPUT",
                        "provider_search_cancelled query='$query' groupKey=$groupKey " +
                            "representative=${representative.providerId}"
                    )
                    throw e
                } catch (e: Exception) {
                    Log.e(
                        "VANTA_SEARCH_PERF",
                        "query='$query' groupKey=$groupKey representative=${representative.providerId} error='${e.message}'"
                    )
                    emptyList()
                }
            }
        }

        deferred.forEach { deferredResult ->
            results.addAll(deferredResult.await())
        }

        Log.d("VANTA_SEARCH", "SourceRegistry finalResultCount=${results.size}")
        results
    }

    private fun searchGroupKey(provider: MusicSourceProvider): String =
        when (provider) {
            is ExternalSourceProvider -> provider.searchGroupKey
            is CloudflareGatewaySource -> provider.searchGroupKey
            else -> provider.providerId
        }

    /** Gateway/catalog before supplemental providers (e.g. YouTube). */
    private fun searchGroupPriority(provider: MusicSourceProvider): Int = when {
        provider is ExternalSourceProvider || provider is CloudflareGatewaySource -> 0
        provider.providerId == "youtube_music" -> 2
        else -> 1
    }

    suspend fun resolveStream(providerId: String, trackId: String, timeoutMs: Long = CloudLibraryHelpers.TAP_PLAY_RESOLVE_TIMEOUT_MS): ResolvedStream? {
        val provider = providers.find { it.providerId == providerId } ?: return null
        Log.d("VANTA_PLAY_TRACK_REQUEST", "resolveStream provider=$providerId trackId=$trackId timeoutMs=$timeoutMs")
        // Capture late successes: Deezer/gateway often finish a few ms after withTimeout
        // fires. Discarding them caused "gateway_get_stream_ok" followed by TIMEOUT → no play.
        val lateSuccess = java.util.concurrent.atomic.AtomicReference<ResolvedStream?>(null)
        return try {
            val resolved = withTimeout(timeoutMs) {
                provider.resolveStream(trackId)?.also { lateSuccess.set(it) }
            }
            if (resolved != null) {
                Log.d("VANTA_SEARCH", "SourceRegistry resolveStream success for ${provider.providerId}:$trackId")
            } else {
                Log.d("VANTA_SEARCH", "SourceRegistry resolveStream failure (null) for ${provider.providerId}:$trackId")
            }
            resolved
        } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
            val kept = lateSuccess.get()
            if (kept != null && kept.streamUrl.isNotBlank()) {
                Log.w(
                    "VANTA_SEARCH",
                    "SourceRegistry resolveStream late_success_after_timeout for ${provider.providerId}:$trackId (${timeoutMs}ms)"
                )
                kept
            } else {
                Log.e("VANTA_SEARCH", "SourceRegistry resolveStream TIMEOUT for ${provider.providerId}:$trackId (${timeoutMs}ms exceeded)")
                null
            }
        } catch (e: Exception) {
            Log.e("VANTA_SEARCH", "SourceRegistry resolveStream error for ${provider.providerId}:$trackId", e)
            null
        }
    }

    suspend fun resolveVideoStream(
        providerId: String,
        trackId: String,
        timeoutMs: Long = CloudLibraryHelpers.TAP_PLAY_RESOLVE_TIMEOUT_MS
    ): ResolvedStream? {
        val provider = providers.find { it.providerId == providerId } ?: return null
        return try {
            withTimeout(timeoutMs) { provider.resolveVideoStream(trackId) }
        } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
            Log.e("VANTA_SEARCH", "SourceRegistry resolveVideoStream TIMEOUT for ${provider.providerId}:$trackId")
            null
        } catch (e: Exception) {
            Log.e("VANTA_SEARCH", "SourceRegistry resolveVideoStream error for ${provider.providerId}:$trackId", e)
            null
        }
    }

    suspend fun resolvePlayback(
        providerId: String,
        trackId: String,
        timeoutMs: Long = CloudLibraryHelpers.TAP_PLAY_RESOLVE_TIMEOUT_MS,
        requestedQuality: com.audiophile.musicplayer.data.source.playback.RequestedAudioQuality =
            com.audiophile.musicplayer.data.source.playback.RequestedAudioQuality.HI_RES_24
    ): com.audiophile.musicplayer.data.source.playback.PlaybackSourceOutcome {
        val provider = providers.find { it.providerId == providerId }
        if (provider == null) {
            return com.audiophile.musicplayer.data.source.playback.PlaybackSourceOutcome.Failed(
                com.audiophile.musicplayer.data.source.playback.PlaybackSourceFailure.of(
                    code = com.audiophile.musicplayer.data.source.playback.PlaybackSourceErrorCode.NOT_FOUND,
                    sourceLabel = providerId,
                    adapterId = "catalog"
                )
            )
        }
        Log.d("VANTA_PLAY_TRACK_REQUEST", "resolvePlayback provider=$providerId trackId=$trackId timeoutMs=$timeoutMs")
        return try {
            kotlinx.coroutines.withTimeout(timeoutMs) {
                provider.resolvePlayback(trackId, requestedQuality)
            }
        } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
            Log.e("VANTA_SEARCH", "SourceRegistry resolvePlayback TIMEOUT for ${provider.providerId}:$trackId")
            com.audiophile.musicplayer.data.source.playback.PlaybackSourceOutcome.Failed(
                com.audiophile.musicplayer.data.source.playback.PlaybackSourceFailure.of(
                    code = com.audiophile.musicplayer.data.source.playback.PlaybackSourceErrorCode.SOURCE_OFFLINE,
                    sourceLabel = provider.providerName,
                    adapterId = "catalog",
                    detail = "Timed out."
                )
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: java.io.IOException) {
            Log.e("VANTA_SEARCH", "SourceRegistry resolvePlayback IO error for ${provider.providerId}:$trackId")
            com.audiophile.musicplayer.data.source.playback.PlaybackSourceOutcome.Failed(
                com.audiophile.musicplayer.data.source.playback.PlaybackSourceFailure.of(
                    code = com.audiophile.musicplayer.data.source.playback.PlaybackSourceErrorCode.SOURCE_OFFLINE,
                    sourceLabel = provider.providerName,
                    adapterId = "catalog"
                )
            )
        }
    }

    /**
     * Resolve stream using priority-based fallback.
     * Attempts providers in order, skipping those that fail or time out.
     *
     * NOTE: each provider is resolved with the SAME [trackId]. Provider ID
     * namespaces differ (gateways strip the prefix and look the bare id up in a
     * different catalog), so a successful resolution may still belong to a *different*
     * recording. We therefore reject any stream whose reported providerId does not
     * match the provider that produced it (a cross-namespace collision signal).
     */
    suspend fun resolveStreamWithFallback(trackId: String, providerIds: List<String>, timeoutMs: Long = 10000L): ResolvedStream? {
        for (providerId in providerIds) {
            val provider = providers.find { it.providerId == providerId } ?: continue
            try {
                val resolved = withTimeout(timeoutMs) { provider.resolveStream(trackId) }
                if (resolved != null &&
                    resolved.streamUrl.isNotBlank() &&
                    (resolved.providerId == null || resolved.providerId == providerId)
                ) {
                    Log.d("VANTA_SEARCH", "SourceRegistry fallback success for ${provider.providerId}:$trackId")
                    return resolved
                }
            } catch (e: Exception) {
                Log.w("VANTA_SEARCH", "SourceRegistry fallback failed for ${provider.providerId}:$trackId")
            }
        }
        return null
    }

    /**
     * Race all [providerIds] in parallel and return the highest-bitrate stream.
     * Tie-breaks by provider order in [providerIds].
     */
    suspend fun resolveStreamParallelBest(
        trackId: String,
        providerIds: List<String>,
        timeoutMs: Long = 12_000L
    ): Pair<String, ResolvedStream>? = coroutineScope {
        val enabled = providerIds.mapNotNull { id -> providers.find { it.providerId == id } }
        if (enabled.isEmpty()) return@coroutineScope null

        val results = enabled.map { provider ->
            async {
                runCatching {
                    withTimeout(timeoutMs) {
                        provider.providerId to provider.resolveStream(trackId)
                    }
                }.getOrNull()
            }
        }

        val successes = results.mapNotNull { deferred ->
            val pair = runCatching { deferred.await() }.getOrNull() ?: return@mapNotNull null
            val (providerId, stream) = pair
            if (stream != null && stream.streamUrl.isNotBlank()) providerId to stream else null
        }

        if (successes.isEmpty()) return@coroutineScope null

        val best = successes.maxWithOrNull(
            compareByDescending<Pair<String, ResolvedStream>> {
                SourceIdentityGate.streamPlaybackScore(it.first, it.second)
            }.thenBy { providerIds.indexOf(it.first).let { index -> if (index < 0) Int.MAX_VALUE else index } }
        )
        best?.let {
            Log.d(
                "VANTA_PLAY_TRACK_REQUEST",
                "parallel_best provider=${it.first} trackId=$trackId bitrate=${it.second.bitrateKbps}"
            )
        }
        best
    }

    /**
     * Search a single provider with a custom timeout.
     * Used by debug broadcasts for provider-specific testing.
     */
    suspend fun searchSingle(providerId: String, query: String, timeoutMs: Long = 10000L): List<SourceSearchResult> {
        val provider = providers.find { it.providerId == providerId }
        if (provider == null) {
            Log.e("VANTA_SEARCH", "searchSingle: provider '$providerId' not found in registry (available=${providers.map { it.providerId }})")
            return emptyList()
        }
        val startMs = System.currentTimeMillis()
        return try {
            val results = withTimeout(timeoutMs) { provider.search(query) }
                .filter { result ->
                    ContentPurityFilter.isAllowed(
                        title = result.title,
                        artist = result.artist,
                        album = result.album,
                        durationMs = result.durationMs,
                        source = result.providerId,
                        userQuery = query
                    )
                }
            val durationMs = System.currentTimeMillis() - startMs
            Log.d("VANTA_SEARCH_PERF", "query='$query' providerId=$providerId resultCount=${results.size} durationMs=$durationMs timeout=false")
            results
        } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
            val durationMs = System.currentTimeMillis() - startMs
            Log.w("VANTA_SEARCH_PERF", "query='$query' providerId=$providerId durationMs=$durationMs timeout=true")
            emptyList()
        } catch (e: Exception) {
            val durationMs = System.currentTimeMillis() - startMs
            Log.e("VANTA_SEARCH_PERF", "query='$query' providerId=$providerId error='${e.message}' durationMs=$durationMs")
            emptyList()
        }
    }

    fun getProviderIds(): List<String> = providers.map { it.providerId }

    companion object {
        /**
         * Extract explicit expiry timestamp from common signed-URL query parameters.
         * Works with Akamai, CloudFront, and generic signed URLs.
         */
        fun extractExpiryFromUrl(url: String): Long? {
            return try {
                val uri = java.net.URI(url)
                val query = uri.query
                if (query != null) {
                    val params = query.split('&').associate {
                        val parts = it.split('=', limit = 2)
                        parts[0] to (parts.getOrNull(1) ?: "")
                    }
                    val fromQuery = params["expires"]?.toLongOrNull()
                        ?: params["Expires"]?.toLongOrNull()
                        ?: params["etsp"]?.toLongOrNull()
                        ?: params["exp"]?.toLongOrNull()
                        ?: params["e"]?.toLongOrNull()
                        ?: params["hdnts"]?.let { hdnts ->
                            hdnts.split('~').firstOrNull { it.startsWith("exp=") }
                                ?.removePrefix("exp=")?.toLongOrNull()
                        }
                    if (fromQuery != null) return fromQuery
                }
                neteasePathExpiryEpochMs(uri)
            } catch (_: java.net.URISyntaxException) {
                null
            } catch (_: IllegalArgumentException) {
                null
            }
        }

        /**
         * NetEase / GDStudio signed paths embed issued-at as `/20YYMMDDHHmmss/`
         * in China Standard Time. Those URLs die in minutes, not days.
         */
        internal fun neteasePathExpiryEpochMs(uri: java.net.URI): Long? {
            val host = uri.host?.lowercase() ?: return null
            if (!host.contains("126.net") && !host.contains("163.com")) return null
            val stamp = Regex("""/(20\d{12})(?:/|$)""")
                .find(uri.rawPath ?: uri.path.orEmpty())
                ?.groupValues
                ?.getOrNull(1)
                ?: return null
            val year = stamp.substring(0, 4).toIntOrNull() ?: return null
            val month = stamp.substring(4, 6).toIntOrNull() ?: return null
            val day = stamp.substring(6, 8).toIntOrNull() ?: return null
            val hour = stamp.substring(8, 10).toIntOrNull() ?: return null
            val minute = stamp.substring(10, 12).toIntOrNull() ?: return null
            val second = stamp.substring(12, 14).toIntOrNull() ?: return null
            return try {
                java.time.OffsetDateTime.of(
                    year, month, day, hour, minute, second, 0,
                    java.time.ZoneOffset.ofHours(8)
                ).toInstant().toEpochMilli() + 15L * 60_000L
            } catch (_: java.time.DateTimeException) {
                null
            }
        }

        /**
         * Infer a reasonable TTL (milliseconds) based on CDN host for URLs
         * without an explicit expiry parameter.
         */
        fun inferTtlFromHost(url: String): Long? {
            val host = runCatching { java.net.URI(url).host }.getOrNull() ?: return null
            return when {
                host.contains("qobuz", ignoreCase = true) ||
                host.contains("akamai", ignoreCase = true) ||
                host.contains("akamaized", ignoreCase = true) -> 120_000L // Qobuz/Akamai: ~2 min
                host.contains("cloudfront", ignoreCase = true) ||
                host.contains("aws", ignoreCase = true) -> 300_000L // CloudFront: ~5 min
                host.contains("tidal", ignoreCase = true) -> 300_000L // Tidal: ~5 min
                host.contains("deezer", ignoreCase = true) -> 300_000L
                host.contains("spotify", ignoreCase = true) ||
                host.contains("scdn", ignoreCase = true) -> 180_000L // Spotify: ~3 min
                host.contains("126.net", ignoreCase = true) ||
                host.contains("163.com", ignoreCase = true) -> 480_000L // NetEase signed: ~8 min
                else -> null // Unknown host; no inference
            }
        }

        /**
         * Returns the best-guess expiry timestamp (epoch ms) for a stream URL.
         * Priority: explicit query param > host-based TTL inference > no expiry.
         */
        fun resolveExpiryMs(url: String, resolvedExpiresAt: Long? = null): Long? {
            // 1. Use explicitly provided expiry (convert seconds to ms if needed)
            if (resolvedExpiresAt != null && resolvedExpiresAt > 0L) {
                // Providers may return Unix epoch in seconds (< 10T) vs milliseconds (> 10T)
                return if (resolvedExpiresAt < 100_000_000_000L) resolvedExpiresAt * 1000L else resolvedExpiresAt
            }
            // 2. Extract from URL query parameters (convert seconds to ms if needed)
            val fromParams = extractExpiryFromUrl(url)
            if (fromParams != null && fromParams > 0L) {
                return if (fromParams < 100_000_000_000L) fromParams * 1000L else fromParams
            }
            // 3. Infer from host + current time
            val ttl = inferTtlFromHost(url) ?: return null
            return System.currentTimeMillis() + ttl
        }

        /**
         * Returns the MINIMUM expiry (epoch ms) across all available sources:
         * provider-reported expiry, URL param expiry, and host-inferred TTL.
         *
         * For known temporary CDN hosts (Akamai 120s), this caps the effective TTL
         * even if the provider reports a far-future expiry (e.g. 28 min).
         */
        fun resolveMinExpiryMs(url: String, resolvedExpiresAt: Long? = null, providerId: String? = null): Long? {
            val now = System.currentTimeMillis()
            val host = runCatching { java.net.URI(url).host }.getOrNull() ?: "unknown"

            // 1. Provider-reported expiry as absolute epoch ms
            val providerExpiry = if (resolvedExpiresAt != null && resolvedExpiresAt > 0L) {
                if (resolvedExpiresAt < 100_000_000_000L) resolvedExpiresAt * 1000L else resolvedExpiresAt
            } else null

            // 2. URL query-param expiry as absolute epoch ms
            val paramExpiry = extractExpiryFromUrl(url)?.let {
                if (it < 100_000_000_000L) it * 1000L else it
            }

            // 3. Host-inferred TTL as absolute epoch ms
            val hostExpiry = inferTtlFromHost(url)?.let { now + it }

            // Return the minimum non-null value (most conservative = earliest expiry)
            val candidates = listOfNotNull(providerExpiry, paramExpiry, hostExpiry)
            val normalized = candidates.minOrNull()
            Log.d(
                "VANTA_SOURCE_EXPIRY",
                "providerId=${providerId ?: "unknown"} raw=${resolvedExpiresAt ?: "null"} " +
                    "providerMs=${providerExpiry ?: "null"} paramMs=${paramExpiry ?: "null"} " +
                    "hostMs=${hostExpiry ?: "null"} normalizedMs=${normalized ?: "null"} host=$host"
            )
            return normalized
        }
    }
}
