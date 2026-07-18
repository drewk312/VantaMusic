package com.audiophile.musicplayer.sync

import com.google.gson.annotations.SerializedName
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * Retrofit interface for the VANTA cloud gateway.
 *
 * Endpoints:
 * - POST /sync/library/{userId}       push a library snapshot
 * - GET  /sync/library/{userId}       fetch merged library
 * - POST /sync/activity/{userId}      post own listening activity
 * - GET  /sync/activity/{userId}      fetch friend activity feed
 * - POST /sync/friends/{userId}       add a friend by id
 * - GET  /sync/friends/{userId}       list friend ids
 */
interface VantaGatewayApi {

    @POST("sync/library/{userId}")
    suspend fun pushLibrarySnapshot(
        @Path("userId") userId: String,
        @Body snapshot: LibrarySnapshotDto
    ): Response<SyncPushResponseDto>

    @GET("sync/library/{userId}")
    suspend fun getLibrarySnapshot(
        @Path("userId") userId: String,
        @Query("since") sinceMs: Long? = null
    ): Response<LibrarySnapshotDto>

    @POST("sync/activity/{userId}")
    suspend fun postActivity(
        @Path("userId") userId: String,
        @Body activity: ActivityEventDto
    ): Response<Unit>

    @GET("sync/activity/{userId}")
    suspend fun getActivityFeed(
        @Path("userId") userId: String,
        @Query("limit") limit: Int = 50
    ): Response<ActivityFeedDto>

    @POST("sync/friends/{userId}")
    suspend fun addFriend(
        @Path("userId") userId: String,
        @Body body: AddFriendRequestDto
    ): Response<FriendsListDto>

    @GET("sync/friends/{userId}")
    suspend fun getFriends(
        @Path("userId") userId: String
    ): Response<FriendsListDto>
}

data class LibrarySnapshotDto(
    @SerializedName("vantaUserId") val vantaUserId: String,
    @SerializedName("deviceName") val deviceName: String,
    @SerializedName("version") val version: Int = 1,
    @SerializedName("generatedAtMs") val generatedAtMs: Long,
    @SerializedName("tracks") val tracks: List<LibrarySnapshotTrackDto> = emptyList(),
    @SerializedName("likedTrackIds") val likedTrackIds: List<String> = emptyList(),
    @SerializedName("recentPlayedTrackIds") val recentPlayedTrackIds: List<String> = emptyList()
)

data class LibrarySnapshotTrackDto(
    @SerializedName("vantaTrackId") val vantaTrackId: String,
    @SerializedName("title") val title: String,
    @SerializedName("artist") val artist: String,
    @SerializedName("album") val album: String? = null,
    @SerializedName("isrc") val isrc: String? = null,
    @SerializedName("isFavorite") val isFavorite: Boolean = false,
    @SerializedName("playCount") val playCount: Int = 0,
    @SerializedName("lastPlayedAtMs") val lastPlayedAtMs: Long? = null,
    @SerializedName("addedAtMs") val addedAtMs: Long = System.currentTimeMillis(),
    @SerializedName("artworkUrl") val artworkUrl: String? = null,
    @SerializedName("sourceProviderIds") val sourceProviderIds: List<String> = emptyList()
)

data class SyncPushResponseDto(
    @SerializedName("snapshotId") val snapshotId: String? = null,
    @SerializedName("serverTracksMerged") val serverTracksMerged: Int = 0,
    @SerializedName("conflicts") val conflicts: Int = 0,
    @SerializedName("nextSyncAtMs") val nextSyncAtMs: Long? = null
)

data class ActivityEventDto(
    @SerializedName("userId") val userId: String,
    @SerializedName("displayName") val displayName: String? = null,
    @SerializedName("trackId") val trackId: String,
    @SerializedName("title") val title: String,
    @SerializedName("artist") val artist: String,
    @SerializedName("album") val album: String? = null,
    @SerializedName("artworkUrl") val artworkUrl: String? = null,
    @SerializedName("sourceLabel") val sourceLabel: String? = null,
    @SerializedName("startedAtMs") val startedAtMs: Long = System.currentTimeMillis(),
    @SerializedName("positionMs") val positionMs: Long = 0L,
    @SerializedName("durationMs") val durationMs: Long = 0L
)

data class ActivityFeedDto(
    @SerializedName("events") val events: List<ActivityEventDto> = emptyList(),
    @SerializedName("updatedAtMs") val updatedAtMs: Long = System.currentTimeMillis()
)

data class AddFriendRequestDto(
    @SerializedName("friendId") val friendId: String
)

data class FriendsListDto(
    @SerializedName("friendIds") val friendIds: List<String> = emptyList()
)
