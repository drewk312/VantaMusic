package com.audiophile.musicplayer.ui

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.audiophile.musicplayer.data.display.TrackDisplayResolver
import com.audiophile.musicplayer.data.dj.JukeboxTrackEligibility
import com.audiophile.musicplayer.data.local.entities.UnifiedTrackWithSources
import com.audiophile.musicplayer.data.source.isPlayableMusicCandidate
import com.audiophile.musicplayer.discovery.personalized.PersonalizedMixKind
import com.audiophile.musicplayer.social.FriendFeed
import com.audiophile.musicplayer.social.VantaSocialManager
import com.audiophile.musicplayer.playback.NowPlayingState
import java.util.Calendar

fun timeBasedGreeting(): String {
    val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    return when (hour) {
        in 0..11 -> "Good morning"
        in 12..16 -> "Good afternoon"
        else -> "Good evening"
    }
}

@Composable
fun HomeScreen(
    uiState: MainUiState,
    nowPlayingState: NowPlayingState,
    personalizedMixState: PersonalizedMixUiState = PersonalizedMixUiState(),
    onOpenSettings: () -> Unit,
    onOpenSearch: () -> Unit,
    onOpenRadio: () -> Unit,
    onOpenDrive: () -> Unit = {},
    onPlayFirstPlayable: () -> Unit,
    onPlayTrack: (UnifiedTrackWithSources) -> Unit,
    onPlayPersonalizedMix: (PersonalizedMixKind) -> Unit = {},
    onRefreshPersonalizedMix: (PersonalizedMixKind) -> Unit = {},
    onQueryChanged: (String) -> Unit,
    onNavigateToArtist: (String, String?) -> Unit,
    onNavigateToAlbum: (String, String, String?) -> Unit,
    accountManager: com.audiophile.musicplayer.account.AccountManager? = null,
    vantaSocialManager: VantaSocialManager? = null,

    onOpenAccount: (() -> Unit)? = null,
    miniPlayerVisible: Boolean = false
) {
    val libraryTracks = uiState.library
    val hasNowPlaying = nowPlayingState.trackId != null

    val accountProfile = accountManager?.profile?.collectAsState()?.value
    val friendFeed by vantaSocialManager?.feed?.collectAsState(initial = FriendFeed())
        ?: remember { androidx.compose.runtime.mutableStateOf(FriendFeed()) }
    val profile = accountProfile?.let { if (it.isOnboarded) it else null }
    val isSignedIn = profile != null
    val userDisplayName = profile?.displayName

    val greeting = if (isSignedIn && userDisplayName != null) {
        "${timeBasedGreeting()}, ${userDisplayName.split(" ").first()}"
    } else {
        timeBasedGreeting()
    }

    val greetingSubtitle = when {
        hasNowPlaying -> "What should play next?"
        else -> "Music, the way it should be."
    }

    val likedLocalSongs = uiState.localSongs.filter { it.isFavorite }
    val seenKeys = mutableSetOf<String>()
    val validTracks = libraryTracks.filter { t ->
        if (t.track.title.isBlank() || t.track.artist.isBlank()) return@filter false
        if (!t.isPlayableMusicCandidate()) return@filter false
        if (JukeboxTrackEligibility.isTributeOrKaraokeArtifact(t)) return@filter false
        val display = TrackDisplayResolver.resolve(t.track, emptySet())
        val key = "${display.title.lowercase().trim()}|${display.artist.lowercase().trim()}"
        if (key in seenKeys) return@filter false
        seenKeys.add(key)
        true
    }

    val recentlyPlayed = validTracks
        .filter { it.track.lastPlayedAt != null }
        .sortedByDescending { it.track.lastPlayedAt }
        .take(20)

    val recentlyPlayedIds = recentlyPlayed.mapTo(mutableSetOf()) { it.track.trackId }
    val forYouTracks = validTracks.filterNot { it.track.trackId in recentlyPlayedIds }.take(9)
    val usedHomeIds = recentlyPlayedIds + forYouTracks.map { it.track.trackId }

    val nowPlayingArtwork = if (hasNowPlaying) nowPlayingState.artworkUrl else null

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(top = appTopContentPadding())
            .padding(bottom = appBottomContentPadding(isMiniPlayerVisible = miniPlayerVisible)),
        verticalArrangement = Arrangement.spacedBy(0.dp)
    ) {
        // ===== HEADER =====
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    start = VantaSpacing.screenHorizontal,
                    end = VantaSpacing.screenHorizontal,
                    top = 0.dp
                ),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    greeting,
                    style = VantaType.editorialLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    greetingSubtitle,
                    style = VantaType.subtitle,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.width(12.dp))
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(AppSurfaceRaised)
                    .border(0.5.dp, AppOutline, CircleShape)
                    .clickable { onOpenAccount?.invoke() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Filled.Person,
                    contentDescription = if (isSignedIn) "Profile" else "Sign In",
                    tint = AppTextSecondary,
                    modifier = Modifier.size(22.dp)
                )
            }
            Spacer(Modifier.width(6.dp))
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(AppSurfaceRaised)
                    .border(0.5.dp, AppOutline, CircleShape)
                    .clickable(onClick = onOpenSettings),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Filled.Settings,
                    contentDescription = "Settings",
                    tint = AppTextSecondary,
                    modifier = Modifier.size(22.dp)
                )
            }
        }

        Spacer(Modifier.height(20.dp))
        Box(
            modifier = Modifier
                .padding(horizontal = VantaSpacing.screenHorizontal)
                .fillMaxWidth()
                .height(1.dp)
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(
                            Color.Transparent,
                            AppAccent.copy(alpha = 0.35f),
                            Color.Transparent
                        )
                    )
                )
        )
        Spacer(Modifier.height(20.dp))

        // #region agent log
        androidx.compose.runtime.LaunchedEffect(Unit) {
            com.audiophile.musicplayer.debug.Debug80b070.log(
                hypothesisId = "A",
                location = "HomeScreen",
                message = "home_layout",
                data = mapOf("driveModeCardShown" to false)
            )
        }
        // #endregion

        if (personalizedMixState.cards.isNotEmpty()) {
            VantaSectionHeader("Made For You", modifier = Modifier.padding(horizontal = VantaSpacing.screenHorizontal))
            Spacer(Modifier.height(12.dp))
            personalizedMixState.cards.forEach { card ->
                PersonalizedMixCard(
                    card = card,
                    onPlay = { onPlayPersonalizedMix(card.kind) },
                    onRefresh = { onRefreshPersonalizedMix(card.kind) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = VantaSpacing.screenHorizontal, vertical = 6.dp)
                )
            }
            Spacer(Modifier.height(VantaSpacing.sectionVertical))
        }

        if (!validTracks.isEmpty() || hasNowPlaying) {
            // ===== FEATURED CARD =====
            if (hasNowPlaying) {
                FeaturedNowPlayingCard(
                    artworkUrl = nowPlayingArtwork,
                    title = nowPlayingState.title,
                    artist = nowPlayingState.artist,
                    onPlay = onPlayFirstPlayable,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = VantaSpacing.screenHorizontal)
                )
                Spacer(Modifier.height(VantaSpacing.sectionVertical))
            }

            // ===== CONTINUE LISTENING =====
            if (recentlyPlayed.isNotEmpty()) {
                VantaSectionHeader("Continue Listening", modifier = Modifier.padding(horizontal = VantaSpacing.screenHorizontal))
                Spacer(Modifier.height(12.dp))
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(horizontal = VantaSpacing.screenHorizontal)
                ) {
                    items(recentlyPlayed) { track ->
                        ArtworkCard(
                            track = track,
                            onPlay = { onPlayTrack(track) }
                        )
                    }
                }
                Spacer(Modifier.height(VantaSpacing.sectionVertical))
            }

            // ===== FOR YOU =====
            if (forYouTracks.isNotEmpty()) {
                VantaSectionHeader("For You", modifier = Modifier.padding(horizontal = VantaSpacing.screenHorizontal))
                Spacer(Modifier.height(12.dp))
                ForYouGrid(
                    tracks = forYouTracks,
                    onPlay = onPlayTrack
                )
                Spacer(Modifier.height(VantaSpacing.sectionVertical))
            }

            // ===== EDITORIAL PICKS =====
            val editorialTrack = validTracks.firstOrNull { it.track.trackId !in usedHomeIds }
            if (editorialTrack != null) {
                VantaSectionHeader("Editorial Picks", modifier = Modifier.padding(horizontal = VantaSpacing.screenHorizontal))
                Spacer(Modifier.height(12.dp))
                EditorialCard(
                    track = editorialTrack,
                    onPlay = { onPlayTrack(editorialTrack) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = VantaSpacing.screenHorizontal)
                )
                Spacer(Modifier.height(VantaSpacing.sectionVertical))
            }

            // ===== FRIENDS ARE LISTENING =====
            VantaSectionHeader("Friends Are Listening", modifier = Modifier.padding(horizontal = VantaSpacing.screenHorizontal))
            Spacer(Modifier.height(12.dp))
            FriendsListeningRow(
                onPlayFirstPlayable = onPlayFirstPlayable,
                modifier = Modifier.padding(horizontal = VantaSpacing.screenHorizontal)
            )
            Spacer(Modifier.height(32.dp))
        } else {
            // ===== EMPTY STATE =====
            EmptyOnboardingSection(onOpenSearch = onOpenSearch)
        }
    }
}

@Composable
private fun FeaturedNowPlayingCard(
    artworkUrl: String?,
    title: String?,
    artist: String?,
    onPlay: () -> Unit,
    modifier: Modifier = Modifier
) {
    val heroShape = RoundedCornerShape(24.dp)
    Box(
        modifier = modifier
            .heightIn(min = 236.dp)
            .clip(heroShape)
            .shadow(
                elevation = 16.dp,
                shape = heroShape,
                ambientColor = Color.Black.copy(alpha = 0.5f),
                spotColor = AppAccent.copy(alpha = 0.1f)
            )
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(252.dp)
        ) {
            if (artworkUrl != null) {
                NetworkArtwork(
                    artworkUrl = artworkUrl,
                    seed = title ?: "Music",
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                listOf(AppSurfaceRaised, AppBackground)
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
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.12f),
                                Color.Black.copy(alpha = 0.32f),
                                Color.Black.copy(alpha = 0.74f),
                                Color.Black.copy(alpha = 0.94f),
                            ),
                            startY = 0.18f
                        )
                    )
            )

            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(120.dp)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                AppAccent.copy(alpha = 0.06f)
                            )
                        )
                    )
            )
        }

        Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = "NOW PLAYING",
                style = VantaType.caption,
                color = AppAccent.copy(alpha = 0.6f),
                letterSpacing = 2.sp
            )

            if (title != null) {
                Text(
                    text = title,
                    style = VantaType.songTitle.copy(
                        fontSize = 23.sp,
                        lineHeight = 27.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontStyle = FontStyle.Normal
                    ),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    color = Color.White
                )
            }
            if (artist != null) {
                Text(
                    text = artist,
                    style = VantaType.subtitle,
                    color = Color.White.copy(alpha = 0.65f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(Modifier.height(6.dp))

            Box(
                modifier = Modifier
                    .size(54.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                Color.White.copy(alpha = 0.15f),
                                Color.White.copy(alpha = 0.08f)
                            )
                        )
                    )
                    .border(0.5.dp, Color.White.copy(alpha = 0.2f), CircleShape)
                    .clickable(onClick = onPlay),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Filled.PlayArrow,
                    contentDescription = "Play",
                    tint = Color.White,
                    modifier = Modifier.size(28.dp)
                )
            }
        }
    }
}

@Composable
private fun ArtworkCard(
    track: UnifiedTrackWithSources,
    onPlay: () -> Unit
) {
    val display = remember(track.track) {
        TrackDisplayResolver.resolve(track.track, emptySet())
    }
    SideEffect {
        val hasArtwork = display.artworkUrl != null
        Log.d("VANTA_HOME_ARTWORK", "section=continue_listening trackId=${track.track.trackId} title='${display.title}' artist='${display.artist}' coverArtUrl=${display.artworkUrl ?: "null"} usedFallback=${!hasArtwork} reason=${if (!hasArtwork) "no_cover_art_url" else "ok"}")
    }
    Column(
        modifier = Modifier
            .width(160.dp)
            .clickable(onClick = onPlay)
    ) {
        Box(
            modifier = Modifier
                .size(160.dp)
                .clip(RoundedCornerShape(VantaRadius.artwork))
        ) {
            NetworkArtwork(
                artworkUrl = display.artworkUrl,
                seed = track.track.title,
                modifier = Modifier.fillMaxSize()
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            display.title,
            style = VantaType.cardTitle,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            display.artist,
            style = VantaType.subtitle,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun ForYouGrid(
    tracks: List<UnifiedTrackWithSources>,
    onPlay: (UnifiedTrackWithSources) -> Unit
) {
    val rows = tracks.chunked(3)
    Column(
        modifier = Modifier.padding(horizontal = VantaSpacing.screenHorizontal),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        rows.forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                row.forEach { track ->
                    MixCard(
                        track = track,
                        onPlay = { onPlay(track) },
                        modifier = Modifier.weight(1f)
                    )
                }
                if (row.size < 3) {
                    Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun MixCard(
    track: UnifiedTrackWithSources,
    onPlay: () -> Unit,
    modifier: Modifier = Modifier
) {
    val display = remember(track.track) {
        TrackDisplayResolver.resolve(track.track, emptySet())
    }
    Box(
        modifier = modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(VantaRadius.card))
            .clickable(onClick = onPlay)
    ) {
        NetworkArtwork(
            artworkUrl = display.artworkUrl,
            seed = track.track.title,
            modifier = Modifier.fillMaxSize()
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.6f)),
                        startY = 0.5f
                    )
                )
        )
        Text(
            display.title,
            style = VantaType.songTitle,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(10.dp)
        )
    }
}

@Composable
private fun EditorialCard(
    track: UnifiedTrackWithSources,
    onPlay: () -> Unit,
    modifier: Modifier = Modifier
) {
    val display = remember(track.track) {
        TrackDisplayResolver.resolve(track.track, emptySet())
    }
    val notes = listOf(
        "A quiet masterpiece that rewards every listen.",
        "The kind of song that stops time for four minutes.",
        "An essential track from an essential artist.",
        "Hauntingly beautiful. Press play and disappear.",
        "This one lives in the space between genres."
    )
    val note = remember(track.track.trackId) { notes.random() }

    Box(
        modifier = modifier
            .height(200.dp)
            .clip(RoundedCornerShape(VantaRadius.largeCard))
            .clickable(onClick = onPlay)
    ) {
        NetworkArtwork(
            artworkUrl = display.artworkUrl,
            seed = track.track.title,
            modifier = Modifier.fillMaxSize()
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.75f)),
                        startY = 0.3f
                    )
                )
        )
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(16.dp)
        ) {
            Text(
                "Editorial Note",
                style = VantaType.caption,
                color = AppAccent
            )
            Spacer(Modifier.height(4.dp))
            Text(
                note,
                style = VantaType.editorialBody,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "${display.title} \u2014 ${display.artist}",
                style = VantaType.subtitle,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(12.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(AppAccentSoft)
                .clickable(onClick = onPlay)
                .padding(horizontal = 10.dp, vertical = 4.dp)
        ) {
            Text(
                "Play",
                color = AppAccent,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun FriendsListeningRow(
    onPlayFirstPlayable: () -> Unit,
    modifier: Modifier = Modifier
) {
    val friends = listOf(
        "Alex" to emptyList<UnifiedTrackWithSources>(),
        "Jordan" to emptyList(),
        "Sam" to emptyList(),
        "Riley" to emptyList(),
        "Casey" to emptyList()
    )
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        friends.take(4).forEach { (name, _) ->
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.clickable(onClick = onPlayFirstPlayable)
            ) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(AppSurfaceRaised)
                        .border(1.dp, AppAccent.copy(alpha = 0.2f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        name.first().toString(),
                        color = AppAccent,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    name,
                    style = VantaType.caption,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        if (friends.size > 4) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(AppSurfaceRaised)
                    .border(0.5.dp, AppOutline, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "+${friends.size - 4}",
                    color = AppTextMuted,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

@Composable
private fun EmptyOnboardingSection(
    onOpenSearch: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = VantaSpacing.screenHorizontal),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Spacer(Modifier.height(80.dp))
        Text(
            "Your library is quiet.",
            style = VantaType.editorialLarge,
            textAlign = TextAlign.Center
        )
        Text(
            "Search for music to get started",
            style = VantaType.subtitle,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(20.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(VantaRadius.button))
                .background(AppAccent)
                .clickable(onClick = onOpenSearch)
                .padding(vertical = 16.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                "Start Searching",
                color = AppBackground,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun PersonalizedMixCard(
    card: PersonalizedMixCardState,
    onPlay: () -> Unit,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier
) {
    val statusText = when {
        card.isLoading -> "Refreshing…"
        card.error != null -> card.error
        card.isEmpty -> "Ready when you are"
        card.lastRefreshedLabel != null -> card.lastRefreshedLabel
        else -> "${card.trackCount} tracks ready"
    }
    val statusColor = when {
        card.error != null -> AppError
        card.isStale -> AppWarning
        else -> AppTextSecondary
    }

    val capsuleShape = RoundedCornerShape(28.dp)
    Box(
        modifier = modifier
            .clip(capsuleShape)
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color.Black.copy(alpha = 0.42f),
                        Color.Black.copy(alpha = 0.58f)
                    )
                )
            )
            .border(0.5.dp, Color.White.copy(alpha = 0.10f), capsuleShape)
            .shadow(8.dp, capsuleShape, ambientColor = Color.Black.copy(alpha = 0.35f))
            .clickable(enabled = !card.isLoading, onClick = onPlay)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(RoundedCornerShape(VantaRadius.artwork))
                    .shadow(
                        elevation = 4.dp,
                        shape = RoundedCornerShape(VantaRadius.artwork),
                        ambientColor = Color.Black.copy(alpha = 0.3f),
                        spotColor = AppAccent.copy(alpha = 0.15f)
                    )
                    .background(
                        Brush.linearGradient(
                            listOf(
                                AppAccent.copy(alpha = 0.12f),
                                AppAccent.copy(alpha = 0.04f)
                            )
                        )
                    )
                    .border(0.5.dp, AppAccent.copy(alpha = 0.1f), RoundedCornerShape(VantaRadius.artwork))
                    .clickable(enabled = !card.isLoading, onClick = onPlay),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Filled.PlayArrow,
                    contentDescription = "Play ${card.title}",
                    tint = AppAccent,
                    modifier = Modifier.size(28.dp)
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = card.title,
                    style = VantaType.songTitle,
                    color = AppText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = card.subtitle,
                    style = VantaType.caption,
                    color = AppTextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = statusText.orEmpty(),
                    style = VantaType.caption,
                    color = statusColor,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(AppSurfaceRaised)
                    .clickable(enabled = !card.isLoading, onClick = onRefresh),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Filled.Refresh,
                    contentDescription = "Refresh ${card.title}",
                    tint = if (card.isLoading) AppTextMuted else AppAccent,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}


