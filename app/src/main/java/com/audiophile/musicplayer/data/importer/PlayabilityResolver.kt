package com.audiophile.musicplayer.data.importer

class PlayabilityResolver {
    fun resolve(
        bestCandidate: TrackMatchCandidate?,
        candidateCount: Int,
        parsedTitle: String?,
        isEnhanced: Boolean = false
    ): PlayabilityStatus {
        if (parsedTitle.isNullOrBlank()) return PlayabilityStatus.NOT_FOUND
        if (bestCandidate == null) return PlayabilityStatus.NOT_FOUND

        if (candidateCount > 1 && bestCandidate.confidence == MatchConfidence.LOW) {
            return PlayabilityStatus.NEEDS_REVIEW
        }

        return when {
            bestCandidate.isPlayable -> if (isEnhanced) PlayabilityStatus.PLAYABLE_ENHANCED else PlayabilityStatus.PLAYABLE
            bestCandidate.confidence == MatchConfidence.LOW -> PlayabilityStatus.NEEDS_REVIEW
            else -> if (isEnhanced) PlayabilityStatus.METADATA_ONLY_ENHANCED else PlayabilityStatus.METADATA_ONLY
        }
    }

    fun isValidStreamUrl(value: String?): Boolean {
        val trimmed = value?.trim().orEmpty()
        return trimmed.startsWith("http://", ignoreCase = true) ||
            trimmed.startsWith("https://", ignoreCase = true) ||
            trimmed.startsWith("content://", ignoreCase = true) ||
            trimmed.startsWith("file://", ignoreCase = true)
    }
}
