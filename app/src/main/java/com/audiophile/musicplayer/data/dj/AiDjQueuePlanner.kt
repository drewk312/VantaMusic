package com.audiophile.musicplayer.data.dj

import android.util.Log
import com.audiophile.musicplayer.data.local.entities.UnifiedTrackWithSources
import com.audiophile.musicplayer.data.local.DeviceMediaMetadataReader
import com.audiophile.musicplayer.data.repository.LocalLibraryRepository
import com.audiophile.musicplayer.data.repository.TrackRepository
import com.audiophile.musicplayer.data.source.SourceRegistry
import com.audiophile.musicplayer.data.source.isPlayableMusicCandidate
import com.audiophile.musicplayer.data.lastfm.LastFmGenreResolver
import com.audiophile.musicplayer.data.dj.PulseMoment
import com.audiophile.musicplayer.data.dj.PulseMoment.Anchor
import com.audiophile.musicplayer.data.dj.PulseMoment.Position
import com.audiophile.musicplayer.data.dj.PulseMoment.Signal
import kotlin.random.Random

class AiDjQueuePlanner(
    private val trackRepository: TrackRepository,
    private val sourceRegistry: SourceRegistry,
    private val localLibraryRepository: LocalLibraryRepository,
    private val personaMemory: DjPersonaMemory? = null,
    private val stationMemory: StationTasteMemory? = null,
    private val chapterPlanner: PulseChapterPlanner = PulseChapterPlanner(),
    private val releaseYearResolver: TrackReleaseYearResolver? = null,
    private val deviceMediaMetadataReader: DeviceMediaMetadataReader? = null,
    private val lastFmGenreResolver: LastFmGenreResolver? = null
) {
    private val segmentSize = 7
    private val jukeboxRefillSize = 8
    private val jukeboxRefillThreshold = 5
    private var nextSegmentId = 1L
    private var nextPickId = 1L

    /** Broad tags that alone should not qualify a track for an era-locked station. */
    private val genericStationKeywords = setOf(
        "rock", "pop", "dance", "indie", "alternative", "electronic", "hip-hop", "hip hop", "rap",
        "golden", "classic", "hits", "oldies", "timeless", "best", "greatest", "ever"
    )

    fun jukeboxRefillThreshold(): Int = jukeboxRefillThreshold

    suspend fun planChapterSegment(
        profile: AiDjTasteProfile,
        chapterPlan: PulseChapterPlanner.ChapterPlan,
        feedbackHistory: List<AiDjFeedback>,
        excludeTrackIds: Set<Long> = emptySet(),
        rotationSalt: Long = System.currentTimeMillis(),
        stationId: String? = null
    ): AiDjSegment {
        val mode = AiDjMode.DAILY_DJ
        val keywords = chapterPlanner.clusterKeywords(chapterPlan.genreCluster)
        val bridgeKeywords = chapterPlan.bridgeFrom?.let { chapterPlanner.clusterKeywords(it) } ?: emptyList()

        val allTracks = playableCandidates(excludeTrackIds)
            .filter { RadioIdentityPolicy.acceptsTaste(profile, it.track.artist, it.track.genre) }
        if (allTracks.isEmpty()) return emptySegment(mode, "No playable tracks in your library.")

        val scored = allTracks.map { track ->
            var score = scoreTrackForMode(track, mode, profile, feedbackHistory, emptyList(), emptyList(), null)
            score += scoreGenreKeywords(track, keywords) * 3f
            if (bridgeKeywords.isNotEmpty()) {
                score += scoreGenreKeywords(track, bridgeKeywords) * 1.5f
            }
            score += stationMemory?.let { mem ->
                stationId?.let { id ->
                    mem.stationArtistAffinity(id, track.track.artist.orEmpty()) * 0.4f +
                        mem.stationTrackAffinity(id, track.track.trackId) * 0.3f
                } ?: 0f
            } ?: 0f
            track to score
        }

        val ordered = applySessionRotation(
            scored.sortedByDescending { it.second }.map { it.first },
            mode,
            profile,
            rotationSalt
        )
        val finalSorted = capArtists(ordered, chapterPlan.targetTracks)

        return buildSegment(mode, profile, finalSorted, chapterPlan.vibeDescription)
    }

    suspend fun planJukeboxSegment(
        station: JukeboxStation,
        profile: AiDjTasteProfile,
        feedbackHistory: List<AiDjFeedback>,
        excludeTrackIds: Set<Long> = emptySet(),
        rotationSalt: Long = System.currentTimeMillis(),
        maxTracks: Int = jukeboxRefillSize
    ): AiDjSegment {
        val mode = AiDjMode.VANTA_RADIO
        val allTracks = jukeboxPlayableCandidates(excludeTrackIds, station)
            .filter { RadioIdentityPolicy.acceptsStation(station, it.track.artist, it.track.genre) }
        if (allTracks.isEmpty()) return emptySegment(mode, "")

        releaseYearResolver?.ensureLoaded()
        val seedTracks = station.seedTrackIds.mapNotNull { trackRepository.getTrackWithSources(it) }
        val seedGenres = seedTracks.mapNotNull { it.track.genre?.trim()?.lowercase() }.filter { it.isNotBlank() }.distinct()
        val scoring = StationScoringContext(station, seedTracks, seedGenres)
        val eraLocked = isEraLockedStation(station)
        val genreFocused = isGenreFocusedStation(station)

        val yearFilterCache = if (eraLocked) buildYearFilterCache(allTracks) else emptyMap()
        val yearAnyCache = if (eraLocked) buildYearAnyYearCache(allTracks) else emptyMap()
        val genreCache = if (genreFocused) buildGenreCache(allTracks) else emptyMap()

        val (pool, poolMode) = buildJukeboxPool(
            station,
            allTracks,
            eraLocked,
            genreFocused,
            yearFilterCache,
            yearAnyCache,
            genreCache
        )
        if (pool.isEmpty()) {
            return emptySegment(
                mode,
                when {
                    eraLocked ->
                        "No tracks with ${station.name} release years in your library. Import albums with year metadata, or try a broader station."
                    genreFocused ->
                        "No ${station.name} tracks found — check that your library has genre tags for this style."
                    else ->
                        "No playable tracks for ${station.name}."
                }
            )
        }

        val tasteFree = eraLocked || genreFocused

        // In OPEN fallback mode, all tracks are eligible regardless of era/genre score.
        // Skip scoring to avoid decade penalties from filtering out non-era tracks.
        if (poolMode == JukeboxPoolMode.OPEN) {
            val ordered = applySessionRotation(pool, mode, profile, rotationSalt)
            val finalSorted = capArtists(ordered, maxTracks, allowWeakFallback = true)
            val prioritized = prioritizeArtistSeedTracks(finalSorted, station)
            if (prioritized.isEmpty()) {
                return emptySegment(mode, "Couldn't build a strong enough ${station.name} set from your library.")
            }
            return buildSegment(mode, profile, prioritized, station.name)
        }

        val scored = pool.map { track ->
            var score = if (tasteFree) 0f else {
                scoreTrackForMode(track, mode, profile, feedbackHistory, emptyList(), emptyList(), null)
            }
            score += scoreForStation(scoring, track, genreCache[track.track.trackId].orEmpty()) * 2.5f
            score += stationMemory?.let { mem ->
                mem.stationArtistAffinity(station.id, track.track.artist.orEmpty()) * 0.5f +
                    mem.stationTrackAffinity(station.id, track.track.trackId) * 0.35f
            } ?: 0f
            track to score
        }

        val minScore = when {
            eraLocked -> if (poolMode == JukeboxPoolMode.STRICT) 2.5f else 2f
            genreFocused -> if (poolMode == JukeboxPoolMode.STRICT) 2.5f else 1.5f
            else -> 0f
        }
        val ranked = scored.sortedByDescending { it.second }
        val candidateTracks = ranked.filter { it.second >= minScore }.map { it.first }

        val ordered = applySessionRotation(
            candidateTracks,
            mode,
            profile,
            rotationSalt
        )
        val finalSorted = capArtists(
            ordered,
            maxTracks,
            allowWeakFallback = !eraLocked && (poolMode == JukeboxPoolMode.SOFT || !tasteFree)
        )
        val prioritized = prioritizeArtistSeedTracks(finalSorted, station)
        if (prioritized.isEmpty()) {
            return emptySegment(mode, "Couldn't build a strong enough ${station.name} set from your library.")
        }
        return buildSegment(mode, profile, prioritized, station.name)
    }

    private fun prioritizeArtistSeedTracks(
        tracks: List<UnifiedTrackWithSources>,
        station: JukeboxStation
    ): List<UnifiedTrackWithSources> {
        if (tracks.isEmpty()) return tracks
        if (station.stationType != JukeboxStationType.ARTIST_SEED && station.seedArtists.isEmpty()) {
            return tracks
        }
        val seedKeys = station.seedArtists.map { it.trim().lowercase() }.filter { it.isNotBlank() }
        if (seedKeys.isEmpty()) return tracks
        return tracks.sortedByDescending { track ->
            val artist = track.track.artist.orEmpty().trim().lowercase()
            when {
                track.track.trackId in station.seedTrackIds -> 4
                seedKeys.any { key -> artist == key } -> 3
                seedKeys.any { key -> artist.contains(key) || key.contains(artist) } -> 2
                else -> 0
            }
        }
    }

    private enum class JukeboxPoolMode { STRICT, SOFT, OPEN }

    private suspend fun buildJukeboxPool(
        station: JukeboxStation,
        allTracks: List<UnifiedTrackWithSources>,
        eraLocked: Boolean,
        genreFocused: Boolean,
        yearFilterCache: Map<Long, Int?>,
        yearAnyCache: Map<Long, Int?>,
        genreCache: Map<Long, String>
    ): Pair<List<UnifiedTrackWithSources>, JukeboxPoolMode> {
        when {
            eraLocked -> {
                val start = station.decadeStart ?: 0
                val end = station.decadeEnd ?: 9999
                val hasGenreKeywords = station.genreKeywords.any { it.lowercase() !in genericStationKeywords }
                val strict = allTracks.filter { track ->
                    if (JukeboxTrackEligibility.shouldExcludeFromJukebox(
                            track,
                            genreCache[track.track.trackId].orEmpty(),
                            end
                        )
                    ) {
                        return@filter false
                    }
                    val year = yearFilterCache[track.track.trackId]
                    val yearOk = year != null && year in start..end
                    if (!yearOk) return@filter false
                    if (hasGenreKeywords) {
                        val genre = genreCache[track.track.trackId].orEmpty()
                        fitsGenreField(station, genre) ||
                            qualifiesViaGenreMetadata(station, genre) ||
                            station.seedArtists.any { seed ->
                                track.track.artist.trim().lowercase() == seed.trim().lowercase()
                            }
                    } else {
                        true
                    }
                }
                if (strict.isNotEmpty()) return strict to JukeboxPoolMode.STRICT

                val soft = allTracks.filter { track ->
                    fitsEraSoftPool(station, track, yearFilterCache, yearAnyCache, genreCache) ||
                        (hasGenreKeywords && (
                            fitsGenreField(station, genreCache[track.track.trackId].orEmpty()) ||
                            qualifiesViaGenreMetadata(station, genreCache[track.track.trackId].orEmpty())
                        ))
                }
                if (soft.isNotEmpty()) return soft to JukeboxPoolMode.SOFT
                Log.w("VANTA_JUKEBOX", "era_locked strict+soft empty for ${station.id}; no OPEN fallback")
                return emptyList<UnifiedTrackWithSources>() to JukeboxPoolMode.STRICT
            }
            genreFocused -> {
                val strict = allTracks.filter { track ->
                    fitsGenreField(station, genreCache[track.track.trackId].orEmpty()) &&
                        !JukeboxTrackEligibility.isTributeOrKaraokeArtifact(track)
                }
                if (strict.isNotEmpty()) return strict to JukeboxPoolMode.STRICT
                val soft = allTracks.filter { track ->
                    val genre = genreCache[track.track.trackId].orEmpty()
                    (fitsGenreField(station, genre) ||
                        qualifiesViaGenreMetadata(station, genre)) &&
                        !JukeboxTrackEligibility.isTributeOrKaraokeArtifact(track)
                }
                if (soft.isNotEmpty()) return soft to JukeboxPoolMode.SOFT
                Log.w("VANTA_JUKEBOX", "genre_focused strict+soft empty for ${station.id}; no OPEN fallback")
                return emptyList<UnifiedTrackWithSources>() to JukeboxPoolMode.STRICT
            }
            else -> return allTracks to JukeboxPoolMode.OPEN
        }
    }

    private suspend fun fitsEraSoftPool(
        station: JukeboxStation,
        track: UnifiedTrackWithSources,
        yearFilterCache: Map<Long, Int?>,
        yearAnyCache: Map<Long, Int?>,
        genreCache: Map<Long, String> = emptyMap()
    ): Boolean {
        val start = station.decadeStart ?: return false
        val end = station.decadeEnd ?: return false
        val filterYear = yearFilterCache[track.track.trackId]
        val anyYear = yearAnyCache[track.track.trackId]
        val haystack = eraMetadataHaystack(track)

        if (contradictsEraDecade(station, filterYear, anyYear, haystack)) return false
        if (JukeboxTrackEligibility.shouldExcludeFromJukebox(track, genreCache[track.track.trackId].orEmpty(), end)) {
            return false
        }

        val confirmedYear = filterYear ?: anyYear
        if (confirmedYear != null) return confirmedYear in start..end

        val artist = track.track.artist.trim().lowercase()
        val isKnownStationArtist = station.seedArtists.any { seed ->
            val key = seed.trim().lowercase()
            key.isNotBlank() && (artist == key || artist.contains(key) || key.contains(artist))
        }
        val hasSpecificStyleTag = station.genreKeywords
            .filterNot { it.lowercase() in genericStationKeywords }
            .any { keyword ->
                val genre = genreCache[track.track.trackId].orEmpty()
                genre.isNotBlank() && genre.contains(keyword.lowercase())
            }

        // Missing year metadata must not make a curated station unusable. Known
        // station artists and specific genre tags are safe soft evidence; broad
        // tags such as "rock", "pop", or "golden" in titles remain insufficient.
        return isKnownStationArtist || hasSpecificStyleTag || targetDecadeHintsInHaystack(station, haystack)
    }

    /** Genre evidence must come from tags/metadata — never title-only keyword hits. */
    private fun qualifiesViaGenreMetadata(station: JukeboxStation, genre: String): Boolean {
        if (genre.isBlank()) return false
        val specificKeywords = station.genreKeywords.filter { it.lowercase() !in genericStationKeywords }
        if (specificKeywords.any { keyword -> genre.contains(keyword.lowercase()) }) return true
        for ((tag, aliases) in LastFmGenreResolver.JUKEBOX_TAG_ALIASES) {
            if (!genre.contains(tag)) continue
            if (station.genreKeywords.any { keyword ->
                    val k = keyword.lowercase()
                    k == tag || aliases.any { alias -> k.contains(alias) || alias.contains(k) }
                }
            ) {
                return true
            }
        }
        return false
    }

    private fun eraMetadataHaystack(track: UnifiedTrackWithSources): String {
        val unified = track.track
        return "${unified.genre.orEmpty()} ${unified.title} ${unified.albumName.orEmpty()} ${unified.artist.orEmpty()}"
            .lowercase()
    }

    private fun targetDecadeHintsInHaystack(station: JukeboxStation, haystack: String): Boolean {
        val start = station.decadeStart ?: return false
        val end = station.decadeEnd ?: return false
        val hints = releaseYearResolver?.decadeTextHints(start, end).orEmpty()
        return hints.any { hint -> hint.length >= 3 && hint.lowercase() in haystack }
    }

    private fun contradictsEraDecade(
        station: JukeboxStation,
        filterYear: Int?,
        anyYear: Int?,
        haystack: String
    ): Boolean {
        val start = station.decadeStart ?: return false
        val end = station.decadeEnd ?: return false
        if (filterYear != null && filterYear !in start..end) return true
        if (anyYear != null && anyYear !in start..end) return true

        for (otherStart in 1950..2020 step 10) {
            val otherEnd = otherStart + 9
            if (otherEnd < start || otherStart > end) {
                val hints = releaseYearResolver?.decadeTextHints(otherStart, otherEnd).orEmpty()
                if (hints.any { hint -> hint.length >= 3 && hint.lowercase() in haystack }) {
                    return true
                }
            }
        }
        return false
    }

    private fun scoreGenreHaystack(
        station: JukeboxStation,
        track: UnifiedTrackWithSources,
        effectiveGenre: String,
        eraLocked: Boolean = false
    ): Float {
        val genre = effectiveGenre.ifBlank { track.track.genre?.lowercase().orEmpty() }
        if (eraLocked) {
            return if (qualifiesViaGenreMetadata(station, genre)) 1f else 0f
        }
        val haystack = eraMetadataHaystack(track).let { if (genre.isNotBlank()) "$genre $it" else it }
        var score = 0f
        for (keyword in station.genreKeywords) {
            val lower = keyword.lowercase()
            if (lower in genericStationKeywords) {
                if (genre.isNotBlank() && genre.contains(lower)) score += 0.5f
            } else if (genre.isNotBlank() && genre.contains(lower)) {
                score += 1f
            } else if (!eraLocked && lower in haystack) {
                score += 1f
            }
        }
        return score
    }

    private suspend fun buildYearFilterCache(tracks: List<UnifiedTrackWithSources>): Map<Long, Int?> {
        releaseYearResolver?.ensureLoaded()
        return tracks.associate { track ->
            track.track.trackId to releaseYearResolver?.resolveForEraFilter(track)
        }
    }

    private suspend fun buildYearAnyYearCache(tracks: List<UnifiedTrackWithSources>): Map<Long, Int?> {
        releaseYearResolver?.ensureLoaded()
        return tracks.associate { track ->
            track.track.trackId to releaseYearResolver?.resolve(track)
        }
    }

    private suspend fun buildGenreCache(tracks: List<UnifiedTrackWithSources>): Map<Long, String> {
        return tracks.associate { track ->
            track.track.trackId to effectiveGenreForTrack(track)
        }
    }

    private suspend fun effectiveGenreForTrack(track: UnifiedTrackWithSources): String {
        val direct = track.track.genre?.trim().orEmpty()
        if (direct.isNotBlank()) return direct.lowercase()

        val title = track.track.title
        val artist = track.track.artist
        val localSong = localLibraryRepository.findSongByTitleArtist(title, artist)
        if (localSong != null && localSong.genres.isNotEmpty()) {
            return localSong.genres.joinToString(" ").lowercase()
        }

        val mediaId = track.track.localLibraryId
        if (mediaId != null) {
            deviceMediaMetadataReader?.genreByMediaId(mediaId)?.let { return it.lowercase() }
        }

        val lastFmGenre = lastFmGenreResolver?.resolveGenreString(artist.orEmpty()).orEmpty()
        if (lastFmGenre.isNotBlank()) return lastFmGenre

        return ""
    }

    private fun fitsGenreField(station: JukeboxStation, genre: String): Boolean {
        if (genre.isBlank()) return false
        val parts = genre.split('/', ',', ';', '&', '|').map { it.trim().lowercase() }.filter { it.isNotBlank() }
        return station.genreKeywords.any { keyword ->
            val k = keyword.lowercase().trim()
            genre.contains(k) || parts.any { part -> part.contains(k) || k.contains(part) }
        }
    }

    private fun isGenreFocusedStation(station: JukeboxStation): Boolean {
        return station.stationType == JukeboxStationType.GENRE ||
            (station.genreKeywords.isNotEmpty() && station.decadeStart == null && station.decadeEnd == null)
    }

    private data class StationScoringContext(
        val station: JukeboxStation,
        val seedTracks: List<UnifiedTrackWithSources>,
        val seedGenres: List<String>
    )

    private suspend fun scoreForStation(
        ctx: StationScoringContext,
        track: UnifiedTrackWithSources,
        effectiveGenre: String = ""
    ): Float {
        val station = ctx.station
        var score = 0f
        val genre = effectiveGenre.ifBlank { track.track.genre?.lowercase().orEmpty() }
        val title = track.track.title.lowercase()
        val artist = track.track.artist.orEmpty().lowercase()
        val haystack = "$genre $title $artist"
        val eraLocked = isEraLockedStation(station)

        if (genre.isNotBlank()) {
            for (keyword in station.genreKeywords) {
                val lower = keyword.lowercase()
                val weight = if (lower in genericStationKeywords) 0.6f else 2f
                if (genre.contains(lower)) score += weight * 1.5f
                else if (!eraLocked && lower in haystack) score += weight * 0.4f
            }
        } else if (!eraLocked) {
            for (keyword in station.genreKeywords) {
                val lower = keyword.lowercase()
                if (lower in genericStationKeywords) continue
                val weight = 2f
                if (lower in haystack) score += weight
            }
        }

        score += scoreDecadeForStation(station, track, haystack)

        for (seedArtist in station.seedArtists) {
            val key = seedArtist.trim().lowercase()
            if (key.isBlank()) continue
            when {
                artist == key -> score += when (station.stationType) {
                    JukeboxStationType.ARTIST_SEED -> 5f
                    JukeboxStationType.MULTI_ARTIST -> 4f
                    JukeboxStationType.SONG_SEED -> 3f
                    else -> 3f
                }
                artist.contains(key) || key.contains(artist) -> score += 1.5f
            }
        }

        if (track.track.trackId in station.seedTrackIds) score += 5f

        when (station.stationType) {
            JukeboxStationType.SONG_SEED -> {
                val seedGenre = ctx.seedGenres.firstOrNull()
                if (seedGenre != null && genre.contains(seedGenre)) {
                    score += 3f
                }
                val seedArtist = station.seedArtists.firstOrNull()?.trim()?.lowercase()
                if (seedGenre != null && seedArtist != null && artist != seedArtist && genre.contains(seedGenre)) {
                    score += 2f
                }
                for (seed in ctx.seedTracks) {
                    val seedAlbum = seed.track.albumName?.lowercase().orEmpty()
                    val album = track.track.albumName?.lowercase().orEmpty()
                    if (seedAlbum.isNotBlank() && album.isNotBlank() && seedAlbum == album) score += 1.5f
                }
            }
            JukeboxStationType.ARTIST_SEED -> {
                if (ctx.seedGenres.any { genre.contains(it) }) score += 1.5f
            }
            JukeboxStationType.MULTI_ARTIST -> {
                if (station.seedArtists.isNotEmpty() && ctx.seedGenres.any { genre.contains(it) }) {
                    val isSeedArtist = station.seedArtists.any {
                        artist == it.trim().lowercase()
                    }
                    if (!isSeedArtist) score += 2f
                }
            }
            else -> Unit
        }

        return score
    }

    private suspend fun scoreDecadeForStation(
        station: JukeboxStation,
        track: UnifiedTrackWithSources,
        haystack: String
    ): Float {
        val start = station.decadeStart
        val end = station.decadeEnd
        if (start == null || end == null) return 0f

        val resolver = releaseYearResolver
        val year = resolver?.resolve(track)
        if (year != null) {
            return when {
                year in start..end -> 4f
                year < start -> -6f
                else -> -5f
            }
        }

        // Unknown year on an era station: small boost only from specific metadata hints, not silence.
        var textScore = 0f
        val hints = resolver?.decadeTextHints(start, end).orEmpty()
        for (hint in hints) {
            if (hint.lowercase() in haystack) textScore += 1.5f
        }
        return textScore.coerceAtMost(2.5f)
    }

    private fun isEraLockedStation(station: JukeboxStation): Boolean {
        if (station.decadeStart == null || station.decadeEnd == null) return false
        return when (station.stationType) {
            JukeboxStationType.PRESET,
            JukeboxStationType.ERA -> true
            JukeboxStationType.MULTI_ARTIST -> true
            else -> false
        }
    }

    private fun scoreGenreKeywords(track: UnifiedTrackWithSources, keywords: List<String>): Float {
        val genre = track.track.genre?.lowercase().orEmpty()
        val title = track.track.title.lowercase()
        val artist = track.track.artist.orEmpty().lowercase()
        val haystack = "$genre $title $artist"
        var score = 0f
        for (keyword in keywords) {
            if (keyword.lowercase() in haystack) score += 1f
        }
        return score
    }

    private suspend fun jukeboxPlayableCandidates(
        excludeTrackIds: Set<Long>,
        station: JukeboxStation
    ): List<UnifiedTrackWithSources> {
        val eraEnd = station.decadeEnd
        return playableCandidates(excludeTrackIds).filterNot { track ->
            JukeboxTrackEligibility.shouldExcludeFromJukebox(
                track,
                effectiveGenreForTrack(track),
                eraEnd
            )
        }
    }

    private suspend fun playableCandidates(excludeTrackIds: Set<Long>): List<UnifiedTrackWithSources> {
        val blockedTracks = personaMemory?.blockedTrackIds() ?: emptySet()
        val blockedArtists = personaMemory?.blockedArtists() ?: emptySet()
        return trackRepository.getAllTracks()
            .filter { it.isPlayableMusicCandidate() }
            .filterNot { JukeboxTrackEligibility.shouldExcludeFromRadioQueue(it) }
            .filterNot { it.track.trackId in excludeTrackIds }
            .filterNot { it.track.trackId in blockedTracks }
            .filterNot { it.track.artist.orEmpty().trim().lowercase() in blockedArtists }
            .distinctBy { (it.track.title.lowercase() to it.track.artist.lowercase()) }
    }

    private fun capArtists(
        ordered: List<UnifiedTrackWithSources>,
        maxTracks: Int,
        allowWeakFallback: Boolean = true
    ): List<UnifiedTrackWithSources> {
        val artistCounts = mutableMapOf<String, Int>()
        val sorted = ordered.filter { track ->
            val artistKey = track.track.artist.trim().lowercase()
            val count = artistCounts[artistKey] ?: 0
            if (count >= 2) false else {
                artistCounts[artistKey] = count + 1
                true
            }
        }.take(maxTracks.coerceAtLeast(1))
        return if (sorted.isEmpty() && ordered.isNotEmpty() && allowWeakFallback) {
            ordered.take(maxTracks.coerceAtLeast(1))
        } else {
            sorted
        }
    }

    private fun buildSegment(
        mode: AiDjMode,
        profile: AiDjTasteProfile,
        finalSorted: List<UnifiedTrackWithSources>,
        vibeLabel: String
    ): AiDjSegment {
        val picks = finalSorted.mapIndexed { index, track ->
            AiDjPick(
                track = track,
                reason = generateReason(track, index, finalSorted.size),
                confidence = 1f - (index.toFloat() / finalSorted.size.coerceAtLeast(1))
            )
        }
        return AiDjSegment(
            id = nextSegmentId++,
            title = "${mode.displayName} Set",
            vibeDescription = vibeLabel,
            tracks = finalSorted,
            picks = picks,
            narration = generateNarration(mode, profile, picks.size)
        )
    }

    private fun emptySegment(mode: AiDjMode, message: String): AiDjSegment =
        AiDjSegment(
            id = nextSegmentId++,
            title = "${mode.displayName} Set",
            vibeDescription = mode.description,
            tracks = emptyList(),
            picks = emptyList(),
            narration = message
        )

    suspend fun planSegment(
        mode: AiDjMode,
        profile: AiDjTasteProfile,
        feedbackHistory: List<AiDjFeedback>,
        seedTrack: UnifiedTrackWithSources? = null,
        excludeTrackIds: Set<Long> = emptySet(),
        rotationSalt: Long = System.currentTimeMillis(),
        maxTracks: Int = segmentSize
    ): AiDjSegment {
        val allTracks = trackRepository.getAllTracks()
        val playable = allTracks
            .filter { RadioIdentityPolicy.acceptsTaste(profile, it.track.artist, it.track.genre) }
            .filter { it.isPlayableMusicCandidate() }
            .filterNot { JukeboxTrackEligibility.shouldExcludeFromRadioQueue(it) }
            .filterNot { it.track.trackId in excludeTrackIds }
            .filterNot { personaMemory?.blockedTrackIds()?.contains(it.track.trackId) == true }
            .filterNot {
                personaMemory?.blockedArtists()?.contains(
                    it.track.artist.orEmpty().trim().lowercase()
                ) == true
            }
            .distinctBy { (it.track.title.lowercase() to it.track.artist.lowercase()) }

        if (playable.isEmpty()) {
            return AiDjSegment(
                id = nextSegmentId++,
                title = "${mode.displayName} Set",
                vibeDescription = mode.description,
                tracks = emptyList(),
                picks = emptyList(),
                narration = "No playable tracks found in your library."
            )
        }

        val moreLikeThisArtists = feedbackHistory
            .filterIsInstance<AiDjFeedback.MoreLikeThis>()
            .mapNotNull { trackRepository.getTrackWithSources(it.trackId)?.track?.artist }
        val lessLikeThisArtists = feedbackHistory
            .filterIsInstance<AiDjFeedback.LessLikeThis>()
            .mapNotNull { trackRepository.getTrackWithSources(it.trackId)?.track?.artist }

        val scored = playable.map { track ->
            val score = scoreTrackForMode(track, mode, profile, feedbackHistory, moreLikeThisArtists, lessLikeThisArtists, seedTrack)
            Log.d("VANTA_DJ", "scored ${track.track.title}=$score for mode=$mode")
            track to score
        }

        // A DJ set should move through a taste cluster, not repeat one artist's catalog.
        // Cap artists at two tracks and rotate the weekly discovery order deterministically.
        val ranked = scored.sortedByDescending { it.second }.map { it.first }
        val ordered = applySessionRotation(ranked, mode, profile, rotationSalt)
        val artistCounts = mutableMapOf<String, Int>()
        val sorted = ordered.filter { track ->
            val artistKey = track.track.artist.trim().lowercase()
            val count = artistCounts[artistKey] ?: 0
            if (count >= 2) false else {
                artistCounts[artistKey] = count + 1
                true
            }
        }.take(maxTracks.coerceAtLeast(1))

        val finalSorted = if (sorted.isEmpty() && ranked.isNotEmpty()) {
            ordered.take(maxTracks.coerceAtLeast(1))
        } else {
            sorted
        }

        val orderedTracks = if (seedTrack != null) {
            finalSorted.sortedByDescending { track ->
                val artist = track.track.artist.orEmpty().trim()
                val seedArtist = seedTrack.track.artist.orEmpty().trim()
                when {
                    artist.equals(seedArtist, ignoreCase = true) -> 2
                    else -> 0
                }
            }
        } else {
            finalSorted
        }

        val picks = orderedTracks.mapIndexed { index, track ->
            AiDjPick(
                track = track,
                reason = generateReason(track, index, orderedTracks.size),
                confidence = 1f - (index.toFloat() / orderedTracks.size.coerceAtLeast(1))
            )
        }

        val vibe = buildSegmentVibeDescription(orderedTracks, mode)

        return AiDjSegment(
            id = nextSegmentId++,
            title = "${mode.displayName} Set",
            vibeDescription = vibe,
            tracks = orderedTracks,
            picks = picks,
            narration = generateNarration(mode, profile, picks.size)
        )
    }

    private fun scoreTrackForMode(
        track: UnifiedTrackWithSources,
        mode: AiDjMode,
        profile: AiDjTasteProfile,
        feedbackHistory: List<AiDjFeedback>,
        moreLikeThisArtists: List<String>,
        lessLikeThisArtists: List<String>,
        seedTrack: UnifiedTrackWithSources?
    ): Float {
        var score = 0.5f

        val artist = track.track.artist
        val trackId = track.track.trackId

        // Boost favorite artists
        if (artist in profile.favoriteArtists) score += 2f
        if (artist in profile.topArtistsByPlayCount) score += 1.5f
        if (artist in profile.recentlyPlayedArtists) score += 0.5f

        // Mode-specific scoring
        when (mode) {
            AiDjMode.DAILY_DJ -> {
                if (artist in profile.favoriteArtists) score += 1f
                if (track.track.genre in profile.favoriteGenres) score += 0.5f
            }
            AiDjMode.LATE_NIGHT -> {
                val lateNightGenres = listOf("electronic", "ambient", "r&b", "synthwave", "dark", "chill")
                if (track.track.genre?.lowercase() in lateNightGenres) score += 2f
            }
            AiDjMode.WORKOUT -> {
                val workoutGenres = listOf("dance", "electronic", "hip-hop", "rock", "pop")
                if (track.track.genre?.lowercase() in workoutGenres) score += 2f
            }
            AiDjMode.DISCOVER_NEW -> {
                if (artist !in profile.favoriteArtists) score += 1.5f
                if (artist !in profile.recentlyPlayedArtists) score += 1f
            }
            AiDjMode.THROWBACKS -> {
                if (artist in profile.favoriteArtists) score += 2f
                if (artist in profile.topArtistsByPlayCount) score += 1f
            }
            AiDjMode.DEEP_CUTS -> {
                if (artist in profile.favoriteArtists) score += 1f
                if (artist !in profile.recentlyPlayedArtists) score += 1.5f
            }
            AiDjMode.OUTSIDE_COMFORT_ZONE -> {
                if (artist !in profile.favoriteArtists) score += 2f
                if (artist !in profile.recentlyPlayedArtists) score += 1f
                val genre = track.track.genre?.lowercase()
                if (genre != null && genre !in profile.favoriteGenres) score += 1f
            }
            AiDjMode.VANTA_RADIO -> {
                if (artist in profile.favoriteArtists) score += 1f
                score += Random.nextFloat() * 0.5f
            }
            AiDjMode.CHILL_VIBES -> {
                val chillGenres = listOf("ambient", "chill", "electronic", "r&b", "lofi", "jazz", "acoustic", "soul", "downtempo")
                if (track.track.genre?.lowercase() in chillGenres) score += 2.5f
                val negativeGenres = listOf("metal", "death", "hardcore", "punk", "dubstep")
                if (track.track.genre?.lowercase() in negativeGenres) score -= 3f
            }
            AiDjMode.SIMILAR_TO_SONG -> {
                if (seedTrack != null && artist == seedTrack.track.artist) score += 3f
                if (seedTrack != null && track.track.genre == seedTrack.track.genre) score += 1.5f
            }
            AiDjMode.SIMILAR_TO_ARTIST -> {
                if (seedTrack != null && artist == seedTrack.track.artist) score += 3f
            }
        }

        // Persona memory — long-term relationship signals
        personaMemory?.let { memory ->
            score += memory.artistAffinity(artist) * 0.35f
            score += memory.trackAffinity(trackId) * 0.25f
            if (artist.trim().lowercase() in memory.dislikedArtists()) score -= 2.5f
            if (artist.trim().lowercase() in memory.favoredArtists()) score += 1.2f
        }

        // Feedback-based adjustment
        for (feedback in feedbackHistory) {
            when (feedback) {
                is AiDjFeedback.Liked -> if (feedback.trackId == trackId) score += 2f
                is AiDjFeedback.Skipped -> if (feedback.trackId == trackId) score -= 3f
                is AiDjFeedback.MoreLikeThis -> {
                    if (artist in moreLikeThisArtists) score += 2f
                }
                is AiDjFeedback.LessLikeThis -> {
                    if (artist in lessLikeThisArtists) score -= 2f
                }
                is AiDjFeedback.Blocked -> if (feedback.trackId == trackId) score -= 10f
            }
        }

        // Prefer higher bitrate sources
        val maxBitrate = track.sources.maxOfOrNull { it.bitrate } ?: 0
        score += maxBitrate / 1000f * 0.1f

        // Small randomization for variety
        score += Random.nextFloat() * 0.3f

        return score.coerceAtLeast(0f)
    }

    private fun applySessionRotation(
        ranked: List<UnifiedTrackWithSources>,
        mode: AiDjMode,
        profile: AiDjTasteProfile,
        rotationSalt: Long
    ): List<UnifiedTrackWithSources> {
        if (ranked.isEmpty()) return ranked
        val calendar = java.util.Calendar.getInstance()
        val day = calendar.get(java.util.Calendar.DAY_OF_YEAR)
        val salt = profile.favoriteArtists.joinToString("|").hashCode() * 31L +
            mode.ordinal * 17L + day + rotationSalt + System.nanoTime()
        val offset = kotlin.math.abs(salt.toInt()) % ranked.size
        val rotated = ranked.drop(offset) + ranked.take(offset)
        // Weighted shuffle within the top pool so reopening Radio DJ does not replay the same opener.
        val poolSize = minOf(24, rotated.size)
        val pool = rotated.take(poolSize).toMutableList()
        val shuffled = mutableListOf<UnifiedTrackWithSources>()
        val random = Random(salt)
        while (pool.isNotEmpty() && shuffled.size < poolSize) {
            val weights = pool.map { random.nextFloat() + 0.2f }
            val total = weights.sum()
            var pick = random.nextFloat() * total
            var index = 0
            while (index < weights.lastIndex && pick > weights[index]) {
                pick -= weights[index]
                index++
            }
            shuffled.add(pool.removeAt(index))
        }
        return shuffled + rotated.drop(poolSize)
    }

    private fun generateReason(track: UnifiedTrackWithSources, index: Int, total: Int): String {
        val artist = track.track.artist
        val title = track.track.title
        val genre = track.track.genre?.lowercase()
        return when {
            index == 0 -> "Starting strong with \"$title\" by $artist."
            index == total - 1 -> "Closing out with \"$title\" by $artist."
            genre != null && genre.contains("rock") -> "$artist brings the rock energy with \"$title\"."
            genre != null && genre.contains("electronic") -> "$artist keeps the electronic vibe going with \"$title\"."
            genre != null && genre.contains("hip-hop") -> "$artist brings the heat with \"$title\"."
            genre != null && (genre.contains("r&b") || genre.contains("soul")) -> "$artist sets the mood with \"$title\"."
            genre != null && genre.contains("jazz") -> "$artist adds a smooth jazz feel with \"$title\"."
            genre != null && genre.contains("pop") -> "$artist keeps it catchy with \"$title\"."
            genre != null && genre.contains("country") -> "$artist brings the country flavor with \"$title\"."
            genre != null && genre.contains("ambient") -> "$artist creates atmosphere with \"$title\"."
            index < total / 3 -> "\"$title\" by $artist — setting the tone for this set."
            index < total * 2 / 3 -> "$artist maintains the flow with \"$title\"."
            else -> "\"$title\" by $artist — building toward the close."
        }
    }

    private fun buildSegmentVibeDescription(
        tracks: List<UnifiedTrackWithSources>,
        mode: AiDjMode
    ): String {
        if (tracks.isEmpty()) return "${mode.displayName} set"
        val genres = tracks.mapNotNull { it.track.genre?.lowercase() }.distinct()
        val artists = tracks.mapNotNull { it.track.artist }.distinct()
        val topGenre = genres.groupingBy { it }.eachCount().maxByOrNull { it.value }?.key
        val artistCount = artists.size
        return when {
            topGenre != null && artistCount > 4 -> "$topGenre-influenced mix with $artistCount artists"
            topGenre != null && artistCount > 1 -> "$topGenre-driven set featuring $artistCount artists"
            topGenre != null -> "Focused $topGenre journey"
            artistCount > 5 -> "Multi-artist collection with $artistCount artists"
            artistCount > 1 -> "Set spanning $artistCount artists"
            else -> {
                val singleArtist = artists.firstOrNull() ?: "unknown"
                "$singleArtist spotlight"
            }
        }
    }

    private fun generateNarration(mode: AiDjMode, profile: AiDjTasteProfile, count: Int): String {
        val artist = profile.favoriteArtists.firstOrNull()
        return when {
            artist != null && count > 3 -> "Kicking things off with $artist. Let's see where the music takes us."
            artist != null -> "A little $artist and a few songs to go with it. Turn it up."
            count > 3 -> "A few favorites and something fresh. This one's for you."
            else -> "Let's get into it. Here's your next mix."
        }
    }
}
