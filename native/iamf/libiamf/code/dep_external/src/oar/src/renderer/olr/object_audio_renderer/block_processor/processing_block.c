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

#include "processing_block.h"

#include <stdlib.h>
#include <string.h>

#include "definitions.h"
#include "utils.h"

typedef struct FixedGains {
  processing_block_t base;
  auto_float_t *gains;
  int num_gains;
} fixed_gains_t;

typedef struct InterpGains {
  processing_block_t base;
  auto_float_t *gains_start;
  auto_float_t *gains_end;
  int num_gains;
  auto_float_t *gains_interp;
} interp_gains_t;

typedef struct Slice {
  int s;
  int e;
} slice_t;

static int _processing_block_init(processing_block_t *self,
                                  uint64_t start_sample, uint64_t end_sample) {
  self->start_sample = start_sample;
  self->end_sample = end_sample;
  self->first_sample = start_sample;
  self->last_sample = end_sample;
  return 0;
}

static int _processing_block_overlap(processing_block_t *self, int start_sample,
                                     int num_samples, slice_t *states,
                                     slice_t *samples) {
  int end_sample = start_sample + num_samples;
  int overlay_start_sample = def_max(start_sample, self->first_sample);
  int overlay_end_sample = def_min(end_sample, self->last_sample);

  if (overlay_start_sample < overlay_end_sample) {
    if (samples) {
      samples->s = overlay_start_sample - start_sample;
      samples->e = overlay_end_sample - start_sample;
    }

    if (states) {
      states->s = overlay_start_sample - self->first_sample;
      states->e = overlay_end_sample - self->first_sample;
    }
  }
  return 0;
}

static int _fixed_gains_process(processing_block_t *self, uint64_t start,
                                float *in, int num_samples, int channels,
                                float *out);
static void _fixed_gains_destroy(processing_block_t *self);

processing_block_t *fixed_gains_create(uint64_t start_sample,
                                       uint64_t end_sample, auto_float_t *gains,
                                       int n) {
  fixed_gains_t *fixed_gains = def_mallocz(fixed_gains_t, 1);
  if (!fixed_gains) return 0;

  _processing_block_init(&fixed_gains->base, start_sample, end_sample);
  fixed_gains->base.process = _fixed_gains_process;
  fixed_gains->base.destroy = _fixed_gains_destroy;
  fixed_gains->gains = gains;
  fixed_gains->num_gains = n;
#ifdef __dbg__
  debug_array_float("Fixed gains", gains, n);
#endif
  return (processing_block_t *)fixed_gains;
}

int _fixed_gains_process(processing_block_t *self, uint64_t start_sample,
                         float *in, int num_samples, int channels, float *out) {
  fixed_gains_t *fixed_gains = (fixed_gains_t *)self;
  slice_t samples;

  memset(&samples, 0, sizeof(slice_t));
  _processing_block_overlap(self, start_sample, num_samples, 0, &samples);

  for (int i = 0; i < fixed_gains->num_gains; ++i) {
    for (int j = samples.s; j < samples.e; ++j) {
      for (int k = 0; k < channels; ++k)
        out[i * num_samples + j] +=
            in[k * num_samples + j] * fixed_gains->gains[i];
    }
  }

  return 0;
}

void _fixed_gains_destroy(processing_block_t *self) { free(self); }

static int _interp_gains_process(processing_block_t *self, uint64_t start,
                                 float *in, int num_samples, int channels,
                                 float *out);
static void _interp_gains_destroy(processing_block_t *self);
static void _init_interp_p(interp_gains_t *self);

processing_block_t *interp_gains_create(uint64_t start_sample,
                                        uint64_t end_sample,
                                        auto_float_t *gains_start,
                                        auto_float_t *gains_end, int n) {
  interp_gains_t *self = def_mallocz(interp_gains_t, 1);
  if (!self) return 0;
  self->base.process = _interp_gains_process;
  self->base.destroy = _interp_gains_destroy;
  _processing_block_init(&self->base, start_sample, end_sample);
  self->gains_start = gains_start;
  self->gains_end = gains_end;
  self->num_gains = n;
  _init_interp_p(self);
  return (processing_block_t *)self;
}

static int _interp_gains_process(processing_block_t *self,
                                 uint64_t start_sample, float *in,
                                 int num_samples, int channels, float *out) {
  interp_gains_t *interp = (interp_gains_t *)self;
  slice_t states, samples;

  memset(&states, 0, sizeof(slice_t));
  memset(&samples, 0, sizeof(slice_t));
  _processing_block_overlap(self, start_sample, num_samples, &states, &samples);

  if (interp->gains_start) {
    for (int i = 0; i < interp->num_gains; ++i) {
      for (int j = samples.s, m = states.s; j < samples.e; ++j, ++m) {
        for (int k = 0; k < channels; ++k)
          out[i * num_samples + j] +=
              in[k * num_samples + j] *
              (1.0 - (m < states.e ? interp->gains_interp[m] : 0)) *
              interp->gains_start[i];
      }
    }
  }

  if (interp->gains_end) {
    for (int i = 0; i < interp->num_gains; ++i) {
      for (int j = samples.s, m = states.s; j < samples.e; ++j, ++m) {
        for (int k = 0; k < channels; ++k)
          out[i * num_samples + j] +=
              in[k * num_samples + j] *
              (m < states.e ? interp->gains_interp[m] : 1.f) *
              interp->gains_end[i];
      }
    }
  }
  return 0;
}

void _interp_gains_destroy(processing_block_t *self) {
  interp_gains_t *interp = (interp_gains_t *)self;
  if (!self) return;

  if (interp->gains_interp) free(interp->gains_interp);
  free(interp);
}

void _init_interp_p(interp_gains_t *self) {
  uint64_t n = 0;
  auto_float_t start, end;
  if (self->base.last_sample <= self->base.first_sample) {
    warn("there are no samples");
    return;
  }

  n = self->base.last_sample - self->base.first_sample;
  self->gains_interp = def_mallocz(auto_float_t, n);
  if (!self->gains_interp) return;

  start = (auto_float_t)(self->base.first_sample - self->base.start_sample) /
          (self->base.end_sample - self->base.start_sample);
  end = (auto_float_t)(self->base.last_sample - self->base.start_sample) /
        (self->base.end_sample - self->base.start_sample);

  for (int i = 0; i < n; ++i)
    self->gains_interp[i] = start + i * ((end - start) / n);
}
