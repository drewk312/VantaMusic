package com.audiophile.musicplayer.sync

import com.audiophile.musicplayer.data.local.entities.UnifiedTrackWithSources

/**
 * Data models for VANTA Sync — library snapshots, identity, and device state.
 *
 * Sync is designed to be iCloud-like: anonymous by default, optional link to
 * Apple/Google/Spotify identity later. The library snapshot is a compact
 * manifest of tracks the user has added, liked, or played. It grows with the user.
 */
data class SyncIdentity(
    val vantaUserId: String,
    val displayName: String? = null,
    val email: String? = null,
    val linkedProviders: List<String> = emptyList(),
    val createdAtMs: Long = System.currentTimeMillis()
)

data class LibrarySnapshotTrack(
    val vantaTrackId: String,
    val title: String,
    val artist: String,
    val album: String? = null,
    val isrc: String? = null,
    val isFavorite: Boolean = false,
    val playCount: Int = 0,
    val lastPlayedAtMs: Long? = null,
    val addedAtMs: Long = System.currentTimeMillis(),
    val artworkUrl: String? = null,
    val sourceProviderIds: List<String> = emptyList()
)

data class LibrarySnapshot(
    val vantaUserId: String,
    val deviceName: String,
    val version: Int = 1,
    val generatedAtMs: Long = System.currentTimeMillis(),
    val tracks: List<LibrarySnapshotTrack> = emptyList(),
    val likedTrackIds: List<String> = emptyList(),
    val recentPlayedTrackIds: List<String> = emptyList()
)

/**
 * Minimal response from the sync gateway.
 */
data class SyncPushResult(
    val snapshotId: String? = null,
    val serverTracksMerged: Int = 0,
    val conflicts: Int = 0,
    val nextSyncAtMs: Long? = null
)

fun UnifiedTrackWithSources.toLibrarySnapshotTrack(): LibrarySnapshotTrack {
    return LibrarySnapshotTrack(
        vantaTrackId = track.trackId.toString(),
        title = track.title,
        artist = track.artist,
        album = track.albumName,
        isrc = track.isrc,
        lastPlayedAtMs = track.lastPlayedAt,
        artworkUrl = track.coverArtUrl,
        sourceProviderIds = sources.map { it.sourceType.name }.distinct()
    )
}
