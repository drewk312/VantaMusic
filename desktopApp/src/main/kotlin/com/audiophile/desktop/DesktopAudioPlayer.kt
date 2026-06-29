package com.audiophile.desktop

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.bytedeco.javacv.FFmpegFrameGrabber
import org.bytedeco.javacv.Frame
import java.nio.Buffer
import java.nio.ByteBuffer
import java.nio.FloatBuffer
import java.nio.IntBuffer
import java.nio.ShortBuffer
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.SourceDataLine
import javax.swing.SwingUtilities

class DesktopAudioPlayer {
    private val executor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "desktop-audio-player").apply {
            isDaemon = true
        }
    }

    private val sessionCounter = AtomicInteger()

    @Volatile
    private var stopRequested = false

    @Volatile
    private var pauseRequested = false

    @Volatile
    private var activeLine: SourceDataLine? = null

    @Volatile
    private var activeGrabber: FFmpegFrameGrabber? = null

    var currentTrack by mutableStateOf<DesktopTrack?>(null)
        private set

    var isPlaying by mutableStateOf(false)
        private set

    var isPaused by mutableStateOf(false)
        private set

    var playbackPositionSec by mutableStateOf(0)
        private set

    var lastError by mutableStateOf<String?>(null)
        private set

    var onTrackEnded: (() -> Unit)? = null
    var onStatusMessage: ((String) -> Unit)? = null

    fun play(track: DesktopTrack) {
        val source = normalizeSource(track.pathOrHint)
        if (source == null) {
            publishFailure("Desktop playback supports local files or direct http(s) URLs")
            return
        }

        stopCurrentPlayback(clearTrack = false)
        val sessionId = sessionCounter.incrementAndGet()
        stopRequested = false
        pauseRequested = false

        updateState {
            currentTrack = track
            isPlaying = true
            isPaused = false
            playbackPositionSec = 0
            lastError = null
        }

        executor.submit {
            runPlaybackLoop(track, source, sessionId)
        }
    }

    fun togglePause() {
        if (currentTrack == null) return

        pauseRequested = !pauseRequested
        if (pauseRequested) {
            activeLine?.stop()
        } else {
            activeLine?.start()
        }

        updateState {
            isPaused = pauseRequested
            isPlaying = !pauseRequested
        }
    }

    fun close() {
        stopCurrentPlayback(clearTrack = true)
        executor.shutdownNow()
    }

    private fun runPlaybackLoop(track: DesktopTrack, source: String, sessionId: Int) {
        var endedNaturally = false
        try {
            val grabber = FFmpegFrameGrabber(source)
            activeGrabber = grabber
            grabber.start()

            val sampleRate = grabber.sampleRate.takeIf { it > 0 } ?: 44_100
            val channels = grabber.audioChannels.coerceAtLeast(1)
            val lineFormat = AudioFormat(sampleRate.toFloat(), 16, 2, true, false)
            val line = AudioSystem.getSourceDataLine(lineFormat)
            line.open(lineFormat)
            line.start()
            activeLine = line

            val engine = DspEngine(sampleRate)

            while (!stopRequested && sessionCounter.get() == sessionId) {
                if (pauseRequested) {
                    Thread.sleep(40)
                    continue
                }

                val frame = grabber.grabSamples() ?: break
                val bytes = decodeFrame(frame, channels, engine)
                if (bytes.isNotEmpty()) {
                    line.write(bytes, 0, bytes.size)
                }

                val currentPositionSec = (grabber.timestamp / 1_000_000L).toInt().coerceAtLeast(0)
                updateState {
                    playbackPositionSec = currentPositionSec
                }
            }

            line.drain()
            endedNaturally = !stopRequested && sessionCounter.get() == sessionId
        } catch (e: Exception) {
            publishFailure(e.message ?: "Playback failed")
        } finally {
            cleanupPlaybackResources()
            updateState {
                isPlaying = false
                isPaused = false
            }
            if (endedNaturally) {
                dispatchCallback {
                    onStatusMessage?.invoke("Finished ${track.title}")
                    onTrackEnded?.invoke()
                }
            }
        }
    }

    private fun decodeFrame(frame: Frame, inputChannels: Int, engine: DspEngine): ByteArray {
        val sampleBuffers = frame.samples ?: return ByteArray(0)
        if (sampleBuffers.isEmpty()) return ByteArray(0)

        val sampleCount = when {
            sampleBuffers.size >= 2 -> minOf(bufferLength(sampleBuffers[0]), bufferLength(sampleBuffers[1]))
            else -> (bufferLength(sampleBuffers[0]) / inputChannels.coerceAtLeast(1)).coerceAtLeast(0)
        }

        if (sampleCount <= 0) return ByteArray(0)

        val left = FloatArray(sampleCount)
        val right = FloatArray(sampleCount)

        if (sampleBuffers.size >= 2) {
            readPlanarChannel(sampleBuffers[0], left)
            readPlanarChannel(sampleBuffers[1], right)
        } else {
            readInterleavedChannels(sampleBuffers[0], inputChannels, left, right)
        }

        engine.process(left, right)
        return toStereoPcm16(left, right)
    }

    private fun readPlanarChannel(buffer: Buffer, target: FloatArray) {
        when (buffer) {
            is FloatBuffer -> {
                for (index in target.indices) {
                    target[index] = if (buffer.hasRemaining()) buffer.get() else 0f
                }
            }

            is ShortBuffer -> {
                for (index in target.indices) {
                    target[index] = if (buffer.hasRemaining()) buffer.get() / 32768f else 0f
                }
            }

            is IntBuffer -> {
                for (index in target.indices) {
                    target[index] = if (buffer.hasRemaining()) buffer.get() / 2147483648f else 0f
                }
            }

            is ByteBuffer -> {
                for (index in target.indices) {
                    target[index] = if (buffer.hasRemaining()) buffer.get() / 128f else 0f
                }
            }
        }
    }

    private fun readInterleavedChannels(buffer: Buffer, channels: Int, left: FloatArray, right: FloatArray) {
        val safeChannels = channels.coerceAtLeast(1)
        when (buffer) {
            is FloatBuffer -> {
                for (index in left.indices) {
                    val first = nextSample(buffer)
                    val second = if (safeChannels > 1) nextSample(buffer) else first
                    left[index] = first
                    right[index] = second
                    skipExtraChannels(buffer, safeChannels)
                }
            }

            is ShortBuffer -> {
                for (index in left.indices) {
                    val first = nextSample(buffer)
                    val second = if (safeChannels > 1) nextSample(buffer) else first
                    left[index] = first
                    right[index] = second
                    skipExtraChannels(buffer, safeChannels)
                }
            }

            is IntBuffer -> {
                for (index in left.indices) {
                    val first = nextSample(buffer)
                    val second = if (safeChannels > 1) nextSample(buffer) else first
                    left[index] = first
                    right[index] = second
                    skipExtraChannels(buffer, safeChannels)
                }
            }

            is ByteBuffer -> {
                for (index in left.indices) {
                    val first = nextSample(buffer)
                    val second = if (safeChannels > 1) nextSample(buffer) else first
                    left[index] = first
                    right[index] = second
                    skipExtraChannels(buffer, safeChannels)
                }
            }
        }
    }

    private fun nextSample(buffer: FloatBuffer): Float = if (buffer.hasRemaining()) buffer.get() else 0f

    private fun nextSample(buffer: ShortBuffer): Float = if (buffer.hasRemaining()) buffer.get() / 32768f else 0f

    private fun nextSample(buffer: IntBuffer): Float = if (buffer.hasRemaining()) buffer.get() / 2147483648f else 0f

    private fun nextSample(buffer: ByteBuffer): Float = if (buffer.hasRemaining()) buffer.get() / 128f else 0f

    private fun skipExtraChannels(buffer: FloatBuffer, channels: Int) {
        repeat((channels - 2).coerceAtLeast(0)) {
            if (buffer.hasRemaining()) buffer.get()
        }
    }

    private fun skipExtraChannels(buffer: ShortBuffer, channels: Int) {
        repeat((channels - 2).coerceAtLeast(0)) {
            if (buffer.hasRemaining()) buffer.get()
        }
    }

    private fun skipExtraChannels(buffer: IntBuffer, channels: Int) {
        repeat((channels - 2).coerceAtLeast(0)) {
            if (buffer.hasRemaining()) buffer.get()
        }
    }

    private fun skipExtraChannels(buffer: ByteBuffer, channels: Int) {
        repeat((channels - 2).coerceAtLeast(0)) {
            if (buffer.hasRemaining()) buffer.get()
        }
    }

    private fun toStereoPcm16(left: FloatArray, right: FloatArray): ByteArray {
        val bytes = ByteArray(left.size * 4)
        var writeIndex = 0
        for (index in left.indices) {
            val leftSample = (left[index].coerceIn(-1f, 1f) * 32767f).toInt().coerceIn(-32768, 32767)
            val rightSample = (right[index].coerceIn(-1f, 1f) * 32767f).toInt().coerceIn(-32768, 32767)

            bytes[writeIndex++] = (leftSample and 0xFF).toByte()
            bytes[writeIndex++] = ((leftSample shr 8) and 0xFF).toByte()
            bytes[writeIndex++] = (rightSample and 0xFF).toByte()
            bytes[writeIndex++] = ((rightSample shr 8) and 0xFF).toByte()
        }
        return bytes
    }

    private fun bufferLength(buffer: Buffer): Int = when (buffer) {
        is FloatBuffer -> buffer.remaining()
        is ShortBuffer -> buffer.remaining()
        is IntBuffer -> buffer.remaining()
        is ByteBuffer -> buffer.remaining()
        else -> 0
    }

    private fun normalizeSource(pathOrHint: String): String? {
        val trimmed = pathOrHint.trim()
        if (trimmed.isBlank()) return null
        if (trimmed.startsWith("http://", ignoreCase = true) || trimmed.startsWith("https://", ignoreCase = true)) {
            return trimmed
        }
        return trimmed.takeIf { java.io.File(it).exists() }
    }

    private fun stopCurrentPlayback(clearTrack: Boolean) {
        stopRequested = true
        pauseRequested = false
        cleanupPlaybackResources()
        updateState {
            isPlaying = false
            isPaused = false
            playbackPositionSec = 0
            if (clearTrack) {
                currentTrack = null
            }
        }
    }

    private fun cleanupPlaybackResources() {
        try {
            activeLine?.stop()
            activeLine?.flush()
            activeLine?.close()
        } catch (_: Exception) {
        } finally {
            activeLine = null
        }

        try {
            activeGrabber?.stop()
            activeGrabber?.release()
        } catch (_: Exception) {
        } finally {
            activeGrabber = null
        }
    }

    private fun publishFailure(message: String) {
        updateState {
            isPlaying = false
            isPaused = false
            lastError = message
        }
        dispatchCallback {
            onStatusMessage?.invoke(message)
        }
    }

    private fun updateState(block: () -> Unit) {
        if (SwingUtilities.isEventDispatchThread()) {
            block()
        } else {
            SwingUtilities.invokeLater(block)
        }
    }

    private fun dispatchCallback(block: () -> Unit) {
        if (SwingUtilities.isEventDispatchThread()) {
            block()
        } else {
            SwingUtilities.invokeLater(block)
        }
    }
}
