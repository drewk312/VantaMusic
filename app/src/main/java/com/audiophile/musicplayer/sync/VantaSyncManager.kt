package com.audiophile.musicplayer.sync

import android.content.Context
import android.os.Build
import com.audiophile.musicplayer.BuildConfig
import com.audiophile.musicplayer.account.AccountManager
import com.audiophile.musicplayer.account.FirebaseIdTokenInterceptor
import com.audiophile.musicplayer.data.repository.TrackRepository
import com.audiophile.musicplayer.data.connectors.ConnectedLibraryProvider
import com.audiophile.musicplayer.data.connectors.ConnectedLibraryTokenStore
import com.audiophile.musicplayer.social.FriendListeningEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

/**
 * Orchestrates VANTA Sync: identity, library snapshot, and friend activity.
 *
 * Sync is opt-in and never blocks local playback. The manager creates a compact
 * library snapshot and pushes it to the gateway. It also fetches friend activity
 * so the HomeScreen "Friends Are Listening" row can be live.
 */
class VantaSyncManager(
    context: Context,
    private val accountManager: AccountManager,
    private val trackRepository: TrackRepository,
    private val connectedLibraryTokenStore: ConnectedLibraryTokenStore,
    private val identityStore: SyncIdentityStore = SyncIdentityStore(context),
    gatewayApi: VantaGatewayApi? = null
) {
    private val appContext = context.applicationContext
    private val gatewayApi = gatewayApi ?: createGatewayApi(appContext)

    val isSyncEnabled: Boolean
        get() = accountManager.profile.value.sourceSyncEnabled && accountManager.isSignedIn

    /**
     * Ensure the user has a stable VANTA ID. This is the iCloud-like anonymous identity
     * that follows the user even before they link an external account.
     */
    fun ensureIdentity(): SyncIdentity? {
        val userId = accountManager.cloudUserIdOrNull ?: return null
        val profile = accountManager.profile.value
        return SyncIdentity(
            vantaUserId = userId,
            displayName = profile.displayName.takeIf { it.isNotBlank() },
            email = profile.email.takeIf { it.isNotBlank() },
            linkedProviders = linkedProviders()
        )
    }

    /**
     * Build a library snapshot from the local Room database.
     */
    suspend fun buildLibrarySnapshot(): LibrarySnapshot? = withContext(Dispatchers.IO) {
        val identity = ensureIdentity() ?: return@withContext null
        val tracks = trackRepository.getAllTracks()
        val snapshotTracks = tracks.map { it.toLibrarySnapshotTrack() }
        LibrarySnapshot(
            vantaUserId = identity.vantaUserId,
            deviceName = deviceName(),
            tracks = snapshotTracks,
            likedTrackIds = snapshotTracks.filter { it.isFavorite }.map { it.vantaTrackId },
            recentPlayedTrackIds = snapshotTracks
                .filter { it.lastPlayedAtMs != null }
                .sortedByDescending { it.lastPlayedAtMs }
                .take(50)
                .map { it.vantaTrackId }
        )
    }

    /**
     * Push the library snapshot to the gateway. Returns a result describing the merge.
     */
    suspend fun pushLibrarySnapshot(): SyncPushResult = withContext(Dispatchers.IO) {
        val api = gatewayApi ?: return@withContext SyncPushResult()
        val snapshot = buildLibrarySnapshot() ?: return@withContext SyncPushResult()
        val dto = snapshot.toDto()
        val response = api.pushLibrarySnapshot(snapshot.vantaUserId, dto)
        if (response.isSuccessful) {
            val body = response.body()
            identityStore.storeLastSnapshotId(body?.snapshotId)
            identityStore.storeLastSyncedAtMs(System.currentTimeMillis())
            SyncPushResult(
                snapshotId = body?.snapshotId,
                serverTracksMerged = body?.serverTracksMerged ?: 0,
                conflicts = body?.conflicts ?: 0,
                nextSyncAtMs = body?.nextSyncAtMs
            )
        } else {
            SyncPushResult(conflicts = 0)
        }
    }

    /**
     * Fetch friend activity from the gateway.
     */
    suspend fun fetchFriendActivity(): List<FriendListeningEvent> = withContext(Dispatchers.IO) {
        val api = gatewayApi ?: return@withContext emptyList()
        val identity = ensureIdentity() ?: return@withContext emptyList()
        val response = runCatching { api.getActivityFeed(identity.vantaUserId) }.getOrNull()
            ?: return@withContext emptyList()
        if (response.isSuccessful) {
            response.body()?.events?.map { it.toFriendListeningEvent() } ?: emptyList()
        } else {
            emptyList()
        }
    }

    /**
     * Post the current user's listening activity to the gateway so friends can see it.
     * Fire-and-forget: failures are swallowed, sync must never affect playback.
     */
    suspend fun postOwnActivity(event: FriendListeningEvent): Boolean = withContext(Dispatchers.IO) {
        val api = gatewayApi ?: return@withContext false
        val identity = ensureIdentity() ?: return@withContext false
        val dto = ActivityEventDto(
            userId = identity.vantaUserId,
            displayName = identity.displayName,
            trackId = event.trackId,
            title = event.title,
            artist = event.artist,
            album = event.album,
            artworkUrl = event.artworkUrl,
            sourceLabel = event.sourceLabel,
            startedAtMs = event.startedAtMs,
            positionMs = event.positionMs,
            durationMs = event.durationMs
        )
        runCatching { api.postActivity(identity.vantaUserId, dto).isSuccessful }
            .getOrDefault(false)
    }

    /**
     * Register a friend on the gateway so their activity appears in the feed.
     * Best-effort; failures are swallowed.
     */
    suspend fun addFriendOnGateway(friendId: String): Boolean = withContext(Dispatchers.IO) {
        val api = gatewayApi ?: return@withContext false
        val identity = ensureIdentity() ?: return@withContext false
        val normalized = friendId.trim().lowercase()
        if (normalized.isBlank()) return@withContext false
        runCatching {
            api.addFriend(
                identity.vantaUserId,
                AddFriendRequestDto(friendId = normalized)
            ).isSuccessful
        }.getOrDefault(false)
    }

    /**
     * Fetch the server-side friend list. Returns empty on failure.
     */
    suspend fun fetchServerFriendIds(): List<String> = withContext(Dispatchers.IO) {
        val api = gatewayApi ?: return@withContext emptyList()
        val identity = ensureIdentity() ?: return@withContext emptyList()
        val response = runCatching { api.getFriends(identity.vantaUserId) }.getOrNull()
            ?: return@withContext emptyList()
        if (response.isSuccessful) {
            response.body()?.friendIds ?: emptyList()
        } else {
            emptyList()
        }
    }

    private fun deviceName(): String = runCatching {
        Build.MODEL ?: "Android Device"
    }.getOrDefault("Android Device")

    private fun linkedProviders(): List<String> {
        val providers = mutableListOf<String>()
        if (!connectedLibraryTokenStore.musicUserToken(ConnectedLibraryProvider.APPLE_MUSIC).isNullOrBlank()) {
            providers.add("apple_music")
        }
        if (!connectedLibraryTokenStore.accessToken(ConnectedLibraryProvider.SPOTIFY).isNullOrBlank()) {
            providers.add("spotify")
        }
        return providers
    }

    companion object {
        private fun createGatewayApi(context: Context): VantaGatewayApi? {
            val baseUrl = BuildConfig.STATION_BACKEND_URL.takeIf { it.isNotBlank() } ?: return null
            return try {
                val client = okhttp3.OkHttpClient.Builder()
                    .addInterceptor(FirebaseIdTokenInterceptor())
                    .addInterceptor(com.audiophile.musicplayer.playback.GatewayApiKeyInterceptor)
                    .build()
                Retrofit.Builder()
                    .baseUrl(baseUrl)
                    .client(client)
                    .addConverterFactory(GsonConverterFactory.create())
                    .build()
                    .create(VantaGatewayApi::class.java)
            } catch (e: Exception) {
                null
            }
        }
    }
}

private fun LibrarySnapshot.toDto(): com.audiophile.musicplayer.sync.LibrarySnapshotDto =
    LibrarySnapshotDto(
        vantaUserId = vantaUserId,
        deviceName = deviceName,
        version = version,
        generatedAtMs = generatedAtMs,
        tracks = tracks.map {
            LibrarySnapshotTrackDto(
                vantaTrackId = it.vantaTrackId,
                title = it.title,
                artist = it.artist,
                album = it.album,
                isrc = it.isrc,
                isFavorite = it.isFavorite,
                playCount = it.playCount,
                lastPlayedAtMs = it.lastPlayedAtMs,
                addedAtMs = it.addedAtMs,
                artworkUrl = it.artworkUrl,
                sourceProviderIds = it.sourceProviderIds
            )
        },
        likedTrackIds = likedTrackIds,
        recentPlayedTrackIds = recentPlayedTrackIds
    )

private fun com.audiophile.musicplayer.sync.ActivityEventDto.toFriendListeningEvent(): FriendListeningEvent =
    FriendListeningEvent(
        friendId = userId,
        friendDisplayName = displayName ?: userId,
        trackId = trackId,
        title = title,
        artist = artist,
        album = album,
        artworkUrl = artworkUrl,
        sourceLabel = sourceLabel,
        startedAtMs = startedAtMs,
        positionMs = positionMs,
        durationMs = durationMs
    )
