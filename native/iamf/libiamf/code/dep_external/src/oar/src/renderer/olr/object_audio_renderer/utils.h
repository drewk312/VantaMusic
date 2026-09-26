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

#ifndef __UTILS_H__
#define __UTILS_H__

#include <stdarg.h>
#include <stdbool.h>
#include <stdio.h>
#include <string.h>

#include "clog.h"
#include "oar_config.h"

typedef struct CartesianPosition {
  auto_float_t x;
  auto_float_t y;
  auto_float_t z;
} cartesian_position_t;

typedef struct PolarPosition {
  auto_float_t azimuth;
  auto_float_t elevation;
  auto_float_t distance;
} polar_position_t;

typedef struct Conversion {
  struct {
    auto_float_t azimuth;
    auto_float_t position[3];
  } mapping[5];
  auto_float_t el_top;
  auto_float_t el_top_tilde;
} conversion_t;

cartesian_position_t polar_to_cartesian(polar_position_t polar);
auto_float_t linear_interp(auto_float_t start_value, auto_float_t end_value,
                           auto_float_t start_time, auto_float_t end_time,
                           auto_float_t current_time);
cartesian_position_t polar_to_cart(auto_float_t azimuth, auto_float_t elevation,
                                   auto_float_t distance);
polar_position_t cart_to_polar(auto_float_t[3]);

#define def_f8g "-.8g"

#define debug_array_float(name, array, len)         \
  do {                                              \
    char _buf[1024] = {0};                          \
    char *_p = _buf;                                \
    int _n = 0;                                     \
    _n = sprintf(_buf, "%s: [", name);              \
    _p += _n;                                       \
    for (int i = 0; i < len; ++i) {                 \
      _n = sprintf(_p, "%" def_f8g ", ", array[i]); \
      _p += _n;                                     \
    }                                               \
    sprintf(_p, "\b\b]");                           \
    debug("%s", _buf);                              \
  } while (0)

#endif  // __UTILS_H__
