package com.audiophile.musicplayer.playback

import android.media.AudioDeviceInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OutputSwitchControllerTest {

    private fun device(
        type: Int,
        label: String,
        id: Int = type,
        address: String? = null,
        isBluetooth: Boolean = false
    ) = OutputSwitchController.OutputDevice(
        key = "type=$type|id=$id|name=$label|addr=${address.orEmpty()}",
        type = type,
        id = id,
        label = label,
        address = address,
        isBluetooth = isBluetooth
    )

    @Test
    fun collapsesFoldPhoneSpeakerCopiesAndHidesEarpiece() {
        val outputs = OutputSwitchController.curatedMusicOutputs(
            listOf(
                device(AudioDeviceInfo.TYPE_BUILTIN_SPEAKER, "Pixel 10 Pro Fold", id = 1),
                device(AudioDeviceInfo.TYPE_BUILTIN_SPEAKER, "Pixel 10 Pro Fold", id = 2),
                device(24, "Pixel 10 Pro Fold", id = 3),
                device(AudioDeviceInfo.TYPE_BUILTIN_EARPIECE, "Pixel 10 Pro Fold", id = 4)
            )
        )
        assertEquals(1, outputs.size)
        assertEquals("Phone speaker", outputs.single().label)
        assertEquals(AudioDeviceInfo.TYPE_BUILTIN_SPEAKER, outputs.single().type)
    }

    @Test
    fun keepsOneBluetoothRoutePerAddress() {
        val outputs = OutputSwitchController.curatedMusicOutputs(
            listOf(
                device(AudioDeviceInfo.TYPE_BLUETOOTH_A2DP, "WH-1000XM5", id = 10, address = "AA:BB", isBluetooth = true),
                device(AudioDeviceInfo.TYPE_BLUETOOTH_SCO, "WH-1000XM5", id = 11, address = "AA:BB", isBluetooth = true),
                device(AudioDeviceInfo.TYPE_BLE_HEADSET, "WH-1000XM5", id = 12, address = "AA:BB", isBluetooth = true)
            )
        )
        assertEquals(1, outputs.size)
        assertEquals(AudioDeviceInfo.TYPE_BLUETOOTH_A2DP, outputs.single().type)
        assertTrue(outputs.single().isBluetooth)
    }
}
