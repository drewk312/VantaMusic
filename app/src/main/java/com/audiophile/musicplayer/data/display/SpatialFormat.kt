package com.audiophile.musicplayer.data.display

/**
 * Single canonical spatial format model, in sync with the music gateway.
 * Only a decoded/proven stream may be stamped with an explicit value:
 * [DOLBY_ATMOS] requires an E-AC-3 JOC (or AC-4 immersive) signal,
 * [SONY_360_REALITY_AUDIO] requires MPEG-H / 360 Reality Audio wording,
 * and [ECLIPSA_AUDIO] requires IAMF evidence. Marketing labels alone are
 * never proof and resolve to [NONE] or [UNKNOWN_SPATIAL].
 */
enum class SpatialFormat {
    NONE,
    DOLBY_ATMOS,
    SONY_360_REALITY_AUDIO,
    ECLIPSA_AUDIO,
    UNKNOWN_SPATIAL
}