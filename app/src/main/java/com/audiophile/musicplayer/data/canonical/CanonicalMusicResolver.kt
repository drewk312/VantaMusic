package com.audiophile.musicplayer.data.canonical

import android.util.Log
import com.audiophile.musicplayer.data.display.DisplayMetadataCleaner
import com.audiophile.musicplayer.radio.SongRadioRelatedness
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Shared canonical music graph resolver.
 *
 * Enrichment may add truth; enrichment may never downgrade truth.
 * Weak fuzzy matches (FUZZY_CANDIDATE) never auto-merge.
 */
class CanonicalMusicResolver(
    private val dao: CanonicalGraphDao
) {
    data class TrackInput(
        val title: String,
        val artist: String,
        val album: String? = null,
        val isrc: String? = null,
        val durationMs: Long? = null,
        val artworkUrl: String? = null,
        val genre: String? = null,
        val trackNumber: Int? = null,
        val discNumber: Int? = null,
        val releaseYear: Int? = null,
        val explicit: Boolean? = null,
        val providerId: String? = null,
        val externalTrackId: String? = null,
        val externalArtistId: String? = null,
        val externalAlbumId: String? = null,
        val upc: String? = null,
        val unifiedTrackId: Long? = null,
        val localSongId: Long? = null
    )

    suspend fun resolveArtist(
        name: String,
        providerId: String? = null,
        externalArtistId: String? = null,
        artworkUrl: String? = null,
        genre: String? = null
    ): ResolveResult<CanonicalArtistEntity> = withContext(Dispatchers.IO) {
        val cleaned = DisplayMetadataCleaner.cleanArtistName(name)?.ifBlank { name.trim() } ?: name.trim()
        if (cleaned.isBlank() || cleaned.equals("Unknown Artist", ignoreCase = true)) {
            return@withContext ResolveResult(null, MatchConfidence.UNRESOLVED, 0f)
        }
        val normalized = normalizeArtistName(cleaned)

        // 1) Exact provider identity
        if (!providerId.isNullOrBlank() && !externalArtistId.isNullOrBlank()) {
            val ext = dao.artistExternal(providerId, externalArtistId)
            if (ext != null) {
                val artist = dao.artistById(ext.canonicalArtistId)
                if (artist != null) {
                    val upgraded = upgradeArtist(artist, cleaned, artworkUrl, genre)
                    logArtist("resolved", cleaned, providerId, externalArtistId, artist.artistId, MatchConfidence.EXACT_PROVIDER_ID, 1f)
                    return@withContext ResolveResult(upgraded, MatchConfidence.EXACT_PROVIDER_ID, 1f, upgradedFields = diffArtist(artist, upgraded))
                }
            }
        }

        // 2) Exact normalized name
        val byName = dao.artistByNormalizedName(normalized)
        if (byName != null) {
            // Guard Weeknd ↔ Weekend style collisions against a different existing name.
            if (SongRadioRelatedness.isArtistNameCollision(byName.canonicalName, cleaned, cleaned)) {
                logArtist("ambiguous_match", cleaned, providerId, externalArtistId, null, MatchConfidence.FUZZY_CANDIDATE, 0.3f)
                return@withContext ResolveResult(null, MatchConfidence.FUZZY_CANDIDATE, 0.3f)
            }
            val upgraded = upgradeArtist(byName, cleaned, artworkUrl, genre)
            if (!providerId.isNullOrBlank() && !externalArtistId.isNullOrBlank()) {
                dao.upsertArtistExternal(upgraded.artistId, providerId, externalArtistId, cleaned, 0.95f)
                logArtist("external_identity_added", cleaned, providerId, externalArtistId, upgraded.artistId, MatchConfidence.EXACT_NORMALIZED_METADATA, 0.95f)
            }
            logArtist("resolved", cleaned, providerId, externalArtistId, upgraded.artistId, MatchConfidence.EXACT_NORMALIZED_METADATA, 0.95f)
            return@withContext ResolveResult(upgraded, MatchConfidence.EXACT_NORMALIZED_METADATA, 0.95f, upgradedFields = diffArtist(byName, upgraded))
        }

        // 3) Near-homophone collision with an existing artist → do NOT merge
        // (scan is expensive; only check when names are short distinctive tokens)
        // Create new artist instead of fuzzy merge.

        val quality = metadataQualityArtist(cleaned, artworkUrl, genre, providerId, externalArtistId)
        val createdId = dao.insertArtist(
            CanonicalArtistEntity(
                canonicalName = cleaned,
                normalizedName = normalized,
                sortName = cleaned,
                artworkUrl = sanitizeArtwork(artworkUrl),
                genresJson = genre?.takeIf { it.isNotBlank() },
                metadataQuality = quality
            )
        )
        val created = dao.artistById(createdId)
        if (created != null && !providerId.isNullOrBlank() && !externalArtistId.isNullOrBlank()) {
            dao.upsertArtistExternal(createdId, providerId, externalArtistId, cleaned, 1f)
            logArtist("external_identity_added", cleaned, providerId, externalArtistId, createdId, MatchConfidence.EXACT_PROVIDER_ID, 1f)
        }
        logArtist("created", cleaned, providerId, externalArtistId, createdId, MatchConfidence.EXACT_NORMALIZED_METADATA, 0.9f)
        ResolveResult(created, MatchConfidence.EXACT_NORMALIZED_METADATA, 0.9f, created = true)
    }

    suspend fun resolveAlbum(
        title: String,
        artistName: String?,
        providerId: String? = null,
        externalAlbumId: String? = null,
        artworkUrl: String? = null,
        releaseYear: Int? = null,
        genre: String? = null,
        upc: String? = null,
        trackCount: Int? = null,
        externalArtistId: String? = null
    ): ResolveResult<CanonicalAlbumEntity> = withContext(Dispatchers.IO) {
        val cleanedTitle = DisplayMetadataCleaner.cleanTitle(title).ifBlank { title.trim() }
        if (cleanedTitle.isBlank()) {
            return@withContext ResolveResult(null, MatchConfidence.UNRESOLVED, 0f)
        }
        val artist = artistName?.takeIf { it.isNotBlank() }?.let {
            resolveArtist(it, providerId, externalArtistId, artworkUrl = null, genre = genre).entity
        }
        val normalizedTitle = normalizeAlbumTitle(cleanedTitle)

        if (!providerId.isNullOrBlank() && !externalAlbumId.isNullOrBlank()) {
            val ext = dao.albumExternal(providerId, externalAlbumId)
            if (ext != null) {
                val album = dao.albumById(ext.canonicalAlbumId)
                if (album != null) {
                    val upgraded = upgradeAlbum(album, cleanedTitle, artist?.artistId, artistName, artworkUrl, releaseYear, genre, upc, trackCount)
                    logAlbum("resolved", cleanedTitle, providerId, externalAlbumId, album.albumId, MatchConfidence.EXACT_PROVIDER_ID)
                    return@withContext ResolveResult(upgraded, MatchConfidence.EXACT_PROVIDER_ID, 1f, upgradedFields = diffAlbum(album, upgraded))
                }
            }
        }

        if (!upc.isNullOrBlank()) {
            val byUpc = dao.albumByUpc(upc.trim())
            if (byUpc != null) {
                val upgraded = upgradeAlbum(byUpc, cleanedTitle, artist?.artistId, artistName, artworkUrl, releaseYear, genre, upc, trackCount)
                logAlbum("resolved", cleanedTitle, providerId, externalAlbumId, byUpc.albumId, MatchConfidence.EXACT_UPC)
                return@withContext ResolveResult(upgraded, MatchConfidence.EXACT_UPC, 1f, upgradedFields = diffAlbum(byUpc, upgraded))
            }
        }

        val byMeta = dao.albumByNormalizedTitleArtist(normalizedTitle, artist?.artistId)
        if (byMeta != null) {
            val upgraded = upgradeAlbum(byMeta, cleanedTitle, artist?.artistId, artistName, artworkUrl, releaseYear, genre, upc, trackCount)
            if (!providerId.isNullOrBlank() && !externalAlbumId.isNullOrBlank()) {
                dao.insertAlbumExternal(
                    AlbumExternalIdentityEntity(
                        canonicalAlbumId = upgraded.albumId,
                        providerId = providerId,
                        externalAlbumId = externalAlbumId,
                        externalTitle = cleanedTitle,
                        confidence = 0.95f
                    )
                )
            }
            logAlbum("resolved", cleanedTitle, providerId, externalAlbumId, upgraded.albumId, MatchConfidence.EXACT_NORMALIZED_METADATA)
            return@withContext ResolveResult(upgraded, MatchConfidence.EXACT_NORMALIZED_METADATA, 0.95f, upgradedFields = diffAlbum(byMeta, upgraded))
        }

        val quality = metadataQualityAlbum(cleanedTitle, artistName, artworkUrl, releaseYear, upc, providerId, externalAlbumId)
        val createdId = dao.insertAlbum(
            CanonicalAlbumEntity(
                title = cleanedTitle,
                normalizedTitle = normalizedTitle,
                canonicalArtistId = artist?.artistId,
                albumArtistName = artist?.canonicalName ?: artistName,
                artworkUrl = sanitizeArtwork(artworkUrl),
                releaseYear = releaseYear,
                genre = genre,
                upc = upc?.takeIf { it.isNotBlank() },
                trackCount = trackCount,
                metadataQuality = quality
            )
        )
        if (!providerId.isNullOrBlank() && !externalAlbumId.isNullOrBlank()) {
            dao.insertAlbumExternal(
                AlbumExternalIdentityEntity(
                    canonicalAlbumId = createdId,
                    providerId = providerId,
                    externalAlbumId = externalAlbumId,
                    externalTitle = cleanedTitle,
                    confidence = 1f
                )
            )
        }
        val created = dao.albumById(createdId)
        logAlbum("created", cleanedTitle, providerId, externalAlbumId, createdId, MatchConfidence.EXACT_NORMALIZED_METADATA)
        ResolveResult(created, MatchConfidence.EXACT_NORMALIZED_METADATA, 0.9f, created = true)
    }

    suspend fun resolveTrack(input: TrackInput): ResolveResult<CanonicalTrackEntity> = withContext(Dispatchers.IO) {
        val cleanedTitle = DisplayMetadataCleaner.cleanTitle(input.title).ifBlank { input.title.trim() }
        val cleanedArtist = DisplayMetadataCleaner.cleanArtistName(input.artist)?.ifBlank { input.artist.trim() }
            ?: input.artist.trim()
        if (cleanedTitle.isBlank() || cleanedArtist.isBlank()) {
            return@withContext ResolveResult(null, MatchConfidence.UNRESOLVED, 0f)
        }

        val artistResult = resolveArtist(
            name = cleanedArtist,
            providerId = input.providerId,
            externalArtistId = input.externalArtistId,
            artworkUrl = null,
            genre = input.genre
        )
        val artist = artistResult.entity
        val album = input.album?.takeIf { it.isNotBlank() }?.let { albumTitle ->
            resolveAlbum(
                title = albumTitle,
                artistName = cleanedArtist,
                providerId = input.providerId,
                externalAlbumId = input.externalAlbumId,
                artworkUrl = input.artworkUrl,
                releaseYear = input.releaseYear,
                genre = input.genre,
                upc = input.upc,
                externalArtistId = input.externalArtistId
            ).entity
        }

        // 1) Exact provider track id
        if (!input.providerId.isNullOrBlank() && !input.externalTrackId.isNullOrBlank()) {
            val ext = dao.trackExternal(input.providerId, input.externalTrackId)
            if (ext != null) {
                val track = dao.trackById(ext.canonicalTrackId)
                if (track != null) {
                    val upgraded = upgradeTrack(track, input, artist?.artistId, album?.albumId, cleanedTitle, cleanedArtist)
                    logTrack("resolved", cleanedTitle, cleanedArtist, input.providerId, input.externalTrackId, track.trackId, MatchConfidence.EXACT_PROVIDER_ID)
                    return@withContext ResolveResult(upgraded, MatchConfidence.EXACT_PROVIDER_ID, 1f, upgradedFields = diffTrack(track, upgraded))
                }
            }
        }

        // 2) Exact ISRC
        val isrc = input.isrc?.trim()?.takeIf { it.isNotBlank() }
        if (isrc != null) {
            val byIsrc = dao.trackByIsrc(isrc.uppercase())
            if (byIsrc != null) {
                val upgraded = upgradeTrack(byIsrc, input, artist?.artistId, album?.albumId, cleanedTitle, cleanedArtist)
                if (!input.providerId.isNullOrBlank() && !input.externalTrackId.isNullOrBlank()) {
                    dao.insertTrackExternal(
                        TrackExternalIdentityEntity(
                            canonicalTrackId = upgraded.trackId,
                            providerId = input.providerId,
                            externalTrackId = input.externalTrackId,
                            externalArtistId = input.externalArtistId,
                            externalAlbumId = input.externalAlbumId,
                            isrc = isrc.uppercase(),
                            confidence = 1f
                        )
                    )
                }
                logTrack("resolved", cleanedTitle, cleanedArtist, input.providerId, input.externalTrackId, upgraded.trackId, MatchConfidence.EXACT_ISRC)
                return@withContext ResolveResult(upgraded, MatchConfidence.EXACT_ISRC, 1f, upgradedFields = diffTrack(byIsrc, upgraded))
            }
        }

        // 3) Exact normalized title + artist
        if (artist != null) {
            val byMeta = dao.trackByNormalizedTitleArtist(normalizeTrackTitle(cleanedTitle), artist.artistId)
            if (byMeta != null && versionsCompatible(byMeta.title, cleanedTitle)) {
                // Duration soft check
                val durationOk = durationsCompatible(byMeta.durationMs, input.durationMs)
                if (durationOk) {
                    val upgraded = upgradeTrack(byMeta, input, artist.artistId, album?.albumId, cleanedTitle, cleanedArtist)
                    if (!input.providerId.isNullOrBlank() && !input.externalTrackId.isNullOrBlank()) {
                        dao.insertTrackExternal(
                            TrackExternalIdentityEntity(
                                canonicalTrackId = upgraded.trackId,
                                providerId = input.providerId,
                                externalTrackId = input.externalTrackId,
                                externalArtistId = input.externalArtistId,
                                externalAlbumId = input.externalAlbumId,
                                isrc = isrc?.uppercase(),
                                confidence = 0.95f
                            )
                        )
                    }
                    logTrack("resolved", cleanedTitle, cleanedArtist, input.providerId, input.externalTrackId, upgraded.trackId, MatchConfidence.EXACT_NORMALIZED_METADATA)
                    return@withContext ResolveResult(upgraded, MatchConfidence.EXACT_NORMALIZED_METADATA, 0.95f, upgradedFields = diffTrack(byMeta, upgraded))
                }
            }
        }

        val quality = metadataQualityTrack(input, cleanedTitle, cleanedArtist)
        val createdId = dao.insertTrack(
            CanonicalTrackEntity(
                title = cleanedTitle,
                normalizedTitle = normalizeTrackTitle(cleanedTitle),
                artistDisplay = cleanedArtist,
                albumDisplay = album?.title ?: input.album,
                canonicalArtistId = artist?.artistId,
                canonicalAlbumId = album?.albumId,
                isrc = isrc?.uppercase(),
                durationMs = input.durationMs,
                discNumber = input.discNumber,
                trackNumber = input.trackNumber,
                explicit = input.explicit,
                genre = input.genre,
                artworkUrl = sanitizeArtwork(input.artworkUrl),
                unifiedTrackId = input.unifiedTrackId,
                localSongId = input.localSongId,
                metadataQuality = quality
            )
        )
        if (!input.providerId.isNullOrBlank() && !input.externalTrackId.isNullOrBlank()) {
            dao.insertTrackExternal(
                TrackExternalIdentityEntity(
                    canonicalTrackId = createdId,
                    providerId = input.providerId,
                    externalTrackId = input.externalTrackId,
                    externalArtistId = input.externalArtistId,
                    externalAlbumId = input.externalAlbumId,
                    isrc = isrc?.uppercase(),
                    confidence = 1f
                )
            )
        }
        val created = dao.trackById(createdId)
        logTrack("created", cleanedTitle, cleanedArtist, input.providerId, input.externalTrackId, createdId, MatchConfidence.EXACT_NORMALIZED_METADATA)
        ResolveResult(created, MatchConfidence.EXACT_NORMALIZED_METADATA, 0.9f, created = true)
    }

    suspend fun artistById(artistId: Long): CanonicalArtistEntity? = withContext(Dispatchers.IO) {
        dao.artistById(artistId)
    }

    suspend fun albumById(albumId: Long): CanonicalAlbumEntity? = withContext(Dispatchers.IO) {
        dao.albumById(albumId)
    }

    suspend fun tracksForArtist(artistId: Long): List<CanonicalTrackEntity> = withContext(Dispatchers.IO) {
        dao.tracksForArtist(artistId)
    }

    suspend fun tracksForAlbum(albumId: Long): List<CanonicalTrackEntity> = withContext(Dispatchers.IO) {
        dao.tracksForAlbum(albumId)
    }

    suspend fun albumsForArtist(artistId: Long): List<CanonicalAlbumEntity> = withContext(Dispatchers.IO) {
        dao.albumsForArtist(artistId)
    }

    suspend fun artistsMatchingPrefix(prefix: String, limit: Int = 8): List<CanonicalArtistEntity> =
        withContext(Dispatchers.IO) {
            val clean = prefix.trim().lowercase()
            if (clean.length < 2) emptyList()
            else dao.artistsMatchingPrefix(clean, limit)
        }

    suspend fun albumsMatchingPrefix(prefix: String, limit: Int = 8): List<CanonicalAlbumEntity> =
        withContext(Dispatchers.IO) {
            val clean = prefix.trim().lowercase()
            if (clean.length < 2) emptyList()
            else dao.albumsMatchingPrefix(clean, limit)
        }

    fun toDtoArtist(entity: CanonicalArtistEntity): CanonicalArtist =
        CanonicalArtist(
            name = entity.canonicalName,
            id = entity.artistId.toString(),
            genre = entity.genresJson,
            artworkUrl = entity.artworkUrl
        )

    fun toDtoAlbum(entity: CanonicalAlbumEntity): CanonicalAlbum =
        CanonicalAlbum(
            title = entity.title,
            artist = entity.albumArtistName.orEmpty(),
            id = entity.albumId.toString(),
            artworkUrl = entity.artworkUrl,
            releaseYear = entity.releaseYear,
            genre = entity.genre,
            trackCount = entity.trackCount
        )

    companion object {
        fun normalizeArtistName(value: String): String =
            value.trim().lowercase()
                .replace(Regex("""\b(vevo|topic|official|music)\b"""), " ")
                .replace(Regex("""[^\p{L}\p{N}\s]+"""), " ")
                .replace(Regex("""\s+"""), " ")
                .trim()

        fun normalizeAlbumTitle(value: String): String =
            value.trim().lowercase()
                .replace(Regex("""[^\p{L}\p{N}\s().+-]+"""), " ")
                .replace(Regex("""\s+"""), " ")
                .trim()

        fun normalizeTrackTitle(value: String): String =
            value.trim().lowercase()
                .replace(Regex("""\((official|audio|video|lyric|lyrics|visualizer|hd|4k).*?\)""", RegexOption.IGNORE_CASE), " ")
                .replace(Regex("""[^\p{L}\p{N}\s().+-]+"""), " ")
                .replace(Regex("""\s+"""), " ")
                .trim()

        fun artistsEquivalent(a: String, b: String): Boolean {
            if (normalizeArtistName(a) == normalizeArtistName(b)) {
                // Still reject Weeknd ↔ Weekend near-homophone if names aren't exact after normalize
                if (SongRadioRelatedness.isArtistNameCollision(a, b, b)) return false
                return true
            }
            return false
        }

        fun versionsCompatible(existingTitle: String, candidateTitle: String): Boolean {
            // Do not collapse studio recordings with video/upload packaging via title normalize alone.
            // ISRC / exact provider ID paths may still merge when evidence is stronger.
            val videoMarkers = listOf(
                "official audio", "official video", "lyric video", "lyrics video",
                "music video", "visualizer", "official lyric"
            )
            fun hasMarker(title: String, markers: List<String>): Boolean {
                val lower = title.lowercase()
                return markers.any { lower.contains(it) }
            }
            if (hasMarker(existingTitle, videoMarkers) != hasMarker(candidateTitle, videoMarkers)) {
                return false
            }
            val versionMarkers = listOf(
                "live", "remix", "acoustic", "instrumental", "sped up", "slowed",
                "nightcore", "karaoke", "deluxe", "remaster"
            )
            if (hasMarker(existingTitle, versionMarkers) != hasMarker(candidateTitle, versionMarkers)) {
                return false
            }
            val a = normalizeTrackTitle(existingTitle)
            val b = normalizeTrackTitle(candidateTitle)
            return a == b
        }

        fun durationsCompatible(a: Long?, b: Long?): Boolean {
            if (a == null || a <= 0L || b == null || b <= 0L) return true
            return kotlin.math.abs(a - b) <= 45_000L
        }

        fun sanitizeArtwork(url: String?): String? {
            val clean = url?.trim().orEmpty()
            if (clean.isBlank()) return null
            if (!clean.startsWith("http://") && !clean.startsWith("https://")) return null
            return clean
        }

        fun metadataQualityArtist(
            name: String,
            artworkUrl: String?,
            genre: String?,
            providerId: String?,
            externalArtistId: String?
        ): Int {
            var score = 10
            if (sanitizeArtwork(artworkUrl) != null) score += 20
            if (!genre.isNullOrBlank()) score += 10
            if (!providerId.isNullOrBlank() && !externalArtistId.isNullOrBlank()) score += 25
            if (name.contains("VEVO", ignoreCase = true) || name.contains(" - Topic", ignoreCase = true)) score -= 40
            return score.coerceAtLeast(0)
        }

        fun metadataQualityAlbum(
            title: String,
            artist: String?,
            artworkUrl: String?,
            releaseYear: Int?,
            upc: String?,
            providerId: String?,
            externalAlbumId: String?
        ): Int {
            var score = 10
            if (sanitizeArtwork(artworkUrl) != null) score += 20
            if (!artist.isNullOrBlank()) score += 10
            if (releaseYear != null) score += 10
            if (!upc.isNullOrBlank()) score += 20
            if (!providerId.isNullOrBlank() && !externalAlbumId.isNullOrBlank()) score += 25
            if (title.contains("Official Audio", ignoreCase = true)) score -= 30
            return score.coerceAtLeast(0)
        }

        fun metadataQualityTrack(input: TrackInput, title: String, artist: String): Int {
            var score = 10
            if (!input.isrc.isNullOrBlank()) score += 30
            if (sanitizeArtwork(input.artworkUrl) != null) score += 15
            if (!input.album.isNullOrBlank()) score += 15
            if (input.durationMs != null && input.durationMs > 0L) score += 10
            if (!input.providerId.isNullOrBlank() && !input.externalTrackId.isNullOrBlank()) score += 20
            if (title.contains("Official Audio", ignoreCase = true) ||
                title.contains("Music Video", ignoreCase = true) ||
                artist.contains("VEVO", ignoreCase = true) ||
                artist.contains(" - Topic", ignoreCase = true)
            ) {
                score -= 40
            }
            return score.coerceAtLeast(0)
        }
    }

    private suspend fun upgradeArtist(
        current: CanonicalArtistEntity,
        name: String,
        artworkUrl: String?,
        genre: String?
    ): CanonicalArtistEntity {
        val incomingQuality = metadataQualityArtist(name, artworkUrl, genre, null, null)
        if (incomingQuality < current.metadataQuality - 5) return current
        var changed = current
        val art = sanitizeArtwork(artworkUrl)
        if (changed.artworkUrl.isNullOrBlank() && art != null) {
            changed = changed.copy(artworkUrl = art)
        }
        if (changed.genresJson.isNullOrBlank() && !genre.isNullOrBlank()) {
            changed = changed.copy(genresJson = genre)
        }
        // Prefer cleaner display name without VEVO/Topic when current is dirty
        if (isDirtyArtistName(changed.canonicalName) && !isDirtyArtistName(name)) {
            changed = changed.copy(canonicalName = name, normalizedName = normalizeArtistName(name))
        }
        if (changed != current) {
            changed = changed.copy(
                metadataQuality = maxOf(changed.metadataQuality, incomingQuality),
                updatedAt = System.currentTimeMillis()
            )
            dao.updateArtist(changed)
        }
        return changed
    }

    private suspend fun upgradeAlbum(
        current: CanonicalAlbumEntity,
        title: String,
        artistId: Long?,
        artistName: String?,
        artworkUrl: String?,
        releaseYear: Int?,
        genre: String?,
        upc: String?,
        trackCount: Int?
    ): CanonicalAlbumEntity {
        val incomingQuality = metadataQualityAlbum(title, artistName, artworkUrl, releaseYear, upc, null, null)
        if (incomingQuality < current.metadataQuality - 5) return current
        var changed = current
        val art = sanitizeArtwork(artworkUrl)
        if (changed.artworkUrl.isNullOrBlank() && art != null) changed = changed.copy(artworkUrl = art)
        if (changed.canonicalArtistId == null && artistId != null) changed = changed.copy(canonicalArtistId = artistId)
        if (changed.albumArtistName.isNullOrBlank() && !artistName.isNullOrBlank()) changed = changed.copy(albumArtistName = artistName)
        if (changed.releaseYear == null && releaseYear != null) changed = changed.copy(releaseYear = releaseYear)
        if (changed.genre.isNullOrBlank() && !genre.isNullOrBlank()) changed = changed.copy(genre = genre)
        if (changed.upc.isNullOrBlank() && !upc.isNullOrBlank()) changed = changed.copy(upc = upc)
        if (changed.trackCount == null && trackCount != null) changed = changed.copy(trackCount = trackCount)
        if (changed != current) {
            changed = changed.copy(
                metadataQuality = maxOf(changed.metadataQuality, incomingQuality),
                updatedAt = System.currentTimeMillis()
            )
            dao.updateAlbum(changed)
        }
        return changed
    }

    private suspend fun upgradeTrack(
        current: CanonicalTrackEntity,
        input: TrackInput,
        artistId: Long?,
        albumId: Long?,
        cleanedTitle: String,
        cleanedArtist: String
    ): CanonicalTrackEntity {
        val incomingQuality = metadataQualityTrack(input, cleanedTitle, cleanedArtist)
        if (incomingQuality < current.metadataQuality - 5) return current
        var changed = current
        // Never downgrade clean title/artist with VEVO/Official Audio junk
        if (!isDirtyTrackTitle(cleanedTitle) && isDirtyTrackTitle(changed.title)) {
            changed = changed.copy(title = cleanedTitle, normalizedTitle = normalizeTrackTitle(cleanedTitle))
        }
        if (!isDirtyArtistName(cleanedArtist) && isDirtyArtistName(changed.artistDisplay)) {
            changed = changed.copy(artistDisplay = cleanedArtist)
        }
        if (changed.albumDisplay.isNullOrBlank() && !input.album.isNullOrBlank()) {
            changed = changed.copy(albumDisplay = input.album)
        }
        if (changed.isrc.isNullOrBlank() && !input.isrc.isNullOrBlank()) {
            changed = changed.copy(isrc = input.isrc.trim().uppercase())
        }
        val art = sanitizeArtwork(input.artworkUrl)
        if (changed.artworkUrl.isNullOrBlank() && art != null) changed = changed.copy(artworkUrl = art)
        if (changed.canonicalArtistId == null && artistId != null) changed = changed.copy(canonicalArtistId = artistId)
        if (changed.canonicalAlbumId == null && albumId != null) changed = changed.copy(canonicalAlbumId = albumId)
        if (changed.durationMs == null && input.durationMs != null) changed = changed.copy(durationMs = input.durationMs)
        if (changed.trackNumber == null && input.trackNumber != null) changed = changed.copy(trackNumber = input.trackNumber)
        if (changed.discNumber == null && input.discNumber != null) changed = changed.copy(discNumber = input.discNumber)
        if (changed.unifiedTrackId == null && input.unifiedTrackId != null) changed = changed.copy(unifiedTrackId = input.unifiedTrackId)
        if (changed.localSongId == null && input.localSongId != null) changed = changed.copy(localSongId = input.localSongId)
        if (changed != current) {
            changed = changed.copy(
                metadataQuality = maxOf(changed.metadataQuality, incomingQuality),
                updatedAt = System.currentTimeMillis()
            )
            dao.updateTrack(changed)
        }
        return changed
    }

    private fun isDirtyArtistName(name: String): Boolean =
        name.contains("VEVO", ignoreCase = true) ||
            name.contains("- Topic", ignoreCase = true) ||
            name.contains("Official", ignoreCase = true) && name.length > 40

    private fun isDirtyTrackTitle(title: String): Boolean =
        title.contains("Official Audio", ignoreCase = true) ||
            title.contains("Music Video", ignoreCase = true) ||
            title.contains("Lyric Video", ignoreCase = true) ||
            title.contains("Visualiser", ignoreCase = true) ||
            title.contains("Visualizer", ignoreCase = true)

    private fun diffArtist(before: CanonicalArtistEntity, after: CanonicalArtistEntity): List<String> = buildList {
        if (before.artworkUrl != after.artworkUrl) add("artworkUrl")
        if (before.genresJson != after.genresJson) add("genres")
        if (before.canonicalName != after.canonicalName) add("canonicalName")
    }

    private fun diffAlbum(before: CanonicalAlbumEntity, after: CanonicalAlbumEntity): List<String> = buildList {
        if (before.artworkUrl != after.artworkUrl) add("artworkUrl")
        if (before.canonicalArtistId != after.canonicalArtistId) add("canonicalArtistId")
        if (before.releaseYear != after.releaseYear) add("releaseYear")
        if (before.upc != after.upc) add("upc")
        if (before.albumArtistName != after.albumArtistName) add("albumArtistName")
    }

    private fun diffTrack(before: CanonicalTrackEntity, after: CanonicalTrackEntity): List<String> = buildList {
        if (before.artworkUrl != after.artworkUrl) add("artworkUrl")
        if (before.albumDisplay != after.albumDisplay) add("albumDisplay")
        if (before.isrc != after.isrc) add("isrc")
        if (before.canonicalArtistId != after.canonicalArtistId) add("canonicalArtistId")
        if (before.canonicalAlbumId != after.canonicalAlbumId) add("canonicalAlbumId")
        if (before.title != after.title) add("title")
        if (before.artistDisplay != after.artistDisplay) add("artistDisplay")
    }

    private fun logArtist(
        action: String,
        inputName: String,
        provider: String?,
        externalId: String?,
        canonicalId: Long?,
        method: MatchConfidence,
        confidence: Float
    ) {
        Log.i(
            "VANTA_ARTIST_GRAPH",
            "action=$action inputName='${inputName.take(80)}' provider='${provider.orEmpty()}' " +
                "externalArtistId='${externalId.orEmpty()}' matchedCanonicalArtistId=${canonicalId ?: -1} " +
                "matchMethod=$method confidence=$confidence"
        )
        Log.i(
            "VANTA_ARTIST_RESOLVE",
            "inputName='${inputName.take(80)}' provider='${provider.orEmpty()}' " +
                "externalArtistId='${externalId.orEmpty()}' matchedCanonicalArtistId=${canonicalId ?: -1} " +
                "matchMethod=$method confidence=$confidence"
        )
        Log.i(
            "VANTA_CANONICAL_RESOLVE",
            "entity=artist input='${inputName.take(80)}' provider='${provider.orEmpty()}' " +
                "externalId='${externalId.orEmpty()}' resultCanonicalId=${canonicalId ?: -1} " +
                "method='$method' confidence=$confidence"
        )
    }

    private fun logAlbum(
        action: String,
        title: String,
        provider: String?,
        externalId: String?,
        canonicalId: Long?,
        method: MatchConfidence
    ) {
        Log.i(
            "VANTA_ALBUM_GRAPH",
            "action=$action title='${title.take(80)}' provider='${provider.orEmpty()}' " +
                "externalAlbumId='${externalId.orEmpty()}' canonicalAlbumId=${canonicalId ?: -1} method=$method"
        )
        Log.i(
            "VANTA_CANONICAL_RESOLVE",
            "entity=album input='${title.take(80)}' provider='${provider.orEmpty()}' " +
                "externalId='${externalId.orEmpty()}' resultCanonicalId=${canonicalId ?: -1} method='$method'"
        )
    }

    private fun logTrack(
        action: String,
        title: String,
        artist: String,
        provider: String?,
        externalId: String?,
        canonicalId: Long?,
        method: MatchConfidence
    ) {
        Log.i(
            "VANTA_TRACK_GRAPH",
            "action=$action title='${title.take(80)}' artist='${artist.take(80)}' " +
                "provider='${provider.orEmpty()}' externalTrackId='${externalId.orEmpty()}' " +
                "canonicalTrackId=${canonicalId ?: -1} method=$method"
        )
        Log.i(
            "VANTA_CANONICAL_RESOLVE",
            "entity=track input='${title.take(60)}|${artist.take(60)}' provider='${provider.orEmpty()}' " +
                "externalId='${externalId.orEmpty()}' resultCanonicalId=${canonicalId ?: -1} method='$method'"
        )
    }
}
