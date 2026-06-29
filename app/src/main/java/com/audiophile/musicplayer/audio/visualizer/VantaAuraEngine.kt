package com.audiophile.musicplayer.audio.visualizer

import android.util.Log
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

class VantaAuraEngine(
    private val scope: CoroutineScope,
    private val analyzer: VantaAudioAnalyzer
) {
    companion object {
        private const val TAG = "VANTA_AURA_ENGINE"
    }

    private val _state = MutableStateFlow(VantaAuraState())
    val state: StateFlow<VantaAuraState> = _state.asStateFlow()

    private val _mode = MutableStateFlow(AuraMode.AMBIENT)
    val mode: StateFlow<AuraMode> = _mode.asStateFlow()

    init {
        scope.launch {
            combine(
                analyzer.audioFrame,
                analyzer.isActive
            ) { frame, isActive ->
                val s = _state.value
                val isPlaying = s.isPlaying
                
                // Enhanced energy calculation using rich frame data
                val energy = when {
                    frame.isLiveAudio -> {
                        // Blend RMS with beat intensity for more "pop"
                        (frame.rms * 0.7f + frame.beatIntensity * 0.3f).coerceIn(0f, 1f)
                    }
                    isPlaying -> 0.3f
                    else -> 0.1f
                }
                
                _state.value = s.copy(
                    isActive = s.isActive || isActive,
                    lyricLineEnergy = energy,
                    beatDetected = frame.isBeat
                )
            }.collect {}
        }
    }

    fun setMode(mode: AuraMode) {
        _mode.value = mode
        _state.value = _state.value.copy(mode = mode)
        Log.d(TAG, "mode=$mode")
    }

    fun setPlaying(isPlaying: Boolean) {
        _state.value = _state.value.copy(isPlaying = isPlaying, isActive = isPlaying || _state.value.isActive)
    }

    fun setTrackId(trackId: Long?) {
        _state.value = _state.value.copy(currentTrackId = trackId)
    }

    fun setReducedMotion(reduced: Boolean) {
        _state.value = _state.value.copy(reducedMotion = reduced)
    }

    fun setAudioReactive(enabled: Boolean) {
        _state.value = _state.value.copy(audioReactiveEnabled = enabled)
    }

    fun applyArtworkPalette(colors: List<Color>) {
        val palette = _state.value.palette.forArtwork(colors)
        _state.value = _state.value.copy(palette = palette)
    }

    fun setReduceMotionInCar(reduce: Boolean) {
        _state.value = _state.value.copy(reduceMotionInCar = reduce)
    }
}
