package com.audiophile.musicplayer.data.lyrics

import com.audiophile.musicplayer.data.local.entities.UnifiedTrack
import com.google.gson.Gson
import com.google.gson.JsonParser
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

class LRCLibLyricsProvider : LyricsProvider {
    override val providerId: String = "lrclib"

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    private val gson = Gson()

    override suspend fun getLyrics(track: UnifiedTrack, isrc: String?): LyricsData? = withContext(Dispatchers.IO) {
        for (url in buildExactUrls(track, isrc)) {
            val lyrics = fetchSingle(url)?.toLyricsData(track)
            if (lyrics != null) return@withContext lyrics
        }

        for (url in buildSearchUrls(track)) {
            val lyrics = fetchSearch(url)
                .sortedByDescending { scoreResult(it, track) }
                .firstNotNullOfOrNull { it.toLyricsData(track) }
            if (lyrics != null) return@withContext lyrics
        }

        return@withContext null
    }

    private fun buildExactUrls(track: UnifiedTrack, isrc: String?): List<String> {
        val base = "https://lrclib.net/api"
        val urls = mutableListOf<String>()
        if (!isrc.isNullOrBlank()) {
            urls += "$base/get?isrc=${encode(isrc.trim().uppercase())}"
        }
        val title = encode(cleanQueryText(track.title))
        val artist = encode(cleanArtistName(track.artist))
        urls += "$base/get?artist_name=$artist&track_name=$title"
        if (!track.albumName.isNullOrBlank()) {
            urls += "$base/get?artist_name=$artist&track_name=$title&album_name=${encode(cleanQueryText(track.albumName))}"
        }
        if (track.durationMs != null && track.durationMs > 0L) {
            urls += "$base/get?artist_name=$artist&track_name=$title&duration=${track.durationMs / 1000L}"
        }
        return urls.distinct()
    }

    private fun buildSearchUrls(track: UnifiedTrack): List<String> {
        val base = "https://lrclib.net/api/search"
        val title = cleanQueryText(track.title)
        val artist = cleanArtistName(track.artist)
        val urls = mutableListOf(
            "$base?track_name=${encode(title)}&artist_name=${encode(artist)}",
            "$base?q=${encode("$title $artist")}"
        )
        val primaryArtist = artist.substringBefore("&").substringBefore(",").trim()
        if (primaryArtist.isNotBlank() && primaryArtist != artist) {
            urls += "$base?track_name=${encode(title)}&artist_name=${encode(primaryArtist)}"
            urls += "$base?q=${encode("$title $primaryArtist")}"
        }
        return urls.distinct()
    }

    private fun fetchSingle(url: String): LRCLibResponse? {
        val body = fetchBody(url) ?: return null
        return runCatching { gson.fromJson(body, LRCLibResponse::class.java) }.getOrNull()
    }

    private fun fetchSearch(url: String): List<LRCLibResponse> {
        val body = fetchBody(url) ?: return emptyList()
        return runCatching {
            JsonParser.parseString(body).asJsonArray.mapNotNull { element ->
                runCatching { gson.fromJson(element, LRCLibResponse::class.java) }.getOrNull()
            }
        }.getOrDefault(emptyList())
    }

    private fun fetchBody(url: String): String? {
        val request = Request.Builder().url(url).header("User-Agent", "VANTA/1.0").build()
        return try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                response.body?.string()?.takeIf { it.isNotBlank() }
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun LRCLibResponse.toLyricsData(track: UnifiedTrack): LyricsData? {
        val syncedLines = LrcParser.parseSyncedLines(syncedLyrics)
        if (!syncedLines.isNullOrEmpty()) {
            return LyricsData(
                trackKey = track.trackId.toString(),
                isSynced = true,
                lines = syncedLines,
                providerId = providerId,
                sourceLabel = "LRCLib"
            )
        }

        val plainLines = LrcParser.parsePlainLines(plainLyrics)
        if (plainLines.isNotEmpty()) {
            val timedLines = track.durationMs?.takeIf { it > 0L }?.let { durationMs ->
                LrcParser.estimatePlainLyricTimings(plainLines, durationMs)
            } ?: plainLines
            val hasTimings = timedLines.any { it.startTimeMs != null }
            return LyricsData(
                trackKey = track.trackId.toString(),
                isSynced = hasTimings,
                lines = timedLines,
                providerId = providerId,
                sourceLabel = if (hasTimings) "LRCLib (estimated sync)" else "LRCLib"
            )
        }

        if (instrumental == true) return null
        return null
    }

    private fun scoreResult(response: LRCLibResponse, track: UnifiedTrack): Int {
        val targetTitle = normalizeForMatch(track.title)
        val targetArtist = normalizeForMatch(track.artist)
        val resultTitle = normalizeForMatch(response.trackName ?: response.name.orEmpty())
        val resultArtist = normalizeForMatch(response.artistName.orEmpty())
        var score = 0
        if (resultTitle == targetTitle) score += 80
        else if (resultTitle.contains(targetTitle) || targetTitle.contains(resultTitle)) score += 45
        val artistTokens = targetArtist.split(" ").filter { it.length > 2 }
        score += artistTokens.count { it in resultArtist } * 8
        val durationMs = response.duration?.times(1000)?.toLong()
        if (durationMs != null && track.durationMs != null) {
            val diff = kotlin.math.abs(durationMs - track.durationMs)
            if (diff < 2_000L) score += 25 else if (diff < 8_000L) score += 10
        }
        if (!response.syncedLyrics.isNullOrBlank()) score += 6
        if (!response.plainLyrics.isNullOrBlank()) score += 3
        if (response.instrumental == true) score -= 40
        return score
    }

    private fun cleanQueryText(value: String?): String =
        value.orEmpty()
            .replace(Regex("""\s*[\[(].*?(official|audio|video|lyrics?|remaster(?:ed)?|radio edit).*?[\])]""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""\s+"""), " ")
            .trim()

    private fun cleanArtistName(value: String): String =
        value.replace(Regex("""\s*(feat\.?|ft\.?|featuring)\s+.*$""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""\s+"""), " ")
            .trim()

    private fun normalizeForMatch(value: String): String =
        cleanQueryText(value)
            .lowercase()
            .replace(Regex("""[^\p{L}\p{N}\s]"""), " ")
            .replace(Regex("""\s+"""), " ")
            .trim()

    private fun encode(value: String): String =
        java.net.URLEncoder.encode(value, "UTF-8")

    private data class LRCLibResponse(
        val id: Long? = null,
        val name: String? = null,
        @SerializedName("trackName") val trackName: String? = null,
        @SerializedName("artistName") val artistName: String? = null,
        @SerializedName("albumName") val albumName: String? = null,
        val duration: Double? = null,
        @SerializedName("instrumental") val instrumental: Boolean? = null,
        @SerializedName("plainLyrics") val plainLyrics: String? = null,
        @SerializedName("syncedLyrics") val syncedLyrics: String? = null
    )
}
