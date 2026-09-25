package com.audiophile.musicplayer.data.catalog

import com.audiophile.musicplayer.data.canonical.CanonicalAlbum
import com.audiophile.musicplayer.data.canonical.CanonicalArtist
import com.audiophile.musicplayer.data.canonical.CanonicalTrack
import java.util.Locale

/** Category membership comes from the catalog, not words in a song's title. */
data class CategoryBrowseResults(
    val songs: List<CanonicalTrack>,
    val albums: List<CanonicalAlbum>,
    val artists: List<CanonicalArtist>
) {
    companion object {
        fun from(tracks: List<CanonicalTrack>): CategoryBrowseResults {
            fun key(value: String) = value.trim().lowercase(Locale.ROOT)
            val songs = tracks.filter { it.title.isNotBlank() && it.artist.isNotBlank() }
                .distinctBy { key(it.title) to key(it.artist) }
            val albums = songs.filter { !it.album.isNullOrBlank() }
                .distinctBy { key(it.album.orEmpty()) to key(it.artist) }
                .map { CanonicalAlbum(title = it.album!!.trim(), artist = it.artist,
                    artworkUrl = it.artworkUrl, releaseYear = it.releaseYear, genre = it.genre) }
            val artists = songs.distinctBy { key(it.artist) }.map {
                CanonicalArtist(name = it.artist, artworkUrl = it.artworkUrl, genre = it.genre)
            }
            return CategoryBrowseResults(songs, albums, artists)
        }
    }
}
