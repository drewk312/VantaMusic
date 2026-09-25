package com.audiophile.musicplayer.data.lyrics

import android.util.Log
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
        var fallbackPlainLyrics: LyricsData? = null

        for (url in buildExactUrls(track, isrc)) {
            val response = fetchSingle(url) ?: continue
            if (!durationAcceptable(response, track)) {
                Log.d("VANTA_LYRICS_TRUTH", "lrclib exact rejected duration url='$url' resultDuration=${response.duration} expectedMs=${track.durationMs}")
                continue
            }
            if (!artistAcceptable(response, track)) {
                Log.d("VANTA_LYRICS_TRUTH", "lrclib exact rejected artist url='$url' resultArtist='${response.artistName}' expected='${track.artist}'")
                continue
            }
            if (!titleAcceptable(response, track)) {
                Log.d("VANTA_LYRICS_TRUTH", "lrclib exact rejected title url='$url' resultTitle='${response.trackName}' expected='${track.title}'")
                continue
            }
            val lyrics = response.toLyricsData(track)
            if (lyrics != null) {
                if (lyrics.isSynced) {
                    return@withContext lyrics
                } else if (fallbackPlainLyrics == null) {
                    fallbackPlainLyrics = lyrics
                }
            }
        }

        for (url in buildSearchUrls(track)) {
            val scored = fetchSearch(url)
                .filter { durationAcceptable(it, track) }
                .filter { artistAcceptable(it, track) }
                .filter { titleAcceptable(it, track) }
                .map { it to scoreResult(it, track) }
                .sortedByDescending { it.second }
            for ((response, score) in scored) {
                Log.d("VANTA_LYRICS_TRUTH", "lrclib search url='$url' title='${response.trackName}' artist='${response.artistName}' score=$score synced=${!response.syncedLyrics.isNullOrBlank()}")
                val lyrics = response.toLyricsData(track)
                if (lyrics != null) {
                    if (lyrics.isSynced) {
                        return@withContext lyrics
                    } else if (fallbackPlainLyrics == null) {
                        fallbackPlainLyrics = lyrics
                    }
                }
            }
        }

        return@withContext fallbackPlainLyrics
    }

    private fun durationAcceptable(response: LRCLibResponse, track: UnifiedTrack): Boolean {
        if (response.duration == null || track.durationMs == null || track.durationMs <= 0L) return true
        val diffSec = kotlin.math.abs(response.duration - track.durationMs / 1000.0)
        return diffSec <= 18.0
    }

    private fun artistAcceptable(response: LRCLibResponse, track: UnifiedTrack): Boolean {
        val targetArtist = normalizeForMatch(track.artist)
        val resultArtist = normalizeForMatch(response.artistName.orEmpty())
        if (targetArtist.isBlank() || resultArtist.isBlank()) return true
        val targetTokens = targetArtist.split(" ").filter { it.length > 1 }
        val resultTokens = resultArtist.split(" ").filter { it.length > 1 }
        if (targetTokens.isEmpty()) return true
        val resultContainsTarget = resultArtist.contains(targetArtist)
        val targetContainsResult = targetArtist.contains(resultArtist)
        val overlapRatio = if (resultTokens.isNotEmpty()) {
            val both = targetTokens.intersect(resultTokens.toSet()).size
            both.toFloat() / maxOf(targetTokens.size, resultTokens.size)
        } else 0f
        // Strong: result contains target artist exactly (handles featured artists)
        // Medium: >= 70% token overlap with at least one shared meaningful token
        return resultContainsTarget || targetContainsResult ||
            (overlapRatio >= 0.7f && targetTokens.any { it in resultArtist })
    }

    private fun titleAcceptable(response: LRCLibResponse, track: UnifiedTrack): Boolean {
        val targetTitle = normalizeForMatch(track.title)
        val resultTitle = normalizeForMatch(response.trackName.orEmpty())
        if (targetTitle.isBlank() || resultTitle.isBlank()) return true
        val targetTokens = targetTitle.split(" ").filter { it.length > 1 }
        val resultTokens = resultTitle.split(" ").filter { it.length > 1 }
        if (targetTokens.isEmpty()) return true
        val resultContainsTarget = resultTitle.contains(targetTitle)
        val targetContainsResult = targetTitle.contains(resultTitle)
        val overlapRatio = if (resultTokens.isNotEmpty()) {
            val both = targetTokens.intersect(resultTokens.toSet()).size
            both.toFloat() / maxOf(targetTokens.size, resultTokens.size)
        } else 0f

        // Strict prefix check: if the result is a shorter version of the target, it is only
        // acceptable when the missing words are common suffixes (live, acoustic, remix, etc.)
        // and not core distinguishing words like "five/5".
        if (targetContainsResult && targetTokens.size > resultTokens.size) {
            val missingWords = targetTokens.filter { it !in resultTokens }
            val meaningfulMissing = missingWords.filter { !isCommonTitleSuffix(it) }
            if (meaningfulMissing.isNotEmpty()) {
                Log.d("VANTA_LYRICS_TRUTH", "lrclib rejected title prefix: target='$targetTitle' result='$resultTitle' missing=${meaningfulMissing}")
                return false
            }
        }

        return resultContainsTarget || targetContainsResult ||
            (targetTokens.any { it in resultTitle } && overlapRatio >= 0.75f)
    }

    private fun isCommonTitleSuffix(word: String): Boolean {
        val suffixes = setOf(
            "live", "acoustic", "remix", "edit", "version", "radio", "extended",
            "mix", "cover", "instrumental", "karaoke", "studio", "original",
            "feat", "ft", "featuring"
        )
        return word in suffixes
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
        } catch (e: java.io.IOException) {
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
            return LyricsData(
                trackKey = track.trackId.toString(),
                isSynced = false,
                lines = timedLines,
                providerId = providerId,
                sourceLabel = "LRCLib [Static]"
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
        else score -= 60
        val targetArtistTokens = targetArtist.split(" ").filter { it.length > 2 }
        val matchedTokens = targetArtistTokens.count { it in resultArtist }
        score += matchedTokens * 12
        val missedTokens = targetArtistTokens.size - matchedTokens
        if (missedTokens > 0) score -= missedTokens * 25
        if (resultArtist.isNotBlank() && targetArtist.isNotBlank()) {
            val resultTokens = resultArtist.split(" ").filter { it.length > 2 }
            val targetContainedInResult = targetArtistTokens.any { resultArtist.contains(it) }
            val resultContainedInTarget = resultTokens.any { targetArtist.contains(it) }
            if (!targetContainedInResult && !resultContainedInTarget && targetArtistTokens.isNotEmpty()) score -= 50
        }
        val durationMs = response.duration?.times(1000)?.toLong()
        if (durationMs != null && track.durationMs != null) {
            val diff = kotlin.math.abs(durationMs - track.durationMs)
            if (diff < 2_000L) score += 25 else if (diff < 8_000L) score += 10
            else if (diff > 30_000L) score -= 20
        }
        if (!response.syncedLyrics.isNullOrBlank()) score += 50
        if (!response.plainLyrics.isNullOrBlank()) score += 3
        if (response.instrumental == true) score -= 40
        return score
    }

    private fun cleanQueryText(value: String?): String =
        value.orEmpty()
            .replace(Regex("""\s*[\[(].*?(official|audio|video|lyrics?|remaster(?:ed)?|radio edit).*?[\])]""", RegexOption.IGNORE_CASE), "")
            // Feat clauses skew LRCLib search toward wrong or generic matches.
            .replace(Regex("""\s*[\[(]\s*(?:feat\.?|featuring|ft\.?|with)\s+[^\])]*[\])]""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""\s+\b(?:feat\.?|featuring|ft\.?)\s+.*$""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""\s+"""), " ")
            .trim()

    private fun cleanArtistName(value: String): String =
        value.replace(Regex("""\s*(feat\.?|ft\.?|featuring)\s+.*$""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""\s+"""), " ")
            .trim()

    private val numberWords = mapOf(
        "one" to "1", "two" to "2", "three" to "3", "four" to "4", "five" to "5",
        "six" to "6", "seven" to "7", "eight" to "8", "nine" to "9", "ten" to "10"
    )

    private fun normalizeForMatch(value: String): String =
        cleanQueryText(value)
            .lowercase()
            .replace(Regex("""[^\p{L}\p{N}\s]"""), " ")
            .replace(Regex("""\s+"""), " ")
            .trim()
            // "Victory Lap Five" and "Victory Lap 5" must compare equal.
            .split(" ")
            .joinToString(" ") { numberWords[it] ?: it }

    suspend fun searchByLyrics(query: String, limit: Int = 8): List<com.audiophile.musicplayer.data.canonical.CanonicalTrack> = withContext(Dispatchers.IO) {
        val q = query.trim()
        if (q.length < 4) return@withContext emptyList()
        val url = "https://lrclib.net/api/search?q=${encode(q)}"
        val results = fetchSearch(url)
        val queryTokens = q.lowercase().split(" ").filter { it.length > 2 }
        buildList {
            for (res in results.take(limit * 2)) {
                val trackName = (res.trackName ?: res.name)?.trim() ?: continue
                val artistName = res.artistName?.trim() ?: continue
                val rawLyrics = res.plainLyrics ?: res.syncedLyrics ?: continue
                val cleanLines = rawLyrics.lines()
                    .map { it.replace(Regex("""^\[\d{2}:\d{2}\.\d{2}\]"""), "").trim() }
                    .filter { it.isNotBlank() && !it.startsWith("[") }

                val matchingLine = cleanLines.firstOrNull { line ->
                    val lower = line.lowercase()
                    lower.contains(q.lowercase()) || (queryTokens.isNotEmpty() && queryTokens.all { lower.contains(it) })
                } ?: continue

                add(
                    com.audiophile.musicplayer.data.canonical.CanonicalTrack(
                        title = trackName,
                        artist = artistName,
                        album = res.albumName,
                        durationMs = res.duration?.let { (it * 1000).toLong() },
                        sourceStatus = com.audiophile.musicplayer.data.source.SearchItemStatus.SOURCE_FOUND,
                        sourceProviderId = "lrclib",
                        matchedLyricSnippet = matchingLine
                    )
                )
                if (size >= limit) break
            }
        }
    }

    private fun encode(value: String): String =
        java.net.URLEncoder.encode(value, "UTF-8")

    private data class LRCLibResponse(
        @SerializedName("id") val id: Long? = null,
        @SerializedName("name") val name: String? = null,
        @SerializedName("trackName") val trackName: String? = null,
        @SerializedName("artistName") val artistName: String? = null,
        @SerializedName("albumName") val albumName: String? = null,
        @SerializedName("duration") val duration: Double? = null,
        @SerializedName("instrumental") val instrumental: Boolean? = null,
        @SerializedName("plainLyrics") val plainLyrics: String? = null,
        @SerializedName("syncedLyrics") val syncedLyrics: String? = null
    )
}
