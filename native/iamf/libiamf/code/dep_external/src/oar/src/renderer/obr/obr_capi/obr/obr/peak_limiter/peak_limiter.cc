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
#include "obr/peak_limiter/peak_limiter.h"

#include <algorithm>
#include <cmath>
#include <cstddef>
#include <cstdlib>
#include <vector>

#include "absl/log/absl_check.h"
#include "obr/audio_buffer/audio_buffer.h"

namespace obr {

PeakLimiter::PeakLimiter(const int sampling_rate, const double release_ms,
                         const double ceiling_db)
    : sampling_rate_(sampling_rate),
      ceiling_(std::pow(10, ceiling_db / 20)),
      release_time_constant_(
          std::exp(-3 / (sampling_rate_ * release_ms / 1000))),
      env_(1.0) {}

void PeakLimiter::Process(const AudioBuffer& input, AudioBuffer* output) {
  ABSL_CHECK_EQ(input.num_channels(), output->num_channels());
  ABSL_CHECK_EQ(input.num_frames(), output->num_frames());
  const size_t num_channels = input.num_channels();
  const size_t num_frames = input.num_frames();

  // Get a vector of maximum sample values across all channels.
  std::vector<float> max_samples(num_frames, 0.0);
  for (const auto& channel : input) {
    for (size_t frame = 0; frame < num_frames; ++frame) {
      max_samples[frame] = std::max(max_samples[frame], abs(channel[frame]));
    }
  }

  // Get the limiter envelope.
  std::vector<float> limiter_env(num_frames, 0.0);
  for (size_t frame = 0; frame < num_frames; ++frame) {
    double max_req_gain = GetMaximumRequiredGain(max_samples[frame]);
    if (max_req_gain < env_) {
      env_ = max_req_gain;
    } else {
      env_ = release_time_constant_ * (env_ - max_req_gain) + max_req_gain;
    }
    limiter_env[frame] = static_cast<float>(env_);
  }

  // Apply the limiter envelope to the input buffer.
  for (size_t channel = 0; channel < num_channels; ++channel) {
    for (size_t frame = 0; frame < num_frames; ++frame) {
      (*output)[channel][frame] = input[channel][frame] * limiter_env[frame];
    }
  }
}

double PeakLimiter::GetMaximumRequiredGain(double sample) const {
  if (abs(sample) > ceiling_) {
    return ceiling_ / abs(sample);
  } else {
    return 1;
  }
}

}  // namespace obr
