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

#include "layout.h"

#include "utils.h"

static speaker_position_t g_sp_mp000 = {"M+000", 0.0, 0.0, 0};

// Screen edge speakers for Sound System G in BS.2094
static speaker_position_t g_sp_mp025 = {"M+025", 25.0, 0.0,
                                        0};  // Left Screen Edge
static speaker_position_t g_sp_mm025 = {"M-025", -25.0, 0.0,
                                        0};  // Right Screen Edge

static speaker_position_t g_sp_mm030 = {"M-030", -30.0, 0.0, 0};
static speaker_position_t g_sp_mp030 = {"M+030", 30.0, 0.0, 0};
static speaker_position_t g_sp_mm060 = {"M-060", -60.0, 0.0, 0};
static speaker_position_t g_sp_mp060 = {"M+060", 60.0, 0.0, 0};
static speaker_position_t g_sp_mm090 = {"M-090", -90.0, 0.0, 0};
static speaker_position_t g_sp_mp090 = {"M+090", 90.0, 0.0, 0};
static speaker_position_t g_sp_mm110 = {"M-110", -110.0, 0.0, 0};
static speaker_position_t g_sp_mp110 = {"M+110", 110.0, 0.0, 0};
static speaker_position_t g_sp_mm135 = {"M-135", -135.0, 0.0, 0};
static speaker_position_t g_sp_mp135 = {"M+135", 135.0, 0.0, 0};
static speaker_position_t g_sp_mp180 = {"M+180", 180.0, 0.0, 0};

static speaker_position_t g_sp_up000 = {"U+000", 0.0, 30.0, 0};
static speaker_position_t g_sp_um030 = {"U-030", -30.0, 30.0, 0};
static speaker_position_t g_sp_up030 = {"U+030", 30.0, 30.0, 0};
static speaker_position_t g_sp_um045 = {"U-045", -45.0, 30.0, 0};
static speaker_position_t g_sp_up045 = {"U+045", 45.0, 30.0, 0};
static speaker_position_t g_sp_um090 = {"U-090", -90.0, 30.0, 0};
static speaker_position_t g_sp_up090 = {"U+090", 90.0, 30.0, 0};
static speaker_position_t g_sp_um110 = {"U-110", -110.0, 30.0, 0};
static speaker_position_t g_sp_up110 = {"U+110", 110.0, 30.0, 0};
static speaker_position_t g_sp_um135 = {"U-135", -135.0, 30.0, 0};
static speaker_position_t g_sp_up135 = {"U+135", 135.0, 30.0, 0};
static speaker_position_t g_sp_up180 = {"U+180", 180.0, 30.0, 0};

// Centre Height for Sound System F in BS.2094
static speaker_position_t g_sp_uhp180 = {"U+180", 180.0, 45.0, 0};

static speaker_position_t g_sp_bp000 = {"B+000", 0.0, -30.0, 0};
static speaker_position_t g_sp_bm045 = {"B-045", -45.0, -30.0, 0};
static speaker_position_t g_sp_bp045 = {"B+045", 45.0, -30.0, 0};
static speaker_position_t g_sp_bm135 = {"B-135", -135.0, -30.0, 0};
static speaker_position_t g_sp_bp135 = {"B+135", 135.0, -30.0, 0};

static speaker_position_t g_sp_tp000 = {"T+000", 0.0, 90.0, 0};
static speaker_position_t g_sp_lfe1 = {"LFE1", 45.0, -30.0, 1};
static speaker_position_t g_sp_lfe2 = {"LFE2", -45.0, -30.0, 1};

static speaker_layout_t g_speaker_layouts[] = {
    {.name = "layout_1.0.0(mono)",
     .layout = ck_olr_layout_100,
     .num_speakers = 1,
     .speakers = {&g_sp_mp000}},
    {.name = "layout_2.0.0(stereo)",
     .layout = ck_olr_layout_200,
     .num_speakers = 2,
     .speakers = {&g_sp_mp030, &g_sp_mm030}},
    {.name = "layout_5.1.0",
     .layout = ck_olr_layout_510,
     .num_speakers = 6,
     .speakers = {&g_sp_mp030, &g_sp_mm030, &g_sp_mp000, &g_sp_lfe1,
                  &g_sp_mp110, &g_sp_mm110}},
    {.name = "layout_5.1.2",
     .layout = ck_olr_layout_512,
     .num_speakers = 8,
     .speakers = {&g_sp_mp030, &g_sp_mm030, &g_sp_mp000, &g_sp_lfe1,
                  &g_sp_mp110, &g_sp_mm110, &g_sp_up030, &g_sp_um030}},
    {.name = "layout_5.1.4",
     .layout = ck_olr_layout_514,
     .num_speakers = 10,
     .speakers = {&g_sp_mp030, &g_sp_mm030, &g_sp_mp000, &g_sp_lfe1,
                  &g_sp_mp110, &g_sp_mm110, &g_sp_up030, &g_sp_um030,
                  &g_sp_up110, &g_sp_um110}},
    {.name = "layout_7.1.0",
     .layout = ck_olr_layout_710,
     .num_speakers = 8,
     .speakers = {&g_sp_mp030, &g_sp_mm030, &g_sp_mp000, &g_sp_lfe1,
                  &g_sp_mp090, &g_sp_mm090, &g_sp_mp135, &g_sp_mm135}},
    {.name = "layout_7.1.2",
     .layout = ck_olr_layout_712,
     .num_speakers = 10,
     .speakers = {&g_sp_mp030, &g_sp_mm030, &g_sp_mp000, &g_sp_lfe1,
                  &g_sp_mp090, &g_sp_mm090, &g_sp_mp135, &g_sp_mm135,
                  &g_sp_up045, &g_sp_um045}},
    {.name = "layout_7.1.4",
     .layout = ck_olr_layout_714,
     .num_speakers = 12,
     .speakers = {&g_sp_mp030, &g_sp_mm030, &g_sp_mp000, &g_sp_lfe1,
                  &g_sp_mp090, &g_sp_mm090, &g_sp_mp135, &g_sp_mm135,
                  &g_sp_up045, &g_sp_um045, &g_sp_up135, &g_sp_um135}},
    {.name = "layout_3.1.2",
     .layout = ck_olr_layout_312,
     .num_speakers = 6,
     .speakers = {&g_sp_mp030, &g_sp_mm030, &g_sp_mp000, &g_sp_lfe1,
                  &g_sp_up045, &g_sp_um045}},
    {.name = "layout_9.1.6",
     .layout = ck_olr_layout_916,
     .num_speakers = 16,
     .speakers = {&g_sp_mp060, &g_sp_mm060, &g_sp_mp000, &g_sp_lfe1,
                  &g_sp_mp135, &g_sp_mm135, &g_sp_mp030, &g_sp_mm030,
                  &g_sp_mp090, &g_sp_mm090, &g_sp_up045, &g_sp_um045,
                  &g_sp_up135, &g_sp_um135, &g_sp_up090, &g_sp_um090}},
    {.name = "layout_7.1.5.4",
     .layout = ck_olr_layout_7154,
     .num_speakers = 17,
     .speakers = {&g_sp_mp030, &g_sp_mm030, &g_sp_mp000, &g_sp_lfe1,
                  &g_sp_mp090, &g_sp_mm090, &g_sp_mp135, &g_sp_mm135,
                  &g_sp_up045, &g_sp_um045, &g_sp_tp000, &g_sp_up135,
                  &g_sp_um135, &g_sp_bp045, &g_sp_bm045, &g_sp_bp135,
                  &g_sp_bm135}},
    {.name = "layout_10.2.9.3",
     .layout = ck_olr_layout_a293,
     .num_speakers = 24,
     .speakers = {&g_sp_mp060, &g_sp_mm060, &g_sp_mp000, &g_sp_lfe1,
                  &g_sp_mp135, &g_sp_mm135, &g_sp_mp030, &g_sp_mm030,
                  &g_sp_mp180, &g_sp_lfe2,  &g_sp_mp090, &g_sp_mm090,
                  &g_sp_up045, &g_sp_um045, &g_sp_up000, &g_sp_tp000,
                  &g_sp_up135, &g_sp_um135, &g_sp_up090, &g_sp_um090,
                  &g_sp_up180, &g_sp_bp000, &g_sp_bp045, &g_sp_bm045}},
    {.name = "layout_sound_system_e",
     .layout = ck_olr_layout_sound_system_e,
     .num_speakers = 11,
     .speakers = {&g_sp_mp030, &g_sp_mm030, &g_sp_mp000, &g_sp_lfe1,
                  &g_sp_mp110, &g_sp_mm110, &g_sp_up030, &g_sp_um030,
                  &g_sp_up110, &g_sp_um110, &g_sp_bp000}},
    {.name = "layout_sound_system_f",
     .layout = ck_olr_layout_sound_system_f,
     .num_speakers = 12,
     .speakers = {&g_sp_mp000, &g_sp_mp030, &g_sp_mm030, &g_sp_up045,
                  &g_sp_um045, &g_sp_mp090, &g_sp_mm090, &g_sp_mp135,
                  &g_sp_mm135, &g_sp_uhp180, &g_sp_lfe1, &g_sp_lfe2}},
    {.name = "layout_sound_system_g",
     .layout = ck_olr_layout_sound_system_g,
     .num_speakers = 14,
     .speakers = {&g_sp_mp030, &g_sp_mm030, &g_sp_mp000, &g_sp_lfe1,
                  &g_sp_mp090, &g_sp_mm090, &g_sp_mp135, &g_sp_mm135,
                  &g_sp_up045, &g_sp_um045, &g_sp_up135, &g_sp_um135,
                  &g_sp_mp025, &g_sp_mm025}},
};

static speaker_layout_t g_speaker_layouts_without_lft[] = {
    {.name = "layout_1.0.0(mono)",
     .layout = ck_olr_layout_100,
     .num_speakers = 1,
     .speakers = {&g_sp_mp000}},
    {.name = "layout_2.0.0(stereo)",
     .layout = ck_olr_layout_200,
     .num_speakers = 2,
     .speakers = {&g_sp_mp030, &g_sp_mm030}},
    {.name = "layout_5.0.0",
     .layout = ck_olr_layout_510,
     .num_speakers = 5,
     .speakers = {&g_sp_mp030, &g_sp_mm030, &g_sp_mp000, &g_sp_mp110,
                  &g_sp_mm110}},
    {.name = "layout_5.0.2",
     .layout = ck_olr_layout_512,
     .num_speakers = 7,
     .speakers = {&g_sp_mp030, &g_sp_mm030, &g_sp_mp000, &g_sp_mp110,
                  &g_sp_mm110, &g_sp_up030, &g_sp_um030}},
    {.name = "layout_5.0.4",
     .layout = ck_olr_layout_514,
     .num_speakers = 9,
     .speakers = {&g_sp_mp030, &g_sp_mm030, &g_sp_mp000, &g_sp_mp110,
                  &g_sp_mm110, &g_sp_up030, &g_sp_um030, &g_sp_up110,
                  &g_sp_um110}},
    {.name = "layout_7.0.0",
     .layout = ck_olr_layout_710,
     .num_speakers = 7,
     .speakers = {&g_sp_mp030, &g_sp_mm030, &g_sp_mp000, &g_sp_mp090,
                  &g_sp_mm090, &g_sp_mp135, &g_sp_mm135}},
    {.name = "layout_7.0.2",
     .layout = ck_olr_layout_712,
     .num_speakers = 9,
     .speakers = {&g_sp_mp030, &g_sp_mm030, &g_sp_mp000, &g_sp_mp090,
                  &g_sp_mm090, &g_sp_mp135, &g_sp_mm135, &g_sp_up045,
                  &g_sp_um045}},
    {.name = "layout_7.0.4",
     .layout = ck_olr_layout_714,
     .num_speakers = 11,
     .speakers = {&g_sp_mp030, &g_sp_mm030, &g_sp_mp000, &g_sp_mp090,
                  &g_sp_mm090, &g_sp_mp135, &g_sp_mm135, &g_sp_up045,
                  &g_sp_um045, &g_sp_up135, &g_sp_um135}},
    {.name = "layout_3.0.2",
     .layout = ck_olr_layout_312,
     .num_speakers = 5,
     .speakers = {&g_sp_mp030, &g_sp_mm030, &g_sp_mp000, &g_sp_up045,
                  &g_sp_um045}},
    {.name = "layout_9.0.6",
     .layout = ck_olr_layout_916,
     .num_speakers = 15,
     .speakers = {&g_sp_mp060, &g_sp_mm060, &g_sp_mp000, &g_sp_mp135,
                  &g_sp_mm135, &g_sp_mp030, &g_sp_mm030, &g_sp_mp090,
                  &g_sp_mm090, &g_sp_up045, &g_sp_um045, &g_sp_up135,
                  &g_sp_um135, &g_sp_up090, &g_sp_um090}},
    {.name = "layout_7.0.5.4",
     .layout = ck_olr_layout_7154,
     .num_speakers = 16,
     .speakers = {&g_sp_mp030, &g_sp_mm030, &g_sp_mp000, &g_sp_mp090,
                  &g_sp_mm090, &g_sp_mp135, &g_sp_mm135, &g_sp_up045,
                  &g_sp_um045, &g_sp_tp000, &g_sp_up135, &g_sp_um135,
                  &g_sp_bp045, &g_sp_bm045, &g_sp_bp135, &g_sp_bm135}},
    {.name = "layout_10.0.9.3",
     .layout = ck_olr_layout_a293,
     .num_speakers = 22,
     .speakers = {&g_sp_mp060, &g_sp_mm060, &g_sp_mp000, &g_sp_mp135,
                  &g_sp_mm135, &g_sp_mp030, &g_sp_mm030, &g_sp_mp180,
                  &g_sp_mp090, &g_sp_mm090, &g_sp_up045, &g_sp_um045,
                  &g_sp_up000, &g_sp_tp000, &g_sp_up135, &g_sp_um135,
                  &g_sp_up090, &g_sp_um090, &g_sp_up180, &g_sp_bp000,
                  &g_sp_bp045, &g_sp_bm045}},
    {.name = "layout_sound_system_e",
     .layout = ck_olr_layout_sound_system_e,
     .num_speakers = 10,
     .speakers = {&g_sp_mp030, &g_sp_mm030, &g_sp_mp000, &g_sp_mp110,
                  &g_sp_mm110, &g_sp_up030, &g_sp_um030, &g_sp_up110,
                  &g_sp_um110, &g_sp_bp000}},
    {.name = "layout_sound_system_f",
     .layout = ck_olr_layout_sound_system_f,
     .num_speakers = 10,
     .speakers = {&g_sp_mp000, &g_sp_mp030, &g_sp_mm030, &g_sp_up045,
                  &g_sp_um045, &g_sp_mp090, &g_sp_mm090, &g_sp_mp135,
                  &g_sp_mm135, &g_sp_uhp180}},
    {.name = "layout_sound_system_g",
     .layout = ck_olr_layout_sound_system_g,
     .num_speakers = 13,
     .speakers = {&g_sp_mp030, &g_sp_mm030, &g_sp_mp000, &g_sp_mp090,
                  &g_sp_mm090, &g_sp_mp135, &g_sp_mm135, &g_sp_up045,
                  &g_sp_um045, &g_sp_up135, &g_sp_um135, &g_sp_mp025,
                  &g_sp_mm025}},
};

static int cart2xyz(cartesian_position_t *cart, auto_float_t xyz[3]) {
  xyz[0] = cart->x;
  xyz[1] = cart->y;
  xyz[2] = cart->z;
  return 0;
}

int get_speaker_count(const speaker_layout_t *layout) {
  return layout->num_speakers;
}

int is_lfe(speaker_position_t *sp) { return sp->is_lfe; }

const speaker_layout_t *get_layout_without_lfe(olr_layout_t layout) {
  for (int i = 0; i < sizeof(g_speaker_layouts_without_lft) /
                          sizeof(g_speaker_layouts_without_lft[0]);
       i++) {
    if (g_speaker_layouts_without_lft[i].layout == layout) {
      return &g_speaker_layouts_without_lft[i];
    }
  }
  return 0;
}

int get_cartesian_positions(speaker_layout_t *layout,
                            auto_float_t positions[][3], int n) {
  if (n != layout->num_speakers) return -22;
  for (int i = 0; i < layout->num_speakers; i++) {
    cartesian_position_t c = polar_to_cart(layout->speakers[i]->azimuth,
                                           layout->speakers[i]->elevation, 1.0);
    cart2xyz(&c, positions[i]);
  }
  return 0;
}

const speaker_layout_t *get_layout(olr_layout_t layout) {
  for (int i = 0; i < sizeof(g_speaker_layouts) / sizeof(g_speaker_layouts[0]);
       i++) {
    if (g_speaker_layouts[i].layout == layout) {
      return &g_speaker_layouts[i];
    }
  }
  return 0;
}
