package com.audiophile.musicplayer.audio.visualizer

import android.media.audiofx.Visualizer
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.sqrt

class VantaAudioAnalyzer(
    private val scope: CoroutineScope,
    private val hasRecordAudioPermission: () -> Boolean = { false }
) {
    companion object {
        private const val TAG = "VANTA_AURA_ANALYZER"
        private const val CAPTURE_RATE_MS = 50L
        private const val FALLBACK_FRAME_INTERVAL_MS = 100L
        // Avoid the highest capture rates that can trigger offload parameter warnings on Pixel devices.
        private const val CAPTURE_RATE_HZ = 22050
    }

    private var visualizer: Visualizer? = null
    private var captureJob: Job? = null
    private var currentSessionId: Int = -1

    private val _audioFrame = MutableStateFlow(VantaAudioFrame())
    val audioFrame: StateFlow<VantaAudioFrame> = _audioFrame.asStateFlow()

    private val _isActive = MutableStateFlow(false)
    val isActive: StateFlow<Boolean> = _isActive.asStateFlow()

    private var smoothedBass = 0f
    private var smoothedMid = 0f
    private var smoothedTreble = 0f
    private var smoothedRms = 0f
    private var smoothedPeak = 0f
    private var lastRms = 0f
    private var beatThreshold = 0.15f
    private var lastBeatTime = 0L

    fun attach(sessionId: Int) {
        if (sessionId == currentSessionId && visualizer != null) return
        release()
        currentSessionId = sessionId
        Log.d(TAG, "attach sessionId=$sessionId")
        if (!hasRecordAudioPermission()) {
            Log.w(TAG, "RECORD_AUDIO not granted, using fallback")
            startFallbackFrameEmitter()
            return
        }
        try {
            val viz = Visualizer(sessionId)
            val captureSize = Visualizer.getCaptureSizeRange()[1].coerceAtMost(1024)
            viz.captureSize = captureSize
            val captureRate = Visualizer.getMaxCaptureRate().coerceAtMost(CAPTURE_RATE_HZ)
            viz.setDataCaptureListener(
                object : Visualizer.OnDataCaptureListener {
                    override fun onWaveFormDataCapture(
                        visualizer: Visualizer?,
                        waveform: ByteArray?,
                        samplingRate: Int
                    ) {
                        if (waveform == null) return
                        val frame = processWaveform(waveform)
                        _audioFrame.value = frame
                    }

                    override fun onFftDataCapture(
                        visualizer: Visualizer?,
                        fft: ByteArray?,
                        samplingRate: Int
                    ) {
                        if (fft == null) return
                        val frame = processFft(fft)
                        _audioFrame.value = frame
                    }
                },
                captureRate,
                true,
                true
            )
            viz.enabled = true
            visualizer = viz
            _isActive.value = true
            Log.d(TAG, "attach success sessionId=$sessionId captureSize=$captureSize captureRate=$captureRate")
        } catch (e: UnsupportedOperationException) {
            Log.w(TAG, "Visualizer unsupported for sessionId=$sessionId (offload?); falling back", e)
            startFallbackFrameEmitter()
        } catch (e: RuntimeException) {
            Log.w(TAG, "Visualizer runtime error for sessionId=$sessionId; falling back", e)
            startFallbackFrameEmitter()
        } catch (e: Exception) {
            Log.w(TAG, "Visualizer attach failed sessionId=$sessionId error=${e.message}; falling back")
            startFallbackFrameEmitter()
        }
    }

    fun release() {
        captureJob?.cancel()
        captureJob = null
        try {
            visualizer?.enabled = false
            visualizer?.release()
        } catch (_: Exception) {
        }
        visualizer = null
        currentSessionId = -1
        _isActive.value = false
        resetSmoothed()
        Log.d(TAG, "release")
    }

    private fun processWaveform(waveform: ByteArray): VantaAudioFrame {
        val len = waveform.size
        if (len == 0) return VantaAudioFrame(timestampMs = System.currentTimeMillis())

        val magnitudes = FloatArray(len) { i ->
            kotlin.math.abs((waveform[i].toInt() and 0xFF) - 128).toFloat() / 128f
        }
        val rmsVal = sqrt(magnitudes.map { it * it }.average().toFloat()).coerceIn(0f, 1f)
        val peakVal = (magnitudes.maxOrNull() ?: 0f).coerceIn(0f, 1f)

        val bandSize = (len / 3).coerceAtLeast(1)
        val bass = magnitudes.take(bandSize).average().toFloat().coerceIn(0f, 1f)
        val mid = magnitudes.drop(bandSize).take(bandSize).average().toFloat().coerceIn(0f, 1f)
        val treble = magnitudes.drop(bandSize * 2).average().toFloat().coerceIn(0f, 1f)

        val bucketCount = 8
        val bucketSize = (len / bucketCount).coerceAtLeast(1)
        val buckets = (0 until bucketCount).map { b ->
            val start = b * bucketSize
            val end = (start + bucketSize).coerceAtMost(len)
            magnitudes.slice(start until end).average().toFloat().coerceIn(0f, 1f)
        }

        smoothedBass = smooth(smoothedBass, bass)
        smoothedMid = smooth(smoothedMid, mid)
        smoothedTreble = smooth(smoothedTreble, treble)
        smoothedRms = smooth(smoothedRms, rmsVal)
        smoothedPeak = smooth(smoothedPeak, peakVal)

        val isBeat = (rmsVal > beatThreshold && (rmsVal - lastRms) > 0.05f && System.currentTimeMillis() - lastBeatTime > 250)
        if (isBeat) {
            lastBeatTime = System.currentTimeMillis()
            beatThreshold = (beatThreshold * 0.9f + rmsVal * 0.1f).coerceAtLeast(0.1f)
        } else {
            beatThreshold = (beatThreshold * 0.995f).coerceAtLeast(0.1f)
        }
        val transient = ((rmsVal - lastRms).coerceAtLeast(0f) * 5f).coerceIn(0f, 1f)
        lastRms = rmsVal

        return VantaAudioFrame(
            bassEnergy = smoothedBass,
            midEnergy = smoothedMid,
            trebleEnergy = smoothedTreble,
            rms = smoothedRms,
            peak = smoothedPeak,
            fftBuckets = buckets,
            timestampMs = System.currentTimeMillis(),
            isLiveAudio = true,
            isBeat = isBeat,
            beatIntensity = if (isBeat) rmsVal else 0f,
            transientEnergy = transient
        )
    }

    private fun processFft(fft: ByteArray): VantaAudioFrame {
        val n = fft.size
        if (n <= 2) return _audioFrame.value.copy(timestampMs = System.currentTimeMillis())

        val bucketCount = 24
        val spectrum = FloatArray(bucketCount)
        
        // Real part at [0], Nyquist at [1]. We skip these or handle separately.
        // Frequencies are in index i: [2*i, 2*i+1]
        // k-th frequency is k * samplingRate / n
        
        for (i in 0 until bucketCount) {
            val start = (i * (n / 2) / bucketCount).coerceAtLeast(1)
            val end = ((i + 1) * (n / 2) / bucketCount).coerceAtMost(n / 2 - 1)
            var sum = 0f
            var count = 0
            for (k in start..end) {
                val re = fft[2 * k].toFloat()
                val im = fft[2 * k + 1].toFloat()
                val mag = sqrt(re * re + im * im)
                sum += mag
                count++
            }
            spectrum[i] = if (count > 0) (sum / count) / 128f else 0f
        }

        val bass = spectrum.take(3).average().toFloat().coerceIn(0f, 1f)
        val mid = spectrum.slice(3 until 12).average().toFloat().coerceIn(0f, 1f)
        val treble = spectrum.drop(12).average().toFloat().coerceIn(0f, 1f)

        smoothedBass = smooth(smoothedBass, bass, 0.25f)
        smoothedMid = smooth(smoothedMid, mid, 0.15f)
        smoothedTreble = smooth(smoothedTreble, treble, 0.12f)
        
        // Use existing RMS from waveform if available, or estimate from FFT
        val rms = sqrt(spectrum.map { it * it }.average().toFloat()).coerceIn(0f, 1f)
        smoothedRms = smooth(smoothedRms, rms, 0.2f)

        val isBeat = (rms > beatThreshold && (rms - lastRms) > 0.05f && System.currentTimeMillis() - lastBeatTime > 250)
        if (isBeat) {
            lastBeatTime = System.currentTimeMillis()
            beatThreshold = (beatThreshold * 0.9f + rms * 0.1f).coerceAtLeast(0.1f)
        } else {
            beatThreshold = (beatThreshold * 0.995f).coerceAtLeast(0.1f)
        }
        val transient = ((rms - lastRms).coerceAtLeast(0f) * 5f).coerceIn(0f, 1f)
        lastRms = rms

        return VantaAudioFrame(
            bassEnergy = smoothedBass,
            midEnergy = smoothedMid,
            trebleEnergy = smoothedTreble,
            rms = smoothedRms,
            peak = spectrum.maxOrNull() ?: 0f,
            fftBuckets = spectrum.toList(),
            timestampMs = System.currentTimeMillis(),
            isLiveAudio = true,
            isBeat = isBeat,
            beatIntensity = if (isBeat) rms else 0f,
            transientEnergy = transient
        )
    }

    private fun startFallbackFrameEmitter() {
        captureJob?.cancel()
        resetSmoothed()
        _isActive.value = true
        captureJob = scope.launch {
            while (isActive) {
                val phase = (System.currentTimeMillis() % 2000L) / 2000f
                val breath = ((kotlin.math.sin(phase * kotlin.math.PI * 2) + 1f) / 2f).toFloat()
                smoothedBass = smooth(smoothedBass, breath * 0.4f)
                smoothedMid = smooth(smoothedMid, breath * 0.25f)
                smoothedTreble = smooth(smoothedTreble, breath * 0.15f)
                smoothedRms = smooth(smoothedRms, breath * 0.3f)
                smoothedPeak = smooth(smoothedPeak, breath * 0.5f)
                val buckets = (0 until 8).map { ((kotlin.math.sin(phase * kotlin.math.PI * 2 + it) + 1f) / 2f * 0.3f).toFloat() }
                _audioFrame.value = VantaAudioFrame(
                    bassEnergy = smoothedBass,
                    midEnergy = smoothedMid,
                    trebleEnergy = smoothedTreble,
                    rms = smoothedRms,
                    peak = smoothedPeak,
                    fftBuckets = buckets,
                    timestampMs = System.currentTimeMillis(),
                    isLiveAudio = false
                )
                delay(FALLBACK_FRAME_INTERVAL_MS)
            }
        }
    }

    private fun resetSmoothed() {
        smoothedBass = 0f
        smoothedMid = 0f
        smoothedTreble = 0f
        smoothedRms = 0f
        smoothedPeak = 0f
        lastRms = 0f
        lastBeatTime = 0L
        beatThreshold = 0.15f
    }

    fun updateFromDspSpectrum(magnitudes: FloatArray) {
        if (magnitudes.isEmpty()) return
        val bucketCount = 24
        val sourceBands = magnitudes.size
        val buckets = if (sourceBands >= bucketCount) {
            (0 until bucketCount).map { b ->
                val start = b * sourceBands / bucketCount
                val end = ((b + 1) * sourceBands / bucketCount).coerceAtMost(sourceBands)
                magnitudes.slice(start until end).average().toFloat().coerceIn(0f, 1f)
            }
        } else {
            magnitudes.map { it.coerceIn(0f, 1f) } + List(bucketCount - sourceBands) { 0f }
        }
        val bass = buckets.take(4).average().toFloat().coerceIn(0f, 1f)
        val mid = buckets.slice(4 until 12).average().toFloat().coerceIn(0f, 1f)
        val treble = buckets.drop(12).average().toFloat().coerceIn(0f, 1f)
        val rms = sqrt(buckets.map { it * it }.average().toFloat()).coerceIn(0f, 1f)
        smoothedBass = smooth(smoothedBass, bass)
        smoothedMid = smooth(smoothedMid, mid)
        smoothedTreble = smooth(smoothedTreble, treble)
        smoothedRms = smooth(smoothedRms, rms)
        val peak = buckets.maxOrNull() ?: 0f
        smoothedPeak = smooth(smoothedPeak, peak)

        val isBeat = (rms > beatThreshold && (rms - lastRms) > 0.05f && System.currentTimeMillis() - lastBeatTime > 250)
        if (isBeat) {
            lastBeatTime = System.currentTimeMillis()
            beatThreshold = (beatThreshold * 0.9f + rms * 0.1f).coerceAtLeast(0.1f)
        } else {
            beatThreshold = (beatThreshold * 0.995f).coerceAtLeast(0.1f)
        }
        val transient = ((rms - lastRms).coerceAtLeast(0f) * 5f).coerceIn(0f, 1f)
        lastRms = rms

        _audioFrame.value = VantaAudioFrame(
            bassEnergy = smoothedBass, midEnergy = smoothedMid,
            trebleEnergy = smoothedTreble, rms = smoothedRms, peak = smoothedPeak,
            fftBuckets = buckets, timestampMs = System.currentTimeMillis(), isLiveAudio = true,
            isBeat = isBeat, beatIntensity = if (isBeat) rms else 0f, transientEnergy = transient
        )
    }

    private fun smooth(current: Float, target: Float, factor: Float = 0.3f): Float =
        current + (target - current) * factor
}
