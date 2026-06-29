import SwiftUI

struct SettingsView: View {
    @EnvironmentObject var viewModel: SettingsViewModel

    var body: some View {
        NavigationStack {
            List {
                // MARK: - Sound Section
                Section("Sound") {
                    // Immersive Sound
                    VStack(alignment: .leading, spacing: 8) {
                        HStack {
                            Image(systemName: "spatial.audio")
                                .font(.system(size: 16))
                            Toggle("Immersive Sound", isOn: Binding(
                                get: { viewModel.immersiveConfig.enabled },
                                set: { _ in viewModel.toggleImmersive() }
                            ))
                            .tint(.accentColor)
                        }

                        if viewModel.immersiveConfig.enabled {
                            VStack(spacing: 4) {
                                HStack {
                                    Text("Strength")
                                        .font(.system(size: 13))
                                        .foregroundColor(.secondary)
                                    Spacer()
                                    Text("\(Int(viewModel.immersiveConfig.strength * 100))%")
                                        .font(.system(size: 13, weight: .medium))
                                        .foregroundColor(.secondary)
                                }
                                Slider(value: Binding(
                                    get: { viewModel.immersiveConfig.strength },
                                    set: { viewModel.updateImmersiveStrength($0) }
                                ), in: 0...1)
                                .tint(.accentColor)
                            }
                            .padding(.leading, 24)
                        }
                    }
                    .padding(.vertical, 4)

                    // Quality
                    Picker("Quality", selection: $viewModel.playbackQuality) {
                        ForEach(PlaybackQuality.allCases, id: \.self) { quality in
                            Text(quality.displayName).tag(quality)
                        }
                    }

                    Toggle("Lossless", isOn: $viewModel.useLossless)
                    Toggle("Crossfade", isOn: $viewModel.crossfadeEnabled)
                    Toggle("Gapless Playback", isOn: $viewModel.gaplessPlayback)
                }

                // MARK: - Sources Section
                Section("Sources") {
                    ForEach($viewModel.sourceConfigs) { $config in
                        HStack {
                            VStack(alignment: .leading, spacing: 2) {
                                Text(config.name)
                                    .font(.system(size: 15, weight: .medium))
                                Text(config.isEnabled ? "Enabled" : "Disabled")
                                    .font(.system(size: 12))
                                    .foregroundColor(.secondary)
                            }
                            Spacer()
                            Toggle("", isOn: $config.isEnabled)
                                .tint(.accentColor)
                                .onChange(of: config.isEnabled) { _, _ in
                                    viewModel.toggleSource(config.id)
                                }
                        }
                    }
                }

                // MARK: - Storage Section
                Section("Storage") {
                    HStack {
                        Text("Cache Size")
                        Spacer()
                        Text(formatBytes(viewModel.cacheSize))
                            .foregroundColor(.secondary)
                    }
                    HStack {
                        Text("Storage Used")
                        Spacer()
                        Text(formatBytes(viewModel.storageUsed))
                            .foregroundColor(.secondary)
                    }
                    Button("Clear Cache", role: .destructive) {
                        viewModel.clearCache()
                    }
                }

                // MARK: - About Section
                Section("About") {
                    HStack {
                        Text("Version")
                        Spacer()
                        Text("1.0.0")
                            .foregroundColor(.secondary)
                    }
                    HStack {
                        Text("Build")
                        Spacer()
                        Text("1")
                            .foregroundColor(.secondary)
                    }
                }
            }
            .navigationTitle("Settings")
        }
    }

    private func formatBytes(_ bytes: Int64) -> String {
        let formatter = ByteCountFormatter()
        formatter.countStyle = .file
        return formatter.string(fromByteCount: bytes)
    }
}
