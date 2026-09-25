package com.audiophile.musicplayer.radio.sonic

object StreamingEraFilter {
    fun passesEraGate(trackYear: Int, trackTitle: String, spec: SonicStationSpec): Boolean =
        com.audiophile.musicplayer.radio.StreamingEraFilter.passesEraGate(trackYear, trackTitle, spec)
}
