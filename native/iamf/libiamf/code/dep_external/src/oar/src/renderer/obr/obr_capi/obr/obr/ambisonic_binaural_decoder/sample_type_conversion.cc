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

#include "obr/ambisonic_binaural_decoder/sample_type_conversion.h"

#include <algorithm>
#include <cstddef>
#include <cstdint>

#include "obr/audio_buffer/simd_utils.h"

namespace obr {

void ConvertPlanarSamples(size_t length, const int16_t* input, float* output) {
  FloatFromInt16(length, input, output);
}

void ConvertPlanarSamples(size_t length, const float* input, float* output) {
  std::copy_n(input, length, output);
}

void ConvertPlanarSamples(size_t length, const float* input, int16_t* output) {
  Int16FromFloat(length, input, output);
}

}  // namespace obr
