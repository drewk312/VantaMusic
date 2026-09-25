@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package com.audiophile.musicplayer.playback

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.util.Log
import androidx.core.content.edit
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink

/**
 * System output routing for the live sinking. Enumerates the platform's output devices
 * (phone speaker, wired/BT headphones, USB DAC, HDMI...), persists the user's pick, and
 * applies it to the current [DefaultAudioSink] via setPreferredDevice. Media3 1.10.0 has
 * no OutputSwitcher API, so this surfaces the platform routes directly.
 */
object OutputSwitchController {
    private const val PREFS_NAME = "vanta_output"
    private const val KEY_SELECTED = "selected_output_key"

    const val LOG_TAG = "VANTA_OUTPUT"

    data class OutputDevice(
        val key: String,
        val type: Int,
        val id: Int,
        val label: String,
        val address: String? = null,
        val isBluetooth: Boolean = false,
        val isUsb: Boolean = false,
        val isBle: Boolean = false
    )

    @Volatile
    private var deviceCallback: android.media.AudioDeviceCallback? = null

    fun listOutputs(context: Context): List<OutputDevice> {
        if (Build.VERSION.SDK_INT < 23) return emptyList()
        return runCatching {
            val raw = context.getSystemService(AudioManager::class.java)
                .getDevices(AudioManager.GET_DEVICES_OUTPUTS)
                .map { toOutputDevice(it) }
            curatedMusicOutputs(raw)
        }.getOrElse {
            Log.w(LOG_TAG, "listOutputs failed", it)
            emptyList()
        }
    }

    /**
     * Pixel Fold and similar devices report the phone 3–4 times (speaker,
     * speaker-safe, earpiece, inner/outer). Collapse those into one music
     * route and hide call-only hardware.
     */
    internal fun curatedMusicOutputs(devices: List<OutputDevice>): List<OutputDevice> {
        val visible = devices.filter { shouldShowForMusic(it.type) }
        val speakers = visible.filter { it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER }
        val withoutDuplicateSpeakers = if (speakers.size <= 1) {
            visible
        } else {
            val keeper = speakers.first().copy(label = "Phone speaker", address = null)
            listOf(keeper) + visible.filterNot { it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER }
        }
        val bluetoothKept = mutableSetOf<String>()
        val result = mutableListOf<OutputDevice>()
        val bluetoothSorted = withoutDuplicateSpeakers.sortedBy { bluetoothPriority(it.type) }
        for (device in bluetoothSorted) {
            if (device.isBluetooth) {
                val group = device.address?.takeIf { it.isNotBlank() && it != "topology" }
                    ?: device.label.trim().lowercase()
                if (!bluetoothKept.add(group)) continue
            }
            result.add(
                if (device.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER) {
                    device.copy(label = "Phone speaker", address = null)
                } else {
                    device
                }
            )
        }
        return result.sortedBy { displayOrder(it.type) }
    }

    private fun shouldShowForMusic(type: Int): Boolean = type !in HIDDEN_OUTPUT_TYPES

    private fun bluetoothPriority(type: Int): Int = when (type) {
        AudioDeviceInfo.TYPE_BLUETOOTH_A2DP -> 0
        AudioDeviceInfo.TYPE_BLE_HEADSET, AudioDeviceInfo.TYPE_BLE_SPEAKER -> 1
        AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> 2
        else -> 3
    }

    private fun displayOrder(type: Int): Int = when (type) {
        AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> 0
        AudioDeviceInfo.TYPE_WIRED_HEADSET, AudioDeviceInfo.TYPE_WIRED_HEADPHONES -> 1
        AudioDeviceInfo.TYPE_BLUETOOTH_A2DP, AudioDeviceInfo.TYPE_BLE_HEADSET, AudioDeviceInfo.TYPE_BLE_SPEAKER -> 2
        AudioDeviceInfo.TYPE_USB_DEVICE, AudioDeviceInfo.TYPE_USB_HEADSET, AudioDeviceInfo.TYPE_USB_ACCESSORY -> 3
        AudioDeviceInfo.TYPE_HDMI, AudioDeviceInfo.TYPE_HDMI_ARC -> 4
        else -> 5
    }

    private val HIDDEN_OUTPUT_TYPES = setOf(
        AudioDeviceInfo.TYPE_BUILTIN_EARPIECE,
        AudioDeviceInfo.TYPE_TELEPHONY,
        24, // TYPE_BUILTIN_SPEAKER_SAFE
        25, // TYPE_REMOTE_SUBMIX
        AudioDeviceInfo.TYPE_FM,
        AudioDeviceInfo.TYPE_FM_TUNER,
        AudioDeviceInfo.TYPE_UNKNOWN,
        AudioDeviceInfo.TYPE_IP,
        AudioDeviceInfo.TYPE_BUS
    )

    fun selectedDeviceKey(context: Context): String? =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_SELECTED, null)

    fun selectedDevice(context: Context): OutputDevice? {
        val key = selectedDeviceKey(context) ?: return null
        return listOutputs(context).firstOrNull { it.key == key }
    }

    /**
     * True when music is (or will be) going out the phone speakers — no wired,
     * USB, or Bluetooth headphones attached, or the user explicitly picked speaker.
     */
    fun isBuiltInSpeakerRoute(context: Context): Boolean {
        val preferred = selectedDevice(context)
        if (preferred != null) {
            return preferred.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER
        }
        if (Build.VERSION.SDK_INT < 23) return true
        return runCatching {
            val devices = context.getSystemService(AudioManager::class.java)
                .getDevices(AudioManager.GET_DEVICES_OUTPUTS)
            val hasExternal = devices.any { info ->
                info.type in BLUETOOTH_TYPES ||
                    info.type in USB_TYPES ||
                    info.type == AudioDeviceInfo.TYPE_WIRED_HEADSET ||
                    info.type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES ||
                    info.type == AudioDeviceInfo.TYPE_HDMI ||
                    info.type == AudioDeviceInfo.TYPE_HDMI_ARC
            }
            !hasExternal
        }.getOrDefault(true)
    }

    /** True when the user picked a USB DAC/headset, or USB is the only external path. */
    fun isUsbDacRoute(context: Context): Boolean {
        val preferred = selectedDevice(context)
        if (preferred != null) return preferred.isUsb
        if (Build.VERSION.SDK_INT < 23) return false
        return runCatching {
            val devices = context.getSystemService(AudioManager::class.java)
                .getDevices(AudioManager.GET_DEVICES_OUTPUTS)
            val usb = devices.any { it.type in USB_TYPES }
            val headphones = devices.any {
                it.type == AudioDeviceInfo.TYPE_WIRED_HEADSET ||
                    it.type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES ||
                    it.type in BLUETOOTH_TYPES
            }
            usb && !headphones
        }.getOrDefault(false)
    }

    fun select(context: Context, device: OutputDevice) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (prefs.getString(KEY_SELECTED, null) == device.key) {
            applySelection(context, AudioSinkHolder.primarySink)
            return
        }
        prefs.edit { putString(KEY_SELECTED, device.key) }
        Log.d(LOG_TAG, "select key=${device.key} label=${device.label}")
        applySelection(context, AudioSinkHolder.primarySink)
    }

    fun clearSelection(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (prefs.getString(KEY_SELECTED, null) == null) return
        prefs.edit { remove(KEY_SELECTED) }
        Log.d(LOG_TAG, "selection cleared")
        applySelection(context, AudioSinkHolder.primarySink)
    }

    /** Apply the stored selection to the current sink (no-op when no sink is built yet). */
    fun applySelection(context: Context, sink: AudioSink?) {
        val defaultSink = sink as? DefaultAudioSink ?: return
        val key = selectedDeviceKey(context) ?: return
        val info = findDeviceInfo(context, key) ?: return
        runCatching {
            defaultSink.setPreferredDevice(info)
        }.onSuccess {
            Log.d(LOG_TAG, "applied key=$key to sink result=$it")
        }.onFailure {
            Log.w(LOG_TAG, "setPreferredDevice failed", it)
        }
    }

    /** Re-apply the stored selection whenever a matching output appears (BT reconnects, USB DAC hot-plug). */
    fun startWatching(context: Context, onRouteChanged: (() -> Unit)? = null) {
        if (Build.VERSION.SDK_INT < 23 || deviceCallback != null) return
        val callback = object : android.media.AudioDeviceCallback() {
            override fun onAudioDevicesAdded(addedDevices: Array<AudioDeviceInfo>) {
                Log.d(LOG_TAG, "devices added=${addedDevices.map { deviceLabel(it) }}")
                applySelection(context, AudioSinkHolder.primarySink)
                onRouteChanged?.invoke()
            }

            override fun onAudioDevicesRemoved(removedDevices: Array<AudioDeviceInfo>) {
                Log.d(LOG_TAG, "devices removed=${removedDevices.map { deviceLabel(it) }}")
                applySelection(context, AudioSinkHolder.primarySink)
                onRouteChanged?.invoke()
            }
        }
        runCatching {
            context.getSystemService(AudioManager::class.java)
                .registerAudioDeviceCallback(callback, null)
            deviceCallback = callback
            Log.d(LOG_TAG, "startWatching registered")
        }.onFailure {
            Log.w(LOG_TAG, "registerAudioDeviceCallback failed", it)
        }
    }

    fun stopWatching(context: Context) {
        val callback = deviceCallback ?: return
        runCatching {
            context.getSystemService(AudioManager::class.java)
                .unregisterAudioDeviceCallback(callback)
        }
        deviceCallback = null
        Log.d(LOG_TAG, "stopWatching")
    }

    private fun findDeviceInfo(context: Context, key: String): AudioDeviceInfo? {
        val devices = runCatching {
            context.getSystemService(AudioManager::class.java)
                .getDevices(AudioManager.GET_DEVICES_OUTPUTS)
        }.getOrNull() ?: return null
        return devices.firstOrNull { deviceKey(it) == key }
    }

    private fun toOutputDevice(info: AudioDeviceInfo): OutputDevice {
        val name = runCatching { info.productName?.toString() }.getOrNull()
        val address = runCatching { info.address }.getOrNull()
        val builtin = info.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER ||
            info.type == AudioDeviceInfo.TYPE_BUILTIN_EARPIECE ||
            info.type == 24
        return OutputDevice(
            key = deviceKey(info),
            type = info.type,
            id = info.id,
            label = if (builtin) fallbackLabel(info.type) else (name?.takeIf { it.isNotBlank() } ?: fallbackLabel(info.type)),
            address = address?.takeIf { it.isNotBlank() },
            isBluetooth = info.type in BLUETOOTH_TYPES,
            isUsb = info.type in USB_TYPES,
            isBle = info.type == AudioDeviceInfo.TYPE_BLE_HEADSET ||
                info.type == AudioDeviceInfo.TYPE_BLE_SPEAKER
        )
    }

    private fun deviceKey(info: AudioDeviceInfo): String {
        val name = runCatching { info.productName?.toString() }.getOrNull().orEmpty()
        val address = runCatching { info.address }.getOrNull().orEmpty()
        return "type=${info.type}|id=${info.id}|name=$name|addr=$address"
    }

    private fun deviceLabel(info: AudioDeviceInfo): String {
        val name = runCatching { info.productName?.toString() }.getOrNull()
        return name?.takeIf { it.isNotBlank() } ?: fallbackLabel(info.type)
    }

    private val BLUETOOTH_TYPES = setOf(
        AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
        AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
        AudioDeviceInfo.TYPE_BLE_HEADSET,
        AudioDeviceInfo.TYPE_BLE_SPEAKER
    )

    private val USB_TYPES = setOf(
        AudioDeviceInfo.TYPE_USB_DEVICE,
        AudioDeviceInfo.TYPE_USB_HEADSET,
        AudioDeviceInfo.TYPE_USB_ACCESSORY
    )

    private fun fallbackLabel(type: Int): String = when (type) {
        AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> "Phone speaker"
        AudioDeviceInfo.TYPE_BUILTIN_EARPIECE -> "Phone earpiece"
        AudioDeviceInfo.TYPE_WIRED_HEADSET -> "Wired headset"
        AudioDeviceInfo.TYPE_WIRED_HEADPHONES -> "Wired headphones"
        AudioDeviceInfo.TYPE_BLUETOOTH_A2DP -> "Bluetooth headphones"
        AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> "Bluetooth hands-free"
        AudioDeviceInfo.TYPE_BLE_HEADSET -> "Bluetooth LE headset"
        AudioDeviceInfo.TYPE_BLE_SPEAKER -> "Bluetooth LE speaker"
        AudioDeviceInfo.TYPE_USB_DEVICE -> "USB audio"
        AudioDeviceInfo.TYPE_USB_HEADSET -> "USB headset"
        AudioDeviceInfo.TYPE_USB_ACCESSORY -> "USB audio accessory"
        AudioDeviceInfo.TYPE_DOCK -> "Dock"
        AudioDeviceInfo.TYPE_HDMI -> "HDMI"
        AudioDeviceInfo.TYPE_LINE_ANALOG -> "Line out (analog)"
        AudioDeviceInfo.TYPE_LINE_DIGITAL -> "Line out (digital)"
        AudioDeviceInfo.TYPE_HDMI_ARC -> "HDMI ARC"
        AudioDeviceInfo.TYPE_HEARING_AID -> "Hearing aid"
        else -> "Audio output"
    }
}