import Foundation

struct ExternalSourceConfig: Codable, Identifiable, Equatable {
    let id: String
    var name: String
    var kind: PlaybackProviderKind
    var isEnabled: Bool
    var serverUrl: String?
    var apiKey: String?
    var username: String?
    var password: String?
    var extraConfig: [String: String]

    static func `default`(for kind: PlaybackProviderKind) -> ExternalSourceConfig {
        ExternalSourceConfig(
            id: kind.rawValue,
            name: kind.displayName,
            kind: kind,
            isEnabled: false,
            serverUrl: nil,
            apiKey: nil,
            username: nil,
            password: nil,
            extraConfig: [:]
        )
    }

    static func == (lhs: ExternalSourceConfig, rhs: ExternalSourceConfig) -> Bool { lhs.id == rhs.id }
}

enum PlaybackProviderKind: String, Codable, CaseIterable {
    case spotiflac = "spotiflac"
    case pandoraGateway = "pandora_gateway"
    case amazonGateway = "amazon_gateway"
    case communityResolver = "community_resolver"

    var displayName: String {
        switch self {
        case .spotiflac: return "SpotiFLAC"
        case .pandoraGateway: return "Pandora Gateway"
        case .amazonGateway: return "Amazon Music Gateway"
        case .communityResolver: return "Community Resolver"
        }
    }
}
