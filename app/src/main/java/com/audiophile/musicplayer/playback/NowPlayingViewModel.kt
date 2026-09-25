package com.audiophile.musicplayer.playback

import android.util.Log
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import com.audiophile.musicplayer.data.lyrics.LyricsIdentity
import com.audiophile.musicplayer.data.lyrics.LyricsIdentityGate
import com.audiophile.musicplayer.data.lyrics.LyricsRepository
import com.audiophile.musicplayer.data.lyrics.LyricsSyncPreferences
import com.audiophile.musicplayer.data.lyrics.LyricsTranslationProvider
import com.audiophile.musicplayer.data.local.entities.UnifiedTrack
import com.audiophile.musicplayer.data.llm.PulseAiBrain
import com.audiophile.musicplayer.playback.dsp.VantaEqualizerHolder
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import androidx.lifecycle.viewModelScope

@HiltViewModel
class NowPlayingViewModel @Inject constructor(
    private val playerController: PlayerController,
    private val stateStore: NowPlayingStateStore,
    private val lyricsRepositoryLazy: dagger.Lazy<LyricsRepository>,
    private val playbackState: PlaybackStateHolder,
    private val pulseAiBrainLazy: dagger.Lazy<PulseAiBrain>,
    private val lyricsTranslationProviderLazy: dagger.Lazy<LyricsTranslationProvider>,
    private val canonicalGraphDaoLazy: dagger.Lazy<com.audiophile.musicplayer.data.canonical.CanonicalGraphDao>
) : ViewModel() {

    // The mini player only needs transport and the playback state. Lyrics, AI
    // and canonical graph services are loaded on first use by Now Playing,
    // rather than while Home is drawing its first frame.
    private val lyricsRepository: LyricsRepository get() = lyricsRepositoryLazy.get()
    private val pulseAiBrain: PulseAiBrain get() = pulseAiBrainLazy.get()
    private val lyricsTranslationProvider: LyricsTranslationProvider get() = lyricsTranslationProviderLazy.get()
    private val canonicalGraphDao: com.audiophile.musicplayer.data.canonical.CanonicalGraphDao
        get() = canonicalGraphDaoLazy.get()

    private val _lyrics = MutableStateFlow<com.audiophile.musicplayer.data.lyrics.LyricsData?>(null)
    val lyrics: StateFlow<com.audiophile.musicplayer.data.lyrics.LyricsData?> = _lyrics.asStateFlow()
    private val _lyricsTrackId = MutableStateFlow<String?>(null)
    val lyricsTrackId: StateFlow<String?> = _lyricsTrackId.asStateFlow()
    private val _lyricsLoading = MutableStateFlow(false)
    val lyricsLoading: StateFlow<Boolean> = _lyricsLoading.asStateFlow()
    private val _lyricsIdentity = MutableStateFlow<LyricsIdentity>(LyricsIdentity.Unavailable)
    val lyricsIdentity: StateFlow<LyricsIdentity> = _lyricsIdentity.asStateFlow()
    private val _translationEnabled = MutableStateFlow(false)
    val translationEnabled: StateFlow<Boolean> = _translationEnabled.asStateFlow()

    private val lyricsIdentityGate: LyricsIdentityGate get() = LyricsIdentityGate(lyricsRepository)

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

    /**
     * Playback metadata for the app shell. Progress and buffer positions update
     * several times per second, but Home/navigation only need identity and
     * transport state. Keeping those ticks out of the shell prevents broad
     * recomposition while a song is playing.
     */
    val chromeState: StateFlow<NowPlayingState> = state
        .map { it.withoutProgressTicks() }
        .distinctUntilChanged()
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            state.value.withoutProgressTicks()
        )

    private var lastTrackId: String? = null
    private var lyricsFetchGeneration = 0
    private var pulseInsightGeneration = 0
    @Volatile private var detailEnrichmentEnabled = false

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
                    _lyricsIdentity.value = LyricsIdentity.Unavailable
                    _pulseInsight.value = null
                    _pulseDeepInsight.value = null
                    _pulseInsightLoading.value = false
                    _pulseDeepLoading.value = false
                    _translationEnabled.value = false
                    if (newTrackId != null && detailEnrichmentEnabled) {
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
            if (detailEnrichmentEnabled) {
                fetchLyrics(saved)
                fetchPulseInsight(saved)
            }
        }
    }

    /** Enable services only used by the full Now Playing experience. */
    fun activateDetailEnrichment() {
        if (detailEnrichmentEnabled) return
        detailEnrichmentEnabled = true
        val current = playbackState.snapshot()
        if (!current.trackId.isNullOrBlank()) {
            fetchLyrics(current)
            fetchPulseInsight(current)
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
        val repo = lyricsRepository
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
        val provider = lyricsTranslationProvider
        val lines = lyricsData.lines.map { it.text }
        if (lines.isEmpty()) return
        val targetLang = java.util.Locale.getDefault().language  // Use device locale (e.g. "en", "es", "ja")
        viewModelScope.launch {
            Log.d("VANTA_TRANSLATE", "Fetching translations for ${lines.size} lines to '$targetLang'")
            val translated = provider.translate(lines, targetLang)
            var updated = false
            val newLines = lyricsData.lines.mapIndexed { i, line ->
                val t = translated.getOrNull(i)
                if (t != null && t != line.text && t.isNotBlank()) {
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
        val gate = lyricsIdentityGate
        val rawTitle = state.title ?: return
        val rawArtist = state.artist ?: return
        val trackId = state.trackId ?: return
        val cleaned = com.audiophile.musicplayer.data.display.DisplayMetadataCleaner.computeDisplayMetadata(
            rawTitle = rawTitle,
            rawArtist = rawArtist,
            rawAlbum = state.album
        )
        val generation = ++lyricsFetchGeneration
        viewModelScope.launch {
            _lyrics.value = null
            _lyricsTrackId.value = null
            _lyricsLoading.value = true
            _lyricsIdentity.value = LyricsIdentity.Unavailable

            // Prefer persisted canonical graph identity over provider packaging strings.
            val graphTrack = state.canonicalTrackId?.toLongOrNull()?.let { id ->
                runCatching { canonicalGraphDao.trackById(id) }.getOrNull()
            }
            val title = (graphTrack?.title ?: cleaned.title).ifBlank { rawTitle }
            val artist = (graphTrack?.artistDisplay ?: cleaned.artist)
                .substringBefore(" feat.")
                .trim()
                .ifBlank { rawArtist }
            val album = graphTrack?.albumDisplay ?: state.album
            val isrc = graphTrack?.isrc ?: state.isrc
            val durationMs = graphTrack?.durationMs ?: state.durationMs.takeIf { it > 0 }

            val identity = try {
                gate.resolveIdentity(
                    track = UnifiedTrack(
                        title = title,
                        artist = artist,
                        albumName = album,
                        coverArtUrl = graphTrack?.artworkUrl ?: state.artworkUrl,
                        durationMs = durationMs
                    ),
                    isrc = isrc,
                    userQuery = state.userQuery
                )
            } catch (e: Exception) {
                Log.e("VANTA_IDENTITY", "Lyrics identity resolve failed for trackId=$trackId", e)
                LyricsIdentity.Unavailable
            }

            if (generation != lyricsFetchGeneration) return@launch

            val currentState = playbackState.snapshot()
            val currentTrackId = currentState.trackId
            val identityMatch = currentTrackId == trackId
            val accepted = identityMatch && identity !is LyricsIdentity.Unavailable

            Log.d("VANTA_IDENTITY", "fetchLyrics trackId=$trackId currentTrackId=$currentTrackId identity=$identity accepted=$accepted")

            if (accepted) {
                _lyrics.value = identity.lyricsData
                _lyricsTrackId.value = trackId
                _lyricsIdentity.value = identity
            } else {
                _lyrics.value = null
                _lyricsTrackId.value = null
                _lyricsIdentity.value = LyricsIdentity.Unavailable
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

internal fun NowPlayingState.withoutProgressTicks(): NowPlayingState = copy(
    positionMs = 0L,
    bufferedMs = 0L,
    sleepTimerRemainingMs = sleepTimerRemainingMs?.let { 1L }
)
