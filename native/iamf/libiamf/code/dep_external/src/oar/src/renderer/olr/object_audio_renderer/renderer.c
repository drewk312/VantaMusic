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

#include "renderer.h"

#include <math.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#include "block_processor/block_processing_channel.h"
#include "cvector.h"
#include "definitions.h"
#include "layout.h"
#include "utils.h"

def_key_value_type(int, block_processing_channel_t *, processing_channel);

struct RendererImpl {
  olr_layout_t layout;
  layout_gain_calculator_t *gain_calculator;
  int sample_rate;
  int output_channels;  // number of channels in the output buffer
  vector_t *processors;
};

static void __destroy_processing_channel(processing_channel_t *processor);
static processing_channel_t *__find_processing_channel(vector_t *processors,
                                                       int id);

renderer_impl_t *renderer_impl_create(layout_gain_calculator_t *gain_calculator,
                                      int sample_rate) {
  renderer_impl_t *impl = def_mallocz(renderer_impl_t, 1);

  if (!impl) return 0;

  impl->gain_calculator = gain_calculator;
  impl->sample_rate = sample_rate;
  impl->output_channels = gain_calculator->get_gains_count(gain_calculator);

  impl->processors = vector_new();
  if (!impl->processors) {
    error("could not allocate memory for processing channels");
    renderer_impl_destroy(impl);
    impl = 0;
  }
  return impl;
}

void renderer_impl_destroy(renderer_impl_t *impl) {
  if (!impl) return;
  if (impl->processors)
    vector_free(impl->processors,
                def_default_free_ptr(__destroy_processing_channel));
  free(impl);
}

int renderer_impl_add_metadatas(renderer_impl_t *impl,
                                metadata_block_t *metadata_blocks, int n) {
  for (int i = 0; i < n; ++i) {
    processing_channel_t *processor = __find_processing_channel(
        impl->processors, metadata_blocks[i].track_id);
    if (!processor) {
      processor = def_mallocz(processing_channel_t, 1);
      if (!processor) {
        error("could not allocate memory for processing channel.");
        continue;
      }

      processor->key = metadata_blocks[i].track_id;
      processor->value = block_processing_channel_create(impl->gain_calculator);
      if (!processor->value) {
        error("could not create block processing channel.");
        free(processor);
        continue;
      }
      vector_push(impl->processors, def_value_wrap_instance_ptr(processor));
    }

    block_processing_channel_add_metadata(processor->value,
                                          &metadata_blocks[i]);
  }

  return 0;
}

int renderer_impl_render(renderer_impl_t *impl, float *input, int samples,
                         int channels, uint64_t offset, float *out) {
  float *channel_out = 0;
  channel_out = def_malloc(float, samples * impl->output_channels);
  if (!channel_out) return -12;

  for (int i = 0; i < channels; ++i) {
    processing_channel_t *processor =
        __find_processing_channel(impl->processors, i);
    if (processor) {
      memset(channel_out, 0, sizeof(float) * samples * impl->output_channels);
      block_processing_channel_process(processor->value, impl->sample_rate,
                                       offset, &input[i * samples], samples, 1,
                                       channel_out);
      for (int j = 0; j < impl->output_channels; ++j) {
        for (int k = 0; k < samples; ++k) {
          out[j * samples + k] += channel_out[j * samples + k];
        }
      }
    }
  }
  free(channel_out);

  return 0;
}

void __destroy_processing_channel(processing_channel_t *processor) {
  block_processing_channel_destroy(processor->value);
  free(processor);
}

static int __compare_processing_channel_id(value_wrap_t a, value_wrap_t b) {
  processing_channel_t *pc = (processing_channel_t *)a.ptr;
  return pc->key == b.i32;
}

processing_channel_t *__find_processing_channel(vector_t *processors, int id) {
  processing_channel_t *processor = 0;
  value_wrap_t v;
  if (vector_find(processors, def_value_wrap_instance_i32(id),
                  __compare_processing_channel_id, &v) >= 0)
    processor = v.ptr;
  return processor;
}
