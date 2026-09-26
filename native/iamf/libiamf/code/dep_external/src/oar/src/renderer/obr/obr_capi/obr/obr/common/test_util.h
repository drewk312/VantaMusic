/*
 * Copyright (c) 2025, Alliance for Open Media. All rights reserved
 *
 * This source code is subject to the terms of the BSD 3-Clause Clear License
 * and the Alliance for Open Media Patent License 1.0. If the BSD 3-Clause Clear
 * License was not distributed with this source code in the LICENSE file, you
 * can obtain it at www.aomedia.org/license/software-license/bsd-3-c-c. If the
 * Alliance for Open Media Patent License 1.0 was not distributed with this
 * source code in the PATENTS file, you can obtain it at
 * www.aomedia.org/license/patent.
 */

#ifndef OBR_COMMON_TEST_UTIL_H_
#define OBR_COMMON_TEST_UTIL_H_

#include <cstddef>

#include "obr/audio_buffer/audio_buffer.h"

namespace obr {

// Silences an audio channel.
void GenerateSilence(AudioBuffer::Channel* output);

// Generates a sine wave between -1 and 1 at the specified frequency in hertz at
// the given sampling rate in hertz.
void GenerateSineWave(float frequency_hz, int sample_rate,
                      AudioBuffer::Channel* output);

// Generates a sine wave with pseudo-random phase.
void GenerateSineWithRandomPhase(float frequency_hz, float amplitude,
                                 int sampling_rate,
                                 AudioBuffer::Channel* output,
                                 unsigned int seed = 42);

// Generates a saw tooth signal between -1 and 1 for the given wave form length.
void GenerateSawToothSignal(size_t tooth_length_samples,
                            AudioBuffer::Channel* output);

// Generates a Dirac impulse filter kernel, which delays filtered signals by
// the given delay.
void GenerateDiracImpulseFilter(size_t delay_samples,
                                AudioBuffer::Channel* output);

// Generates a linear ramp signal between -1 and 1.
void GenerateIncreasingSignal(AudioBuffer::Channel* output);

// Generates white noise with the specified amplitude.
void GenerateWhiteNoise(float amplitude, AudioBuffer::Channel* output,
                        unsigned int seed = 42);

// Returns true if the buffer contains any non-zero values.
bool HasNonZeroOutput(const AudioBuffer& buffer);

// Returns true if the buffer contains only zero values.
bool HasAllZeroOutput(const AudioBuffer& buffer);

// Returns energy of a signal.
double CalculateSignalEnergy(const AudioBuffer::Channel& channel);

// Compares the content of two audio channels. Returns true if the absolute
// difference between all samples is below epsilon.
bool CompareAudioBuffers(const AudioBuffer::Channel& buffer_a,
                         const AudioBuffer::Channel& buffer_b, float epsilon);

// Returns delayed_signal.size() in output if delayed_signal is approximately
// equal to original_signal delayed by the given amount; otherwise, returns the
// index of the first unequal element in delayed_signal.
size_t DelayCompare(const AudioBuffer::Channel& original_signal,
                    const AudioBuffer::Channel& delayed_signal, size_t delay,
                    float epsilon);

// Creates an AudioBuffer with a Kronecker delta (Dirac impulse) encoded to
// Ambisonics at the specified position.
AudioBuffer GetKroneckerDeltaEncodedToAmbisonics(size_t num_frames,
                                                 float azimuth, float elevation,
                                                 float distance,
                                                 int ambisonic_order);

// Calculates broadband Interaural Level Difference (ILD) between left and
// right channels. Returns positive values when left is louder, negative when
// right is louder.
double GetBroadbandILD(const AudioBuffer::Channel& left,
                       const AudioBuffer::Channel& right);

// Returns the peak absolute amplitude in an audio channel.
float GetPeakAmplitude(const AudioBuffer::Channel& channel);

// Returns the RMS (root mean square) amplitude of an audio channel.
double GetRmsAmplitude(const AudioBuffer::Channel& channel);

}  // namespace obr

#endif  // OBR_COMMON_TEST_UTIL_H_
