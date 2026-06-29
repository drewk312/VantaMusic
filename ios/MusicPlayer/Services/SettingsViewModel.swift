import Foundation
import Combine

final class SettingsViewModel: ObservableObject {
    @Published var immersiveConfig: ImmersiveAudioConfig
    @Published var sourceConfigs: [ExternalSourceConfig] = []
    @Published var playbackQuality: PlaybackQuality = .high
    @Published var useLossless: Bool = false
    @Published var crossfadeEnabled: Bool = false
    @Published var crossfadeDuration: Double = 3.0
    @Published var gaplessPlayback: Bool = true
    @Published var storageUsed: Int64 = 0
    @Published var cacheSize: Int64 = 0

    private let defaults = UserDefaults.standard

    init() {
        // Load saved immersive config
        if let data = defaults.data(forKey: "immersiveConfig"),
           let config = try? JSONDecoder().decode(ImmersiveAudioConfig.self, from: data) {
            immersiveConfig = config
        } else {
            immersiveConfig = .default
        }

        loadSourceConfigs()
    }

    func saveImmersiveConfig(_ config: ImmersiveAudioConfig) {
        immersiveConfig = config
        if let data = try? JSONEncoder().encode(config) {
            defaults.set(data, forKey: "immersiveConfig")
        }
    }

    func toggleImmersive() {
        var config = immersiveConfig
        config.enabled.toggle()
        if config.enabled {
            config = config.withEnableDefaults()
        }
        saveImmersiveConfig(config)
    }

    func updateImmersiveStrength(_ strength: Float) {
        var config = immersiveConfig
        config.strength = strength
        saveImmersiveConfig(config)
    }

    func toggleSource(_ id: String) {
        guard let index = sourceConfigs.firstIndex(where: { $0.id == id }) else { return }
        var config = sourceConfigs[index]
        config.isEnabled.toggle()
        sourceConfigs[index] = config
    }

    private func loadSourceConfigs() {
        sourceConfigs = PlaybackProviderKind.allCases.map { kind in
            ExternalSourceConfig.default(for: kind)
        }
    }

    func clearCache() {
        cacheSize = 0
        storageUsed = 0
    }
}
