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

#if defined(_MSC_VER)
#define _USE_MATH_DEFINES
#endif
#include "numc.h"
#include <stdlib.h>

auto_float_t nc_radians(auto_float_t degrees) { return degrees * M_PI / 180.0; }

auto_float_t nc_degrees(auto_float_t radians) { return radians * 180.0 / M_PI; }

typedef struct {
  int key;
  auto_float_t value;
} nc_pair_t;

static int _nc_argsort_compare(const void *a, const void *b) {
  const nc_pair_t *pa = (const nc_pair_t *)a;
  const nc_pair_t *pb = (const nc_pair_t *)b;
  return pa->value < pb->value ? 0 : 1;
}

int nc_argsort(const auto_float_t *arr, int n, int *idx) {
  nc_pair_t *pairs = malloc(n * sizeof(nc_pair_t));
  if (!pairs) return -12;
  for (int i = 0; i < n; ++i) {
    pairs[i].key = i;
    pairs[i].value = arr[i];
  }

  qsort(pairs, n, sizeof(nc_pair_t), _nc_argsort_compare);

  for (int i = 0; i < n; ++i) {
    idx[i] = pairs[i].key;
  }
  free(pairs);
  return 0;
}

int nc_sign(auto_float_t x) { return x > 0 ? 1 : (x < 0 ? -1 : 0); }

int nc_dot_n_nxn(const auto_float_t *_n, const auto_float_t *_nxn, int n,
                 auto_float_t *out) {
  for (int i = 0; i < n; ++i) {
    out[i] = 0;

    for (int j = 0; j < n; ++j) {
      out[i] += _n[j] * _nxn[j * n + i];
    }
  }
  return 0;
}

int nc_maximum(auto_float_t *a, int n, auto_float_t b) {
  for (int i = 0; i < n; i++) {
    if (a[i] < b) a[i] = b;
  }
  return 0;
}

int nc_clip(auto_float_t *a, int n, auto_float_t min, auto_float_t max) {
  for (int i = 0; i < n; i++)
    a[i] = a[i] < min ? min : (a[i] > max ? max : a[i]);
  return 0;
}

auto_float_t nc_hypot(auto_float_t x, auto_float_t y) {
#ifdef OLR_ENABLE_F64
  return sqrt(x * x + y * y);
#else
  return sqrtf(x * x + y * y);
#endif
}

int nc_linalg_inv_2x2(const auto_float_t a[4], auto_float_t inv_a[4]) {
  auto_float_t det = a[0] * a[3] - a[1] * a[2];
  auto_float_t inv_det = 0;

  if (nc_abs(det) == 0.0) return -22;

  inv_det = 1.0 / det;

  inv_a[0] = a[3] * inv_det;
  inv_a[1] = -a[1] * inv_det;
  inv_a[2] = -a[2] * inv_det;
  inv_a[3] = a[0] * inv_det;

  return 0;
}

auto_float_t nc_linalg_norm(const auto_float_t *v, int n) {
  auto_float_t norm = 0.0f;
  for (int i = 0; i < n; ++i) {
    norm += v[i] * v[i];
  }
#ifdef OLR_ENABLE_F64
  return sqrt(norm);
#else
  return sqrtf(norm);
#endif
}

auto_float_t nc_linalg_sum(const auto_float_t *a, int n) {
  auto_float_t sum = 0;
  for (int i = 0; i < n; ++i) sum += a[i];
  return sum;
}
