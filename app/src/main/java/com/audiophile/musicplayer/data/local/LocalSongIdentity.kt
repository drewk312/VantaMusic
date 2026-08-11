package com.audiophile.musicplayer.data.local

import com.audiophile.musicplayer.data.local.entities.LocalSongEntity

/**
 * Resolves a local library song against Now Playing / action-sheet identity.
 * Prefer ISRC, then exact title+artist — never invent matches from partial titles.
 */
object LocalSongIdentity {
    fun findMatchingSong(
        songs: List<LocalSongEntity>,
        isrc: String?,
        title: String,
        artist: String
    ): LocalSongEntity? {
        val cleanIsrc = isrc?.trim()?.takeIf { it.isNotBlank() }
        if (cleanIsrc != null) {
            val byIsrc = songs.firstOrNull { song ->
                song.isrc?.trim()?.equals(cleanIsrc, ignoreCase = true) == true
            }
            if (byIsrc != null) return byIsrc
        }
        val cleanTitle = title.trim()
        val cleanArtist = artist.trim()
        if (cleanTitle.isEmpty()) return null
        return songs.firstOrNull { song ->
            song.title.equals(cleanTitle, ignoreCase = true) &&
                song.artist.equals(cleanArtist, ignoreCase = true)
        }
    }

    fun isFavorite(
        songs: List<LocalSongEntity>,
        isrc: String?,
        title: String,
        artist: String
    ): Boolean = findMatchingSong(songs, isrc, title, artist)?.isFavorite == true

    fun isInLibrary(
        songs: List<LocalSongEntity>,
        isrc: String?,
        title: String,
        artist: String
    ): Boolean = findMatchingSong(songs, isrc, title, artist) != null
}
