package com.audiophile.musicplayer.discovery.personalized

/**
 * Stable identifiers for personalized mix kinds. Only kinds with real generators are
 * registered and surfaced; the remaining IDs are reserved for future implementations.
 */
enum class PersonalizedMixKind(val id: String) {
    DISCOVERY_WEEKLY("discovery_weekly"),
    RELEASE_RADAR("release_radar"),
    HIDDEN_GEMS("hidden_gems"),
    DAILY_MIX("daily_mix"),
    DISCOVERY_SHUFFLE("discovery_shuffle"),
    POPULAR_PICKS("popular_picks"),
    TIME_MACHINE("time_machine"),
    GENRE_PLAYLIST("genre_playlist"),
    SEASONAL_MIX("seasonal_mix");

    companion object {
        fun fromId(id: String): PersonalizedMixKind? =
            entries.firstOrNull { it.id == id }
    }
}
