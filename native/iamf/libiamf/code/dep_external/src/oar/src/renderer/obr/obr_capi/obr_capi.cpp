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

/**
 * @file obr_capi.cpp
 * @brief obr CAPIs.
 * @version 2.0.0
 * @date Created 2025-08-04
 **/

#include "obr_capi.h"

#include <cstddef>
#include <cstdint>
#include <cstdio>
#include <string>
#include <vector>

#include "obr/renderer/obr_impl.h"
using namespace obr;

struct CObrApiPriv {
  std::unique_ptr<ObrImpl> obr;
};

inline ObrImpl *cast_(obr_handle op) {
  auto priv = static_cast<CObrApiPriv *>(op);
  return priv ? priv->obr.get() : nullptr;
}

CObrApiPriv *ObrApiPrivCreate() {
  CObrApiPriv *instance = new CObrApiPriv();
  return instance;
}

void ObrApiPrivDestroy(CObrApiPriv *&instance) {
  if (instance) delete instance;
  instance = nullptr;
}

obr_handle obr_create(int buffer_size_per_channel, int sampling_rate) {
  if (!buffer_size_per_channel || !sampling_rate) return NULL;

  obr_handle handle = static_cast<obr_handle>(ObrApiPrivCreate());
  auto priv = static_cast<CObrApiPriv *>(handle);

  priv->obr = std::make_unique<ObrImpl>(buffer_size_per_channel, sampling_rate);
  if (!priv->obr) {
    ObrApiPrivDestroy(priv);
    return NULL;
  }

  return handle;
}

int obr_add_audio_element(obr_handle handle, ObrAudioElementType type,
                          ObrBinauralFilterProfile filter_profile) {
  if (!handle) return -1;  // Error: invalid handle

  auto priv = static_cast<CObrApiPriv *>(handle);
  if (!priv->obr) return -2;  // Error: OBR instance not created

  AudioElementType input_type = static_cast<AudioElementType>(type);
  if (input_type == AudioElementType::kInvalidType)
    return -3;  // Error: invalid audio element type

  auto status = cast_(handle)->AddAudioElement(
      input_type, static_cast<BinauralFilterProfile>(filter_profile));
  if (!status.ok())
    return -4;  // Error: failed to add audio element from ObrImpl

  return 0;  // Success
}

int obr_process(obr_handle handle, const float *input, float *output,
                int num_samples) {
  int input_nch = 0;
  int output_nch = 2;
  size_t buffer_size = num_samples;

  if (!handle || !input || !output) return 0;

  input_nch = (int)cast_(handle)->GetNumberOfInputChannels();

  std::vector<std::vector<float>> input_buffer_float(
      input_nch, std::vector<float>(buffer_size));

  for (size_t ch = 0; ch < input_nch; ch++)
    for (size_t smp = 0; smp < buffer_size; smp++)
      input_buffer_float[ch][smp] = input[ch * buffer_size + smp];

  AudioBuffer input_buffer(input_nch, buffer_size);
  input_buffer = input_buffer_float;
  AudioBuffer output_buffer(output_nch, buffer_size);

  cast_(handle)->Process(input_buffer, &output_buffer);

  for (size_t ch = 0; ch < output_buffer.num_channels(); ch++)
    for (size_t smp = 0; smp < output_buffer.num_frames(); smp++)
      output[ch * output_buffer.num_frames() + smp] = output_buffer[ch][smp];

  return num_samples;
}

int obr_get_buffer_size_per_channel(obr_handle handle) {
  return (int)cast_(handle)->GetBufferSizePerChannel();
}

int obr_get_sampling_rate(obr_handle handle) {
  if (!handle) return -1;
  return (int)cast_(handle)->GetSamplingRate();
}

int obr_get_number_of_input_channels(obr_handle handle) {
  if (!handle) return -1;
  return (int)cast_(handle)->GetNumberOfInputChannels();
}

int obr_get_number_of_output_channels(obr_handle handle) {
  if (!handle) return -1;
  return (int)cast_(handle)->GetNumberOfOutputChannels();
}

int obr_get_number_of_audio_elements(obr_handle handle) {
  if (!handle) return -1;
  return (int)cast_(handle)->GetNumberOfAudioElements();
}

int obr_update_object_position(obr_handle handle, uint32_t index, float azimuth,
                               float elevation, float distance) {
  if (!handle) return -1;
  auto status =
      cast_(handle)->UpdateObjectPosition(index, azimuth, elevation, distance);
  if (status.ok()) {
    return 0;
  } else
    return -1;
}

void obr_enable_head_tracking(obr_handle handle, int EnableHeadTracking) {
  if (!handle) return;
  cast_(handle)->EnableHeadTracking(EnableHeadTracking);
}

void obr_enable_limiter(obr_handle handle, int enable_limiter) {
  if (!handle) return;
  cast_(handle)->EnableLimiter(enable_limiter);
}

int obr_set_element_head_locked(obr_handle handle, uint32_t audio_element_index,
                                int head_locked) {
  if (!handle) return -1;
  auto status =
      cast_(handle)->SetElementHeadLocked(audio_element_index, head_locked);
  if (status.ok()) {
    return 0;
  } else
    return -1;
}

int obr_update_object_channel_position(obr_handle handle,
                                       uint32_t audio_element_index,
                                       uint32_t channel_index, float azimuth,
                                       float elevation, float distance) {
  if (!handle) return -1;
  auto status = cast_(handle)->UpdateObjectChannelPosition(
      audio_element_index, channel_index, azimuth, elevation, distance);
  if (status.ok()) {
    return 0;
  } else
    return -1;
}

int obr_set_head_rotation(obr_handle handle, float w, float x, float y,
                          float z) {
  if (!handle) return -1;
  auto status = cast_(handle)->SetHeadRotation(w, x, y, z);
  if (status.ok()) {
    return 0;
  } else
    return -1;
}

int obr_destroy(obr_handle handle) {
  if (!handle) return -1;
  auto priv = static_cast<CObrApiPriv *>(handle);
  ObrApiPrivDestroy(priv);
  return 0;
}
