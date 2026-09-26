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
 * @file downmix.c
 * @brief A audio renderer.
 * @version 2.0.0
 * @date Created 20/03/2025
 **/

#include "downmix.h"

#include <stdio.h>

#include "cqueue.h"
#include "definitions.h"
#include "downmix_renderer.h"
#include "oar_utils.h"
#include "renderer_library_api.h"

#define def_dm_max_channel_number 12

struct {
  dm_layout_t layout;
  rid_t rid;
} dm_layout_rid_map[] = {
    {ck_dm_layout_mono, ck_rio_id_mono},
    {ck_dm_layout_stereo, ck_rio_id_stereo},
    {ck_dm_layout_510, ck_rio_id_51},
    {ck_dm_layout_512, ck_rio_id_512},
    {ck_dm_layout_514, ck_rio_id_514},
    {ck_dm_layout_710, ck_rio_id_71},
    {ck_dm_layout_712, ck_rio_id_712},
    {ck_dm_layout_714, ck_rio_id_714},
    {ck_dm_layout_312, ck_rio_id_312},
};

typedef struct LibdownmixRenderer {
  DMRenderer *downmixer;
  queue_t *downmix_info;
  int metadata_duration;
} libdownmix_renderer_t;

static dm_layout_t _get_dm_layout_type(rid_t id) {
  for (size_t i = 0; i < sizeof(dm_layout_rid_map) / sizeof(*dm_layout_rid_map);
       ++i)
    if (dm_layout_rid_map[i].rid == id) return dm_layout_rid_map[i].layout;
  return ck_dm_layout_none;
}

static int _close(renderer_library_context_t *ctx);

static int _open(renderer_library_context_t *ctx) {
  libdownmix_renderer_t *renderer = 0;

  dm_layout_t in = _get_dm_layout_type(ctx->in);
  dm_layout_t out = _get_dm_layout_type(ctx->out);

  if (in == ck_dm_layout_none || out == ck_dm_layout_none)
    return ck_oar_error_inval;

  if (~ctx->parameters.flags & def_parameter_set_flag_iamf_downmix_info)
    return ck_oar_error_inval;

  renderer = def_mallocz(libdownmix_renderer_t, 1);
  if (!renderer) return ck_oar_error_nomem;

  ctx->renderer = renderer;

  renderer->downmix_info = queue_new();
  if (!renderer->downmix_info) {
    _close(ctx);
    return ck_oar_error_nomem;
  }

  renderer->downmixer = DMRenderer_open(in, out);
  if (!renderer->downmixer) {
    _close(ctx);
    return ck_oar_error_notsup;
  }

  if (DMRenderer_set_mode_weight(
          renderer->downmixer, ctx->parameters.downmix_info.mode,
          ctx->parameters.downmix_info.weight_index) < 0) {
    _close(ctx);
    return ck_oar_error_notsup;
  }

  return ck_oar_ok;
}

static int _metadata_update(renderer_library_context_t *ctx, uint32_t index,
                            const oar_metadata_t *metadata) {
  oar_metadata_t *metadata_copy = 0;
  libdownmix_renderer_t *renderer = ctx->renderer;

  if (metadata->type != ck_metadata_iamf_downmix_mode)
    return ck_oar_error_notsup;

  metadata_copy = def_mallocz(oar_metadata_t, 1);
  if (!metadata_copy) return ck_oar_error_nomem;
  *metadata_copy = *metadata;
  queue_push(renderer->downmix_info,
             def_value_wrap_instance_ptr(metadata_copy));
  return ck_oar_ok;
}

static int _render(renderer_library_context_t *ctx, const oar_audio_block_t *in,
                   oar_audio_block_t *out) {
  libdownmix_renderer_t *renderer = ctx->renderer;
  float *fin[def_dm_max_channel_number];
  float *fout[def_dm_max_channel_number];
  uint32_t num_samples = in->samples_per_channel;
  uint32_t offset = 0;

  while (offset < num_samples) {
    uint32_t samples = num_samples - offset;

    if (renderer->metadata_duration <= 0 &&
        !queue_is_empty(renderer->downmix_info)) {
      value_wrap_t v;
      oar_metadata_t *metadata = 0;
      queue_pop(renderer->downmix_info, &v);
      metadata = (oar_metadata_t *)v.ptr;
      DMRenderer_set_mode_weight(renderer->downmixer,
                                 metadata->iamf_downmix_mode.mode, -1);
      renderer->metadata_duration = metadata->duration;
      def_free(metadata);
    }

    if (renderer->metadata_duration > 0)
      samples = def_min(renderer->metadata_duration, num_samples - offset);

    for (int c = 0; c < in->channels; ++c)
      fin[c] = (float *)in->data + c * num_samples + offset;
    for (int c = 0; c < out->channels; ++c)
      fout[c] = (float *)out->data + c * num_samples + offset;

    DMRenderer_downmix(renderer->downmixer, fin, fout, samples);

    renderer->metadata_duration -= samples;
    offset += samples;
  }

  return ck_oar_ok;
}

int _close(renderer_library_context_t *ctx) {
  libdownmix_renderer_t *renderer = ctx->renderer;
  if (renderer) {
    if (renderer->downmixer) DMRenderer_close(renderer->downmixer);
    if (renderer->downmix_info) queue_free(renderer->downmix_info, free);
    def_free(renderer);
    ctx->renderer = 0;
  }
  return ck_oar_ok;
}

const renderer_library_api_t iamf_downmix = {
    .id = def_downmix_renderer_library_api_id,
    .priority = 254,
    .open = _open,
    .metadata_update = _metadata_update,
    .render = _render,
    .close = _close,
};

const renderer_library_api_t *downmix_get_api(void) { return &iamf_downmix; }
