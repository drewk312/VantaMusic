package com.audiophile.musicplayer.data.lyrics

import com.audiophile.musicplayer.data.local.entities.UnifiedTrack

interface LyricsProvider {
    val providerId: String
    suspend fun getLyrics(track: UnifiedTrack, isrc: String?): LyricsData?
}
