package com.audiophile.musicplayer.data.source

object SourceCandidateRanker {

    fun rankSearchResults(
        selected: SelectedRecordingIdentity,
        candidates: List<SourceSearchResult>
    ): List<SourceSearchResult> {
        return candidates
            .map { candidate -> candidate to SourceIdentityGate.evaluateSearchResult(selected, candidate) }
            .filter { (_, evaluation) -> evaluation.accepted }
            .sortedWith(
                compareByDescending<Pair<SourceSearchResult, SourceCandidateEvaluation>> { it.second.score }
                    .thenByDescending { SourceIdentityGate.playbackProviderRank(it.first.providerId) }
            )
            .map { (candidate, _) -> candidate }
    }

    fun bestSearchResult(
        selected: SelectedRecordingIdentity,
        candidates: List<SourceSearchResult>
    ): SourceSearchResult? = rankSearchResults(selected, candidates).firstOrNull()
}
