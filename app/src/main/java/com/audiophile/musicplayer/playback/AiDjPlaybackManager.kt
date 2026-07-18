package com.audiophile.musicplayer.playback

import android.content.Context
import android.util.Base64
import android.util.Log
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.audiophile.musicplayer.data.local.entities.UnifiedTrackWithSources
import com.audiophile.musicplayer.radio.DjSegmentRequestV1
import com.audiophile.musicplayer.radio.RadioApiService
import com.audiophile.musicplayer.radio.SimpleTrackRef
import kotlinx.coroutines.*
import java.io.File
import java.io.FileOutputStream

class AiDjPlaybackManager(
    private val context: Context,
    private val radioApiService: RadioApiService
) {
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var djPlayer: ExoPlayer? = null

    private var prefetchJob: Job? = null
    private var cachedAudioFile: File? = null
    private var isPlaying = false
    private var songsSinceLastDj = 0

    // To prevent stale tokens from playing
    private var lastPrefetchedGeneration = 0L

    init {
        scope.launch(Dispatchers.IO) {
            try {
                context.cacheDir.listFiles { file -> 
                    file.name.startsWith("dj_segment_") && file.name.endsWith(".mp3") 
                }?.forEach { it.delete() }
            } catch (e: Exception) {
                Log.e("VANTA_AI_DJ", "Failed to clean old dj segments: ${e.message}")
            }
        }
    }

    fun initPlayer() {
        if (djPlayer == null) {
            val audioAttributes = androidx.media3.common.AudioAttributes.Builder()
                .setUsage(androidx.media3.common.C.USAGE_MEDIA)
                .setContentType(androidx.media3.common.C.AUDIO_CONTENT_TYPE_SPEECH)
                .build()
            djPlayer = ExoPlayer.Builder(context)
                .setAudioAttributes(audioAttributes, true)
                .build()
            djPlayer?.addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(playbackState: Int) {
                    if (playbackState == Player.STATE_ENDED) {
                        Log.d("VANTA_AI_DJ", "DJ playback ended.")
                        isPlaying = false
                        onCompletionCallback?.invoke()
                        onCompletionCallback = null
                    }
                }
            })
        }
    }

    private var onCompletionCallback: (() -> Unit)? = null

    fun getFrequency(): String {
        return context.getSharedPreferences("vanta_settings", Context.MODE_PRIVATE)
            .getString("dj_frequency", "Occasional") ?: "Occasional"
    }

    private fun shouldPrefetchNext(frequency: String): Boolean {
        if (frequency == "Off") return false
        val threshold = if (frequency == "Frequent") 2 else 4
        return songsSinceLastDj >= threshold - 1
    }
    
    fun isDjScheduledForNext(): Boolean {
        return shouldPrefetchNext(getFrequency())
    }

    /**
     * Call this when a new song starts playing to prefetch the DJ for the transition.
     */
    fun onTrackStarted(
        recentTracks: List<UnifiedTrackWithSources>,
        nextTrack: UnifiedTrackWithSources?,
        playbackGeneration: Long
    ) {
        val freq = getFrequency()
        if (!shouldPrefetchNext(freq)) {
            songsSinceLastDj++
            Log.d("VANTA_AI_DJ", "Skipping prefetch. songsSinceLastDj=$songsSinceLastDj freq=$freq")
            return
        }
        
        if (nextTrack == null) {
            Log.d("VANTA_AI_DJ", "Skipping prefetch, nextTrack is null.")
            return
        }

        val capturedGeneration = playbackGeneration
        prefetchJob?.cancel()
        cachedAudioFile?.delete()
        cachedAudioFile = null
        lastPrefetchedGeneration = capturedGeneration

        Log.d("VANTA_AI_DJ", "Starting prefetch for next track: ${nextTrack.track.title}")
        
        prefetchJob = scope.launch(Dispatchers.IO) {
            try {
                val recentRefs = recentTracks.map { 
                    SimpleTrackRef(title = it.track.title ?: "", artist = it.track.artist ?: "") 
                }
                val nextRef = SimpleTrackRef(title = nextTrack.track.title ?: "", artist = nextTrack.track.artist ?: "")

                val tone = selectTone(recentTracks, nextTrack)
                val stationName = recentTracks.firstOrNull()?.track?.genre?.let { "$it station" }
                    ?: nextTrack.track.genre?.let { "$it station" }
                    ?: "VANTA Station"

                val request = DjSegmentRequestV1(
                    stationName = stationName,
                    recentTracks = recentRefs,
                    nextTrack = nextRef,
                    tone = tone
                )
                val response = radioApiService.generateDjSegment(request)
                if (response.isSuccessful && response.body() != null) {
                    val base64 = response.body()?.audioBase64
                    if (!base64.isNullOrEmpty()) {
                        val bytes = Base64.decode(base64, Base64.DEFAULT)
                        val file = File(context.cacheDir, "dj_segment_${System.currentTimeMillis()}.mp3")
                        FileOutputStream(file).use { it.write(bytes) }
                        
                        withContext(Dispatchers.Main) {
                            if (lastPrefetchedGeneration == capturedGeneration) {
                                cachedAudioFile = file
                                Log.d("VANTA_AI_DJ", "Prefetch successful, saved to ${file.absolutePath}")
                            } else {
                                file.delete()
                                Log.d("VANTA_AI_DJ", "Prefetch successful but generation changed. Discarding.")
                            }
                        }
                    }
                } else {
                    Log.e("VANTA_AI_DJ", "DJ generation failed: ${response.code()} ${response.message()}")
                }
            } catch (e: Exception) {
                Log.e("VANTA_AI_DJ", "Error prefetching DJ: ${e.message}")
            }
        }
    }

    /**
     * Attempt to play the DJ segment.
     * Returns true if DJ is playing, false if no DJ audio is ready.
     * If true is returned, onComplete will be called when it finishes.
     */
    fun playDjSegment(playbackGeneration: Long, onComplete: () -> Unit): Boolean {
        val freq = getFrequency()
        if (freq == "Off") return false
        
        if (lastPrefetchedGeneration != playbackGeneration || cachedAudioFile == null) {
            Log.d("VANTA_AI_DJ", "Cannot play DJ: generation mismatch or no audio cached. Expected Gen $playbackGeneration, Got $lastPrefetchedGeneration")
            return false
        }

        initPlayer()

        isPlaying = true
        songsSinceLastDj = 0
        onCompletionCallback = onComplete

        val audioFile = cachedAudioFile ?: run { Log.e("AiDjPlayback", "No cached audio file"); return false }
        val mediaItem = MediaItem.fromUri(audioFile.absolutePath)
        djPlayer?.stop()
        djPlayer?.setMediaItem(mediaItem)
        djPlayer?.prepare()
        djPlayer?.play()

        Log.d("VANTA_AI_DJ", "Started DJ playback for generation $playbackGeneration")
        return true
    }
    
    fun isDjPlaying() = isPlaying

    fun pause() {
        djPlayer?.pause()
    }

    fun resume() {
        if (isPlaying) djPlayer?.play()
    }
    
    fun togglePlayPause() {
        if (djPlayer?.isPlaying == true) {
            pause()
        } else {
            resume()
        }
    }

    private fun selectTone(
        recentTracks: List<UnifiedTrackWithSources>,
        nextTrack: UnifiedTrackWithSources
    ): String {
        val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
        val timeTone = when (hour) {
            in 5..11 -> "bright morning"
            in 12..17 -> "midday energy"
            in 18..21 -> "evening cool"
            else -> "late night"
        }
        val energy = nextTrack.track.genre?.lowercase()?.let { genre ->
            when {
                genre.contains("rock") || genre.contains("metal") ||
                    genre.contains("hip-hop") || genre.contains("edm") ||
                    genre.contains("trap") || genre.contains("drum") -> "high energy"
                genre.contains("jazz") || genre.contains("classical") ||
                    genre.contains("ambient") || genre.contains("acoustic") ||
                    genre.contains("lo-fi") || genre.contains("folk") -> "mellow"
                else -> "smooth"
            }
        } ?: "smooth"
        return "$timeTone, $energy"
    }

    fun releasePlayer() {
        prefetchJob?.cancel()
        djPlayer?.release()
        djPlayer = null
        cachedAudioFile?.delete()
    }
}

