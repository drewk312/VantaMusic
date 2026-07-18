package com.audiophile.musicplayer.social

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

/**
 * Local storage for friend list and cached friend activity feed.
 *
 * Uses plain SharedPreferences for the friend graph (non-sensitive) and keeps a
 * small in-memory snapshot so the HomeScreen feed can render immediately. The
 * actual listening events are fetched/pushed through the gateway.
 */
class FriendActivityLocalSource(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("vanta_social", Context.MODE_PRIVATE)

    private val _friendFeed = MutableStateFlow(loadFeed())
    val friendFeed: StateFlow<FriendFeed> = _friendFeed.asStateFlow()

    fun addOrUpdateFriend(friend: VantaFriend) {
        val current = loadFriends().toMutableList()
        current.removeAll { it.id == friend.id }
        current.add(friend)
        saveFriends(current)
        _friendFeed.value = loadFeed()
    }

    fun removeFriend(friendId: String) {
        val current = loadFriends().filterNot { it.id == friendId }
        saveFriends(current)
        _friendFeed.value = loadFeed()
    }

    fun updateEvents(events: List<FriendListeningEvent>) {
        val friends = sortFriends(loadFriends())
        val feed = FriendFeed(
            friends = friends,
            events = events.sortedByDescending { it.startedAtMs },
            updatedAtMs = System.currentTimeMillis()
        )
        saveFeed(feed)
        _friendFeed.value = feed
    }

    fun updateOwnActivity(event: FriendListeningEvent) {
        // Own activity is stored separately so it can be included in the feed preview.
        prefs.edit {
            putString(KEY_OWN_ACTIVITY, event.toJson().toString())
        }
    }

    fun clearOwnActivity() {
        prefs.edit { remove(KEY_OWN_ACTIVITY) }
    }

    private fun loadFriends(): List<VantaFriend> {
        val raw = prefs.getString(KEY_FRIENDS, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            List(array.length()) { index ->
                val obj = array.getJSONObject(index)
                VantaFriend(
                    id = obj.getString("id"),
                    displayName = obj.getString("displayName"),
                    avatarSeed = obj.optString("avatarSeed", obj.getString("displayName")),
                    friendCode = obj.optString("friendCode").takeIf { it.isNotBlank() },
                    privacy = runCatching {
                        FriendPrivacy.valueOf(obj.optString("privacy", FriendPrivacy.SHARE_ALL.name))
                    }.getOrDefault(FriendPrivacy.SHARE_ALL),
                    isFavorite = obj.optBoolean("isFavorite", false),
                    addedAtMs = obj.optLong("addedAtMs", System.currentTimeMillis()),
                    isOnline = obj.optBoolean("isOnline", false),
                    lastSeenAtMs = obj.optLong("lastSeenAtMs", 0L),
                    likedTracks = loadLikedTracks(obj.getString("id"))
                )
            }
        }.getOrDefault(emptyList())
    }

    private fun saveFriends(friends: List<VantaFriend>) {
        val array = JSONArray()
        friends.forEach { friend ->
            array.put(
                JSONObject().apply {
                    put("id", friend.id)
                    put("displayName", friend.displayName)
                    put("avatarSeed", friend.avatarSeed)
                    put("friendCode", friend.friendCode)
                    put("privacy", friend.privacy.name)
                    put("isFavorite", friend.isFavorite)
                    put("addedAtMs", friend.addedAtMs)
                    put("isOnline", friend.isOnline)
                    put("lastSeenAtMs", friend.lastSeenAtMs)
                }
            )
        }
        prefs.edit { putString(KEY_FRIENDS, array.toString()) }
    }

    fun addLikedTrack(friendId: String, track: FriendLikedTrack) {
        val tracks = loadLikedTracks(friendId).toMutableList()
        tracks.removeAll { it.trackId == track.trackId }
        tracks.add(0, track)
        saveLikedTracks(friendId, tracks)
        _friendFeed.value = loadFeed()
    }

    fun removeLikedTrack(friendId: String, trackId: String) {
        val tracks = loadLikedTracks(friendId).filterNot { it.trackId == trackId }
        saveLikedTracks(friendId, tracks)
        _friendFeed.value = loadFeed()
    }

    fun updateFriendOnlineStatus(friendId: String, isOnline: Boolean, lastSeenAtMs: Long) {
        val friends = loadFriends().toMutableList()
        val index = friends.indexOfFirst { it.id == friendId }
        if (index >= 0) {
            friends[index] = friends[index].copy(isOnline = isOnline, lastSeenAtMs = lastSeenAtMs)
            saveFriends(friends)
            _friendFeed.value = loadFeed()
        }
    }

    private fun loadLikedTracks(friendId: String): List<FriendLikedTrack> {
        val raw = prefs.getString("$KEY_LIKED_TRACKS_PREFIX$friendId", null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            List(array.length()) { index ->
                val obj = array.getJSONObject(index)
                FriendLikedTrack(
                    trackId = obj.getString("trackId"),
                    title = obj.getString("title"),
                    artist = obj.getString("artist"),
                    album = obj.optString("album").takeIf { it.isNotBlank() },
                    artworkUrl = obj.optString("artworkUrl").takeIf { it.isNotBlank() },
                    likedAtMs = obj.optLong("likedAtMs", System.currentTimeMillis())
                )
            }
        }.getOrDefault(emptyList())
    }

    private fun saveLikedTracks(friendId: String, tracks: List<FriendLikedTrack>) {
        val array = JSONArray()
        tracks.forEach { track ->
            array.put(
                JSONObject().apply {
                    put("trackId", track.trackId)
                    put("title", track.title)
                    put("artist", track.artist)
                    put("album", track.album)
                    put("artworkUrl", track.artworkUrl)
                    put("likedAtMs", track.likedAtMs)
                }
            )
        }
        prefs.edit { putString("$KEY_LIKED_TRACKS_PREFIX$friendId", array.toString()) }
    }

    private fun loadFeed(): FriendFeed {
        val friends = sortFriends(loadFriends())
        val raw = prefs.getString(KEY_EVENTS, null)
        val events = runCatching {
            if (raw.isNullOrBlank()) return@runCatching emptyList()
            val array = JSONArray(raw)
            List(array.length()) { index ->
                val obj = array.getJSONObject(index)
                FriendListeningEvent(
                    friendId = obj.getString("friendId"),
                    friendDisplayName = obj.getString("friendDisplayName"),
                    trackId = obj.getString("trackId"),
                    title = obj.getString("title"),
                    artist = obj.getString("artist"),
                    album = obj.optString("album").takeIf { it.isNotBlank() },
                    artworkUrl = obj.optString("artworkUrl").takeIf { it.isNotBlank() },
                    sourceLabel = obj.optString("sourceLabel").takeIf { it.isNotBlank() },
                    startedAtMs = obj.optLong("startedAtMs", 0L),
                    positionMs = obj.optLong("positionMs", 0L),
                    durationMs = obj.optLong("durationMs", 0L)
                )
            }
        }.getOrDefault(emptyList())
        return FriendFeed(friends, events, System.currentTimeMillis())
    }

    private fun sortFriends(friends: List<VantaFriend>): List<VantaFriend> =
        friends.sortedWith(
            compareByDescending<VantaFriend> { it.isFavorite }
                .thenByDescending { it.isOnline }
                .thenByDescending { it.lastSeenAtMs }
                .thenBy { it.displayName.lowercase() }
        )

    private fun saveFeed(feed: FriendFeed) {
        val array = JSONArray()
        feed.events.forEach { event ->
            array.put(event.toJson())
        }
        prefs.edit {
            putString(KEY_EVENTS, array.toString())
            putLong(KEY_UPDATED_AT, feed.updatedAtMs)
        }
    }

    private fun FriendListeningEvent.toJson(): JSONObject =
        JSONObject().apply {
            put("friendId", friendId)
            put("friendDisplayName", friendDisplayName)
            put("trackId", trackId)
            put("title", title)
            put("artist", artist)
            put("album", album)
            put("artworkUrl", artworkUrl)
            put("sourceLabel", sourceLabel)
            put("startedAtMs", startedAtMs)
            put("positionMs", positionMs)
            put("durationMs", durationMs)
        }

    /**
     * Rotate the opaque friend code for this user. Returns the new code and
     * persists it so existing friends can be re-resolved if needed.
     */
    fun rotateOwnFriendCode(userId: String): String {
        val code = generateFriendCode()
        prefs.edit { putString(KEY_OWN_FRIEND_CODE, code) }
        return code
    }

    fun getOwnFriendCode(userId: String): String {
        return prefs.getString(KEY_OWN_FRIEND_CODE, null) ?: rotateOwnFriendCode(userId)
    }

    private fun generateFriendCode(): String {
        val chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        return (1..8).map { chars.random() }.joinToString("")
    }

    companion object {
        private const val KEY_FRIENDS = "friends"
        private const val KEY_EVENTS = "events"
        private const val KEY_UPDATED_AT = "updated_at"
        private const val KEY_OWN_ACTIVITY = "own_activity"
        private const val KEY_LIKED_TRACKS_PREFIX = "liked_tracks_"
        private const val KEY_OWN_FRIEND_CODE = "own_friend_code"
    }
}



