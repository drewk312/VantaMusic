package com.audiophile.musicplayer.radio

import android.util.Log
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

data class LiveRadioStation(
    val id: String,
    val name: String,
    val streamUrl: String,
    val artworkUrl: String?,
    val tags: String,
    val codec: String,
    val bitrateKbps: Int
)

class LiveRadioDirectory {
    private val client = OkHttpClient.Builder()
        .connectTimeout(7, TimeUnit.SECONDS)
        .readTimeout(9, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    private val mirrors = listOf(
        "https://de1.api.radio-browser.info",
        "https://de2.api.radio-browser.info"
    )

    private val adFreeBoostTags = listOf(
        "no ads",
        "non-commercial",
        "commercial-free",
        "commercial free",
        "non-stop",
        "nonstop"
    )
    private val adPenaltyTags = listOf(
        "advertisement",
        "commercial break",
        "with ads",
        "ads"
    )

    suspend fun findPlayable(query: String): LiveRadioStation? =
        findNextPlayable(query, emptySet())

    suspend fun findNextPlayable(query: String, excludeStreamUrls: Set<String>): LiveRadioStation? =
        withContext(Dispatchers.IO) {
            val candidates = collectCandidates(query)
            for (station in candidates) {
                if (station.streamUrl in excludeStreamUrls) continue
                if (validateStream(station.streamUrl)) return@withContext station
            }
            null
        }

    private fun collectCandidates(query: String): List<LiveRadioStation> {
        val merged = mutableListOf<LiveRadioStation>()
        for (mirror in mirrors) {
            val batch = runCatching { searchMirror(mirror, query) }.getOrElse {
                Log.w("VANTA_LIVE_RADIO", "directory mirror failed host=$mirror reason=${it.message}")
                emptyList()
            }
            merged += batch
        }
        return merged
            .distinctBy { it.streamUrl }
            .sortedWith(
                compareByDescending<LiveRadioStation> { scoreAdPreference(it) }
                    .thenByDescending { it.name.contains(query, ignoreCase = true) }
                    .thenByDescending { it.bitrateKbps }
            )
    }

    private fun scoreAdPreference(station: LiveRadioStation): Int {
        val tags = station.tags.lowercase()
        val name = station.name.lowercase()
        var score = 0
        for (tag in adFreeBoostTags) {
            if (tag in tags || tag in name) score += 3
        }
        for (tag in adPenaltyTags) {
            if (tag in tags || tag in name) score -= 4
        }
        return score
    }

    private fun searchMirror(mirror: String, query: String): List<LiveRadioStation> {
        val url = "$mirror/json/stations/search".toHttpUrl().newBuilder()
            .addQueryParameter("name", query)
            .addQueryParameter("hidebroken", "true")
            .addQueryParameter("order", "clickcount")
            .addQueryParameter("reverse", "true")
            .addQueryParameter("limit", "30")
            .build()
        val request = Request.Builder().url(url).header("User-Agent", "VANTA/1.0").build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return emptyList()
            val body = response.body?.string().orEmpty()
            if (body.isBlank()) return emptyList()
            val parsed = runCatching { JsonParser.parseString(body) }.getOrNull() ?: return emptyList()
            if (!parsed.isJsonArray) return emptyList()
            val array = parsed.asJsonArray
            return array.mapNotNull { element ->
                val item = element.asJsonObject
                val stream = item.get("url_resolved")?.asString
                    ?.takeIf { it.startsWith("https://") || it.startsWith("http://") }
                    ?: return@mapNotNull null
                val lastCheckOk = item.get("lastcheckok")?.asInt ?: 0
                if (lastCheckOk != 1) return@mapNotNull null
                val codec = item.get("codec")?.asString.orEmpty()
                LiveRadioStation(
                    id = item.get("stationuuid")?.asString.orEmpty(),
                    name = item.get("name")?.asString?.trim().orEmpty().ifBlank { query },
                    streamUrl = stream,
                    artworkUrl = item.get("favicon")?.asString?.trim()?.takeIf { it.startsWith("http") },
                    tags = item.get("tags")?.asString.orEmpty(),
                    codec = codec,
                    bitrateKbps = item.get("bitrate")?.asInt ?: 0
                )
            }
        }
    }

    private fun validateStream(url: String): Boolean = runCatching {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "VANTA/1.0")
            .header("Range", "bytes=0-1024")
            .build()
        client.newCall(request).execute().use { response ->
            val type = response.header("Content-Type").orEmpty().lowercase()
            response.isSuccessful && !type.contains("text/html") && !type.contains("application/json")
        }
    }.getOrDefault(false)
}
