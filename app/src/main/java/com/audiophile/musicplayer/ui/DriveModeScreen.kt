@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.audiophile.musicplayer.ui

import android.Manifest
import android.app.Activity
import android.view.WindowManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lyrics
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.audiophile.musicplayer.data.display.TrackDisplayResolver
import com.audiophile.musicplayer.data.local.entities.UnifiedTrack
import com.audiophile.musicplayer.data.dj.AiDjViewModel
import com.audiophile.musicplayer.data.dj.JukeboxCatalog
import com.audiophile.musicplayer.data.lyrics.LyricsData
import com.audiophile.musicplayer.data.source.isPlaylistCompilationArtifact
import com.audiophile.musicplayer.drive.DriveSpeedMonitor
import com.audiophile.musicplayer.playback.dsp.VantaEqualizerPreferences
import com.audiophile.musicplayer.playback.NowPlayingState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt

@Composable
fun DriveModeScreen(
    nowPlayingState: NowPlayingState,
    lyricsData: LyricsData?,
    pulseInsight: String?,
    pulseInsightLoading: Boolean,
    aiDjViewModel: AiDjViewModel,
    animatedArtworkEnabled: Boolean,
    onTogglePlayPause: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onOpenNowPlaying: () -> Unit,
    onOpenRadio: () -> Unit,
    onExitDrive: () -> Unit
) {
    val context = LocalContext.current
    val view = LocalView.current
    val aiDjState by aiDjViewModel.state.collectAsState()
    val speedMonitor = remember { DriveSpeedMonitor(context) }
    val speedMph by speedMonitor.speedMph.collectAsState()
    var showLyricsLine by remember { mutableStateOf(true) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) speedMonitor.start()
    }

    DisposableEffect(Unit) {
        val window = (context as? Activity)?.window
        window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        view.keepScreenOn = true
        onDispose {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            view.keepScreenOn = false
            speedMonitor.stop()
        }
    }

    LaunchedEffect(speedMonitor.hasPermission()) {
        if (speedMonitor.hasPermission()) {
            speedMonitor.start()
        } else {
            permissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

    val trackDisplay = remember(
        nowPlayingState.trackId,
        nowPlayingState.title,
        nowPlayingState.artist,
        nowPlayingState.album,
        nowPlayingState.artworkUrl
    ) {
        val trackId = nowPlayingState.trackId?.toLongOrNull()
        if (trackId != null && !nowPlayingState.title.isNullOrBlank()) {
            TrackDisplayResolver.resolve(
                UnifiedTrack(
                    trackId = trackId,
                    title = nowPlayingState.title,
                    artist = nowPlayingState.artist.orEmpty(),
                    albumName = nowPlayingState.album,
                    coverArtUrl = nowPlayingState.artworkUrl
                )
            )
        } else {
            null
        }
    }
    val heroArtworkUrl = trackDisplay?.artworkUrl?.takeIf { it.isNotBlank() }
        ?: nowPlayingState.artworkUrl?.takeIf { it.isNotBlank() }
    val hasTrack = nowPlayingState.trackId != null
    val isPlaylistArtifact = remember(
        nowPlayingState.title,
        nowPlayingState.artist,
        nowPlayingState.album,
        nowPlayingState.durationMs
    ) {
        val title = nowPlayingState.title.orEmpty()
        if (title.isBlank()) {
            false
        } else {
            isPlaylistCompilationArtifact(
                title = title,
                artist = nowPlayingState.artist.orEmpty(),
                album = nowPlayingState.album,
                durationMs = nowPlayingState.durationMs.takeIf { it > 0L }
            )
        }
    }
    val hasRealTrack = hasTrack && !isPlaylistArtifact
    val hasArtwork = !heroArtworkUrl.isNullOrBlank()
    val displayTitle = trackDisplay?.title?.takeIf { it.isNotBlank() }
        ?: if (hasTrack) nowPlayingState.title.orEmpty() else "Ready to drive"
    val displayArtist = trackDisplay?.artist?.takeIf { it.isNotBlank() }
        ?: if (hasTrack) nowPlayingState.artist.orEmpty() else "Pick a station below"
    val lyricsLine = rememberLyricsLine(lyricsData, nowPlayingState.positionMs, nowPlayingState.isPlaying)
    val activeStationId = aiDjState.session.currentSession?.stationId
    val activeStation = remember(activeStationId) { activeStationId?.let(JukeboxCatalog::findStation) }
    val stationLabel = activeStation?.name ?: aiDjState.session.currentSession?.title
    val stationEmoji = activeStation?.emoji
    val isStationSession = aiDjState.session.isStarted && activeStationId != null
    val isSessionLoading = aiDjState.session.isLoading
    val sessionStatus = aiDjState.statusMessage

    val driveDockClearance = 132.dp
    val systemBarBottom = with(LocalDensity.current) {
        WindowInsets.systemBars
            .only(WindowInsetsSides.Bottom)
            .getBottom(this)
            .toDp()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AppBackground)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = driveDockClearance + systemBarBottom)
        ) {
            DriveHeaderRow(
                speedMph = speedMph,
                hasLocationPermission = speedMonitor.hasPermission(),
                onRequestLocation = { permissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION) },
                onExitDrive = onExitDrive
            )

            Spacer(Modifier.height(14.dp))

            DriveHeroCard(
                displayTitle = displayTitle,
                displayArtist = displayArtist,
                stationLabel = stationLabel,
                stationEmoji = stationEmoji,
                heroArtworkUrl = heroArtworkUrl,
                hasArtwork = hasArtwork && !isPlaylistArtifact,
                hasTrack = hasRealTrack,
                isStationSession = isStationSession,
                isLoading = isSessionLoading,
                statusMessage = sessionStatus
            )

            Spacer(Modifier.height(16.dp))

            if (hasRealTrack && nowPlayingState.durationMs > 0L) {
                DriveProgressSection(
                    positionMs = nowPlayingState.positionMs,
                    durationMs = nowPlayingState.durationMs,
                    onSeekTo = onSeekTo
                )
                Spacer(Modifier.height(16.dp))
            }

            Text(
                text = "STATIONS",
                color = AppTextMuted,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.5.sp
            )
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                JukeboxCatalog.stations.take(8).forEach { station ->
                    DriveStationChip(
                        emoji = station.emoji,
                        label = station.name,
                        selected = station.id == activeStationId,
                        loading = isSessionLoading && station.id == activeStationId,
                        onClick = { aiDjViewModel.startJukeboxStation(station.id) }
                    )
                }
                DriveActionChip(label = "Pulse Live", onClick = aiDjViewModel::startPulseLive)
            }

            Spacer(Modifier.height(14.dp))

            if (pulseInsightLoading || !pulseInsight.isNullOrBlank()) {
                DriveInsightRow(text = pulseInsight, loading = pulseInsightLoading)
                Spacer(Modifier.height(10.dp))
            } else if (!aiDjState.liveCommentary.isNullOrBlank()) {
                DriveInsightRow(
                    text = aiDjState.liveCommentary,
                    loading = false,
                    label = if (aiDjState.commentaryFromPulseAi) "Pulse" else "DJ"
                )
                Spacer(Modifier.height(10.dp))
            }

            if (showLyricsLine && !lyricsLine.isNullOrBlank()) {
                DriveLyricsRow(lyricsLine)
            } else if (!hasRealTrack && (isSessionLoading || !sessionStatus.isNullOrBlank())) {
                DriveInsightRow(
                    text = sessionStatus ?: "Starting your mix…",
                    loading = isSessionLoading,
                    label = stationLabel ?: "Station"
                )
            } else if (!hasRealTrack) {
                Text(
                    text = "Choose a station to start a continuous mix from your library.",
                    color = AppTextMuted,
                    fontSize = 13.sp,
                    lineHeight = 18.sp
                )
            }
        }

        DriveControlDock(
            modifier = Modifier.align(Alignment.BottomCenter),
            nowPlayingState = nowPlayingState,
            isStationSession = isStationSession,
            showLyricsLine = showLyricsLine,
            onTogglePlayPause = onTogglePlayPause,
            onPrevious = onPrevious,
            onNext = onNext,
            onRefreshMix = aiDjViewModel::refreshMix,
            onToggleLyrics = { showLyricsLine = !showLyricsLine },
            onOpenNowPlaying = onOpenNowPlaying,
            onOpenRadio = onOpenRadio
        )
    }
}

@Composable
private fun DriveHeaderRow(
    speedMph: Float?,
    hasLocationPermission: Boolean,
    onRequestLocation: () -> Unit,
    onExitDrive: () -> Unit
) {
    var clock by remember { mutableStateOf(currentClockLabel()) }
    LaunchedEffect(Unit) {
        while (true) {
            clock = currentClockLabel()
            kotlinx.coroutines.delay(30_000L)
        }
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text("DRIVE", color = AppAccent, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
            Text(clock, color = AppTextMuted, fontSize = 13.sp)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            DriveSpeedChip(speedMph, hasLocationPermission, onRequestLocation)
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.08f))
                    .clickable(onClick = onExitDrive),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.Close, "Exit drive", tint = AppTextSecondary, modifier = Modifier.size(20.dp))
            }
        }
    }
}

@Composable
private fun DriveHeroCard(
    displayTitle: String,
    displayArtist: String,
    stationLabel: String?,
    stationEmoji: String?,
    heroArtworkUrl: String?,
    hasArtwork: Boolean,
    hasTrack: Boolean,
    isStationSession: Boolean,
    isLoading: Boolean,
    statusMessage: String?
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(280.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(AppSurfaceRaised)
            .border(0.5.dp, AppOutline, RoundedCornerShape(24.dp))
    ) {
        when {
            hasArtwork && heroArtworkUrl != null -> {
                NetworkArtwork(
                    artworkUrl = heroArtworkUrl,
                    seed = displayTitle,
                    modifier = Modifier.fillMaxSize()
                )
            }
            isStationSession && !hasTrack && !stationEmoji.isNullOrBlank() -> {
                DriveStationHeroPlaceholder(
                    emoji = stationEmoji,
                    stationName = stationLabel.orEmpty(),
                    modifier = Modifier.fillMaxSize()
                )
            }
            else -> {
                DriveDarkArtworkPlaceholder(modifier = Modifier.fillMaxSize())
            }
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color.Black.copy(alpha = 0.2f),
                            Color.Black.copy(alpha = 0.92f)
                        )
                    )
                )
        )
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(18.dp)
        ) {
            if (!stationLabel.isNullOrBlank() && isStationSession) {
                Text(
                    text = stationLabel.uppercase(),
                    color = AppAccent.copy(alpha = 0.85f),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.2.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(4.dp))
            }
            when {
                isLoading -> {
                    Text(
                        text = statusMessage ?: "Starting your mix…",
                        color = AppText,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                hasTrack -> {
                    Text(
                        text = displayTitle,
                        color = AppText,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = displayArtist,
                        color = AppTextSecondary,
                        fontSize = 16.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.basicMarquee()
                    )
                }
                isStationSession -> {
                    Text(
                        text = stationLabel ?: "Your station",
                        color = AppText,
                        fontSize = 26.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = statusMessage ?: "Tap play or wait for the mix to start",
                        color = AppTextSecondary,
                        fontSize = 14.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                else -> {
                    Text(
                        text = displayTitle,
                        color = AppText,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = displayArtist,
                        color = AppTextSecondary,
                        fontSize = 16.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            if (!statusMessage.isNullOrBlank() && !isLoading && hasTrack) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = statusMessage,
                    color = AppTextMuted,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun DriveDarkArtworkPlaceholder(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.background(
            Brush.verticalGradient(
                colors = listOf(Color(0xFF1A181C), Color(0xFF101018), Color(0xFF09090B))
            )
        ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            Icons.Filled.MusicNote,
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.16f),
            modifier = Modifier.size(72.dp)
        )
    }
}

@Composable
private fun DriveStationHeroPlaceholder(
    emoji: String,
    stationName: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.background(
            Brush.verticalGradient(
                colors = listOf(Color(0xFF1C1A24), Color(0xFF121018), Color(0xFF09090B))
            )
        ),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(emoji, fontSize = 64.sp)
            if (stationName.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stationName,
                    color = AppText.copy(alpha = 0.5f),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun DriveProgressSection(
    positionMs: Long,
    durationMs: Long,
    onSeekTo: (Long) -> Unit
) {
    var slider by remember(positionMs, durationMs) {
        mutableFloatStateOf(
            if (durationMs > 0L) (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f) else 0f
        )
    }
    var dragging by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Slider(
            value = if (dragging) slider else (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f),
            onValueChange = {
                dragging = true
                slider = it
            },
            onValueChangeFinished = {
                dragging = false
                onSeekTo((slider * durationMs).toLong())
            },
            colors = SliderDefaults.colors(
                thumbColor = AppAccent,
                activeTrackColor = AppAccent,
                inactiveTrackColor = Color.White.copy(alpha = 0.12f)
            )
        )
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(formatDriveTime(positionMs), color = AppTextMuted, fontSize = 11.sp)
            Text(formatDriveTime(durationMs), color = AppTextMuted, fontSize = 11.sp)
        }
    }
}

@Composable
private fun DriveControlDock(
    nowPlayingState: NowPlayingState,
    isStationSession: Boolean,
    showLyricsLine: Boolean,
    onTogglePlayPause: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onRefreshMix: () -> Unit,
    onToggleLyrics: () -> Unit,
    onOpenNowPlaying: () -> Unit,
    onOpenRadio: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    colors = listOf(Color.Transparent, AppBackground.copy(alpha = 0.92f), AppBackground)
                )
            )
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            DriveTransportButton(Icons.Filled.SkipPrevious, "Previous", 56.dp, onPrevious)
            Spacer(Modifier.width(28.dp))
            DriveTransportButton(
                icon = if (nowPlayingState.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                contentDescription = if (nowPlayingState.isPlaying) "Pause" else "Play",
                size = 76.dp,
                filled = true,
                onClick = onTogglePlayPause
            )
            Spacer(Modifier.width(28.dp))
            DriveTransportButton(Icons.Filled.SkipNext, "Next", 56.dp, onNext)
        }
        Spacer(Modifier.height(10.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            if (isStationSession) {
                DriveDockAction("Mix", Icons.Filled.Refresh, onRefreshMix)
            }
            DriveDockAction(
                if (showLyricsLine) "Lyrics" else "Lyrics off",
                Icons.Filled.Lyrics,
                onToggleLyrics
            )
            DriveDockAction("Player", Icons.Filled.MusicNote, onOpenNowPlaying)
            DriveDockAction("Radio", Icons.Filled.AutoAwesome, onOpenRadio)
        }
    }
}

@Composable
private fun DriveDockAction(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Icon(icon, label, tint = AppTextSecondary, modifier = Modifier.size(20.dp))
        Spacer(Modifier.height(4.dp))
        Text(label, color = AppTextMuted, fontSize = 10.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun DriveSpeedChip(speedMph: Float?, hasPermission: Boolean, onRequestPermission: () -> Unit) {
    val label = when {
        !hasPermission -> "GPS"
        speedMph == null -> "—"
        else -> "${speedMph.roundToInt()} mph"
    }
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Color.White.copy(alpha = 0.06f))
            .border(0.5.dp, AppOutline, RoundedCornerShape(12.dp))
            .clickable(enabled = !hasPermission, onClick = onRequestPermission)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Icon(Icons.Filled.Speed, null, tint = AppAccent.copy(alpha = 0.8f), modifier = Modifier.size(16.dp))
        Text(label, color = AppText, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun DriveStationChip(
    emoji: String,
    label: String,
    selected: Boolean,
    loading: Boolean = false,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .background(if (selected) AppAccent.copy(alpha = 0.2f) else Color.White.copy(alpha = 0.06f))
            .border(0.5.dp, if (selected) AppAccent.copy(alpha = 0.5f) else AppOutline, RoundedCornerShape(14.dp))
            .clickable(enabled = !loading, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (loading) {
            CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                color = AppAccent,
                strokeWidth = 2.dp
            )
        } else {
            Text(emoji, fontSize = 18.sp)
        }
        Text(
            label,
            color = if (selected) AppAccent else AppTextSecondary,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun DriveActionChip(label: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .background(AppAccentSoft)
            .border(0.5.dp, AppAccent.copy(alpha = 0.25f), RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 18.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = AppAccent, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun DriveInsightRow(text: String?, loading: Boolean, label: String = "Pulse") {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color.White.copy(alpha = 0.05f))
            .padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Filled.AutoAwesome, null, tint = AppAccent.copy(alpha = 0.75f), modifier = Modifier.size(16.dp))
        Text(label.uppercase(), color = AppAccent.copy(alpha = 0.65f), fontSize = 9.sp, fontWeight = FontWeight.Bold)
        Text(
            if (loading) "Thinking…" else text.orEmpty(),
            color = AppTextSecondary,
            fontSize = 13.sp,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun DriveLyricsRow(line: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color.White.copy(alpha = 0.05f))
            .padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Filled.Lyrics, null, tint = AppAccent.copy(alpha = 0.7f), modifier = Modifier.size(16.dp))
        Text(line, color = AppText, fontSize = 15.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun DriveTransportButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    size: androidx.compose.ui.unit.Dp,
    onClick: () -> Unit,
    filled: Boolean = false
) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(if (filled) AppAccent else Color.White.copy(alpha = 0.1f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            icon,
            contentDescription,
            tint = if (filled) AppBackground else AppText,
            modifier = Modifier.size(if (filled) 38.dp else 28.dp)
        )
    }
}

@Composable
private fun rememberLyricsLine(lyricsData: LyricsData?, positionMs: Long, isPlaying: Boolean): String? {
    val context = LocalContext.current
    val pipelineLeadMs = remember { VantaEqualizerPreferences(context).lyricsPipelineLeadMs() }
    var livePositionMs by remember { mutableLongStateOf(positionMs) }
    var anchorPositionMs by remember { mutableLongStateOf(positionMs.coerceAtLeast(0L)) }
    var anchorWallMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var lastReportedPositionMs by remember { mutableLongStateOf(positionMs) }

    LaunchedEffect(isPlaying) {
        if (!isPlaying) {
            livePositionMs = positionMs
            anchorPositionMs = positionMs.coerceAtLeast(0L)
            anchorWallMs = System.currentTimeMillis()
            lastReportedPositionMs = positionMs
            return@LaunchedEffect
        }
        anchorPositionMs = positionMs.coerceAtLeast(0L)
        anchorWallMs = System.currentTimeMillis()
        lastReportedPositionMs = positionMs
        while (true) {
            livePositionMs = anchorPositionMs + (System.currentTimeMillis() - anchorWallMs)
            kotlinx.coroutines.delay(50L)
        }
    }

    SideEffect {
        if (!isPlaying) {
            livePositionMs = positionMs
            lastReportedPositionMs = positionMs
            return@SideEffect
        }
        val predictedMs = anchorPositionMs + (System.currentTimeMillis() - anchorWallMs)
        val stepMs = positionMs - lastReportedPositionMs
        val driftMs = kotlin.math.abs(positionMs - predictedMs)
        if (stepMs < 0L || stepMs > 2_000L || driftMs > 1_500L) {
            anchorPositionMs = positionMs.coerceAtLeast(0L)
            anchorWallMs = System.currentTimeMillis()
            livePositionMs = positionMs
        }
        lastReportedPositionMs = positionMs
    }

    val adjustedPositionMs = (livePositionMs + pipelineLeadMs).coerceAtLeast(0L)
    return try {
        if (lyricsData == null || lyricsData.lines.isEmpty()) null
        else if (!lyricsData.isSynced) lyricsData.lines.firstOrNull()?.text?.takeIf { it.isNotBlank() }
        else {
            val line = lyricsData.lines.lastOrNull { line ->
                val start = line.startTimeMs
                start != null && start <= adjustedPositionMs
            }
            line?.text?.takeIf { it.isNotBlank() }
        }
    } catch (_: Exception) {
        null
    }
}

private fun formatDriveTime(ms: Long): String {
    val totalSeconds = TimeUnit.MILLISECONDS.toSeconds(ms.coerceAtLeast(0L))
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}

private fun currentClockLabel(): String = SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date())
