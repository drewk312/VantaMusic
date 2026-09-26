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

#ifndef __GAIN_CALCULATOR_H__
#define __GAIN_CALCULATOR_H__

#include "carray.h"
#include "layout.h"
#include "object_based_audio_renderer.h"

#define def_none_degree 361.0f

#define def_gain_calculator(a) ((gain_calculator_t *)a)
#define def_gain_calculator_calculate_gains(name)                              \
  ((int (*)(struct GainCalculator *, auto_float_t, auto_float_t, auto_float_t, \
            auto_float_t *, int))(name))
#define def_gain_calculator_destroy(name) \
  ((void (*)(struct GainCalculator *))(name))

typedef struct GainCalculator {
  int (*calculate_gains)(struct GainCalculator *, auto_float_t, auto_float_t,
                         auto_float_t, auto_float_t *, int);
  void (*destroy)(struct GainCalculator *);
} gain_calculator_t;

typedef struct LayoutGainCalculator {
  gain_calculator_t base;
  const speaker_layout_t *layout;

  int (*calculate_block_gains)(struct LayoutGainCalculator *,
                               metadata_block_t *, auto_float_t *, int);
  int (*get_gains_count)(struct LayoutGainCalculator *);
} layout_gain_calculator_t;

layout_gain_calculator_t *layout_gain_calculator_create(
    gain_calculator_type_t type, olr_layout_t layout);
void layout_gain_calculator_destroy(layout_gain_calculator_t *self);
int layout_gain_calculator_calculate_gains(layout_gain_calculator_t *self,
                                           auto_float_t azimuth,
                                           auto_float_t elevation,
                                           auto_float_t distance,
                                           auto_float_t *gains, int n);

#endif  // __GAIN_CALCULATOR_H__