#import <Foundation/Foundation.h>
#import <AVFoundation/AVFoundation.h>

NS_ASSUME_NONNULL_BEGIN

/// Objective-C++ wrapper around the ImmersiveDSP (JamesDSP) native C engine.
/// Bridged to Swift via the DSPNative module.
@interface ImmersiveDspBridge : NSObject

@property (nonatomic, readonly) BOOL isValid;
@property (nonatomic, readonly) double sampleRate;

- (instancetype)initWithSampleRate:(double)sampleRate blockSize:(int)blockSize;
- (void)configureWithWideness:(double)wideness crossfeed:(double)crossfeed reverbMix:(double)reverbMix reverbDecay:(double)reverbDecay;
- (void)disable;
- (void)processFrames:(float *)buffer frameCount:(int)frameCount;
- (void)reset;

@end

NS_ASSUME_NONNULL_END
