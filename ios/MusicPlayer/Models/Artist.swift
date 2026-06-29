import Foundation

struct Artist: Identifiable, Codable, Equatable {
    let id: String
    var name: String
    var artworkUrl: String?
    var genre: String?
    var albumCount: Int
    var trackCount: Int
    var biography: String?
    var source: SourceKind
    var color: String?

    var displayName: String { name.trimmingCharacters(in: .whitespaces) }

    static func == (lhs: Artist, rhs: Artist) -> Bool { lhs.id == rhs.id }
}
