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

#ifndef __NUMC_H__
#define __NUMC_H__

#include <math.h>

#include "oar_config.h"

#ifdef OLR_ENABLE_F64
#define nc_abs fabs
#define nc_tan tan
#define nc_arctan atan
#define nc_arctan2 atan2
#define nc_cos cos
#define nc_sin sin
#else
#define nc_abs fabsf
#define nc_tan tanf
#define nc_arctan atanf
#define nc_arctan2 atan2f
#define nc_cos cosf
#define nc_sin sinf
#endif

auto_float_t nc_radians(auto_float_t deg);
auto_float_t nc_degrees(auto_float_t rad);
int nc_argsort(const auto_float_t *arr, int n, int *idx);
int nc_sign(auto_float_t x);
int nc_dot_n_nxn(const auto_float_t *a, const auto_float_t *b, int n,
                 auto_float_t *out);
int nc_maximum(auto_float_t *a, int n, auto_float_t b);
int nc_clip(auto_float_t *a, int n, auto_float_t min, auto_float_t max);
auto_float_t nc_hypot(auto_float_t x, auto_float_t y);
int nc_linalg_inv_2x2(const auto_float_t a[4], auto_float_t inv_a[4]);
auto_float_t nc_linalg_norm(const auto_float_t *v, int n);
auto_float_t nc_linalg_sum(const auto_float_t *a, int n);

#endif  //__NUMC_H__
