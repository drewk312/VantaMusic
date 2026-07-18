package com.audiophile.musicplayer.testutil

import com.audiophile.musicplayer.data.importer.ImportMatchStatus
import com.audiophile.musicplayer.data.local.LibraryCounts
import com.audiophile.musicplayer.data.local.LibraryDao
import com.audiophile.musicplayer.data.local.dao.TrackDao
import com.audiophile.musicplayer.data.local.entities.CachedMetadataEntity
import com.audiophile.musicplayer.data.local.entities.ImportBatchEntity
import com.audiophile.musicplayer.data.local.entities.ImportMatchHistoryEntity
import com.audiophile.musicplayer.data.local.entities.ImportedTrackEntity
import com.audiophile.musicplayer.data.local.entities.LocalSongEntity
import com.audiophile.musicplayer.data.local.entities.PlaylistEntity
import com.audiophile.musicplayer.data.local.entities.PlaylistSongCrossRef
import com.audiophile.musicplayer.data.local.entities.PlaylistTrackCrossRef
import com.audiophile.musicplayer.data.local.entities.TrackSource
import com.audiophile.musicplayer.data.local.entities.UnifiedTrack
import com.audiophile.musicplayer.data.local.entities.UnifiedTrackWithSources
import com.audiophile.musicplayer.data.metadata.EnhancedMetadata
import com.audiophile.musicplayer.data.metadata.MetadataCache
import com.audiophile.musicplayer.data.metadata.MetadataProvider
import com.audiophile.musicplayer.data.metadata.MetadataResolver
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine

internal fun <T> runSuspendTest(block: suspend () -> T): T {
    val latch = CountDownLatch(1)
    var finalResult: Result<T>? = null
    block.startCoroutine(
        object : Continuation<T> {
            override val context = EmptyCoroutineContext

            override fun resumeWith(result: Result<T>) {
                finalResult = result
                latch.countDown()
            }
        }
    )
    check(latch.await(5, TimeUnit.SECONDS)) { "Timed out waiting for suspend test to finish" }
    val completed = finalResult ?: error("Suspend test completed without a result")
    return completed.getOrThrow()
}

internal fun newMetadataResolver(libraryDao: LibraryDao): MetadataResolver {
    val provider = object : MetadataProvider {
        override suspend fun searchByText(title: String, artist: String, album: String?): EnhancedMetadata? = null

        override suspend fun lookupByIsrc(isrc: String): EnhancedMetadata? = null

        override suspend fun lookupAlbum(album: String, artist: String): List<EnhancedMetadata> = emptyList()

        override suspend fun lookupArtist(artist: String): List<EnhancedMetadata> = emptyList()

        override suspend fun getRelatedTracks(track: EnhancedMetadata): List<EnhancedMetadata> = emptyList()
    }
    return MetadataResolver(provider = provider, cache = MetadataCache(libraryDao))
}

internal class InMemoryLibraryDao : LibraryDao {
    private val songs = mutableListOf<LocalSongEntity>()
    private val playlists = mutableListOf<PlaylistEntity>()
    private val importBatches = mutableListOf<ImportBatchEntity>()
    private val importedTracks = mutableListOf<ImportedTrackEntity>()
    private val playlistRefs = mutableListOf<PlaylistSongCrossRef>()
    private val metadataCache = linkedMapOf<String, CachedMetadataEntity>()
    private var nextSongId = 1L
    private var nextPlaylistId = 1L
    private var nextBatchId = 1L
    private var nextImportedTrackId = 1L

    fun songsSnapshot(): List<LocalSongEntity> = songs.toList()

    override fun allSongs(): Flow<List<LocalSongEntity>> = flowOf(songs.toList())

    override fun favorites(): Flow<List<LocalSongEntity>> = flowOf(songs.filter { it.isFavorite })

    override fun recentlyPlayed(): Flow<List<LocalSongEntity>> = flowOf(songs.filter { it.lastPlayedAt != null })

    override fun playlists(): Flow<List<PlaylistEntity>> = flowOf(playlists.toList())

    override suspend fun playlistsOnce(): List<PlaylistEntity> = playlists.toList()

    override fun importBatches(): Flow<List<ImportBatchEntity>> = flowOf(importBatches.toList())

    override suspend fun importBatchesOnce(): List<ImportBatchEntity> = importBatches.toList()

    override fun importedTracksByBatch(batchId: Long): Flow<List<ImportedTrackEntity>> =
        flowOf(importedTracks.filter { it.batchId == batchId }.sortedBy { it.id })

    override fun libraryCounts(): Flow<LibraryCounts> = flowOf(
        LibraryCounts(
            songsCount = songs.size,
            favoritesCount = songs.count { it.isFavorite },
            recentlyPlayedCount = songs.count { it.lastPlayedAt != null },
            playlistsCount = playlists.size,
            importsCount = importBatches.size
        )
    )

    override suspend fun allSongsOnce(): List<LocalSongEntity> = songs.toList()

    override suspend fun songById(songId: Long): LocalSongEntity? = songs.firstOrNull { it.id == songId }

    override suspend fun upsertSongs(songs: List<LocalSongEntity>): List<Long> = songs.map { upsertSong(it) }

    override suspend fun upsertSong(song: LocalSongEntity): Long {
        val id = if (song.id > 0L) song.id else nextSongId++
        val updated = song.copy(id = id)
        val existingIndex = songs.indexOfFirst { it.id == id }
        if (existingIndex >= 0) {
            songs[existingIndex] = updated
        } else {
            songs += updated
        }
        return id
    }

    override suspend fun toggleFavorite(songId: Long, updatedAt: Long) {
        val index = songs.indexOfFirst { it.id == songId }
        if (index >= 0) {
            val song = songs[index]
            songs[index] = song.copy(isFavorite = !song.isFavorite, updatedAt = updatedAt)
        }
    }

    override suspend fun updateRecentlyPlayed(songId: Long, playedAt: Long) {
        val index = songs.indexOfFirst { it.id == songId }
        if (index >= 0) {
            val song = songs[index]
            songs[index] = song.copy(lastPlayedAt = playedAt, updatedAt = playedAt)
        }
    }

    override suspend fun incrementPlayCount(songId: Long, playedAt: Long) {
        val index = songs.indexOfFirst { it.id == songId }
        if (index >= 0) {
            val song = songs[index]
            songs[index] = song.copy(playCount = song.playCount + 1, lastPlayedAt = playedAt, updatedAt = playedAt)
        }
    }

    override suspend fun insertPlaylist(playlist: PlaylistEntity): Long {
        val id = if (playlist.id > 0L) playlist.id else nextPlaylistId++
        playlists += playlist.copy(id = id)
        return id
    }

    override suspend fun insertPlaylistSongs(crossRefs: List<PlaylistSongCrossRef>) {
        playlistRefs += crossRefs
    }

    override suspend fun insertImportBatch(batch: ImportBatchEntity): Long {
        val id = if (batch.id > 0L) batch.id else nextBatchId++
        importBatches += batch.copy(id = id)
        return id
    }

    override suspend fun insertImportedTracks(tracks: List<ImportedTrackEntity>): List<Long> = tracks.map { track ->
        val id = if (track.id > 0L) track.id else nextImportedTrackId++
        importedTracks += track.copy(id = id)
        id
    }

    override suspend fun updateImportedTrack(track: ImportedTrackEntity) {
        val index = importedTracks.indexOfFirst { it.id == track.id }
        if (index >= 0) {
            importedTracks[index] = track
        }
    }

    override suspend fun deleteImportedTrack(trackId: Long) {
        importedTracks.removeAll { it.id == trackId }
    }

    override suspend fun deleteSong(songId: Long) {
        songs.removeAll { it.id == songId }
    }

    override suspend fun getCachedMetadata(key: String): CachedMetadataEntity? = metadataCache[key]

    override suspend fun insertCachedMetadata(entity: CachedMetadataEntity) {
        metadataCache[entity.matchKey] = entity
    }

    override suspend fun importedTracksByBatchOnce(batchId: Long): List<ImportedTrackEntity> =
        importedTracks.filter { it.batchId == batchId }.sortedBy { it.id }

    override suspend fun importedTracksByBatchAndStatus(
        batchId: Long,
        status: ImportMatchStatus
    ): List<ImportedTrackEntity> = importedTracksByBatchOnce(batchId).filter { it.matchStatus == status }

    override suspend fun insertMatchHistory(history: ImportMatchHistoryEntity) = Unit

    override suspend fun findSongByExactTitleArtist(title: String, artist: String): LocalSongEntity? =
        songs.firstOrNull {
            it.title.equals(title, ignoreCase = true) && it.artist.equals(artist, ignoreCase = true)
        }

    override suspend fun getPlaylistSongs(playlistId: Long): List<LocalSongEntity> {
        val idsInOrder = playlistRefs
            .filter { it.playlistId == playlistId }
            .sortedBy { it.position }
            .map { it.songId }
        return idsInOrder.mapNotNull { id -> songs.firstOrNull { it.id == id } }
    }

    override suspend fun deletePlaylist(playlistId: Long) {
        playlists.removeAll { it.id == playlistId }
        playlistRefs.removeAll { it.playlistId == playlistId }
    }
}

internal class InMemoryTrackDao(
    var searchResults: List<UnifiedTrackWithSources> = emptyList()
) : TrackDao {
    override suspend fun findTrackByMetadata(artist: String, title: String): UnifiedTrack? = null

    override suspend fun findTrackByLocalLibraryId(localLibraryId: Long): UnifiedTrack? = null

    override suspend fun insertUnifiedTrack(track: UnifiedTrack): Long = 0L

    override suspend fun insertTrackSource(source: TrackSource) = Unit

    override suspend fun updateTrackSource(source: TrackSource) = Unit

    override suspend fun findSourceByStreamUrl(streamUrl: String): TrackSource? = null

    override suspend fun getTrackCount(): Int = searchResults.size

    override suspend fun getAllTracksWithSources(): List<UnifiedTrackWithSources> = searchResults

    override suspend fun searchTracksWithSources(query: String, limit: Int): List<UnifiedTrackWithSources> =
        searchResults.take(limit)

    override suspend fun getTrackWithSourcesById(trackId: Long): UnifiedTrackWithSources? =
        searchResults.firstOrNull { it.track.trackId == trackId }

    override suspend fun insertAlbum(album: com.audiophile.musicplayer.data.local.entities.Album): Long = 0L

    override suspend fun findAlbumId(albumName: String, artistName: String): Long? = null

    override suspend fun getAllAlbumsWithTracks(): List<com.audiophile.musicplayer.data.local.entities.AlbumWithTracks> =
        emptyList()

    override suspend fun upsertPlaylistTrackCrossRef(crossRef: PlaylistTrackCrossRef) = Unit

    override suspend fun removeTrackFromPlaylist(playlistId: Long, trackId: Long) = Unit

    override suspend fun getOrderedTracksForPlaylist(playlistId: Long): List<UnifiedTrack> = emptyList()

    override suspend fun updatePlaylistTrackPosition(playlistId: Long, trackId: Long, newPosition: Int) = Unit

    override suspend fun getLastPlaylistPosition(playlistId: Long): Int = -1

    override suspend fun updateCoverArtIfMissing(trackId: Long, coverArtUrl: String) = Unit

    override suspend fun updateTrackMetadataIfMissing(
        trackId: Long,
        albumName: String?,
        coverArtUrl: String?,
        genre: String?,
        isrc: String?,
        durationMs: Long?,
        localLibraryId: Long?
    ) = Unit

    override suspend fun updateLastPlayedAt(trackId: Long, timestamp: Long) = Unit

    override suspend fun getRecentlyPlayedWithSources(limit: Int): List<UnifiedTrackWithSources> = emptyList()

    override suspend fun getRecentTrackIds(limit: Int): List<Long> = emptyList()

    override suspend fun trackExists(trackId: Long): Boolean = false

    override suspend fun findPoisonedTrackIds(): List<Long> = emptyList()

    override suspend fun deletePoisonedSources() = Unit

    override suspend fun migrateLocalHttpToAddon() = Unit

    override suspend fun deleteSourcesForTrack(trackId: Long) = Unit

    override suspend fun deletePlaylistRefsForTrack(trackId: Long) = Unit

    override suspend fun deleteUnifiedTrack(trackId: Long) = Unit
}
