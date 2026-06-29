import Foundation
import AVFoundation
import Accelerate

/// iOS DSP processor — wraps native ImmersiveDSP engine with a Swift fallback.
/// When the native C++ engine is unavailable, uses vDSP-based stereo widening and crossfeed.
final class DSPProcessor {
    private var nativeEngine: DSPNativeEngine?
    private var isNativeAvailable: Bool = false

    // Fallback DSP state
    private var fallbackWideness: Double = 0.0
    private var fallbackCrossfeed: Double = 0.0
    private var prevLeft: Float = 0.0
    private var prevRight: Float = 0.0

    init() {
        initializeNative()
    }

    func initialize() {
        if nativeEngine == nil {
            initializeNative()
        }
    }

    private func initializeNative() {
        nativeEngine = DSPNativeEngine()
        isNativeAvailable = nativeEngine?.isValid ?? false
        if isNativeAvailable {
            nativeEngine?.configure(sampleRate: 44100, blockSize: 4096)
        }
    }

    func apply(params: ResolvedSoundParams) {
        if isNativeAvailable {
            nativeEngine?.applyConfig(params: params)
        } else {
            fallbackWideness = params.wideness
            fallbackCrossfeed = params.crossfeed
        }
    }

    func disable() {
        if isNativeAvailable {
            nativeEngine?.disable()
        } else {
            fallbackWideness = 0.0
            fallbackCrossfeed = 0.0
        }
    }

    /// Process interleaved audio buffer (in-place)
    func process(buffer: UnsafeMutablePointer<Float>, frameCount: Int, channels: Int) {
        guard frameCount > 0, channels == 2 else { return }

        if isNativeAvailable {
            nativeEngine?.process(
                buffer: buffer,
                frameCount: Int32(frameCount)
            )
        } else {
            processFallback(buffer: buffer, frameCount: frameCount)
        }
    }

    /// Fallback DSP using vDSP
    private func processFallback(buffer: UnsafeMutablePointer<Float>, frameCount: Int) {
        let stride = 2 // interleaved stereo

        if fallbackWideness > 0.01 {
            // Simple mid-side stereo widener
            var wideness = Float(fallbackWideness * 0.5)
            for i in 0..<frameCount {
                let idx = i * stride
                let left = buffer[idx]
                let right = buffer[idx + 1]
                let mid = (left + right) * 0.5
                let side = (left - right) * 0.5
                buffer[idx] = mid + side * (1.0 + wideness)
                buffer[idx + 1] = mid - side * (1.0 + wideness)
            }
        }

        if fallbackCrossfeed > 0.01 {
            // Simple crossfeed (blend small amount of opposite channel)
            let amount = Float(fallbackCrossfeed * 0.15)
            for i in 0..<frameCount {
                let idx = i * stride
                let left = buffer[idx]
                let right = buffer[idx + 1]
                buffer[idx] = left + right * amount
                buffer[idx + 1] = right + left * amount
            }
        }

        // Prevent clipping
        var maxVal: Float = 0.0
        vDSP_maxmgv(buffer, 1, &maxVal, vDSP_Length(frameCount * stride))
        if maxVal > 1.0 {
            var scale = 1.0 / maxVal
            vDSP_vsmul(buffer, 1, &scale, buffer, 1, vDSP_Length(frameCount * stride))
        }
    }

    deinit {
        nativeEngine = nil
    }
}
