import Foundation

struct Track: Identifiable, Codable, Equatable {
    let id: String
    var title: String
    var artist: String
    var album: String
    var albumArtist: String?
    var albumId: String?
    var genre: String?
    var trackNumber: Int
    var discNumber: Int
    var duration: TimeInterval
    var year: Int?
    var artworkUrl: String?
    var isrc: String?
    var source: SourceKind
    var sourceId: String?
    var url: String?
    var isPlayable: Bool
    var explicit: Bool
    var popularity: Float
    var dateAdded: Date
    var lastPlayed: Date?

    // Computed properties
    var displayTitle: String {
        title.trimmingCharacters(in: .whitespaces)
    }

    var displayArtist: String {
        cleanDisplayName(artist)
    }

    var displayAlbum: String {
        cleanDisplayName(album)
    }

    var formattedDuration: String {
        let minutes = Int(duration) / 60
        let seconds = Int(duration) % 60
        return "\(minutes):\(String(format: "%02d", seconds))"
    }

    private func cleanDisplayName(_ text: String) -> String {
        let suffixes = [" - YouTube", " - YouTube Music", " - Amazon Music",
                        " (Official Video)", " (Official Audio)", " (Lyric Video)",
                        " (Audio)", " (Visualizer)", " (Official Lyric Video)"]
        var cleaned = text
        for suffix in suffixes {
            if cleaned.hasSuffix(suffix) {
                cleaned = String(cleaned.dropLast(suffix.count))
            }
        }
        return cleaned.trimmingCharacters(in: .whitespaces)
    }

    static func == (lhs: Track, rhs: Track) -> Bool {
        lhs.id == rhs.id
    }
}

enum SourceKind: String, Codable, CaseIterable {
    case local = "local"
    case youTubeMusic = "youtube_music"
    case qobuz = "qobuz"
    case realDebrid = "real_debrid"
    case torBox = "tor_box"
    case appleMusic = "apple_music"
    case deezer = "deezer"
    case pandora = "pandora"
    case amazonMusic = "amazon_music"
    case unknown = "unknown"

    var displayName: String {
        switch self {
        case .local: return "Local"
        case .youTubeMusic: return "YouTube Music"
        case .qobuz: return "Qobuz"
        case .realDebrid: return "Real-Debrid"
        case .torBox: return "TorBox"
        case .appleMusic: return "Apple Music"
        case .deezer: return "Deezer"
        case .pandora: return "Pandora"
        case .amazonMusic: return "Amazon Music"
        case .unknown: return "Unknown"
        }
    }
}
