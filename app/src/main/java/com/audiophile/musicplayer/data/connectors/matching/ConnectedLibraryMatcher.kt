package com.audiophile.musicplayer.data.connectors.matching

import com.audiophile.musicplayer.data.canonical.CanonicalIdentityResolver
import com.audiophile.musicplayer.data.connectors.ConnectedLibraryMatchStatus
import com.audiophile.musicplayer.data.connectors.ImportedLibraryTrack
import com.audiophile.musicplayer.data.connectors.ProviderTrackLink
import com.audiophile.musicplayer.data.local.entities.UnifiedTrackWithSources
import com.audiophile.musicplayer.data.source.SourceSearchResult
import com.audiophile.musicplayer.data.source.VariantClassifier
import java.util.Locale
import kotlin.math.abs

data class ConnectedLibraryMatchResult(
    val status: ConnectedLibraryMatchStatus,
    val vantaCanonicalTrackId: String? = null,
    val vantaLocalTrackId: Long? = null,
    val confidence: Float,
    val reason: String
)

class ConnectedLibraryMatcher {

    fun match(
        imported: ImportedLibraryTrack,
        localCatalog: List<UnifiedTrackWithSources>,
        gatewayResults: List<SourceSearchResult> = emptyList(),
        existingLinks: List<ProviderTrackLink> = emptyList()
    ): ConnectedLibraryMatchResult {
        existingLinks.firstOrNull {
            it.provider == imported.provider && it.providerTrackId == imported.providerTrackId
        }?.let { link ->
            return ConnectedLibraryMatchResult(
                status = if (link.vantaLocalTrackId != null) {
                    ConnectedLibraryMatchStatus.MATCHED_LOCAL
                } else {
                    ConnectedLibraryMatchStatus.MATCHED_CANONICAL
                },
                vantaCanonicalTrackId = link.vantaCanonicalTrackId,
                vantaLocalTrackId = link.vantaLocalTrackId,
                confidence = link.matchConfidence,
                reason = "provider_link"
            )
        }

        rejectWrongVersion(imported)?.let { return it }

        val isrc = imported.isrc?.normalizedIsrc()
        if (!isrc.isNullOrBlank()) {
            localCatalog.firstOrNull { it.track.isrc?.normalizedIsrc() == isrc }?.let { local ->
                return localResult(local.track.trackId, imported, 0.99f, "isrc_exact")
            }
            gatewayResults.firstOrNull { it.isrc?.normalizedIsrc() == isrc }?.let { remote ->
                return canonicalResult(remote, 0.96f, "isrc_exact_gateway")
            }
        }

        bestLocalMatch(imported, localCatalog)?.let { (track, confidence, reason) ->
            return localResult(track.track.trackId, imported, confidence, reason)
        }

        bestGatewayMatch(imported, gatewayResults)?.let { (result, confidence, reason) ->
            return canonicalResult(result, confidence, reason)
        }

        return ConnectedLibraryMatchResult(
            status = ConnectedLibraryMatchStatus.METADATA_ONLY,
            confidence = 0f,
            reason = "unmatched_metadata_only"
        )
    }

    fun linkFor(imported: ImportedLibraryTrack, result: ConnectedLibraryMatchResult, linkedAt: Long): ProviderTrackLink? {
        if (result.status != ConnectedLibraryMatchStatus.MATCHED_LOCAL &&
            result.status != ConnectedLibraryMatchStatus.MATCHED_CANONICAL
        ) {
            return null
        }
        return ProviderTrackLink(
            id = "${imported.provider.name}:${imported.providerTrackId}",
            provider = imported.provider,
            providerTrackId = imported.providerTrackId,
            providerUri = imported.providerUri,
            isrc = imported.isrc,
            vantaCanonicalTrackId = result.vantaCanonicalTrackId,
            vantaLocalTrackId = result.vantaLocalTrackId,
            matchConfidence = result.confidence,
            linkedAt = linkedAt
        )
    }

    private fun rejectWrongVersion(imported: ImportedLibraryTrack): ConnectedLibraryMatchResult? {
        val classification = VariantClassifier.classify(imported.title, imported.artist, imported.album)
        return if (classification.variantType == VariantClassifier.VariantType.COVER ||
            classification.variantType == VariantClassifier.VariantType.KARAOKE ||
            classification.variantType == VariantClassifier.VariantType.INSTRUMENTAL
        ) {
            ConnectedLibraryMatchResult(
                status = ConnectedLibraryMatchStatus.REJECTED_WRONG_VERSION,
                confidence = classification.confidence,
                reason = classification.rejectionReason ?: "wrong_version"
            )
        } else {
            null
        }
    }

    private fun bestLocalMatch(
        imported: ImportedLibraryTrack,
        catalog: List<UnifiedTrackWithSources>
    ): Triple<UnifiedTrackWithSources, Float, String>? {
        return catalog.mapNotNull { local ->
            val score = metadataScore(
                title = imported.title,
                artist = imported.artist,
                album = imported.album,
                durationMs = imported.durationMs,
                candidateTitle = local.track.title,
                candidateArtist = local.track.artist,
                candidateAlbum = local.track.albumName,
                candidateDurationMs = local.track.durationMs
            )
            val threshold = if (sameDurationBucket(imported.durationMs, local.track.durationMs)) 0.78f else 0.86f
            if (score >= threshold) {
                Triple(local, score, if (sameDurationBucket(imported.durationMs, local.track.durationMs)) "title_artist_duration" else "title_artist_album")
            } else {
                null
            }
        }.maxByOrNull { it.second }
    }

    private fun bestGatewayMatch(
        imported: ImportedLibraryTrack,
        results: List<SourceSearchResult>
    ): Triple<SourceSearchResult, Float, String>? {
        return results.mapNotNull { remote ->
            val score = metadataScore(
                title = imported.title,
                artist = imported.artist,
                album = imported.album,
                durationMs = imported.durationMs,
                candidateTitle = remote.title,
                candidateArtist = remote.artist,
                candidateAlbum = remote.album,
                candidateDurationMs = remote.durationMs
            )
            if (score >= 0.84f) Triple(remote, score, "gateway_metadata_match") else null
        }.maxByOrNull { it.second }
    }

    private fun localResult(
        localTrackId: Long,
        imported: ImportedLibraryTrack,
        confidence: Float,
        reason: String
    ): ConnectedLibraryMatchResult =
        ConnectedLibraryMatchResult(
            status = ConnectedLibraryMatchStatus.MATCHED_LOCAL,
            vantaCanonicalTrackId = canonicalId(imported.title, imported.artist),
            vantaLocalTrackId = localTrackId,
            confidence = confidence,
            reason = reason
        )

    private fun canonicalResult(
        result: SourceSearchResult,
        confidence: Float,
        reason: String
    ): ConnectedLibraryMatchResult =
        ConnectedLibraryMatchResult(
            status = ConnectedLibraryMatchStatus.MATCHED_CANONICAL,
            vantaCanonicalTrackId = canonicalId(result.title, result.artist),
            confidence = confidence,
            reason = reason
        )

    private fun metadataScore(
        title: String,
        artist: String,
        album: String?,
        durationMs: Long?,
        candidateTitle: String,
        candidateArtist: String,
        candidateAlbum: String?,
        candidateDurationMs: Long?
    ): Float {
        var score = 0f
        if (normalize(title) == normalize(candidateTitle)) score += 0.44f
        if (normalize(artist) == normalize(candidateArtist)) score += 0.34f
        if (!album.isNullOrBlank() && normalize(album) == normalize(candidateAlbum)) score += 0.12f
        if (sameDurationBucket(durationMs, candidateDurationMs)) score += 0.10f
        return score.coerceAtMost(1f)
    }

    private fun sameDurationBucket(a: Long?, b: Long?): Boolean {
        if (a == null || b == null || a <= 0L || b <= 0L) return false
        return abs(a - b) <= 15_000L
    }

    private fun canonicalId(title: String, artist: String): String =
        CanonicalIdentityResolver.generateCanonicalId(
            isrc = null,
            _unused = null,
            title = title,
            artist = artist,
            album = null,
            durationMs = null
        )

    private fun normalize(value: String?): String =
        value.orEmpty()
            .trim()
            .lowercase(Locale.US)
            .replace(Regex("""\s+"""), " ")
            .removeSuffix(" - remastered")

    private fun String.normalizedIsrc(): String = trim().uppercase(Locale.US)
}
