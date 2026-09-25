package com.audiophile.musicplayer.data.repository

import com.audiophile.musicplayer.data.importer.ImportMatchStatus
import com.audiophile.musicplayer.data.local.LibraryCounts
import com.audiophile.musicplayer.data.local.LibraryDao
import com.audiophile.musicplayer.data.local.entities.ImportBatchEntity
import com.audiophile.musicplayer.data.local.entities.ImportedTrackEntity
import com.audiophile.musicplayer.data.local.entities.LocalSongEntity
import com.audiophile.musicplayer.data.local.entities.PlaylistEntity
import com.audiophile.musicplayer.data.local.entities.PlaylistSongCrossRef
import com.audiophile.musicplayer.data.local.entities.SourceType
import com.audiophile.musicplayer.data.local.toLocalSongEntity
import com.audiophile.musicplayer.data.metadata.MetadataResolver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

class LocalLibraryRepository(
    private val libraryDao: LibraryDao,
    private val metadataResolver: MetadataResolver
) {
    val allSongs: Flow<List<LocalSongEntity>> = libraryDao.allSongs()
    val favorites: Flow<List<LocalSongEntity>> = libraryDao.favorites()
    val recentlyPlayed: Flow<List<LocalSongEntity>> = libraryDao.recentlyPlayed()
    val playlists: Flow<List<PlaylistEntity>> = libraryDao.playlists()
    val importBatches: Flow<List<ImportBatchEntity>> = libraryDao.importBatches()
    val libraryCounts: Flow<LibraryCounts> = libraryDao.libraryCounts()

    fun importedTracksByBatch(batchId: Long): Flow<List<ImportedTrackEntity>> {
        return libraryDao.importedTracksByBatch(batchId)
    }

    suspend fun allSongsSnapshot(): List<LocalSongEntity> = withContext(Dispatchers.IO) {
        libraryDao.allSongsOnce()
    }

    suspend fun libraryCountsSnapshot(): LibraryCounts = withContext(Dispatchers.IO) {
        libraryDao.libraryCounts().first()
    }

    suspend fun importBatchesSnapshot(): List<ImportBatchEntity> = withContext(Dispatchers.IO) {
        libraryDao.importBatchesOnce()
    }

    suspend fun playlistsSnapshot(): List<PlaylistEntity> = withContext(Dispatchers.IO) {
        libraryDao.playlistsOnce()
    }

    suspend fun saveSongs(songs: List<LocalSongEntity>): List<Long> = withContext(Dispatchers.IO) {
        libraryDao.upsertSongs(songs)
    }

    suspend fun songById(songId: Long): LocalSongEntity? = withContext(Dispatchers.IO) {
        libraryDao.songById(songId)
    }

    suspend fun findSongByTitleArtist(title: String, artist: String): LocalSongEntity? =
        withContext(Dispatchers.IO) {
            libraryDao.findSongByExactTitleArtist(title.trim(), artist.trim())
        }

    suspend fun findSongByIsrc(isrc: String): LocalSongEntity? =
        withContext(Dispatchers.IO) {
            val clean = isrc.trim()
            if (clean.isEmpty()) null else libraryDao.findSongByIsrc(clean)
        }

    suspend fun toggleFavorite(songId: Long) = withContext(Dispatchers.IO) {
        libraryDao.toggleFavorite(songId)
    }

    suspend fun updateRecentlyPlayed(songId: Long) = withContext(Dispatchers.IO) {
        libraryDao.updateRecentlyPlayed(songId)
    }

    suspend fun incrementPlayCount(songId: Long) = withContext(Dispatchers.IO) {
        libraryDao.incrementPlayCount(songId)
    }

    suspend fun createPlaylist(
        name: String,
        description: String? = null,
        artworkUrl: String? = null,
        sourceType: SourceType = SourceType.LOCAL,
        importBatchId: Long? = null
    ): Long = withContext(Dispatchers.IO) {
        libraryDao.insertPlaylist(
            PlaylistEntity(
                name = name,
                description = description,
                artworkUrl = artworkUrl,
                sourceType = sourceType,
                importBatchId = importBatchId
            )
        )
    }

    suspend fun replaceImportedPlaylistSongs(playlistId: Long, songIds: List<Long>) = withContext(Dispatchers.IO) {
        libraryDao.replacePlaylistMembership(playlistId, songIds)
    }

    suspend fun addSongsToPlaylist(playlistId: Long, songIds: List<Long>) = withContext(Dispatchers.IO) {
        val existing = libraryDao.getPlaylistSongs(playlistId).map { it.id }
        libraryDao.insertPlaylistSongs(
            (existing + songIds).distinct().mapIndexed { index, songId ->
                PlaylistSongCrossRef(playlistId = playlistId, songId = songId, position = index)
            }
        )
    }

    suspend fun likeSongs(songIds: List<Long>) = withContext(Dispatchers.IO) {
        songIds.distinct().chunked(500).forEach { libraryDao.likeSongs(it) }
    }

    suspend fun playlistSongsSnapshot(playlistId: Long): List<LocalSongEntity> = withContext(Dispatchers.IO) {
        libraryDao.getPlaylistSongs(playlistId)
    }

    suspend fun deletePlaylist(playlistId: Long) = withContext(Dispatchers.IO) {
        libraryDao.deletePlaylist(playlistId)
    }

    suspend fun saveImportBatch(batch: ImportBatchEntity): Long = withContext(Dispatchers.IO) {
        libraryDao.insertImportBatch(batch)
    }

    suspend fun saveImportedTracks(tracks: List<ImportedTrackEntity>): List<Long> = withContext(Dispatchers.IO) {
        libraryDao.insertImportedTracks(tracks)
    }

    suspend fun updateImportedTracks(tracks: List<ImportedTrackEntity>) = withContext(Dispatchers.IO) {
        tracks.forEach { libraryDao.updateImportedTrack(it) }
    }

    suspend fun importedTracksByBatchSnapshot(batchId: Long): List<ImportedTrackEntity> = withContext(Dispatchers.IO) {
        libraryDao.importedTracksByBatchOnce(batchId)
    }

    suspend fun removeImportedTrack(trackId: Long) = withContext(Dispatchers.IO) {
        libraryDao.deleteImportedTrack(trackId)
    }

    suspend fun deleteSong(songId: Long) = withContext(Dispatchers.IO) {
        libraryDao.deleteSong(songId)
    }

    suspend fun saveMatchedTracksToLibrary(batchId: Long): SaveMatchedTracksResult = withContext(Dispatchers.IO) {
        val matched = libraryDao.importedTracksByBatchAndStatus(batchId, ImportMatchStatus.MATCHED)
        val savedSongs = mutableListOf<LocalSongEntity>()
        var skippedDuplicates = 0

        matched.forEach { imported ->
            val initialSong = imported.toLocalSongEntity() ?: return@forEach
            val enrichedSong = metadataResolver.resolveAndEnrich(initialSong)
            
            val title = enrichedSong.title
            val artist = enrichedSong.artist
            val existing = libraryDao.findSongByExactTitleArtist(title, artist)
            if (existing != null) {
                skippedDuplicates += 1
                savedSongs += existing
            } else {
                val id = libraryDao.upsertSong(enrichedSong)
                savedSongs += enrichedSong.copy(id = id)
            }
        }

        SaveMatchedTracksResult(
            requestedCount = matched.size,
            savedCount = matched.size - skippedDuplicates,
            skippedDuplicates = skippedDuplicates,
            songs = savedSongs
        )
    }

    suspend fun saveAllImportMetadataToLibrary(batchId: Long): SaveMatchedTracksResult = withContext(Dispatchers.IO) {
        val rows = libraryDao.importedTracksByBatchOnce(batchId)
        val savedSongs = mutableListOf<LocalSongEntity>()
        var skippedDuplicates = 0

        rows.forEach { imported ->
            val title = imported.userEditedTitle ?: imported.parsedTitle ?: return@forEach
            val artist = imported.userEditedArtist ?: imported.parsedArtist ?: "Unknown Artist"
            val existing = libraryDao.findSongByExactTitleArtist(title, artist)
            if (existing != null) {
                skippedDuplicates += 1
                savedSongs += existing
            } else {
                val initialSong = LocalSongEntity(
                    title = title,
                    artist = artist,
                    album = imported.parsedAlbum,
                    sourceType = SourceType.LOCAL,
                    streamUrl = imported.sourceUrl,
                    quality = imported.friendlySourceLabel,
                    importSource = "Library Import",
                    externalIdsJson = imported.matchedSongId?.let { """{"matchedSongId":$it}""" }
                )
                val enrichedSong = metadataResolver.resolveAndEnrich(initialSong)
                val id = libraryDao.upsertSong(enrichedSong)
                savedSongs += enrichedSong.copy(id = id)
            }
        }

        SaveMatchedTracksResult(
            requestedCount = rows.size,
            savedCount = savedSongs.size - skippedDuplicates,
            skippedDuplicates = skippedDuplicates,
            songs = savedSongs
        )
    }
}

data class SaveMatchedTracksResult(
    val requestedCount: Int,
    val savedCount: Int,
    val skippedDuplicates: Int,
    val songs: List<LocalSongEntity>
)
