package com.audiophile.musicplayer.data.dj

import android.util.Log
import com.audiophile.musicplayer.data.local.entities.UnifiedTrackWithSources
import com.audiophile.musicplayer.data.repository.TrackRepository
import com.audiophile.musicplayer.data.source.SourceRegistry
import com.audiophile.musicplayer.data.source.SourceIdentityGate
import com.audiophile.musicplayer.data.source.SearchItemStatus
import com.audiophile.musicplayer.data.source.canResolveStream
import com.audiophile.musicplayer.data.source.canEnterPlaybackFlow
import com.audiophile.musicplayer.data.source.isLikelyMusicTrack
import com.audiophile.musicplayer.data.source.isPlayableMusicCandidate
import com.audiophile.musicplayer.data.source.sourceValidityStatus
import com.audiophile.musicplayer.data.llm.PulseAiBrain
import com.audiophile.musicplayer.data.llm.getTrackSuggestions
import com.audiophile.musicplayer.playback.RecommendationEngine

class AiDjRecommendationEngine(
    private val sourceRegistry: SourceRegistry,
    private val trackRepository: TrackRepository,
    private val localLibraryRepository: com.audiophile.musicplayer.data.repository.LocalLibraryRepository,
    private val pulseAiBrain: PulseAiBrain,
    private val listeningHistory: com.audiophile.musicplayer.data.local.ListeningHistoryRepository? = null
) : RecommendationEngine {

    enum class TimeOfDay { MORNING, AFTERNOON, EVENING, NIGHT }

    sealed class PlaybackSignal {
        data class FullPlay(val trackId: Long, val artist: String, val genre: String?) : PlaybackSignal()
        data class Skip(val trackId: Long, val artist: String) : PlaybackSignal()
        data class Favorite(val trackId: Long, val artist: String, val genre: String?) : PlaybackSignal()
    }

    private val playbackHistory = mutableListOf<PlaybackSignal>()
    private val favoriteArtists = mutableSetOf<String>()
    private val skipCounts = mutableMapOf<String, Int>()
    private val genreAffinities = mutableMapOf<String, Float>()
    private val historyArtists = java.util.concurrent.atomic.AtomicReference<List<String>>(emptyList())
    private val historyHydrated = java.util.concurrent.atomic.AtomicBoolean(false)

    fun recordFullPlay(trackId: Long, artist: String, genre: String?) {
        playbackHistory.add(PlaybackSignal.FullPlay(trackId, artist, genre))
        genre?.let { genreAffinities[it] = (genreAffinities[it] ?: 0f) + 1f }
        Log.d("VANTA_RECOMMEND", "FullPlay recorded: $artist")
    }

    fun recordSkip(trackId: Long, artist: String) {
        playbackHistory.add(PlaybackSignal.Skip(trackId, artist))
        skipCounts[artist] = (skipCounts[artist] ?: 0) + 1
        Log.d("VANTA_RECOMMEND", "Skip recorded: $artist (count=${skipCounts[artist]})")
    }

    fun getTimeOfDay(): TimeOfDay {
        val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
        return when (hour) {
            in 5..11 -> TimeOfDay.MORNING
            in 12..17 -> TimeOfDay.AFTERNOON
            in 18..21 -> TimeOfDay.EVENING
            else -> TimeOfDay.NIGHT
        }
    }

    fun getTimeOfDayPlaylistName(): String {
        return when (getTimeOfDay()) {
            TimeOfDay.MORNING -> "Morning Rise"
            TimeOfDay.AFTERNOON -> "Afternoon Vibes"
            TimeOfDay.EVENING -> "Evening Wind Down"
            TimeOfDay.NIGHT -> "Late Night Explorations"
        }
    }

    fun recentTrackIds(limit: Int = 20): Set<Long> =
        playbackHistory
            .mapNotNull { signal ->
                when (signal) {
                    is PlaybackSignal.FullPlay -> signal.trackId
                    is PlaybackSignal.Skip -> signal.trackId
                    else -> null
                }
            }
            .takeLast(limit)
            .toSet()

    fun recordFavorite(trackId: Long, artist: String, genre: String?) {
        playbackHistory.add(PlaybackSignal.Favorite(trackId, artist, genre))
        favoriteArtists.add(artist)
        genre?.let { genreAffinities[it] = (genreAffinities[it] ?: 0f) + 2f }
        Log.d("VANTA_RECOMMEND", "Favorite recorded: $artist")
    }

    override suspend fun getSimilarTracks(
        seedArtist: String,
        seedGenre: String?
    ): List<UnifiedTrackWithSources> {
        val topArtists = computeTopArtists()
        val searchArtists = listOf(seedArtist).filter { it.isNotBlank() }
        val results = resolveSearchQueries(searchArtists.map { null to it })
        Log.d("VANTA_RECOMMEND", "getSimilarTracks seed=$seedArtist returned ${results.size} tracks (topArtists=$topArtists)")
        return results
    }

    suspend fun discoverViaLlm(profile: AiDjTasteProfile): List<UnifiedTrackWithSources> {
        val client = pulseAiBrain.configuredClient() ?: return emptyList()
        val tastePrompt = buildString {
            append("Suggest real, commercially released songs for music discovery. ")
            if (profile.favoriteArtists.isNotEmpty()) {
                append("Favorite artists: ${profile.favoriteArtists.take(5).joinToString(", ")}. ")
            }
            if (profile.favoriteGenres.isNotEmpty()) {
                append("Favorite genres: ${profile.favoriteGenres.take(3).joinToString(", ")}. ")
            }
            if (profile.recentlyPlayedArtists.isNotEmpty()) {
                append("Recently played: ${profile.recentlyPlayedArtists.take(3).joinToString(", ")}. ")
            }
            append("Suggest specific original songs they may not have heard — no live recordings, no radio streams, no covers.")
        }
        val suggestions = client.getTrackSuggestions(tastePrompt)
        if (suggestions.isEmpty()) {
            Log.d("VANTA_RECOMMEND", "discoverViaLlm: no LLM suggestions returned")
            return emptyList()
        }
        val queries = suggestions.map { (title, artist) -> title to artist }
        val results = resolveSearchQueries(queries)
            .filter { RadioIdentityPolicy.acceptsTaste(profile, it.track.artist, it.track.genre) }
        Log.d("VANTA_RECOMMEND", "discoverViaLlm returned ${results.size} tracks from ${suggestions.size} suggestions")
        return results
    }

    /** Expands an empty station from playable catalog results, then persists them. */
    suspend fun expandStation(station: JukeboxStation): List<UnifiedTrackWithSources> {
        val queries = if (station.seedArtists.isNotEmpty()) {
            station.seedArtists.take(8).map { null to it }
        } else {
            (listOf(station.name) + station.genreKeywords.filter { it.isNotBlank() }.take(3))
                .distinct()
                .map { it to null }
        }
        val tracks = resolveSearchQueries(queries)
        Log.d("VANTA_RADIO_EXPAND", "station=${station.id} queries=${queries.size} playable=${tracks.size}")
        return tracks
    }

    private suspend fun resolveSearchQueries(
        queries: List<Pair<String?, String?>>
    ): List<UnifiedTrackWithSources> {
        val seenTrackIds = mutableSetOf<Long>()
        val results = mutableListOf<UnifiedTrackWithSources>()

        for ((title, artist) in queries) {
            val query = listOfNotNull(title?.takeIf { it.isNotBlank() }, artist?.takeIf { it.isNotBlank() })
                .joinToString(" ")
                .trim()
            if (query.isBlank()) continue

            val searchResults = sourceRegistry.searchAll(query, includeSupplemental = false)
            val (matched, skipped) = searchResults
                .filter { it.status.canResolveStream() && RadioIdentityPolicy.matches(it.title, it.artist, title, artist) }
                .filter { !SourceIdentityGate.isSupplementalPlaybackProvider(it.providerId) }
                .partition { it.isLikelyMusicTrack() &&
                    !JukeboxTrackEligibility.shouldExcludeFromRadioQueue(it.title, it.artist, it.durationMs, it.album) }
            skipped.forEach {
                Log.d("VANTA_QUEUE_EXPAND", "filtered_non_music title='${it.title}' artist='${it.artist}' provider=${it.providerId}")
            }

            val triedProviders = mutableSetOf<String>()
            for (result in matched.take(5)) {
                if (!triedProviders.add(result.providerId)) {
                    Log.d("VANTA_SOURCE_RESOLVE", "provider_skip providerId='${result.providerId}' reason='previous_failed_for_query'")
                    continue
                }
                try {
                    val resolved = sourceRegistry.resolveStream(result.providerId, result.id) ?: continue
                    Log.d("VANTA_SOURCE_RESOLVE", "provider_success providerId='${result.providerId}' trackId='${result.id}'")
                    val trackId = trackRepository.addTrackSource(
                        title = result.title,
                        artist = result.artist,
                        album = result.album,
                        coverArtUrl = null,
                        sourceType = com.audiophile.musicplayer.data.local.entities.SourceType.ADDON,
                        streamUrl = resolved.streamUrl,
                        bitrate = resolved.bitrateKbps,
                        isrc = result.isrc,
                        durationMs = result.durationMs,
                        externalProviderId = result.providerId,
                        externalTrackId = result.id,
                        expiresAtMs = resolved.expiresAt
                    )
                    val track = trackRepository.getTrackWithSources(trackId)
                    if (track != null &&
                        DjLlmPlaybackGuard.canEnterPlaybackFromResolvedCandidate(track) &&
                        trackId !in seenTrackIds
                    ) {
                        seenTrackIds.add(trackId)
                        results.add(track)
                        if (results.size >= 40) break
                    } else if (track != null && !track.isPlayableMusicCandidate()) {
                        Log.d(
                            "VANTA_QUEUE_EXPAND",
                            "filtered_non_music title='${track.track.title}' artist='${track.track.artist}' reason=post_resolve_broadcast"
                        )
                    }
                } catch (e: android.database.sqlite.SQLiteConstraintException) {
                    Log.w("VANTA_QUEUE_EXPAND", "skipped_child_missing_parent seed='${result.title}' reason='${e.message}'")
                } catch (e: Exception) {
                    Log.w("VANTA_QUEUE_EXPAND", "candidate_skipped seed='${result.title}' reason='${e.message}'")
                }
            }
            if (results.size >= 40) break
        }

        return results
    }

    private suspend fun refreshListeningHistory() {
        val repository = listeningHistory ?: return
        historyArtists.set(repository.recommendationArtists())
        historyHydrated.set(true)
    }

    private fun computeTopArtists(): List<String> {
        return mergeTasteArtists(
            sessionScores = sessionArtistScores(),
            historyArtists = historyArtists.get(),
            favoriteArtists = favoriteArtists,
            skipCounts = skipCounts
        )
    }

    private fun sessionArtistScores(): Map<String, Int> {
        val artistScores = mutableMapOf<String, Int>()
        for (signal in playbackHistory) {
            when (signal) {
                is PlaybackSignal.FullPlay -> {
                    artistScores[signal.artist] = (artistScores[signal.artist] ?: 0) + 3
                }
                is PlaybackSignal.Favorite -> {
                    artistScores[signal.artist] = (artistScores[signal.artist] ?: 0) + 5
                }
                is PlaybackSignal.Skip -> {
                    artistScores[signal.artist] = (artistScores[signal.artist] ?: 0) - 2
                }
            }
        }
        return artistScores
    }

    data class TasteProfile(
        val topArtists: List<String>,
        val topGenres: List<String>,
        val totalPlays: Int,
        val totalSkips: Int,
        val favoriteArtists: List<String>,
        val timeOfDayPlaylist: String
    )

    suspend fun getTasteProfile(): TasteProfile {
        if (!historyHydrated.get()) refreshListeningHistory()
        val plays = playbackHistory.count { it is PlaybackSignal.FullPlay }
        val skips = playbackHistory.count { it is PlaybackSignal.Skip }
        return TasteProfile(
            topArtists = computeTopArtists(),
            topGenres = genreAffinities.entries.sortedByDescending { it.value }.take(5).map { it.key },
            totalPlays = plays,
            totalSkips = skips,
            favoriteArtists = favoriteArtists.toList(),
            timeOfDayPlaylist = getTimeOfDayPlaylistName()
        )
    }

    override fun streamingTasteSignals(): com.audiophile.musicplayer.radio.StreamingStationTasteSignals {
        val disliked = skipCounts.filter { it.value >= 3 }.keys.map { it.lowercase() }.toSet()
        return com.audiophile.musicplayer.radio.StreamingStationTasteSignals(
            favoriteArtists = favoriteArtists.map { it.lowercase() }.toSet(),
            skippedArtists = skipCounts.mapKeys { it.key.lowercase() },
            genreAffinities = genreAffinities,
            recentTrackIds = recentTrackIds(),
            dislikedArtists = disliked
        )
    }

    suspend fun getBecauseYouPlayed(): List<String> {
        val top = computeTopArtists().take(3)
        if (top.isEmpty()) return emptyList()
        return top.map { "Because you listened to $it" }
    }

    /** Release Radar: finds recent tracks from favorite artists and their similar artists. */
    suspend fun discoverReleaseRadar(profile: AiDjTasteProfile): List<UnifiedTrackWithSources> {
        val seedArtists = (profile.favoriteArtists + profile.topArtistsByPlayCount)
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
            .take(6)
        if (seedArtists.isEmpty()) return emptyList()

        val currentYear = java.util.Calendar.getInstance().get(java.util.Calendar.YEAR)
        val yearLabels = listOf(currentYear.toString(), (currentYear - 1).toString())
        val queries = mutableListOf<Pair<String?, String?>>()
        for (artist in seedArtists) {
            queries.add(null to artist)
            for (year in yearLabels) {
                queries.add("${artist} ${year}" to null)
            }
        }
        val tracks = resolveSearchQueries(queries)
        Log.d("VANTA_RELEASE_RADAR", "seed=$seedArtists found=${tracks.size}")
        return tracks
    }

    /**
     * Mood discovery: translates a natural-language mood description into songs.
     *
     * Strategy (in order):
     * 1. If an LLM is configured, ask it for specific song titles, then resolve them.
     * 2. If no LLM, or LLM returns nothing: score every local library track by how
     *    well its genre/title/artist matches keywords derived from the mood phrase.
     * 3. As a last resort, fall back to a shuffled sample from the whole library.
     */
    suspend fun discoverByMood(moodQuery: String): List<UnifiedTrackWithSources> {
        val client = pulseAiBrain.configuredClient()

        // --- 1. LLM path ---
        if (client != null) {
            val prompt = """
Suggest 5-8 specific, real, commercially released original songs that match this mood or description: "$moodQuery"
Only suggest well-known original studio recordings — no live versions, no radio streams, no covers, no karaoke.
Return each as: Song Title - Artist Name
""".trimIndent()
            val suggestions = runCatching { client.getTrackSuggestions(prompt) }.getOrNull()
            if (!suggestions.isNullOrEmpty()) {
                val queries = suggestions.map { (title, artist) -> title to artist }
                val tracks = resolveSearchQueries(queries)
                Log.d("VANTA_MOOD", "LLM path: query='$moodQuery' suggestions=${suggestions.size} resolved=${tracks.size}")
                if (tracks.isNotEmpty()) return tracks
            } else {
                Log.d("VANTA_MOOD", "LLM returned no suggestions; falling back to library mood scoring")
            }
        } else {
            Log.d("VANTA_MOOD", "No LLM configured; using library mood scoring for '$moodQuery'")
        }

        // --- 2. Library mood-scoring fallback ---
        return discoverByMoodFromLibrary(moodQuery)
    }

    /**
     * Score every track in the local library by how well its metadata (genre, title, artist)
     * matches keywords derived from the user's mood phrase. Treats the mood as a vibe description,
     * NOT as a song/artist name to search for literally.
     */
    private suspend fun discoverByMoodFromLibrary(moodQuery: String): List<UnifiedTrackWithSources> {
        val allTracks = trackRepository.getAllTracks().filter { it.isPlayableMusicCandidate() }
        if (allTracks.isEmpty()) return emptyList()

        // Expand the raw mood phrase into individual keyword tokens plus some synonyms
        val rawTokens = moodQuery.lowercase()
            .replace(Regex("[^a-z0-9 ]"), " ")
            .split(Regex("\\s+"))
            .filter { it.length >= 3 }
            .toSet()

        // Mood → genre/vibe keyword synonym expansion
        val moodSynonyms: Map<String, List<String>> = mapOf(
            "quiet"    to listOf("calm", "chill", "soft", "ambient", "acoustic", "slow", "mellow", "peaceful"),
            "loud"     to listOf("rock", "metal", "loud", "energy", "hype", "hard", "intense"),
            "calm"     to listOf("calm", "chill", "soft", "ambient", "lo-fi", "lofi", "mellow", "peaceful", "slow"),
            "chill"    to listOf("chill", "lo-fi", "lofi", "mellow", "calm", "relax", "ambient", "soft"),
            "sad"      to listOf("sad", "melancholy", "ballad", "heartbreak", "slow", "emotional", "blues", "acoustic"),
            "happy"    to listOf("happy", "upbeat", "pop", "dance", "fun", "bright", "feel good"),
            "energetic" to listOf("energetic", "upbeat", "dance", "edm", "house", "hype", "workout", "high energy"),
            "romantic" to listOf("romantic", "love", "ballad", "soul", "r&b", "smooth", "sensual"),
            "study"    to listOf("instrumental", "ambient", "focus", "lo-fi", "lofi", "classical", "piano", "soft"),
            "sleep"    to listOf("ambient", "calm", "sleep", "soft", "peaceful", "white noise", "nature"),
            "workout"  to listOf("workout", "gym", "high energy", "edm", "hip hop", "hype", "motivational", "intense"),
            "night"    to listOf("night", "dark", "late", "ambient", "deep", "chill", "r&b", "soul"),
            "morning"  to listOf("morning", "acoustic", "soft", "folk", "peaceful", "bright", "fresh"),
            "drive"    to listOf("rock", "pop", "road trip", "upbeat", "classic", "sing along", "fun"),
            "ride"     to listOf("rock", "pop", "road trip", "upbeat", "classic", "fun", "drive"),
            "focus"    to listOf("focus", "instrumental", "ambient", "lo-fi", "lofi", "classical", "concentration"),
            "party"    to listOf("party", "dance", "edm", "pop", "hip hop", "hype", "club"),
            "summer"   to listOf("summer", "pop", "beach", "upbeat", "fun", "tropical", "bright"),
            "winter"   to listOf("winter", "cozy", "acoustic", "soft", "folk", "christmas", "calm"),
            "rainy"    to listOf("rainy", "acoustic", "soft", "indie", "chill", "melancholy", "calm"),
            "jazz"     to listOf("jazz", "swing", "bebop", "blues", "soul", "smooth"),
            "hip"      to listOf("hip hop", "rap", "urban", "trap", "r&b"),
            "hop"      to listOf("hip hop", "rap", "urban", "trap"),
            "rap"      to listOf("rap", "hip hop", "trap", "urban", "flow"),
            "rock"     to listOf("rock", "alternative", "indie", "guitar", "band"),
            "pop"      to listOf("pop", "top 40", "mainstream", "catchy"),
            "soul"     to listOf("soul", "r&b", "funk", "motown", "groove"),
            "blues"    to listOf("blues", "soul", "jazz", "classic"),
            "country"  to listOf("country", "folk", "americana", "bluegrass", "southern")
        )

        // Build the final expanded keyword set
        val expandedKeywords = mutableSetOf<String>()
        expandedKeywords.addAll(rawTokens)
        for (token in rawTokens) {
            moodSynonyms[token]?.forEach { synonym ->
                expandedKeywords.addAll(synonym.split(" ").filter { it.length >= 2 })
            }
        }

        if (expandedKeywords.isEmpty()) {
            Log.d("VANTA_MOOD", "mood='$moodQuery' → no usable keywords; returning shuffled library")
            return allTracks.shuffled().take(20)
        }

        Log.d("VANTA_MOOD", "mood='$moodQuery' → keywords=$expandedKeywords")

        // Score each track by keyword matches against genre, title, artist
        val scored = allTracks.map { track ->
            val haystack = buildString {
                append(track.track.genre.orEmpty().lowercase()); append(" ")
                append(track.track.title.orEmpty().lowercase()); append(" ")
                append(track.track.artist.orEmpty().lowercase()); append(" ")
                append(track.track.albumName.orEmpty().lowercase())
            }
            val score = expandedKeywords.count { kw -> haystack.contains(kw) }.toFloat()
            track to score
        }

        val matching = scored.filter { it.second > 0f }.sortedByDescending { it.second }
        val result = if (matching.size >= 8) {
            // Good keyword hits — cap per-artist, take up to 25
            capArtistsList(matching.map { it.first }, 25)
        } else {
            // Sparse matches — blend matched tracks with a shuffled fill
            val matchedTracks = matching.map { it.first }
            val fillPool = allTracks.filter { t -> matchedTracks.none { m -> m.track.trackId == t.track.trackId } }.shuffled()
            (matchedTracks + fillPool).take(20)
        }

        Log.d("VANTA_MOOD", "mood='$moodQuery' library_scored=${scored.size} matched=${matching.size} final=${result.size}")
        return result
    }

    /** Discovers forgotten favorites: tracks the user has liked/played but not recently. */
    suspend fun forgottenFavorites(profile: AiDjTasteProfile): List<UnifiedTrackWithSources> {
        val allTracks = trackRepository.getAllTracks()
            .filter { it.isPlayableMusicCandidate() }
        if (allTracks.size <= 10) return allTracks

        val favoriteArtists = profile.favoriteArtists.map { it.trim().lowercase() }.toSet()
        val topArtists = profile.topArtistsByPlayCount.map { it.trim().lowercase() }.toSet()

        val scored = allTracks.map { track ->
            val artist = track.track.artist.trim().lowercase()
            val title = track.track.title.lowercase()
            var score = 0f
            if (artist in favoriteArtists) score += 3f
            else if (artist in topArtists) score += 1.5f
            if (profile.favoriteGenres.any { track.track.genre?.lowercase()?.contains(it) == true }) score += 1f
            score -= track.track.trackId * 0.00001f
            track to score
        }
        val shuffled = scored.sortedByDescending { it.second }
        val capped = capArtistsList(shuffled.map { it.first }, 20)
        Log.d("VANTA_FORGOTTEN", "candidates=${scored.size} final=${capped.size}")
        return capped
    }

    private fun capArtistsList(tracks: List<UnifiedTrackWithSources>, maxTracks: Int): List<UnifiedTrackWithSources> {
        val artistCounts = mutableMapOf<String, Int>()
        return tracks.filter { track ->
            val key = track.track.artist.trim().lowercase()
            val count = artistCounts[key] ?: 0
            if (count >= 2) false else {
                artistCounts[key] = count + 1
                true
            }
        }.take(maxTracks.coerceAtLeast(1))
    }
}

internal fun mergeTasteArtists(
    sessionScores: Map<String, Int>,
    historyArtists: List<String>,
    favoriteArtists: Collection<String>,
    skipCounts: Map<String, Int>
): List<String> {
    val artistScores = mutableMapOf<String, Int>()
    historyArtists.forEachIndexed { index, artist ->
        val name = artist.trim()
        if (name.isBlank()) return@forEachIndexed
        artistScores[name] = (artistScores[name] ?: 0) + (12 - index).coerceAtLeast(1)
    }
    for ((artist, score) in sessionScores) {
        val name = artist.trim()
        if (name.isBlank()) continue
        artistScores[name] = (artistScores[name] ?: 0) + score
    }
    for (artist in favoriteArtists) {
        val name = artist.trim()
        if (name.isBlank()) continue
        artistScores[name] = (artistScores[name] ?: 0) + 8
    }
    return artistScores
        .filter { (artist, _) -> (skipCounts[artist] ?: 0) < 3 }
        .entries
        .sortedByDescending { it.value }
        .take(10)
        .map { it.key }
}
