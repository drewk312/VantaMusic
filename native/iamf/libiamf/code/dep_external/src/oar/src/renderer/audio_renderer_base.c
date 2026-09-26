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

#include "audio_renderer_base.h"

#include <math.h>
#include <stdlib.h>
#include <string.h>

#include "animated.h"
#include "clog.h"
#include "cvector.h"
#include "definitions.h"
#include "oar_utils.h"
#include "renderer_library_manager.h"

// Helper functions
static int _find_metadata_item_id(value_wrap_t item, value_wrap_t key) {
  metadata_item_t *metadata_item = item.ptr;
  return metadata_item->id == key.u32 ? 1 : 0;
}

void _metadata_item_delete(metadata_item_t *metadata_item) {
  if (!metadata_item) return;

  if (metadata_item->metadatas) {
    queue_free(metadata_item->metadatas, def_default_free_ptr(metadata_delete));
    metadata_item->metadatas = 0;
  }

  def_free(metadata_item);
}

// AudioElementContext related functions
int audio_element_context_init(audio_element_context_t *ctx, uint32_t id,
                               const oar_audio_element_config_t *config,
                               uint32_t sample_rate) {
  if (!ctx || !config) return ck_oar_error_inval;
  memset(ctx, 0, sizeof(audio_element_context_t));

  ctx->eid = id;
  ctx->rid = audio_element_config_to_ri_id(config);

  ctx->rendering_metadata.gains = vector_new();
  if (!ctx->rendering_metadata.gains) return ck_oar_error_nomem;

  if (config->type == ck_object_based) {
    ctx->rendering_metadata.positions = def_mallocz(metadata_item_t, 1);
    if (!ctx->rendering_metadata.positions) return ck_oar_error_nomem;

    ctx->rendering_metadata.positions->metadatas = queue_new();
    if (!ctx->rendering_metadata.positions->metadatas)
      return ck_oar_error_nomem;
  }

  if (config->parameters.flags &
      def_parameter_set_flag_iamf_element_rendering_config) {
    ctx->binaural_filter_profile =
        config->parameters.element_rendering_config.binaural_filter_profile;
  } else {
    ctx->binaural_filter_profile = ck_binaural_filter_profile_default;
  }

#ifdef __as_dbg__
  ctx->original = wav_writer_open(ck_tag_original, id, sample_rate,
                                  rid_channels_count(ctx->rid));
  if (!ctx->original) warning("Failed to open WAV file for stream %u", id);
#endif

  return ck_oar_ok;
}

void audio_element_context_uninit(audio_element_context_t *ctx) {
  if (!ctx) return;
  if (ctx->rendering_metadata.gains)
    vector_free(ctx->rendering_metadata.gains,
                def_default_free_ptr(_metadata_item_delete));
  if (ctx->rendering_metadata.positions) {
    queue_free(ctx->rendering_metadata.positions->metadatas,
               def_default_free_ptr(metadata_delete));
    def_free(ctx->rendering_metadata.positions);
  }

#ifdef __as_dbg__
  if (ctx->original) wav_writer_close(ctx->original);
#endif
}

void audio_element_context_delete(audio_element_context_t *ctx) {
  if (!ctx) return;
  audio_element_context_uninit(ctx);
  def_free(ctx);
}

int audio_element_context_update_gain(audio_element_context_t *ctx,
                                      const oar_metadata_t *metadata) {
  value_wrap_t v;
  metadata_item_t *gain = 0;
  oar_metadata_t *clone = 0;
  if (vector_find(ctx->rendering_metadata.gains,
                  def_value_wrap_instance_u32(metadata->gain.id),
                  _find_metadata_item_id, &v) < 0) {
    metadata_item_t *item = def_mallocz(metadata_item_t, 1);
    if (!item) return ck_oar_error_nomem;
    item->id = metadata->gain.id;
    item->metadatas = queue_new();
    if (!item->metadatas) {
      def_free(item);
      return ck_oar_error_nomem;
    }
    gain = item;
    vector_push(ctx->rendering_metadata.gains,
                def_value_wrap_instance_ptr(gain));
  } else {
    gain = def_value_wrap_type_ptr(metadata_item_t, &v);
  }

  clone = metadata_clone(metadata);
  if (!clone) return ck_oar_error_nomem;
  metadata_gain_linear(clone);
  queue_push(gain->metadatas, def_value_wrap_instance_ptr(clone));
  gain->duration += metadata->duration;

  return ck_oar_ok;
}

int audio_element_context_update_object_positions(
    audio_element_context_t *ctx, const oar_metadata_t *metadata) {
  oar_metadata_t *clone = 0;

  if (!ctx || !metadata || metadata->type != ck_metadata_object_positions)
    return ck_oar_error_inval;

  clone = metadata_clone(metadata);
  if (!clone) return ck_oar_error_nomem;

  queue_push(ctx->rendering_metadata.positions->metadatas,
             def_value_wrap_instance_ptr(clone));
  ctx->rendering_metadata.positions->duration += metadata->duration;

  return ck_oar_ok;
}

void metadata_item_elapse(metadata_item_t *item, uint32_t samples_per_channel) {
  queue_t *metadatas;
  if (!item || !item->metadatas || queue_is_empty(item->metadatas)) return;

  debug("metadata item %u S: elapsed %u, duration %d, elapse %u", item->id,
        item->start, item->duration, samples_per_channel);

  item->start += samples_per_channel;
  metadatas = item->metadatas;

  while (!queue_is_empty(metadatas)) {
    value_wrap_t *metadata_wrap = queue_at(metadatas, 0);
    if (!metadata_wrap) break;

    oar_metadata_t *metadata = def_value_wrap_ptr(metadata_wrap);
    if (!metadata) break;

    if (metadata->duration <= item->start) {
      value_wrap_t v;
      item->start -= metadata->duration;
      item->duration -= metadata->duration;
      queue_pop(metadatas, &v);
      metadata_delete(metadata);
    } else
      break;
  }

  if (item->start > item->duration) {
    warning(
        "Metadata item %d has negative duration (%d vs %d). reset metadata "
        "item.",
        item->id, item->start, item->duration);
    item->duration = item->start = 0;
  }

  debug("metadata item %u E: elapsed %u, duration %d, subblocks %d", item->id,
        item->start, item->duration, queue_length(metadatas));
}

int audio_block_sub_frames_apply_gain(oar_audio_block_t *block,
                                      metadata_item_t *gain_item,
                                      uint32_t sub_frame_samples) {
  uint32_t total_samples = block->samples_per_channel;
  uint32_t processed_samples = 0;

  if (!gain_item || !gain_item->metadatas) return ck_oar_error_inval;

  while (processed_samples < total_samples) {
    float cumulative_gain = 1.0f;
    uint32_t current_unit_samples = sub_frame_samples;
    uint32_t remaining_samples = total_samples - processed_samples;
    uint32_t accumulated_duration = 0;
    uint32_t sample_gain_position = processed_samples + gain_item->start;

    if (remaining_samples < sub_frame_samples)
      current_unit_samples = remaining_samples;

    for (int j = 0; j < queue_length(gain_item->metadatas); j++) {
      value_wrap_t *metadata_wrap = queue_at(gain_item->metadatas, j);
      if (!metadata_wrap) continue;

      oar_metadata_t *metadata = def_value_wrap_ptr(metadata_wrap);
      if (!metadata || metadata->type != ck_metadata_gain) continue;

      if (sample_gain_position >= accumulated_duration &&
          sample_gain_position < accumulated_duration + metadata->duration) {
        float gain_value = 1.0f;

        if (metadata->gain.param_type == ck_param_constant) {
          gain_value = metadata->gain.constant_gain;
        } else if (metadata->gain.param_type == ck_param_multiple) {
          uint32_t relative_pos = sample_gain_position - accumulated_duration;
          if (relative_pos < metadata->duration && metadata->gain.gain_array) {
            gain_value = metadata->gain.gain_array[relative_pos];
          } else {
            warning("Gain array out of bounds for metadata id %u",
                    metadata->gain.id);
          }
        } else if (metadata->gain.param_type == ck_param_animated) {
          uint32_t relative_pos = sample_gain_position - accumulated_duration;
          if (metadata->gain.animated_gains.animation_type ==
              ck_animation_type_step)
            gain_value = metadata->gain.animated_gains.data.start;
          else if (metadata->gain.animated_gains.animation_type ==
                   ck_animation_type_linear) {
            gain_value = animated_bezier_linear_calculate(
                &metadata->gain.animated_gains.data,
                bezier_linear_factor_get(metadata->duration, relative_pos));
          } else if (metadata->gain.animated_gains.animation_type ==
                     ck_animation_type_bezier) {
            gain_value = animated_bezier_quadratic_calculate(
                &metadata->gain.animated_gains.data,
                bezier_quadratic_factor_get(
                    0,
                    metadata->gain.animated_gains.data.control_relative_time *
                            metadata->duration +
                        0.5f,
                    metadata->duration, relative_pos));
          }
        }

        cumulative_gain *= gain_value;
        break;
      }

      accumulated_duration += metadata->duration;
    }

    // Apply the cumulative gain to the current unit
    for (uint32_t ch = 0; ch < block->channels; ch++) {
      uint32_t sample_index = ch * block->samples_per_channel;
      for (uint32_t k = processed_samples;
           k < processed_samples + current_unit_samples; k++) {
        block->data[sample_index + k] *= cumulative_gain;
      }
    }

    processed_samples += current_unit_samples;
  }

  return ck_oar_ok;
}

oar_metadata_t *metadata_constant_polar_positions_create(
    const oar_metadata_t *metadata, uint32_t relative_pos, uint32_t duration) {
  oar_metadata_t *constant_metadata = def_mallocz(oar_metadata_t, 1);
  if (!constant_metadata) return NULL;

  constant_metadata->type = ck_metadata_object_positions;
  constant_metadata->duration = duration;
  constant_metadata->object_positions.param_type = ck_param_constant;
  constant_metadata->object_positions.position_type = ck_polar;
  constant_metadata->object_positions.num_objects =
      metadata->object_positions.num_objects;

  if (metadata->object_positions.param_type == ck_param_constant) {
    for (uint32_t obj_idx = 0; obj_idx < metadata->object_positions.num_objects;
         obj_idx++) {
      if (metadata->object_positions.position_type == ck_polar) {
        constant_metadata->object_positions.polar_positions[obj_idx] =
            metadata->object_positions.polar_positions[obj_idx];
      } else {
        cartesian_t cartesian_pos =
            metadata->object_positions.cartesian_positions[obj_idx];
        constant_metadata->object_positions.polar_positions[obj_idx] =
            cartesian_to_polar_sector_float32(cartesian_pos);
      }
    }
  } else if (metadata->object_positions.param_type == ck_param_animated) {
    // Calculate animated positions for this specific sample
    for (uint32_t obj_idx = 0; obj_idx < metadata->object_positions.num_objects;
         obj_idx++) {
      if (metadata->object_positions.position_type == ck_polar) {
        const animated_data_float32_t *azimuth_anim =
            &metadata->object_positions.animated_polar_positions[obj_idx]
                 .azimuth;
        const animated_data_float32_t *elevation_anim =
            &metadata->object_positions.animated_polar_positions[obj_idx]
                 .elevation;
        const animated_data_float32_t *distance_anim =
            &metadata->object_positions.animated_polar_positions[obj_idx]
                 .distance;

        if (metadata->object_positions.animated_polar_positions[obj_idx]
                .animation_type == ck_animation_type_step) {
          constant_metadata->object_positions.polar_positions[obj_idx].azimuth =
              azimuth_anim->start;
          constant_metadata->object_positions.polar_positions[obj_idx]
              .elevation = elevation_anim->start;
          constant_metadata->object_positions.polar_positions[obj_idx]
              .distance = distance_anim->start;
        } else if (metadata->object_positions.animated_polar_positions[obj_idx]
                       .animation_type == ck_animation_type_linear) {
          constant_metadata->object_positions.polar_positions[obj_idx] =
              animated_spherical_linear_calculate_polar(
                  def_polar_instance(azimuth_anim->start, elevation_anim->start,
                                     distance_anim->start),
                  def_polar_instance(azimuth_anim->end, elevation_anim->end,
                                     distance_anim->end),
                  bezier_linear_factor_get(metadata->duration, relative_pos));
        } else {
          warning("Unsupported animation type (%u) for polar object positions.",
                  metadata->object_positions.animated_polar_positions[obj_idx]
                      .animation_type);
        }

      } else {
        // Handle Cartesian coordinates - convert to polar
        const animated_data_float32_t *x_anim =
            &metadata->object_positions.animated_cartesian_positions[obj_idx].x;
        const animated_data_float32_t *y_anim =
            &metadata->object_positions.animated_cartesian_positions[obj_idx].y;
        const animated_data_float32_t *z_anim =
            &metadata->object_positions.animated_cartesian_positions[obj_idx].z;

        cartesian_t cartesian_pos;

        if (metadata->object_positions.animated_cartesian_positions[obj_idx]
                .animation_type == ck_animation_type_step) {
          cartesian_pos.x = x_anim->start;
          cartesian_pos.y = y_anim->start;
          cartesian_pos.z = z_anim->start;
        } else if (metadata->object_positions
                       .animated_cartesian_positions[obj_idx]
                       .animation_type == ck_animation_type_linear) {
          cartesian_pos.x = animated_bezier_linear_calculate(
              x_anim,
              bezier_linear_factor_get(metadata->duration, relative_pos));
          cartesian_pos.y = animated_bezier_linear_calculate(
              y_anim,
              bezier_linear_factor_get(metadata->duration, relative_pos));
          cartesian_pos.z = animated_bezier_linear_calculate(
              z_anim,
              bezier_linear_factor_get(metadata->duration, relative_pos));
        } else if (metadata->object_positions
                       .animated_cartesian_positions[obj_idx]
                       .animation_type == ck_animation_type_bezier) {
          cartesian_pos.x = animated_bezier_quadratic_calculate(
              x_anim,
              bezier_quadratic_factor_get(
                  0, x_anim->control_relative_time * metadata->duration + 0.5f,
                  metadata->duration, relative_pos));
          cartesian_pos.y = animated_bezier_quadratic_calculate(
              y_anim,
              bezier_quadratic_factor_get(
                  0, y_anim->control_relative_time * metadata->duration + 0.5f,
                  metadata->duration, relative_pos));
          cartesian_pos.z = animated_bezier_quadratic_calculate(
              z_anim,
              bezier_quadratic_factor_get(
                  0, z_anim->control_relative_time * metadata->duration + 0.5f,
                  metadata->duration, relative_pos));
        } else {
          warning(
              "Unsupported animation type (%u) for cartesian object positions.",
              metadata->object_positions.animated_cartesian_positions[obj_idx]
                  .animation_type);
        }

        // Convert the calculated Cartesian position to Polar
        constant_metadata->object_positions.polar_positions[obj_idx] =
            cartesian_to_polar_sector_float32(cartesian_pos);
      }
    }
  }

  return constant_metadata;
}

void audio_renderer_base_uninit(audio_renderer_base_t *base) {
  if (!base) return;

  if (base->lib && base->lib->close) base->lib->close(&base->ctx);
  if (base->block.data) def_free(base->block.data);
  memset(base, 0, sizeof(audio_renderer_base_t));
}

void audio_renderer_delete(audio_renderer_base_t *base) {
  if (base && base->impl && base->impl->destroy) base->impl->destroy(base);
}

int audio_renderer_enable_head_tracking(audio_renderer_base_t *base,
                                        int enable) {
  int ret = ck_oar_error_notsup;

  if (!base) return ck_oar_error_inval;
  if (base->ctx.out != ck_rio_id_binaural) return ck_oar_error_notsup;

  if (base->lib && base->lib->set_attribute) {
    ret = base->lib->set_attribute(&base->ctx, ck_attribute_head_tracking,
                                   &enable);
    if (ret != ck_oar_ok) {
      warning("Failed to set head tracking for renderer %s", base->lib->id);
    }
  }

  return ret;
}

int audio_renderer_set_head_rotation(audio_renderer_base_t *base,
                                     const quaternion_t *rotation) {
  int ret = ck_oar_error_notsup;

  if (!base || !rotation) return ck_oar_error_inval;
  if (base->ctx.out != ck_rio_id_binaural) return ck_oar_error_notsup;

  // Set head rotation for the main renderer context
  if (base->lib && base->lib->metadata_update) {
    oar_metadata_t metadata;
    metadata.type = ck_metadata_head_rotation;
    metadata.head_rotation = *rotation;
    metadata.duration = 0;  // Not used for head rotation

    ret = base->lib->metadata_update(&base->ctx, 0, &metadata);
  }

  return ret;
}
