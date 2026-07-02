package com.audiophile.musicplayer.data.source

/**
 * Hard gate for catalog/search/library ingest. Rejects live streams, karaoke, tribute
 * tracks, and other content that does not match VANTA's luxury audiophile positioning.
 */
object ContentPurityFilter {

    fun isAllowed(
        title: String?,
        artist: String?,
        album: String?,
        durationMs: Long?,
        source: String?,
        userQuery: String? = null
    ): Boolean {
        if (!VocalRecordingClassifier.shouldAllowInCatalog(title, artist, album, userQuery)) {
            return false
        }

        val t = (title ?: "").lowercase()
        val a = (artist ?: "").lowercase()
        val stack = VocalRecordingClassifier.haystack(title, artist, album)

        // BAN: YouTube live streams (ruins luxury vibe)
        if (durationMs != null && (durationMs > 14400000L || durationMs <= 0L)) return false
        val liveKeywords = listOf(
            "beats to relax",
            "beats to study",
            "beats to sleep",
            "beats to chill",
            "radio 🌌",
            "live stream",
            "24/7",
            "beats to relax/study to",
            "beats to sleep/chill to",
            "radio – beats to",
            "streaming now",
            "playing now"
        )
        if (liveKeywords.any { t.contains(it) }) return false

        // BAN: YouTube-specific "artists" that only do live streams
        val bannedArtists = listOf("lofi girl", "steezyasfuck", "chilledcow")
        if (bannedArtists.any { a.contains(it) }) return false

        // BAN: Non-music channel patterns (news, weather, location names, podcasts, SEO uploaders)
        val nonMusicArtists = listOf(
            "news", "weather", "traffic", "radio", "podcast", "episode",
            "daily", "morning show", "evening update", "live at", "coverage",
            "watch mojo", "watchmojo", "top 10", "facts about", "biography",
            "lyrics", "lyric", "official video", "official audio", "official music video",
            "explicit", "clean version", "audio library", "no copyright",
            "alabama", "alaska", "arizona", "arkansas", "california",
            "colorado", "connecticut", "delaware", "florida", "georgia",
            "hawaii", "idaho", "illinois", "indiana", "iowa", "kansas",
            "kentucky", "louisiana", "maine", "maryland", "massachusetts",
            "michigan", "minnesota", "mississippi", "missouri", "montana",
            "nebraska", "nevada", "new hampshire", "new jersey", "new mexico",
            "new york", "north carolina", "north dakota", "ohio", "oklahoma",
            "oregon", "pennsylvania", "rhode island", "south carolina",
            "south dakota", "tennessee", "texas", "utah", "vermont",
            "virginia", "washington", "west virginia", "wisconsin", "wyoming"
        )
        if (nonMusicArtists.any { a.contains(it) }) return false

        // BAN: Low-quality variants
        val junkKeywords = listOf(
            "instrumental",
            "karaoke",
            "acapella",
            "a cappella",
            "tribute",
            "8-bit",
            "8bit",
            "chiptune",
            "made famous by",
            "in the style of",
            "midi",
            "fl studio",
            "garageband",
            "nightcore",
            "daycore",
            "sped up",
            "reverb",
            "slowed",
            "slowed and reverb",
            "tiktok",
            "ringtone",
            "alarm",
            "q&a",
            "q&amp;a",
            "qna",
            "q & a",
            "interview",
            "vlog",
            "podcast",
            "commentary",
            "behind the scenes",
            "making of",
            "spoken word",
            "discussion",
            "why he writes",
            "why she writes",
            "meaning of",
            "explained",
            "reaction"
        )
        if (junkKeywords.any { stack.contains(it) }) return false

        // Legacy broadcast/sports filters still apply for album context
        if (isBroadcastLikeMetadata(title.orEmpty(), artist.orEmpty(), album)) return false
        if (isSportsOrVideoMetadata(title.orEmpty(), artist.orEmpty(), album)) return false

        return true
    }
}
