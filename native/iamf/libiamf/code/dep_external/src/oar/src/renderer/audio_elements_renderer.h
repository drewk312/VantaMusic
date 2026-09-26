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

#ifndef __AUDIO_ELEMENTS_RENDERER_H__
#define __AUDIO_ELEMENTS_RENDERER_H__

#include "audio_renderer_base.h"

// Constructor
audio_renderer_base_t *audio_elements_renderer_create_wrapper(
    const oar_config_t *oar_config, uint32_t id,
    renderer_library_manager_t *manager, const uint32_t *metadata_samples_ref,
    const oar_audio_element_config_t *config);

#endif /* __AUDIO_ELEMENTS_RENDERER_H__ */
