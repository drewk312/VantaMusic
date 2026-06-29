package com.audiophile.musicplayer.discovery.personalized

object TrackNormKey {
    fun normalize(title: String, artist: String): String =
        "${title.trim().lowercase()}|${artist.trim().lowercase()}"
}
