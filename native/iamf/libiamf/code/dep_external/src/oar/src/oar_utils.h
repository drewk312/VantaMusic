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

#ifndef __OAR_UTILS_H__
#define __OAR_UTILS_H__

#include <math.h>
#include <stdint.h>

#include "oar.h"
#include "oar_metadata.h"
#include "renderer_library_api.h"

ri_id_t audio_element_config_to_ri_id(const oar_audio_element_config_t *config);
ri_id_t layout_to_ri_id(oar_layout_t layout);
ro_id_t layout_to_ro_id(oar_layout_t layout);
rid_t hoa_to_rid(oar_hoa_t order);
ri_id_t object_to_rid(int num_objects);
uint32_t layout_channels_count(oar_layout_t layout);
uint32_t rid_channels_count(rid_t rid);

cartesian_t normalized_polar_to_cartesian_float32(float azimuth,
                                                  float elevation);
cartesian_t polar_to_cartesian_float32(polar_t polar);
polar_t cartesian_to_polar_float32(cartesian_t cartesian);
float cartesian_calculate_angle_radians_float32(cartesian_t p1, cartesian_t p2);
polar_t cartesian_to_polar_sector_float32(cartesian_t cartesian);

// Angle conversion functions
#define def_degrees_to_radians(degrees) ((degrees) * M_PI / 180.0f)
#define def_radians_to_degrees(radians) ((radians) * 180.0f / M_PI)

// Metadata utility functions
oar_metadata_t *metadata_clone(const oar_metadata_t *metadata);
void metadata_delete(oar_metadata_t *metadata);
int metadata_gain_linear(oar_metadata_t *metadata);

float db_to_linear_float32(float db);

#ifdef __as_dbg__
typedef enum EWavTag {
  ck_tag_original,
  ck_tag_rendered,
  ck_tag_mixed,
} wav_tag_t;
void *wav_writer_open(wav_tag_t tag, int id, uint32_t sample_rate,
                      uint32_t channel_count);
void wav_writer_close(void *wav);
int wav_writer_write(void *wav, float *data, uint32_t sampels_per_channel,
                     uint32_t channels);
#endif

#endif  // __OAR_UTILS_H__
