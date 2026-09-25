package com.audiophile.musicplayer.data.source.playback

/**
 * Source-acquisition adapter. Playback never talks to Tidal/Amazon/Qobuz
 * directly; adapters supply a URL/file plus truthful metadata, or a
 * structured error that is not collapsed into "needs token".
 */
interface PlaybackSourceAdapter {
    val adapterId: String
    val sourceLabel: String

    suspend fun resolve(request: PlaybackSourceRequest): PlaybackSourceOutcome
}
