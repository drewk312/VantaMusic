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

#include <math.h>
#include <string.h>

#include "animated.h"
#include "oar_utils.h"

// Animation calculation functions converted from macros
float animated_bezier_linear_original_calculate(
    const animated_data_float32_t *a, float c) {
  if (!a) return 0.0f;
  if (isnan(c) || isinf(c)) return 0.0f;

  return ((1 - c) * a->start + c * a->end);
}

float animated_bezier_quadratic_original_calculate(
    const animated_data_float32_t *a, float c) {
  if (!a) return 0.0f;
  if (isnan(c) || isinf(c)) return 0.0f;

  return ((1 - c) * (1 - c) * a->start + 2 * (1 - c) * c * a->control +
          c * c * a->end);
}

float animated_bezier_linear_calculate(const animated_data_float32_t *a,
                                       float c) {
  if (!a) return 0.0f;
  if (isnan(c) || isinf(c)) return 0.0f;

  return (a->start + c * (a->end - a->start));
}

float animated_bezier_quadratic_calculate(const animated_data_float32_t *a,
                                          float c) {
  if (!a) return 0.0f;
  if (isnan(c) || isinf(c)) return 0.0f;

  return ((a->start + a->end - 2 * a->control) * c * c +
          2 * c * (a->control - a->start) + a->start);
}

float animated_spherical_linear_calculate(float start, float end, float rad,
                                          float c) {
  if (isnan(c) || isinf(c)) return 0.0f;
  if (isnan(start) || isnan(end) || isnan(rad)) return 0.0f;
  if (isinf(start) || isinf(end) || isinf(rad)) return 0.0f;

  float sin_rad = sinf(rad);
  if (fabsf(sin_rad) < 1e-6f) {
    return start + c * (end - start);
  }

  return ((sinf((1 - c) * rad) * start + sinf(c * rad) * end) / sin_rad);
}

polar_t animated_spherical_linear_calculate_polar(polar_t start, polar_t end,
                                                  float c) {
  if (isnan(c) || isinf(c)) {
    polar_t zero_result = {0.0f, 0.0f, 0.0f};
    return zero_result;
  }

  polar_t result;
  cartesian_t v0 =
      normalized_polar_to_cartesian_float32(start.azimuth, start.elevation);
  cartesian_t v2 =
      normalized_polar_to_cartesian_float32(end.azimuth, end.elevation);
  cartesian_t v;
  animated_data_float32_t distance = def_animated_data_linear_instance(
      animated_data_float32_t, start.distance, end.distance);

  float omega = cartesian_calculate_angle_radians_float32(v0, v2);

  memset(&result, 0, sizeof(result));

  v.x = animated_spherical_linear_calculate(v0.x, v2.x, omega, c);
  v.y = animated_spherical_linear_calculate(v0.y, v2.y, omega, c);
  v.z = animated_spherical_linear_calculate(v0.z, v2.z, omega, c);

  result = cartesian_to_polar_float32(v);
  result.distance = animated_bezier_linear_calculate(&distance, c);
  return result;
}

float bezier_linear_factor_get(int n2, int n) {
  if (n2 == 0) return 0.0f;
  return (float)n / n2;
}

float bezier_quadratic_factor_get(int n0, int n1, int n2, int n) {
  int alpha = n0 - 2 * n1 + n2;
  float beta = 2 * (n1 - n0);
  float gamma = n0 - n;

  if (alpha == 0) {
    if (beta == 0) return 0.0f;
    return -gamma / beta;
  }

  float discriminant = beta * beta - 4 * alpha * gamma;
  if (discriminant < 0.0f) return 0.0f;

  return (-beta + sqrtf(discriminant)) / (2 * alpha);
}
