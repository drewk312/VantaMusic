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

#include "audio_element_renderer.h"

#include <math.h>
#include <stdlib.h>
#include <string.h>

#include "animated.h"
#include "clog.h"
#include "cvector.h"
#include "definitions.h"
#include "oar_utils.h"

typedef struct AudioElementRenderer {
  audio_renderer_base_t base;  // Base class
  oar_audio_element_config_t config;
  audio_element_context_t element;
#ifdef __as_dbg__
  void *rendered;
#endif
} audio_element_renderer_t;

#define def_audio_element_renderer_ptr(a) ((audio_element_renderer_t *)(a))

static int _sub_frames_apply_positions(audio_element_renderer_t *renderer,
                                       oar_audio_block_t *block,
                                       uint32_t sub_frame_samples) {
  uint32_t total_samples = block->samples_per_channel;
  uint32_t processed_samples = 0;

  if (!renderer->element.rendering_metadata.positions ||
      !renderer->element.rendering_metadata.positions->metadatas)
    return ck_oar_error_inval;

  while (processed_samples < total_samples) {
    uint32_t current_unit_samples = sub_frame_samples;
    uint32_t remaining_samples = total_samples - processed_samples;
    uint32_t accumulated_duration = 0;
    uint32_t sample_position =
        processed_samples +
        renderer->element.rendering_metadata.positions->start;

    if (remaining_samples < sub_frame_samples)
      current_unit_samples = remaining_samples;

    // Find the current position metadata for this sample position
    oar_metadata_t *current_metadata = 0;
    for (int j = 0;
         j < queue_length(
                 renderer->element.rendering_metadata.positions->metadatas);
         j++) {
      value_wrap_t *metadata_wrap = queue_at(
          renderer->element.rendering_metadata.positions->metadatas, j);
      if (!metadata_wrap) continue;

      oar_metadata_t *metadata = def_value_wrap_ptr(metadata_wrap);
      if (!metadata || metadata->type != ck_metadata_object_positions) continue;

      if (sample_position >= accumulated_duration &&
          sample_position < accumulated_duration + metadata->duration) {
        // Create a constant position metadata for this specific sample position
        uint32_t relative_pos = sample_position - accumulated_duration;
        current_metadata = metadata_constant_polar_positions_create(
            metadata, relative_pos, current_unit_samples);
        break;
      }

      accumulated_duration += metadata->duration;
    }

    if (current_metadata) {
      // Apply position metadata if found
      if (renderer->base.lib && renderer->base.lib->metadata_update)
        renderer->base.lib->metadata_update(&renderer->base.ctx, 0,
                                            current_metadata);
      metadata_delete(current_metadata);
    }

    // Create sub-block for this segment with proper memory allocation
    oar_audio_block_t sub_block;
    sub_block.channels = renderer->base.block.channels;
    sub_block.samples_per_channel = current_unit_samples;

    // Allocate memory for sub-block data
    sub_block.data =
        def_malloc(float, sub_block.channels *sub_block.samples_per_channel);
    if (!sub_block.data) {
      return ck_oar_error_nomem;
    }

    // Copy data from original block in planar format with proper offset
    // alignment
    for (uint32_t ch = 0; ch < sub_block.channels; ch++) {
      // Source channel data starts at ch * renderer->block.samples_per_channel
      // Copy from processed_samples offset for current_unit_samples length
      const float *src_channel = renderer->base.block.data +
                                 ch * renderer->base.block.samples_per_channel +
                                 processed_samples;
      float *dst_channel = sub_block.data + ch * sub_block.samples_per_channel;

      memcpy(dst_channel, src_channel, current_unit_samples * sizeof(float));
    }

    // Create output sub-block with proper memory allocation
    oar_audio_block_t sub_out_block;
    sub_out_block.channels = block->channels;
    sub_out_block.samples_per_channel = current_unit_samples;

    // Allocate memory for output sub-block data
    sub_out_block.data = def_mallocz(
        float, sub_out_block.channels *sub_out_block.samples_per_channel);
    if (!sub_out_block.data) {
      def_free(sub_block.data);
      return ck_oar_error_nomem;
    }

    // Render this sub-frame
    if (renderer->base.lib && renderer->base.lib->render) {
      renderer->base.lib->render(&renderer->base.ctx, &sub_block,
                                 &sub_out_block);

      // Copy rendered data to output block at correct position in planar format
      for (uint32_t ch = 0; ch < sub_out_block.channels; ch++) {
        // Source channel data from rendered sub-block
        const float *src_channel =
            sub_out_block.data + ch * sub_out_block.samples_per_channel;
        // Destination channel data in output block at correct offset
        float *dst_channel =
            block->data + ch * block->samples_per_channel + processed_samples;

        memcpy(dst_channel, src_channel, current_unit_samples * sizeof(float));
      }
    }

    // Clean up allocated memory
    def_free(sub_block.data);
    def_free(sub_out_block.data);

    processed_samples += current_unit_samples;
  }

  return ck_oar_ok;
}

// Public API functions
static void audio_element_renderer_delete(audio_renderer_base_t *base);
static uint32_t audio_element_renderer_get_channels(
    audio_renderer_base_t *base);

audio_element_renderer_t *audio_element_renderer_new(
    const oar_audio_element_config_t *config, const oar_config_t *oar_config,
    uint32_t id, renderer_library_manager_t *manager,
    const uint32_t *metadata_samples_ref) {
  renderer_library_context_t *ctx = 0;
  audio_element_renderer_t *self = def_mallocz(audio_element_renderer_t, 1);
  if (!self) return 0;

  self->config = *config;

  ctx = &self->base.ctx;
  ctx->sample_rate = oar_config->sampling_rate;
  ctx->samples_per_frame = oar_config->samples_per_channel;

  ctx->in = audio_element_config_to_ri_id(config);
  if (ctx->in == ck_rid_none) {
    warning("Unsupported input self.");
    audio_element_renderer_delete(&self->base);
    return 0;
  }

  ctx->out = layout_to_ro_id(oar_config->target_layout);
  ctx->stream_id = id;
  ctx->parameters = config->parameters;

  if (ctx->out == ck_rio_id_binaural &&
      ctx->parameters.flags &
          def_parameter_set_flag_iamf_element_rendering_config &&
      !ctx->parameters.element_rendering_config.headphones_rendering_mode &&
      config->type == ck_channel_based)
    ctx->out = ck_rio_id_stereo;

  self->base.lib = renderer_library_manager_find_library(manager, ctx);
  if (!self->base.lib) {
    warning("Failed to find self library for stream %u", id);
    audio_element_renderer_delete(&self->base);
    return 0;
  }

  self->base.metadata_samples_ref = metadata_samples_ref;

  info("Using self library %s for stream %u", self->base.lib->id, id);
  self->base.block.channels = audio_element_renderer_get_channels(&self->base);
  self->base.block.samples_per_channel = oar_config->samples_per_channel;
  self->base.block.data = def_malloc(
      float, self->base.block.channels * self->base.block.samples_per_channel);
  if (!self->base.block.data) {
    audio_element_renderer_delete(&self->base);
    return 0;
  }

  if (audio_element_context_init(&self->element, id, config,
                                 ctx->sample_rate) != ck_oar_ok) {
    audio_element_renderer_delete(&self->base);
    return 0;
  }

  if (self->base.lib) self->base.lib->open(&self->base.ctx);

  if (ctx->out == ck_rio_id_binaural && self->base.lib &&
      self->base.lib->set_attribute) {
    element_parameter_set_t set;
    set.id = ctx->in;
    set.profile = config->parameters.flags &
                          def_parameter_set_flag_iamf_element_rendering_config
                      ? config->parameters.element_rendering_config
                            .binaural_filter_profile
                      : ck_binaural_filter_profile_default;
    if (self->base.lib->set_attribute(ctx, ck_attribute_add_element, &set) !=
        ck_oar_ok) {
      warning("Failed to set binaural filter profile for stream %u", id);
      audio_element_renderer_delete(&self->base);
      return 0;
    }

    if (ctx->parameters.flags &
            def_parameter_set_flag_iamf_element_rendering_config &&
        ctx->parameters.element_rendering_config.headphones_rendering_mode ==
            ck_head_locked) {
      int enable = 1;
      self->base.lib->set_attribute(ctx, ck_attribute_head_tracking, &enable);
    }
  }

#ifdef __as_dbg__
  self->rendered =
      wav_writer_open(ck_tag_rendered, id, ctx->sample_rate,
                      layout_channels_count(oar_config->target_layout));
  if (!self->rendered)
    warning("Failed to open WAV file for rendered stream %u", id);
#endif

  return self;
}

void audio_element_renderer_delete(audio_renderer_base_t *base) {
  audio_element_renderer_t *self = def_audio_element_renderer_ptr(base);
  if (!self) return;

  audio_renderer_base_uninit(&self->base);
  audio_element_context_uninit(&self->element);

#ifdef __as_dbg__
  if (self->rendered) wav_writer_close(self->rendered);
#endif

  def_free(self);
}

static int audio_element_renderer_update_metadata(
    audio_renderer_base_t *base, uint32_t element_id,
    const oar_metadata_t *metadata) {
  audio_element_renderer_t *self = def_audio_element_renderer_ptr(base);
  if (!self) return ck_oar_error_inval;

  if (self->element.eid != element_id) {
    warning("Element ID mismatch: expected %u, got %u", self->element.eid,
            element_id);
    return ck_oar_error_inval;
  }

  if (metadata->type == ck_metadata_gain) {
    return audio_element_context_update_gain(&self->element, metadata);
  } else if (metadata->type == ck_metadata_object_positions &&
             self->config.type == ck_object_based) {
    return audio_element_context_update_object_positions(&self->element,
                                                         metadata);
  } else if (self->base.lib && self->base.lib->metadata_update) {
    return self->base.lib->metadata_update(&self->base.ctx, 0, metadata);
  }
  return ck_oar_error_notsup;
}

int audio_element_renderer_update_data(audio_renderer_base_t *base, uint32_t id,
                                       oar_audio_block_t *block) {
  audio_element_renderer_t *self = def_audio_element_renderer_ptr(base);

  if (!self) return ck_oar_error_inval;

  if (self->element.eid != id) {
    warning("Element ID mismatch: expected %u, got %u", self->element.eid, id);
    return ck_oar_error_inval;
  }

  if (!block || !block->data) return ck_oar_error_inval;

  if (block->channels != self->base.block.channels) {
    warning("Input block channels (%u) don't match self channels (%u)",
            block->channels, self->base.block.channels);
    return ck_oar_error_inval;
  }

  if (block->samples_per_channel != self->base.block.samples_per_channel) {
    warning(
        "Input block samples per channel (%u) don't match self samples per "
        "channel (%u)",
        block->samples_per_channel, self->base.block.samples_per_channel);
    return ck_oar_error_inval;
  }

  memcpy(self->base.block.data, block->data,
         block->channels * block->samples_per_channel * sizeof(float));

#ifdef __as_dbg__
  // Write original audio data to debug WAV file if available
  if (self->element.original) {
    wav_writer_write(self->element.original, block->data,
                     block->samples_per_channel, block->channels);
  }
#endif

  return ck_oar_ok;
}

int audio_element_renderer_render(audio_renderer_base_t *base,
                                  oar_audio_block_t *out_block) {
  audio_element_renderer_t *self = def_audio_element_renderer_ptr(base);
  int ret = ck_oar_error_inval;

  if (!self || !out_block) return ck_oar_error_inval;

  // For object-based renderers, use sub-frame rendering with position updates
  if (self->config.type == ck_object_based) {
    ret = _sub_frames_apply_positions(
        self, out_block,
        self->base.metadata_samples_ref[ck_metadata_object_positions]);
  } else if (self->base.lib &&
             self->base.lib->render) {  // For other self types, render the
                                        // entire block at once
    ret = self->base.lib->render(&self->base.ctx, &self->base.block, out_block);
  }

#ifdef __as_dbg__
  if (ret == ck_oar_ok && self->rendered)
    wav_writer_write(self->rendered, out_block->data,
                     out_block->samples_per_channel, out_block->channels);
#endif

  return ret;
}

int audio_element_renderer_apply_gains(audio_renderer_base_t *base,
                                       oar_audio_block_t *block) {
  audio_element_renderer_t *self = def_audio_element_renderer_ptr(base);

  if (!self || !block || !block->data) return ck_oar_error_inval;

  if (vector_size(self->element.rendering_metadata.gains) == 0)
    return ck_oar_ok;

  for (int i = 0; i < vector_size(self->element.rendering_metadata.gains);
       i++) {
    audio_block_sub_frames_apply_gain(
        block,
        def_value_wrap_type_ptr(
            metadata_item_t,
            vector_at(self->element.rendering_metadata.gains, i)),
        self->base.metadata_samples_ref[ck_metadata_gain]);
  }

  return ck_oar_ok;
}

uint32_t audio_element_renderer_get_channels(audio_renderer_base_t *base) {
  audio_element_renderer_t *self = def_audio_element_renderer_ptr(base);
  switch (self->config.type) {
    case ck_channel_based:
      return layout_channels_count(self->config.cbc.layout);
    case ck_scene_based:
      return (self->config.sbc.order + 1) * (self->config.sbc.order + 1);
    case ck_object_based:
      return self->config.obc.num_objects;
    default:
      return 0;
  }
}

int audio_element_renderer_set_element_head_locked(audio_renderer_base_t *base,
                                                   uint32_t element_id,
                                                   int enabled) {
  audio_element_renderer_t *self = def_audio_element_renderer_ptr(base);
  if (!self) return ck_oar_error_inval;

  if (self->element.eid != element_id) {
    warning("Element ID mismatch: expected %u, got %u", self->element.eid,
            element_id);
    return ck_oar_error_inval;
  }

  if (self->base.lib && self->base.lib->set_attribute) {
    element_head_locked_t params =
        (element_head_locked_t){.index = 0, .locked = enabled};
    return self->base.lib->set_attribute(
        &self->base.ctx, ck_attribute_element_head_locked, &params);
  }
  return ck_oar_error_notsup;
}

void audio_element_renderer_metadatas_elapse(audio_renderer_base_t *base,
                                             uint32_t samples_per_channel) {
  audio_element_renderer_t *self = def_audio_element_renderer_ptr(base);

  if (!self) return;

  // Elapse gain metadata for each self
  if (self->element.rendering_metadata.gains) {
    for (int k = 0; k < vector_size(self->element.rendering_metadata.gains);
         k++) {
      metadata_item_t *gain_item = def_value_wrap_type_ptr(
          metadata_item_t,
          vector_at(self->element.rendering_metadata.gains, k));
      if (!gain_item || !gain_item->metadatas) continue;

      metadata_item_elapse(gain_item, samples_per_channel);
    }
  }

  // Elapse position metadata for object-based renderers
  if (self->element.rendering_metadata.positions &&
      self->element.rendering_metadata.positions->metadatas) {
    metadata_item_elapse(self->element.rendering_metadata.positions,
                         samples_per_channel);
  }
}

// Wrapper function implementations
audio_renderer_base_t *audio_element_renderer_create_wrapper(
    const oar_config_t *oar_config, uint32_t id,
    renderer_library_manager_t *manager, const uint32_t *metadata_samples_ref,
    const oar_audio_element_config_t *config) {
  audio_element_renderer_t *renderer = audio_element_renderer_new(
      config, oar_config, id, manager, metadata_samples_ref);

  if (renderer) {
    // Set virtual function table
    static audio_renderer_api_t element_api = {
        .destroy = audio_element_renderer_delete,
        .update_element_metadata = audio_element_renderer_update_metadata,
        .add_data = audio_element_renderer_update_data,
        .apply_gains = audio_element_renderer_apply_gains,
        .render = audio_element_renderer_render,
        .enable_head_tracking = audio_renderer_enable_head_tracking,
        .set_element_head_locked =
            audio_element_renderer_set_element_head_locked,
        .set_head_rotation = audio_renderer_set_head_rotation,
        .get_channels = audio_element_renderer_get_channels,
        .metadatas_elapse = audio_element_renderer_metadatas_elapse,
    };

    renderer->base.impl = &element_api;
    return &renderer->base;
  }

  return 0;
}
