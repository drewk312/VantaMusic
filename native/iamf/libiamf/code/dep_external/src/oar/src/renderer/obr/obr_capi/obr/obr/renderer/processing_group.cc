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

#include "obr/renderer/processing_group.h"

#include <cstddef>
#include <memory>
#include <vector>

#include "absl/log/absl_check.h"
#include "absl/log/absl_log.h"
#include "absl/status/status.h"
#include "absl/strings/str_cat.h"
#include "obr/ambisonic_binaural_decoder/ambisonic_binaural_decoder.h"
#include "obr/ambisonic_binaural_decoder/fft_manager.h"
#include "obr/ambisonic_binaural_decoder/resampler.h"
#include "obr/ambisonic_binaural_decoder/sh_hrir_creator.h"
#include "obr/ambisonic_encoder/ambisonic_encoder.h"
#include "obr/ambisonic_rotator/ambisonic_rotator.h"
#include "obr/audio_buffer/audio_buffer.h"
#include "obr/common/ambisonic_utils.h"
#include "obr/common/constants.h"
#include "obr/common/misc_math.h"
#include "obr/renderer/audio_element_config.h"
#include "obr/renderer/audio_element_type.h"

namespace obr {

ProcessingGroup::ProcessingGroup(
    const ProcessingGroupKey& key,
    const std::vector<size_t>& audio_element_indices,
    int buffer_size_per_channel, int sampling_rate)
    : key_(key),
      audio_element_indices_(audio_element_indices),
      buffer_size_per_channel_(buffer_size_per_channel),
      sampling_rate_(sampling_rate) {
  const size_t num_ambisonic_channels =
      GetNumPeriphonicComponents(key_.ambisonic_order);
  ambisonic_mix_bed_ =
      AudioBuffer(num_ambisonic_channels, buffer_size_per_channel_);
  ambisonic_mix_bed_head_locked_ =
      AudioBuffer(num_ambisonic_channels, buffer_size_per_channel_);
}

ProcessingGroup::~ProcessingGroup() = default;

absl::Status ProcessingGroup::Initialize(FftManager* fft_manager,
                                         Resampler* resampler) {
  if (fft_manager == nullptr) {
    return absl::InvalidArgumentError("fft_manager cannot be null.");
  }
  if (resampler == nullptr) {
    return absl::InvalidArgumentError("resampler cannot be null.");
  }

  const int order = key_.ambisonic_order;
  const BinauralFilterProfile filter_profile = key_.filter_profile;

  if (order < kMinSupportedAmbisonicOrder) {
    return absl::InvalidArgumentError(absl::StrCat(
        "Ambisonic order ", order, " is below minimum supported order ",
        kMinSupportedAmbisonicOrder, "."));
  }
  if (order > kMaxSupportedAmbisonicOrder) {
    return absl::InvalidArgumentError(absl::StrCat(
        "Ambisonic order ", order, " exceeds maximum supported order ",
        kMaxSupportedAmbisonicOrder, "."));
  }

  ABSL_LOG(INFO) << "  - Initializing processing group:";
  ABSL_LOG(INFO) << "      - Ambisonic order: " << order;
  ABSL_LOG(INFO) << "      - Filter profile: "
                 << static_cast<int>(filter_profile);
  ABSL_LOG(INFO) << "      - Number of audio elements: "
                 << audio_element_indices_.size();

  // Initialize Ambisonic rotator.
  ambisonic_rotator_ = std::make_unique<AmbisonicRotator>(order);

  // Load filters matching the selected operational Ambisonic order and filter
  // type.
  const auto filter_type_string = BinauralFilterProfileToString(filter_profile);
  if (!filter_type_string.ok()) {
    return filter_type_string.status();
  }

  sh_hrirs_L_ = CreateShHrirsFromAssets(
      absl::StrCat(order, "OA", *filter_type_string, "L"), sampling_rate_,
      resampler);
  sh_hrirs_R_ = CreateShHrirsFromAssets(
      absl::StrCat(order, "OA", *filter_type_string, "R"), sampling_rate_,
      resampler);

  ABSL_CHECK_EQ(sh_hrirs_L_->num_channels(), sh_hrirs_R_->num_channels());
  ABSL_CHECK_EQ(sh_hrirs_L_->num_frames(), sh_hrirs_R_->num_frames());

  ambisonic_binaural_decoder_ = std::make_unique<AmbisonicBinauralDecoder>(
      *sh_hrirs_L_, *sh_hrirs_R_, buffer_size_per_channel_, fft_manager);

  return absl::OkStatus();
}

void ProcessingGroup::Process(
    const AudioBuffer& input_buffer,
    const std::vector<AudioElementConfig>& audio_elements,
    bool head_tracking_enabled, const WorldRotation& world_rotation,
    AudioBuffer* output_buffer) {
  ABSL_CHECK_NE(output_buffer, nullptr);
  ABSL_CHECK_EQ(output_buffer->num_channels(), kNumBinauralChannels);
  ABSL_CHECK_EQ(output_buffer->num_frames(), buffer_size_per_channel_);

  // Clear both Ambisonic mix beds.
  ambisonic_mix_bed_.Clear();
  ambisonic_mix_bed_head_locked_.Clear();

  // PASS 1: Process world-locked elements (head_locked = false)
  const std::vector<size_t> world_locked_indices =
      GetAmbisonicEncoderSourceChannelIndices(audio_elements, false);

  if (!world_locked_indices.empty() && ambisonic_encoder_ != nullptr) {
    // Copy selected channels from input buffer to encoder input buffer.
    for (size_t i = 0; i < world_locked_indices.size(); ++i) {
      ambisonic_encoder_input_buffer_[i] =
          input_buffer[world_locked_indices[i]];
    }

    ambisonic_encoder_->ProcessPlanarAudioData(ambisonic_encoder_input_buffer_,
                                               &ambisonic_mix_bed_);
  }

  // Add world-locked Ambisonic input channels to world-locked bed.
  for (size_t ae_index : audio_element_indices_) {
    const auto& audio_element = audio_elements[ae_index];
    if (IsAmbisonicsType(audio_element.GetType()) &&
        !audio_element.IsHeadLocked()) {
      for (size_t channel = 0;
           channel < audio_element.GetNumberOfInputChannels(); ++channel) {
        ambisonic_mix_bed_[channel] +=
            input_buffer[audio_element.GetFirstChannelIndex() + channel];
      }
    }
  }

  // PASS 2: Process head-locked elements (head_locked = true)
  const std::vector<size_t> head_locked_indices =
      GetAmbisonicEncoderSourceChannelIndices(audio_elements, true);

  if (!head_locked_indices.empty() && ambisonic_encoder_ != nullptr) {
    // Copy selected channels from input buffer to encoder input buffer.
    for (size_t i = 0; i < head_locked_indices.size(); ++i) {
      ambisonic_encoder_input_buffer_[i] = input_buffer[head_locked_indices[i]];
    }

    ambisonic_encoder_->ProcessPlanarAudioData(ambisonic_encoder_input_buffer_,
                                               &ambisonic_mix_bed_head_locked_);
  }

  // Add head-locked Ambisonic input channels to head-locked bed.
  for (size_t ae_index : audio_element_indices_) {
    const auto& audio_element = audio_elements[ae_index];
    if (IsAmbisonicsType(audio_element.GetType()) &&
        audio_element.IsHeadLocked()) {
      for (size_t channel = 0;
           channel < audio_element.GetNumberOfInputChannels(); ++channel) {
        ambisonic_mix_bed_head_locked_[channel] +=
            input_buffer[audio_element.GetFirstChannelIndex() + channel];
      }
    }
  }

  // Apply rotation ONLY to world-locked bed if head tracking is enabled.
  if (head_tracking_enabled) {
    // Pass world-locked Ambisonic mix bed through Ambisonic Rotator.
    ambisonic_rotator_->Process(world_rotation, ambisonic_mix_bed_,
                                &ambisonic_mix_bed_);
  }

  // Sum both beds: combine world-locked (possibly rotated) and head-locked.
  ambisonic_mix_bed_ += ambisonic_mix_bed_head_locked_;

  // Pass combined Ambisonic mix bed through Ambisonic Binaural Decoder.
  ambisonic_binaural_decoder_->ProcessAudioBuffer(ambisonic_mix_bed_,
                                                  output_buffer);
}

absl::Status ProcessingGroup::UpdateAmbisonicEncoder(
    std::vector<AudioElementConfig>& audio_elements) {
  // Get encoder source indices to determine if we need an encoder.
  const auto indices = GetAmbisonicEncoderSourceChannelIndices(audio_elements);

  if (indices.empty()) {
    // No sources to encode, so no encoder needed.
    ambisonic_encoder_.reset();
    ambisonic_encoder_input_buffer_ = AudioBuffer();
    return absl::OkStatus();
  }

  // Initialize or reinitialize encoder if source count changed.
  if (ambisonic_encoder_ == nullptr ||
      ambisonic_encoder_input_buffer_.num_channels() != indices.size()) {
    ambisonic_encoder_input_buffer_ =
        AudioBuffer(indices.size(), buffer_size_per_channel_);
    ambisonic_encoder_ = std::make_unique<AmbisonicEncoder>(
        indices.size(), key_.ambisonic_order);
  }

  // Update Ambisonic encoder settings.
  size_t ambisonic_encoder_input_channel_index = 0;
  for (size_t ae_index : audio_element_indices_) {
    auto& audio_element = audio_elements[ae_index];

    // Iterate over loudspeaker channels.
    for (const auto& source : audio_element.GetLoudspeakerChannels()) {
      ambisonic_encoder_->SetSource(
          static_cast<int>(ambisonic_encoder_input_channel_index), 1.0f,
          source.GetAzimuth(), source.GetElevation(), source.GetDistance());
      ambisonic_encoder_input_channel_index++;
    }

    // Iterate over object input channels.
    for (const auto& source : audio_element.GetObjectChannels()) {
      ambisonic_encoder_->SetSource(
          static_cast<int>(ambisonic_encoder_input_channel_index), 1.0f,
          source.GetAzimuth(), source.GetElevation(), source.GetDistance());
      ambisonic_encoder_input_channel_index++;
    }
  }

  return absl::OkStatus();
}

std::vector<size_t> ProcessingGroup::GetAmbisonicEncoderSourceChannelIndices(
    const std::vector<AudioElementConfig>& audio_elements) const {
  std::vector<size_t> source_channel_indices;
  // Reserve capacity based on estimated number of channels to avoid
  // reallocations.
  size_t estimated_channels = 0;
  for (size_t ae_index : audio_element_indices_) {
    const auto& audio_element = audio_elements[ae_index];
    if (IsLoudspeakerLayoutType(audio_element.GetType()) ||
        IsObjectType(audio_element.GetType())) {
      estimated_channels += audio_element.GetNumberOfInputChannels();
    }
  }
  source_channel_indices.reserve(estimated_channels);

  for (size_t ae_index : audio_element_indices_) {
    const auto& audio_element = audio_elements[ae_index];
    if (IsLoudspeakerLayoutType(audio_element.GetType()) ||
        IsObjectType(audio_element.GetType())) {
      for (size_t i = 0; i < audio_element.GetNumberOfInputChannels(); ++i) {
        source_channel_indices.push_back(audio_element.GetFirstChannelIndex() +
                                         i);
      }
    }
  }
  return source_channel_indices;
}

std::vector<size_t> ProcessingGroup::GetAmbisonicEncoderSourceChannelIndices(
    const std::vector<AudioElementConfig>& audio_elements,
    bool filter_head_locked) const {
  std::vector<size_t> source_channel_indices;

  for (size_t ae_index : audio_element_indices_) {
    const auto& audio_element = audio_elements[ae_index];

    // Filter by head_locked status.
    if (audio_element.IsHeadLocked() != filter_head_locked) {
      continue;
    }

    if (IsLoudspeakerLayoutType(audio_element.GetType()) ||
        IsObjectType(audio_element.GetType())) {
      for (size_t i = 0; i < audio_element.GetNumberOfInputChannels(); ++i) {
        source_channel_indices.push_back(audio_element.GetFirstChannelIndex() +
                                         i);
      }
    }
  }

  return source_channel_indices;
}

}  // namespace obr
