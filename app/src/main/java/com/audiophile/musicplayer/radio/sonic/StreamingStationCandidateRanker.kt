package com.audiophile.musicplayer.radio.sonic

import com.audiophile.musicplayer.data.local.entities.TrackFeatureEntity

class StreamingStationCandidateRanker(private val discoveryMode: RadioDiscoveryMode) {

    fun rankCandidates(
        candidates: List<TrackFeatureEntity>,
        userFavoritesIds: Set<String>,
        recentHistoryTrackIds: Set<String>
    ): List<TrackFeatureEntity> {
        return candidates.map { track ->
            var score = 100.0

            val isFavorite = track.trackId in userFavoritesIds
            val isRecent = track.trackId in recentHistoryTrackIds

            when (discoveryMode) {
                RadioDiscoveryMode.MY_FAVORITES -> {
                    if (isFavorite) score += 80.0 else score -= 50.0
                    if (isRecent) score -= 20.0
                }
                RadioDiscoveryMode.HYBRID_MIX -> {
                    if (isFavorite) score += 30.0
                    if (isRecent) score -= 40.0
                }
                RadioDiscoveryMode.DEEP_DISCOVERY -> {
                    if (isFavorite || isRecent) {
                        score -= 200.0 // Push to bottom or eliminate
                    } else {
                        score += 35.0
                    }
                }
            }
            Pair(track, score)
        }
        .sortedByDescending { it.second }
        .map { it.first }
    }
}
