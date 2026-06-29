package com.audiophile.musicplayer.data.dj

import com.audiophile.musicplayer.data.local.DeviceMediaMetadataReader
import com.audiophile.musicplayer.data.local.entities.Album
import com.audiophile.musicplayer.data.local.entities.UnifiedTrackWithSources
import com.audiophile.musicplayer.data.metadata.MetadataResolver
import com.audiophile.musicplayer.data.repository.TrackRepository

/** Resolves release year from catalog API, album DB, MediaStore, and album-name text. */
class TrackReleaseYearResolver(
    private val trackRepository: TrackRepository,
    private val deviceMediaMetadataReader: DeviceMediaMetadataReader? = null,
    private val metadataResolver: MetadataResolver? = null
) {
    private val yearRegex = Regex("""\b(19|20)\d{2}\b""")
    private var albumsByKey: Map<Pair<String, String>, Album>? = null
    private var albumsById: Map<Long, Album>? = null
    private val catalogYearByTrackId = mutableMapOf<Long, Int?>()

    suspend fun ensureLoaded() {
        if (albumsByKey != null) return
        val albums = trackRepository.getAllAlbumsWithTracks().map { it.album }
        albumsByKey = albums.associateBy {
            it.album_name.trim().lowercase() to it.artist_name.trim().lowercase()
        }
        albumsById = albums.associateBy { it.albumId }
    }

    suspend fun resolve(track: UnifiedTrackWithSources): Int? {
        val catalog = resolveCatalogYear(track)
        if (catalog != null) return catalog
        return resolveLocalIncludingTitle(track)
    }

    /**
     * Era station filter — prefers catalog year (corrects tribute/karaoke with fake file tags),
     * then album DB, MediaStore, album-name text. Never guesses from track title alone.
     */
    suspend fun resolveForEraFilter(track: UnifiedTrackWithSources): Int? {
        val catalog = resolveCatalogYear(track)
        if (catalog != null) return catalog
        return resolveLocalForEraFilter(track)
    }

    suspend fun resolveCatalogYear(track: UnifiedTrackWithSources): Int? {
        val trackId = track.track.trackId
        if (catalogYearByTrackId.containsKey(trackId)) return catalogYearByTrackId[trackId]
        val resolver = metadataResolver
        if (resolver == null) {
            catalogYearByTrackId[trackId] = null
            return null
        }
        val year = runCatching {
            resolver.resolveRawMetadata(
                title = track.track.title,
                artist = track.track.artist,
                album = track.track.albumName,
                isrc = track.track.isrc
            )?.releaseYear?.takeIf { isPlausibleYear(it) }
        }.getOrNull()
        catalogYearByTrackId[trackId] = year
        return year
    }

    private suspend fun resolveLocalIncludingTitle(track: UnifiedTrackWithSources): Int? {
        ensureLoaded()
        val local = resolveLocalForEraFilter(track)
        if (local != null) return local
        return parseYearFromText(track.track.title)
    }

    private suspend fun resolveLocalForEraFilter(track: UnifiedTrackWithSources): Int? {
        ensureLoaded()
        val unified = track.track
        unified.albumId?.let { id ->
            albumsById?.get(id)?.release_year?.let { year ->
                if (isPlausibleYear(year)) return year
            }
        }
        val albumName = unified.albumName
        if (!albumName.isNullOrBlank()) {
            val key = albumName.trim().lowercase() to unified.artist.trim().lowercase()
            albumsByKey?.get(key)?.release_year?.let { year ->
                if (isPlausibleYear(year)) return year
            }
            parseYearFromText(albumName)?.let { return it }
        }
        unified.localLibraryId?.let { mediaId ->
            deviceMediaMetadataReader?.yearByMediaId(mediaId)?.let { return it }
        }
        return null
    }

    fun parseYearFromText(text: String): Int? {
        val match = yearRegex.find(text) ?: return null
        val year = match.value.toIntOrNull()
        return year?.takeIf { isPlausibleYear(it) }
    }

    fun decadeTextHints(decadeStart: Int, decadeEnd: Int): List<String> {
        val hints = mutableSetOf<String>()
        val shortDecade = when {
            decadeStart >= 2000 -> "${(decadeStart % 100).toString().padStart(2, '0')}s"
            else -> "${decadeStart % 100 / 10}0s"
        }
        hints.add(shortDecade)
        hints.add("${decadeStart}s")
        if (decadeEnd >= decadeStart) {
            for (y in decadeStart..decadeEnd step 5) {
                hints.add(y.toString())
            }
        }
        return hints.toList()
    }

    private fun isPlausibleYear(year: Int): Boolean = year in 1950..2030
}
