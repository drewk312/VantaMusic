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

#ifndef __AUDIO_RENDERER_API_H__
#define __AUDIO_RENDERER_API_H__

#include "oar.h"
#include "renderer/renderer_library_manager.h"

#ifdef __cplusplus
extern "C" {
#endif

// Forward declarations
typedef struct AudioRendererBase audio_renderer_base_t;
typedef struct AudioRendererAPI audio_renderer_api_t;

typedef void (*func_destroy_t)(audio_renderer_base_t *base);

typedef int (*func_add_element_t)(audio_renderer_base_t *base,
                                  uint32_t element_id,
                                  const oar_audio_element_config_t *config);

typedef int (*func_get_element_index_t)(audio_renderer_base_t *base,
                                        uint32_t element_id);

typedef int (*func_update_element_metadata_t)(audio_renderer_base_t *base,
                                              uint32_t element_id,
                                              const oar_metadata_t *metadata);

typedef int (*func_add_data_t)(audio_renderer_base_t *base, uint32_t element_id,
                               oar_audio_block_t *block);

typedef int (*func_apply_gains_t)(audio_renderer_base_t *base,
                                  oar_audio_block_t *output_block);

typedef int (*func_render_t)(audio_renderer_base_t *base,
                             oar_audio_block_t *output_block);

typedef int (*func_enable_head_tracking_t)(audio_renderer_base_t *base,
                                           int enable);

typedef int (*func_set_element_head_locked_t)(audio_renderer_base_t *base,
                                              uint32_t element_id, int lock);

typedef int (*func_set_head_rotation_t)(audio_renderer_base_t *base,
                                        const quaternion_t *rotation);

typedef uint32_t (*func_get_channels_t)(audio_renderer_base_t *base);

typedef void (*func_metadatas_elapse_t)(audio_renderer_base_t *base,
                                        uint32_t samples_per_channel);

typedef int (*func_get_element_channels_t)(audio_renderer_base_t *base,
                                           uint32_t element_id);

// AudioRendererAPI structure definition
struct AudioRendererAPI {
  func_destroy_t destroy;
  func_add_element_t add_element;
  func_get_element_index_t get_element_index;
  func_update_element_metadata_t update_element_metadata;
  func_set_element_head_locked_t set_element_head_locked;
  func_add_data_t add_data;
  func_apply_gains_t apply_gains;
  func_render_t render;
  func_enable_head_tracking_t enable_head_tracking;
  func_set_head_rotation_t set_head_rotation;
  func_get_channels_t get_channels;
  func_metadatas_elapse_t metadatas_elapse;
  func_get_element_channels_t get_element_channels;
};

#ifdef __cplusplus
}
#endif

#endif  // __AUDIO_RENDERER_API_H__
