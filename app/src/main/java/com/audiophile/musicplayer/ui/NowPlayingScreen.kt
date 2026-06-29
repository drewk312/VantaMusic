@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
package com.audiophile.musicplayer.ui

import android.util.Log
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.audiophile.musicplayer.data.display.VantaQualityInfo
import com.audiophile.musicplayer.data.metadata.EnhancedMetadata
import com.audiophile.musicplayer.data.lyrics.LyricsData
import com.audiophile.musicplayer.playback.NowPlayingState
import com.audiophile.musicplayer.playback.QueueSnapshot
import com.audiophile.musicplayer.ui.nowplaying.*
import com.audiophile.musicplayer.audio.visualizer.VantaAuraState
import com.audiophile.musicplayer.audio.visualizer.VantaAudioFrame
import com.audiophile.musicplayer.ui.visualizer.VantaAuraBackground
import com.audiophile.musicplayer.ui.visualizer.VantaBeatProgressBar
import com.audiophile.musicplayer.ui.visualizer.VantaVisualizerViewModel
import com.audiophile.musicplayer.ui.VantaType
import com.audiophile.musicplayer.ui.visualizer.VantaBeatOrb
import com.audiophile.musicplayer.playback.UpnpCastingHolder
import com.audiophile.musicplayer.ui.AppTextMuted
import com.audiophile.musicplayer.ui.AppTextSecondary
import com.audiophile.musicplayer.audio.visualizer.AuraPalette
import com.audiophile.musicplayer.ui.VantaCompactQualityChip

fun formatDuration(valueMs: Long): String = com.audiophile.musicplayer.ui.nowplaying.formatDuration(valueMs)



@Composable
fun NowPlayingScreen(
    nowPlayingState: NowPlayingState,
    enhancedMetadata: EnhancedMetadata?,
    lyricsData: LyricsData?,
    lyricsTrackId: String?,
    lyricsLoading: Boolean = false,
    translationEnabled: Boolean = false,
    onToggleTranslation: () -> Unit = {},
    onRetryLyrics: () -> Unit = {},
    queueSnapshot: QueueSnapshot,
    onBack: () -> Unit,
    onToggleFavorite: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onPlayNextQueue: () -> Unit = {},
    onMoveQueueItem: (Int) -> Unit = {},
    onRemoveQueueItem: (Int) -> Unit = {},
    onNavigateToArtist: (String, String?) -> Unit = { _, _ -> },
    onNavigateToAlbum: (String, String, String?, Int?, String?, Boolean?) -> Unit = { _, _, _, _, _, _ -> },
    onOpenTrackSheet: () -> Unit = {},
    onOpenDj: () -> Unit = {},
    onOpenEqualizer: () -> Unit = {},
    onOpenQueueTrackSheet: ((com.audiophile.musicplayer.data.local.entities.UnifiedTrackWithSources) -> Unit)? = null,
    animatedArtworkEnabled: Boolean = true,
    statusMessage: String = "",
    auraState: VantaAuraState? = null,
    audioFrame: VantaAudioFrame? = null,
    visualizerViewModel: VantaVisualizerViewModel? = null,
    nowPlayingViewModel: com.audiophile.musicplayer.playback.NowPlayingViewModel? = null
) {
    var mode by rememberSaveable { mutableStateOf(NowPlayingMode.ARTWORK) }
    var showQualityDetails by remember { mutableStateOf(false) }
    var showCastSheet by remember { mutableStateOf(false) }
    var visibleStatusMessage by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(statusMessage) {
        val cleanStatus = statusMessage.trim()
        if (cleanStatus.isBlank() || cleanStatus.startsWith("Playing ", ignoreCase = true)) {
            visibleStatusMessage = null; return@LaunchedEffect
        }
        visibleStatusMessage = cleanStatus
        kotlinx.coroutines.delay(2400)
        visibleStatusMessage = null
    }

    val resolvedDisplaySnapshot = remember(
        nowPlayingState.trackId, nowPlayingState.canonicalTrackId, nowPlayingState.preferredExternalTrackId,
        nowPlayingState.title, nowPlayingState.artist, nowPlayingState.album, nowPlayingState.versionLabel,
        nowPlayingState.artworkUrl, nowPlayingState.qualityInfo, nowPlayingState.queuePosition,
        enhancedMetadata, lyricsTrackId
    ) { resolveNowPlayingDisplaySnapshot(nowPlayingState, enhancedMetadata, lyricsTrackId) }
    var lastUsableDisplaySnapshot by remember { mutableStateOf<NowPlayingDisplaySnapshot?>(null) }
    LaunchedEffect(resolvedDisplaySnapshot) {
        if (resolvedDisplaySnapshot.source != "unknown_fallback") {
            lastUsableDisplaySnapshot = resolvedDisplaySnapshot
        }
    }
    val displaySnapshot = if (resolvedDisplaySnapshot.source == "unknown_fallback") {
        lastUsableDisplaySnapshot ?: resolvedDisplaySnapshot
    } else {
        resolvedDisplaySnapshot
    }
    val displayTitle = displaySnapshot.title
    val displayArtist = displaySnapshot.artist
    val displayAlbum = displaySnapshot.album
    val mediaId = displaySnapshot.mediaId

    var previousLoggedMediaId by remember { mutableStateOf<String?>(null) }
    var previousLoggedTitle by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(
        mediaId,
        displaySnapshot.title,
        displaySnapshot.artist,
        displaySnapshot.album,
        displaySnapshot.lyricsTrackId,
        displaySnapshot.queueIndex,
        displaySnapshot.source
    ) {
        Log.d("VANTA_NOWPLAYING_STATE", "trackId=${displaySnapshot.trackId ?: "null"} mediaId=${mediaId ?: "null"} title=${displaySnapshot.title} artist=${displaySnapshot.artist} album=${displaySnapshot.album ?: ""} lyricsTrackId=${displaySnapshot.lyricsTrackId ?: "null"} artworkTrackId=${displaySnapshot.trackId ?: "null"} queueIndex=${displaySnapshot.queueIndex} source=${displaySnapshot.source}")
        if (previousLoggedMediaId != null && previousLoggedMediaId == mediaId && previousLoggedTitle != null && previousLoggedTitle != displaySnapshot.title) {
            Log.w("VANTA_NOWPLAYING_ANOMALY", "title_changed_same_mediaId=true mediaId=${mediaId ?: "null"} oldTitle=${previousLoggedTitle} newTitle=${displaySnapshot.title}")
        }
        previousLoggedMediaId = mediaId; previousLoggedTitle = displaySnapshot.title
        Log.d("VANTA_METADATA_FINAL", "title='${displaySnapshot.title}' artist='${displaySnapshot.artist}' album='${displaySnapshot.album ?: ""}' artwork=${displaySnapshot.artworkUrl != null} sourceProvider='${displaySnapshot.source}'")
    }

    val resolvedArtworkUrl = displaySnapshot.artworkUrl
    val hasArtwork = !resolvedArtworkUrl.isNullOrBlank()
    val artworkSeed = "${nowPlayingState.title ?: ""} ${nowPlayingState.artist ?: ""}"
    val artworkColors = rememberArtworkGradientColors(artworkUrl = resolvedArtworkUrl, seed = artworkSeed)
    val screenAccent = if (hasArtwork) artworkColors.accentColor else Color(0xFFB8A77F)
    val screenTopColor = if (hasArtwork) artworkColors.topColor else Color(0xFF1A1218)
    val screenMidColor = if (hasArtwork) artworkColors.midColor else Color(0xFF0D0A0C)

    val animatedAccentColor by animateColorAsState(targetValue = screenAccent, animationSpec = tween(900), label = "ambientAccentColor")
    val animatedTopColorForBg by animateColorAsState(targetValue = screenTopColor, animationSpec = tween(900), label = "ambientTopColor")
    val animatedMidColorForBg by animateColorAsState(targetValue = screenMidColor, animationSpec = tween(900), label = "ambientMidColor")

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val isWide = maxWidth > maxHeight
        val isCompact = maxHeight < 680.dp

        val effectiveAuraPalette = remember(auraState?.palette, screenAccent) {
            auraState?.palette?.forArtwork(listOf(screenAccent)) ?: com.audiophile.musicplayer.audio.visualizer.AuraPalette()
        }

        val permissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
            androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
        ) { isGranted ->
            if (isGranted) {
                // If granted, we can restart the visualizer polling or analyzer attachment.
                visualizerViewModel?.setPlaying(nowPlayingState.isPlaying)
            }
        }

        LaunchedEffect(nowPlayingState.trackId) {
            visualizerViewModel?.setTrackId(nowPlayingState.trackId?.toLongOrNull())
        }
        LaunchedEffect(nowPlayingState.isPlaying) {
            if (nowPlayingState.isPlaying && visualizerViewModel?.hasRecordAudioPermission() == false) {
                permissionLauncher.launch(android.Manifest.permission.RECORD_AUDIO)
            }
            visualizerViewModel?.setPlaying(nowPlayingState.isPlaying)
        }

        Box(modifier = Modifier.fillMaxSize()) {
            LuxuryBlurredArtworkBackground(
                artworkUrl = resolvedArtworkUrl, seed = artworkSeed,
                topColor = animatedTopColorForBg, midColor = animatedMidColorForBg, accentColor = animatedAccentColor,
                modifier = Modifier.fillMaxSize()
            )
            if (auraState != null && audioFrame != null && auraState.isActive && auraState.audioReactiveEnabled) {
                VantaAuraBackground(
                    palette = effectiveAuraPalette,
                    audioFrame = audioFrame,
                    isPlaying = nowPlayingState.isPlaying,
                    reducedMotion = auraState.reducedMotion,
                    intensityModifier = if (mode == NowPlayingMode.LYRICS) 0.45f else 1.0f,
                    modifier = Modifier.fillMaxSize()
                )
            }
            if (isWide) {
                WideNowPlayingContent(
                    nowPlayingState = nowPlayingState, enhancedMetadata = enhancedMetadata,
                    lyricsData = lyricsData, lyricsTrackId = lyricsTrackId, lyricsLoading = lyricsLoading,
                    translationEnabled = translationEnabled, onToggleTranslation = onToggleTranslation,
                    onRetryLyrics = onRetryLyrics,
                    queueSnapshot = queueSnapshot, mode = mode, onModeChange = { mode = it },
                    onBack = onBack, onToggleFavorite = onToggleFavorite, onTogglePlayPause = onTogglePlayPause,
                    onPrevious = onPrevious, onNext = onNext, onSeekTo = onSeekTo,
                    onMoveQueueItem = onMoveQueueItem, onRemoveQueueItem = onRemoveQueueItem,
                    onNavigateToArtist = onNavigateToArtist, onNavigateToAlbum = onNavigateToAlbum,
                    onOpenTrackSheet = onOpenTrackSheet, onOpenDj = onOpenDj, onOpenEqualizer = onOpenEqualizer,
                    onOpenQueueTrackSheet = onOpenQueueTrackSheet,
                    onOpenCast = { showCastSheet = true },
                    displayTitle = displayTitle, displayArtist = displayArtist, displayAlbum = displayAlbum,
                    hasArtwork = hasArtwork,
                    animatedArtworkEnabled = animatedArtworkEnabled, posterAccentColor = screenAccent,
                    displaySnapshot = displaySnapshot, onOpenQualityDetails = { showQualityDetails = true },
                    audioFrame = audioFrame, auraEnabled = auraState?.isActive == true, reducedMotion = auraState?.reducedMotion == true,
                    auraState = auraState, effectiveAuraPalette = effectiveAuraPalette,
                    nowPlayingViewModel = nowPlayingViewModel
                )
            } else {
                PortraitNowPlayingContent(
                    nowPlayingState = nowPlayingState, enhancedMetadata = enhancedMetadata,
                    lyricsData = lyricsData, lyricsTrackId = lyricsTrackId, lyricsLoading = lyricsLoading,
                    translationEnabled = translationEnabled, onToggleTranslation = onToggleTranslation,
                    onRetryLyrics = onRetryLyrics,
                    queueSnapshot = queueSnapshot, mode = mode, onModeChange = { mode = it },
                    onBack = onBack, onToggleFavorite = onToggleFavorite, onTogglePlayPause = onTogglePlayPause,
                    onPrevious = onPrevious, onNext = onNext, onSeekTo = onSeekTo,
                    onMoveQueueItem = onMoveQueueItem, onRemoveQueueItem = onRemoveQueueItem,
                    onNavigateToArtist = onNavigateToArtist, onNavigateToAlbum = onNavigateToAlbum,
                    onOpenTrackSheet = onOpenTrackSheet, onOpenDj = onOpenDj, onOpenEqualizer = onOpenEqualizer,
                    onOpenQueueTrackSheet = onOpenQueueTrackSheet,
                    onOpenCast = { showCastSheet = true },
                    displayTitle = displayTitle, displayArtist = displayArtist, displayAlbum = displayAlbum,
                    hasArtwork = hasArtwork,
                    animatedArtworkEnabled = animatedArtworkEnabled, posterAccentColor = screenAccent,
                    displaySnapshot = displaySnapshot, onOpenQualityDetails = { showQualityDetails = true },
                    audioFrame = audioFrame, auraEnabled = auraState?.isActive == true, reducedMotion = auraState?.reducedMotion == true,
                    auraState = auraState, effectiveAuraPalette = effectiveAuraPalette,
                    nowPlayingViewModel = nowPlayingViewModel,
                    isCompact = isCompact
                )
            }
        }

        NowPlayingStatusSignal(message = visibleStatusMessage, accentColor = screenAccent,
            modifier = Modifier.align(Alignment.TopCenter).padding(start = 24.dp, end = 24.dp, top = if (isWide) 24.dp else 86.dp).zIndex(100f))

        val qualityInfo = nowPlayingState.qualityInfo
        if (showQualityDetails && qualityInfo != null) {
            QualityDetailsSheet(qualityInfo = qualityInfo, accentColor = screenAccent, trackTitle = displayTitle, trackArtist = displayArtist, onDismiss = { showQualityDetails = false })
        }

        if (showCastSheet) {
            val castingManager = UpnpCastingHolder.manager
            if (castingManager != null) {
                @OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
                androidx.compose.material3.ModalBottomSheet(
                    onDismissRequest = { showCastSheet = false },
                    containerColor = com.audiophile.musicplayer.ui.AppSurface
                ) {
                    CastDeviceSheet(
                        castingManager = castingManager,
                        streamUrl = null,
                        onDismiss = { showCastSheet = false }
                    )
                }
            }
        }
    }
}

@Composable
private fun WideNowPlayingContent(
    nowPlayingState: NowPlayingState, enhancedMetadata: EnhancedMetadata?,
    lyricsData: LyricsData?, lyricsTrackId: String?, lyricsLoading: Boolean,
    translationEnabled: Boolean = false, onToggleTranslation: () -> Unit = {},
    onRetryLyrics: () -> Unit,
    queueSnapshot: QueueSnapshot, mode: NowPlayingMode, onModeChange: (NowPlayingMode) -> Unit,
    onBack: () -> Unit, onToggleFavorite: () -> Unit, onTogglePlayPause: () -> Unit,
    onPrevious: () -> Unit, onNext: () -> Unit, onSeekTo: (Long) -> Unit,
    onMoveQueueItem: (Int) -> Unit, onRemoveQueueItem: (Int) -> Unit,
    onNavigateToArtist: (String, String?) -> Unit, onNavigateToAlbum: (String, String, String?, Int?, String?, Boolean?) -> Unit,
    onOpenTrackSheet: () -> Unit, onOpenDj: () -> Unit, onOpenEqualizer: () -> Unit,
    onOpenQueueTrackSheet: ((com.audiophile.musicplayer.data.local.entities.UnifiedTrackWithSources) -> Unit)?,
    onOpenCast: () -> Unit,
    displayTitle: String, displayArtist: String, displayAlbum: String?,
    hasArtwork: Boolean, animatedArtworkEnabled: Boolean, posterAccentColor: Color,
    displaySnapshot: NowPlayingDisplaySnapshot, onOpenQualityDetails: () -> Unit,
    audioFrame: VantaAudioFrame? = null, auraEnabled: Boolean = false, reducedMotion: Boolean = true,
    auraState: VantaAuraState? = null, effectiveAuraPalette: AuraPalette? = null,
    nowPlayingViewModel: com.audiophile.musicplayer.playback.NowPlayingViewModel? = null
) {
    val canPlayNext = nowPlayingState.queuePosition < nowPlayingState.queueSize - 1
    val canPlayPrevious = nowPlayingState.queuePosition > 0
    val effectiveLyricsData = lyricsData?.takeIf { displaySnapshot.canDisplayLyrics }
    val bottomInsetDp = with(LocalDensity.current) { WindowInsets.systemBars.only(WindowInsetsSides.Bottom).getBottom(this).toDp() }

    Row(
        modifier = Modifier.fillMaxSize().padding(start = 18.dp, end = 18.dp, top = 24.dp, bottom = bottomInsetDp + 18.dp).zIndex(10f),
        horizontalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        Box(modifier = Modifier.weight(0.62f).fillMaxHeight(), contentAlignment = Alignment.Center) {
            if (mode == NowPlayingMode.ARTWORK && auraState != null && audioFrame != null && auraState.isActive && auraState.audioReactiveEnabled) {
                VantaBeatOrb(
                    palette = effectiveAuraPalette ?: AuraPalette(),
                    audioFrame = audioFrame,
                    isPlaying = nowPlayingState.isPlaying,
                    reducedMotion = auraState.reduceMotionInCar || !animatedArtworkEnabled,
                    modifier = Modifier.fillMaxSize()
                )
            }
            when (mode) {
                NowPlayingMode.ARTWORK -> VantaArtworkStage(
                    coverArtUrl = displaySnapshot.artworkUrl, seed = displayTitle, hasArtwork = hasArtwork,
                    isPlaying = nowPlayingState.isPlaying, animatedArtworkEnabled = animatedArtworkEnabled,
                    accentColor = posterAccentColor, isFavorite = nowPlayingState.isFavorite,
                    onToggleFavorite = onToggleFavorite, modifier = Modifier.fillMaxSize()
                )
                NowPlayingMode.LYRICS -> VantaLyricsStage(
                    lyricsData = effectiveLyricsData,
                    lyricsLoading = lyricsLoading || lyricsTrackId != null && !displaySnapshot.canDisplayLyrics,
                    nowPlayingState = nowPlayingState, displayTitle = displayTitle, displayArtist = displayArtist,
                    displayAlbum = displayAlbum, qualityInfo = displaySnapshot.qualityInfo,
                    translationEnabled = translationEnabled, onToggleTranslation = onToggleTranslation,
                    onSeekTo = onSeekTo, onRetryLyrics = onRetryLyrics,
                    onOpenQualityDetails = onOpenQualityDetails, modifier = Modifier.fillMaxSize()
                )
                NowPlayingMode.QUEUE -> Box(modifier = Modifier.fillMaxSize()) {
                    QueueView(queueSnapshot = queueSnapshot, onMoveQueueItem = onMoveQueueItem,
                        onRemoveQueueItem = onRemoveQueueItem, onOpenTrackSheet = onOpenQueueTrackSheet, modifier = Modifier.fillMaxSize())
                }
            }
        }

        Column(modifier = Modifier.weight(0.38f).fillMaxHeight(), verticalArrangement = Arrangement.SpaceBetween) {
            VantaSceneHeaderRow(centerLabel = "VANTA LISTENING", titleForMenu = displaySnapshot.title,
                onBack = onBack, onOpenEqualizer = onOpenEqualizer, onOpenTrackSheet = onOpenTrackSheet, onOpenCast = onOpenCast)
            Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
                CleanProgressSection(state = nowPlayingState, accentColor = posterAccentColor, onSeekTo = onSeekTo, audioFrame = audioFrame, auraEnabled = auraEnabled, reducedMotion = reducedMotion)
                Spacer(Modifier.height(10.dp))
                Text(displayTitle, style = VantaType.editorialHero.copy(fontSize = 24.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(4.dp))
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(displayArtist, style = VantaType.subtitle.copy(color = Color.White.copy(alpha = 0.7f)), maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false).clickable { Log.d("VANTA_UI_ACTION", "control='nowplaying_artist' result='tap'"); onNavigateToArtist(displayArtist, null) })
                    val albumText = (displayAlbum ?: enhancedMetadata?.album)?.takeIf { it.isNotBlank() && !it.equals(displayTitle, ignoreCase = true) }
                    if (albumText != null) {
                        Spacer(Modifier.width(8.dp))
                        Text("•", style = VantaType.caption.copy(color = Color.White.copy(alpha = 0.3f)))
                        Spacer(Modifier.width(8.dp))
                        Text(albumText, style = VantaType.caption.copy(color = Color.White.copy(alpha = 0.5f)), maxLines = 1, overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false).clickable { Log.d("VANTA_UI_ACTION", "control='nowplaying_album' result='tap'"); onNavigateToAlbum(albumText, enhancedMetadata?.artist ?: displayArtist, enhancedMetadata?.artworkUrl, enhancedMetadata?.releaseYear, enhancedMetadata?.genres?.firstOrNull(), enhancedMetadata?.explicit ?: false) })
                    }
                    val pq = nowPlayingState.qualityInfo
                    if (pq != null && shouldShowQualityChip(pq)) {
                        Spacer(Modifier.width(10.dp))
                        NowPlayingQualitySignal(qualityInfo = pq, onClick = onOpenQualityDetails)
                    }
                }
                Spacer(Modifier.height(14.dp))
                LuxuryControlsRow(isPlaying = nowPlayingState.isPlaying, canPlayPrevious = canPlayPrevious, canPlayNext = canPlayNext, onPrevious = onPrevious, onTogglePlayPause = onTogglePlayPause, onNext = onNext)
                Spacer(Modifier.height(12.dp))
                PortraitUtilityBar(mode = mode, displayTitle = displayTitle, displayArtist = displayArtist, onModeChange = onModeChange)
            }
        }
    }
}

@Composable
private fun PortraitNowPlayingContent(
    nowPlayingState: NowPlayingState, enhancedMetadata: EnhancedMetadata?,
    lyricsData: LyricsData?, lyricsTrackId: String?, lyricsLoading: Boolean,
    translationEnabled: Boolean = false, onToggleTranslation: () -> Unit = {},
    onRetryLyrics: () -> Unit,
    queueSnapshot: QueueSnapshot, mode: NowPlayingMode, onModeChange: (NowPlayingMode) -> Unit,
    onBack: () -> Unit, onToggleFavorite: () -> Unit, onTogglePlayPause: () -> Unit,
    onPrevious: () -> Unit, onNext: () -> Unit, onSeekTo: (Long) -> Unit,
    onMoveQueueItem: (Int) -> Unit, onRemoveQueueItem: (Int) -> Unit,
    onNavigateToArtist: (String, String?) -> Unit, onNavigateToAlbum: (String, String, String?, Int?, String?, Boolean?) -> Unit,
    onOpenTrackSheet: () -> Unit, onOpenDj: () -> Unit, onOpenEqualizer: () -> Unit,
    onOpenQueueTrackSheet: ((com.audiophile.musicplayer.data.local.entities.UnifiedTrackWithSources) -> Unit)?,
    onOpenCast: () -> Unit,
    displayTitle: String, displayArtist: String, displayAlbum: String?,
    hasArtwork: Boolean, animatedArtworkEnabled: Boolean, posterAccentColor: Color,
    displaySnapshot: NowPlayingDisplaySnapshot, onOpenQualityDetails: () -> Unit,
    audioFrame: VantaAudioFrame? = null, auraEnabled: Boolean = false, reducedMotion: Boolean = true,
    auraState: VantaAuraState? = null, effectiveAuraPalette: AuraPalette? = null,
    nowPlayingViewModel: com.audiophile.musicplayer.playback.NowPlayingViewModel? = null,
    isCompact: Boolean = false
) {
    val canPlayNext = nowPlayingState.queuePosition < nowPlayingState.queueSize - 1
    val canPlayPrevious = nowPlayingState.queuePosition > 0
    val effectiveLyricsData = lyricsData?.takeIf { displaySnapshot.canDisplayLyrics }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val screenMaxHeight = maxHeight; val compact = screenMaxHeight < 720.dp
        val horizontalPadding = if (compact) 18.dp else 24.dp
        val stageTopPadding = if (compact) 0.dp else 4.dp
        val bottomInsetDp = with(LocalDensity.current) { WindowInsets.systemBars.only(WindowInsetsSides.Bottom).getBottom(this).toDp() }
        val artSize = minOf(maxWidth - horizontalPadding * 2, 380.dp, screenMaxHeight * 0.42f)

        Column(modifier = Modifier.fillMaxSize().padding(top = if (compact) 0.dp else 4.dp, bottom = bottomInsetDp + 12.dp).zIndex(10f)) {
            VantaSceneHeaderRow(centerLabel = "NOW PLAYING", titleForMenu = displaySnapshot.title,
                onBack = onBack, onOpenEqualizer = onOpenEqualizer, onOpenTrackSheet = onOpenTrackSheet,
                onOpenCast = onOpenCast,
                modifier = Modifier.padding(horizontal = horizontalPadding))

            val stageModifier = if (mode == NowPlayingMode.ARTWORK) {
                Modifier
                    .fillMaxWidth()
                    .height(artSize + if (compact) 2.dp else 8.dp)
            } else {
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
            }
            Box(modifier = stageModifier.padding(horizontal = horizontalPadding).padding(top = stageTopPadding, bottom = 4.dp)) {
                if (mode == NowPlayingMode.ARTWORK && auraState != null && audioFrame != null && auraState.isActive && auraState.audioReactiveEnabled) {
                    VantaBeatOrb(
                        palette = effectiveAuraPalette ?: AuraPalette(),
                        audioFrame = audioFrame,
                        isPlaying = nowPlayingState.isPlaying,
                        reducedMotion = auraState.reduceMotionInCar || !animatedArtworkEnabled,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                when (mode) {
                    NowPlayingMode.ARTWORK -> VantaArtworkStage(
                        coverArtUrl = displaySnapshot.artworkUrl, seed = displayTitle, hasArtwork = hasArtwork,
                        isPlaying = nowPlayingState.isPlaying, animatedArtworkEnabled = animatedArtworkEnabled,
                        accentColor = posterAccentColor, isFavorite = nowPlayingState.isFavorite,
                        onToggleFavorite = onToggleFavorite, artworkSize = artSize, modifier = Modifier.fillMaxSize()
                    )
                    NowPlayingMode.LYRICS -> VantaLyricsStage(
                        lyricsData = effectiveLyricsData,
                        lyricsLoading = lyricsLoading || lyricsTrackId != null && !displaySnapshot.canDisplayLyrics,
                        nowPlayingState = nowPlayingState, displayTitle = displayTitle, displayArtist = displayArtist,
                        displayAlbum = displayAlbum, qualityInfo = displaySnapshot.qualityInfo,
                        translationEnabled = translationEnabled, onToggleTranslation = onToggleTranslation,
                        onSeekTo = onSeekTo, onRetryLyrics = onRetryLyrics,
                        onOpenQualityDetails = onOpenQualityDetails, modifier = Modifier.fillMaxSize()
                    )
                    NowPlayingMode.QUEUE -> Box(modifier = Modifier.fillMaxSize()) {
                        QueueView(queueSnapshot = queueSnapshot, onMoveQueueItem = onMoveQueueItem,
                            onRemoveQueueItem = onRemoveQueueItem, onOpenTrackSheet = onOpenQueueTrackSheet, modifier = Modifier.fillMaxSize())
                    }
                }
            }

            Column(modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = horizontalPadding), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Spacer(Modifier.weight(1f))
                CleanProgressSection(state = nowPlayingState, accentColor = posterAccentColor, onSeekTo = onSeekTo, audioFrame = audioFrame, auraEnabled = auraEnabled, reducedMotion = reducedMotion)
                Spacer(Modifier.height(16.dp))
                Text(displayTitle, style = VantaType.editorialHero.copy(fontSize = if (compact) 24.sp else 30.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(if (compact) 2.dp else 4.dp))
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(displayArtist, style = VantaType.sectionTitle.copy(color = Color.White.copy(alpha = 0.75f), fontSize = if (compact) 15.sp else 17.sp), maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false).clickable { Log.d("VANTA_UI_ACTION", "control='nowplaying_artist' result='tap'"); onNavigateToArtist(displayArtist, null) })
                    val albumText = (displayAlbum ?: enhancedMetadata?.album)?.takeIf { it.isNotBlank() && !it.equals(displayTitle, ignoreCase = true) }
                    if (albumText != null) {
                        Spacer(Modifier.width(8.dp))
                        Text("•", style = VantaType.subtitle.copy(color = Color.White.copy(alpha = 0.3f)))
                        Spacer(Modifier.width(8.dp))
                        Text(albumText, style = VantaType.subtitle.copy(color = Color.White.copy(alpha = 0.5f), fontSize = if (compact) 13.sp else 15.sp), maxLines = 1, overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false).clickable { Log.d("VANTA_UI_ACTION", "control='nowplaying_album' result='tap'"); onNavigateToAlbum(albumText, enhancedMetadata?.artist ?: displayArtist, enhancedMetadata?.artworkUrl, enhancedMetadata?.releaseYear, enhancedMetadata?.genres?.firstOrNull(), enhancedMetadata?.explicit ?: false) })
                    }
                    val pq = nowPlayingState.qualityInfo
                    if (pq != null && shouldShowQualityChip(pq)) {
                        Spacer(Modifier.width(10.dp))
                        NowPlayingQualitySignal(qualityInfo = pq, onClick = onOpenQualityDetails)
                    }
                }
                Spacer(Modifier.height(if (compact) 16.dp else 32.dp))
                LuxuryControlsRow(isPlaying = nowPlayingState.isPlaying, canPlayPrevious = canPlayPrevious, canPlayNext = canPlayNext, onPrevious = onPrevious, onTogglePlayPause = onTogglePlayPause, onNext = onNext)
                Spacer(Modifier.height(if (compact) 16.dp else 32.dp))
                PortraitUtilityBar(mode = mode, displayTitle = displayTitle, displayArtist = displayArtist, onModeChange = onModeChange)
                Spacer(Modifier.height(if (compact) 12.dp else 24.dp))
            }
        }
    }
}



@Composable
private fun VantaSceneHeaderRow(centerLabel: String, titleForMenu: String, onBack: () -> Unit, onOpenEqualizer: () -> Unit, onOpenTrackSheet: () -> Unit, modifier: Modifier = Modifier, onOpenCast: () -> Unit = {}) {
    Row(modifier = modifier.fillMaxWidth().height(50.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "Back", tint = AppText,
            modifier = Modifier.size(40.dp).clip(CircleShape).clickable { Log.d("VANTA_UI_ACTION", "control='nowplaying_back' result='tap'"); onBack() }.padding(8.dp))
        Text(centerLabel, color = Color.White.copy(alpha = 0.48f), fontSize = 10.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 2.2.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
        Row(horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Tune, contentDescription = "Equalizer", tint = AppAccent.copy(alpha = 0.85f),
                modifier = Modifier.size(40.dp).clip(CircleShape).clickable { Log.d("VANTA_UI_ACTION", "control='nowplaying_equalizer' result='tap'"); onOpenEqualizer() }.padding(8.dp))
            Icon(Icons.Filled.Cast, contentDescription = "Cast", tint = AppAccent.copy(alpha = 0.7f),
                modifier = Modifier.size(40.dp).clip(CircleShape).clickable { Log.d("VANTA_UI_ACTION", "control='nowplaying_cast' result='tap'"); onOpenCast() }.padding(8.dp))
            Icon(Icons.Filled.MoreVert, contentDescription = "More", tint = AppText,
                modifier = Modifier.size(40.dp).clip(CircleShape).clickable { Log.d("VANTA_UI_ACTION", "control='nowplaying_more' result='tap'"); Log.d("VANTA_ACTION_MENU", "opened track='${titleForMenu}'"); onOpenTrackSheet() }.padding(8.dp))
        }
    }
}


