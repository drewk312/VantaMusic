import Foundation

struct ImmersiveAudioConfig: Codable, Equatable {
    var enabled: Bool = false
    var strength: Float = 0.3
    var crossfeedEnabled: Bool = true
    var crossfeedMode: CrossfeedMode = .balanced
    var reverbEnabled: Bool = false
    var reverbPreset: ReverbPreset = .smallRoom
    var stereoEnhanceEnabled: Bool = true

    static let `default` = ImmersiveAudioConfig()

    func resolvedParams() -> ResolvedSoundParams {
        ResolvedSoundParams(
            wideness: Double(strength) * 0.7,
            crossfeed: crossfeedEnabled ? Double(strength) * 0.5 : 0.0,
            reverbMix: reverbEnabled ? Double(strength) * 0.15 : 0.0,
            reverbDecay: reverbEnabled ? Double(strength) * 0.3 : 0.0
        )
    }

    mutating func withEnableDefaults() -> ImmersiveAudioConfig {
        guard enabled else { return self }
        var config = self
        if !crossfeedEnabled && !reverbEnabled && !stereoEnhanceEnabled {
            config.crossfeedEnabled = true
            config.stereoEnhanceEnabled = true
        }
        return config
    }
}

struct ResolvedSoundParams: Equatable {
    var wideness: Double
    var crossfeed: Double
    var reverbMix: Double
    var reverbDecay: Double
}

enum CrossfeedMode: Int, Codable, CaseIterable {
    case mild = 0
    case balanced = 1
    case aggressive = 2

    var displayName: String {
        switch self {
        case .mild: return "Mild"
        case .balanced: return "Balanced"
        case .aggressive: return "Aggressive"
        }
    }
}

enum ReverbPreset: Int, Codable, CaseIterable {
    case smallRoom = 0
    case mediumRoom = 1
    case largeRoom = 2
    case hall = 3
    case cathedral = 4
    case plate = 5

    var displayName: String {
        switch self {
        case .smallRoom: return "Small Room"
        case .mediumRoom: return "Medium Room"
        case .largeRoom: return "Large Room"
        case .hall: return "Hall"
        case .cathedral: return "Cathedral"
        case .plate: return "Plate"
        }
    }
}
