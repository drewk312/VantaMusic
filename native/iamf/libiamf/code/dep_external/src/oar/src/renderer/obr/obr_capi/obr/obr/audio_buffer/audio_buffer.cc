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

#include "obr/audio_buffer/audio_buffer.h"

#include <cstddef>
#include <utility>

#include "obr/audio_buffer/channel_view.h"
#include "obr/audio_buffer/simd_utils.h"
#include "obr/common/constants.h"

namespace obr {

AudioBuffer::AudioBuffer() : num_frames_(0) {}

AudioBuffer::AudioBuffer(size_t num_channels, size_t num_frames)
    : num_frames_(num_frames) {
  InitChannelViews(num_channels);
}

// Copy assignment from AudioBuffer.
AudioBuffer& AudioBuffer::operator=(const AudioBuffer& other) {
  if (this != &other) {
    num_frames_ = other.num_frames_;
    InitChannelViews(other.num_channels());
    for (size_t i = 0; i < num_channels(); ++i) {
      channel_views_[i] = other.channel_views_[i];
    }
  }
  return *this;
}

AudioBuffer::AudioBuffer(AudioBuffer&& other) {
  num_frames_ = other.num_frames_;
  other.num_frames_ = 0;
  data_ = std::move(other.data_);
  data_size_ = other.data_size_;
  other.data_size_ = 0;
  channel_views_ = std::move(other.channel_views_);
}

void AudioBuffer::InitChannelViews(size_t num_channels) {
  const size_t num_frames_to_next_channel = FindNextAlignedArrayIndex(
      num_frames_, sizeof(float), kMemoryAlignmentBytes);

  data_size_ = num_channels * num_frames_to_next_channel;
  data_.resize(data_size_);

  channel_views_.clear();
  channel_views_.reserve(num_channels);

  float* itr = data_.data();

  for (size_t i = 0; i < num_channels; ++i) {
    ChannelView new_channel_view(itr, num_frames_);
    channel_views_.push_back(new_channel_view);
    itr += num_frames_to_next_channel;
  }
}

}  // namespace obr
