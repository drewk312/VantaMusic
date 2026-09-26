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

#include "obr/ambisonic_binaural_decoder/dsp_utils.h"

#include <algorithm>
#include <cmath>
#include <cstddef>

#include "absl/log/absl_check.h"
#include "obr/audio_buffer/audio_buffer.h"
#include "obr/common/constants.h"

namespace obr {

size_t CeilToMultipleOfFramesPerBuffer(size_t size, size_t frames_per_buffer) {
  ABSL_DCHECK_NE(frames_per_buffer, 0U);
  const size_t remainder = size % frames_per_buffer;
  return remainder == 0 ? std::max(size, frames_per_buffer)
                        : size + frames_per_buffer - remainder;
}

void GenerateHannWindow(bool full_window, size_t window_length,
                        AudioBuffer::Channel* buffer) {
  ABSL_DCHECK_NE(buffer, nullptr);
  ABSL_DCHECK_LE(window_length, buffer->size());
  const float full_window_scaling_factor =
      kTwoPi / (static_cast<float>(window_length) - 1.0f);
  const float half_window_scaling_factor =
      kTwoPi / (2.0f * static_cast<float>(window_length) - 1.0f);
  const float scaling_factor =
      (full_window) ? full_window_scaling_factor : half_window_scaling_factor;
  for (size_t i = 0; i < window_length; ++i) {
    (*buffer)[i] =
        0.5f * (1.0f - std::cos(scaling_factor * static_cast<float>(i)));
  }
}

}  // namespace obr
