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

#ifndef OBR_AUDIO_BUFFER_CHANNEL_VIEW_H_
#define OBR_AUDIO_BUFFER_CHANNEL_VIEW_H_

#include <cstring>
#include <vector>

#include "absl/log/absl_check.h"

// This code is forked from Resonance Audio's `channel_view.h`.
namespace obr {

/*!\brief Provides an interface to a single audio channel in `AudioBuffer`. Note
 * that a `ChannelView` instance does not own the data it is initialized with.
 */
class ChannelView {
 public:
  // Array subscript operator returning a reference.
  float& operator[](size_t index) {
    ABSL_DCHECK(enabled_);
    ABSL_DCHECK_LT(index, size_);
    return *(begin() + index);
  }

  // Const array subscript operator returning a const reference.
  const float& operator[](size_t index) const {
    ABSL_DCHECK(enabled_);
    ABSL_DCHECK_LT(index, size_);
    return *(begin() + index);
  }

  // Returns the size of the channel in samples.
  size_t size() const { return size_; }

  // Returns a float pointer to the begin of the channel data.
  float* begin() {
    ABSL_DCHECK(enabled_);
    return begin_iter_;
  }

  // Returns a float pointer to the end of the channel data.
  float* end() {
    ABSL_DCHECK(enabled_);
    return begin_iter_ + size_;
  }

  // Returns a const float pointer to the begin of the channel data.
  const float* begin() const {
    ABSL_DCHECK(enabled_);
    return begin_iter_;
  }

  // Returns a const float pointer to the end of the channel data.
  const float* end() const {
    ABSL_DCHECK(enabled_);
    return begin_iter_ + size_;
  }

  // Copy assignment from float vector.
  ChannelView& operator=(const std::vector<float>& other) {
    ABSL_DCHECK(enabled_);
    ABSL_DCHECK_EQ(other.size(), size_);
    memcpy(begin(), other.data(), sizeof(float) * size_);
    return *this;
  }

  // Copy assignment from ChannelView.
  ChannelView& operator=(const ChannelView& other) {
    if (this != &other) {
      ABSL_DCHECK(enabled_);
      ABSL_DCHECK_EQ(other.size(), size_);
      memcpy(begin(), other.begin(), sizeof(float) * size_);
    }
    return *this;
  }

  // Adds a `ChannelView` to this `ChannelView`.
  ChannelView& operator+=(const ChannelView& other);

  // Subtracts a `ChannelView` from this `ChannelView`.
  ChannelView& operator-=(const ChannelView& other);

  // Pointwise multiplies a `ChannelView` with this `Channelview`.
  ChannelView& operator*=(const ChannelView& other);

  // Fills channel buffer with zeros.
  void Clear() {
    ABSL_DCHECK(enabled_);
    memset(begin(), 0, sizeof(float) * size_);
  }

  /*!\brief Allows for disabling the channel to prevent access to the channel
   * data and channel iterators. It is used in the `Mixer` class to prevent the
   * copies of silence `ChannelView`s. Note that `ChannelView` are enabled by
   * default.
   *
   * \param enabled True to enable the channel.
   */
  void SetEnabled(bool enabled) { enabled_ = enabled; }

  /*!\brief Returns true if `ChannelView` is enabled.
   *
   * \return State of `enabled_` flag.
   */
  bool IsEnabled() const { return enabled_; }

 private:
  friend class AudioBuffer;

  // Constructor is initialized with a float pointer to the first sample and the
  // size of chunk of planar channel data.
  ChannelView(float* begin_iter, size_t size)
      : begin_iter_(begin_iter), size_(size), enabled_(true) {}

  // Iterator of first and last element in channel.
  float* const begin_iter_;

  // Channel size.
  const size_t size_;

  // Flag indicating if the channel is enabled.
  bool enabled_;
};

}  // namespace obr

#endif  // OBR_AUDIO_BUFFER_CHANNEL_VIEW_H_
