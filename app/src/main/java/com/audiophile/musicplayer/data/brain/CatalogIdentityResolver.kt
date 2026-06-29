package com.audiophile.musicplayer.data.brain

import android.util.Log
import com.audiophile.musicplayer.data.canonical.CanonicalTrack
import com.audiophile.musicplayer.data.source.VariantClassifier
import com.audiophile.musicplayer.search.UnifiedSearchEngine

object CatalogIdentityResolver {
    fun evaluateCandidate(intent: CatalogIntent, track: CanonicalTrack): CatalogCandidateDecision {
        val score = UnifiedSearchEngine.score(
            intent = UnifiedSearchEngine.SearchQueryIntent(
                rawQuery = intent.rawQuery,
                songTitle = intent.title,
                primaryArtist = intent.primaryArtist,
                featuredArtists = intent.featuredArtists
            ),
            track = track
        )
        val variant = VariantClassifier.classify(track.title, track.artist, track.album, intent.rawQuery).variantType
        val softArtistMatch = intent.primaryArtist.isNullOrBlank() || artistAliasMatches(track.artist, intent.primaryArtist)
        val identitySafe = score.eligibleForTop ||
            (softArtistMatch && score.finalScore > 50) // Simplified fallback logic
        val exactProviderTap = track.externalTrackId != null && track.sourceProviderId != null && intent.exactUserTap
        val hasStrongIsrcEvidence = !track.isrc.isNullOrBlank() && score.finalScore >= 120
        val hasPlausibleStudioIdentity = score.finalScore >= 50 &&
            (softArtistMatch) &&
            (track.durationMs == null || score.finalScore >= 0)
        val tier = when {
            exactProviderTap -> MatchTier.S
            hasStrongIsrcEvidence && identitySafe -> MatchTier.A
            hasPlausibleStudioIdentity && identitySafe -> MatchTier.B
            score.finalScore > 0 -> MatchTier.C
            else -> MatchTier.X
        }
        val vocalExpected = variant == VariantClassifier.VariantType.STUDIO_VOCAL || intent.requestedVariant == RequestedVariant.NONE
        val lyricsExpected = LyricsEligibilityGate.lyricsExpected(track.title, track.artist, track.album, intent.rawQuery)
        val eligible = when {
            intent.exactUserTap -> tier != MatchTier.X
            else -> tier in setOf(MatchTier.S, MatchTier.A, MatchTier.B)
        }
        val reason = when {
            eligible -> "catalog_match"
            else -> "not_safe_for_autoplay"
        }
        logCandidate(
            title = track.title,
            artist = track.artist,
            album = track.album,
            provider = track.sourceProviderId,
            variant = variant,
            tier = tier,
            eligible = eligible,
            reason = reason
        )
        return CatalogCandidateDecision(
            title = track.title,
            artist = track.artist,
            album = track.album,
            provider = track.sourceProviderId,
            variantType = variant,
            matchTier = tier,
            vocalExpected = vocalExpected,
            lyricsExpected = lyricsExpected,
            eligible = eligible,
            reason = reason
        )
    }

    fun resolveTopCandidate(intent: CatalogIntent, tracks: List<CanonicalTrack>): Pair<CanonicalTrack?, CatalogCandidateDecision?> {
        val searchIntent = UnifiedSearchEngine.SearchQueryIntent(
            rawQuery = intent.rawQuery,
            songTitle = intent.title,
            primaryArtist = intent.primaryArtist,
            featuredArtists = intent.featuredArtists
        )
        val scoreByTrack = UnifiedSearchEngine.rank(searchIntent, tracks).associate { it.first to it.second.finalScore }
        val ranked = tracks.map { it to evaluateCandidate(intent, it) }
        val selected = ranked
            .sortedWith(
                compareBy<Pair<CanonicalTrack, CatalogCandidateDecision>> { tierPriority(it.second.matchTier) }
                    .thenByDescending { scoreByTrack[it.first] ?: Int.MIN_VALUE / 4 }
            )
            .firstOrNull { (_, decision) -> decision.eligible }
            ?: ranked.firstOrNull()
        return selected?.first to selected?.second
    }

    private fun tierPriority(tier: MatchTier): Int = when (tier) {
        MatchTier.S -> 0
        MatchTier.A -> 1
        MatchTier.B -> 2
        MatchTier.C -> 3
        MatchTier.X -> 4
    }

    private fun logCandidate(
        title: String,
        artist: String,
        album: String?,
        provider: String?,
        variant: VariantClassifier.VariantType,
        tier: MatchTier,
        eligible: Boolean,
        reason: String
    ) {
        Log.d(
            "VANTA_BRAIN_CANDIDATE",
            "title=$title artist=$artist album=${album ?: ""} provider=${provider ?: "null"} " +
                "variant=$variant tier=$tier eligible=$eligible reason=$reason"
        )
    }
}
