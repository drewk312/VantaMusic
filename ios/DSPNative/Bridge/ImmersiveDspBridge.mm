#import "ImmersiveDspBridge.h"

// Include the JamesDSP native headers from the libjamesdsp library
extern "C" {
#include "jdsp_header.h"
}

@interface ImmersiveDspBridge () {
    JamesDSPLib *_dsp;
    int _blockSize;
    float _sampleRate;
    BOOL _configured;
}

@end

@implementation ImmersiveDspBridge

- (instancetype)initWithSampleRate:(double)sampleRate blockSize:(int)blockSize {
    self = [super init];
    if (self) {
        _sampleRate = (float)sampleRate;
        _blockSize = blockSize;
        _configured = NO;

        JamesDSPGlobalMemoryAllocation();
        _dsp = (JamesDSPLib *)malloc(sizeof(JamesDSPLib));
        if (!_dsp) {
            JamesDSPGlobalMemoryDeallocation();
            return nil;
        }
        memset(_dsp, 0, sizeof(JamesDSPLib));

        JamesDSPInit(_dsp, blockSize, _sampleRate);
        if (!JamesDSPGetMutexStatus(_dsp)) {
            JamesDSPFree(_dsp);
            free(_dsp);
            _dsp = NULL;
            JamesDSPGlobalMemoryDeallocation();
            return nil;
        }

        if ((size_t)blockSize > _dsp->blockSizeMax) {
            JamesDSPReallocateBlock(_dsp, (size_t)blockSize);
        }

        JLimiterInit(_dsp);
        JLimiterSetCoefficients(_dsp, -0.1, 60.0);
        JamesDSPSetPostGain(_dsp, 0.0);

        _isValid = YES;
    }
    return self;
}

- (BOOL)isValid {
    return _dsp != NULL;
}

- (void)configureWithWideness:(double)wideness
                    crossfeed:(double)crossfeed
                    reverbMix:(double)reverbMix
                  reverbDecay:(double)reverbDecay {
    if (!_dsp) return;

    jdsp_lock(_dsp);

    if (wideness > 0.01) {
        StereoEnhancementSetParam(_dsp, (float)wideness);
        StereoEnhancementEnable(_dsp);
    } else {
        StereoEnhancementDisable(_dsp);
    }

    if (crossfeed > 0.01) {
        CrossfeedChangeMode(_dsp, 1); // balanced mode
        if (!_dsp->crossfeedEnabled) {
            CrossfeedEnable(_dsp, 1);
        }
    } else if (_dsp->crossfeedEnabled) {
        CrossfeedDisable(_dsp);
    }

    if (reverbMix > 0.01) {
        Reverb_SetParam(_dsp, 0); // small room
        if (!_dsp->reverbEnabled) {
            ReverbEnable(_dsp);
        }
    } else if (_dsp->reverbEnabled) {
        ReverbDisable(_dsp);
    }

    jdsp_unlock(_dsp);
    _configured = YES;
}

- (void)disable {
    if (!_dsp) return;

    jdsp_lock(_dsp);
    StereoEnhancementDisable(_dsp);
    if (_dsp->crossfeedEnabled) {
        CrossfeedDisable(_dsp);
    }
    if (_dsp->reverbEnabled) {
        ReverbDisable(_dsp);
    }
    jdsp_unlock(_dsp);
    _configured = NO;
}

- (void)processFrames:(float *)buffer frameCount:(int)frameCount {
    if (!_dsp || frameCount <= 0) return;

    // Process deinterleaved: the buffer is interleaved stereo (L,R,L,R,...)
    // JamesDSP uses deinterleaved format, so we process in chunks
    const int CHUNK = 1024;
    int offset = 0;
    while (offset < frameCount) {
        int chunk = MIN(CHUNK, frameCount - offset);

        // Prepare deinterleaved scratch buffers on the stack
        float left[CHUNK];
        float right[CHUNK];

        // Deinterleave
        for (int i = 0; i < chunk; i++) {
            left[i] = buffer[(offset + i) * 2];
            right[i] = buffer[(offset + i) * 2 + 1];
        }

        // Process through native engine
        _dsp->processFloatDeinterleaved(_dsp, left, right, left, right, (size_t)chunk);

        // Re-interleave
        for (int i = 0; i < chunk; i++) {
            buffer[(offset + i) * 2] = left[i];
            buffer[(offset + i) * 2 + 1] = right[i];
        }
        offset += chunk;
    }
}

- (void)reset {
    if (!_dsp) return;
    // Re-apply initial state
    JamesDSPSetSampleRate(_dsp, _sampleRate, 1);
    if (_configured) {
        [self disable];
    }
}

- (void)dealloc {
    if (_dsp) {
        JamesDSPFree(_dsp);
        free(_dsp);
        JamesDSPGlobalMemoryDeallocation();
        _dsp = NULL;
    }
}

@end
