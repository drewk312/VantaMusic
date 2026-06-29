@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package com.audiophile.musicplayer.ui.visualizer

import android.app.Application
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.audiophile.musicplayer.audio.visualizer.AuraMode
import com.audiophile.musicplayer.audio.visualizer.VantaAudioAnalyzer
import com.audiophile.musicplayer.audio.visualizer.VantaAuraEngine
import com.audiophile.musicplayer.audio.visualizer.VantaAuraState
import com.audiophile.musicplayer.playback.dsp.VantaEqualizerHolder
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class VantaVisualizerViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        private const val TAG = "VANTA_AURA_VM"
        private const val SPECTRUM_NOISE_FLOOR = 0.002f
        private const val ACTIVE_POLL_MS = 50L
        private const val IDLE_POLL_MS = 200L
    }

    private val analyzer = VantaAudioAnalyzer(
        scope = viewModelScope,
        hasRecordAudioPermission = {
            hasRecordAudioPermission()
        }
    )

    private val engine = VantaAuraEngine(
        scope = viewModelScope,
        analyzer = analyzer
    )

    val auraState: StateFlow<VantaAuraState> = engine.state
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), engine.state.value)

    val audioFrame = analyzer.audioFrame
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), analyzer.audioFrame.value)

    val analyzerActive = analyzer.isActive
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), analyzer.isActive.value)

    private var spectrumPollJob: Job? = null
    private var attachedSessionId: Int? = null

    fun attachToSession(sessionId: Int) {
        if (sessionId <= 0) {
            Log.w(TAG, "Ignoring invalid sessionId=$sessionId")
            return
        }

        if (attachedSessionId == sessionId && spectrumPollJob?.isActive == true) {
            return
        }

        attachedSessionId = sessionId
        Log.d(TAG, "attach sessionId=$sessionId")

        analyzer.attach(sessionId)
        startSpectrumPolling()
    }

    fun releaseAnalyzer() {
        Log.d(TAG, "release")
        attachedSessionId = null
        stopSpectrumPolling()
        analyzer.release()
    }

    fun setMode(mode: AuraMode) {
        engine.setMode(mode)
    }

    fun setPlaying(isPlaying: Boolean) {
        engine.setPlaying(isPlaying)
    }

    fun setTrackId(trackId: Long?) {
        engine.setTrackId(trackId)
    }

    fun setReducedMotion(reduced: Boolean) {
        engine.setReducedMotion(reduced)
    }

    fun setAudioReactive(enabled: Boolean) {
        engine.setAudioReactive(enabled)
    }

    fun setReduceMotionInCar(reduce: Boolean) {
        engine.setReduceMotionInCar(reduce)
    }

    fun hasRecordAudioPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            getApplication(),
            android.Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun startSpectrumPolling() {
        spectrumPollJob?.cancel()
        spectrumPollJob = viewModelScope.launch(kotlinx.coroutines.Dispatchers.Default) {
            var emptyCount = 0

            while (isActive) {
                val mags = VantaEqualizerHolder.processor?.getSpectrum()
                if (mags != null) {
                    val hasRealSignal = mags.any { it > SPECTRUM_NOISE_FLOOR }
                    if (hasRealSignal) {
                        emptyCount = 0
                        analyzer.updateFromDspSpectrum(mags)
                    } else {
                        // Keep silent DSP snapshots from overwriting live Visualizer or fallback frames.
                        emptyCount++
                    }
                } else {
                    emptyCount += 5 // Fast path to idle if no DSP is attached at all
                }

                delay(if (emptyCount > 60) IDLE_POLL_MS else ACTIVE_POLL_MS)
            }
        }
    }

    private fun stopSpectrumPolling() {
        spectrumPollJob?.cancel()
        spectrumPollJob = null
    }

    override fun onCleared() {
        stopSpectrumPolling()
        analyzer.release()
        super.onCleared()
    }
}
