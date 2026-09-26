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

#include "vbap_region.h"

#include <stdlib.h>

#include "numc.h"
#include "utils.h"

static void _vbap_region_destroy(vbap_region_t *region);
static int _vbap_region_calculate_gains(vbap_region_t *region,
                                        auto_float_t azimuth,
                                        auto_float_t elevation,
                                        auto_float_t distance,
                                        auto_float_t *gains, int n);

vbap_region_t *vbap_region_create(array_t *speaker_indices,
                                  array_t *speaker_positions) {
  vbap_region_t *region = calloc(1, sizeof(vbap_region_t));
  int n = 0;
  int idx = 0;
  auto_float_t matrix_2x2[4] = {0.f};
  auto_float_t matrix_3x3[9] = {0.f};

  if (!region) return 0;

  region->base.destroy = def_gain_calculator_destroy(_vbap_region_destroy);
  region->base.calculate_gains =
      def_gain_calculator_calculate_gains(_vbap_region_calculate_gains);

  region->ref_speaker_indices = speaker_indices;
  region->speaker_positions = speaker_positions;

  n = array_size(speaker_positions);

  if (n != 2 && n != 3) {
    free(region);
    return 0;  // invalid number of speakers for VBAP
  }

  region->inv_matrix = calloc(n * n, sizeof(auto_float_t));
  for (int i = 0; i < n; i++) {
    cartesian_position_t *cp =
        def_value_wrap_ptr(array_at(region->speaker_positions, i));
    if (n == 2) {  // 2D case
      matrix_2x2[idx++] = cp->x;
      matrix_2x2[idx++] = cp->y;
    } else {  // 3D case
      matrix_3x3[idx++] = cp->x;
      matrix_3x3[idx++] = cp->y;
      matrix_3x3[idx++] = cp->z;
    }
  }

  if (n == 2) {
    nc_linalg_inv_2x2(matrix_2x2, region->inv_matrix);
  }

  return region;
}

void _vbap_region_destroy(vbap_region_t *region) {
  if (!region) return;
  if (region->speaker_positions) {
    array_free(region->speaker_positions, 0);
  }
  if (region->inv_matrix) free(region->inv_matrix);
  free(region);
}

int _vbap_region_calculate_gains(vbap_region_t *region, auto_float_t azimuth,
                                 auto_float_t elevation, auto_float_t distance,
                                 auto_float_t *gains, int n) {
  int ret = 0;
  cartesian_position_t cp = polar_to_cart(azimuth, elevation, distance);
  auto_float_t xyz[3] = {cp.x, cp.y, cp.z};

  nc_dot_n_nxn(xyz, region->inv_matrix, n, gains);
#ifdef __dbg__
  {
#include <stdio.h>
    char buf[256];
    char *p = buf;

    debug("xyz: [%" def_f8g ", %" def_f8g ", %" def_f8g "]", xyz[0], xyz[1],
          xyz[2]);
    p += sprintf(p, "facets: ");
    for (int i = 0; i < n; ++i)
      p +=
          sprintf(p, "%d ",
                  def_value_wrap_i32(array_at(region->ref_speaker_indices, i)));
    p += sprintf(p, "\b");
    debug("%s", buf);

    debug_array_float("inv matrix", region->inv_matrix, n * n);
    debug_array_float("gains", gains, n);
  }
#endif
  for (int i = 0; i < n; i++) {
    // auto_float_t epsilon = -1e-11;
    if (gains[i] < 0.0) {
      gains[i] = 0.0;
      ret = -22;
    }
  }

  return ret;
}