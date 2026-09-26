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

#include "vbap_panner_2d.h"

#include <math.h>
#include <stdlib.h>

#include "convex_hull.h"
#include "definitions.h"
#include "numc.h"
#include "utils.h"
#include "vbap_region.h"
#include "vog.h"

typedef struct VBAPPanner2D vbap_panner_2d_t;

struct VBAPPanner2D {
  gain_calculator_t base;
  vector_t *target_speakers;  // speaker_position_t *
  int is_convexhull;
  array_t *cartesian_positions;  // cartesian_position_t *
  array_t *simplices;            // array_t *  (2~3)
  vector_t *regions;             // gain_calculator_t *
};

def_key_value_type(int, speaker_position_t *, speaker_index);

static void _vbap_panner_2d_destroy(vbap_panner_2d_t *self);
static int _vbap_panner_2d_calculate_gains(vbap_panner_2d_t *self,
                                           auto_float_t azimuth,
                                           auto_float_t elevation,
                                           auto_float_t distance,
                                           auto_float_t *gains, int n);

static int _is_convex_hull(vector_t *speaker_positions);
static int _make_vbap_regions(vbap_panner_2d_t *self);
static int _vbap_panner_2d_instance_check(vbap_panner_2d_t *self);

gain_calculator_t *vbap_panner_2d_create(vector_t *speaker_positions) {
  vbap_panner_2d_t *self = def_mallocz(vbap_panner_2d_t, 1);
  if (!self) return 0;

  self->base.destroy = def_gain_calculator_destroy(_vbap_panner_2d_destroy);
  self->base.calculate_gains =
      def_gain_calculator_calculate_gains(_vbap_panner_2d_calculate_gains);

  self->target_speakers = vector_clone(speaker_positions);
  self->is_convexhull = _is_convex_hull(speaker_positions);
  info("VBAP Panner 2D Convex Hull: %d", self->is_convexhull);
  _make_vbap_regions(self);

  return def_gain_calculator(self);
}

int vbap_panner_2d_is_convex_hull(gain_calculator_t *self) {
  vbap_panner_2d_t *vbap_panner = (vbap_panner_2d_t *)self;
  return _vbap_panner_2d_instance_check(vbap_panner)
             ? vbap_panner->is_convexhull
             : -1;
}

vector_t *vbap_panner_2d_get_speaker_positions(gain_calculator_t *self) {
  vbap_panner_2d_t *vbap_panner = (vbap_panner_2d_t *)self;
  return _vbap_panner_2d_instance_check(vbap_panner)
             ? vbap_panner->target_speakers
             : 0;
}

int _vbap_panner_2d_instance_check(vbap_panner_2d_t *self) {
  if (!self || self->base.destroy !=
                   def_gain_calculator_destroy(_vbap_panner_2d_destroy))
    return 0;
  return 1;
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

array_t *__create_cartesian_positions(vector_t *speaker_positions) {
  array_t *cartesian_positions = 0;  // cartesian_position_t
  int n = vector_size(speaker_positions);

#ifdef __dbg__
  trace("input speaker positions size: %d", n);
#endif

  cartesian_positions = array_new(n);
  if (!cartesian_positions) return 0;

  for (int i = 0; i < n; ++i) {
    cartesian_position_t *cp = def_mallocz(cartesian_position_t, 1);
    if (!cp) continue;
    speaker_position_t *sp =
        def_value_wrap_ptr(vector_at(speaker_positions, i));
    *cp = polar_to_cart(sp->azimuth, sp->elevation, 1.0f);
    def_value_wrap_ptr(array_at(cartesian_positions, i)) = cp;
#ifdef __dbg__
    trace("Cartesian Position: [%d][%" def_f8g ", %" def_f8g "]", i, cp->x,
          cp->y);
#endif
  }

  return cartesian_positions;
}

static array_t *__get_2d_simplices(int indices[], array_t *cartesian_positions,
                                   int n) {
  array_t *simplices = array_new(n);
  if (!simplices) return 0;

  for (int i = 0; i < n; ++i) {
    array_t *_indices = array_new(2);
    polar_position_t *pp[2] = {0};
    if (!_indices) continue;
    pp[0] = def_value_wrap_ptr(array_at(cartesian_positions, indices[i]));
    pp[1] =
        def_value_wrap_ptr(array_at(cartesian_positions, indices[(i + 1) % n]));
    def_value_wrap_ptr(array_at(simplices, i)) = _indices;

    if (pp[0]->azimuth < pp[1]->azimuth) {
      def_value_wrap_i32(array_at(_indices, 0)) = indices[i];
      def_value_wrap_i32(array_at(_indices, 1)) = indices[(i + 1) % n];
    } else {
      def_value_wrap_i32(array_at(_indices, 1)) = indices[i];
      def_value_wrap_i32(array_at(_indices, 0)) = indices[(i + 1) % n];
    }
  }
  return simplices;
}

static array_t *__get_2d_simplices_with_convex_hull(
    array_t *cartesian_positions) {
  struct Point in_points[def_max_speakers];
  struct Point out_points[def_max_speakers];
  int out_indices[def_max_speakers];
  int m = 0, n = 0;

  n = array_size(cartesian_positions);

  for (int i = 0; i < n; ++i) {
    cartesian_position_t *cp =
        def_value_wrap_ptr(array_at(cartesian_positions, i));
    in_points[i] = (struct Point){.x = cp->x, .y = cp->y};
  }

  convexHull(in_points, n, out_points, &m);

  for (int i = 0; i < m; i++) {
    for (int j = 0; j < n; j++) {
      if (in_points[j].x == out_points[i].x &&
          in_points[j].y == out_points[i].y) {
        out_indices[i] = j;
        break;
      }
    }
  }

  return __get_2d_simplices(out_indices, cartesian_positions, m);
}

static int __init_vbap_regions(vbap_panner_2d_t *self,
                               array_t *cartesian_positions,
                               array_t *simplices) {
  for (int i = 0; i < array_size(simplices); ++i) {
    vbap_region_t *region = 0;
    array_t *region_simplices = def_value_wrap_ptr(array_at(simplices, i));
    array_t *region_cartesian_positions =
        array_new(array_size(region_simplices));
    for (int j = 0; j < array_size(region_simplices); ++j) {
      int index = def_value_wrap_i32(array_at(region_simplices, j));
      cartesian_position_t *cp =
          def_value_wrap_ptr(array_at(cartesian_positions, index));
      def_value_wrap_ptr(array_at(region_cartesian_positions, j)) = cp;
    }

    region = vbap_region_create(region_simplices, region_cartesian_positions);
    vector_push(self->regions, def_value_wrap_instance_ptr(region));
  }
  return 0;
}

int __make_vbap_regions_with_convex_hull(vbap_panner_2d_t *self,
                                         array_t *cartesian_positions) {
  self->simplices = __get_2d_simplices_with_convex_hull(cartesian_positions);
  __init_vbap_regions(self, cartesian_positions, self->simplices);
  return 0;
}

static int __compare_with_azimuth(const void *vp1, const void *vp2) {
  speaker_index_t *s1 = (speaker_index_t *)vp1;
  speaker_index_t *s2 = (speaker_index_t *)vp2;

  return s1->value->azimuth < s2->value->azimuth ? -1 : 1;
}

static array_t *__get_2d_simplices_without_convex_hull(
    speaker_index_t *sorted_indexed, int n) {
  array_t *simplices = array_new(n - 1);
  if (!simplices) return 0;

  for (int i = 0; i < n - 1; ++i) {
    array_t *region_simplices = array_new(2);
    if (!region_simplices) continue;
    def_value_wrap_i32(array_at(region_simplices, 0)) = sorted_indexed[i].key;
    def_value_wrap_i32(array_at(region_simplices, 1)) =
        sorted_indexed[(i + 1)].key;
    def_value_wrap_ptr(array_at(simplices, i)) = region_simplices;
  }
  return simplices;
}

int __make_vbap_regions_without_convex_hull(vbap_panner_2d_t *self,
                                            array_t *cartesian_positions) {
  int n = vector_size(self->target_speakers);
  speaker_index_t *sorted_indexed = 0;
  speaker_index_t *indexed = def_mallocz(speaker_index_t, n);

  if (!indexed) return -12;

  for (int i = 0; i < n; ++i) {
    indexed[i] = (speaker_index_t){
        .key = i,
        .value = def_value_wrap_ptr(vector_at(self->target_speakers, i))};
  }
  qsort(indexed, n, sizeof(speaker_index_t), __compare_with_azimuth);
  sorted_indexed = indexed;
  self->simplices = __get_2d_simplices_without_convex_hull(sorted_indexed, n);

  __init_vbap_regions(self, cartesian_positions, self->simplices);

  free(indexed);

  return 0;
}

int _make_vbap_regions(vbap_panner_2d_t *self) {
  self->cartesian_positions =
      __create_cartesian_positions(self->target_speakers);
  self->regions = vector_new();

  if (array_size(self->cartesian_positions) > 1) {
    if (self->is_convexhull) {
      __make_vbap_regions_with_convex_hull(self, self->cartesian_positions);
    } else {
      __make_vbap_regions_without_convex_hull(self, self->cartesian_positions);
    }
  } else if (array_size(self->cartesian_positions) == 1) {
    vector_push(self->regions, def_value_wrap_instance_ptr(vog_create()));
  }

  return 0;
}

static void __region_free(gain_calculator_t *region) {
  if (!region) return;
  region->destroy(region);
}

static void __simplices_free(array_t *indices) {
  if (!indices) return;
  array_free(indices, def_default_free_ptr(free));
}

static void _vbap_panner_2d_destroy(vbap_panner_2d_t *self) {
  if (!self) return;
  if (self->regions)
    vector_free(self->regions, def_default_free_ptr(__region_free));
  if (self->simplices)
    array_free(self->simplices, def_default_free_ptr(__simplices_free));
  if (self->cartesian_positions)
    array_free(self->cartesian_positions, def_default_free_ptr(free));
  if (self->target_speakers) vector_free(self->target_speakers, 0);
  free(self);
}

static int _vbap_panner_2d_calculate_gains(vbap_panner_2d_t *self,
                                           auto_float_t azimuth,
                                           auto_float_t elevation,
                                           auto_float_t distance,
                                           auto_float_t *gains, int n) {
  int r = vector_size(self->regions);
  int i = 0;

  for (int j = 0; j < n; ++j) gains[j] = 0.0f;
  for (; i < r; ++i) {
    vbap_region_t *region = def_value_wrap_ptr(vector_at(self->regions, i));
    int s = 0;
    auto_float_t *r_gains = 0;

    s = array_size(region->ref_speaker_indices);
    r_gains = def_mallocz(auto_float_t, s);
    if (!r_gains) continue;

    if (region->base.calculate_gains(&region->base, azimuth, elevation,
                                     distance, r_gains, s) >= 0) {
      auto_float_t r_gains_norm = nc_linalg_norm(r_gains, s);
      if (r_gains_norm > 0.0f) {
        for (int j = 0; j < s; ++j) {
          gains[def_value_wrap_i32(array_at(region->ref_speaker_indices, j))] +=
              r_gains[j] / r_gains_norm;
        }
        free(r_gains);
        break;
      }
    }
    free(r_gains);
  }

  // If source is not contain in regions, find closest speaker by azimuth
  if (i == r) {
    auto_float_t min_diff = def_none_degree;
    auto_float_t az_diff;
    int closest_spk_idx = 0;

    r = vector_size(self->target_speakers);
    for (int j = 0; j < r; ++j) {
      speaker_position_t *sp =
          def_value_wrap_ptr(vector_at(self->target_speakers, j));
      az_diff = nc_abs(sp->azimuth - azimuth);
      if (az_diff < min_diff) {
        min_diff = az_diff;
        closest_spk_idx = j;
      }
    }
    gains[closest_spk_idx] = 1.0f;
  }

  return 0;
}