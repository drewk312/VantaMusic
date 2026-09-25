package com.audiophile.musicplayer.radio.genome

import android.util.Log
import com.audiophile.musicplayer.data.local.entities.UnifiedTrackWithSources
import com.audiophile.musicplayer.data.source.SourceSearchResult
import kotlin.math.abs

/**
 * Core engine replicating Pandora's Music Genome Project station matching,
 * acoustic vector scoring, Pandora Station Modes, rotation rules, and thumbs steering.
 */
class MusicGenomeEngine(
    val seedTrackTitle: String,
    val seedTrackArtist: String,
    seedGenre: String? = null,
    seedReleaseYear: Int? = null,
    initialMode: PandoraStationMode = PandoraStationMode.BALANCED
) {
    private val TAG = "VANTA_GENOME_ENGINE"

    val seedGenome: MusicGenomeVector = MusicGenomeExtractor.extract(
        title = seedTrackTitle,
        artist = seedTrackArtist,
        genre = seedGenre,
        releaseYear = seedReleaseYear
    )

    var currentStationGenome: MusicGenomeVector = seedGenome
        private set

    var stationMode: PandoraStationMode = initialMode
        private set

    private val playedTitleArtists = mutableSetOf<String>()
    private val playedIsrcs = mutableSetOf<String>()
    private val recentArtists = mutableListOf<String>() // Rolling window for artist separation
    private val bannedTrackIds = mutableSetOf<String>()
    private val dislikedGenomes = mutableListOf<MusicGenomeVector>()

    val seedArtistNormalized: String = normalizeKey(seedTrackArtist)

    init {
        val initialKey = "${normalizeKey(seedTrackTitle)}|$seedArtistNormalized"
        playedTitleArtists.add(initialKey)
        recentArtists.add(seedArtistNormalized)
    }

    fun setMode(mode: PandoraStationMode) {
        stationMode = mode
        Log.d(TAG, "mode_changed to=${mode.displayName}")
    }

    /**
     * Thumbs Up: Pulls the station's dynamic genome vector towards this track's musical DNA.
     */
    fun onThumbsUp(track: UnifiedTrackWithSources) {
        val vector = MusicGenomeExtractor.extract(track)
        currentStationGenome = currentStationGenome.nudgeTowards(vector, weight = 0.28f)
        Log.d(TAG, "thumbs_up title='${track.track.title}' artist='${track.track.artist}'")
    }

    fun onThumbsUp(result: SourceSearchResult) {
        val vector = MusicGenomeExtractor.extract(result)
        currentStationGenome = currentStationGenome.nudgeTowards(vector, weight = 0.28f)
        Log.d(TAG, "thumbs_up title='${result.title}' artist='${result.artist}'")
    }

    /**
     * Thumbs Down: Pushes the station genome away from this track, bans the track from the
     * session, and registers the acoustic cluster as disliked.
     */
    fun onThumbsDown(track: UnifiedTrackWithSources) {
        val vector = MusicGenomeExtractor.extract(track)
        currentStationGenome = currentStationGenome.pushAway(vector, weight = 0.22f)
        dislikedGenomes.add(vector)
        val key = "${normalizeKey(track.track.title)}|${normalizeKey(track.track.artist)}"
        bannedTrackIds.add(key)
        Log.d(TAG, "thumbs_down title='${track.track.title}' artist='${track.track.artist}'")
    }

    fun onThumbsDown(result: SourceSearchResult) {
        val vector = MusicGenomeExtractor.extract(result)
        currentStationGenome = currentStationGenome.pushAway(vector, weight = 0.22f)
        dislikedGenomes.add(vector)
        val key = "${normalizeKey(result.title)}|${normalizeKey(result.artist)}"
        bannedTrackIds.add(key)
        Log.d(TAG, "thumbs_down title='${result.title}' artist='${result.artist}'")
    }

    /**
     * Scores a candidate track against the station's active genome vector and station mode.
     * Higher score = stronger match. Returns negative values for disqualified candidates.
     */
    fun scoreCandidate(
        candidateTitle: String,
        candidateArtist: String,
        candidateAlbum: String? = null,
        candidateGenre: String? = null,
        candidateIsrc: String? = null,
        candidateDurationMs: Long? = null,
        lastPlayedBpmNorm: Float? = null
    ): Double {
        val normTitle = normalizeKey(candidateTitle)
        val normArtist = normalizeKey(candidateArtist)
        val titleArtistKey = "$normTitle|$normArtist"

        // 1. Strict Bans & Title Deduplication
        if (titleArtistKey in bannedTrackIds) return -1000.0
        if (titleArtistKey in playedTitleArtists) return -1000.0
        if (!candidateIsrc.isNullOrBlank() && candidateIsrc in playedIsrcs) return -1000.0

        // 2. Extract candidate genome
        val candidateGenome = MusicGenomeExtractor.extract(
            title = candidateTitle,
            artist = candidateArtist,
            album = candidateAlbum,
            genre = candidateGenre,
            durationMs = candidateDurationMs
        )

        // 3. Pandora Disliked Cluster Penalty
        for (disliked in dislikedGenomes) {
            val dist = candidateGenome.distance(disliked)
            if (dist < 0.25f) {
                return -500.0 // Very close to a thumbed-down acoustic signature
            }
        }

        // 4. Base Musical Genome Cosine Similarity (0.0 to 100.0)
        val baseSimilarity = currentStationGenome.cosineSimilarity(candidateGenome)
        var score = (baseSimilarity * 50.0) + 50.0 // Normalize [-1, 1] to [0, 100]

        // 5. Apply Pandora Station Mode Weights
        when (stationMode) {
            PandoraStationMode.BALANCED -> {
                // Classic balanced mix
                if (normArtist == seedArtistNormalized) score += 15.0
            }
            PandoraStationMode.CROWD_FAVES -> {
                // Boost seed artist and high-affinity hits
                if (normArtist == seedArtistNormalized) score += 25.0
                score += (candidateGenome.danceability * 10.0)
            }
            PandoraStationMode.DISCOVERY -> {
                // Penalize seed artist to force new artist exploration
                if (normArtist == seedArtistNormalized) {
                    score -= 30.0
                } else {
                    score += 15.0 // Reward other artists sharing the genome
                }
            }
            PandoraStationMode.DEEP_CUTS -> {
                // Favor deeper tracks, slightly penalize top radio hits
                if (candidateTitle.contains("feat.") || candidateTitle.contains("remix")) {
                    score += 10.0
                }
            }
            PandoraStationMode.ARTIST_ONLY -> {
                if (normArtist != seedArtistNormalized) {
                    return -1000.0 // Reject non-seed artists completely
                }
                score += 50.0
            }
            PandoraStationMode.CHILL -> {
                score += (candidateGenome.acousticWeight * 20.0)
                score -= (candidateGenome.energyLevel * 25.0)
                if (candidateGenome.tempoBpmNorm < 0.45f) score += 15.0
            }
            PandoraStationMode.UPBEAT -> {
                score += (candidateGenome.energyLevel * 25.0)
                score += (candidateGenome.danceability * 20.0)
                if (candidateGenome.tempoBpmNorm > 0.48f) score += 15.0
            }
        }

        // 6. Tempo / Energy Continuity Smoothing
        if (lastPlayedBpmNorm != null) {
            val bpmDelta = abs(candidateGenome.tempoBpmNorm - lastPlayedBpmNorm)
            if (bpmDelta > 0.35f) {
                // Steep BPM jump (> 55 BPM jump) penalizes flow
                score -= (bpmDelta * 25.0)
            }
        }

        // 7. Artist Separation Rule (Pandora DMCA style: min 3 songs between same artist)
        if (recentArtists.takeLast(3).contains(normArtist)) {
            score -= 60.0 // Heavy penalty for repeating artist too soon
        }

        return score
    }

    /**
     * Enforces rotation rules when accepting a track into the station playback queue.
     */
    fun recordTrackPlayed(
        title: String,
        artist: String,
        isrc: String? = null
    ) {
        val normTitle = normalizeKey(title)
        val normArtist = normalizeKey(artist)
        playedTitleArtists.add("$normTitle|$normArtist")
        if (!isrc.isNullOrBlank()) {
            playedIsrcs.add(isrc)
        }
        recentArtists.add(normArtist)
        if (recentArtists.size > 20) {
            recentArtists.removeAt(0)
        }
    }

    /**
     * Filters and interleaves candidates according to Pandora rotation rules:
     * - Minimum 3 tracks separation between same artist.
     * - Max 25% seed artist representation.
     */
    fun applyPandoraRotation(
        candidates: List<UnifiedTrackWithSources>,
        maxTracks: Int = 30
    ): List<UnifiedTrackWithSources> {
        val result = mutableListOf<UnifiedTrackWithSources>()
        val artistHistory = mutableListOf<String>()
        val artistCounts = mutableMapOf<String, Int>()
        val seenKeys = mutableSetOf<String>()

        val seedArtistLower = seedArtistNormalized

        // Sort candidates by genome score descending
        val scored = candidates.map { track ->
            val s = scoreCandidate(
                candidateTitle = track.track.title,
                candidateArtist = track.track.artist,
                candidateAlbum = track.track.albumName,
                candidateGenre = track.track.genre,
                candidateIsrc = track.track.isrc,
                candidateDurationMs = track.track.durationMs
            )
            s to track
        }.filter { it.first > 0.0 }.sortedByDescending { it.first }

        val pool = scored.map { it.second }.toMutableList()

        while (pool.isNotEmpty() && result.size < maxTracks) {
            var selectedIndex = -1

            for (i in pool.indices) {
                val candidate = pool[i]
                val artKey = normalizeKey(candidate.track.artist)
                val songKey = "${normalizeKey(candidate.track.title)}|$artKey"

                if (songKey in seenKeys) continue

                // Check artist separation (min 3 tracks in recent history)
                val last3 = artistHistory.takeLast(3)
                if (last3.contains(artKey) && pool.size > 3) {
                    continue // Skip for now, pick another artist
                }

                // Check seed artist cap (max 25%)
                if (artKey == seedArtistLower && result.size >= 4) {
                    val seedCount = artistCounts[seedArtistLower] ?: 0
                    val currentShare = seedCount.toFloat() / result.size.toFloat()
                    if (currentShare > 0.25f && stationMode != PandoraStationMode.ARTIST_ONLY) {
                        continue // Defer seed artist
                    }
                }

                selectedIndex = i
                break
            }

            if (selectedIndex == -1) {
                // If separation constraint can't be met, take the best remaining non-duplicate
                selectedIndex = pool.indexOfFirst {
                    val k = "${normalizeKey(it.track.title)}|${normalizeKey(it.track.artist)}"
                    k !in seenKeys
                }
                if (selectedIndex == -1) break
            }

            val chosen = pool.removeAt(selectedIndex)
            val chosenArtKey = normalizeKey(chosen.track.artist)
            val chosenSongKey = "${normalizeKey(chosen.track.title)}|$chosenArtKey"

            seenKeys.add(chosenSongKey)
            result.add(chosen)
            artistHistory.add(chosenArtKey)
            artistCounts[chosenArtKey] = (artistCounts[chosenArtKey] ?: 0) + 1
        }

        return result
    }

    private fun normalizeKey(str: String): String {
        return str.lowercase().replace(Regex("[^a-z0-9]"), "")
    }
}
