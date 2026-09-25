package com.audiophile.musicplayer.ui

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.audiophile.musicplayer.AppContainer
import com.audiophile.musicplayer.common.VantaLogger
import com.audiophile.musicplayer.data.local.entities.ImportBatchEntity
import com.audiophile.musicplayer.data.local.entities.ImportedTrackEntity
import com.audiophile.musicplayer.data.local.entities.ListeningHistoryEntity
import com.audiophile.musicplayer.data.local.entities.SourceType
import com.audiophile.musicplayer.data.importer.ImportMatchStatus
import com.audiophile.musicplayer.data.importer.PlayabilityStatus
import com.audiophile.musicplayer.data.importer.MatchConfidence
import com.audiophile.musicplayer.data.importer.SpotifyExportImporter
import com.audiophile.musicplayer.permissions.MediaPermissions
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class ImportUiState(
    val activeImportBatchId: Long? = null,
    val activeImportedTracks: List<ImportedTrackEntity> = emptyList(),
    val importBatches: List<ImportBatchEntity> = emptyList(),
    val isScanning: Boolean = false,
    val scanProgress: String? = null,
    val statusMessage: String? = null,
)

sealed class ImportUiEvent {
    data class ScanDeviceLibrary(val uri: Uri? = null) : ImportUiEvent()
    data class ImportFromText(val text: String) : ImportUiEvent()
    data object SyncTorBox : ImportUiEvent()
    data object ImportAppleMusicLibrary : ImportUiEvent()
    data class SelectBatch(val batchId: Long) : ImportUiEvent()
    data class DownloadTrack(val batchId: Long, val trackId: Long) : ImportUiEvent()
}

/**
 * Owns all import flows: device library scan, platform link import,
 * Soundiiz text import, TorBox sync, Apple Music, and download-to-library.
 *
 * Previously embedded in [MainViewModel]; extracted for single-responsibility.
 */
@HiltViewModel
class ImportViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val container: AppContainer
) : ViewModel() {

    private val _uiState = MutableStateFlow(ImportUiState())
    val uiState: StateFlow<ImportUiState> = _uiState.asStateFlow()

    init { refreshBatches() }

    fun onEvent(event: ImportUiEvent) {
        when (event) {
            is ImportUiEvent.ScanDeviceLibrary -> scanDevice(event.uri)
            is ImportUiEvent.ImportFromText -> importFromText(event.text)
            is ImportUiEvent.SyncTorBox -> syncTorBox()
            is ImportUiEvent.ImportAppleMusicLibrary -> importAppleMusicLibrary()
            is ImportUiEvent.SelectBatch -> selectBatch(event.batchId)
            is ImportUiEvent.DownloadTrack -> { /* handled externally via downloadTrack */ }
        }
    }

    private fun refreshBatches() {
        viewModelScope.launch(Dispatchers.IO) {
            val batches = container.localLibraryRepository.importBatchesSnapshot()
            _uiState.update { it.copy(importBatches = batches) }
        }
    }

    private fun selectBatch(batchId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            val tracks = container.localLibraryRepository.importedTracksByBatchSnapshot(batchId)
            _uiState.update { it.copy(activeImportBatchId = batchId, activeImportedTracks = tracks) }
        }
    }

    private fun scanDevice(uri: Uri?) {
        if (!MediaPermissions.hasAudioPermission(appContext)) {
            _uiState.update { it.copy(statusMessage = "Storage permission required to scan device music.") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isScanning = true, scanProgress = "Scanning device library\u2026", statusMessage = null) }
            try {
                val result = withContext(Dispatchers.IO) {
                    if (uri != null) {
                        val imported = container.localMediaImporter.importSingleUri(uri)
                        com.audiophile.musicplayer.data.local.LocalImportResult(scannedCount = 1, importedCount = if (imported) 1 else 0)
                    } else {
                        container.localMediaImporter.importDeviceLibrary { scanned, imported ->
                            viewModelScope.launch {
                                _uiState.update { it.copy(scanProgress = "Scanned $scanned songs, imported $imported\u2026") }
                            }
                        }
                    }
                }
                val msg = if (result.scannedCount == 0 && result.importedCount == 0) {
                    "No device songs found. Check storage permission and try again."
                } else {
                    "Imported ${result.importedCount} tracks from ${result.scannedCount} scanned device songs"
                }
                _uiState.update { it.copy(isScanning = false, scanProgress = null, statusMessage = msg) }
                refreshBatches()
            } catch (e: SecurityException) {
                VantaLogger.e(VantaLogger.Tag.IMPORT, "scan_denied", e)
                _uiState.update { it.copy(isScanning = false, scanProgress = null, statusMessage = "Storage permission required to scan device music.") }
            } catch (e: Exception) {
                VantaLogger.e(VantaLogger.Tag.IMPORT, "scan_failed", e)
                _uiState.update { it.copy(isScanning = false, scanProgress = null, statusMessage = "Scan failed: ${e.message}") }
            }
        }
    }

private fun importFromText(text: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(statusMessage = "Parsing import text\u2026") }
            try {
                val batchId = withContext(Dispatchers.IO) {
                    container.libraryImporter.importPastedText("Pasted Import", text)
                }
                _uiState.update { it.copy(statusMessage = "Created import batch #$batchId") }
                refreshBatches()
            } catch (e: Exception) {
                VantaLogger.e(VantaLogger.Tag.IMPORT, "text_import_failed", e)
                _uiState.update { it.copy(statusMessage = "Import failed: ${e.message}") }
            }
        }
    }

    /** Imports Spotify Extended Streaming History into ListeningHistory (local only). */
    fun importSpotifyHistory(uri: Uri) {
        viewModelScope.launch {
            _uiState.update { it.copy(statusMessage = "Reading Spotify streaming history\u2026") }
            try {
                val result = withContext(Dispatchers.IO) {
                    val rows = appContext.contentResolver.openInputStream(uri)?.use {
                        SpotifyExportImporter.extractHistoryRows(it)
                    }.orEmpty()
                    val entries = rows.mapNotNull { row ->
                        runCatching {
                            ListeningHistoryEntity(
                                startedAt = row.ts,
                                title = row.trackName,
                                artist = row.artistName,
                                album = row.albumName,
                                platform = "SPOTIFY",
                                sourceTrackId = row.spotifyTrackUri,
                                msPlayed = row.msPlayed,
                                skipped = row.skipped,
                                reasonStart = row.reasonStart,
                                reasonEnd = row.reasonEnd
                            )
                        }.getOrNull()
                    }
                    container.listeningHistoryRepository.recordAll(entries)
                    rows.size to entries.size
                }
                val (read, saved) = result
                _uiState.update {
                    it.copy(
                        statusMessage = if (read == 0) {
                            "No streaming history found in that file. This import needs the Extended Streaming History ZIP from your Spotify account (Stored streaming + playlists)."
                        } else {
                            "Imported $saved listening rows from Spotify history ($read parsed)"
                        }
                    )
                }
            } catch (e: Exception) {
                VantaLogger.e(VantaLogger.Tag.IMPORT, "spotify_history_failed", e)
                _uiState.update { it.copy(statusMessage = "Spotify history import failed: ${e.message}") }
            }
        }
    }

    private fun syncTorBox() {
        viewModelScope.launch {
            val token = container.resolverConfigStore.getTorBoxApiToken().orEmpty()
            if (token.isBlank()) {
                _uiState.update { it.copy(statusMessage = "Connect an advanced source token first") }
                return@launch
            }
            _uiState.update { it.copy(statusMessage = "Syncing advanced source\u2026") }
            val imported = withContext(Dispatchers.IO) {
                val candidates = container.torBoxRepository.importableAudioFiles(token)
                var count = 0
                candidates.forEach { candidate ->
                    val streamUrl = candidate.file.downloadUrl
                        ?: container.torBoxRepository.getDirectStreamUrlForTorrent(
                            bearerToken = token,
                            torrentId = candidate.torrentId,
                            fileId = candidate.file.fileId
                        ) ?: return@forEach
                    val raw = candidate.file.name
                    val dashIdx = raw.lastIndexOf(" - ")
                    val artist = if (dashIdx > 0) raw.substring(0, dashIdx).trim() else "Unknown"
                    val title = if (dashIdx > 0) raw.substring(dashIdx + 3).removeSuffix(".mp3").removeSuffix(".flac").trim() else raw
                    container.trackRepository.addTrackSource(
                        title = title,
                        artist = artist,
                        album = candidate.torrentName,
                        coverArtUrl = null,
                        sourceType = SourceType.TORBOX,
                        streamUrl = streamUrl,
                        bitrate = 320
                    )
                    count++
                }
                count
            }
            _uiState.update { it.copy(statusMessage = "Imported $imported advanced source audio file(s)") }
            refreshBatches()
        }
    }

    private fun importAppleMusicLibrary() {
        val userToken = container.connectedLibraryTokenStore.musicUserToken(
            com.audiophile.musicplayer.data.connectors.ConnectedLibraryProvider.APPLE_MUSIC
        )
        val devToken = container.connectedLibraryTokenStore.accessToken(
            com.audiophile.musicplayer.data.connectors.ConnectedLibraryProvider.APPLE_MUSIC
        ) ?: container.resolverConfigStore.getAppleMusicDeveloperToken()
        if (devToken.isNullOrBlank() || userToken.isNullOrBlank()) {
            _uiState.update { it.copy(statusMessage = "Connect Apple Music in Settings first") }
            return
        }
        val account = com.audiophile.musicplayer.data.connectors.ConnectedLibraryAccount(
            id = "apple_music_user",
            provider = com.audiophile.musicplayer.data.connectors.ConnectedLibraryProvider.APPLE_MUSIC,
            displayName = container.accountManager.profile.value.displayName,
            accountId = "apple_music_user",
            connectedAt = System.currentTimeMillis(),
            scopesGranted = setOf("dev:$devToken", "user:$userToken")
        )
        viewModelScope.launch {
            _uiState.update { it.copy(statusMessage = "Starting Apple Music import\u2026") }
            try {
                var totalImported = 0
                var cursor: String? = null
                val request = com.audiophile.musicplayer.data.connectors.ConnectedLibraryImportRequest(
                    provider = com.audiophile.musicplayer.data.connectors.ConnectedLibraryProvider.APPLE_MUSIC
                )
                var batchId: Long? = null
                do {
                    val page = container.appleMusicLibraryConnector.fetchLibraryPage(account, request, cursor)
                    if (page.tracks.isNotEmpty()) {
                        if (batchId == null) {
                            batchId = container.localLibraryRepository.saveImportBatch(
                                com.audiophile.musicplayer.data.local.entities.ImportBatchEntity(
                                    name = "Apple Music Import",
                                    sourceName = "Apple Music",
                                    importedAt = System.currentTimeMillis(),
                                    totalTracks = 0,
                                    matchedCount = 0,
                                    needsReviewCount = 0,
                                    notFoundCount = 0
                                )
                            )
                        }
                        val mapped = page.tracks.map {
                            ImportedTrackEntity(
                                batchId = batchId,
                                rawText = "${it.title} - ${it.artist}",
                                parsedTitle = it.title,
                                parsedArtist = it.artist,
                                parsedAlbum = it.album,
                                sourceUrl = it.artworkUrl,
                                matchStatus = ImportMatchStatus.MATCHED,
                                playabilityStatus = PlayabilityStatus.METADATA_ONLY,
                                matchConfidence = MatchConfidence.MEDIUM,
                                matchReason = "Imported from Apple Music",
                                friendlySourceLabel = "Apple Music",
                                matchedSongId = null,
                                confidenceScore = 0.5f
                            )
                        }
                        container.localLibraryRepository.saveImportedTracks(mapped)
                        totalImported += mapped.size
                        _uiState.update { it.copy(statusMessage = "Imported $totalImported tracks\u2026") }
                    }
                    cursor = page.nextCursor
                } while (cursor != null)
                _uiState.update { it.copy(statusMessage = "Import complete! $totalImported tracks added.") }
                refreshBatches()
            } catch (e: Exception) {
                VantaLogger.e(VantaLogger.Tag.IMPORT, "apple_music_import_failed", e)
                _uiState.update { it.copy(statusMessage = "Import failed: ${e.message}") }
            }
        }
    }

    /** Resume any pending managed downloads on startup. */
    fun resumePendingDownloads(onRefreshNeeded: () -> Unit) {
        container.downloadManager.pendingDownloadIds().forEach { downloadId ->
            monitorDownload(downloadId, "downloaded track", onRefreshNeeded)
        }
    }

    fun downloadTrack(
        track: com.audiophile.musicplayer.data.local.entities.UnifiedTrackWithSources,
        onRefreshNeeded: () -> Unit
    ) {
        viewModelScope.launch {
            val bestSource = withContext(Dispatchers.IO) {
                container.trackRepository
                    .getBestQualitySourcesForTrack(track.track.trackId)
                    .orEmpty()
                    .firstOrNull()
            }
            if (bestSource == null) {
                _uiState.update { it.copy(statusMessage = "No downloadable source found for ${track.track.title}") }
                return@launch
            }
            val result = withContext(Dispatchers.IO) {
                container.downloadManager.enqueueTrackDownload(track.track, bestSource)
            }
            _uiState.update { it.copy(statusMessage = "Downloading: ${result.fileName}") }
            monitorDownload(result.downloadId, result.fileName, onRefreshNeeded)
        }
    }

    private fun monitorDownload(downloadId: Long, displayName: String, onRefreshNeeded: () -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            while (true) {
                val snapshot = container.downloadManager.getDownloadSnapshot(downloadId)
                when (snapshot?.status) {
                    DownloadManager.STATUS_SUCCESSFUL -> {
                        val uri = container.downloadManager.getDownloadedFileUri(downloadId)
                        val imported = uri != null && container.localMediaImporter.importSingleUri(uri)
                        container.downloadManager.markDownloadHandled(downloadId)
                        val msg = if (imported) "$displayName saved to VANTA Library"
                                  else "$displayName downloaded, but library import failed"
                        _uiState.update { it.copy(statusMessage = msg) }
                        if (imported) onRefreshNeeded()
                        return@launch
                    }
                    DownloadManager.STATUS_FAILED -> {
                        container.downloadManager.markDownloadHandled(downloadId)
                        _uiState.update { it.copy(statusMessage = "Download failed for $displayName") }
                        return@launch
                    }
                    null -> { /* waiting for system */ }
                }
                delay(1_500L)
            }
        }
    }
}
