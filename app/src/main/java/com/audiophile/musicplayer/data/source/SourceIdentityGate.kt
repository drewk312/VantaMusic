package com.audiophile.musicplayer.data.source

import android.util.Log

data class SelectedRecordingIdentity(
    val title: String,
    val artist: String,
    val durationMs: Long? = null,
    val isrc: String? = null,
    val preferredProviderId: String? = null,
    val preferredExternalTrackId: String? = null,
    val userQuery: String? = null
)

data class SourceCandidateEvaluation(
    val accepted: Boolean,
    val score: Int,
    val variantType: VariantClassifier.VariantType,
    val identityConfidence: Float,
    val rejectionReason: String?
)

/**
 * Hard gate + ranking for source candidates. Identity beats quality.
 */
object SourceIdentityGate {

    fun evaluate(
        selected: SelectedRecordingIdentity,
        candidateTitle: String,
        candidateArtist: String,
        candidateAlbum: String? = null,
        candidateDurationMs: Long? = null,
        candidateIsrc: String? = null,
        providerId: String? = null,
        bitrateKbps: Int = 0,
        isExactProviderMatch: Boolean = false
    ): SourceCandidateEvaluation {
        val (rejected, variantReason) = VariantClassifier.isRejectedForStudioIntent(
            title = candidateTitle,
            artist = candidateArtist,
            album = candidateAlbum,
            userQuery = selected.userQuery
        )
        val classification = VariantClassifier.classify(candidateTitle, candidateArtist, candidateAlbum, selected.userQuery)

        var score = 0
        var identityConfidence = 0.35f

        val isIsrcMatch = !selected.isrc.isNullOrBlank() && selected.isrc.equals(candidateIsrc, ignoreCase = true)
        val titleMatches = titleMatchesSelected(selected.title, candidateTitle, selected.artist)
        val artistMatches = artistMatchesSelected(selected.artist, candidateArtist)
        val isTrustedExactIdentity = isExactProviderMatch || isIsrcMatch

        if (isExactProviderMatch) {
            score += 500
            identityConfidence = 1.0f
        }
        if (isIsrcMatch) {
            score += 300
            identityConfidence = maxOf(identityConfidence, 0.95f)
        }
        if (candidateTitle.equals(selected.title, ignoreCase = true)) {
            score += 120
            identityConfidence = maxOf(identityConfidence, 0.8f)
        } else if (candidateTitle.contains(selected.title, ignoreCase = true) ||
            selected.title.contains(candidateTitle, ignoreCase = true)
        ) {
            score += 60
        }
        if (candidateArtist.equals(selected.artist, ignoreCase = true)) {
            score += 100
            identityConfidence = maxOf(identityConfidence, 0.85f)
        } else if (candidateArtist.contains(selected.artist, ignoreCase = true) ||
            selected.artist.contains(candidateArtist, ignoreCase = true)
        ) {
            score += 40
        }

        selected.durationMs?.let { expected ->
            candidateDurationMs?.let { actual ->
                val delta = kotlin.math.abs(actual - expected)
                if (delta < 15_000L) {
                    score += 40
                    identityConfidence = maxOf(identityConfidence, 0.75f)
                } else if (delta > 90_000L) {
                    score -= 30
                }
            }
        }

        if (!isTrustedExactIdentity) {
            if (!titleMatches) {
                score -= 500
            }
            if (!artistMatches) {
                score -= 500
            }
        }

        when (classification.variantType) {
            VariantClassifier.VariantType.STUDIO_VOCAL -> score += 80
            VariantClassifier.VariantType.UNKNOWN -> score += 10
            else -> score -= 250
        }

        score += (bitrateKbps / 100).coerceAtMost(20)
        score += providerQualityBonus(providerId)

        val durationMismatch = selected.durationMs != null &&
            candidateDurationMs != null &&
            kotlin.math.abs(candidateDurationMs - selected.durationMs) > 120_000L
        val identityRejected = !isTrustedExactIdentity && (!titleMatches || !artistMatches || durationMismatch)
        val accepted = !rejected && !identityRejected && score > Int.MIN_VALUE / 4
        val rejectionReason = when {
            rejected -> variantReason
            identityRejected && !titleMatches -> "wrong_title"
            identityRejected && !artistMatches -> "wrong_artist"
            identityRejected && durationMismatch -> "wrong_duration"
            score <= Int.MIN_VALUE / 4 -> "low_identity_score"
            else -> null
        }

        logCandidate(
            selectedTitle = selected.title,
            selectedArtist = selected.artist,
            candidateTitle = candidateTitle,
            candidateArtist = candidateArtist,
            provider = providerId,
            duration = candidateDurationMs,
            variantType = classification.variantType,
            score = score,
            accepted = accepted,
            rejectionReason = rejectionReason
        )

        return SourceCandidateEvaluation(
            accepted = accepted,
            score = score,
            variantType = classification.variantType,
            identityConfidence = identityConfidence,
            rejectionReason = rejectionReason
        )
    }

    fun evaluateSearchResult(
        selected: SelectedRecordingIdentity,
        candidate: SourceSearchResult
    ): SourceCandidateEvaluation {
        val exactProvider = !selected.preferredProviderId.isNullOrBlank() &&
            !selected.preferredExternalTrackId.isNullOrBlank() &&
            selected.preferredProviderId == candidate.providerId &&
            selected.preferredExternalTrackId == candidate.id
        return evaluate(
            selected = selected,
            candidateTitle = candidate.title,
            candidateArtist = candidate.artist,
            candidateAlbum = candidate.album,
            candidateDurationMs = candidate.durationMs,
            candidateIsrc = candidate.isrc,
            providerId = candidate.providerId,
            isExactProviderMatch = exactProvider
        )
    }

    fun logSelected(
        title: String,
        artist: String,
        provider: String?,
        variantType: VariantClassifier.VariantType,
        score: Int,
        identityConfidence: Float
    ) {
        Log.i(
            "VANTA_SOURCE_SELECTED",
            "title=$title artist=$artist provider=${provider ?: "null"} " +
                "variantType=$variantType score=$score identityConfidence=$identityConfidence"
        )
    }

    private fun providerQualityBonus(providerId: String?): Int {
        if (providerId == null) return 0
        return when {
            providerId.contains("qobuz", ignoreCase = true) -> 30
            providerId.contains("tidal", ignoreCase = true) -> 30
            providerId.contains("deezer", ignoreCase = true) -> 20
            providerId.contains("pandora", ignoreCase = true) -> 15
            providerId.contains("amazon", ignoreCase = true) -> 20
            providerId.contains("youtube", ignoreCase = true) -> 0
            providerId == "cloudflare_gateway" -> 25
            else -> 5
        }
    }

    private fun titleMatchesSelected(selectedTitle: String, candidateTitle: String, selectedArtist: String): Boolean {
        val expected = normalizeTitle(selectedTitle, selectedArtist)
        val actual = normalizeTitle(candidateTitle, selectedArtist)
        if (expected.isBlank() || actual.isBlank()) return false
        if (expected == actual) return true
        if ((actual.contains(expected) || expected.contains(actual)) &&
            minOf(expected.length, actual.length).toFloat() / maxOf(expected.length, actual.length).coerceAtLeast(1) >= 0.72f
        ) {
            return true
        }
        return tokenOverlap(expected, actual) >= 0.72f
    }

    private fun artistMatchesSelected(selectedArtist: String, candidateArtist: String): Boolean {
        val expected = normalizeArtist(selectedArtist)
        val actual = normalizeArtist(candidateArtist)
        if (expected.isBlank() || actual.isBlank()) return false
        if (expected == actual) return true
        return (actual.contains(expected) || expected.contains(actual)) &&
            minOf(expected.length, actual.length).toFloat() / maxOf(expected.length, actual.length).coerceAtLeast(1) >= 0.72f
    }

    private fun normalizeTitle(value: String, selectedArtist: String): String {
        val artist = normalizeArtist(selectedArtist)
        var normalized = value.lowercase()
            .replace(Regex("""\([^)]*\b(official|audio|video|hd|hq|lyrics?|visualizer)\b[^)]*\)"""), " ")
            .replace(Regex("""\[[^]]*\b(official|audio|video|hd|hq|lyrics?|visualizer)\b[^]]*]"""), " ")
            .replace(Regex("""\b(official|audio|video|hd|hq|lyrics?|visualizer)\b"""), " ")
            // Strip variant suffixes like "(Instrumental)", "(Piano Version)", "(Live at Wembley)"
            .replace(Regex("""\([^)]*\b(instrumental|karaoke|piano version|piano cover|acoustic|live|remix|cover|tribute|slowed|reverb|8d|8 d|nightcore|unplugged|dj mix|workout|tiktok version|full concert|performance|session)\b[^)]*\)"""), " ")
            .replace(Regex("""\[[^]]*\b(instrumental|karaoke|piano version|piano cover|acoustic|live|remix|cover|tribute|slowed|reverb|8d|8 d|nightcore|unplugged|dj mix|workout|tiktok version|full concert|performance|session)\b[^]]*]"""), " ")
            .replace(Regex("""\b(instrumental|karaoke|piano version|piano cover|acoustic version|live version|live at|live from|remix|cover version|tribute to|slowed \+ reverb|sped up|nightcore|unplugged|dj mix)\b"""), " ")
            .replace(Regex("""[^\p{L}\p{N}\s]+"""), " ")
            .replace(Regex("""\s+"""), " ")
            .trim()
        if (artist.isNotBlank()) {
            normalized = normalized
                .removePrefix(artist)
                .trim()
                .removePrefix("-")
                .trim()
        }
        return normalized
    }

    private fun normalizeArtist(value: String): String =
        value.lowercase()
            .replace("official", " ")
            .replace("vevo", " ")
            .replace("topic", " ")
            .replace(Regex("""[^\p{L}\p{N}\s]+"""), " ")
            .replace(Regex("""\s+"""), " ")
            .trim()

    private fun tokenOverlap(a: String, b: String): Float {
        val aTokens = a.split(" ").filter { it.length > 1 }.toSet()
        val bTokens = b.split(" ").filter { it.length > 1 }.toSet()
        if (aTokens.isEmpty() || bTokens.isEmpty()) return 0f
        return aTokens.intersect(bTokens).size.toFloat() / maxOf(aTokens.size, bTokens.size)
    }

    private fun logCandidate(
        selectedTitle: String,
        selectedArtist: String,
        candidateTitle: String,
        candidateArtist: String,
        provider: String?,
        duration: Long?,
        variantType: VariantClassifier.VariantType,
        score: Int,
        accepted: Boolean,
        rejectionReason: String?
    ) {
        Log.d(
            "VANTA_SOURCE_CANDIDATE",
            "selectedTitle=$selectedTitle selectedArtist=$selectedArtist " +
                "candidateTitle=$candidateTitle candidateArtist=$candidateArtist " +
                "provider=${provider ?: "null"} duration=${duration ?: "null"} " +
                "variantType=$variantType score=$score accepted=$accepted " +
                "rejectionReason=${rejectionReason ?: "none"}"
        )
    }
}
