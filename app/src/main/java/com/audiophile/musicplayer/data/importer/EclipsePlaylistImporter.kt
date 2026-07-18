package com.audiophile.musicplayer.data.importer

import android.util.Log
import com.audiophile.musicplayer.data.local.entities.LocalSongEntity
import com.audiophile.musicplayer.data.local.entities.SourceType
import com.audiophile.musicplayer.data.remote.eclipse.EclipsePlaylistApi
import com.audiophile.musicplayer.data.remote.eclipse.EclipseTrack
import com.audiophile.musicplayer.data.repository.LocalLibraryRepository
import com.audiophile.musicplayer.data.repository.TrackRepository
import com.audiophile.musicplayer.data.source.SourceRegistry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext

class EclipsePlaylistImporter(
    private val eclipseApi: EclipsePlaylistApi,
    private val trackRepository: TrackRepository,
    private val localLibraryRepository: LocalLibraryRepository,
    private val sourceRegistry: SourceRegistry? = null
) {

    data class ImportResult(
        val playlistId: Long,
        val playlistName: String,
        val matched: Int,
        val unmatched: Int
    )

    suspend fun import(url: String): Result<ImportResult> = withContext(Dispatchers.IO) {
        runCatching {
            val playlist = eclipseApi.fetchPlaylist(url).getOrThrow()
            val name = playlist.name?.takeIf { it.isNotBlank() } ?: "Imported Playlist"
            val description = playlist.description?.takeIf { it.isNotBlank() }
                ?: "Imported playlist"

            val playlistId = localLibraryRepository.createPlaylist(
                name = name,
                description = description,
                artworkUrl = playlist.artworkUrl,
                sourceType = SourceType.ADDON
            )

            Log.d("VANTA_ECLIPSE_IMPORT", "playlist_created id=$playlistId name='$name' tracks=${playlist.tracks.size}")

            val gate = Semaphore(permits = 15)
            val results: List<Long?> = coroutineScope {
                playlist.tracks.map { track ->
                    async {
                        gate.withPermit { findOrResolveLocalTrack(track) }
                    }
                }.awaitAll()
            }

            val matchedIds = mutableListOf<Long>()
            var unmatchedCount = 0

            results.forEachIndexed { index, localId ->
                if (localId != null) {
                    matchedIds.add(localId)
                } else {
                    unmatchedCount++
                    val stubId = saveStub(playlist.tracks[index])
                    matchedIds.add(stubId)
                }
                if (index % 20 == 0 || index == results.size - 1) {
                    Log.d("VANTA_ECLIPSE_IMPORT", "progress ${index + 1}/${results.size}")
                }
            }

            if (matchedIds.isNotEmpty()) {
                localLibraryRepository.addSongsToPlaylist(playlistId, matchedIds)
            }

            Log.i(
                "VANTA_ECLIPSE_IMPORT",
                "playlist=$name tracks=${playlist.tracks.size} matched=${matchedIds.size - unmatchedCount} unmatched=$unmatchedCount"
            )

            ImportResult(
                playlistId = playlistId,
                playlistName = name,
                matched = matchedIds.size - unmatchedCount,
                unmatched = unmatchedCount
            )
        }
    }

    private suspend fun findOrResolveLocalTrack(track: EclipseTrack): Long? {
        val title = track.title?.trim() ?: return null
        val artist = track.artist?.trim() ?: ""
        if (title.isBlank()) return null

        // 1) Exact local match
        localLibraryRepository.findSongByTitleArtist(title, artist)?.id?.let { return it }

        // 2) Search the local catalog for an exact title/artist match.
        val catalogMatch = if (artist.isBlank()) {
            null
        } else {
            runCatching {
                val results = trackRepository.searchLibrary("$title $artist".trim(), limit = 5)
                results.firstOrNull { candidate ->
                    candidate.track.title.equals(title, ignoreCase = true) &&
                        candidate.track.artist.equals(artist, ignoreCase = true)
                }?.track?.trackId
            }.getOrNull()
        }
        if (catalogMatch != null) {
            Log.d("VANTA_ECLIPSE_IMPORT", "catalog_match title='$title' localId=$catalogMatch")
            return catalogMatch
        }

        // 3) Source registry search (parallelized via Semaphore in import()).
        val registry = sourceRegistry
        if (registry != null) {
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
            }
            if (match != null) {
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
                    importSource = "eclipse_playlist",
                    dateAdded = System.currentTimeMillis(),
                    createdAt = System.currentTimeMillis(),
                    updatedAt = System.currentTimeMillis()
                )
                val ids = localLibraryRepository.saveSongs(listOf(song))
                val savedId = ids.firstOrNull() ?: 0L
                Log.d("VANTA_ECLIPSE_IMPORT", "registry_match title='$title' provider=${match.providerId} savedId=$savedId hasStreamUrl=${resolved != null}")
                return savedId
            }
        }

        // Not found locally or in registry — save as stub for later resolution.
        Log.d("VANTA_ECLIPSE_IMPORT", "stub title='$title' artist='$artist'")
        return null
    }

    private suspend fun saveStub(track: EclipseTrack): Long {
        val song = LocalSongEntity(
            title = track.title?.trim() ?: "Unknown Track",
            artist = track.artist?.trim() ?: "Unknown Artist",
            album = track.album,
            durationMs = track.durationMs ?: 0L,
            artworkUrl = track.artworkUrl,
            isrc = track.isrc,
            sourceType = SourceType.ADDON,
            importSource = "eclipse_playlist",
            dateAdded = System.currentTimeMillis(),
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )
        val ids = localLibraryRepository.saveSongs(listOf(song))
        return ids.firstOrNull() ?: 0L
    }
}
