package com.audiophile.musicplayer.ui

import android.util.Log
import androidx.core.net.toUri
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.audiophile.musicplayer.ui.preview.ArtworkPlaceholder

@Composable
fun NetworkArtwork(
    artworkUrl: String?,
    seed: String,
    modifier: Modifier = Modifier,
    crossfade: Boolean = false
) {
    val url = artworkUrl?.takeIf { it.isNotBlank() }

    if (url != null) {
        val context = LocalContext.current
        val request = remember(context, url, crossfade) {
            ImageRequest.Builder(context)
                .data(url)
                // Cached artwork should appear in the same frame. Crossfades
                // make a warm cache still feel delayed when moving between songs.
                .crossfade(crossfade)
                .build()
        }
        var loaded by remember(url) { mutableStateOf(false) }
        Box(modifier = modifier) {
            if (!loaded) {
                ArtworkPlaceholder(seed = seed, modifier = Modifier.fillMaxSize())
            }
            AsyncImage(
                model = request,
                contentDescription = "Album artwork",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                onSuccess = { loaded = true },
                onError = { e ->
                    loaded = false
                    val host = runCatching { url.toUri().host }.getOrNull() ?: "unknown"
                    Log.w("VANTA_UI", "Artwork load failed host=$host: ${e.result.throwable.javaClass.simpleName}")
                }
            )
        }
    } else {
        ArtworkPlaceholder(seed = seed, modifier = modifier)
    }
}
