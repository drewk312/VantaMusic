package com.audiophile.musicplayer.debug

import android.util.Log
import com.audiophile.musicplayer.BuildConfig
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** Debug-session logger for layout/nav investigation (session 80b070). Only active in debug builds. */
object Debug80b070 {
    private const val TAG = "VANTA_DEBUG_80b070"
    private const val SESSION_ID = "80b070"
    private const val ENDPOINT =
        "http://127.0.0.1:7503/ingest/66f05885-7219-477e-8f17-6014c2516924"

    fun log(
        hypothesisId: String,
        location: String,
        message: String,
        data: Map<String, Any?> = emptyMap(),
        runId: String = "pre-fix"
    ) {
        if (!BuildConfig.DEBUG) return
        val payload = JSONObject().apply {
            put("sessionId", SESSION_ID)
            put("runId", runId)
            put("hypothesisId", hypothesisId)
            put("location", location)
            put("message", message)
            put("timestamp", System.currentTimeMillis())
            put("data", JSONObject(data))
        }
        val line = payload.toString()
        Log.d(TAG, line)
        Thread {
            try {
                val conn = (URL(ENDPOINT).openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    setRequestProperty("Content-Type", "application/json")
                    setRequestProperty("X-Debug-Session-Id", SESSION_ID)
                    doOutput = true
                    connectTimeout = 1500
                    readTimeout = 1500
                }
                conn.outputStream.use { it.write(line.toByteArray(Charsets.UTF_8)) }
                conn.inputStream.close()
                conn.disconnect()
            } catch (_: Exception) {
            }
        }.start()
    }
}
