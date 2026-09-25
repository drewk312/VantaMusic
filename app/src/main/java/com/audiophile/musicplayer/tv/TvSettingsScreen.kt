package com.audiophile.musicplayer.tv

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Speaker
import androidx.compose.material.icons.outlined.SurroundSound
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.audiophile.musicplayer.playback.PlaybackCommandAuth
import com.audiophile.musicplayer.playback.PlaybackService
import com.audiophile.musicplayer.playback.dsp.VantaEqualizerConfig
import com.audiophile.musicplayer.playback.dsp.VantaEqualizerPreferences
import com.audiophile.musicplayer.playback.dsp.VantaEqualizerPreset
import com.audiophile.musicplayer.playback.dsp.VantaImmersiveMode
import com.audiophile.musicplayer.sync.DeviceLibrarySyncManager
import com.audiophile.musicplayer.ui.theme.VantaSans
import kotlinx.coroutines.launch

/**
 * 10-foot Living Room Audio, Receiver, and System Settings.
 * Remote D-pad optimized with ReplayGain volume leveling, HDMI/Optical pass-through modes,
 * EQ presets, and TV/Phone synchronization.
 */
@Composable
fun TvSettingsScreen(
    pairCode: String,
    syncStatus: String?,
    metrics: TvMetrics,
    onEnsureCode: () -> Unit,
    onPullFromPhone: () -> Unit,
    onPushToCloud: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val eqPrefs = remember(context) { VantaEqualizerPreferences(context) }
    var eqConfig by remember { mutableStateOf(eqPrefs.load()) }

    fun updateEq(newConfig: VantaEqualizerConfig) {
        eqConfig = newConfig
        eqPrefs.save(newConfig)
        runCatching {
            context.startService(
                PlaybackCommandAuth.createIntent(context, PlaybackService.ACTION_REFRESH_IMMERSIVE_AUDIO)
            )
        }
    }

    LaunchedEffect(Unit) {
        if (pairCode.isBlank()) {
            onEnsureCode()
        }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = metrics.pagePadding,
            end = metrics.pagePadding,
            top = 16.dp,
            bottom = 80.dp
        ),
        verticalArrangement = Arrangement.spacedBy(28.dp)
    ) {
        // Section: Header
        item {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "AUDIO & RECEIVER SETTINGS",
                    color = TvTheme.HiResGoldBright,
                    fontSize = metrics.caption,
                    fontFamily = VantaSans,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.5.sp
                )
                Text(
                    text = "Living Room Acoustics",
                    color = TvTheme.Text,
                    fontSize = metrics.pageTitle,
                    fontFamily = VantaSans,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "Configure digital signal processing, volume matching, and speaker profiles for your TV receiver setup.",
                    color = TvTheme.TextMuted,
                    fontSize = metrics.body,
                    fontFamily = VantaSans
                )
            }
        }

        // Section: Volume Normalization (ReplayGain)
        item {
            TvSettingsCard(
                title = "Volume Leveling & ReplayGain",
                icon = Icons.AutoMirrored.Outlined.VolumeUp
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    TvSettingsToggleRow(
                        title = "Loudness Normalization",
                        subtitle = if (eqConfig.loudnessNormalizationEnabled) {
                            "ReplayGain active (${String.format("%.1f", eqConfig.replayGainDb)} dB target)"
                        } else {
                            "Off · Audio plays at raw studio gain"
                        },
                        checked = eqConfig.loudnessNormalizationEnabled,
                        metrics = metrics,
                        onToggle = {
                            updateEq(
                                eqConfig.copy(
                                    loudnessNormalizationEnabled = !eqConfig.loudnessNormalizationEnabled,
                                    replayGainDb = if (eqConfig.replayGainDb == 0f) -8f else eqConfig.replayGainDb
                                )
                            )
                        }
                    )

                    if (eqConfig.loudnessNormalizationEnabled) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(TvTheme.BgElevated)
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text(
                                    "Target Pre-Amp Headroom",
                                    color = TvTheme.Text,
                                    fontSize = metrics.cardSubtitle,
                                    fontFamily = VantaSans,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    "Prevents clipping when switching between streaming services",
                                    color = TvTheme.TextMuted,
                                    fontSize = metrics.caption,
                                    fontFamily = VantaSans
                                )
                            }

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                TvFocusable(
                                    onClick = {
                                        val next = (eqConfig.replayGainDb - 1f).coerceIn(-18f, 0f)
                                        updateEq(eqConfig.copy(replayGainDb = next))
                                    },
                                    cornerRadius = 8,
                                    focusScale = 1.08f
                                ) { focused ->
                                    Box(
                                        modifier = Modifier
                                            .size(36.dp)
                                            .background(if (focused) TvTheme.HiResGold else TvTheme.SurfaceSoft, RoundedCornerShape(8.dp)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text("−", color = if (focused) TvTheme.HiResGoldDark else TvTheme.Text, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                                    }
                                }

                                Text(
                                    text = "${String.format("%.1f", eqConfig.replayGainDb)} dB",
                                    color = TvTheme.HiResGoldBright,
                                    fontSize = metrics.cardTitle,
                                    fontFamily = VantaSans,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.width(68.dp),
                                    maxLines = 1
                                )

                                TvFocusable(
                                    onClick = {
                                        val next = (eqConfig.replayGainDb + 1f).coerceIn(-18f, 0f)
                                        updateEq(eqConfig.copy(replayGainDb = next))
                                    },
                                    cornerRadius = 8,
                                    focusScale = 1.08f
                                ) { focused ->
                                    Box(
                                        modifier = Modifier
                                            .size(36.dp)
                                            .background(if (focused) TvTheme.HiResGold else TvTheme.SurfaceSoft, RoundedCornerShape(8.dp)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text("+", color = if (focused) TvTheme.HiResGoldDark else TvTheme.Text, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Section: Spatial & Immersive Modes
        item {
            TvSettingsCard(
                title = "Surround & Immersive Audio",
                icon = Icons.Outlined.SurroundSound
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text(
                        "Select acoustic presentation for soundbars and home theater receivers:",
                        color = TvTheme.TextSecondary,
                        fontSize = metrics.cardSubtitle,
                        fontFamily = VantaSans
                    )

                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(VantaImmersiveMode.values()) { mode ->
                            val selected = eqConfig.immersiveMode == mode
                            TvFocusable(
                                onClick = {
                                    updateEq(eqConfig.copy(immersiveMode = mode, spatialEnabled = mode != VantaImmersiveMode.OFF))
                                },
                                cornerRadius = 14,
                                focusScale = 1.05f
                            ) { focused ->
                                val bg = when {
                                    selected -> TvTheme.HiResGold
                                    focused -> TvTheme.SurfaceSoft
                                    else -> TvTheme.BgElevated
                                }
                                val fg = when {
                                    selected -> TvTheme.HiResGoldDark
                                    focused -> TvTheme.Text
                                    else -> TvTheme.TextSecondary
                                }

                                Row(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(bg)
                                        .border(
                                            width = if (focused) 1.5.dp else 1.dp,
                                            color = if (focused) TvTheme.FocusRing else TvTheme.Hairline,
                                            shape = RoundedCornerShape(14.dp)
                                        )
                                        .padding(horizontal = 16.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    if (selected) {
                                        Icon(
                                            imageVector = Icons.Outlined.Check,
                                            contentDescription = null,
                                            tint = fg,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                    Text(
                                        text = mode.label,
                                        color = fg,
                                        fontSize = metrics.cardSubtitle,
                                        fontFamily = VantaSans,
                                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Section: EQ Acoustic Presets
        item {
            TvSettingsCard(
                title = "Receiver Equalizer Presets",
                icon = Icons.Outlined.GraphicEq
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    val presets = listOf(
                        VantaEqualizerPreset.FLAT,
                        VantaEqualizerPreset.CINEMA,
                        VantaEqualizerPreset.CONCERT_HALL,
                        VantaEqualizerPreset.BASS_BOOST,
                        VantaEqualizerPreset.WARM,
                        VantaEqualizerPreset.JAZZ,
                        VantaEqualizerPreset.ROCK,
                        VantaEqualizerPreset.STUDIO,
                        VantaEqualizerPreset.VOCAL
                    )

                    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(presets) { preset ->
                            val selected = eqConfig.preset == preset
                            TvFocusable(
                                onClick = {
                                    updateEq(eqConfig.copy(preset = preset, eqEnabled = preset != VantaEqualizerPreset.FLAT))
                                },
                                cornerRadius = 12,
                                focusScale = 1.05f
                            ) { focused ->
                                val bg = when {
                                    selected -> TvTheme.Text
                                    focused -> TvTheme.SurfaceSoft
                                    else -> TvTheme.BgElevated
                                }
                                val fg = when {
                                    selected -> TvTheme.Bg
                                    focused -> TvTheme.Text
                                    else -> TvTheme.TextSecondary
                                }

                                Text(
                                    text = preset.label,
                                    color = fg,
                                    fontSize = metrics.cardSubtitle,
                                    fontFamily = VantaSans,
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(bg)
                                        .border(
                                            1.dp,
                                            if (focused) TvTheme.FocusRing else TvTheme.Hairline,
                                            RoundedCornerShape(12.dp)
                                        )
                                        .padding(horizontal = 16.dp, vertical = 10.dp)
                                )
                            }
                        }
                    }

                    // Extra living room enhancements
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(Modifier.weight(1f)) {
                            TvSettingsToggleRow(
                                title = "Analog Tube Warmth",
                                subtitle = "Even-order harmonic warmth for harsh digital remasters",
                                checked = eqConfig.tubeEnabled,
                                metrics = metrics,
                                onToggle = { updateEq(eqConfig.copy(tubeEnabled = !eqConfig.tubeEnabled)) }
                            )
                        }

                        Box(Modifier.weight(1f)) {
                            TvSettingsToggleRow(
                                title = "Subwoofer Punch",
                                subtitle = "Low-end resonance extension for cinema subwoofers",
                                checked = eqConfig.bassCannonEnabled,
                                metrics = metrics,
                                onToggle = { updateEq(eqConfig.copy(bassCannonEnabled = !eqConfig.bassCannonEnabled)) }
                            )
                        }
                    }
                }
            }
        }

        // Section: Phone & TV Pairing
        item {
            TvSettingsCard(
                title = "Link Phone & TV",
                icon = Icons.Outlined.PhoneAndroid
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = "TV PAIRING CODE",
                            color = TvTheme.TextMuted,
                            fontSize = 11.sp,
                            fontFamily = VantaSans,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.5.sp
                        )
                        Text(
                            text = if (pairCode.isBlank()) "······" else pairCode,
                            color = TvTheme.HiResGoldBright,
                            fontSize = 32.sp,
                            fontFamily = VantaSans,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 4.sp
                        )
                        Text(
                            text = syncStatus ?: "Enter this code on your phone in Settings → Link Phone & TV to sync liked tracks.",
                            color = TvTheme.TextSecondary,
                            fontSize = metrics.caption,
                            fontFamily = VantaSans
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        TvFocusable(
                            onClick = onPullFromPhone,
                            cornerRadius = 12,
                            focusScale = 1.05f
                        ) { focused ->
                            Text(
                                text = "Pull from Phone",
                                color = if (focused) TvTheme.HiResGoldDark else TvTheme.Text,
                                fontSize = metrics.cardSubtitle,
                                fontFamily = VantaSans,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(if (focused) TvTheme.HiResGold else TvTheme.SurfaceSoft)
                                    .border(1.dp, if (focused) TvTheme.FocusRing else TvTheme.Hairline, RoundedCornerShape(12.dp))
                                    .padding(horizontal = 18.dp, vertical = 12.dp)
                            )
                        }

                        TvFocusable(
                            onClick = onPushToCloud,
                            cornerRadius = 12,
                            focusScale = 1.05f
                        ) { focused ->
                            Text(
                                text = "Push TV Library",
                                color = if (focused) TvTheme.Text else TvTheme.TextSecondary,
                                fontSize = metrics.cardSubtitle,
                                fontFamily = VantaSans,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(if (focused) TvTheme.SurfaceSoft else TvTheme.BgElevated)
                                    .border(1.dp, if (focused) TvTheme.FocusRing else TvTheme.Hairline, RoundedCornerShape(12.dp))
                                    .padding(horizontal = 18.dp, vertical = 12.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TvSettingsCard(
    title: String,
    icon: ImageVector,
    content: @Composable () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(TvTheme.SurfaceGlass)
            .border(1.dp, TvTheme.Hairline, RoundedCornerShape(18.dp))
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = TvTheme.HiResGold,
                modifier = Modifier.size(20.dp)
            )
            Text(
                text = title,
                color = TvTheme.Text,
                fontSize = 17.sp,
                fontFamily = VantaSans,
                fontWeight = FontWeight.SemiBold
            )
        }
        content()
    }
}

@Composable
private fun TvSettingsToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    metrics: TvMetrics,
    onToggle: () -> Unit
) {
    TvFocusable(
        onClick = onToggle,
        cornerRadius = 14,
        focusScale = 1.02f,
        modifier = Modifier.fillMaxWidth()
    ) { focused ->
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(if (focused) TvTheme.SurfaceSoft else TvTheme.BgElevated)
                .border(
                    width = if (focused) 1.5.dp else 1.dp,
                    color = if (focused) TvTheme.FocusRing else TvTheme.Hairline,
                    shape = RoundedCornerShape(14.dp)
                )
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = title,
                    color = TvTheme.Text,
                    fontSize = metrics.cardTitle,
                    fontFamily = VantaSans,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = subtitle,
                    color = TvTheme.TextMuted,
                    fontSize = metrics.caption,
                    fontFamily = VantaSans,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Box(
                modifier = Modifier
                    .padding(start = 16.dp)
                    .size(width = 48.dp, height = 26.dp)
                    .clip(RoundedCornerShape(13.dp))
                    .background(if (checked) TvTheme.HiResGold else TvTheme.ProgressTrack)
                    .padding(3.dp),
                contentAlignment = if (checked) Alignment.CenterEnd else Alignment.CenterStart
            ) {
                Box(
                    modifier = Modifier
                        .size(20.dp)
                        .clip(CircleShape)
                        .background(if (checked) TvTheme.HiResGoldDark else TvTheme.TextMuted)
                )
            }
        }
    }
}
