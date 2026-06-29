package com.audiophile.musicplayer.data.brain

import com.audiophile.musicplayer.data.source.VocalRecordingClassifier

object LyricsEligibilityGate {
    fun lyricsExpected(
        title: String?,
        artist: String?,
        album: String?,
        userQuery: String?
    ): Boolean = VocalRecordingClassifier.lyricsExpected(title, artist, album, userQuery)
}
