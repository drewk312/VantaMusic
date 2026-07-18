package com.audiophile.musicplayer.social

/**
 * Privacy controls for each friendship. The owner decides what each friend can see.
 */
enum class FriendPrivacy {
    SHARE_ALL,
    SHARE_ACTIVITY_ONLY,
    SHARE_LIKES_ONLY,
    SHARE_NONE
}

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
    val friendCode: String? = null,
    val privacy: FriendPrivacy = FriendPrivacy.SHARE_ALL,
    val isFavorite: Boolean = false,
    val addedAtMs: Long = System.currentTimeMillis(),
    val isOnline: Boolean = false,
    val lastSeenAtMs: Long = 0L,
    val likedTracks: List<FriendLikedTrack> = emptyList()
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

data class FriendLikedTrack(
    val trackId: String,
    val title: String,
    val artist: String,
    val album: String? = null,
    val artworkUrl: String? = null,
    val likedAtMs: Long = System.currentTimeMillis()
)

data class FriendFeed(
    val friends: List<VantaFriend> = emptyList(),
    val events: List<FriendListeningEvent> = emptyList(),
    val updatedAtMs: Long = System.currentTimeMillis()
)
