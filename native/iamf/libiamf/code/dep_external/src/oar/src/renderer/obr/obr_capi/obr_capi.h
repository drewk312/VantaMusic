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
 * @file obr_capi.h
 * @brief obr CAPIs.
 * @version 2.0.0
 * @date Created 2025-08-04
 **/

#ifndef __OBR_CAPI_H__
#define __OBR_CAPI_H__

#include <stdint.h>

#ifdef __cplusplus
extern "C" {
#endif

typedef void *obr_handle;

typedef enum ObrAudioElementType {
  kObrInvalidType = 0,

  kObrAmbisonicsGroupBegin = 100,
  kObr1OA = 101,
  kObr2OA = 102,
  kObr3OA = 103,
  kObr4OA = 104,
  kObrAmbisonicsGroupEnd = 105,

  kObrLoudspeakerLayoutsGroupBegin = 200,
  kObrLayoutMono = 201,
  kObrLayoutStereo = 202,
  kObrLayout5_1_0_ch = 203,
  kObrLayout5_1_2_ch = 204,
  kObrLayout5_1_4_ch = 205,
  kObrLayout7_1_0_ch = 206,
  kObrLayout7_1_2_ch = 207,
  kObrLayout7_1_4_ch = 208,
  kObrLayout3_1_2_ch = 209,
  kObrLayout9_1_6_ch = 210,
  kObrLayout9_1_6_ch_alt = 211,
  kObrLayout7_1_5_4_ch = 212,
  kObrLayout10_2_9_3_ch = 213,
  kObrSubsetLFE = 214,
  kObrSubsetStereo_S = 215,
  kObrSubsetStereo_SS = 216,
  kObrSubsetStereo_RS = 217,
  kObrSubsetStereo_TF = 218,
  kObrSubsetStereo_TB = 219,
  kObrSubsetTop_4ch = 220,
  kObrSubset3_0ch = 221,
  kObrSubsetStereo_F = 222,
  kObrSubsetStereo_Si = 223,
  kObrSubsetStereo_TpSi = 224,
  kObrSubsetTop_6ch = 225,
  kObrSubsetLFE_Pair = 226,
  kObrSubsetBottom_3ch = 227,
  kObrSubsetBottom_4ch = 228,
  kObrSubsetTop_1ch = 229,
  kObrSubsetTop_5ch = 230,
  kObrLoudspeakerLayoutsGroupEnd = 231,

  kObrObjectsGroupBegin = 300,
  kObrObjectMono = 301,
  kObrObjectDual = 302,
  kObrObjectsGroupEnd = 303,

  kObrPassthroughGroupBegin = 400,
  kObrPassthroughMono = 401,
  kObrPassthroughStereo = 402,
  kObrPassthroughGroupEnd = 403,
} ObrAudioElementType;

typedef enum ObrBinauralFilterProfile {
  kObrDirect = 0,
  kObrAmbient = 1,
  kObrReverberant = 2,
} ObrBinauralFilterProfile;

/**
 * @brief     Create open binaural renderer handle
 * @param     [in] buffer_size_per_channel :  Buffer size per channel in samples
 * @param     [in] sampling_rate : Sampling rate in Hz.
 * @return    return open binaural renderer handle, or NULL on failure.
 */
obr_handle obr_create(int buffer_size_per_channel, int sampling_rate);

/**
 * @brief     Add an audio element to the OBR instance.
 * @param     [in] handle : obr handle.
 * @param     [in] type : audio element type.
 * @param     [in] profile : binaural filter profile for spatial audio
 * rendering. Available options:
 *            - kObrDirect: Direct binaural rendering with minimal processing
 *            - kObrAmbient: Ambient rendering with enhanced spatial diffusion
 *            - kObrReverberant: Reverberant rendering with added room
 * simulation
 * @return    0 on success, negative value on error.
 */
int obr_add_audio_element(obr_handle handle, ObrAudioElementType type,
                          ObrBinauralFilterProfile profile);

/**
 * @brief     Processes planar audio data.
 * @param     [in] handle : obr handle.
 * @param     [in] input : Input buffer with planar audio data.
 * @param     [in] output : Output buffer with planar audio data.
 * @param     [in] num_samples : The number of samples for input buffer
 * @return    The number of samples for output buffer
 */
int obr_process(obr_handle handle, const float *input, float *output,
                int num_samples);

/**
 * @brief     Returns the buffer size per channel.
 * @param     [in] handle : obr handle.
 * @return    Buffer size per channel.
 */
int obr_get_buffer_size_per_channel(obr_handle handle);

/**
 * @brief     Returns the sampling rate.
 * @param     [in] handle : obr handle.
 * @return    Sampling rate.
 */
int obr_get_sampling_rate(obr_handle handle);

/**
 * @brief     Returns the number of input channels.
 * @param     [in] handle : obr handle.
 * @return    Number of input channels.
 */
int obr_get_number_of_input_channels(obr_handle handle);

/**
 * @brief     Returns the number of output channels.
 * @param     [in] handle : obr handle.
 * @return    Number of output channels.
 */
int obr_get_number_of_output_channels(obr_handle handle);

/**
 * @brief     Returns the number of audio elements.
 * @param     [in] handle : obr handle.
 * @return    Number of audio elements.
 */
int obr_get_number_of_audio_elements(obr_handle handle);

/**
 * @brief     Sets the position of an audio object.
 * @param     [in] handle : obr handle.
 * @param     [in] index : Index of the audio object.
 * @param     [in] azimuth : Azimuth in degrees.
 * @param     [in] elevation : Elevation in degrees.
 * @param     [in] distance : Distance in meters.
 * @return    @0: success,@others: fail
 */
int obr_update_object_position(obr_handle handle, uint32_t index, float azimuth,
                               float elevation, float distance);

/**
 * @brief     Enables or disables head tracking.
 * @param     [in] handle : obr handle.
 * @param     [in] EnableHeadTracking : True to enable head tracking, false to
 *            disable.
 */
void obr_enable_head_tracking(obr_handle handle, int EnableHeadTracking);

/**
 * @brief     Sets the head rotation. The head rotation expressed using
 * quaternions is used to counter-rotate the intermediate Ambisonic bed in order
 * to produce stable sound sources in binaural reproduction.
 * @param     [in] handle : obr handle.
 * @param     [in] w
 * @param     [in] x
 * @param     [in] y
 * @param     [in] z
 * @return    @0: success,@others: fail
 */
int obr_set_head_rotation(obr_handle handle, float w, float x, float y,
                          float z);

/**
 * @brief     Enables or disables the output peak limiter.
 * @param     [in] handle : obr handle.
 * @param     [in] enable_limiter : True to enable the limiter, false to
 * disable.
 */
void obr_enable_limiter(obr_handle handle, int enable_limiter);

/**
 * @brief     Sets the head-locked rendering mode for a specific audio element.
 * @param     [in] handle : obr handle.
 * @param     [in] audio_element_index : Index of the audio element (0-based).
 * @param     [in] head_locked : True to enable head-locked rendering, false to
 * enable world-locked rendering.
 * @return    @0: success,@others: fail
 */
int obr_set_element_head_locked(obr_handle handle, uint32_t audio_element_index,
                                int head_locked);

/**
 * @brief     Sets the position of a specific channel within an audio object.
 * @param     [in] handle : obr handle.
 * @param     [in] audio_element_index : Index of the audio element containing
 * the object.
 * @param     [in] channel_index : Index of the channel within the object
 * (0-based).
 * @param     [in] azimuth : Azimuth in degrees.
 * @param     [in] elevation : Elevation in degrees.
 * @param     [in] distance : Normalized distance in 0 to 1 range.
 * @return    @0: success,@others: fail
 */
int obr_update_object_channel_position(obr_handle handle,
                                       uint32_t audio_element_index,
                                       uint32_t channel_index, float azimuth,
                                       float elevation, float distance);

/**
 * @brief     Destroy the obr handle.
 * @param     [in] handle : obr handle.
 * @return    @0: success,@others: fail
 */
int obr_destroy(obr_handle handle);

#ifdef __cplusplus
}
#endif  // __cplusplus
#endif  // __OBR_CAPI_H__
