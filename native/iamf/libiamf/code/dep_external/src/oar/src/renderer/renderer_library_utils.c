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

#include "renderer_library_utils.h"

int rid_check(rid_t rid) {
  if (rid == ck_rid_none) {
    return ck_oar_error_inval;
  }

  switch (rid) {
    case ck_rio_id_mono:
    case ck_rio_id_stereo:
    case ck_rio_id_51:
    case ck_rio_id_512:
    case ck_rio_id_514:
    case ck_rio_id_71:
    case ck_rio_id_712:
    case ck_rio_id_714:
    case ck_rio_id_312:
    case ck_rio_id_916:
    case ck_rio_id_a293:
    case ck_rio_id_7154:
    case ck_ro_id_sound_system_e_451:
    case ck_ro_id_sound_system_f_370:
    case ck_ro_id_sound_system_g_490:
    case ck_ri_id_oa_mono:
    case ck_ri_id_oa_dual:
    case ck_rio_id_binaural:
    case ck_ri_zoa:
    case ck_ri_1oa:
    case ck_ri_2oa:
    case ck_ri_3oa:
    case ck_ri_4oa:
    case ck_ri_id_iamf:
    case ck_ri_id_lfe:
    case ck_ri_id_stereo_s:
    case ck_ri_id_stereo_ss:
    case ck_ri_id_stereo_rs:
    case ck_ri_id_stereo_tf:
    case ck_ri_id_stereo_tb:
    case ck_ri_id_top_4ch:
    case ck_ri_id_3ch:
    case ck_ri_id_stereo_f:
    case ck_ri_id_stereo_si:
    case ck_ri_id_stereo_tpsi:
    case ck_ri_id_top_6ch:
    case ck_ri_id_lfe_pair:
    case ck_ri_id_bottom_3ch:
    case ck_ri_id_bottom_4ch:
    case ck_ri_id_top_1ch:
    case ck_ri_id_top_5ch:
      return ck_oar_ok;
    default:
      return ck_oar_error_notsup;
  }
}

int sub_rid_check(rid_t rid) {
  if (rid > ck_ri_id_iamf && rid < ck_ri_id_iamf_end) return ck_oar_ok;
  return ck_oar_error_inval;
}

static rid_t base_rid_map[][2] = {
    {ck_ri_id_lfe, ck_rio_id_714},
    {ck_ri_id_stereo_s, ck_rio_id_514},
    /** az 85~110, el 0 */
    {ck_ri_id_stereo_ss, ck_rio_id_714},
    {ck_ri_id_stereo_rs, ck_rio_id_714},
    {ck_ri_id_stereo_tf, ck_rio_id_714},
    {ck_ri_id_stereo_tb, ck_rio_id_714},
    {ck_ri_id_top_4ch, ck_rio_id_714},
    {ck_ri_id_3ch, ck_rio_id_714},
    {ck_ri_id_stereo_f, ck_rio_id_916},
    /** az 90, el 0~15 */
    {ck_ri_id_stereo_si, ck_rio_id_916},
    {ck_ri_id_stereo_tpsi, ck_rio_id_916},
    {ck_ri_id_top_6ch, ck_rio_id_916},
    {ck_ri_id_lfe_pair, ck_rio_id_a293},
    {ck_ri_id_bottom_3ch, ck_rio_id_a293},
    {ck_ri_id_bottom_4ch, ck_rio_id_7154},
    {ck_ri_id_top_1ch, ck_rio_id_7154},
    {ck_ri_id_top_5ch, ck_rio_id_7154},
};

rid_t rid_get_base(rid_t rid) {
  if (rid_check(rid) != ck_oar_ok) return ck_rid_none;
  if (sub_rid_check(rid) == ck_oar_ok) {
    for (int i = 0; i < sizeof(base_rid_map) / sizeof(base_rid_map[0]); ++i) {
      if (base_rid_map[i][0] == rid) return base_rid_map[i][1];
    }
  }
  return rid;
}

int rid_is_hoa(rid_t rid) {
  return rid >= ck_ri_zoa && rid <= ck_ri_4oa ? 1 : 0;
}
