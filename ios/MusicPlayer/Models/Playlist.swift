import Foundation

struct Playlist: Identifiable, Codable, Equatable {
    let id: String
    var name: String
    var description: String?
    var artworkUrl: String?
    var trackCount: Int
    var duration: TimeInterval
    var source: SourceKind
    var isEditable: Bool
    var dateCreated: Date
    var dateModified: Date
    var color: String?

    static func == (lhs: Playlist, rhs: Playlist) -> Bool { lhs.id == rhs.id }
}

struct PlaylistWithTracks: Identifiable, Equatable {
    let playlist: Playlist
    var tracks: [Track]

    var id: String { playlist.id }

    static func == (lhs: PlaylistWithTracks, rhs: PlaylistWithTracks) -> Bool { lhs.playlist.id == rhs.playlist.id }
}
