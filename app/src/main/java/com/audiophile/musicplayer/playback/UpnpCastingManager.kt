package com.audiophile.musicplayer.playback

import android.annotation.SuppressLint
import android.content.Context
import android.net.wifi.WifiManager
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.StringReader
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.NetworkInterface
import java.net.SocketTimeoutException
import java.net.URL
import kotlin.random.Random

@SuppressLint("StaticFieldLeak")
object UpnpCastingHolder {
    @Volatile
    var manager: UpnpCastingManager? = null
}

data class UpnpDevice(
    val id: String,
    val friendlyName: String,
    val ipAddress: String,
    val port: Int = 0,
    val location: String = "",
    val udn: String = "",
    val avTransportUrl: String? = null,
    val renderingControlUrl: String? = null
)

sealed interface CastingState {
    data object Idle : CastingState
    data object Discovering : CastingState
    data class Connected(val device: UpnpDevice) : CastingState
    data class Streaming(val device: UpnpDevice, val streamUrl: String) : CastingState
    data class Error(val message: String) : CastingState
}

/**
 * Handles discovery of DLNA/UPnP media renderers on the local network using
 * SSDP (Simple Service Discovery Protocol) and provides methods to cast
 * audio streams to them via SOAP AVTransport control.
 *
 * Discovery: SSDP M-SEARCH multicast to 239.255.255.250:1900
 * Control: SOAP over HTTP to the device's AVTransport control URL
 */
class UpnpCastingManager(
    private val context: Context
) {
    companion object {
        private const val TAG = "VANTA_UPNP"
        private const val SSDP_ADDR = "239.255.255.250"
        private const val SSDP_PORT = 1900
        private const val SSDP_SEARCH_TARGET = "urn:schemas-upnp-org:device:MediaRenderer:1"
        private const val DISCOVERY_TIMEOUT_MS = 3000L
        private const val SOAP_TIMEOUT_MS = 5000
        private const val XML_NAMESPACE_AVT = "urn:schemas-upnp-org:service:AVTransport:1"
        private const val XML_NAMESPACE_RCS = "urn:schemas-upnp-org:service:RenderingControl:1"
    }

    private val _availableDevices = MutableStateFlow<List<UpnpDevice>>(emptyList())
    val availableDevices: StateFlow<List<UpnpDevice>> = _availableDevices

    private val _castingState = MutableStateFlow<CastingState>(CastingState.Idle)
    val castingState: StateFlow<CastingState> = _castingState

    private var connectedDevice: UpnpDevice? = null
    private var wifiManager: WifiManager? = null
    private var multicastLock: WifiManager.MulticastLock? = null
    private var discoverySocket: DatagramSocket? = null
    private var isDiscovering = false

    init {
        try {
            wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
        } catch (e: Exception) {
            Log.w(TAG, "WifiManager not available: ${e.message}")
        }
    }

    /**
     * Starts SSDP discovery for UPnP Media Renderers on the local network.
     * Results are emitted via [availableDevices].
     */
    fun startDiscovery() {
        if (isDiscovering) {
            Log.d(TAG, "Discovery already in progress")
            return
        }
        isDiscovering = true
        _castingState.value = CastingState.Discovering
        _availableDevices.value = emptyList()

        // Acquire multicast lock so we receive SSDP responses
        try {
            multicastLock?.release()
            multicastLock = wifiManager?.createMulticastLock("vanta_upnp_discovery")
            multicastLock?.setReferenceCounted(false)
            multicastLock?.acquire()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to acquire multicast lock: ${e.message}")
        }

        kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch {
            try {
                performSsdpDiscovery()
            } catch (e: Exception) {
                Log.e(TAG, "SSDP discovery failed: ${e.message}")
                _castingState.value = CastingState.Error("Discovery failed: ${e.message}")
            } finally {
                isDiscovering = false
                releaseMulticastLock()
            }
        }
    }

    fun stopDiscovery() {
        isDiscovering = false
        try {
            discoverySocket?.close()
        } catch (_: Exception) {}
        discoverySocket = null
        releaseMulticastLock()
        _castingState.value = CastingState.Idle
    }

    fun connectToDevice(device: UpnpDevice) {
        connectedDevice = device
        _castingState.value = CastingState.Connected(device)
        Log.d(TAG, "Connected to device: ${device.friendlyName} (${device.ipAddress})")
    }

    /**
     * Casts the given stream URL to the specified DLNA/UPnP device using
     * SOAP AVTransport: SetAVTransportURI + Play.
     */
    fun castToDevice(device: UpnpDevice, streamUrl: String, metadata: String = "") {
        Log.d(TAG, "Casting to ${device.friendlyName}: $streamUrl")
        connectToDevice(device)

        kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch {
            try {
                val avUrl = resolveAvTransportUrl(device)
                if (avUrl == null) {
                    _castingState.value = CastingState.Error("AVTransport not available on ${device.friendlyName}")
                    return@launch
                }

                // Step 1: SetAVTransportURI
                val didlLite = buildDidlLite(metadata)
                val setUriSuccess = sendSoapAction(
                    controlUrl = avUrl,
                    serviceType = "AVTransport",
                    action = "SetAVTransportURI",
                    body = """
                        <?xml version="1.0"?>
                        <s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/"
                                    s:encodingStyle="http://schemas.xmlsoap.org/soap/encoding/">
                          <s:Body>
                            <u:SetAVTransportURI xmlns:u="$XML_NAMESPACE_AVT">
                              <InstanceID>0</InstanceID>
                              <CurrentURI>$streamUrl</CurrentURI>
                              <CurrentURIMetaData><![CDATA[$didlLite]]></CurrentURIMetaData>
                            </u:SetAVTransportURI>
                          </s:Body>
                        </s:Envelope>
                    """.trimIndent()
                )
                if (!setUriSuccess) {
                    _castingState.value = CastingState.Error("SetAVTransportURI failed on ${device.friendlyName}")
                    return@launch
                }

                // Step 2: Play
                val playSuccess = sendSoapAction(
                    controlUrl = avUrl,
                    serviceType = "AVTransport",
                    action = "Play",
                    body = """
                        <?xml version="1.0"?>
                        <s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/"
                                    s:encodingStyle="http://schemas.xmlsoap.org/soap/encoding/">
                          <s:Body>
                            <u:Play xmlns:u="$XML_NAMESPACE_AVT">
                              <InstanceID>0</InstanceID>
                              <Speed>1</Speed>
                            </u:Play>
                          </s:Body>
                        </s:Envelope>
                    """.trimIndent()
                )
                if (!playSuccess) {
                    _castingState.value = CastingState.Error("Play command failed on ${device.friendlyName}")
                    return@launch
                }

                _castingState.value = CastingState.Streaming(device, streamUrl)
                Log.d(TAG, "Successfully casting to ${device.friendlyName}")
            } catch (e: Exception) {
                Log.e(TAG, "Cast failed: ${e.message}", e)
                _castingState.value = CastingState.Error("Cast error: ${e.message}")
            }
        }
    }

    fun stopCasting() {
        val device = connectedDevice ?: run {
            _castingState.value = CastingState.Idle
            return
        }

        kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch {
            try {
                val avUrl = resolveAvTransportUrl(device)
                if (avUrl != null) {
                    sendSoapAction(
                        controlUrl = avUrl,
                        serviceType = "AVTransport",
                        action = "Stop",
                        body = """
                            <?xml version="1.0"?>
                            <s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/"
                                        s:encodingStyle="http://schemas.xmlsoap.org/soap/encoding/">
                              <s:Body>
                                <u:Stop xmlns:u="$XML_NAMESPACE_AVT">
                                  <InstanceID>0</InstanceID>
                                </u:Stop>
                              </s:Body>
                            </s:Envelope>
                        """.trimIndent()
                    )
                }
            } catch (e: Exception) {
                Log.w(TAG, "Stop failed: ${e.message}")
            }
            _castingState.value = CastingState.Connected(device)
        }
    }

    fun disconnect() {
        stopCasting()
        connectedDevice = null
        _castingState.value = CastingState.Idle
    }

    // ---------------------------------------------------------------
    // SSDP Discovery
    // ---------------------------------------------------------------

    private fun performSsdpDiscovery() {
        val localIp = getLocalIpAddress() ?: run {
            Log.w(TAG, "No WiFi IP address found — ensure WiFi is enabled")
            _castingState.value = CastingState.Error("No WiFi connection. Enable WiFi for UPnP discovery.")
            return
        }
        Log.d(TAG, "Local IP for SSDP: $localIp")

        val socket = DatagramSocket(null).apply {
            soTimeout = DISCOVERY_TIMEOUT_MS.toInt()
            reuseAddress = true
            bind(java.net.InetSocketAddress(localIp, 0))
        }
        discoverySocket = socket

        val searchMessage = buildMSearch()
        val searchPacket = DatagramPacket(
            searchMessage.toByteArray(),
            searchMessage.length,
            InetAddress.getByName(SSDP_ADDR),
            SSDP_PORT
        )

        val seenUdns = mutableSetOf<String>()
        val startTime = System.currentTimeMillis()

        try {
            // Send M-SEARCH 3 times for reliability
            repeat(3) {
                try {
                    socket.send(searchPacket)
                } catch (e: Exception) {
                    Log.w(TAG, "M-SEARCH send #$it failed: ${e.message}")
                }
                Thread.sleep(200)
            }

            // Collect responses
            val devices = mutableListOf<UpnpDevice>()
            while (System.currentTimeMillis() - startTime < DISCOVERY_TIMEOUT_MS && isDiscovering) {
                try {
                    val buffer = ByteArray(4096)
                    val response = DatagramPacket(buffer, buffer.size)
                    socket.receive(response)

                    val data = String(response.data, 0, response.length, Charsets.UTF_8)
                    val location = parseSsdpHeader(data, "LOCATION")
                        ?: parseSsdpHeader(data, "Location")
                    val st = parseSsdpHeader(data, "ST")
                        ?: parseSsdpHeader(data, "st")
                    val usn = parseSsdpHeader(data, "USN")
                        ?: parseSsdpHeader(data, "usn")

                    if (location != null && (st?.contains("MediaRenderer") == true || usn?.contains("MediaRenderer") == true)) {
                        val udn = usn?.substringBefore("::") ?: location.hashCode().toString()
                        if (udn !in seenUdns) {
                            seenUdns.add(udn)
                            Log.d(TAG, "Found Media Renderer: location=$location st=$st usn=$usn")
                            // Fetch device description to get friendly name and AVTransport URL
                            val device = fetchDeviceDescription(location, udn)
                            if (device != null) {
                                devices.add(device)
                            }
                        }
                    }
                } catch (e: SocketTimeoutException) {
                    // Timeout reached — done collecting
                    break
                } catch (e: Exception) {
                    if (isDiscovering) {
                        Log.w(TAG, "SSDP receive error: ${e.message}")
                    }
                }
            }

            if (devices.isNotEmpty()) {
                _availableDevices.value = devices
                Log.d(TAG, "Discovered ${devices.size} UPnP device(s)")
            } else {
                Log.d(TAG, "No UPnP Media Renderers found on network")
                // Don't change state — just leave availableDevices empty
            }

            if (_castingState.value is CastingState.Discovering) {
                _castingState.value = CastingState.Idle
            }
        } finally {
            socket.close()
            discoverySocket = null
        }
    }

    private fun buildMSearch(): String {
        val uuid = Random.nextLong().toString(16).take(8)
        return """
M-SEARCH * HTTP/1.1
HOST: $SSDP_ADDR:$SSDP_PORT
MAN: "ssdp:discover"
MX: 3
ST: $SSDP_SEARCH_TARGET
USER-AGENT: VANTA/1.0 UPnP/1.0 Android/$uuid

        """.trimIndent().replace("\n", "\r\n")
    }

    private fun parseSsdpHeader(response: String, header: String): String? {
        return response.lines().firstOrNull { line ->
            line.trimStart().startsWith(header, ignoreCase = true)
        }?.substringAfter(":")?.trim()
    }

    // ---------------------------------------------------------------
    // Device Description Parsing
    // ---------------------------------------------------------------

    private fun fetchDeviceDescription(location: String, udn: String): UpnpDevice? {
        return try {
            val url = URL(location)
            val connection = url.openConnection() as HttpURLConnection
            connection.connectTimeout = SOAP_TIMEOUT_MS
            connection.readTimeout = SOAP_TIMEOUT_MS
            connection.requestMethod = "GET"

            val xml = connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            connection.disconnect()

            parseDeviceXml(xml, url, udn)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to fetch device description from $location: ${e.message}")
            null
        }
    }

    private fun parseDeviceXml(xml: String, baseUrl: URL, udn: String): UpnpDevice? {
        return try {
            val factory = XmlPullParserFactory.newInstance()
            val parser = factory.newPullParser()
            parser.setInput(StringReader(xml))

            var friendlyName = "Unknown"
            var avTransportUrl: String? = null
            var renderingControlUrl: String? = null
            var insideDevice = false
            var insideService = false
            var currentServiceType = ""
            var currentControlUrl = ""
            var eventDepth = 0

            while (parser.eventType != XmlPullParser.END_DOCUMENT) {
                when (parser.eventType) {
                    XmlPullParser.START_TAG -> {
                        when (parser.name) {
                            "device" -> insideDevice = true
                            "service" -> if (insideDevice) {
                                insideService = true
                                currentServiceType = ""
                                currentControlUrl = ""
                            }
                        }
                        eventDepth++
                    }
                    XmlPullParser.TEXT -> {
                        val text = parser.text?.trim() ?: ""
                        if (insideDevice && !insideService && parser.name == "friendlyName") {
                            friendlyName = text.ifBlank { "Unknown" }
                        }
                        if (insideService) {
                            when (parser.name) {
                                "serviceType" -> currentServiceType = text
                                "controlURL" -> currentControlUrl = text
                            }
                        }
                    }
                    XmlPullParser.END_TAG -> {
                        when (parser.name) {
                            "service" -> {
                                if (insideService) {
                                    val resolvedUrl = resolveUrl(baseUrl, currentControlUrl)
                                    when {
                                        currentServiceType.contains("AVTransport") -> avTransportUrl = resolvedUrl
                                        currentServiceType.contains("RenderingControl") -> renderingControlUrl = resolvedUrl
                                    }
                                    insideService = false
                                }
                            }
                            "device" -> insideDevice = false
                        }
                        eventDepth--
                    }
                }
                parser.next()
            }

            val deviceId = udn.ifBlank { "upnp_${friendlyName.lowercase().replace(" ", "_")}" }
            UpnpDevice(
                id = deviceId,
                friendlyName = friendlyName,
                ipAddress = baseUrl.host ?: "",
                port = baseUrl.port.coerceAtLeast(0),
                location = baseUrl.toString(),
                udn = udn,
                avTransportUrl = avTransportUrl,
                renderingControlUrl = renderingControlUrl
            )
        } catch (e: Exception) {
            Log.w(TAG, "XML parsing failed: ${e.message}")
            null
        }
    }

    private fun resolveUrl(baseUrl: URL, controlUrl: String): String? {
        if (controlUrl.isBlank()) return null
        return if (controlUrl.startsWith("http")) {
            controlUrl
        } else if (controlUrl.startsWith("/")) {
            "${baseUrl.protocol}://${baseUrl.host}:${baseUrl.port}$controlUrl"
        } else {
            val base = baseUrl.toString().substringBeforeLast("/")
            "$base/$controlUrl"
        }
    }

    // ---------------------------------------------------------------
    // SOAP AVTransport Control
    // ---------------------------------------------------------------

    private fun sendSoapAction(
        controlUrl: String,
        serviceType: String,
        action: String,
        body: String
    ): Boolean {
        return try {
            val url = URL(controlUrl)
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "POST"
            connection.doOutput = true
            connection.connectTimeout = SOAP_TIMEOUT_MS
            connection.readTimeout = SOAP_TIMEOUT_MS
            connection.setRequestProperty("Content-Type", "text/xml; charset=utf-8")
            connection.setRequestProperty("SOAPAction", "\"urn:schemas-upnp-org:service:$serviceType:1#$action\"")

            val outputBytes = body.toByteArray(Charsets.UTF_8)
            connection.setRequestProperty("Content-Length", outputBytes.size.toString())
            connection.outputStream.buffered().use { it.write(outputBytes); it.flush() }

            val responseCode = connection.responseCode
            val responseBody = if (responseCode in 200..299) {
                connection.inputStream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: ""
            } else {
                connection.errorStream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: ""
            }
            connection.disconnect()

            if (responseCode in 200..299) {
                Log.d(TAG, "SOAP $action success on $serviceType (HTTP $responseCode)")
                true
            } else {
                Log.w(TAG, "SOAP $action failed on $serviceType: HTTP $responseCode — $responseBody")
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "SOAP $action error: ${e.message}")
            false
        }
    }

    // ---------------------------------------------------------------
    // DIDL-Lite Metadata Builder
    // ---------------------------------------------------------------

    private fun buildDidlLite(metadata: String): String {
        if (metadata.isBlank()) {
            return """<DIDL-Lite xmlns:dc="http://purl.org/dc/elements/1.1/"
                                 xmlns:upnp="urn:schemas-upnp-org:metadata-1-0/upnp/"
                                 xmlns="urn:schemas-upnp-org:metadata-1-0/DIDL-Lite/"
                                 xmlns:dlna="urn:schemas-dlna-org:metadata-1-0/">
                        <item id="0" parentID="-1" restricted="true">
                            <dc:title>VANTA Stream</dc:title>
                            <upnp:class>object.item.audioItem.musicTrack</upnp:class>
                        </item>
                      </DIDL-Lite>"""
        }
        return metadata
    }

    // ---------------------------------------------------------------
    // Utilities
    // ---------------------------------------------------------------

    private fun resolveAvTransportUrl(device: UpnpDevice): String? {
        return device.avTransportUrl ?: run {
            // If we don't have the AVTransport URL cached, fetch the description again
            if (device.location.isNotBlank()) {
                val fetched = fetchDeviceDescription(device.location, device.udn)
                fetched?.avTransportUrl
            } else null
        }
    }

    private fun getLocalIpAddress(): InetAddress? {
        return try {
            NetworkInterface.getNetworkInterfaces()?.asSequence()
                ?.filter { it.isUp && !it.isLoopback && it.name.startsWith("wlan") }
                ?.flatMap { it.inetAddresses.asSequence() }
                ?.firstOrNull { it is java.net.Inet4Address }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to get local IP: ${e.message}")
            null
        }
    }

    private fun releaseMulticastLock() {
        try {
            if (multicastLock?.isHeld == true) {
                multicastLock?.release()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to release multicast lock: ${e.message}")
        }
    }
}
