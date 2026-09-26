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

#include "olr.h"

#include <stdlib.h>

#include "clog.h"
#include "definitions.h"
#include "oar_metadata.h"
#include "object_audio_renderer/object_based_audio_renderer.h"
#include "object_audio_renderer/utils.h"
#include "renderer_library_api.h"

typedef struct OlrContext {
  object_audio_renderer_t *api;
  uint32_t num_objects;
  olr_layout_t layout;
  metadata_block_t *metadata_blocks;
  uint64_t *metadata_block_durations;
  int offset;
} olr_context_t;

static int _close(renderer_library_context_t *ctx);

static const struct {
  olr_layout_t layout;
  rid_t rid;
} output_layout_map[] = {
    {ck_olr_layout_100, ck_rio_id_mono},
    {ck_olr_layout_200, ck_rio_id_stereo},
    {ck_olr_layout_312, ck_rio_id_312},
    {ck_olr_layout_510, ck_rio_id_51},
    {ck_olr_layout_512, ck_rio_id_512},
    {ck_olr_layout_514, ck_rio_id_514},
    {ck_olr_layout_710, ck_rio_id_71},
    {ck_olr_layout_712, ck_rio_id_712},
    {ck_olr_layout_714, ck_rio_id_714},
    {ck_olr_layout_916, ck_rio_id_916},
    {ck_olr_layout_7154, ck_rio_id_7154},
    {ck_olr_layout_a293, ck_rio_id_a293},
    {ck_olr_layout_sound_system_e, ck_ro_id_sound_system_e_451},
    {ck_olr_layout_sound_system_f, ck_ro_id_sound_system_f_370},
    {ck_olr_layout_sound_system_g, ck_ro_id_sound_system_g_490},
};

static olr_layout_t _get_output_layout(rid_t out_rid) {
  for (size_t i = 0;
       i < sizeof(output_layout_map) / sizeof(output_layout_map[0]); i++)
    if (output_layout_map[i].rid == out_rid) return output_layout_map[i].layout;
  return ck_olr_layout_none;
}

static void _olr_context_delete(olr_context_t *olr) {
  if (olr) {
    if (olr->api) object_audio_renderer_destroy(olr->api);
    if (olr->metadata_blocks) def_free(olr->metadata_blocks);
    if (olr->metadata_block_durations) def_free(olr->metadata_block_durations);
    def_free(olr);
  }
}

static int _is_capable(const renderer_library_context_t *ctx) {
  // Check if input is one of the supported object audio types
  if (ctx->in != ck_ri_id_oa_mono && ctx->in != ck_ri_id_oa_dual) {
    info(
        "OLR: Unsupported input type. Only ck_ri_id_oa_mono and "
        "ck_ri_id_oa_dual are supported. Got: %x",
        ctx->in);
    return ck_oar_error_inval;
  }

  // Determine output layout from ctx->out
  olr_layout_t output_layout = _get_output_layout(ctx->out);
  if (output_layout == ck_olr_layout_none) {
    info("OLR: Unsupported output layout. Got: %x", ctx->out);
    return ck_oar_error_inval;
  }

  return ck_oar_ok;
}

static int _open(renderer_library_context_t *ctx) {
  olr_context_t *olr = 0;

  int capable = _is_capable(ctx);
  if (capable != ck_oar_ok) return capable;

  if (ctx->renderer) _close(ctx);

  olr = def_mallocz(olr_context_t, 1);
  if (!olr) return ck_oar_error_nomem;

  olr->layout = _get_output_layout(ctx->out);

  olr->api = object_audio_renderer_create(
      olr->layout, ck_gain_calculator_custom, ctx->sample_rate);
  if (!olr->api) {
    error("OLR: Failed to create object_audio_renderer instance for layout %d.",
          olr->layout);
    def_free(olr);
    return ck_oar_error_notsup;
  }
  ctx->renderer = olr;

  // Determine number of objects based on input type
  if (ctx->in == ck_ri_id_oa_mono)
    olr->num_objects = 1;
  else if (ctx->in == ck_ri_id_oa_dual)
    olr->num_objects = 2;
  else {
    // This case should ideally not be reached due to the check at the beginning
    // of _open but as a fallback:
    olr->num_objects =
        1;  // Default to 1 if somehow an unsupported type gets through
    warning(
        "OLR: Unexpected input type %d in num_objects determination, "
        "defaulting to 1 object.",
        ctx->in);
  }

  if (olr->num_objects > 0) {
    olr->metadata_blocks = def_mallocz(metadata_block_t, olr->num_objects);
    olr->metadata_block_durations = def_mallocz(uint64_t, olr->num_objects);

    if (!olr->metadata_blocks || !olr->metadata_block_durations) {
      _olr_context_delete(olr);
      return ck_oar_error_nomem;
    }

    for (uint32_t i = 0; i < olr->num_objects; i++)
      olr->metadata_blocks[i].track_id = i;
  }

  return ck_oar_ok;
}

static int _set_attribute(renderer_library_context_t *ctx,
                          rendering_attribute_t attr, const void *value) {
  switch (attr) {
    case ck_attribute_head_tracking:
      warning(
          "OLR: Head tracking attribute not directly supported by "
          "object_audio_renderer.");
      break;
    default:
      return ck_oar_error_notsup;
  }
  return ck_oar_ok;
}

static int _metadata_update(renderer_library_context_t *ctx, uint32_t index,
                            const oar_metadata_t *metadata) {
  olr_context_t *olr = ctx->renderer;
  if (!olr || !olr->metadata_blocks) return ck_oar_error_inval;

  switch (metadata->type) {
    case ck_metadata_object_positions: {
      if (metadata->object_positions.param_type != ck_param_constant ||
          metadata->object_positions.position_type != ck_polar) {
        warning("only support constant object positions with polar.");
        return ck_oar_error_notsup;
      }
      uint32_t n = olr->num_objects;
      if (n < metadata->object_positions.num_objects) {
        warning(
            "OLR: Number of objects in metadata (%d) exceeds renderer's "
            "capacity (%d). Clipping.",
            metadata->object_positions.num_objects, n);
      } else {
        n = metadata->object_positions.num_objects;
      }

      for (uint32_t i = 0; i < n; i++) {
        olr->metadata_blocks[i].start = olr->metadata_block_durations[i];
        olr->metadata_blocks[i].duration =
            metadata->duration > 0 ? metadata->duration : 0;
        olr->metadata_block_durations[i] += olr->metadata_blocks[i].duration;

        olr->metadata_blocks[i].azimuth =
            metadata->object_positions.polar_positions[i].azimuth;
        olr->metadata_blocks[i].elevation =
            metadata->object_positions.polar_positions[i].elevation;
        olr->metadata_blocks[i].distance =
            metadata->object_positions.polar_positions[i].distance;
      }

      object_audio_renderer_add_metadatas(olr->api, olr->metadata_blocks, n);
    } break;
    case ck_metadata_head_rotation:
      warning(
          "OLR: Head rotation metadata not directly supported by "
          "object_audio_renderer.");
      break;
    default:
      return ck_oar_error_notsup;
  }
  return ck_oar_ok;
}

static int _render(renderer_library_context_t *ctx, const oar_audio_block_t *in,
                   oar_audio_block_t *out) {
  olr_context_t *olr = ctx->renderer;
  int ret = object_audio_renderer_render(
      olr->api, (float *)in->data, in->samples_per_channel, olr->num_objects,
      olr->offset, (float *)out->data);
  olr->offset += in->samples_per_channel;
  if (ret != ck_oar_ok) warning("OLR: Rendering failed with errno %d.", ret);

  return ret;
}

int _close(renderer_library_context_t *ctx) {
  olr_context_t *olr = ctx->renderer;
  if (olr) {
    if (olr->api) object_audio_renderer_destroy(olr->api);
    if (olr->metadata_blocks) def_free(olr->metadata_blocks);
    if (olr->metadata_block_durations) def_free(olr->metadata_block_durations);
    def_free(olr);
    ctx->renderer = 0;
  }
  return 0;
}

const renderer_library_api_t iamf_olr_renderer = {
    .id = def_olr_renderer_library_api_id,
    .priority = 253,  // Assuming similar priority, can be adjusted
    .is_capable = _is_capable,
    .open = _open,
    .set_attribute = _set_attribute,
    .metadata_update = _metadata_update,
    .render = _render,
    .close = _close,
};

const renderer_library_api_t *olr_get_api(void) { return &iamf_olr_renderer; }
