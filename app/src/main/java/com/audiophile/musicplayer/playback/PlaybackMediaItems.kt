package com.audiophile.musicplayer.playback

import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import com.audiophile.musicplayer.auto.AutoMainStageLyrics
import com.audiophile.musicplayer.data.display.DisplayMetadataCleaner
import com.audiophile.musicplayer.data.local.entities.TrackSource
import com.audiophile.musicplayer.data.local.entities.UnifiedTrackWithSources
import androidx.core.net.toUri

/** Builds ExoPlayer [MediaItem]s with title, artist, album, and artwork attached. */
object PlaybackMediaItems {
    fun fromTrack(
        track: UnifiedTrackWithSources,
        source: TrackSource? = null,
        streamUrl: String? = null,
        mimeType: String? = null,
    ): MediaItem {
        val resolvedSource = source ?: track.sources.firstOrNull { it.streamUrl.isNotBlank() }
        val url = streamUrl?.takeIf { it.isNotBlank() } ?: resolvedSource?.streamUrl?.takeIf { it.isNotBlank() }
        val display = DisplayMetadataCleaner.computeDisplayMetadata(
            rawTitle = track.track.title.orEmpty(),
            rawArtist = track.track.artist.orEmpty(),
            rawAlbum = track.track.albumName
        )
        val title = display.title.ifBlank { track.track.title.orEmpty() }.ifBlank { "Unknown Track" }
        val artist = display.artist.ifBlank { track.track.artist.orEmpty() }.ifBlank { "Unknown Artist" }

        val metadata = MediaMetadata.Builder()
            .setTitle(title)
            .setArtist(artist)
            .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
            .apply {
                DisplayMetadataCleaner.cleanAlbumName(track.track.albumName)?.let { setAlbumTitle(it) }
                track.track.durationMs?.takeIf { it > 0 }?.let { setDurationMs(it) }
                artworkUri(track.track.coverArtUrl)?.let { setArtworkUri(it) }
            }
            .build()

        return MediaItem.Builder()
            .setMediaId("${track.track.trackId}:${resolvedSource?.sourceId ?: 0}")
            .setMediaMetadata(metadata)
            .apply {
                if (!url.isNullOrBlank()) {
                    setUri(url)
                    mimeType?.let { setMimeType(it) }
                }
            }
            .build()
    }

    fun queuePlaceholder(track: UnifiedTrackWithSources): MediaItem =
        fromTrack(track, source = track.sources.firstOrNull { it.streamUrl.isNotBlank() })

    private fun artworkUri(url: String?): Uri? {
        if (url.isNullOrBlank()) return null
        if (!url.startsWith("http://", ignoreCase = true) && !url.startsWith("https://", ignoreCase = true)) {
            return null
        }
        return runCatching { url.toUri() }.getOrNull()
    }
}