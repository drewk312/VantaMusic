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

#include "obr/renderer/obr_impl.h"

#include <cstddef>
#include <map>
#include <memory>
#include <string>
#include <vector>

#include "absl/log/absl_check.h"
#include "absl/log/absl_log.h"
#include "absl/status/status.h"
#include "absl/synchronization/mutex.h"
#include "obr/ambisonic_binaural_decoder/fft_manager.h"
#include "obr/audio_buffer/audio_buffer.h"
#include "obr/common/constants.h"
#include "obr/common/misc_math.h"
#include "obr/common/status_macros.h"
#include "obr/peak_limiter/peak_limiter.h"
#include "obr/renderer/audio_element_config.h"
#include "obr/renderer/audio_element_table_formatter.h"
#include "obr/renderer/audio_element_type.h"
#include "obr/renderer/processing_group.h"

namespace obr {

ObrImpl::ObrImpl(int buffer_size_per_channel, int sampling_rate)
    : buffer_size_per_channel_(buffer_size_per_channel),
      sampling_rate_(sampling_rate),
      head_tracking_enabled_(false),
      limiter_enabled_(true),
      world_rotation_(WorldRotation()),
      fft_manager_(buffer_size_per_channel_) {
  ABSL_CHECK_GT(buffer_size_per_channel, 0)
      << "Buffer size per channel must be greater than 0.";
  ABSL_CHECK_GT(sampling_rate_, 0) << "Sampling rate must be greater than 0.";

  ABSL_CHECK_GE(buffer_size_per_channel, FftManager::kMinFftSize)
      << "Buffer size per channel must be at least " << FftManager::kMinFftSize
      << " samples.";

  ABSL_CHECK_LE(buffer_size_per_channel, kMaxSupportedNumFrames)
      << "Only frame lengths up to " << kMaxSupportedNumFrames
      << " are supported.";
}

ObrImpl::~ObrImpl() = default;

absl::Status ObrImpl::ResetDsp() {
  ABSL_LOG(INFO) << "Resetting DSP.";

  // Enable the lock.
  absl::MutexLock lock(&mutex_);

  // Release resources from all processing groups.
  processing_groups_.clear();

  // Release peak limiter.
  peak_limiter_.reset();

  return absl::OkStatus();
}

absl::Status ObrImpl::InitializeDsp() {
  // Reset the DSP.
  RETURN_IF_NOT_OK(ResetDsp());

  // Acquire the lock for the entire initialization.
  absl::MutexLock lock(&mutex_);

  // Check that the audio elements list is not empty.
  if (audio_elements_.empty()) {
    return absl::FailedPreconditionError(
        "No audio elements configured. Can't initialize DSP.");
  }

  const auto number_of_input_channels_locked = GetNumberOfInputChannelsLocked();
  if (number_of_input_channels_locked == 0) {
    return absl::FailedPreconditionError(
        "No input channels configured. Can't initialize DSP.");
  }

  // Create processing groups based on audio elements.
  CreateProcessingGroups();

  ABSL_LOG(INFO) << "Initializing DSP:";
  ABSL_LOG(INFO) << "  - Number of input channels: "
                 << number_of_input_channels_locked;
  ABSL_LOG(INFO) << "  - Number of processing groups: "
                 << processing_groups_.size();

  // Initialize each processing group.
  for (auto& group : processing_groups_) {
    RETURN_IF_NOT_OK(group.Initialize(&fft_manager_, &resampler_));
    RETURN_IF_NOT_OK(group.UpdateAmbisonicEncoder(audio_elements_));
  }

  // Initialize peak limiter (shared across all groups).
  peak_limiter_ = std::make_unique<PeakLimiter>(sampling_rate_, 50, -0.5);

  return absl::OkStatus();
}

void ObrImpl::CreateProcessingGroups() {
  processing_groups_.clear();

  // Group audio elements by their processing key (order + filter profile).
  // Skip passthrough elements as they bypass binaural processing.
  std::map<ProcessingGroupKey, std::vector<size_t>> groups_map;

  for (size_t i = 0; i < audio_elements_.size(); ++i) {
    // Skip passthrough types - they don't need processing groups.
    if (IsPassthroughType(audio_elements_[i].GetType())) {
      continue;
    }
    ProcessingGroupKey key;
    key.ambisonic_order = audio_elements_[i].GetBinauralFiltersAmbisonicOrder();
    key.filter_profile = audio_elements_[i].GetBinauralFilterProfile();
    groups_map[key].push_back(i);
  }

  // Create ProcessingGroup objects for each unique key.
  for (const auto& [key, indices] : groups_map) {
    processing_groups_.emplace_back(key, indices, buffer_size_per_channel_,
                                    sampling_rate_);
  }
}

void ObrImpl::Process(const AudioBuffer& input_buffer,
                      AudioBuffer* output_buffer) {
  ABSL_CHECK_EQ(input_buffer.num_frames(), buffer_size_per_channel_);
  ABSL_CHECK_NE(output_buffer, nullptr);
  ABSL_CHECK_EQ(output_buffer->num_channels(), GetNumberOfOutputChannels());
  ABSL_CHECK_EQ(output_buffer->num_frames(), buffer_size_per_channel_);

  // Acquire the lock for the entire processing duration.
  absl::MutexLock lock(&mutex_);

  // Clear the output buffer.
  output_buffer->Clear();

  // Check if the input channel count matches the expected count.
  // This can mismatch during dynamic reconfiguration (adding/removing
  // elements).
  const size_t expected_channels = GetNumberOfInputChannelsLocked();
  if (input_buffer.num_channels() != expected_channels) {
    // During reconfiguration, the input buffer may have been prepared with
    // a stale channel count. Handle this gracefully by outputting silence.
    ABSL_LOG(WARNING) << "Channel count mismatch during processing: input has "
                      << input_buffer.num_channels() << " channels, expected "
                      << expected_channels << ". Outputting silence.";
    // Output buffer already cleared, so we output silence.
    return;
  }

  // Process passthrough elements first (kPassthroughMono,
  // kPassthroughStereo). These bypass binaural processing and head tracking.
  for (const auto& audio_element : audio_elements_) {
    if (IsPassthroughType(audio_element.GetType())) {
      const size_t first_ch = audio_element.GetFirstChannelIndex();
      if (audio_element.GetType() == AudioElementType::kPassthroughMono) {
        // kPassthroughMono: 1 channel -> copy to both L and R.
        const auto& input_ch = input_buffer[first_ch];
        auto& output_l = (*output_buffer)[0];
        auto& output_r = (*output_buffer)[1];
        for (size_t i = 0; i < buffer_size_per_channel_; ++i) {
          output_l[i] += input_ch[i];
          output_r[i] += input_ch[i];
        }
      } else if (audio_element.GetType() ==
                 AudioElementType::kPassthroughStereo) {
        // kPassthroughStereo: 2 channels -> direct L/R passthrough.
        const auto& input_l = input_buffer[first_ch];
        const auto& input_r = input_buffer[first_ch + 1];
        auto& output_l = (*output_buffer)[0];
        auto& output_r = (*output_buffer)[1];
        for (size_t i = 0; i < buffer_size_per_channel_; ++i) {
          output_l[i] += input_l[i];
          output_r[i] += input_r[i];
        }
      }
    }
  }

  // Skip binaural processing if no processing groups exist.
  // (May still have passthrough elements processed above.)
  if (processing_groups_.empty()) {
    // Apply peak limiter even for passthrough-only configurations.
    if (limiter_enabled_) {
      peak_limiter_->Process(*output_buffer, output_buffer);
    }
    return;
  }

  // Create a temporary buffer for each group's output.
  AudioBuffer group_output(kNumBinauralChannels, buffer_size_per_channel_);

  // Process each processing group.
  for (auto& group : processing_groups_) {
    group_output.Clear();
    group.Process(input_buffer, audio_elements_, head_tracking_enabled_,
                  world_rotation_, &group_output);

    // Add this group's binaural output to the main output buffer.
    (*output_buffer)[0] += group_output[0];
    (*output_buffer)[1] += group_output[1];
  }

  // Peak limit the output if enabled.
  if (limiter_enabled_) {
    peak_limiter_->Process(*output_buffer, output_buffer);
  }
}

int ObrImpl::GetBufferSizePerChannel() const {
  return buffer_size_per_channel_;
}

int ObrImpl::GetSamplingRate() const { return sampling_rate_; }

size_t ObrImpl::GetNumberOfOutputChannels() { return kNumBinauralChannels; }

absl::Status ObrImpl::UpdateAllAmbisonicEncoders() {
  for (auto& group : processing_groups_) {
    RETURN_IF_NOT_OK(group.UpdateAmbisonicEncoder(audio_elements_));
  }
  return absl::OkStatus();
}

size_t ObrImpl::GetNumberOfInputChannelsLocked() const {
  size_t number_of_input_channels = 0;
  for (const auto& audio_element : audio_elements_) {
    number_of_input_channels += audio_element.GetNumberOfInputChannels();
  }
  return number_of_input_channels;
}

size_t ObrImpl::GetNumberOfInputChannels() {
  absl::MutexLock lock(&mutex_);
  return GetNumberOfInputChannelsLocked();
}

size_t ObrImpl::GetNumberOfAudioElements() {
  absl::MutexLock lock(&mutex_);
  return audio_elements_.size();
}

absl::Status ObrImpl::AddAudioElement(const AudioElementType type,
                                      BinauralFilterProfile filter_profile) {
  // Create an audio element configuration with filter type.
  auto audio_element_config = AudioElementConfig(type, filter_profile);

  // Acquire mutex to safely access and modify audio_elements_.
  {
    absl::MutexLock lock(&mutex_);

    if (!audio_elements_.empty()) {
      // If there are already audio elements, alter the first channel in the new
      // Audio Element config to shift the new element down on the input list.
      audio_element_config.SetFirstChannelIndex(
          audio_elements_.back().GetFirstChannelIndex() +
          audio_elements_.back().GetNumberOfInputChannels());
    }

    // Calculate the new number of input channels.
    size_t current_input_channels = 0;
    for (const auto& audio_element : audio_elements_) {
      current_input_channels += audio_element.GetNumberOfInputChannels();
    }

    // Check if there is enough input channels.
    if (current_input_channels +
            audio_element_config.GetNumberOfInputChannels() >
        kMaxSupportedNumInputChannels) {
      ABSL_LOG(ERROR) << "More input channels requested than supported ("
                      << kMaxSupportedNumInputChannels << ").";
      return absl::ResourceExhaustedError(
          "More input channels requested than supported.");
    }

    // All checks passed, add the Audio Element config.
    audio_elements_.push_back(audio_element_config);
    ABSL_LOG(INFO) << "Added audio element: "
                   << GetAudioElementTypeStr(audio_elements_.back().GetType())
                   << ".";
  }
  // Mutex is released here when lock goes out of scope.

  // Initialize DSP with the new configuration.
  // InitializeDsp() will acquire the mutex internally.
  RETURN_IF_NOT_OK(InitializeDsp());

  return absl::OkStatus();
}

absl::Status ObrImpl::RemoveLastAudioElement() {
  // Acquire mutex to safely access and modify audio_elements_.
  {
    absl::MutexLock lock(&mutex_);

    if (audio_elements_.empty()) {
      ABSL_LOG(INFO) << "No audio elements to remove.";
      return absl::FailedPreconditionError("No audio elements to remove.");
    }

    ABSL_LOG(INFO) << "Removing audio element: "
                   << GetAudioElementTypeStr(audio_elements_.back().GetType())
                   << ".";
    audio_elements_.pop_back();

    if (audio_elements_.empty()) {
      ABSL_LOG(INFO) << "No audio elements left.";
      // Release mutex before calling ResetDsp() which acquires it internally.
    } else {
      // Release mutex before calling InitializeDsp() which acquires it
      // internally.
    }
  }
  // Mutex is released here when lock goes out of scope.

  if (audio_elements_.empty()) {
    // Reset the DSP when no audio elements remain.
    return ResetDsp();
  }

  RETURN_IF_NOT_OK(InitializeDsp());

  return absl::OkStatus();
}

absl::Status ObrImpl::UpdateObjectPosition(size_t audio_element_index,
                                           float azimuth, float elevation,
                                           float distance) {
  // Acquire mutex to safely access and modify audio_elements_ and
  // processing_groups_.
  absl::MutexLock lock(&mutex_);

  // Check if the audio element index is valid.
  if (audio_element_index >= audio_elements_.size()) {
    ABSL_LOG(ERROR) << "Invalid audio element index.";
    return absl::InvalidArgumentError("Invalid audio element index.");
  }

  if (audio_elements_[audio_element_index].GetObjectChannels().empty()) {
    ABSL_LOG(ERROR) << "No objects in the audio element.";
    return absl::FailedPreconditionError("No objects in the audio element.");
  }

  // Unwrap azimuth and elevation angles to the range of (-180, 180] and [-90,
  // 90].
  UnwrapAzimuthElevation(azimuth, elevation);

  // Get object channels from the element. For now if there are multiple input
  // channels in an object, configure them to use the same coordinates.
  for (auto& object_ch :
       audio_elements_[audio_element_index].GetObjectChannels()) {
    // Update the object position.
    object_ch.SetAzimuth(azimuth);
    object_ch.SetElevation(elevation);
    object_ch.SetDistance(distance);
  }

  RETURN_IF_NOT_OK(UpdateAllAmbisonicEncoders());

  return absl::OkStatus();
}

absl::Status ObrImpl::UpdateObjectChannelPosition(size_t audio_element_index,
                                                  size_t channel_index,
                                                  float azimuth,
                                                  float elevation,
                                                  float distance) {
  // Acquire mutex to safely access and modify audio_elements_ and
  // processing_groups_.
  absl::MutexLock lock(&mutex_);

  // Check if the audio element index is valid.
  if (audio_element_index >= audio_elements_.size()) {
    ABSL_LOG(ERROR) << "Invalid audio element index.";
    return absl::InvalidArgumentError("Invalid audio element index.");
  }

  auto& object_channels =
      audio_elements_[audio_element_index].GetObjectChannels();

  if (object_channels.empty()) {
    ABSL_LOG(ERROR) << "No objects in the audio element.";
    return absl::FailedPreconditionError("No objects in the audio element.");
  }

  // Check if the channel index is valid.
  if (channel_index >= object_channels.size()) {
    ABSL_LOG(ERROR) << "Invalid channel index: " << channel_index
                    << ". Object has " << object_channels.size()
                    << " channel(s).";
    return absl::InvalidArgumentError("Invalid channel index.");
  }

  // Unwrap azimuth and elevation angles to the range of (-180, 180] and [-90,
  // 90].
  UnwrapAzimuthElevation(azimuth, elevation);

  // Update the specific channel's position.
  object_channels[channel_index].SetAzimuth(azimuth);
  object_channels[channel_index].SetElevation(elevation);
  object_channels[channel_index].SetDistance(distance);

  RETURN_IF_NOT_OK(UpdateAllAmbisonicEncoders());

  return absl::OkStatus();
}

void ObrImpl::EnableHeadTracking(bool enable_head_tracking) {
  head_tracking_enabled_ = enable_head_tracking;
}

void ObrImpl::EnableLimiter(bool enable_limiter) {
  limiter_enabled_ = enable_limiter;
}

absl::Status ObrImpl::SetHeadRotation(float w, float x, float y, float z) {
  // Apply counter-rotation for head tracking.
  world_rotation_ = WorldRotation(w, -x, -y, -z);

  return absl::OkStatus();
}

absl::Status ObrImpl::SetElementHeadLocked(size_t audio_element_index,
                                           bool head_locked) {
  // Acquire mutex to safely access and modify audio_elements_.
  absl::MutexLock lock(&mutex_);

  // Validate audio element index.
  if (audio_element_index >= audio_elements_.size()) {
    ABSL_LOG(ERROR) << "Invalid audio element index: " << audio_element_index
                    << ". Valid range: 0-" << (audio_elements_.size() - 1);
    return absl::InvalidArgumentError("Invalid audio element index.");
  }

  // For passthrough elements, this is a no-op (they bypass binaural
  // processing).
  if (IsPassthroughType(audio_elements_[audio_element_index].GetType())) {
    ABSL_LOG(WARNING) << "SetElementHeadLocked called on passthrough element "
                      << audio_element_index << ". Passthrough elements "
                      << "bypass binaural processing; head-locked setting "
                      << "has no effect.";
    // Return success - this is a valid no-op for API flexibility.
    return absl::OkStatus();
  }

  // Update the head-locked setting.
  audio_elements_[audio_element_index].SetHeadLocked(head_locked);

  ABSL_LOG(INFO) << "Audio element " << audio_element_index
                 << " head-locked rendering: "
                 << (head_locked ? "enabled" : "disabled");

  return absl::OkStatus();
}

std::string ObrImpl::GetAudioElementConfigLogMessage() {
  absl::MutexLock lock(&mutex_);
  return AudioElementTableFormatter::FormatTable(audio_elements_);
}

}  // namespace obr
