package com.audiophile.musicplayer.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.audiophile.musicplayer.data.display.DisplayMetadataCleaner
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
    onPlayFriendTrack: (title: String, artist: String) -> Unit = { _, _ -> },
    onOpenFriendProfile: (friendId: String) -> Unit = { _ -> },
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
    // A premium home never leads with empty recommendation shells. Keep cards
    // visible while generating, but hide completed zero-track mixes and let the
    // first real listening surface take visual priority.
    val readyMixCards = personalizedMixState.cards.filter { card ->
        card.isLoading || (!card.isEmpty && card.trackCount > 0)
    }

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

        // ===== SEASONAL MOMENT =====
        val moment = remember { CalendarMomentProvider.currentMoment() }
        if (moment != null) {
            SeasonalMomentCard(
                moment = moment,
                artworkUrls = recentlyPlayed.mapNotNull { TrackDisplayResolver.resolve(it.track, emptySet()).artworkUrl }.take(4),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = VantaSpacing.screenHorizontal)
            )
            Spacer(Modifier.height(VantaSpacing.sectionVertical))
        }

        // #region agent log
        LaunchedEffect(Unit) {
            com.audiophile.musicplayer.debug.Debug80b070.log(
                hypothesisId = "A",
                location = "HomeScreen",
                message = "home_layout",
                data = mapOf("driveModeCardShown" to false, "moment" to (moment?.id ?: "none"))
            )
        }
        // #endregion

        if (!validTracks.isEmpty() || hasNowPlaying) {
            // ===== NOW PLAYING HERO =====
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

            if (readyMixCards.isNotEmpty()) {
                VantaSectionHeader("Made For You", modifier = Modifier.padding(horizontal = VantaSpacing.screenHorizontal))
                Spacer(Modifier.height(12.dp))
                readyMixCards.forEach { card ->
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

            // ===== RECENTLY PLAYED =====
            if (recentlyPlayed.isNotEmpty()) {
                VantaSectionHeader("Recently Played", modifier = Modifier.padding(horizontal = VantaSpacing.screenHorizontal))
                Spacer(Modifier.height(12.dp))
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(horizontal = VantaSpacing.screenHorizontal)
                ) {
                    items(recentlyPlayed, key = { it.track.trackId }) { track ->
                        ArtworkCard(
                            track = track,
                            onPlay = { onPlayTrack(track) }
                        )
                    }
                }
                Spacer(Modifier.height(VantaSpacing.sectionVertical))
            }

            // ===== HEAVY ROTATION =====
            val heavyRotation = validTracks
                .sortedByDescending { it.track.lastPlayedAt ?: 0L }
                .take(10)
            if (heavyRotation.isNotEmpty() && heavyRotation != recentlyPlayed.take(10)) {
                VantaSectionHeader("Heavy Rotation", modifier = Modifier.padding(horizontal = VantaSpacing.screenHorizontal))
                Spacer(Modifier.height(12.dp))
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(horizontal = VantaSpacing.screenHorizontal)
                ) {
                    items(heavyRotation, key = { it.track.trackId }) { track ->
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

            // ===== BECAUSE YOU PLAYED =====
            val lastArtist = recentlyPlayed.firstOrNull()?.track?.artist
            val becauseYouPlayed = if (lastArtist != null) {
                validTracks.filter { it.track.artist.equals(lastArtist, ignoreCase = true) && it.track.trackId !in usedHomeIds }.take(10)
            } else emptyList()
            if (becauseYouPlayed.isNotEmpty()) {
                VantaSectionHeader("Because You Played ${DisplayMetadataCleaner.cleanDisplayName(lastArtist ?: "")}", modifier = Modifier.padding(horizontal = VantaSpacing.screenHorizontal))
                Spacer(Modifier.height(12.dp))
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(horizontal = VantaSpacing.screenHorizontal)
                ) {
                    items(becauseYouPlayed, key = { it.track.trackId }) { track ->
                        ArtworkCard(
                            track = track,
                            onPlay = { onPlayTrack(track) }
                        )
                    }
                }
                Spacer(Modifier.height(VantaSpacing.sectionVertical))
            }

            // ===== START A STATION =====
            StartStationStrip(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = VantaSpacing.screenHorizontal)
            )
            Spacer(Modifier.height(VantaSpacing.sectionVertical))

            // ===== FRIENDS ARE LISTENING =====
            if (friendFeed.friends.isNotEmpty()) {
                VantaSectionHeader("Friends Are Listening", modifier = Modifier.padding(horizontal = VantaSpacing.screenHorizontal))
                Spacer(Modifier.height(12.dp))
                FriendsListeningRow(
                    feed = friendFeed,
                    onAddFriends = { onOpenAccount?.invoke() },
                    onPlayFriendTrack = onPlayFriendTrack,
                    onOpenFriendProfile = onOpenFriendProfile,
                    modifier = Modifier.padding(horizontal = VantaSpacing.screenHorizontal)
                )
                Spacer(Modifier.height(32.dp))
            }
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
    val (cleanTitle, cleanArtist) = remember(title, artist) {
        DisplayMetadataCleaner.computeDisplayTitleArtist(title.orEmpty(), artist.orEmpty())
    }
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
                    text = cleanTitle.ifBlank { title },
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
                    text = cleanArtist.ifBlank { artist },
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
private fun FriendsListeningRow(
    feed: FriendFeed,
    onAddFriends: () -> Unit,
    onPlayFriendTrack: (title: String, artist: String) -> Unit,
    onOpenFriendProfile: (friendId: String) -> Unit = { _ -> },
    modifier: Modifier = Modifier
) {
    val latestByFriend = feed.events
        .sortedByDescending { it.startedAtMs }
        .distinctBy { it.friendId }
        .associateBy { it.friendId }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        feed.friends.forEach { friend ->
            val event = latestByFriend[friend.id]
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .width(84.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .clickable {
                        onOpenFriendProfile(friend.id)
                    }
                    .padding(vertical = 4.dp)
            ) {
                Box(contentAlignment = Alignment.BottomEnd) {
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(CircleShape)
                            .background(AppSurfaceRaised)
                            .border(
                                width = if (event != null) 1.5.dp else 1.dp,
                                color = if (event != null) AppAccent.copy(alpha = 0.65f)
                                else AppAccent.copy(alpha = 0.2f),
                                shape = CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            friend.avatarSeed.take(2).uppercase(),
                            color = AppAccent,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    if (event != null) {
                        Box(
                            modifier = Modifier
                                .size(14.dp)
                                .clip(CircleShape)
                                .background(AppAccent)
                                .border(2.dp, AppBackgroundTop, CircleShape)
                        )
                    }
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    friend.displayName,
                    style = VantaType.caption,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (event != null) {
                    Text(
                        DisplayMetadataCleaner.cleanDisplayName(event.title).ifBlank { event.title },
                        style = VantaType.caption.copy(fontSize = 10.sp),
                        color = AppTextMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.clickable { onPlayFriendTrack(event.title, event.artist) }
                    )
                }
            }
        }
        // Trailing add chip keeps the row inviting.
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .width(84.dp)
                .clickable(onClick = onAddFriends)
                .padding(vertical = 4.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(AppSurfaceRaised)
                    .border(0.5.dp, AppOutline, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text("+", color = AppTextMuted, fontSize = 22.sp, fontWeight = FontWeight.Medium)
            }
            Spacer(Modifier.height(6.dp))
            Text("Add", style = VantaType.caption, color = AppTextMuted)
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
private fun SeasonalMomentCard(
    moment: CalendarMoment,
    artworkUrls: List<String> = emptyList(),
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(24.dp)
    Box(
        modifier = modifier
            .height(220.dp)
            .clip(shape)
            .background(Brush.verticalGradient(moment.heroGradient))
            .border(0.5.dp, moment.accentColor.copy(alpha = 0.15f), shape)
    ) {
        if (moment.id == "july4") {
            FireworksOverlay(modifier = Modifier.matchParentSize())
        }
        if (artworkUrls.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 12.dp, end = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                artworkUrls.take(4).forEachIndexed { i, url ->
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .graphicsLayer {
                                rotationZ = when (i) { 0 -> -4f; 1 -> 2f; 2 -> -2f; else -> 4f }
                                translationY = when (i) { 0 -> 4f; 1 -> 0f; 2 -> 2f; else -> 6f }
                                alpha = 0.85f
                            }
                            .shadow(6.dp, RoundedCornerShape(10.dp), clip = true)
                            .clip(RoundedCornerShape(10.dp))
                    ) {
                        NetworkArtwork(
                            artworkUrl = url,
                            seed = "seasonal",
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }
        }
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                moment.title,
                color = moment.accentColor,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1
            )
            Text(
                moment.subtitle,
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 14.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                moment.playlists.take(3).forEach { playlist ->
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color.White.copy(alpha = 0.1f))
                            .padding(horizontal = 10.dp, vertical = 5.dp)
                    ) {
                        Text(
                            playlist.title,
                            color = Color.White.copy(alpha = 0.85f),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StartStationStrip(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(
                Brush.verticalGradient(
                    listOf(
                        AppAccent.copy(alpha = 0.08f),
                        AppAccent.copy(alpha = 0.03f)
                    )
                )
            )
            .border(0.5.dp, AppAccent.copy(alpha = 0.12f), RoundedCornerShape(18.dp))
            .clickable { }
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(AppAccent.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Filled.AutoAwesome,
                contentDescription = null,
                tint = AppAccent,
                modifier = Modifier.size(22.dp)
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                "Start a Station",
                color = AppText,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                "Let VANTA curate based on your current mood",
                color = AppTextSecondary,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Icon(
            Icons.Filled.Shuffle,
            contentDescription = null,
            tint = AppAccent,
            modifier = Modifier.size(24.dp)
        )
    }
}

@Composable
fun PersonalizedMixCard(
    card: PersonalizedMixCardState,
    onPlay: () -> Unit,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier
) {
    val capsuleShape = RoundedCornerShape(20.dp)
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
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Artwork (or gradient fallback)
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .shadow(4.dp, RoundedCornerShape(14.dp), ambientColor = Color.Black.copy(alpha = 0.3f))
            ) {
                if (card.artworkUrl != null) {
                    NetworkArtwork(
                        artworkUrl = card.artworkUrl,
                        seed = card.title,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.linearGradient(
                                    listOf(
                                        AppAccent.copy(alpha = 0.15f),
                                        AppAccent.copy(alpha = 0.05f)
                                    )
                                )
                            )
                    )
                }
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.2f))
                            .border(0.5.dp, Color.White.copy(alpha = 0.25f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Filled.PlayArrow,
                            contentDescription = "Play ${card.title}",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
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
                    text = "${card.trackCount} tracks",
                    style = VantaType.caption.copy(fontSize = 11.sp),
                    color = AppTextMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (card.lastRefreshedLabel != null) {
                    Text(
                        text = card.lastRefreshedLabel,
                        style = VantaType.caption.copy(fontSize = 10.sp),
                        color = AppTextMuted.copy(alpha = 0.6f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            if (card.isLoading) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(AppSurfaceRaised),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Filled.Refresh,
                        contentDescription = "Loading",
                        tint = AppTextMuted,
                        modifier = Modifier.size(18.dp)
                    )
                }
            } else {
                // The card itself is the primary play target. Keep only one
                // quiet secondary action instead of a competing stack of icons.
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(AppSurfaceRaised)
                        .border(0.5.dp, AppOutline, CircleShape)
                        .clickable(onClickLabel = "Refresh ${card.title}", onClick = onRefresh),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Filled.Refresh,
                        contentDescription = "Refresh ${card.title}",
                        tint = if (card.isStale) AppWarning else AppAccent,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}


