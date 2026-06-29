package com.audiophile.musicplayer.data.lastfm

import android.util.Log
import com.google.gson.JsonParser
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

class LastFmApiClient(
    private val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()
) {
    companion object {
        private const val TAG = "LastFmApiClient"
        private const val BASE_URL = "https://ws.audioscrobbler.com/2.0/"
    }

    fun artistTopTags(apiKey: String, artistName: String): List<Pair<String, Int>> {
        if (apiKey.isBlank() || artistName.isBlank()) return emptyList()
        return try {
            val url = BASE_URL.toHttpUrlOrNull()
                ?.newBuilder()
                ?.addQueryParameter("method", "artist.getTopTags")
                ?.addQueryParameter("artist", artistName)
                ?.addQueryParameter("api_key", apiKey)
                ?.addQueryParameter("format", "json")
                ?.build() ?: return emptyList()

            val request = Request.Builder().url(url).build()
            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.w(TAG, "artist.getTopTags HTTP ${response.code} for $artistName")
                    return emptyList()
                }
                val body = response.body?.string().orEmpty()
                if (body.isBlank()) return emptyList()
                parseTopTags(body)
            }
        } catch (e: Exception) {
            Log.w(TAG, "artist.getTopTags failed for $artistName: ${e.message}")
            emptyList()
        }
    }

    fun artistSimilar(apiKey: String, artistName: String, limit: Int = 8): List<String> {
        if (apiKey.isBlank() || artistName.isBlank()) return emptyList()
        return try {
            val url = BASE_URL.toHttpUrlOrNull()
                ?.newBuilder()
                ?.addQueryParameter("method", "artist.getSimilar")
                ?.addQueryParameter("artist", artistName)
                ?.addQueryParameter("api_key", apiKey)
                ?.addQueryParameter("limit", limit.coerceIn(1, 20).toString())
                ?.addQueryParameter("format", "json")
                ?.build() ?: return emptyList()

            okHttpClient.newCall(Request.Builder().url(url).build()).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.w(TAG, "artist.getSimilar HTTP ${response.code} for $artistName")
                    return emptyList()
                }
                val body = response.body?.string().orEmpty()
                if (body.isBlank()) return emptyList()
                parseSimilarArtists(body)
            }
        } catch (e: Exception) {
            Log.w(TAG, "artist.getSimilar failed for $artistName: ${e.message}")
            emptyList()
        }
    }

    fun validateApiKey(apiKey: String): String? {
        if (apiKey.isBlank()) return "API key is blank"
        return try {
            val url = BASE_URL.toHttpUrlOrNull()
                ?.newBuilder()
                ?.addQueryParameter("method", "artist.getTopTags")
                ?.addQueryParameter("artist", "Radiohead")
                ?.addQueryParameter("api_key", apiKey)
                ?.addQueryParameter("format", "json")
                ?.build() ?: return "Invalid URL"

            okHttpClient.newCall(Request.Builder().url(url).build()).execute().use { response ->
                when {
                    response.isSuccessful -> null
                    response.code == 403 ->
                        "Key not recognized by Last.fm. Copy the API Key (not the Shared Secret)."
                    response.code == 429 -> "Last.fm rate limit — wait a minute and try again."
                    else -> "Last.fm returned HTTP ${response.code}"
                }
            }
        } catch (e: java.net.UnknownHostException) {
            "Cannot reach Last.fm. Check your internet connection."
        } catch (e: Exception) {
            "Connection failed: ${e.message}"
        }
    }

    private fun parseSimilarArtists(json: String): List<String> {
        val root = JsonParser.parseString(json).asJsonObject
        val artistArray = root.getAsJsonObject("similarartists")?.getAsJsonArray("artist")
            ?: return emptyList()
        return artistArray.mapNotNull { element ->
            element.asJsonObject.get("name")?.asString?.trim()?.takeIf { it.isNotBlank() }
        }
    }

    private fun parseTopTags(json: String): List<Pair<String, Int>> {
        val root = JsonParser.parseString(json).asJsonObject
        val tagArray = root.getAsJsonObject("toptags")?.getAsJsonArray("tag") ?: return emptyList()
        return tagArray.mapNotNull { element ->
            val obj = element.asJsonObject
            val name = obj.get("name")?.asString?.trim().orEmpty()
            val count = obj.get("count")?.asInt ?: 0
            if (name.isBlank()) null else name to count
        }
    }
}
