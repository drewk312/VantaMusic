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

#ifndef __PROCESSING_BLOCK_H__
#define __PROCESSING_BLOCK_H__

#include "oar_config.h"

typedef struct ProcessingBlock {
  uint64_t first_sample;
  uint64_t last_sample;

  uint64_t start_sample;
  uint64_t end_sample;

  int (*process)(struct ProcessingBlock *self, uint64_t start, float *in,
                 int num_samples, int channels, float *out);
  void (*destroy)(struct ProcessingBlock *self);
} processing_block_t;

processing_block_t *fixed_gains_create(uint64_t start_sample,
                                       uint64_t end_sample, auto_float_t *gain,
                                       int num);
processing_block_t *interp_gains_create(uint64_t start_sample,
                                        uint64_t end_sample,
                                        auto_float_t *gains_start,
                                        auto_float_t *gains_end, int num);

#endif
