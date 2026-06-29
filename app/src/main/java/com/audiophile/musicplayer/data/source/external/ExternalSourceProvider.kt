package com.audiophile.musicplayer.data.source.external

import android.util.Log
import com.audiophile.musicplayer.data.source.MusicSourceProvider
import com.audiophile.musicplayer.data.source.ResolvedStream
import com.audiophile.musicplayer.data.source.SearchItemStatus
import com.audiophile.musicplayer.data.source.ContentPurityFilter
import com.audiophile.musicplayer.data.source.VocalRecordingClassifier
import com.audiophile.musicplayer.data.source.SourceSearchResult
import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

open class ExternalSourceProvider(
    protected val config: ExternalSourceConfig
) : MusicSourceProvider {

    override val providerId: String = config.id
    override val providerName: String = config.displayName

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(22, TimeUnit.SECONDS)
        .build()

    private val streamResolveClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()
        
    private val gson = Gson()
    
    protected val catalogBaseUrl: String =
        config.searchBaseUrl?.trim()?.trimEnd('/')?.takeIf { it.isNotBlank() }
            ?: config.baseUrl.trimEnd('/')

    /** Providers sharing a catalog URL run one search instead of hammering the same gateway. */
    val searchGroupKey: String
        get() = catalogBaseUrl.trim().trimEnd('/').lowercase().ifBlank { providerId }

    protected open fun searchUrlCandidates(query: String): List<String> = searchUrls(query, catalogBaseUrl)

    protected open fun streamUrlCandidates(trackId: String): List<String> = streamUrls(trackId)

    override suspend fun search(query: String): List<SourceSearchResult> = withContext(Dispatchers.IO) {
        if (!config.enabled) return@withContext emptyList()
        try {
            val bodyString = firstSuccessfulBody(searchUrlCandidates(query), "search")
                ?: return@withContext emptyList()
            val tracks = parseSearchTracks(bodyString)

            tracks.mapNotNull { track ->
                try {
                    if (!ContentPurityFilter.isAllowed(
                            title = track.title,
                            artist = track.artist,
                            album = track.album,
                            durationMs = track.resolveDurationMs(),
                            source = providerId,
                            userQuery = query
                        )
                    ) {
                        return@mapNotNull null
                    }
                    val qualityLabel = track.quality ?: track.format ?: "Stream"
                    val playbackId = track.id

                    SourceSearchResult(
                        id = playbackId,
                        providerId = providerId,
                        title = track.title,
                        artist = track.artist,
                        album = track.album,
                        coverSeed = track.resolveArtwork() ?: track.title,
                        durationMs = track.resolveDurationMs(),
                        isrc = track.isrc,
                        status = SearchItemStatus.SOURCE_FOUND,
                        qualityLabel = qualityLabel
                    )
                } catch (e: Exception) {
                    Log.e("ExternalSourceProvider", "Failed to map track: ${track.id}", e)
                    null
                }
            }
        } catch (e: Exception) {
            Log.e("ExternalSourceProvider", "Exception during search for $providerName", e)
            emptyList()
        }
    }

    override suspend fun resolveStream(trackId: String): ResolvedStream? = withContext(Dispatchers.IO) {
        if (!config.enabled) return@withContext null
        try {
            val bodyString = firstSuccessfulBody(streamUrlCandidates(trackId), "stream")
                ?: return@withContext null
            parseStreamResult(bodyString)
        } catch (e: Exception) {
            Log.e("ExternalSourceProvider", "Exception during stream resolution for $providerName", e)
            null
        }
    }

    private fun encoded(value: String): String =
        URLEncoder.encode(value, "UTF-8").replace("+", "%20")

    protected fun searchUrls(query: String, base: String = catalogBaseUrl): List<String> {
        val q = encoded(query)
        return listOf(
            "$base/search?q=$q",
            "$base/api/search?q=$q",
            "$base/search/$q",
            "$base/api/search/$q"
        )
    }

    protected fun streamUrls(trackId: String, base: String = config.baseUrl.trimEnd('/')): List<String> {
        val id = encoded(
            trackId
                .removePrefix("tidal:")
                .removePrefix("qobuz:")
                .removePrefix("deezer:")
                .removePrefix("amazon:")
                .removePrefix("pandora:")
        )
        return listOf(
            "$base/stream/$id?quality=${SpotiFlacEndpoints.PREFERRED_STREAM_QUALITY}",
            "$base/api/stream/$id?quality=${SpotiFlacEndpoints.PREFERRED_STREAM_QUALITY}",
            "$base/stream/$id?quality=${SpotiFlacEndpoints.PREFERRED_STREAM_QUALITY}&provider=$providerId",
            "$base/stream/$id",
            "$base/api/stream/$id",
            "$base/stream?id=$id&quality=${SpotiFlacEndpoints.PREFERRED_STREAM_QUALITY}",
            "$base/stream?trackId=$id&quality=${SpotiFlacEndpoints.PREFERRED_STREAM_QUALITY}",
            "$base/resolve/$id",
            "$base/api/resolve/$id",
            "$base/download/$id",
            "$base/api/download/$id"
        )
    }

    internal fun firstSuccessfulBodyPublic(urls: List<String>, operation: String): String? =
        firstSuccessfulBody(urls, operation)

    internal fun parseStreamResultPublic(bodyString: String): ResolvedStream? =
        parseStreamResult(bodyString)

    private fun firstSuccessfulBody(urls: List<String>, operation: String): String? {
        for (url in urls) {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "VANTA/1.0")
                .build()
            try {
                val httpClient = if (operation == "search") client else streamResolveClient
                httpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        Log.d("VANTA_EXTERNAL_SOURCE",
                            "providerId=$providerId operation=$operation url='$url' status=${response.code}")
                        return@use
                    }
                    val body = response.body?.string()
                    if (!body.isNullOrBlank()) {
                        Log.d("VANTA_EXTERNAL_SOURCE",
                            "providerId=$providerId operation=$operation url='$url' status=${response.code} result=body")
                        return body
                    }
                }
            } catch (e: Exception) {
                Log.d("VANTA_EXTERNAL_SOURCE",
                    "providerId=$providerId operation=$operation url='$url' error='${e.message}'")
            }
        }
        Log.e("ExternalSourceProvider", "$operation failed for $providerName")
        return null
    }

    private fun parseSearchTracks(bodyString: String): List<ExternalTrack> {
        val root = runCatching { JsonParser.parseString(bodyString) }.getOrNull() ?: return emptyList()
        return collectTrackObjects(root).mapNotNull { parseTrackObject(it) }
    }

    private fun collectTrackObjects(element: JsonElement?): List<JsonObject> {
        if (element == null || element.isJsonNull) return emptyList()
        if (element.isJsonArray) return element.asJsonArray.mapNotNull { it.asObjectOrNull() }
        val obj = element.asObjectOrNull() ?: return emptyList()

        val collectionKeys = listOf("tracks", "results", "items", "data", "matches", "songs")
        for (key in collectionKeys) {
            val child = obj.get(key) ?: continue
            if (child.isJsonArray) return child.asJsonArray.mapNotNull { it.asObjectOrNull() }
            if (child.isJsonObject) {
                val nested = collectTrackObjects(child)
                if (nested.isNotEmpty()) return nested
            }
        }

        return if (obj.hasAny("id", "trackId", "track_id", "asin") && obj.hasAny("title", "name", "trackName")) {
            listOf(obj)
        } else {
            emptyList()
        }
    }

    private fun prefixCatalogTrackId(id: String, catalogProvider: String?, obj: JsonObject): String {
        val tidalId = obj.stringValue("tidal_id", "tidalId")?.trim()
        if (!tidalId.isNullOrBlank()) return "tidal:$tidalId"

        val qobuzId = obj.stringValue("qobuz_id", "qobuzId")?.trim()
        if (!qobuzId.isNullOrBlank()) return "qobuz:$qobuzId"

        val explicitProvider = catalogProvider?.takeIf { it.isNotBlank() }
            ?: when {
                obj.has("deezer_id") -> "deezer"
                obj.has("qobuz_id") -> "qobuz"
                obj.has("tidal_id") -> "tidal"
                else -> null
            }
        val normalizedId = id.trim()
        if (normalizedId.contains(":")) return normalizedId
        return when (explicitProvider) {
            "deezer" -> "deezer:$normalizedId"
            "qobuz" -> "qobuz:$normalizedId"
            "tidal" -> "tidal:$normalizedId"
            "amazon" -> "amazon:$normalizedId"
            else -> normalizedId
        }
    }


    private fun parseTrackObject(obj: JsonObject): ExternalTrack? {
        val id = obj.stringValue("id", "trackId", "track_id", "sourceId", "asin") ?: return null
        val catalogProvider = obj.stringValue("provider", "service", "source", "catalogProvider")
            ?.trim()
            ?.lowercase()
        val prefixedId = prefixCatalogTrackId(id, catalogProvider, obj)
        val title = obj.stringValue("title", "name", "trackName") ?: return null
        val artist = obj.stringValue("artist", "artistName", "performer", "subtitle")
            ?: obj.objectValue("artist")?.stringValue("name")
            ?: obj.objectValue("performer")?.stringValue("name")
            ?: obj.objectValue("album")?.objectValue("artist")?.stringValue("name")
            ?: return null
        val albumObj = obj.objectValue("album")
        val album = obj.stringValue("album", "albumTitle", "release")
            ?: albumObj?.stringValue("title", "name")
        if (!VocalRecordingClassifier.shouldAllowInCatalog(title, artist, album)) return null
        val artwork = obj.stringValue("artworkUrl", "artworkURL", "coverUrl", "cover", "image", "thumbnail")
            ?: albumObj?.stringValue("artworkUrl", "artworkURL", "coverUrl", "cover", "image", "thumbnail")
            ?: albumObj?.objectValue("image")?.stringValue("large", "thumbnail", "small")
        val durationMs = obj.longValue("durationMs", "duration_ms", "durationMilliseconds")
        val durationSeconds = obj.intValue("duration", "durationSeconds", "duration_seconds")
        val quality = obj.stringValue("quality", "qualityLabel", "audioQuality", "maximumQuality")
        val format = obj.stringValue("format", "codec", "mimeType", "mime")

        return ExternalTrack(
            id = prefixedId,
            title = title,
            artist = artist,
            album = album,
            durationMs = durationMs,
            durationSeconds = durationSeconds,
            artworkUrl = artwork,
            artworkURL = artwork,
            coverUrl = artwork,
            isrc = obj.stringValue("isrc", "ISRC"),
            format = format,
            quality = quality,
            streamUrl = obj.stringValue("streamUrl", "stream_url", "downloadUrl", "download_url", "url"),
            playable = obj.booleanValue("playable", "available", "streamable")
        )
    }

    private fun parseStreamResult(bodyString: String): ResolvedStream? {
        val trimmedBody = bodyString.trim().trim('"')
        if (trimmedBody.startsWith("http://", ignoreCase = true) || trimmedBody.startsWith("https://", ignoreCase = true)) {
            val streamUrl = trimmedBody
            return ResolvedStream(
                streamUrl = streamUrl,
                bitrateKbps = inferBitrate(null, null),
                mimeType = null,
                qualityLabel = null
            )
        }

        val root = runCatching { JsonParser.parseString(bodyString) }.getOrNull() ?: return null
        val streamUrl = findStringByKeys(
            root,
            listOf("streamUrl", "stream_url", "url", "downloadUrl", "download_url", "download_url_flac", "playUrl", "play_url", "link", "location")
        ) ?: return null
        val quality = findStringByKeys(root, listOf("quality", "qualityLabel", "audioQuality", "format", "codec"))
        val mimeType = findStringByKeys(root, listOf("mimeType", "mime", "contentType", "content_type"))
        val bitrate = findIntByKeys(root, listOf("bitrateKbps", "bitrate_kbps", "bitrate", "bit_rate", "br"))
            ?: inferBitrate(quality, mimeType)
        val expiresAt = findLongByKeys(root, listOf("expiresAt", "expires_at", "expires", "exp", "expiration"))

        return ResolvedStream(
            streamUrl = streamUrl,
            bitrateKbps = bitrate,
            mimeType = mimeType,
            expiresAt = expiresAt,
            qualityLabel = quality
        )
    }
    
    private fun inferBitrate(quality: String?, mimeType: String?): Int {
        val q = quality?.lowercase().orEmpty()
        val mime = mimeType?.lowercase().orEmpty()
        return when {
            q.contains("192") || q.contains("dsd") -> 9216
            q.contains("96") || q.contains("88.2") -> 3072
            q.contains("48") && q.contains("24") -> 2304
            q == "24" || q.contains("24-bit") || q.contains("hi-res") || q.contains("hires") -> 1411
            q.contains("lossless") || q.contains("flac") -> 1411
            q == "16" || q.contains("16-bit") -> 1411
            mime.contains("flac") -> 1411
            mime.contains("mp3") -> 320
            mime.contains("aac") || mime.contains("m4a") -> 256
            else -> 1411
        }
    }

    private fun JsonElement.asObjectOrNull(): JsonObject? =
        if (isJsonObject) asJsonObject else null

    private fun JsonObject.hasAny(vararg keys: String): Boolean =
        keys.any { has(it) && !get(it).isJsonNull }

    private fun JsonObject.objectValue(key: String): JsonObject? =
        get(key)?.asObjectOrNull()

    private fun JsonObject.stringValue(vararg keys: String): String? {
        for (key in keys) {
            val value = get(key) ?: continue
            if (value.isJsonNull) continue
            if (value.isJsonPrimitive) {
                val raw = runCatching { value.asString }.getOrNull()?.trim()
                if (!raw.isNullOrBlank()) return raw
            }
        }
        return null
    }

    private fun JsonObject.intValue(vararg keys: String): Int? {
        for (key in keys) {
            val value = get(key) ?: continue
            val intValue = runCatching { value.asInt }.getOrNull()
            if (intValue != null && intValue > 0) return intValue
        }
        return null
    }

    private fun JsonObject.longValue(vararg keys: String): Long? {
        for (key in keys) {
            val value = get(key) ?: continue
            val longValue = runCatching { value.asLong }.getOrNull()
            if (longValue != null && longValue > 0L) return longValue
        }
        return null
    }

    private fun JsonObject.booleanValue(vararg keys: String): Boolean? {
        for (key in keys) {
            val value = get(key) ?: continue
            val boolValue = runCatching { value.asBoolean }.getOrNull()
            if (boolValue != null) return boolValue
        }
        return null
    }

    private fun findStringByKeys(element: JsonElement?, keys: List<String>): String? {
        if (element == null || element.isJsonNull) return null
        if (element.isJsonPrimitive) {
            val raw = runCatching { element.asString }.getOrNull()?.trim()
            return raw?.takeIf { it.startsWith("http://", ignoreCase = true) || it.startsWith("https://", ignoreCase = true) }
        }
        if (element.isJsonArray) {
            element.asJsonArray.forEach { child ->
                findStringByKeys(child, keys)?.let { return it }
            }
            return null
        }
        val obj = element.asJsonObject
        for (key in keys) {
            val value = obj.get(key) ?: continue
            if (value.isJsonPrimitive) {
                val raw = runCatching { value.asString }.getOrNull()?.trim()
                if (!raw.isNullOrBlank()) return raw
            }
        }
        for ((_, child) in obj.entrySet()) {
            findStringByKeys(child, keys)?.let { return it }
        }
        return null
    }

    private fun findIntByKeys(element: JsonElement?, keys: List<String>): Int? {
        if (element == null || element.isJsonNull) return null
        if (element.isJsonArray) {
            element.asJsonArray.forEach { child ->
                findIntByKeys(child, keys)?.let { return it }
            }
            return null
        }
        if (!element.isJsonObject) return null
        val obj = element.asJsonObject
        for (key in keys) {
            obj.intValue(key)?.let { return it }
        }
        for ((_, child) in obj.entrySet()) {
            findIntByKeys(child, keys)?.let { return it }
        }
        return null
    }

    private fun findLongByKeys(element: JsonElement?, keys: List<String>): Long? {
        if (element == null || element.isJsonNull) return null
        if (element.isJsonArray) {
            element.asJsonArray.forEach { child ->
                findLongByKeys(child, keys)?.let { return it }
            }
            return null
        }
        if (!element.isJsonObject) return null
        val obj = element.asJsonObject
        for (key in keys) {
            obj.longValue(key)?.let { return it }
        }
        for ((_, child) in obj.entrySet()) {
            findLongByKeys(child, keys)?.let { return it }
        }
        return null
    }

    suspend fun testConnection(): ExternalSourceManifest? = withContext(Dispatchers.IO) {
        try {
            val manifestUrl = "$catalogBaseUrl/manifest.json"
            val request = Request.Builder().url(manifestUrl).build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.e("ExternalSourceProvider", "Test connection failed: ${response.code} for $catalogBaseUrl")
                    return@withContext null
                }
                
                val bodyString = response.body?.string() ?: return@withContext null
                val manifest = gson.fromJson(bodyString, ExternalSourceManifest::class.java)
                if (manifest.id.isBlank() || manifest.name.isBlank()) {
                    Log.e("ExternalSourceProvider", "Manifest missing required fields for $catalogBaseUrl")
                    return@withContext null
                }
                manifest
            }
        } catch (e: Exception) {
            Log.e("ExternalSourceProvider", "Exception testing connection to $catalogBaseUrl", e)
            null
        }
    }

    /**
     * Comprehensive health check: manifest + search + stream.
     * Returns a [SourceHealthResult] with full diagnostics.
     */
    suspend fun testHealth(): SourceHealthResult = withContext(Dispatchers.IO) {
        val startMs = System.currentTimeMillis()
        var manifest: ExternalSourceManifest? = null
        var manifestMs: Long = -1
        var searchResultCount = 0
        var searchMs: Long = -1
        var streamValid = false
        var streamMs: Long = -1
        var errorMessage: String? = null
        var firstTrackId: String? = null

        // Step 1: Manifest
        try {
            val manifestStart = System.currentTimeMillis()
            manifest = testConnection()
            manifestMs = System.currentTimeMillis() - manifestStart
            if (manifest == null) {
                errorMessage = "Manifest fetch failed or invalid JSON"
            }
        } catch (e: Exception) {
            errorMessage = "Manifest exception: ${e.message}"
        }

        // Step 2: Search (if manifest passed)
        if (manifest != null && errorMessage == null) {
            try {
                val searchStart = System.currentTimeMillis()
                val results = search("The Weeknd")
                searchMs = System.currentTimeMillis() - searchStart
                searchResultCount = results.size
                if (results.isNotEmpty()) {
                    firstTrackId = results.first().id
                } else {
                    errorMessage = "Catalog search returned no playable results"
                }
            } catch (e: Exception) {
                errorMessage = "Search exception: ${e.message}"
            }
        }

        // Step 3: Stream (if we got a track)
        if (firstTrackId != null && errorMessage == null) {
            try {
                val streamStart = System.currentTimeMillis()
                val resolved = resolveStream(firstTrackId)
                streamMs = System.currentTimeMillis() - streamStart
                if (resolved != null && resolved.streamUrl.isNotBlank()) {
                    streamValid = validateStreamUrl(resolved.streamUrl)
                } else {
                    errorMessage = "Stream resolve returned null or blank URL"
                }
            } catch (e: Exception) {
                errorMessage = "Stream exception: ${e.message}"
            }
        }

        val totalMs = System.currentTimeMillis() - startMs
        val status = when {
            !config.enabled -> "Disabled"
            errorMessage != null -> "Failed"
            manifest == null -> "Misconfigured"
            !streamValid && firstTrackId != null -> "Stream Unavailable"
            searchMs > 5000 || streamMs > 5000 -> "Slow"
            else -> "Healthy"
        }

        Log.d("VANTA_SOURCE_HEALTH",
            "providerId=$providerId baseUrl=$catalogBaseUrl status=$status " +
            "manifestMs=$manifestMs searchMs=$searchMs searchCount=$searchResultCount " +
            "streamMs=$streamMs streamValid=$streamValid totalMs=$totalMs " +
            "capabilities=${manifest?.capabilities ?: "null"} " +
            "errorMessage=${errorMessage ?: "none"}")

        SourceHealthResult(
            providerId = providerId,
            baseUrl = catalogBaseUrl,
            status = status,
            manifest = manifest,
            manifestMs = manifestMs,
            searchResultCount = searchResultCount,
            searchMs = searchMs,
            streamValid = streamValid,
            streamMs = streamMs,
            totalMs = totalMs,
            errorMessage = errorMessage,
            firstTrackId = firstTrackId
        )
    }

    /**
     * Test a single search query against this source and return raw results.
     */
    suspend fun testSearch(query: String): SourceTestSearchResult = withContext(Dispatchers.IO) {
        val startMs = System.currentTimeMillis()
        try {
            val results = search(query)
            val durationMs = System.currentTimeMillis() - startMs
            Log.d("VANTA_SOURCE_TEST_SEARCH",
                "providerId=$providerId query='$query' resultCount=${results.size} durationMs=$durationMs")
            SourceTestSearchResult(
                providerId = providerId,
                query = query,
                results = results,
                durationMs = durationMs,
                error = null
            )
        } catch (e: Exception) {
            val durationMs = System.currentTimeMillis() - startMs
            Log.e("VANTA_SOURCE_TEST_SEARCH",
                "providerId=$providerId query='$query' durationMs=$durationMs error='${e.message}'")
            SourceTestSearchResult(
                providerId = providerId,
                query = query,
                results = emptyList(),
                durationMs = durationMs,
                error = e.message
            )
        }
    }

    /**
     * Test stream resolution for a specific trackId, then validate the stream URL.
     */
    suspend fun testStream(trackId: String): SourceTestStreamResult = withContext(Dispatchers.IO) {
        val startMs = System.currentTimeMillis()
        try {
            val resolved = resolveStream(trackId)
            val resolveMs = System.currentTimeMillis() - startMs
            if (resolved == null) {
                Log.w("VANTA_SOURCE_TEST_STREAM",
                    "providerId=$providerId trackId=$trackId resolveMs=$resolveMs result=null")
                return@withContext SourceTestStreamResult(
                    providerId = providerId,
                    trackId = trackId,
                    resolved = null,
                    resolveMs = resolveMs,
                    validationPassed = false,
                    validationReason = "Resolve returned null",
                    error = null
                )
            }

            val streamUrl = resolved.streamUrl
            val host = runCatching { java.net.URI(streamUrl).host }.getOrDefault("unknown")
            val validated = validateStreamUrl(streamUrl)
            val validationReason = if (validated) "PASS" else "Stream URL validation failed"

            Log.d("VANTA_SOURCE_TEST_STREAM",
                "providerId=$providerId trackId=$trackId resolveMs=$resolveMs " +
                "host=$host bitrate=${resolved.bitrateKbps} mime=${resolved.mimeType ?: "unknown"} " +
                "quality=${resolved.qualityLabel ?: "unknown"} " +
                "validation=${if (validated) "PASS" else "FAIL"} reason=$validationReason")

            SourceTestStreamResult(
                providerId = providerId,
                trackId = trackId,
                resolved = resolved,
                resolveMs = resolveMs,
                validationPassed = validated,
                validationReason = validationReason,
                error = null
            )
        } catch (e: Exception) {
            val durationMs = System.currentTimeMillis() - startMs
            Log.e("VANTA_SOURCE_TEST_STREAM",
                "providerId=$providerId trackId=$trackId durationMs=$durationMs error='${e.message}'")
            SourceTestStreamResult(
                providerId = providerId,
                trackId = trackId,
                resolved = null,
                resolveMs = durationMs,
                validationPassed = false,
                validationReason = "Exception",
                error = e.message
            )
        }
    }

    private fun validateStreamUrl(url: String): Boolean {
        var connection: java.net.HttpURLConnection? = null
        return try {
            connection = java.net.URL(url).openConnection() as java.net.HttpURLConnection
            connection.requestMethod = "HEAD"
            connection.connectTimeout = 5000
            connection.readTimeout = 5000
            connection.setRequestProperty("Range", "bytes=0-0")
            val responseCode = connection.responseCode
            val contentType = connection.contentType.orEmpty().lowercase()
            val looksLikeAudio = contentType.startsWith("audio/") ||
                contentType.contains("octet-stream") ||
                url.substringBefore('?').lowercase().let {
                    it.endsWith(".flac") || it.endsWith(".m4a") || it.endsWith(".aac") ||
                        it.endsWith(".mp3") || it.endsWith(".ogg") || it.endsWith(".opus")
                }
            (responseCode in 200..299 || responseCode == 206) && looksLikeAudio
        } catch (e: Exception) {
            false
        } finally {
            connection?.disconnect()
        }
    }
}

/** Result of a comprehensive health check. */
data class SourceHealthResult(
    val providerId: String,
    val baseUrl: String,
    val status: String,
    val manifest: ExternalSourceManifest?,
    val manifestMs: Long,
    val searchResultCount: Int,
    val searchMs: Long,
    val streamValid: Boolean,
    val streamMs: Long,
    val totalMs: Long,
    val errorMessage: String?,
    val firstTrackId: String?
)

/** Result of a test search. */
data class SourceTestSearchResult(
    val providerId: String,
    val query: String,
    val results: List<SourceSearchResult>,
    val durationMs: Long,
    val error: String?
)

/** Result of a test stream resolve. */
data class SourceTestStreamResult(
    val providerId: String,
    val trackId: String,
    val resolved: ResolvedStream?,
    val resolveMs: Long,
    val validationPassed: Boolean,
    val validationReason: String,
    val error: String?
)
