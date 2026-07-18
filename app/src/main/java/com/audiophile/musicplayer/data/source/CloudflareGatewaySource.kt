package com.audiophile.musicplayer.data.source

import android.util.Log
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

class CloudflareGatewaySource(
    private val gatewayUrl: String = "https://vanta-music-gateway.16drewk.workers.dev"
) : MusicSourceProvider {

    override val providerId: String = "cloudflare_gateway"
    override val providerName: String = "Cloudflare Gateway"

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    /** Current editorial releases. These are catalog tracks, not codec claims. */
    suspend fun editorialNewReleases(limit: Int = 18): List<SourceSearchResult> = withContext(Dispatchers.IO) {
        try {
            val url = "$gatewayUrl/api/new-releases?limit=${limit.coerceIn(1, 30)}"
            val body = client.newCall(Request.Builder().url(url).build()).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.w("CloudflareGateway", "New releases failed: ${response.code}")
                    return@withContext emptyList()
                }
                response.body?.string() ?: return@withContext emptyList()
            }
            val tracks = JsonParser.parseString(body)?.asJsonObject?.getAsJsonArray("tracks")
                ?: return@withContext emptyList()
            buildList {
                for (item in tracks) {
                    val track = item.asJsonObject
                    val id = track.get("id")?.asString ?: continue
                    val title = track.get("title")?.asString?.takeIf { it.isNotBlank() } ?: continue
                    val artist = track.get("artist")?.asString?.takeIf { it.isNotBlank() } ?: continue
                    add(
                        SourceSearchResult(
                            id = "deezer:$id",
                            providerId = providerId,
                            title = title,
                            artist = artist,
                            album = track.get("album")?.asString,
                            coverSeed = track.get("artworkURL")?.asString ?: "$title-$artist",
                            durationMs = track.get("duration")?.asLong?.times(1000L),
                            isrc = track.get("isrc")?.asString,
                            status = SearchItemStatus.SOURCE_FOUND,
                            qualityLabel = "Catalog metadata"
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Log.w("CloudflareGateway", "New releases exception", e)
            emptyList()
        }
    }

    override suspend fun search(query: String): List<SourceSearchResult> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext emptyList()
        try {
            val url = "$gatewayUrl/api/search?q=${URLEncoder.encode(query, "UTF-8")}&limit=50"
            val request = Request.Builder().url(url).build()
            val body = client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.w("CloudflareGateway", "Search failed: ${response.code}")
                    return@withContext emptyList()
                }
                response.body?.string() ?: return@withContext emptyList()
            }
            val json = JsonParser.parseString(body)?.asJsonObject ?: return@withContext emptyList()
            if (json.get("error")?.asString != null) {
                Log.w("CloudflareGateway", "Gateway error: ${json.get("error").asString}")
                return@withContext emptyList()
            }
            val resultsArray = json.getAsJsonArray("tracks")
                ?: json.getAsJsonArray("results")
                ?: return@withContext emptyList()

            val output = mutableListOf<SourceSearchResult>()
            for (i in 0 until resultsArray.size()) {
                try {
                    val track = resultsArray[i].asJsonObject
                    val trackId = track.get("id")?.asString ?: continue
                    val title = track.get("title")?.asString ?: continue
                    if (title.isBlank()) continue
                    val artist = track.get("artist")?.asString ?: "Unknown Artist"
                    if (artist.isBlank()) continue
                    val sourceProvider = track.get("provider")?.asString?.trim()?.lowercase().orEmpty()
                    val resolvedProvider = sourceProvider.ifBlank {
                        when {
                            track.has("deezer_id") -> "deezer"
                            track.has("qobuz_id") -> "qobuz"
                            track.has("tidal_id") -> "tidal"
                            track.has("amazon_id") -> "amazon"
                            track.has("apple_id") -> "apple"
                            else -> ""
                        }
                    }

                    val artwork = track.get("artworkURL")?.asString ?: track.get("artworkUrl")?.asString
                    val coverSeed = artwork ?: title
                    val durationMs = track.get("durationMs")?.asLong
                        ?: track.get("duration")?.asLong?.times(1000L)
                    val gatewayId = if (resolvedProvider.isNotBlank()) {
                        "$resolvedProvider:$trackId"
                    } else {
                        trackId
                    }
                    val status = if (resolvedProvider == "apple") {
                        SearchItemStatus.METADATA_ONLY
                    } else {
                        SearchItemStatus.SOURCE_FOUND
                    }

                    val isDolbyAtmos = track.get("isDolbyAtmos")?.asBoolean == true ||
                        containsAtmosSignal(title, track.get("album")?.asString, track.get("audioQuality")?.asString, track.get("quality")?.asString)
                    val isSpatialAudio = track.get("isSpatialAudio")?.asBoolean == true || isDolbyAtmos ||
                        containsSpatialSignal(title, track.get("album")?.asString, track.get("audioQuality")?.asString, track.get("quality")?.asString)
                    val isSurround = track.get("isSurround")?.asBoolean == true || isDolbyAtmos ||
                        containsSurroundSignal(title, track.get("album")?.asString, track.get("audioQuality")?.asString, track.get("quality")?.asString)
                    val isHiRes = track.get("isHiRes")?.asBoolean == true ||
                        containsHiResSignal(track.get("audioQuality")?.asString, track.get("quality")?.asString, track.get("format")?.asString)

                    output.add(
                        SourceSearchResult(
                            id = gatewayId,
                            providerId = providerId,
                            title = title,
                            artist = artist,
                            album = track.get("album")?.asString,
                            coverSeed = coverSeed,
                            durationMs = durationMs,
                            isrc = track.get("isrc")?.asString,
                            status = status,
                            qualityLabel = track.get("audioQuality")?.asString
                                ?: track.get("quality")?.asString
                                ?: "Stream",
                            isDolbyAtmos = isDolbyAtmos,
                            isSpatialAudio = isSpatialAudio,
                            isSurround = isSurround,
                            isHiRes = isHiRes,
                            spatialEvidence = track.get("spatialEvidence")?.asString
                                ?: if (isDolbyAtmos || isSpatialAudio || isSurround) "metadata" else null
                        )
                    )
                } catch (e: Exception) {
                    Log.e("CloudflareGateway", "Failed to parse search result", e)
                }
            }
            output
        } catch (e: Exception) {
            Log.e("CloudflareGateway", "Search exception", e)
            emptyList()
        }
    }

    override suspend fun resolveStream(trackId: String): ResolvedStream? = withContext(Dispatchers.IO) {
        try {
            val (providerHint, cleanTrackId) = splitGatewayTrackId(trackId)
            val payload = buildString {
                append("{\"id\":\"" )
                append(escapeJson(cleanTrackId))
                append("\",\"quality\":\"24\"" )
                if (!providerHint.isNullOrBlank()) {
                    append("\",\"provider\":\"" )
                    append(escapeJson(providerHint))
                    append("\"" )
                }
                append("}")
            }
            val request = Request.Builder()
                .url("$gatewayUrl/api/dl")
                .post(payload.toRequestBody(jsonMediaType))
                .build()
            val body = client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.w("CloudflareGateway", "Stream resolve failed: ${response.code}")
                    return@withContext null
                }
                response.body?.string() ?: return@withContext null
            }
            val json = JsonParser.parseString(body)?.asJsonObject ?: return@withContext null
            if (json.get("error")?.asString != null) {
                Log.w("CloudflareGateway", "Stream error: ${json.get("error").asString}")
                return@withContext null
            }
            val streamUrl = json.get("streamUrl")?.asString
                ?: json.get("url")?.asString
                ?: return@withContext null
            if (streamUrl.isBlank()) return@withContext null
            val expiresAt = json.get("expiresAt")?.asLong?.let { value ->
                if (value in 1 until 100_000_000_000L) value * 1000L else value
            }

            ResolvedStream(
                streamUrl = streamUrl,
                bitrateKbps = json.get("bitrateKbps")?.asInt ?: 128,
                mimeType = json.get("mimeType")?.asString,
                expiresAt = expiresAt,
                qualityLabel = json.get("quality")?.asString
                    ?: json.get("qualityLabel")?.asString
                    ?: json.get("audioQuality")?.asString,
                format = json.get("format")?.asString ?: json.get("codec")?.asString,
                isSpatialAudio = json.get("isSpatialAudio")?.asBoolean == true ||
                    json.get("spatialAudio")?.asBoolean == true ||
                    json.get("spatial")?.asBoolean == true ||
                    containsSpatialSignal(json.get("quality")?.asString, json.get("qualityLabel")?.asString, json.get("audioQuality")?.asString),
                isDolbyAtmos = json.get("isDolbyAtmos")?.asBoolean == true ||
                    json.get("dolbyAtmos")?.asBoolean == true ||
                    containsAtmosSignal(json.get("quality")?.asString, json.get("qualityLabel")?.asString, json.get("audioQuality")?.asString, json.get("codec")?.asString),
                isSurround = json.get("isSurround")?.asBoolean == true ||
                    json.get("surround")?.asBoolean == true ||
                    containsSurroundSignal(json.get("quality")?.asString, json.get("qualityLabel")?.asString, json.get("audioQuality")?.asString),
                providerId = json.get("provider")?.asString ?: providerHint
            )
        } catch (e: Exception) {
            Log.e("CloudflareGateway", "Stream resolve exception", e)
            null
        }
    }

    private fun splitGatewayTrackId(trackId: String): Pair<String?, String> {
        val separator = trackId.indexOf(':')
        if (separator <= 0 || separator == trackId.lastIndex) return null to trackId
        return trackId.substring(0, separator).lowercase() to trackId.substring(separator + 1)
    }

    private fun containsAtmosSignal(vararg values: String?): Boolean =
        values.any { value ->
            val text = value?.lowercase().orEmpty()
            "dolby atmos" in text || "atmos" in text || "e-ac-3 joc" in text || "eac3-joc" in text
        }

    private fun containsSpatialSignal(vararg values: String?): Boolean =
        values.any { value ->
            val text = value?.lowercase().orEmpty()
            "spatial audio" in text || "spatial" in text || containsAtmosSignal(value)
        }

    private fun containsSurroundSignal(vararg values: String?): Boolean =
        values.any { value ->
            val text = value?.lowercase().orEmpty()
            "surround" in text || "5.1" in text || "7.1" in text || containsAtmosSignal(value)
        }

    private fun containsHiResSignal(vararg values: String?): Boolean =
        values.any { value ->
            val text = value?.lowercase().orEmpty()
            "hi-res" in text || "hires" in text || "24-bit" in text || "24 bit" in text ||
                "24/" in text || "96" in text || "192" in text || "88" in text || "176" in text
        }

    private fun escapeJson(value: String): String =
        value.replace("\\", "\\\\").replace("\"", "\\\"")
}
