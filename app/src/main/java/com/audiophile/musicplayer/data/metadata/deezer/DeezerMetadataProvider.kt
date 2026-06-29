package com.audiophile.musicplayer.data.metadata.deezer

import android.util.Log
import com.audiophile.musicplayer.data.metadata.EnhancedMetadata
import com.audiophile.musicplayer.data.metadata.MetadataMatchKey
import com.audiophile.musicplayer.data.metadata.MetadataProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class DeezerMetadataProvider(
    private val client: DeezerApiClient = DeezerApiClient()
) : MetadataProvider {

    override suspend fun searchByText(title: String, artist: String, album: String?): EnhancedMetadata? {
        return withContext(Dispatchers.IO) {
            val query = buildList {
                add(title)
                add(artist)
                if (!album.isNullOrBlank()) add(album)
            }.joinToString(" ")
            val response = runCatching { client.searchTracks(query, limit = 25) }
                .onFailure { Log.d("VANTA_DEEZER", "search_failed query='$query' error='${it.message}'") }
                .getOrNull()
                ?: return@withContext null

            response.data
                .mapNotNull { it.toEnhancedMetadata("Deezer text match") }
                .maxByOrNull { it.scoreAgainst(title, artist, album) }
        }
    }

    override suspend fun lookupByIsrc(isrc: String): EnhancedMetadata? {
        val cleanIsrc = isrc.trim().uppercase()
        if (cleanIsrc.isBlank()) return null
        return withContext(Dispatchers.IO) {
            runCatching { client.lookupByIsrc(cleanIsrc) }
                .onFailure { Log.d("VANTA_DEEZER", "isrc_lookup_failed isrc=$cleanIsrc error='${it.message}'") }
                .getOrNull()
                ?.takeIf { it.id > 0 && it.isrc?.uppercase() == cleanIsrc }
                ?.toEnhancedMetadata("Deezer ISRC match")
        }
    }

    override suspend fun lookupAlbum(album: String, artist: String): List<EnhancedMetadata> {
        return withContext(Dispatchers.IO) {
            val response = runCatching { client.searchTracks("$album $artist", limit = 50) }.getOrNull()
                ?: return@withContext emptyList()
            response.data
                .filter { it.album?.title.equals(album, ignoreCase = true) }
                .mapNotNull { it.toEnhancedMetadata("Deezer album match") }
        }
    }

    override suspend fun lookupArtist(artist: String): List<EnhancedMetadata> {
        return withContext(Dispatchers.IO) {
            val response = runCatching { client.searchTracks(artist, limit = 50) }.getOrNull()
                ?: return@withContext emptyList()
            response.data
                .filter { it.artistDisplay().equals(artist, ignoreCase = true) }
                .mapNotNull { it.toEnhancedMetadata("Deezer artist match") }
        }
    }

    override suspend fun getRelatedTracks(track: EnhancedMetadata): List<EnhancedMetadata> {
        return lookupArtist(track.artist)
            .filterNot {
                MetadataMatchKey.generateKey(it.isrc, it.title, it.artist, it.album) ==
                    MetadataMatchKey.generateKey(track.isrc, track.title, track.artist, track.album)
            }
            .take(10)
    }
}

fun DeezerTrack.toEnhancedMetadata(matchReason: String): EnhancedMetadata? {
    val title = titleShort?.takeIf { it.isNotBlank() }
        ?: title?.takeIf { it.isNotBlank() }
        ?: return null
    val artist = artistDisplay().takeIf { it.isNotBlank() } ?: return null
    val releaseYear = album?.releaseDate?.take(4)?.toIntOrNull()
    return EnhancedMetadata(
        title = title,
        artist = artist,
        album = album?.title,
        albumArtist = artist,
        durationMs = duration?.takeIf { it > 0 }?.times(1000L),
        artworkUrl = upgradeDeezerArtwork(album?.coverXl ?: album?.cover),
        isrc = isrc?.trim()?.uppercase()?.takeIf { it.isNotBlank() },
        genres = emptyList(),
        releaseYear = releaseYear,
        trackNumber = trackPosition,
        discNumber = diskNumber,
        explicit = explicitLyrics,
        lyricsAvailable = true,
        syncedLyricsAvailable = true,
        externalIds = mapOf("deezer" to id.toString()),
        sourceConfidence = 0.88f,
        matchReason = matchReason
    )
}

fun DeezerTrack.artistDisplay(): String {
    val contributorNames = contributors
        .mapNotNull { it.name?.trim()?.takeIf(String::isNotBlank) }
        .distinctBy { it.lowercase() }
    if (contributorNames.isNotEmpty()) return contributorNames.joinToString(", ")
    return artist?.name?.trim().orEmpty()
}

fun upgradeDeezerArtwork(url: String?): String? {
    if (url.isNullOrBlank()) return null
    val upgraded = url.replace(
        Regex("/\\d+x\\d+-\\d+-\\d+-\\d+-\\d+\\.jpg$"),
        "/1800x1800-000000-80-0-0.jpg"
    )
    return upgraded.ifBlank { url }
}

private fun EnhancedMetadata.scoreAgainst(title: String, artist: String, album: String?): Float {
    var score = sourceConfidence
    if (this.title.equals(title, ignoreCase = true)) score += 0.06f
    if (this.artist.equals(artist, ignoreCase = true)) score += 0.04f
    if (!album.isNullOrBlank() && this.album.equals(album, ignoreCase = true)) score += 0.03f
    return score.coerceAtMost(1.0f)
}
