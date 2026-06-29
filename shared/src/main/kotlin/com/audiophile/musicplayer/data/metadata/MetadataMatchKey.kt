package com.audiophile.musicplayer.data.metadata

object MetadataMatchKey {
    fun generateKey(isrc: String?, title: String, artist: String, album: String? = null): String {
        if (!isrc.isNullOrBlank()) {
            return "isrc:${isrc.trim().uppercase()}"
        }
        
        val normalizedTitle = normalize(title)
        val normalizedArtist = normalize(artist)
        val normalizedAlbum = album?.let { normalize(it) }.orEmpty()
        
        return "text:$normalizedTitle:$normalizedArtist:$normalizedAlbum"
    }
    
    private fun normalize(value: String): String {
        return value.lowercase()
            .replace(Regex("""[^\p{L}\p{N}]+"""), "") // Strip spaces and punctuation
            .trim()
    }
}
