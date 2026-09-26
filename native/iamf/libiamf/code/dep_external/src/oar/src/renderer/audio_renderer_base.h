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

#ifndef __AUDIO_RENDERER_BASE_H__
#define __AUDIO_RENDERER_BASE_H__

#include "audio_renderer_api.h"
#include "common/cqueue.h"
#include "common/cvector.h"
#include "oar_base.h"
#include "oar_metadata.h"
#include "renderer/renderer_library_manager.h"

#ifdef __cplusplus
extern "C" {
#endif

// Forward declarations
typedef struct AudioRendererBase audio_renderer_base_t;
typedef struct MetadataItem metadata_item_t;
typedef struct AudioRenderingMetadata audio_rendering_metadata_t;
typedef struct AudioElementContext audio_element_context_t;

// Public structure definitions
typedef struct MetadataItem {
  uint32_t id;
  queue_t *metadatas;
  uint32_t start;
  uint32_t duration;
} metadata_item_t;

typedef struct AudioRenderingMetadata {
  vector_t *gains;  // vector<metadata_item_t:oar_metadata_gain_t>
  metadata_item_t *positions;
} audio_rendering_metadata_t;

typedef struct AudioElementContext {
  uint32_t eid;
  rid_t rid;

  oar_binaural_filter_profile_t binaural_filter_profile;

  uint32_t index;
  uint32_t channel_start_index;
  audio_rendering_metadata_t rendering_metadata;

#ifdef __as_dbg__
  void *original;
#endif
} audio_element_context_t;

// AudioRendererBase structure definition (base class)
struct AudioRendererBase {
  const audio_renderer_api_t *impl;
  renderer_library_context_t ctx;
  const renderer_library_api_t *lib;
  const uint32_t *metadata_samples_ref;
  oar_audio_block_t block;
};

// AudioElementContext related functions
int audio_element_context_init(audio_element_context_t *ctx, uint32_t id,
                               const oar_audio_element_config_t *config,
                               uint32_t sample_rate);

void audio_element_context_uninit(audio_element_context_t *ctx);

void audio_element_context_delete(audio_element_context_t *ctx);

int audio_element_context_update_gain(audio_element_context_t *ctx,
                                      const oar_metadata_t *metadata);

int audio_element_context_update_object_positions(
    audio_element_context_t *ctx, const oar_metadata_t *metadata);

void metadata_item_elapse(metadata_item_t *item, uint32_t samples_per_channel);

int audio_block_sub_frames_apply_gain(oar_audio_block_t *block,
                                      metadata_item_t *gain_item,
                                      uint32_t sub_frame_samples);

oar_metadata_t *metadata_constant_polar_positions_create(
    const oar_metadata_t *metadata, uint32_t relative_pos, uint32_t duration);

void audio_renderer_base_uninit(audio_renderer_base_t *base);
void audio_renderer_delete(audio_renderer_base_t *base);
int audio_renderer_enable_head_tracking(audio_renderer_base_t *base,
                                        int enable);
int audio_renderer_set_head_rotation(audio_renderer_base_t *base,
                                     const quaternion_t *rotation);

#ifdef __cplusplus
}
#endif

#endif  // __AUDIO_RENDERER_BASE_H__
