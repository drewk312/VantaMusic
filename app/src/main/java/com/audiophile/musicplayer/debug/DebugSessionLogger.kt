package com.audiophile.musicplayer.debug

import android.content.Context
import android.util.Log
import org.json.JSONObject

/**
 * Debug-session NDJSON logger — also mirrored into [VantaDiagnosticLog] for Settings.
 */
object DebugSessionLogger {
    private const val TAG = "VANTA_DEBUG_36efc8"
    private const val SESSION_ID = "36efc8"

    fun init(context: Context) {
        VantaDiagnosticLog.init(context)
    }

    fun log(
        hypothesisId: String,
        location: String,
        message: String,
        data: Map<String, Any?> = emptyMap(),
        runId: String = "pre-fix",
    ) {
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
        VantaDiagnosticLog.info(
            tag = "DebugSession",
            message = "$location — $message (${data.entries.joinToString { "${it.key}=${it.value}" }})"
        )
    }
}
