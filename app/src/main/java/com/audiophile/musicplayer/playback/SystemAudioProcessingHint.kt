package com.audiophile.musicplayer.playback

import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.core.content.edit

/**
 * Samsung (and some OEM) system Dolby Atmos / Adapt Sound re-processes app audio.
 * That doubles spatial processing with VANTA Immersive and clips phone speakers.
 */
object SystemAudioProcessingHint {
    private const val PREFS = "vanta_tips"
    private const val KEY_SAMSUNG_ATMOS_DISMISSED = "samsung_atmos_tip_dismissed"

    fun isSamsungDevice(): Boolean =
        Build.MANUFACTURER.equals("samsung", ignoreCase = true) ||
            Build.BRAND.equals("samsung", ignoreCase = true)

    fun shouldShowSamsungAtmosTip(context: Context): Boolean {
        if (!isSamsungDevice()) return false
        return !context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_SAMSUNG_ATMOS_DISMISSED, false)
    }

    fun dismissSamsungAtmosTip(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit { putBoolean(KEY_SAMSUNG_ATMOS_DISMISSED, true) }
    }

    /** Best-effort deep link into the system sound panel (OEM panels vary). */
    fun openSystemSoundSettings(context: Context): Boolean {
        val candidates = listOf(
            Intent("com.samsung.intent.action.SOUND_SETTING"),
            Intent("android.settings.SOUND_SETTINGS"),
            Intent(Settings.ACTION_SOUND_SETTINGS),
            Intent(Settings.ACTION_SETTINGS)
        )
        for (intent in candidates) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            val resolved = intent.resolveActivity(context.packageManager)
            if (resolved != null) {
                runCatching { context.startActivity(intent) }
                    .onSuccess { return true }
            }
        }
        return false
    }
}
