package com.audiophile.musicplayer.ui.nowplaying

import android.util.Log
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.material3.TextButton
import androidx.compose.material3.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GraphicEq
import com.audiophile.musicplayer.ui.VantaSheetHeader
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.audiophile.musicplayer.data.display.VantaQualityInfo
import com.audiophile.musicplayer.ui.AppAccent
import com.audiophile.musicplayer.ui.AppAccentSecondary
import com.audiophile.musicplayer.ui.AppSuccess
import com.audiophile.musicplayer.ui.AppText
import com.audiophile.musicplayer.ui.AppTextMuted
import com.audiophile.musicplayer.ui.AppTextSecondary
import com.audiophile.musicplayer.ui.VantaBottomSheet
import com.audiophile.musicplayer.ui.VantaSheetDivider
import com.audiophile.musicplayer.ui.glassSurface
import java.util.Locale

private data class QualityVisualLabel(val primary: String, val secondary: String?)

private fun qualityVisualLabel(info: VantaQualityInfo): QualityVisualLabel {
    val audio = info.toAudioQualityInfo()
    val secondary = formatSampleRate(audio.sampleRateHz)
        ?: audio.bitrateKbps?.takeIf { it > 0 }?.let { "$it kbps" }
    return QualityVisualLabel(audio.badgeLabel(), secondary)
}

fun composedQualityLine(info: VantaQualityInfo): String {
    val audio = info.toAudioQualityInfo()
    val playback = audio.playbackLabel()
    return if (playback.isNullOrBlank()) audio.badgeLabel() else "${audio.badgeLabel()} \u00B7 $playback"
}

private fun qualityDetailRows(info: VantaQualityInfo): List<Pair<String, String>> = buildList {
    info.format?.takeIf { it.isNotBlank() }?.uppercase(Locale.US)?.let { add("File format" to it) }
    info.bitrateKbps?.takeIf { it > 0 }?.let { add("Bitrate" to "$it kbps") }
    formatSampleRate(info.sampleRateHz)?.let { add("Sample rate" to it) }
    info.bitDepth?.takeIf { it > 0 }?.let { add("Bit depth" to "$it-bit") }
    info.channels?.takeIf { it > 0 }?.let { add("Recording" to when (it) {
        1 -> "Mono"; 2 -> "Stereo"; else -> "$it channels"
    }) }
}

private fun isMpegh(info: VantaQualityInfo): Boolean = listOfNotNull(info.format, info.mimeType).any {
    it.contains("mpeg-h", true) || it.contains("mpegh", true) || it.contains("mha1", true) || it.contains("mhm1", true)
}

private fun qualityTierLabel(info: VantaQualityInfo): String = when {
    info.isEclipsaAudio -> "Eclipsa Audio"
    info.isSony360RealityAudio || isMpegh(info) -> "360 Reality Audio"
    info.isDolbyAtmos -> "Dolby Atmos"
    else -> when (info.toAudioQualityInfo().tier) {
        com.audiophile.musicplayer.data.display.QualityTier.ATMOS -> "Dolby Atmos"
        com.audiophile.musicplayer.data.display.QualityTier.MAX -> "Hi-Res Lossless"
        com.audiophile.musicplayer.data.display.QualityTier.LOSSLESS -> "Lossless"
        com.audiophile.musicplayer.data.display.QualityTier.HIGH -> "High quality"
        com.audiophile.musicplayer.data.display.QualityTier.PREVIEW -> "Preview"
        else -> if (info.format.isNullOrBlank() && info.bitrateKbps == null) "Your sound" else "Standard quality"
    }
}

private fun qualityDescription(info: VantaQualityInfo): String = when {
    info.isPreview -> "A preview of this recording. Full-track quality may differ."
    info.isEclipsaAudio -> "An immersive recording in the Eclipsa Audio format. Playback adapts to your listening setup."
    info.isSony360RealityAudio || isMpegh(info) ->
        "A Sony 360 Reality Audio (MPEG-H) mix. Use headphones or a compatible system to hear the full sphere of sound."
    info.isDolbyAtmos -> "An immersive Dolby Atmos recording. Compatible devices use their audio system; supported E-AC-3 recordings can also use VANTA’s experimental headphone renderer."
    info.isLossless == true && info.isHiRes == true -> "A high-resolution recording with the detail preserved in a lossless format."
    info.isLossless == true -> "The recording’s detail is preserved without lossy compression."
    info.format.isNullOrBlank() && info.bitrateKbps == null -> "Start a song to see the quality available for this recording."
    else -> "A compressed recording that uses less data while you listen."
}

private fun formatSampleRate(sampleRateHz: Int?): String? {
    sampleRateHz ?: return null
    if (sampleRateHz <= 0) return null
    return if (sampleRateHz % 1000 == 0) "${sampleRateHz / 1000} kHz"
        else com.audiophile.musicplayer.data.display.AudioQualityInfo.formatKhz(sampleRateHz)
}

@Composable
fun NowPlayingQualitySignal(
    qualityInfo: VantaQualityInfo,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val label = qualityInfo.bestQualityLabel()?.takeIf { it.isNotBlank() } ?: return
    val visual = remember(qualityInfo) { qualityVisualLabel(qualityInfo) }
    val composed = remember(qualityInfo) { composedQualityLine(qualityInfo) }
    val spatialKind = remember(qualityInfo) { com.audiophile.musicplayer.ui.spatialIdentityKind(qualityInfo) }

    LaunchedEffect(composed) {
        Log.d("VANTA_NOWPLAYING_UI", "qualityBadge='$composed' placement='metadata_row'")
    }

    if (spatialKind != null) {
        com.audiophile.musicplayer.ui.SpatialIdentityTag(
            qualityInfo = qualityInfo,
            modifier = modifier,
            size = com.audiophile.musicplayer.ui.SpatialTagSize.Compact,
            onClick = onClick
        )
        return
    }

    Row(
        modifier = modifier
            .widthIn(max = 148.dp)
            .heightIn(min = 44.dp)
            .semantics { contentDescription = "Audio quality: ${composed.replace(" \u00B7 ", " ")}" }
            .clickable(onClick = onClick)
            .clip(RoundedCornerShape(50))
            .background(AppAccent.copy(alpha = 0.08f))
            .border(0.5.dp, AppAccent.copy(alpha = 0.18f), RoundedCornerShape(50))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        QualityMark(accentColor = AppAccent, modifier = Modifier.size(8.dp))
        Text(
            text = visual.primary,
            color = AppAccent,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1
        )
        if (visual.secondary != null) {
            Text(
                text = visual.secondary,
                color = AppAccent.copy(alpha = 0.65f),
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun AudiophileSignalPanel(
    qualityInfo: VantaQualityInfo,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val audio = remember(qualityInfo) { qualityInfo.toAudioQualityInfo() }
    val tier = remember(audio) { audio.badgeLabel() }
    val resolution = remember(audio) {
        listOfNotNull(
            audio.bitDepth?.takeIf { it > 0 }?.let { "$it-bit" },
            formatSampleRate(audio.sampleRateHz)
        ).joinToString("  ·  ")
    }
    val streamLine = remember(audio) {
        listOfNotNull(
            (audio.codec ?: audio.container)?.uppercase(Locale.US),
            resolution.takeIf { it.isNotBlank() }
                ?: audio.bitrateKbps?.takeIf { it > 0 }?.let { "$it kbps" }
        ).joinToString("  ·  ")
    }
    val originLabel = "AUDIO QUALITY"
    val processingLabel = if (audio.transcodingOccurred) "Adapted for playback" else "View details"
    val signalColor = if (audio.measured) AppSuccess else AppAccentSecondary

    Row(
        modifier = modifier
            .fillMaxWidth()
            .semantics {
                contentDescription = listOf(originLabel, tier, resolution, streamLine, processingLabel)
                    .filter { it.isNotBlank() }
                    .joinToString(" ")
            }
            .glassSurface(
                shape = RoundedCornerShape(18.dp),
                borderAlpha = 0.13f,
                surfaceAlpha = 0.70f
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 13.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(RoundedCornerShape(11.dp))
                .background(signalColor.copy(alpha = 0.11f))
                .border(0.5.dp, signalColor.copy(alpha = 0.24f), RoundedCornerShape(11.dp)),
            contentAlignment = Alignment.Center
        ) {
            QualityMark(accentColor = signalColor, modifier = Modifier.size(13.dp))
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = originLabel,
                color = signalColor,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
                maxLines = 1
            )
            Text(
                text = qualityTierLabel(qualityInfo),
                color = AppText,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (resolution.isNotBlank() && streamLine.isNotBlank()) {
                Text(
                    text = streamLine,
                    color = AppTextSecondary,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = "Details",
                color = AppAccentSecondary,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.7.sp,
                maxLines = 1
            )
            Text(
                text = "›",
                color = if (audio.transcodingOccurred) Color(0xFFFFD166) else AppTextMuted,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.4.sp,
                maxLines = 1
            )
        }
    }
}

@Composable
fun QualityMark(accentColor: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        drawCircle(accentColor.copy(alpha = 0.28f), radius = size.minDimension / 2f)
        drawCircle(AppText.copy(alpha = 0.72f), radius = size.minDimension * 0.24f)
    }
}

@Composable
fun QualityDetailsSheet(
    qualityInfo: VantaQualityInfo,
    accentColor: Color,
    trackTitle: String,
    trackArtist: String,
    onDismiss: () -> Unit
) {
    val details = remember(qualityInfo) { qualityDetailRows(qualityInfo) }
    var expanded by remember(trackTitle, trackArtist) { mutableStateOf(false) }
    val resolution = listOfNotNull(
        qualityInfo.bitDepth?.takeIf { it > 0 }?.let { "$it-bit" },
        formatSampleRate(qualityInfo.sampleRateHz)
    ).joinToString("  ·  ")
    VantaBottomSheet(visible = true, onDismiss = onDismiss, maxHeightFraction = 0.82f) {
        VantaSheetHeader("Audio quality", listOf(trackTitle, trackArtist).filter { it.isNotBlank() }.joinToString(" · "), onDismiss)
        Spacer(Modifier.height(22.dp))
        Column(Modifier.padding(horizontal = 20.dp).fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(Brush.linearGradient(listOf(AppAccent.copy(alpha = .16f), AppAccent.copy(alpha = .025f))))
            .border(.5.dp, AppAccent.copy(alpha = .24f), RoundedCornerShape(24.dp))
            .padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                com.audiophile.musicplayer.ui.VantaFormatBadgeRow(
                    qualityInfo = qualityInfo,
                    size = com.audiophile.musicplayer.ui.FormatBadgeSize.Hero
                )
            }
            when (com.audiophile.musicplayer.ui.spatialIdentityKind(qualityInfo)) {
                com.audiophile.musicplayer.ui.SpatialIdentityKind.DOLBY_ATMOS -> {
                    Spacer(Modifier.height(4.dp))
                    Text("Immersive object-based mix", color = AppText, fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.Medium)
                }
                com.audiophile.musicplayer.ui.SpatialIdentityKind.SONY_360 -> {
                    Spacer(Modifier.height(4.dp))
                    Text("360° Reality Audio mix", color = AppText, fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.Medium)
                }
                null -> Text(qualityTierLabel(qualityInfo), color = AppText, fontSize = 29.sp, lineHeight = 34.sp, fontWeight = FontWeight.Medium)
            }
            if (resolution.isNotBlank()) Text(resolution, color = AppAccent, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            Text(qualityDescription(qualityInfo), color = AppTextSecondary, fontSize = 14.sp, lineHeight = 21.sp)
        }
        Column(Modifier.padding(horizontal = 24.dp, vertical = 20.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Text("Made for your listening setup", color = AppText, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Text(if (qualityInfo.transcodingOccurred) "Adapted for your device. The playback format may differ from the recording shown above."
                else "Your phone, headphones and sound settings shape what you hear. Spatial audio needs a compatible recording and output.",
                color = AppTextSecondary, fontSize = 13.sp, lineHeight = 20.sp)
            if (!qualityInfo.measured) Text("Details are supplied with the recording and may update when playback starts.",
                color = AppTextSecondary, fontSize = 12.sp, lineHeight = 18.sp)
        }
        if (details.isNotEmpty()) {
            VantaSheetDivider()
            TextButton(onClick = { expanded = !expanded }, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)
                .semantics { contentDescription = if (expanded) "Hide recording details" else "Show recording details" }) {
                Text(if (expanded) "Hide recording details  −" else "Recording details  +", color = AppAccent, fontSize = 14.sp)
            }
            if (expanded) details.forEach { (name, value) -> QualityDetailRow(name, value) }
        }
    }
}

@Composable
private fun QualityDetailRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = AppTextSecondary, fontSize = 13.sp, modifier = Modifier.weight(1f))
        Text(value, color = AppText, fontSize = 13.sp, fontWeight = FontWeight.Medium, textAlign = TextAlign.End,
            modifier = Modifier.weight(1f).padding(start = 12.dp))
    }
}
