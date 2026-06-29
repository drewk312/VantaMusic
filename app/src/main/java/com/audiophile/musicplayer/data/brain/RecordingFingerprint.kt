package com.audiophile.musicplayer.data.brain

import com.audiophile.musicplayer.data.canonical.CanonicalTrack

data class RecordingFingerprint(
    val normalizedTitle: String,
    val normalizedPrimaryArtist: String,
    val featuredArtists: List<String>,
    val durationBucketSec: Int?,
    val isrc: String?,
    val albumContext: String?,
    val explicit: Boolean?,
    val versionLabel: String?
)

object RecordingFingerprintFactory {
    fun fromCanonicalTrack(track: CanonicalTrack, featuredArtists: List<String> = track.featuredArtists): RecordingFingerprint =
        RecordingFingerprint(
            normalizedTitle = normalize(track.title),
            normalizedPrimaryArtist = normalize(track.artist),
            featuredArtists = featuredArtists.map(::normalize).filter { it.isNotBlank() },
            durationBucketSec = track.durationMs?.let { (it / 1000L / 5L).toInt() * 5 },
            isrc = track.isrc?.trim()?.uppercase(),
            albumContext = track.album?.trim(),
            explicit = track.explicit,
            versionLabel = track.qualityInfo?.reason
        )
}
