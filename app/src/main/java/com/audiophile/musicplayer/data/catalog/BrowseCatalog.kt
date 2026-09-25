package com.audiophile.musicplayer.data.catalog

import java.util.Locale

data class BrowseCategory(val title: String, val group: String, val artists: List<String> = emptyList(), val aliases: List<String> = emptyList())

/** Editorial starting points, not inferred song genres or a copy of Spotify's taxonomy. */
object BrowseCatalog {
    private fun c(title: String, group: String, artists: String = "", vararg aliases: String) =
        BrowseCategory(title, group, artists.split("|").filter(String::isNotBlank), aliases.toList())
    val categories = listOf(
        c("Pop", "Music genres", "Dua Lipa|Ariana Grande|Sabrina Carpenter|Lady Gaga|Harry Styles|Billie Eilish"),
        c("Rock", "Music genres", "Foo Fighters|The Rolling Stones|Queen|Muse|Paramore|Led Zeppelin"),
        c("Hip-Hop", "Music genres", "Kendrick Lamar|J. Cole|Nas|Little Simz|Tyler, The Creator|A Tribe Called Quest", "hip hop", "rap"),
        c("Dance", "Music genres", "Calvin Harris|Disclosure|Daft Punk|Rüfüs Du Sol|Fred again..|The Chemical Brothers", "electronic/dance", "edm"),
        c("Electronic", "Music genres", "Bonobo|Four Tet|Aphex Twin|Jon Hopkins|Bicep|Floating Points", "electro"),
        c("R&B", "Music genres", "SZA|H.E.R.|Frank Ocean|Summer Walker|Daniel Caesar|Jorja Smith", "rnb"),
        c("Country", "Music genres", "Dolly Parton|Chris Stapleton|Kacey Musgraves|George Strait|Lainey Wilson|Tyler Childers"),
        c("Classical", "Music genres", "Johann Sebastian Bach|Claude Debussy|Ludwig van Beethoven|Wolfgang Amadeus Mozart|Frederic Chopin"),
        c("Jazz", "Music genres", "Miles Davis|John Coltrane|Ella Fitzgerald|Nina Simone|Bill Evans|Kamasi Washington"),
        c("Indie", "Music genres", "The National|Big Thief|Phoebe Bridgers|Alvvays|Japanese Breakfast|The War on Drugs"),
        c("Alternative", "Music genres", "Radiohead|Arctic Monkeys|The Strokes|Tame Impala|The Cure|Wolf Alice"),
        c("Soul", "Music genres", "Aretha Franklin|Marvin Gaye|D'Angelo|Erykah Badu|Leon Bridges|Curtis Mayfield"),
        c("Metal", "Music genres", "Metallica|Iron Maiden|Black Sabbath|Gojira|Mastodon|Judas Priest"),
        c("Reggae", "Music genres", "Bob Marley & The Wailers|Chronixx|Koffee|Toots & The Maytals|Jimmy Cliff"),
        c("Oldies", "Music genres", "Elvis Presley|The Supremes|Chuck Berry|Buddy Holly|Roy Orbison|Sam Cooke", "golden oldies", "doo wop", "doo-wop"),
        c("Chill", "Moods & activities", "", "relax"), c("Focus", "Moods & activities", "", "study"),
        c("Party", "Moods & activities"), c("Sleep", "Moods & activities"),
        c("Workout", "Moods & activities"), c("Romance", "Moods & activities", "", "love"),
        c("Feel Good", "Moods & activities"), c("Road Trip", "Moods & activities"),
        c("50s", "Decades", "Chuck Berry|Little Richard|Elvis Presley|Buddy Holly|Fats Domino", "1950s"),
        c("60s", "Decades", "The Beatles|The Beach Boys|The Kinks|The Supremes|The Rolling Stones", "1960s"),
        c("70s", "Decades", "Fleetwood Mac|Led Zeppelin|Pink Floyd|Stevie Wonder|Elton John", "1970s"),
        c("80s", "Decades", "", "1980s"), c("90s", "Decades", "", "1990s"),
        c("2000s", "Decades"), c("2010s", "Decades"),
        c("Hindi Bollywood", "Around the world", "Arijit Singh|Shreya Ghoshal|Sonu Nigam|Sunidhi Chauhan|Udit Narayan|Alka Yagnik", "hindi", "bollywood"),
        c("K-Pop", "Around the world", "BTS|BLACKPINK|TWICE|SEVENTEEN|Stray Kids|NewJeans", "kpop"),
        c("Latin", "Around the world", "Bad Bunny|KAROL G|Shakira|Rauw Alejandro|Rosalia|J Balvin"),
        c("Afrobeats", "Around the world", "Burna Boy|Wizkid|Tems|Rema|Ayra Starr|Asake"),
        c("Yacht Rock", "Explore deeper", "Steely Dan|Toto|Christopher Cross|Michael McDonald|Boz Scaggs", "soft rock"),
        c("One-Hit Wonders", "Explore deeper", "Norman Greenbaum|Soft Cell|Dexys Midnight Runners|Tommy Tutone|a-ha", "one hit wonders"),
        c("Bedroom Pop", "Explore deeper", "Clairo|Rex Orange County|beabadoobee|Cuco|Gus Dapperton|Still Woozy"),
        c("Dream Pop", "Explore deeper", "Beach House|Cocteau Twins|Slowdive|Men I Trust|Mazzy Star"),
        c("Shoegaze", "Explore deeper", "My Bloody Valentine|Slowdive|Ride|Lush|DIIV"),
        c("Dark Ambient", "Explore deeper", "Lustmord|Atrium Carceri|Raison d'etre|Kammarheit"),
        c("Neo Soul", "Explore deeper", "Erykah Badu|D'Angelo|Jill Scott|Cleo Sol|Hiatus Kaiyote", "neo-soul"),
        c("Lo-Fi", "Explore deeper", "", "lofi", "lo fi"),
        c("Hits", "Popular now", "", "top hits")
    )
    /** Specific recordings keep mood/decade fallbacks relevant when playlist search is empty. */
    fun fallbackQueries(category: String): List<String> {
        val title = find(category)?.title ?: category
        val recordings = when (title) {
            "Oldies" -> listOf("Elvis Presley|Jailhouse Rock", "The Supremes|Baby Love", "Chuck Berry|Johnny B. Goode", "Buddy Holly|Peggy Sue")
            "50s" -> listOf("Chuck Berry|Johnny B. Goode", "Elvis Presley|Hound Dog", "Little Richard|Tutti Frutti", "Buddy Holly|That'll Be the Day")
            "60s" -> listOf("The Beatles|She Loves You", "The Beach Boys|Good Vibrations", "The Kinks|You Really Got Me", "The Supremes|Where Did Our Love Go")
            "70s" -> listOf("Fleetwood Mac|Dreams", "Stevie Wonder|Superstition", "Led Zeppelin|Stairway to Heaven", "Elton John|Rocket Man")
            "Yacht Rock" -> listOf("Steely Dan|Peg", "Toto|Rosanna", "Christopher Cross|Ride Like the Wind", "Michael McDonald|I Keep Forgettin'")
            "One-Hit Wonders" -> listOf("Norman Greenbaum|Spirit in the Sky", "Soft Cell|Tainted Love", "Dexys Midnight Runners|Come On Eileen", "Tommy Tutone|867-5309/Jenny")
            "Chill" -> listOf("Bonobo|Kerala", "Air|La femme d'argent", "Zero 7|In the Waiting Line", "Khruangbin|Friday Morning")
            "Focus" -> listOf("Nils Frahm|Ambre", "Olafur Arnalds|Saman", "Brian Eno|An Ending (Ascent)", "Max Richter|Dream 3")
            "Party" -> listOf("Daft Punk|One More Time", "Dua Lipa|Don't Start Now", "Beyonce|Crazy in Love", "Usher|Yeah!")
            "Sleep" -> listOf("Marconi Union|Weightless", "Brian Eno|1/1", "Stars of the Lid|Requiem for Dying Mothers, Pt. 2", "Max Richter|Dream 1")
            "Workout" -> listOf("Eminem|Till I Collapse", "Kanye West|Stronger", "Survivor|Eye of the Tiger", "The Chemical Brothers|Galvanize")
            "Romance" -> listOf("Sade|By Your Side", "Etta James|At Last", "Al Green|Let's Stay Together", "John Legend|All of Me")
            "Feel Good" -> listOf("Bill Withers|Lovely Day", "Pharrell Williams|Happy", "Earth, Wind & Fire|September", "Corinne Bailey Rae|Put Your Records On")
            "Road Trip" -> listOf("Tom Petty|Runnin' Down a Dream", "Fleetwood Mac|Go Your Own Way", "Tracy Chapman|Fast Car", "Bruce Springsteen|Born to Run")
            "80s" -> listOf("a-ha|Take on Me", "Michael Jackson|Billie Jean", "Whitney Houston|I Wanna Dance with Somebody", "Tears for Fears|Everybody Wants to Rule the World")
            "90s" -> listOf("Nirvana|Come As You Are", "TLC|No Scrubs", "Oasis|Wonderwall", "Lauryn Hill|Doo Wop (That Thing)")
            "2000s" -> listOf("Outkast|Hey Ya!", "Beyonce|Crazy in Love", "Coldplay|Viva La Vida", "Gorillaz|Feel Good Inc.")
            "2010s" -> listOf("Adele|Rolling in the Deep", "Daft Punk|Get Lucky", "Lorde|Royals", "The Weeknd|Can't Feel My Face")
            "Lo-Fi" -> listOf("Nujabes|Aruarian Dance", "J Dilla|Time: The Donut of the Heart", "Jinsang|Affection", "idealism|Both of Us")
            else -> emptyList()
        }
        return recordings.map { recording ->
            val (artist, track) = recording.split('|', limit = 2)
            "$artist $track"
        }
    }
    fun find(value: String): BrowseCategory? = categories.firstOrNull {
        it.title.equals(value.trim(), true) || it.aliases.any { alias -> alias.equals(value.trim(), true) }
    }
    fun parseGenreQuery(query: String): String? = Regex("""^genre:\s*(?:"([^"]+)"|([^"\r\n]+))$""", RegexOption.IGNORE_CASE)
        .matchEntire(query.trim())?.let { (it.groups[1]?.value ?: it.groups[2]?.value)?.trim()?.takeIf(String::isNotBlank) }
    fun collectionMatches(category: String, title: String): Boolean {
        fun norm(s: String) = s.lowercase(Locale.ROOT).replace(Regex("[^\\p{L}\\p{N}]+"), " ").trim()
        val names = listOf(category) + find(category)?.aliases.orEmpty()
        val titleWords = " " + norm(title) + " "
        return names.any { " " + norm(it) + " " in titleWords }
    }
}
