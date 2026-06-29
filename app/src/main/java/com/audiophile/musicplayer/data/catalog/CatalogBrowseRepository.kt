package com.audiophile.musicplayer.data.catalog

import android.util.Log
import com.audiophile.musicplayer.data.canonical.CanonicalTrack
import com.audiophile.musicplayer.data.canonical.CanonicalAlbum
import com.audiophile.musicplayer.data.canonical.CanonicalArtist
import com.audiophile.musicplayer.data.metadata.deezer.DeezerApiClient
import com.audiophile.musicplayer.data.metadata.deezer.DeezerTrack
import com.audiophile.musicplayer.data.metadata.deezer.artistDisplay
import com.audiophile.musicplayer.data.metadata.deezer.upgradeDeezerArtwork
import com.audiophile.musicplayer.data.source.SearchItemStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class ArtistCatalog(
    val artist: CanonicalArtist,
    val tracks: List<CanonicalTrack>,
    val albums: List<CanonicalAlbum>
)

data class AlbumCatalog(
    val album: CanonicalAlbum,
    val tracks: List<CanonicalTrack>
)

class CatalogBrowseRepository(
    private val deezerClient: DeezerApiClient = DeezerApiClient()
) {
    suspend fun browseAlbum(albumName: String, artistName: String, limit: Int = 100): AlbumCatalog = withContext(Dispatchers.IO) {
        val cleanAlbum = albumName.trim()
        val cleanArtist = artistName.trim()
        if (cleanAlbum.isBlank()) return@withContext AlbumCatalog(CanonicalAlbum(), emptyList())
        val results = runCatching {
            deezerClient.searchTracks("album:\"$cleanAlbum\" artist:\"$cleanArtist\"", limit = limit)
        }.recoverCatching {
            deezerClient.searchTracks("$cleanAlbum $cleanArtist", limit = limit)
        }.onFailure {
            Log.d("VANTA_ALBUM_CATALOG", "album_lookup_failed album='$cleanAlbum' artist='$cleanArtist' error='${it.message}'")
        }.getOrNull()?.data.orEmpty()
        val exact = results.filter { track ->
            track.album?.title?.trim()?.equals(cleanAlbum, ignoreCase = true) == true &&
                (cleanArtist.isBlank() || track.artist?.name?.trim()?.equals(cleanArtist, ignoreCase = true) == true)
        }
        val tracks = exact.mapNotNull { it.toCatalogTrack("album") }
            .distinctBy { "${it.discNumber ?: 1}|${it.trackNumber ?: 0}|${it.title.trim().lowercase()}" }
            .sortedWith(compareBy<CanonicalTrack> { it.discNumber ?: 1 }.thenBy { it.trackNumber ?: Int.MAX_VALUE })
        val first = tracks.firstOrNull()
        AlbumCatalog(
            album = CanonicalAlbum(
                title = cleanAlbum,
                artist = cleanArtist.ifBlank { first?.artist.orEmpty() },
                artworkUrl = first?.artworkUrl,
                releaseYear = first?.releaseYear,
                genre = first?.genre,
                trackCount = tracks.size.takeIf { it > 0 },
                explicit = tracks.any { it.explicit == true }.takeIf { tracks.isNotEmpty() }
            ),
            tracks = tracks
        )
    }

    suspend fun browseArtist(artistName: String, limit: Int = 50): ArtistCatalog = withContext(Dispatchers.IO) {
        val cleanArtist = artistName.trim()
        if (cleanArtist.isBlank()) {
            return@withContext ArtistCatalog(CanonicalArtist(), emptyList(), emptyList())
        }

        val results = runCatching {
            deezerClient.searchTracks("artist:\"$cleanArtist\"", limit = limit)
        }.recoverCatching {
            deezerClient.searchTracks(cleanArtist, limit = limit)
        }.onFailure {
            Log.d("VANTA_ARTIST_CATALOG", "artist_lookup_failed artist='$cleanArtist' error='${it.message}'")
        }.getOrNull()?.data.orEmpty()

        val exactTracks = results.filter {
            it.artist?.name?.trim()?.equals(cleanArtist, ignoreCase = true) == true
        }
        val canonicalTracks = exactTracks
            .mapNotNull { it.toCatalogTrack("artist") }
            .distinctBy { "${it.title.trim().lowercase()}|${it.artist.trim().lowercase()}" }
            .take(limit)
        val artistArtwork = exactTracks.firstNotNullOfOrNull {
            upgradeDeezerArtwork(it.artist?.pictureXl ?: it.artist?.picture)
        }
        val albums = canonicalTracks
            .filter { !it.album.isNullOrBlank() }
            .distinctBy { (it.album ?: "").trim().lowercase() }
            .map { track ->
                CanonicalAlbum(
                    title = track.album.orEmpty(),
                    artist = cleanArtist,
                    artworkUrl = track.artworkUrl,
                    releaseYear = track.releaseYear,
                    genre = track.genre,
                    explicit = track.explicit
                )
            }

        ArtistCatalog(
            artist = CanonicalArtist(name = cleanArtist, artworkUrl = artistArtwork),
            tracks = canonicalTracks,
            albums = albums
        )
    }

    suspend fun browseCategory(category: String, limit: Int = 50): List<CanonicalTrack> = withContext(Dispatchers.IO) {
        val cleanCategory = category.trim()
        if (cleanCategory.isBlank()) return@withContext emptyList()

        val chartTracks = categoryChartId(cleanCategory)
            ?.let { chartId ->
                runCatching { deezerClient.getChartTracks(chartId = chartId, limit = limit).data }
                    .onFailure { Log.d("VANTA_BROWSE", "deezer_chart_failed category='$cleanCategory' chartId=$chartId error='${it.message}'") }
                    .getOrNull()
            }
            .orEmpty()

        val fallbackTracks = if (chartTracks.isEmpty()) {
            categoryCatalogQueries(cleanCategory)
                .flatMap { query ->
                    runCatching { deezerClient.searchTracks(query, limit = 25).data }
                        .onFailure { Log.d("VANTA_BROWSE", "deezer_catalog_search_failed category='$cleanCategory' query='$query' error='${it.message}'") }
                        .getOrNull()
                        .orEmpty()
                }
        } else {
            emptyList()
        }

        (chartTracks + fallbackTracks)
            .asSequence()
            .mapNotNull { it.toCatalogTrack(cleanCategory) }
            .filter { categoryResultAllowed(cleanCategory, it) }
            .distinctBy { "${it.title.trim().lowercase()}|${it.artist.trim().lowercase()}" }
            .take(limit)
            .toList()
    }

    private fun categoryChartId(category: String): Int? = when (category.trim().lowercase()) {
        "hits", "top hits" -> 0
        "pop" -> 132
        "dance" -> 113
        "hip-hop", "hip hop", "hip-hop/rap", "rap" -> 116
        "rock" -> 152
        "country" -> 84
        "alternative" -> 85
        else -> null
    }

    private fun categoryCatalogQueries(category: String): List<String> = when (category.trim().lowercase()) {
        "pop" -> listOf("chart pop", "top pop")
        "alternative" -> listOf("alternative rock", "indie alternative")
        "country" -> listOf("country hits", "country music")
        "hits" -> listOf("global hits", "top songs")
        "hip-hop", "hip hop" -> listOf("hip hop hits", "rap hits")
        "dance" -> listOf("dance hits", "electronic dance")
        "rock" -> listOf("rock hits", "modern rock")
        "chill" -> listOf("chill", "lofi")
        "sleep" -> listOf("sleep ambient", "ambient")
        "focus" -> listOf("focus instrumental", "study beats")
        "feel good" -> listOf("feel good hits")
        "party" -> listOf("party hits")
        else -> listOf(category)
    }

    private fun DeezerTrack.toCatalogTrack(category: String): CanonicalTrack? {
        val cleanTitle = titleShort?.takeIf { it.isNotBlank() }
            ?: title?.takeIf { it.isNotBlank() }
            ?: return null
        val cleanArtist = artistDisplay().takeIf { it.isNotBlank() } ?: return null
        return CanonicalTrack(
            title = cleanTitle,
            artist = cleanArtist,
            album = album?.title?.takeIf { it.isNotBlank() },
            isrc = isrc?.trim()?.uppercase()?.takeIf { it.isNotBlank() },
            durationMs = duration?.takeIf { it > 0 }?.times(1000L),
            genre = category,
            trackNumber = trackPosition,
            discNumber = diskNumber,
            releaseYear = album?.releaseDate?.take(4)?.toIntOrNull(),
            artworkUrl = upgradeDeezerArtwork(album?.coverXl ?: album?.cover),
            explicit = explicitLyrics,
            sourceStatus = SearchItemStatus.METADATA_ONLY,
            sourceProviderId = "deezer"
        )
    }

    private fun categoryResultAllowed(category: String, track: CanonicalTrack): Boolean {
        val title = track.title.lowercase()
        val artist = track.artist.lowercase()
        val album = track.album.orEmpty().lowercase()
        val haystack = "$title $artist $album"
        if ("karaoke" in haystack || "reaction" in haystack || "tutorial" in haystack) return false
        if (category.equals("pop", ignoreCase = true)) {
            if (title.startsWith("pop ") || title.contains(" pop dat ") || title.contains("pop dat")) return false
        }
        return true
    }
}
