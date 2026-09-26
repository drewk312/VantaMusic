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
// TODO(b/400674746): Use existing library to convert sample types.
#ifndef OBR_SAMPLE_TYPE_CONVERSION_H_
#define OBR_SAMPLE_TYPE_CONVERSION_H_

#include <algorithm>
#include <cstddef>
#include <cstdint>

#include "absl/log/absl_check.h"

// This code is forked from Resonance Audio's `sample_type_conversion.h`.
namespace obr {

// Convert the given int16_t to a float in the range [-1.0f, 1.0f].
inline void ConvertSampleToFloatFormat(int16_t input, float* output) {
  ABSL_DCHECK_NE(output, nullptr);
  static const float kInt16Max = static_cast<float>(0x7FFF);
  static const float kInt16ToFloat = 1.0f / kInt16Max;
  *output = input * kInt16ToFloat;
}

// Overloaded input argument to support sample type templated methods.
inline void ConvertSampleToFloatFormat(float input, float* output) {
  ABSL_DCHECK_NE(output, nullptr);
  *output = input;
}

// Saturating if the float is not in [-1.0f, 1.0f].
inline void ConvertSampleFromFloatFormat(float input, int16_t* output) {
  ABSL_DCHECK_NE(output, nullptr);
  // Convert the given float to an int16_t in the range
  // [-32767 (0x7FFF), 32767 (0x7FFF)],
  static const float kInt16Min = static_cast<float>(-0x7FFF);
  static const float kInt16Max = static_cast<float>(0x7FFF);
  static const float kFloatToInt16 = kInt16Max;
  const float scaled_value = input * kFloatToInt16;
  const float clamped_value =
      std::min(kInt16Max, std::max(kInt16Min, scaled_value));
  *output = static_cast<int16_t>(clamped_value);
}

// Overloaded output argument to support sample type templated methods.
inline void ConvertSampleFromFloatFormat(float input, float* output) {
  ABSL_DCHECK_NE(output, nullptr);
  *output = input;
}

// Convert a vector of int16_t samples to float format in the range
// [-1.0f, 1.0f].
void ConvertPlanarSamples(size_t length, const int16_t* input, float* output);

// Overloaded input argument to support sample type templated methods.
void ConvertPlanarSamples(size_t length, const float* input, float* output);

// Overloaded method to support methods templated against the input sample type.
void ConvertPlanarSamples(size_t length, const float* input, int16_t* output);

// Overloaded output argument to support sample type templated methods.
void ConvertPlanarSamples(size_t length, const float* input, float* output);

}  // namespace obr

#endif  // OBR_SAMPLE_TYPE_CONVERSION_H_
