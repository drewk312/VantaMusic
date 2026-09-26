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


/**
 * @file downmix_renderer.h
 * @brief DMRenderer APIs.
 * @version 2.0.0
 * @date Created 06/21/2023
 **/

#ifndef __DOWNMIX_RENDERER_H_
#define __DOWNMIX_RENDERER_H_

#include <stdint.h>

typedef enum EDMLayoutType {
  ck_dm_layout_none,
  ck_dm_layout_mono,    // 1.0.0
  ck_dm_layout_stereo,  // 2.0.0
  ck_dm_layout_510,     // 5.1.0
  ck_dm_layout_512,     // 5.1.2
  ck_dm_layout_514,     // 5.1.4
  ck_dm_layout_710,     // 7.1.0
  ck_dm_layout_712,     // 7.1.2
  ck_dm_layout_714,     // 7.1.4
  ck_dm_layout_312,     // 3.1.2
  ck_dm_layout_count,
} dm_layout_t;

typedef struct Downmixer DMRenderer;

/**
 * @brief     Open a downmix renderer.
 * @param     [in] in : the input layout of downmix renderer.
 * @param     [in] out : the output layout of downmix renderer.
 * @return    return a downmix renderer handle.
 */
DMRenderer *DMRenderer_open(dm_layout_t in, dm_layout_t out);

/**
 * @brief     Close the downmix renderer.
 * @param     [in] self : the downmix renderer handle.
 */
void DMRenderer_close(DMRenderer *self);

/**
 * @brief     Set the demix mode and demix weight index for downmix renderer.
 * @param     [in] self : the downmix renderer handle.
 * @param     [in] mode : the demix mode.
 * @param     [in] w_idx : the demix weight index.
 * @return    @ref IAErrCode.
 */
int DMRenderer_set_mode_weight(DMRenderer *self, int mode, int w_idx);

/**
 * @brief     Do downmix rendering from input layout to output layout.
 * @param     [in] self : the downmix renderer handle.
 * @param     [in] in : the input audio pcm.
 * @param     [in] out : the output audio pcm.
 * @param     [in] samples : the number of samples.
 * @return    @ref IAErrCode.
 */
int DMRenderer_downmix(DMRenderer *self, float *in[], float *out[],
                       uint32_t samples);

#endif /* __DOWNMIX_RENDERER_H_ */
