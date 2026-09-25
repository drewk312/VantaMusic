package com.audiophile.musicplayer.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.audiophile.musicplayer.data.display.DisplayMetadataCleaner
import com.audiophile.musicplayer.social.FriendFeed
import com.audiophile.musicplayer.social.FriendLikedTrack
import com.audiophile.musicplayer.social.FriendListeningEvent
import com.audiophile.musicplayer.social.VantaFriend
import com.audiophile.musicplayer.social.VantaSocialManager
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val avatarColors = listOf(
    Color(0xFF66D9EF), Color(0xFF93C5FD), Color(0xFFF5B971),
    Color(0xFF60D394), Color(0xFFA78BFA), Color(0xFFF472B6),
    Color(0xFFFB923C), Color(0xFF34D399)
)

@Composable
fun FriendProfileScreen(
    friendId: String,
    vantaSocialManager: VantaSocialManager,
    onBack: () -> Unit,
    onPlayTrack: (title: String, artist: String) -> Unit,
    miniPlayerVisible: Boolean = false,
    bottomNavVisible: Boolean = false
) {
    val feed by vantaSocialManager.feed.collectAsState(initial = FriendFeed())
    val friend = remember(friendId, feed) {
        feed.friends.find { it.id == friendId }
    }
    val friendEvents = remember(friendId, feed) {
        feed.events.filter { it.friendId == friendId }
            .sortedByDescending { it.startedAtMs }
    }
    val latestEvent = friendEvents.firstOrNull()

    val avatarColor = remember(friend?.avatarSeed) {
        val seed = friend?.avatarSeed ?: friendId
        val index = seed.hashCode().let { (it and Int.MAX_VALUE) % avatarColors.size }
        avatarColors[if (index < 0) 0 else index]
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(AppBackgroundTop, AppBackgroundBottom)))
            .padding(top = appTopContentPadding())
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = AppText)
            }
            Text(
                text = "Profile",
                color = AppText,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.width(48.dp))
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = VantaSpacing.screenHorizontal),
            verticalArrangement = Arrangement.spacedBy(VantaSpacing.itemGap)
        ) {
            item {
                FriendProfileHeader(
                    friend = friend,
                    avatarColor = avatarColor,
                    latestEvent = latestEvent,
                    onPlayTrack = onPlayTrack
                )
            }

            if (latestEvent != null) {
                item {
                    SectionHeader(title = "Now Playing")
                }
                item {
                    NowPlayingCard(
                        event = latestEvent,
                        onPlayTrack = onPlayTrack
                    )
                }
            }

            if (friendEvents.size > 1) {
                item {
                    SectionHeader(title = "Recent Activity")
                }
                items(friendEvents.drop(1).take(10)) { event ->
                    ActivityHistoryCard(
                        event = event,
                        onPlayTrack = onPlayTrack
                    )
                }
            }

            if (friend?.likedTracks?.isNotEmpty() == true) {
                item {
                    SectionHeader(title = "Liked Tracks")
                }
                items(friend.likedTracks) { track ->
                    LikedTrackCard(
                        track = track,
                        onPlayTrack = onPlayTrack
                    )
                }
            }

            item {
                Spacer(modifier = Modifier.height(appBottomContentPadding(miniPlayerVisible, bottomNavVisible)))
            }
        }
    }
}

@Composable
private fun FriendProfileHeader(
    friend: VantaFriend?,
    avatarColor: Color,
    latestEvent: FriendListeningEvent?,
    onPlayTrack: (title: String, artist: String) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(16.dp))

        val cleanName = remember(friend?.displayName) {
            friend?.displayName?.replace('_', ' ')?.trim()?.ifBlank { "Friend" } ?: "Friend"
        }

        Box(
            modifier = Modifier
                .size(100.dp)
                .clip(CircleShape)
                .background(avatarColor.copy(alpha = 0.2f))
                .border(3.dp, avatarColor.copy(alpha = 0.5f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = (friend?.avatarSeed?.takeIf { it.isNotBlank() } ?: cleanName).take(2).uppercase(),
                color = avatarColor,
                fontSize = 36.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = cleanName,
            color = AppText,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(4.dp))

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(if (friend?.isOnline == true) AppSuccess else AppTextMuted)
            )
            Text(
                text = if (friend?.isOnline == true) "Online" else "Offline",
                color = AppTextSecondary,
                fontSize = 13.sp
            )
        }

        if (latestEvent != null) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Listening to ${DisplayMetadataCleaner.cleanDisplayName(latestEvent.title)}",
                color = AppAccent,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        color = AppTextSecondary,
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun NowPlayingCard(
    event: FriendListeningEvent,
    onPlayTrack: (title: String, artist: String) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(AppSurface)
            .border(0.5.dp, AppAccent.copy(alpha = 0.2f), RoundedCornerShape(14.dp))
            .clickable { onPlayTrack(event.title, event.artist) }
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(AppSurfaceRaised),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.MusicNote,
                contentDescription = null,
                tint = AppAccent,
                modifier = Modifier.size(24.dp)
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = DisplayMetadataCleaner.cleanDisplayName(event.title),
                color = AppText,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = event.artist,
                color = AppTextSecondary,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (event.album != null) {
                Text(
                    text = event.album,
                    color = AppTextMuted,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Icon(
            Icons.Default.PlayArrow,
            contentDescription = "Play",
            tint = AppAccent,
            modifier = Modifier.size(24.dp)
        )
    }
}

@Composable
private fun ActivityHistoryCard(
    event: FriendListeningEvent,
    onPlayTrack: (title: String, artist: String) -> Unit
) {
    val timeAgo = remember(event.startedAtMs) {
        formatTimeAgo(event.startedAtMs)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(AppSurface)
            .clickable { onPlayTrack(event.title, event.artist) }
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(AppSurfaceRaised),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.MusicNote,
                contentDescription = null,
                tint = AppTextMuted,
                modifier = Modifier.size(18.dp)
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = DisplayMetadataCleaner.cleanDisplayName(event.title),
                color = AppText,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = event.artist,
                color = AppTextMuted,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Text(
            text = timeAgo,
            color = AppTextMuted,
            fontSize = 11.sp
        )
    }
}

@Composable
private fun LikedTrackCard(
    track: FriendLikedTrack,
    onPlayTrack: (title: String, artist: String) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(AppSurface)
            .clickable { onPlayTrack(track.title, track.artist) }
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(AppSurfaceRaised),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.Favorite,
                contentDescription = null,
                tint = AppAccent,
                modifier = Modifier.size(18.dp)
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = DisplayMetadataCleaner.cleanDisplayName(track.title),
                color = AppText,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = track.artist,
                color = AppTextMuted,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

private fun formatTimeAgo(timestampMs: Long): String {
    val now = System.currentTimeMillis()
    val diff = now - timestampMs
    val seconds = diff / 1000
    val minutes = seconds / 60
    val hours = minutes / 60
    val days = hours / 24

    return when {
        seconds < 60 -> "Just now"
        minutes < 60 -> "${minutes}m ago"
        hours < 24 -> "${hours}h ago"
        days < 7 -> "${days}d ago"
        else -> {
            val sdf = SimpleDateFormat("MMM d", Locale.getDefault())
            sdf.format(Date(timestampMs))
        }
    }
}
