package com.audiophile.musicplayer.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.time.Instant
import java.util.concurrent.TimeUnit

private data class ArtistEditorial(val id: String, val artist: String, val title: String, val description: String, val source: String, val sourceLabel: String, val song: String)

@Composable
fun ArtistCelebrationCard(onExplore: (String) -> Unit, onListen: (String, String) -> Unit) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val preferences = remember { context.getSharedPreferences("editorial_cards", 0) }
    var card by remember { mutableStateOf<ArtistEditorial?>(null) }
    LaunchedEffect(Unit) {
        card = withContext(Dispatchers.IO) {
            runCatching {
                val client = OkHttpClient.Builder().callTimeout(8, TimeUnit.SECONDS)
                    .addInterceptor(com.audiophile.musicplayer.playback.GatewayApiKeyInterceptor)
                    .build()
                client.newCall(Request.Builder().url("https://vanta-music-gateway.16drewk.workers.dev/api/editorial").build()).execute().use { response ->
                    if (!response.isSuccessful) return@use null
                    val entries = JsonParser.parseString(response.body?.string()).asJsonObject.getAsJsonArray("entries")
                    entries.map { it.asJsonObject }.firstOrNull {
                        !preferences.getBoolean(it.get("id").asString, false) &&
                            Instant.parse(it.get("expiresAt").asString).isAfter(Instant.now()) &&
                            it.get("kind").asString == "tribute" && it.get("sponsored").asBoolean == false
                    }?.let {
                        ArtistEditorial(it.get("id").asString, it.get("artist").asString, it.get("title").asString,
                            it.get("description").asString, it.get("sourceUrl").asString, it.get("sourceLabel").asString, it.get("featuredSong").asString)
                    }
                }
            }.getOrNull()
        }
    }
    val item = card ?: return
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp)
        .background(Brush.linearGradient(listOf(Color(0xFF332B23), AppSurface)), RoundedCornerShape(24.dp)).padding(24.dp)) {
        Text("A LIFE IN MUSIC", color = AppAccent, fontSize = 11.sp, letterSpacing = 2.sp)
        Spacer(Modifier.height(12.dp))
        Text(item.title, color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(10.dp))
        Text(item.description, color = Color(0xFFE2DDE5), fontSize = 14.sp)
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { onListen(item.song, item.artist) }) { Text("Listen") }
            OutlinedButton(onClick = { onExplore(item.artist) }) { Text("Explore songs", color = Color.White) }
        }
        TextButton(onClick = { if (item.source.startsWith("https://")) uriHandler.openUri(item.source) }) {
            Text(item.sourceLabel, color = AppAccent, fontSize = 11.sp)
        }
        TextButton(onClick = { preferences.edit().putBoolean(item.id, true).apply(); card = null }) { Text("Hide this celebration", color = Color.LightGray) }
    }
}
