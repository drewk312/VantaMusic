package com.audiophile.musicplayer.radio.genome

/**
 * Pandora Station Modes: Listener-controlled steering modes introduced by Pandora
 * to modulate variety, familiarity, and energy on a station.
 */
enum class PandoraStationMode(
    val displayName: String,
    val description: String
) {
    BALANCED(
        displayName = "Balanced",
        description = "The classic Music Genome mix of familiar tracks and musical siblings"
    ),
    CROWD_FAVES(
        displayName = "Crowd Faves",
        description = "Top hits and fan-favorite anthems sharing this musical DNA"
    ),
    DISCOVERY(
        displayName = "Discovery",
        description = "Uncover rising and lesser-known artists matching this sound"
    ),
    DEEP_CUTS(
        displayName = "Deep Cuts",
        description = "Rare album tracks and B-sides from your favorite artists"
    ),
    ARTIST_ONLY(
        displayName = "Artist Only",
        description = "Strictly the seed artist and direct collaborations"
    ),
    CHILL(
        displayName = "Chill",
        description = "Mellow acoustic textures, lower tempo, and relaxed energy"
    ),
    UPBEAT(
        displayName = "Energy Up",
        description = "Higher tempo, driving rhythm grooves, and danceable intensity"
    );

    companion object {
        fun fromName(name: String?): PandoraStationMode {
            return entries.firstOrNull { it.name.equals(name, ignoreCase = true) } ?: BALANCED
        }
    }
}
