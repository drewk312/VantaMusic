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

#ifndef __OAR_METADATA_H__
#define __OAR_METADATA_H__

#include "animation.h"
#include "oar_base.h"

#define def_max_number_of_objects 2

typedef enum EOarMetadataType {
  ck_metadata_none,
  ck_metadata_gain,
  ck_metadata_iamf_downmix_mode,
  ck_metadata_object_positions,
  ck_metadata_head_rotation,
  ck_metadata_count,
} oar_metadata_type_t;

typedef enum EOarParamType {
  ck_param_constant,
  ck_param_multiple,
  ck_param_animated,
} oar_param_type_t;

/**
 * Gain metadata for audio elements or groups
 * Supports constant, per-sample, and animated gain control
 * Note: All gain values are specified in decibels (dB) as input
 */
typedef struct OarMetadataGain {
  /* Unique identifier for the gain parameter */
  uint32_t id;
  /* Type of gain parameter (constant/multiple/animated) */
  oar_param_type_t param_type;
  union {
    /* Fixed gain value for the entire duration (input in dB) */
    float constant_gain;
    /* Array of gain values, one per sample (input in dB) */
    float *gain_array;
    /* Animated gain with keyframes (input in dB) */
    animated_float32_t animated_gains;
  };
} oar_metadata_gain_t;

/**
 * IAMF downmix mode metadata
 * Controls how audio is downmixed to different speaker configurations
 */
typedef struct OarMetadataIamfDownmixMode {
  /* Downmix mode identifier as specified in IAMF standard */
  int mode;
} oar_metadata_iamf_downmix_mode_t;

/**
 * Object position metadata for object-based audio
 * Defines 3D positions of audio objects using polar or Cartesian coordinates
 * Note: Only supports constant and animated parameters. For animated, only step
 * and linear animation types are supported.
 */
typedef struct OarMetadataPositions {
  /* Type of position parameter (constant/animated) */
  oar_param_type_t param_type;
  /* Coordinate system (polar or Cartesian) */
  coordinate_type_t position_type;
  /* Number of objects (max: def_max_number_of_objects) */
  uint32_t num_objects;
  union {
    /* Static polar coordinates */
    polar_t polar_positions[def_max_number_of_objects];
    /* Static Cartesian coordinates */
    cartesian_t cartesian_positions[def_max_number_of_objects];
    /* Animated polar coordinates */
    animated_polar_t animated_polar_positions[def_max_number_of_objects];
    /* Animated Cartesian coordinates */
    animated_cartesian_t
        animated_cartesian_positions[def_max_number_of_objects];
  };
} oar_metadata_object_positions_t;

/**
 * Unified metadata structure for all OAR metadata types
 * Encapsulates different metadata types in a single structure for API
 * consistency
 */
typedef struct OarMetadata {
  /* Type of metadata (gain/positions/head_rotation/etc.) */
  oar_metadata_type_t type;
  union {
    /* Gain control metadata */
    oar_metadata_gain_t gain;
    /* IAMF downmix metadata */
    oar_metadata_iamf_downmix_mode_t iamf_downmix_mode;
    /* Object position metadata */
    oar_metadata_object_positions_t object_positions;
    /**
     * Head rotation metadata for binaural rendering
     * Represents head orientation as a quaternion (w, x, y, z)
     */
    quaternion_t head_rotation;
  };
  /* Duration in samples for which this metadata is valid */
  int duration;
} oar_metadata_t;

#endif  // __OAR_METADATA_H__
