import Foundation

enum AiDjMode: String, Codable, CaseIterable {
    case jukebox = "jukebox"
    case pulse = "pulse"
    case discovery = "discovery"
    case off = "off"
}

enum JukeboxStation: String, Codable, CaseIterable {
    case goldenOldies = "golden_oldies"
    case yachtRock = "yacht_rock"
    case eightiesNight = "eigthies_night"
    case classicSoul = "classic_soul"
    case smoothJazz = "smooth_jazz"
    case chillLoFi = "chill_lofi"
    case morningAcoustic = "morning_acoustic"
    case lateNightBlues = "late_night_blues"
    case summerParty = "summer_party"
    case deepFocus = "deep_focus"
    case roadTrip = "road_trip"

    var displayName: String {
        switch self {
        case .goldenOldies: return "Golden Oldies"
        case .yachtRock: return "Yacht Rock"
        case .eightiesNight: return "80s Night"
        case .classicSoul: return "Classic Soul"
        case .smoothJazz: return "Smooth Jazz"
        case .chillLoFi: return "Chill Lo-Fi"
        case .morningAcoustic: return "Morning Acoustic"
        case .lateNightBlues: return "Late Night Blues"
        case .summerParty: return "Summer Party"
        case .deepFocus: return "Deep Focus"
        case .roadTrip: return "Road Trip"
        }
    }

    var description: String {
        switch self {
        case .goldenOldies: return "Classic hits from the 50s through 70s"
        case .yachtRock: return "Smooth 70s/80s soft rock"
        case .eightiesNight: return "Iconic 80s hits"
        case .classicSoul: return "Soul and R&B classics"
        case .smoothJazz: return "Smooth jazz favorites"
        case .chillLoFi: return "Relaxing lo-fi beats"
        case .morningAcoustic: return "Gentle acoustic start to your day"
        case .lateNightBlues: return "Blues for late night listening"
        case .summerParty: return "Upbeat summer vibes"
        case .deepFocus: return "Ambient music for concentration"
        case .roadTrip: return "Highway anthems"
        }
    }

    var eraRange: ClosedRange<Int>? {
        switch self {
        case .goldenOldies: return 1950...1979
        case .yachtRock: return 1975...1985
        case .eightiesNight: return 1980...1989
        case .classicSoul: return 1960...1979
        case .lateNightBlues: return 1950...2000
        default: return nil
        }
    }

    var genreKeywords: [String] {
        switch self {
        case .goldenOldies: return ["oldies", "classic", "vintage"]
        case .yachtRock: return ["yacht rock", "soft rock", "AOR"]
        case .eightiesNight: return ["80s", "synth", "new wave"]
        case .classicSoul: return ["soul", "motown", "funk"]
        case .smoothJazz: return ["smooth jazz", "jazz fusion"]
        case .chillLoFi: return ["lo-fi", "chill", "ambient"]
        case .morningAcoustic: return ["acoustic", "folk", "singer-songwriter"]
        case .lateNightBlues: return ["blues", "delta", "electric blues"]
        case .summerParty: return ["party", "dance", "pop", "summer"]
        case .deepFocus: return ["ambient", "instrumental", "electronic"]
        case .roadTrip: return ["rock", "country", "americana"]
        }
    }
}

enum PulseListeningStyle: String, Codable, CaseIterable {
    case eclectic = "eclectic"
    case deepDive = "deep_dive"
    case moodMatch = "mood_match"
    case eraFocus = "era_focus"
    case genreExplorer = "genre_explorer"
    case releaseRadar = "release_radar"
    case mood = "mood"
    case forgottenFavorites = "forgotten_favorites"
}

struct StationConfig: Codable, Equatable {
    var station: JukeboxStation
    var eraRange: ClosedRange<Int>?
    var genreKeywords: [String]
    var seedArtists: [String]
    var strictMode: Bool
}

struct AiDjNarration: Codable, Identifiable {
    let id: String
    var text: String
    var style: NarrationStyle
    var timestamp: Date
    var trackContext: Track?

    enum NarrationStyle: String, Codable {
        case intro = "intro"
        case transition = "transition"
        case factoid = "factoid"
        case outro = "outro"
        case error = "error"
    }
}

struct DiscoveryResult: Identifiable, Equatable {
    let id: String
    var tracks: [Track]
    var reason: String
    var mood: String?

    static func == (lhs: DiscoveryResult, rhs: DiscoveryResult) -> Bool { lhs.id == rhs.id }
}

struct PulseSession: Codable, Identifiable {
    let id: String
    var style: PulseListeningStyle
    var startedAt: Date
    var trackHistory: [Track]
    var currentEnergy: Float
    var userMood: String?
    var isActive: Bool
}
