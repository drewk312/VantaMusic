package com.audiophile.musicplayer.data.source

import android.util.Log
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.URLDecoder
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

class YouTubeMusicSourceProvider : MusicSourceProvider {
    override val providerId: String = "youtube_music"
    override val providerName: String = "YouTube Music"

    private val client = OkHttpClient.Builder()
        .followRedirects(false)
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private val userAgent =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"

    private data class InnerTubeClient(
        val label: String,
        val host: String,
        val apiKey: String,
        val clientName: String,
        val clientVersion: String,
        val clientNameId: Int,
        val userAgent: String,
        val extraClientFields: Map<String, String> = emptyMap()
    )

    private val innerTubeClients = listOf(
        InnerTubeClient(
            label = "IOS",
            host = "https://www.youtube.com",
            apiKey = "AIzaSyB-63vPrdThhKuerbB2Wj8dzmuvS7K7YRA",
            clientName = "IOS",
            clientVersion = "21.02.3",
            clientNameId = 5,
            userAgent = "com.google.ios.youtube/21.02.3 (iPhone16,2; U; CPU iOS 18_3_2 like Mac OS X;)",
            extraClientFields = mapOf(
                "deviceMake" to "Apple",
                "deviceModel" to "iPhone16,2",
                "osName" to "iPhone",
                "osVersion" to "18.3.2.22D82"
            )
        ),
        InnerTubeClient(
            label = "ANDROID",
            host = "https://www.youtube.com",
            apiKey = "AIzaSyA8eiZmM1FaDVZBJV41YeqZtphdrcjnAqQ",
            clientName = "ANDROID",
            clientVersion = "21.02.35",
            clientNameId = 3,
            userAgent = "com.google.android.youtube/21.02.35 (Linux; U; Android 11) gzip",
            extraClientFields = mapOf(
                "androidSdkVersion" to "30",
                "osName" to "Android",
                "osVersion" to "11"
            )
        ),
        InnerTubeClient(
            label = "ANDROID_VR",
            host = "https://www.youtube.com",
            apiKey = "AIzaSyA8eiZmM1FaDVZBJV41YeqZtphdrcjnAqQ",
            clientName = "ANDROID_VR",
            clientVersion = "1.65.10",
            clientNameId = 28,
            userAgent = "com.google.android.apps.youtube.vr.oculus/1.65.10 (Linux; U; Android 12L) gzip",
            extraClientFields = mapOf(
                "deviceMake" to "Oculus",
                "deviceModel" to "Quest 3",
                "androidSdkVersion" to "32",
                "osName" to "Android",
                "osVersion" to "12L"
            )
        ),
        InnerTubeClient(
            label = "TVHTML5",
            host = "https://www.youtube.com",
            apiKey = "AIzaSyAO_FJ2SlqU8Q4STEHLGCilw_Y9_11qcW8",
            clientName = "TVHTML5",
            clientVersion = "7.20260114.12.00",
            clientNameId = 7,
            userAgent = "Mozilla/5.0 (ChromiumStylePlatform) Cobalt/25.lts.30.1034943-gold (unlike Gecko), Unknown_TV_Unknown_0/Unknown (Unknown, Unknown)"
        ),
        InnerTubeClient(
            label = "WEB_EMBEDDED",
            host = "https://www.youtube.com",
            apiKey = "AIzaSyAO_FJ2SlqU8Q4STEHLGCilw_Y9_11qcW8",
            clientName = "WEB_EMBEDDED_PLAYER",
            clientVersion = "1.20260115.01.00",
            clientNameId = 56,
            userAgent = userAgent
        ),
        InnerTubeClient(
            label = "WEB_REMIX",
            host = "https://music.youtube.com",
            apiKey = "AIzaSyC9XL3ZjWHiXJCL0uaZ6Fw--OoGntj8bdg",
            clientName = "WEB_REMIX",
            clientVersion = "1.20260114.03.00",
            clientNameId = 67,
            userAgent = userAgent
        )
    )

    override suspend fun search(query: String): List<SourceSearchResult> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext emptyList()
        try {
            val encoded = URLEncoder.encode(query, "UTF-8")
            val url = "https://www.youtube.com/results?search_query=$encoded"
            val request = Request.Builder().url(url).header("User-Agent", userAgent).build()
            val body = client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext emptyList()
                response.body?.string() ?: return@withContext emptyList()
            }
            val dataJson = extractJsonVar(body, "ytInitialData") ?: return@withContext emptyList()
            val parsed = JsonParser.parseString(dataJson)?.asJsonObject ?: return@withContext emptyList()
            val results = parseSearchResults(parsed)

            Log.d("YTMusic", "search query='$query' found=${results.size}")
            results
        } catch (e: Exception) {
            Log.e("YTMusic", "Search exception", e)
            emptyList()
        }
    }

    override suspend fun resolveStream(trackId: String): ResolvedStream? = withContext(Dispatchers.IO) {
        try {
            Log.d("YTMusic", "resolveStream: page+InnerTube for $trackId")
            val watchUrl = "https://www.youtube.com/watch?v=$trackId"
            val request = Request.Builder()
                .url(watchUrl)
                .header("User-Agent", userAgent)
                .header("Accept-Language", "en-US,en;q=0.9")
                .build()
            val body = client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.w("YTMusic", "resolveStream: page HTTP ${response.code} for $trackId")
                    return@withContext null
                }
                response.body?.string()
            } ?: return@withContext null

            Log.d("YTMusic", "resolveStream: page body=${body.length} for $trackId")

            extractJsonVar(body, "ytInitialPlayerResponse")?.let { playerJson ->
                JsonParser.parseString(playerJson)?.asJsonObject?.let { parsed ->
                    parsePlayerResponse(parsed, trackId, "page")?.let { return@withContext it }
                }
            }

            val scrapedApiKey = extractYtcfgValue(body, "INNERTUBE_API_KEY")
            val scrapedClientVersion = extractYtcfgValue(body, "INNERTUBE_CLIENT_VERSION")
            val visitorData = extractYtcfgValue(body, "VISITOR_DATA")
            Log.d(
                "YTMusic",
                "resolveStream: extracted apiKey=${scrapedApiKey ?: "(fallback)"} " +
                    "cv=${scrapedClientVersion ?: "(fallback)"} vd=${visitorData?.take(12).orEmpty()}"
            )

            for (tubeClient in innerTubeClients) {
                val apiKey = scrapedApiKey ?: tubeClient.apiKey
                val resolved = requestInnerTubePlayer(
                    trackId = trackId,
                    tubeClient = tubeClient,
                    apiKey = apiKey,
                    visitorData = visitorData
                )
                if (resolved != null) return@withContext resolved
            }

            if (scrapedApiKey != null && scrapedClientVersion != null) {
                val webClient = InnerTubeClient(
                    label = "WEB",
                    host = "https://www.youtube.com",
                    apiKey = scrapedApiKey,
                    clientName = "WEB",
                    clientVersion = scrapedClientVersion,
                    clientNameId = 1,
                    userAgent = userAgent
                )
                requestInnerTubePlayer(trackId, webClient, scrapedApiKey, visitorData)
                    ?.let { return@withContext it }
            }

            Log.w("YTMusic", "resolveStream: all strategies failed for $trackId")
            null
        } catch (e: Exception) {
            Log.e("YTMusic", "Resolve exception", e)
            null
        }
    }

    private fun requestInnerTubePlayer(
        trackId: String,
        tubeClient: InnerTubeClient,
        apiKey: String,
        visitorData: String?
    ): ResolvedStream? {
        val clientObj = JsonObject().apply {
            addProperty("clientName", tubeClient.clientName)
            addProperty("clientVersion", tubeClient.clientVersion)
            addProperty("hl", "en")
            addProperty("gl", "US")
            tubeClient.extraClientFields.forEach { (key, value) -> addProperty(key, value) }
        }
        val context = JsonObject().apply {
            add("client", clientObj)
        }
        val payload = JsonObject().apply {
            addProperty("videoId", trackId)
            addProperty("contentCheckOk", true)
            addProperty("racyCheckOk", true)
            add("context", context)
        }

        val endpoint = "${tubeClient.host}/youtubei/v1/player?key=$apiKey&prettyPrint=false"
        val requestBuilder = Request.Builder()
            .url(endpoint)
            .post(payload.toString().toRequestBody("application/json".toMediaType()))
            .header("Content-Type", "application/json")
            .header("User-Agent", tubeClient.userAgent)
            .header("X-Youtube-Client-Name", tubeClient.clientNameId.toString())
            .header("X-Youtube-Client-Version", tubeClient.clientVersion)
        if (!visitorData.isNullOrBlank()) {
            requestBuilder.header("X-Goog-Visitor-Id", visitorData)
        }

        return try {
            client.newCall(requestBuilder.build()).execute().use { response ->
                val responseBody = response.body?.string()
                if (!response.isSuccessful || responseBody.isNullOrBlank()) {
                    Log.d(
                        "YTMusic",
                        "resolveStream: InnerTube ${tubeClient.label} HTTP ${response.code} for $trackId"
                    )
                    return null
                }
                val parsed = JsonParser.parseString(responseBody)?.asJsonObject ?: return null
                val status = parsed.getAsJsonObject("playabilityStatus")
                    ?.get("status")
                    ?.asString
                if (status != "OK") {
                    val reason = parsed.getAsJsonObject("playabilityStatus")
                        ?.get("reason")
                        ?.asString
                        ?: status
                    Log.d(
                        "YTMusic",
                        "resolveStream: InnerTube ${tubeClient.label} status=$status reason=$reason for $trackId"
                    )
                    return null
                }
                parsePlayerResponse(parsed, trackId, tubeClient.label)
            }
        } catch (e: Exception) {
            Log.d("YTMusic", "resolveStream: InnerTube ${tubeClient.label} error for $trackId", e)
            null
        }
    }

    private fun parsePlayerResponse(
        parsed: JsonObject,
        trackId: String,
        source: String
    ): ResolvedStream? {
        val playability = parsed.getAsJsonObject("playabilityStatus")
        val status = playability?.get("status")?.asString
        if (status != null && status != "OK") {
            val reason = playability.get("reason")?.asString ?: status
            Log.d("YTMusic", "resolveStream: $source status=$status reason=$reason for $trackId")
            return null
        }

        val streamingData = parsed.getAsJsonObject("streamingData") ?: return null
        val formats = streamingData.getAsJsonArray("adaptiveFormats")
            ?: streamingData.getAsJsonArray("formats")
            ?: return null

        var bestUrl: String? = null
        var bestBitrate = 0
        var bestMime = "audio/mp4"
        var bestScore = -1

        for (i in 0 until formats.size()) {
            val format = formats[i].asJsonObject
            val mimeType = format.get("mimeType")?.asString ?: continue
            if (!mimeType.startsWith("audio/")) continue

            val audioUrl = format.get("url")?.asString
                ?: extractDirectUrlFromCipher(format.get("signatureCipher")?.asString)
                ?: extractDirectUrlFromCipher(format.get("cipher")?.asString)
                ?: continue

            val bitrate = format.get("bitrate")?.asInt ?: 0
            val score = scoreAudioFormat(format)

            if (bestUrl == null || score > bestScore || (score == bestScore && bitrate > bestBitrate)) {
                bestUrl = audioUrl
                bestBitrate = bitrate
                bestMime = mimeType.substringBefore(";")
                bestScore = score
            }
        }

        val streamUrl = bestUrl ?: return null
        Log.d("YTMusic", "resolveStream: $source OK bitrate=${bestBitrate / 1000}kbps for $trackId")

        return ResolvedStream(
            streamUrl = streamUrl,
            bitrateKbps = bestBitrate / 1000,
            mimeType = bestMime,
            qualityLabel = "Audio Stream"
        )
    }

    private fun scoreAudioFormat(format: JsonObject): Int {
        val audioTrack = format.getAsJsonObject("audioTrack") ?: return 100
        if (audioTrack.get("audioIsDefault")?.asBoolean == true) return 100
        val displayName = audioTrack.get("displayName")?.asString?.lowercase() ?: return 50
        if (displayName.contains("original") || displayName.contains("default")) return 90
        if (displayName.contains("instrumental") || displayName.contains("karaoke")) return 0
        return 50
    }

    private fun extractDirectUrlFromCipher(cipher: String?): String? {
        if (cipher.isNullOrBlank()) return null
        return runCatching {
            val params = cipher.split("&").mapNotNull { seg ->
                val eq = seg.indexOf('=')
                if (eq < 0) null else seg.substring(0, eq) to URLDecoder.decode(seg.substring(eq + 1), "UTF-8")
            }.toMap()
            val url = params["url"] ?: return null
            val sig = params["s"] ?: return url
            val sp = params["sp"] ?: "sig"
            "$url&$sp=${URLEncoder.encode(sig, "UTF-8")}"
        }.getOrNull()
    }

    private fun extractYtcfgValue(html: String, key: String): String? {
        val patterns = listOf(
            Pattern.compile("\"${Pattern.quote(key)}\"\\s*:\\s*\"([^\"]+)\""),
            Pattern.compile("'${Pattern.quote(key)}'\\s*:\\s*'([^']+)'")
        )
        for (pattern in patterns) {
            val matcher = pattern.matcher(html)
            if (matcher.find()) return matcher.group(1)
        }
        return null
    }

    private fun parseSearchResults(root: JsonObject): List<SourceSearchResult> {
        val results = mutableListOf<SourceSearchResult>()

        try {
            val contents = root
                .getAsJsonObject("contents")
                ?.getAsJsonObject("twoColumnSearchResultsRenderer")
                ?.getAsJsonObject("primaryContents")
                ?.getAsJsonObject("sectionListRenderer")
                ?.getAsJsonArray("contents")
                ?: return results

            for (i in 0 until contents.size()) {
                val itemSection = contents[i].asJsonObject?.getAsJsonObject("itemSectionRenderer")
                    ?.getAsJsonArray("contents") ?: continue

                for (j in 0 until itemSection.size()) {
                    val videoRenderer = itemSection[j].asJsonObject?.getAsJsonObject("videoRenderer") ?: continue
                    val result = parseVideoRenderer(videoRenderer) ?: continue
                    results.add(result)
                }
            }
        } catch (e: Exception) {
            Log.e("YTMusic", "Parse error", e)
        }

        return results
    }

    private fun parseVideoRenderer(vr: JsonObject): SourceSearchResult? {
        try {
            val videoId = vr.get("videoId")?.asString ?: return null

            val titleObj = vr.getAsJsonObject("title")
            val title = titleObj
                ?.getAsJsonArray("runs")
                ?.get(0)
                ?.asJsonObject
                ?.get("text")
                ?.asString
                ?: return null

            val longByline = vr.getAsJsonObject("longBylineText")
            val artist = longByline
                ?.getAsJsonArray("runs")
                ?.get(0)
                ?.asJsonObject
                ?.get("text")
                ?.asString
                ?: "Unknown Artist"

            val lengthText = vr.getAsJsonObject("lengthText")?.get("simpleText")?.asString
            val durationMs = parseDurationToMs(lengthText)

            val thumbnail = vr
                .getAsJsonObject("thumbnail")
                ?.getAsJsonArray("thumbnails")
                ?.last()
                ?.asJsonObject
                ?.get("url")
                ?.asString

            return SourceSearchResult(
                id = videoId,
                providerId = providerId,
                title = title,
                artist = artist,
                album = null,
                coverSeed = thumbnail ?: title,
                durationMs = durationMs,
                isrc = null,
                status = SearchItemStatus.SOURCE_FOUND,
                qualityLabel = "Audio Stream"
            )
        } catch (e: Exception) {
            return null
        }
    }

    private fun extractJsonVar(html: String, varName: String): String? {
        val escaped = Pattern.quote(varName)

        try {
            val p1 = Pattern.compile("$escaped\\s*=\\s*(\\{.*?\\})\\s*;", Pattern.DOTALL)
            val m1 = p1.matcher(html)
            if (m1.find()) return m1.group(1)
        } catch (_: Exception) {
        }

        try {
            val p2 = Pattern.compile("var\\[\\s*\"${Pattern.quote(varName)}\"\\s*\\]\\s*=\\s*(\\{.*?\\})\\s*;", Pattern.DOTALL)
            val m2 = p2.matcher(html)
            if (m2.find()) return m2.group(1)
        } catch (_: Exception) {
        }

        val searchPatterns = listOf(
            "$varName = ",
            "$varName=",
            "var[\"$varName\"] = ",
            "window[\"$varName\"] = "
        )
        for (search in searchPatterns) {
            val idx = html.indexOf(search)
            if (idx < 0) continue
            val start = idx + search.length
            val braceStart = html.indexOf('{', start)
            if (braceStart < 0 || braceStart > start + 100) continue
            var depth = 0
            var inString = false
            var escape = false
            for (i in braceStart until html.length) {
                val c = html[i]
                when {
                    escape -> escape = false
                    c == '\\' && inString -> escape = true
                    c == '"' -> inString = !inString
                    !inString && c == '{' -> depth++
                    !inString && c == '}' -> {
                        depth--
                        if (depth == 0) {
                            return html.substring(braceStart, i + 1)
                        }
                    }
                }
            }
        }
        return null
    }

    private fun parseDurationToMs(duration: String?): Long? {
        if (duration == null) return null
        try {
            val parts = duration.split(":").map { it.toLong() }
            return when (parts.size) {
                3 -> (parts[0] * 3600 + parts[1] * 60 + parts[2]) * 1000
                2 -> (parts[0] * 60 + parts[1]) * 1000
                else -> parts[0] * 1000
            }
        } catch (_: Exception) {
            return null
        }
    }
}
