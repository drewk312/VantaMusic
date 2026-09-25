package com.audiophile.musicplayer.data.dj

import android.content.Context
import android.annotation.SuppressLint
import android.util.Log
import com.audiophile.musicplayer.debug.VantaDiagnosticLog
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import androidx.lifecycle.viewModelScope
import com.audiophile.musicplayer.data.llm.PulseAiBrain
import com.audiophile.musicplayer.playback.PlaybackStateHolder
import com.audiophile.musicplayer.playback.PlayerController
import com.audiophile.musicplayer.account.AccountManager
import com.audiophile.musicplayer.playback.AutoMixConfig
import com.audiophile.musicplayer.playback.AutoMixMode
import com.audiophile.musicplayer.playback.AutoMixPreferences
import com.audiophile.musicplayer.data.voice.PulseVoiceEngine
import com.audiophile.musicplayer.radio.LiveRadioDirectory
import com.audiophile.musicplayer.radio.LiveRadioTrackLibrary
import com.audiophile.musicplayer.radio.LiveRadioStation
import com.audiophile.musicplayer.data.local.entities.UnifiedTrackWithSources
import com.audiophile.musicplayer.playback.QueueMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import androidx.core.content.edit

data class AiDjUiState(
    val session: AiDjSessionUiState = AiDjSessionUiState(),
    val selectedMode: AiDjMode? = null,
    val availableModes: List<AiDjMode> = AiDjMode.entries,
    val statusMessage: String? = null,
    val liveCommentary: String? = null,
    val isPulseAiActive: Boolean = false,
    val commentaryFromPulseAi: Boolean = false,
    val isGeneratingCommentary: Boolean = false,
    val djVoiceEnabled: Boolean = false,
    val listenerDisplayName: String? = null,
    val customStations: List<JukeboxStation> = emptyList(),
    val liveRadioStation: LiveRadioStation? = null,
    val isLoadingLiveRadio: Boolean = false,
    val liveRadioPresetId: String? = null,
    val liveRadioTriedUrls: Set<String> = emptySet(),
    val liveRadioActionMessage: String? = null,
    val companionMode: DjCompanionMode = DjCompanionMode.CHILL_FRIEND,
    val isCompanionThinking: Boolean = false
)

@HiltViewModel
class AiDjViewModel @Inject constructor(
    @ApplicationContext context: Context,
    private val playerController: PlayerController,
    private val playbackStateHolder: PlaybackStateHolder,
    private val sessionManager: AiDjSessionManager,
    private val narrationGenerator: AiDjNarrationGenerator,
    private val queuePlanner: AiDjQueuePlanner,
    private val pulseAiBrain: PulseAiBrain,
    private val accountManager: AccountManager,
    private val pulseVoiceEngine: PulseVoiceEngine,
    private val liveRadioTrackLibrary: LiveRadioTrackLibrary
) : ViewModel() {
    @SuppressLint("StaticFieldLeak")
    private val context = context.applicationContext

    private val companionBrain = DjCompanionBrain(pulseAiBrain)

    private val djPrefs = context.getSharedPreferences("vanta_dj", Context.MODE_PRIVATE)

    private val _state = MutableStateFlow(AiDjUiState())
    val state: StateFlow<AiDjUiState> = _state.asStateFlow()

    val nowPlayingState = playbackStateHolder.state

    init {
        if (djPrefs.getBoolean(KEY_WELCOME_DISMISSED, false)) {
            _state.update { it.copy(liveCommentary = null) }
        }
    }

    private val spokenSegmentIndices = mutableSetOf<Int>()
    private var isAutoLoadingNextSegment = false
    private var liveRadioJob: Job? = null

    private val djVibes = listOf(
        AiDjMode.DAILY_DJ,
        AiDjMode.LATE_NIGHT,
        AiDjMode.CHILL_VIBES,
        AiDjMode.THROWBACKS,
        AiDjMode.DEEP_CUTS,
        AiDjMode.WORKOUT,
        AiDjMode.DISCOVER_NEW,
        AiDjMode.VANTA_RADIO
    )
    private var currentVibeIndex = 0

    private var lastCommentaryTrackId: Long? = null
    private val personaMemory = DjPersonaMemory(context)
    private val stationMemory = StationTasteMemory(context)
    private val customStationStore = CustomStationStore(context)
    private val chapterPlanner = PulseChapterPlanner()
    private val autoMixPreferences = AutoMixPreferences(context)
    private val liveRadioDirectory = LiveRadioDirectory()
    private var tracksSinceNarration = 0
    private val narrationCooldown = 2
    private val playCountsThisSession = mutableMapOf<Long, Int>()
    private var lastPlaybackSignal: PlaybackSignalSnapshot? = null

    // Look-ahead DJ voice spool. Scripts + synthesized audio are primed as soon as
    // a track joins the upcoming queue (minutes of buffer, not a 15s countdown) so
    // a transition never blocks on LLM + TTS. Keyed by trackId.
    private val scriptSpool = HashMap<Long, DjCommentary>()
    private val voiceSpool = HashMap<Long, String>()

    private data class PlaybackSignalSnapshot(
        val trackId: Long,
        val title: String,
        val artist: String,
        val versionLabel: String?,
        val positionMs: Long,
        val durationMs: Long,
        val sessionId: Long
    )

    init {
        val savedMode = runCatching {
            DjCompanionMode.valueOf(
                djPrefs.getString("dj_companion_mode", DjCompanionMode.CHILL_FRIEND.name) ?: DjCompanionMode.CHILL_FRIEND.name
            )
        }.getOrDefault(DjCompanionMode.CHILL_FRIEND)
        _state.update {
            it.copy(
                isPulseAiActive = pulseAiBrain.isConfigured(),
                djVoiceEnabled = pulseVoiceEngine.isPremiumConfigured(),
                companionMode = savedMode
            )
        }
        loadTasteProfile()
        refreshCustomStations()
        observeTrackTransitions()
    }

    private fun refreshCustomStations() {
        _state.update { it.copy(customStations = customStationStore.allStations()) }
    }

    fun suggestedStationArtists(): List<String> {
        val profile = _state.value.session.tasteProfile
        return (profile.favoriteArtists + profile.topArtistsByPlayCount + profile.recentlyPlayedArtists)
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
            .take(14)
    }

    fun stationLabel(stationId: String): String =
        JukeboxCatalog.stationLabel(stationId, customStationStore)

    private fun buildListenerContext(mode: AiDjMode): DjListenerContext {
        val profile = _state.value.session.tasteProfile
        val session = _state.value.session.currentSession
        val displayName = accountManager.profile.value.displayName.takeIf {
            accountManager.isSignedIn && it.isNotBlank()
        }
        return DjListenerContext(
            displayName = displayName,
            favoriteArtists = profile.favoriteArtists,
            favoriteGenres = profile.favoriteGenres,
            recentLikes = personaMemory.recentLikeLabels(),
            recentSkips = personaMemory.recentSkipLabels(),
            recentReplays = personaMemory.recentSignals()
                .filter { it.type == DjPersonaMemory.SignalType.REPLAY }
                .map { it.label() }
                .distinct()
                .take(4),
            sessionTrackCount = session?.segments?.sumOf { it.tracks.size } ?: 0,
            totalSessions = personaMemory.totalSessions(),
            mode = mode
        )
    }

    private fun ensureDjCrossfadeForSession() {
        val config = autoMixPreferences.load()
        if (config.mode == AutoMixMode.OFF) {
            autoMixPreferences.save(
                AutoMixConfig(mode = AutoMixMode.CROSSFADE, transitionSeconds = 5)
            )
        }
    }

    private fun applyCommentary(commentary: DjCommentary, speak: Boolean = false, audioPath: String? = null) {
        if (commentary.isSilent) {
            _state.update {
                it.copy(
                    liveCommentary = null,
                    commentaryFromPulseAi = false,
                    isGeneratingCommentary = false
                )
            }
            return
        }
        _state.update {
            it.copy(
                liveCommentary = commentary.text,
                commentaryFromPulseAi = commentary.fromPulseAi,
                isGeneratingCommentary = false
            )
        }
        if (speak && _state.value.djVoiceEnabled) {
            if (audioPath != null) {
                playerController.speakDjVoice(audioPath)
                return
            }
            viewModelScope.launch {
                val result = withContext(Dispatchers.IO) { pulseVoiceEngine.synthesize(commentary.text) }
                if (result != null) playerController.speakDjVoice(result.filePath)
            }
        }
    }

    /**
     * Look-ahead priming: as soon as a track enters the upcoming DJ queue its
     * commentary script is generated and synthesized (minutes of buffer, not a
     * 15-second countdown). If a primed beat isn't ready by transition time the
     * cold path still speaks/skips without ever blocking playback.
     */
    private fun primeVoiceSpool(tracks: List<UnifiedTrackWithSources>) {
        if (!_state.value.djVoiceEnabled || tracks.isEmpty()) return
        val session = _state.value.session.currentSession ?: return
        if (isStationListeningStyle(session.listeningStyle)) return
        val listener = buildListenerContext(session.mode)
        val segmentIndex = session.segments.size
        viewModelScope.launch {
            for (t in tracks.take(3)) {
                val tid = t.track.trackId
                if (tid in scriptSpool || tid in voiceSpool) continue
                val commentary = runCatching {
                    narrationGenerator.generateTrackCommentary(t, listener, segmentIndex)
                }.getOrDefault(DjCommentary.Silent)
                scriptSpool[tid] = commentary
                if (commentary.isSilent) continue
                val result = withContext(Dispatchers.IO) { pulseVoiceEngine.synthesize(commentary.text) }
                if (result != null) voiceSpool[tid] = result.filePath
            }
        }
    }

    private fun recordPlaybackSignalsForPreviousTrack() {
        val previous = lastPlaybackSignal ?: return
        val completed = previous.durationMs > 0L &&
            previous.positionMs >= (previous.durationMs * 0.78f)
        if (completed) {
            personaMemory.recordFullPlay(
                previous.trackId,
                previous.title,
                previous.artist,
                previous.versionLabel,
                previous.sessionId
            )
            sessionManager.recordFavoriteInEngine(
                previous.trackId,
                previous.artist,
                null
            )
        }
    }

    private fun recordSessionPlaySignals(track: UnifiedTrackWithSources, sessionId: Long) {
        val playback = track.toPlaybackDisplay()
        val trackId = track.track.trackId
        val playCount = (playCountsThisSession[trackId] ?: 0) + 1
        playCountsThisSession[trackId] = playCount
        if (playCount > 1) {
            personaMemory.recordReplay(
                trackId,
                track.track.title,
                track.track.artist.orEmpty(),
                playback.versionLabel,
                sessionId
            )
        }
        val snap = playbackStateHolder.snapshot()
        lastPlaybackSignal = PlaybackSignalSnapshot(
            trackId = trackId,
            title = track.track.title,
            artist = track.track.artist.orEmpty(),
            versionLabel = playback.versionLabel,
            positionMs = snap.positionMs,
            durationMs = snap.durationMs,
            sessionId = sessionId
        )
    }

    private fun loadTasteProfile() {
        viewModelScope.launch {
            val profile = sessionManager.buildTasteProfile()
            val displayName = accountManager.profile.value.displayName.takeIf {
                accountManager.isSignedIn && it.isNotBlank()
            }
            _state.update {
                it.copy(
                    session = it.session.copy(
                        tasteProfile = profile,
                        isLoading = false
                    ),
                    listenerDisplayName = displayName
                )
            }
        }
    }

    private fun observeTrackTransitions() {
        viewModelScope.launch {
            playerController.trackTransition.collect {
                handleTrackTransition()
            }
        }
    }

    private fun handleTrackTransition() {
        val session = _state.value.session.currentSession ?: return
        val currentTrackId = playbackStateHolder.snapshot().trackId?.toLongOrNull() ?: return
        if (lastCommentaryTrackId == currentTrackId) return

        val segmentIndex = session.segments.indexOfFirst { segment ->
            segment.tracks.any { it.track.trackId == currentTrackId }
        }
        if (segmentIndex < 0) return

        val segment = session.segments[segmentIndex]
        val currentTrack = segment.tracks.find { it.track.trackId == currentTrackId } ?: return
        lastCommentaryTrackId = currentTrackId
        recordPlaybackSignalsForPreviousTrack()
        recordSessionPlaySignals(currentTrack, session.id)

        viewModelScope.launch {
            val mode = session.mode
            val pulseActive = pulseAiBrain.isConfigured()
            _state.update { it.copy(isPulseAiActive = pulseActive) }

            val isSegmentOpening = segment.tracks.firstOrNull()?.track?.trackId == currentTrackId &&
                segmentIndex > 0
            val shouldNarrate = pulseActive && (isSegmentOpening || tracksSinceNarration >= narrationCooldown)

            if (pulseActive && shouldNarrate) {
                _state.update { it.copy(isGeneratingCommentary = true) }
                val listener = buildListenerContext(mode)
                val commentary = scriptSpool.remove(currentTrackId)
                    ?: narrationGenerator.generateTrackCommentary(
                        currentTrack,
                        listener,
                        segmentIndex
                    )
                _state.update { it.copy(isGeneratingCommentary = false) }
                if (!commentary.isSilent) {
                    tracksSinceNarration = 0
                    val primedAudio = voiceSpool.remove(currentTrackId)
                    applyCommentary(
                        commentary,
                        speak = !isStationListeningStyle(session.listeningStyle) && _state.value.djVoiceEnabled,
                        audioPath = primedAudio
                    )
                } else {
                    tracksSinceNarration++
                }
            } else if (pulseActive) {
                tracksSinceNarration++
            } else {
                if (!spokenSegmentIndices.contains(segmentIndex)) {
                    spokenSegmentIndices.add(segmentIndex)
                    val speak = !isStationListeningStyle(session.listeningStyle) && _state.value.djVoiceEnabled
                    applyCommentary(
                        DjCommentary(text = segment.narration, fromPulseAi = false),
                        speak = speak
                    )
                }
            }

            val lastSegment = session.segments.lastOrNull()
            val lastTrack = lastSegment?.tracks?.lastOrNull()
            if (lastTrack != null && lastTrack.track.trackId == currentTrackId) {
                autoLoadNextSegment()
            }

            updateChapterProgress(session, segmentIndex, currentTrackId)
            maybeRefillJukeboxQueue()
        }
    }

    private fun updateChapterProgress(session: AiDjSession, segmentIndex: Int, currentTrackId: Long) {
        if (session.listeningStyle != PulseListeningStyle.PULSE_LIVE) return
        val segment = session.segments.getOrNull(segmentIndex) ?: return
        val trackIndex = segment.tracks.indexOfFirst { it.track.trackId == currentTrackId }
        if (trackIndex < 0) return
        val updatedSession = session.copy(chapterTracksPlayed = trackIndex + 1)
        _state.update { state ->
            state.copy(session = state.session.copy(currentSession = updatedSession))
        }
        val decision = chapterPlanner.evaluateChapter(
            chapterLikes = updatedSession.chapterLikes,
            chapterSkips = updatedSession.chapterSkips,
            tracksPlayedInChapter = updatedSession.chapterTracksPlayed,
            targetTracks = updatedSession.chapterTrackTarget
        )
        if (decision.action == PulseChapterPlanner.ChapterDecision.Action.TRANSITION &&
            trackIndex >= updatedSession.chapterTrackTarget - 1
        ) {
            // Chapter naturally ending — next segment load handled by autoLoadNextSegment at last track
        }
    }

    private fun maybeRefillJukeboxQueue() {
        val session = _state.value.session.currentSession ?: return
        if (session.listeningStyle != PulseListeningStyle.ENDLESS_JUKEBOX &&
            session.listeningStyle != PulseListeningStyle.ERA &&
            session.listeningStyle != PulseListeningStyle.GENRE
        ) return
        val upcoming = playerController.upcomingDjQueue().size
        if (upcoming >= queuePlanner.jukeboxRefillThreshold()) return
        if (isAutoLoadingNextSegment) return
        viewModelScope.launch {
            val profile = _state.value.session.tasteProfile
            val updated = sessionManager.refillJukebox(session, profile)
            if (updated != null) {
                val newSegment = updated.segments.lastOrNull()
                _state.update { state ->
                    state.copy(
                        session = state.session.copy(
                            currentSession = updated,
                            currentSegment = newSegment ?: state.session.currentSegment
                        )
                    )
                }
                if (newSegment != null && newSegment.tracks.isNotEmpty()) {
                    playerController.appendDjTracks(newSegment.tracks)
                    primeVoiceSpool(newSegment.tracks)
                }
            }
        }
    }

    private fun autoLoadNextSegment() {
        if (isAutoLoadingNextSegment) return
        val session = _state.value.session.currentSession ?: return
        isAutoLoadingNextSegment = true
        viewModelScope.launch {
            try {
                Log.d("AiDjViewModel", "End of current set reached. Auto-planning next segment...")
                val profile = _state.value.session.tasteProfile
                val listener = buildListenerContext(session.mode)
                val updated = sessionManager.nextSegment(session, profile, listener)
                if (updated != null) {
                    val newSegment = updated.segments.lastOrNull()
                    _state.update { state ->
                        state.copy(
                            session = state.session.copy(
                                currentSession = updated,
                                currentSegment = newSegment
                            )
                        )
                    }
                    if (newSegment != null) {
                        Log.d("AiDjViewModel", "Adding ${newSegment.tracks.size} tracks to queue from new segment")
                        newSegment.tracks.forEach { track ->
                            playerController.addToOriginalQueue(track)
                        }
                        primeVoiceSpool(newSegment.tracks)
                    }
                }
            } catch (e: Exception) {
                Log.e("AiDjViewModel", "Failed to auto load next segment", e)
            } finally {
                isAutoLoadingNextSegment = false
            }
        }
    }

    fun startPulseLive() {
        if (_state.value.session.isLoading) return
        _state.update { it.copy(session = it.session.copy(isLoading = true), statusMessage = "Finding music for your Live DJ…") }
        viewModelScope.launch {
            try {
                _state.update { state ->
                    state.copy(session = state.session.copy(isLoading = true))
                }
                ensureDjCrossfadeForSession()
                personaMemory.incrementSession()
                val profile = _state.value.session.tasteProfile
                val listener = buildListenerContext(AiDjMode.DAILY_DJ)
                val session = kotlinx.coroutines.withTimeout(60_000) {
                    val freshProfile = sessionManager.buildTasteProfile()
                    sessionManager.startPulseLiveSession(freshProfile, listener)
                }
                beginSession(session, AiDjMode.DAILY_DJ, listener)
            } catch (e: Exception) {
                Log.e("AiDjViewModel", "startPulseLive failed", e)
                _state.update {
                    it.copy(
                        session = it.session.copy(isLoading = false),
                        statusMessage = "Couldn't start Live DJ. Try again."
                    )
                }
            }
        }
    }

    /** Legacy library-only jukebox. Prefer [com.audiophile.musicplayer.ui.MainViewModel.startStreamingStation] for streaming stations. */
    fun startJukeboxStation(stationId: String, style: PulseListeningStyle = PulseListeningStyle.ENDLESS_JUKEBOX) {
        val sessionState = _state.value.session
        if (sessionState.isStarted && sessionState.currentSession?.stationId == stationId) {
            if (!playbackStateHolder.snapshot().isPlaying) {
                playerController.togglePlayPause()
            }
            return
        }
        val station = sessionManager.resolveStation(stationId) ?: return
        startJukebox(station, style)
    }

    fun startLiveRadio(stationId: String) {
        tuneLiveRadio(stationId, emptySet())
    }

    fun skipLiveRadioStation() {
        val presetId = _state.value.liveRadioPresetId ?: return
        val currentUrl = _state.value.liveRadioStation?.streamUrl
        val tried = _state.value.liveRadioTriedUrls + setOfNotNull(currentUrl)
        tuneLiveRadio(presetId, tried)
    }

    fun skipLiveRadioAd() {
        playerController.skipLiveRadioAd()
    }

    fun addLiveRadioTrackToLibrary() {
        val np = playbackStateHolder.snapshot()
        val station = _state.value.liveRadioStation
        val title = np.title?.trim().orEmpty()
        val artist = np.artist?.trim().orEmpty()
        if (title.isBlank()) return
        if (station != null && title.equals(station.name, ignoreCase = true)) {
            _state.update {
                it.copy(liveRadioActionMessage = "Waiting for song info from the broadcast…")
            }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(liveRadioActionMessage = "Adding “$title” to your library…") }
            val added = liveRadioTrackLibrary.addIdentifiedTrack(title, artist)
            _state.update {
                it.copy(
                    liveRadioActionMessage = if (added != null) {
                        "Added “${added.track.title}” to your library."
                    } else {
                        "Couldn't find a playable copy of “$title” to save."
                    }
                )
            }
        }
    }

    private fun tuneLiveRadio(stationId: String, triedUrls: Set<String>) {
        val preset = JukeboxCatalog.findStation(stationId) ?: return
        liveRadioJob?.cancel()
        liveRadioJob = viewModelScope.launch {
            try {
                _state.update {
                    it.copy(
                        isLoadingLiveRadio = true,
                        liveRadioStation = null,
                        liveRadioPresetId = stationId,
                        liveRadioTriedUrls = triedUrls,
                        liveRadioActionMessage = null,
                        statusMessage = "Finding a live ${preset.name} broadcast…"
                    )
                }
                val station = liveRadioDirectory.findNextPlayable(preset.name, triedUrls)
                if (station == null) {
                    _state.update {
                        it.copy(
                            isLoadingLiveRadio = false,
                            statusMessage = "No more working ${preset.name} broadcasts right now. Try another station."
                        )
                    }
                    return@launch
                }
                playerController.clearDjSession()
                playerController.playDirectUrl(
                    url = station.streamUrl,
                    title = station.name,
                    artist = "Live ${preset.name} radio"
                )
                _state.update {
                    it.copy(
                        liveRadioStation = station,
                        isLoadingLiveRadio = false,
                        liveRadioTriedUrls = triedUrls + station.streamUrl,
                        statusMessage = null
                    )
                }
            } catch (e: Exception) {
                Log.e("VANTA_LIVE_RADIO", "tuneLiveRadio failed stationId=$stationId", e)
                VantaDiagnosticLog.error("LiveRadio", "tune_failed stationId=$stationId", e)
                _state.update {
                    it.copy(
                        isLoadingLiveRadio = false,
                        statusMessage = "Couldn't start live radio. Try again."
                    )
                }
            }
        }
    }

    fun clearLiveRadioActionMessage() {
        _state.update { it.copy(liveRadioActionMessage = null) }
    }

    fun closeLiveRadio() {
        liveRadioJob?.cancel()
        liveRadioJob = null
        playerController.stop()
        _state.update { it.copy(liveRadioStation = null, isLoadingLiveRadio = false, statusMessage = null) }
    }

    fun startArtistStation(artist: String) {
        if (artist.isBlank()) return
        val station = JukeboxCatalog.buildArtistStation(artist)
        customStationStore.save(station)
        refreshCustomStations()
        startJukebox(station, PulseListeningStyle.ENDLESS_JUKEBOX)
    }

    fun startSongStationFromNowPlaying() {
        val trackId = playbackStateHolder.snapshot().trackId?.toLongOrNull() ?: return
        viewModelScope.launch {
            val track = sessionManager.getTrack(trackId) ?: return@launch
            val station = JukeboxCatalog.buildSongStation(track)
            customStationStore.save(station)
            refreshCustomStations()
            startJukebox(station, PulseListeningStyle.ENDLESS_JUKEBOX)
        }
    }

    fun createCustomStation(
        name: String,
        artists: List<String>,
        eraId: String? = null,
        genreId: String? = null
    ) {
        if (artists.isEmpty()) return
        val station = JukeboxCatalog.buildMultiArtistStation(name, artists, eraId, genreId)
        customStationStore.save(station)
        stationMemory.saveStation(station.id)
        refreshCustomStations()
        startJukebox(station, PulseListeningStyle.ENDLESS_JUKEBOX)
    }

    private fun startJukebox(station: JukeboxStation, style: PulseListeningStyle) {
        viewModelScope.launch {
            liveRadioJob?.cancel()
            liveRadioJob = null
            _state.update {
                it.copy(
                    liveRadioStation = null,
                    isLoadingLiveRadio = false,
                    session = it.session.copy(isLoading = true)
                )
            }
            ensureDjCrossfadeForSession()
            personaMemory.incrementSession()
            val profile = _state.value.session.tasteProfile
            val listener = buildListenerContext(AiDjMode.VANTA_RADIO)
            val session = sessionManager.startJukeboxSession(station, profile, listener, style)
            beginSession(session, AiDjMode.VANTA_RADIO, listener)
        }
    }

    fun startReleaseRadar() {
        viewModelScope.launch {
            _state.update { state -> state.copy(session = state.session.copy(isLoading = true)) }
            ensureDjCrossfadeForSession()
            personaMemory.incrementSession()
            val profile = _state.value.session.tasteProfile
            val listener = buildListenerContext(AiDjMode.DISCOVER_NEW)
            val session = sessionManager.startReleaseRadarSession(profile, listener)
            beginSession(session, AiDjMode.DISCOVER_NEW, listener)
        }
    }

    fun startMoodSession(moodQuery: String) {
        processDjRequest(moodQuery)
    }

    fun processDjRequest(query: String) {
        val clean = query.trim()
        if (clean.isBlank()) return
        submitCompanionPrompt(clean)
        viewModelScope.launch {
            if (_state.value.companionMode.shouldShowMoment()) {
                postDjMoment("Finding some cozy vibes for you.")
            }
            _state.update { state -> state.copy(session = state.session.copy(isLoading = true)) }
            ensureDjCrossfadeForSession()
            personaMemory.incrementSession()
            val profile = _state.value.session.tasteProfile
            val listener = buildListenerContext(_state.value.companionMode.mappedDjMode)
            val session = sessionManager.startMoodSession(clean, profile, listener)
            val tracks = session.segments.flatMap { it.tracks }
            if (tracks.isEmpty()) {
                postDjMoment("I couldn't find anything matching that vibe. Let's try something else.")
                _state.update { state ->
                    state.copy(
                        session = state.session.copy(isLoading = false),
                        statusMessage = "No tracks matched \"$clean\"."
                    )
                }
                return@launch
            }
            beginSession(session, _state.value.companionMode.mappedDjMode, listener)
        }
    }

    fun submitCompanionPrompt(prompt: String) {
        val clean = prompt.trim()
        if (clean.isBlank()) return
        viewModelScope.launch {
            _state.update { it.copy(isCompanionThinking = true) }
            val listener = buildListenerContext(_state.value.companionMode.mappedDjMode)
            val nowPlaying = playbackStateHolder.snapshot()
            val upcoming = playerController.upcomingDjQueue()
            val queueSummary = if (upcoming.isEmpty()) {
                "empty"
            } else {
                "${upcoming.size} upcoming: " + upcoming.take(3).joinToString { "\"${it.track.title}\"" }
            }
            val response = companionBrain.respond(
                listener = listener,
                companionMode = _state.value.companionMode,
                nowPlaying = nowPlaying,
                userPrompt = clean,
                queueSummary = queueSummary
            )
            if (response.message.isNotBlank() && _state.value.companionMode.shouldShowMoment()) {
                postDjMoment(response.message, fromPulse = pulseAiBrain.isConfigured())
            }
            response.actions.forEach { executeCompanionAction(it) }
            _state.update { it.copy(isCompanionThinking = false) }
        }
    }

    fun selectCompanionMode(mode: DjCompanionMode) {
        djPrefs.edit {
                putString("dj_companion_mode", mode.name)
            }
        _state.update { it.copy(companionMode = mode) }
        if (mode.shouldShowMoment()) {
            val line = when (mode) {
                DjCompanionMode.SILENT -> return
                DjCompanionMode.LATE_NIGHT -> "Late-night mode. I'll keep this smooth and clean."
                DjCompanionMode.HYPE -> "Energy up. I'll keep momentum high."
                DjCompanionMode.COMFORT -> "Comfort mode. Familiar and calm."
                DjCompanionMode.FOCUS -> "Focus mode. Minimal chatter, steady flow."
                DjCompanionMode.DISCOVERY -> "Discovery mode. I'll explain why tracks fit."
                DjCompanionMode.CHILL_FRIEND -> "Chill friend mode. Warm and personal."
            }
            postDjMoment(line)
        }
    }

    private suspend fun executeCompanionAction(action: DjStructuredAction) {
        when (action.type) {
            DjActionType.SKIP -> playerController.next()
            DjActionType.PAUSE -> if (playbackStateHolder.snapshot().isPlaying) playerController.pause()
            DjActionType.RESUME -> if (!playbackStateHolder.snapshot().isPlaying) playerController.resume()
            DjActionType.REDUCE_TALKING -> selectCompanionMode(DjCompanionMode.SILENT)
            DjActionType.INCREASE_TALKING -> selectCompanionMode(DjCompanionMode.CHILL_FRIEND)
            DjActionType.CHANGE_MOOD -> when (action.direction?.lowercase()) {
                "hype", "workout", "gym" -> selectCompanionMode(DjCompanionMode.HYPE)
                "late_night", "late night" -> selectCompanionMode(DjCompanionMode.LATE_NIGHT)
                "comfort" -> selectCompanionMode(DjCompanionMode.COMFORT)
                "focus" -> selectCompanionMode(DjCompanionMode.FOCUS)
                else -> selectCompanionMode(DjCompanionMode.CHILL_FRIEND)
            }
            DjActionType.ADJUST_QUEUE, DjActionType.FIND_SIMILAR -> {
                val artist = playbackStateHolder.snapshot().artist
                if (!artist.isNullOrBlank()) {
                    val tracks = sessionManager.fetchSimilarTracks(artist, null)
                    if (tracks.isNotEmpty()) {
                        playerController.appendDjTracks(tracks.take(6))
                    }
                }
            }
            DjActionType.START_RADIO -> {
                val artist = action.artist ?: playbackStateHolder.snapshot().artist
                if (!artist.isNullOrBlank()) startArtistStation(artist)
            }
            DjActionType.EXPLAIN_CURRENT_SONG, DjActionType.NO_ACTION, DjActionType.UNKNOWN -> Unit
            else -> Unit
        }
    }

    private fun postDjMoment(text: String, fromPulse: Boolean = false) {
        if (text.isBlank()) return
        _state.update {
            it.copy(
                liveCommentary = text,
                commentaryFromPulseAi = fromPulse,
                isGeneratingCommentary = false
            )
        }
    }

    fun onDjScreenOpened() {
        if (djPrefs.getBoolean(KEY_WELCOME_DISMISSED, false)) return
        val name = _state.value.listenerDisplayName?.trim().orEmpty()
        val greeting = if (name.isNotBlank()) {
            "Welcome back, $name. What are we listening to tonight?"
        } else {
            "Welcome to VANTA. What are we listening to tonight?"
        }
        if (_state.value.liveCommentary == greeting) return
        postDjMoment(greeting)
        viewModelScope.launch {
            kotlinx.coroutines.delay(WELCOME_AUTO_DISMISS_MS)
            if (_state.value.liveCommentary == greeting) {
                _state.update { it.copy(liveCommentary = null) }
            }
        }
    }

    fun dismissDjMoment() {
        val current = _state.value.liveCommentary
        _state.update {
            it.copy(
                liveCommentary = null,
                commentaryFromPulseAi = false,
                isGeneratingCommentary = false
            )
        }
        if (current?.contains("welcome", ignoreCase = true) == true) {
            djPrefs.edit { putBoolean(KEY_WELCOME_DISMISSED, true) }
        }
    }

    companion object {
        private const val KEY_WELCOME_DISMISSED = "welcome_dismissed"
        private const val WELCOME_AUTO_DISMISS_MS = 12_000L
    }

    fun startForgottenFavorites() {
        viewModelScope.launch {
            _state.update { state -> state.copy(session = state.session.copy(isLoading = true)) }
            ensureDjCrossfadeForSession()
            personaMemory.incrementSession()
            val profile = _state.value.session.tasteProfile
            val listener = buildListenerContext(AiDjMode.DAILY_DJ)
            val session = sessionManager.startForgottenFavoritesSession(profile, listener)
            beginSession(session, AiDjMode.DAILY_DJ, listener)
        }
    }

    fun startMadeForYou() {
        startSession(AiDjMode.DAILY_DJ)
    }

    private suspend fun beginSession(
        session: AiDjSession,
        mode: AiDjMode,
        listener: DjListenerContext
    ) {
        spokenSegmentIndices.clear()
        tracksSinceNarration = 0
        playCountsThisSession.clear()
        lastPlaybackSignal = null
        _state.update { state ->
            state.copy(
                selectedMode = mode,
                listenerDisplayName = listener.displayName,
                session = state.session.copy(
                    isStarted = true,
                    currentSession = session,
                    currentSegment = session.segments.firstOrNull(),
                    isLoading = false,
                    availableModes = AiDjMode.entries
                ),
                statusMessage = session.title
            )
        }
        val firstSegment = session.segments.firstOrNull()
        playerController.clearDjSession()
        if (firstSegment != null && firstSegment.tracks.isNotEmpty()) {
            playerController.playQueue(firstSegment.tracks, 0, QueueMode.AI_DJ_QUEUE)
            spokenSegmentIndices.add(0)
            lastCommentaryTrackId = null
            if (!isStationListeningStyle(session.listeningStyle)) {
                if (pulseAiBrain.isConfigured()) {
                    _state.update { it.copy(isGeneratingCommentary = true) }
                    val commentary = narrationGenerator.generateSegmentIntro(
                        segment = firstSegment,
                        segmentIndex = 0,
                        listener = listener
                    )
                    _state.update { it.copy(isGeneratingCommentary = false) }
                    applyCommentary(commentary, speak = _state.value.djVoiceEnabled)
                } else {
                    applyCommentary(
                        DjCommentary(text = firstSegment.narration, fromPulseAi = false),
                        speak = _state.value.djVoiceEnabled
                    )
                }
            } else {
                applyCommentary(DjCommentary.Silent)
            }
        } else {
            val emptyMessage = firstSegment?.narration?.takeIf { it.isNotBlank() }
                ?: "This station couldn't find enough playable music. Try another station or check your playback provider."
            postDjMoment("I couldn't find anything matching that vibe. Let's try something else.")
            _state.update { state ->
                state.copy(
                    statusMessage = emptyMessage,
                    session = state.session.copy(
                        isStarted = false,
                        currentSession = null,
                        currentSegment = null,
                        isLoading = false
                    )
                )
            }
            applyCommentary(DjCommentary.Silent)
        }
    }

    fun stayHere() {
        val session = _state.value.session.currentSession ?: return
        viewModelScope.launch {
            val trackId = playbackStateHolder.snapshot().trackId?.toLongOrNull()
            val track = session.segments.flatMap { it.tracks }.find { it.track.trackId == trackId }
            if (track != null) {
                recordStationFeedback(session, track, positive = true)
            }
            val updated = session.copy(chapterLikes = session.chapterLikes + 1)
            _state.update { state ->
                state.copy(session = state.session.copy(currentSession = updated))
            }
            reshapeQueueAfterThumbsUp(track ?: session.segments.lastOrNull()?.tracks?.lastOrNull() ?: return@launch)
            val listener = buildListenerContext(session.mode)
            applyCommentary(
                narrationGenerator.generateFeedbackAcknowledgment(
                    positive = true,
                    listener = listener,
                    trackTitle = track?.track?.title.orEmpty(),
                    artist = track?.track?.artist.orEmpty()
                ),
                speak = _state.value.djVoiceEnabled
            )
        }
    }

    fun changeItUp() {
        val session = _state.value.session.currentSession ?: return
        viewModelScope.launch {
            _state.update { state ->
                state.copy(session = state.session.copy(isLoading = true))
            }
            val profile = _state.value.session.tasteProfile
            val listener = buildListenerContext(session.mode)
            val updated = when (session.listeningStyle) {
                PulseListeningStyle.PULSE_LIVE ->
                    sessionManager.nextChapter(session, profile, listener, forceTransition = true)
                else -> sessionManager.refillJukebox(session, profile)
            }
            if (updated != null) {
                val newSegment = updated.segments.lastOrNull()
                _state.update { state ->
                    state.copy(
                        session = state.session.copy(
                            currentSession = updated,
                            currentSegment = newSegment,
                            isLoading = false
                        )
                    )
                }
                if (newSegment != null && newSegment.tracks.isNotEmpty()) {
                    playerController.appendDjTracks(newSegment.tracks)
                    mergeTracksIntoCurrentSegment(newSegment.tracks)
                }
            } else {
                _state.update { state ->
                    state.copy(session = state.session.copy(isLoading = false))
                }
            }
            val commentarySegment = updated?.segments?.lastOrNull() ?: session.segments.lastOrNull()
            if (commentarySegment != null) {
                val listenerCtx = buildListenerContext(session.mode)
                applyCommentary(
                    narrationGenerator.generateSegmentIntro(
                        segment = commentarySegment,
                        segmentIndex = updated?.segments?.size ?: session.segments.size,
                        listener = listenerCtx
                    ),
                    speak = _state.value.djVoiceEnabled
                )
            }
        }
    }

    fun neverPlayCurrent() {
        val trackId = playbackStateHolder.snapshot().trackId?.toLongOrNull() ?: return
        val session = _state.value.session.currentSession ?: return
        val track = session.segments.flatMap { it.tracks }.find { it.track.trackId == trackId } ?: return
        personaMemory.blockTrack(trackId, track.track.artist.orEmpty())
        recordFeedback(AiDjFeedback.Blocked(trackId, track.track.artist.orEmpty()))
        viewModelScope.launch {
            playerController.removeUpcomingDjTracks { it.track.trackId == trackId }
            playerController.removeUpcomingDjTracks {
                it.track.artist.orEmpty().trim().lowercase() ==
                    track.track.artist.orEmpty().trim().lowercase()
            }
        }
        nextTrack()
    }

    fun saveStation() {
        val session = _state.value.session.currentSession ?: return
        val stationId = session.stationId ?: return
        stationMemory.saveStation(stationId)
        _state.update { it.copy(statusMessage = "Station saved") }
    }

    private fun recordStationFeedback(
        session: AiDjSession,
        track: UnifiedTrackWithSources,
        positive: Boolean
    ) {
        val stationId = session.stationId ?: return
        if (positive) {
            stationMemory.recordStationLike(stationId, track.track.trackId, track.track.artist.orEmpty())
        } else {
            stationMemory.recordStationSkip(stationId, track.track.trackId, track.track.artist.orEmpty())
        }
    }

    fun selectMode(mode: AiDjMode) {
        _state.update { it.copy(selectedMode = mode) }
    }

    fun startSession(mode: AiDjMode? = null) {
        val modeToUse = mode ?: _state.value.selectedMode ?: AiDjMode.DAILY_DJ
        currentVibeIndex = djVibes.indexOf(modeToUse).coerceAtLeast(0)
        viewModelScope.launch {
            _state.update { state ->
                state.copy(session = state.session.copy(isLoading = true))
            }
            ensureDjCrossfadeForSession()
            personaMemory.incrementSession()
            val profile = _state.value.session.tasteProfile
            val listener = buildListenerContext(modeToUse)
            val session = sessionManager.startSession(modeToUse, profile, listener)
            spokenSegmentIndices.clear()
            tracksSinceNarration = 0
            playCountsThisSession.clear()
            lastPlaybackSignal = null
            val firstTrack = session.segments.firstOrNull()?.tracks?.firstOrNull()
            // #region agent log
            com.audiophile.musicplayer.debug.DebugSessionLogger.log(
                hypothesisId = "H7",
                location = "AiDjViewModel.startSession",
                message = "session_started",
                data = mapOf(
                    "mode" to modeToUse.name,
                    "sessionId" to session.id,
                    "segmentCount" to session.segments.size,
                    "firstTrackId" to firstTrack?.track?.trackId,
                    "firstTrackTitle" to firstTrack?.track?.title,
                    "trackCountInSegment" to (session.segments.firstOrNull()?.tracks?.size ?: 0)
                ),
                runId = "post-fix"
            )
            // #endregion
            _state.update { state ->
                state.copy(
                    selectedMode = modeToUse,
                    listenerDisplayName = listener.displayName,
                    session = state.session.copy(
                        isStarted = true,
                        currentSession = session,
                        currentSegment = session.segments.firstOrNull(),
                        isLoading = false,
                        availableModes = AiDjMode.entries
                    ),
                    statusMessage = "New ${modeToUse.displayName} set ready"
                )
            }
            val firstSegment = session.segments.firstOrNull()
            if (firstSegment != null && firstSegment.tracks.isNotEmpty()) {
                playerController.playQueue(firstSegment.tracks, 0, QueueMode.AI_DJ_QUEUE)
                spokenSegmentIndices.add(0)
                lastCommentaryTrackId = null
                if (pulseAiBrain.isConfigured()) {
                    _state.update { it.copy(isGeneratingCommentary = true) }
                    val commentary = narrationGenerator.generateSegmentIntro(
                        segment = firstSegment,
                        segmentIndex = 0,
                        listener = listener
                    )
                    _state.update { it.copy(isGeneratingCommentary = false) }
                    applyCommentary(commentary, speak = _state.value.djVoiceEnabled)
                } else {
                    applyCommentary(
                        DjCommentary(text = firstSegment.narration, fromPulseAi = false),
                        speak = _state.value.djVoiceEnabled
                    )
                }
            }
        }
    }

    fun switchVibe() {
        val session = _state.value.session.currentSession ?: return
        viewModelScope.launch {
            _state.update { state ->
                state.copy(session = state.session.copy(isLoading = true))
            }
            
            // Cycle to the next vibe
            val nextVibeIndex = (currentVibeIndex + 1) % djVibes.size
            currentVibeIndex = nextVibeIndex
            val nextMode = djVibes[nextVibeIndex]
            
            Log.d("AiDjViewModel", "Switching vibe to: ${nextMode.displayName}")
            
            val profile = _state.value.session.tasteProfile
            val listener = buildListenerContext(nextMode)
            val nextSegment = queuePlanner.planSegment(
                mode = nextMode,
                profile = profile,
                feedbackHistory = session.feedbackHistory,
                excludeTrackIds = session.segments.flatMap { it.tracks }.map { it.track.trackId }.toSet()
            )
            
            val intro = narrationGenerator.generateSegmentIntro(
                segment = nextSegment,
                segmentIndex = session.segments.size,
                listener = listener
            )
            val segmentWithNarration = nextSegment.copy(narration = intro.text)
            
            val updated = session.copy(
                mode = nextMode,
                title = "${nextMode.displayName} — ${segmentWithNarration.vibeDescription}",
                segments = session.segments + segmentWithNarration,
                currentSegmentIndex = session.segments.size
            )
            
            _state.update { state ->
                state.copy(
                    selectedMode = nextMode,
                    session = state.session.copy(
                        currentSession = updated,
                        currentSegment = segmentWithNarration,
                        isLoading = false
                    )
                )
            }
            
            if (segmentWithNarration.tracks.isNotEmpty()) {
                playerController.playQueue(segmentWithNarration.tracks, 0, QueueMode.AI_DJ_QUEUE)
                spokenSegmentIndices.add(updated.segments.size - 1)
                lastCommentaryTrackId = null
                applyCommentary(intro, speak = _state.value.djVoiceEnabled)
            }
        }
    }

    fun nextSegment() {
        val session = _state.value.session.currentSession ?: return
        viewModelScope.launch {
            _state.update { state ->
                state.copy(session = state.session.copy(isLoading = true))
            }
            val profile = _state.value.session.tasteProfile
            val listener = buildListenerContext(session.mode)
            val updated = sessionManager.nextSegment(session, profile, listener)
            if (updated != null) {
                val newSegment = updated.segments.lastOrNull()
                val newIndex = updated.segments.size - 1
                _state.update { state ->
                    state.copy(
                        session = state.session.copy(
                            currentSession = updated,
                            currentSegment = newSegment,
                            isLoading = false
                        )
                    )
                }
                if (newSegment != null && newSegment.tracks.isNotEmpty()) {
                    playerController.playQueue(newSegment.tracks, 0, QueueMode.AI_DJ_QUEUE)
                    spokenSegmentIndices.add(newIndex)
                    lastCommentaryTrackId = null
                    _state.update { it.copy(isGeneratingCommentary = true) }
                    val commentary = narrationGenerator.generateSegmentIntro(
                        segment = newSegment,
                        segmentIndex = newIndex,
                        listener = listener
                    )
                    _state.update { it.copy(isGeneratingCommentary = false) }
                    applyCommentary(commentary, speak = _state.value.djVoiceEnabled)
                }
            } else {
                _state.update { state ->
                    state.copy(
                        session = state.session.copy(isLoading = false),
                        statusMessage = "No more tracks available for this mode."
                    )
                }
            }
        }
    }

    fun thumbsUpCurrentTrack() {
        val trackId = playbackStateHolder.snapshot().trackId?.toLongOrNull() ?: return
        val session = _state.value.session.currentSession ?: return
        val track = session.segments.flatMap { it.tracks }.find { it.track.trackId == trackId } ?: return
        val playback = track.toPlaybackDisplay()
        personaMemory.recordThumbsUp(
            trackId = trackId,
            title = track.track.title,
            artist = track.track.artist.orEmpty(),
            versionLabel = playback.versionLabel,
            sessionId = session.id
        )
        recordStationFeedback(session, track, positive = true)
        val updatedSession = session.copy(chapterLikes = session.chapterLikes + 1)
        _state.update { state ->
            state.copy(session = state.session.copy(currentSession = updatedSession))
        }
        recordFeedback(AiDjFeedback.Liked(trackId))
        recordFeedback(AiDjFeedback.MoreLikeThis(trackId))
        viewModelScope.launch {
            reshapeQueueAfterThumbsUp(track)
            if (!isStationListeningStyle(session.listeningStyle)) {
                val listener = buildListenerContext(session.mode)
                val ack = narrationGenerator.generateFeedbackAcknowledgment(
                    positive = true,
                    listener = listener,
                    trackTitle = track.track.title,
                    artist = track.track.artist.orEmpty()
                )
                applyCommentary(ack, speak = _state.value.djVoiceEnabled)
                tracksSinceNarration = 0
            }
        }
    }

    fun thumbsDownCurrentTrack() {
        val trackId = playbackStateHolder.snapshot().trackId?.toLongOrNull() ?: return
        val session = _state.value.session.currentSession ?: return
        val track = session.segments.flatMap { it.tracks }.find { it.track.trackId == trackId } ?: return
        val playback = track.toPlaybackDisplay()
        personaMemory.recordThumbsDown(
            trackId = trackId,
            title = track.track.title,
            artist = track.track.artist.orEmpty(),
            versionLabel = playback.versionLabel,
            sessionId = session.id
        )
        recordStationFeedback(session, track, positive = false)
        val updatedSession = session.copy(chapterSkips = session.chapterSkips + 1)
        _state.update { state ->
            state.copy(session = state.session.copy(currentSession = updatedSession))
        }
        recordFeedback(AiDjFeedback.Skipped(trackId))
        recordFeedback(AiDjFeedback.LessLikeThis(trackId))
        reshapeQueueAfterThumbsDown(track)
        viewModelScope.launch {
            if (!isStationListeningStyle(session.listeningStyle)) {
                val listener = buildListenerContext(session.mode)
                val ack = narrationGenerator.generateFeedbackAcknowledgment(
                    positive = false,
                    listener = listener,
                    trackTitle = track.track.title,
                    artist = track.track.artist.orEmpty()
                )
                applyCommentary(ack, speak = _state.value.djVoiceEnabled)
                tracksSinceNarration = 0
            }
            nextTrack()
        }
    }

    private suspend fun reshapeQueueAfterThumbsUp(seedTrack: UnifiedTrackWithSources) {
        val session = _state.value.session.currentSession ?: return
        val profile = _state.value.session.tasteProfile
        val exclude = session.segments.flatMap { it.tracks }.map { it.track.trackId }.toSet() +
            playerController.upcomingDjQueue().map { it.track.trackId }
        val segment = queuePlanner.planSegment(
            mode = session.mode,
            profile = profile,
            feedbackHistory = session.feedbackHistory,
            seedTrack = seedTrack,
            excludeTrackIds = exclude,
            rotationSalt = System.currentTimeMillis(),
            maxTracks = 3
        )
        if (segment.tracks.isEmpty()) return
        playerController.appendDjTracks(segment.tracks)
        mergeTracksIntoCurrentSegment(segment.tracks)
    }

    private fun reshapeQueueAfterThumbsDown(track: UnifiedTrackWithSources) {
        val artistKey = track.track.artist.orEmpty().trim().lowercase()
        viewModelScope.launch {
            playerController.removeUpcomingDjTracks {
                it.track.artist.orEmpty().trim().lowercase() == artistKey
            }
        }
        val session = _state.value.session.currentSession ?: return
        val currentSegment = session.segments.lastOrNull() ?: return
        val filteredTracks = currentSegment.tracks.filter {
            it.track.artist.orEmpty().trim().lowercase() != artistKey || it.track.trackId == track.track.trackId
        }
        val filteredPicks = currentSegment.picks.filter { pick ->
            pick.track.track.artist.orEmpty().trim().lowercase() != artistKey ||
                pick.track.track.trackId == track.track.trackId
        }
        if (filteredTracks.size == currentSegment.tracks.size) return
        val updatedSegment = currentSegment.copy(tracks = filteredTracks, picks = filteredPicks)
        val updatedSession = session.copy(
            segments = session.segments.dropLast(1) + updatedSegment
        )
        _state.update { state ->
            state.copy(
                session = state.session.copy(
                    currentSession = updatedSession,
                    currentSegment = updatedSegment
                )
            )
        }
    }

    private fun mergeTracksIntoCurrentSegment(newTracks: List<UnifiedTrackWithSources>) {
        val session = _state.value.session.currentSession ?: return
        val currentSegment = session.segments.lastOrNull() ?: return
        val existingIds = session.segments.flatMap { it.tracks }.map { it.track.trackId }.toSet()
        val toAdd = newTracks.filter { it.track.trackId !in existingIds }
        if (toAdd.isEmpty()) return
        val mergedTracks = currentSegment.tracks + toAdd
        val mergedPicks = mergedTracks.mapIndexed { index, t ->
            AiDjPick(
                track = t,
                reason = currentSegment.picks.find { it.track.track.trackId == t.track.trackId }?.moment?.humanReason()
                    ?: narrationGenerator.generatePickReason(t, index, mergedTracks.size),
                confidence = 1f - (index.toFloat() / mergedTracks.size.coerceAtLeast(1))
            )
        }
        val updatedSegment = currentSegment.copy(tracks = mergedTracks, picks = mergedPicks)
        val updatedSession = session.copy(
            segments = session.segments.dropLast(1) + updatedSegment
        )
        _state.update { state ->
            state.copy(
                session = state.session.copy(
                    currentSession = updatedSession,
                    currentSegment = updatedSegment
                )
            )
        }
    }

    fun recordFeedback(feedback: AiDjFeedback) {
        val session = _state.value.session.currentSession ?: return
        viewModelScope.launch {
            val updated = sessionManager.recordFeedback(session, feedback)
            _state.update { state ->
                state.copy(
                    session = state.session.copy(currentSession = updated)
                )
            }
            // Record in recommendation engine for permanent growth
            when (feedback) {
                is AiDjFeedback.Liked -> {
                    val track = session.segments.flatMap { it.tracks }.find { it.track.trackId == feedback.trackId }
                    if (track != null) {
                        sessionManager.recordFavoriteInEngine(track.track.trackId, track.track.artist.orEmpty(), track.track.genre)
                    }
                }
                is AiDjFeedback.Skipped -> {
                    val track = session.segments.flatMap { it.tracks }.find { it.track.trackId == feedback.trackId }
                    if (track != null) {
                        sessionManager.recordSkipInEngine(track.track.trackId, track.track.artist.orEmpty())
                    }
                }
                else -> {}
            }
        }
    }

    fun resetSession() {
        val hadActiveSession = _state.value.session.isStarted ||
            _state.value.session.currentSession != null
        if (hadActiveSession) {
            playerController.clearDjSession()
        }
        spokenSegmentIndices.clear()
        lastCommentaryTrackId = null
        tracksSinceNarration = 0
        playCountsThisSession.clear()
        lastPlaybackSignal = null
        scriptSpool.clear()
        voiceSpool.clear()
        _state.update {
            AiDjUiState(
                session = AiDjSessionUiState(
                    tasteProfile = it.session.tasteProfile
                ),
                isPulseAiActive = pulseAiBrain.isConfigured(),
                djVoiceEnabled = it.djVoiceEnabled,
                liveRadioStation = null,
                isLoadingLiveRadio = false
            )
        }
    }

    /** Returning to Radio resumes the active session; only an explicit exit resets it. */
    fun prepareFreshEntry() {
        refreshPulseAiStatus()
    }

    fun refreshMix() {
        val session = _state.value.session.currentSession
        if (session?.stationId != null) {
            spokenSegmentIndices.clear()
            startJukeboxStation(session.stationId, session.listeningStyle)
            return
        }
        val mode = _state.value.selectedMode ?: AiDjMode.DAILY_DJ
        spokenSegmentIndices.clear()
        startSession(mode)
    }

    private fun isStationListeningStyle(style: PulseListeningStyle): Boolean {
        return style == PulseListeningStyle.ENDLESS_JUKEBOX ||
            style == PulseListeningStyle.ERA ||
            style == PulseListeningStyle.GENRE
    }

    fun getPlayableQueue(): List<com.audiophile.musicplayer.data.local.entities.UnifiedTrackWithSources> {
        return _state.value.session.currentSegment?.tracks.orEmpty()
    }

    fun playTrackAt(track: com.audiophile.musicplayer.data.local.entities.UnifiedTrackWithSources) {
        val session = _state.value.session.currentSession ?: return
        val allSessionTracks = session.segments.flatMap { it.tracks }
        val startIndex = allSessionTracks.indexOfFirst { it.track.trackId == track.track.trackId }.coerceAtLeast(0)
        playerController.playQueue(allSessionTracks, startIndex, QueueMode.AI_DJ_QUEUE)
    }

    fun nextTrack() = playerController.next()

    fun previousTrack() = playerController.previous()

    fun togglePlayback() = playerController.togglePlayPause()

    fun seekTo(positionMs: Long) = playerController.seekTo(positionMs)

    /** Cycles companion personality (text-first; no TTS). */
    fun toggleDjVoice() {
        val modes = DjCompanionMode.entries
        val current = _state.value.companionMode
        val next = modes[(modes.indexOf(current) + 1) % modes.size]
        selectCompanionMode(next)
    }

    fun speakCurrentCommentary() {
        val text = _state.value.liveCommentary?.takeIf { it.isNotBlank() } ?: return
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) { pulseVoiceEngine.synthesize(text) }
            if (result != null) playerController.speakDjVoice(result.filePath)
        }
    }

    fun savedStationIds(): List<String> = stationMemory.savedStationIds()

    fun refreshPulseAiStatus() {
        _state.update { it.copy(isPulseAiActive = pulseAiBrain.isConfigured()) }
    }

    override fun onCleared() {
        super.onCleared()
    }
}
