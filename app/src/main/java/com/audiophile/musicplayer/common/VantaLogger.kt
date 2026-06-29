package com.audiophile.musicplayer.common

import android.util.Log

/**
 * Centralized logging tag for VANTA. All production code should use this
 * instead of raw Log.d("RANDOM_TAG", ...) so filtering and crash reporting
 * stay consistent.
 */
object VantaLogger {
    private const val TAG = "VANTA"

    fun v(subTag: String, message: String) = Log.v("$TAG.$subTag", message)
    fun d(subTag: String, message: String) = Log.d("$TAG.$subTag", message)
    fun i(subTag: String, message: String) = Log.i("$TAG.$subTag", message)
    fun w(subTag: String, message: String, throwable: Throwable? = null) =
        if (throwable == null) Log.w("$TAG.$subTag", message) else Log.w("$TAG.$subTag", message, throwable)
    fun e(subTag: String, message: String, throwable: Throwable? = null) =
        if (throwable == null) Log.e("$TAG.$subTag", message) else Log.e("$TAG.$subTag", message, throwable)
}
