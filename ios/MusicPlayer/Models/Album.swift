import Foundation

struct Album: Identifiable, Codable, Equatable {
    let id: String
    var title: String
    var artist: String
    var artistId: String?
    var artworkUrl: String?
    var year: Int?
    var genre: String?
    var trackCount: Int
    var duration: TimeInterval
    var source: SourceKind
    var isCompilation: Bool
    var dateAdded: Date

    var displayTitle: String { title.trimmingCharacters(in: .whitespaces) }
    var displayArtist: String { artist.trimmingCharacters(in: .whitespaces) }

    static func == (lhs: Album, rhs: Album) -> Bool { lhs.id == rhs.id }
}

struct AlbumWithTracks: Identifiable, Equatable {
    let album: Album
    var tracks: [Track]

    var id: String { album.id }

    static func == (lhs: AlbumWithTracks, rhs: AlbumWithTracks) -> Bool { lhs.album.id == rhs.album.id }
}
