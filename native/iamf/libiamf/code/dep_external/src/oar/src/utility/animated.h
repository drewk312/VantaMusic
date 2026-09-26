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

#ifndef __ANIMATED_H__
#define __ANIMATED_H__

#include "animation.h"
#include "oar_base.h"

float animated_bezier_linear_original_calculate(
    const animated_data_float32_t *a, float c);
float animated_bezier_quadratic_original_calculate(
    const animated_data_float32_t *a, float c);
float animated_bezier_linear_calculate(const animated_data_float32_t *a,
                                       float c);
float animated_bezier_quadratic_calculate(const animated_data_float32_t *a,
                                          float c);
float animated_spherical_linear_calculate(float start, float end, float rad,
                                          float c);
polar_t animated_spherical_linear_calculate_polar(polar_t start, polar_t end,
                                                  float c);
float bezier_linear_factor_get(int n2, int n);
float bezier_quadratic_factor_get(int n0, int n1, int n2, int n);

#endif  // __ANIMATED_H__
