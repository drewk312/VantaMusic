import Foundation

/// Swift wrapper around the Objective-C++ ImmersiveDspBridge.
/// Called from DSPProcessor to apply native DSP effects.
final class DSPNativeEngine {
    private let bridge: ImmersiveDspBridge

    var sampleRate: Double { bridge.sampleRate }
    var isValid: Bool { bridge.isValid }

    init?() {
        guard let bridge = ImmersiveDspBridge(sampleRate: 44100, blockSize: 4096),
              bridge.isValid else {
            return nil
        }
        self.bridge = bridge
    }

    func configure(sampleRate: Double, blockSize: Int) {
        // Bridge already initialized in init
    }

    func applyConfig(params: ResolvedSoundParams) {
        bridge.configure(
            withWideness: params.wideness,
            crossfeed: params.crossfeed,
            reverbMix: params.reverbMix,
            reverbDecay: params.reverbDecay
        )
    }

    func disable() {
        bridge.disable()
    }

    func process(buffer: UnsafeMutablePointer<Float>, frameCount: Int32) {
        bridge.processFrames(buffer, frameCount: Int(frameCount))
    }

    func reset() {
        bridge.reset()
    }

    deinit {
        bridge.disable()
    }
}
