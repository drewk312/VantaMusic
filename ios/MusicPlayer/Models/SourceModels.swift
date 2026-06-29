import Foundation

struct MusicSource: Identifiable, Codable, Equatable {
    let id: String
    var name: String
    var kind: SourceKind
    var isEnabled: Bool
    var requiresAuth: Bool
    var isAuthenticated: Bool
    var config: SourceConfig?

    static func == (lhs: MusicSource, rhs: MusicSource) -> Bool { lhs.id == rhs.id }
}

struct SourceConfig: Codable, Equatable {
    var apiKey: String?
    var baseUrl: String?
    var username: String?
    var password: String?
    var token: String?
    var extraHeaders: [String: String]?
}

struct SourceHealth: Codable, Equatable {
    var isReachable: Bool
    var latencyMs: Double
    var lastChecked: Date
    var errorMessage: String?
}

enum PlaybackQuality: String, Codable, CaseIterable {
    case low = "low"
    case medium = "medium"
    case high = "high"
    case lossless = "lossless"
    case hiRes = "hi_res"

    var displayName: String {
        switch self {
        case .low: return "Low (128kbps)"
        case .medium: return "Medium (256kbps)"
        case .high: return "High (320kbps)"
        case .lossless: return "Lossless (CD)"
        case .hiRes: return "Hi-Res 24-bit"
        }
    }

    var bitrate: Int {
        switch self {
        case .low: return 128
        case .medium: return 256
        case .high: return 320
        case .lossless: return 1411
        case .hiRes: return 4608
        }
    }
}
