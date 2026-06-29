package com.audiophile.musicplayer.data.local

import com.audiophile.musicplayer.data.importer.ImportMatchStatus
import com.audiophile.musicplayer.data.importer.PlayabilityStatus
import com.audiophile.musicplayer.data.local.entities.ImportedTrackEntity
import com.audiophile.musicplayer.data.local.entities.LocalSongEntity
import com.audiophile.musicplayer.data.local.entities.SourceType
import com.audiophile.musicplayer.data.local.entities.TrackSource
import com.audiophile.musicplayer.data.local.entities.UnifiedTrack
import com.audiophile.musicplayer.data.local.entities.UnifiedTrackWithSources

fun LocalSongEntity.toUnifiedTrack(): UnifiedTrack {
    return UnifiedTrack(
        trackId = id,
        title = title,
        artist = artist,
        albumName = album,
        coverArtUrl = artworkUrl,
        genre = genres.firstOrNull(),
        localLibraryId = id,
        isrc = isrc,
        durationMs = durationMs
    )
}

fun LocalSongEntity.toPlayableQueueItem(): UnifiedTrackWithSources? {
    val url = streamUrl?.takeIf { it.isNotBlank() } ?: return null
    return UnifiedTrackWithSources(
        track = toUnifiedTrack(),
        sources = listOf(
            TrackSource(
                parentTrackId = id,
                sourceType = sourceType,
                streamUrl = url,
                bitrate = qualityToBitrate(quality)
            )
        )
    )
}

fun UnifiedTrack.toLocalSongEntity(
    sourceType: SourceType = SourceType.LOCAL,
    streamUrl: String? = null,
    quality: String? = null
): LocalSongEntity {
    return LocalSongEntity(
        id = localLibraryId ?: 0L,
        title = title,
        artist = artist,
        album = albumName,
        artworkUrl = coverArtUrl,
        genres = listOfNotNull(genre),
        isrc = isrc,
        durationMs = durationMs,
        quality = quality,
        sourceType = sourceType,
        streamUrl = streamUrl
    )
}

fun ImportedTrackEntity.toLocalSongEntity(): LocalSongEntity? {
    if (matchStatus != ImportMatchStatus.MATCHED) return null
    if (playabilityStatus == PlayabilityStatus.NOT_FOUND || playabilityStatus == PlayabilityStatus.NEEDS_REVIEW) return null
    val title = userEditedTitle ?: parsedTitle ?: return null
    val artist = userEditedArtist ?: parsedArtist ?: "Unknown Artist"
    return LocalSongEntity(
        title = title,
        artist = artist,
        album = parsedAlbum,
        sourceType = SourceType.LOCAL,
        streamUrl = sourceUrl,
        importSource = "Soundiiz",
        externalIdsJson = matchedSongId?.let { """{"matchedSongId":$it}""" }
    )
}

private fun qualityToBitrate(quality: String?): Int {
    val normalized = quality.orEmpty().lowercase()
    return Regex("""\b(\d{2,4})\s*kbps\b""")
        .find(normalized)?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 0
}
