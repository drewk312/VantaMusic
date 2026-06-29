package com.audiophile.musicplayer.data.rpc

import android.util.Log
import com.google.gson.Gson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.CancellableContinuation
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicInteger

class RadioRpcClient(
    private val scope: CoroutineScope,
    private val gson: Gson = Gson(),
    private val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .pingInterval(30, TimeUnit.SECONDS)
        .build()
) {
    companion object {
        private const val TAG = "VANTA_RPC"
        private const val RECONNECT_DELAY_MS = 3000L
        private const val MAX_RECONNECT_DELAY_MS = 30_000L
        private const val RESPONSE_TIMEOUT_MS = 15_000L
    }

    private val requestId = AtomicInteger(1)
    private val pending = ConcurrentHashMap<Int, PendingRequest>()
    private var webSocket: WebSocket? = null
    private var connectUrl: String? = null
    private var reconnectJob: Job? = null
    private var closed = false
    private var consecutiveFailures = 0

    private class PendingRequest(val cont: CancellableContinuation<Any?>)

    fun connect(url: String) {
        connectUrl = url
        closed = false
        doConnect(url)
    }

    fun disconnect() {
        closed = true
        reconnectJob?.cancel()
        reconnectJob = null
        webSocket?.close(1000, "Client closing")
        webSocket = null
        val failures = pending.keys.toList()
        failures.forEach { id ->
            pending.remove(id)?.cont?.resumeWith(Result.failure(IllegalStateException("Disconnected")))
        }
    }

    @Suppress("UNCHECKED_CAST")
    suspend fun <T> call(method: String, params: Any? = null): T {
        val id = requestId.getAndIncrement()
        val request = JsonRpcRequest(id = id, method = method, params = params)
        val json = gson.toJson(request)

        return suspendCancellableCoroutine { cont ->
            pending[id] = PendingRequest(cont as CancellableContinuation<Any?>)

            val ws = webSocket
            if (ws != null && ws.send(json)) {
                scope.launch(Dispatchers.IO) {
                    delay(RESPONSE_TIMEOUT_MS)
                    pending.remove(id)?.let {
                        if (cont.isActive) {
                            cont.resumeWith(
                                Result.failure(TimeoutException("RPC $method($id) timed out"))
                            )
                        }
                    }
                }
            } else {
                pending.remove(id)
                cont.resumeWith(Result.failure(IllegalStateException("WebSocket not connected")))
            }
        }
    }

    private fun doConnect(url: String) {
        val request = Request.Builder().url(url).build()
        okHttpClient.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(ws: WebSocket, response: Response) {
                response.close()
                Log.i(TAG, "Connected to $url")
                webSocket = ws
                consecutiveFailures = 0
            }

            override fun onMessage(ws: WebSocket, text: String) {
                handleMessage(text)
            }

            override fun onClosing(ws: WebSocket, code: Int, reason: String) {
                ws.close(code, reason)
            }

            override fun onClosed(ws: WebSocket, code: Int, reason: String) {
                Log.d(TAG, "Disconnected code=$code reason=$reason")
                webSocket = null
                if (!closed) scheduleReconnect(url)
            }

            override fun onFailure(ws: WebSocket, t: Throwable, response: Response?) {
                Log.e(TAG, "WebSocket failure: ${t.message}", t)
                webSocket = null
                consecutiveFailures++
                if (!closed) scheduleReconnect(url)
            }
        })
    }

    private fun scheduleReconnect(url: String) {
        reconnectJob?.cancel()
        reconnectJob = scope.launch(Dispatchers.IO) {
            val delayMs = (RECONNECT_DELAY_MS * (1 shl (consecutiveFailures.coerceAtMost(4))))
                .coerceAtMost(MAX_RECONNECT_DELAY_MS)
            Log.i(TAG, "Reconnecting in ${delayMs}ms (attempt ${consecutiveFailures + 1})")
            delay(delayMs)
            if (!closed) doConnect(url)
        }
    }

    private fun handleMessage(text: String) {
        try {
            val base = gson.fromJson(text, Map::class.java)
            val id = (base["id"] as? Double)?.toInt() ?: return
            val pendingReq = pending.remove(id) ?: return

            if (base.containsKey("error")) {
                val err = base["error"] as? Map<*, *>
                val code = (err?.get("code") as? Double)?.toInt() ?: -1
                val msg = err?.get("message") as? String ?: "Unknown error"
                pendingReq.cont.resumeWith(Result.failure(RpcException(code, msg)))
                return
            }

            pendingReq.cont.resume(gson.fromJson(gson.toJson(base["result"]), Any::class.java) as Any) { }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse RPC response: ${e.message}", e)
        }
    }
}

class RpcException(val code: Int, msg: String) : Exception("RPC error $code: $msg")
