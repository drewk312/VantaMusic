package com.audiophile.musicplayer.radio.sonic

/**
 * The Smart Radio Engine handles scanning a library or candidate pool for closest matches
 * based on a seed song's sonic DNA, weighting by shared micro-genres, and sorting by Euclidean fit.
 */
class RadioEngine(private val library: List<SonicTrack>) {

    /**
     * Finds candidate tracks matching the seed song's sonic DNA.
     * @param seedTrack The song the user started the radio station from.
     * @param limit The pool size to select from.
     */
    fun getSimilarTracks(seedTrack: SonicTrack, limit: Int = 20): List<SonicTrack> {
        return getSimilarTracks(
            targetFeatures = seedTrack.features,
            seedSubGenres = seedTrack.subGenres,
            excludeIds = setOf(seedTrack.id),
            limit = limit
        )
    }

    /**
     * Finds candidate tracks matching a dynamic target feature vector (e.g. steered via thumbs),
     * applying relational micro-genre weighting and Euclidean distance sorting.
     */
    fun getSimilarTracks(
        targetFeatures: FeatureVector,
        seedSubGenres: Set<String> = emptySet(),
        excludeIds: Set<String> = emptySet(),
        limit: Int = 20
    ): List<SonicTrack> {
        return library
            .asSequence()
            // 1. Exclude forbidden or already-played IDs
            .filter { it.id !in excludeIds }
            // 2. Heavy relational tagging: prioritize shared micro-genres if available
            .map { candidate ->
                val sonicDistance = targetFeatures.distanceTo(candidate.features)

                // If they share a specific sub-genre, give it a similarity boost (lower distance)
                val sharedSubGenres = seedSubGenres.intersect(candidate.subGenres).size
                val adjustedDistance = if (sharedSubGenres > 0) {
                    sonicDistance * 0.8 // 20% boost to precision matching
                } else {
                    sonicDistance
                }

                Pair(candidate, adjustedDistance)
            }
            // 3. Sort by closest mathematical fit
            .sortedBy { it.second }
            .map { it.first }
            .take(limit)
            .toList()
    }
}
