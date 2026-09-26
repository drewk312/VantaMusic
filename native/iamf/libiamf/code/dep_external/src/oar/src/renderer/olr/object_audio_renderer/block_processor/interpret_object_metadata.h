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

#ifndef __INTERPRET_OBJECT_METADATA_H__
#define __INTERPRET_OBJECT_METADATA_H__

#include "gain_calculator/gain_calculator.h"
#include "processing_block.h"

typedef struct InterpretObjectMetadata interpret_object_metadata_t;

interpret_object_metadata_t *interpret_object_metadata_create(
    layout_gain_calculator_t *gain_calculator);
processing_block_t *interpret_object_metadata_process(
    interpret_object_metadata_t *self, int sample_rate,
    metadata_block_t *block);
void interpret_object_metadata_destroy(interpret_object_metadata_t *self);
#endif