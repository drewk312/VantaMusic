package com.audiophile.musicplayer.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Group
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
import com.audiophile.musicplayer.data.source.GatewayHomeFeed
import com.audiophile.musicplayer.data.source.GatewayHomePlaylist
import com.audiophile.musicplayer.data.source.SourceSearchResult
import com.audiophile.musicplayer.data.source.isPlayableMusicCandidate
import com.audiophile.musicplayer.discovery.personalized.PersonalizedMixKind
import com.audiophile.musicplayer.social.FriendFeed
import com.audiophile.musicplayer.social.FriendListeningEvent
import com.audiophile.musicplayer.social.VantaFriend
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
    onToggleNowPlaying: () -> Unit,
    onPlayTrack: (UnifiedTrackWithSources) -> Unit,
    onPlayPersonalizedMix: (PersonalizedMixKind) -> Unit = {},
    onOpenPersonalizedMix: (PersonalizedMixKind) -> Unit = {},
    onRefreshPersonalizedMix: (PersonalizedMixKind) -> Unit = {},
    onLoadHomeFeed: () -> Unit = {},
    onLoadCatalogForYou: () -> Unit = {},
    onRefreshHome: () -> Unit = {},
    onPlayHomeTrack: (SourceSearchResult) -> Unit = {},
    onOpenHomePlaylist: (GatewayHomePlaylist) -> Unit = {},
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
    val ownFriendCode = remember(vantaSocialManager) { vantaSocialManager?.friendCode() ?: "VANTA-HQ" }
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
    val forYouTracks = validTracks.filterNot { it.track.trackId in recentlyPlayedIds }.take(6)
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
                    "V A N T A",
                    style = VantaType.caption.copy(
                        color = AppAccentSecondary,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.8.sp
                    )
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    greeting,
                    style = VantaType.pageTitle.copy(
                        fontSize = 24.sp,
                        lineHeight = 29.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontStyle = FontStyle.Normal
                    ),
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
                    .glassSurfaceElevated(shape = RoundedCornerShape(24.dp), surfaceAlpha = 0.68f)
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
                    .glassSurfaceElevated(shape = RoundedCornerShape(24.dp), surfaceAlpha = 0.68f)
                    .clickable { onRefreshHome() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Filled.Refresh,
                    contentDescription = "Refresh home",
                    tint = AppTextSecondary,
                    modifier = Modifier.size(22.dp)
                )
            }
            Spacer(Modifier.width(6.dp))
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .glassSurfaceElevated(shape = RoundedCornerShape(24.dp), surfaceAlpha = 0.68f)
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

        Spacer(Modifier.height(18.dp))

        HomeCommandDeck(
            onOpenSearch = onOpenSearch,
            onOpenRadio = onOpenRadio,
            onOpenDrive = onOpenDrive,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = VantaSpacing.screenHorizontal)
        )

        Spacer(Modifier.height(VantaSpacing.sectionVertical))

        // ===== SEASONAL MOMENT =====
        ArtistCelebrationCard(
            onExplore = { artist -> onNavigateToArtist(artist, null) },
            onListen = onPlayFriendTrack
        )
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

        // ===== FRIENDS LISTENING NOW =====
        FriendsListeningSection(
            friendFeed = friendFeed,
            ownFriendCode = ownFriendCode,
            onPlayFriendTrack = onPlayFriendTrack,
            onOpenFriendProfile = onOpenFriendProfile,
            onOpenAccount = onOpenAccount,
            modifier = Modifier.fillMaxWidth()
        )

        // ===== CATALOG HIGHLIGHTS (Gateway home feed) =====
        HomeCatalogHighlights(
            homeFeed = uiState.homeFeed,
            isLoading = uiState.homeFeedLoading,
            forYouArtist = uiState.catalogForYouArtist,
            forYouTracks = uiState.catalogForYou,
            onPlayTrack = onPlayHomeTrack,
            onOpenPlaylist = onOpenHomePlaylist
        )

        // ===== TOP ARTISTS (your listening, local truth) =====
        val topArtists = remember(libraryTracks) {
            val latestByArtist = mutableMapOf<String, Long>()
            for (track in validTracks) {
                val playedAt = track.track.lastPlayedAt ?: continue
                val artist = track.track.artist.trim()
                if (artist.isBlank()) continue
                val current = latestByArtist[artist] ?: 0L
                if (playedAt > current) latestByArtist[artist] = playedAt
            }
            latestByArtist.entries
                .sortedByDescending { it.value }
                .take(12)
                .map { it.key }
        }
        if (topArtists.size >= 2) {
            HomeCatalogSection(title = "Top Artists") {
                items(topArtists, key = { "artist:$it" }) { artistName ->
                    val artwork = validTracks
                        .filter { it.track.artist.equals(artistName, ignoreCase = true) && TrackDisplayResolver.resolve(it.track, emptySet()).artworkUrl != null }
                        .firstOrNull()
                        ?.let { TrackDisplayResolver.resolve(it.track, emptySet()).artworkUrl }
                    TopArtistChip(
                        artistName = artistName,
                        artworkUrl = artwork,
                        onClick = { onNavigateToArtist(artistName, null) }
                    )
                }
            }
        }

        // #region agent log
        LaunchedEffect(Unit) {
            onLoadHomeFeed()
            onLoadCatalogForYou()
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
            if (hasNowPlaying && !miniPlayerVisible) {
                FeaturedNowPlayingCard(
                    artworkUrl = nowPlayingArtwork,
                    title = nowPlayingState.title,
                    artist = nowPlayingState.artist,
                    isPlaying = nowPlayingState.isPlaying,
                    onTogglePlayback = onToggleNowPlaying,
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
                        onOpen = { onOpenPersonalizedMix(card.kind) },
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
                .filter { it.track.lastPlayedAt != null }
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
                onClick = onOpenRadio,
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
private fun HomeCommandDeck(
    onOpenSearch: () -> Unit,
    onOpenRadio: () -> Unit,
    onOpenDrive: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Column(
            Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(26.dp))
                .background(Brush.linearGradient(listOf(Color(0xFF302A22), AppSurface)))
                .border(0.5.dp, AppAccent.copy(alpha = 0.20f), RoundedCornerShape(26.dp))
                .padding(24.dp)
        ) {
            Text("THE ART OF LISTENING", style = VantaType.caption.copy(color = AppAccent, letterSpacing = 2.sp))
            Spacer(Modifier.height(14.dp))
            Text("Stay for one\nmore song.", style = VantaType.editorialHero.copy(fontSize = 34.sp, lineHeight = 38.sp))
            Spacer(Modifier.height(10.dp))
            Text("Personal stations. Familiar favourites. Something new.", style = VantaType.subtitle, maxLines = 2)
            Spacer(Modifier.height(18.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                androidx.compose.material3.Button(onClick = onOpenRadio, shape = RoundedCornerShape(16.dp)) {
                    Icon(Icons.Filled.PlayArrow, null, Modifier.size(20.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Explore radio")
                }
                androidx.compose.material3.TextButton(onClick = onOpenDrive) { Text("Drive mode", color = AppTextSecondary) }
            }
        }
        Row(
            Modifier.fillMaxWidth().heightIn(min = 56.dp)
                .luxuryCard(RoundedCornerShape(18.dp))
                .clickable(onClickLabel = "Search music", onClick = onOpenSearch)
                .padding(horizontal = 18.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(Icons.Filled.Search, null, Modifier.size(21.dp), tint = AppAccent)
            Text("Songs, artists, albums…", style = VantaType.subtitle, modifier = Modifier.weight(1f))
            Text("Search", color = AppText, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun HomeQuickAction(
    eyebrow: String,
    title: String,
    icon: ImageVector,
    accent: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(20.dp)
    Row(
        modifier = modifier
            .heightIn(min = 72.dp)
            .clip(shape)
            .background(
                Brush.linearGradient(
                    listOf(accent.copy(alpha = 0.20f), AppSurfaceRaised, AppSurface)
                )
            )
            .border(0.75.dp, accent.copy(alpha = 0.22f), shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 13.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = accent,
            modifier = Modifier.size(22.dp)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = eyebrow,
                color = accent.copy(alpha = 0.92f),
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
                maxLines = 1
            )
            Text(
                text = title,
                style = VantaType.cardTitle.copy(fontSize = 13.sp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun FeaturedNowPlayingCard(
    artworkUrl: String?,
    title: String?,
    artist: String?,
    isPlaying: Boolean,
    onTogglePlayback: () -> Unit,
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
            .border(
                width = 0.75.dp,
                brush = Brush.linearGradient(
                    listOf(
                        Color.White.copy(alpha = 0.34f),
                        AppAuroraCyan.copy(alpha = 0.12f),
                        AppAuroraViolet.copy(alpha = 0.20f),
                        Color.White.copy(alpha = 0.06f)
                    )
                ),
                shape = heroShape
            )
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
            NetworkArtwork(
                artworkUrl = artworkUrl,
                seed = title ?: "VANTA",
                modifier = Modifier.fillMaxSize()
            )

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
                                AppAuroraViolet.copy(alpha = 0.12f)
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
                color = AppAccentSecondary.copy(alpha = 0.88f),
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
                                Color.White.copy(alpha = 0.22f),
                                AppAccent.copy(alpha = 0.18f),
                                AppAccentSecondary.copy(alpha = 0.12f)
                            )
                        )
                    )
                    .border(0.75.dp, Color.White.copy(alpha = 0.32f), CircleShape)
                    .clickable(
                        onClickLabel = if (isPlaying) "Pause $title" else "Play $title",
                        onClick = onTogglePlayback
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = if (isPlaying) "Pause" else "Play",
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
                .border(0.75.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(VantaRadius.artwork))
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
    val columns = if (androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp >= 600) 3 else 2
    val rows = tracks.chunked(columns)
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
                repeat(columns - row.size) {
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
    modifier: Modifier = Modifier,
    onOpenFriendProfile: (friendId: String) -> Unit = { _ -> }
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
    modifier: Modifier = Modifier,
    artworkUrls: List<String> = emptyList()
) {
    val shape = RoundedCornerShape(24.dp)
    Box(
        modifier = modifier
            .height(220.dp)
            .glassSurface(shape = shape, borderAlpha = 0.18f, surfaceAlpha = 0.64f)
            .background(
                Brush.verticalGradient(
                    listOf(
                        (moment.heroGradient.firstOrNull() ?: AppAuroraViolet).copy(alpha = 0.22f),
                        AppAuroraViolet.copy(alpha = 0.10f),
                        Color.Transparent
                    )
                )
            )
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
                            .border(0.75.dp, Color.White.copy(alpha = 0.18f), RoundedCornerShape(10.dp))
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
                color = Color.White,
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
                            .background(AppChromeElevated.copy(alpha = 0.54f))
                            .border(0.5.dp, Color.White.copy(alpha = 0.13f), RoundedCornerShape(12.dp))
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
private fun StartStationStrip(onClick: () -> Unit, modifier: Modifier = Modifier) {
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
            .clickable(onClick = onClick)
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
    onOpen: () -> Unit,
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
            .clickable(enabled = !card.isLoading, onClick = onOpen)
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
                            .border(0.5.dp, Color.White.copy(alpha = 0.25f), CircleShape)
                            .clickable(enabled = !card.isLoading, onClickLabel = "Play ${card.title}", onClick = onPlay),
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



@Composable
private fun HomeCatalogHighlights(
    homeFeed: GatewayHomeFeed?,
    isLoading: Boolean,
    forYouArtist: String? = null,
    forYouTracks: List<SourceSearchResult> = emptyList(),
    onPlayTrack: (SourceSearchResult) -> Unit,
    onOpenPlaylist: (GatewayHomePlaylist) -> Unit
) {
    if (forYouArtist != null && forYouTracks.isNotEmpty()) {
        HomeCatalogSection(title = "Because you listened to $forYouArtist") {
            items(forYouTracks, key = { "fy:${it.id}" }) { track ->
                GatewayTrackCard(result = track, badge = "FOR YOU", onClick = { onPlayTrack(track) })
            }
        }
    }
    if (homeFeed == null || homeFeed.isEmpty()) {
        if (isLoading) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = VantaSpacing.screenHorizontal, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                repeat(3) {
                    Box(
                        modifier = Modifier
                            .size(148.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(AppSurface.copy(alpha = 0.4f))
                    )
                }
            }
        }
        return
    }
    if (homeFeed.playlists.isNotEmpty()) {
        HomeCatalogSection(title = "Playlists For You") {
            items(homeFeed.playlists, key = { it.id }) { playlist ->
                HomePlaylistCard(playlist = playlist, onClick = { onOpenPlaylist(playlist) })
            }
        }
    }
    homeFeed.freshDrops.takeIf { it.isNotEmpty() }?.let { drops ->
        HomeCatalogSection(title = "Fresh Drops") {
            items(drops, key = { it.id }) { drop ->
                GatewayTrackCard(result = drop, badge = "NEW", onClick = { onPlayTrack(drop) })
            }
        }
    }
    homeFeed.popularTracks.takeIf { it.isNotEmpty() }?.let { popular ->
        HomeCatalogSection(title = "Popular Right Now") {
            items(popular, key = { it.id }) { track ->
                GatewayTrackCard(result = track, badge = "HOT", onClick = { onPlayTrack(track) })
            }
        }
    }
    homeFeed.trendingNow.takeIf { it.isNotEmpty() }?.let { trending ->
        HomeCatalogSection(title = "Trending Now") {
            items(trending, key = { it.id }) { track ->
                GatewayTrackCard(result = track, badge = "TREND", onClick = { onPlayTrack(track) })
            }
        }
    }
}

@Composable
private fun HomeCatalogSection(title: String, content: LazyListScope.() -> Unit) {
    VantaSectionHeader(title, modifier = Modifier.padding(horizontal = VantaSpacing.screenHorizontal))
    Spacer(Modifier.height(10.dp))
    LazyRow(
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = VantaSpacing.screenHorizontal),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        content()
    }
    Spacer(Modifier.height(VantaSpacing.sectionVertical))
}

@Composable
private fun HomePlaylistCard(playlist: GatewayHomePlaylist, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .width(148.dp)
            .clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .size(148.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(AppSurface.copy(alpha = 0.5f))
        ) {
            NetworkArtwork(
                artworkUrl = playlist.artworkUrl,
                seed = playlist.name,
                modifier = Modifier.fillMaxSize()
            )
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color.Black.copy(alpha = 0.55f))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(
                    com.audiophile.musicplayer.data.source.debrandCuratorLabel(playlist.curator).uppercase(),
                    color = Color.White,
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.8.sp
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(playlist.name, color = AppText, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        playlist.description?.takeIf { it.isNotBlank() }?.let {
            Text(it, color = AppTextSecondary, fontSize = 11.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun GatewayTrackCard(result: SourceSearchResult, badge: String, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .width(148.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(AppSurface.copy(alpha = 0.5f))
            .clickable(onClick = onClick)
            .padding(10.dp)
    ) {
        Box(
            modifier = Modifier
                .size(128.dp)
                .clip(RoundedCornerShape(10.dp))
        ) {
            NetworkArtwork(
                artworkUrl = result.artworkUrl,
                seed = "${result.title}-${result.artist}",
                modifier = Modifier.fillMaxSize()
            )
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(6.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(AppAccent.copy(alpha = 0.85f))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(badge, color = Color.Black, fontSize = 8.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp)
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(result.title, color = AppText, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(result.artist, color = AppTextSecondary, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun TopArtistChip(artistName: String, artworkUrl: String?, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .width(96.dp)
            .clickable(onClick = onClick),
        horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(84.dp)
                .clip(androidx.compose.foundation.shape.CircleShape)
                .background(AppSurface.copy(alpha = 0.5f))
        ) {
            NetworkArtwork(
                artworkUrl = artworkUrl,
                seed = artistName,
                modifier = Modifier.fillMaxSize()
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            artistName,
            color = AppText,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

private val friendAvatarPalette = listOf(
    Color(0xFF66D9EF), Color(0xFF93C5FD), Color(0xFFF5B971),
    Color(0xFF60D394), Color(0xFFA78BFA), Color(0xFFF472B6),
    Color(0xFFFB923C), Color(0xFF34D399)
)

@Composable
private fun FriendsListeningSection(
    friendFeed: FriendFeed,
    ownFriendCode: String = "",
    onPlayFriendTrack: (title: String, artist: String) -> Unit,
    onOpenFriendProfile: (friendId: String) -> Unit,
    onOpenAccount: (() -> Unit)?,
    modifier: Modifier = Modifier
) {
    // Purge dummy curator accounts — the user IS the curator
    val friends = remember(friendFeed.friends) {
        friendFeed.friends.filterNot {
            it.id.contains("curator", ignoreCase = true) ||
                it.displayName.contains("curator", ignoreCase = true)
        }
    }
    val eventsByFriend = remember(friendFeed) {
        friendFeed.events.associateBy { it.friendId }
    }
    val activeFriends = remember(friends, eventsByFriend) {
        friends.map { friend ->
            val event = eventsByFriend[friend.id]
            friend to event
        }.sortedWith(
            compareByDescending<Pair<VantaFriend, FriendListeningEvent?>> {
                it.first.isOnline || (it.second != null && System.currentTimeMillis() - it.second!!.startedAtMs <= 20 * 60 * 1000L)
            }.thenByDescending { it.second?.startedAtMs ?: it.first.lastSeenAtMs }
        )
    }

    Column(modifier = modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = VantaSpacing.screenHorizontal),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(androidx.compose.foundation.shape.CircleShape)
                        .background(AppSuccess)
                )
                Text(
                    text = "Friends Are Listening",
                    style = VantaType.sectionTitle
                )
            }
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .glassSurface(shape = RoundedCornerShape(12.dp), surfaceAlpha = 0.5f)
                    .clickable { onOpenAccount?.invoke() }
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.PersonAdd,
                    contentDescription = "Add Friend",
                    tint = AppAccent,
                    modifier = Modifier.size(14.dp)
                )
                Text(
                    text = "Friends",
                    color = AppAccent,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        if (activeFriends.isEmpty()) {
            FriendEmptyInviteCard(
                ownFriendCode = ownFriendCode,
                onAddFriends = { onOpenAccount?.invoke() },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = VantaSpacing.screenHorizontal)
            )
        } else {
            LazyRow(
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = VantaSpacing.screenHorizontal),
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                items(activeFriends, key = { it.first.id }) { (friend, event) ->
                    FriendActivityCard(
                        friend = friend,
                        event = event,
                        onPlay = { t, a -> onPlayFriendTrack(t, a) },
                        onClick = { onOpenFriendProfile(friend.id) }
                    )
                }
            }
        }

        Spacer(Modifier.height(VantaSpacing.sectionVertical))
    }
}

@Composable
private fun FriendActivityCard(
    friend: VantaFriend,
    event: FriendListeningEvent?,
    onPlay: (title: String, artist: String) -> Unit,
    onClick: () -> Unit
) {
    val cleanName = remember(friend.displayName) {
        friend.displayName.replace('_', ' ').trim().ifBlank { "Friend" }
    }
    val avatarBg = remember(friend.avatarSeed, cleanName) {
        val seed = friend.avatarSeed.ifBlank { cleanName }
        val idx = seed.hashCode().let { (it and Int.MAX_VALUE) % friendAvatarPalette.size }
        friendAvatarPalette[if (idx < 0) 0 else idx]
    }
    val isLive = friend.isOnline || (event != null && System.currentTimeMillis() - event.startedAtMs <= 20 * 60 * 1000L)

    Column(
        modifier = Modifier
            .width(240.dp)
            .clip(RoundedCornerShape(18.dp))
            .glassSurfaceElevated(shape = RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Friend header row
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(modifier = Modifier.size(36.dp)) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(androidx.compose.foundation.shape.CircleShape)
                        .background(avatarBg),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = cleanName.take(2).uppercase(),
                        color = Color.Black,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                if (isLive) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .align(Alignment.BottomEnd)
                            .clip(androidx.compose.foundation.shape.CircleShape)
                            .background(AppSuccess)
                            .border(1.5.dp, Color(0xFF16141D), androidx.compose.foundation.shape.CircleShape)
                    )
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = cleanName,
                    color = AppText,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = if (isLive) "Listening now" else "Offline",
                    color = if (isLive) AppSuccess else AppTextMuted,
                    fontSize = 10.sp,
                    fontWeight = if (isLive) FontWeight.SemiBold else FontWeight.Normal
                )
            }
        }

        // Track info
        if (event != null) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(AppSurface.copy(alpha = 0.5f))
                ) {
                    NetworkArtwork(
                        artworkUrl = event.artworkUrl,
                        seed = event.title,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = event.title,
                        color = AppText,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = event.artist,
                        color = AppTextSecondary,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (!event.sourceLabel.isNullOrBlank()) {
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = event.sourceLabel,
                            color = AppAccent,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1
                        )
                    }
                }
            }

            // Listen along button
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(AppAccent.copy(alpha = 0.12f))
                    .border(1.dp, AppAccent.copy(alpha = 0.35f), RoundedCornerShape(10.dp))
                    .clickable { onPlay(event.title, event.artist) }
                    .padding(vertical = 7.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Filled.PlayArrow,
                    contentDescription = "Listen Along",
                    tint = AppAccent,
                    modifier = Modifier.size(15.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = "Listen Along",
                    color = AppAccent,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        } else {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(60.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No recent tracks",
                    color = AppTextMuted,
                    fontSize = 11.sp
                )
            }
        }
    }
}

@Composable
private fun FriendEmptyInviteCard(
    ownFriendCode: String,
    onAddFriends: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val copied = remember { androidx.compose.runtime.mutableStateOf(false) }

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .glassSurfaceElevated(shape = RoundedCornerShape(20.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(androidx.compose.foundation.shape.CircleShape)
                    .background(AppAccent.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.Group,
                    contentDescription = null,
                    tint = AppAccent,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Curate & Listen Together",
                    color = AppText,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "Share codes with fellow audiophiles to stream along in real-time.",
                    color = AppTextMuted,
                    fontSize = 11.sp
                )
            }
        }

        if (ownFriendCode.isNotBlank()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(AppSurface)
                    .border(0.5.dp, AppOutline, RoundedCornerShape(12.dp))
                    .clickable {
                        val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                        clipboard.setPrimaryClip(android.content.ClipData.newPlainText("VANTA Curator Code", ownFriendCode))
                        copied.value = true
                    }
                    .padding(horizontal = 12.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "Your Curator Code:",
                        color = AppTextSecondary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = ownFriendCode,
                        color = AppAccent,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    )
                }
                Text(
                    text = if (copied.value) "Copied!" else "Tap to copy",
                    color = if (copied.value) AppSuccess else AppAccent,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(AppAccent)
                    .clickable(onClick = onAddFriends)
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.PersonAdd,
                    contentDescription = null,
                    tint = Color.Black,
                    modifier = Modifier.size(14.dp)
                )
                Text(
                    text = "Add Friends",
                    color = Color.Black,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

