package com.audiophile.musicplayer.ui

import android.util.Log
import androidx.compose.runtime.Composable
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
    crossfade: Boolean = true
) {
    val url = artworkUrl?.takeIf { it.isNotBlank() }

    if (url != null) {
        val context = LocalContext.current
        AsyncImage(
            model = ImageRequest.Builder(context)
                .data(url)
                .crossfade(crossfade)
                .build(),
            contentDescription = "Album artwork",
            modifier = modifier,
            contentScale = ContentScale.Crop,
            onError = { e ->
                Log.w("VANTA_UI", "NetworkArtwork load error $url: ${e.result.throwable?.message}")
            }
        )
    } else {
        ArtworkPlaceholder(seed = seed, modifier = modifier)
    }
}
