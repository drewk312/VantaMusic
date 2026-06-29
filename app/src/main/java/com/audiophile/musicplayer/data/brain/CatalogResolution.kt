package com.audiophile.musicplayer.data.brain

import com.audiophile.musicplayer.data.canonical.CanonicalTrack
import com.audiophile.musicplayer.data.source.SourceSearchResult
import com.audiophile.musicplayer.data.source.VariantClassifier

data class CatalogCandidateDecision(
    val title: String,
    val artist: String,
    val album: String?,
    val provider: String?,
    val variantType: VariantClassifier.VariantType,
    val matchTier: MatchTier,
    val vocalExpected: Boolean,
    val lyricsExpected: Boolean,
    val eligible: Boolean,
    val reason: String
)

data class SourceSelectionDecision(
    val acceptedSource: SourceSearchResult?,
    val rejectedSources: List<Pair<SourceSearchResult, String>>,
    val matchTier: MatchTier,
    val variantType: VariantClassifier.VariantType,
    val reason: String
)

data class CatalogResolution(
    val canonicalTrackIdentity: CanonicalTrack?,
    val fingerprint: RecordingFingerprint?,
    val matchTier: MatchTier,
    val variantType: VariantClassifier.VariantType,
    val vocalExpected: Boolean,
    val lyricsExpected: Boolean,
    val acceptedSource: SourceSearchResult?,
    val rejectedSources: List<Pair<SourceSearchResult, String>>,
    val rejectionReasons: List<String>,
    val reason: String
)
