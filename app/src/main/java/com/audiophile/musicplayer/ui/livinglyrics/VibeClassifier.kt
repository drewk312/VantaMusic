package com.audiophile.musicplayer.ui.livinglyrics

import android.util.Log

/**
 * Classifies a song's vibe profile from metadata signals: artist, title, album,
 * genre, lyric keywords, and audio energy. This is the "taste" layer that decides
 * which visual family a song belongs to — before any rendering happens.
 *
 * The classifier uses a scoring system: every signal adds weight to multiple
 * dimensions, and the highest-scoring scene family wins.
 */
object VibeClassifier {

    // ── Artist fingerprints ─────────────────────────────────────────────
    private data class ArtistFingerprint(
        val patterns: List<String>,
        val scene: LivingSceneType,
        val energy: Float, val darkness: Float, val aggression: Float,
        val warmth: Float, val industrial: Float, val spiritual: Float,
        val urban: Float, val nostalgia: Float, val romance: Float,
        val genre: String
    )

    private val ARTIST_FINGERPRINTS = listOf(
        // Industrial / Metal
        ArtistFingerprint(
            patterns = listOf("rammstein", "nine inch nails", "ministry", "kmfdm", "marilyn manson", "rob zombie", "static-x", "oomph!"),
            scene = LivingSceneType.INDUSTRIAL_STAGE,
            energy = 0.95f, darkness = 0.9f, aggression = 0.95f,
            warmth = 0.05f, industrial = 0.95f, spiritual = 0.0f,
            urban = 0.3f, nostalgia = 0.0f, romance = 0.0f,
            genre = "industrial metal"
        ),
        ArtistFingerprint(
            patterns = listOf("metallica", "slayer", "megadeth", "pantera", "lamb of god", "tool", "system of a down", "rage against the machine", "slipknot", "korn", "disturbed"),
            scene = LivingSceneType.INDUSTRIAL_STAGE,
            energy = 0.9f, darkness = 0.8f, aggression = 0.85f,
            warmth = 0.1f, industrial = 0.6f, spiritual = 0.0f,
            urban = 0.2f, nostalgia = 0.0f, romance = 0.0f,
            genre = "metal"
        ),
        // Classic Rock / Road Rock
        ArtistFingerprint(
            patterns = listOf("zz top", "ac/dc", "led zeppelin", "deep purple", "lynyrd skynyrd", "aerosmith", "boston", "journey", "def leppard", "van halen", "guns n' roses", "bon jovi"),
            scene = LivingSceneType.NIGHT_HIGHWAY_STAGE,
            energy = 0.8f, darkness = 0.3f, aggression = 0.4f,
            warmth = 0.7f, industrial = 0.1f, spiritual = 0.0f,
            urban = 0.2f, nostalgia = 0.5f, romance = 0.2f,
            genre = "classic rock"
        ),
        // 80s Synth/Pop
        ArtistFingerprint(
            patterns = listOf("tears for fears", "depeche mode", "a-ha", "duran duran", "pet shop boys", "new order", "the cure", "erasure", "talk talk", "omd", "gary numan"),
            scene = LivingSceneType.CITY_REFLECTION,
            energy = 0.6f, darkness = 0.3f, aggression = 0.1f,
            warmth = 0.5f, industrial = 0.1f, spiritual = 0.1f,
            urban = 0.5f, nostalgia = 0.7f, romance = 0.3f,
            genre = "synth pop"
        ),
        // Rap / Hip-Hop
        ArtistFingerprint(
            patterns = listOf("eminem", "kendrick lamar", "drake", "kanye west", "jay-z", "travis scott", "lil wayne", "future", "21 savage", "migos", "j. cole", "nas", "tupac", "biggie", "a\$ap rocky", "playboi carti", "metro boomin"),
            scene = LivingSceneType.NIGHT_CITY_PULSE,
            energy = 0.8f, darkness = 0.5f, aggression = 0.5f,
            warmth = 0.2f, industrial = 0.1f, spiritual = 0.0f,
            urban = 0.95f, nostalgia = 0.0f, romance = 0.1f,
            genre = "hip hop"
        ),
        ArtistFingerprint(
            patterns = listOf("fred again", "fred again..", "fred again...", "skepta"),
            scene = LivingSceneType.NIGHT_CITY_PULSE,
            energy = 0.82f, darkness = 0.45f, aggression = 0.35f,
            warmth = 0.35f, industrial = 0.05f, spiritual = 0.0f,
            urban = 0.82f, nostalgia = 0.15f, romance = 0.1f,
            genre = "electronic rap"
        ),
        // Punk / Garage
        ArtistFingerprint(
            patterns = listOf("ramones", "sex pistols", "the clash", "green day", "blink-182", "bad religion", "nofx", "the offspring", "dead kennedys", "misfits", "black flag"),
            scene = LivingSceneType.BASEMENT_STAGE,
            energy = 0.9f, darkness = 0.4f, aggression = 0.7f,
            warmth = 0.3f, industrial = 0.2f, spiritual = 0.0f,
            urban = 0.5f, nostalgia = 0.3f, romance = 0.0f,
            genre = "punk"
        ),
        // Country / Folk
        ArtistFingerprint(
            patterns = listOf("johnny cash", "willie nelson", "dolly parton", "hank williams", "merle haggard", "george strait", "garth brooks", "chris stapleton", "luke combs", "zach bryan", "tyler childers"),
            scene = LivingSceneType.OPEN_FIELD_ROAD,
            energy = 0.4f, darkness = 0.2f, aggression = 0.05f,
            warmth = 0.9f, industrial = 0.0f, spiritual = 0.2f,
            urban = 0.0f, nostalgia = 0.7f, romance = 0.4f,
            genre = "country"
        ),
        // Spiritual / Gospel
        ArtistFingerprint(
            patterns = listOf("norman greenbaum"),
            scene = LivingSceneType.OPEN_ROAD_SKY,
            energy = 0.6f, darkness = 0.1f, aggression = 0.0f,
            warmth = 0.7f, industrial = 0.0f, spiritual = 0.9f,
            urban = 0.0f, nostalgia = 0.4f, romance = 0.0f,
            genre = "psychedelic rock"
        )
    )

    // ── Genre keyword → scene mappings ───────────────────────────────────
    private data class GenreSignal(
        val keywords: List<String>,
        val scene: LivingSceneType,
        val energy: Float, val darkness: Float, val aggression: Float,
        val industrial: Float, val urban: Float, val spiritual: Float,
        val warmth: Float
    )

    private val GENRE_SIGNALS = listOf(
        GenreSignal(listOf("industrial", "neue deutsche härte", "ndh"), LivingSceneType.INDUSTRIAL_STAGE, 0.9f, 0.9f, 0.9f, 0.95f, 0.2f, 0.0f, 0.05f),
        GenreSignal(listOf("metal", "thrash", "death metal", "black metal", "doom", "nu metal", "metalcore", "deathcore"), LivingSceneType.INDUSTRIAL_STAGE, 0.9f, 0.85f, 0.85f, 0.5f, 0.1f, 0.0f, 0.05f),
        GenreSignal(listOf("hard rock", "classic rock", "southern rock", "blues rock", "arena rock"), LivingSceneType.NIGHT_HIGHWAY_STAGE, 0.75f, 0.3f, 0.4f, 0.1f, 0.15f, 0.0f, 0.6f),
        GenreSignal(listOf("synth pop", "new wave", "synthwave", "darkwave", "post-punk", "coldwave"), LivingSceneType.CITY_REFLECTION, 0.5f, 0.4f, 0.1f, 0.2f, 0.5f, 0.0f, 0.4f),
        GenreSignal(listOf("hip hop", "rap", "trap", "drill", "grime", "crunk"), LivingSceneType.NIGHT_CITY_PULSE, 0.8f, 0.5f, 0.5f, 0.1f, 0.9f, 0.0f, 0.2f),
        GenreSignal(listOf("punk", "garage rock", "garage punk", "post-punk", "hardcore punk", "skate punk"), LivingSceneType.BASEMENT_STAGE, 0.85f, 0.4f, 0.7f, 0.2f, 0.5f, 0.0f, 0.2f),
        GenreSignal(listOf("country", "folk", "bluegrass", "americana", "outlaw country"), LivingSceneType.OPEN_FIELD_ROAD, 0.4f, 0.15f, 0.05f, 0.0f, 0.0f, 0.15f, 0.85f),
        GenreSignal(listOf("gospel", "christian", "worship", "spiritual", "hymn"), LivingSceneType.CHURCH_LIGHT, 0.5f, 0.1f, 0.0f, 0.0f, 0.0f, 0.9f, 0.7f),
        GenreSignal(listOf("r&b", "soul", "neo soul", "quiet storm"), LivingSceneType.BEDROOM_MEMORY, 0.4f, 0.3f, 0.0f, 0.0f, 0.4f, 0.0f, 0.7f),
        GenreSignal(listOf("electronic", "edm", "house", "techno", "trance"), LivingSceneType.CITY_NIGHT, 0.8f, 0.4f, 0.2f, 0.3f, 0.6f, 0.0f, 0.3f),
        GenreSignal(listOf("ambient", "chillout", "downtempo"), LivingSceneType.STORM_WINDOW, 0.2f, 0.4f, 0.0f, 0.0f, 0.2f, 0.2f, 0.5f),
    )

    // ── Lyric keyword signals ────────────────────────────────────────────
    private data class LyricKeywordSignal(
        val keywords: List<String>,
        val scene: LivingSceneType,
        val weight: Float = 1.0f
    )

    private val LYRIC_KEYWORD_SIGNALS = listOf(
        LyricKeywordSignal(listOf("fire", "burn", "blaze", "flame", "ash", "smoke", "steel", "iron", "machine", "factory", "hammer", "forge"), LivingSceneType.INDUSTRIAL_STAGE),
        LyricKeywordSignal(listOf("highway", "headlight", "engine", "chrome", "guitar", "stage", "road", "drive", "ride", "car", "motor"), LivingSceneType.NIGHT_HIGHWAY_STAGE),
        LyricKeywordSignal(listOf("sky", "heaven", "spirit", "angel", "god", "pray", "soul", "holy", "divine", "glory", "blessed"), LivingSceneType.OPEN_ROAD_SKY),
        LyricKeywordSignal(listOf("city", "glass", "mirror", "world", "power", "rule", "empire", "skyline", "reflection"), LivingSceneType.CITY_REFLECTION),
        LyricKeywordSignal(listOf("street", "block", "hood", "club", "bass", "whip", "chain", "ice", "hustle", "grind"), LivingSceneType.NIGHT_CITY_PULSE),
        LyricKeywordSignal(listOf("garage", "basement", "sweat", "mosh", "slam", "loud", "amp", "scream"), LivingSceneType.BASEMENT_STAGE),
        LyricKeywordSignal(listOf("field", "porch", "country", "river", "creek", "barn", "dust", "sunset"), LivingSceneType.OPEN_FIELD_ROAD),
        LyricKeywordSignal(listOf("bed", "room", "memory", "photo", "pillow", "dream", "window", "curtain"), LivingSceneType.BEDROOM_MEMORY),
        LyricKeywordSignal(listOf("rain", "storm", "thunder", "cold", "dark", "cry", "tear", "pain"), LivingSceneType.STORM_WINDOW),
        LyricKeywordSignal(listOf("church", "chapel", "light", "beam", "stained", "altar"), LivingSceneType.CHURCH_LIGHT),
        LyricKeywordSignal(listOf("train", "track", "station", "platform", "rail"), LivingSceneType.MOVING_TRAIN),
    )

    /**
     * Classify a song into a vibe profile. This is the single entry point
     * for scene routing — called by FallbackStoryMapper and StoryPromptBuilder.
     */
    fun classify(
        title: String,
        artist: String,
        album: String?,
        genre: String? = null,
        lyricText: String? = null,
        audioEnergy: Float? = null
    ): SongVibeProfile {
        val artistLower = artist.lowercase().trim()
        val titleLower = title.lowercase().trim()
        val albumLower = album?.lowercase()?.trim() ?: ""
        val genreLower = genre?.lowercase()?.trim() ?: ""
        val lyricsLower = lyricText?.lowercase() ?: ""
        val allText = "$titleLower $artistLower $albumLower $lyricsLower"
        val keywordSceneScores = scoreSceneKeywords(allText)
        val titleScene = inferFromTitle(titleLower, artistLower)

        // 1. Try artist fingerprint, but let strong lyric/title evidence steer
        // songs whose words clearly ask for a different cinematic language.
        val artistMatch = ARTIST_FINGERPRINTS.firstOrNull { fp ->
            fp.patterns.any { pattern -> artistLower.contains(pattern) }
        }
        if (artistMatch != null) {
            val lyricScene = keywordSceneScores.maxByOrNull { it.value }
            val overrideScene = when {
                titleScene != null && titleScene != artistMatch.scene -> titleScene
                lyricScene != null && lyricScene.key != artistMatch.scene && lyricScene.value >= 3.0f -> lyricScene.key
                else -> null
            }
            if (overrideScene != null) {
                Log.d("VANTA_VIBE", "artist fingerprint '${artistMatch.patterns.first()}' overridden by lyrics/title -> $overrideScene")
                return buildProfileFromScene(overrideScene, artistMatch.genre, audioEnergy)
            }

            Log.d("VANTA_VIBE", "artist fingerprint match: '${artistMatch.patterns.first()}' -> ${artistMatch.scene}")
            return buildProfileFromArtistFingerprint(artistMatch, audioEnergy)
        }

        // 2. Title-based heuristics should beat broad genre tags.
        if (titleScene != null) {
            Log.d("VANTA_VIBE", "title heuristic: $titleScene")
            return buildProfileFromScene(titleScene, genreLower, audioEnergy)
        }

        // 3. Strong lyric keywords beat genre.
        val bestKeywordScene = keywordSceneScores.maxByOrNull { it.value }
        if (bestKeywordScene != null && bestKeywordScene.value >= 3.0f) {
            Log.d("VANTA_VIBE", "strong keyword score match: ${bestKeywordScene.key} (score=${bestKeywordScene.value})")
            return buildProfileFromScene(bestKeywordScene.key, genreLower, audioEnergy)
        }

        // 4. Genre signals.
        val genreMatch = GENRE_SIGNALS.firstOrNull { gs ->
            gs.keywords.any { kw -> genreLower.contains(kw) }
        }
        if (genreMatch != null) {
            Log.d("VANTA_VIBE", "genre match: '$genreLower' -> ${genreMatch.scene}")
            return SongVibeProfile(
                genre = genreLower.ifBlank { null },
                energy = audioEnergy ?: genreMatch.energy,
                darkness = genreMatch.darkness,
                aggression = genreMatch.aggression,
                warmth = genreMatch.warmth,
                industrial = genreMatch.industrial,
                spiritual = genreMatch.spiritual,
                urban = genreMatch.urban,
                cinematicKeywords = inferKeywords(genreMatch.scene),
                recommendedScene = genreMatch.scene
            )
        }

        // 5. Score-based classification from weaker lyrics + title + album keywords.
        val sceneScores = keywordSceneScores
        val bestScene = sceneScores.maxByOrNull { it.value }
        if (bestScene != null && bestScene.value >= 1.5f) {
            Log.d("VANTA_VIBE", "keyword score match: ${bestScene.key} (score=${bestScene.value})")
            return buildProfileFromScene(bestScene.key, genreLower, audioEnergy)
        }

        // 6. Energy-based fallback: high energy → stage, low → bedroom
        val effectiveEnergy = audioEnergy ?: 0.5f
        val fallbackScene = when {
            effectiveEnergy > 0.75f -> LivingSceneType.STAGE_SPOTLIGHT
            effectiveEnergy < 0.3f -> LivingSceneType.BEDROOM_MEMORY
            else -> LivingSceneType.PREMIUM_FALLBACK
        }
        Log.d("VANTA_VIBE", "energy fallback: energy=$effectiveEnergy -> $fallbackScene")
        return buildProfileFromScene(fallbackScene, genreLower, audioEnergy)
    }

    private fun scoreSceneKeywords(text: String): MutableMap<LivingSceneType, Float> {
        val sceneScores = mutableMapOf<LivingSceneType, Float>()
        for (signal in LYRIC_KEYWORD_SIGNALS) {
            val matchCount = signal.keywords.count { kw -> text.contains(kw) }
            if (matchCount > 0) {
                val score = matchCount.toFloat() * signal.weight
                sceneScores[signal.scene] = (sceneScores[signal.scene] ?: 0f) + score
            }
        }

        if (hasGermanPatterns(text)) {
            sceneScores[LivingSceneType.INDUSTRIAL_STAGE] =
                (sceneScores[LivingSceneType.INDUSTRIAL_STAGE] ?: 0f) + 2.0f
            Log.d("VANTA_VIBE", "German pattern detected in text")
        }
        return sceneScores
    }

    /** Detect German-language patterns that suggest industrial/German rock. */
    private fun hasGermanPatterns(text: String): Boolean {
        val germanMarkers = listOf(
            "du hast", "ich will", "mein", "dein", "feuer frei", "keine",
            "sonne", "mutter", "engel", "herz", "sehnsucht", "reise",
            "auslander", "ausländer", "deutschland", "zeit", "angst",
            "nein", "wir", "ihr"
        )
        return germanMarkers.count { text.contains(it) } >= 1
    }

    /** Simple title-based scene inference for common patterns. */
    private fun inferFromTitle(title: String, artist: String): LivingSceneType? {
        return when {
            title.contains("spirit") && title.contains("sky") -> LivingSceneType.OPEN_ROAD_SKY
            title.contains("victory") && title.contains("lap") -> LivingSceneType.NIGHT_CITY_PULSE
            title.contains("highway") || title.contains("road") -> LivingSceneType.NIGHT_HIGHWAY_STAGE
            title.contains("city") || title.contains("world") -> LivingSceneType.CITY_REFLECTION
            title.contains("church") || title.contains("heaven") -> LivingSceneType.CHURCH_LIGHT
            title.contains("rain") || title.contains("storm") -> LivingSceneType.STORM_WINDOW
            title.contains("train") -> LivingSceneType.MOVING_TRAIN
            title.contains("bed") || title.contains("dream") -> LivingSceneType.BEDROOM_MEMORY
            else -> null
        }
    }

    private fun buildProfileFromArtistFingerprint(
        fingerprint: ArtistFingerprint,
        audioEnergy: Float?
    ): SongVibeProfile = SongVibeProfile(
        genre = fingerprint.genre,
        energy = audioEnergy ?: fingerprint.energy,
        darkness = fingerprint.darkness,
        aggression = fingerprint.aggression,
        warmth = fingerprint.warmth,
        industrial = fingerprint.industrial,
        spiritual = fingerprint.spiritual,
        urban = fingerprint.urban,
        nostalgia = fingerprint.nostalgia,
        romance = fingerprint.romance,
        cinematicKeywords = inferKeywords(fingerprint.scene),
        recommendedScene = fingerprint.scene
    )

    /** Build a vibe profile from a scene type (reverse-mapping for keyword/fallback paths). */
    private fun buildProfileFromScene(scene: LivingSceneType, genre: String, audioEnergy: Float?): SongVibeProfile {
        return when (scene) {
            LivingSceneType.INDUSTRIAL_STAGE -> SongVibeProfile(
                genre = genre.ifBlank { "industrial" }, energy = audioEnergy ?: 0.9f,
                darkness = 0.9f, aggression = 0.9f, warmth = 0.05f, industrial = 0.9f,
                cinematicKeywords = inferKeywords(scene), recommendedScene = scene
            )
            LivingSceneType.NIGHT_HIGHWAY_STAGE -> SongVibeProfile(
                genre = genre.ifBlank { "rock" }, energy = audioEnergy ?: 0.75f,
                darkness = 0.3f, aggression = 0.3f, warmth = 0.7f,
                cinematicKeywords = inferKeywords(scene), recommendedScene = scene
            )
            LivingSceneType.CITY_REFLECTION -> SongVibeProfile(
                genre = genre.ifBlank { "pop" }, energy = audioEnergy ?: 0.5f,
                darkness = 0.3f, nostalgia = 0.6f, urban = 0.5f,
                cinematicKeywords = inferKeywords(scene), recommendedScene = scene
            )
            LivingSceneType.NIGHT_CITY_PULSE -> SongVibeProfile(
                genre = genre.ifBlank { "hip hop" }, energy = audioEnergy ?: 0.8f,
                darkness = 0.5f, urban = 0.9f, aggression = 0.4f,
                cinematicKeywords = inferKeywords(scene), recommendedScene = scene
            )
            LivingSceneType.BASEMENT_STAGE -> SongVibeProfile(
                genre = genre.ifBlank { "punk" }, energy = audioEnergy ?: 0.85f,
                darkness = 0.4f, aggression = 0.7f, warmth = 0.3f,
                cinematicKeywords = inferKeywords(scene), recommendedScene = scene
            )
            LivingSceneType.OPEN_FIELD_ROAD -> SongVibeProfile(
                genre = genre.ifBlank { "country" }, energy = audioEnergy ?: 0.4f,
                warmth = 0.85f, nostalgia = 0.6f,
                cinematicKeywords = inferKeywords(scene), recommendedScene = scene
            )
            LivingSceneType.OPEN_ROAD_SKY -> SongVibeProfile(
                genre = genre.ifBlank { "rock" }, energy = audioEnergy ?: 0.6f,
                spiritual = 0.8f, warmth = 0.6f,
                cinematicKeywords = inferKeywords(scene), recommendedScene = scene
            )
            LivingSceneType.CHURCH_LIGHT -> SongVibeProfile(
                genre = genre.ifBlank { "gospel" }, energy = audioEnergy ?: 0.5f,
                spiritual = 0.9f, warmth = 0.7f,
                cinematicKeywords = inferKeywords(scene), recommendedScene = scene
            )
            LivingSceneType.STORM_WINDOW -> SongVibeProfile(
                genre = genre.ifBlank { null }, energy = audioEnergy ?: 0.4f,
                darkness = 0.6f, warmth = 0.2f,
                cinematicKeywords = inferKeywords(scene), recommendedScene = scene
            )
            LivingSceneType.BEDROOM_MEMORY -> SongVibeProfile(
                genre = genre.ifBlank { null }, energy = audioEnergy ?: 0.3f,
                warmth = 0.6f, romance = 0.5f, nostalgia = 0.5f,
                cinematicKeywords = inferKeywords(scene), recommendedScene = scene
            )
            else -> SongVibeProfile(
                genre = genre.ifBlank { null }, energy = audioEnergy ?: 0.5f,
                cinematicKeywords = inferKeywords(scene), recommendedScene = scene
            )
        }
    }

    /** Scene-specific cinematic keywords for fallback story beats. */
    private fun inferKeywords(scene: LivingSceneType): List<String> {
        return when (scene) {
            LivingSceneType.INDUSTRIAL_STAGE -> listOf("steel beams", "sparks", "smoke", "strobe", "machinery", "red light", "fire", "concrete", "harsh shadow")
            LivingSceneType.NIGHT_HIGHWAY_STAGE -> listOf("headlights", "amber glow", "guitar", "neon sign", "asphalt", "dust", "stage lights", "road lines")
            LivingSceneType.CITY_REFLECTION -> listOf("glass", "skyline", "reflection", "blue light", "gold light", "clouds", "highway overpass", "elegant")
            LivingSceneType.NIGHT_CITY_PULSE -> listOf("neon", "streetlight", "bass", "car", "skyline", "concrete", "chain link", "smoke")
            LivingSceneType.BASEMENT_STAGE -> listOf("poster", "harsh lamp", "torn paper", "microphone", "sweat", "crowd", "concrete wall", "graffiti")
            LivingSceneType.OPEN_FIELD_ROAD -> listOf("field", "porch light", "sunset", "dirt road", "fence", "truck", "warm sky", "firefly")
            LivingSceneType.OPEN_ROAD_SKY -> listOf("giant sky", "sun", "horizon", "field", "chapel silhouette", "light beams", "road", "upward motion")
            LivingSceneType.CHURCH_LIGHT -> listOf("sunbeam", "dust", "stone arch", "stained glass", "candle", "altar", "shadow")
            LivingSceneType.DESERT_HIGHWAY -> listOf("road lines", "heat shimmer", "dust", "sun", "horizon", "cactus")
            LivingSceneType.STORM_WINDOW -> listOf("rain", "glass", "lightning", "fog", "droplets", "dark cloud")
            LivingSceneType.BEDROOM_MEMORY -> listOf("curtain", "lamp", "shadow", "photo", "pillow", "soft light")
            LivingSceneType.CITY_NIGHT -> listOf("neon", "rain", "building", "window", "wet street", "reflection")
            LivingSceneType.MOVING_TRAIN -> listOf("rail", "passing pole", "window reflection", "landscape")
            LivingSceneType.STAGE_SPOTLIGHT -> listOf("spotlight", "dust particle", "shadow edge", "beam")
            LivingSceneType.OPEN_FIELD -> listOf("grass", "wind", "cloud", "distant tree", "wide sky")
            LivingSceneType.PREMIUM_FALLBACK -> listOf("warm light", "particle", "gentle haze")
        }
    }
}
