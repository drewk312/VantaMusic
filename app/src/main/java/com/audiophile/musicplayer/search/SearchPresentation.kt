package com.audiophile.musicplayer.search

import com.audiophile.musicplayer.data.canonical.CanonicalAlbum
import com.audiophile.musicplayer.data.canonical.CanonicalArtist
import com.audiophile.musicplayer.data.canonical.CanonicalPlaylist
import com.audiophile.musicplayer.data.canonical.CanonicalTrack
import com.audiophile.musicplayer.data.source.canEnterPlaybackFlow
import java.util.Locale

/** Presentation grouping only: original recording identities are preserved for playback. */
object SearchPresentation {
    fun withArtistCatalog(response: UnifiedSearchResponse, catalog: com.audiophile.musicplayer.data.catalog.ArtistCatalog?): UnifiedSearchResponse {
        if (catalog == null) return response
        val mergedSongs = songs(response.songs + catalog.tracks)
        return response.copy(
            topResult = response.topResult ?: mergedSongs.firstOrNull(),
            songs = mergedSongs,
            albums = albums(catalog.albums + response.albums, mergedSongs, catalog.artist),
            artists = (listOf(catalog.artist) + response.artists).distinctBy { key(it.name) }
        )
    }

    fun key(value: String?): String = (value ?: "").lowercase(Locale.ROOT)
        .replace(Regex("[^\\p{L}\\p{N}]+"), " ").trim()

    fun artistMatch(query: String, artists: List<CanonicalArtist>, songs: List<CanonicalTrack>): CanonicalArtist? {
        val name = query.trim().replace(Regex("^artist[: ]+", RegexOption.IGNORE_CASE), "")
        if (name.length < 2) return null
        val target = key(name)
        return artists.firstOrNull { key(it.name) == target }
            ?: songs.firstOrNull { key(it.artist) == target }?.let {
                CanonicalArtist(name = it.artist, artworkUrl = it.artworkUrl, genre = it.genre)
            }
    }

    fun songs(tracks: List<CanonicalTrack>): List<CanonicalTrack> {
        val grouped = linkedMapOf<String, CanonicalTrack>()
        for (track in tracks) {
            val identity = track.isrc?.takeIf { it.isNotBlank() }?.uppercase(Locale.ROOT)
                ?: "${key(track.title)}|${key(track.artist)}|${track.explicit}"
            val previous = grouped[identity]
            fun score(value: CanonicalTrack) =
                (if (value.sourceStatus?.canEnterPlaybackFlow() == true) 10000 else 0) + value.sourcePriority
            if (previous == null || score(track) > score(previous)) grouped[identity] = track
        }
        return grouped.values.toList()
    }

    fun albums(albums: List<CanonicalAlbum>, tracks: List<CanonicalTrack>, artist: CanonicalArtist?): List<CanonicalAlbum> {
        val derived = tracks.filter { !it.album.isNullOrBlank() }.map {
            CanonicalAlbum(title = it.album.orEmpty(), artist = it.artist, artworkUrl = it.artworkUrl, releaseYear = it.releaseYear)
        }
        return (albums + derived)
            .filter { artist == null || key(it.artist) == key(artist.name) }
            .distinctBy { key(it.title) to key(it.artist) }
            .sortedByDescending { it.releaseYear ?: 0 }
    }

    fun playlists(playlists: List<CanonicalPlaylist>, query: String = ""): List<CanonicalPlaylist> =
        playlists.filter { it.title.isNotBlank() && !it.id.isNullOrBlank() }
            .distinctBy { it.id ?: key(it.title) }
            .filter { matchesPlaylistQuery(it.title, query) }

    fun matchesPlaylistQuery(title: String, query: String): Boolean {
        val tokens = query.lowercase(Locale.ROOT)
            .split(Regex("[^\\p{L}\\p{N}]+"))
            .filter { it.length >= 3 }
        if (tokens.isEmpty()) return true
        val haystack = title.lowercase(Locale.ROOT)
        return tokens.all { haystack.contains(it) }
    }
}
