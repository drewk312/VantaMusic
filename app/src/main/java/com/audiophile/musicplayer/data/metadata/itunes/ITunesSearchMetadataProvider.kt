package com.audiophile.musicplayer.data.metadata.itunes

import android.util.Log
import com.audiophile.musicplayer.data.metadata.EnhancedMetadata
import com.audiophile.musicplayer.data.metadata.MetadataMatchKey
import com.audiophile.musicplayer.data.metadata.MetadataProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ITunesSearchMetadataProvider(
    private val client: ITunesSearchApiClient = ITunesSearchApiClient()
) : MetadataProvider {

    override suspend fun searchByText(title: String, artist: String, album: String?): EnhancedMetadata? {
        return withContext(Dispatchers.IO) {
            val query = buildList {
                add(title)
                add(artist)
                if (!album.isNullOrBlank()) add(album)
            }.joinToString(" ")

            val response = runCatching { client.search(query) }.getOrNull() ?: return@withContext null
            response.results
                .mapNotNull { it.toEnhancedMetadata("iTunes Search text match") }
                .maxByOrNull { it.scoreAgainst(title, artist, album) }
        }
    }

    override suspend fun lookupByIsrc(isrc: String): EnhancedMetadata? {
        val cleanIsrc = isrc.trim().uppercase()
        return withContext(Dispatchers.IO) {
            val response = runCatching { client.search(cleanIsrc, limit = 10) }.getOrNull() ?: return@withContext null
            response.results
                .firstOrNull { it.isrc?.uppercase() == cleanIsrc }
                ?.toEnhancedMetadata("iTunes Search ISRC match")
        }
    }

    override suspend fun lookupAlbum(album: String, artist: String): List<EnhancedMetadata> {
        return withContext(Dispatchers.IO) {
            val query = "$album $artist"
            val response = runCatching { client.search(query, limit = 50) }.getOrNull() ?: return@withContext emptyList()
            response.results
                .filter { it.collectionName?.equals(album, ignoreCase = true) == true }
                .mapNotNull { it.toEnhancedMetadata("iTunes Search album match") }
        }
    }

    override suspend fun lookupArtist(artist: String): List<EnhancedMetadata> {
        return withContext(Dispatchers.IO) {
            val response = runCatching { client.search(artist, limit = 25) }.getOrNull() ?: return@withContext emptyList()
            response.results
                .filter { it.artistName?.equals(artist, ignoreCase = true) == true }
                .mapNotNull { it.toEnhancedMetadata("iTunes Search artist match") }
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

    private fun ITunesTrack.toEnhancedMetadata(matchReason: String): EnhancedMetadata? {
        val title = trackName?.takeIf { it.isNotBlank() } ?: return null
        val artist = artistName?.takeIf { it.isNotBlank() } ?: return null

        val explicitVal = isExplicit
        Log.d("VANTA_METADATA_EXPLICIT",
            "title='${title}' artist='${artist}' explicit=${explicitVal} source='itunes_search' confidence='high' rawExplicitness='${trackExplicitness}'")

        return EnhancedMetadata(
            title = title,
            artist = artist,
            album = collectionName,
            albumArtist = collectionArtistName,
            durationMs = trackTimeMillis,
            artworkUrl = artworkUrl(),
            isrc = isrc,
            genres = genres ?: primaryGenreName?.let { listOf(it) }.orEmpty(),
            releaseYear = releaseDate?.take(4)?.toIntOrNull(),
            trackNumber = trackNumber,
            discNumber = discNumber,
            explicit = explicitVal,
            lyricsAvailable = false,
            syncedLyricsAvailable = false,
            externalIds = mapOf("itunes" to trackId.toString()),
            sourceConfidence = 0.85f,
            matchReason = matchReason
        )
    }

    private fun EnhancedMetadata.scoreAgainst(title: String, artist: String, album: String?): Float {
        var score = sourceConfidence
        if (this.title.equals(title, ignoreCase = true)) score += 0.05f
        if (this.artist.equals(artist, ignoreCase = true)) score += 0.03f
        if (!album.isNullOrBlank() && this.album.equals(album, ignoreCase = true)) score += 0.02f
        return score.coerceAtMost(1.0f)
    }
}
