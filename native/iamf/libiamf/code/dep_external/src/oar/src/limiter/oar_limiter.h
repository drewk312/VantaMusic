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


/*
 * Copyright (c) 2025 Google LLC
 *
 * This source code is subject to the terms of the BSD 3-Clause Clear License,
 * which you can find in the LICENSE file, and the Open Binaural Renderer
 * Patent License 1.0, which you can find in the PATENTS file.
 */

#ifndef _OAR_LIMITER_H_
#define _OAR_LIMITER_H_

#include "oar_base.h"  // For oar_audio_block_t

#ifdef __cplusplus
extern "C" {
#endif

/*!\brief Struct for oar_limiter_t state. */
typedef struct OarLimiter {
  int sampling_rate;
  double ceiling;
  double release_time_constant;
  double env;
} oar_limiter_t;

/*!\brief Constructor for oar_limiter_t.
 *
 * \param sampling_rate Sampling rate of the audio data.
 * \param release_ms Release time in milliseconds.
 * \param ceiling_db Ceiling level in decibels.
 * \return A pointer to the newly created oar_limiter_t instance, or NULL on
 * failure.
 */
oar_limiter_t* oar_limiter_create(int sampling_rate, double release_ms,
                                  double ceiling_db);

/*!\brief Destructor for oar_limiter_t.
 *
 * \param limiter Pointer to the oar_limiter_t instance to destroy.
 */
void oar_limiter_destroy(oar_limiter_t* limiter);

/*!\brief Processes the input audio buffer and applies peak limiting.
 *
 * \param limiter Pointer to the oar_limiter_t instance.
 * \param Audio block to be processed.
 * \return Zero on success, non-zero on failure.
 */
int oar_limiter_process(oar_limiter_t* limiter, oar_audio_block_t* block);

#ifdef __cplusplus
}  // extern "C"
#endif

#endif  // _OAR_LIMITER_H_
