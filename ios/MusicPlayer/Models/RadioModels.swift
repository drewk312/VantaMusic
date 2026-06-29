import Foundation

struct RadioStation: Identifiable, Codable, Equatable {
    let id: String
    var name: String
    var description: String?
    var streamUrl: String
    var artworkUrl: String?
    var genre: String?
    var country: String?
    var language: String?
    var bitrate: Int?
    var isOnline: Bool
    var reliability: Float
}

struct RadioMetadata: Codable, Equatable {
    var stationName: String?
    var currentTrack: String?
    var currentArtist: String?
    var streamTitle: String?
    var bitrate: Int?
    var isIcecast: Bool
}
