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

#ifndef __LAYOUT_H__
#define __LAYOUT_H__

#include "cvalue.h"
#include "object_based_audio_renderer.h"

#define def_max_speakers 24

typedef struct SpeakerPosition {
  const char *name;
  auto_float_t azimuth;
  auto_float_t elevation;
  int is_lfe;
} speaker_position_t;

typedef struct SpeakerLayout {
  const char *name;
  olr_layout_t layout;
  int num_speakers;
  speaker_position_t *speakers[def_max_speakers];
} speaker_layout_t;

int get_speaker_count(const speaker_layout_t *layout);
int is_lfe(speaker_position_t *sp);
const speaker_layout_t *get_layout_without_lfe(olr_layout_t layout);
int get_cartesian_positions(speaker_layout_t *layout,
                            auto_float_t positions[][3], int n);
const speaker_layout_t *get_layout(olr_layout_t layout);

#endif  // __LAYOUT_H__