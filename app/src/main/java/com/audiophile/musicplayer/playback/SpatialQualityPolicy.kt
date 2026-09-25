package com.audiophile.musicplayer.playback

import com.audiophile.musicplayer.data.source.playback.RequestedAudioQuality

/** Auto asks the gateway for Atmos then 360 then FLAC; device output does not skip that hunt. */
@Suppress("UNUSED_PARAMETER")
internal fun selectSpatialQuality(requested: RequestedAudioQuality, atmosOutput: Boolean, iamfDecoder: Boolean,
    softwareAtmos: Boolean = false, mpeghOutput: Boolean = false): RequestedAudioQuality = when {
    requested == RequestedAudioQuality.AUTO_SPATIAL -> RequestedAudioQuality.AUTO_SPATIAL
    requested == RequestedAudioQuality.ATMOS -> RequestedAudioQuality.ATMOS
    requested == RequestedAudioQuality.SONY_360 -> RequestedAudioQuality.SONY_360
    requested == RequestedAudioQuality.IAMF && !iamfDecoder -> RequestedAudioQuality.HI_RES_24
    else -> requested
}

internal fun shouldRecoverStalledPlayback(wantsPlayback: Boolean, suppressed: Boolean,
    ended: Boolean, previousPosition: Long, currentPosition: Long): Boolean =
    wantsPlayback && !suppressed && !ended && currentPosition == previousPosition
