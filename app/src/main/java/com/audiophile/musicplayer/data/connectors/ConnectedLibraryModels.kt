package com.audiophile.musicplayer.data.connectors

enum class ConnectedLibraryProvider {
    APPLE_MUSIC,
    SPOTIFY
}

enum class ConnectedLibraryTokenStatus {
    DISCONNECTED,
    VALID,
    EXPIRED,
    NEEDS_REAUTH,
    ERROR
}

enum class ConnectedLibraryMatchStatus {
    MATCHED_CANONICAL,
    MATCHED_LOCAL,
    METADATA_ONLY,
    NEEDS_REVIEW,
    REJECTED_WRONG_VERSION
}

enum class ConnectedTasteSignalType {
    ARTIST_AFFINITY,
    ALBUM_AFFINITY,
    GENRE_AFFINITY,
    ERA_AFFINITY,
    MOOD_TAG,
    PLAYLIST_THEME,
    FAMILIAR_ANCHOR
}

enum class ProviderSyncActionType {
    SAVE_LIKE,
    EXPORT_PLAYLIST_TRACK,
    CREATE_PLAYLIST,
    UPDATE_PLAYLIST
}

enum class ProviderSyncStatus {
    QUEUED,
    WAITING_FOR_PROVIDER_MATCH,
    NEEDS_REAUTH,
    RATE_LIMITED,
    COMPLETED,
    FAILED
}

data class ConnectedLibraryAccount(
    val id: String,
    val provider: ConnectedLibraryProvider,
    val displayName: String,
    val accountId: String,
    val connectedAt: Long,
    val lastImportAt: Long? = null,
    val tokenStatus: ConnectedLibraryTokenStatus = ConnectedLibraryTokenStatus.DISCONNECTED,
    val scopesGranted: Set<String> = emptySet(),
    val importEnabled: Boolean = true,
    val syncLikesEnabled: Boolean = false,
    val syncPlaylistsEnabled: Boolean = false
)

data class ImportedLibraryTrack(
    val id: String,
    val provider: ConnectedLibraryProvider,
    val providerTrackId: String,
    val providerAlbumId: String? = null,
    val providerArtistIds: List<String> = emptyList(),
    val providerUri: String? = null,
    val title: String,
    val artist: String,
    val album: String? = null,
    val durationMs: Long? = null,
    val isrc: String? = null,
    val artworkUrl: String? = null,
    val explicit: Boolean? = null,
    val addedAt: Long? = null,
    val playlistIds: List<String> = emptyList(),
    val importedAt: Long,
    val matchStatus: ConnectedLibraryMatchStatus = ConnectedLibraryMatchStatus.METADATA_ONLY,
    val vantaCanonicalTrackId: String? = null,
    val vantaLocalTrackId: Long? = null,
    val matchConfidence: Float = 0f
)

data class ImportedPlaylist(
    val id: String,
    val provider: ConnectedLibraryProvider,
    val providerPlaylistId: String,
    val name: String,
    val ownerName: String? = null,
    val trackCount: Int,
    val artworkUrl: String? = null,
    val importedAt: Long,
    val isEditableByUser: Boolean = false
)

data class ProviderTrackLink(
    val id: String,
    val provider: ConnectedLibraryProvider,
    val providerTrackId: String,
    val providerUri: String? = null,
    val isrc: String? = null,
    val vantaCanonicalTrackId: String? = null,
    val vantaLocalTrackId: Long? = null,
    val matchConfidence: Float,
    val linkedAt: Long
) {
    init {
        require(!providerTrackId.startsWith("http", ignoreCase = true)) {
            "Provider track links are metadata identities, not stream URLs."
        }
    }
}

data class ConnectedTasteSignal(
    val provider: ConnectedLibraryProvider,
    val signalType: ConnectedTasteSignalType,
    val artist: String? = null,
    val title: String? = null,
    val album: String? = null,
    val genre: String? = null,
    val era: String? = null,
    val moodTag: String? = null,
    val sourcePlaylistName: String? = null,
    val strength: Float,
    val importedAt: Long
)

data class ProviderSyncAction(
    val id: String,
    val provider: ConnectedLibraryProvider,
    val actionType: ProviderSyncActionType,
    val vantaTrackId: Long,
    val providerTrackId: String? = null,
    val providerPlaylistId: String? = null,
    val status: ProviderSyncStatus = ProviderSyncStatus.QUEUED,
    val attemptCount: Int = 0,
    val lastError: String? = null,
    val createdAt: Long,
    val completedAt: Long? = null
)

data class ConnectedLibraryImportRequest(
    val provider: ConnectedLibraryProvider,
    val importSavedTracks: Boolean = true,
    val importPlaylists: Boolean = true,
    val importPlaylistTracks: Boolean = true
)

data class ConnectedLibraryImportPage(
    val tracks: List<ImportedLibraryTrack> = emptyList(),
    val playlists: List<ImportedPlaylist> = emptyList(),
    val nextCursor: String? = null
)

data class ConnectedLibraryImportSummary(
    val provider: ConnectedLibraryProvider,
    val tracksImported: Int,
    val playlistsImported: Int,
    val matchedToVanta: Int,
    val unmatchedMetadataOnly: Int,
    val artworkFound: Int,
    val errors: List<String>,
    val lastImportAt: Long
)
