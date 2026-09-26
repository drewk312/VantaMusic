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

#include "gain_calculator.h"

#include <stdlib.h>

#include "custom_gain_calculator.h"
#include "layout.h"
#include "numc.h"
#include "utils.h"

static int _layout_gain_calculator_calculate_block_gains(
    layout_gain_calculator_t *self, metadata_block_t *block,
    auto_float_t *gains, int n);
static int _layout_gain_calculator_get_gains_count(
    layout_gain_calculator_t *self);

layout_gain_calculator_t *layout_gain_calculator_create(
    gain_calculator_type_t type, olr_layout_t layout) {
  const speaker_layout_t *speaker_layout = get_layout(layout);
  layout_gain_calculator_t *self = 0;
#ifdef __dbg__
  trace("calculator type %d, speaker layout: %p(%d)", type, speaker_layout,
        layout);
#endif

  if (!speaker_layout) return 0;

  if (type == ck_gain_calculator_custom) {
    self = (layout_gain_calculator_t *)custom_gain_calculator_create(
        speaker_layout);
  }

  if (self) {
    if (!self->layout) self->layout = speaker_layout;
    if (!self->calculate_block_gains)
      self->calculate_block_gains =
          _layout_gain_calculator_calculate_block_gains;
    if (!self->get_gains_count)
      self->get_gains_count = _layout_gain_calculator_get_gains_count;
  }
  return self;
}
void layout_gain_calculator_destroy(layout_gain_calculator_t *self) {
  if (!self) return;
  if (self->base.destroy) self->base.destroy(def_gain_calculator(self));
}

int layout_gain_calculator_calculate_gains(layout_gain_calculator_t *self,
                                           auto_float_t azimuth,
                                           auto_float_t elevation,
                                           auto_float_t distance,
                                           auto_float_t *gains, int n) {
  if (!self || !self->base.calculate_gains) return -22;
  return self->base.calculate_gains(def_gain_calculator(self), azimuth,
                                    elevation, distance, gains, n);
}

int _layout_gain_calculator_calculate_block_gains(
    layout_gain_calculator_t *self, metadata_block_t *block,
    auto_float_t *gains, int n) {
  return layout_gain_calculator_calculate_gains(
      self, block->azimuth, block->elevation, block->distance, gains, n);
}

int _layout_gain_calculator_get_gains_count(layout_gain_calculator_t *self) {
  if (self->layout) return get_speaker_count(self->layout);
  return 0;
}
