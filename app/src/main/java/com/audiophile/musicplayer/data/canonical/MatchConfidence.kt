package com.audiophile.musicplayer.data.canonical

/**
 * Explicit match confidence for graph resolution.
 * FUZZY_CANDIDATE must never auto-merge into an existing entity.
 */
enum class MatchConfidence {
    EXACT_PROVIDER_ID,
    EXACT_ISRC,
    EXACT_UPC,
    EXACT_NORMALIZED_METADATA,
    HIGH_CONFIDENCE_METADATA,
    FUZZY_CANDIDATE,
    UNRESOLVED
}

data class ResolveResult<T>(
    val entity: T?,
    val method: MatchConfidence,
    val confidence: Float,
    val created: Boolean = false,
    val upgradedFields: List<String> = emptyList()
)
