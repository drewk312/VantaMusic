package com.audiophile.musicplayer.continuity

import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.util.Log
import android.app.UiModeManager
import com.audiophile.musicplayer.BuildConfig
import com.audiophile.musicplayer.account.AccountManager
import com.audiophile.musicplayer.account.FirebaseIdTokenInterceptor
import com.audiophile.musicplayer.data.local.entities.SourceType
import com.audiophile.musicplayer.data.repository.TrackRepository
import com.audiophile.musicplayer.playback.GatewayApiKeyInterceptor
import com.audiophile.musicplayer.playback.NowPlayingState
import com.audiophile.musicplayer.playback.PlaybackStateHolder
import com.audiophile.musicplayer.playback.PlayerController
import com.audiophile.musicplayer.sync.VantaGatewayApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

/**
 * Phone ↔ TV Continuity MVP.
 *
 * Independent by default. Cast = TV plays, phone pauses. Follow = both play in sync.
 * Each device resolves FLAC/Atmos locally from track identity (no shared stream URL).
 */
class ContinuityCoordinator(
    context: Context,
    private val accountManager: AccountManager,
    private val playbackState: PlaybackStateHolder,
    private val playerController: PlayerController,
    private val trackRepository: TrackRepository,
) {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences("vanta_continuity", Context.MODE_PRIVATE)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val api: VantaGatewayApi? = createGatewayApi()

    val deviceId: String = prefs.getString(KEY_DEVICE_ID, null) ?: java.util.UUID.randomUUID().toString().also {
        prefs.edit().putString(KEY_DEVICE_ID, it).apply()
    }

    val role: ContinuityRole = if (isTelevision()) ContinuityRole.TV else ContinuityRole.PHONE
    private val deviceName: String = Build.MODEL?.takeIf { it.isNotBlank() } ?: "VANTA"

    private val _snapshot = MutableStateFlow(ContinuitySnapshotDto())
    val snapshot: StateFlow<ContinuitySnapshotDto> = _snapshot.asStateFlow()

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

    private var loopJob: Job? = null
    private var lastAppliedSeq: Long = -1L
    private var lastPublishedTrackKey: String? = null
    private var suppressingLocalPublish = false

    fun start() {
        if (loopJob?.isActive == true) return
        loopJob = scope.launch {
            while (isActive) {
                runCatching { tick() }
                    .onFailure { err -> Log.d(TAG, "tick failed: ${err.message}") }
                delay(if (role == ContinuityRole.TV) 1_500L else 2_000L)
            }
        }
        Log.i(TAG, "started role=$role deviceId=${deviceId.take(8)}")
    }

    fun stop() {
        loopJob?.cancel()
        loopJob = null
    }

    fun onlineTvs(): List<ContinuityDeviceDto> =
        _snapshot.value.devices.filter {
            it.deviceId != deviceId && it.role.equals("tv", ignoreCase = true)
        }

    fun castTo(tvDeviceId: String) {
        scope.launch {
            val state = playbackState.snapshot()
            val ok = postSession(
                mode = "cast",
                leaderDeviceId = deviceId,
                followerDeviceIds = listOf(tvDeviceId),
                track = trackRefFrom(state),
                positionMs = state.positionMs,
                isPlaying = true
            )
            if (ok) {
                _statusMessage.value = "Casting to TV"
                // Phone becomes remote: pause local audio after handoff.
                withContext(Dispatchers.Main) { playerController.pause() }
            } else {
                _statusMessage.value = "Cast failed — sign in and open VANTA on TV"
            }
        }
    }

    fun listenTogether(tvDeviceId: String) {
        scope.launch {
            val state = playbackState.snapshot()
            val ok = postSession(
                mode = "follow",
                leaderDeviceId = deviceId,
                followerDeviceIds = listOf(tvDeviceId),
                track = trackRefFrom(state),
                positionMs = state.positionMs,
                isPlaying = state.isPlaying
            )
            _statusMessage.value = if (ok) "Listening together" else "Link failed — sign in on both devices"
        }
    }

    fun unlink() {
        scope.launch {
            postSession(
                mode = "independent",
                leaderDeviceId = null,
                followerDeviceIds = emptyList(),
                track = null,
                positionMs = 0L,
                isPlaying = false
            )
            _statusMessage.value = "Independent"
        }
    }

    private suspend fun tick() {
        val userId = accountManager.cloudUserIdOrNull ?: return
        if (api == null) return

        val heartbeat = api.postContinuity(
            userId,
            ContinuityPostBody(
                action = "heartbeat",
                deviceId = deviceId,
                role = role.name.lowercase(),
                name = deviceName
            )
        )
        if (heartbeat.isSuccessful) {
            heartbeat.body()?.let { _snapshot.value = it }
        }

        val snap = _snapshot.value
        val session = snap.session
        val mode = session.parsedMode()

        if (mode == ContinuityMode.INDEPENDENT) {
            lastAppliedSeq = session.seq
            return
        }

        val isLeader = session.leaderDeviceId == deviceId
        val isFollower = session.followerDeviceIds.contains(deviceId) ||
            (role == ContinuityRole.TV && !isLeader && mode != ContinuityMode.INDEPENDENT)

        if (isLeader) {
            publishLeaderState(userId, session)
        }
        if (isFollower) {
            applyFollowerState(session)
        }
    }

    private suspend fun publishLeaderState(userId: String, session: ContinuitySessionDto) {
        if (suppressingLocalPublish) return
        val state = playbackState.snapshot()
        val track = trackRefFrom(state) ?: session.track
        val trackKey = listOf(
            track?.trackId, track?.providerId, track?.externalTrackId, track?.title, track?.artist
        ).joinToString("|")
        // Always publish while cast/follow so TV keeps position.
        api?.postContinuity(
            userId,
            ContinuityPostBody(
                action = "session",
                deviceId = deviceId,
                mode = session.mode,
                leaderDeviceId = deviceId,
                followerDeviceIds = session.followerDeviceIds,
                track = track,
                positionMs = state.positionMs,
                // In cast mode phone may be paused locally; keep session playing for TV.
                isPlaying = if (session.parsedMode() == ContinuityMode.CAST) {
                    true
                } else {
                    state.isPlaying
                }
            )
        )?.body()?.let { _snapshot.value = it }
        lastPublishedTrackKey = trackKey
    }

    private suspend fun applyFollowerState(session: ContinuitySessionDto) {
        if (session.seq == lastAppliedSeq) {
            // Soft seek drift correction while same seq is rare; still nudge if far.
            return
        }
        val track = session.track ?: return
        val title = track.title?.trim().orEmpty()
        val artist = track.artist?.trim().orEmpty()
        if (title.isBlank()) return

        val current = playbackState.snapshot()
        val sameTrack = current.title.equals(title, ignoreCase = true) &&
            current.artist.equals(artist, ignoreCase = true)

        suppressingLocalPublish = true
        try {
            if (!sameTrack) {
                Log.i(TAG, "follower load title='$title' artist='$artist' mode=${session.mode}")
                playTrackRef(track, session.positionMs, session.isPlaying)
            } else {
                withContext(Dispatchers.Main) {
                    val drift = kotlin.math.abs(current.positionMs - session.positionMs)
                    if (drift > 1_500L) {
                        playerController.seekTo(session.positionMs)
                    }
                    if (session.isPlaying && !current.isPlaying) {
                        playerController.play()
                    } else if (!session.isPlaying && current.isPlaying) {
                        playerController.pause()
                    }
                }
            }
            lastAppliedSeq = session.seq
            _statusMessage.value = when (session.parsedMode()) {
                ContinuityMode.CAST -> "Playing from phone"
                ContinuityMode.FOLLOW -> "Listening together"
                ContinuityMode.INDEPENDENT -> null
            }
        } finally {
            suppressingLocalPublish = false
        }
    }

    private suspend fun playTrackRef(track: ContinuityTrackRefDto, positionMs: Long, play: Boolean) {
        val localId = track.trackId?.toLongOrNull()
        if (localId != null && localId > 0L) {
            val existing = trackRepository.getTrackWithSources(localId)
            if (existing != null) {
                withContext(Dispatchers.Main) {
                    playerController.playTrack(existing)
                    if (positionMs > 0L) playerController.seekTo(positionMs)
                    if (!play) playerController.pause()
                }
                return
            }
        }

        val providerId = track.providerId?.takeIf { it.isNotBlank() }
        val externalId = track.externalTrackId?.takeIf { it.isNotBlank() }
        val placeholderUrl = when {
            providerId != null && externalId != null -> "continuity://$providerId/$externalId"
            else -> "continuity://meta/${track.title}-${track.artist}"
        }
        val sourceType = when {
            providerId.equals("youtube_music", ignoreCase = true) -> SourceType.YOUTUBE_MUSIC
            else -> SourceType.ADDON
        }
        val insertedId = trackRepository.addTrackSource(
            title = track.title.orEmpty(),
            artist = track.artist.orEmpty(),
            album = track.album,
            coverArtUrl = track.artworkUrl,
            sourceType = sourceType,
            streamUrl = placeholderUrl,
            bitrate = 0,
            isrc = track.isrc,
            externalProviderId = providerId,
            externalTrackId = externalId
        )
        if (insertedId <= 0L) {
            Log.w(TAG, "follower could not ingest track '${track.title}'")
            return
        }
        val playable = trackRepository.getTrackWithSources(insertedId) ?: return
        withContext(Dispatchers.Main) {
            playerController.playTrack(playable)
            if (positionMs > 0L) playerController.seekTo(positionMs)
            if (!play) playerController.pause()
        }
    }

    private suspend fun postSession(
        mode: String,
        leaderDeviceId: String?,
        followerDeviceIds: List<String>,
        track: ContinuityTrackRefDto?,
        positionMs: Long,
        isPlaying: Boolean
    ): Boolean {
        val userId = accountManager.cloudUserIdOrNull
        if (userId.isNullOrBlank() || api == null) {
            Log.w(TAG, "postSession blocked signedIn=${accountManager.isSignedIn} api=${api != null}")
            return false
        }
        // Heartbeat first so TV list stays fresh.
        putContinuityHeartbeat(userId)
        val response = api.postContinuity(
            userId,
            ContinuityPostBody(
                action = "session",
                deviceId = deviceId,
                role = role.name.lowercase(),
                name = deviceName,
                mode = mode,
                leaderDeviceId = leaderDeviceId,
                followerDeviceIds = followerDeviceIds,
                track = track,
                positionMs = positionMs,
                isPlaying = isPlaying
            )
        )
        if (response.isSuccessful) {
            response.body()?.let { _snapshot.value = it }
            return true
        }
        Log.w(TAG, "postSession HTTP ${response.code()}")
        return false
    }

    private suspend fun putContinuityHeartbeat(userId: String) {
        api?.postContinuity(
            userId,
            ContinuityPostBody(
                action = "heartbeat",
                deviceId = deviceId,
                role = role.name.lowercase(),
                name = deviceName
            )
        )?.body()?.let { _snapshot.value = it }
    }

    private fun trackRefFrom(state: NowPlayingState): ContinuityTrackRefDto? {
        if (state.title.isNullOrBlank()) return null
        return ContinuityTrackRefDto(
            trackId = state.trackId,
            title = state.title,
            artist = state.artist,
            album = state.album,
            artworkUrl = state.artworkUrl,
            providerId = state.preferredProviderId,
            externalTrackId = state.preferredExternalTrackId,
            isrc = state.isrc
        )
    }

    private fun isTelevision(): Boolean {
        val uiMode = appContext.getSystemService(Context.UI_MODE_SERVICE) as? UiModeManager
        if (uiMode?.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION) return true
        return appContext.packageManager.hasSystemFeature("android.software.leanback")
    }

    private fun createGatewayApi(): VantaGatewayApi? {
        val baseUrl = BuildConfig.STATION_BACKEND_URL.takeIf { it.isNotBlank() }
            ?: "https://vanta-music-gateway.16drewk.workers.dev/"
        return runCatching {
            val client = okhttp3.OkHttpClient.Builder()
                .addInterceptor(FirebaseIdTokenInterceptor())
                .addInterceptor(GatewayApiKeyInterceptor)
                .build()
            Retrofit.Builder()
                .baseUrl(if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/")
                .client(client)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
                .create(VantaGatewayApi::class.java)
        }.getOrNull()
    }

    companion object {
        private const val TAG = "VANTA_CONTINUITY"
        private const val KEY_DEVICE_ID = "device_id"
    }
}
