@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package com.audiophile.musicplayer.auto

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.net.Uri
import android.os.Bundle
import androidx.media3.common.Player
import androidx.media3.common.util.BitmapLoader
import androidx.media3.session.CommandButton
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionError
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import coil.Coil
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.audiophile.musicplayer.R
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import androidx.core.graphics.createBitmap

class AndroidAutoController(
    private val context: Context,
    private val scope: CoroutineScope,
    private val player: Player,
    private val onToggleFavorite: () -> Unit,
    private val isFavoriteProvider: () -> Boolean,
    private val ensureLibraryAccess: (MediaSession.ControllerInfo) -> Boolean,
    private val onSongRadio: () -> Unit = {},
    private val onMoreLikeThis: () -> Unit = {},
    private val onChangeVibe: () -> Unit = {},
    private val onAiDj: () -> Unit = {},
    private val onShowLyrics: () -> Unit = {}
) {
    companion object {
        const val ACTION_TOGGLE_FAVORITE = "vanta_toggle_favorite"
        const val ACTION_TOGGLE_SHUFFLE = "vanta_toggle_shuffle"
        const val ACTION_TOGGLE_REPEAT = "vanta_toggle_repeat"
        const val ACTION_SONG_RADIO = "vanta_song_radio"
        const val ACTION_MORE_LIKE_THIS = "vanta_more_like_this"
        const val ACTION_CHANGE_VIBE = "vanta_change_vibe"
        const val ACTION_AI_DJ = "vanta_ai_dj"
        const val ACTION_LYRICS = "vanta_show_lyrics"
    }

    fun createBitmapLoader(): BitmapLoader {
        return object : BitmapLoader {
            override fun supportsMimeType(mimeType: String) = true

            override fun decodeBitmap(data: ByteArray): ListenableFuture<Bitmap> {
                val future = SettableFuture.create<Bitmap>()
                scope.launch(Dispatchers.IO) {
                    try {
                        val bitmap = android.graphics.BitmapFactory.decodeByteArray(data, 0, data.size)
                        if (bitmap != null) future.set(bitmap)
                        else future.setException(IllegalArgumentException("Failed to decode bitmap"))
                    } catch (e: Exception) {
                        future.setException(e)
                    }
                }
                return future
            }

            override fun loadBitmap(uri: Uri): ListenableFuture<Bitmap> {
                val future = SettableFuture.create<Bitmap>()
                scope.launch(Dispatchers.IO) {
                    try {
                        val request = ImageRequest.Builder(context)
                            .data(uri)
                            .size(320, 320)
                            .allowHardware(false)
                            .build()
                        val result = Coil.imageLoader(context).execute(request)
                        val bitmap = (result as? SuccessResult)?.drawable?.let { drawable ->
                            createBitmap(
                                drawable.intrinsicWidth.coerceAtLeast(1),
                                drawable.intrinsicHeight.coerceAtLeast(1),
                                Bitmap.Config.ARGB_8888
                            ).also { bmp ->
                                Canvas(bmp).apply {
                                    drawable.setBounds(0, 0, width, height)
                                    drawable.draw(this)
                                }
                            }
                        }
                        if (bitmap != null) {
                            future.set(bitmap)
                        } else {
                            future.setException(IllegalArgumentException("Failed to load $uri"))
                        }
                    } catch (e: Exception) {
                        future.setException(e)
                    }
                }
                return future
            }
        }
    }

    fun onCustomCommand(
        session: MediaSession,
        controller: MediaSession.ControllerInfo,
        customAction: SessionCommand,
        args: Bundle
    ): ListenableFuture<SessionResult> {
        if (!ensureLibraryAccess(controller)) {
            return Futures.immediateFuture(SessionResult(SessionError.ERROR_UNKNOWN))
        }
        val result = Bundle()
        when (customAction.customAction) {
            ACTION_TOGGLE_FAVORITE -> {
                onToggleFavorite()
                result.putBoolean("isFavorite", isFavoriteProvider())
                session.setCustomLayout(buildCommandButtons())
            }
            ACTION_TOGGLE_SHUFFLE -> {
                player.shuffleModeEnabled = !player.shuffleModeEnabled
                session.setCustomLayout(buildCommandButtons())
                result.putBoolean("shuffleEnabled", player.shuffleModeEnabled)
            }
            ACTION_TOGGLE_REPEAT -> {
                player.repeatMode = when (player.repeatMode) {
                    Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
                    Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
                    else -> Player.REPEAT_MODE_OFF
                }
                session.setCustomLayout(buildCommandButtons())
                result.putInt("repeatMode", player.repeatMode)
            }
            ACTION_SONG_RADIO -> {
                onSongRadio()
                result.putBoolean("action", true)
            }
            ACTION_MORE_LIKE_THIS -> {
                onMoreLikeThis()
                result.putBoolean("action", true)
            }
            ACTION_CHANGE_VIBE -> {
                onChangeVibe()
                result.putBoolean("action", true)
            }
            ACTION_AI_DJ -> {
                onAiDj()
                result.putBoolean("action", true)
            }
            ACTION_LYRICS -> {
                onShowLyrics()
                result.putBoolean("action", true)
            }
        }
        return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS, result))
    }

    fun buildCommandButtons(): List<CommandButton> {
        val buttons = mutableListOf<CommandButton>()

        buttons.add(CommandButton.Builder()
            .setDisplayName("Favorite")
            .setIconResId(if (isFavoriteProvider()) R.drawable.ic_favorite_filled else android.R.drawable.star_off)
            .setSessionCommand(SessionCommand(ACTION_TOGGLE_FAVORITE, Bundle()))
            .setEnabled(true)
            .build())

        buttons.add(CommandButton.Builder()
            .setDisplayName("Shuffle")
            .setIconResId(R.drawable.ic_shuffle)
            .setSessionCommand(SessionCommand(ACTION_TOGGLE_SHUFFLE, Bundle()))
            .setEnabled(player.shuffleModeEnabled)
            .build())

        val repeatIcon = when (player.repeatMode) {
            Player.REPEAT_MODE_ONE -> R.drawable.ic_repeat_one
            Player.REPEAT_MODE_ALL -> R.drawable.ic_repeat_all
            else -> R.drawable.ic_repeat
        }
        buttons.add(CommandButton.Builder()
            .setDisplayName("Repeat")
            .setIconResId(repeatIcon)
            .setSessionCommand(SessionCommand(ACTION_TOGGLE_REPEAT, Bundle()))
            .setEnabled(true)
            .build())

        buttons.add(CommandButton.Builder()
            .setDisplayName("Song Radio")
            .setIconResId(android.R.drawable.ic_menu_share)
            .setSessionCommand(SessionCommand(ACTION_SONG_RADIO, Bundle()))
            .setEnabled(true)
            .build())

        buttons.add(CommandButton.Builder()
            .setDisplayName("More Like This")
            .setIconResId(android.R.drawable.ic_menu_search)
            .setSessionCommand(SessionCommand(ACTION_MORE_LIKE_THIS, Bundle()))
            .setEnabled(true)
            .build())

        buttons.add(CommandButton.Builder()
            .setDisplayName("Change Vibe")
            .setIconResId(android.R.drawable.ic_menu_sort_by_size)
            .setSessionCommand(SessionCommand(ACTION_CHANGE_VIBE, Bundle()))
            .setEnabled(true)
            .build())

        buttons.add(CommandButton.Builder()
            .setDisplayName("AI DJ")
            .setIconResId(android.R.drawable.ic_menu_compass)
            .setSessionCommand(SessionCommand(ACTION_AI_DJ, Bundle()))
            .setEnabled(true)
            .build())

        buttons.add(CommandButton.Builder()
            .setDisplayName("Lyrics")
            .setIconResId(android.R.drawable.ic_menu_gallery)
            .setSessionCommand(SessionCommand(ACTION_LYRICS, Bundle()))
            .setEnabled(true)
            .build())

        return buttons
    }
}