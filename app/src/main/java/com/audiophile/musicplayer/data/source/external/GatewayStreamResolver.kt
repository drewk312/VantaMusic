package com.audiophile.musicplayer.data.source.external

import android.util.Log
import com.audiophile.musicplayer.data.source.ResolvedStream
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
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
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    /** Ordered /api/dl attempts — Tidal first for Deezer/Qobuz hits (best masters). */
    suspend fun buildStreamAttempts(trackId: String): List<CatalogStreamAttempt> = withContext(Dispatchers.IO) {
        val trimmed = trackId.trim()
        val attempts = mutableListOf<CatalogStreamAttempt>()

        when {
            trimmed.startsWith("tidal:", ignoreCase = true) -> {
                attempts += CatalogStreamAttempt("tidal", trimmed.removePrefix("tidal:").trim())
            }
            trimmed.startsWith("qobuz:", ignoreCase = true) -> {
                val qobuzId = trimmed.removePrefix("qobuz:").trim()
                resolveTidalTrackIdFromQobuz(qobuzId)?.let {
                    attempts += CatalogStreamAttempt("tidal", it)
                }
                attempts += CatalogStreamAttempt("qobuz", qobuzId)
            }
            trimmed.startsWith("amazon:", ignoreCase = true) -> {
                attempts += CatalogStreamAttempt("amazon", trimmed.removePrefix("amazon:").trim())
            }
            trimmed.startsWith("pandora:", ignoreCase = true) -> {
                attempts += CatalogStreamAttempt("pandora", trimmed.removePrefix("pandora:").trim())
            }
            trimmed.startsWith("deezer:", ignoreCase = true) -> {
                val deezerId = trimmed.removePrefix("deezer:").trim()
                val cross = resolveCrossIdsFromDeezer(deezerId)
                cross.tidalId?.let { attempts += CatalogStreamAttempt("tidal", it) }
                cross.qobuzId?.let { attempts += CatalogStreamAttempt("qobuz", it) }
                attempts += CatalogStreamAttempt("deezer", deezerId)
            }
            else -> {
                val cross = resolveCrossIdsFromDeezer(trimmed)
                cross.tidalId?.let { attempts += CatalogStreamAttempt("tidal", it) }
                cross.qobuzId?.let { attempts += CatalogStreamAttempt("qobuz", it) }
                attempts += CatalogStreamAttempt("deezer", trimmed)
            }
        }

        attempts.distinctBy { "${it.service}:${it.trackId}" }
    }

    suspend fun resolveCrossIdsFromDeezer(deezerTrackId: String): CatalogCrossIds = withContext(Dispatchers.IO) {
        val id = deezerTrackId.removePrefix("deezer:").trim()
        if (id.isBlank()) return@withContext CatalogCrossIds()

        val deezerUrl = "https://www.deezer.com/track/$id"
        val request = Request.Builder()
            .url(SpotiFlacEndpoints.buildSongLinkUrl(deezerUrl))
            .header("User-Agent", "VANTA/1.0")
            .build()

        runCatching {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext CatalogCrossIds(deezerId = id)
                val root = JsonParser.parseString(response.body?.string().orEmpty()).asJsonObject
                val links = root.getAsJsonObject("linksByPlatform") ?: return@withContext CatalogCrossIds(deezerId = id)
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
        val results = urls.map { url ->
            async(Dispatchers.IO) {
                val request = Request.Builder()
                    .url(url)
                    .header("User-Agent", "VANTA/1.0")
                    .build()
                runCatching {
                    client.newCall(request).execute().use { response ->
                        if (!response.isSuccessful) return@use null
                        val body = response.body?.string()
                        if (body.isNullOrBlank()) null
                        else provider.parseStreamResultPublic(body)
                    }
                }.getOrNull()
            }
        }
        results
            .mapNotNull { runCatching { it.await() }.getOrNull() }
            .maxByOrNull { it.bitrateKbps }
    }

    suspend fun resolveCommunityStream(
        provider: ExternalSourceProvider,
        endpoint: String,
        trackId: String,
        service: String
    ): ResolvedStream? = withContext(Dispatchers.IO) {
        val url = endpoint.trim().trimEnd('/').let { base ->
            if (base.endsWith(SpotiFlacEndpoints.COMMUNITY_DOWNLOAD_PATH)) base
            else "$base${SpotiFlacEndpoints.COMMUNITY_DOWNLOAD_PATH}"
        }
        val payload = SpotiFlacEndpoints.buildCommunityDownloadPayload(
            trackId,
            SpotiFlacEndpoints.PREFERRED_STREAM_QUALITY,
            service
        )
        val numericId = trackId.substringAfter(":", trackId).trim()
        Log.d(
            "VANTA_PLAY_TRACK_REQUEST",
            "POST /api/dl service=$service id=$numericId endpoint=${url.take(80)}"
        )
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "VANTA/1.0")
            .header("Accept", "application/json")
            .header("Content-Type", "application/json")
            .post(payload.toRequestBody("application/json".toMediaType()))
            .build()
        runCatching {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.d("VANTA_${service.uppercase()}_GATEWAY", "community failed status=${response.code}")
                    return@withContext null
                }
                provider.parseStreamResultPublic(response.body?.string().orEmpty())
            }
        }.getOrNull()
    }

    suspend fun resolveTidalTrackIdFromQobuz(qobuzTrackId: String): String? = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(SpotiFlacEndpoints.buildSongLinkUrl(SpotiFlacEndpoints.buildQobuzOpenTrackUrl(qobuzTrackId)))
            .header("User-Agent", "VANTA/1.0")
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
        jobs.mapNotNull { runCatching { it.await() }.getOrNull() }.maxByOrNull { it.bitrateKbps }
    }

    suspend fun resolveFirstCommunityStream(
        provider: ExternalSourceProvider,
        endpoint: String,
        attempts: List<CatalogStreamAttempt>
    ): ResolvedStream? = coroutineScope {
        if (attempts.isEmpty()) return@coroutineScope null
        val results = attempts.mapIndexed { index, attempt ->
            async(Dispatchers.IO) {
                val prefixedId = "${attempt.service}:${attempt.trackId}"
                Log.d(
                    "VANTA_PLAY_TRACK_REQUEST",
                    "parallel_resolve service=${attempt.service} id=${attempt.trackId}"
                )
                index to resolveCommunityStream(provider, endpoint, prefixedId, attempt.service)
            }
        }
        results
            .mapNotNull { runCatching { it.await() }.getOrNull() }
            .filter { it.second != null }
            .minByOrNull { it.first }
            ?.second
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
