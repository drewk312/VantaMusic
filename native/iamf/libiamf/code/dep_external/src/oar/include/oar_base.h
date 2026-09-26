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

#ifndef __OAR_BASE_H__
#define __OAR_BASE_H__

#include <stdint.h>

/**
 * OAR status codes definition
 */
enum EOarStatus {
  ck_oar_ok,                  ///< Operation succeeded
  ck_oar_error_nomem = -12,   ///< Out of memory error
  ck_oar_error_busy = -16,    ///< Resource busy/unavailable error
  ck_oar_error_inval = -22,   ///< Invalid parameters error
  ck_oar_error_nosys = -38,   ///< Function not implemented error
  ck_oar_error_notsup = -95,  ///< Operation not supported error
};

typedef enum EOarHeadphonesRenderingMode {
  ck_world_locked_restricted = 0,
  ck_world_locked,
  ck_head_locked,
  ck_reserved,
} oar_headphones_rendering_mode_t;

typedef enum EOarBinauralFilterProfile {
  ck_ambient,
  ck_direct,
  ck_reverberant,
  ck_binaural_filter_profile_default = ck_ambient,
} oar_binaural_filter_profile_t;

typedef enum ECoordinateType {
  ck_polar,
  ck_cartesian,
} coordinate_type_t;

typedef struct PolarCoordinate {
  float azimuth, elevation, distance;
} polar_t;

#define def_polar_instance(azimuth, elevation, distance) \
  ((polar_t){(azimuth), (elevation), (distance)})

typedef struct CartesianCoordinate {
  float x, y, z;
} cartesian_t;

#define def_cartesian_instance(x, y, z) ((cartesian_t){(x), (y), (z)})

typedef struct Quaternion {
  float w, x, y, z;
} quaternion_t;

#define def_quaternion_instance(w, x, y, z) ((quaternion_t){(w), (x), (y), (z)})

typedef struct OarAudioBlock {
  float *data;
  uint32_t channels;
  uint32_t samples_per_channel;
} oar_audio_block_t;

/**
 * downmix_info is an instance of the DemixingParamDefinition() class,
 * which provides the parameter definition for the demixing
 * information. Refer to https://aomediacodec.github.io/iamf/#demixing_info.
 */
typedef struct DownmixInfo {
  int mode;
  int weight_index;
} downmix_info_t;

/**
 * headphones_rendering_mode indicates whether the input channel-based
 * Audio Element is rendered to stereo loudspeakers or spatialized with
 * a binaural renderer when played back on headphones. Refer to
 * https://aomediacodec.github.io/iamf/#headphones_rendering_mode.
 *
 * headphones_rendering_mode indicates how the input Audio Element is rendered
 * when played back on headphones. If the playback layout is a loudspeaker
 * layout, parsers SHALL ignore this field.
 */
typedef struct AudioElementRenderingConfig {
  oar_headphones_rendering_mode_t headphones_rendering_mode;
  oar_binaural_filter_profile_t binaural_filter_profile;
} audio_element_rendering_config_t;

typedef struct ParameterSet {
#define def_parameter_set_flag_iamf_downmix_info 0x01
#define def_parameter_set_flag_iamf_element_rendering_config 0x02

  /**flags indicating which of the following structures are valid */
  uint32_t flags;

  downmix_info_t downmix_info;
  audio_element_rendering_config_t element_rendering_config;
} parameter_set_t;
#endif  // __OAR_BASE_H__
