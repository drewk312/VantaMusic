package com.audiophile.musicplayer.data.importer

import android.util.Log
import com.audiophile.musicplayer.data.local.entities.SourceType
import com.audiophile.musicplayer.data.remote.eclipse.EclipsePlaylistApi
import com.audiophile.musicplayer.data.remote.eclipse.EclipseTrack
import com.audiophile.musicplayer.data.repository.LocalLibraryRepository
import com.audiophile.musicplayer.data.repository.TrackRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Imports an Eclipse Music shared playlist into the VANTA local library.
 *
 * Creates a local playlist, searches the VANTA catalog for each track, and
 * saves matches. Tracks that cannot be matched immediately are still stored
 * as playlist entries with the imported title/artist so the user can resolve
 * them later.
 */
class EclipsePlaylistImporter(
    private val eclipseApi: EclipsePlaylistApi,
    private val trackRepository: TrackRepository,
    private val localLibraryRepository: LocalLibraryRepository
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
                ?: "Imported from Eclipse Music"

            val playlistId = localLibraryRepository.createPlaylist(
                name = name,
                description = description,
                sourceType = SourceType.ADDON
            )

            val matchedIds = mutableListOf<Long>()
            var unmatchedCount = 0

            playlist.tracks.forEach { track ->
                val localId = findOrResolveLocalTrack(track)
                if (localId != null) {
                    matchedIds.add(localId)
                } else {
                    unmatchedCount++
                    // Save as a local song stub so it appears in the playlist.
                    val stubId = saveStub(track)
                    matchedIds.add(stubId)
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
        // 2) Search the catalog and take the top result
        return runCatching {
            val results = trackRepository.searchLibrary("$title $artist".trim(), limit = 5)
            results.firstOrNull { candidate ->
                candidate.track.title.equals(title, ignoreCase = true) ||
                    candidate.track.artist.equals(artist, ignoreCase = true)
            }?.track?.trackId
        }.getOrNull()
    }
    private suspend fun saveStub(track: EclipseTrack): Long {
        val song = com.audiophile.musicplayer.data.local.entities.LocalSongEntity(
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
