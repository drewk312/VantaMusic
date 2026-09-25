package com.audiophile.musicplayer.data.dj

import com.audiophile.musicplayer.data.local.entities.UnifiedTrackWithSources
import com.audiophile.musicplayer.data.source.isPlaylistCompilationArtifact

/** Filters tribute/karaoke junk and obvious modern tracks out of era/genre jukebox pools. */
object JukeboxTrackEligibility {
    private val videoLiveMarkers = listOf(
        "ed sullivan",
        "live at",
        "live from",
        "live session",
        "live on",
        "live in",
        "mtv unplugged",
        "tiny desk",
        "kexp",
        "snippet",
        "video",
        "footage",
        "broadcast",
        "tv show",
        "talk show",
        "talking about",
        "talks about",
        "reacting to",
        "reacts to",
        "ranked every",
        "tier list",
        "behind the lyrics",
        "concert film",
        "full concert",
        "live performance",
        "performs live",
        "interview",
        "documentary"
    )

    fun isJunkOrVideoTrack(title: String?, artist: String?, durationMs: Long?): Boolean {
        val haystack = "${title.orEmpty()} ${artist.orEmpty()}".lowercase()
        if (videoLiveMarkers.any { marker -> marker in haystack }) return true
        if (durationMs != null && durationMs > 600_000L) return true
        return false
    }

    fun isJunkOrVideoTrack(track: UnifiedTrackWithSources): Boolean =
        isJunkOrVideoTrack(track.track.title, track.track.artist, track.track.durationMs)

    private val variantMarkers = listOf(
        "remix",
        "slowed",
        "sped up",
        "sped-up",
        "speed up",
        "nightcore",
        "acoustic version",
        "acoustic cover",
        "unplugged",
        "instrumental",
        "instrumental version",
        "karaoke",
        "tribute",
        "cover version",
        "re-recorded",
        "re recorded"
    )

    fun isVariantArtifact(title: String?, artist: String?, album: String? = null): Boolean {
        val haystack = "${title.orEmpty()} ${artist.orEmpty()} ${album.orEmpty()}".lowercase()
        return variantMarkers.any { marker -> marker in haystack }
    }

    /** Radio/DJ queue filter — studio-first unless the user explicitly asked for variants. */
    fun shouldExcludeFromRadioQueue(track: UnifiedTrackWithSources): Boolean {
        val unified = track.track
        if (shouldExcludeFromJukebox(track, effectiveGenre = unified.genre.orEmpty())) return true
        if (isVariantArtifact(unified.title, unified.artist, unified.albumName)) return true
        return false
    }

    fun shouldExcludeFromRadioQueue(title: String?, artist: String?, durationMs: Long?, album: String? = null): Boolean {
        if (isJunkOrVideoTrack(title, artist, durationMs)) return true
        if (isVariantArtifact(title, artist, album)) return true
        return false
    }

    private val tributeMarkers = listOf(
        "tribute stars",
        "tribute to",
        "tribute band",
        "ultimate tribute",
        "party tyme",
        "karaoke",
        "in the style of",
        "cover version",
        "cover hits",
        "sound-a-like",
        "made popular by",
        "vocal version",
        "instrumental version",
        "backing track",
        "sing along"
    )

    private val modernGenreMarkers = listOf(
        "hip hop",
        "hip-hop",
        "rap",
        "trap",
        "drill",
        "grime",
        "edm",
        "house",
        "techno",
        "dubstep",
        "hyperpop"
    )

    fun isTributeOrKaraokeArtifact(track: UnifiedTrackWithSources): Boolean {
        val haystack = metadataHaystack(track)
        return tributeMarkers.any { marker -> marker in haystack }
    }

    fun contradictsVintageEra(
        track: UnifiedTrackWithSources,
        effectiveGenre: String,
        eraEnd: Int
    ): Boolean {
        if (eraEnd >= 1995) return false
        val genre = effectiveGenre.ifBlank { track.track.genre.orEmpty().lowercase() }
        if (genre.isNotBlank() && modernGenreMarkers.any { genre.contains(it) }) return true
        val haystack = metadataHaystack(track)
        return modernGenreMarkers.any { it in haystack }
    }

    fun shouldExcludeFromJukebox(
        track: UnifiedTrackWithSources,
        effectiveGenre: String = "",
        eraEnd: Int? = null
    ): Boolean {
        val unified = track.track
        if (isPlaylistCompilationArtifact(
                title = unified.title.orEmpty(),
                artist = unified.artist.orEmpty(),
                album = unified.albumName,
                durationMs = unified.durationMs
            )
        ) {
            return true
        }
        if (isTributeOrKaraokeArtifact(track)) return true
        if (isJunkOrVideoTrack(track)) return true
        if (eraEnd != null && contradictsVintageEra(track, effectiveGenre, eraEnd)) return true
        return false
    }

    private fun metadataHaystack(track: UnifiedTrackWithSources): String {
        val unified = track.track
        return "${unified.title} ${unified.albumName.orEmpty()} ${unified.artist.orEmpty()}"
            .lowercase()
    }
}
