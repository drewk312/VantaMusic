package com.audiophile.musicplayer.data.source.external

import android.util.Log
import com.audiophile.musicplayer.data.source.ResolvedStream
import com.google.gson.JsonParser
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

internal data class CatalogStreamAttempt(
    val service: String,
    val trackId: String
)

internal data class CatalogCrossIds(
    val tidalId: String? = null,
    val qobuzId: String? = null,
    val deezerId: String? = null
)

internal object GatewayStreamResolver {
    private val client = OkHttpClient.Builder()
        .connectTimeout(4, TimeUnit.SECONDS)
        .readTimeout(28, TimeUnit.SECONDS)
        .callTimeout(30, TimeUnit.SECONDS)
        .followRedirects(false)
        .followSslRedirects(false)
        .addInterceptor(com.audiophile.musicplayer.playback.GatewayApiKeyInterceptor)
        .build()

    @Volatile
    var byoaStore: ByoaCredentialStore? = null

    private fun byoaHeaders(): Map<String, String> = byoaStore?.buildByoaHeaders().orEmpty()

    private fun applyByoaHeaders(builder: Request.Builder): Request.Builder {
        byoaHeaders().forEach { (k, v) -> builder.header(k, v) }
        return builder
    }

    private fun effectiveGatewayBaseUrl(fallback: String): String =
        byoaStore?.getEffectiveGatewayUrl()?.takeIf { it.isNotBlank() } ?: fallback

    /**
     * Exact catalog first, mapped catalogs only as later fallbacks.
     * Cross-catalog IDs are fallible; racing them first was a wrong-song source.
     */
    suspend fun buildStreamAttempts(trackId: String): List<CatalogStreamAttempt> = withContext(Dispatchers.IO) {
        val exact = exactCatalogAttempt(trackId) ?: return@withContext emptyList()
        val preferSpatial = SpotiFlacEndpoints.prefersSpatialMix()
        val mapped = when (exact.service) {
            "qobuz" -> CatalogCrossIds(tidalId = resolveTidalTrackIdFromQobuz(exact.trackId, preferSpatial))
            "deezer" -> resolveCrossIdsFromDeezer(exact.trackId, preferSpatial)
            else -> CatalogCrossIds()
        }
        val attempts = catalogAttemptsExactFirst(
            trackId = trackId,
            mappedTidalId = mapped.tidalId,
            mappedQobuzId = mapped.qobuzId
        )
        if (preferSpatial && !mapped.tidalId.isNullOrBlank()) {
            val tidalAttempt = attempts.find { it.service == "tidal" }
            if (tidalAttempt != null) {
                return@withContext listOf(tidalAttempt) + attempts.filter { it.service != "tidal" }
            }
        }
        attempts
    }

    suspend fun resolveCrossIdsFromDeezer(
        deezerTrackId: String,
        preferSpatial: Boolean = SpotiFlacEndpoints.prefersSpatialMix()
    ): CatalogCrossIds = withContext(Dispatchers.IO) {
        val id = deezerTrackId.removePrefix("deezer:").trim()
        if (id.isBlank()) return@withContext CatalogCrossIds()

        // 1. Gateway /resolve
        val gatewayBase = effectiveGatewayBaseUrl(SpotiFlacEndpoints.DEFAULT_GATEWAY_BASE_URL).trimEnd('/')
        val gatewayResolveUrl = "$gatewayBase/resolve?trackId=$id&provider=deezer"
        val gatewayRequest = applyByoaHeaders(Request.Builder()
            .url(gatewayResolveUrl)
            .header("User-Agent", "VANTA/1.0")
            .header("Accept", "application/json"))
            .build()

        val fromGateway = runCatching {
            client.newCall(gatewayRequest).execute().use { response ->
                if (!response.isSuccessful) return@use null
                val json = JsonParser.parseString(response.body?.string().orEmpty()).asJsonObject
                val atmosId = json.get("tidal_atmos_id")?.asString?.takeIf { it.isNotBlank() }
                val tidalId = json.get("tidal_id")?.asString?.takeIf { it.isNotBlank() }
                val qobuzId = json.get("qobuz_id")?.asString?.takeIf { it.isNotBlank() }
                val pickedTidal = if (preferSpatial && !atmosId.isNullOrBlank()) atmosId else (tidalId ?: atmosId)
                CatalogCrossIds(tidalId = pickedTidal, qobuzId = qobuzId, deezerId = id)
            }
        }.getOrNull()

        if (fromGateway != null && (!fromGateway.tidalId.isNullOrBlank() || !fromGateway.qobuzId.isNullOrBlank())) {
            return@withContext fromGateway
        }

        // 2. Fallback to SongLink
        val deezerUrl = "https://www.deezer.com/track/$id"
        val request = applyByoaHeaders(Request.Builder()
            .url(SpotiFlacEndpoints.buildSongLinkUrl(deezerUrl))
            .header("User-Agent", "VANTA/1.0"))
            .build()

        runCatching {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext CatalogCrossIds(deezerId = id)
                val root = JsonParser.parseString(response.body?.string().orEmpty()).asJsonObject
                val links = root.getAsJsonObject("links") ?: root.getAsJsonObject("linksByPlatform") ?: return@withContext CatalogCrossIds(deezerId = id)
                val tidalId = links.getAsJsonObject("tidal")?.get("url")?.asString
                    ?.let { extractTidalTrackId(it) }
                val qobuzId = links.getAsJsonObject("qobuz")?.get("url")?.asString
                    ?.let { extractQobuzTrackId(it) }
                Log.d(
                    "VANTA_PLAY_TRACK_REQUEST",
                    "songlink deezer=$id tidal=$tidalId qobuz=$qobuzId"
                )
                CatalogCrossIds(tidalId = tidalId, qobuzId = qobuzId, deezerId = id)
            }
        }.getOrElse { CatalogCrossIds(deezerId = id) }
    }

    private fun extractQobuzTrackId(value: String): String? {
        if (value.isBlank()) return null
        return Regex("""/track/(\d+)""").find(value)?.groupValues?.getOrNull(1)
    }

    suspend fun resolveGetUrls(
        provider: ExternalSourceProvider,
        urls: List<String>,
        operation: String
    ): ResolvedStream? = resolveGetUrlsParallel(provider, urls, operation)

    suspend fun resolveGetUrlsParallel(
        provider: ExternalSourceProvider,
        urls: List<String>,
        operation: String
    ): ResolvedStream? = coroutineScope {
        if (urls.isEmpty()) return@coroutineScope null
        val effectiveUrls = urls.map { url ->
            val base = effectiveGatewayBaseUrl(url)
            if (url.startsWith(SpotiFlacEndpoints.DEFAULT_GATEWAY_BASE_URL, ignoreCase = true) && base != SpotiFlacEndpoints.DEFAULT_GATEWAY_BASE_URL) {
                url.replace(SpotiFlacEndpoints.DEFAULT_GATEWAY_BASE_URL, base.trimEnd('/') + "/")
            } else url
        }
        val results = effectiveUrls.map { url ->
            async(Dispatchers.IO) {
                val request = applyByoaHeaders(Request.Builder()
                    .url(url)
                    .header("User-Agent", "VANTA/1.0"))
                    .build()
                runCatching {
                    client.newCall(request).execute().use { response ->
                        val parsed = com.audiophile.musicplayer.data.source.GatewayStreamResponseParser.locationOrBody(
                            code = response.code,
                            location = response.header("Location"),
                            body = response.body?.string()
                        ) ?: return@use null
                        provider.parseStreamResultPublic(parsed)
                    }
                }.getOrNull()
            }
        }
        firstReadyStream(results)
    }

    suspend fun firstReadyStream(jobs: List<Deferred<ResolvedStream?>>): ResolvedStream? {
        if (jobs.isEmpty()) return null
        return coroutineScope {
            val winner = CompletableDeferred<ResolvedStream?>()
            jobs.forEach { job ->
                launch {
                    val value = runCatching { job.await() }.getOrNull()
                    if (value != null) winner.complete(value)
                }
            }
            launch {
                jobs.forEach { runCatching { it.join() } }
                if (winner.isActive) winner.complete(null)
            }
            val result = winner.await()
            jobs.forEach { it.cancel() }
            result
        }
    }

    suspend fun resolveCommunityStream(
        provider: ExternalSourceProvider,
        endpoint: String,
        trackId: String,
        service: String,
        quality: String? = null
    ): ResolvedStream? = withContext(Dispatchers.IO) {
        val preferred = quality ?: SpotiFlacEndpoints.PREFERRED_STREAM_QUALITY
        val prefersSpatial = SpotiFlacEndpoints.prefersSpatialMix(preferred)
        val qualities = when (service.lowercase()) {
            "tidal" -> {
                val fallbacks = mutableListOf<String>()
                if (prefersSpatial) {
                    fallbacks.add("atmos")
                }
                fallbacks.add(preferred)
                if (!fallbacks.contains("atmos")) fallbacks.add("atmos")
                if (!fallbacks.contains("HI_RES")) fallbacks.add("HI_RES")
                if (!fallbacks.contains("16")) fallbacks.add("16")
                fallbacks
            }
            "amazon" -> {
                val fallbacks = mutableListOf<String>()
                if (prefersSpatial) {
                    fallbacks.add("atmos")
                    fallbacks.add("360")
                }
                fallbacks.add(preferred)
                if (!fallbacks.contains("atmos")) fallbacks.add("atmos")
                if (!fallbacks.contains("360")) fallbacks.add("360")
                if (!fallbacks.contains("HI_RES")) fallbacks.add("HI_RES")
                if (!fallbacks.contains("16")) fallbacks.add("16")
                fallbacks
            }
            else -> listOf(preferred)
        }.distinct()
        Log.d(
            "VANTA_TIDAL_ATMOS",
            "resolveCommunityStream service=$service preferred=$preferred qualities=$qualities"
        )
        for (requestedQuality in qualities) {
            val stream = resolveCommunityStreamOnce(provider, endpoint, trackId, service, requestedQuality)
            if (stream != null) {
                Log.d(
                    "VANTA_TIDAL_ATMOS",
                    "resolveCommunityStream SUCCESS quality=$requestedQuality url=${stream.streamUrl.take(120)}..."
                )
                return@withContext stream
            }
            Log.d(
                "VANTA_TIDAL_ATMOS",
                "resolveCommunityStream FAILED quality=$requestedQuality, trying next..."
            )
        }
        Log.w("VANTA_TIDAL_ATMOS", "resolveCommunityStream ALL QUALITIES FAILED service=$service id=$trackId")
        null
    }

    private suspend fun resolveCommunityStreamOnce(
        provider: ExternalSourceProvider,
        endpoint: String,
        trackId: String,
        service: String,
        quality: String
    ): ResolvedStream? {
        val url = endpoint.trim().trimEnd('/').let { base ->
            if (base.endsWith(SpotiFlacEndpoints.COMMUNITY_DOWNLOAD_PATH)) base
            else "$base${SpotiFlacEndpoints.COMMUNITY_DOWNLOAD_PATH}"
        }
        val payload = SpotiFlacEndpoints.buildCommunityDownloadPayload(
            trackId,
            quality,
            service
        )
        val numericId = trackId.substringAfter(":", trackId).trim()
        val isAtmosRequest = quality.equals("atmos", ignoreCase = true)
        byoaStore?.refreshTidalAccessIfNeeded(client)
        Log.d(
            "VANTA_TIDAL_ATMOS",
            "POST /api/dl service=$service id=$numericId quality=$quality atmos=$isAtmosRequest endpoint=${url.take(80)}"
        )
        val request = applyByoaHeaders(Request.Builder()
            .url(url)
            .header("User-Agent", "VANTA/1.0")
            .header("Accept", "application/json")
            .header("Content-Type", "application/json"))
            .post(payload.toRequestBody("application/json".toMediaType()))
            .build()
        return runCatching {
            client.newCall(request).execute().use { response ->
                val bodyPreview = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    Log.d(
                        "VANTA_TIDAL_ATMOS",
                        "community FAILED status=${response.code} quality=$quality service=$service " +
                        "body=${bodyPreview.take(200)}"
                    )
                    return@use null
                }
                Log.d(
                    "VANTA_TIDAL_ATMOS",
                    "community OK status=${response.code} quality=$quality service=$service " +
                    "body=${bodyPreview.take(200)}"
                )
                provider.parseStreamResultPublic(bodyPreview)
            }
        }.getOrElse { e ->
            Log.e("VANTA_TIDAL_ATMOS", "community EXCEPTION quality=$quality service=$service error='${e.message}'")
            null
        }
    }

    suspend fun resolveTidalTrackIdFromQobuz(
        qobuzTrackId: String,
        preferSpatial: Boolean = SpotiFlacEndpoints.prefersSpatialMix()
    ): String? = withContext(Dispatchers.IO) {
        val cleanId = qobuzTrackId.removePrefix("qobuz:").trim()
        if (cleanId.isBlank()) return@withContext null

        // 1. Gateway /resolve
        val gatewayBase = effectiveGatewayBaseUrl(SpotiFlacEndpoints.DEFAULT_GATEWAY_BASE_URL).trimEnd('/')
        val gatewayResolveUrl = "$gatewayBase/resolve?trackId=$cleanId&provider=qobuz"
        val gatewayRequest = applyByoaHeaders(Request.Builder()
            .url(gatewayResolveUrl)
            .header("User-Agent", "VANTA/1.0")
            .header("Accept", "application/json"))
            .build()

        val fromGateway = runCatching {
            client.newCall(gatewayRequest).execute().use { response ->
                if (!response.isSuccessful) return@use null
                val json = JsonParser.parseString(response.body?.string().orEmpty()).asJsonObject
                val atmosId = json.get("tidal_atmos_id")?.asString?.takeIf { it.isNotBlank() }
                val tidalId = json.get("tidal_id")?.asString?.takeIf { it.isNotBlank() }
                if (preferSpatial && !atmosId.isNullOrBlank()) atmosId else (tidalId ?: atmosId)
            }
        }.getOrNull()

        if (!fromGateway.isNullOrBlank()) return@withContext fromGateway

        // 2. Legacy song.link fallback
        val request = applyByoaHeaders(Request.Builder()
            .url(SpotiFlacEndpoints.buildSongLinkUrl(SpotiFlacEndpoints.buildQobuzOpenTrackUrl(cleanId)))
            .header("User-Agent", "VANTA/1.0"))
            .build()
        runCatching {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                val tidalUrl = JsonParser.parseString(response.body?.string().orEmpty())
                    .asJsonObject.getAsJsonObject("linksByPlatform")
                    ?.getAsJsonObject("tidal")
                    ?.get("url")?.asString.orEmpty()
                extractTidalTrackId(tidalUrl)
            }
        }.getOrNull()
    }

    /** Direct zero-config mirrors (WJHE + gateway GET) raced in parallel with community POST. */
    suspend fun resolveDirectMirrors(
        provider: ExternalSourceProvider,
        trackId: String,
        gatewayBaseUrl: String
    ): ResolvedStream? = coroutineScope {
        val qobuzId = when {
            trackId.startsWith("qobuz:", ignoreCase = true) -> trackId.removePrefix("qobuz:").trim()
            else -> resolveCrossIdsFromDeezer(trackId).qobuzId
        }
        val jobs = buildList {
            if (!qobuzId.isNullOrBlank() && SpotiFlacEndpoints.QOBUZ_WJHE_STREAM_API_URL.isNotBlank()) {
                add(async(Dispatchers.IO) {
                    resolveGetUrlsParallel(
                        provider,
                        listOf(SpotiFlacEndpoints.buildQobuzWjheStreamUrl(qobuzId)),
                        "wjhe_stream"
                    )
                })
            }
            val catalogId = trackId.removePrefix("tidal:").removePrefix("qobuz:").removePrefix("deezer:").trim()
            if (catalogId.isNotBlank()) {
                add(async(Dispatchers.IO) {
                    resolveGetUrlsParallel(
                        provider,
                        SpotiFlacEndpoints.buildAddonStreamUrls(gatewayBaseUrl, catalogId),
                        "addon_stream_parallel"
                    )
                })
            }
        }
        firstReadyStream(jobs)
    }

    suspend fun resolveFirstCommunityStream(
        provider: ExternalSourceProvider,
        endpoint: String,
        attempts: List<CatalogStreamAttempt>
    ): ResolvedStream? {
        for (attempt in attempts) {
            val prefixedId = "${attempt.service}:${attempt.trackId}"
            Log.d(
                "VANTA_PLAY_TRACK_REQUEST",
                "sequential_resolve service=${attempt.service} id=${attempt.trackId}"
            )
            val stream = resolveCommunityStream(provider, endpoint, prefixedId, attempt.service)
            if (stream != null) return stream
        }
        return null
    }

    private fun extractTidalTrackId(value: String): String? {
        if (value.isBlank()) return null
        listOf("""/track/(\d+)""", """[?&]id=(\d+)""", """(\d{6,})""").forEach { pattern ->
            val matcher = Pattern.compile(pattern).matcher(value)
            if (matcher.find()) return matcher.group(1)
        }
        return null
    }
}

internal fun exactCatalogAttempt(trackId: String): CatalogStreamAttempt? {
    val trimmed = trackId.trim()
    if (trimmed.isEmpty()) return null
    val separator = trimmed.indexOf(':')
    if (separator > 0 && separator < trimmed.lastIndex) {
        val service = trimmed.substring(0, separator).lowercase()
        val id = trimmed.substring(separator + 1).trim()
        if (id.isEmpty()) return null
        return when (service) {
            "tidal", "qobuz", "deezer", "amazon", "pandora" -> CatalogStreamAttempt(service, id)
            else -> null
        }
    }
    return if (trimmed.matches(Regex("""^\d{4,}$"""))) {
        CatalogStreamAttempt("qobuz", trimmed)
    } else {
        null
    }
}

internal fun catalogAttemptsExactFirst(
    trackId: String,
    mappedTidalId: String? = null,
    mappedQobuzId: String? = null
): List<CatalogStreamAttempt> {
    val exact = exactCatalogAttempt(trackId) ?: return emptyList()
    val attempts = mutableListOf(exact)
    fun addFallback(service: String, id: String?) {
        val value = id?.trim().orEmpty()
        if (value.isEmpty()) return
        if (service.equals(exact.service, ignoreCase = true) && value == exact.trackId) return
        attempts += CatalogStreamAttempt(service, value)
    }
    when (exact.service) {
        "qobuz", "deezer" -> {
            addFallback("tidal", mappedTidalId)
            addFallback("qobuz", mappedQobuzId)
        }
    }
    return attempts.distinctBy { "${it.service}:${it.trackId}" }
}

/** Tidal resolvers may map a Qobuz id via song.link. They must never use the Qobuz number as a Tidal id. */
internal fun tidalIdForTidalProvider(trackId: String, mappedTidalId: String?): String? {
    val trimmed = trackId.trim()
    if (trimmed.startsWith("tidal:", ignoreCase = true)) {
        return trimmed.removePrefix("tidal:").trim().takeIf { it.isNotEmpty() }
    }
    return mappedTidalId?.trim()?.takeIf { it.isNotEmpty() }
}
