package com.audiophile.musicplayer.data.source

/**
 * Hard gate for catalog/search/library ingest. Rejects live streams, karaoke, tribute
 * tracks, and other content that does not match VANTA's luxury audiophile positioning.
 */
object ContentPurityFilter {

    /** A multi-song upload is not a recording, even when a short duration is supplied. */
    fun isCompilationUpload(title: String?, durationMs: Long? = null): Boolean {
        val text = title.orEmpty().lowercase().replace(Regex("""\s+"""), " ")
        val rankedYear = Regex("""\b(?:top|best|greatest|biggest)\s+(?:\d+\s+)?(?:songs|hits|tracks)\s+(?:(?:of|from|in)\s+)?(?:the\s+)?(?:19|20)\d{2}s?\b""")
        val countList = Regex("""\b(?:top|best)\s+\d+\s+(?:songs|hits|tracks)\b""")
        val yearsOfSongs = Regex("""\b\d+\s+years?\s+\d+\s+songs?\b""")
        val fullCollection = Regex("""\b(?:full album|complete album|non[ -]?stop mix|greatest hits mix|dj set|music compilation|songs compilation)\b""")
        if (rankedYear.containsMatchIn(text) || countList.containsMatchIn(text) || yearsOfSongs.containsMatchIn(text) || fullCollection.containsMatchIn(text)) return true
        return durationMs != null && durationMs > 15 * 60_000L &&
            Regex("""\b(?:playlist|compilation|oldies mix|hits mix|best of|top songs|continuous mix)\b""").containsMatchIn(text)
    }

    /**
     * High-confidence rejection used at persistence and playback boundaries.
     *
     * Keep this deliberately narrower than [isAllowed]: a saved queue must never
     * resurrect an obvious talk/news/review video, but it also must not delete a
     * legitimate artist just because their name happens to contain a place or a
     * generic word such as "radio".
     */
    fun isClearlyNonMusicContent(
        title: String?,
        artist: String?,
        album: String? = null,
        durationMs: Long? = null
    ): Boolean {
        if (isCompilationUpload(title, durationMs)) return true
        val t = title.orEmpty().lowercase().replace(Regex("""\s+"""), " ").trim()
        val a = artist.orEmpty().lowercase().replace(Regex("""\s+"""), " ").trim()
        val stack = "$t $a ${album.orEmpty().lowercase()}"

        // Educational explainers can arrive from a saved video library too.
        // Require both a technical subject and instructional phrasing, so a song
        // named "Electronics" or an artist named "The Tutorial" remains eligible.
        val technicalSubject = Regex("""\b(electronics|circuits?|transistors?|capacitors?|resistors?|programming|coding)\b""")
            .containsMatchIn(t)
        val instructionalTitle = Regex("""\b(tutorial|explained|beginners?|how to|things in|confused me|learn to)\b""")
            .containsMatchIn(t)
        if (technicalSubject && instructionalTitle) return true

        val blockedPublishers = listOf(
            "barstool pizza review",
            "front america"
        )
        if (blockedPublishers.any { a == it || a.contains(it) }) return true

        // WIRED is also an artist name, so only reject the publisher when its
        // title has an unmistakable interview/explainer shape.
        if (a == "wired" && listOf(
                " answers ", "answers ", "tech support", "autocomplete interview",
                "most searched questions", "explains", "breaks down"
            ).any { marker -> t.contains(marker.trim()) }
        ) return true

        val travelChannel = listOf("travel", "tourism", "vacation guide").any(a::contains)
        val travelVideo = listOf(
            "travel itinerary", "travel guide", "things to do in", "how to spend",
            "days in sydney", "days in paris", "days in london", "days in new york"
        ).any(t::contains)
        if (travelChannel && travelVideo) return true

        val reviewChannel = listOf("pizza review", "restaurant review", "food review")
            .any(a::contains)
        val restaurantVideo = Regex("""\b(pizza|pizzeria|restaurant)\b""").containsMatchIn(t)
        if (reviewChannel && restaurantVideo) return true

        val channelLikeArtist = listOf(
            " news", "news ", " news ", "review", "podcast", "tv", "television",
            "network", "current affairs", "politics"
        ).any(a::contains)
        val spokenVideoTitle = listOf(
            "answers questions", "answers voice", "interview with", "presented by",
            "deadliest trap", "breaking news", "full episode", "podcast episode",
            "reveals how", "how to write a hit", "in 8 minutes", "explained",
            "reaction to", "breaks down", "talking about", "talks about",
            "reacting to", "reacts to", "first time hearing", "ranked every",
            "every song ranked", "tier list", "behind the lyrics", "song meaning",
            "the meaning of this", "what this song", "songs that make you"
        ).any(t::contains)
        if (channelLikeArtist && spokenVideoTitle) return true

        // Commentary / reaction videos often use a music-channel artist name.
        val songTalkTitle = listOf(
            "talking about", "talks about", "reacting to", "reacts to",
            "first time hearing", "ranked every", "every song ranked",
            "tier list", "behind the lyrics", "song meaning",
            "the meaning of this", "what this song means", "songs that make you"
        ).any(t::contains)
        if (songTalkTitle) return true

        // Extremely long spoken uploads with explicit episode/interview metadata
        // are never songs. Duration alone is intentionally not enough.
        if (durationMs != null && durationMs > 20 * 60 * 1000L &&
            listOf("podcast", "episode", "interview", "news", "review").any(stack::contains)
        ) return true

        return false
    }

    fun isAllowed(
        title: String?,
        artist: String?,
        album: String?,
        durationMs: Long?,
        source: String?,
        userQuery: String? = null
    ): Boolean {
        if (isClearlyNonMusicContent(title, artist, album, durationMs)) return false

        if (!VocalRecordingClassifier.shouldAllowInCatalog(title, artist, album, userQuery)) {
            return false
        }

        val t = (title ?: "").lowercase()
        val a = (artist ?: "").lowercase()
        val stack = VocalRecordingClassifier.haystack(title, artist, album)

        // BAN: YouTube live streams (ruins luxury vibe, > 4 hours)
        if (durationMs != null && durationMs > 14400000L) return false
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
            "news", "weather", "traffic", "podcast", "episode",
            "daily", "morning show", "evening update", "live at", "coverage",
            "watch mojo", "watchmojo", "top 10", "facts about", "biography",
            "audio library", "no copyright"
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
            "originally performed",
            "backing track",
            "the backing tracks",
            "type beat",
            "dj mix",
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

        val typeDumpTitle = title.orEmpty().count { it == ',' } >= 2 &&
            Regex("""\btype\b""").containsMatchIn(t)
        if (typeDumpTitle) return false

        // BAN: Workout / style-pack variants unless explicitly searched
        if (VariantClassifier.isWorkoutOrStylePackVariant(title, artist, album) &&
            !VariantClassifier.userAllowsVariantType(userQuery, VariantClassifier.VariantType.REMIX)
        ) {
            return false
        }

        // BAN: listicle / compilation album junk
        if (isPlaylistCompilationArtifact(title.orEmpty(), artist.orEmpty(), album, durationMs)) {
            return false
        }

        // Legacy broadcast/sports filters still apply for album context
        if (isBroadcastLikeMetadata(title.orEmpty(), artist.orEmpty(), album)) return false
        if (isSportsOrVideoMetadata(title.orEmpty(), artist.orEmpty(), album)) return false

        return true
    }
}
