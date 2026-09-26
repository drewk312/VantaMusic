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
 * @file obr.c
 * @brief A audio renderer.
 * @version 2.0.0
 * @date Created 24/06/2025
 **/

#include "obr.h"

#include <stdlib.h>

#include "clog.h"
#include "definitions.h"
#include "obr_capi/obr_capi.h"
#include "renderer_library_api.h"

typedef struct ObrRenderer {
  obr_handle *api;
} obr_renderer_t;

static int _close(renderer_library_context_t *ctx);

static const struct {
  ObrAudioElementType type;
  rid_t rid;
} audio_element_type_rid_map[] = {
    // Ambisonics types
    {kObr1OA, ck_ri_1oa},
    {kObr2OA, ck_ri_2oa},
    {kObr3OA, ck_ri_3oa},
    {kObr4OA, ck_ri_4oa},

    // Loudspeaker layouts
    {kObrLayoutMono, ck_rio_id_mono},
    {kObrLayoutStereo, ck_rio_id_stereo},
    {kObrLayout5_1_0_ch, ck_rio_id_51},
    {kObrLayout5_1_2_ch, ck_rio_id_512},
    {kObrLayout5_1_4_ch, ck_rio_id_514},
    {kObrLayout7_1_0_ch, ck_rio_id_71},
    {kObrLayout7_1_2_ch, ck_rio_id_712},
    {kObrLayout7_1_4_ch, ck_rio_id_714},
    {kObrLayout3_1_2_ch, ck_rio_id_312},
    {kObrLayout9_1_6_ch, ck_rio_id_916},
    {kObrLayout7_1_5_4_ch, ck_rio_id_7154},
    {kObrLayout10_2_9_3_ch, ck_rio_id_a293},

    // Layout subsets
    {kObrSubsetLFE, ck_ri_id_lfe},
    {kObrSubsetStereo_S, ck_ri_id_stereo_s},
    {kObrSubsetStereo_SS, ck_ri_id_stereo_ss},
    {kObrSubsetStereo_RS, ck_ri_id_stereo_rs},
    {kObrSubsetStereo_TF, ck_ri_id_stereo_tf},
    {kObrSubsetStereo_TB, ck_ri_id_stereo_tb},
    {kObrSubsetTop_4ch, ck_ri_id_top_4ch},
    {kObrSubset3_0ch, ck_ri_id_3ch},
    {kObrSubsetStereo_F, ck_ri_id_stereo_f},
    {kObrSubsetStereo_Si, ck_ri_id_stereo_si},
    {kObrSubsetStereo_TpSi, ck_ri_id_stereo_tpsi},
    {kObrSubsetTop_6ch, ck_ri_id_top_6ch},
    {kObrSubsetLFE_Pair, ck_ri_id_lfe_pair},
    {kObrSubsetBottom_3ch, ck_ri_id_bottom_3ch},
    {kObrSubsetBottom_4ch, ck_ri_id_bottom_4ch},
    {kObrSubsetTop_1ch, ck_ri_id_top_1ch},
    {kObrSubsetTop_5ch, ck_ri_id_top_5ch},

    // Object types
    {kObrObjectMono, ck_ri_id_oa_mono},
    {kObrObjectDual, ck_ri_id_oa_dual},

    // Passthrough types
    {kObrPassthroughMono, ck_ri_zoa},
    {kObrPassthroughStereo, ck_rio_id_binaural},
};

static ObrAudioElementType _get_audio_element_type(rid_t rid) {
  for (size_t i = 0; i < sizeof(audio_element_type_rid_map) /
                             sizeof(audio_element_type_rid_map[0]);
       i++)
    if (audio_element_type_rid_map[i].rid == rid)
      return audio_element_type_rid_map[i].type;
  return kObrInvalidType;
}

static ObrBinauralFilterProfile _get_binaural_filter_profile(
    oar_binaural_filter_profile_t profile) {
  switch (profile) {
    case ck_direct:
      return kObrDirect;
    case ck_ambient:
      return kObrAmbient;
    case ck_reverberant:
      return kObrReverberant;
    default:
      // Default to ambient profile for unknown values
      return kObrAmbient;
  }
}

static int _is_capable(const renderer_library_context_t *ctx) {
  if (ctx->out != ck_rio_id_binaural) return ck_oar_error_inval;
  if (ctx->in != ck_ri_multi_ids &&
      _get_audio_element_type(ctx->in) == kObrInvalidType)
    return ck_oar_error_inval;
  return ck_oar_ok;
}

static int _open(renderer_library_context_t *ctx) {
  obr_renderer_t *obr = 0;

  int capable = _is_capable(ctx);
  if (capable != ck_oar_ok) return capable;

  if (ctx->renderer) _close(ctx);  // Close any existing renderer first.

  obr = def_mallocz(obr_renderer_t, 1);
  if (!obr) return ck_oar_error_nomem;

  obr->api = obr_create(ctx->samples_per_frame, ctx->sample_rate);
  if (!obr->api) {
    def_free(obr);
    return ck_oar_error_notsup;
  }
  obr_enable_limiter(obr->api, 0);

  ctx->renderer = obr;

  return ck_oar_ok;
}

static int _set_attribute(renderer_library_context_t *ctx,
                          rendering_attribute_t attr, const void *value) {
  obr_renderer_t *obr = ctx->renderer;
  switch (attr) {
    case ck_attribute_head_tracking:
      obr_enable_head_tracking(obr->api, *(int *)value);
      break;
    case ck_attribute_add_element: {
      element_parameter_set_t *params = (element_parameter_set_t *)value;
      ObrAudioElementType element_type = _get_audio_element_type(params->id);
      ObrBinauralFilterProfile profile =
          _get_binaural_filter_profile(params->profile);

      if (element_type == kObrInvalidType) {
        warning("Unsupported element rendering id 0x%x", params->id);
        return ck_oar_error_inval;
      }

      if (obr_add_audio_element(obr->api, element_type, profile) < 0) {
        warning("Failed to add element rendering id %d", params->id);
        return ck_oar_error_inval;
      }

    } break;
    case ck_attribute_element_head_locked: {
      element_head_locked_t *params = (element_head_locked_t *)value;
      int num_elements = obr_get_number_of_audio_elements(obr->api);
      if (params->index >= num_elements) {
        warning("Invalid element index %d, element count %d", params->index,
                num_elements);
        return ck_oar_error_inval;
      }
      if (obr_set_element_head_locked(obr->api, params->index,
                                      !!params->locked) < 0) {
        warning("Failed to set head locked state %u for element index %d",
                !!params->locked, params->index);
        return ck_oar_error_inval;
      }
    } break;
    default:
      return ck_oar_error_notsup;
  }
  return ck_oar_ok;
}

static int _metadata_update(renderer_library_context_t *ctx, uint32_t index,
                            const oar_metadata_t *metadata) {
  obr_renderer_t *obr = ctx->renderer;

  switch (metadata->type) {
    case ck_metadata_object_positions: {
      int num_elements = obr_get_number_of_audio_elements(obr->api);
      if (metadata->object_positions.param_type != ck_param_constant ||
          metadata->object_positions.position_type != ck_polar) {
        warning("only support constant object positions with polar.");
        return ck_oar_error_notsup;
      }

      if (index >= num_elements) {
        warning("Invalid element index %d, element count %d", index,
                num_elements);
        return ck_oar_error_inval;
      }

      for (uint32_t i = 0; i < metadata->object_positions.num_objects; i++) {
        obr_update_object_channel_position(
            obr->api, index, i,
            metadata->object_positions.polar_positions[i].azimuth,
            metadata->object_positions.polar_positions[i].elevation,
            metadata->object_positions.polar_positions[i].distance);
      }
    } break;

    case ck_metadata_head_rotation:
      if (obr_set_head_rotation(
              obr->api, metadata->head_rotation.w, metadata->head_rotation.x,
              metadata->head_rotation.y, metadata->head_rotation.z) < 0) {
        warning("Failed to set head rotation for element index %d", index);
        return ck_oar_error_inval;
      }
      break;
    default:
      return ck_oar_error_notsup;
  }
  return ck_oar_ok;
}

static int _render(renderer_library_context_t *ctx, const oar_audio_block_t *in,
                   oar_audio_block_t *out) {
  obr_renderer_t *obr = ctx->renderer;
  if (!obr || !obr->api) return ck_oar_error_inval;
  obr_process(obr->api, in->data, out->data, in->samples_per_channel);
  return ck_oar_ok;
}

int _close(renderer_library_context_t *ctx) {
  obr_renderer_t *obr = ctx->renderer;
  if (obr) {
    if (obr->api) obr_destroy(obr->api);
    def_free(obr);
    ctx->renderer = 0;
  }
  return 0;
}

const renderer_library_api_t iamf_obr_renderer = {
    .id = def_obr_renderer_library_api_id,
    .priority = 253,
    .is_capable = _is_capable,
    .open = _open,
    .set_attribute = _set_attribute,
    .metadata_update = _metadata_update,
    .render = _render,
    .close = _close,
};

const renderer_library_api_t *obr_get_api(void) { return &iamf_obr_renderer; }
