package com.audiophile.musicplayer.data.importer

import com.audiophile.musicplayer.data.local.entities.LocalSongEntity

enum class PlayabilityStatus {
    PLAYABLE,
    PLAYABLE_ENHANCED,
    METADATA_ONLY,
    METADATA_ONLY_ENHANCED,
    NEEDS_REVIEW,
    NOT_FOUND,
    ERROR
}

enum class MatchConfidence {
    EXACT,
    HIGH,
    MEDIUM,
    LOW,
    NONE
}

data class TrackMatchCandidate(
    val candidateSong: LocalSongEntity,
    val confidence: MatchConfidence,
    val reason: String,
    val source: String,
    val isPlayable: Boolean = false,
    val hasStreamUrl: Boolean = false
)

data class ImportMatchResult(
    val importedTitle: String,
    val importedArtist: String,
    val importedAlbum: String? = null,
    val matchedId: String? = null,
    val matchedTitle: String? = null,
    val matchedArtist: String? = null,
    val confidence: Float = 0f,
    val status: MatchStatus = MatchStatus.UNMATCHED
)

enum class MatchStatus {
    UNMATCHED,
    PARTIAL_MATCH,
    EXACT_MATCH,
    RESOLVING,
    RESOLVED,
    FAILED
}

data class ImportBatch(
    val name: String,
    val source: String,
    val tracks: List<ImportMatchResult>,
    val createdAt: Long = System.currentTimeMillis()
)
