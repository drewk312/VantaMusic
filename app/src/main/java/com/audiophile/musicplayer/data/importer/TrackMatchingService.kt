package com.audiophile.musicplayer.data.importer

import com.audiophile.musicplayer.data.local.entities.LocalSongEntity
import kotlin.math.max

data class TrackMatchResult(
    val bestCandidate: TrackMatchCandidate?,
    val candidates: List<TrackMatchCandidate>,
    val playabilityStatus: PlayabilityStatus
)

class TrackMatchingService(
    private val playabilityResolver: PlayabilityResolver = PlayabilityResolver()
) {
    fun matchImportedTrack(
        parsed: ParsedPlaylistLine,
        catalog: List<LocalSongEntity>
    ): TrackMatchResult {
        val title = parsed.title?.trim().orEmpty()
        val artist = parsed.artist?.trim().orEmpty()
        if (title.isBlank()) {
            return TrackMatchResult(null, emptyList(), PlayabilityStatus.NOT_FOUND)
        }

        val candidates = catalog
            .mapNotNull { song -> scoreCandidate(song, title, artist, null, null) }
            .sortedWith(
                compareByDescending<TrackMatchCandidate> { it.confidence.ordinalForSort() }
                    .thenByDescending { numericScoreForReason(it.reason) }
            )

        val best = candidates.firstOrNull()
        return TrackMatchResult(
            bestCandidate = best,
            candidates = candidates,
            playabilityStatus = playabilityResolver.resolve(best, candidates.size, title)
        )
    }

    fun matchLocalSong(
        song: LocalSongEntity,
        catalog: List<LocalSongEntity>
    ): TrackMatchResult {
        val parsed = ParsedPlaylistLine(
            rawLine = "${song.title} - ${song.artist}",
            title = song.title,
            artist = song.artist
        )
        return matchImportedTrack(parsed, catalog)
    }

    private fun scoreCandidate(
        song: LocalSongEntity,
        title: String,
        artist: String,
        album: String?,
        durationMs: Long?
    ): TrackMatchCandidate? {
        val normalizedTitle = normalize(title)
        val normalizedArtist = normalize(artist)
        val songTitle = normalize(song.title)
        val songArtist = normalize(song.artist)
        val songAlbum = normalize(song.album.orEmpty())

        val titleExact = normalizedTitle == songTitle
        val artistExact = normalizedArtist.isNotBlank() && normalizedArtist == songArtist
        val albumExact = !album.isNullOrBlank() && normalize(album) == songAlbum
        val durationClose = durationMs != null && song.durationMs != null &&
            kotlin.math.abs(durationMs - song.durationMs) <= 2_000L

        val confidence = when {
            titleExact && artistExact -> MatchConfidence.EXACT
            titleExact && (albumExact || durationClose) -> MatchConfidence.HIGH
            titleExact -> MatchConfidence.MEDIUM
            fuzzyScore(title, artist, song) >= 0.55f -> MatchConfidence.LOW
            else -> MatchConfidence.NONE
        }
        if (confidence == MatchConfidence.NONE) return null

        val streamUrlPresent = !song.streamUrl.isNullOrBlank()
        val playable = PlayabilityResolver().isValidStreamUrl(song.streamUrl)
        return TrackMatchCandidate(
            candidateSong = song,
            confidence = confidence,
            reason = when (confidence) {
                MatchConfidence.EXACT -> "Exact title and artist"
                MatchConfidence.HIGH -> "Strong title match with album or duration support"
                MatchConfidence.MEDIUM -> "Title match"
                MatchConfidence.LOW -> "Partial fuzzy match"
                MatchConfidence.NONE -> "No match"
            },
            source = "Local library",
            isPlayable = playable,
            hasStreamUrl = streamUrlPresent
        )
    }

    private fun fuzzyScore(title: String, artist: String, song: LocalSongEntity): Float {
        val titleScore = tokenOverlap(title, song.title)
        val artistScore = if (artist.isBlank()) 0f else tokenOverlap(artist, song.artist)
        return max(titleScore * 0.7f + artistScore * 0.3f, titleScore)
    }

    private fun tokenOverlap(left: String, right: String): Float {
        val leftTokens = normalize(left).split(" ").filter { it.isNotBlank() }.toSet()
        val rightTokens = normalize(right).split(" ").filter { it.isNotBlank() }.toSet()
        if (leftTokens.isEmpty() || rightTokens.isEmpty()) return 0f
        return leftTokens.intersect(rightTokens).size.toFloat() / leftTokens.union(rightTokens).size.toFloat()
    }

    private fun normalize(value: String): String {
        return value.lowercase()
            .replace(Regex("""[^\p{L}\p{N}]+"""), " ")
            .trim()
            .replace(Regex("""\s+"""), " ")
    }

    private fun MatchConfidence.ordinalForSort(): Int = when (this) {
        MatchConfidence.EXACT -> 5
        MatchConfidence.HIGH -> 4
        MatchConfidence.MEDIUM -> 3
        MatchConfidence.LOW -> 2
        MatchConfidence.NONE -> 1
    }

    private fun numericScoreForReason(reason: String): Int = when {
        reason.startsWith("Exact") -> 4
        reason.startsWith("Strong") -> 3
        reason.startsWith("Title") -> 2
        reason.startsWith("Partial") -> 1
        else -> 0
    }
}
