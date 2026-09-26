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
// Prevent Visual Studio from complaining about std::copy_n.
#if defined(_WIN32)
#define _SCL_SECURE_NO_WARNINGS
#endif

#include "obr/ambisonic_binaural_decoder/fft_manager.h"

#include <algorithm>
#include <cmath>
#include <cstddef>
#include <cstdlib>

#include "absl/log/absl_check.h"
#include "obr/audio_buffer/audio_buffer.h"
#include "obr/audio_buffer/simd_utils.h"
#include "obr/common/constants.h"
#include "obr/common/misc_math.h"
#include "pffft.h"

namespace obr {

namespace {

// for `fft_size_`s less than 2^14, the stack is used, on the recommendation of
// the author of the pffft library.
const size_t kPffftMaxStackSize = 16384;

}  // namespace

// The pffft implementation requires a minimum fft size of 32 samples.
const size_t FftManager::kMinFftSize = 32;

FftManager::FftManager(size_t frames_per_buffer)
    : fft_size_(std::max(NextPowTwo(frames_per_buffer) * 2, kMinFftSize)),
      frames_per_buffer_(frames_per_buffer),
      inverse_fft_scale_(1.0f / static_cast<float>(fft_size_)),
      temp_zeropad_buffer_(kNumMonoChannels, fft_size_),
      temp_freq_buffer_(kNumMonoChannels, fft_size_) {
  ABSL_DCHECK_GT(frames_per_buffer, 0U);
  ABSL_DCHECK_GE(fft_size_, kMinFftSize);
  ABSL_DCHECK(!(fft_size_ & (fft_size_ - 1)));  // Ensure it is a power of two.
  // Suggested pffft initialization.
  if (fft_size_ > kPffftMaxStackSize) {
    // Allocate memory for work space factors etc, Size recommended by pffft.
    const size_t num_bytes = 2 * fft_size_ * sizeof(float);
    pffft_workspace_ =
        reinterpret_cast<float*>(pffft_aligned_malloc(num_bytes));
  }

  fft_ = pffft_new_setup(static_cast<int>(fft_size_), PFFFT_REAL);

  temp_zeropad_buffer_.Clear();
}

FftManager::~FftManager() {
  pffft_destroy_setup(fft_);
  if (pffft_workspace_ != nullptr) {
    pffft_aligned_free(pffft_workspace_);
  }
}

void FftManager::FreqFromTimeDomain(const AudioBuffer::Channel& time_channel,
                                    AudioBuffer::Channel* freq_channel) {
  ABSL_DCHECK_NE(freq_channel, nullptr);
  ABSL_DCHECK_EQ(freq_channel->size(), fft_size_);
  ABSL_DCHECK_LE(time_channel.size(), fft_size_);

  // Perform forward FFT transform.
  if (time_channel.size() == fft_size_) {
    pffft_transform(fft_, time_channel.begin(), freq_channel->begin(),
                    pffft_workspace_, PFFFT_FORWARD);
  } else {
    std::copy_n(time_channel.begin(), frames_per_buffer_,
                temp_zeropad_buffer_[0].begin());
    pffft_transform(fft_, temp_zeropad_buffer_[0].begin(),
                    freq_channel->begin(), pffft_workspace_, PFFFT_FORWARD);
  }
}

void FftManager::TimeFromFreqDomain(const AudioBuffer::Channel& freq_channel,
                                    AudioBuffer::Channel* time_channel) {
  ABSL_DCHECK_NE(time_channel, nullptr);
  ABSL_DCHECK_EQ(freq_channel.size(), fft_size_);

  // Perform reverse FFT transform.
  const size_t time_channel_size = time_channel->size();
  if (time_channel_size == fft_size_) {
    pffft_transform(fft_, freq_channel.begin(), time_channel->begin(),
                    pffft_workspace_, PFFFT_BACKWARD);
  } else {
    ABSL_DCHECK_EQ(time_channel_size, frames_per_buffer_);
    auto& temp_channel = temp_freq_buffer_[0];
    pffft_transform(fft_, freq_channel.begin(), temp_channel.begin(),
                    pffft_workspace_, PFFFT_BACKWARD);
    std::copy_n(temp_channel.begin(), frames_per_buffer_,
                time_channel->begin());
  }
}

void FftManager::ApplyReverseFftScaling(AudioBuffer::Channel* time_channel) {
  ABSL_DCHECK(time_channel->size() == frames_per_buffer_ ||
              time_channel->size() == fft_size_);
  // Normalization must be performed here as we normally do this as part of the
  // convolution.
  ScalarMultiply(time_channel->size(), inverse_fft_scale_,
                 time_channel->begin(), time_channel->begin());
}

void FftManager::GetCanonicalFormatFreqBuffer(const AudioBuffer::Channel& input,
                                              AudioBuffer::Channel* output) {
  ABSL_DCHECK_EQ(input.size(), fft_size_);
  ABSL_DCHECK_EQ(output->size(), fft_size_);
  pffft_zreorder(fft_, input.begin(), output->begin(), PFFFT_FORWARD);
}

void FftManager::GetPffftFormatFreqBuffer(const AudioBuffer::Channel& input,
                                          AudioBuffer::Channel* output) {
  ABSL_DCHECK_EQ(input.size(), fft_size_);
  ABSL_DCHECK_EQ(output->size(), fft_size_);
  pffft_zreorder(fft_, input.begin(), output->begin(), PFFFT_BACKWARD);
}

void FftManager::MagnitudeFromCanonicalFreqBuffer(
    const AudioBuffer::Channel& freq_channel,
    AudioBuffer::Channel* magnitude_channel) {
  ABSL_DCHECK_NE(magnitude_channel, nullptr);
  ABSL_DCHECK_EQ(freq_channel.size(), fft_size_);
  ABSL_DCHECK_EQ(magnitude_channel->size(), frames_per_buffer_ + 1);

  (*magnitude_channel)[0] = std::abs(freq_channel[0]);
  ApproxComplexMagnitude(frames_per_buffer_ - 1, freq_channel.begin() + 2,
                         magnitude_channel->begin() + 1);
  (*magnitude_channel)[frames_per_buffer_] = std::abs(freq_channel[1]);
}

void FftManager::CanonicalFreqBufferFromMagnitudeAndPhase(
    const AudioBuffer::Channel& magnitude_channel,
    const AudioBuffer::Channel& phase_channel,
    AudioBuffer::Channel* canonical_freq_channel) {
  ABSL_DCHECK_NE(canonical_freq_channel, nullptr);
  ABSL_DCHECK_EQ(magnitude_channel.size(), frames_per_buffer_ + 1);
  ABSL_DCHECK_EQ(phase_channel.size(), frames_per_buffer_ + 1);
  ABSL_DCHECK_EQ(canonical_freq_channel->size(), fft_size_);

  (*canonical_freq_channel)[0] = magnitude_channel[0];
  (*canonical_freq_channel)[1] = -magnitude_channel[frames_per_buffer_];
  for (size_t i = 1, j = 2; i < frames_per_buffer_; ++i, j += 2) {
    (*canonical_freq_channel)[j] =
        magnitude_channel[i] * std::cos(phase_channel[i]);
    (*canonical_freq_channel)[j + 1] =
        magnitude_channel[i] * std::sin(phase_channel[i]);
  }
}

void FftManager::CanonicalFreqBufferFromMagnitudeAndSinCosPhase(
    size_t phase_offset, const AudioBuffer::Channel& magnitude_channel,
    const AudioBuffer::Channel& sin_phase_channel,
    const AudioBuffer::Channel& cos_phase_channel,
    AudioBuffer::Channel* canonical_freq_channel) {
  static const size_t kSimdLength = 4;
  ABSL_DCHECK_NE(canonical_freq_channel, nullptr);
  ABSL_DCHECK_EQ(magnitude_channel.size(), frames_per_buffer_ + 1);
  ABSL_DCHECK_GE(sin_phase_channel.size() + phase_offset,
                 frames_per_buffer_ + 1);
  ABSL_DCHECK_GE(cos_phase_channel.size() + phase_offset,
                 frames_per_buffer_ + 1);
  ABSL_DCHECK_EQ(canonical_freq_channel->size(), fft_size_);

  (*canonical_freq_channel)[0] = magnitude_channel[0];
  (*canonical_freq_channel)[1] = -magnitude_channel[frames_per_buffer_];
  // Continue on till we can guarantee alignment in our audio buffer.
  for (size_t i = 1, j = 2; i <= kSimdLength; ++i, j += 2) {
    (*canonical_freq_channel)[j] =
        magnitude_channel[i] * cos_phase_channel[i + phase_offset];
    (*canonical_freq_channel)[j + 1] =
        magnitude_channel[i] * sin_phase_channel[i + phase_offset];
  }
  ComplexInterleavedFormatFromMagnitudeAndSinCosPhase(
      2 * (frames_per_buffer_ - kSimdLength), &magnitude_channel[kSimdLength],
      &cos_phase_channel[kSimdLength + phase_offset],
      &sin_phase_channel[kSimdLength + phase_offset],
      &(*canonical_freq_channel)[2 * kSimdLength]);
}

void FftManager::FreqDomainConvolution(const AudioBuffer::Channel& input_a,
                                       const AudioBuffer::Channel& input_b,
                                       AudioBuffer::Channel* scaled_output) {
  ABSL_DCHECK_EQ(input_a.size(), fft_size_);
  ABSL_DCHECK_EQ(input_b.size(), fft_size_);
  ABSL_DCHECK_EQ(scaled_output->size(), fft_size_);
  pffft_zconvolve_accumulate(fft_, input_a.begin(), input_b.begin(),
                             scaled_output->begin(), inverse_fft_scale_);
}

}  // namespace obr
