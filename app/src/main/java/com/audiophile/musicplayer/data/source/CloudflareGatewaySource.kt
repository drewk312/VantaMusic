package com.audiophile.musicplayer.data.source

import android.util.Log
import com.audiophile.musicplayer.data.canonical.CanonicalPlaylist
import com.audiophile.musicplayer.data.display.DisplayMetadataCleaner
import com.audiophile.musicplayer.data.source.playback.toGatewayQuality
import com.audiophile.musicplayer.playback.CdnPlaybackHeaders
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

class CloudflareGatewaySource(
    private val gatewayUrl: String = "https://vanta-music-gateway.16drewk.workers.dev"
) : MusicSourceProvider {

    override val providerId: String = "cloudflare_gateway"
    override val providerName: String = "Cloudflare Gateway"

    val searchGroupKey: String
        get() = gatewayUrl.trim().trimEnd('/').lowercase()

    @Volatile private var cachedPlaylistQuery: String = ""
    @Volatile private var cachedPlaylists: List<CanonicalPlaylist> = emptyList()

    fun cachedSearchPlaylists(query: String): List<CanonicalPlaylist> =
        if (query.trim().equals(cachedPlaylistQuery, ignoreCase = true)) cachedPlaylists else emptyList()

    private val client = OkHttpClient.Builder()
        .connectTimeout(3, TimeUnit.SECONDS)
        .readTimeout(6, TimeUnit.SECONDS)
        .callTimeout(8, TimeUnit.SECONDS)
        .followRedirects(false)
        .followSslRedirects(false)
        .addInterceptor(com.audiophile.musicplayer.playback.GatewayApiKeyInterceptor)
        .build()

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    // GET /stream is the working catalog path. Keep it inside the 8s tap budget.
    private val playbackClient = client.newBuilder()
        .readTimeout(7, TimeUnit.SECONDS)
        .callTimeout(8, TimeUnit.SECONDS)
        .build()

    private val fastPostClient = client.newBuilder()
        .readTimeout(2, TimeUnit.SECONDS)
        .callTimeout(3, TimeUnit.SECONDS)
        .build()

    /** Current editorial releases. These are catalog tracks, not codec claims. */
    suspend fun editorialNewReleases(limit: Int = 18): List<SourceSearchResult> = withContext(Dispatchers.IO) {
        try {
            val url = "$gatewayUrl/api/new-releases?limit=${limit.coerceIn(1, 30)}"
            val body = client.newCall(Request.Builder().url(url).build()).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.w("CloudflareGateway", "New releases failed: ${response.code}")
                    return@withContext emptyList()
                }
                response.body?.string() ?: return@withContext emptyList()
            }
            val tracks = JsonParser.parseString(body)?.asJsonObject?.getAsJsonArray("tracks")
                ?: return@withContext emptyList()
            parseGatewayTrackArray(tracks)
        } catch (e: Exception) {
            Log.w("CloudflareGateway", "New releases exception", e)
            emptyList()
        }
    }

    /** One-call home feed: Apple editorial playlists + chart/fresh-drop/trending rows. */
    suspend fun homeFeed(limit: Int = 12): GatewayHomeFeed = withContext(Dispatchers.IO) {
        val empty = GatewayHomeFeed(storefront = "us", updatedAt = System.currentTimeMillis(), playlists = emptyList(), freshDrops = emptyList(), popularTracks = emptyList(), trendingNow = emptyList())
        try {
            val url = "$gatewayUrl/api/home?limit=${limit.coerceIn(1, 30)}"
            val body = client.newBuilder()
                .readTimeout(12, TimeUnit.SECONDS)
                .callTimeout(14, TimeUnit.SECONDS)
                .build()
                .newCall(Request.Builder().url(url).build())
                .execute().use { response ->
                    if (!response.isSuccessful) {
                        Log.w("CloudflareGateway", "Home feed failed: ${response.code}")
                        return@withContext empty
                    }
                    response.body?.string() ?: return@withContext empty
                }
            val json = JsonParser.parseString(body)?.asJsonObject ?: return@withContext empty
            fun trackList(key: String): List<SourceSearchResult> {
                val array = json.getAsJsonArray(key) ?: return emptyList()
                return parseGatewayTrackArray(array)
            }
            val playlists = parseGatewayPlaylistCards(json.getAsJsonArray("playlists"))
            GatewayHomeFeed(
                storefront = json.stringOrNull("storefront") ?: "us",
                updatedAt = System.currentTimeMillis(),
                playlists = playlists,
                freshDrops = trackList("freshDrops"),
                popularTracks = trackList("popularTracks"),
                trendingNow = trackList("trendingNow")
            )
        } catch (e: Exception) {
            Log.w("CloudflareGateway", "Home feed exception", e)
            empty
        }
    }

    /** Full track list for an Apple editorial playlist (e.g. from the home feed). */
    suspend fun applePlaylistTracks(
        playlistId: String,
        limit: Int = 100,
        storefront: String = "us",
    ): List<SourceSearchResult> = withContext(Dispatchers.IO) {
        try {
            val capped = limit.coerceIn(1, 300)
            val sf = storefront.trim().lowercase().ifBlank { "us" }
            val url =
                "$gatewayUrl/apple/playlist/${URLEncoder.encode(playlistId, "UTF-8")}/tracks" +
                    "?limit=$capped&storefront=${URLEncoder.encode(sf, "UTF-8")}"
            val body = client.newBuilder()
                .readTimeout(18, TimeUnit.SECONDS)
                .callTimeout(20, TimeUnit.SECONDS)
                .build()
                .newCall(Request.Builder().url(url).build())
                .execute().use { response ->
                    if (!response.isSuccessful) {
                        Log.w("CloudflareGateway", "Apple playlist tracks failed: ${response.code}")
                        return@withContext emptyList()
                    }
                    response.body?.string() ?: return@withContext emptyList()
                }
            val tracks = JsonParser.parseString(body)?.asJsonObject?.getAsJsonArray("tracks")
                ?: return@withContext emptyList()
            parseGatewayTrackArray(tracks)
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (e: Exception) {
            Log.w("CloudflareGateway", "Apple playlist tracks exception", e)
            emptyList()
        }
    }

    /** Full track list for a Spotify playlist. */
    suspend fun spotifyPlaylistTracks(playlistId: String, limit: Int = 1000): List<SourceSearchResult> = withContext(Dispatchers.IO) {
        try {
            val capped = limit.coerceIn(1, 2000)
            val url = "$gatewayUrl/spotify/playlist/${URLEncoder.encode(playlistId, "UTF-8")}/tracks?limit=$capped"
            val body = client.newBuilder()
                .readTimeout(35, TimeUnit.SECONDS)
                .callTimeout(40, TimeUnit.SECONDS)
                .build()
                .newCall(Request.Builder().url(url).build())
                .execute().use { response ->
                    if (!response.isSuccessful) {
                        Log.w("CloudflareGateway", "Spotify playlist tracks failed: ${response.code}")
                        return@withContext emptyList()
                    }
                    response.body?.string() ?: return@withContext emptyList()
                }
            val tracks = JsonParser.parseString(body)?.asJsonObject?.getAsJsonArray("tracks")
                ?: return@withContext emptyList()
            parseGatewayTrackArray(tracks)
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (e: Exception) {
            Log.w("CloudflareGateway", "Spotify playlist tracks exception", e)
            emptyList()
        }
    }

    private fun parseGatewayTrackArray(tracks: com.google.gson.JsonArray): List<SourceSearchResult> = buildList {
        for (item in tracks) {
            if (!item.isJsonObject) continue
            val track = item.asJsonObject
            val id = track.stringOrNull("id") ?: continue
            val title = track.stringOrNull("title") ?: continue
            val artist = track.stringOrNull("artist") ?: continue
            val sourceProvider = track.stringOrNull("provider")
                ?.trim()
                ?.lowercase()
                .orEmpty()
                .ifBlank {
                    when {
                        track.hasNonNull("deezer_id") -> "deezer"
                        track.hasNonNull("apple_id") -> "apple"
                        else -> ""
                    }
                }
            val gatewayId = if (sourceProvider.isNotBlank()) "$sourceProvider:$id" else id
            val sourceStatus = if (sourceProvider == "apple") {
                SearchItemStatus.METADATA_ONLY
            } else {
                SearchItemStatus.SOURCE_FOUND
            }
            add(
                SourceSearchResult(
                    id = gatewayId,
                    providerId = providerId,
                    title = title,
                    artist = artist,
                    album = track.stringOrNull("album"),
                    coverSeed = track.stringOrNull("artworkURL")
                        ?: track.stringOrNull("artworkUrl")
                        ?: "$title-$artist",
                    durationMs = track.longOrNull("durationMs")
                        ?: track.longOrNull("duration")?.times(1000L),
                    isrc = track.stringOrNull("isrc"),
                    status = sourceStatus,
                    qualityLabel = "Catalog metadata",
                    releaseDate = track.stringOrNull("releaseDate"),
                    discoveryKind = track.stringOrNull("discoveryKind")
                )
            )
        }
    }

    override suspend fun search(query: String): List<SourceSearchResult> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext emptyList()
        try {
            val url = "$gatewayUrl/api/search?q=${URLEncoder.encode(query, "UTF-8")}&limit=50"
            val request = Request.Builder().url(url).build()
            val body = client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.w("CloudflareGateway", "Search failed: ${response.code}")
                    return@withContext emptyList()
                }
                response.body?.string() ?: return@withContext emptyList()
            }
            val json = JsonParser.parseString(body)?.asJsonObject ?: return@withContext emptyList()
            val gatewayError = json.stringOrNull("error")
            if (gatewayError != null) {
                Log.w("CloudflareGateway", "Gateway error: $gatewayError")
                return@withContext emptyList()
            }
            cachedPlaylistQuery = query.trim()
            cachedPlaylists = parseGatewayPlaylistCards(
                json.get("playlists")?.takeIf { it.isJsonArray }?.asJsonArray
            ).map { it.toCanonicalPlaylist() }
            val resultsArray = json.getAsJsonArray("tracks")
                ?: json.getAsJsonArray("results")
                ?: return@withContext emptyList()

            val output = mutableListOf<SourceSearchResult>()
            for (i in 0 until resultsArray.size()) {
                try {
                    val element = resultsArray[i]
                    if (!element.isJsonObject) continue
                    val track = element.asJsonObject
                    val trackId = track.stringOrNull("id") ?: continue
                    val title = track.stringOrNull("title") ?: continue
                    val artist = track.stringOrNull("artist") ?: "Unknown Artist"
                    if (artist.isBlank()) continue
                    val sourceProvider = track.stringOrNull("provider")?.trim()?.lowercase().orEmpty()
                    val resolvedProvider = sourceProvider.ifBlank {
                        when {
                            track.hasNonNull("deezer_id") -> "deezer"
                            track.hasNonNull("qobuz_id") -> "qobuz"
                            track.hasNonNull("tidal_id") -> "tidal"
                            track.hasNonNull("amazon_id") -> "amazon"
                            track.hasNonNull("apple_id") -> "apple"
                            else -> ""
                        }
                    }

                    val artwork = track.stringOrNull("artworkURL") ?: track.stringOrNull("artworkUrl")
                    val coverSeed = artwork ?: title
                    val durationMs = track.longOrNull("durationMs")
                        ?: track.longOrNull("duration")?.times(1000L)
                    val gatewayId = if (resolvedProvider.isNotBlank()) {
                        "$resolvedProvider:$trackId"
                    } else {
                        trackId
                    }
                    val status = if (resolvedProvider == "apple") {
                        SearchItemStatus.METADATA_ONLY
                    } else {
                        SearchItemStatus.SOURCE_FOUND
                    }

                    val atmosMixAvailable = track.booleanOrNull("atmosMixAvailable") == true
                    val spatialFormat = track.stringOrNull("spatialFormat")
                    val isDolbyAtmos = containsAtmosCodecSignal(
                        track.stringOrNull("format"),
                        track.stringOrNull("codec"),
                        track.stringOrNull("mimeType"),
                        track.stringOrNull("audioQuality")
                    ) || spatialFormat == "DOLBY_ATMOS"
                    val isSony360 = spatialFormat == "SONY_360_REALITY_AUDIO" ||
                        com.audiophile.musicplayer.data.display.AudioQualityInfo.hasSony360RealityAudioEvidence(
                            track.stringOrNull("format"),
                            track.stringOrNull("codec"),
                            track.stringOrNull("mimeType"),
                            track.stringOrNull("audioQuality"),
                            track.stringOrNull("quality")
                        )
                    val isSpatialAudio = isDolbyAtmos || isSony360 ||
                        track.booleanOrNull("isSpatialAudio") == true
                    val isSurround = isDolbyAtmos || isSony360
                    val isHiRes = track.booleanOrNull("isHiRes") == true ||
                        containsHiResSignal(
                            track.stringOrNull("audioQuality"),
                            track.stringOrNull("quality"),
                            track.stringOrNull("format")
                        )
                    val featuredFromGateway = track.get("featuredArtists")
                        ?.takeIf { it.isJsonArray }
                        ?.asJsonArray
                        ?.mapNotNull { el ->
                            runCatching {
                                if (el.isJsonPrimitive) el.asString?.trim()?.takeIf { it.isNotBlank() } else null
                            }.getOrNull()
                        }
                        .orEmpty()
                    val featuredArtists = (
                        featuredFromGateway +
                            DisplayMetadataCleaner.extractFeaturedArtists(title, artist)
                        )
                        .distinctBy { it.lowercase() }
                    val primaryArtist = DisplayMetadataCleaner.splitArtistCredits(artist).primary
                        .ifBlank { artist }

                    val releaseDate = track.stringOrNull("releaseDate")
                        ?: track.stringOrNull("release_date")
                    val discoveryKind = track.stringOrNull("discoveryKind")
                        ?: track.stringOrNull("discovery_kind")
                    val qobuzId = track.stringOrNull("qobuz_id") ?: track.stringOrNull("qobuzId")
                    val tidalId = track.stringOrNull("tidal_id") ?: track.stringOrNull("tidalId")
                    val amazonId = track.stringOrNull("amazon_id") ?: track.stringOrNull("amazonId")
                    val deezerId = track.stringOrNull("deezer_id") ?: track.stringOrNull("deezerId")
                    val appleId = track.stringOrNull("apple_id") ?: track.stringOrNull("appleId")

                    output.add(
                        SourceSearchResult(
                            id = gatewayId,
                            providerId = providerId,
                            title = title,
                            artist = primaryArtist,
                            album = track.stringOrNull("album"),
                            coverSeed = coverSeed,
                            durationMs = durationMs,
                            isrc = track.stringOrNull("isrc"),
                            status = status,
                            qualityLabel = track.stringOrNull("audioQuality")
                                ?: track.stringOrNull("quality")
                                ?: "Stream",
                            isDolbyAtmos = isDolbyAtmos,
                            isSony360RealityAudio = isSony360,
                            isSpatialAudio = isSpatialAudio,
                            isSurround = isSurround,
                            isHiRes = isHiRes,
                            spatialEvidence = when {
                                isDolbyAtmos || isSony360 -> "verified"
                                else -> track.stringOrNull("spatialEvidence")
                                    ?: if (atmosMixAvailable) "catalog" else null
                            },
                            atmosMixAvailable = atmosMixAvailable || isDolbyAtmos,
                            featuredArtists = featuredArtists,
                            releaseDate = releaseDate,
                            discoveryKind = discoveryKind,
                            qobuzId = qobuzId,
                            tidalId = tidalId,
                            amazonId = amazonId,
                            deezerId = deezerId,
                            appleId = appleId
                        )
                    )
                } catch (e: IllegalStateException) {
                    Log.e("CloudflareGateway", "Failed to parse search result", e)
                } catch (e: ClassCastException) {
                    Log.e("CloudflareGateway", "Failed to parse search result", e)
                } catch (e: UnsupportedOperationException) {
                    Log.e("CloudflareGateway", "Failed to parse search result", e)
                }
            }
            output
        } catch (e: java.io.IOException) {
            Log.e("CloudflareGateway", "Search exception", e)
            emptyList()
        } catch (e: com.google.gson.JsonParseException) {
            Log.e("CloudflareGateway", "Search exception", e)
            emptyList()
        }
    }

    override suspend fun resolveStream(trackId: String): ResolvedStream? =
        (
            resolvePlayback(
                trackId,
                com.audiophile.musicplayer.data.source.playback.requestedAudioQualityFromPreference(
                    com.audiophile.musicplayer.data.source.external.SpotiFlacEndpoints.PREFERRED_STREAM_QUALITY
                )
            ) as? com.audiophile.musicplayer.data.source.playback.PlaybackSourceOutcome.Ready
        )?.stream

    override suspend fun resolvePlayback(
        trackId: String,
        requestedQuality: com.audiophile.musicplayer.data.source.playback.RequestedAudioQuality
    ): com.audiophile.musicplayer.data.source.playback.PlaybackSourceOutcome {
        // Auto is one gateway race (Atmos + 360 + FLAC). Explicit 360/Atmos
        // must ask for that mix; a miss falls through to stereo on the client.
        if (requestedQuality == com.audiophile.musicplayer.data.source.playback.RequestedAudioQuality.AUTO_SPATIAL) {
            return resolvePlaybackQuality(trackId, requestedQuality)
        }
        return com.audiophile.musicplayer.data.source.playback.resolveWithQualityFallback(requestedQuality) { quality ->
            resolvePlaybackQuality(trackId, quality)
        }
    }

    private suspend fun resolvePlaybackQuality(
        trackId: String,
        requestedQuality: com.audiophile.musicplayer.data.source.playback.RequestedAudioQuality
    ): com.audiophile.musicplayer.data.source.playback.PlaybackSourceOutcome = withContext(Dispatchers.IO) {
        val (providerHint, cleanTrackId) = splitGatewayTrackId(trackId)
        resolveCatalogGetStream(trackId, cleanTrackId, providerHint, requestedQuality)?.let { return@withContext it }
        postGatewayDownload(cleanTrackId, providerHint, requestedQuality)?.let { return@withContext it }
        val missing = if (requestedQuality == com.audiophile.musicplayer.data.source.playback.RequestedAudioQuality.ATMOS) {
            com.audiophile.musicplayer.data.source.playback.PlaybackSourceErrorCode.ATMOS_UNAVAILABLE
        } else {
            com.audiophile.musicplayer.data.source.playback.PlaybackSourceErrorCode.SOURCE_OFFLINE
        }
        com.audiophile.musicplayer.data.source.playback.PlaybackSourceOutcome.Failed(
            com.audiophile.musicplayer.data.source.playback.PlaybackSourceFailure.of(
                code = missing,
                sourceLabel = providerName,
                adapterId = "catalog"
            )
        )
    }

    private fun postGatewayDownload(
        cleanTrackId: String,
        providerHint: String?,
        requestedQuality: com.audiophile.musicplayer.data.source.playback.RequestedAudioQuality
    ): com.audiophile.musicplayer.data.source.playback.PlaybackSourceOutcome? {
        val payload = buildGatewayResolvePayload(
            cleanTrackId,
            providerHint,
            requestedQuality.toGatewayQuality()
        )
        val request = Request.Builder()
            .url("$gatewayUrl/api/dl")
            .header("User-Agent", "VANTA/1.0")
            .post(payload.toRequestBody(jsonMediaType))
            .build()
        return try {
            fastPostClient.newCall(request).execute().use { response ->
                val body = response.body?.string()
                val json = body?.let(::parseGatewayObject)
                val errorCode = json?.get("error")?.takeIf { it.isJsonPrimitive }?.asString
                if (!response.isSuccessful || errorCode != null) {
                    Log.w(
                        "CloudflareGateway",
                        "POST /api/dl failed http=${response.code} error=$errorCode id=$cleanTrackId"
                    )
                    // Community POST is one path. GET /stream is the working catalog
                    // resolver — a failed /api/dl must not abort playback.
                    return@use null
                }
                val streamUrl = json?.stringOrNull("streamUrl") ?: json?.stringOrNull("url")
                if (streamUrl.isNullOrBlank() || json == null) {
                    Log.w("CloudflareGateway", "POST /api/dl missing streamUrl id=$cleanTrackId")
                    return@use null
                }
                Log.d("VANTA_PLAY_CLICK", "gateway_api_dl_ok id=$cleanTrackId")
                readyStreamOutcome(json, streamUrl, providerHint, requestedQuality)
            }
        } catch (e: java.io.IOException) {
            Log.w("CloudflareGateway", "POST /api/dl io id=$cleanTrackId reason='${e.message}'")
            null
        }
    }

    private fun parseGatewayObject(body: String): com.google.gson.JsonObject? {
        val element = try {
            JsonParser.parseString(body)
        } catch (_: com.google.gson.JsonParseException) {
            return null
        }
        return if (element != null && element.isJsonObject) element.asJsonObject else null
    }

    private fun com.google.gson.JsonObject.stringOrNull(key: String): String? {
        val element = get(key) ?: return null
        if (element.isJsonNull || !element.isJsonPrimitive) return null
        return try {
            element.asString?.trim()?.takeIf { it.isNotEmpty() }
        } catch (_: IllegalStateException) {
            null
        } catch (_: ClassCastException) {
            null
        } catch (_: UnsupportedOperationException) {
            null
        }
    }

    private fun com.google.gson.JsonObject.booleanOrNull(key: String): Boolean? {
        val element = get(key) ?: return null
        if (element.isJsonNull || !element.isJsonPrimitive) return null
        return try {
            element.asBoolean
        } catch (_: IllegalStateException) {
            null
        } catch (_: ClassCastException) {
            null
        } catch (_: UnsupportedOperationException) {
            null
        }
    }

    private fun com.google.gson.JsonObject.hasNonNull(key: String): Boolean {
        val element = get(key) ?: return false
        return !element.isJsonNull
    }

    private fun com.google.gson.JsonObject.intOrNull(key: String): Int? {
        val element = get(key) ?: return null
        if (element.isJsonNull || !element.isJsonPrimitive) return null
        return try {
            element.asInt
        } catch (_: NumberFormatException) {
            null
        } catch (_: IllegalStateException) {
            null
        } catch (_: ClassCastException) {
            null
        } catch (_: UnsupportedOperationException) {
            null
        }
    }

    private fun com.google.gson.JsonObject.longOrNull(key: String): Long? {
        val element = get(key) ?: return null
        if (element.isJsonNull || !element.isJsonPrimitive) return null
        return try {
            element.asLong
        } catch (_: NumberFormatException) {
            null
        } catch (_: IllegalStateException) {
            null
        } catch (_: ClassCastException) {
            null
        } catch (_: UnsupportedOperationException) {
            null
        }
    }

    private fun resolveCatalogGetStream(
        trackId: String,
        cleanTrackId: String,
        providerHint: String?,
        requestedQuality: com.audiophile.musicplayer.data.source.playback.RequestedAudioQuality
    ): com.audiophile.musicplayer.data.source.playback.PlaybackSourceOutcome? {
        val encodedId = URLEncoder.encode(cleanTrackId, "UTF-8").replace("+", "%20")
        val quality = requestedQuality.toGatewayQuality()
        val providerQuery = providerHint?.takeIf { it.isNotBlank() }?.let { "&provider=$it" }.orEmpty()
        val request = Request.Builder()
            .url("$gatewayUrl/stream/$encodedId?quality=$quality$providerQuery")
            .header("User-Agent", "VANTA/1.0")
            .build()
        return try {
            playbackClient.newCall(request).execute().use { response ->
                val body = response.body?.string()
                val parsed = GatewayStreamResponseParser.locationOrBody(
                    code = response.code,
                    location = response.header("Location"),
                    body = body
                ) ?: run {
                    Log.d("CloudflareGateway", "GET /stream failed http=${response.code} id=$cleanTrackId")
                    val errorJson = body?.let(::parseGatewayObject)
                    return@use classifiedGatewayFailure(
                        requestedQuality = requestedQuality,
                        httpCode = response.code,
                        errorCode = errorJson?.stringOrNull("error"),
                        message = errorJson?.stringOrNull("message")
                    )
                }
                val trimmed = parsed.trim().trim('"')
                if (trimmed.startsWith("http://", ignoreCase = true) || trimmed.startsWith("https://", ignoreCase = true)) {
                    if (GatewayStreamResponseParser.streamUrlLooksUnplayableWithoutDrm(trimmed, null, false)) {
                        Log.w("CloudflareGateway", "GET /stream locked CDN id=$cleanTrackId")
                        val code = if (requestedQuality ==
                            com.audiophile.musicplayer.data.source.playback.RequestedAudioQuality.ATMOS
                        ) {
                            com.audiophile.musicplayer.data.source.playback.PlaybackSourceErrorCode.ATMOS_UNAVAILABLE
                        } else {
                            com.audiophile.musicplayer.data.source.playback.PlaybackSourceErrorCode.QUALITY_UNAVAILABLE
                        }
                        return@use com.audiophile.musicplayer.data.source.playback.PlaybackSourceOutcome.Failed(
                            com.audiophile.musicplayer.data.source.playback.PlaybackSourceFailure.of(
                                code = code,
                                sourceLabel = providerName,
                                adapterId = "catalog",
                                detail = "Encrypted stream without a license"
                            )
                        )
                    }
                    Log.d("VANTA_PLAY_CLICK", "gateway_get_stream_ok id=$trackId")
                    return@use readyStreamOutcome(
                        com.google.gson.JsonObject(),
                        trimmed,
                        providerHint,
                        requestedQuality
                    )
                }
                val json = parseGatewayObject(trimmed) ?: return@use null
                val streamUrl = json.stringOrNull("streamUrl") ?: json.stringOrNull("url")
                if (streamUrl.isNullOrBlank()) return@use null
                Log.d("VANTA_PLAY_CLICK", "gateway_get_stream_ok id=$trackId")
                readyStreamOutcome(json, streamUrl, providerHint, requestedQuality)
            }
        } catch (e: java.io.IOException) {
            Log.d("CloudflareGateway", "GET /stream io id=$cleanTrackId reason='${e.message}'")
            null
        }
    }

    private fun readyStreamOutcome(
        payload: com.google.gson.JsonObject,
        streamUrl: String,
        providerHint: String?,
        requestedQuality: com.audiophile.musicplayer.data.source.playback.RequestedAudioQuality
    ): com.audiophile.musicplayer.data.source.playback.PlaybackSourceOutcome {
        val expiresAt = payload.longOrNull("expiresAt")?.let { value ->
            if (value in 1 until 100_000_000_000L) value * 1000L else value
        }
        val providerIdentity = gatewayStreamProviderIdentity(
            routeProviderId = providerId,
            providerHint = providerHint,
            responseProviderId = payload.stringOrNull("provider")
        )
        val format = payload.stringOrNull("format") ?: payload.stringOrNull("codec")
        val mimeType = payload.stringOrNull("mimeType")
            ?: com.audiophile.musicplayer.playback.PlaybackMediaType.inferFromUrl(streamUrl)
        val quality = payload.stringOrNull("quality")
            ?: payload.stringOrNull("qualityLabel")
            ?: payload.stringOrNull("audioQuality")
        val atmos = containsAtmosCodecSignal(format, payload.stringOrNull("codec"), mimeType, quality)
        val spatialFormat = payload.stringOrNull("spatialFormat")
        val sony360 = spatialFormat == "SONY_360_REALITY_AUDIO" ||
            com.audiophile.musicplayer.data.display.AudioQualityInfo.hasSony360RealityAudioEvidence(
                format, mimeType, quality, payload.stringOrNull("codec")
            )
        val qualityLabel = if (sony360 &&
            !com.audiophile.musicplayer.data.display.AudioQualityInfo.hasSony360RealityAudioEvidence(quality)
        ) "360 Reality Audio" else quality
        val stream = com.audiophile.musicplayer.data.source.playback.PlaybackStreamNormalizer.normalize(
            ResolvedStream(
                streamUrl = streamUrl,
                bitrateKbps = CloudLibraryHelpers.inferStreamBitrateKbps(
                    reportedKbps = payload.intOrNull("bitrateKbps"),
                    quality = qualityLabel,
                    mimeType = mimeType,
                    format = format
                ),
                mimeType = mimeType,
                expiresAt = SourceRegistry.resolveMinExpiryMs(
                    streamUrl,
                    expiresAt,
                    providerIdentity.routeProviderId
                ),
                requestHeaders = CdnPlaybackHeaders.forUrl(streamUrl),
                qualityLabel = qualityLabel,
                format = format,
                isSpatialAudio = atmos || sony360,
                isDolbyAtmos = atmos,
                isSony360RealityAudio = sony360,
                isSurround = atmos || sony360,
                bitDepth = payload.intOrNull("bitDepth") ?: payload.intOrNull("bit_depth"),
                sampleRateHz = parseSampleRateHz(payload),
                channelCount = payload.intOrNull("channelCount") ?: payload.intOrNull("channels"),
                providerId = providerIdentity.routeProviderId,
                fulfillmentProviderId = providerIdentity.fulfillmentProviderId,
                sourceLabel = providerName,
                drm = GatewayStreamResponseParser.drmConfiguration(payload)
            ),
            sourceLabel = providerName,
            providerId = providerIdentity.routeProviderId
        )
        if (GatewayStreamResponseParser.streamUrlLooksUnplayableWithoutDrm(
                stream.streamUrl,
                stream.format ?: format,
                stream.drm != null
            )
        ) {
            Log.w("CloudflareGateway", "skipping locked CDN stream id hint=$providerHint")
            val code = if (requestedQuality == com.audiophile.musicplayer.data.source.playback.RequestedAudioQuality.ATMOS) {
                com.audiophile.musicplayer.data.source.playback.PlaybackSourceErrorCode.ATMOS_UNAVAILABLE
            } else {
                com.audiophile.musicplayer.data.source.playback.PlaybackSourceErrorCode.QUALITY_UNAVAILABLE
            }
            return com.audiophile.musicplayer.data.source.playback.PlaybackSourceOutcome.Failed(
                com.audiophile.musicplayer.data.source.playback.PlaybackSourceFailure.of(
                    code = code,
                    sourceLabel = providerName,
                    adapterId = "catalog"
                )
            )
        }
        return com.audiophile.musicplayer.data.source.playback.PlaybackSourceOutcome.Ready(
            stream = stream,
            qualityShortfall = com.audiophile.musicplayer.data.source.playback.PlaybackStreamNormalizer.qualityShortfall(
                stream,
                requestedQuality
            )
        )
    }

    private fun splitGatewayTrackId(trackId: String): Pair<String?, String> {
        val separator = trackId.indexOf(':')
        if (separator <= 0 || separator == trackId.lastIndex) return null to trackId
        return trackId.substring(0, separator).lowercase() to trackId.substring(separator + 1)
    }

    /**
     * Gateway adapters never ask the end user for a Tidal token.
     * An Atmos miss is ATMOS_UNAVAILABLE, not AUTH_REQUIRED.
     * Returning null lets the caller try the next HTTP path.
     */
    private fun classifiedGatewayFailure(
        requestedQuality: com.audiophile.musicplayer.data.source.playback.RequestedAudioQuality,
        httpCode: Int,
        errorCode: String?,
        message: String?
    ): com.audiophile.musicplayer.data.source.playback.PlaybackSourceOutcome? {
        if (requestedQuality == com.audiophile.musicplayer.data.source.playback.RequestedAudioQuality.ATMOS &&
            httpCode != 401 && httpCode != 403 && errorCode != "AUTH_REQUIRED") {
            return com.audiophile.musicplayer.data.source.playback.PlaybackSourceOutcome.Failed(
                com.audiophile.musicplayer.data.source.playback.PlaybackSourceFailure.of(
                    code = com.audiophile.musicplayer.data.source.playback.PlaybackSourceErrorCode.ATMOS_UNAVAILABLE,
                    sourceLabel = providerName,
                    adapterId = "catalog"
                )
            )
        }
        if (requestedQuality == com.audiophile.musicplayer.data.source.playback.RequestedAudioQuality.SONY_360 &&
            httpCode != 401 && httpCode != 403 && errorCode != "AUTH_REQUIRED") {
            return com.audiophile.musicplayer.data.source.playback.PlaybackSourceOutcome.Failed(
                com.audiophile.musicplayer.data.source.playback.PlaybackSourceFailure.of(
                    code = com.audiophile.musicplayer.data.source.playback.PlaybackSourceErrorCode.QUALITY_UNAVAILABLE,
                    sourceLabel = providerName,
                    adapterId = "catalog"
                )
            )
        }
        val classified = com.audiophile.musicplayer.data.source.playback.GatewayStreamErrorClassifier.classify(
            httpCode,
            errorCode,
            message
        )
        if (
            classified == com.audiophile.musicplayer.data.source.playback.PlaybackSourceErrorCode.AUTH_REQUIRED ||
            classified == com.audiophile.musicplayer.data.source.playback.PlaybackSourceErrorCode.QUALITY_UNAVAILABLE
        ) {
            return com.audiophile.musicplayer.data.source.playback.PlaybackSourceOutcome.Failed(
                com.audiophile.musicplayer.data.source.playback.PlaybackSourceFailure.of(
                    code = classified,
                    sourceLabel = providerName,
                    adapterId = "catalog"
                )
            )
        }
        return null
    }

    private fun containsAtmosCodecSignal(vararg values: String?): Boolean =
        com.audiophile.musicplayer.data.display.AudioQualityInfo.hasAtmosCodecEvidence(*values)

    private fun parseSampleRateHz(json: com.google.gson.JsonObject): Int? {
        val raw = json.intOrNull("sampleRateHz")
            ?: json.intOrNull("sample_rate_hz")
            ?: json.intOrNull("samplingRate")
            ?: json.intOrNull("sampling_rate")
            ?: return null
        return when {
            raw in 1..999 -> raw * 1000
            raw > 0 -> raw
            else -> null
        }
    }

    private fun containsHiResSignal(vararg values: String?): Boolean =
        values.any { value ->
            val text = value?.lowercase().orEmpty()
            "hi-res" in text || "hires" in text || "24-bit" in text || "24 bit" in text ||
                "24/" in text || "96" in text || "192" in text || "88" in text || "176" in text
        }

}

/** Builds valid /api/dl JSON for both auto and provider-specific resolution. */
internal fun buildGatewayResolvePayload(
    trackId: String,
    providerHint: String?,
    quality: String = "24"
): String {
    fun escape(value: String): String = value.replace("\\", "\\\\").replace("\"", "\\\"")
    val safeQuality = when (quality.trim().lowercase()) {
        "atmos", "dolby_atmos", "eac3", "eac3_joc" -> "atmos"
        "360", "360ra", "sony360", "sony_360", "360_reality_audio" -> "360"
        "auto" -> "auto"
        "16" -> "16"
        else -> "24"
    }
    return buildString {
        append("{\"id\":\"")
        append(escape(trackId))
        append("\",\"quality\":\"")
        append(escape(safeQuality))
        append("\"")
        if (!providerHint.isNullOrBlank()) {
            append(",\"provider\":\"")
            append(escape(providerHint))
            append("\"")
        }
        append("}")
    }
}

internal data class GatewayStreamProviderIdentity(
    val routeProviderId: String,
    val fulfillmentProviderId: String?
)

internal fun gatewayStreamProviderIdentity(
    routeProviderId: String,
    providerHint: String?,
    responseProviderId: String?
): GatewayStreamProviderIdentity = GatewayStreamProviderIdentity(
    routeProviderId = routeProviderId,
    fulfillmentProviderId = responseProviderId
        ?.trim()
        ?.takeIf { it.isNotBlank() }
        ?: providerHint?.trim()?.takeIf { it.isNotBlank() }
)
