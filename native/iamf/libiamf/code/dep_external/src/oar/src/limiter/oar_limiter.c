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


#include "oar_limiter.h"

#include <math.h>
#include <stdlib.h>
#include <string.h>

#include "definitions.h"

oar_limiter_t* oar_limiter_create(int sampling_rate, double release_ms,
                                  double ceiling_db) {
  if (sampling_rate <= 0 || release_ms <= 0) return 0;

  oar_limiter_t* limiter = def_mallocz(oar_limiter_t, 1);
  if (!limiter) return 0;

  limiter->sampling_rate = sampling_rate;
  limiter->ceiling = pow(10.0, ceiling_db / 20.0);
  limiter->release_time_constant =
      exp(-3.0 / (limiter->sampling_rate * release_ms / 1000.0));
  limiter->env = 1.0;

  return limiter;
}

void oar_limiter_destroy(oar_limiter_t* limiter) { def_free(limiter); }

static double GetMaximumRequiredGain(const oar_limiter_t* limiter,
                                     double sample) {
  if (!limiter) return 1.0;  // Should not happen if called from Process
  return fabs(sample) > limiter->ceiling ? limiter->ceiling / fabs(sample)
                                         : 1.0;
}

int oar_limiter_process(oar_limiter_t* limiter, oar_audio_block_t* block) {
  if (!limiter || !block || !block->data) return ck_oar_error_inval;

  uint32_t num_channels = block->channels;
  uint32_t num_frames = block->samples_per_channel;

  if (num_channels == 0 || num_frames == 0) return ck_oar_error_inval;

  float* max_samples = def_mallocz(float, num_frames);
  float* limiter_env = def_mallocz(float, num_frames);

  if (!max_samples || !limiter_env) {
    def_free(max_samples);
    def_free(limiter_env);
    return ck_oar_error_nomem;
  }

  for (uint32_t c = 0; c < num_channels; ++c) {
    const float* channel_data = block->data + (c * num_frames);
    for (uint32_t f = 0; f < num_frames; ++f) {
      float abs_val = fabsf(channel_data[f]);
      if (abs_val > max_samples[f]) {
        max_samples[f] = abs_val;
      }
    }
  }

  for (uint32_t f = 0; f < num_frames; ++f) {
    double max_req_gain = GetMaximumRequiredGain(limiter, max_samples[f]);
    if (max_req_gain < limiter->env) {
      limiter->env = max_req_gain;
    } else {
      limiter->env =
          limiter->release_time_constant * (limiter->env - max_req_gain) +
          max_req_gain;
    }
    limiter_env[f] = (float)limiter->env;
  }

  for (uint32_t c = 0; c < num_channels; ++c) {
    float* output_channel_data = block->data + (c * num_frames);
    for (uint32_t f = 0; f < num_frames; ++f) {
      output_channel_data[f] *= limiter_env[f];
    }
  }

  free(max_samples);
  free(limiter_env);
  return ck_oar_ok;
}
