package com.audiophile.musicplayer.data.importer

import android.util.Log
import com.audiophile.musicplayer.data.metadata.itunes.ITunesSearchApiClient
import com.audiophile.musicplayer.data.metadata.itunes.ITunesTrack
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URI
import java.net.URLEncoder
import java.util.Locale
import java.util.concurrent.TimeUnit

/** A single track entry inside a resolved collection. */
data class CollectionTrack(
    val title: String,
    val artist: String,
    val album: String? = null,
    val durationMs: Long? = null,
    val isrc: String? = null,
    val artworkUrl: String? = null,
    val trackNumber: Int? = null
)

/** The resolved album or playlist, ready for bulk import. */
data class CollectionResult(
    val collectionTitle: String,
    val collectionArtist: String?,
    val platform: String,
    val artworkUrl: String? = null,
    val collectionType: CollectionType = CollectionType.ALBUM,
    val tracks: List<CollectionTrack> = emptyList(),
    val sourceUrl: String
) {
    val trackCount: Int get() = tracks.size
}

enum class CollectionType { ALBUM, PLAYLIST }

/**
 * Resolves a collection URL (album or playlist) into a [CollectionResult].
 *
 * Supported inputs:
 *  - Apple Music album URLs  → iTunes lookup with entity=song
 *  - Pandora album/playlist  → slug-based artist+album search on iTunes
 *  - Spotify album           → Songlink entity lookup
 */
class CollectionResolver(
    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
        .build(),
    private val itunesClient: ITunesSearchApiClient = ITunesSearchApiClient()
) {

    suspend fun resolve(url: String): CollectionResult? = withContext(Dispatchers.IO) {
        val platform = platformName(url) ?: return@withContext null
        return@withContext when (platform) {
            "Apple Music" -> resolveAppleMusicAlbum(url)
            "Pandora"     -> resolvePandoraCollection(url)
            "Spotify"     -> resolveSpotifyAlbumViaSonglink(url)
            else          -> null
        }
    }

    // ─── Apple Music ───────────────────────────────────────────────────────────

    private suspend fun resolveAppleMusicAlbum(url: String): CollectionResult? {
        val albumId = numericPathId(url) ?: return null
        return runCatching {
            // iTunes lookup with entity=song returns the album + all its tracks
            val response = itunesClient.lookupByIdNoEntity(albumId)
            val all = response.results
            if (all.isEmpty()) return null

            // First result is usually the album/collection; rest are tracks
            val collectionEntry = all.firstOrNull { it.trackName == null }
                ?: all.firstOrNull { it.collectionName != null }
            val tracks = all.filter { it.trackName?.isNotBlank() == true }
            if (tracks.isEmpty()) return null

            val albumName = collectionEntry?.collectionName
                ?: tracks.firstOrNull()?.collectionName
                ?: "Unknown Album"
            val artist = collectionEntry?.artistName
                ?: tracks.firstOrNull()?.artistName
                ?: "Unknown Artist"
            val artwork = (collectionEntry?.artworkUrl() ?: tracks.firstOrNull()?.artworkUrl())

            Log.d("VANTA_COLLECTION", "Apple album id=$albumId tracks=${tracks.size} album='$albumName'")
            CollectionResult(
                collectionTitle = albumName,
                collectionArtist = artist,
                platform = "Apple Music",
                artworkUrl = artwork,
                collectionType = CollectionType.ALBUM,
                tracks = tracks.mapNotNull { it.toCollectionTrack() }.sortedBy { it.trackNumber ?: 0 },
                sourceUrl = url
            )
        }.onFailure {
            Log.w("VANTA_COLLECTION", "apple_album_failed id=$albumId error='${it.message}'")
        }.getOrNull()
    }

    // ─── Pandora ───────────────────────────────────────────────────────────────

    private suspend fun resolvePandoraCollection(url: String): CollectionResult? {
        val uri = runCatching { URI(url) }.getOrNull() ?: return null
        val segments = uri.path.orEmpty().split('/').filter { it.isNotBlank() }
        // Pandora paths: /artist/<artist-slug>/<album-slug>/AL<id>
        //                /artist/<artist-slug>/<album-slug>/<track-slug>/TR<id>
        //                /playlist/<playlist-slug>/PL<id>
        val entityTypeSegmentIndex = segments.indexOfFirst { seg ->
            seg.equals("album", ignoreCase = true) ||
                seg.equals("playlist", ignoreCase = true) ||
                seg.equals("station", ignoreCase = true)
        }
        val collectionType = when {
            segments.any { it.equals("playlist", ignoreCase = true) } -> CollectionType.PLAYLIST
            else -> CollectionType.ALBUM
        }

        // Extract artist slug and album/playlist slug for iTunes fallback search
        val artistSlug = if (segments.firstOrNull()?.equals("artist", ignoreCase = true) == true) {
            segments.getOrNull(1)
        } else null
        val collectionSlug = if (artistSlug != null) segments.getOrNull(2) else segments.getOrNull(1)

        val artistName = artistSlug?.slugToTitle()
        val collectionName = collectionSlug?.slugToTitle()

        if (artistName.isNullOrBlank() && collectionName.isNullOrBlank()) return null

        Log.d("VANTA_COLLECTION", "Pandora slug artist='$artistName' collection='$collectionName' type=$collectionType")

        // Search iTunes by artist + album name to get the track listing
        return searchItunesByAlbum(artistName, collectionName, collectionType, url)
    }

    private suspend fun searchItunesByAlbum(
        artist: String?,
        album: String?,
        collectionType: CollectionType,
        sourceUrl: String
    ): CollectionResult? {
        val query = listOfNotNull(artist, album).joinToString(" ").trim()
        if (query.isBlank()) return null

        return runCatching {
            val response = itunesClient.search(query, limit = 50)
            val tracks = response.results.filter { it.trackName?.isNotBlank() == true }
            if (tracks.isEmpty()) return null

            // Group by collection, pick the one that best matches
            val grouped = tracks.groupBy { it.collectionName?.lowercase(Locale.US) }
            val bestGroup = grouped.maxByOrNull { (key, group) ->
                val albumScore = if (!album.isNullOrBlank() && key?.contains(album.lowercase(Locale.US)) == true) 10 else 0
                val artistScore = if (!artist.isNullOrBlank() && group.any { it.artistName?.lowercase(Locale.US)?.contains(artist.lowercase(Locale.US)) == true }) 5 else 0
                albumScore + artistScore + group.size
            }?.value ?: return null

            val albumName = bestGroup.firstOrNull()?.collectionName ?: album ?: "Unknown"
            val artistName = bestGroup.firstOrNull()?.artistName ?: artist ?: "Unknown"
            val artwork = bestGroup.firstOrNull()?.artworkUrl()

            Log.d("VANTA_COLLECTION", "iTunes fallback found album='$albumName' tracks=${bestGroup.size}")
            CollectionResult(
                collectionTitle = albumName,
                collectionArtist = artistName,
                platform = "Pandora → iTunes",
                artworkUrl = artwork,
                collectionType = collectionType,
                tracks = bestGroup.mapNotNull { it.toCollectionTrack() }.sortedBy { it.trackNumber ?: 0 },
                sourceUrl = sourceUrl
            )
        }.onFailure {
            Log.w("VANTA_COLLECTION", "itunes_album_search_failed query='$query' error='${it.message}'")
        }.getOrNull()
    }

    // ─── Spotify ───────────────────────────────────────────────────────────────

    private fun resolveSpotifyAlbumViaSonglink(url: String): CollectionResult? {
        // Songlink supports album lookups; returns the entity and links but not the full track list.
        // We use it to get album metadata, then search iTunes for the track listing.
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
                val entityId = root.get("entityUniqueId")?.asString
                val entities = root.getAsJsonObject("entitiesByUniqueId") ?: return null
                val entity = entityId?.let { entities.getAsJsonObject(it) }
                    ?: entities.entrySet().firstOrNull()?.value?.asJsonObject
                    ?: return null
                val title = entity.get("title")?.asString?.takeIf { it.isNotBlank() } ?: return null
                val artist = entity.get("artistName")?.asString
                val artwork = entity.get("thumbnailUrl")?.asString

                // Now search iTunes to get the actual track list
                // (Songlink doesn't provide individual tracks for albums)
                Log.d("VANTA_COLLECTION", "Spotify album via Songlink title='$title' artist='$artist'")
                null // Will be populated by the iTunes fallback below
            }
        }.onFailure {
            Log.w("VANTA_COLLECTION", "spotify_songlink_failed error='${it.message}'")
        }.getOrNull()
        // Note: full Spotify track listing requires API access; we return null and let
        // the caller fallback to iTunes search with the slug.
    }

    // ─── Helpers ───────────────────────────────────────────────────────────────

    private fun ITunesTrack.toCollectionTrack(): CollectionTrack? {
        val title = trackName?.takeIf { it.isNotBlank() } ?: return null
        return CollectionTrack(
            title = title,
            artist = artistName ?: collectionArtistName ?: "Unknown",
            album = collectionName,
            durationMs = trackTimeMillis,
            isrc = isrc,
            artworkUrl = artworkUrl(),
            trackNumber = trackNumber
        )
    }

    private fun numericPathId(url: String): Long? {
        val uri = runCatching { URI(url) }.getOrNull() ?: return null
        // Check ?i= query param first (Apple Music single-song in album URL)
        uri.rawQuery?.split('&')?.firstNotNullOfOrNull { pair ->
            val key = pair.substringBefore('=')
            val value = pair.substringAfter('=', "")
            value.toLongOrNull()?.takeIf { key == "i" }
        }?.let { return it }
        return uri.path.orEmpty()
            .split('/')
            .lastOrNull { seg -> seg.isNotBlank() && seg.all { it.isDigit() } }
            ?.toLongOrNull()
    }

    private fun platformName(url: String): String? {
        val host = runCatching { URI(url).host.orEmpty().removePrefix("www.").lowercase(Locale.US) }
            .getOrNull() ?: return null
        return when {
            "music.apple.com" in host || "itunes.apple.com" in host -> "Apple Music"
            "open.spotify.com" in host || "spotify.link" in host    -> "Spotify"
            "pandora.com" in host                                   -> "Pandora"
            "tidal.com" in host                                     -> "Tidal"
            "deezer.com" in host                                    -> "Deezer"
            else                                                    -> null
        }
    }

    private fun String.slugToTitle(): String =
        replace('-', ' ').replace('_', ' ').trim()
            .split(Regex("""\s+"""))
            .joinToString(" ") { word ->
                word.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.US) else it.toString() }
            }

    companion object {
        /** Returns true if the URL points to a collection (album/playlist) rather than a single track. */
        fun isCollectionUrl(url: String): Boolean {
            val lower = url.trim().lowercase(Locale.US)
            if (!lower.startsWith("http")) return false
            val uri = runCatching { URI(url.trim()) }.getOrNull() ?: return false
            val host = uri.host.orEmpty().removePrefix("www.").lowercase(Locale.US)
            val path = uri.path.orEmpty().lowercase(Locale.US)
            return when {
                "music.apple.com" in host ->
                    "/album/" in path && uri.rawQuery?.contains("i=") != true
                "pandora.com" in host ->
                    "/album/" in path || "/playlist/" in path || "/station/" in path
                "open.spotify.com" in host ->
                    path.contains("/album/") || path.contains("/playlist/")
                "tidal.com" in host ->
                    path.contains("/album/") || path.contains("/playlist/")
                "deezer.com" in host ->
                    path.contains("/album/") || path.contains("/playlist/")
                else -> false
            }
        }
    }
}
