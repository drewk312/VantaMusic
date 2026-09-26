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

#include "audio_element_config.h"

#include <cstddef>
#include <string>
#include <vector>

#include "absl/log/absl_check.h"
#include "absl/log/absl_log.h"
#include "absl/status/status.h"
#include "absl/status/statusor.h"
#include "absl/strings/string_view.h"
#include "obr/common/ambisonic_utils.h"
#include "obr/common/constants.h"
#include "obr/renderer/audio_element_type.h"
#include "obr/renderer/input_channel_config.h"
#include "obr/renderer/loudspeaker_layouts.h"

namespace obr {

AudioElementConfig::AudioElementConfig(AudioElementType type,
                                       BinauralFilterProfile filter_profile)
    : type_(type),
      first_channel_index_(0),
      number_of_input_channels_(0),
      binaural_filters_ambisonic_order_(0),
      binaural_filter_profile_(filter_profile),
      head_locked_(false) {  // Default: world-locked (head tracking enabled)
  // Configure the audio element based on the requested type.
  // Handle passthrough types first (kPassthroughMono, kPassthroughStereo).
  if (IsPassthroughType(type)) {
    if (type == AudioElementType::kPassthroughMono) {
      number_of_input_channels_ = 1;
      // Use single passthrough channel with kMono label.
      passthrough_channels_.push_back(PassthroughInputChannel("kMono"));
    } else if (type == AudioElementType::kPassthroughStereo) {
      // Binaural: 2 channels (L/R), direct passthrough.
      number_of_input_channels_ = 2;
      // Use two passthrough channels with kL/kR labels.
      passthrough_channels_.push_back(PassthroughInputChannel("kL"));
      passthrough_channels_.push_back(PassthroughInputChannel("kR"));
    }
    // Passthrough types don't use binaural filters or ambisonic processing.
    binaural_filters_ambisonic_order_ = 0;
  } else if (IsAmbisonicsType(type)) {
    // Get the Ambisonic order from the type.
    const auto returned_order = GetAmbisonicOrder(type);
    ABSL_CHECK_OK(returned_order);
    const int order = returned_order.value();
    ABSL_CHECK_GE(order, kMinSupportedAmbisonicOrder);
    ABSL_CHECK_LE(order, kMaxSupportedAmbisonicOrder);

    // Set binaural filters matching the Ambisonic order of the input.
    // Downscaling / upscaling of Ambisonic scenes is not supported.
    binaural_filters_ambisonic_order_ = order;
    number_of_input_channels_ =
        GetNumPeriphonicComponents(binaural_filters_ambisonic_order_);

    // Populate the list of Ambisonic input channels.
    for (size_t i = 0; i < number_of_input_channels_; ++i) {
      AmbisonicSceneInputChannel ambisonic_channel("kACN" + std::to_string(i));
      ambisonic_channels_.push_back(ambisonic_channel);
    }

  } else if (IsLoudspeakerLayoutType(type)) {
    // Check if sub_type matches any of the available loudspeaker layouts.
    const LoudspeakerLayouts lspk_layouts;
    loudspeaker_channels_ = lspk_layouts.getLoudspeakerLayout(type);

    binaural_filters_ambisonic_order_ = kDefaultBinauralFiltersAmbisonicOrder;
    number_of_input_channels_ = loudspeaker_channels_.size();
  } else if (IsObjectType(type)) {
    if (type == AudioElementType::kObjectMono) {
      // Create a single input channel.
      AudioObjectInputChannel input_channel("kMono", 0.0f, 0.0f, 1.0f);
      object_channels_.push_back(input_channel);
    } else if (type == AudioElementType::kObjectDual) {
      // Create two input channels with independent positions.
      AudioObjectInputChannel channel_0("kDual0", 0.0f, 0.0f, 1.0f);
      AudioObjectInputChannel channel_1("kDual1", 0.0f, 0.0f, 1.0f);
      object_channels_.push_back(channel_0);
      object_channels_.push_back(channel_1);
    } else {
      ABSL_LOG(ERROR) << "Unsupported object type.";
    }

    binaural_filters_ambisonic_order_ = kDefaultBinauralFiltersAmbisonicOrder;
    number_of_input_channels_ = object_channels_.size();

  } else {
    ABSL_LOG(ERROR) << "Unknown audio element type.";
  }

  // Set the first channel index and update the channel indices for all.
  SetFirstChannelIndex(0);
}

AudioElementType AudioElementConfig::GetType() const { return type_; }

absl::StatusOr<absl::string_view> AudioElementConfig::GetTypeStr() {
  const auto type_string = GetAudioElementTypeStr(type_);
  if (!type_string.ok()) {
    return type_string.status();
  }
  return type_string.value();
}

void AudioElementConfig::SetFirstChannelIndex(size_t first_channel) {
  first_channel_index_ = first_channel;

  // Update the channel indices for all channels.
  for (size_t i = 0; i < ambisonic_channels_.size(); ++i) {
    ambisonic_channels_[i].SetChannelIndex(first_channel + i);
  }
  for (size_t i = 0; i < loudspeaker_channels_.size(); ++i) {
    loudspeaker_channels_[i].SetChannelIndex(first_channel + i);
  }
  for (size_t i = 0; i < object_channels_.size(); ++i) {
    object_channels_[i].SetChannelIndex(first_channel + i);
  }
  for (size_t i = 0; i < passthrough_channels_.size(); ++i) {
    passthrough_channels_[i].SetChannelIndex(first_channel + i);
  }
}

size_t AudioElementConfig::GetFirstChannelIndex() const {
  return first_channel_index_;
}

size_t AudioElementConfig::GetNumberOfInputChannels() const {
  return number_of_input_channels_;
}

std::vector<AmbisonicSceneInputChannel>&
AudioElementConfig::GetAmbisonicChannels() {
  return ambisonic_channels_;
}

std::vector<LoudspeakerLayoutInputChannel>&
AudioElementConfig::GetLoudspeakerChannels() {
  return loudspeaker_channels_;
}

std::vector<AudioObjectInputChannel>& AudioElementConfig::GetObjectChannels() {
  return object_channels_;
}

std::vector<PassthroughInputChannel>&
AudioElementConfig::GetPassthroughChannels() {
  return passthrough_channels_;
}

int AudioElementConfig::GetBinauralFiltersAmbisonicOrder() const {
  return binaural_filters_ambisonic_order_;
}

absl::StatusOr<absl::string_view>
AudioElementConfig::GetBinauralFilterProfileStr() {
  const auto& map = GetBinauralFilterProfileStringMap();
  auto it = map.find(binaural_filter_profile_);
  if (it != map.end()) {
    return it->second;
  }
  return absl::NotFoundError("Unknown BinauralFilterProfile enum value");
}

}  // namespace obr
