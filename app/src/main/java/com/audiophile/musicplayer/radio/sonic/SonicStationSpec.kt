package com.audiophile.musicplayer.radio.sonic

typealias RadioDiscoveryMode = com.audiophile.musicplayer.radio.RadioDiscoveryMode

data class SonicStationSpec(
    val id: String,
    val title: String,
    val eraStart: Int,
    val eraEnd: Int,
    val seeds: List<String>,
    val forbiddenGenres: List<String> = emptyList(),
    val isOneHitWonderOnly: Boolean = false
)
