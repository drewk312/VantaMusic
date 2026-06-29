package com.audiophile.musicplayer.data.source

import android.util.Log
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
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

    override suspend fun search(query: String): List<SourceSearchResult> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext emptyList()
        try {
            val url = "$gatewayUrl/search?q=${URLEncoder.encode(query, "UTF-8")}&limit=25"
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
            val resultsArray = json.getAsJsonArray("results") ?: return@withContext emptyList()

            val output = mutableListOf<SourceSearchResult>()
            for (i in 0 until resultsArray.size()) {
                try {
                    val track = resultsArray[i].asJsonObject
                    val trackId = track.get("id")?.asString ?: continue
                    val title = track.get("title")?.asString ?: continue
                    if (title.isBlank()) continue
                    val artist = track.get("artist")?.asString ?: "Unknown Artist"
                    if (artist.isBlank()) continue

                    val artwork = track.get("artworkUrl")?.asString
                    val coverSeed = artwork ?: title

                    output.add(
                        SourceSearchResult(
                            id = trackId,
                            providerId = providerId,
                            title = title,
                            artist = artist,
                            album = track.get("album")?.asString,
                            coverSeed = coverSeed,
                            durationMs = track.get("durationMs")?.asLong,
                            isrc = track.get("isrc")?.asString,
                            status = SearchItemStatus.SOURCE_FOUND,
                            qualityLabel = track.get("quality")?.asString ?: "Stream"
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
            val url = "$gatewayUrl/stream?id=$trackId"
            val request = Request.Builder().url(url).build()
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
            val streamUrl = json.get("streamUrl")?.asString ?: return@withContext null
            if (streamUrl.isBlank()) return@withContext null

            ResolvedStream(
                streamUrl = streamUrl,
                bitrateKbps = json.get("bitrateKbps")?.asInt ?: 128,
                mimeType = json.get("mimeType")?.asString,
                expiresAt = json.get("expiresAt")?.asLong,
                qualityLabel = json.get("qualityLabel")?.asString
            )
        } catch (e: Exception) {
            Log.e("CloudflareGateway", "Stream resolve exception", e)
            null
        }
    }
}
