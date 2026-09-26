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
 * @file ear.c
 * @brief A audio renderer.
 * @version 2.0.0
 * @date Created 20/03/2025
 **/

#include "ear.h"

#include <stdlib.h>
#include <string.h>

#include "ae_rdr.h"
#include "definitions.h"
#include "renderer_library_api.h"
#include "renderer_library_utils.h"

#define def_max_channels 25
#define def_lfe_none -1

typedef struct EarRenderer {
  int scene;
  union {
    struct m2m_rdr_t mmm;
    struct h2m_rdr_t hmm;
  };
  int channels_map[def_max_channels];
  IAMF_SP_LAYOUT out_sp_layout;
} ear_renderer_t;

static int _close(renderer_library_context_t *ctx);

static uint32_t _get_ambisionisc_order(rid_t rid) {
  switch (rid) {
    case ck_ri_zoa:
      return 0;
    case ck_ri_1oa:
      return 1;
    case ck_ri_2oa:
      return 2;
    case ck_ri_3oa:
      return 3;
    case ck_ri_4oa:
      return 4;
    default:
      return UINT32_MAX;
  }
}

typedef struct {
  IAMF_SOUND_SYSTEM sound_system;
  rid_t rid;
} sound_system_rid_t;

static sound_system_rid_t _out_sound_system_rid_map[] = {
    {BS2051_A, ck_rio_id_sound_system_a_020},
    {BS2051_B, ck_rio_id_sound_system_b_050},
    {BS2051_C, ck_rio_id_sound_system_c_250},
    {BS2051_D, ck_rio_id_sound_system_d_450},
    {BS2051_E, ck_ro_id_sound_system_e_451},
    {BS2051_F, ck_ro_id_sound_system_f_370},
    {BS2051_G, ck_ro_id_sound_system_g_490},
    {BS2051_H, ck_rio_id_sound_system_h_9a3},
    {BS2051_I, ck_rio_id_sound_system_i_070},
    {BS2051_J, ck_rio_id_sound_system_j_470},
    {IAMF_MONO, ck_rio_id_mono},
    {IAMF_712, ck_rio_id_712},
    {IAMF_312, ck_rio_id_312},
    {IAMF_BINAURAL, ck_rio_id_binaural},
    {IAMF_916, ck_rio_id_916},
    {IAMF_7154, ck_rio_id_7154},
};

static IAMF_SOUND_SYSTEM _out_sound_system_get(rid_t rid) {
  for (int i = 0;
       i < sizeof(_out_sound_system_rid_map) / sizeof(sound_system_rid_t); i++)
    if (_out_sound_system_rid_map[i].rid == rid)
      return _out_sound_system_rid_map[i].sound_system;
  return IAMF_BS2051_NONE;
}

static sound_system_rid_t _in_sound_system_rid_map[] = {
    {IAMF_MONO, ck_rio_id_mono},
    {IAMF_STEREO, ck_rio_id_stereo},
    {IAMF_51, ck_rio_id_51},
    {IAMF_512, ck_rio_id_512},
    {IAMF_514, ck_rio_id_514},
    {IAMF_71, ck_rio_id_71},
    {IAMF_712, ck_rio_id_712},
    {IAMF_714, ck_rio_id_714},
    {IAMF_312, ck_rio_id_312},
    {IAMF_916, ck_rio_id_916},
    {IAMF_BINAURAL, ck_rio_id_binaural},
    {IAMF_7154, ck_rio_id_7154},
    {IAMF_A293, ck_rio_id_a293},
};

static IAMF_SOUND_SYSTEM _in_sound_system_get(rid_t rid) {
  for (int i = 0;
       i < sizeof(_in_sound_system_rid_map) / sizeof(sound_system_rid_t); i++)
    if (_in_sound_system_rid_map[i].rid == rid)
      return _in_sound_system_rid_map[i].sound_system;
  return IAMF_BS2051_NONE;
}

static const struct {
  rid_t rid;
  int lfe1;
  int lfe2;
} _rid_lfe_index_map[] = {
    {ck_rio_id_51, 3, def_lfe_none},
    {ck_rio_id_512, 3, def_lfe_none},
    {ck_rio_id_514, 3, def_lfe_none},
    {ck_rio_id_71, 3, def_lfe_none},
    {ck_rio_id_712, 3, def_lfe_none},
    {ck_rio_id_714, 3, def_lfe_none},
    {ck_rio_id_312, 3, def_lfe_none},
    {ck_ro_id_sound_system_e_451, 3, def_lfe_none},
    {ck_ro_id_sound_system_f_370, 10, 11},
    {ck_ro_id_sound_system_g_490, 3, def_lfe_none},
    {ck_rio_id_sound_system_h_9a3, 3, 9},
    {ck_rio_id_916, 3, def_lfe_none},
    {ck_rio_id_7154, 3, def_lfe_none},
};

static int _lfe1_get(rid_t rid) {
  for (int i = 0;
       i < sizeof(_rid_lfe_index_map) / sizeof(_rid_lfe_index_map[0]); i++)
    if (_rid_lfe_index_map[i].rid == rid) return _rid_lfe_index_map[i].lfe1;
  return def_lfe_none;
}

int _lfe2_get(rid_t rid) {
  for (int i = 0;
       i < sizeof(_rid_lfe_index_map) / sizeof(_rid_lfe_index_map[0]); i++)
    if (_rid_lfe_index_map[i].rid == rid) return _rid_lfe_index_map[i].lfe2;
  return def_lfe_none;
}

static const struct {
  rid_t rid;
  uint64_t sp_labels;
} _rid_sp_labels_map[] = {
    {ck_ri_id_lfe, SP_LFE1},
    {ck_ri_id_stereo_s, SP_MP110 | SP_MM110},
    {ck_ri_id_stereo_ss, SP_MP090 | SP_MM090},
    {ck_ri_id_stereo_rs, SP_MP135 | SP_MM135},
    {ck_ri_id_stereo_tf, SP_UP045 | SP_UM045},
    {ck_ri_id_stereo_tb, SP_UP135 | SP_UM135},
    {ck_ri_id_top_4ch, SP_UP045 | SP_UM045 | SP_UP135 | SP_UM135},
    {ck_ri_id_3ch, SP_MP030 | SP_MP000 | SP_MM030},
    {ck_ri_id_stereo_f, SP_MP060 | SP_MM060},
    {ck_ri_id_stereo_si, SP_MP090 | SP_MM090},
    {ck_ri_id_stereo_tpsi, SP_UP090 | SP_UM090},
    {ck_ri_id_top_6ch,
     SP_UP045 | SP_UM045 | SP_UP090 | SP_UM090 | SP_UP135 | SP_UM135},
    {ck_ri_id_lfe_pair, SP_LFE1 | SP_LFE2},
    {ck_ri_id_bottom_3ch, SP_BP000 | SP_BP045 | SP_BM045},
    {ck_ri_id_bottom_4ch, SP_BP045 | SP_BM045 | SP_BP135 | SP_BM135},
    {ck_ri_id_top_1ch, SP_TP000},
    {ck_ri_id_top_5ch, SP_TP000 | SP_UP045 | SP_UM045 | SP_UP135 | SP_UM135},
};

static uint64_t _get_sp_labels(rid_t rid) {
  for (int i = 0;
       i < sizeof(_rid_sp_labels_map) / sizeof(_rid_sp_labels_map[0]); i++)
    if (_rid_sp_labels_map[i].rid == rid)
      return _rid_sp_labels_map[i].sp_labels;
  return 0;
}

static int _is_capable(const renderer_library_context_t *ctx) {
  IAMF_SOUND_SYSTEM out_system = _out_sound_system_get(ctx->out);
  if (out_system == IAMF_BS2051_NONE) {
    return ck_oar_error_inval;
  }

  if (rid_is_hoa(ctx->in)) {
    if (_get_ambisionisc_order(ctx->in) == UINT32_MAX) {
      return ck_oar_error_inval;
    }
  } else {
    rid_t base_rid = rid_get_base(ctx->in);
    if (base_rid == ctx->in) {
      // Full layout input
      IAMF_SOUND_SYSTEM in_system = _in_sound_system_get(ctx->in);
      if (in_system == IAMF_BS2051_NONE) return ck_oar_error_inval;
    } else {
      // Subset layout input
      if (_get_sp_labels(ctx->in) == 0) return ck_oar_error_inval;
    }
  }
  return ck_oar_ok;
}

static int _open(renderer_library_context_t *ctx) {
  ear_renderer_t *ear_renderer = 0;
  IAMF_PREDEFINED_SP_LAYOUT pout;

  int capable = _is_capable(ctx);
  if (capable != ck_oar_ok) return capable;

  if (ctx->renderer) _close(ctx);

  ear_renderer = def_mallocz(ear_renderer_t, 1);
  if (!ear_renderer) return ck_oar_error_nomem;

  ctx->renderer = ear_renderer;

  memset(&pout, 0, sizeof(IAMF_PREDEFINED_SP_LAYOUT));
  ear_renderer->out_sp_layout.sp_layout.predefined_sp = &pout;
  pout.system = _out_sound_system_get(ctx->out);
  pout.lfe1 = _lfe1_get(ctx->out);
  pout.lfe2 = _lfe2_get(ctx->out);

  ear_renderer->scene = rid_is_hoa(ctx->in);

  if (!ear_renderer->scene) {
    IAMF_SP_LAYOUT in_sp;
    rid_t base_rid = rid_get_base(ctx->in);

    memset(&in_sp, 0, sizeof(IAMF_SP_LAYOUT));

    if (base_rid == ctx->in) {
      IAMF_PREDEFINED_SP_LAYOUT pin;

      memset(&pin, 0, sizeof(IAMF_PREDEFINED_SP_LAYOUT));
      in_sp.sp_layout.predefined_sp = &pin;
      pin.system = _in_sound_system_get(ctx->in);
      pin.lfe1 = _lfe1_get(ctx->in);
      pin.lfe2 = _lfe2_get(ctx->in);

      IAMF_element_renderer_get_M2M_matrix(&in_sp, &ear_renderer->out_sp_layout,
                                           &ear_renderer->mmm);
      in_sp.sp_layout.predefined_sp = 0;
    } else {
      IAMF_CUSTOM_SP_LAYOUT cin;

      memset(&cin, 0, sizeof(IAMF_CUSTOM_SP_LAYOUT));
      in_sp.sp_layout.custom_sp = &cin;
      cin.system = _in_sound_system_get(base_rid);
      cin.sp_flags = _get_sp_labels(ctx->in);
      in_sp.sp_type = 1;
      IAMF_element_renderer_get_M2M_custom_matrix(
          &in_sp, &ear_renderer->out_sp_layout, &ear_renderer->mmm,
          ear_renderer->channels_map);
      in_sp.sp_layout.custom_sp = 0;
    }
  } else {
    IAMF_HOA_LAYOUT hin;
    hin.order = _get_ambisionisc_order(ctx->in);
#if DISABLE_LFE_HOA == 1
    hin.lfe_on = 0;
    IAMF_element_renderer_get_H2M_matrix(
        &hin, ear_renderer->out_sp_layout.sp_layout.predefined_sp,
        &ear_renderer->hmm);
#else

    hin.lfe_on = 1;
    IAMF_element_renderer_get_H2M_matrix(
        &hin, ear_renderer->out_sp_layout.sp_layout.predefined_sp,
        &ear_renderer->hmm);

    if (hin.lfe_on && out_info && out_info->lfe1) {
      lfe_filter_t *plfe = &ear_renderer->out_sp_layout.lfe_f;
      if (plfe->init == 0) lfefilter_init(plfe, 120, ctx->sample_rate);
    }
#endif
  }
  return 0;
}

static int _render(renderer_library_context_t *ctx, const oar_audio_block_t *in,
                   oar_audio_block_t *out) {
  ear_renderer_t *ear_renderer = ctx->renderer;
  float *fin[BS2051_MAX_CHANNELS];
  float *fout[BS2051_MAX_CHANNELS];
  uint32_t num_samples = in->samples_per_channel;

  for (int c = 0; c < in->channels; ++c) fin[c] = in->data + c * num_samples;
  for (int c = 0; c < out->channels; ++c) fout[c] = out->data + c * num_samples;

  if (!ear_renderer->scene) {
    rid_t base_rid = rid_get_base(ctx->in);
    if (base_rid == ctx->in) {
      IAMF_element_renderer_render_M2M(&ear_renderer->mmm, fin, fout,
                                       num_samples);
    } else {
      IAMF_element_renderer_render_M2M_custom(&ear_renderer->mmm, fin, fout,
                                              num_samples,
                                              ear_renderer->channels_map);
    }
  } else {
    IAMF_element_renderer_render_H2M(&ear_renderer->hmm, fin, fout, num_samples,
                                     &ear_renderer->out_sp_layout.lfe_f);
  }

  return ck_oar_ok;
}

int _close(renderer_library_context_t *ctx) {
  def_free(ctx->renderer);
  ctx->renderer = 0;
  return 0;
}

const renderer_library_api_t iamf_ear_renderer = {
    .id = def_ear_renderer_library_api_id,
    .priority = 255,
    .is_capable = _is_capable,
    .open = _open,
    .render = _render,
    .close = _close,
};

const renderer_library_api_t *ear_get_api(void) { return &iamf_ear_renderer; }
