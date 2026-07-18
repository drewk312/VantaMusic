package com.audiophile.musicplayer.data.dj

import com.audiophile.musicplayer.data.repository.LocalLibraryRepository
import com.audiophile.musicplayer.data.repository.TrackRepository
import com.audiophile.musicplayer.data.source.SourceRegistry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AiDjSessionManager(
    private val trackRepository: TrackRepository,
    private val localLibraryRepository: LocalLibraryRepository,
    private val sourceRegistry: SourceRegistry,
    private val queuePlanner: AiDjQueuePlanner,
    private val narrationGenerator: AiDjNarrationGenerator,
    private val recommendationEngine: AiDjRecommendationEngine,
    private val customStationStore: CustomStationStore? = null
) {
    private val sessionLock = Any()
    private var currentSession: AiDjSession? = null
    private var nextSessionId = 1L
    private var nextDiscoverySegmentId = 1000L

    fun recordFavoriteInEngine(trackId: Long, artist: String, genre: String?) {
        recommendationEngine.recordFavorite(trackId, artist, genre)
    }

    fun recordSkipInEngine(trackId: Long, artist: String) {
        recommendationEngine.recordSkip(trackId, artist)
    }

    suspend fun fetchSimilarTracks(artist: String, genre: String? = null) =
        recommendationEngine.getSimilarTracks(artist, genre)

    suspend fun buildTasteProfile(): AiDjTasteProfile = withContext(Dispatchers.IO) {
        val allTracks = trackRepository.getAllTracks()
        val localSongs = localLibraryRepository.allSongsSnapshot()

        val favoriteArtists = localSongs
            .filter { it.isFavorite }
            .map { it.artist }
            .filter { it.isNotBlank() }
            .distinct()

        val allArtists = allTracks.map { it.track.artist }.filter { it.isNotBlank() }
        val artistPlayCounts = allArtists.groupBy { it }.mapValues { it.value.size }
        val topArtistsByPlayCount = artistPlayCounts.entries
            .sortedByDescending { it.value }
            .take(10)
            .map { it.key }

        val genres = allTracks.mapNotNull { it.track.genre }.filter { it.isNotBlank() }
        val genreCounts = genres.groupBy { it }.mapValues { it.value.size }
        val favoriteGenres = genreCounts.entries
            .sortedByDescending { it.value }
            .take(5)
            .map { it.key }

        val recentArtists = allTracks
            .sortedByDescending { it.track.trackId }
            .take(20)
            .map { it.track.artist }
            .filter { it.isNotBlank() }
            .distinct()
            .take(5)

        val hasImportedTracks = allTracks.any { it.track.localLibraryId == null }

        AiDjTasteProfile(
            favoriteArtists = favoriteArtists,
            favoriteGenres = favoriteGenres,
            recentlyPlayedArtists = recentArtists,
            topArtistsByPlayCount = topArtistsByPlayCount,
            genreCounts = genreCounts,
            totalTracks = allTracks.size,
            hasImportedTracks = hasImportedTracks
        )
    }

    suspend fun startPulseLiveSession(
        profile: AiDjTasteProfile,
        listener: DjListenerContext
    ): AiDjSession {
        val chapterPlanner = PulseChapterPlanner()
        val chapterPlan = chapterPlanner.planChapter(profile, previousCluster = null, likes = 0, skips = 0)
        val introNarration = narrationGenerator.generateModeIntro(
            listener.copy(mode = AiDjMode.DAILY_DJ)
        ).text
        val recentTrackIds = recommendationEngine.recentTrackIds()
        val newSessionId = nextSessionId++
        val segment = queuePlanner.planChapterSegment(
            profile = profile,
            chapterPlan = chapterPlan,
            feedbackHistory = emptyList(),
            excludeTrackIds = recentTrackIds,
            rotationSalt = newSessionId.toLong()
        )
        val segmentWithNarration = segmentWithIntroOrPlannerMessage(segment, introNarration, mode = AiDjMode.DAILY_DJ)
        val session = AiDjSession(
            id = newSessionId,
            mode = AiDjMode.DAILY_DJ,
            title = "Pulse Live — ${chapterPlan.vibeDescription}",
            currentSegmentIndex = 0,
            segments = listOf(segmentWithNarration),
            feedbackHistory = emptyList(),
            createdAt = System.currentTimeMillis(),
            listeningStyle = PulseListeningStyle.PULSE_LIVE,
            stationId = null,
            chapterGenre = chapterPlan.genreCluster,
            chapterTrackTarget = chapterPlan.targetTracks
        )
        synchronized(sessionLock) { currentSession = session }
        return session
    }

    suspend fun startJukeboxSession(
        station: JukeboxStation,
        profile: AiDjTasteProfile,
        listener: DjListenerContext,
        listeningStyle: PulseListeningStyle = PulseListeningStyle.ENDLESS_JUKEBOX
    ): AiDjSession {
        val recentTrackIds = recommendationEngine.recentTrackIds()
        val newSessionId = nextSessionId++
        var segment = queuePlanner.planJukeboxSegment(
            station = station,
            profile = profile,
            feedbackHistory = emptyList(),
            excludeTrackIds = recentTrackIds,
            rotationSalt = newSessionId.toLong()
        )
        if (segment.tracks.isEmpty()) {
            recommendationEngine.expandStation(station)
            segment = queuePlanner.planJukeboxSegment(
                station = station,
                profile = profile,
                feedbackHistory = emptyList(),
                excludeTrackIds = recentTrackIds,
                rotationSalt = newSessionId.toLong()
            )
        }
        if (segment.tracks.isEmpty()) {
            val broaderQuery = station.genreKeywords.filter { it.isNotBlank() }.take(2)
            if (broaderQuery.isNotEmpty()) {
                val broadStation = JukeboxStation(
                    id = station.id,
                    name = station.name,
                    description = station.description,
                    genreKeywords = broaderQuery,
                    decadeStart = null,
                    decadeEnd = null,
                    emoji = station.emoji,
                    stationType = station.stationType
                )
                recommendationEngine.expandStation(broadStation)
                segment = queuePlanner.planJukeboxSegment(
                    station = broadStation,
                    profile = profile,
                    feedbackHistory = emptyList(),
                    excludeTrackIds = recentTrackIds,
                    rotationSalt = newSessionId.toLong()
                )
            }
        }
        val segmentWithNarration = segmentWithIntroOrPlannerMessage(segment, introNarration = "")
        val session = AiDjSession(
            id = newSessionId,
            mode = AiDjMode.VANTA_RADIO,
            title = station.name,
            currentSegmentIndex = 0,
            segments = listOf(segmentWithNarration),
            feedbackHistory = emptyList(),
            createdAt = System.currentTimeMillis(),
            listeningStyle = listeningStyle,
            stationId = station.id
        )
        synchronized(sessionLock) { currentSession = session }
        return session
    }

    suspend fun startReleaseRadarSession(
        profile: AiDjTasteProfile,
        listener: DjListenerContext
    ): AiDjSession {
        val newSessionId = nextSessionId++
        val discovered = recommendationEngine.discoverReleaseRadar(profile)
        val tracks = if (discovered.isEmpty()) {
            val seedArtist = (profile.favoriteArtists + profile.topArtistsByPlayCount).firstOrNull()
            if (seedArtist != null) recommendationEngine.getSimilarTracks(seedArtist, profile.favoriteGenres.firstOrNull())
            else emptyList()
        } else discovered
        val segment = AiDjSegment(
            id = nextDiscoverySegmentId++,
            title = "Release Radar",
            vibeDescription = "New and recent tracks from your artists",
            tracks = tracks,
            picks = tracks.mapIndexed { i, t ->
                AiDjPick(track = t, reason = "Fresh from your rotation", confidence = 1f - i * 0.1f)
            },
            narration = "Fresh tracks from your artists and similar sounds."
        )
        val session = AiDjSession(
            id = newSessionId,
            mode = AiDjMode.DISCOVER_NEW,
            title = "Release Radar",
            currentSegmentIndex = 0,
            segments = listOf(segment),
            feedbackHistory = emptyList(),
            createdAt = System.currentTimeMillis(),
            listeningStyle = PulseListeningStyle.RELEASE_RADAR
        )
        synchronized(sessionLock) { currentSession = session }
        return session
    }

    suspend fun startMoodSession(
        moodQuery: String,
        profile: AiDjTasteProfile,
        listener: DjListenerContext
    ): AiDjSession {
        val newSessionId = nextSessionId++
        val tracks = recommendationEngine.discoverByMood(moodQuery)
        val segment = AiDjSegment(
            id = nextDiscoverySegmentId++,
            title = "\"$moodQuery\"",
            vibeDescription = moodQuery,
            tracks = tracks,
            picks = tracks.mapIndexed { i, t ->
                AiDjPick(track = t, reason = "Matches the mood", confidence = 1f - i * 0.1f)
            },
            narration = "Music for: $moodQuery"
        )
        val session = AiDjSession(
            id = newSessionId,
            mode = AiDjMode.DISCOVER_NEW,
            title = "Mood — $moodQuery",
            currentSegmentIndex = 0,
            segments = listOf(segment),
            feedbackHistory = emptyList(),
            createdAt = System.currentTimeMillis(),
            listeningStyle = PulseListeningStyle.MOOD
        )
        synchronized(sessionLock) { currentSession = session }
        return session
    }

    suspend fun startForgottenFavoritesSession(
        profile: AiDjTasteProfile,
        listener: DjListenerContext
    ): AiDjSession {
        val newSessionId = nextSessionId++
        val tracks = recommendationEngine.forgottenFavorites(profile)
        val segment = AiDjSegment(
            id = nextDiscoverySegmentId++,
            title = "Forgotten Favorites",
            vibeDescription = "Rediscover tracks you loved",
            tracks = tracks,
            picks = tracks.mapIndexed { i, t ->
                AiDjPick(track = t, reason = "One you might have missed", confidence = 1f - i * 0.1f)
            },
            narration = "Songs worth rediscovering."
        )
        val session = AiDjSession(
            id = newSessionId,
            mode = AiDjMode.DAILY_DJ,
            title = "Forgotten Favorites",
            currentSegmentIndex = 0,
            segments = listOf(segment),
            feedbackHistory = emptyList(),
            createdAt = System.currentTimeMillis(),
            listeningStyle = PulseListeningStyle.FORGOTTEN_FAVORITES
        )
        currentSession = session
        return session
    }

    suspend fun nextChapter(
        session: AiDjSession,
        profile: AiDjTasteProfile,
        listener: DjListenerContext,
        forceTransition: Boolean = false
    ): AiDjSession? {
        if (session.listeningStyle != PulseListeningStyle.PULSE_LIVE) return null
        val chapterPlanner = PulseChapterPlanner()
        val chapterPlan = chapterPlanner.planChapter(
            profile = profile,
            previousCluster = session.chapterGenre,
            likes = session.chapterLikes,
            skips = session.chapterSkips,
            forceTransition = forceTransition
        )
        val alreadyQueued = session.segments.flatMap { it.tracks }.map { it.track.trackId }.toSet()
        val segment = queuePlanner.planChapterSegment(
            profile = profile,
            chapterPlan = chapterPlan,
            feedbackHistory = session.feedbackHistory,
            excludeTrackIds = alreadyQueued,
            rotationSalt = System.currentTimeMillis(),
            stationId = session.stationId
        )
        if (segment.tracks.isEmpty()) return null
        val intro = narrationGenerator.generateSegmentIntro(
            segment = segment,
            segmentIndex = session.segments.size,
            listener = listener.copy(mode = session.mode)
        ).text
        val segmentWithNarration = segment.copy(narration = intro)
        val updated = session.copy(
            segments = session.segments + segmentWithNarration,
            currentSegmentIndex = session.segments.size,
            chapterGenre = chapterPlan.genreCluster,
            chapterLikes = 0,
            chapterSkips = 0,
            chapterTrackTarget = chapterPlan.targetTracks,
            chapterTracksPlayed = 0,
            title = "Pulse Live — ${chapterPlan.vibeDescription}"
        )
        currentSession = updated
        return updated
    }

    suspend fun getTrack(trackId: Long): com.audiophile.musicplayer.data.local.entities.UnifiedTrackWithSources? =
        trackRepository.getTrackWithSources(trackId)

    fun resolveStation(stationId: String): JukeboxStation? =
        JukeboxCatalog.resolveStation(stationId, customStationStore)

    suspend fun refillJukebox(
        session: AiDjSession,
        profile: AiDjTasteProfile
    ): AiDjSession? {
        val stationId = session.stationId ?: return null
        val station = resolveStation(stationId) ?: return null
        val alreadyQueued = session.segments.flatMap { it.tracks }.map { it.track.trackId }.toSet()
        val segment = queuePlanner.planJukeboxSegment(
            station = station,
            profile = profile,
            feedbackHistory = session.feedbackHistory,
            excludeTrackIds = alreadyQueued,
            rotationSalt = System.currentTimeMillis()
        )
        if (segment.tracks.isEmpty()) return null
        val segmentWithNarration = segment.copy(narration = "")
        val updated = session.copy(
            segments = session.segments + segmentWithNarration,
            currentSegmentIndex = session.segments.size
        )
        currentSession = updated
        return updated
    }

    suspend fun startSession(
        mode: AiDjMode,
        profile: AiDjTasteProfile,
        listener: DjListenerContext,
        seedTrack: com.audiophile.musicplayer.data.local.entities.UnifiedTrackWithSources? = null
    ): AiDjSession {
        // Discover Weekly expands the local candidate pool from the listener's strongest
        // taste seed before ranking. getSimilarTracks resolves playable sources and stores
        // them in the canonical repository, so the resulting set is immediately skippable.
        if (mode == AiDjMode.DISCOVER_NEW) {
            val llmTracks = recommendationEngine.discoverViaLlm(profile)
            if (llmTracks.isEmpty()) {
                val seedArtist = profile.favoriteArtists.firstOrNull()
                    ?: profile.topArtistsByPlayCount.firstOrNull()
                if (!seedArtist.isNullOrBlank()) {
                    recommendationEngine.getSimilarTracks(seedArtist, profile.favoriteGenres.firstOrNull())
                }
            }
        }
        val introNarration = narrationGenerator.generateModeIntro(listener.copy(mode = mode)).text
        val recentTrackIds = recommendationEngine.recentTrackIds()
        val newSessionId = nextSessionId++
        val segment = queuePlanner.planSegment(
            mode = mode,
            profile = profile,
            feedbackHistory = emptyList(),
            seedTrack = seedTrack,
            excludeTrackIds = recentTrackIds,
            rotationSalt = newSessionId.toLong()
        )

        val segmentWithNarration = segmentWithIntroOrPlannerMessage(segment, introNarration, mode = mode)

        val session = AiDjSession(
            id = newSessionId,
            mode = mode,
            title = "${mode.displayName} — ${segmentWithNarration.vibeDescription}",
            currentSegmentIndex = 0,
            segments = listOf(segmentWithNarration),
            feedbackHistory = emptyList(),
            createdAt = System.currentTimeMillis()
        )

        synchronized(sessionLock) { currentSession = session }
        return session
    }

    suspend fun nextSegment(session: AiDjSession, profile: AiDjTasteProfile, listener: DjListenerContext): AiDjSession? {
        if (session.listeningStyle == PulseListeningStyle.PULSE_LIVE) {
            return nextChapter(session, profile, listener)
        }
        if (session.listeningStyle == PulseListeningStyle.ENDLESS_JUKEBOX ||
            session.listeningStyle == PulseListeningStyle.ERA ||
            session.listeningStyle == PulseListeningStyle.GENRE
        ) {
            return refillJukebox(session, profile)
        }
        // Single-shot discovery sessions don't auto-refill; start a new session to re-discover.
        if (session.listeningStyle == PulseListeningStyle.RELEASE_RADAR ||
            session.listeningStyle == PulseListeningStyle.MOOD ||
            session.listeningStyle == PulseListeningStyle.FORGOTTEN_FAVORITES
        ) {
            return null
        }
        val alreadyQueued = session.segments
            .flatMap { it.tracks }
            .map { it.track.trackId }
            .toSet()
        val segment = queuePlanner.planSegment(
            mode = session.mode,
            profile = profile,
            feedbackHistory = session.feedbackHistory,
            excludeTrackIds = alreadyQueued,
            rotationSalt = System.currentTimeMillis()
        )
        if (segment.tracks.isEmpty()) return null

        val intro = narrationGenerator.generateSegmentIntro(
            segment = segment,
            segmentIndex = session.segments.size,
            listener = listener.copy(mode = session.mode)
        ).text
        val segmentWithNarration = segment.copy(narration = intro)

        val updated = session.copy(
            segments = session.segments + segmentWithNarration,
            currentSegmentIndex = session.segments.size
        )
        synchronized(sessionLock) { currentSession = updated }
        return updated
    }

    suspend fun recordFeedback(
        session: AiDjSession,
        feedback: AiDjFeedback
    ): AiDjSession {
        val updated = session.copy(
            feedbackHistory = session.feedbackHistory + feedback
        )
        synchronized(sessionLock) { currentSession = updated }
        return updated
    }

    fun getCurrentSession(): AiDjSession? = synchronized(sessionLock) { currentSession }

    private fun segmentWithIntroOrPlannerMessage(segment: AiDjSegment, introNarration: String, mode: AiDjMode? = null): AiDjSegment {
        return when {
            segment.tracks.isNotEmpty() -> segment.copy(narration = introNarration)
            segment.narration.isNotBlank() -> segment
            else -> segment.copy(narration = narrationGenerator.generateEmptyLibraryMessage(mode))
        }
    }
}

