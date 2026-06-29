package com.audiophile.musicplayer.data.metadata.apple

import android.util.Log
import com.audiophile.musicplayer.data.metadata.EnhancedMetadata
import com.audiophile.musicplayer.data.metadata.MetadataMatchKey
import com.audiophile.musicplayer.data.metadata.MetadataProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URI

class AppleMusicMetadataProvider(
    private val configProvider: () -> AppleMusicConfig,
    private val clientFactory: (String) -> AppleMusicApiClient = { AppleMusicApiClient(it) }
) : MetadataProvider {
    
    private var lastToken: String? = null
    private var cachedClient: AppleMusicApiClient? = null

    private val client: AppleMusicApiClient?
        get() {
            val currentConfig = configProvider()
            val token = currentConfig.developerToken?.takeIf { it.isNotBlank() }
            if (token != lastToken) {
                lastToken = token
                cachedClient = token?.let(clientFactory)
            }
            return cachedClient
        }

    suspend fun lookupByCatalogId(catalogId: String): EnhancedMetadata? {
        val apiClient = client ?: return null
        return runCatching {
            withContext(Dispatchers.IO) {
                apiClient.lookupSongById(configProvider().storefront, catalogId)
                    .data
                    .firstNotNullOfOrNull { it.toEnhancedMetadata("Apple Music catalog ID match") }
            }
        }.getOrNull()
    }

    suspend fun resolveUrl(url: String): EnhancedMetadata? {
        val catalogId = parseAppleMusicCatalogId(url) ?: return null
        return lookupByCatalogId(catalogId)
    }

    suspend fun resolveCollectionUrl(url: String): AppleMusicCollectionMetadata? {
        val uri = runCatching { URI(url) }.getOrNull() ?: return null
        val segments = uri.path.orEmpty().split('/').filter { it.isNotBlank() }
        val type = when {
            segments.any { it.equals("playlist", ignoreCase = true) } -> "playlist"
            segments.any { it.equals("album", ignoreCase = true) } && uri.rawQuery?.contains("i=") != true -> "album"
            else -> return null
        }
        val id = segments.lastOrNull()?.takeIf { it.isNotBlank() } ?: return null
        val apiClient = client ?: return null
        return runCatching {
            withContext(Dispatchers.IO) {
                if (type == "playlist") {
                    val resource = apiClient.lookupPlaylistById(configProvider().storefront, id).data.firstOrNull()
                        ?: return@withContext null
                    AppleMusicCollectionMetadata(
                        id = resource.id,
                        type = type,
                        title = resource.attributes?.name ?: "Apple Music Playlist",
                        artist = resource.attributes?.curatorName,
                        artworkUrl = resource.attributes?.artwork?.sizedUrl(),
                        tracks = resource.relationships?.tracks?.data.orEmpty()
                            .mapNotNull { it.toEnhancedMetadata("Apple Music playlist track") }
                    )
                } else {
                    val resource = apiClient.lookupAlbumById(configProvider().storefront, id).data.firstOrNull()
                        ?: return@withContext null
                    AppleMusicCollectionMetadata(
                        id = resource.id,
                        type = type,
                        title = resource.attributes?.name ?: "Apple Music Album",
                        artist = resource.attributes?.artistName,
                        artworkUrl = resource.attributes?.artwork?.sizedUrl(),
                        tracks = resource.relationships?.tracks?.data.orEmpty()
                            .mapNotNull { it.toEnhancedMetadata("Apple Music album track") }
                    )
                }
            }
        }.onFailure {
            Log.w("VANTA_APPLE_COLLECTION", "type=$type id=$id failed='${it.message}'")
        }.getOrNull()
    }

    private fun parseAppleMusicCatalogId(url: String): String? {
        val uri = runCatching { URI(url) }.getOrNull() ?: return null
        val host = uri.host.orEmpty().lowercase()
        if ("music.apple.com" !in host && "itunes.apple.com" !in host) return null
        val queryParam = uri.rawQuery
            ?.split('&')
            ?.firstNotNullOfOrNull { pair ->
                val parts = pair.split('=', limit = 2)
                if (parts.size == 2 && parts[0] == "i" && parts[1].all { it.isDigit() }) parts[1] else null
            }
        if (queryParam != null) return queryParam
        return uri.path.orEmpty()
            .split('/')
            .lastOrNull { segment -> segment.isNotBlank() && segment.all { it.isDigit() } }
    }

    override suspend fun searchByText(title: String, artist: String, album: String?): EnhancedMetadata? {
        val apiClient = client ?: return null
        val query = listOf(title, artist, album.orEmpty()).filter { it.isNotBlank() }.joinToString(" ")
        return runCatching {
            withContext(Dispatchers.IO) {
                apiClient.searchSongs(configProvider().storefront, query)
                    .results
                    ?.songs
                    ?.data
                    .orEmpty()
                    .mapNotNull { it.toEnhancedMetadata("Apple Music catalog text match") }
                    .maxByOrNull { it.scoreAgainst(title, artist, album) }
            }
        }.getOrNull()
    }

    override suspend fun lookupByIsrc(isrc: String): EnhancedMetadata? {
        val apiClient = client ?: return null
        return runCatching {
            withContext(Dispatchers.IO) {
                apiClient.lookupSongsByIsrc(configProvider().storefront, isrc)
                    .data
                    .firstNotNullOfOrNull { it.toEnhancedMetadata("Apple Music catalog ISRC match") }
            }
        }.getOrNull()
    }

    override suspend fun lookupAlbum(album: String, artist: String): List<EnhancedMetadata> {
        val apiClient = client ?: return emptyList()
        return runCatching {
            withContext(Dispatchers.IO) {
                apiClient.searchSongs(configProvider().storefront, "$album $artist", limit = 25)
                    .results
                    ?.songs
                    ?.data
                    .orEmpty()
                    .mapNotNull { it.toEnhancedMetadata("Apple Music catalog album match") }
                    .filter { it.album.equals(album, ignoreCase = true) }
            }
        }.getOrDefault(emptyList())
    }

    override suspend fun lookupArtist(artist: String): List<EnhancedMetadata> {
        val apiClient = client ?: return emptyList()
        return runCatching {
            withContext(Dispatchers.IO) {
                apiClient.searchSongs(configProvider().storefront, artist, limit = 25)
                    .results
                    ?.songs
                    ?.data
                    .orEmpty()
                    .mapNotNull { it.toEnhancedMetadata("Apple Music catalog artist match") }
                    .filter { it.artist.equals(artist, ignoreCase = true) }
            }
        }.getOrDefault(emptyList())
    }

    override suspend fun getRelatedTracks(track: EnhancedMetadata): List<EnhancedMetadata> {
        return lookupArtist(track.artist)
            .filterNot {
                MetadataMatchKey.generateKey(it.isrc, it.title, it.artist, it.album) ==
                    MetadataMatchKey.generateKey(track.isrc, track.title, track.artist, track.album)
            }
            .take(10)
    }

    private fun AppleMusicSongResource.toEnhancedMetadata(matchReason: String): EnhancedMetadata? {
        val attr = attributes ?: return null
        val title = attr.name?.takeIf { it.isNotBlank() } ?: return null
        val artist = attr.artistName?.takeIf { it.isNotBlank() } ?: return null
        val notes = attr.editorialNotes?.standard ?: attr.editorialNotes?.short
        val explicitVal = when (attr.contentRating?.lowercase()) {
            "explicit" -> true
            "clean" -> false
            else -> null
        }
        Log.d("VANTA_METADATA_EXPLICIT",
            "title='${title}' artist='${artist}' explicit=${explicitVal} source='apple_music' confidence='high' contentRating='${attr.contentRating}'")

        return EnhancedMetadata(
            title = title,
            artist = artist,
            album = attr.albumName,
            albumArtist = attr.albumArtistName,
            durationMs = attr.durationInMillis,
            artworkUrl = attr.artwork?.sizedUrl(),
            isrc = attr.isrc,
            genres = attr.genreNames,
            releaseYear = attr.releaseDate?.take(4)?.toIntOrNull(),
            trackNumber = attr.trackNumber,
            discNumber = attr.discNumber,
            explicit = explicitVal,
            lyricsAvailable = false,
            syncedLyricsAvailable = false,
            credits = emptyMap(),
            editorialNotes = notes,
            externalIds = mapOf("appleMusicCatalogId" to id),
            sourceConfidence = 0.92f,
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
