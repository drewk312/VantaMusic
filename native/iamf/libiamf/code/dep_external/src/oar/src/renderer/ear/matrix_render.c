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


#include "matrix_render.h"

void matrix_render(float* mat, int in_dim, int in_next, int* in_idx_map,
                   int out_dim, int out_next, float** in, float** out,
                   int nsamples) {
#if defined(def_oar_arch_arm)
  multiply_channels_by_matrix_neon(mat, in_dim, in_next, in_idx_map, out_dim,
                                   out_next, in, out, nsamples);
#else
  multiply_channels_by_matrix_c(mat, in_dim, in_next, in_idx_map, out_dim,
                                out_next, in, out, nsamples);
#endif
}