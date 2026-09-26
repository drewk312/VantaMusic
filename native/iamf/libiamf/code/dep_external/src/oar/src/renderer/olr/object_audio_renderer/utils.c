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

#include "utils.h"

#if defined(_MSC_VER)
#define _USE_MATH_DEFINES
#endif
#include <math.h>

#include "numc.h"

cartesian_position_t polar_to_cartesian(polar_position_t polar) {
  return polar_to_cart(polar.azimuth, polar.elevation, polar.distance);
}

auto_float_t linear_interp(auto_float_t start_value, auto_float_t end_value,
                           auto_float_t start_time, auto_float_t end_time,
                           auto_float_t current_time);
cartesian_position_t polar_to_cart(auto_float_t azimuth, auto_float_t elevation,
                                   auto_float_t distance) {
  return (cartesian_position_t){
      nc_sin(nc_radians(-azimuth)) * nc_cos(nc_radians(elevation)) * distance,
      nc_cos(nc_radians(-azimuth)) * nc_cos(nc_radians(elevation)) * distance,
      nc_sin(nc_radians(elevation)) * distance};
}

polar_position_t cart_to_polar(auto_float_t xyz[3]) {
  auto_float_t radius = nc_hypot(xyz[0], xyz[1]);

  return (polar_position_t){
      -nc_degrees(nc_arctan2(xyz[0], xyz[1])),
      radius ? nc_degrees(nc_arctan2(xyz[2], radius)) : 0.0f,
      nc_linalg_norm(xyz, 3)};
}