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

#ifndef __DEFINITIONS_H__
#define __DEFINITIONS_H__

#include <stdlib.h>

#define def_mallocz(type, n) ((type *)calloc(n, sizeof(type)))
#define def_malloc(type, n) ((type *)malloc(n * sizeof(type)))
#define def_realloc(ptr, type, n) ((type *)realloc((ptr), (n) * sizeof(type)))
#define def_free(ptr)   \
  do {                  \
    if (ptr) free(ptr); \
  } while (0)

#define def_max(a, b) (((a) > (b)) ? (a) : (b))
#define def_min(a, b) (((a) < (b)) ? (a) : (b))

#ifndef M_PI
#define M_PI 3.14159265358979323846
#endif

#endif  //__DEFINITIONS_H__