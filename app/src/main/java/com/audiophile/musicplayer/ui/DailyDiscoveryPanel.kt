package com.audiophile.musicplayer.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.audiophile.musicplayer.data.canonical.CanonicalTrack

@Composable
fun DailyDiscoveryPanel(viewModel: DailyDiscoveryViewModel, onPlay: (CanonicalTrack) -> Unit, onSave: (CanonicalTrack) -> Unit) {
    val state by viewModel.state.collectAsState()
    val dismissKeyboard = rememberKeyboardDismissal()
    var tuning by remember { mutableStateOf(false) }
    LaunchedEffect(state.picks) { if (state.picks.isNotEmpty()) tuning = false }
    LaunchedEffect(Unit) { viewModel.load() }
    Column(
        Modifier.padding(horizontal = 20.dp).fillMaxWidth().clip(RoundedCornerShape(24.dp))
            .background(Brush.linearGradient(listOf(AppAccent.copy(alpha = 0.17f), AppSurfaceRaised)))
            .border(0.5.dp, AppOutline, RoundedCornerShape(24.dp)).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text("Daily Discover", color = AppText, fontSize = 27.sp, fontWeight = FontWeight.Bold)
        Text("A little familiar. A little unexpected. Find what moves you next.", color = AppTextSecondary, fontSize = 14.sp)
        if (state.picks.isNotEmpty()) {
            Text("${state.category.ifBlank { "Your favorites" }} · ${listOf("Keep it close", "Branch out", "Surprise me")[state.openness]}", color = AppAccent, fontSize = 13.sp)
            TextButton(onClick = { tuning = !tuning }) { Text(if (tuning) "Done tuning" else "Tune your discovery", color = AppAccent) }
        }
        if (tuning || state.picks.isEmpty()) {
        Text("Start with a sound", color = AppText, fontWeight = FontWeight.SemiBold)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(listOf("Hindi Bollywood", "Pop", "Indie", "R&B", "Jazz", "Rock", "Electronic", "Country", "K-Pop", "Afrobeats")) { name ->
                FilterChip(selected = state.category == name, onClick = { dismissKeyboard(); viewModel.chooseCategory(name) }, label = { Text(name) })
            }
        }
        Text("How far do you want to go?", color = AppText, fontWeight = FontWeight.SemiBold)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(3) { level ->
                FilterChip(selected = state.openness == level, onClick = { dismissKeyboard(); viewModel.openness(level) },
                    label = { Text(listOf("Keep it close", "Branch out", "Surprise me")[level]) })
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Explore other cultures", color = AppTextSecondary, modifier = Modifier.weight(1f))
            Switch(checked = state.otherLanguages, onCheckedChange = { dismissKeyboard(); viewModel.languages(it) })
        }
        OutlinedTextField(value = state.request, onValueChange = viewModel::request,
            label = { Text("Ask AI to fine-tune it") },
            placeholder = { Text("Dreamy Hindi songs, fewer big hits") }, modifier = Modifier.fillMaxWidth(), maxLines = 3,
            colors = OutlinedTextFieldDefaults.colors(focusedTextColor = AppText, unfocusedTextColor = AppText,
                focusedBorderColor = AppAccent, unfocusedBorderColor = AppOutline))
        }
        Button(onClick = { dismissKeyboard(); viewModel.load(refresh = true) }, enabled = !state.loading,
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
            colors = ButtonDefaults.buttonColors(containerColor = AppAccent, contentColor = AppBackground)) {
            Text(if (state.loading) "Finding your next favorites..." else if (state.picks.isEmpty()) "Find my music" else "Try a fresh set")
        }
        if (state.loading) LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = AppAccent)
        state.message?.let { Text(it, color = AppTextSecondary, fontSize = 13.sp) }
        if (!state.loading && state.picks.isNotEmpty()) {
            Text(if (state.aiRanked) "AI picked for you · ${state.date}" else "Picked from your taste · ${state.date}", color = AppAccent, fontSize = 12.sp)
            LazyRow(modifier = Modifier.testTag("daily-discovery-picks"), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(state.picks, key = { com.audiophile.musicplayer.data.catalog.DiscoveryPolicy.key(it.track) }) { pick ->
                Column(Modifier.width(280.dp).clip(RoundedCornerShape(16.dp)).background(AppSurface).padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(Modifier.fillMaxWidth().clickable { dismissKeyboard(); onPlay(pick.track) }.heightIn(min = 56.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        NetworkArtwork(artworkUrl = pick.track.artworkUrl, seed = pick.track.title, modifier = Modifier.size(56.dp).clip(RoundedCornerShape(10.dp)))
                        Column(Modifier.weight(1f)) {
                            Text(pick.track.title, color = AppText, fontWeight = FontWeight.SemiBold, maxLines = 2)
                            Text(pick.track.artist, color = AppTextSecondary, fontSize = 13.sp)
                        }
                        Icon(Icons.Default.PlayArrow, contentDescription = "Play ${pick.track.title}", tint = AppAccent)
                    }
                    Text(pick.reason, color = AppTextSecondary, fontSize = 12.sp)
                    Row(Modifier.fillMaxWidth()) {
                        TextButton(onClick = { viewModel.feedback(pick, true) }, modifier = Modifier.weight(1f)) { Text("More like this", color = AppAccent) }
                        TextButton(onClick = { viewModel.feedback(pick, false) }, modifier = Modifier.weight(1f)) { Text("Not for me", color = AppTextSecondary) }
                    }
                    TextButton(onClick = { onSave(pick.track) }) { Text("Save to library", color = AppAccent) }
                }
            }
            }
        }
    }
}
