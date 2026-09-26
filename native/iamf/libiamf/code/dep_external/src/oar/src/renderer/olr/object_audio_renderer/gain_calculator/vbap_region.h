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

#ifndef __VBAP_REGION_H__
#define __VBAP_REGION_H__

#include <carray.h>

#include "gain_calculator.h"

typedef struct VBAPRegion {
  gain_calculator_t base;
  array_t *ref_speaker_indices;
  array_t *speaker_positions;
  auto_float_t *inv_matrix;
} vbap_region_t;

vbap_region_t *vbap_region_create(array_t *speaker_indices,
                                  array_t *speaker_positions);

#endif  // __VBAP_REGION_H__