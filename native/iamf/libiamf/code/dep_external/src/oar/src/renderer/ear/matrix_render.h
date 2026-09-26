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

#ifndef _MATRIX_RENDER_H_
#define _MATRIX_RENDER_H_

#if defined(__ARM_NEON)
#define def_oar_arch_arm
#endif

void matrix_render(float* mat, int in_dim, int in_next, int* in_idx_map,
                   int out_dim, int out_next, float** in, float** out,
                   int nsamples);

void multiply_channels_by_matrix_neon(float* mat, int in_dim, int in_next,
                                      int* in_idx_map, int out_dim,
                                      int out_next, float** in, float** out,
                                      int nsamples);
void multiply_channels_by_matrix_c(float* mat, int in_dim, int in_next,
                                   int* in_idx_map, int out_dim, int out_next,
                                   float** in, float** out, int nsamples);
#endif /* _MATRIX_RENDER_H_ */