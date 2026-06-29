package com.audiophile.musicplayer.data.brain

import android.util.Log
import com.audiophile.musicplayer.data.canonical.CanonicalTrack
import com.audiophile.musicplayer.data.display.VantaQualityInfo
import com.audiophile.musicplayer.data.source.SelectedRecordingIdentity
import com.audiophile.musicplayer.data.source.SourceCandidateRanker
import com.audiophile.musicplayer.data.source.SourceIdentityGate
import com.audiophile.musicplayer.data.source.SourceSearchResult
import com.audiophile.musicplayer.data.source.VariantClassifier
import com.audiophile.musicplayer.data.taste.TasteVector
import com.audiophile.musicplayer.data.taste.TimeOfDay

object VantaMusicBrain {
    fun resolveCatalog(
        query: String,
        catalogCandidates: List<CanonicalTrack>,
        sourceCandidates: List<SourceSearchResult> = emptyList(),
        exactUserTap: Boolean = false,
        isRadioContext: Boolean = false
    ): CatalogResolution {
        val intent = IntentParser.parse(query, exactUserTap = exactUserTap)

        val filteredCandidates = if (isRadioContext && intent.requestedVariant == RequestedVariant.NONE) {
            catalogCandidates.filterNot { track ->
                val variant = VariantClassifier.classify(track.title, track.artist, track.album, intent.rawQuery).variantType
                variant == VariantClassifier.VariantType.LIVE ||
                    variant == VariantClassifier.VariantType.KARAOKE ||
                    (track.durationMs ?: 0L) > 600000L
            }
        } else {
            catalogCandidates
        }

        val catalogDecisions = filteredCandidates.map { it to CatalogIdentityResolver.evaluateCandidate(intent, it) }
        val (selectedTrack, selectedDecision) = CatalogIdentityResolver.resolveTopCandidate(intent, filteredCandidates)
        if (selectedTrack == null || selectedDecision == null) {
            return CatalogResolution(
                canonicalTrackIdentity = null,
                fingerprint = null,
                matchTier = MatchTier.X,
                variantType = VariantClassifier.VariantType.UNKNOWN,
                vocalExpected = false,
                lyricsExpected = false,
                acceptedSource = null,
                rejectedSources = emptyList(),
                rejectionReasons = listOf("no_candidates"),
                reason = "no_catalog_candidate"
            )
        }

        val fingerprint = RecordingFingerprintFactory.fromCanonicalTrack(selectedTrack, intent.featuredArtists)
        val identity = SelectedRecordingIdentity(
            title = selectedTrack.title,
            artist = selectedTrack.artist,
            durationMs = selectedTrack.durationMs,
            isrc = selectedTrack.isrc,
            preferredProviderId = selectedTrack.sourceProviderId,
            preferredExternalTrackId = selectedTrack.externalTrackId,
            userQuery = intent.rawQuery
        )
        val sourceDecision = selectSource(identity, sourceCandidates, selectedDecision.matchTier, selectedDecision.variantType)
        val catalogRejectedReasons = catalogDecisions
            .filter { (track, decision) -> track != selectedTrack || !decision.eligible }
            .mapNotNull { (_, decision) -> decision.reason.takeIf { it.isNotBlank() } }
            .distinct()
        Log.i(
            "VANTA_BRAIN_SELECTED",
            "title=${selectedTrack.title} artist=${selectedTrack.artist} provider=${sourceDecision.acceptedSource?.providerId ?: selectedTrack.sourceProviderId ?: "null"} " +
                "tier=${selectedDecision.matchTier} variant=${selectedDecision.variantType} quality=${qualityLabel(sourceDecision.acceptedSource, selectedTrack.qualityInfo)} reason=${sourceDecision.reason}"
        )
        return CatalogResolution(
            canonicalTrackIdentity = selectedTrack,
            fingerprint = fingerprint,
            matchTier = selectedDecision.matchTier,
            variantType = selectedDecision.variantType,
            vocalExpected = selectedDecision.vocalExpected,
            lyricsExpected = selectedDecision.lyricsExpected,
            acceptedSource = sourceDecision.acceptedSource,
            rejectedSources = sourceDecision.rejectedSources,
            rejectionReasons = (catalogRejectedReasons + sourceDecision.rejectedSources.map { it.second }).distinct(),
            reason = sourceDecision.reason
        )
    }

    fun selectSource(
        identity: SelectedRecordingIdentity,
        candidates: List<SourceSearchResult>,
        matchTier: MatchTier,
        variantType: VariantClassifier.VariantType
    ): SourceSelectionDecision {
        val ranked = SourceCandidateRanker.rankSearchResults(identity, candidates)
        val accepted = ranked.firstOrNull()
        val rejected = candidates
            .filterNot { ranked.contains(it) }
            .map { candidate ->
                candidate to (SourceIdentityGate.evaluateSearchResult(identity, candidate).rejectionReason ?: "rejected")
            }
        return SourceSelectionDecision(
            acceptedSource = accepted,
            rejectedSources = rejected,
            matchTier = matchTier,
            variantType = variantType,
            reason = when {
                accepted != null -> "accepted_source"
                rejected.isNotEmpty() -> rejected.first().second
                else -> "no_source_candidates"
            }
        )
    }

    fun buildDjContext(
        currentTrack: CanonicalTrack?,
        taste: TasteVector,
        djMode: String
    ): String {
        val timeContext = when (taste.timeOfDay) {
            TimeOfDay.MORNING -> "It is morning."
            TimeOfDay.AFTERNOON -> "It is afternoon."
            TimeOfDay.EVENING -> "It is evening."
            TimeOfDay.LATE_NIGHT -> "It is late at night."
        }

        val moodContext = when (taste.currentMood) {
            "rejecting_current_vibe" -> "The user has skipped several tracks recently. They are not feeling the current vibe."
            "locked_in" -> "The user is replaying tracks. They are locked into the music."
            else -> "The user is listening casually."
        }

        val trackContext = currentTrack?.let {
            "Currently playing: '${it.title}' by ${it.artist}."
        } ?: "No track is currently playing."

        return """
$timeContext
$moodContext
$trackContext
User's top artists: ${taste.artistAffinity.keys.take(3).joinToString(", ")}
Current DJ Mode: $djMode

Rules:
- Return JSON only.
- Be brief, warm, and confident.
- Never act like a radio host. Be a smart music friend.
- If the user is skipping, suggest a mood shift.
        """.trimIndent()
    }

    private fun qualityLabel(source: SourceSearchResult?, qualityInfo: VantaQualityInfo?): String =
        source?.qualityLabel ?: qualityInfo?.bestQualityLabel() ?: "unknown"
}

fun artistAliasMatches(candidateArtist: String, expectedArtist: String?): Boolean {
    if (expectedArtist.isNullOrBlank()) return true
    val candidate = normalize(candidateArtist).removePrefix("the ").trim()
    val expected = normalize(expectedArtist).removePrefix("the ").trim()
    if (candidate.isBlank() || expected.isBlank()) return false
    return candidate == expected || candidate.contains(expected) || expected.contains(candidate)
}

fun normalize(value: String?): String =
    value.orEmpty()
        .lowercase()
        .replace(Regex("""[^\p{L}\p{N}\s]+"""), " ")
        .replace(Regex("""\s+"""), " ")
        .trim()
