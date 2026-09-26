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

#ifndef __OBJECT_BASED_AUDIO_RENDERER_H__
#define __OBJECT_BASED_AUDIO_RENDERER_H__

#include <stdint.h>

#include "oar_config.h"

typedef struct ObjectAudioRenderer object_audio_renderer_t;

typedef enum EGainCalculatorType {
  ck_gain_calculator_custom,
} gain_calculator_type_t;

typedef enum EOlrLayout {
  ck_olr_layout_none = -1,
  ck_olr_layout_200,
  ck_olr_layout_510,
  ck_olr_layout_512,
  ck_olr_layout_514,
  ck_olr_layout_710,
  ck_olr_layout_712,
  ck_olr_layout_714,
  ck_olr_layout_312,
  ck_olr_layout_916,
  ck_olr_layout_7154,
  ck_olr_layout_a293,

  ck_olr_layout_sound_system_e,
  ck_olr_layout_sound_system_f,
  ck_olr_layout_sound_system_g,

  ck_olr_layout_100,

  ck_olr_layout_sound_system_a = ck_olr_layout_200,
  ck_olr_layout_sound_system_b = ck_olr_layout_510,
  ck_olr_layout_sound_system_c = ck_olr_layout_512,
  ck_olr_layout_sound_system_d = ck_olr_layout_514,
  ck_olr_layout_sound_system_h = ck_olr_layout_a293,
  ck_olr_layout_sound_system_i = ck_olr_layout_710,
  ck_olr_layout_sound_system_j = ck_olr_layout_712,
} olr_layout_t;

typedef enum EInterpolation {
  ck_interpolation_none = -1,
  ck_interpolation_linear,
  ck_interpolation_step
} interpolation_t;

typedef struct MetadataBlock {
  uint32_t track_id;

  uint64_t start;
  uint64_t duration;

  auto_float_t azimuth;
  auto_float_t elevation;
  auto_float_t distance;
} metadata_block_t;

/**
 * @brief Create an object audio renderer instance.
 *
 * @param layout @ref olr_layout_t
 * @param type @ref gain_calculator_type_t
 * @param sample_rate sample rate of the input signal.
 * @return object_audio_renderer_t*
 */
object_audio_renderer_t *object_audio_renderer_create(
    olr_layout_t layout, gain_calculator_type_t type, int sample_rate);

/**
 * @brief Destroy the object audio renderer instance.
 *
 * @param renderer
 */
void object_audio_renderer_destroy(object_audio_renderer_t *renderer);

/**
 * @brief Set the metadata blocks.
 *
 * @param renderer The object audio renderer instance.
 * @param metadata_blocks The metadata blocks to set.
 * @param n The number of metadata blocks.
 * @return int
 */
int object_audio_renderer_add_metadatas(object_audio_renderer_t *renderer,
                                        metadata_block_t *metadata_blocks,
                                        int n);

/**
 * @brief Render the input signal.
 *
 * @param renderer The object audio renderer instance.
 * @param input The input audio signal that should be in planar format.
 * @param samples The number of samples per channel in the input signal.
 * @param channels The number of channels in the input signal.
 * @param offset The offset of the input signal. This is used for rendering a
 * part of the input signal.
 * @param output The output audio signal that will be rendered.
 * @return int Returns 0 on success and -1 on failure.
 */
int object_audio_renderer_render(object_audio_renderer_t *renderer,
                                 float *input, int samples, int channels,
                                 uint64_t offset, float *output);

#endif  // __OBJECT_BASED_AUDIO_RENDERER_H__
