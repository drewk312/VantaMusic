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

    fun canonicalVersionKey(track: CanonicalTrack): String {
        val cleanTitle = track.title.lowercase(Locale.ROOT)
            .replace(Regex("""\s*[\[(][^\])]+[\])]"""), " ")
            .replace(Regex("""(?i)\s*-\s*(music from|from|original motion picture|soundtrack|inspired by).*$"""), " ")
            .replace(Regex("""(?i)\s*-\s*.*?(remaster|deluxe|edit|version|mono|stereo|clean|explicit|audio|video|official).*$"""), " ")
            .replace(Regex("""\b(feat|featuring|ft)\b.*$"""), "")
            .replace(Regex("""\b(remaster|remastered|deluxe|edition|version|edit|mono|stereo|audio|video|official)\b(\s*\d{2,4})?"""), " ")
            .replace(Regex("[^\\p{L}\\p{N}]+"), " ")
            .trim()
        val cleanArtist = track.artist.lowercase(Locale.ROOT)
            .replace(Regex("""\b(feat|featuring|ft)\b.*$"""), "")
            .split(",").firstOrNull()?.trim().orEmpty()
            .replace(Regex("[^\\p{L}\\p{N}]+"), " ")
            .trim()
        val base = if (cleanTitle.isNotBlank()) "$cleanTitle|$cleanArtist" else "${key(track.title)}|${key(track.artist)}"
        val isLive = track.title.contains("live", ignoreCase = true)
        val isRemix = track.title.contains("remix", ignoreCase = true)
        val variantTag = when {
            isLive -> ":live"
            isRemix -> ":remix"
            else -> ""
        }
        val explicitTag = if (track.explicit != null) ":exp=${track.explicit}" else ""
        return "$base$variantTag$explicitTag"
    }

    fun songs(tracks: List<CanonicalTrack>): List<CanonicalTrack> {
        val grouped = linkedMapOf<String, CanonicalTrack>()
        for (track in tracks) {
            val identity = canonicalVersionKey(track)
            val previous = grouped[identity]
            fun score(value: CanonicalTrack): Int {
                var s = (if (value.sourceStatus?.canEnterPlaybackFlow() == true) 10000 else 0) + value.sourcePriority
                val lower = value.title.lowercase(Locale.ROOT)
                if (!lower.contains("remaster") && !lower.contains("deluxe") && !lower.contains("karaoke")) {
                    s += 100
                }
                if (value.artworkUrl?.isNotBlank() == true) s += 10
                return s
            }
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
