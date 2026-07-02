package com.audiophile.musicplayer.social

/**
 * Data model for the "Friends Are Listening" feature.
 *
 * Friends are represented by a stable handle. The feed shows the most recent
 * listening event for each friend. All timestamps are UTC milliseconds.
 */
data class VantaFriend(
    val id: String,
    val displayName: String,
    val avatarSeed: String = displayName,
    val isFavorite: Boolean = false,
    val addedAtMs: Long = System.currentTimeMillis()
)

data class FriendListeningEvent(
    val friendId: String,
    val friendDisplayName: String,
    val trackId: String,
    val title: String,
    val artist: String,
    val album: String? = null,
    val artworkUrl: String? = null,
    val sourceLabel: String? = null,
    val startedAtMs: Long = System.currentTimeMillis(),
    val positionMs: Long = 0L,
    val durationMs: Long = 0L
)

data class FriendFeed(
    val friends: List<VantaFriend> = emptyList(),
    val events: List<FriendListeningEvent> = emptyList(),
    val updatedAtMs: Long = System.currentTimeMillis()
)
