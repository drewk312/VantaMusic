package com.audiophile.musicplayer.data.importer

import android.util.Log
import com.audiophile.musicplayer.data.local.entities.LocalSongEntity
import com.audiophile.musicplayer.data.local.entities.SourceType
import com.audiophile.musicplayer.data.repository.LocalLibraryRepository
import com.audiophile.musicplayer.data.repository.TrackRepository
import com.audiophile.musicplayer.data.source.CloudflareGatewaySource
import com.audiophile.musicplayer.data.source.SourceRegistry
import com.audiophile.musicplayer.data.source.SourceSearchResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.net.URI
import java.util.Locale

/**
 * Paste a public Spotify or Apple Music playlist URL — no user tokens.
 * Track lists come from the music-gateway (server-side creds / anonymous web session).
 */
class GatewayPlaylistImporter(
    private val gateway: CloudflareGatewaySource,
    private val trackRepository: TrackRepository,
    private val localLibraryRepository: LocalLibraryRepository,
    private val sourceRegistry: SourceRegistry? = null,
) {
    data class ImportResult(
        val playlistId: Long,
        val playlistName: String,
        val matched: Int,
        val unmatched: Int,
        val platform: String,
    )

    data class ParsedPlaylistLink(
        val platform: String,
        val playlistId: String,
        val storefront: String? = null,
        val displayNameHint: String? = null,
    )

    suspend fun import(url: String): Result<ImportResult> = withContext(Dispatchers.IO) {
        runCatching {
            val link = parsePlaylistUrl(url)
                ?: error("Not a public Spotify or Apple Music playlist link")
            val tracks = when (link.platform) {
                "spotify" -> gateway.spotifyPlaylistTracks(link.playlistId, limit = 300)
                "apple" -> gateway.applePlaylistTracks(
                    playlistId = link.playlistId,
                    limit = 300,
                    storefront = link.storefront ?: "us",
                )
                else -> emptyList()
            }
            if (tracks.isEmpty()) {
                error("Could not load playlist tracks. Make sure the playlist is public and try again.")
            }

            val name = link.displayNameHint?.takeIf { it.isNotBlank() }
                ?: "${link.platform.replaceFirstChar { it.titlecase(Locale.US) }} playlist"
            val description = "Imported from ${link.platform} · no login"
            val artwork = tracks.firstOrNull()?.artworkUrl
            val playlistId = localLibraryRepository.createPlaylist(
                name = name,
                description = description,
                artworkUrl = artwork,
                sourceType = SourceType.ADDON,
            )

            Log.d(
                "VANTA_GATEWAY_IMPORT",
                "playlist_created id=$playlistId platform=${link.platform} tracks=${tracks.size}",
            )

            val gate = Semaphore(permits = 15)
            val results: List<Long?> = coroutineScope {
                tracks.map { track ->
                    async { gate.withPermit { findOrResolveLocalTrack(track, link.platform) } }
                }.awaitAll()
            }

            val matchedIds = mutableListOf<Long>()
            var unmatchedCount = 0
            results.forEachIndexed { index, localId ->
                if (localId != null) {
                    matchedIds.add(localId)
                } else {
                    unmatchedCount++
                    matchedIds.add(saveStub(tracks[index], link.platform))
                }
            }

            if (matchedIds.isNotEmpty()) {
                localLibraryRepository.addSongsToPlaylist(playlistId, matchedIds)
            }

            ImportResult(
                playlistId = playlistId,
                playlistName = name,
                matched = matchedIds.size - unmatchedCount,
                unmatched = unmatchedCount,
                platform = link.platform,
            )
        }
    }

    private suspend fun findOrResolveLocalTrack(track: SourceSearchResult, platform: String): Long? {
        val title = track.title.trim()
        val artist = track.artist.trim()
        if (title.isBlank()) return null

        localLibraryRepository.findSongByTitleArtist(title, artist)?.id?.let { return it }

        val catalogMatch = if (artist.isBlank()) {
            null
        } else {
            runCatching {
                trackRepository.searchLibrary("$title $artist".trim(), limit = 5)
                    .firstOrNull { candidate ->
                        candidate.track.title.equals(title, ignoreCase = true) &&
                            candidate.track.artist.equals(artist, ignoreCase = true)
                    }?.track?.trackId
            }.getOrNull()
        }
        if (catalogMatch != null) return catalogMatch

        val registry = sourceRegistry ?: return null
        val query = buildString {
            append(title)
            if (artist.isNotBlank()) append(" $artist")
        }
        val sourceResults = runCatching {
            registry.searchAll(query, timeoutMs = 3_000L)
        }.getOrNull().orEmpty()

        val match = sourceResults.firstOrNull { candidate ->
            candidate.title.equals(title, ignoreCase = true) &&
                candidate.artist.equals(artist, ignoreCase = true)
        } ?: return null

        val resolved = runCatching {
            registry.resolveStream(match.providerId, match.id, timeoutMs = 3_000L)
        }.getOrNull()

        val song = LocalSongEntity(
            title = title,
            artist = artist,
            album = track.album ?: match.album,
            durationMs = match.durationMs ?: track.durationMs,
            artworkUrl = track.artworkUrl ?: match.artworkUrl,
            isrc = match.isrc ?: track.isrc,
            sourceType = SourceType.ADDON,
            streamUrl = resolved?.streamUrl,
            importSource = "${platform}_playlist",
            dateAdded = System.currentTimeMillis(),
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis(),
        )
        return localLibraryRepository.saveSongs(listOf(song)).firstOrNull()
    }

    private suspend fun saveStub(track: SourceSearchResult, platform: String): Long {
        val song = LocalSongEntity(
            title = track.title.trim().ifBlank { "Unknown Track" },
            artist = track.artist.trim().ifBlank { "Unknown Artist" },
            album = track.album,
            durationMs = track.durationMs ?: 0L,
            artworkUrl = track.artworkUrl,
            isrc = track.isrc,
            sourceType = SourceType.ADDON,
            importSource = "${platform}_playlist",
            dateAdded = System.currentTimeMillis(),
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis(),
        )
        return localLibraryRepository.saveSongs(listOf(song)).firstOrNull() ?: 0L
    }

    companion object {
        fun isGatewayPlaylistUrl(text: String): Boolean = parsePlaylistUrl(text) != null

        fun parsePlaylistUrl(raw: String): ParsedPlaylistLink? {
            val trimmed = raw.trim()
            if (trimmed.isBlank()) return null

            if (trimmed.startsWith("spotify:playlist:", ignoreCase = true)) {
                val id = trimmed.removePrefix("spotify:playlist:").removePrefix("SPOTIFY:PLAYLIST:").trim()
                return id.takeIf { it.isNotBlank() && it.all { ch -> ch.isLetterOrDigit() } }?.let {
                    ParsedPlaylistLink(platform = "spotify", playlistId = it)
                }
            }

            val uri = runCatching { URI(trimmed) }.getOrNull() ?: return null
            val host = uri.host.orEmpty().removePrefix("www.").lowercase(Locale.US)
            val segments = uri.path.orEmpty().split('/').filter { it.isNotBlank() }

            when {
                host.contains("open.spotify.com") || host.contains("spotify.link") -> {
                    val idx = segments.indexOfFirst { it.equals("playlist", ignoreCase = true) }
                    if (idx < 0) return null
                    val id = segments.getOrNull(idx + 1)?.substringBefore('?')?.trim().orEmpty()
                    if (id.isBlank() || !id.all { it.isLetterOrDigit() }) return null
                    return ParsedPlaylistLink(platform = "spotify", playlistId = id)
                }
                host.contains("music.apple.com") || host.contains("itunes.apple.com") -> {
                    val idx = segments.indexOfFirst { it.equals("playlist", ignoreCase = true) }
                    if (idx < 0) return null
                    val storefront = segments.firstOrNull()?.takeIf { it.length == 2 } ?: "us"
                    val slug = segments.getOrNull(idx + 1)
                    val id = segments.getOrNull(idx + 2)
                        ?: segments.getOrNull(idx + 1)?.takeIf { it.startsWith("pl.") }
                    val playlistId = id?.substringBefore('?')?.trim().orEmpty()
                    if (playlistId.isBlank()) return null
                    val hint = slug
                        ?.takeUnless { it.startsWith("pl.") }
                        ?.replace('-', ' ')
                        ?.replace('_', ' ')
                        ?.trim()
                        ?.split(Regex("""\s+"""))
                        ?.joinToString(" ") { word ->
                            word.replaceFirstChar { ch ->
                                if (ch.isLowerCase()) ch.titlecase(Locale.US) else ch.toString()
                            }
                        }
                    return ParsedPlaylistLink(
                        platform = "apple",
                        playlistId = playlistId,
                        storefront = storefront,
                        displayNameHint = hint,
                    )
                }
                else -> return null
            }
        }
    }
}
