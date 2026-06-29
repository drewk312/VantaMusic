@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package com.audiophile.musicplayer.playback

import android.content.Context
import android.os.Process
import android.util.Log
import com.audiophile.musicplayer.BuildConfig
import androidx.media3.common.Player
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionCommands
import com.audiophile.musicplayer.auto.AndroidAutoController

/**
 * Classifies MediaSession controllers and maps each trust tier to allowed commands.
 *
 * Package-name allowlists are combined with [PackageValidator] certificate pinning so
 * sideloaded spoof apps cannot inherit Auto/system privileges by name alone.
 */
class MediaSessionTrustPolicy(
    private val packageValidator: PackageValidator
) {
    enum class TrustLevel {
        SELF,
        TRUSTED_LIBRARY,
        TRUSTED_TRANSPORT,
        LIMITED,
        REJECTED
    }

    fun classify(
        context: Context,
        session: MediaSession,
        controller: MediaSession.ControllerInfo
    ): TrustLevel {
        val appPackage = context.packageName
        val controllerPackage = controller.packageName?.trim().orEmpty()

        if (controller.uid == Process.myUid()) {
            return TrustLevel.SELF
        }
        if (controllerPackage == appPackage) {
            return TrustLevel.SELF
        }
        if (session.isMediaNotificationController(controller)) {
            return TrustLevel.TRUSTED_TRANSPORT
        }

        if (controllerPackage in LIBRARY_PACKAGES) {
            if (BuildConfig.DEBUG) {
                Log.d(TAG, "debug trust library package=$controllerPackage uid=${controller.uid}")
                return TrustLevel.TRUSTED_LIBRARY
            }
            val pinned = packageValidator.hasPinnedSignature(controllerPackage, controller.uid)
            val platformSigned = packageValidator.isPlatformOrSystemCaller(controllerPackage, controller.uid)
            val frameworkTrusted = controller.isTrusted
            val knownAutoCaller = packageValidator.isKnownCaller(controllerPackage, controller.uid)
            return if (pinned || platformSigned || frameworkTrusted || knownAutoCaller) {
                TrustLevel.TRUSTED_LIBRARY
            } else {
                Log.w(TAG, "library package not pinned package=$controllerPackage uid=${controller.uid}")
                TrustLevel.REJECTED
            }
        }

        if (controllerPackage in TRANSPORT_PACKAGES) {
            return if (packageValidator.isKnownCaller(controllerPackage, controller.uid)) {
                TrustLevel.TRUSTED_TRANSPORT
            } else {
                Log.w(TAG, "transport package not trusted package=$controllerPackage uid=${controller.uid}")
                TrustLevel.REJECTED
            }
        }

        if (controller.isTrusted) {
            return TrustLevel.TRUSTED_TRANSPORT
        }

        if (controllerPackage.isNotBlank()) {
            return if (packageValidator.isKnownCaller(controllerPackage, controller.uid)) {
                TrustLevel.TRUSTED_TRANSPORT
            } else {
                TrustLevel.REJECTED
            }
        }

        // Legacy platform / Bluetooth controllers may not expose a package name.
        return TrustLevel.LIMITED
    }

    fun buildConnectionResult(
        session: MediaSession,
        level: TrustLevel,
        controller: MediaSession.ControllerInfo,
        player: Player? = null,
    ): MediaSession.ConnectionResult {
        if (level == TrustLevel.REJECTED) {
            Log.w(
                TAG,
                "reject package=${controller.packageName} uid=${controller.uid}"
            )
            return MediaSession.ConnectionResult.reject()
        }

        val baseCommands = when (level) {
            TrustLevel.SELF,
            TrustLevel.TRUSTED_LIBRARY -> MediaSession.ConnectionResult.DEFAULT_SESSION_AND_LIBRARY_COMMANDS
            TrustLevel.TRUSTED_TRANSPORT,
            TrustLevel.LIMITED -> MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS
            TrustLevel.REJECTED -> error("Rejected controllers cannot receive session commands")
        }
        val sessionCommandsBuilder = SessionCommands.Builder()
        for (cmd in baseCommands.commands) {
            sessionCommandsBuilder.add(cmd)
        }
        sessionCommandsBuilder.add(SessionCommand(AndroidAutoController.ACTION_TOGGLE_FAVORITE, android.os.Bundle()))
        sessionCommandsBuilder.add(SessionCommand(AndroidAutoController.ACTION_TOGGLE_SHUFFLE, android.os.Bundle()))
        sessionCommandsBuilder.add(SessionCommand(AndroidAutoController.ACTION_TOGGLE_REPEAT, android.os.Bundle()))
        sessionCommandsBuilder.add(SessionCommand(AndroidAutoController.ACTION_SONG_RADIO, android.os.Bundle()))
        sessionCommandsBuilder.add(SessionCommand(AndroidAutoController.ACTION_MORE_LIKE_THIS, android.os.Bundle()))
        sessionCommandsBuilder.add(SessionCommand(AndroidAutoController.ACTION_CHANGE_VIBE, android.os.Bundle()))
        sessionCommandsBuilder.add(SessionCommand(AndroidAutoController.ACTION_AI_DJ, android.os.Bundle()))
        sessionCommandsBuilder.add(SessionCommand(AndroidAutoController.ACTION_LYRICS, android.os.Bundle()))
        val sessionCommands = sessionCommandsBuilder.build()

        val playerCommands = when (level) {
            TrustLevel.SELF -> player?.availableCommands
                ?: Player.Commands.Builder().addAllCommands().build()
            TrustLevel.TRUSTED_LIBRARY -> player?.availableCommands
                ?: Player.Commands.Builder().addAllCommands().build()
            TrustLevel.TRUSTED_TRANSPORT,
            TrustLevel.LIMITED -> transportPlayerCommands()
            TrustLevel.REJECTED -> error("Rejected controllers cannot receive player commands")
        }

        Log.d(
            TAG,
            "accept level=$level package=${controller.packageName} uid=${controller.uid} " +
                "sessionCommands=${if (canAccessLibrary(level)) "library" else "transport"} " +
                "playerCommands=${if (level == TrustLevel.SELF || level == TrustLevel.TRUSTED_LIBRARY) "full" else "transport"}"
        )

        return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
            .setAvailableSessionCommands(sessionCommands)
            .setAvailablePlayerCommands(playerCommands)
            .build()
    }

    companion object {
        private const val TAG = "VANTA_SESSION_TRUST"

        private val LIBRARY_PACKAGES = setOf(
            "com.google.android.projection.gearhead",
            "com.google.android.embedded.projection",
            "com.google.android.carassistant",
            "com.google.android.car.media",
            "com.google.android.apps.automotive",
            "com.google.android.autosimulator",
            "com.google.android.mediasimulator",
            "com.android.car.media",
        )

        private val TRANSPORT_PACKAGES = setOf(
            "com.android.systemui",
            "com.android.bluetooth",
            "com.google.android.googlequicksearchbox",
            "com.google.android.gms",
            "android",
        )

        fun canAccessLibrary(level: TrustLevel): Boolean =
            level == TrustLevel.SELF || level == TrustLevel.TRUSTED_LIBRARY

        internal fun transportPlayerCommands(): Player.Commands {
            return Player.Commands.Builder()
                .add(Player.COMMAND_PLAY_PAUSE)
                .add(Player.COMMAND_PREPARE)
                .add(Player.COMMAND_STOP)
                .add(Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM)
                .add(Player.COMMAND_SEEK_TO_DEFAULT_POSITION)
                .add(Player.COMMAND_SEEK_TO_PREVIOUS)
                .add(Player.COMMAND_SEEK_TO_NEXT)
                .add(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
                .add(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
                .add(Player.COMMAND_GET_CURRENT_MEDIA_ITEM)
                .add(Player.COMMAND_GET_TIMELINE)
                .add(Player.COMMAND_GET_METADATA)
                .add(Player.COMMAND_GET_AUDIO_ATTRIBUTES)
                .add(Player.COMMAND_GET_VOLUME)
                .add(Player.COMMAND_SET_VOLUME)
                .build()
        }
    }
}
