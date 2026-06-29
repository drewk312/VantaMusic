import Foundation

struct CanonicalTrack: Codable, Equatable, Identifiable {
    let id: String
    var isrc: String?
    var title: String
    var artist: String
    var album: String
    var duration: TimeInterval
    var sources: [TrackSource]
}

struct TrackSource: Codable, Equatable, Identifiable {
    let id: String
    var kind: SourceKind
    var sourceId: String
    var url: String?
    var quality: PlaybackQuality
    var isAvailable: Bool
    var expiresAt: Date?
}

struct ResolutionResult: Codable, Equatable {
    var track: Track
    var resolvedUrl: String?
    var quality: PlaybackQuality
    var source: SourceKind
    var cacheHit: Bool
}

struct ResolutionCacheEntry: Codable, Equatable, Identifiable {
    let id: String
    var trackId: String
    var resolvedUrl: String
    var source: SourceKind
    var quality: PlaybackQuality
    var expiresAt: Date
    var createdAt: Date
}
