package com.audiophile.musicplayer.radio

import android.util.Log
import com.audiophile.musicplayer.data.local.entities.UnifiedTrackWithSources

data class RadioStationIntent(
    val seedType: RadioSeedType,
    val seedTrackTitle: String? = null,
    val seedTrackArtist: String? = null,
    val mood: String? = null,
    val genre: String? = null,
    val eraStart: Int? = null,
    val eraEnd: Int? = null,
    val energy: RadioEnergyLevel = RadioEnergyLevel.ANY,
    val discoveryLevel: RadioDiscoveryLevel = RadioDiscoveryLevel.FAMILIAR,
    val exclusions: List<String> = emptyList(),
    val maxPerArtist: Int = 2,
    val targetQueueSize: Int = 30,
    val minPlayableToStart: Int = 5
)

enum class RadioSeedType { SONG, ARTIST, MOOD, ERA, PROMPT, ACTIVITY }
enum class RadioEnergyLevel { ANY, LOW, MEDIUM, HIGH }
enum class RadioDiscoveryLevel { FAMILIAR, BALANCED, DISCOVERY }

sealed class RadioFeedbackAction {
    data object MoreLikeThis : RadioFeedbackAction()
    data object LessLikeThis : RadioFeedbackAction()
    data class NeverPlayThis(val trackId: Long, val title: String, val artist: String) : RadioFeedbackAction()
    data object MoreFamiliar : RadioFeedbackAction()
    data object MoreDiscovery : RadioFeedbackAction()
    data object MoreEnergy : RadioFeedbackAction()
    data object LessEnergy : RadioFeedbackAction()
    data object StayOnThisVibe : RadioFeedbackAction()
    data class ChangeTheVibe(val newMood: String) : RadioFeedbackAction()
}

data class RadioGenerationLog(
    val seedIdentity: String,
    val stationIntent: String,
    val candidateCount: Int,
    val rejectedCandidates: List<String>,
    val gateRejections: List<String>,
    val finalQueueSize: Int,
    val firstTrackTitle: String,
    val firstTrackArtist: String,
    val top10Reasons: List<String>,
    val seedType: RadioSeedType,
    val totalQueries: Int
)

object RadioSongRegistry {
    private val knownSongs = mapOf(
        "spirit in the sky" to "Norman Greenbaum",
        "everybody wants to rule the world" to "Tears for Fears",
        "shout" to "Tears for Fears",
        "mad world" to "Tears for Fears",
        "head over heels" to "Tears for Fears",
        "bohemian rhapsody" to "Queen",
        "hotel california" to "Eagles",
        "billie jean" to "Michael Jackson",
        "piano man" to "Billy Joel",
        "stayin alive" to "Bee Gees",
        "blinding lights" to "The Weeknd",
        "shape of you" to "Ed Sheeran",
        "bad guy" to "Billie Eilish",
        "flowers" to "Miley Cyrus",
        "anti hero" to "Taylor Swift",
        "hello" to "Adele",
        "rolling in the deep" to "Adele",
        "someone like you" to "Adele",
        "bohemian rhapsody" to "Queen",
        "hotel california" to "Eagles",
        "piano man" to "Billy Joel",
        "imagine" to "John Lennon",
        "what a wonderful world" to "Louis Armstrong",
        "stand by me" to "Ben E. King",
        "lean on me" to "Bill Withers",
        "aint no sunshine" to "Bill Withers",
        "lets stay together" to "Al Green",
        "superstition" to "Stevie Wonder",
        "purple rain" to "Prince",
        "thriller" to "Michael Jackson",
        "sweet child o mine" to "Guns N' Roses",
        "welcome to the jungle" to "Guns N' Roses",
        "back in black" to "AC/DC",
        "thunderstruck" to "AC/DC",
        "smells like teen spirit" to "Nirvana",
        "come as you are" to "Nirvana",
        "enter sandman" to "Metallica",
        "nothing else matters" to "Metallica",
        "the unforgiven" to "Metallica",
        "one" to "Metallica",
        "master of puppets" to "Metallica",
        "fade to black" to "Metallica",
        "iron man" to "Black Sabbath",
        "paranoid" to "Black Sabbath",
        "stairway to heaven" to "Led Zeppelin",
        "whole lotta love" to "Led Zeppelin",
        "kashmir" to "Led Zeppelin",
        "immigrant song" to "Led Zeppelin",
        "dream on" to "Aerosmith",
        "walk this way" to "Aerosmith",
        "dont stop believin" to "Journey",
        "any way you want it" to "Journey",
        "eye of the tiger" to "Survivor",
        "living on a prayer" to "Bon Jovi",
        "wanted dead or alive" to "Bon Jovi",
        "you give love a bad name" to "Bon Jovi",
        "born in the usa" to "Bruce Springsteen",
        "dancing in the dark" to "Bruce Springsteen",
        "the river" to "Bruce Springsteen",
        "like a rolling stone" to "Bob Dylan",
        "knockin on heavens door" to "Bob Dylan",
        "sweet home alabama" to "Lynyrd Skynyrd",
        "free bird" to "Lynyrd Skynyrd",
        "toxic" to "Britney Spears",
        "oops i did it again" to "Britney Spears",
        "baby one more time" to "Britney Spears",
        "poker face" to "Lady Gaga",
        "bad romance" to "Lady Gaga",
        "just dance" to "Lady Gaga",
        "umbrella" to "Rihanna",
        "diamonds" to "Rihanna",
        "we found love" to "Rihanna",
        "single ladies" to "Beyonce",
        "crazy in love" to "Beyonce",
        "halo" to "Beyonce",
        "lose yourself" to "Eminem",
        "stan" to "Eminem",
        "the real slim shady" to "Eminem",
        "without me" to "Eminem",
        "rap god" to "Eminem",
        "california love" to "2Pac",
        "changes" to "2Pac",
        "hit em up" to "2Pac",
        "juicy" to "The Notorious B.I.G.",
        "big poppa" to "The Notorious B.I.G.",
        "hypnotize" to "The Notorious B.I.G.",
        "mo money mo problems" to "The Notorious B.I.G.",
        "still dre" to "Dr. Dre",
        "the next episode" to "Dr. Dre",
        "in da club" to "50 Cent",
        "god's plan" to "Drake",
        "hotline bling" to "Drake",
        "one dance" to "Drake",
        "passionfruit" to "Drake",
        "as it was" to "Harry Styles",
        "watermelon sugar" to "Harry Styles",
        "levitating" to "Dua Lipa",
        "dont start now" to "Dua Lipa",
        "new rules" to "Dua Lipa",
        "seven rings" to "Ariana Grande",
        "thank u next" to "Ariana Grande",
        "into you" to "Ariana Grande",
        "positions" to "Ariana Grande",
        "despacito" to "Luis Fonsi",
        "havana" to "Camila Cabello",
        "senorita" to "Shawn Mendes",
        "treat you better" to "Shawn Mendes",
        "stitches" to "Shawn Mendes",
        "sunflower" to "Post Malone",
        "rockstar" to "Post Malone",
        "circles" to "Post Malone",
        "congratulations" to "Post Malone",
        "better now" to "Post Malone",
        "sicko mode" to "Travis Scott",
        "goosebumps" to "Travis Scott",
        "highest in the room" to "Travis Scott",
        "humble" to "Kendrick Lamar",
        "dna" to "Kendrick Lamar",
        "alright" to "Kendrick Lamar",
        "swimming pools" to "Kendrick Lamar",
        "king kunta" to "Kendrick Lamar",
        "loyalty" to "Kendrick Lamar",
        "lovely" to "Billie Eilish",
        "ocean eyes" to "Billie Eilish",
        "everything i wanted" to "Billie Eilish",
        "old town road" to "Lil Nas X",
        "industry baby" to "Lil Nas X",
        "montero" to "Lil Nas X",
        "peaches" to "Justin Bieber",
        "sorry" to "Justin Bieber",
        "love yourself" to "Justin Bieber",
        "stay" to "Justin Bieber",
        "intentions" to "Justin Bieber",
        "closer" to "The Chainsmokers",
        "something just like this" to "The Chainsmokers",
        "paris" to "The Chainsmokers",
        "don't let me down" to "The Chainsmokers",
        "happier" to "Marshmello",
        "alone" to "Marshmello",
        "faded" to "Alan Walker",
        "alone" to "Alan Walker",
        "the spectre" to "Alan Walker",
        "on my way" to "Alan Walker",
        "wake me up" to "Avicii",
        "hey brother" to "Avicii",
        "levels" to "Avicii",
        "waiting for love" to "Avicii",
        "dont you worry child" to "Swedish House Mafia",
        "save the world" to "Swedish House Mafia",
        "one more time" to "Daft Punk",
        "harder better faster stronger" to "Daft Punk",
        "get lucky" to "Daft Punk",
        "around the world" to "Daft Punk",
        "something about us" to "Daft Punk",
        "bittersweet symphony" to "The Verve",
        "wonderwall" to "Oasis",
        "dont look back in anger" to "Oasis",
        "champagne supernova" to "Oasis",
        "creep" to "Radiohead",
        "karma police" to "Radiohead",
        "no surprises" to "Radiohead",
        "fake plastic trees" to "Radiohead",
        "high and dry" to "Radiohead",
        "paranoid android" to "Radiohead",
        "exit music for a film" to "Radiohead",
        "let down" to "Radiohead",
        "climbing up the walls" to "Radiohead",
        "in limbo" to "Radiohead",
        "idioteque" to "Radiohead",
        "morning bell" to "Radiohead",
        "motion picture soundtrack" to "Radiohead",
        "airbag" to "Radiohead",
        "subterranean homesick alien" to "Radiohead",
        "electioneering" to "Radiohead",
        "the tourist" to "Radiohead",
        "lucky" to "Radiohead",
        "15 step" to "Radiohead",
        "bodysnatchers" to "Radiohead",
        "nude" to "Radiohead",
        "weird fishes" to "Radiohead",
        "all i need" to "Radiohead",
        "faust arp" to "Radiohead",
        "reckoner" to "Radiohead",
        "house of cards" to "Radiohead",
        "jigsaw falling into place" to "Radiohead",
        "videotape" to "Radiohead",
        "bloom" to "Radiohead",
        "little by little" to "Radiohead",
        "feral" to "Radiohead",
        "codex" to "Radiohead",
        "give up the ghost" to "Radiohead",
        "separator" to "Radiohead",
        "burn the witch" to "Radiohead",
        "daydreaming" to "Radiohead",
        "decks dark" to "Radiohead",
        "desert island disk" to "Radiohead",
        "ful stop" to "Radiohead",
        "glass eyes" to "Radiohead",
        "identikit" to "Radiohead",
        "the numbers" to "Radiohead",
        "present tense" to "Radiohead",
        "tinker tailor soldier sailor rich man poor man beggar man thief" to "Radiohead",
        "true love waits" to "Radiohead",
        "pulk pull revolving doors" to "Radiohead",
        "like spinning plates" to "Radiohead",
        "knives out" to "Radiohead",
        "i might be wrong" to "Radiohead",
        "dollars and cents" to "Radiohead",
        "pyramid song" to "Radiohead",
        "you and whose army" to "Radiohead",
        "optimistic" to "Radiohead",
        "packt like sardines" to "Radiohead",
        "everything in its right place" to "Radiohead",
        "how to disappear completely" to "Radiohead",
        "the national anthem" to "Radiohead",
        "kid a" to "Radiohead",
        "the daily mail" to "Radiohead",
        "staircase" to "Radiohead",
        "supercollider" to "Radiohead",
        "the butcher" to "Radiohead",
        "ill wind" to "Radiohead",
        "go to sleep" to "Radiohead",
        "where i end and you begin" to "Radiohead",
        "sit down stand up" to "Radiohead",
        "myxomatosis" to "Radiohead",
        "scatterbrain" to "Radiohead",
        "a wolf at the door" to "Radiohead",
        "2 + 2 = 5" to "Radiohead",
        "sail to the moon" to "Radiohead",
        "backdrifts" to "Radiohead",
        "go slowly" to "Radiohead",
        "down is the new up" to "Radiohead",
        "bangers + mash" to "Radiohead",
        "4 minute warning" to "Radiohead",
        "pop is dead" to "Radiohead",
        "inside my head" to "Radiohead",
        "milk" to "Radiohead",
        "indian red" to "Radiohead",
        "talk show host" to "Radiohead",
        "lozenge of love" to "Radiohead",
        "lurgee" to "Radiohead",
        "blow out" to "Radiohead",
        "anyone can play guitar" to "Radiohead",
        "creeping" to "Radiohead",
        "stop whispering" to "Radiohead",
        "thinking about you" to "Radiohead",
        "you" to "Radiohead",
        "how do you" to "Radiohead",
        "vegetable" to "Radiohead",
        "prove yourself" to "Radiohead",
        "i can't" to "Radiohead",
        "ripcord" to "Radiohead",
        "the bends" to "Radiohead",
        "planet telex" to "Radiohead",
        "just" to "Radiohead",
        "my iron lung" to "Radiohead",
        "bullet proof i wish i was" to "Radiohead",
        "black star" to "Radiohead",
        "sulk" to "Radiohead",
        "street spirit fade out" to "Radiohead",
        "nice dream" to "Radiohead",
        "maquiladora" to "Radiohead",
        "killer cars" to "Radiohead",
        "the trickster" to "Radiohead",
        "punchdrunk" to "Radiohead",
        "permanent daylight" to "Radiohead",
        "lozenge of love" to "Radiohead",
        "you never wash up after yourself" to "Radiohead",
        "molasses" to "Radiohead",
        "faithless the wonder boy" to "Radiohead",
        "banana co." to "Radiohead",
        "kinetic" to "Radiohead",
        "fast track" to "Radiohead",
        "trans-atlantic drawl" to "Radiohead",
        "gagging order" to "Radiohead",
        "these are my twisted words" to "Radiohead"
    )

    fun search(rawQuery: String): List<Pair<String, String>> {
        val q = rawQuery.lowercase().trim()
        val results = mutableListOf<Pair<String, String>>()
        for ((title, artist) in knownSongs) {
            if (q.contains(title) || title.contains(q)) {
                results.add(title to artist)
            }
        }
        return results.distinct().take(3)
    }

    fun resolveExact(title: String): Pair<String, String>? {
        val nq = title.lowercase().trim()
        return knownSongs[nq]?.let { nq to it }
    }

    fun resolveWithArtist(title: String, artist: String): Pair<String, String>? {
        val nt = title.lowercase().trim()
        val na = artist.lowercase().trim()
        return knownSongs[nt]?.takeIf { it.lowercase() == na }?.let { nt to it }
    }
}

object RadioBrain {

    private const val TAG = "VANTA_RADIO_BRAIN"

    fun buildIntent(
        seedType: RadioSeedType,
        trackTitle: String? = null,
        trackArtist: String? = null,
        userInput: String? = null,
        energy: RadioEnergyLevel = RadioEnergyLevel.ANY,
        discoveryLevel: RadioDiscoveryLevel = RadioDiscoveryLevel.FAMILIAR
    ): RadioStationIntent {
        val resolvedMood: String?
        val resolvedGenre: String?
        val eraStart: Int?
        val eraEnd: Int?

        when (seedType) {
            RadioSeedType.SONG -> {
                resolvedMood = null
                resolvedGenre = null
                eraStart = null
                eraEnd = null
            }
            RadioSeedType.ARTIST -> {
                resolvedMood = null
                resolvedGenre = null
                eraStart = null
                eraEnd = null
            }
            RadioSeedType.MOOD -> {
                val vibe = VibeTranslator.extractSearchQueries(userInput.orEmpty())
                resolvedMood = vibe.firstOrNull()
                resolvedGenre = vibe.getOrNull(1)
                val era = detectEraFromMood(userInput.orEmpty())
                eraStart = era.first
                eraEnd = era.second
            }
            RadioSeedType.PROMPT -> {
                val parsed = parsePrompt(userInput.orEmpty())
                resolvedMood = parsed.mood
                resolvedGenre = parsed.genre
                eraStart = parsed.eraStart
                eraEnd = parsed.eraEnd
            }
            else -> {
                resolvedMood = null
                resolvedGenre = null
                eraStart = null
                eraEnd = null
            }
        }

        return RadioStationIntent(
            seedType = seedType,
            seedTrackTitle = trackTitle,
            seedTrackArtist = trackArtist,
            mood = resolvedMood,
            genre = resolvedGenre,
            eraStart = eraStart,
            eraEnd = eraEnd,
            energy = energy,
            discoveryLevel = discoveryLevel,
            targetQueueSize = if (seedType == RadioSeedType.MOOD || seedType == RadioSeedType.PROMPT) 40 else 30,
            minPlayableToStart = if (seedType == RadioSeedType.MOOD || seedType == RadioSeedType.PROMPT) 8 else 5
        )
    }

    fun buildVerifiedQueue(
        tracks: List<UnifiedTrackWithSources>,
        intent: RadioStationIntent,
        previousTrackIds: Set<Long> = emptySet(),
        generationToken: Long = 0L
    ): Pair<List<UnifiedTrackWithSources>, List<String>> {
        val verified = mutableListOf<UnifiedTrackWithSources>()
        val rejections = mutableListOf<String>()
        val artistCount = mutableMapOf<String, Int>()
        val seenTitleArtist = mutableSetOf<String>()

        for (track in tracks) {
            if (track.track.trackId in previousTrackIds) {
                rejections.add("already_played:${track.track.title}")
                continue
            }

            val gateResult = PlaybackIdentityGate.verify(track)
            if (gateResult !is GateVerdict.Passed) {
                rejections.add("gate:${(gateResult as GateVerdict.Failed).reason}:${track.track.title}:${track.track.artist}")
                continue
            }

            val artistKey = track.track.artist?.lowercase()?.trim().orEmpty()
            val titleArtistKey = "${track.track.title?.lowercase()?.trim()}|$artistKey"

            if (titleArtistKey in seenTitleArtist) {
                rejections.add("duplicate:$titleArtistKey")
                continue
            }
            seenTitleArtist.add(titleArtistKey)

            if (verified.size < 10) {
                val currentArtistCount = artistCount[artistKey] ?: 0
                if (currentArtistCount >= intent.maxPerArtist) {
                    rejections.add("artist_cap:$artistKey")
                    continue
                }
            }

            artistCount[artistKey] = (artistCount[artistKey] ?: 0) + 1
            verified.add(track)

            if (verified.size >= intent.targetQueueSize) break
        }

        return verified to rejections
    }

    fun applyFeedback(intent: RadioStationIntent, action: RadioFeedbackAction): RadioStationIntent {
        return when (action) {
            is RadioFeedbackAction.MoreLikeThis -> intent.copy(
                energy = when (intent.energy) {
                    RadioEnergyLevel.LOW -> RadioEnergyLevel.MEDIUM
                    else -> intent.energy
                },
                discoveryLevel = RadioDiscoveryLevel.FAMILIAR
            )
            is RadioFeedbackAction.LessLikeThis -> intent.copy(
                maxPerArtist = (intent.maxPerArtist - 1).coerceAtLeast(1)
            )
            is RadioFeedbackAction.NeverPlayThis -> intent.copy(
                exclusions = intent.exclusions + "${action.title} ${action.artist}"
            )
            is RadioFeedbackAction.MoreFamiliar -> intent.copy(
                discoveryLevel = RadioDiscoveryLevel.FAMILIAR
            )
            is RadioFeedbackAction.MoreDiscovery -> intent.copy(
                discoveryLevel = RadioDiscoveryLevel.DISCOVERY
            )
            is RadioFeedbackAction.MoreEnergy -> intent.copy(
                energy = when (intent.energy) {
                    RadioEnergyLevel.LOW -> RadioEnergyLevel.MEDIUM
                    RadioEnergyLevel.MEDIUM -> RadioEnergyLevel.HIGH
                    else -> RadioEnergyLevel.HIGH
                }
            )
            is RadioFeedbackAction.LessEnergy -> intent.copy(
                energy = when (intent.energy) {
                    RadioEnergyLevel.HIGH -> RadioEnergyLevel.MEDIUM
                    RadioEnergyLevel.MEDIUM -> RadioEnergyLevel.LOW
                    else -> RadioEnergyLevel.LOW
                }
            )
            is RadioFeedbackAction.StayOnThisVibe -> intent.copy(
                discoveryLevel = RadioDiscoveryLevel.FAMILIAR
            )
            is RadioFeedbackAction.ChangeTheVibe -> intent.copy(
                mood = action.newMood
            )
        }
    }

    fun buildGenerationLog(
        seedIdentity: String,
        intent: RadioStationIntent,
        candidates: List<UnifiedTrackWithSources>,
        rejections: List<String>,
        gateRejections: List<String>,
        verifiedQueue: List<UnifiedTrackWithSources>,
        totalQueries: Int
    ): RadioGenerationLog {
        val top10Reasons = verifiedQueue.take(10).map { track ->
            val artist = track.track.artist ?: "unknown"
            val title = track.track.title ?: "unknown"
            val genre = track.track.genre ?: "unknown"
            "$title by $artist (genre=$genre)"
        }
        return RadioGenerationLog(
            seedIdentity = seedIdentity,
            stationIntent = "${intent.seedType} mood=${intent.mood} genre=${intent.genre} era=${intent.eraStart}-${intent.eraEnd} energy=${intent.energy}",
            candidateCount = candidates.size,
            rejectedCandidates = rejections,
            gateRejections = gateRejections,
            finalQueueSize = verifiedQueue.size,
            firstTrackTitle = verifiedQueue.firstOrNull()?.track?.title ?: "none",
            firstTrackArtist = verifiedQueue.firstOrNull()?.track?.artist ?: "none",
            top10Reasons = top10Reasons,
            seedType = intent.seedType,
            totalQueries = totalQueries
        )
    }

    fun logGeneration(log: RadioGenerationLog) {
        Log.i(TAG, "=== RADIO GENERATION ===")
        Log.i(TAG, "seed_identity='${log.seedIdentity}'")
        Log.i(TAG, "station_intent='${log.stationIntent}'")
        Log.i(TAG, "candidate_count=${log.candidateCount} total_queries=${log.totalQueries}")
        Log.i(TAG, "gate_rejections=${log.gateRejections.size}: ${log.gateRejections.joinToString(" | ")}")
        Log.i(TAG, "candidate_rejections=${log.rejectedCandidates.size}: ${log.rejectedCandidates.take(20).joinToString(" | ")}")
        Log.i(TAG, "final_queue_size=${log.finalQueueSize}")
        Log.i(TAG, "first_track='${log.firstTrackTitle}' by '${log.firstTrackArtist}'")
        Log.i(TAG, "seed_type=${log.seedType}")
        Log.i(TAG, "top10:")
        log.top10Reasons.forEachIndexed { index, reason ->
            Log.i(TAG, "  [${index + 1}] $reason")
        }
        Log.i(TAG, "=== END ===")
    }

    private data class PromptParse(
        val mood: String? = null,
        val genre: String? = null,
        val eraStart: Int? = null,
        val eraEnd: Int? = null
    )

    private fun parsePrompt(input: String): PromptParse {
        val lower = input.lowercase().trim()
        var mood: String? = null
        var genre: String? = null
        var eraStart: Int? = null
        var eraEnd: Int? = null

        val eraPattern = Regex("""\b(\d{2})s\b""")
        val eraMatch = eraPattern.find(lower)
        if (eraMatch != null) {
            val decade = eraMatch.groupValues[1].toIntOrNull()
            if (decade != null) {
                eraStart = 1900 + decade
                eraEnd = eraStart + 9
            }
        }

        val knownGenres = listOf(
            "classic rock", "country", "hip hop", "r&b", "jazz", "blues", "metal", "punk",
            "indie", "alternative", "electronic", "edm", "house", "techno", "pop", "soul",
            "funk", "disco", "folk", "americana", "synthwave", "lofi", "ambient"
        )
        for (g in knownGenres) {
            if (lower.contains(g)) {
                genre = g
                break
            }
        }

        val moodKeywords = mapOf(
            "chill" to "ambient",
            "quiet" to "ambient",
            "relax" to "ambient",
            "sad" to "acoustic",
            "happy" to "pop",
            "upbeat" to "pop",
            "angry" to "metal",
            "dark" to "synthwave",
            "party" to "dance",
            "workout" to "high energy",
            "focus" to "ambient",
            "sleep" to "ambient",
            "romantic" to "soul",
            "driving" to "rock",
            "cookout" to "country",
            "summer" to "pop",
        )
        for ((keyword, targetMood) in moodKeywords) {
            if (lower.contains(keyword)) {
                mood = targetMood
                break
            }
        }

        return PromptParse(mood, genre, eraStart, eraEnd)
    }

    private fun detectEraFromMood(input: String): Pair<Int?, Int?> {
        val lower = input.lowercase()
        return when {
            "4th of july" in lower || "cookout" in lower || "summer" in lower -> 1990 to 2010
            "late night" in lower || "night drive" in lower -> 2010 to null
            "retro" in lower || "vintage" in lower -> 1970 to 1990
            "80s" in lower || "80's" in lower -> 1980 to 1989
            "90s" in lower || "90's" in lower -> 1990 to 1999
            else -> null to null
        }
    }
}
