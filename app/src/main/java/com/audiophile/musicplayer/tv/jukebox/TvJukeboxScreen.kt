package com.audiophile.musicplayer.tv.jukebox

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.audiophile.musicplayer.audio.visualizer.VantaAudioAnalyzerHolder
import com.audiophile.musicplayer.audio.visualizer.VantaAudioFrame
import com.audiophile.musicplayer.data.display.VantaQualityInfo
import com.audiophile.musicplayer.playback.QueueSnapshot
import com.audiophile.musicplayer.tv.TvMetrics
import com.audiophile.musicplayer.ui.theme.VantaSans
import kotlinx.coroutines.flow.MutableStateFlow

/** Physical rewind / fast-forward step, matching the transport keys. */
private const val SEEK_STEP_MS = 10_000L

/**
 * The Physical Jukebox — one machine, not a dashboard.
 *
 * Layout (left → right punctuation of the machine):
 *   - LEFT  ~22%: 45-rpm Record Magazine (walnut rack of vinyl slots).
 *   - CENTER ~56%: Turntable Chamber — huge cast platter, damped tonearm,
 *                  and the mechanical record selector arm crossing the deck
 *                  whenever a disc changes.
 *   - RIGHT  ~22%: Smoked-glass Now Playing panel engraved in the cabinet.
 *   - BOTTOM:      The audiophile amp sill — ballistic VU meters, glowing
 *                  300B tubes, and the recessed illuminated transport keys.
 *
 * Disc changes drive EVERYTHING through [JukeboxMachineFrame]: platter spin
 * down/up with real inertia, tonearm lift/swing/drop, the crossing selector
 * arm, and magazine rotation. Nothing just fades in — the machine performs.
 */
@Composable
fun TvJukeboxScreen(
    queueSnapshot: QueueSnapshot?,
    title: String?,
    artist: String?,
    album: String?,
    artworkUrl: String?,
    isPlaying: Boolean,
    qualityInfo: VantaQualityInfo?,
    positionMs: Long,
    durationMs: Long,
    isLiked: Boolean,
    metrics: TvMetrics,
    acousticness: Double? = null,
    onPrevious: () -> Unit,
    onToggle: () -> Unit,
    onNext: () -> Unit,
    onThumbsUp: () -> Unit,
    onThumbsDown: () -> Unit,
    onPlayQueueIndex: (Int) -> Unit,
    onSeek: ((Long) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val queue = queueSnapshot?.originalQueue.orEmpty()
    val currentIndex = queueSnapshot?.queueIndex ?: 0
    val progress = if (durationMs > 0L) (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f) else 0f

    // --- Machine state machine driver ---
    val machineFlow = remember {
        MutableStateFlow(
            JukeboxMachineFrame(
                state = JukeboxMechanicalState.PLAYING,
                discPresence = 1f,
                platterSpin = 1f,
                tonearmLifted = 0f,
                tonearmAngle = 28f
            )
        )
    }
    val frame by machineFlow.collectAsState()
    var lastTrackId by remember { mutableStateOf<String?>(null) }
    var isFirstLoad by remember { mutableStateOf(true) }

    val currentTrackId = queue.getOrNull(currentIndex)?.track?.trackId?.toString() ?: "idle"

    LaunchedEffect(currentTrackId) {
        val discWasPresent = !isFirstLoad && lastTrackId != null
        isFirstLoad = false
        lastTrackId = currentTrackId
        driveJukeboxChangeSequence(discPresent = discWasPresent) { machineFlow.value = it }
    }

    // Live audio spectrum for the VU meters / LED analyzer. Fallback pulse
    // starts immediately; DSP spectrum replaces it when PlaybackService pushes.
    val audioFrame = rememberLiveAudioFrame()

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF070505)),
        contentAlignment = Alignment.Center
    ) {
        // ── THE CABINET ────────────────────────────────────────────────────────
        // Walnut veneer frame with a chrome edge shared by the whole machine, so
        // magazine + chamber + now-playing read as one physical jukebox, not a
        // row of floating UI cards.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
                .shadow(
                    elevation = 30.dp,
                    shape = RoundedCornerShape(30.dp),
                    ambientColor = Color.Black
                )
                .clip(RoundedCornerShape(30.dp))
                .background(
                    Brush.verticalGradient(
                        listOf(Color(0xFF2E2115), Color(0xFF1B120B), Color(0xFF0E0A07))
                    )
                )
                .border(2.dp, Color(0xFF6B4E14), RoundedCornerShape(30.dp))
        ) {
            // Inset chrome reveal — the machined lip between frame and glass.
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(4.dp)
            ) {
                drawRoundRect(
                    brush = Brush.verticalGradient(
                        listOf(Color(0xFF3C2D1B), Color(0xFF241A11), Color(0xFF2A2015))
                    ),
                    cornerRadius = CornerRadius(26.dp.toPx(), 26.dp.toPx())
                )
            }

            // Operating glow — tungsten light spilling up behind the deck.
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawRect(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color(0xFF22170D).copy(alpha = if (isPlaying) 0.85f else 0.55f),
                            Color(0xFF120C08).copy(alpha = 0.4f),
                            Color.Transparent
                        ),
                        center = Offset(size.width * 0.52f, size.height * 0.52f),
                        radius = size.width * 0.62f
                    )
                )
            }

            // The machine's voice — chasing bulb light always on, marching.
            TvChaseLights(
                isPlaying = isPlaying,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(7.dp)
            )

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 22.dp, vertical = 14.dp)
            ) {
                // Neon marquee plate — a backlit brand sign, not a status header.
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(42.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(
                            Brush.horizontalGradient(
                                listOf(Color(0xFF2B1E11), Color(0xFF0D0905), Color(0xFF251A0E))
                            )
                        )
                        .border(1.dp, Color(0xFF7B5A17), RoundedCornerShape(10.dp))
                ) {
                    Canvas(modifier = Modifier.matchParentSize()) {
                        drawRect(
                            brush = Brush.horizontalGradient(
                                colors = listOf(
                                    Color(0xFFF2C14F).copy(alpha = 0.14f),
                                    Color.Transparent,
                                    Color(0xFFC9A227).copy(alpha = 0.1f)
                                )
                            )
                        )
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 18.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "VANTA · AUDIOPHILE CHANGER",
                            color = Color(0xFFF2D98A),
                            fontSize = 13.sp,
                            letterSpacing = 3.sp,
                            fontFamily = VantaSans,
                            fontWeight = FontWeight.Bold
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Tungsten status lamp — lit while the deck spins.
                            Canvas(modifier = Modifier.size(9.dp)) {
                                drawCircle(
                                    brush = Brush.radialGradient(
                                        colors = listOf(
                                            if (isPlaying) Color(0xFFFFEDB8) else Color(0xFF6A4E1E),
                                            if (isPlaying) Color(0xFFE2A227) else Color(0xFF33250F),
                                            Color.Transparent
                                        )
                                    ),
                                    radius = size.width / 2f,
                                    center = Offset(size.width / 2f, size.height / 2f)
                                )
                            }
                            Text(
                                text = frame.state.label,
                                color = if (isPlaying) Color(0xFFE3CF96) else Color(0xFF8C7D68),
                                fontSize = 11.sp,
                                letterSpacing = 2.sp,
                                fontFamily = VantaSans,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }

                Spacer(Modifier.height(10.dp))

            // Main machine deck: magazine | chamber | now playing
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                horizontalArrangement = Arrangement.spacedBy(18.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // LEFT — 45-rpm magnetic magazine (~22%)
                TvRecordMagazine(
                    queue = queue,
                    currentQueueIndex = currentIndex,
                    selectedIndex = currentIndex,
                    onSelectIndex = onPlayQueueIndex,
                    modifier = Modifier
                        .width(metrics.jukeboxMagazineWidth)
                        .fillMaxHeight()
                )

                // CENTER — turntable chamber (~56%)
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .padding(vertical = 14.dp),
                    contentAlignment = Alignment.Center
                ) {
                    // Walnut plinth with brushed reveal edges
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        drawRoundRect(
                            brush = Brush.radialGradient(
                                colors = listOf(Color(0xFF241B12), Color(0xFF150F09), Color(0xFF0C0906)),
                                center = Offset(size.width * 0.5f, size.height * 0.45f),
                                radius = size.width * 0.62f
                            ),
                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(28.dp.toPx())
                        )
                        // Top/highlight bevels catching the ceiling lamp
                        drawRoundRect(
                            brush = Brush.horizontalGradient(
                                listOf(Color(0xFF422F1C).copy(alpha = 0.55f), Color(0xFF24180E))
                            ),
                            topLeft = Offset.Zero,
                            size = Size(size.width, 3.dp.toPx()),
                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(3.dp.toPx())
                        )
                    }

                    // Huge cast platter — motor speed driven by the machine
                    TvTurntablePlatter(
                        artworkUrl = artworkUrl,
                        isPlaying = isPlaying && !frame.isChanging,
                        size = metrics.jukeboxPlatterSize,
                        discPresence = frame.discPresence,
                        motorSpeedOverride = frame.platterSpin,
                        modifier = Modifier.align(Alignment.Center)
                    )

                    // Damped tonearm — angle/cue driven by the machine while changing
                    TvTonearm(
                        isPlaying = isPlaying,
                        progress = progress,
                        isChangingDisc = frame.isChanging,
                        machineAngle = frame.tonearmAngle,
                        machineLift = frame.tonearmLifted,
                        modifier = Modifier.fillMaxSize()
                    )

                    // Record selector arm crossing the deck during transfers
                    if (frame.isTransferring) {
                        TvMechanicalSelector(
                            frame = frame,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }

                // RIGHT — smoked-glass now playing (~22%)
                TvNowPlayingPanel(
                    title = title,
                    artist = artist,
                    album = album,
                    artworkUrl = artworkUrl,
                    qualityInfo = qualityInfo,
                    positionMs = positionMs,
                    durationMs = durationMs,
                    isPlaying = isPlaying,
                    acousticness = acousticness,
                    modifier = Modifier
                        .width(metrics.jukeboxNowPlayingWidth)
                        .fillMaxHeight()
                )
            }

            Spacer(Modifier.height(8.dp))

            // BOTTOM — the amplifier sill: VU meters, tubes, transport keys
            Row(
                modifier = Modifier.fillMaxWidth().height(86.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TvVuMeters(
                        isPlaying = isPlaying,
                        meterWidth = metrics.jukeboxMeterWidth,
                        meterHeight = 58.dp,
                        audioFrame = audioFrame
                    )
                    TvVacuumTubes(
                        isPlaying = isPlaying && !frame.isChanging,
                        tubeWidth = 40.dp,
                        tubeHeight = 68.dp
                    )
                }
                // Center of the sill — the LED spectrum analyzer spanning
                // between the VU cluster and the transport, fed by the real
                // DSP FFT of whatever is actually on the platter.
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    contentAlignment = Alignment.Center
                ) {
                    TvSpectrumAnalyzer(
                        isPlaying = isPlaying && !frame.isChanging,
                        audioFrame = audioFrame,
                        modifier = Modifier.fillMaxWidth().height(54.dp)
                    )
                }
                TvJukeboxControls(
                    isPlaying = isPlaying,
                    isLiked = isLiked,
                    onPrevious = onPrevious,
                    onRewind = { onSeek?.invoke((positionMs - SEEK_STEP_MS).coerceAtLeast(0L)) },
                    onToggle = onToggle,
                    onFastForward = {
                        val max = if (durationMs > 0L) durationMs else positionMs + SEEK_STEP_MS
                        onSeek?.invoke((positionMs + SEEK_STEP_MS).coerceAtMost(max))
                    },
                    onNext = onNext,
                    onThumbsUp = onThumbsUp,
                    onThumbsDown = onThumbsDown
                )
            }
        }
        }
    }
}

/**
 * Registers a TV-owned analyzer with the shared holder so PlaybackService's
 * DSP pipeline pushes the real playback spectrum into it. Starts a soft
 * fallback pulse immediately so VU / LED meters move before the first DSP
 * frame; live spectrum replaces the fallback once updateFromDspSpectrum fires.
 */
@Composable
private fun rememberLiveAudioFrame(): VantaAudioFrame? {
    val scope = rememberCoroutineScope()
    val analyzer = remember {
        com.audiophile.musicplayer.audio.visualizer.VantaAudioAnalyzer(scope = scope)
    }
    DisposableEffect(Unit) {
        VantaAudioAnalyzerHolder.attach(analyzer)
        analyzer.ensureFallbackFrames()
        onDispose {
            VantaAudioAnalyzerHolder.detach(analyzer)
            analyzer.release()
        }
    }
    val frame by analyzer.audioFrame.collectAsState()
    return frame.takeIf { it.timestampMs > 0L }
}