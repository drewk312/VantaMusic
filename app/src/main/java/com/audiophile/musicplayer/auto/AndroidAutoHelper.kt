package com.audiophile.musicplayer.auto

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.util.Log
import com.audiophile.musicplayer.playback.PlaybackService

object AndroidAutoHelper {
    private const val TAG = "VANTA_ANDROID_AUTO"
    const val GEARHEAD_PACKAGE = "com.google.android.projection.gearhead"

    data class Readiness(
        val gearheadInstalled: Boolean,
        val serviceDeclared: Boolean,
        val automotiveMetaDeclared: Boolean,
        val playbackServiceWarm: Boolean
    ) {
        val codeReady: Boolean get() = serviceDeclared && automotiveMetaDeclared
    }

    fun warmUpPlaybackService(context: Context) {
        try {
            context.startForegroundService(Intent(context, PlaybackService::class.java))
            Log.d(TAG, "PlaybackService warm-up requested")
        } catch (e: Exception) {
            try {
                context.startService(Intent(context, PlaybackService::class.java))
                Log.d(TAG, "PlaybackService warm-up via startService")
            } catch (inner: Exception) {
                Log.e(TAG, "PlaybackService warm-up failed", inner)
            }
        }
    }

    fun isGearheadInstalled(context: Context): Boolean =
        isPackageInstalled(context, GEARHEAD_PACKAGE)

    fun openAndroidAutoApp(context: Context): Boolean {
        val launch = context.packageManager.getLaunchIntentForPackage(GEARHEAD_PACKAGE) ?: return false
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(launch)
        return true
    }

    fun checkReadiness(context: Context): Readiness {
        val pm = context.packageManager
        val gearheadInstalled = isGearheadInstalled(context)
        var serviceDeclared = false
        var automotiveMetaDeclared = false
        try {
            val appInfo = pm.getApplicationInfo(context.packageName, PackageManager.GET_META_DATA)
            automotiveMetaDeclared = appInfo.metaData
                ?.containsKey("com.google.android.gms.car.application") == true
            val serviceInfo = pm.getServiceInfo(
                android.content.ComponentName(context, PlaybackService::class.java),
                PackageManager.GET_META_DATA
            )
            serviceDeclared = serviceInfo.exported
        } catch (e: Exception) {
            Log.w(TAG, "readiness check failed", e)
        }
        return Readiness(
            gearheadInstalled = gearheadInstalled,
            serviceDeclared = serviceDeclared,
            automotiveMetaDeclared = automotiveMetaDeclared,
            playbackServiceWarm = true
        )
    }

    fun setupSteps(): List<String> = listOf(
        "Open the Android Auto app on your phone (not only when plugged in).",
        "Menu → Settings → tap Version 10 times to enable Developer mode.",
        "Turn on Unknown sources (required for sideloaded / debug VANTA).",
        "Open Customize launcher and enable VANTA Music.",
        "Plug in USB or connect wireless Android Auto, then open Media on the car screen."
    )

    private fun isPackageInstalled(context: Context, packageName: String): Boolean =
        try {
            context.packageManager.getPackageInfo(packageName, 0)
            true
        } catch (_: PackageManager.NameNotFoundException) {
            false
        }
}
