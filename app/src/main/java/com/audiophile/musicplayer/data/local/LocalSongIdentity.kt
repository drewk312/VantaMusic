package com.audiophile.musicplayer.data.local

import com.audiophile.musicplayer.data.local.entities.LocalSongEntity

/**
 * Resolves a local library song against Now Playing / action-sheet identity.
 *
 * Preference order:
 * 1. canonicalTrackId (VANTA graph)
 * 2. ISRC
 * 3. exact normalized title + artist
 *
 * Never invent matches from partial titles or provider track IDs.
 */
object LocalSongIdentity {
    enum class MatchMethod { CANONICAL_TRACK_ID, ISRC, TITLE_ARTIST, NONE }

    data class Match(
        val song: LocalSongEntity?,
        val method: MatchMethod
    )

    fun resolveMatch(
        songs: List<LocalSongEntity>,
        isrc: String?,
        title: String,
        artist: String,
        canonicalTrackId: Long? = null
    ): Match {
        if (canonicalTrackId != null && canonicalTrackId > 0L) {
            val byCanonical = songs.firstOrNull { song ->
                song.canonicalTrackId == canonicalTrackId
            }
            if (byCanonical != null) return Match(byCanonical, MatchMethod.CANONICAL_TRACK_ID)
        }
        val cleanIsrc = isrc?.trim()?.takeIf { it.isNotBlank() }
        if (cleanIsrc != null) {
            val byIsrc = songs.firstOrNull { song ->
                song.isrc?.trim()?.equals(cleanIsrc, ignoreCase = true) == true
            }
            if (byIsrc != null) return Match(byIsrc, MatchMethod.ISRC)
        }
        val cleanTitle = title.trim()
        val cleanArtist = artist.trim()
        if (cleanTitle.isEmpty()) return Match(null, MatchMethod.NONE)
        val byTitleArtist = songs.firstOrNull { song ->
            song.title.equals(cleanTitle, ignoreCase = true) &&
                song.artist.equals(cleanArtist, ignoreCase = true)
        }
        return if (byTitleArtist != null) {
            Match(byTitleArtist, MatchMethod.TITLE_ARTIST)
        } else {
            Match(null, MatchMethod.NONE)
        }
    }

    fun findMatchingSong(
        songs: List<LocalSongEntity>,
        isrc: String?,
        title: String,
        artist: String,
        canonicalTrackId: Long? = null
    ): LocalSongEntity? = resolveMatch(songs, isrc, title, artist, canonicalTrackId).song

    fun isFavorite(
        songs: List<LocalSongEntity>,
        isrc: String?,
        title: String,
        artist: String,
        canonicalTrackId: Long? = null
    ): Boolean = findMatchingSong(songs, isrc, title, artist, canonicalTrackId)?.isFavorite == true

    fun isInLibrary(
        songs: List<LocalSongEntity>,
        isrc: String?,
        title: String,
        artist: String,
        canonicalTrackId: Long? = null
    ): Boolean = findMatchingSong(songs, isrc, title, artist, canonicalTrackId) != null
}
