package com.audiophile.musicplayer.data.importer

import android.util.Log
import com.audiophile.musicplayer.common.VantaLogger
import com.audiophile.musicplayer.data.metadata.apple.AppleMusicMetadataProvider
import com.audiophile.musicplayer.data.metadata.deezer.DeezerApiClient
import com.audiophile.musicplayer.data.metadata.deezer.toEnhancedMetadata
import com.audiophile.musicplayer.data.metadata.itunes.ITunesSearchApiClient
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URI
import java.net.URLEncoder
import java.util.Locale
import java.util.concurrent.TimeUnit

data class PlatformLinkMetadata(
    val title: String,
    val artist: String?,
    val album: String? = null,
    val artworkUrl: String? = null,
    val isrc: String? = null,
    val durationMs: Long? = null,
    val platform: String? = null,
    val externalId: String? = null,
    val matchReason: String
)

data class PlatformLinkCollection(
    val title: String,
    val creator: String?,
    val artworkUrl: String?,
    val platform: String,
    val type: String,
    val externalId: String?,
    val tracks: List<PlatformLinkMetadata>
)

class PlatformLinkResolver(
    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(6, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .build(),
    private val itunesClient: ITunesSearchApiClient = ITunesSearchApiClient(),
    private val deezerClient: DeezerApiClient = DeezerApiClient(),
    private val appleMusicProvider: AppleMusicMetadataProvider? = null
) {
    suspend fun resolveCollection(url: String): PlatformLinkCollection? = withContext(Dispatchers.IO) {
        val platform = platformName(url)
        val isApple = platform == "Apple Music"

        // Apple-specific: try catalog API first, then public iTunes lookup, then Songlink.
        if (isApple) {
            appleMusicProvider?.resolveCollectionUrl(url)?.let { collection ->
                return@withContext PlatformLinkCollection(
                    title = collection.title,
                    creator = collection.artist,
                    artworkUrl = collection.artworkUrl,
                    platform = "Apple Music",
                    type = collection.type,
                    externalId = collection.id,
                    tracks = collection.tracks.map { track ->
                        PlatformLinkMetadata(
                            title = track.title,
                            artist = track.artist,
                            album = track.album,
                            artworkUrl = track.artworkUrl,
                            isrc = track.isrc,
                            durationMs = track.durationMs,
                            platform = "Apple Music",
                            externalId = track.externalIds["appleMusicCatalogId"],
                            matchReason = "Apple Music ${collection.type} track"
                        )
                    }
                )
            }

            val uri = runCatching { URI(url) }.getOrNull() ?: return@withContext null
            val isAlbum = uri.path.orEmpty().contains("/album/", ignoreCase = true)
            if (isAlbum) {
                val id = numericPathId(url)
                if (id != null) {
                    val results = runCatching { itunesClient.lookupById(id).results }.getOrDefault(emptyList())
                        .filter { !it.trackName.isNullOrBlank() && !it.artistName.isNullOrBlank() }
                    if (results.isNotEmpty()) {
                        val first = results.first()
                        return@withContext PlatformLinkCollection(
                            title = first.collectionName ?: "Apple Music Album",
                            creator = first.collectionArtistName ?: first.artistName,
                            artworkUrl = first.artworkUrl(),
                            platform = "Apple Music",
                            type = "album",
                            externalId = id.toString(),
                            tracks = results.map { track ->
                                PlatformLinkMetadata(
                                    title = track.trackName.orEmpty(),
                                    artist = track.artistName,
                                    album = track.collectionName,
                                    artworkUrl = track.artworkUrl(),
                                    isrc = track.isrc,
                                    durationMs = track.trackTimeMillis,
                                    platform = "Apple Music",
                                    externalId = track.trackId.toString(),
                                    matchReason = "Apple Music album track via iTunes"
                                )
                            }
                        )
                    }
                }
            }
        }

        // Cross-platform Songlink fallback for any supported platform.
        resolveCollectionViaSonglink(url, platform)?.let { return@withContext it }
    }

    suspend fun resolve(parsed: ParsedPlaylistLine): PlatformLinkMetadata? = withContext(Dispatchers.IO) {
        val url = parsed.sourceUrl?.trim()?.takeIf { it.startsWith("http", ignoreCase = true) } ?: return@withContext null
        // Apple catalog IDs are authoritative. Resolve them before Songlink so a
        // valid Apple URL can never be mapped to an unrelated cross-platform entity.
        if (platformName(url) == "Apple Music") {
            resolveAppleViaCatalogApi(url)?.let { return@withContext it }
            resolveAppleViaItunes(url)?.let { return@withContext it }
            // Songlink fallback — only accepted if the Apple entity ID matches.
            val appleCatalogId = numericPathId(url)
            if (appleCatalogId != null) {
                resolveViaSonglinkForApple(url, appleCatalogId)?.let { return@withContext it }
            } else {
                // Non-numeric Apple catalog IDs (e.g. pl.u-...) need Songlink
                // without ID validation. Accept if the Songlink entity is Apple Music.
                resolveViaSonglink(url)?.takeIf { songlinkResult ->
                    songlinkResult.platform == "iTunes" || songlinkResult.platform == "Apple Music" ||
                        songlinkResult.platform == null
                }?.let { return@withContext it }
            }
            return@withContext fallbackFromParsedUrl(parsed)
        }
        resolveViaSonglink(url)?.let { return@withContext it }
        resolveDeezerViaApi(url)?.let { return@withContext it }
        fallbackFromParsedUrl(parsed)
    }

    private suspend fun resolveAppleViaCatalogApi(url: String): PlatformLinkMetadata? {
        val platform = platformName(url)
        if (platform != "Apple Music") return null
        val provider = appleMusicProvider ?: return null
        val metadata = provider.resolveUrl(url) ?: return null
        return PlatformLinkMetadata(
            title = metadata.title,
            artist = metadata.artist,
            album = metadata.album,
            artworkUrl = metadata.artworkUrl,
            isrc = metadata.isrc,
            durationMs = metadata.durationMs,
            platform = "Apple Music",
            externalId = metadata.externalIds["appleMusicCatalogId"],
            matchReason = "Resolved from Apple Music Catalog API"
        )
    }

    private fun resolveCollectionViaSonglink(url: String, platform: String?): PlatformLinkCollection? {
        val uri = runCatching { URI(url) }.getOrNull() ?: return null
        val isPlaylist = uri.path.orEmpty().contains("/playlist/", ignoreCase = true)
        val isAlbum = uri.path.orEmpty().contains("/album/", ignoreCase = true) ||
            uri.path.orEmpty().contains("/collection/", ignoreCase = true)
        val isSonglink = platform == "Songlink" || platform == null
        val collectionType = when {
            isPlaylist -> "playlist"
            isAlbum -> "album"
            isSonglink -> "album" // song.link/album.link format
            else -> return null
        }

        val encoded = URLEncoder.encode(url, "UTF-8")
        val request = Request.Builder()
            .url("https://api.song.link/v1-alpha.1/links?url=$encoded&userCountry=US")
            .header("Accept", "application/json")
            .header("User-Agent", "VANTA/1.0 Android")
            .build()
        return runCatching {
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                val body = response.body?.string()?.takeIf { it.isNotBlank() } ?: return null
                val root = JsonParser.parseString(body).asJsonObject
                val entities = root.getAsJsonObject("entitiesByUniqueId") ?: return null
                val title = root.stringValue("title") ?: return null
                val linkType = root.stringValue("linkType") ?: collectionType

                val tracks = entities.entrySet().mapNotNull { (key, value) ->
                    val entity = value.asJsonObject ?: return@mapNotNull null
                    if (!key.contains("::")) return@mapNotNull null
                    val entityPlatform = key.substringBefore("::")
                    val entityType = key.substringAfter("::").substringBefore("::")
                    if (entityType != "SONG") return@mapNotNull null
                    val trackTitle = entity.stringValue("title") ?: return@mapNotNull null
                    val artist = entity.stringValue("artistName") ?: return@mapNotNull null
                    PlatformLinkMetadata(
                        title = trackTitle,
                        artist = artist,
                        album = entity.stringValue("albumName"),
                        artworkUrl = entity.stringValue("thumbnailUrl"),
                        isrc = entity.stringValue("isrc"),
                        durationMs = entity.stringValue("durationMs")?.toLongOrNull(),
                        platform = entityPlatform,
                        externalId = entity.stringValue("id"),
                        matchReason = "$entityPlatform track via Songlink"
                    )
                }.distinctBy { "${it.title.lowercase()}|${it.artist?.lowercase().orEmpty()}" }

                if (tracks.isEmpty()) return null
                val resolvedPlatform = root.stringValue("platform") ?: platform ?: "Songlink"
                PlatformLinkCollection(
                    title = title,
                    creator = root.stringValue("artistName"),
                    artworkUrl = tracks.firstOrNull()?.artworkUrl,
                    platform = resolvedPlatform,
                    type = linkType,
                    externalId = tracks.firstOrNull()?.externalId,
                    tracks = tracks
                )
            }
        }.onFailure {
            Log.d("VANTA_IMPORT_LINK", "songlink_collection_failed host='${VantaLogger.urlHost(url)}' error='${it.message}'")
        }.getOrNull()
    }

    private fun resolveAppleCollectionViaSonglink(url: String): PlatformLinkCollection? {
        val platform = platformName(url)
        if (platform != "Apple Music") return null
        return resolveCollectionViaSonglink(url, platform)
    }

    private fun resolveViaSonglink(url: String): PlatformLinkMetadata? {
        val encoded = URLEncoder.encode(url, "UTF-8")
        val request = Request.Builder()
            .url("https://api.song.link/v1-alpha.1/links?url=$encoded&userCountry=US")
            .header("Accept", "application/json")
            .header("User-Agent", "VANTA/1.0 Android")
            .build()
        return runCatching {
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                val body = response.body?.string()?.takeIf { it.isNotBlank() } ?: return null
                val root = JsonParser.parseString(body).asJsonObject
                val entityId = root.stringValue("entityUniqueId")
                val entities = root.getAsJsonObject("entitiesByUniqueId") ?: return null
                val entity = entityId?.let { entities.getAsJsonObject(it) }
                    ?: entities.entrySet().firstOrNull()?.value?.asJsonObject
                    ?: return null
                val title = entity.stringValue("title") ?: return null
                PlatformLinkMetadata(
                    title = title,
                    artist = entity.stringValue("artistName"),
                    album = entity.stringValue("albumName"),
                    artworkUrl = entity.stringValue("thumbnailUrl"),
                    platform = entity.stringValue("apiProvider") ?: platformName(url),
                    externalId = entity.stringValue("id") ?: entityId,
                    matchReason = "Resolved from platform link"
                )
            }
        }.onFailure {
            Log.d("VANTA_IMPORT_LINK", "songlink_failed host='${VantaLogger.urlHost(url)}' error='${it.message}'")
        }.getOrNull()
    }

    private suspend fun resolveAppleViaItunes(url: String): PlatformLinkMetadata? {
        val platform = platformName(url)
        if (platform != "Apple Music") return null
        val id = numericPathId(url) ?: return null
        return runCatching {
            // First try with entity=song; some catalog IDs only surface without the entity filter.
            val trackWithEntity = itunesClient.lookupById(id).results
                .firstOrNull { it.trackName?.isNotBlank() == true }
            val track = trackWithEntity
                ?: itunesClient.lookupByIdNoEntity(id).results
                    .firstOrNull { it.trackName?.isNotBlank() == true }
                ?: return null
            val title = track.trackName?.takeIf { it.isNotBlank() } ?: return null
            Log.d("VANTA_IMPORT_LINK", "itunes_lookup_ok id=$id trackId=${track.trackId} title='$title'")
            PlatformLinkMetadata(
                title = title,
                artist = track.artistName,
                album = track.collectionName,
                artworkUrl = track.artworkUrl(),
                isrc = track.isrc,
                durationMs = track.trackTimeMillis,
                platform = "Apple Music",
                externalId = track.trackId.takeIf { it > 0 }?.toString() ?: id.toString(),
                matchReason = "Resolved from Apple Music link"
            )
        }.onFailure {
            Log.d("VANTA_IMPORT_LINK", "itunes_lookup_failed id=$id error='${it.message}'")
        }.getOrNull()
    }

    /**
     * Validates a Songlink result against the Apple catalog ID embedded in [url].
     * Returns null if the Songlink entity does not carry a matching Apple catalog ID,
     * preventing cross-platform mismatches (e.g. a different song's Spotify entry).
     */
    private fun resolveViaSonglinkForApple(url: String, appleCatalogId: Long): PlatformLinkMetadata? {
        val encoded = URLEncoder.encode(url, "UTF-8")
        val request = Request.Builder()
            .url("https://api.song.link/v1-alpha.1/links?url=$encoded&userCountry=US")
            .header("Accept", "application/json")
            .header("User-Agent", "VANTA/1.0 Android")
            .build()
        return runCatching {
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                val body = response.body?.string()?.takeIf { it.isNotBlank() } ?: return null
                val root = JsonParser.parseString(body).asJsonObject
                // Verify the Apple Music entity in Songlink carries our catalog ID.
                val entities = root.getAsJsonObject("entitiesByUniqueId") ?: return null
                val appleEntity = entities.entrySet()
                    .firstOrNull { it.key.startsWith("ITUNES_SONG::") || it.key.startsWith("APPLE_MUSIC::") || it.key.startsWith("ITUNES::") }
                    ?.value?.asJsonObject
                val appleEntityId = appleEntity?.stringValue("id")?.toLongOrNull()
                if (appleEntityId != null && appleEntityId != appleCatalogId) {
                    Log.w("VANTA_IMPORT_LINK", "songlink_id_mismatch expected=$appleCatalogId got=$appleEntityId — discarding")
                    return null
                }
                val entityUniqueId = root.stringValue("entityUniqueId")
                val entity = entityUniqueId?.let { entities.getAsJsonObject(it) }
                    ?: appleEntity
                    ?: entities.entrySet().firstOrNull()?.value?.asJsonObject
                    ?: return null
                val title = entity.stringValue("title") ?: return null
                PlatformLinkMetadata(
                    title = title,
                    artist = entity.stringValue("artistName"),
                    album = entity.stringValue("albumName"),
                    artworkUrl = entity.stringValue("thumbnailUrl"),
                    platform = "Apple Music",
                    externalId = appleCatalogId.toString(),
                    matchReason = "Resolved from Songlink (Apple-validated)"
                )
            }
        }.onFailure {
            Log.d("VANTA_IMPORT_LINK", "songlink_apple_failed host='${VantaLogger.urlHost(url)}' error='${it.message}'")
        }.getOrNull()
    }

    private suspend fun resolveDeezerViaApi(url: String): PlatformLinkMetadata? {
        val platform = platformName(url)
        if (platform != "Deezer") return null
        val trackId = deezerTrackId(url) ?: return null
        return runCatching {
            val metadata = deezerClient.getTrack(trackId).toEnhancedMetadata("Resolved from Deezer link")
                ?: return null
            PlatformLinkMetadata(
                title = metadata.title,
                artist = metadata.artist,
                album = metadata.album,
                artworkUrl = metadata.artworkUrl,
                isrc = metadata.isrc,
                durationMs = metadata.durationMs,
                platform = "Deezer",
                externalId = metadata.externalIds["deezer"] ?: trackId,
                matchReason = "Resolved from Deezer link"
            )
        }.onFailure {
            Log.d("VANTA_IMPORT_LINK", "deezer_lookup_failed id=$trackId error='${it.message}'")
        }.getOrNull()
    }

    private fun fallbackFromParsedUrl(parsed: ParsedPlaylistLine): PlatformLinkMetadata? {
        val title = parsed.title?.takeIf { it.isNotBlank() } ?: return null
        return PlatformLinkMetadata(
            title = title,
            artist = parsed.artist,
            album = parsed.album,
            platform = parsed.sourcePlatform,
            externalId = parsed.sourceId,
            matchReason = "Parsed from platform link"
        )
    }

    private fun numericPathId(url: String): Long? {
        val uri = runCatching { URI(url) }.getOrNull() ?: return null
        queryParam(uri, "i")?.toLongOrNull()?.let { return it }
        return uri.path.orEmpty()
            .split('/')
            .lastOrNull { segment -> segment.isNotBlank() && segment.all { it.isDigit() } }
            ?.toLongOrNull()
    }

    private fun deezerTrackId(url: String): String? {
        val uri = runCatching { URI(url) }.getOrNull() ?: return null
        val segments = uri.path.orEmpty().split('/').filter { it.isNotBlank() }
        val trackIndex = segments.indexOfFirst { it.equals("track", ignoreCase = true) }
        return segments.getOrNull(trackIndex + 1)
            ?.takeIf { segment -> segment.all { it.isDigit() } }
    }

    private fun queryParam(uri: URI, name: String): String? {
        return uri.rawQuery
            ?.split('&')
            ?.firstNotNullOfOrNull { pair ->
                val key = pair.substringBefore('=')
                val value = pair.substringAfter('=', "")
                value.takeIf { key == name && it.isNotBlank() }
            }
    }

    private fun platformName(url: String): String? {
        val host = runCatching { URI(url).host.orEmpty().removePrefix("www.").lowercase(Locale.US) }.getOrNull() ?: return null
        return when {
            "music.apple.com" in host || "itunes.apple.com" in host -> "Apple Music"
            "open.spotify.com" in host || "spotify.link" in host -> "Spotify"
            "tidal.com" in host -> "Tidal"
            "qobuz.com" in host -> "Qobuz"
            "deezer.com" in host -> "Deezer"
            "music.amazon." in host || host == "amazon.com" || host == "www.amazon.com" -> "Amazon Music"
            "youtube.com" in host || "youtu.be" in host || "music.youtube.com" in host -> "YouTube"
            "soundcloud.com" in host -> "SoundCloud"
            "song.link" in host || "album.link" in host || "odesli.co" in host -> "Songlink"
            else -> host
        }
    }

    private fun JsonObject.stringValue(name: String): String? {
        val value = get(name) ?: return null
        return if (value.isJsonPrimitive) value.asString.trim().takeIf { it.isNotBlank() } else null
    }
}
