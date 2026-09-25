package com.audiophile.musicplayer.sync

import android.content.Context
import android.os.Build
import android.util.Log
import com.audiophile.musicplayer.data.local.entities.LocalSongEntity
import com.audiophile.musicplayer.data.local.entities.SourceType
import com.audiophile.musicplayer.data.repository.LocalLibraryRepository
import com.audiophile.musicplayer.data.repository.TrackRepository
import com.audiophile.musicplayer.data.source.external.SpotiFlacEndpoints
import com.audiophile.musicplayer.playback.GatewayApiKeyInterceptor
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit
import kotlin.random.Random

/**
 * Shares library + likes between phone and TV using a short pair code.
 * No Firebase / Continuity cast — just transfer your music list so TV can play it.
 */
class DeviceLibrarySyncManager(
    context: Context,
    private val trackRepository: TrackRepository,
    private val localLibraryRepository: LocalLibraryRepository,
) {
    private val prefs = context.applicationContext.getSharedPreferences("vanta_device_sync", Context.MODE_PRIVATE)
    private val gson = Gson()
    private val http = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .callTimeout(25, TimeUnit.SECONDS)
        .addInterceptor(GatewayApiKeyInterceptor)
        .build()

    private val _pairCode = MutableStateFlow(prefs.getString(KEY_PAIR_CODE, null).orEmpty())
    val pairCode: StateFlow<String> = _pairCode.asStateFlow()

    private val _status = MutableStateFlow<String?>(null)
    val status: StateFlow<String?> = _status.asStateFlow()

    private val _lastSyncedTrackCount = MutableStateFlow(prefs.getInt(KEY_LAST_COUNT, 0))
    val lastSyncedTrackCount: StateFlow<Int> = _lastSyncedTrackCount.asStateFlow()

    fun ensurePairCode(): String {
        val existing = _pairCode.value.trim().uppercase()
        if (existing.length in 4..8) return existing
        val generated = buildString {
            repeat(6) { append(CODE_ALPHABET[Random.nextInt(CODE_ALPHABET.length)]) }
        }
        setPairCode(generated)
        return generated
    }

    fun setPairCode(code: String) {
        val normalized = code.trim().uppercase().replace(Regex("[^A-Z0-9]"), "").take(8)
        prefs.edit().putString(KEY_PAIR_CODE, normalized).apply()
        _pairCode.value = normalized
    }

    suspend fun pushFromThisDevice(): Boolean = withContext(Dispatchers.IO) {
        val code = ensurePairCode()
        val favorites = localLibraryRepository.allSongsSnapshot().filter { it.isFavorite }
        val unified = trackRepository.getAllTracks()
        val byKey = unified.associateBy { normKey(it.track.title, it.track.artist) }

        val tracks = linkedMapOf<String, DeviceSyncTrackDto>()
        for (song in favorites) {
            val key = normKey(song.title, song.artist)
            val match = byKey[key]
            val source = match?.sources?.firstOrNull {
                !it.externalProviderId.isNullOrBlank() && !it.externalTrackId.isNullOrBlank()
            }
            tracks[key] = DeviceSyncTrackDto(
                title = song.title,
                artist = song.artist,
                album = song.album,
                artworkUrl = song.artworkUrl ?: match?.track?.coverArtUrl,
                isFavorite = true,
                providerId = source?.externalProviderId,
                externalTrackId = source?.externalTrackId,
                isrc = song.isrc ?: match?.track?.isrc,
                durationMs = song.durationMs?.takeIf { it > 0 } ?: match?.track?.durationMs
            )
        }
        for (item in unified) {
            val key = normKey(item.track.title, item.track.artist)
            if (tracks.containsKey(key)) continue
            val source = item.sources.firstOrNull {
                !it.externalProviderId.isNullOrBlank() && !it.externalTrackId.isNullOrBlank()
            }
            tracks[key] = DeviceSyncTrackDto(
                title = item.track.title,
                artist = item.track.artist,
                album = item.track.albumName,
                artworkUrl = item.track.coverArtUrl,
                isFavorite = false,
                providerId = source?.externalProviderId,
                externalTrackId = source?.externalTrackId,
                isrc = item.track.isrc,
                durationMs = item.track.durationMs
            )
        }

        val body = DeviceLibraryPushBody(
            pairCode = code,
            deviceName = Build.MODEL ?: "Android",
            tracks = tracks.values.toList()
        )
        val base = SpotiFlacEndpoints.DEFAULT_GATEWAY_BASE_URL.trimEnd('/')
        val request = Request.Builder()
            .url("$base/device-sync/library")
            .post(gson.toJson(body).toRequestBody(JSON))
            .header("Content-Type", "application/json")
            .build()

        return@withContext runCatching {
            http.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.w(TAG, "push HTTP ${response.code}")
                    _status.value = "Push failed (${response.code})"
                    return@use false
                }
                prefs.edit().putInt(KEY_LAST_COUNT, body.tracks.size).apply()
                _lastSyncedTrackCount.value = body.tracks.size
                _status.value = "Shared ${body.tracks.size} tracks · code $code"
                Log.i(TAG, "pushed ${body.tracks.size} tracks code=$code")
                true
            }
        }.getOrElse {
            Log.w(TAG, "push error: ${it.message}")
            _status.value = "Push failed"
            false
        }
    }

    suspend fun pullToThisDevice(): Int = withContext(Dispatchers.IO) {
        val code = _pairCode.value.trim().uppercase()
        if (code.length !in 4..8) {
            _status.value = "Enter the same link code as your phone"
            return@withContext 0
        }
        val base = SpotiFlacEndpoints.DEFAULT_GATEWAY_BASE_URL.trimEnd('/')
        val request = Request.Builder()
            .url("$base/device-sync/library?code=$code")
            .get()
            .build()

        val payload = runCatching {
            http.newCall(request).execute().use { response ->
                val text = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    Log.w(TAG, "pull HTTP ${response.code} body=${text.take(120)}")
                    _status.value = if (response.code == 404) {
                        "Nothing shared yet — push from phone first"
                    } else {
                        "Pull failed (${response.code})"
                    }
                    return@use null
                }
                gson.fromJson(text, DeviceLibraryPayloadDto::class.java)
            }
        }.getOrElse {
            Log.w(TAG, "pull error: ${it.message}")
            _status.value = "Pull failed"
            null
        } ?: return@withContext 0

        var imported = 0
        val favoriteSongIds = mutableListOf<Long>()
        for (track in payload.tracks.orEmpty()) {
            val title = track.title.trim()
            val artist = track.artist.trim()
            if (title.isBlank() || artist.isBlank()) continue

            val placeholder = when {
                !track.providerId.isNullOrBlank() && !track.externalTrackId.isNullOrBlank() ->
                    "device-sync://${track.providerId}/${track.externalTrackId}"
                else -> "device-sync://meta/${normKey(title, artist)}"
            }
            val sourceType = when {
                track.providerId.equals("youtube_music", ignoreCase = true) -> SourceType.YOUTUBE_MUSIC
                else -> SourceType.ADDON
            }
            val trackId = trackRepository.addTrackSource(
                title = title,
                artist = artist,
                album = track.album,
                coverArtUrl = track.artworkUrl,
                sourceType = sourceType,
                streamUrl = placeholder,
                bitrate = 0,
                isrc = track.isrc,
                durationMs = track.durationMs,
                externalProviderId = track.providerId,
                externalTrackId = track.externalTrackId
            )
            if (trackId <= 0L) continue
            imported++

            val existing = localLibraryRepository.findSongByTitleArtist(title, artist)
                ?: localLibraryRepository.findSongByIsrc(track.isrc.orEmpty())
            val songId = if (existing != null) {
                existing.id
            } else {
                val ids = localLibraryRepository.saveSongs(
                    listOf(
                        LocalSongEntity(
                            title = title,
                            artist = artist,
                            album = track.album,
                            durationMs = track.durationMs,
                            artworkUrl = track.artworkUrl,
                            streamUrl = placeholder,
                            isFavorite = track.isFavorite == true,
                            isrc = track.isrc,
                            sourceType = SourceType.ADDON,
                            importSource = "device_sync"
                        )
                    )
                )
                ids.firstOrNull() ?: continue
            }
            if (track.isFavorite == true) {
                favoriteSongIds.add(songId)
            }
            // Link unified track to local library id when possible.
            runCatching {
                val withSources = trackRepository.getTrackWithSources(trackId)
                if (withSources != null && withSources.track.localLibraryId == null) {
                    trackRepository.addTrackSource(
                        title = title,
                        artist = artist,
                        album = track.album,
                        coverArtUrl = track.artworkUrl,
                        sourceType = sourceType,
                        streamUrl = placeholder,
                        bitrate = 0,
                        localLibraryId = songId,
                        isrc = track.isrc,
                        durationMs = track.durationMs,
                        externalProviderId = track.providerId,
                        externalTrackId = track.externalTrackId
                    )
                }
            }
        }
        if (favoriteSongIds.isNotEmpty()) {
            localLibraryRepository.likeSongs(favoriteSongIds)
        }
        prefs.edit().putInt(KEY_LAST_COUNT, imported).apply()
        _lastSyncedTrackCount.value = imported
        _status.value = "Imported $imported tracks from phone"
        Log.i(TAG, "pulled imported=$imported favorites=${favoriteSongIds.size} code=$code")
        imported
    }

    private fun normKey(title: String, artist: String): String =
        "${title.trim().lowercase()}|${artist.trim().lowercase()}"

    companion object {
        private const val TAG = "VANTA_DEVICE_SYNC"
        private const val KEY_PAIR_CODE = "pair_code"
        private const val KEY_LAST_COUNT = "last_count"
        private const val CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        private val JSON = "application/json".toMediaType()
    }
}

data class DeviceSyncTrackDto(
    @SerializedName("title") val title: String,
    @SerializedName("artist") val artist: String,
    @SerializedName("album") val album: String? = null,
    @SerializedName("artworkUrl") val artworkUrl: String? = null,
    @SerializedName("isFavorite") val isFavorite: Boolean = false,
    @SerializedName("providerId") val providerId: String? = null,
    @SerializedName("externalTrackId") val externalTrackId: String? = null,
    @SerializedName("isrc") val isrc: String? = null,
    @SerializedName("durationMs") val durationMs: Long? = null
)

data class DeviceLibraryPushBody(
    @SerializedName("pairCode") val pairCode: String,
    @SerializedName("deviceName") val deviceName: String,
    @SerializedName("tracks") val tracks: List<DeviceSyncTrackDto>
)

data class DeviceLibraryPayloadDto(
    @SerializedName("pairCode") val pairCode: String? = null,
    @SerializedName("deviceName") val deviceName: String? = null,
    @SerializedName("updatedAtMs") val updatedAtMs: Long = 0L,
    @SerializedName("tracks") val tracks: List<DeviceSyncTrackDto>? = null
)
