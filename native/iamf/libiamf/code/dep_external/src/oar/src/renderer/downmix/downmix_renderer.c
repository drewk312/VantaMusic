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
 * @file downmix_renderer.c
 * @brief DMRenderer.
 * @version 2.0.0
 * @date Created 06/21/2023
 **/

#include "downmix_renderer.h"

#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#include "clog.h"
#include "definitions.h"

#define def_max_channel_number 12

typedef enum {
  IA_CH_INVALID,
  IA_CH_L7,
  IA_CH_R7,
  IA_CH_C,
  IA_CH_LFE,
  IA_CH_SL7,
  IA_CH_SR7,
  IA_CH_BL7,
  IA_CH_BR7,
  IA_CH_HFL,
  IA_CH_HFR,
  IA_CH_HBL,
  IA_CH_HBR,
  IA_CH_MONO,
  IA_CH_L2,
  IA_CH_R2,
  IA_CH_TL,
  IA_CH_TR,
  IA_CH_L3,
  IA_CH_R3,
  IA_CH_SL5,
  IA_CH_SR5,
  IA_CH_HL,
  IA_CH_HR,
  IA_CH_FL,     // M+060 in 9.1.6
  IA_CH_FR,     // M-060 in 9.1.6
  IA_CH_TPSIL,  // U+090
  IA_CH_TPSIR,  // U-090
  IA_CH_COUNT,

  IA_CH_L5 = IA_CH_L7,
  IA_CH_R5 = IA_CH_R7,

  IA_CH_FLC = IA_CH_L7,    // M+030
  IA_CH_FRC = IA_CH_R7,    // M-030
  IA_CH_FC = IA_CH_C,      // M+000
  IA_CH_SIL = IA_CH_SL7,   // M+090
  IA_CH_SIR = IA_CH_SR7,   // M-090
  IA_CH_BL = IA_CH_BL7,    // M+135
  IA_CH_BR = IA_CH_BR7,    // M-135
  IA_CH_TPFL = IA_CH_HFL,  // U+045
  IA_CH_TPFR = IA_CH_HFR,  // U-045
  IA_CH_TPBL = IA_CH_HBL,  // U+135
  IA_CH_TPBR = IA_CH_HBR,  // U-135
  IA_CH_LFE1 = IA_CH_LFE,
} IAChannel;

typedef struct MixFactors {
  float alpha;
  float beta;
  float gamma;
  float delta;
  int w_idx_offset;
} MixFactors;

/* Now define DependOnChannel */
typedef struct DependOnChannel {
  IAChannel ch;
  float s;
  float *sp;
} DependOnChannel;

struct Downmixer {
  int mode;
  int w_idx;
  const IAChannel *chs_in;
  const IAChannel *chs_out;
  int chs_icount;
  int chs_ocount;
  float *chs_data[IA_CH_COUNT];
  DependOnChannel *deps[IA_CH_COUNT];
  MixFactors mix_factors;
};

static DependOnChannel chmono[] = {{IA_CH_R2, 0.5f}, {IA_CH_L2, 0.5}, {0}};
static DependOnChannel chl2[] = {{IA_CH_L3, 1.f}, {IA_CH_C, 0.707}, {0}};
static DependOnChannel chr2[] = {{IA_CH_R3, 1.f}, {IA_CH_C, 0.707}, {0}};
static DependOnChannel chtl[] = {{IA_CH_HL, 1.f}, {IA_CH_SL5}, {0}};
static DependOnChannel chtr[] = {{IA_CH_HR, 1.f}, {IA_CH_SR5}, {0}};
static DependOnChannel chl3[] = {{IA_CH_L5, 1.f}, {IA_CH_SL5}, {0}};
static DependOnChannel chr3[] = {{IA_CH_R5, 1.f}, {IA_CH_SR5}, {0}};
static DependOnChannel chsl5[] = {{IA_CH_SL7}, {IA_CH_BL7}, {0}};
static DependOnChannel chsr5[] = {{IA_CH_SR7}, {IA_CH_BR7}, {0}};
static DependOnChannel chhl[] = {{IA_CH_HFL, 1.f}, {IA_CH_HBL}, {0}};
static DependOnChannel chhr[] = {{IA_CH_HFR, 1.f}, {IA_CH_HBR}, {0}};

static int _valid_downmix_layout(dm_layout_t layout) {
  return layout > ck_dm_layout_none && layout < ck_dm_layout_count;
}

static const struct {
  dm_layout_t layout;
  int height;
  int surround;
  int number_channels;
  IAChannel channels[def_max_channel_number];
} dm_layout_desc[] = {
    {0, 0, 0, 0, {0}},
    {ck_dm_layout_mono, 0, 1, 1, {IA_CH_MONO}},
    {ck_dm_layout_stereo, 0, 2, 2, {IA_CH_L2, IA_CH_R2}},
    {ck_dm_layout_510,
     0,
     5,
     6,
     {IA_CH_L5, IA_CH_R5, IA_CH_C, IA_CH_LFE, IA_CH_SL5, IA_CH_SR5}},
    {ck_dm_layout_512,
     2,
     5,
     8,
     {IA_CH_L5, IA_CH_R5, IA_CH_C, IA_CH_LFE, IA_CH_SL5, IA_CH_SR5, IA_CH_HL,
      IA_CH_HR}},
    {ck_dm_layout_514,
     4,
     5,
     10,
     {IA_CH_L5, IA_CH_R5, IA_CH_C, IA_CH_LFE, IA_CH_SL5, IA_CH_SR5, IA_CH_HFL,
      IA_CH_HFR, IA_CH_HBL, IA_CH_HBR}},
    {ck_dm_layout_710,
     0,
     7,
     8,
     {IA_CH_L7, IA_CH_R7, IA_CH_C, IA_CH_LFE, IA_CH_SL7, IA_CH_SR7, IA_CH_BL7,
      IA_CH_BR7}},
    {ck_dm_layout_712,
     2,
     7,
     10,
     {IA_CH_L7, IA_CH_R7, IA_CH_C, IA_CH_LFE, IA_CH_SL7, IA_CH_SR7, IA_CH_BL7,
      IA_CH_BR7, IA_CH_HL, IA_CH_HR}},
    {ck_dm_layout_714,
     4,
     7,
     12,
     {IA_CH_L7, IA_CH_R7, IA_CH_C, IA_CH_LFE, IA_CH_SL7, IA_CH_SR7, IA_CH_BL7,
      IA_CH_BR7, IA_CH_HFL, IA_CH_HFR, IA_CH_HBL, IA_CH_HBR}},
    {ck_dm_layout_312,
     2,
     3,
     6,
     {IA_CH_L3, IA_CH_R3, IA_CH_C, IA_CH_LFE, IA_CH_TL, IA_CH_TR}}};

static int _get_number_of_height_channels(dm_layout_t layout) {
  if (!_valid_downmix_layout(layout)) return 0;
  return dm_layout_desc[layout].height;
}

static int _get_number_of_surround_channels(dm_layout_t layout) {
  if (!_valid_downmix_layout(layout)) return 0;
  return dm_layout_desc[layout].surround;
}

static int _get_number_of_channels(dm_layout_t layout) {
  if (!_valid_downmix_layout(layout)) return 0;
  return dm_layout_desc[layout].number_channels;
}

static const IAChannel *_get_channels(dm_layout_t layout) {
  if (!_valid_downmix_layout(layout)) return 0;
  return dm_layout_desc[layout].channels;
}

static int _valid_downmix(dm_layout_t in, dm_layout_t out) {
  int hi = _get_number_of_height_channels(in);
  int si = _get_number_of_surround_channels(in);
  int ho = _get_number_of_height_channels(out);
  int so = _get_number_of_surround_channels(out);

  if (hi && !ho) return 0;
  return !(si < so || hi < ho);
}

static float _downmix_channel_data(DMRenderer *self, IAChannel c, int i) {
  DependOnChannel *cs = self->deps[c];
  float sum = 0.f;
  if (self->chs_data[c]) return self->chs_data[c][i];
  if (!self->deps[c]) return 0.f;

  while (cs->ch) {
    if (cs->sp)
      sum += _downmix_channel_data(self, cs->ch, i) * (*cs->sp);
    else
      sum += _downmix_channel_data(self, cs->ch, i) * cs->s;
    ++cs;
  }
  return sum;
}

static float _widx2w_table[11] = {0.0,    0.0179, 0.0391, 0.0658, 0.1038, 0.25,
                                  0.3962, 0.4342, 0.4609, 0.4821, 0.5};

static float _calc_w(int w_idx_offset, int w_idx_prev, int *w_idx) {
  if (w_idx_offset > 0)
    *w_idx = def_min(w_idx_prev + 1, 10);
  else
    *w_idx = def_max(w_idx_prev - 1, 0);

  return _widx2w_table[*w_idx];
}

static float _get_w(int w_idx) {
  if (w_idx < 0)
    return _widx2w_table[0];
  else if (w_idx > 10)
    return _widx2w_table[10];

  return _widx2w_table[w_idx];
}

static int _valid_mix_mode(int mode) {
  return mode >= 0 && mode != 3 && mode < 7;
}

static const MixFactors _mix_factors_mat[] = {
    {1.0, 1.0, 0.707, 0.707, -1},   {0.707, 0.707, 0.707, 0.707, -1},
    {1.0, 0.866, 0.866, 0.866, -1}, {0, 0, 0, 0, 0},
    {1.0, 1.0, 0.707, 0.707, 1},    {0.707, 0.707, 0.707, 0.707, 1},
    {1.0, 0.866, 0.866, 0.866, 1},  {0, 0, 0, 0, 0}};

static const MixFactors *_get_mix_factors(int mode) {
  if (_valid_mix_mode(mode)) return &_mix_factors_mat[mode];
  return 0;
}

DMRenderer *DMRenderer_open(dm_layout_t in, dm_layout_t out) {
  DMRenderer *self = 0;
  if (in == out || !_valid_downmix_layout(in) || !_valid_downmix_layout(out))
    return 0;

  debug("%d downmix to %d", in, out);

  if (!_valid_downmix(in, out)) return 0;

  self = def_mallocz(DMRenderer, 1);
  if (!self) return 0;

  self->chs_in = _get_channels(in);
  self->chs_out = _get_channels(out);
  self->chs_icount = _get_number_of_channels(in);
  self->chs_ocount = _get_number_of_channels(out);

  self->mode = -1;
  self->w_idx = -1;

  self->deps[IA_CH_MONO] = chmono;
  self->deps[IA_CH_L2] = chl2;
  self->deps[IA_CH_R2] = chr2;
  self->deps[IA_CH_L3] = chl3;
  self->deps[IA_CH_R3] = chr3;
  self->deps[IA_CH_SL5] = chsl5;
  self->deps[IA_CH_SR5] = chsr5;
  self->deps[IA_CH_TL] = chtl;
  self->deps[IA_CH_TR] = chtr;
  self->deps[IA_CH_HL] = chhl;
  self->deps[IA_CH_HR] = chhr;

  self->deps[IA_CH_SR5][0].sp = self->deps[IA_CH_SL5][0].sp =
      &self->mix_factors.alpha;
  self->deps[IA_CH_SR5][1].sp = self->deps[IA_CH_SL5][1].sp =
      &self->mix_factors.beta;
  self->deps[IA_CH_L3][1].sp = self->deps[IA_CH_R3][1].sp =
      &self->mix_factors.delta;
  self->deps[IA_CH_HL][1].sp = self->deps[IA_CH_HR][1].sp =
      &self->mix_factors.gamma;

  for (int i = 0; i < self->chs_icount; ++i) self->deps[self->chs_in[i]] = 0;

  return self;
}

void DMRenderer_close(DMRenderer *self) { free(self); }

int DMRenderer_set_mode_weight(DMRenderer *self, int mode, int w_idx) {
  if (!self || !_valid_mix_mode(mode)) return -22;

  if (self->mode != mode) {
    debug("dmixtypenum: %d -> %d", self->mode, mode);
    self->mode = mode;
    self->mix_factors = *_get_mix_factors(mode);
    debug("mode %d: a %f, b %f, g %f, d %f, w index offset %d", mode,
          self->mix_factors.alpha, self->mix_factors.beta,
          self->mix_factors.gamma, self->mix_factors.delta,
          self->mix_factors.w_idx_offset);
  }

  if (w_idx < 0 || w_idx > 10) {
    int new_w_idx;

    _calc_w(self->mix_factors.w_idx_offset, self->w_idx, &new_w_idx);
    debug("weight state index : %d (%f) -> %d (%f)", self->w_idx,
          _get_w(self->w_idx), new_w_idx, _get_w(new_w_idx));
    self->w_idx = new_w_idx;
    if (self->deps[IA_CH_TL] && self->deps[IA_CH_TR])
      self->deps[IA_CH_TL][1].s = self->deps[IA_CH_TR][1].s =
          self->mix_factors.delta * _get_w(new_w_idx);
  } else {
    if (mode != self->mode) debug("set default demixing mode %d", mode);
    if (self->w_idx != w_idx) {
      self->w_idx = w_idx;
      debug("set default weight index %d, value %f", w_idx, _get_w(w_idx));
      if (self->deps[IA_CH_TL] && self->deps[IA_CH_TR]) {
        self->deps[IA_CH_TL][1].s = self->deps[IA_CH_TR][1].s =
            self->mix_factors.delta * _get_w(w_idx);
      }
    }
  }

  return 0;
}

int DMRenderer_downmix(DMRenderer *self, float *in[], float *out[],
                       uint32_t samples) {
  if (!self || !in || !out) return -22;

  memset(self->chs_data, 0, IA_CH_COUNT * sizeof(float));
  for (int i = 0; i < self->chs_icount; ++i) {
    self->chs_data[self->chs_in[i]] = in[i];
  }

  for (int i = 0; i < self->chs_ocount; ++i) {
    debug("channel %d checking...", self->chs_out[i]);
    for (int j = 0; j < samples; ++j) {
      out[i][j] = _downmix_channel_data(self, self->chs_out[i], j);
    }
  }

  return 0;
}
