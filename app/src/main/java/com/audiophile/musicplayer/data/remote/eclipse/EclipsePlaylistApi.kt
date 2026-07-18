package com.audiophile.musicplayer.data.remote.eclipse

import okhttp3.OkHttpClient
import okhttp3.Request
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Client for the Eclipse Music shared-playlist API.
 *
 * Fetches a public playlist by its share URL or token and returns a plain
 * list of tracks. No auth required for public shares.
 */
class EclipsePlaylistApi(private val okHttpClient: OkHttpClient) {

    private val gson = Gson()

    suspend fun fetchPlaylist(shareUrl: String): Result<EclipsePlaylistResponse> = withContext(Dispatchers.IO) {
        runCatching {
            val normalized = normalizeUrl(shareUrl)
            val request = Request.Builder()
                .url(normalized)
                .header("Accept", "application/json")
                .header("User-Agent", "VANTA/1.0 Android")
                .get()
                .build()
            val response = okHttpClient.newCall(request).execute()
            val body = response.body?.string()
                ?: throw IllegalStateException("Empty response from Eclipse API")
            if (!response.isSuccessful) {
                throw IllegalStateException("API returned ${response.code}: $body")
            }
            val wrapper = gson.fromJson(body, EclipseApiResponse::class.java)
            if (!wrapper.success) {
                throw IllegalStateException("API returned success=false")
            }
            wrapper.data ?: throw IllegalStateException("API returned null data")
        }
    }

    private fun normalizeUrl(input: String): String {
        val trimmed = input.trim()
        return when {
            trimmed.startsWith("http://", ignoreCase = true) || trimmed.startsWith("https://", ignoreCase = true) -> trimmed
            trimmed.contains("/api/share/playlist/", ignoreCase = true) -> "https://$trimmed"
            else -> "https://api.eclipsemusic.app/api/share/playlist/$trimmed"
        }
    }
}
