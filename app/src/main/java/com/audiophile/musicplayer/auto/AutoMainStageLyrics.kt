package com.audiophile.musicplayer.auto

import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import com.audiophile.musicplayer.data.display.DisplayMetadataCleaner
import com.audiophile.musicplayer.data.local.entities.UnifiedTrackWithSources
import com.audiophile.musicplayer.data.lyrics.LyricsData
import com.audiophile.musicplayer.data.lyrics.LyricsSyncPreferences
import com.audiophile.musicplayer.data.lyrics.LyricsRepository
import com.audiophile.musicplayer.playback.dsp.VantaEqualizerPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

object AutoMainStageLyrics {
    const val MAX_DESCRIPTION_CHARS = 48

    fun truncateForAutoDisplay(text: String, maxChars: Int = MAX_DESCRIPTION_CHARS): String {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return trimmed
        if (trimmed.length <= maxChars) return trimmed

        val slice = trimmed.substring(0, maxChars)
        val lastSpace = slice.lastIndexOf(' ')
        if (lastSpace >= maxChars / 2) {
            return slice.substring(0, lastSpace).trimEnd() + "…"
        }
        return slice.trimEnd() + "…"
    }

    fun formatArtistAlbumLine(title: String, artist: String, album: String?): String {
        val display = DisplayMetadataCleaner.computeDisplayMetadata(title, artist, album)
        val cleanAlbum = display.album?.trim().takeUnless { it.isNullOrEmpty() }
        return if (cleanAlbum != null) "${display.artist} • $cleanAlbum" else display.artist
    }

    fun buildPlaybackMetadata(
        title: String,
        artist: String,
        album: String?,
        artworkUrl: String?,
        durationMs: Long? = null,
        description: String? = null,
    ): MediaMetadata {
        val cleanTitle = DisplayMetadataCleaner.cleanTitle(title)
        val artistAlbumLine = formatArtistAlbumLine(title, artist, album)
        return MediaMetadata.Builder()
            .setTitle(cleanTitle)
            .setArtist(artistAlbumLine)
            .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
            .apply {
                album?.trim()?.takeIf { it.isNotEmpty() }?.let { setAlbumTitle(it) }
                durationMs?.takeIf { it > 0 }?.let { setDurationMs(it) }
                description?.trim()?.takeIf { it.isNotEmpty() }?.let { setDescription(it) }
                artworkUri(artworkUrl)?.let { setArtworkUri(it) }
            }
            .build()
    }

    private fun artworkUri(url: String?): android.net.Uri? {
        if (url.isNullOrBlank()) return null
        if (!url.startsWith("http://", ignoreCase = true) && !url.startsWith("https://", ignoreCase = true)) {
            return null
        }
        return runCatching { android.net.Uri.parse(url) }.getOrNull()
    }
}

class AutoMainStageLyricsController(
    private val scope: CoroutineScope,
    private val playerProvider: () -> Player,
    private val lyricsRepository: LyricsRepository,
    private val syncPreferencesProvider: () -> LyricsSyncPreferences,
    private val immersivePreferencesProvider: () -> VantaEqualizerPreferences,
    private val currentTrackIdProvider: () -> Long?,
) {
    private var job: Job? = null

    fun cancel() {
        job?.cancel()
        job = null
    }

    fun start(track: UnifiedTrackWithSources) {
        cancel()
        job = scope.launch {
            val lyrics = lyricsRepository.getLyrics(track.track, track.track.isrc)
            val artistAlbumLine = AutoMainStageLyrics.formatArtistAlbumLine(
                track.track.title,
                track.track.artist,
                track.track.albumName,
            )
            if (lyrics == null || lyrics.lines.isEmpty()) {
                updateNowPlayingDescription(description = null, artistAlbumLine = artistAlbumLine)
                return@launch
            }

            val syncOffset = syncPreferencesProvider().getOffsetMs(lyrics.trackKey)
            val pipelineLead = immersivePreferencesProvider().lyricsPipelineLeadMs()
            var lastDescription: String? = null

            if (!lyrics.isSynced) {
                val staticLine = lyrics.lines.firstOrNull()?.text?.trim()?.takeIf { it.isNotBlank() }
                updateNowPlayingDescription(
                    description = staticLine?.let { AutoMainStageLyrics.truncateForAutoDisplay(it) },
                    artistAlbumLine = artistAlbumLine,
                )
                return@launch
            }

            while (isActive) {
                val playingTrackId = currentTrackIdProvider()
                if (playingTrackId != null && playingTrackId != track.track.trackId) break

                val player = playerProvider()
                val position = (player.currentPosition + syncOffset + pipelineLead).coerceAtLeast(0L)
                val line = KaraokeTimeline.lineAt(lyrics.lines, position)
                    ?.let { AutoMainStageLyrics.truncateForAutoDisplay(it) }
                if (line != null && line != lastDescription) {
                    updateNowPlayingDescription(description = line, artistAlbumLine = artistAlbumLine)
                    lastDescription = line
                }
                delay(300L)
            }
        }
    }

    private fun updateNowPlayingDescription(description: String?, artistAlbumLine: String) {
        val player = playerProvider()
        val current = player.currentMediaItem ?: return
        val newDescription = description.orEmpty()
        val existingDescription = current.mediaMetadata.description?.toString().orEmpty()
        val existingArtist = current.mediaMetadata.artist?.toString().orEmpty()
        if (existingDescription == newDescription && existingArtist == artistAlbumLine) return

        val metadata = current.mediaMetadata.buildUpon()
            .setArtist(artistAlbumLine)
            .apply {
                if (newDescription.isEmpty()) {
                    setDescription(null)
                } else {
                    setDescription(newDescription)
                }
            }
            .build()
        player.replaceMediaItem(
            player.currentMediaItemIndex,
            current.buildUpon().setMediaMetadata(metadata).build(),
        )
    }
}
