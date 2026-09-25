package com.audiophile.musicplayer.data.catalog

import com.audiophile.musicplayer.data.canonical.CanonicalTrack
import com.audiophile.musicplayer.data.canonical.CanonicalAlbum
import com.audiophile.musicplayer.data.canonical.CanonicalArtist
import com.audiophile.musicplayer.data.canonical.CanonicalPlaylist
import com.audiophile.musicplayer.data.metadata.deezer.DeezerApiClient
import com.audiophile.musicplayer.data.metadata.deezer.DeezerPlaylist
import com.audiophile.musicplayer.data.metadata.deezer.DeezerTrack
import com.audiophile.musicplayer.data.metadata.deezer.artistDisplay
import com.audiophile.musicplayer.data.metadata.deezer.upgradeDeezerArtwork
import com.audiophile.musicplayer.data.source.SearchItemStatus
import kotlinx.coroutines.async
import kotlinx.coroutines.sync.withPermit
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

data class PlaylistCatalog(
    val playlist: CanonicalPlaylist,
    val tracks: List<CanonicalTrack>
)

class CatalogBrowseRepository(
    private val deezerClient: DeezerApiClient = DeezerApiClient()
) {
    suspend fun browseAlbum(albumName: String, artistName: String, limit: Int = 100): AlbumCatalog = withContext(Dispatchers.IO) {
        val cleanAlbum = albumName.trim()
        val cleanArtist = artistName.trim()
        if (cleanAlbum.isBlank()) return@withContext AlbumCatalog(CanonicalAlbum(), emptyList())
        // Resolve the album identity first, then fetch its full ordered track list.
        // Track search is not an album listing and can silently omit most of a release.
        val album = catalogAttempt {
            deezerClient.searchAlbums("$cleanArtist $cleanAlbum").data.firstOrNull {
                it.id > 0 && catalogNameMatches(it.title, cleanAlbum) &&
                    (cleanArtist.isBlank() || catalogNameMatches(it.artist?.name, cleanArtist))
            }
        }
        val albumTracks = album?.let { selected ->
            catalogAttempt { deezerClient.albumTracks(selected.id, limit).data }?.mapIndexed { index, track ->
                track.copy(album = selected, trackPosition = track.trackPosition ?: index + 1)
            }
        }.orEmpty()
        val exact = albumTracks.ifEmpty {
            catalogAttempt { deezerClient.searchTracks("$cleanArtist $cleanAlbum", limit).data }.orEmpty()
                .filter { track ->
                    catalogNameMatches(track.album?.title, cleanAlbum) &&
                        (cleanArtist.isBlank() || catalogNameMatches(track.artist?.name, cleanArtist))
                }
        }
        val tracks = exact.mapNotNull { it.toCatalogTrack() }
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

    /** An exact artist lookup also works when general song search returns no artist hits. */
    suspend fun findArtistCatalog(query: String, limit: Int = 100, includeAlbums: Boolean = true): ArtistCatalog? = withContext(Dispatchers.IO) {
        val name = query.trim().replace(Regex("^artist[: ]+", RegexOption.IGNORE_CASE), "").trim()
        if (name.isBlank()) return@withContext null
        // Prefer a real artist identity so the discography loads (artistAlbums +
        // artistTopTracks) instead of degrading to a handful of search hits.
        // The artist-search endpoint can return nothing useful while track/album
        // search still carries the matching artist object, so resolve from hits.
        val artist = catalogAttempt { deezerClient.searchArtists(name).data }
            ?.firstOrNull { it.id > 0 && catalogNameMatches(it.name, name) }
            ?: catalogAttempt { deezerClient.searchTracks(name, limit = 25).data }
                ?.firstNotNullOfOrNull { track ->
                    val hit = track.artist
                    if (hit == null || hit.id <= 0 || !catalogNameMatches(hit.name, name)) null else hit
                }
            ?: catalogAttempt { deezerClient.searchAlbums(name, limit = 25).data }
                ?.firstNotNullOfOrNull { album ->
                    val hit = album.artist
                    if (hit == null || hit.id <= 0 || !catalogNameMatches(hit.name, name)) null else hit
                }
            ?: return@withContext null
        val releases = async {
            if (includeAlbums) loadArtistReleases(artist.id, artist.name.orEmpty()) else emptyList()
        }
        val tracks = catalogAttempt { deezerClient.artistTopTracks(artist.id, limit).data }.orEmpty()
            .filter { it.artist?.id == artist.id || catalogNameMatches(it.artist?.name, name) }
            .mapNotNull { it.toCatalogTrack() }
        ArtistCatalog(
            artist = CanonicalArtist(name = artist.name.orEmpty(),
                artworkUrl = upgradeDeezerArtwork(artist.pictureXl ?: artist.picture)),
            tracks = com.audiophile.musicplayer.search.SearchPresentation.songs(tracks),
            albums = releases.await()
        )
    }

    private suspend fun loadArtistReleases(id: Long, name: String): List<CanonicalAlbum> {
        val releases = linkedMapOf<Long, CanonicalAlbum>()
        var index = 0
        // Bound requests and retain completed pages if a later page fails.
        repeat(20) {
            val page = catalogAttempt { deezerClient.artistAlbums(id, limit = 100, index = index) }
                ?: return releases.values.toList()
            var added = false
            page.data.filter { it.id > 0 && !it.title.isNullOrBlank() }.forEach { album ->
                if (album.id !in releases) {
                    added = true
                    releases[album.id] = CanonicalAlbum(
                        title = album.title.orEmpty(), artist = name,
                        artworkUrl = upgradeDeezerArtwork(album.coverXl ?: album.cover),
                        releaseYear = album.releaseDate?.take(4)?.toIntOrNull()
                    )
                }
            }
            index += page.data.size
            if (!added || page.data.isEmpty() ||
                (page.next.isNullOrBlank() && (page.total == null || index >= page.total))) {
                return releases.values.toList()
            }
        }
        return releases.values.toList()
    }

    suspend fun browseArtist(artistName: String, limit: Int = 100, includeAlbums: Boolean = true): ArtistCatalog = withContext(Dispatchers.IO) {
        val cleanArtist = artistName.trim()
        findArtistCatalog(cleanArtist, limit, includeAlbums)?.let { catalog ->
            if (catalog.tracks.isNotEmpty() || catalog.albums.isNotEmpty()) return@withContext catalog
        }
        val exactTracks = if (cleanArtist.isBlank()) emptyList() else
            catalogAttempt { deezerClient.searchTracks(cleanArtist, limit).data }.orEmpty()
                .filter { catalogNameMatches(it.artist?.name, cleanArtist) }
        val tracks = com.audiophile.musicplayer.search.SearchPresentation.songs(exactTracks.mapNotNull { it.toCatalogTrack() })
        ArtistCatalog(
            artist = CanonicalArtist(name = cleanArtist, artworkUrl = exactTracks.firstNotNullOfOrNull {
                upgradeDeezerArtwork(it.artist?.pictureXl ?: it.artist?.picture)
            }),
            tracks = tracks,
            albums = com.audiophile.musicplayer.search.SearchPresentation.albums(emptyList(), tracks, null)
        )
    }

    suspend fun searchPlaylists(query: String, limit: Int = 20): List<CanonicalPlaylist> = withContext(Dispatchers.IO) {
        val clean = query.trim()
        if (clean.length < 2) return@withContext emptyList()
        catalogAttempt { deezerClient.searchPlaylists(clean, limit).data }.orEmpty()
            .filter { it.id > 0 && !it.title.isNullOrBlank() }
            .filter { playlist ->
                val title = playlist.title.orEmpty()
                title.contains(clean, ignoreCase = true) ||
                    playlist.user?.name.orEmpty().contains(clean, ignoreCase = true)
            }
            .distinctBy { it.id }
            .take(limit)
            .map { it.toCanonicalPlaylist() }
    }

    suspend fun browsePlaylist(playlistId: Long, seed: CanonicalPlaylist? = null, limit: Int = 200): PlaylistCatalog =
        withContext(Dispatchers.IO) {
            val tracks = linkedMapOf<Long, CanonicalTrack>()
            var index = 0
            repeat(8) {
                val page = catalogAttempt { deezerClient.playlistTracks(playlistId, limit = 100, index = index) }
                    ?: return@withContext PlaylistCatalog(
                        playlist = seed ?: CanonicalPlaylist(id = playlistId.toString(), source = "deezer"),
                        tracks = tracks.values.toList()
                    )
                var added = false
                page.data.forEach { track ->
                    val mapped = track.toCatalogTrack() ?: return@forEach
                    val key = track.id.takeIf { it > 0 } ?: mapped.title.hashCode().toLong()
                    if (key !in tracks) {
                        added = true
                        tracks[key] = mapped
                    }
                }
                index += page.data.size
                if (!added || page.data.isEmpty() || tracks.size >= limit ||
                    (page.next.isNullOrBlank() && (page.total == null || index >= page.total))
                ) {
                    return@withContext PlaylistCatalog(
                        playlist = (seed ?: CanonicalPlaylist(id = playlistId.toString(), source = "deezer"))
                            .copy(trackCount = tracks.size.takeIf { it > 0 } ?: seed?.trackCount),
                        tracks = tracks.values.take(limit)
                    )
                }
            }
            PlaylistCatalog(
                playlist = (seed ?: CanonicalPlaylist(id = playlistId.toString(), source = "deezer"))
                    .copy(trackCount = tracks.size.takeIf { it > 0 } ?: seed?.trackCount),
                tracks = tracks.values.take(limit)
            )
        }

    suspend fun browseCategory(category: String, limit: Int = 50): List<CanonicalTrack> = withContext(Dispatchers.IO) {
        val clean = category.trim()
        if (clean.isBlank()) return@withContext emptyList()
        val definition = BrowseCatalog.find(clean)
        // Deezer currently returns the same global artists/charts for multiple genre IDs.
        // Use explicit editorial artist seeds rather than silently labeling that global feed.
        val batches: List<List<CanonicalTrack>> = if (!definition?.artists.isNullOrEmpty()) {
            kotlinx.coroutines.coroutineScope {
                val gate = kotlinx.coroutines.sync.Semaphore(3)
                definition.artists.map { artist ->
                    async {
                        gate.withPermit { catalogAttempt { browseArtist(artist, 10, includeAlbums = false).tracks }.orEmpty() }
                    }
                }.map { it.await() }
            }
        } else if (clean.equals("Hits", true)) {
            listOf(catalogAttempt { deezerClient.getChartTracks(limit = limit).data.mapNotNull { it.toCatalogTrack() } }.orEmpty())
        } else {
            val names = listOf(definition?.title ?: clean) + definition?.aliases.orEmpty()
            val playlists = names.flatMap { name ->
                catalogAttempt { deezerClient.searchPlaylists(name).data }.orEmpty()
            }.filter { BrowseCatalog.collectionMatches(clean, it.title.orEmpty()) }
                .filter { it.id > 0 }.distinctBy { it.id }.take(3)
            val collections = playlists.map { playlist ->
                catalogAttempt { deezerClient.playlistTracks(playlist.id, limit).data.mapNotNull { it.toCatalogTrack() } }.orEmpty()
            }
            if (collections.flatten().any { categoryResultAllowed(clean, it) }) collections
            else kotlinx.coroutines.coroutineScope {
                val gate = kotlinx.coroutines.sync.Semaphore(3)
                BrowseCatalog.fallbackQueries(clean).map { query ->
                    async {
                        gate.withPermit {
                            catalogAttempt { deezerClient.searchTracks(query, 5).data.mapNotNull { it.toCatalogTrack() } }.orEmpty()
                        }
                    }
                }.map { it.await() }
            }
        }
        // Interleave collections/artists so the category does not become one artist's page.
        (0 until (batches.maxOfOrNull { it.size } ?: 0)).flatMap { index -> batches.mapNotNull { it.getOrNull(index) } }
            .filter { categoryResultAllowed(clean, it) }
            .distinctBy { "${it.title.trim().lowercase()}|${it.artist.trim().lowercase()}" }.take(limit)
    }

    private suspend fun <T> catalogAttempt(block: suspend () -> T): T? = try {
        kotlinx.coroutines.withTimeout(12_000) { block() }
    } catch (_: kotlinx.coroutines.TimeoutCancellationException) { null
    } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled
    } catch (_: java.io.IOException) { null
    } catch (_: retrofit2.HttpException) { null
    } catch (_: com.google.gson.JsonParseException) { null }

    private fun DeezerTrack.toCatalogTrack(): CanonicalTrack? {
        val cleanTitle = title?.takeIf { it.isNotBlank() }
            ?: titleShort?.takeIf { it.isNotBlank() }
            ?: return null
        val cleanArtist = artistDisplay().takeIf { it.isNotBlank() } ?: return null
        return CanonicalTrack(
            title = cleanTitle,
            artist = cleanArtist,
            album = album?.title?.takeIf { it.isNotBlank() },
            isrc = isrc?.trim()?.uppercase()?.takeIf { it.isNotBlank() },
            durationMs = duration?.takeIf { it > 0 }?.times(1000L),
            externalTrackId = id.takeIf { it > 0 }?.let { "deezer:$it" },
            trackNumber = trackPosition,
            discNumber = diskNumber,
            releaseYear = album?.releaseDate?.take(4)?.toIntOrNull(),
            artworkUrl = upgradeDeezerArtwork(album?.coverXl ?: album?.cover),
            explicit = explicitLyrics,
            // The catalog ID can be resolved on demand; do not disable Play before trying it.
            sourceStatus = if (id > 0) SearchItemStatus.SOURCE_FOUND else SearchItemStatus.METADATA_ONLY,
            sourceProviderId = "cloudflare_gateway"
        )
    }

    private fun DeezerPlaylist.toCanonicalPlaylist(): CanonicalPlaylist = CanonicalPlaylist(
        title = title.orEmpty(),
        id = id.toString(),
        curator = user?.name,
        artworkUrl = upgradeDeezerArtwork(pictureXl ?: pictureMedium ?: picture),
        description = description?.takeIf { it.isNotBlank() },
        trackCount = trackCount?.takeIf { it > 0 },
        source = "deezer"
    )

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

internal fun catalogNameMatches(actual: String?, expected: String): Boolean {
    fun normalize(value: String) = value.trim().lowercase(java.util.Locale.ROOT)
        .replace(Regex("[^\\p{L}\\p{N}]+"), " ").trim()
    return !actual.isNullOrBlank() && normalize(actual) == normalize(expected)
}
