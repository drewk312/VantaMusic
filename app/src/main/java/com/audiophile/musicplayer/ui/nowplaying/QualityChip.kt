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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.audiophile.musicplayer.data.display.VantaQualityInfo
import com.audiophile.musicplayer.ui.AppAccent
import com.audiophile.musicplayer.ui.AppBackgroundBottom
import com.audiophile.musicplayer.ui.AppSurfaceRaised
import com.audiophile.musicplayer.ui.AppText
import com.audiophile.musicplayer.ui.AppTextMuted
import com.audiophile.musicplayer.ui.AppTextSecondary
import com.audiophile.musicplayer.ui.VantaBottomSheet
import com.audiophile.musicplayer.ui.VantaSheetDivider
import java.util.Locale

private data class QualityVisualLabel(val primary: String, val secondary: String?)

private fun qualityVisualLabel(label: String): QualityVisualLabel {
    val clean = label.trim()
    val lower = clean.lowercase()
    val bitrate = Regex("""\d+\s*kbps""", RegexOption.IGNORE_CASE).find(clean)?.value
    val rate = Regex("""\d+(?:\.\d+)?\s*kHz""", RegexOption.IGNORE_CASE).find(clean)?.value
    return when {
        "hi-res" in lower || "hi res" in lower -> QualityVisualLabel("HI-RES", rate ?: bitrate)
        "flac" in lower || "lossless" in lower || "cd quality" in lower -> QualityVisualLabel("LOSSLESS", null)
        else -> QualityVisualLabel(clean.uppercase().take(16), null)
    }
}

private fun qualityDetailRows(info: VantaQualityInfo): List<Pair<String, String>> = buildList {
    add("Tier" to qualityTierLabel(info))
    add("Codec" to (info.format?.uppercase(Locale.US) ?: "Not reported"))
    add("Average bitrate" to (info.bitrateKbps?.let { "$it kbps" } ?: "Not measurable"))
    add("Sample rate" to (formatSampleRate(info.sampleRateHz) ?: "Not reported"))
    add("Bit depth" to (info.bitDepth?.let { "$it-bit" } ?: "Not reported"))
    add("Source" to (info.sourceProviderId?.replace('_', ' ')?.replaceFirstChar { it.uppercase() } ?: "Streaming"))
    add("Status" to qualityStatusLabel(info))
}

private fun qualityTierLabel(info: VantaQualityInfo): String = when {
    info.isPreview -> "Preview"
    info.isHiRes == true -> "Hi-Res Lossless"
    info.isLossless == true -> "Lossless"
    else -> "Standard"
}

private fun qualityStatusLabel(info: VantaQualityInfo): String = when {
    info.isPreview -> "Preview only"
    info.reason == "measured_media_format" -> "Measured by decoder"
    info.reason == "measured_content_length_duration" -> "Measured from file"
    info.isValidated -> "Verified playable"
    else -> "Source reported"
}

private fun formatSampleRate(sampleRateHz: Int?): String? {
    sampleRateHz ?: return null
    if (sampleRateHz <= 0) return null
    return if (sampleRateHz % 1000 == 0) "${sampleRateHz / 1000} kHz"
    else String.format(Locale.US, "%.1f kHz", sampleRateHz / 1000.0)
}

@Composable
fun NowPlayingQualitySignal(
    qualityInfo: VantaQualityInfo,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val label = qualityInfo.bestQualityLabel()?.takeIf { it.isNotBlank() } ?: return
    val visual = remember(label) { qualityVisualLabel(label) }

    LaunchedEffect(label) {
        Log.d("VANTA_NOWPLAYING_UI", "qualityBadge='$label' placement='metadata_row'")
    }

    Row(
        modifier = modifier
            .semantics { contentDescription = "Audio quality: ${label.replace(" \u00B7 ", " ")}" }
            .clickable(onClick = onClick)
            .clip(RoundedCornerShape(50))
            .background(AppAccent.copy(alpha = 0.08f))
            .border(0.5.dp, AppAccent.copy(alpha = 0.18f), RoundedCornerShape(50))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        QualityMark(accentColor = AppAccent, modifier = Modifier.size(8.dp))
        Text(text = visual.primary, color = AppAccent, fontSize = 9.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
        if (visual.secondary != null) {
            Text(text = visual.secondary, color = AppAccent.copy(alpha = 0.65f), fontSize = 9.sp, fontWeight = FontWeight.Medium, maxLines = 1)
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
    val label = qualityInfo.bestQualityLabel().orEmpty()
    val visual = remember(label) { qualityVisualLabel(label.ifBlank { "Audio" }) }
    val details = remember(qualityInfo) { qualityDetailRows(qualityInfo) }

    VantaBottomSheet(visible = true, onDismiss = onDismiss) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(
                modifier = Modifier.size(46.dp).clip(RoundedCornerShape(14.dp))
                    .background(accentColor.copy(alpha = 0.13f))
                    .border(0.5.dp, accentColor.copy(alpha = 0.24f), RoundedCornerShape(14.dp)),
                contentAlignment = Alignment.Center
            ) {
                QualityMark(accentColor = accentColor, modifier = Modifier.size(18.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text("Audio Quality", color = AppText, fontSize = 20.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("$trackTitle - $trackArtist", color = AppTextSecondary, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }

        Spacer(Modifier.height(18.dp))

        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp).clip(RoundedCornerShape(22.dp))
                .background(Brush.verticalGradient(
                    listOf(accentColor.copy(alpha = 0.14f), AppSurfaceRaised.copy(alpha = 0.96f), AppBackgroundBottom.copy(alpha = 0.98f))
                ))
                .border(0.5.dp, accentColor.copy(alpha = 0.18f), RoundedCornerShape(22.dp)).padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(text = visual.primary, color = AppText, fontSize = 26.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(text = label.ifBlank { qualityTierLabel(qualityInfo) }, color = AppTextSecondary, fontSize = 13.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }

        Spacer(Modifier.height(18.dp))
        VantaSheetDivider()
        Spacer(Modifier.height(6.dp))

        details.forEach { (name, value) ->
            QualityDetailRow(label = name, value = value)
        }

        if (!qualityInfo.isValidated) {
            Spacer(Modifier.height(10.dp))
            Text("This source reports the quality, but playback has not verified it yet.", color = AppTextMuted, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 24.dp))
        }
    }
}

@Composable
private fun QualityDetailRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = AppTextSecondary, fontSize = 13.sp, maxLines = 1)
        Text(value, color = AppText, fontSize = 13.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.End, modifier = Modifier.widthIn(max = 210.dp))
    }
}
