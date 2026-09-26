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

#ifndef __DBAP_PANNER_2D_H__
#define __DBAP_PANNER_2D_H__

#include "cvector.h"
#include "gain_calculator.h"

gain_calculator_t *dbap_panner_2d_create(vector_t *speakers);
#endif  // __DBAP_PANNER_2D_H__