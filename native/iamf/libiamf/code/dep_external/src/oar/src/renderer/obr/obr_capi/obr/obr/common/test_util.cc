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

#include "obr/common/test_util.h"

#include <algorithm>
#include <cmath>
#include <cstddef>
#include <random>

#include "absl/log/absl_check.h"
#include "gtest/gtest.h"
#include "obr/ambisonic_encoder/ambisonic_encoder.h"
#include "obr/audio_buffer/audio_buffer.h"
#include "obr/common/ambisonic_utils.h"
#include "obr/common/constants.h"

namespace obr {

namespace {}  // namespace

void GenerateSilence(AudioBuffer::Channel* output) {
  ASSERT_NE(output, nullptr);
  output->Clear();
}

void GenerateSineWave(float frequency_hz, int sample_rate,
                      AudioBuffer::Channel* output) {
  ASSERT_GE(frequency_hz, 0.0f);
  ASSERT_GT(sample_rate, 0);
  ASSERT_NE(output, nullptr);
  for (size_t i = 0; i < output->size(); ++i) {
    const float phase = static_cast<float>(i) * kTwoPi /
                        static_cast<float>(sample_rate) * frequency_hz;
    (*output)[i] = std::sin(phase);
  }
}

void GenerateSineWithRandomPhase(float frequency_hz, float amplitude,
                                 int sampling_rate,
                                 AudioBuffer::Channel* output,
                                 unsigned int seed) {
  ASSERT_NE(output, nullptr);
  ASSERT_GE(frequency_hz, 0.0f);
  ASSERT_GT(sampling_rate, 0);
  ASSERT_GE(amplitude, 0.0f);

  std::mt19937 rng(seed);
  std::uniform_real_distribution<float> phase_dist(0.0f, kTwoPi);

  const float angular_frequency =
      kTwoPi * frequency_hz / static_cast<float>(sampling_rate);
  const float random_phase = phase_dist(rng);

  for (size_t i = 0; i < output->size(); ++i) {
    (*output)[i] =
        amplitude *
        std::sin(angular_frequency * static_cast<float>(i) + random_phase);
  }
}

void GenerateSawToothSignal(size_t tooth_length_samples,
                            AudioBuffer::Channel* output) {
  ASSERT_GT(tooth_length_samples, 0U);
  ASSERT_NE(output, nullptr);

  for (size_t i = 0; i < output->size(); ++i) {
    (*output)[i] = static_cast<float>(i % tooth_length_samples) /
                       static_cast<float>(tooth_length_samples) * 2.0f -
                   1.0f;
  }
}

void GenerateDiracImpulseFilter(size_t delay_samples,
                                AudioBuffer::Channel* output) {
  ASSERT_NE(output, nullptr);
  ASSERT_LT(delay_samples, output->size());
  ASSERT_NE(output, nullptr);
  output->Clear();
  (*output)[delay_samples] = 1.0f;
}

void GenerateIncreasingSignal(AudioBuffer::Channel* output) {
  ASSERT_NE(output, nullptr);
  for (size_t i = 0; i < output->size(); ++i) {
    (*output)[i] =
        static_cast<float>(i) / static_cast<float>(output->size()) * 2.0f -
        1.0f;
  }
}

void GenerateWhiteNoise(float amplitude, AudioBuffer::Channel* output,
                        unsigned int seed) {
  ASSERT_NE(output, nullptr);
  ASSERT_GE(amplitude, 0.0f);

  std::mt19937 rng(seed);
  std::uniform_real_distribution<float> dist(-amplitude, amplitude);

  for (size_t i = 0; i < output->size(); ++i) {
    (*output)[i] = dist(rng);
  }
}

bool HasNonZeroOutput(const AudioBuffer& buffer) {
  for (const auto& buffer_channel : buffer) {
    for (const auto sample : buffer_channel) {
      if (sample != 0.0f) {
        return true;
      }
    }
  }
  return false;
}

bool HasAllZeroOutput(const AudioBuffer& buffer) {
  return !HasNonZeroOutput(buffer);
}

double CalculateSignalEnergy(const AudioBuffer::Channel& channel) {
  double energy = 0.0;
  for (const float sample : channel) {
    energy += sample * sample;
  }
  return energy;
}

bool CompareAudioBuffers(const AudioBuffer::Channel& buffer_a,
                         const AudioBuffer::Channel& buffer_b, float epsilon) {
  if (buffer_a.size() != buffer_b.size()) {
    return false;
  }
  for (size_t i = 0; i < buffer_a.size(); ++i) {
    if (std::abs(buffer_a[i] - buffer_b[i]) > epsilon) {
      return false;
    }
  }
  return true;
}

size_t DelayCompare(const AudioBuffer::Channel& original_signal,
                    const AudioBuffer::Channel& delayed_signal, size_t delay,
                    float epsilon) {
  if (delay > delayed_signal.size() ||
      (delayed_signal.size() > original_signal.size() + delay)) {
    return 0;
  }
  for (size_t i = delay; i < delayed_signal.size(); ++i) {
    const size_t original_index = i - delay;
    const float difference =
        std::abs(delayed_signal[i] - original_signal[original_index]);
    if (difference > epsilon) {
      return i;
    }
  }
  return delayed_signal.size();
}

AudioBuffer GetKroneckerDeltaEncodedToAmbisonics(size_t num_frames,
                                                 float azimuth, float elevation,
                                                 float distance,
                                                 int ambisonic_order) {
  AudioBuffer mono_signal(1, num_frames);
  AudioBuffer output_buffer(GetNumPeriphonicComponents(ambisonic_order),
                            num_frames);

  GenerateDiracImpulseFilter(0, &mono_signal[0]);

  AmbisonicEncoder encoder(1, ambisonic_order);
  encoder.SetSource(0, 1.0f, azimuth, elevation, distance);
  encoder.ProcessPlanarAudioData(mono_signal, &output_buffer);

  return output_buffer;
}

double GetBroadbandILD(const AudioBuffer::Channel& left,
                       const AudioBuffer::Channel& right) {
  const double energy_left = CalculateSignalEnergy(left);
  const double energy_right = CalculateSignalEnergy(right);

  ABSL_DCHECK_GT(energy_right, 0.0);
  const double ILD = 10.0 * std::log10(energy_left / energy_right);
  return ILD;
}

float GetPeakAmplitude(const AudioBuffer::Channel& channel) {
  float peak = 0.0f;
  for (const float& sample : channel) {
    peak = std::max(peak, std::abs(sample));
  }
  return peak;
}

double GetRmsAmplitude(const AudioBuffer::Channel& channel) {
  if (channel.size() == 0) {
    return 0.0;
  }
  const double energy = CalculateSignalEnergy(channel);
  return std::sqrt(energy / static_cast<double>(channel.size()));
}

}  // namespace obr
