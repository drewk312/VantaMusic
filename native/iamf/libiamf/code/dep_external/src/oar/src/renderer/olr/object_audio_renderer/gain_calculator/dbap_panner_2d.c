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


#include "dbap_panner_2d.h"

#include <math.h>
#include <stdlib.h>

#include "definitions.h"
#include "numc.h"
#include "utils.h"

typedef struct DBAPPanner2D {
  gain_calculator_t base;
  vector_t *target_speakers;  // speaker_position_t*
  int is_convexhull;
} dbap_panner_2d_t;

static void _dbap_panner_2d_destroy(dbap_panner_2d_t *self);
static int _dbap_panner_2d_calculate_gains(dbap_panner_2d_t *self,
                                           auto_float_t azimuth,
                                           auto_float_t elevation,
                                           auto_float_t distance,
                                           auto_float_t *gains, int n);

static int _is_convex_hull(vector_t *speaker_positions);

gain_calculator_t *dbap_panner_2d_create(vector_t *speaker_positions) {
  dbap_panner_2d_t *self =
      (dbap_panner_2d_t *)calloc(1, sizeof(dbap_panner_2d_t));
  if (!self) return 0;

  self->base.calculate_gains =
      def_gain_calculator_calculate_gains(_dbap_panner_2d_calculate_gains);
  self->base.destroy = def_gain_calculator_destroy(_dbap_panner_2d_destroy);

  self->target_speakers = vector_clone(speaker_positions);
  self->is_convexhull = _is_convex_hull(speaker_positions);
  return def_gain_calculator(self);
}

void _dbap_panner_2d_destroy(dbap_panner_2d_t *self) {
  if (!self) return;
  vector_free(self->target_speakers, 0);
  free(self);
}

#define def_min_dist 0.1
int _dbap_panner_2d_calculate_gains(dbap_panner_2d_t *self,
                                    auto_float_t azimuth,
                                    auto_float_t elevation,
                                    auto_float_t distance, auto_float_t *gains,
                                    int n) {
  int s;
  auto_float_t gains_norm = 0.f, *distances = 0, *weights = 0, weights_sum = 0;
  cartesian_position_t *np_speakers, projected_source;
  vector_t *speakers = self->target_speakers;

  for (int i = 0; i < n; ++i) gains[i] = 0.0f;

  s = vector_size(speakers);

  np_speakers = def_mallocz(cartesian_position_t, s);
  distances = def_mallocz(auto_float_t, s);
  weights = def_mallocz(auto_float_t, s);

  if (!np_speakers || !distances || !weights) {
    def_free(np_speakers);
    def_free(distances);
    def_free(weights);
    return -12;
  }

  for (int i = 0; i < s; ++i) {
    speaker_position_t *sp = def_value_wrap_ptr(vector_at(speakers, i));
    np_speakers[i] = polar_to_cart(sp->azimuth, sp->elevation, 1.0f);
  }

  projected_source = polar_to_cart(azimuth, elevation, distance);

  for (int i = 0; i < s; ++i) {
    auto_float_t xyz[3] = {np_speakers[i].x - projected_source.x,
                           np_speakers[i].y - projected_source.y,
                           np_speakers[i].z - projected_source.z};
    distances[i] = nc_linalg_norm(xyz, 3);
  }
  nc_maximum(distances, s, def_min_dist);
  for (int i = 0; i < s; ++i) weights[i] = 1.0 / (distances[i] * distances[i]);
  weights_sum = nc_linalg_sum(weights, s);
  for (int i = 0; i < s; ++i) gains[i] = weights[i] / weights_sum;
  gains_norm = nc_linalg_norm(gains, s);
  for (int i = 0; i < s; ++i) gains[i] /= gains_norm;

  def_free(np_speakers);
  def_free(distances);
  def_free(weights);

  return 0;
}

int _is_convex_hull(vector_t *speaker_positions) {
  auto_float_t min_azi = def_none_degree, max_azi = -def_none_degree;
  for (int i = 0; i < vector_size(speaker_positions); ++i) {
    speaker_position_t *sp =
        def_value_wrap_ptr(vector_at(speaker_positions, i));
    min_azi = fmin(min_azi, sp->azimuth);
    max_azi = fmax(max_azi, sp->azimuth);
  }
  return max_azi > 90.f && min_azi < -90.f;
}
