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

#include "obr/audio_buffer/channel_view.h"

#include "absl/log/absl_check.h"
#include "obr/audio_buffer/simd_utils.h"

namespace obr {

ChannelView& ChannelView::operator+=(const ChannelView& other) {
  ABSL_DCHECK_EQ(other.size(), size_);
  ABSL_DCHECK(enabled_);
  float* this_sample = begin();
  const float* other_sample = other.begin();
  AddPointwise(size_, other_sample, this_sample, this_sample);
  return *this;
}

ChannelView& ChannelView::operator-=(const ChannelView& other) {
  ABSL_DCHECK_EQ(other.size(), size_);
  ABSL_DCHECK(enabled_);
  float* this_sample = begin();
  const float* other_sample = other.begin();
  SubtractPointwise(size_, other_sample, this_sample, this_sample);
  return *this;
}

ChannelView& ChannelView::operator*=(const ChannelView& other) {
  ABSL_DCHECK_EQ(other.size(), size_);
  ABSL_DCHECK(enabled_);
  float* this_sample = begin();
  const float* other_sample = other.begin();
  MultiplyPointwise(size_, other_sample, this_sample, this_sample);
  return *this;
}

}  // namespace obr
