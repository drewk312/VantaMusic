package com.audiophile.musicplayer.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.TextButton
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.audiophile.musicplayer.data.display.TrackDisplayResolver
import com.audiophile.musicplayer.data.dj.AiDjFeedback
import com.audiophile.musicplayer.data.dj.DjCompanionMode
import com.audiophile.musicplayer.data.dj.AiDjMode
import com.audiophile.musicplayer.data.dj.AiDjPick
import com.audiophile.musicplayer.data.dj.AiDjSegment
import com.audiophile.musicplayer.data.dj.AiDjViewModel
import com.audiophile.musicplayer.data.dj.JukeboxCatalog
import com.audiophile.musicplayer.data.dj.PulseListeningStyle
import com.audiophile.musicplayer.ui.preview.ArtworkPlaceholder
import com.audiophile.musicplayer.radio.VibeTranslator

@Composable
fun AiDjScreen(
    viewModel: AiDjViewModel,
    onBack: () -> Unit,
    onOpenStation: (String) -> Unit = {},
    onStartStreamingStation: (String) -> Unit = {},
    onStartJukeboxStation: (String) -> Unit = {},
    miniPlayerVisible: Boolean = false,
    bottomNavVisible: Boolean = true
) {
    val state by viewModel.state.collectAsState()
    val sessionState = state.session
    val nowPlaying by viewModel.nowPlayingState.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.onDjScreenOpened()
    }

    BackHandler(enabled = true) {
        if (sessionState.isStarted) {
            viewModel.resetSession()
        } else {
            onBack()
        }
    }

    val chromeBottomPadding = appOverlayBottomPadding(
        miniPlayerVisible = miniPlayerVisible,
        bottomNavVisible = bottomNavVisible
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AppBackground)
            .padding(top = if (sessionState.isStarted) 0.dp else appTopContentPadding(extra = 8.dp))
            .padding(bottom = chromeBottomPadding)
    ) {
        if (!sessionState.isStarted) {
            PulseHubScreen(
                tasteSummary = buildTasteSummary(sessionState.tasteProfile),
                statusMessage = state.statusMessage,
                displayName = state.listenerDisplayName,
                savedStationIds = viewModel.savedStationIds(),
                customStations = state.customStations,
                suggestedArtists = viewModel.suggestedStationArtists(),
                nowPlayingTitle = nowPlaying.title,
                nowPlayingArtist = nowPlaying.artist,
                hasNowPlaying = nowPlaying.trackId != null,
                stationLabel = viewModel::stationLabel,
                onStartPulseLive = {
                    Log.d("VANTA_RADIO", "action=pulse_live")
                    onStartStreamingStation("continuous mix across genres")
                },
                onStartReleaseRadar = {
                    Log.d("VANTA_RADIO", "action=release_radar")
                    onStartStreamingStation("new releases")
                },
                onStartMoodSession = { query ->
                    val cleaned = query.trim()
                    if (cleaned.isNotBlank()) {
                        val translated = VibeTranslator.extractSearchQueries(cleaned).firstOrNull()?.takeIf { it.isNotBlank() } ?: cleaned
                        Log.d("VANTA_RADIO", "action=mood_session input='$cleaned' translated='$translated'")
                        onStartStreamingStation(translated)
                    }
                },
                onCompanionPrompt = { prompt ->
                    val cleaned = prompt.trim()
                    if (cleaned.isNotBlank()) {
                        val translated = VibeTranslator.extractSearchQueries(cleaned).firstOrNull()?.takeIf { it.isNotBlank() } ?: cleaned
                        Log.d("VANTA_RADIO", "action=companion_prompt input='$cleaned' translated='$translated'")
                        onStartStreamingStation(translated)
                    }
                },
                companionMode = state.companionMode,
                onStartForgottenFavorites = {
                    Log.d("VANTA_RADIO", "action=forgotten_favorites")
                    onStartStreamingStation("rediscover classic songs")
                },
                onStartMadeForYou = {
                    Log.d("VANTA_RADIO", "action=made_for_you")
                    onStartStreamingStation("daily mix")
                },
                onStartDiscover = {
                    Log.d("VANTA_RADIO", "action=discover")
                    onStartStreamingStation("discover new music")
                },
                onStartJukebox = { stationId ->
                    Log.d("VANTA_RADIO", "action=jukebox stationId=$stationId")
                    onStartJukeboxStation(stationId)
                },
                onStartEra = { era ->
                    Log.d("VANTA_RADIO", "action=era era=$era")
                    onStartStreamingStation("${era}s hits")
                },
                onStartGenre = { genre ->
                    Log.d("VANTA_RADIO", "action=genre genre=$genre")
                    onStartStreamingStation(genre)
                },
                onStartArtistStation = { artist ->
                    Log.d("VANTA_RADIO", "action=artist_station artist=$artist")
                    onStartStreamingStation(artist)
                },
                onStartSongStation = {
                    val title = nowPlaying.title?.takeIf { it.isNotBlank() }
                    val artist = nowPlaying.artist?.takeIf { it.isNotBlank() } ?: ""
                    if (!title.isNullOrBlank()) {
                        val query = if (artist.isNotBlank()) "songs like $title by $artist" else title
                        Log.d("VANTA_RADIO", "action=song_station title='$title' artist='$artist' query='$query'")
                        onStartStreamingStation(query)
                    }
                },
                onCreateCustomStation = { name, artists, eraId, genreId ->
                    val query = buildCustomStationQuery(name, artists, eraId, genreId)
                    Log.d("VANTA_RADIO", "action=custom_station query='$query'")
                    onStartStreamingStation(query)
                },
                onBack = onBack
            )
        } else {
            SessionScreen(
                segment = sessionState.currentSegment,
                sessionTitle = sessionState.currentSession?.title,
                listeningStyle = sessionState.currentSession?.listeningStyle,
                totalSegments = sessionState.currentSession?.segments?.size ?: 1,
                listenerDisplayName = state.listenerDisplayName,
                isLoading = sessionState.isLoading,
                nowPlaying = nowPlaying,
                liveCommentary = state.liveCommentary,
                isPulseAiActive = state.isPulseAiActive,
                commentaryFromPulseAi = state.commentaryFromPulseAi,
                isGeneratingCommentary = state.isGeneratingCommentary,
                djVoiceEnabled = state.djVoiceEnabled,
                companionMode = state.companionMode,
                onNextSegment = viewModel::nextSegment,
                onRefreshMix = viewModel::refreshMix,
                onRecordFeedback = viewModel::recordFeedback,
                onSwitchVibe = viewModel::switchVibe,
                onStayHere = viewModel::stayHere,
                onChangeItUp = viewModel::changeItUp,
                onNeverPlay = viewModel::neverPlayCurrent,
                onSaveStation = viewModel::saveStation,
                onPlayTrack = viewModel::playTrackAt,
                onPrevious = viewModel::previousTrack,
                onTogglePlayback = viewModel::togglePlayback,
                onNext = viewModel::nextTrack,
                onThumbsUp = viewModel::thumbsUpCurrentTrack,
                onThumbsDown = viewModel::thumbsDownCurrentTrack,
                onSeek = viewModel::seekTo,
                onToggleDjVoice = viewModel::toggleDjVoice,
                onSpeakCommentary = viewModel::speakCurrentCommentary,
                onRefreshPulseStatus = viewModel::refreshPulseAiStatus
            )
        }
    }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun PulseHubScreen(
    tasteSummary: String,
    statusMessage: String?,
    displayName: String?,
    savedStationIds: List<String>,
    customStations: List<com.audiophile.musicplayer.data.dj.JukeboxStation>,
    suggestedArtists: List<String>,
    nowPlayingTitle: String?,
    nowPlayingArtist: String?,
    hasNowPlaying: Boolean,
    stationLabel: (String) -> String,
    onStartPulseLive: () -> Unit,
    onStartReleaseRadar: () -> Unit,
    onStartMoodSession: (String) -> Unit,
    onCompanionPrompt: (String) -> Unit,
    companionMode: DjCompanionMode,
    onStartForgottenFavorites: () -> Unit,
    onStartMadeForYou: () -> Unit,
    onStartDiscover: () -> Unit,
    onStartJukebox: (String) -> Unit,
    onStartEra: (String) -> Unit,
    onStartGenre: (String) -> Unit,
    onStartArtistStation: (String) -> Unit,
    onStartSongStation: () -> Unit,
    onCreateCustomStation: (String, List<String>, String?, String?) -> Unit,
    onBack: () -> Unit
) {
    val madeForLabel = displayName?.takeIf { it.isNotBlank() } ?: "You"
    var showCreateDialog by remember { mutableStateOf(false) }
    var showMoodDialog by remember { mutableStateOf(false) }
    var moodQuery by remember { mutableStateOf("") }

    if (showCreateDialog) {
        CreateCustomStationDialog(
            suggestedArtists = suggestedArtists,
            onDismiss = { showCreateDialog = false },
            onCreate = { name, artists, eraId, genreId ->
                showCreateDialog = false
                onCreateCustomStation(name, artists, eraId, genreId)
            }
        )
    }

    if (showMoodDialog) {
        AlertDialog(
            onDismissRequest = { showMoodDialog = false; moodQuery = "" },
            title = { Text("Mood Discovery", color = AppText, fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text("Describe the vibe you want:", color = AppTextSecondary, fontSize = 13.sp)
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = moodQuery,
                        onValueChange = { text ->
                            try {
                                moodQuery = text
                            } catch (e: Exception) {
                                Log.e("VANTA_RADIO_INPUT", "Input handler crashed", e)
                            }
                        },
                        placeholder = { Text("e.g. something like Boards of Canada but darker", color = AppTextMuted, fontSize = 14.sp) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = AppText,
                            unfocusedTextColor = AppText,
                            focusedBorderColor = AppAccent,
                            unfocusedBorderColor = AppOutline
                        )
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (moodQuery.isNotBlank()) {
                            showMoodDialog = false
                            onStartMoodSession(moodQuery.trim())
                            moodQuery = ""
                        }
                    },
                    enabled = moodQuery.isNotBlank()
                ) { Text("Find music", color = AppAccent, fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { showMoodDialog = false; moodQuery = "" }) {
                    Text("Cancel", color = AppTextSecondary)
                }
            },
            containerColor = AppSurfaceRaised
        )
    }

    // LazyColumn + nested LazyRow rows — never LazyRow inside verticalScroll (crashes at runtime).
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.Start
    ) {
        item {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = AppText,
                    modifier = Modifier
                        .size(32.dp)
                        .clickable(onClick = onBack)
                        .padding(4.dp)
                )
                Spacer(Modifier.width(16.dp))
                Text("Pulse", color = AppText, fontSize = 28.sp, fontWeight = FontWeight.Black)
            }
        }

        item {
            Text(
                text = companionMode.label.uppercase(),
                color = AppTextMuted,
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 1.2.sp,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val prompts = listOf(
                    "More like this",
                    "Change the vibe",
                    "Explain this song",
                    "Make it darker",
                    "Late night",
                    "Less talking"
                )
                items(prompts) { prompt ->
                    Text(
                        text = prompt,
                        color = AppText,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(Color.White.copy(alpha = 0.08f))
                            .border(0.5.dp, AppOutline.copy(alpha = 0.45f), RoundedCornerShape(50))
                            .clickable { onCompanionPrompt(prompt) }
                            .padding(horizontal = 14.dp, vertical = 8.dp)
                    )
                }
            }
            Spacer(Modifier.height(18.dp))
        }

        if (!statusMessage.isNullOrBlank()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(AppAccentSoft)
                        .border(0.5.dp, AppAccent.copy(alpha = 0.18f), RoundedCornerShape(18.dp))
                        .padding(horizontal = 16.dp, vertical = 13.dp)
                ) {
                    Text(
                        text = statusMessage,
                        color = AppTextSecondary,
                        fontSize = 13.sp,
                        lineHeight = 18.sp
                    )
                }
            }
        }

        item {
            Text("YOUR LISTENING ROOM", color = AppAccentSecondary, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.4.sp)
            Spacer(Modifier.height(12.dp))
            PulseHeroCard(
                tag = "PULSE LIVE",
                title = "Live DJ",
                subtitle = "A private concert that evolves — chapters, bridges, and pockets you can stay in.",
                gradient = listOf(AppSurfaceVariant, AppSurfaceRaised, AppBackground),
                onClick = onStartPulseLive
            )
        }

        item {
            Spacer(Modifier.height(22.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(AppSurfaceRaised)
                    .border(0.5.dp, AppAccent.copy(alpha = 0.10f), RoundedCornerShape(20.dp))
                    .padding(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 18.dp)
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier
                                .width(3.dp)
                                .height(16.dp)
                                .background(AppAccentSecondary.copy(alpha = 0.6f), RoundedCornerShape(2.dp))
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("STATIONS", style = VantaType.caption.copy(color = AppAccentSecondary, letterSpacing = 1.2.sp))
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Curated endless mixes from your library — no ads, skip anytime, always know what's playing.",
                        style = VantaType.editorialSmall
                    )
                    Spacer(Modifier.height(14.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(JukeboxCatalog.stations, key = { it.id }) { station ->
                            StationCard(
                                emoji = station.emoji,
                                name = station.name,
                                description = station.description,
                                onClick = { onStartJukebox(station.id) }
                            )
                        }
                    }
                }
            }
        }

        item {
            Spacer(Modifier.height(22.dp))
            PulseHeroCard(
                tag = "MADE FOR ${madeForLabel.uppercase()}",
                title = "Your daily mix",
                subtitle = "Short sets shaped by what you save, finish, and skip.",
                gradient = listOf(AppSurfaceRaised, AppBackground),
                accent = AppAccentSecondary,
                onClick = onStartMadeForYou,
                trailingIcon = Icons.Filled.AutoAwesome
            )
            Spacer(Modifier.height(14.dp))
            PulseHeroCard(
                tag = "DISCOVER WEEKLY",
                title = "New music, built around you",
                subtitle = "Fresh order each week — avoids repeats from this session.",
                gradient = listOf(AppSurface, AppBackground),
                accent = AppAccent,
                onClick = onStartDiscover,
                trailingIcon = Icons.Filled.Explore,
                compact = true
            )
        }

        // — Release Radar + Mood Discovery + Forgotten Favorites —
        item {
            Spacer(Modifier.height(18.dp))
            PulseHeroCard(
                tag = "RELEASE RADAR",
                title = "Fresh from your artists",
                subtitle = "New and recent tracks from your favorite artists and similar sounds.",
                gradient = listOf(AppSurfaceRaised, AppBackground),
                accent = AppAccentSecondary,
                onClick = onStartReleaseRadar,
                trailingIcon = Icons.Filled.AutoAwesome,
                compact = true
            )
            Spacer(Modifier.height(12.dp))
            PulseHeroCard(
                tag = "MOOD DISCOVERY",
                title = "Describe the vibe",
                subtitle = "Find songs matching any mood or description — upbeat, dark, dreamy, anything.",
                gradient = listOf(AppSurfaceRaised, AppBackground),
                accent = AppAccent,
                onClick = { showMoodDialog = true },
                trailingIcon = Icons.Filled.Audiotrack,
                compact = true
            )
            Spacer(Modifier.height(12.dp))
            PulseHeroCard(
                tag = "FORGOTTEN FAVORITES",
                title = "Rediscover old gems",
                subtitle = "Tracks you loved but haven't heard in a while.",
                gradient = listOf(AppSurface, AppBackground),
                accent = AppAccent,
                onClick = onStartForgottenFavorites,
                trailingIcon = Icons.Filled.Favorite,
                compact = true
            )
        }

        item {
            Spacer(Modifier.height(22.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .width(3.dp)
                        .height(16.dp)
                        .background(AppAccentSecondary.copy(alpha = 0.5f), RoundedCornerShape(2.dp))
                )
                Spacer(Modifier.width(8.dp))
                Text("ERAS", style = VantaType.caption.copy(color = AppAccentSecondary, letterSpacing = 1.2.sp))
            }
            Spacer(Modifier.height(12.dp))
        }

        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                items(JukeboxCatalog.eras) { era ->
                    EraBadge(
                        label = era.label,
                        onClick = { onStartEra(era.id) }
                    )
                }
            }
        }

        item {
            Spacer(Modifier.height(22.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .width(3.dp)
                        .height(16.dp)
                        .background(AppAccentSecondary.copy(alpha = 0.5f), RoundedCornerShape(2.dp))
                )
                Spacer(Modifier.width(8.dp))
                Text("GENRES", style = VantaType.caption.copy(color = AppAccentSecondary, letterSpacing = 1.2.sp))
            }
            Spacer(Modifier.height(12.dp))
        }

        item {
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                JukeboxCatalog.genres.forEachIndexed { i, genre ->
                    GenreTag(
                        label = genre.label,
                        accent = GenreAccents[i % GenreAccents.size],
                        onClick = { onStartGenre(genre.id) }
                    )
                }
            }
        }

        item {
            Spacer(Modifier.height(22.dp))
            Text("BUILD YOUR STATION", color = AppTextMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.1.sp)
            Spacer(Modifier.height(8.dp))
            Text(
                "Seed from artists, the song playing now, or mix multiple artists with an era or genre.",
                color = AppTextSecondary,
                fontSize = 13.sp,
                lineHeight = 18.sp
            )
            Spacer(Modifier.height(12.dp))
        }

        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    EraGenreChip(label = "+ Custom mix", onClick = { showCreateDialog = true })
                }
                if (hasNowPlaying && !nowPlayingTitle.isNullOrBlank()) {
                    item {
                        EraGenreChip(
                            label = "Like \"${nowPlayingTitle.take(18)}${if (nowPlayingTitle.length > 18) "…" else ""}\"",
                            onClick = onStartSongStation
                        )
                    }
                }
                items(suggestedArtists.take(6)) { artist ->
                    EraGenreChip(label = artist, onClick = { onStartArtistStation(artist) })
                }
            }
        }

        if (customStations.isNotEmpty()) {
            item {
                Spacer(Modifier.height(22.dp))
                Text("YOUR STATIONS", color = AppTextMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.1.sp)
                Spacer(Modifier.height(10.dp))
            }
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(customStations, key = { it.id }) { station ->
                        JukeboxStationChip(
                            emoji = station.emoji,
                            name = station.name,
                            onClick = { onStartJukebox(station.id) }
                        )
                    }
                }
            }
        }

        if (savedStationIds.isNotEmpty()) {
            item {
                Spacer(Modifier.height(22.dp))
                Text("SAVED STATIONS", color = AppTextMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.1.sp)
                Spacer(Modifier.height(10.dp))
            }
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(savedStationIds, key = { it }) { id ->
                        EraGenreChip(label = stationLabel(id), onClick = { onStartJukebox(id) })
                    }
                }
            }
        }

        item {
            Spacer(Modifier.height(22.dp))
            Text("TASTE PROFILE", color = AppTextMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.1.sp)
            Spacer(Modifier.height(6.dp))
            Text(tasteSummary, color = AppTextSecondary, fontSize = 13.sp, lineHeight = 19.sp)
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun PulseHeroCard(
    tag: String,
    title: String,
    subtitle: String,
    gradient: List<Color>,
    onClick: () -> Unit,
    accent: Color = AppAccentSecondary,
    trailingIcon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    compact: Boolean = false
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(if (compact) 148.dp else 190.dp)
            .clip(RoundedCornerShape(if (compact) 20.dp else 24.dp))
            .background(Brush.linearGradient(gradient))
            .border(0.5.dp, AppOutline.copy(alpha = .5f), RoundedCornerShape(if (compact) 20.dp else 24.dp))
            .clickable(onClick = onClick)
            .padding(20.dp)
    ) {
        Column(Modifier.align(Alignment.BottomStart).padding(end = if (trailingIcon != null) 48.dp else 0.dp)) {
            Text(tag, color = accent, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
            Spacer(Modifier.height(if (compact) 7.dp else 8.dp))
            Text(title, color = AppText, fontSize = if (compact) 20.sp else 24.sp, fontWeight = FontWeight.Black)
            Spacer(Modifier.height(5.dp))
            Text(subtitle, color = AppTextSecondary, fontSize = 13.sp, lineHeight = 18.sp, maxLines = 3)
        }
        if (trailingIcon != null) {
            Icon(trailingIcon, contentDescription = null, tint = accent, modifier = Modifier.align(Alignment.TopEnd).size(30.dp))
        } else {
            Box(Modifier.align(Alignment.TopEnd).size(52.dp).clip(CircleShape).background(AppAccent), contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.PlayArrow, contentDescription = "Start", tint = Color.Black, modifier = Modifier.size(30.dp))
            }
        }
    }
}

private val StationAccents = listOf(
    Color(0xFFD4A574), Color(0xFF7BC8C8), Color(0xFFC89BC8), Color(0xFFFF8C8C),
    Color(0xFF8CBD8C), Color(0xFFE8B88A), Color(0xFF9BB8E8), Color(0xFFE8C88A),
    Color(0xFFA88CC8), Color(0xFFE8A88A), Color(0xFFFFD4A5), Color(0xFF8CC8A8)
)

private val GenreAccents = listOf(
    Color(0xFFE8A88A), Color(0xFFD4C574), Color(0xFFC89BC8), Color(0xFF7BC8C8),
    Color(0xFF8CA8D4), Color(0xFFE8D48A), Color(0xFF88D4A8), Color(0xFFD48CA8),
    Color(0xFFA8C888), Color(0xFFC8A888)
)

@Composable
private fun StationCard(emoji: String, name: String, description: String, onClick: () -> Unit) {
    val accent = remember { StationAccents[name.hashCode().mod(StationAccents.size).let { if (it < 0) it + StationAccents.size else it }] }
    Column(
        modifier = Modifier
            .width(130.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(AppSurface)
            .border(0.5.dp, accent.copy(alpha = 0.18f), RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .padding(top = 18.dp, bottom = 14.dp, start = 10.dp, end = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(accent.copy(alpha = 0.10f))
                .border(0.5.dp, accent.copy(alpha = 0.15f), RoundedCornerShape(14.dp)),
            contentAlignment = Alignment.Center
        ) {
            Text(emoji, fontSize = 22.sp)
        }
        Spacer(Modifier.height(10.dp))
        Text(name, color = AppText, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis, lineHeight = 17.sp)
        Spacer(Modifier.height(3.dp))
        Text(
            description.take(32).let { if (it.length < description.length) "$it…" else it },
            color = AppTextMuted,
            fontSize = 10.sp,
            textAlign = TextAlign.Center,
            maxLines = 1,
            lineHeight = 13.sp,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun EraBadge(label: String, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(AppAccent.copy(alpha = 0.06f))
            .border(0.5.dp, AppAccent.copy(alpha = 0.12f), RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 22.dp, vertical = 14.dp)
    ) {
        Text(
            label.replace("s", ""),
            color = AppAccent,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.5.sp
        )
        Text(
            "s",
            color = AppAccent.copy(alpha = 0.5f),
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            letterSpacing = 2.sp
        )
    }
}

@Composable
private fun JukeboxStationChip(emoji: String, name: String, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .width(108.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(AppSurface)
            .border(0.5.dp, AppOutline.copy(alpha = .45f), RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(emoji, fontSize = 22.sp)
        Spacer(Modifier.height(8.dp))
        Text(name, color = AppText, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun CreateCustomStationDialog(
    suggestedArtists: List<String>,
    onDismiss: () -> Unit,
    onCreate: (String, List<String>, String?, String?) -> Unit
) {
    var stationName by remember { mutableStateOf("") }
    val selectedArtists = remember { mutableStateListOf<String>() }
    var selectedEraId by remember { mutableStateOf<String?>(null) }
    var selectedGenreId by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Custom station", color = AppText, fontWeight = FontWeight.Bold)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = stationName,
                    onValueChange = { text ->
                        try {
                            stationName = text
                        } catch (e: Exception) {
                            Log.e("VANTA_RADIO_INPUT", "Input handler crashed", e)
                        }
                    },
                    label = { Text("Station name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = AppText,
                        unfocusedTextColor = AppText,
                        focusedBorderColor = AppAccent,
                        unfocusedBorderColor = AppOutline
                    )
                )
                Text("Pick artists", color = AppTextSecondary, fontSize = 12.sp)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(suggestedArtists) { artist ->
                        val selected = artist in selectedArtists
                        Text(
                            text = artist,
                            color = if (selected) Color.Black else AppText,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier
                                .clip(RoundedCornerShape(20.dp))
                                .background(if (selected) AppAccent else AppSurface)
                                .border(
                                    0.5.dp,
                                    if (selected) AppAccent else AppOutline.copy(alpha = .4f),
                                    RoundedCornerShape(20.dp)
                                )
                                .clickable {
                                    if (selected) selectedArtists.remove(artist)
                                    else selectedArtists.add(artist)
                                }
                                .padding(horizontal = 14.dp, vertical = 8.dp)
                        )
                    }
                }
                Text("Optional era", color = AppTextSecondary, fontSize = 12.sp)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(JukeboxCatalog.eras) { era ->
                        val selected = selectedEraId == era.id
                        EraGenreChip(
                            label = era.label,
                            onClick = {
                                selectedEraId = if (selected) null else era.id
                            },
                            selected = selected
                        )
                    }
                }
                Text("Optional genre", color = AppTextSecondary, fontSize = 12.sp)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(JukeboxCatalog.genres) { genre ->
                        val selected = selectedGenreId == genre.id
                        EraGenreChip(
                            label = genre.label,
                            onClick = {
                                selectedGenreId = if (selected) null else genre.id
                            },
                            selected = selected
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onCreate(
                        stationName,
                        selectedArtists.toList(),
                        selectedEraId,
                        selectedGenreId
                    )
                },
                enabled = selectedArtists.isNotEmpty()
            ) {
                Text("Start station", color = AppAccent, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = AppTextSecondary)
            }
        },
        containerColor = AppSurfaceRaised
    )
}

@Composable
private fun EraGenreChip(label: String, onClick: () -> Unit, selected: Boolean = false) {
    Text(
        text = label,
        color = if (selected) Color.Black else AppText,
        fontSize = 13.sp,
        fontWeight = FontWeight.Medium,
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(if (selected) AppAccent else AppSurface)
            .border(
                0.5.dp,
                if (selected) AppAccent else AppOutline.copy(alpha = .4f),
                RoundedCornerShape(20.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp)
    )
}

@Composable
private fun GenreTag(label: String, accent: Color, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(accent.copy(alpha = 0.08f))
            .border(0.5.dp, accent.copy(alpha = 0.15f), RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 10.dp)
    ) {
        Text(
            text = label,
            color = accent.copy(alpha = 0.85f),
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            letterSpacing = 0.3.sp
        )
    }
}

@Composable
private fun SessionScreen(
    segment: AiDjSegment?,
    sessionTitle: String?,
    listeningStyle: PulseListeningStyle?,
    totalSegments: Int,
    listenerDisplayName: String?,
    isLoading: Boolean,
    nowPlaying: com.audiophile.musicplayer.playback.NowPlayingState,
    liveCommentary: String?,
    isPulseAiActive: Boolean,
    commentaryFromPulseAi: Boolean,
    isGeneratingCommentary: Boolean,
    djVoiceEnabled: Boolean,
    companionMode: com.audiophile.musicplayer.data.dj.DjCompanionMode,
    onNextSegment: () -> Unit,
    onRefreshMix: () -> Unit,
    onRecordFeedback: (AiDjFeedback) -> Unit,
    onSwitchVibe: () -> Unit,
    onStayHere: () -> Unit,
    onChangeItUp: () -> Unit,
    onNeverPlay: () -> Unit,
    onSaveStation: () -> Unit,
    onPlayTrack: (com.audiophile.musicplayer.data.local.entities.UnifiedTrackWithSources) -> Unit,
    onPrevious: () -> Unit,
    onTogglePlayback: () -> Unit,
    onNext: () -> Unit,
    onThumbsUp: () -> Unit,
    onThumbsDown: () -> Unit,
    onSeek: (Long) -> Unit,
    onToggleDjVoice: () -> Unit,
    onSpeakCommentary: () -> Unit,
    onRefreshPulseStatus: () -> Unit
) {
    LaunchedEffect(Unit) { onRefreshPulseStatus() }
    if (segment == null || isLoading) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                AudioWaveformVisualizer(
                    modifier = Modifier.width(80.dp).height(32.dp),
                    color = AppAccent
                )
                Text(
                    text = if (isLoading) "Mixing your next set…" else "No active session",
                    style = VantaType.editorialSmall,
                    textAlign = TextAlign.Center
                )
            }
        }
        return
    }

    val headline = when {
        sessionTitle != null -> sessionTitle.substringBefore(" —").trim()
        listenerDisplayName != null -> listenerDisplayName
        else -> "Your set"
    }

    val isStationMode = listeningStyle == PulseListeningStyle.ENDLESS_JUKEBOX ||
        listeningStyle == PulseListeningStyle.ERA ||
        listeningStyle == PulseListeningStyle.GENRE

    val styleLabel = when (listeningStyle) {
        PulseListeningStyle.PULSE_LIVE -> "Pulse Live"
        PulseListeningStyle.ENDLESS_JUKEBOX -> "Station"
        PulseListeningStyle.MADE_FOR_YOU -> "Made for you"
        PulseListeningStyle.ERA -> "Era mix"
        PulseListeningStyle.GENRE -> "Genre mix"
        PulseListeningStyle.RELEASE_RADAR -> "Release Radar"
        PulseListeningStyle.MOOD -> "Mood"
        PulseListeningStyle.FORGOTTEN_FAVORITES -> "Forgotten"
        null -> "Radio"
    }

    val subtitle = if (isStationMode) {
        "Continuous mix · no ads"
    } else {
        "$styleLabel · Chapter $totalSegments"
    }

    Box(modifier = Modifier.fillMaxSize().background(AppBackground)) {
        AmbientArtworkBackdrop(
            artworkUrl = nowPlaying.artworkUrl,
            seed = nowPlaying.title ?: "VANTA"
        )

        Column(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .padding(horizontal = VantaSpacing.screenHorizontal)
            ) {
                Spacer(Modifier.height(12.dp))

                SessionHeader(
                    headline = headline,
                    subtitle = subtitle,
                    isPulseActive = isPulseAiActive,
                    isStationMode = isStationMode,
                    onSwitchVibe = onSwitchVibe,
                    onRefreshMix = onRefreshMix
                )

                Spacer(Modifier.height(14.dp))

                ListenerControlsRow(
                    onStayHere = onStayHere,
                    onChangeItUp = onChangeItUp,
                    onNeverPlay = onNeverPlay,
                    onSaveStation = onSaveStation,
                    showSaveStation = listeningStyle == PulseListeningStyle.ENDLESS_JUKEBOX ||
                        listeningStyle == PulseListeningStyle.ERA ||
                        listeningStyle == PulseListeningStyle.GENRE
                )

                Spacer(Modifier.height(16.dp))

                NowPlayingMoment(nowPlaying = nowPlaying)

                Spacer(Modifier.height(22.dp))

                if (!liveCommentary.isNullOrBlank()) {
                    PulseWhisperCard(
                        commentary = liveCommentary,
                        isGenerating = isGeneratingCommentary,
                        fromPulse = commentaryFromPulseAi,
                        companionMode = companionMode,
                        onCycleCompanionMode = onToggleDjVoice
                    )
                    Spacer(Modifier.height(28.dp))
                } else {
                    Spacer(Modifier.height(8.dp))
                }

                ComingUpSection(
                    picks = segment.picks,
                    nowPlayingTrackId = nowPlaying.trackId,
                    onLoadMore = onNextSegment,
                    onPlayTrack = onPlayTrack
                )

                Spacer(Modifier.height(16.dp))
            }

            RadioControlStrip(
                nowPlaying = nowPlaying,
                onPrevious = onPrevious,
                onTogglePlayback = onTogglePlayback,
                onNext = onNext,
                onThumbsUp = onThumbsUp,
                onThumbsDown = onThumbsDown,
                onSeek = onSeek
            )
        }
    }
}

@Composable
private fun ListenerControlsRow(
    onStayHere: () -> Unit,
    onChangeItUp: () -> Unit,
    onNeverPlay: () -> Unit,
    onSaveStation: () -> Unit,
    showSaveStation: Boolean
) {
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        ListenerControlChip(label = "Stay here", onClick = onStayHere)
        ListenerControlChip(label = "Change it up", onClick = onChangeItUp)
        ListenerControlChip(label = "Never play", onClick = onNeverPlay)
        if (showSaveStation) {
            ListenerControlChip(label = "Save station", onClick = onSaveStation, accent = AppAccentSecondary)
        }
    }
}

@Composable
private fun ListenerControlChip(label: String, onClick: () -> Unit, accent: Color = AppText) {
    Text(
        text = label,
        color = accent,
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier
            .clip(RoundedCornerShape(18.dp))
            .background(AppSurfaceRaised.copy(alpha = 0.85f))
            .border(0.5.dp, AppOutline.copy(alpha = .35f), RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp)
    )
}

@Composable
private fun AmbientArtworkBackdrop(artworkUrl: String?, seed: String) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .alpha(0.55f)
    ) {
        if (!artworkUrl.isNullOrBlank()) {
            NetworkArtwork(
                artworkUrl = artworkUrl,
                seed = seed,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer(scaleX = 1.15f, scaleY = 1.15f)
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.radialGradient(
                            colors = listOf(AppAccent.copy(alpha = 0.08f), AppBackground)
                        )
                    )
            )
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            AppBackground.copy(alpha = 0.72f),
                            AppBackground.copy(alpha = 0.88f),
                            AppBackground
                        )
                    )
                )
        )
    }
}

@Composable
private fun SessionHeader(
    headline: String,
    subtitle: String,
    isPulseActive: Boolean,
    isStationMode: Boolean,
    onSwitchVibe: () -> Unit,
    onRefreshMix: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(
                text = if (isStationMode) "Station" else "Radio",
                style = VantaType.caption,
                color = AppAccentSecondary
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = headline,
                style = VantaType.editorialLarge,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = subtitle,
                style = VantaType.subtitle,
                color = AppTextMuted,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            if (isPulseActive) {
                Spacer(Modifier.height(10.dp))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(AppAccentSoft)
                        .border(0.5.dp, AppAccent.copy(alpha = 0.2f), RoundedCornerShape(50))
                        .padding(horizontal = 10.dp, vertical = 5.dp)
                ) {
                    Text(
                        text = "Pulse is curating",
                        color = AppAccent,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 0.6.sp
                    )
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (!isStationMode) {
                SessionActionChip(label = "Vibe", onClick = onSwitchVibe, emphasized = true)
            }
            SessionActionChip(
                label = if (isStationMode) "Shuffle" else "New mix",
                onClick = onRefreshMix,
                emphasized = false
            )
        }
    }
}

@Composable
private fun SessionActionChip(
    label: String,
    onClick: () -> Unit,
    emphasized: Boolean
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(if (emphasized) AppAccentSoft else Color.White.copy(alpha = 0.05f))
            .border(
                0.5.dp,
                AppAccent.copy(alpha = if (emphasized) 0.22f else 0.08f),
                RoundedCornerShape(50)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Text(
            text = label,
            color = if (emphasized) AppAccent else AppTextSecondary,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun NowPlayingMoment(
    nowPlaying: com.audiophile.musicplayer.playback.NowPlayingState
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp)
                .clip(RoundedCornerShape(VantaRadius.largeCard))
                .border(
                    0.5.dp,
                    AppAccent.copy(alpha = 0.14f),
                    RoundedCornerShape(VantaRadius.largeCard)
                )
        ) {
            if (!nowPlaying.artworkUrl.isNullOrBlank()) {
                NetworkArtwork(
                    artworkUrl = nowPlaying.artworkUrl,
                    seed = nowPlaying.title ?: "VANTA",
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(AppSurfaceRaised),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Filled.MusicNote,
                        contentDescription = null,
                        tint = AppTextMuted,
                        modifier = Modifier.size(48.dp)
                    )
                }
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.55f)),
                            startY = 120f
                        )
                    )
            )
            if (nowPlaying.isPlaying) {
                AudioWaveformVisualizer(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(14.dp)
                        .width(56.dp)
                        .height(20.dp),
                    color = AppAccent.copy(alpha = 0.85f),
                    barCount = 8
                )
            }
        }

        Spacer(Modifier.height(18.dp))

        Text(
            text = nowPlaying.title ?: "Not playing",
            style = VantaType.editorialLarge,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = nowPlaying.artist ?: "—",
            style = VantaType.subtitle,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth()
        )
        if (!nowPlaying.versionLabel.isNullOrBlank()) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = nowPlaying.versionLabel,
                color = AppAccentSecondary,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 0.4.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun PulseWhisperCard(
    commentary: String,
    isGenerating: Boolean,
    fromPulse: Boolean,
    companionMode: com.audiophile.musicplayer.data.dj.DjCompanionMode,
    onCycleCompanionMode: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Box(
            modifier = Modifier
                .width(1.dp)
                .heightIn(min = 72.dp)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(AppAccent.copy(alpha = 0.05f), AppAccent.copy(alpha = 0.45f), AppAccent.copy(alpha = 0.05f))
                    )
                )
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = when {
                        isGenerating -> "Pulse is listening…"
                        fromPulse -> "From Pulse"
                        else -> "In the room"
                    },
                    style = VantaType.caption,
                    color = if (fromPulse) AppAccent else AppTextMuted
                )
                Text(
                    text = companionMode.label,
                    color = AppAccent,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .clickable(onClick = onCycleCompanionMode)
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                )
            }
            if (isGenerating) {
                AudioWaveformVisualizer(
                    modifier = Modifier.width(80.dp).height(24.dp),
                    color = AppAccent
                )
            } else {
                Text(
                    text = commentary,
                    style = VantaType.editorialBody,
                    lineHeight = 24.sp,
                    maxLines = 6,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Text(
                text = "Text-first companion · tap mode to change personality",
                color = AppTextMuted,
                fontSize = 11.sp
            )
        }
    }
}

@Composable
private fun CozyToggle(checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    val thumbOffset by animateDpAsState(
        targetValue = if (checked) 18.dp else 0.dp,
        label = "djVoiceThumb"
    )
    Box(
        modifier = Modifier
            .size(width = 44.dp, height = 26.dp)
            .clip(CircleShape)
            .background(if (checked) AppAccent.copy(alpha = 0.28f) else AppOutline.copy(alpha = 0.6f))
            .clickable { onCheckedChange(!checked) }
            .padding(2.dp)
    ) {
        Box(
            modifier = Modifier
                .offset(x = thumbOffset)
                .size(22.dp)
                .clip(CircleShape)
                .background(if (checked) AppAccent else AppTextMuted)
                .align(Alignment.CenterStart)
        )
    }
}

@Composable
private fun ComingUpSection(
    picks: List<AiDjPick>,
    nowPlayingTrackId: String?,
    onLoadMore: () -> Unit,
    onPlayTrack: (com.audiophile.musicplayer.data.local.entities.UnifiedTrackWithSources) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("Coming up", style = VantaType.sectionTitle)
        Text(
            text = "Load more",
            color = AppAccent,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .clickable(onClick = onLoadMore)
                .padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
    Spacer(Modifier.height(8.dp))
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(VantaRadius.card))
            .background(Color.White.copy(alpha = 0.03f))
            .border(0.5.dp, AppOutline.copy(alpha = 0.35f), RoundedCornerShape(VantaRadius.card))
            .padding(vertical = 4.dp, horizontal = 4.dp)
    ) {
        picks.forEach { pick ->
            CompactUpcomingRow(
                pick = pick,
                isNowPlaying = pick.track.track.trackId.toString() == nowPlayingTrackId,
                onPlay = { onPlayTrack(pick.track) }
            )
        }
    }
}

@Composable
private fun RadioControlStrip(
    nowPlaying: com.audiophile.musicplayer.playback.NowPlayingState,
    onPrevious: () -> Unit,
    onTogglePlayback: () -> Unit,
    onNext: () -> Unit,
    onThumbsUp: () -> Unit,
    onThumbsDown: () -> Unit,
    onSeek: (Long) -> Unit
) {
    val progress = if (nowPlaying.durationMs > 0L) {
        (nowPlaying.positionMs.toFloat() / nowPlaying.durationMs.toFloat()).coerceIn(0f, 1f)
    } else 0f

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
            .background(AppBackgroundBottom)
            .border(
                0.5.dp,
                AppAccent.copy(alpha = 0.1f),
                RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
            )
            .padding(horizontal = VantaSpacing.screenHorizontal, vertical = 14.dp)
            .padding(bottom = appBottomContentPadding(isMiniPlayerVisible = false)),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(3.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(AppOutline.copy(alpha = 0.5f))
                .pointerInput(nowPlaying.durationMs) {
                    detectTapGestures { offset ->
                        if (nowPlaying.durationMs > 0L) {
                            val fraction = (offset.x / size.width).coerceIn(0f, 1f)
                            onSeek((nowPlaying.durationMs * fraction).toLong())
                        }
                    }
                }
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(progress)
                    .height(3.dp)
                    .background(AppAccent.copy(alpha = 0.75f))
            )
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(formatDuration(nowPlaying.positionMs), style = VantaType.tabularNumbers)
            Text(formatDuration(nowPlaying.durationMs), style = VantaType.tabularNumbers)
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            ControlIconButton(
                icon = Icons.Filled.ThumbDown,
                tint = AppTextMuted,
                size = 22.dp,
                onClick = onThumbsDown
            )
            ControlIconButton(
                icon = Icons.Filled.SkipPrevious,
                tint = AppText,
                size = 28.dp,
                onClick = onPrevious
            )
            Box(
                modifier = Modifier
                    .size(54.dp)
                    .clip(CircleShape)
                    .background(AppAccent)
                    .clickable(onClick = onTogglePlayback),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    if (nowPlaying.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = "Play or pause",
                    tint = AppBackground,
                    modifier = Modifier.size(28.dp)
                )
            }
            ControlIconButton(
                icon = Icons.Filled.SkipNext,
                tint = AppText,
                size = 28.dp,
                onClick = onNext
            )
            ControlIconButton(
                icon = Icons.Filled.ThumbUp,
                tint = AppAccent,
                size = 22.dp,
                onClick = onThumbsUp
            )
        }
    }
}

@Composable
private fun ControlIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tint: Color,
    size: androidx.compose.ui.unit.Dp,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(size))
    }
}

@Composable
private fun CompactUpcomingRow(
    pick: AiDjPick,
    isNowPlaying: Boolean,
    onPlay: () -> Unit
) {
    val track = pick.track.track
    val display = remember(track) { TrackDisplayResolver.resolve(track) }
    val versionLabel = remember(track.title) {
        com.audiophile.musicplayer.data.display.DisplayMetadataCleaner.extractVersionLabel(track.title)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (isNowPlaying) AppAccent.copy(alpha = 0.08f) else Color.Transparent)
            .clickable(onClick = onPlay)
            .padding(horizontal = 10.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(VantaRadius.artwork))
                .border(0.5.dp, AppOutline.copy(alpha = 0.4f), RoundedCornerShape(VantaRadius.artwork))
        ) {
            if (!display.artworkUrl.isNullOrBlank()) {
                NetworkArtwork(
                    artworkUrl = display.artworkUrl,
                    seed = "${display.title} ${display.artist}",
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                ArtworkPlaceholder(
                    seed = "${display.title} ${display.artist}",
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = display.title,
                style = VantaType.songTitle,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = if (isNowPlaying) AppAccent else AppText
            )
            Text(
                text = display.artist,
                style = VantaType.subtitle,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (!versionLabel.isNullOrBlank()) {
                Text(
                    text = versionLabel,
                    color = AppAccentSecondary,
                    fontSize = 10.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        if (isNowPlaying) {
            AudioWaveformVisualizer(
                modifier = Modifier.width(28.dp).height(14.dp),
                color = AppAccent,
                barCount = 5
            )
        }
    }
}

@Composable
fun AudioWaveformVisualizer(
    modifier: Modifier = Modifier,
    color: Color = AppAccent,
    barCount: Int = 12
) {
    val infiniteTransition = rememberInfiniteTransition(label = "waveform")
    val heights = (0 until barCount).map { index ->
        infiniteTransition.animateFloat(
            initialValue = 0.2f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(
                    durationMillis = 500 + (index * 83) % 350,
                    easing = EaseInOutSine
                ),
                repeatMode = RepeatMode.Reverse
            ),
            label = "bar_$index"
        )
    }

    Canvas(modifier = modifier) {
        val width = size.width
        val height = size.height
        val barWidth = (width / barCount) * 0.6f
        val gap = (width / barCount) * 0.4f

        for (i in 0 until barCount) {
            val barHeight = height * heights[i].value
            val x = i * (barWidth + gap) + (gap / 2)
            val y = (height - barHeight) / 2
            drawRoundRect(
                color = color,
                topLeft = Offset(x, y),
                size = Size(barWidth, barHeight),
                cornerRadius = CornerRadius(barWidth / 2, barWidth / 2)
            )
        }
    }
}


/** Build a clean catalog-backed query from custom station parameters. */
private fun buildCustomStationQuery(
    name: String,
    artists: List<String>,
    eraId: String?,
    genreId: String?
): String {
    val parts = mutableListOf<String>()
    if (artists.isNotEmpty()) parts.add(artists.joinToString(" ") + " music")
    genreId?.takeIf { it.isNotBlank() }?.let { parts.add(it) }
    eraId?.takeIf { it.isNotBlank() }?.let { parts.add("${it}s hits") }
    if (parts.isEmpty()) parts.add(name)
    return parts.joinToString(" ").trim().ifBlank { name }
}

private fun buildTasteSummary(profile: com.audiophile.musicplayer.data.dj.AiDjTasteProfile): String {
    val parts = mutableListOf<String>()
    if (profile.favoriteArtists.isNotEmpty()) {
        parts.add("Likes ${profile.favoriteArtists.joinToString(", ")}")
    }
    if (profile.totalTracks > 0) {
        parts.add("${profile.totalTracks} tracks in library")
    }
    return if (parts.isEmpty()) "Your library is empty. Add music to get started."
    else parts.joinToString(" · ")
}
