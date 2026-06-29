package com.audiophile.musicplayer.data.source

import android.util.Log
import com.google.gson.JsonElement
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

class ITunesMusicSourceProvider : MusicSourceProvider {
    override val providerId: String = "itunes_preview"
    override val providerName: String = "iTunes Preview"

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    override suspend fun search(query: String): List<SourceSearchResult> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext emptyList()
        try {
            val url = "https://itunes.apple.com/search?term=${URLEncoder.encode(query, "UTF-8")}&entity=song&limit=25"
            val request = Request.Builder().url(url).build()
            val body = client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.e("ITunesSource", "Search failed: ${response.code}")
                    return@withContext emptyList()
                }
                response.body?.string() ?: return@withContext emptyList()
            }
            val json = JsonParser.parseString(body)?.asJsonObject ?: return@withContext emptyList()
            val resultsArray = json.getAsJsonArray("results") ?: return@withContext emptyList()

            val output = mutableListOf<SourceSearchResult>()
            for (i in 0 until resultsArray.size()) {
                try {
                    val track = resultsArray[i].asJsonObject
                    val trackId = track.get("trackId")?.asLong ?: continue
                    val title = track.get("trackName")?.asString ?: continue
                    if (title.isBlank()) continue
                    val artist = track.get("artistName")?.asString ?: "Unknown Artist"
                    if (artist.isBlank()) continue
                    val previewUrl = track.get("previewUrl")?.asString ?: continue

                    val artwork = track.get("artworkUrl100")?.asString
                    val coverSeed = artwork?.replace("100x100bb", "600x600bb") ?: title

                    output.add(
                        SourceSearchResult(
                            id = trackId.toString(),
                            providerId = providerId,
                            title = title,
                            artist = artist,
                            album = track.get("collectionName")?.asString,
                            coverSeed = coverSeed,
                            durationMs = track.get("trackTimeMillis")?.asLong,
                            isrc = track.get("isrc")?.asString,
                            status = SearchItemStatus.PREVIEW,
                            qualityLabel = "AAC Preview"
                        )
                    )
                } catch (e: Exception) {
                    Log.e("ITunesSource", "Failed to parse track", e)
                }
            }
            output
        } catch (e: Exception) {
            Log.e("ITunesSource", "Search exception", e)
            emptyList()
        }
    }

    override suspend fun resolveStream(trackId: String): ResolvedStream? = withContext(Dispatchers.IO) {
        try {
            val url = "https://itunes.apple.com/lookup?id=$trackId&entity=song"
            val request = Request.Builder().url(url).build()
            val body = client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                response.body?.string() ?: return@withContext null
            }
            val json = JsonParser.parseString(body)?.asJsonObject ?: return@withContext null
            val resultsArray = json.getAsJsonArray("results") ?: return@withContext null
            if (resultsArray.size() == 0) return@withContext null

            val track = resultsArray[0].asJsonObject
            val previewUrl = track.get("previewUrl")?.asString ?: return@withContext null

            ResolvedStream(
                streamUrl = previewUrl,
                bitrateKbps = 256,
                mimeType = "audio/m4a",
                qualityLabel = "AAC Preview"
            )
        } catch (e: Exception) {
            Log.e("ITunesSource", "Resolve exception", e)
            null
        }
    }
}
