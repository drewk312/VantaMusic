package com.audiophile.musicplayer.ui

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
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material.icons.filled.CastConnected
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Speaker
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.audiophile.musicplayer.playback.CastingState
import com.audiophile.musicplayer.playback.UpnpCastingManager
import com.audiophile.musicplayer.playback.UpnpDevice
import kotlinx.coroutines.launch

@Composable
fun CastDeviceSheet(
    castingManager: UpnpCastingManager,
    streamUrl: String?,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val devices by castingManager.availableDevices.collectAsState()
    val castState by castingManager.castingState.collectAsState()
    val scope = rememberCoroutineScope()

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(
                color = AppSurface,
                shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
            )
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Cast,
                    contentDescription = null,
                    tint = AppAccent,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "Sonos / Cast",
                    color = AppText,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            IconButton(onClick = onDismiss) {
                Icon(Icons.Default.Close, contentDescription = "Close", tint = AppTextMuted)
            }
        }

        // Current status
        when (val state = castState) {
            is CastingState.Idle -> {
                Text("Ready to discover Sonos and DLNA speakers", color = AppTextSecondary, fontSize = 14.sp)
            }
            is CastingState.Discovering -> {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Discovering...", color = AppAccent, fontSize = 14.sp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .clip(CircleShape)
                            .background(AppAccent.copy(alpha = 0.6f))
                    )
                }
            }
            is CastingState.Connected -> {
                Text(
                    "Connected to ${state.device.friendlyName}",
                    color = AppAccentSoft,
                    fontSize = 14.sp
                )
            }
            is CastingState.Streaming -> {
                Text(
                    "Streaming to ${state.device.friendlyName}",
                    color = AppAccent,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
            is CastingState.Error -> {
                Text(state.message, color = AppDestructive, fontSize = 14.sp)
            }
        }

        // Action buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Button(
                onClick = { castingManager.startDiscovery() },
                colors = ButtonDefaults.buttonColors(containerColor = AppAccent.copy(alpha = 0.15f)),
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Default.Refresh, contentDescription = null, tint = AppAccent, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Discover", color = AppAccent)
            }
            if (castState is CastingState.Streaming || castState is CastingState.Connected) {
                Button(
                    onClick = { castingManager.disconnect() },
                    colors = ButtonDefaults.buttonColors(containerColor = AppDestructive.copy(alpha = 0.15f)),
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Disconnect", color = AppDestructive)
                }
            }
        }

        // Device list
        if (devices.isEmpty() && castState !is CastingState.Discovering) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("No Sonos or cast devices found", color = AppTextMuted, fontSize = 15.sp)
                    Text(
                        "Make sure the speaker is on the same Wi-Fi network and tap Discover",
                        color = AppTextMuted.copy(alpha = 0.6f),
                        fontSize = 12.sp
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(devices, key = { it.id }) { device ->
                    DeviceRow(
                        device = device,
                        isActive = when (val s = castState) {
                            is CastingState.Connected -> s.device.id == device.id
                            is CastingState.Streaming -> s.device.id == device.id
                            else -> false
                        },
                        isStreaming = castState is CastingState.Streaming && (castState as CastingState.Streaming).device.id == device.id,
                        onConnect = {
                            if (streamUrl != null) {
                                castingManager.castToDevice(device, streamUrl)
                            } else {
                                castingManager.connectToDevice(device)
                            }
                        },
                        onStopCasting = { castingManager.stopCasting() }
                    )
                }
            }
        }
    }
}

@Composable
private fun DeviceRow(
    device: UpnpDevice,
    isActive: Boolean,
    isStreaming: Boolean,
    onConnect: () -> Unit,
    onStopCasting: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(if (isActive) AppAccent.copy(alpha = 0.1f) else AppSurfaceRaised)
            .clickable { if (isStreaming) onStopCasting() else onConnect() }
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = if (isActive) Icons.Default.CastConnected else Icons.Default.Speaker,
            contentDescription = null,
            tint = if (isActive) AppAccent else AppTextMuted,
            modifier = Modifier.size(28.dp)
        )
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = device.friendlyName,
                color = if (isActive) AppAccent else AppText,
                fontWeight = FontWeight.SemiBold,
                fontSize = 16.sp
            )
            Text(
                text = device.ipAddress,
                color = AppTextMuted,
                fontSize = 12.sp
            )
        }
        if (isStreaming) {
            Text(
                text = "Streaming",
                color = AppAccent,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold
            )
        } else if (isActive) {
            Text(
                text = "Connected",
                color = AppAccentSoft,
                fontSize = 12.sp
            )
        }
    }
}
