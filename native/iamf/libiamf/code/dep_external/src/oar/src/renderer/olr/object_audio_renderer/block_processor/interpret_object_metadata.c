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

#include "interpret_object_metadata.h"

#include <stdlib.h>

#include "cvalue.h"
#include "cvector.h"
#include "definitions.h"
#include "utils.h"

struct InterpretObjectMetadata {
  layout_gain_calculator_t *gain_calculator;
  uint64_t last_block_end;
  auto_float_t *last_block_gains_from;
  auto_float_t *last_block_gains_to;
};

interpret_object_metadata_t *interpret_object_metadata_create(
    layout_gain_calculator_t *gain_calculator) {
  interpret_object_metadata_t *self =
      def_mallocz(interpret_object_metadata_t, 1);
  if (!self) return 0;

  self->gain_calculator = gain_calculator;
  self->last_block_end = UINT64_MAX;
  return self;
}

processing_block_t *interpret_object_metadata_process(
    interpret_object_metadata_t *self, int sample_rate,
    metadata_block_t *block) {
  uint64_t start_sample, end_sample, interp, target_sample;
  auto_float_t *interp_from = 0, *interp_to = 0;
  int n = 0;

  start_sample = block->start;
  end_sample = block->start + block->duration;
  interp = end_sample - start_sample;
  target_sample = start_sample + interp;
  n = self->gain_calculator->get_gains_count(self->gain_calculator);

#ifdef __dbg__
  trace(
      "start_sample: %llu, end_sample: %llu, interp: %llu, target_sample: %llu",
      start_sample, end_sample, interp, target_sample);
#endif

  if (target_sample > end_sample) target_sample = end_sample;

  interp_to = def_mallocz(auto_float_t, n);
  if (!interp_to) return 0;

  if (self->last_block_end != UINT64_MAX &&
      start_sample == self->last_block_end) {
    interp_from = self->last_block_gains_to;
  } else {
    target_sample = start_sample;
    interp_from = 0;
  }

  self->gain_calculator->calculate_block_gains(self->gain_calculator, block,
                                               interp_to, n);

#ifdef __dbg__
  trace("start_sample: %lu, end_sample: %lu, target_sample: %lu", start_sample,
        end_sample, target_sample);

  if (interp_from) debug_array_float("interp_from", interp_from, n);
  debug_array_float("interp_to", interp_to, n);
#endif

  self->last_block_end = end_sample;
  if (self->last_block_gains_from) free(self->last_block_gains_from);
  self->last_block_gains_from = self->last_block_gains_to;
  self->last_block_gains_to = interp_to;

  if (start_sample != target_sample) {
    return interp_gains_create(start_sample, target_sample, interp_from,
                               interp_to, n);
  } else if (target_sample != end_sample) {
    return fixed_gains_create(target_sample, end_sample, interp_to, n);
  }

  return 0;
}

void interpret_object_metadata_destroy(interpret_object_metadata_t *self) {
  if (self->last_block_gains_from) free(self->last_block_gains_from);
  if (self->last_block_gains_to) free(self->last_block_gains_to);
  free(self);
}
