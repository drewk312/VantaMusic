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
        // Provider IDs are routing hints, not proof of recording identity.
        // Upstream mappings and persisted IDs can be stale or incorrect.
        val isTrustedExactIdentity = isIsrcMatch || (isExactProviderMatch && titleMatches && artistMatches)

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

    fun isImmersivePlaybackProvider(providerId: String?): Boolean {
        val id = providerId?.lowercase().orEmpty()
        return "tidal" in id || "amazon" in id || "cloudflare_gateway" in id || id == "gateway"
    }

    fun isSupplementalPlaybackProvider(providerId: String?): Boolean {
        val id = providerId?.lowercase().orEmpty()
        return "youtube" in id
    }

    fun isCatalogPlaybackProvider(providerId: String?): Boolean {
        if (providerId.isNullOrBlank() || isSupplementalPlaybackProvider(providerId)) return false
        val id = providerId.lowercase()
        return "qobuz" in id ||
            "tidal" in id ||
            "deezer" in id ||
            "amazon" in id ||
            "pandora" in id ||
            "gateway" in id ||
            id == "cloudflare_gateway"
    }

    /**
     * Pick the stream that will actually sound like the SpotiFLAC file:
     * measured/hi-res bitrate and Atmos first, catalog next, YouTube last.
     */
    fun streamPlaybackScore(providerId: String?, stream: ResolvedStream): Int {
        val supplementalPenalty = if (isSupplementalPlaybackProvider(providerId)) 100_000 else 0
        val catalogBonus = if (isCatalogPlaybackProvider(providerId)) 10_000 else 0
        return stream.fidelityScore() + catalogBonus - supplementalPenalty
    }

    /** Higher wins when choosing a playback/search row. YouTube stays last. */
    fun playbackProviderRank(
        providerId: String?,
        prefersSpatial: Boolean = com.audiophile.musicplayer.data.source.external.SpotiFlacEndpoints.prefersSpatialMix()
    ): Int {
        val id = providerId?.lowercase().orEmpty()
        return if (prefersSpatial) {
            when {
                "tidal" in id -> 130
                "amazon" in id -> 110
                id == "cloudflare_gateway" -> 95
                "qobuz" in id -> 80
                "deezer" in id -> 70
                "pandora" in id -> 50
                "youtube" in id -> 5
                else -> 20
            }
        } else {
            when {
                "qobuz" in id -> 100
                "tidal" in id -> 90
                id == "cloudflare_gateway" -> 80
                "deezer" in id -> 70
                "amazon" in id -> 60
                "pandora" in id -> 50
                "youtube" in id -> 5
                else -> 20
            }
        }
    }

    fun sourceDisplayPriority(providerId: String?): Int = playbackProviderRank(providerId)

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
            minOf(expected.length, actual.length).toFloat() / maxOf(expected.length, actual.length).coerceAtLeast(1) >= 0.85f
        ) {
            return true
        }
        return tokenOverlap(expected, actual) >= 0.80f
    }

    private fun artistMatchesSelected(selectedArtist: String, candidateArtist: String): Boolean {
        val expected = normalizeArtist(selectedArtist)
        val actual = normalizeArtist(candidateArtist)
        if (expected.isBlank() || actual.isBlank()) return false
        if (expected == actual) return true
        if (actual.contains(expected) || expected.contains(actual)) {
            if (minOf(expected.length, actual.length).toFloat() / maxOf(expected.length, actual.length).coerceAtLeast(1) >= 0.72f) {
                return true
            }
        }
        // Feat awareness: strip "feat.*" from candidate before comparing
        val cleanedActual = actual.replace(Regex("""\s*feat[^\w]*[\w\s]+"""), " ").trim()
        if (cleanedActual.isNotBlank() && (cleanedActual == expected || cleanedActual.contains(expected) || expected.contains(cleanedActual))) {
            return true
        }
        return tokenOverlap(expected, actual) >= 0.72f
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
            .normalizeDigitWords()
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
            .normalizeDigitWords()
            .trim()

    private fun String.normalizeDigitWords(): String {
        var result = this
        DIGIT_WORD_MAP.forEach { (word, digit) ->
            result = result.replace(word, digit)
        }
        return result
    }

    private val DIGIT_WORD_MAP = mapOf(
        "zero" to "0", "one" to "1", "two" to "2", "three" to "3", "four" to "4",
        "five" to "5", "six" to "6", "seven" to "7", "eight" to "8", "nine" to "9",
        "ten" to "10", "eleven" to "11", "twelve" to "12", "thirteen" to "13",
        "fourteen" to "14", "fifteen" to "15", "sixteen" to "16", "seventeen" to "17",
        "eighteen" to "18", "nineteen" to "19", "twenty" to "20", "thirty" to "30",
        "forty" to "40", "fifty" to "50", "sixty" to "60", "seventy" to "70",
        "eighty" to "80", "ninety" to "90", "hundred" to "00", "thousand" to "000"
    )

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
