import Foundation
import Combine
import SwiftUI

final class NowPlayingViewModel: ObservableObject {
    @Published var currentTrack: Track?
    @Published var playbackState: AudioEngine.PlaybackState = .stopped
    @Published var currentTime: TimeInterval = 0
    @Published var duration: TimeInterval = 0
    @Published var volume: Float = 1.0
    @Published var isShuffled: Bool = false
    @Published var repeatMode: AudioEngine.RepeatMode = .none
    @Published var showFullPlayer: Bool = false
    @Published var queue: [Track] = []
    @Published var currentIndex: Int = 0
    @Published var immersiveConfig = ImmersiveAudioConfig()

    private let audioEngine: AudioEngine
    private var cancellables = Set<AnyCancellable>()

    var isPlaying: Bool { playbackState == .playing }
    var progress: Double {
        duration > 0 ? currentTime / duration : 0
    }
    var formattedTime: String {
        let m = Int(currentTime) / 60
        let s = Int(currentTime) % 60
        return "\(m):\(String(format: "%02d", s))"
    }
    var formattedDuration: String {
        let m = Int(duration) / 60
        let s = Int(duration) % 60
        return "\(m):\(String(format: "%02d", s))"
    }

    init(audioEngine: AudioEngine) {
        self.audioEngine = audioEngine

        audioEngine.$currentTrack
            .assign(to: &$currentTrack)
        audioEngine.$playbackState
            .assign(to: &$playbackState)
        audioEngine.$currentTime
            .assign(to: &$currentTime)
        audioEngine.$duration
            .assign(to: &$duration)
        audioEngine.$volume
            .assign(to: &$volume)
        audioEngine.$isShuffled
            .assign(to: &$isShuffled)
        audioEngine.$repeatMode
            .assign(to: &$repeatMode)
    }

    // MARK: - Controls
    func play() { audioEngine.play() }
    func pause() { audioEngine.pause() }
    func togglePlayPause() { isPlaying ? pause() : play() }
    func seek(to time: TimeInterval) { audioEngine.seek(to: time) }
    func setVolume(_ vol: Float) { audioEngine.setVolume(vol) }
    func playNext() { audioEngine.playNext() }
    func playPrevious() { audioEngine.playPrevious() }
    func toggleShuffle() { audioEngine.toggleShuffle() }
    func toggleRepeatMode() { audioEngine.toggleRepeatMode() }

    func playTrack(_ track: Track) {
        audioEngine.play(track: track)
    }

    func playQueue(_ tracks: [Track], startIndex: Int = 0) {
        audioEngine.playQueue(tracks, startIndex: startIndex)
    }

    func toggleImmersiveSound() {
        var config = immersiveConfig
        config.enabled.toggle()
        if config.enabled {
            config = config.withEnableDefaults()
        }
        immersiveConfig = config
        audioEngine.setImmersiveConfig(config)
    }

    func updateImmersiveStrength(_ strength: Float) {
        var config = immersiveConfig
        config.strength = strength
        immersiveConfig = config
        audioEngine.setImmersiveConfig(config)
    }

    func toggleFullPlayer() {
        withAnimation(.spring(response: 0.35, dampingFraction: 0.9)) {
            showFullPlayer.toggle()
        }
    }
}
