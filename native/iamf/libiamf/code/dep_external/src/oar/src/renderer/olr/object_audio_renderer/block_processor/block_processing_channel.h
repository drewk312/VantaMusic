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

#ifndef __BLOCK_PROCESSING_CHANNEL_H__
#define __BLOCK_PROCESSING_CHANNEL_H__

#include "gain_calculator/gain_calculator.h"
#include "object_based_audio_renderer.h"

typedef struct BlockProcessingChannel block_processing_channel_t;

block_processing_channel_t* block_processing_channel_create(
    layout_gain_calculator_t* gain_calculator);
int block_processing_channel_add_metadata(block_processing_channel_t* self,
                                          metadata_block_t* metadata);
int block_processing_channel_process(block_processing_channel_t* self,
                                     int sample_rate, uint64_t offset,
                                     float* input, int num_samples,
                                     int channels, float* output);
void block_processing_channel_destroy(block_processing_channel_t* self);
#endif  // __BLOCK_PROCESSING_CHANNEL_H__