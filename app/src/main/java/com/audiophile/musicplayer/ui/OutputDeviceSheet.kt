package com.audiophile.musicplayer.ui

import android.media.AudioDeviceInfo
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material.icons.filled.Speaker
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.audiophile.musicplayer.playback.OutputSwitchController
import com.audiophile.musicplayer.playback.SpatialHeadTracking
import com.audiophile.musicplayer.playback.UpnpCastingManager

@Composable
fun OutputDeviceSheet(
    castingManager: UpnpCastingManager?,
    streamUrl: String?,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var selectedKey by remember { mutableStateOf(OutputSwitchController.selectedDeviceKey(context)) }
    val outputs = remember { OutputSwitchController.listOutputs(context) }
    val selected = outputs.firstOrNull { it.key == selectedKey }
    val headTrackingAvailable = remember(context) { SpatialHeadTracking.isHeadTrackerAvailable(context) }

    LazyColumn(
        modifier = modifier
            .fillMaxWidth()
            .background(
                color = AppSurface,
                shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
            )
            .padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth().padding(20.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Output device", color = AppText, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Text(
                        when {
                            selected == null -> "Playing on the system route"
                            else -> "Playing on ${selected.label}"
                        },
                        color = AppTextSecondary,
                        fontSize = 13.sp
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = AppTextMuted)
                }
            }
        }

        item {
            Text(
                "THIS DEVICE",
                color = AppAccent,
                fontSize = 10.sp,
                letterSpacing = 2.sp,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 6.dp)
            )
        }

        if (outputs.isEmpty()) {
            item {
                Text(
                    "No switchable outputs found",
                    color = AppTextMuted,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp)
                )
            }
        } else {
            items(outputs, key = { it.key }) { device ->
                SystemOutputRow(
                    device = device,
                    isSelected = device.key == selectedKey,
                    onClick = {
                        OutputSwitchController.select(context, device)
                        selectedKey = device.key
                    }
                )
            }
        }

        if (selected?.isBluetooth == true && headTrackingAvailable) {
            item {
                Text(
                    if (SpatialHeadTracking.isEnabled(context)) {
                        "Head tracking is on for spatial audio headphones — enable or disable in Settings."
                    } else {
                        "Spatial headphones detected — head tracking is available in Settings."
                    },
                    color = AppAccentSoft,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
                )
            }
        }

        castingManager?.let { manager ->
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(4.dp)
                            .clip(CircleShape)
                            .background(AppTextMuted)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "CAST TO SPEAKERS",
                        color = AppTextMuted,
                        fontSize = 10.sp,
                        letterSpacing = 2.sp
                    )
                }
            }
            item {
                CastDeviceSheet(
                    castingManager = manager,
                    streamUrl = streamUrl,
                    onDismiss = onDismiss,
                    embedded = true,
                    modifier = Modifier.padding(bottom = 0.dp)
                )
            }
        }
    }
}

@Composable
private fun SystemOutputRow(
    device: OutputSwitchController.OutputDevice,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(if (isSelected) AppAccent.copy(alpha = 0.10f) else AppSurfaceRaised)
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = outputIcon(device.type),
            contentDescription = null,
            tint = if (isSelected) AppAccent else AppTextMuted,
            modifier = Modifier.size(24.dp)
        )
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = device.label,
                color = if (isSelected) AppAccent else AppText,
                fontWeight = FontWeight.SemiBold,
                fontSize = 15.sp
            )
            device.address?.takeIf { it.isNotBlank() && it != "topology" && !it.startsWith("bus:") }?.let {
                Text(text = it, color = AppTextMuted, fontSize = 11.sp, maxLines = 1)
            }
        }
        if (isSelected) {
            Icon(Icons.Default.Check, contentDescription = "Selected", tint = AppAccent, modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
private fun outputIcon(type: Int): androidx.compose.ui.graphics.vector.ImageVector = when (type) {
    AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> Icons.Default.PhoneAndroid
    AudioDeviceInfo.TYPE_BUILTIN_EARPIECE -> Icons.Default.Smartphone
    AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
    AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
    AudioDeviceInfo.TYPE_BLE_HEADSET,
    AudioDeviceInfo.TYPE_BLE_SPEAKER,
    AudioDeviceInfo.TYPE_HEARING_AID -> Icons.Default.Bluetooth
    AudioDeviceInfo.TYPE_USB_DEVICE,
    AudioDeviceInfo.TYPE_USB_HEADSET,
    AudioDeviceInfo.TYPE_USB_ACCESSORY -> Icons.Default.Usb
    AudioDeviceInfo.TYPE_WIRED_HEADSET,
    AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
    AudioDeviceInfo.TYPE_LINE_ANALOG,
    AudioDeviceInfo.TYPE_LINE_DIGITAL -> Icons.Default.Headphones
    AudioDeviceInfo.TYPE_HDMI,
    AudioDeviceInfo.TYPE_HDMI_ARC -> Icons.Default.Tv
    else -> Icons.Default.Speaker
}