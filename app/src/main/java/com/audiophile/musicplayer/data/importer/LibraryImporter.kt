package com.audiophile.musicplayer.data.importer

import com.audiophile.musicplayer.data.local.entities.ImportBatchEntity
import com.audiophile.musicplayer.data.local.entities.ImportedTrackEntity
import com.audiophile.musicplayer.data.metadata.MetadataResolver
import com.audiophile.musicplayer.data.metadata.apple.AppleMusicMetadataProvider
import com.audiophile.musicplayer.data.repository.LocalLibraryRepository

enum class ImportMatchStatus {
    MATCHED,
    NEEDS_REVIEW,
    NOT_FOUND
}

data class ImportedTrack(
    val rawText: String,
    val parsedTitle: String?,
    val parsedArtist: String?,
    val parsedAlbum: String? = null,
    val matchStatus: ImportMatchStatus,
    val playabilityStatus: PlayabilityStatus,
    val matchConfidence: MatchConfidence,
    val matchReason: String? = null,
    val friendlySourceLabel: String? = null,
    val matchedSongId: Long? = null,
    val confidenceScore: Float = 0f,
    val sourceUrl: String? = null
)

class LibraryImporter(
    private val repository: LocalLibraryRepository,
    private val matchingService: TrackMatchingService = TrackMatchingService(),
    private val metadataResolver: MetadataResolver? = null,
    private val appleMusicMetadataProvider: AppleMusicMetadataProvider? = null,
    private val collectionResolver: CollectionResolver = CollectionResolver()
) {
    private val platformLinkResolver: PlatformLinkResolver = PlatformLinkResolver(
        appleMusicProvider = appleMusicMetadataProvider
    )

    private fun convertCollectionToLines(result: CollectionResult): List<ParsedPlaylistLine> {
        return result.tracks.map { track ->
            ParsedPlaylistLine(
                rawLine = "${track.title} - ${track.artist}",
                title = track.title,
                artist = track.artist,
                album = track.album ?: result.collectionTitle,
                sourceUrl = result.sourceUrl,
                sourcePlatform = result.platform,
                sourceId = null,
                preserved = false
            )
        }
    }

    /** Expands any collection URLs in the parsed lines into individual track lines. */
    private suspend fun expandCollectionUrls(lines: List<ParsedPlaylistLine>): List<ParsedPlaylistLine> {
        val result = mutableListOf<ParsedPlaylistLine>()
        for (line in lines) {
            val url = line.sourceUrl?.takeIf { it.isNotBlank() }
            if (url != null && CollectionResolver.isCollectionUrl(url)) {
                val resolved = collectionResolver.resolve(url)
                if (resolved != null && resolved.tracks.isNotEmpty()) {
                    result.addAll(convertCollectionToLines(resolved))
                } else {
                    result.add(line)
                }
            } else {
                result.add(line)
            }
        }
        return result
    }

    suspend fun importPastedText(importName: String, pastedText: String): Long {
        val trimmed = pastedText.trim()
        val lines = if (CollectionResolver.isCollectionUrl(trimmed)) {
            val resolved = collectionResolver.resolve(trimmed)
            if (resolved != null) {
                convertCollectionToLines(resolved).ifEmpty {
                    SoundiizTextParser.parseBlock(pastedText)
                }
            } else {
                SoundiizTextParser.parseBlock(pastedText)
            }
        } else {
            SoundiizTextParser.parseBlock(pastedText)
        }

        val expanded = if (lines.size == 1 && CollectionResolver.isCollectionUrl(trimmed)) {
            lines
        } else {
            expandCollectionUrls(lines)
        }
        val catalog = repository.allSongsSnapshot()
        val imported = expanded.map { parsed ->
            val linkMetadata = platformLinkResolver.resolve(parsed)
            val importRow = parsed.withResolvedLink(linkMetadata)
            val match = matchingService.matchImportedTrack(importRow, catalog)
            val candidate = match.bestCandidate
            val isEnhanced = metadataResolver != null && candidate != null && metadataResolver.resolveAndEnrich(candidate.candidateSong).album != null
            val finalPlayabilityStatus = PlayabilityResolver().resolve(candidate, match.candidates.size, importRow.title, isEnhanced)
            val fallbackMetadataOnly = candidate == null && !importRow.title.isNullOrBlank()

            val matchStatus = if (fallbackMetadataOnly) {
                ImportMatchStatus.NEEDS_REVIEW
            } else {
                when (finalPlayabilityStatus) {
                    PlayabilityStatus.PLAYABLE,
                    PlayabilityStatus.PLAYABLE_ENHANCED,
                    PlayabilityStatus.METADATA_ONLY,
                    PlayabilityStatus.METADATA_ONLY_ENHANCED -> ImportMatchStatus.MATCHED
                    PlayabilityStatus.NEEDS_REVIEW,
                    PlayabilityStatus.ERROR -> ImportMatchStatus.NEEDS_REVIEW
                    PlayabilityStatus.NOT_FOUND -> ImportMatchStatus.NOT_FOUND
                }
            }
            val resolvedPlayability = if (fallbackMetadataOnly) {
                PlayabilityStatus.NEEDS_REVIEW
            } else {
                finalPlayabilityStatus
            }
            ImportedTrack(
                rawText = importRow.rawLine,
                parsedTitle = importRow.title,
                parsedArtist = importRow.artist,
                parsedAlbum = importRow.album,
                matchStatus = matchStatus,
                playabilityStatus = resolvedPlayability,
                matchConfidence = candidate?.confidence ?: if (fallbackMetadataOnly) MatchConfidence.MEDIUM else MatchConfidence.NONE,
                matchReason = candidate?.reason ?: linkMetadata?.matchReason ?: if (fallbackMetadataOnly) "Parsed import row; source will be resolved when saving" else null,
                friendlySourceLabel = candidate?.let { friendlySourceLabel(it) } ?: linkMetadata?.platform?.let { "$it link" } ?: if (fallbackMetadataOnly) "Ready to resolve" else null,
                matchedSongId = candidate?.candidateSong?.id,
                confidenceScore = confidenceScore(candidate?.confidence ?: if (fallbackMetadataOnly) MatchConfidence.MEDIUM else MatchConfidence.NONE),
                sourceUrl = candidate?.candidateSong?.streamUrl ?: importRow.sourceUrl
            )
        }

        val batchId = repository.saveImportBatch(
            ImportBatchEntity(
                name = importName.ifBlank { "Soundiiz import" },
                sourceName = "Soundiiz",
                sourceLabel = friendlyImportSourceLabel(importName, pastedText),
                totalTracks = imported.size,
                playableCount = imported.count {
                    it.playabilityStatus == PlayabilityStatus.PLAYABLE ||
                        it.playabilityStatus == PlayabilityStatus.PLAYABLE_ENHANCED
                },
                metadataOnlyCount = imported.count {
                    it.playabilityStatus == PlayabilityStatus.METADATA_ONLY ||
                        it.playabilityStatus == PlayabilityStatus.METADATA_ONLY_ENHANCED
                },
                matchedCount = imported.count { it.matchStatus == ImportMatchStatus.MATCHED },
                needsReviewCount = imported.count { it.matchStatus == ImportMatchStatus.NEEDS_REVIEW },
                notFoundCount = imported.count { it.matchStatus == ImportMatchStatus.NOT_FOUND }
            )
        )

        val entities = imported.map { track ->
            ImportedTrackEntity(
                batchId = batchId,
                rawText = track.rawText,
                parsedTitle = track.parsedTitle,
                parsedArtist = track.parsedArtist,
                parsedAlbum = track.parsedAlbum,
                sourceUrl = track.sourceUrl,
                matchStatus = track.matchStatus,
                playabilityStatus = track.playabilityStatus,
                matchConfidence = track.matchConfidence,
                matchReason = track.matchReason,
                friendlySourceLabel = track.friendlySourceLabel,
                matchedSongId = track.matchedSongId,
                confidenceScore = track.confidenceScore
            )
        }
        repository.saveImportedTracks(entities)
        return batchId
    }

    suspend fun rerunMatchingForBatch(batchId: Long): List<ImportedTrackEntity> {
        val existing = repository.importedTracksByBatchSnapshot(batchId)
        val catalog = repository.allSongsSnapshot()
        val updated = existing.map { row ->
            val reparsed = SoundiizTextParser.parseLine(row.rawText)
            val parsed = ParsedPlaylistLine(
                rawLine = row.rawText,
                title = row.userEditedTitle ?: row.parsedTitle ?: reparsed.title,
                artist = row.userEditedArtist ?: row.parsedArtist ?: reparsed.artist,
                album = row.parsedAlbum ?: reparsed.album,
                sourceUrl = reparsed.sourceUrl,
                sourcePlatform = reparsed.sourcePlatform,
                sourceId = reparsed.sourceId
            )
            val linkMetadata = platformLinkResolver.resolve(parsed)
            val importRow = parsed.withResolvedLink(linkMetadata)
            val match = matchingService.matchImportedTrack(importRow, catalog)
            val candidate = match.bestCandidate
            val isEnhanced = metadataResolver != null && candidate != null && metadataResolver.resolveAndEnrich(candidate.candidateSong).album != null
            val finalPlayabilityStatus = PlayabilityResolver().resolve(candidate, match.candidates.size, importRow.title, isEnhanced)
            val fallbackMetadataOnly = candidate == null && !importRow.title.isNullOrBlank()

            val matchStatus = if (fallbackMetadataOnly) {
                ImportMatchStatus.NEEDS_REVIEW
            } else {
                when (finalPlayabilityStatus) {
                    PlayabilityStatus.PLAYABLE,
                    PlayabilityStatus.PLAYABLE_ENHANCED,
                    PlayabilityStatus.METADATA_ONLY,
                    PlayabilityStatus.METADATA_ONLY_ENHANCED -> ImportMatchStatus.MATCHED
                    PlayabilityStatus.NEEDS_REVIEW,
                    PlayabilityStatus.ERROR -> ImportMatchStatus.NEEDS_REVIEW
                    PlayabilityStatus.NOT_FOUND -> ImportMatchStatus.NOT_FOUND
                }
            }
            val resolvedPlayability = if (fallbackMetadataOnly) {
                PlayabilityStatus.NEEDS_REVIEW
            } else {
                finalPlayabilityStatus
            }
            row.copy(
                parsedTitle = importRow.title,
                parsedArtist = importRow.artist,
                parsedAlbum = importRow.album,
                sourceUrl = candidate?.candidateSong?.streamUrl ?: importRow.sourceUrl,
                matchStatus = matchStatus,
                playabilityStatus = resolvedPlayability,
                matchConfidence = candidate?.confidence ?: if (fallbackMetadataOnly) MatchConfidence.MEDIUM else MatchConfidence.NONE,
                matchReason = candidate?.reason ?: linkMetadata?.matchReason ?: if (fallbackMetadataOnly) "Parsed import row; source will be resolved when saving" else null,
                friendlySourceLabel = candidate?.let { friendlySourceLabel(it) } ?: linkMetadata?.platform?.let { "$it link" } ?: if (fallbackMetadataOnly) "Ready to resolve" else null,
                matchedSongId = candidate?.candidateSong?.id,
                confidenceScore = confidenceScore(candidate?.confidence ?: if (fallbackMetadataOnly) MatchConfidence.MEDIUM else MatchConfidence.NONE),
                updatedAt = System.currentTimeMillis()
            )
        }
        repository.updateImportedTracks(updated)
        return updated
    }

    private fun ParsedPlaylistLine.withResolvedLink(linkMetadata: PlatformLinkMetadata?): ParsedPlaylistLine {
        if (linkMetadata == null) return this
        return copy(
            title = title ?: linkMetadata.title,
            artist = artist ?: linkMetadata.artist,
            album = album ?: linkMetadata.album,
            sourcePlatform = sourcePlatform ?: linkMetadata.platform,
            sourceId = sourceId ?: linkMetadata.externalId,
            preserved = false
        )
    }

    private fun confidenceScore(confidence: MatchConfidence): Float = when (confidence) {
        MatchConfidence.EXACT -> 0.98f
        MatchConfidence.HIGH -> 0.86f
        MatchConfidence.MEDIUM -> 0.68f
        MatchConfidence.LOW -> 0.42f
        MatchConfidence.NONE -> 0f
    }

    private fun friendlySourceLabel(candidate: TrackMatchCandidate): String {
        if (!candidate.hasStreamUrl) return "Matched"
        val quality = candidate.candidateSong.quality.orEmpty().lowercase()
        return when {
            "lossless" in quality || "flac" in quality -> "Lossless"
            "preview" in quality -> "Preview"
            else -> "High Quality"
        }
    }

    private fun friendlyImportSourceLabel(importName: String, pastedText: String): String {
        val haystack = "$importName\n$pastedText".lowercase()
        return when {
            "tunemymusic" in haystack || "tune my music" in haystack -> "TuneMyMusic Import"
            "spotify" in haystack -> "Spotify Export"
            "apple" in haystack -> "Apple Music Export"
            "m3u" in haystack -> "M3U Import"
            "," in pastedText && pastedText.lineSequence().any { it.count { ch -> ch == ',' } >= 2 } -> "CSV Import"
            else -> "Soundiiz Import"
        }
    }
}
