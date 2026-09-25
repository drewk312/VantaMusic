@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package com.audiophile.musicplayer.ui

import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.audiophile.musicplayer.playback.PlaybackCommandAuth
import com.audiophile.musicplayer.playback.PlaybackService
import com.audiophile.musicplayer.playback.dsp.VantaEqualizerConfig
import com.audiophile.musicplayer.playback.dsp.VantaEqualizerHolder
import com.audiophile.musicplayer.playback.dsp.VantaEqualizerPreferences
import com.audiophile.musicplayer.playback.dsp.VantaEqualizerPreset
import kotlin.math.abs
import kotlin.math.roundToInt

private val accentColor = Color(0xFFFFB347)
private val eqBackground = Color(0xFF0C0B0D)
private val surfaceColor = Color(0xFF151311)
private val bandActive = Color(0xFFFFB347)
private val bandInactive = Color(0xFF4D4539)
private val gridColor = Color(0xFF252019)
private val spectrumFill = Color(0x20FFB347)
private val spectrumLine = Color(0x40FFB347)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ParametricEqScreen(
    onBack: () -> Unit,
    miniPlayerVisible: Boolean = false,
) {
    val context = LocalContext.current
    val eqPrefs = remember(context) { VantaEqualizerPreferences(context) }
    val scrollState = rememberScrollState()


    var initialConfig = remember { eqPrefs.load() }
    var eqEnabled by remember { mutableStateOf(initialConfig.eqEnabled) }
    var spatialEnabled by remember { mutableStateOf(initialConfig.spatialEnabled) }
    var tubeEnabled by remember { mutableStateOf(initialConfig.tubeEnabled) }
    var tubeDrive by remember { mutableFloatStateOf(initialConfig.tubeDrive) }
    var bassCannonEnabled by remember { mutableStateOf(initialConfig.bassCannonEnabled) }
    var bassCannonAmount by remember { mutableFloatStateOf(initialConfig.bassCannonAmount) }
    var trebleEnabled by remember { mutableStateOf(initialConfig.trebleEnabled) }
    var trebleBoostAmount by remember { mutableFloatStateOf(initialConfig.trebleBoostAmount) }
    var convolverEnabled by remember { mutableStateOf(initialConfig.convolverEnabled) }
    var limiterEnabled by remember { mutableStateOf(initialConfig.limiterEnabled) }
    var crossfeedEnabled by remember { mutableStateOf(initialConfig.crossfeedEnabled) }
    var reverbEnabled by remember { mutableStateOf(initialConfig.reverbEnabled) }
    var currentPreset by remember { mutableStateOf(initialConfig.preset) }
    val eqSwitchColors = SwitchDefaults.colors(
        checkedThumbColor = Color(0xFF20170D),
        checkedTrackColor = accentColor,
        checkedBorderColor = accentColor,
        uncheckedThumbColor = Color(0xFF5C554C),
        uncheckedTrackColor = Color(0xFF27231F),
        uncheckedBorderColor = Color(0xFF494138),
    )

    val bandGains = remember {
        mutableStateListOf<Float>().apply {
            addAll(initialConfig.eqBands)
            while (size < VantaEqualizerConfig.BAND_COUNT) add(0f)
        }
    }

    var spectrumMagnitudes by remember { mutableStateOf(FloatArray(128)) }
    var uiTick by remember { mutableIntStateOf(0) }

    DisposableEffect(Unit) {
        val processor = VantaEqualizerHolder.processor
        val origListener = processor?.spectrumListener
        processor?.spectrumListener = { mags ->
            spectrumMagnitudes = mags
        }
        onDispose {
            processor?.spectrumListener = origListener
        }
    }

    fun pushConfig() {
        val processor = VantaEqualizerHolder.processor
        val config = initialConfig.copy(
            eqEnabled = eqEnabled,
            eqBypassEnabled = false,
            eqBands = bandGains.toList(),
            spatialEnabled = spatialEnabled,
            stereoWidenLevel = if (spatialEnabled) 0.5f else 0f,
            crossfeedEnabled = crossfeedEnabled,
            crossfeedMode = 0,
            reverbEnabled = reverbEnabled,
            reverbPreset = 2,
            tubeEnabled = tubeEnabled,
            tubeDrive = tubeDrive,
            bassCannonEnabled = bassCannonEnabled,
            bassCannonAmount = bassCannonAmount,
            trebleEnabled = trebleEnabled,
            trebleBoostAmount = trebleBoostAmount,
            convolverEnabled = convolverEnabled,
            limiterEnabled = limiterEnabled,
            preset = currentPreset,
        )
        eqPrefs.save(config)
        processor?.let { it.config = config; it.forceApply() }
        context.startService(PlaybackCommandAuth.createIntent(context, PlaybackService.ACTION_REFRESH_IMMERSIVE_AUDIO))
    }

    fun applyPreset(preset: VantaEqualizerPreset) {
        currentPreset = preset
        eqEnabled = true
        bandGains.clear()
        bandGains.addAll(preset.gains)
        // Room-style presets are defined by their space, not just their EQ curve —
        // bring the matching spatial processing with them.
        when (preset) {
            VantaEqualizerPreset.CONCERT_HALL,
            VantaEqualizerPreset.CINEMA -> {
                spatialEnabled = true
                reverbEnabled = true
            }
            VantaEqualizerPreset.JAZZ,
            VantaEqualizerPreset.LOFI -> {
                spatialEnabled = true
            }
            else -> Unit
        }
        pushConfig()
        uiTick++
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(bottom = appOverlayBottomPadding(miniPlayerVisible = miniPlayerVisible))
            .background(eqBackground)
            .verticalScroll(scrollState)
            .padding(top = appTopContentPadding())
            .padding(horizontal = VantaSpacing.screenHorizontal)
            .padding(bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onBack) { Text("‹  Back", color = AppText, fontWeight = FontWeight.SemiBold) }
            Text("AUDIO ENGINE", color = AppTextMuted, fontSize = 11.sp, letterSpacing = 1.6.sp)
        }

        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Equalizer", color = AppText, fontWeight = FontWeight.Bold, fontSize = 28.sp)
            TextButton(onClick = {
                initialConfig = VantaEqualizerConfig()
                eqEnabled = false; spatialEnabled = false; tubeEnabled = false
                bassCannonEnabled = false; trebleEnabled = false; convolverEnabled = false
                crossfeedEnabled = false; reverbEnabled = false; limiterEnabled = true
                tubeDrive = 0.5f; bassCannonAmount = 0.5f; trebleBoostAmount = 0.5f
                currentPreset = VantaEqualizerPreset.FLAT
                bandGains.clear(); bandGains.addAll(initialConfig.eqBands)
                pushConfig(); uiTick++
            }) { Text("Reset sound", color = AppTextSecondary) }
        }

        if (VantaEqualizerHolder.processor == null) {
            Box(Modifier.fillMaxWidth().padding(24.dp)) {
                Text(
                    "Your settings are saved. Start playback to hear your changes.",
                    color = AppTextSecondary, textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        Row(
            Modifier.fillMaxWidth().background(surfaceColor, RoundedCornerShape(16.dp)).padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text("EQUALIZER", color = accentColor, fontSize = 11.sp, letterSpacing = 1.sp)
                Text("31-band precision EQ", color = AppTextSecondary, fontSize = 11.sp)
            }
            Switch(
                modifier = Modifier.semantics { contentDescription = "Enable equalizer" },
                checked = eqEnabled,
                onCheckedChange = { eqEnabled = it; pushConfig(); uiTick++ },
                colors = eqSwitchColors,
            )
        }

        Box(
            Modifier
                .fillMaxWidth()
                .height(240.dp)
                .background(surfaceColor, RoundedCornerShape(16.dp))
                .padding(4.dp)
        ) {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectDragGestures { change, _ ->
                            val bandIndex = ((change.position.x / size.width) * bandGains.size)
                                .toInt().coerceIn(0, bandGains.size - 1)
                            val newGain = ((1f - change.position.y / size.height) * 24f - 12f)
                                .roundToInt().toFloat().coerceIn(-12f, 12f)
                            change.consume()
                            eqEnabled = true
                            bandGains[bandIndex] = newGain
                            pushConfig()
                            uiTick++
                        }
                    }
            ) {
                val w = size.width
                val h = size.height
                val halfH = h / 2f
                val maxDb = 12f
                val perBand = w / bandGains.size

                for (i in 0..5) {
                    val y = h * i / 5
                    drawLine(gridColor, Offset(0f, y), Offset(w, y), strokeWidth = 0.5f)
                }
                drawLine(accentColor.copy(alpha = 0.3f), Offset(0f, halfH), Offset(w, halfH), strokeWidth = 1f)

                val spectrum = spectrumMagnitudes
                if (spectrum.isNotEmpty()) {
                    val bars = 64
                    val barW = w / bars
                    for (i in 0 until minOf(bars, spectrum.size)) {
                        val norm = (spectrum[i] / 100f).coerceIn(0f, 1f)
                        val barH = norm * h * 0.8f
                        drawRect(
                            spectrumFill,
                            topLeft = Offset(i * barW, h - barH),
                            size = androidx.compose.ui.geometry.Size(barW - 1f, barH)
                        )
                    }
                }

                val path = Path()
                bandGains.forEachIndexed { i, g ->
                    val cx = i * perBand + perBand / 2f
                    val cy = halfH - (g / maxDb) * halfH * 0.9f
                    val barHeight = abs(cy - halfH)
                    val barColor = if (g != 0f) bandActive else bandInactive
                    drawRect(
                        barColor.copy(alpha = 0.4f),
                        topLeft = Offset(cx - perBand * 0.15f, minOf(cy, halfH)),
                        size = androidx.compose.ui.geometry.Size(perBand * 0.3f, barHeight)
                    )
                    if (i == 0) path.moveTo(cx, cy) else path.lineTo(cx, cy)
                }
                drawPath(path, bandActive.copy(alpha = 0.8f), style = Stroke(width = 2f))

                bandGains.forEachIndexed { i, g ->
                    val cx = i * perBand + perBand / 2f
                    val cy = halfH - (g / maxDb) * halfH * 0.9f
                    drawCircle(bandActive, 4f, Offset(cx, cy))
                }
            }
        }

        Spacer(Modifier.height(4.dp))

        Text("PRESETS", color = accentColor, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            VantaEqualizerPreset.entries.forEach { preset ->
                val isActive = currentPreset == preset
                Box(
                    Modifier
                        .clickable { applyPreset(preset) }
                        .background(
                            if (isActive) accentColor.copy(alpha = 0.2f) else surfaceColor,
                            RoundedCornerShape(12.dp)
                        )
                        .border(
                            0.5.dp,
                            if (isActive) accentColor else accentColor.copy(alpha = 0.2f),
                            RoundedCornerShape(12.dp)
                        )
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    Text(preset.label, color = if (isActive) accentColor else AppText, fontSize = 12.sp)
                }
            }
        }

        Spacer(Modifier.height(4.dp))

        Text("SPATIAL AUDIO", color = accentColor, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
        Box(Modifier.fillMaxWidth().background(surfaceColor, RoundedCornerShape(16.dp)).padding(16.dp)) {
            Column {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column {
                        Text("3D Spatial Audio", color = AppText, fontSize = 13.sp)
                        Text("HRTF + crossfeed simulation", color = AppTextSecondary, fontSize = 11.sp)
                    }
                    Switch(checked = spatialEnabled, onCheckedChange = { spatialEnabled = it; pushConfig(); uiTick++ }, colors = eqSwitchColors)
                }
                if (spatialEnabled) {
                    Spacer(Modifier.height(8.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("Crossfeed", color = AppTextSecondary, fontSize = 12.sp)
                        Switch(checked = crossfeedEnabled, onCheckedChange = { crossfeedEnabled = it; pushConfig() }, colors = eqSwitchColors)
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("Concert Reverb", color = AppTextSecondary, fontSize = 12.sp)
                        Switch(checked = reverbEnabled, onCheckedChange = { reverbEnabled = it; pushConfig() }, colors = eqSwitchColors)
                    }
                }
            }
        }

        Text("STUDIO FILTERS", color = accentColor, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
        Box(Modifier.fillMaxWidth().background(surfaceColor, RoundedCornerShape(16.dp)).padding(16.dp)) {
            Column {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column {
                        Text("Studio Warmth", color = AppText, fontSize = 13.sp)
                        Text("Tube amp simulation", color = AppTextSecondary, fontSize = 11.sp)
                    }
                    Switch(checked = tubeEnabled, onCheckedChange = { tubeEnabled = it; pushConfig() }, colors = eqSwitchColors)
                }
                if (tubeEnabled) {
                    Spacer(Modifier.height(6.dp))
                    Text("Drive", color = AppTextSecondary, fontSize = 11.sp)
                    Slider(
                        value = tubeDrive, onValueChange = { tubeDrive = it; pushConfig() },
                        valueRange = 0f..1f, colors = SliderDefaults.colors(thumbColor = accentColor, activeTrackColor = accentColor)
                    )
                }
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column {
                        Text("Bass Cannon", color = AppText, fontSize = 13.sp)
                        Text("Sub-harmonic synthesizer", color = AppTextSecondary, fontSize = 11.sp)
                    }
                    Switch(checked = bassCannonEnabled, onCheckedChange = { bassCannonEnabled = it; pushConfig() }, colors = eqSwitchColors)
                }
                if (bassCannonEnabled) {
                    Spacer(Modifier.height(6.dp))
                    Text("Intensity", color = AppTextSecondary, fontSize = 11.sp)
                    Slider(
                        value = bassCannonAmount, onValueChange = { bassCannonAmount = it; pushConfig() },
                        valueRange = 0f..1f, colors = SliderDefaults.colors(thumbColor = accentColor, activeTrackColor = accentColor)
                    )
                }
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column {
                        Text("Treble Boost", color = AppText, fontSize = 13.sp)
                        Text("High-frequency enhancement", color = AppTextSecondary, fontSize = 11.sp)
                    }
                    Switch(checked = trebleEnabled, onCheckedChange = { trebleEnabled = it; pushConfig() }, colors = eqSwitchColors)
                }
                if (trebleEnabled) {
                    Spacer(Modifier.height(6.dp))
                    Text("Boost", color = AppTextSecondary, fontSize = 11.sp)
                    Slider(
                        value = trebleBoostAmount, onValueChange = { trebleBoostAmount = it; pushConfig() },
                        valueRange = 0f..1f, colors = SliderDefaults.colors(thumbColor = accentColor, activeTrackColor = accentColor)
                    )
                }
            }
        }

        Box(Modifier.fillMaxWidth().background(surfaceColor, RoundedCornerShape(16.dp)).padding(16.dp)) {
            Column {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column {
                        Text("Output Limiter", color = AppText, fontSize = 13.sp)
                        Text("Prevents clipping", color = AppTextSecondary, fontSize = 11.sp)
                    }
                    Switch(checked = limiterEnabled, onCheckedChange = { limiterEnabled = it; pushConfig() }, colors = eqSwitchColors)
                }
            }
        }

        Spacer(Modifier.height(24.dp))
    }
}
