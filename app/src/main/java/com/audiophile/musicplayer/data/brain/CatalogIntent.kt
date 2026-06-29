package com.audiophile.musicplayer.data.brain

enum class VantaIntentType {
    SONG, ARTIST, ALBUM, RADIO, GENERAL
}

enum class RequestedVariant {
    NONE, INSTRUMENTAL, KARAOKE, PIANO, LULLABY, ORCHESTRA,
    TRIBUTE, COVER, LIVE, REMIX, ACOUSTIC, SLOWED_SPED, UNKNOWN
}

enum class MatchTier {
    S, A, B, C, X
}

data class CatalogIntent(
    val rawQuery: String,
    val title: String?,
    val primaryArtist: String?,
    val featuredArtists: List<String>,
    val requestedVariant: RequestedVariant,
    val intentType: VantaIntentType,
    val exactUserTap: Boolean = false
)
