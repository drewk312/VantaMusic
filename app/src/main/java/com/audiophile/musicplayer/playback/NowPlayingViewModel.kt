package com.audiophile.musicplayer.playback

import android.util.Log
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import com.audiophile.musicplayer.data.lyrics.LyricsRepository
import com.audiophile.musicplayer.data.lyrics.LyricsTranslationProvider
import com.audiophile.musicplayer.data.local.entities.UnifiedTrack
import com.audiophile.musicplayer.data.llm.PulseAiBrain
import com.audiophile.musicplayer.playback.dsp.VantaEqualizerHolder
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import androidx.lifecycle.viewModelScope

@HiltViewModel
class NowPlayingViewModel @Inject constructor(
    private val playerController: PlayerController,
    private val stateStore: NowPlayingStateStore,
    private val lyricsRepository: LyricsRepository?,
    private val playbackState: PlaybackStateHolder,
    private val pulseAiBrain: PulseAiBrain,
    private val lyricsTranslationProvider: LyricsTranslationProvider? = null
) : ViewModel() {

    private val _lyrics = MutableStateFlow<com.audiophile.musicplayer.data.lyrics.LyricsData?>(null)
    val lyrics: StateFlow<com.audiophile.musicplayer.data.lyrics.LyricsData?> = _lyrics.asStateFlow()
    private val _lyricsTrackId = MutableStateFlow<String?>(null)
    val lyricsTrackId: StateFlow<String?> = _lyricsTrackId.asStateFlow()
    private val _lyricsLoading = MutableStateFlow(false)
    val lyricsLoading: StateFlow<Boolean> = _lyricsLoading.asStateFlow()
    private val _translationEnabled = MutableStateFlow(false)
    val translationEnabled: StateFlow<Boolean> = _translationEnabled.asStateFlow()

    private val _pulseInsight = MutableStateFlow<String?>(null)
    val pulseInsight: StateFlow<String?> = _pulseInsight.asStateFlow()
    private val _pulseDeepInsight = MutableStateFlow<String?>(null)
    val pulseDeepInsight: StateFlow<String?> = _pulseDeepInsight.asStateFlow()
    private val _pulseDeepLoading = MutableStateFlow(false)
    val pulseDeepLoading: StateFlow<Boolean> = _pulseDeepLoading.asStateFlow()

    val isPulseConfigured: Boolean get() = pulseAiBrain.isConfigured()
    private val _pulseInsightLoading = MutableStateFlow(false)
    val pulseInsightLoading: StateFlow<Boolean> = _pulseInsightLoading.asStateFlow()

    @androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val amplitudes: StateFlow<FloatArray> = flowOf(FloatArray(24))
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), FloatArray(24))

    val state: StateFlow<NowPlayingState> = playbackState.state

    private var lastTrackId: String? = null
    private var lyricsFetchGeneration = 0
    private var pulseInsightGeneration = 0

    init {
        viewModelScope.launch {
            playbackState.state.collect { state ->
                val newTrackId = state.trackId
                if (newTrackId != lastTrackId) {
                    lastTrackId = newTrackId
                    lyricsFetchGeneration++
                    pulseInsightGeneration++
                    _lyrics.value = null
                    _lyricsTrackId.value = null
                    _lyricsLoading.value = false
                    _pulseInsight.value = null
                    _pulseDeepInsight.value = null
                    _pulseInsightLoading.value = false
                    _pulseDeepLoading.value = false
                    _translationEnabled.value = false
                    if (newTrackId != null) {
                        fetchLyrics(state)
                        fetchPulseInsight(state)
                    }
                }
            }
        }
    }

    fun restore() {
        if (playerController.isDjQueueActive()) {
            Log.d("VANTA_NP", "restore() skipped — DJ queue active")
            return
        }
        if (playbackState.snapshot().isPlaying) {
            Log.d("VANTA_NP", "restore() skipped — player is active")
            return
        }
        val live = playbackState.snapshot()
        if (!live.trackId.isNullOrBlank() || !live.title.isNullOrBlank() || !live.artist.isNullOrBlank()) {
            Log.d("VANTA_NP", "restore() skipped — live snapshot already populated trackId=${live.trackId} title=${live.title}")
            return
        }
        val saved = stateStore.load()
        if (saved != null) {
            playbackState.replace(saved)
            lastTrackId = saved.trackId
            Log.d("VANTA_NP", "restore() loaded trackId=${saved.trackId} title=${saved.title}")
            viewModelScope.launch {
                fetchLyrics(saved)
                fetchPulseInsight(saved)
            }
        }
    }

    fun refreshFavorite() {
        val saved = stateStore.load()
        if (saved != null) {
            playbackState.update { copy(isFavorite = saved.isFavorite) }
            Log.d("VANTA_NP", "refreshFavorite() isFavorite=${saved.isFavorite}")
        }
    }

    fun updateState(transform: NowPlayingState.() -> NowPlayingState) {
        playbackState.update(transform)
        stateStore.save(playbackState.snapshot())
    }

    fun retryLyrics() {
        val state = playbackState.snapshot()
        val repo = lyricsRepository ?: return
        val title = state.title ?: return
        val artist = state.artist ?: return
        viewModelScope.launch {
            val cacheKey = com.audiophile.musicplayer.data.canonical.CanonicalIdentityResolver.generateCanonicalId(
                state.isrc, null, title, artist, state.album, state.durationMs.takeIf { it > 0 }
            )
            repo.invalidateCache(cacheKey, state.isrc)
            fetchLyrics(state)
        }
    }

    fun toggleTranslation() {
        val current = _translationEnabled.value
        _translationEnabled.value = !current
        if (!current) {
            _lyrics.value?.let { fetchTranslations(it) }
        }
    }

    private fun fetchTranslations(lyricsData: com.audiophile.musicplayer.data.lyrics.LyricsData) {
        val provider = lyricsTranslationProvider ?: return
        val lines = lyricsData.lines.map { it.text }
        if (lines.isEmpty()) return
        viewModelScope.launch {
            Log.d("VANTA_TRANSLATE", "Fetching translations for ${lines.size} lines")
            val translated = provider.translate(lines)
            var updated = false
            val newLines = lyricsData.lines.mapIndexed { i, line ->
                val t = translated.getOrNull(i)
                if (t != null && t != line.text) {
                    updated = true
                    line.copy(translatedText = t)
                } else line
            }
            if (updated) {
                _lyrics.value = lyricsData.copy(lines = newLines)
                Log.d("VANTA_TRANSLATE", "Applied translations to ${newLines.size} lines")
            }
        }
    }

    fun loadPulseDeepInsight() {
        if (!pulseAiBrain.isConfigured()) return
        val state = playbackState.snapshot()
        val title = state.title ?: return
        val artist = state.artist ?: return
        val trackId = state.trackId ?: return
        val generation = ++pulseInsightGeneration
        viewModelScope.launch {
            _pulseDeepLoading.value = true
            val album = state.album?.takeIf { it.isNotBlank() }
            val userPrompt = buildString {
                append("Write 2 short sentences about \"")
                append(title)
                append("\" by ")
                append(artist)
                if (album != null) append(" from the album \"$album\"")
                append(". Include one interesting fact (recording, era, or influence). ")
                append("Conversational tone, no bullet points, max 45 words total.")
            }
            val (text, fromAi) = pulseAiBrain.narrate(
                systemContext = "You are Pulse, an insightful music companion in a premium player app.",
                userPrompt = userPrompt,
                fallback = ""
            )
            if (generation != pulseInsightGeneration) return@launch
            _pulseDeepLoading.value = false
            if (playbackState.snapshot().trackId == trackId && fromAi && text.isNotBlank()) {
                _pulseDeepInsight.value = text.trim()
            }
        }
    }

    fun clearPulseDeepInsight() {
        _pulseDeepInsight.value = null
    }

    private fun fetchLyrics(state: NowPlayingState) {
        val repo = lyricsRepository ?: return
        val title = state.title ?: return
        val artist = state.artist ?: return
        val trackId = state.trackId ?: return
        val generation = ++lyricsFetchGeneration
        viewModelScope.launch {
            _lyrics.value = null
            _lyricsTrackId.value = null
            _lyricsLoading.value = true

            val result = try {
                repo.getLyrics(
                    UnifiedTrack(
                        title = title,
                        artist = artist,
                        albumName = state.album,
                        coverArtUrl = state.artworkUrl,
                        durationMs = state.durationMs.takeIf { it > 0 }
                    ),
                    state.isrc
                )
            } catch (e: Exception) {
                Log.e("VANTA_LYRICS_TRUTH", "Lyrics fetch failed for trackId=$trackId", e)
                null
            }

            if (generation != lyricsFetchGeneration) return@launch

            val currentState = playbackState.snapshot()
            val currentTrackId = currentState.trackId
            val identityMatch = currentTrackId == trackId
            val isrcMatch = result != null && state.isrc != null && currentState.isrc != null && state.isrc == currentState.isrc
            val accepted = identityMatch

            val msg = buildString {
                append("nowPlaying.trackId=$currentTrackId ")
                append("nowPlaying.title='${currentState.title}' ")
                append("nowPlaying.artist='${currentState.artist}' ")
                append("lyricsTrackId=$trackId ")
                append("lyricsTitle='${state.title}' ")
                append("lyricsArtist='${state.artist}' ")
                append("isrcMatch=$isrcMatch ")
                append("accepted=$accepted ")
                if (!accepted) append("rejectReason=stale_response currentTrackId=$currentTrackId lyricsTrackId=$trackId")
                else append("rejectReason=none")
            }
            Log.d("VANTA_LYRICS_TRUTH", msg)

            if (accepted) {
                _lyrics.value = result
                _lyricsTrackId.value = trackId
            } else {
                _lyrics.value = null
                _lyricsTrackId.value = null
            }
            _lyricsLoading.value = false
        }
    }

    private fun fetchPulseInsight(state: NowPlayingState) {
        if (!pulseAiBrain.isConfigured()) {
            _pulseInsight.value = null
            _pulseInsightLoading.value = false
            return
        }
        val title = state.title ?: return
        val artist = state.artist ?: return
        val trackId = state.trackId ?: return
        val generation = ++pulseInsightGeneration
        viewModelScope.launch {
            _pulseInsight.value = null
            _pulseInsightLoading.value = true
            val album = state.album?.takeIf { it.isNotBlank() }
            val userPrompt = buildString {
                append("One concise sentence (max 15 words) — a tasteful music insight about ")
                append("\"$title\" by $artist")
                if (album != null) append(" from \"$album\"")
                append(". No quotes around the insight, no hype.")
            }
            val (text, fromAi) = pulseAiBrain.narrate(
                systemContext = "You are Pulse, a knowledgeable but understated music companion.",
                userPrompt = userPrompt,
                fallback = ""
            )
            if (generation != pulseInsightGeneration) return@launch
            _pulseInsightLoading.value = false
            _pulseInsight.value = if (fromAi && text.isNotBlank()) text.trim() else null
        }
    }
}
