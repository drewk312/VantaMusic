package com.audiophile.musicplayer.data.importer

import com.audiophile.musicplayer.common.VantaLogger
import com.audiophile.musicplayer.data.local.entities.LocalSongEntity
import com.audiophile.musicplayer.data.local.entities.SourceType
import com.audiophile.musicplayer.data.repository.LocalLibraryRepository

/** Result of a liked-songs CSV import. */
data class LikedCsvImportResult(
    val totalRows: Int,
    val likedExisting: Int,
    val addedNew: Int,
    val skippedBlank: Int,
    val autoLikedCount: Int
)

/**
 * Generic CSV import that treats the file as a list of songs the user loves.
 * Every row that already exists in the local library is auto-liked; rows that are
 * not in the library are inserted as favorite (liked) metadata entries so they
 * appear immediately in Liked Songs and can be stream-resolved later.
 */
class LikedSongsCsvImporter(
    private val repository: LocalLibraryRepository
) {
    suspend fun importLikedCsv(text: String): LikedCsvImportResult {
        val parsed = SoundiizTextParser.parseBlock(text)
            .filter { !it.title.isNullOrBlank() }
        if (parsed.isEmpty()) {
            return LikedCsvImportResult(
                totalRows = 0,
                likedExisting = 0,
                addedNew = 0,
                skippedBlank = text.lines().count { it.isNotBlank() },
                autoLikedCount = 0
            )
        }

        val catalog = repository.allSongsSnapshot()
        val matcher = TrackMatchingService()
        val idsToLike = LinkedHashSet<Long>()
        val newFavorites = mutableListOf<LocalSongEntity>()

        parsed.forEachIndexed { index, line ->
            val title = line.title?.trim() ?: return@forEachIndexed
            val artist = line.artist?.trim() ?: return@forEachIndexed
            val match = matcher.matchImportedTrack(
                ParsedPlaylistLine(
                    rawLine = line.rawLine,
                    title = title,
                    artist = artist,
                    album = line.album
                ),
                catalog
            )
            val candidate = match.bestCandidate
            if (candidate != null && candidate.confidence.ordinal <= MatchConfidence.HIGH.ordinal) {
                idsToLike += candidate.candidateSong.id
            } else {
                val existing = catalog.firstOrNull { existingSong ->
                    existingSong.title.trim().equals(title, ignoreCase = true) &&
                        existingSong.artist.trim().equals(artist, ignoreCase = true)
                }
                if (existing != null) {
                    idsToLike += existing.id
                } else {
                    newFavorites += LocalSongEntity(
                        title = title,
                        artist = artist,
                        album = line.album,
                        isFavorite = true,
                        sourceType = SourceType.LOCAL,
                        importSource = "Liked Songs CSV"
                    )
                }
            }
            if (index % 500 == 0) {
                VantaLogger.d(
                    VantaLogger.Tag.LIBRARY,
                    "liked_csv_import progress=$index/${parsed.size} likedIds=${idsToLike.size} new=${newFavorites.size}"
                )
            }
        }

        if (idsToLike.isNotEmpty()) repository.likeSongs(idsToLike.toList())
        val savedIds = repository.saveSongs(newFavorites)

        VantaLogger.d(
            VantaLogger.Tag.LIBRARY,
            "liked_csv_import done total=${parsed.size} likedExisting=${idsToLike.size} addedNew=${savedIds.size}"
        )

        return LikedCsvImportResult(
            totalRows = parsed.size,
            likedExisting = idsToLike.size,
            addedNew = savedIds.size,
            skippedBlank = 0,
            autoLikedCount = idsToLike.size + savedIds.size
        )
    }
}