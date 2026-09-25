@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package com.audiophile.musicplayer.playback

import android.net.Uri
import android.util.Log
import androidx.media3.common.C
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.HttpDataSource
import androidx.media3.datasource.TransferListener
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.io.InputStream
import kotlin.math.min

/**
 * Transparent chunking DataSource for GoogleVideo CDN audio streams.
 *
 * Requests bounded 256 KB ranges on GoogleVideo URLs and delegates other URLs.
 * Range chunking does not establish full-stream availability: a server can accept
 * initial ranges and reject later ones. HTTP failures are propagated to the player.
 */
class GoogleVideoChunkingDataSource(
    private val upstream: DataSource,
    private val okHttpClient: OkHttpClient,
    private val headersProvider: (String) -> Map<String, String>,
) : DataSource {

    companion object {
        private const val TAG = "VANTA_CHUNK_DS"
        private const val CHUNK_SIZE = 256L * 1024L // 256 KB chunk window
        private const val DEFAULT_IOS_UA = "com.google.ios.youtube/21.02.3 (iPhone16,2; U; CPU iOS 18_3_2 like Mac OS X;)"
    }

    private var activeDataSpec: DataSpec? = null
    private var isGoogleVideo = false

    private var uri: Uri? = null
    private var totalLength: Long = C.LENGTH_UNSET.toLong()
    private var currentPosition: Long = 0L
    private var bytesRemaining: Long = C.LENGTH_UNSET.toLong()

    private var currentCall: Call? = null
    private var currentResponse: Response? = null
    private var currentInputStream: InputStream? = null
    private var bytesRemainingInChunk: Long = 0L

    override fun addTransferListener(transferListener: TransferListener) {
        upstream.addTransferListener(transferListener)
    }

    override fun open(dataSpec: DataSpec): Long {
        activeDataSpec = dataSpec
        val host = dataSpec.uri.host?.lowercase().orEmpty()
        isGoogleVideo = host.contains("googlevideo.com")

        if (!isGoogleVideo) {
            return upstream.open(dataSpec)
        }

        uri = dataSpec.uri
        currentPosition = dataSpec.position
        val requestedLength = dataSpec.length

        // Fetch initial chunk starting at requested position
        openChunk(currentPosition)

        val total = totalLength
        bytesRemaining = if (requestedLength != C.LENGTH_UNSET.toLong()) {
            requestedLength
        } else if (total != C.LENGTH_UNSET.toLong()) {
            (total - currentPosition).coerceAtLeast(0L)
        } else {
            C.LENGTH_UNSET.toLong()
        }

        Log.d(
            TAG,
            "open: GoogleVideo pos=$currentPosition reqLen=$requestedLength total=$total bytesRemaining=$bytesRemaining"
        )
        return bytesRemaining
    }

    private fun openChunk(start: Long) {
        closeCurrentChunk()

        val urlString = uri?.toString() ?: throw IOException("URI is null")
        val customHeaders = headersProvider(urlString)
        val end = if (totalLength != C.LENGTH_UNSET.toLong()) {
            min(start + CHUNK_SIZE - 1, totalLength - 1)
        } else {
            start + CHUNK_SIZE - 1
        }

        val rangeHeader = "bytes=$start-$end"
        val reqBuilder = Request.Builder()
            .url(urlString)
            .header("Range", rangeHeader)
            .header("User-Agent", customHeaders["User-Agent"] ?: DEFAULT_IOS_UA)

        customHeaders.forEach { (k, v) ->
            if (!k.equals("Range", ignoreCase = true) && !k.equals("Referer", ignoreCase = true)) {
                reqBuilder.header(k, v)
            }
        }

        val request = reqBuilder.build()
        val call = okHttpClient.newCall(request)
        currentCall = call

        val response = call.execute()
        currentResponse = response

        if (!response.isSuccessful && response.code != 206) {
            val code = response.code
            val msg = response.message
            response.close()
            throw HttpDataSource.InvalidResponseCodeException(
                code,
                msg,
                null,
                emptyMap(),
                activeDataSpec ?: DataSpec(uri ?: Uri.EMPTY),
                ByteArray(0)
            )
        }

        // Parse total length from Content-Range: bytes START-END/TOTAL
        val contentRange = response.header("Content-Range")
        if (contentRange != null && totalLength == C.LENGTH_UNSET.toLong()) {
            val slashIdx = contentRange.lastIndexOf('/')
            if (slashIdx != -1 && slashIdx < contentRange.length - 1) {
                val totalStr = contentRange.substring(slashIdx + 1).trim()
                totalLength = totalStr.toLongOrNull() ?: C.LENGTH_UNSET.toLong()
            }
        }

        val body = response.body ?: throw IOException("Empty response body for chunk $rangeHeader")
        currentInputStream = body.byteStream()
        bytesRemainingInChunk = body.contentLength().takeIf { it > 0 } ?: (end - start + 1)
    }

    private fun closeCurrentChunk() {
        try {
            currentInputStream?.close()
        } catch (_: Exception) {
        }
        currentInputStream = null
        try {
            currentResponse?.close()
        } catch (_: Exception) {
        }
        currentResponse = null
        currentCall = null
        bytesRemainingInChunk = 0L
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (!isGoogleVideo) {
            return upstream.read(buffer, offset, length)
        }

        if (length == 0) return 0
        if (bytesRemaining == 0L) return C.RESULT_END_OF_INPUT

        // If current chunk exhausted, open next chunk
        while (currentInputStream == null || bytesRemainingInChunk <= 0L) {
            if (totalLength != C.LENGTH_UNSET.toLong() && currentPosition >= totalLength) {
                return C.RESULT_END_OF_INPUT
            }
            openChunk(currentPosition)
        }

        val bytesToReadFromChunk = if (bytesRemaining != C.LENGTH_UNSET.toLong()) {
            min(length.toLong(), bytesRemaining).toInt()
        } else {
            length
        }

        val toRead = min(bytesToReadFromChunk.toLong(), bytesRemainingInChunk).toInt()
        val readBytes = currentInputStream?.read(buffer, offset, toRead) ?: -1
        if (readBytes == -1) {
            closeCurrentChunk()
            if (totalLength != C.LENGTH_UNSET.toLong() && currentPosition >= totalLength) {
                return C.RESULT_END_OF_INPUT
            }
            openChunk(currentPosition)
            return read(buffer, offset, length)
        }

        currentPosition += readBytes
        bytesRemainingInChunk -= readBytes
        if (bytesRemaining != C.LENGTH_UNSET.toLong()) {
            bytesRemaining = (bytesRemaining - readBytes).coerceAtLeast(0L)
        }
        return readBytes
    }

    override fun getUri(): Uri? {
        return if (isGoogleVideo) uri else upstream.uri
    }

    override fun getResponseHeaders(): Map<String, List<String>> {
        if (!isGoogleVideo) return upstream.responseHeaders
        val resp = currentResponse ?: return emptyMap()
        return resp.headers.toMultimap()
    }

    override fun close() {
        if (!isGoogleVideo) {
            upstream.close()
        } else {
            closeCurrentChunk()
            activeDataSpec = null
            uri = null
            totalLength = C.LENGTH_UNSET.toLong()
            currentPosition = 0L
            bytesRemaining = C.LENGTH_UNSET.toLong()
            isGoogleVideo = false
        }
    }
}
