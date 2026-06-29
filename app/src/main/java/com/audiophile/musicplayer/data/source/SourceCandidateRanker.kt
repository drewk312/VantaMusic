package com.audiophile.musicplayer.data.source

object SourceCandidateRanker {

    fun rankSearchResults(
        selected: SelectedRecordingIdentity,
        candidates: List<SourceSearchResult>
    ): List<SourceSearchResult> {
        return candidates
            .map { candidate -> candidate to SourceIdentityGate.evaluateSearchResult(selected, candidate) }
            .filter { (_, evaluation) -> evaluation.accepted }
            .sortedByDescending { (_, evaluation) -> evaluation.score }
            .map { (candidate, _) -> candidate }
    }

    fun bestSearchResult(
        selected: SelectedRecordingIdentity,
        candidates: List<SourceSearchResult>
    ): SourceSearchResult? = rankSearchResults(selected, candidates).firstOrNull()
}
