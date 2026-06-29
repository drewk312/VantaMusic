package com.audiophile.musicplayer.data.dj

data class DjCommentary(
    val text: String,
    val fromPulseAi: Boolean,
    val isSilent: Boolean = false
) {
    companion object {
        val Silent = DjCommentary(text = "", fromPulseAi = true, isSilent = true)
    }
}
