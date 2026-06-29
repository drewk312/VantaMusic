package com.audiophile.musicplayer.playback

import androidx.media3.common.PlaybackException
import com.audiophile.musicplayer.common.VantaLogger

/**
 * Maps [PlaybackException] error codes to human-readable, actionable messages
 * for display in [NowPlayingState.errorMessage].
 *
 * Accept/reject examples:
 * - ERROR_CODE_IO_NETWORK_CONNECTION_FAILED → "Network error. Check your connection."
 * - ERROR_CODE_DECODING_FAILED             → "Audio format not supported."
 * - ERROR_CODE_TIMEOUT                     → "Stream timed out. Retrying…"
 */
object ErrorTranslator {

    /**
     * Returns a human-readable message and whether the error is likely transient
     * (worth an automatic retry).
     */
    fun translate(exception: PlaybackException): ErrorInfo {
        val code = exception.errorCode
        val msg = when (code) {
            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED ->
                "Network error. Check your connection."
            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
            PlaybackException.ERROR_CODE_TIMEOUT ->
                "Stream timed out. Retrying\u2026"
            PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS ->
                "The server returned an error for this track."
            PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND ->
                "Track file not found. It may have expired."
            PlaybackException.ERROR_CODE_IO_NO_PERMISSION ->
                "Permission denied. Check DRM or source settings."
            PlaybackException.ERROR_CODE_DECODING_FAILED,
            PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED ->
                "Audio format not supported."
            PlaybackException.ERROR_CODE_AUDIO_TRACK_WRITE_FAILED ->
                "Audio output error. Try restarting the app."
            PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW ->
                "Live stream buffer lost. Reconnecting\u2026"
            else ->
                "Playback error (${exception.message?.take(60).orEmpty()})"
        }
        val transient = code in TRANSIENT_CODES
        VantaLogger.w(
            VantaLogger.Tag.PLAYBACK,
            "error_translated code=$code transient=$transient msg='$msg'",
            exception
        )
        return ErrorInfo(message = msg, isTransient = transient)
    }

    data class ErrorInfo(val message: String, val isTransient: Boolean)

    private val TRANSIENT_CODES = setOf(
        PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
        PlaybackException.ERROR_CODE_TIMEOUT,
        PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW,
    )
}
