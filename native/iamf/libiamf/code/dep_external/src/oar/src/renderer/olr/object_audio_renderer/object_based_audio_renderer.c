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

#include "object_based_audio_renderer.h"

#include <math.h>
#include <stdlib.h>

#include "gain_calculator/gain_calculator.h"
#include "layout.h"
#include "renderer.h"
#include "utils.h"

struct ObjectAudioRenderer {
  layout_gain_calculator_t *calculator;
  renderer_impl_t *impl;
};

object_audio_renderer_t *object_audio_renderer_create(
    olr_layout_t layout, gain_calculator_type_t type, int sample_rate) {
  object_audio_renderer_t *renderer = 0;
  layout_gain_calculator_t *calculator =
      layout_gain_calculator_create(type, layout);
  renderer_impl_t *impl = 0;

  renderer = calloc(1, sizeof(object_audio_renderer_t));
  if (!renderer) {
    if (calculator) layout_gain_calculator_destroy(calculator);
    return 0;
  }
  renderer->calculator = calculator;

  impl = renderer_impl_create(calculator, sample_rate);
  renderer->impl = impl;

  return renderer;
}

void object_audio_renderer_destroy(object_audio_renderer_t *renderer) {
  if (!renderer) return;
  if (renderer->calculator)
    layout_gain_calculator_destroy(renderer->calculator);
  if (renderer->impl) renderer_impl_destroy(renderer->impl);
  free(renderer);
}

int object_audio_renderer_add_metadatas(object_audio_renderer_t *renderer,
                                        metadata_block_t *metadata_blocks,
                                        int n) {
  return renderer_impl_add_metadatas(renderer->impl, metadata_blocks, n);
}

int object_audio_renderer_render(object_audio_renderer_t *renderer,
                                 float *input, int samples, int channels,
                                 uint64_t offset, float *output) {
  return renderer_impl_render(renderer->impl, input, samples, channels, offset,
                              output);
}
