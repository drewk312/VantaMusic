package com.audiophile.musicplayer.ui

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.audiophile.musicplayer.AppContainer
import com.audiophile.musicplayer.common.VantaLogger
import com.audiophile.musicplayer.common.VantaResult
import com.audiophile.musicplayer.data.catalog.ArtistCatalog
import com.audiophile.musicplayer.data.catalog.AlbumCatalog
import com.audiophile.musicplayer.data.local.LibraryCounts
import com.audiophile.musicplayer.data.local.entities.Album
import com.audiophile.musicplayer.data.local.entities.LocalSongEntity
import com.audiophile.musicplayer.data.local.entities.UnifiedTrackWithSources
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class LibraryUiState(
    val library: List<UnifiedTrackWithSources> = emptyList(),
    val localSongs: List<LocalSongEntity> = emptyList(),
    val libraryAlbums: List<Album> = emptyList(),
    val libraryCounts: LibraryCounts = LibraryCounts(
        songsCount = 0,
        favoritesCount = 0,
        recentlyPlayedCount = 0,
        playlistsCount = 0,
        importsCount = 0
    ),
    val artistCatalog: ArtistCatalog? = null,
    val artistCatalogLoading: Boolean = false,
    val albumCatalog: AlbumCatalog? = null,
    val albumCatalogLoading: Boolean = false,
    val isScanningDevice: Boolean = false,
    val scanProgress: String? = null,
    val isRefreshing: Boolean = false,
    val needsRefresh: Boolean = false,
    val statusMessage: String? = null,
)

sealed class LibraryUiEvent {
    data object Refresh : LibraryUiEvent()
    data class LoadArtistCatalog(val artistName: String) : LibraryUiEvent()
    data class LoadAlbumCatalog(val albumName: String, val artistName: String) : LibraryUiEvent()
    data object ScanDeviceLibrary : LibraryUiEvent()
    data class ToggleFavorite(val trackId: Long) : LibraryUiEvent()
}

/**
 * Manages library data: unified tracks, local songs, albums, artist/album catalog
 * browsing, and device-library scanning.
 *
 * Previously embedded in [MainViewModel]; extracted for single-responsibility.
 */
@HiltViewModel
class LibraryViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val container: AppContainer
) : ViewModel() {

    private val _uiState = MutableStateFlow(LibraryUiState())
    val uiState: StateFlow<LibraryUiState> = _uiState.asStateFlow()

    private val refreshMutex = Mutex()
    private var artistCatalogRequest: String? = null
    private var albumCatalogRequest: String? = null

    init {
        refresh()
    }

    fun onEvent(event: LibraryUiEvent) {
        when (event) {
            is LibraryUiEvent.Refresh -> refresh()
            is LibraryUiEvent.LoadArtistCatalog -> loadArtistCatalog(event.artistName)
            is LibraryUiEvent.LoadAlbumCatalog -> loadAlbumCatalog(event.albumName, event.artistName)
            is LibraryUiEvent.ScanDeviceLibrary -> scanDeviceLibrary()
            is LibraryUiEvent.ToggleFavorite -> toggleFavorite(event.trackId)
        }
    }

    fun refresh(skipRoomMaterialize: Boolean = false) {
        viewModelScope.launch {
            if (_uiState.value.isScanningDevice) return@launch
            refreshMutex.withLock {
                try {
                    _uiState.update { it.copy(isRefreshing = true) }
                    val library = withContext(Dispatchers.IO) {
                        container.registerConfiguredProviders()
                        container.trackRepository.getAllTracks()
                    }
                    val localSongs = withContext(Dispatchers.IO) {
                        container.localLibraryRepository.allSongsSnapshot()
                    }
                    val libraryAlbums = withContext(Dispatchers.IO) {
                        container.trackRepository.getAllAlbumsWithTracks().map { it.album }
                    }
                    val counts = withContext(Dispatchers.IO) {
                        container.localLibraryRepository.libraryCountsSnapshot()
                    }
                    _uiState.update {
                        it.copy(
                            library = library,
                            localSongs = localSongs,
                            libraryAlbums = libraryAlbums,
                            libraryCounts = counts,
                            isRefreshing = false,
                            needsRefresh = false
                        )
                    }
                    VantaLogger.d(VantaLogger.Tag.LIBRARY, "refresh_ok size=${library.size}")
                } catch (e: Exception) {
                    VantaLogger.e(VantaLogger.Tag.LIBRARY, "refresh_failed", e)
                    _uiState.update {
                        it.copy(
                            isRefreshing = false,
                            statusMessage = "Library refresh failed: ${e.message}"
                        )
                    }
                }
            }
        }
    }

    private fun loadArtistCatalog(artistName: String) {
        val clean = artistName.trim()
        if (clean.isBlank()) return
        if (artistCatalogRequest.equals(clean, ignoreCase = true) &&
            (_uiState.value.artistCatalogLoading ||
             _uiState.value.artistCatalog?.artist?.name.equals(clean, ignoreCase = true))) return
        artistCatalogRequest = clean
        _uiState.update { it.copy(artistCatalog = null, artistCatalogLoading = true) }
        viewModelScope.launch {
            val catalog = withContext(Dispatchers.IO) {
                container.catalogBrowseRepository.browseArtist(clean, limit = 50)
            }
            if (artistCatalogRequest.equals(clean, ignoreCase = true)) {
                _uiState.update { it.copy(artistCatalog = catalog, artistCatalogLoading = false) }
            }
        }
    }

    private fun loadAlbumCatalog(albumName: String, artistName: String) {
        val request = "${albumName.trim().lowercase()}|${artistName.trim().lowercase()}"
        if (albumName.isBlank()) return
        if (albumCatalogRequest == request &&
            (_uiState.value.albumCatalogLoading ||
             _uiState.value.albumCatalog?.album?.title.equals(albumName, ignoreCase = true))) return
        albumCatalogRequest = request
        _uiState.update { it.copy(albumCatalog = null, albumCatalogLoading = true) }
        viewModelScope.launch {
            val catalog = withContext(Dispatchers.IO) {
                container.catalogBrowseRepository.browseAlbum(albumName, artistName)
            }
            if (albumCatalogRequest == request) {
                _uiState.update { it.copy(albumCatalog = catalog, albumCatalogLoading = false) }
            }
        }
    }

    private fun scanDeviceLibrary() {
        if (_uiState.value.isScanningDevice) return
        viewModelScope.launch {
            _uiState.update { it.copy(isScanningDevice = true, scanProgress = "Scanning device library\u2026") }
            try {
                withContext(Dispatchers.IO) {
                    container.localMediaImporter.importDeviceLibrary { scanned, imported ->
                        viewModelScope.launch {
                            _uiState.update { it.copy(scanProgress = "Scanned $scanned, imported $imported\u2026") }
                        }
                    }
                }
                val counts = withContext(Dispatchers.IO) {
                    container.localLibraryRepository.libraryCountsSnapshot()
                }
                _uiState.update {
                    it.copy(
                        isScanningDevice = false,
                        scanProgress = null,
                        libraryCounts = counts,
                        needsRefresh = true
                    )
                }
                refresh()
            } catch (e: Exception) {
                VantaLogger.e(VantaLogger.Tag.LIBRARY, "scan_failed", e)
                _uiState.update {
                    it.copy(
                        isScanningDevice = false,
                        scanProgress = null,
                        statusMessage = "Device scan failed: ${e.message}"
                    )
                }
            }
        }
    }

    private fun toggleFavorite(trackId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                container.localLibraryRepository.toggleFavorite(trackId)
                refresh(skipRoomMaterialize = true)
            } catch (e: Exception) {
                VantaLogger.e(VantaLogger.Tag.LIBRARY, "toggle_favorite_failed trackId=$trackId", e)
            }
        }
    }
}
