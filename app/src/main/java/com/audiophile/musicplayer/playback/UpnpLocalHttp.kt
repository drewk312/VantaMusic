package com.audiophile.musicplayer.playback

import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URL

/** HTTP is part of UPnP. Confine it to LAN addresses without permitting public cleartext traffic. */
internal object UpnpLocalHttp {
    data class Response(val status: Int, val body: String)
    fun request(url: URL, body: String? = null, soapAction: String? = null): Response {
        require(url.protocol == "http" && url.userInfo == null) { "UPnP requires a local HTTP endpoint" }
        val address = InetAddress.getByName(url.host)
        require((address.isSiteLocalAddress || address.isLinkLocalAddress) && !address.isLoopbackAddress) { "UPnP endpoint must be on the LAN" }
        val path = url.file.ifBlank { "/" }
        require(!path.contains('\r') && !path.contains('\n'))
        require(soapAction == null || (!soapAction.contains('\r') && !soapAction.contains('\n')))
        val bytes = body?.toByteArray(Charsets.UTF_8) ?: byteArrayOf()
        return Socket().use { socket ->
            socket.connect(InetSocketAddress(address, if (url.port > 0) url.port else 80), 5000)
            socket.soTimeout = 5000
            val headers = buildString {
                append(if (body == null) "GET" else "POST").append(" $path HTTP/1.1\r\n")
                append("Host: ${url.authority}\r\nConnection: close\r\n")
                if (body != null) append("Content-Type: text/xml; charset=utf-8\r\nContent-Length: ${bytes.size}\r\n")
                if (soapAction != null) append("SOAPAction: \"$soapAction\"\r\n")
                append("\r\n")
            }
            socket.getOutputStream().apply { write(headers.toByteArray(Charsets.UTF_8)); write(bytes); flush() }
            val input = socket.getInputStream().buffered()
            fun line(): String {
                val buffer = java.io.ByteArrayOutputStream()
                while (true) {
                    val value = input.read()
                    check(value >= 0) { "Incomplete UPnP response" }
                    if (value == 10) break
                    check(buffer.size() < 8192) { "UPnP header too large" }
                    if (value != 13) buffer.write(value)
                }
                return buffer.toString("UTF-8")
            }
            val status = line().split(' ').getOrNull(1)?.toIntOrNull() ?: error("Invalid HTTP response")
            val responseHeaders = mutableMapOf<String, String>()
            var headerBytes = 0
            while (true) {
                val header = line()
                if (header.isEmpty()) break
                headerBytes += header.length
                check(headerBytes <= 32768)
                responseHeaders[header.substringBefore(':').lowercase()] = header.substringAfter(':').trim()
            }
            val output = java.io.ByteArrayOutputStream()
            fun readBytes(count: Int) {
                check(count >= 0 && output.size() + count <= 1_048_576) { "UPnP response too large" }
                var remaining = count
                val chunk = ByteArray(8192)
                while (remaining > 0) {
                    val read = input.read(chunk, 0, minOf(chunk.size, remaining))
                    check(read > 0) { "Truncated UPnP response" }
                    output.write(chunk, 0, read); remaining -= read
                }
            }
            if (responseHeaders["transfer-encoding"]?.contains("chunked", true) == true) {
                while (true) {
                    val size = line().substringBefore(';').trim().toInt(16)
                    if (size == 0) break
                    readBytes(size)
                    check(line().isEmpty())
                }
            } else {
                val length = responseHeaders["content-length"]?.toInt()
                if (length != null) readBytes(length) else {
                    val chunk = ByteArray(8192)
                    while (true) {
                        val read = input.read(chunk)
                        if (read < 0) break
                        check(output.size() + read <= 1_048_576)
                        output.write(chunk, 0, read)
                    }
                }
            }
            Response(status, output.toString("UTF-8"))
        }
    }
}
