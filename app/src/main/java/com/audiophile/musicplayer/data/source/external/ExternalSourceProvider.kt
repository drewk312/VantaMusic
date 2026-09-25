package com.audiophile.musicplayer.data.source.external

import android.util.Log
import com.audiophile.musicplayer.data.source.external.CommunityRelaySigner
import com.audiophile.musicplayer.data.display.AudioQualityInfo
import com.audiophile.musicplayer.data.source.CloudLibraryHelpers
import com.audiophile.musicplayer.data.source.MusicSourceProvider
import com.audiophile.musicplayer.data.source.ResolvedStream
import com.audiophile.musicplayer.data.source.SourceRegistry
import com.audiophile.musicplayer.playback.CdnPlaybackHeaders
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
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

open class ExternalSourceProvider(
    protected val config: ExternalSourceConfig
) : MusicSourceProvider {

    override val providerId: String = config.id
    override val providerName: String = config.displayName

    private val client = OkHttpClient.Builder()
        .connectTimeout(3, TimeUnit.SECONDS)
        .readTimeout(6, TimeUnit.SECONDS)
        .callTimeout(8, TimeUnit.SECONDS)
        .addInterceptor(com.audiophile.musicplayer.playback.GatewayApiKeyInterceptor)
        .build()

    private val streamResolveClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .callTimeout(8, TimeUnit.SECONDS)
        .followRedirects(false)
        .followSslRedirects(false)
        .addInterceptor(com.audiophile.musicplayer.playback.GatewayApiKeyInterceptor)
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
                    if (!providerAcceptsCatalogTrackId(providerId, playbackId)) {
                        Log.d(
                            "VANTA_EXTERNAL_SOURCE",
                            "providerId=$providerId operation=search skippedMismatchedCatalogId=$playbackId"
                        )
                        return@mapNotNull null
                    }

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
            val stream = run {
                val root = config.baseUrl.trim().trimEnd('/')
                if (isVantaGateway(root) || isVantaGateway(catalogBaseUrl)) {
                    val bodyString = firstSuccessfulBody(streamUrlCandidates(trackId), "stream")
                    if (!bodyString.isNullOrBlank()) {
                        parseStreamResult(bodyString)?.let { return@run it }
                    }
                    postVantaGatewayDownload(trackId)?.let { return@run it }
                    return@run null
                }
                postDirectCommunityRelay(trackId)?.let { return@run it }
                val bodyString = firstSuccessfulBody(streamUrlCandidates(trackId), "stream")
                    ?: return@run null
                parseStreamResult(bodyString)
            } ?: return@withContext null

            Log.d(
                "VANTA_TIDAL_ATMOS",
                "resolveStream won provider=$providerId atmos=${stream.isDolbyAtmos} " +
                    "surround=${stream.isSurround} spatial=${stream.isSpatialAudio} " +
                    "format=${stream.format} label=${stream.qualityLabel}"
            )

            // A spatial/Atmos master arriving for a non-Atmos request means a
            // relay handed back the immersive mix instead of the stereo one.
            // iPhone/Pixel-style downmixes of E-AC-3 JOC can sound ruined, so
            // re-resolve at CD FLAC before committing an unexpected Atmos file.
            val atmosTolerated = SpotiFlacEndpoints.prefersSpatialMix()
            if (!atmosTolerated && (stream.isDolbyAtmos || stream.isSurround || stream.isSpatialAudio)) {
                Log.w(
                    "VANTA_TIDAL_ATMOS",
                    "resolveStream UNEXPECTED spatial stream for non-Atmos request, forcing stereo FLAC retry"
                )
                val stereo = resolveStereoFallback(trackId)
                if (stereo != null && !(stereo.isDolbyAtmos || stereo.isSurround || stereo.isSpatialAudio)) {
                    Log.d("VANTA_TIDAL_ATMOS", "resolveStream stereo fallback accepted format=${stereo.format}")
                    return@withContext stereo
                }
                Log.w("VANTA_TIDAL_ATMOS", "resolveStream stereo fallback unavailable, keeping spatial result")
            }
            stream
        } catch (e: java.io.IOException) {
            Log.e("ExternalSourceProvider", "IO during stream resolution for $providerName", e)
            null
        }
    }

    /** Re-resolve at CD FLAC so an unexpected immersive master is never committed. */
    private suspend fun resolveStereoFallback(trackId: String): ResolvedStream? = withContext(Dispatchers.IO) {
        val id = encoded(
            trackId
                .removePrefix("tidal:")
                .removePrefix("qobuz:")
                .removePrefix("deezer:")
                .removePrefix("amazon:")
                .removePrefix("pandora:")
        )
        if (id.isBlank()) return@withContext null
        val root = config.baseUrl.trim().trimEnd('/')
        val bodyString = firstSuccessfulBody(compactStreamUrls(root, id, "16", providerId), "stream")
            ?: return@withContext null
        parseStreamResult(bodyString)
    }

    private fun hasByoaAuthTokens(): Boolean {
        val store = GatewayStreamResolver.byoaStore ?: return false
        return store.getQobuzUserToken() != null ||
            store.getDeezerArl() != null ||
            store.getTidalOauthJson() != null ||
            store.getAmazonToken() != null
    }

    private fun encoded(value: String): String =
        URLEncoder.encode(value, "UTF-8").replace("+", "%20")

    protected fun searchUrls(query: String, base: String = catalogBaseUrl): List<String> {
        val q = encoded(query)
        return compactSearchUrls(base, q)
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
        return compactStreamUrls(base, id, SpotiFlacEndpoints.PREFERRED_STREAM_QUALITY, providerId)
    }

    internal fun firstSuccessfulBodyPublic(urls: List<String>, operation: String): String? =
        firstSuccessfulBody(urls, operation)

    internal fun parseStreamResultPublic(bodyString: String): ResolvedStream? =
        parseStreamResult(bodyString)

    private fun postVantaGatewayDownload(trackId: String): ResolvedStream? {
        val root = config.baseUrl.trim().trimEnd('/')
        if (!isVantaGateway(root) && !isVantaGateway(catalogBaseUrl)) return null
        val separator = trackId.indexOf(':')
        val providerHint = if (separator > 0) {
            trackId.substring(0, separator).lowercase()
        } else {
            catalogProviderHint(providerId) ?: if (trackId.matches(Regex("^\\d{4,}$"))) "qobuz" else null
        } ?: return null
        val cleanId = if (separator > 0) trackId.substring(separator + 1).trim() else trackId.trim()
        if (cleanId.isBlank()) return null
        GatewayStreamResolver.byoaStore?.refreshTidalAccessIfNeeded(streamResolveClient)
        val payload = com.audiophile.musicplayer.data.source.buildGatewayResolvePayload(
            cleanId,
            providerHint,
            SpotiFlacEndpoints.PREFERRED_STREAM_QUALITY
        )
        val request = Request.Builder()
            .url("$root/api/dl")
            .header("User-Agent", "VANTA/1.0")
            .header("Accept", "application/json")
            .apply { GatewayStreamResolver.byoaStore?.buildByoaHeaders()?.forEach { (k, v) -> header(k, v) } }
            .post(payload.toRequestBody("application/json; charset=utf-8".toMediaType()))
            .build()
        return try {
            streamResolveClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.d(
                        "VANTA_EXTERNAL_SOURCE",
                        "providerId=$providerId operation=api_dl status=${response.code} id=$cleanId"
                    )
                    return@use null
                }
                parseStreamResult(response.body?.string().orEmpty())
            }
        } catch (e: java.io.IOException) {
            Log.d(
                "VANTA_EXTERNAL_SOURCE",
                "providerId=$providerId operation=api_dl error='${e.message}'"
            )
            null
        }
    }

    private fun postDirectCommunityRelay(trackId: String): ResolvedStream? {
        val credentialStore = GatewayStreamResolver.byoaStore
        val session = credentialStore?.getCommunityRelaySession()?.takeIf { CommunityRelaySigner.isUsable(it) }
            ?: CommunityRelaySigner.fallbackSession?.takeIf { CommunityRelaySigner.isUsable(it) }
        if (session == null) return null

        val separator = trackId.indexOf(':')
        val providerHint = if (separator > 0) {
            trackId.substring(0, separator).lowercase()
        } else {
            catalogProviderHint(providerId) ?: if (trackId.matches(Regex("^\\d{4,}$"))) "qobuz" else null
        } ?: return null

        val validProviders = setOf("qobuz", "tidal", "amazon")
        if (providerHint !in validProviders) return null

        val cleanId = if (separator > 0) trackId.substring(separator + 1).trim() else trackId.trim()
        if (!cleanId.matches(Regex("^[A-Za-z0-9_-]{4,128}$"))) return null

        val endpointHost = when (providerHint) {
            "qobuz" -> "qbz-oss.spotbye.qzz.io"
            "tidal" -> "tdl-oss.spotbye.qzz.io"
            "amazon" -> "amz-oss.spotbye.qzz.io"
            else -> return null
        }
        val url = "https://$endpointHost/api/dl"
        val primaryQuality = when (providerHint) {
            "qobuz" -> SpotiFlacEndpoints.mapQobuzQualityToCommunity(SpotiFlacEndpoints.PREFERRED_STREAM_QUALITY)
            else -> SpotiFlacEndpoints.mapTidalQualityToCommunity(SpotiFlacEndpoints.PREFERRED_STREAM_QUALITY)
        }
        val qualityCandidates = if (primaryQuality != "16") listOf(primaryQuality, "16") else listOf("16")

        for (quality in qualityCandidates) {
            val jsonBody = gson.toJson(mapOf("id" to cleanId, "quality" to quality))
            val headers = CommunityRelaySigner.signRequest(jsonBody, session)

            Log.d("VANTA_EXTERNAL_SOURCE", "postDirectCommunityRelay: POST $url id=$cleanId quality=$quality provider=$providerHint")

            val requestBuilder = Request.Builder().url(url)
                .post(jsonBody.toRequestBody("application/json; charset=utf-8".toMediaType()))
            headers.forEach { (k, v) -> requestBuilder.header(k, v) }

            try {
                val resolved = streamResolveClient.newCall(requestBuilder.build()).execute().use { response ->
                    val responseBody = response.body?.string().orEmpty()
                    if (response.isSuccessful) {
                        val root = runCatching { JsonParser.parseString(responseBody) }.getOrNull()
                        val rawUrl = root?.let { if (it.isJsonObject) it.asJsonObject.get("url")?.asString else null }
                        if (!rawUrl.isNullOrBlank()) {
                            if (!rawUrl.startsWith("MANIFEST:") && isSampleOrPreviewUrl(rawUrl)) {
                                Log.w("VANTA_EXTERNAL_SOURCE", "Community relay returned sample/preview, rejecting: $rawUrl")
                                return@use null
                            }
                            val isManifest = rawUrl.startsWith("MANIFEST:")
                            val manifestXml = if (isManifest) {
                                runCatching {
                                    String(java.util.Base64.getDecoder().decode(rawUrl.removePrefix("MANIFEST:").trim()), Charsets.UTF_8)
                                }.getOrNull()
                            } else null
                            val isAtmos = AudioQualityInfo.hasAtmosCodecEvidence(
                                manifestXml,
                                manifestXml?.let { "application/dash+xml" },
                                rawUrl,
                                quality
                            ) || (rawUrl.lowercase().let { urlLower ->
                                ("ec.3" in urlLower || "eac3" in urlLower || "eac-3" in urlLower || "eac3_joc" in urlLower) &&
                                    "atmos" in urlLower
                            })
                            val finalUrl = if (isManifest) {
                                val b64 = rawUrl.removePrefix("MANIFEST:").trim()
                                val safeB64 = b64.replace('+', '-').replace('/', '_').trimEnd('=')
                                val gwUrl = credentialStore?.getEffectiveGatewayUrl() ?: SpotiFlacEndpoints.DEFAULT_GATEWAY_BASE_URL
                                "${gwUrl.trimEnd('/')}/manifest/mpd?data=$safeB64"
                            } else {
                                rawUrl
                            }
                            Log.d("VANTA_EXTERNAL_SOURCE", "Community relay $providerHint succeeded for $cleanId (quality=$quality isAtmos=$isAtmos)")
                            decorateForPlayback(
                            ResolvedStream(
                                streamUrl = finalUrl,
                                bitrateKbps = CloudLibraryHelpers.inferStreamBitrateKbps(null, quality, if (isManifest) "application/dash+xml" else "audio/flac", null),
                                mimeType = if (isManifest) "application/dash+xml" else "audio/flac",
                                qualityLabel = when {
                                    isAtmos -> "Dolby Atmos"
                                    isManifest -> "Tidal Lossless"
                                    quality == "24" -> "24-bit Hi-Res FLAC"
                                    else -> "16-bit / 44.1 kHz FLAC"
                                },
                                format = when {
                                    isAtmos -> "EAC3_JOC"
                                    isManifest -> "DASH"
                                    else -> "FLAC"
                                },
                                isDolbyAtmos = isAtmos,
                                isSpatialAudio = isAtmos,
                                isSurround = isAtmos,
                                providerId = providerId,
                                fulfillmentProviderId = providerHint,
                                isLossless = !isAtmos
                            )
                            )
                        } else null
                    } else if (response.code == 400 && quality != "16") {
                        Log.d("VANTA_EXTERNAL_SOURCE", "Community relay $providerHint rejected quality $quality for $cleanId, falling back...")
                        null
                    } else if (response.code == 401) {
                        Log.w("VANTA_EXTERNAL_SOURCE", "Community relay $providerHint 401 IP mismatch or invalid session for $cleanId.")
                        return null
                    } else if (response.code == 428) {
                        Log.w("VANTA_EXTERNAL_SOURCE", "Community relay $providerHint 428 verification session required for $cleanId")
                        return null
                    } else {
                        Log.d("VANTA_EXTERNAL_SOURCE", "Community relay $providerHint failed: ${response.code} body=${responseBody.take(200)}")
                        null
                    }
                }
                if (resolved != null) return resolved
            } catch (e: Exception) {
                Log.d("VANTA_EXTERNAL_SOURCE", "Community relay $providerHint error: ${e.message}")
            }
        }
        return null
    }

    private fun catalogProviderHint(providerId: String): String? {
        val id = providerId.lowercase()
        return when {
            "tidal" in id && "qobuz" !in id -> "tidal"
            id.contains("qobuz") && "tidal" !in id -> "qobuz"
            "deezer" in id -> "deezer"
            "amazon" in id -> "amazon"
            "pandora" in id -> "pandora"
            else -> null
        }
    }

    private fun firstSuccessfulBody(urls: List<String>, operation: String): String? {
        val budgetMs = if (operation == "search") {
            CloudLibraryHelpers.CLOUD_SEARCH_TIMEOUT_MS
        } else {
            CloudLibraryHelpers.TAP_PLAY_TOTAL_BUDGET_MS
        }
        val startedAt = System.currentTimeMillis()
        for (url in urls) {
            if (System.currentTimeMillis() - startedAt >= budgetMs) {
                Log.w(
                    "VANTA_EXTERNAL_SOURCE",
                    "providerId=$providerId operation=$operation budgetMs=$budgetMs exhausted"
                )
                break
            }
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "VANTA/1.0")
                .build()
            try {
                val httpClient = if (operation == "search") client else streamResolveClient
                httpClient.newCall(request).execute().use { response ->
                    val parsed = if (operation == "stream") {
                        com.audiophile.musicplayer.data.source.GatewayStreamResponseParser.locationOrBody(
                            code = response.code,
                            location = response.header("Location"),
                            body = response.body?.string()
                        )
                    } else if (response.isSuccessful) {
                        response.body?.string()
                    } else {
                        null
                    }
                    if (!parsed.isNullOrBlank()) {
                        Log.d(
                            "VANTA_EXTERNAL_SOURCE",
                            "providerId=$providerId operation=$operation url='$url' status=${response.code} result=body"
                        )
                        return parsed
                    }
                    Log.d(
                        "VANTA_EXTERNAL_SOURCE",
                        "providerId=$providerId operation=$operation url='$url' status=${response.code}"
                    )
                }
            } catch (e: java.io.IOException) {
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

    private fun isSampleOrPreviewUrl(url: String): Boolean {
        if (url.isBlank()) return false
        val lower = url.lowercase()
        if (lower.contains("audio-ssl.itunes.apple.com") || lower.contains("itunes.apple.com")) return true
        if (lower.contains("cdns-preview") || lower.contains(".dzcdn.net/stream/")) return true
        if (lower.contains("/preview/") || lower.contains("preview.mpd") || lower.contains("preview.m4a")) return true
        if (lower.contains("range=0-") || lower.contains("range=0%2d")) return true
        return false
    }

    private fun parseStreamResult(bodyString: String): ResolvedStream? {
        val trimmedBody = bodyString.trim().trim('"')
        if (trimmedBody.startsWith("http://", ignoreCase = true) || trimmedBody.startsWith("https://", ignoreCase = true)) {
            if (isSampleOrPreviewUrl(trimmedBody)) return null
            val streamUrl = trimmedBody
            return decorateForPlayback(
                ResolvedStream(
                    streamUrl = streamUrl,
                    providerId = providerId,
                    bitrateKbps = CloudLibraryHelpers.inferStreamBitrateKbps(
                        reportedKbps = null,
                        quality = null,
                        mimeType = com.audiophile.musicplayer.playback.PlaybackMediaType.inferFromUrl(streamUrl),
                        format = null
                    ),
                    mimeType = com.audiophile.musicplayer.playback.PlaybackMediaType.inferFromUrl(streamUrl),
                    qualityLabel = null
                )
            )
        }

        val root = runCatching { JsonParser.parseString(bodyString) }.getOrNull() ?: return null
        val streamUrl = findStringByKeys(
            root,
            listOf("streamUrl", "stream_url", "url", "downloadUrl", "download_url", "download_url_flac", "playUrl", "play_url", "link", "location")
        ) ?: return null
        if (isSampleOrPreviewUrl(streamUrl)) {
            Log.w("ExternalSourceProvider", "Rejected sample or preview stream: $streamUrl")
            return null
        }
        val quality = findStringByKeys(root, listOf("quality", "qualityLabel", "audioQuality"))
        val format = findStringByKeys(root, listOf("format", "codec"))
        val mimeType = findStringByKeys(root, listOf("mimeType", "mime", "contentType", "content_type"))
        val resolvedProviderId = findStringByKeys(root, listOf("provider", "providerId", "provider_id", "service"))
            ?.takeIf { it.isNotBlank() } ?: providerId
        val bitrate = CloudLibraryHelpers.inferStreamBitrateKbps(
            reportedKbps = findIntByKeys(root, listOf("bitrateKbps", "bitrate_kbps", "bitrate", "bit_rate", "br")),
            quality = quality,
            mimeType = mimeType,
            format = format
        )
        val expiresAt = findLongByKeys(root, listOf("expiresAt", "expires_at", "expires", "exp", "expiration"))
            ?.let { value -> if (value < 100_000_000_000L) value * 1000L else value }
        val drm = root.takeIf { it.isJsonObject }
            ?.asJsonObject
            ?.let(com.audiophile.musicplayer.data.source.GatewayStreamResponseParser::drmConfiguration)

        // An Atmos-encoded bitstream (E-AC-3 JOC / AC-4) must reach the sink
        // untouched so it can be passed through; flag it so the player bypasses
        // PCM-only DSP and the UI can label it correctly.
        val isAtmos = AudioQualityInfo.hasAtmosCodecEvidence(mimeType, quality, format)
        // Eclipsa Audio (IAMF) is open: it is decoded + binaural-rendered, not
        // passed through, so mark it spatial and let the Eclipsa renderer act.
        val isEclipsa = AudioQualityInfo.hasEclipsaCodecEvidence(mimeType, quality)
            || quality?.contains("eclipsa", ignoreCase = true) == true
            || mimeType?.contains("iamf", ignoreCase = true) == true

        return decorateForPlayback(
            ResolvedStream(
                streamUrl = streamUrl,
                providerId = resolvedProviderId,
                bitrateKbps = bitrate,
                mimeType = mimeType,
                expiresAt = expiresAt,
                qualityLabel = quality,
                format = format,
                isDolbyAtmos = isAtmos,
                isEclipsaAudio = isEclipsa,
                isSpatialAudio = isAtmos || isEclipsa,
                isSurround = isAtmos,
                drm = drm,
            )
        ).also {
            if (isAtmos || isEclipsa) {
                Log.d(
                    "VANTA_TIDAL_ATMOS",
                    "parseStreamResult DETECTED isAtmos=$isAtmos isEclipsa=$isEclipsa " +
                    "quality=$quality mime=$mimeType url=${streamUrl.take(120)}"
                )
            }
        }
    }

    private fun decorateForPlayback(stream: ResolvedStream): ResolvedStream {
        val url = stream.streamUrl
        val cdnHeaders = CdnPlaybackHeaders.forUrl(url)
        return stream.copy(
            expiresAt = SourceRegistry.resolveMinExpiryMs(url, stream.expiresAt, stream.providerId),
            requestHeaders = stream.requestHeaders + cdnHeaders
        )
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
        } catch (e: java.io.IOException) {
            false
        } finally {
            connection?.disconnect()
        }
    }

    companion object {
        internal fun isVantaGateway(base: String): Boolean {
            val host = base.lowercase()
            return "workers.dev" in host || "vanta-music-gateway" in host
        }

        internal fun compactSearchUrls(base: String, encodedQuery: String): List<String> {
            val root = base.trim().trimEnd('/')
            return if (isVantaGateway(root)) {
                listOf("$root/search?q=$encodedQuery")
            } else {
                listOf(
                    "$root/search?q=$encodedQuery",
                    "$root/api/search?q=$encodedQuery"
                )
            }
        }

        internal fun compactStreamUrls(
            base: String,
            encodedId: String,
            quality: String,
            providerId: String
        ): List<String> {
            val root = base.trim().trimEnd('/')
            return if (isVantaGateway(root)) {
                val catalog = providerId.substringAfterLast(':').lowercase().let { id ->
                    when {
                        "tidal" in id && "qobuz" !in id -> "tidal"
                        "qobuz" in id && "tidal" !in id -> "qobuz"
                        "deezer" in id -> "deezer"
                        "amazon" in id -> "amazon"
                        "pandora" in id -> "pandora"
                        id in setOf("qobuz", "tidal", "deezer", "amazon", "pandora") -> id
                        else -> null
                    }
                }
                val providerQuery = catalog?.let { "&provider=$it" }.orEmpty()
                listOf("$root/stream/$encodedId?quality=$quality$providerQuery")
            } else {
                listOf(
                    "$root/stream/$encodedId?quality=$quality",
                    "$root/api/stream/$encodedId?quality=$quality",
                    "$root/stream/$encodedId?quality=$quality&provider=$providerId"
                )
            }
        }
    }
}

/** Prevent a provider-specific resolver from receiving another catalog's id. */
internal fun providerAcceptsCatalogTrackId(providerId: String, trackId: String): Boolean {
    val separator = trackId.indexOf(':')
    if (separator <= 0) return true
    val catalog = trackId.substring(0, separator).lowercase()
    val provider = providerId.lowercase()
    return when {
        provider == "cloudflare_gateway" -> true
        provider.contains("qobuz") && provider != "qobuz_tidal" -> catalog == "qobuz"
        provider.contains("tidal") && provider != "qobuz_tidal" -> catalog == "tidal"
        provider.contains("deezer") -> catalog == "deezer"
        provider.contains("amazon") -> catalog == "amazon"
        provider.contains("pandora") -> catalog == "pandora"
        else -> true
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
