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

#include "audio_elements_renderer.h"

#include "common/chashmap.h"
#include "common/clog.h"
#include "common/definitions.h"
#include "oar_utils.h"

typedef struct AudioElementsRenderer {
  audio_renderer_base_t base;

  int elements_updated;
  vector_t *elements;       // vector<audio_element_context>
  hash_map_t *element_map;  // map<id, audio_element_context>
  uint32_t channels_count;

#ifdef __as_dbg__
  void *rendered;
#endif
} audio_elements_renderer_t;

#define def_audio_elements_renderer_ptr(a) ((audio_elements_renderer_t *)(a))

static int audio_elements_renderer_private_object_based_elements_count(
    audio_elements_renderer_t *self) {
  int count = 0;
  int n = vector_size(self->elements);

  for (int i = 0; i < n; ++i) {
    audio_element_context_t *ctx = def_value_wrap_type_ptr(
        audio_element_context_t, vector_at(self->elements, i));
    if (ctx->rid == ck_ri_id_oa_mono || ctx->rid == ck_ri_id_oa_dual) {
      count++;
    }
  }

  return count;
}

static int audio_elements_renderer_sub_frames_apply_positions(
    audio_elements_renderer_t *self, oar_audio_block_t *block,
    uint32_t sub_frame_samples) {
  uint32_t total_samples = block->samples_per_channel;
  uint32_t processed_samples = 0;

  while (processed_samples < total_samples) {
    uint32_t current_unit_samples = sub_frame_samples;
    uint32_t remaining_samples = total_samples - processed_samples;
    uint32_t accumulated_duration = 0;

    int n = vector_size(self->elements);

    if (remaining_samples < sub_frame_samples)
      current_unit_samples = remaining_samples;

    for (int i = 0; i < n; i++) {
      audio_element_context_t *ctx = def_value_wrap_type_ptr(
          audio_element_context_t, vector_at(self->elements, i));
      if (!ctx->rendering_metadata.positions) continue;

      uint32_t sample_position =
          processed_samples + ctx->rendering_metadata.positions->start;
      // Find the current position metadata for this sample position
      oar_metadata_t *current_metadata = 0;
      int m = queue_length(ctx->rendering_metadata.positions->metadatas);
      for (int j = 0; j < m; j++) {
        oar_metadata_t *metadata = def_value_wrap_optional_type_ptr(
            oar_metadata_t,
            queue_at(ctx->rendering_metadata.positions->metadatas, j));
        if (!metadata || metadata->type != ck_metadata_object_positions)
          continue;

        if (sample_position >= accumulated_duration &&
            sample_position < accumulated_duration + metadata->duration) {
          // Create a constant position metadata for this specific sample
          // position
          uint32_t relative_pos = sample_position - accumulated_duration;
          current_metadata = metadata_constant_polar_positions_create(
              metadata, relative_pos, current_unit_samples);
          break;
        }

        accumulated_duration += metadata->duration;
      }

      if (current_metadata) {
        // Apply position metadata if found
        if (self->base.lib && self->base.lib->metadata_update)
          self->base.lib->metadata_update(&self->base.ctx, i, current_metadata);
        metadata_delete(current_metadata);
      }
    }

    oar_audio_block_t sub_block;
    sub_block.channels = self->channels_count;
    sub_block.samples_per_channel = current_unit_samples;

    sub_block.data =
        def_malloc(float, (sub_block.channels * sub_block.samples_per_channel));
    if (!sub_block.data) return ck_oar_error_nomem;

    oar_audio_block_t sub_out_block;
    sub_out_block.channels = block->channels;
    sub_out_block.samples_per_channel = current_unit_samples;

    sub_out_block.data = def_malloc(
        float, (sub_out_block.channels * sub_out_block.samples_per_channel));
    if (!sub_out_block.data) {
      def_free(sub_block.data);
      return ck_oar_error_nomem;
    }

    for (uint32_t ch = 0; ch < sub_block.channels; ch++)
      memcpy(sub_block.data + ch * sub_block.samples_per_channel,
             self->base.block.data + ch * self->base.block.samples_per_channel +
                 processed_samples,
             (current_unit_samples * sizeof(float)));

    // Render this sub-frame
    if (self->base.lib && self->base.lib->render) {
      self->base.lib->render(&self->base.ctx, &sub_block, &sub_out_block);

      for (uint32_t ch = 0; ch < sub_out_block.channels; ch++)
        memcpy(
            block->data + ch * block->samples_per_channel + processed_samples,
            sub_out_block.data + ch * sub_out_block.samples_per_channel,
            (current_unit_samples * sizeof(float)));
    }

    // Clean up allocated memory
    def_free(sub_block.data);
    def_free(sub_out_block.data);

    processed_samples += current_unit_samples;
  }

  return ck_oar_ok;
}

audio_elements_renderer_t *audio_elements_renderer_new(
    const oar_config_t *oar_config, uint32_t id,
    renderer_library_manager_t *manager, const uint32_t *metadata_samples_ref) {
  renderer_library_context_t *ctx = 0;
  audio_elements_renderer_t *self = def_mallocz(audio_elements_renderer_t, 1);
  if (!self) return 0;

  ctx = &self->base.ctx;
  ctx->sample_rate = oar_config->sampling_rate;
  ctx->samples_per_frame = oar_config->samples_per_channel;

  ctx->in = ck_ri_multi_ids;
  ctx->out = layout_to_ro_id(oar_config->target_layout);
  ctx->stream_id = id;

  self->base.lib = renderer_library_manager_find_library(manager, ctx);
  if (!self->base.lib) {
    warning("Failed to find renderer library for stream %u", id);
    def_free(self);
    return 0;
  }

  self->base.metadata_samples_ref = metadata_samples_ref;

  info("Using renderer library %s for stream %u", self->base.lib->id, id);

  self->elements = vector_new();
  if (!self->elements) {
    def_free(self);
    return 0;
  }

  self->element_map = hash_map_new(def_hash_map_capacity_elements);
  if (!self->element_map) {
    vector_free(self->elements,
                def_default_free_ptr(audio_element_context_delete));
    def_free(self);
    return 0;
  }

#ifdef __as_dbg__
  self->rendered =
      wav_writer_open(ck_tag_rendered, id, oar_config->sampling_rate,
                      layout_channels_count(oar_config->target_layout));
  if (!self->rendered)
    warning("Failed to open WAV file for rendered stream %u", id);
#endif

  return self;
}

void audio_elements_renderer_delete(audio_renderer_base_t *base) {
  audio_elements_renderer_t *self = def_audio_elements_renderer_ptr(base);
  if (!self) return;

  // Clean up base renderer
  audio_renderer_base_uninit(&self->base);

  if (self->element_map) hash_map_delete(self->element_map, 0);
  if (self->elements)
    vector_free(self->elements,
                def_default_free_ptr(audio_element_context_delete));

#ifdef __as_dbg__
  if (self->rendered) wav_writer_close(self->rendered);
#endif

  def_free(self);
}

int audio_elements_renderer_add_element(
    audio_renderer_base_t *base, uint32_t element_id,
    const oar_audio_element_config_t *config) {
  audio_elements_renderer_t *self = def_audio_elements_renderer_ptr(base);
  int ret = ck_oar_error_notsup;
  if (!self || !config) return ck_oar_error_inval;

  if (hash_map_get(self->element_map, element_id)) {
    warning("Element %u already exists", element_id);
    return ck_oar_error_busy;
  }

  audio_element_context_t *ctx = def_mallocz(audio_element_context_t, 1);
  if (!ctx) return ck_oar_error_nomem;

  audio_element_context_init(ctx, element_id, config,
                             self->base.ctx.sample_rate);

  ctx->index = vector_size(self->elements);
  ctx->channel_start_index = self->channels_count;

  if (!ctx->index && self->base.lib && self->base.lib->open) {
    ret = self->base.lib->open(&self->base.ctx);
    if (ret != ck_oar_ok) {
      error("Failed to open renderer library for stream %u",
            self->base.ctx.stream_id);
      audio_element_context_delete(ctx);
      return ret;
    }
  }

  if (self->base.lib && self->base.lib->set_attribute) {
    element_parameter_set_t set = (element_parameter_set_t){
        .id = ctx->rid, .profile = ctx->binaural_filter_profile};
    ret = self->base.lib->set_attribute(&self->base.ctx,
                                        ck_attribute_add_element, &set);
    if (ret == ck_oar_ok) {
      self->elements_updated = 1;
      self->channels_count += rid_channels_count(ctx->rid);
      info("Added element %u, index %d, channels %d", element_id, ctx->index,
           self->channels_count);
    }
  }

  if (ret != ck_oar_ok ||
      vector_push(self->elements, def_value_wrap_instance_ptr(ctx)) < 0 ||
      hash_map_put(self->element_map, element_id,
                   def_value_wrap_instance_ptr(ctx)) < 0) {
    audio_element_context_delete(ctx);

    if (ret != ck_oar_ok) error("Failed to add element.");
  }

  return ret;
}

int audio_elements_renderer_get_element_index(audio_renderer_base_t *base,
                                              uint32_t element_id) {
  audio_elements_renderer_t *self = def_audio_elements_renderer_ptr(base);
  if (!self) return ck_oar_error_inval;

  audio_element_context_t *ctx = def_value_wrap_optional_type_ptr(
      audio_element_context_t, hash_map_get(self->element_map, element_id));
  return ctx ? ctx->index : ck_oar_error_inval;
}

int audio_elements_renderer_update_element_metadata(
    audio_renderer_base_t *base, uint32_t element_id,
    const oar_metadata_t *metadata) {
  audio_elements_renderer_t *self = def_audio_elements_renderer_ptr(base);
  int ret = ck_oar_error_notsup;
  if (!self || !metadata) return ck_oar_error_inval;

  audio_element_context_t *ctx =
      (audio_element_context_t *)def_value_wrap_optional_ptr(
          hash_map_get(self->element_map, element_id));
  if (!ctx) {
    warning("Element %u not found", element_id);
    return ck_oar_error_inval;
  }

  if (metadata->type == ck_metadata_gain) {
    ret = audio_element_context_update_gain(ctx, metadata);
  } else if (metadata->type == ck_metadata_object_positions &&
             (ctx->rid == ck_ri_id_oa_mono || ctx->rid == ck_ri_id_oa_dual)) {
    ret = audio_element_context_update_object_positions(ctx, metadata);
  } else if (self->base.lib && self->base.lib->metadata_update) {
    ret =
        self->base.lib->metadata_update(&self->base.ctx, ctx->index, metadata);
  }

  return ret;
}

int audio_elements_renderer_add_data(audio_renderer_base_t *base,
                                     uint32_t element_id,
                                     oar_audio_block_t *block) {
  audio_elements_renderer_t *self = def_audio_elements_renderer_ptr(base);
  if (!self || !block || !block->data) return ck_oar_error_inval;

  audio_element_context_t *ctx =
      (audio_element_context_t *)def_value_wrap_optional_ptr(
          hash_map_get(self->element_map, element_id));
  if (!ctx) {
    warning("Element %u not found", element_id);
    return ck_oar_error_inval;
  }

  if (self->elements_updated) {
    if (!self->base.block.data) {
      debug("channels %d, samples %d", self->channels_count,
            block->samples_per_channel);
      self->base.block.data = def_malloc(
          float, (self->channels_count * block->samples_per_channel));
      if (!self->base.block.data) {
        return ck_oar_error_nomem;
      }
      self->base.block.samples_per_channel = block->samples_per_channel;
    } else if (block->channels < self->channels_count) {
      float *data =
          def_realloc(self->base.block.data, float,
                      (self->channels_count * block->samples_per_channel));
      if (!data) return ck_oar_error_nomem;
      self->base.block.data = data;
    }
    self->base.block.channels = self->channels_count;
    self->elements_updated = 0;
  }

  memcpy(self->base.block.data +
             ctx->channel_start_index * self->base.block.samples_per_channel,
         block->data,
         (block->samples_per_channel * block->channels * sizeof(float)));

#ifdef __as_dbg__
  // Write original audio data to debug WAV file if available
  if (ctx->original) {
    wav_writer_write(ctx->original, block->data, block->samples_per_channel,
                     block->channels);
  }
#endif

  return ck_oar_ok;
}

int audio_elements_renderer_apply_gains(audio_renderer_base_t *base,
                                        oar_audio_block_t *block) {
  audio_elements_renderer_t *self = def_audio_elements_renderer_ptr(base);
  if (!self) return ck_oar_error_inval;
  int n = vector_size(self->elements);

  for (int i = 0; i < n; i++) {
    audio_element_context_t *ctx = def_value_wrap_type_ptr(
        audio_element_context_t, vector_at(self->elements, i));
    if (ctx->rendering_metadata.gains) {
      for (int j = 0; j < vector_size(ctx->rendering_metadata.gains); j++) {
        oar_audio_block_t gain_block = (oar_audio_block_t){
            .data =
                self->base.block.data +
                ctx->channel_start_index * self->base.block.samples_per_channel,
            .samples_per_channel = self->base.block.samples_per_channel,
            .channels = rid_channels_count(ctx->rid)};

        audio_block_sub_frames_apply_gain(
            &gain_block,
            def_value_wrap_type_ptr(
                metadata_item_t, vector_at(ctx->rendering_metadata.gains, j)),
            self->base.metadata_samples_ref[ck_metadata_gain]);
      }
    }
  }

  return ck_oar_ok;
}

int audio_elements_renderer_render(audio_renderer_base_t *base,
                                   oar_audio_block_t *output_block) {
  audio_elements_renderer_t *self = def_audio_elements_renderer_ptr(base);
  int ret = ck_oar_error_inval;
  if (!self || !output_block) return ck_oar_error_inval;

  // if includes object-based renderers, use sub-frame rendering with position
  // updates
  if (audio_elements_renderer_private_object_based_elements_count(self)) {
    ret = audio_elements_renderer_sub_frames_apply_positions(
        self, output_block,
        self->base.metadata_samples_ref[ck_metadata_object_positions]);
  }
  // Render the entire block at once using the underlying self library
  else if (self->base.lib && self->base.lib->render) {
    ret = self->base.lib->render(&self->base.ctx, &self->base.block,
                                 output_block);
  }
#ifdef __as_dbg__
  if (ret == ck_oar_ok && self->rendered)
    wav_writer_write(self->rendered, output_block->data,
                     output_block->samples_per_channel, output_block->channels);
#endif

  return ret;
}

int audio_elements_renderer_set_element_head_locked(audio_renderer_base_t *base,
                                                    uint32_t element_id,
                                                    int head_locked) {
  audio_elements_renderer_t *self = def_audio_elements_renderer_ptr(base);
  if (!self) return ck_oar_error_inval;

  audio_element_context_t *ctx =
      (audio_element_context_t *)def_value_wrap_optional_ptr(
          hash_map_get(self->element_map, element_id));
  if (!ctx) {
    warning("Element %u not found", element_id);
    return ck_oar_error_inval;
  }

  if (self->base.lib && self->base.lib->set_attribute) {
    element_head_locked_t params =
        (element_head_locked_t){.index = ctx->index, .locked = head_locked};
    return self->base.lib->set_attribute(
        &self->base.ctx, ck_attribute_element_head_locked, &params);
  }

  return ck_oar_error_notsup;
}

uint32_t audio_elements_renderer_get_channels(audio_renderer_base_t *base) {
  audio_elements_renderer_t *self = def_audio_elements_renderer_ptr(base);
  return self->channels_count;
}

void audio_elements_renderer_metadatas_elapse(audio_renderer_base_t *base,
                                              uint32_t samples_per_channel) {
  audio_elements_renderer_t *self = def_audio_elements_renderer_ptr(base);

  if (!self) return;

  // Elapse gain and position metadata for each element in a single loop
  int n = vector_size(self->elements);
  for (int i = 0; i < n; i++) {
    audio_element_context_t *ctx = def_value_wrap_type_ptr(
        audio_element_context_t, vector_at(self->elements, i));
    if (!ctx) continue;

    // Elapse gain metadata
    if (ctx->rendering_metadata.gains) {
      for (int k = 0; k < vector_size(ctx->rendering_metadata.gains); k++) {
        metadata_item_t *gain_item = def_value_wrap_type_ptr(
            metadata_item_t, vector_at(ctx->rendering_metadata.gains, k));
        if (!gain_item || !gain_item->metadatas) continue;

        metadata_item_elapse(gain_item, samples_per_channel);
      }
    }

    // Elapse position metadata for object-based renderers
    if (ctx->rendering_metadata.positions &&
        ctx->rendering_metadata.positions->metadatas) {
      metadata_item_elapse(ctx->rendering_metadata.positions,
                           samples_per_channel);
    }
  }
}

// Wrapper function implementations
audio_renderer_base_t *audio_elements_renderer_create_wrapper(
    const oar_config_t *oar_config, uint32_t id,
    renderer_library_manager_t *manager, const uint32_t *metadata_samples_ref,
    const oar_audio_element_config_t *config) {
  audio_elements_renderer_t *self = audio_elements_renderer_new(
      oar_config, id, manager, metadata_samples_ref);

  if (self) {
    static const audio_renderer_api_t elements_api = {
        .destroy = audio_elements_renderer_delete,
        .get_element_index = audio_elements_renderer_get_element_index,
        .add_element = audio_elements_renderer_add_element,
        .update_element_metadata =
            audio_elements_renderer_update_element_metadata,
        .add_data = audio_elements_renderer_add_data,
        .apply_gains = audio_elements_renderer_apply_gains,
        .render = audio_elements_renderer_render,
        .enable_head_tracking = audio_renderer_enable_head_tracking,
        .set_element_head_locked =
            audio_elements_renderer_set_element_head_locked,
        .set_head_rotation = audio_renderer_set_head_rotation,
        .get_channels = audio_elements_renderer_get_channels,
        .metadatas_elapse = audio_elements_renderer_metadatas_elapse,
    };

    self->base.impl = &elements_api;
    return &self->base;
  }

  return 0;
}
