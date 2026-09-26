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

#include "oar_utils.h"

#include <stddef.h>
#include <time.h>

#ifdef __as_dbg__
#include <stdio.h>

#include "dep_wavwriter.h"
#endif

#include <math.h>
#include <string.h>

#include "definitions.h"
#include "oar.h"
#include "renderer/renderer_library_api.h"

#define def_eps 1e-10

static const struct {
  oar_layout_t layout;
  rid_t rid;
  uint32_t number_channels;
} _layout_desc[] = {
    {ck_oar_layout_mono, ck_rio_id_mono, 1},
    {ck_oar_layout_stereo, ck_rio_id_stereo, 2},
    {ck_oar_layout_51, ck_rio_id_51, 6},
    {ck_oar_layout_512, ck_rio_id_512, 8},
    {ck_oar_layout_514, ck_rio_id_514, 10},
    {ck_oar_layout_71, ck_rio_id_71, 8},
    {ck_oar_layout_712, ck_rio_id_712, 10},
    {ck_oar_layout_714, ck_rio_id_714, 12},
    {ck_oar_layout_312, ck_rio_id_312, 6},
    {ck_oar_layout_916, ck_rio_id_916, 16},
    {ck_oar_layout_a293, ck_rio_id_a293, 24},
    {ck_oar_layout_7154, ck_rio_id_7154, 17},
    {ck_oar_layout_sound_system_e_451, ck_ro_id_sound_system_e_451, 11},
    {ck_oar_layout_sound_system_f_370, ck_ro_id_sound_system_f_370, 12},
    {ck_oar_layout_sound_system_g_490, ck_ro_id_sound_system_g_490, 14},
    {ck_oar_layout_binaural, ck_rio_id_binaural, 2},
    {ck_oar_layout_lfe, ck_ri_id_lfe, 1},
    {ck_oar_layout_stereo_s, ck_ri_id_stereo_s, 2},
    {ck_oar_layout_stereo_ss, ck_ri_id_stereo_ss, 2},
    {ck_oar_layout_stereo_rs, ck_ri_id_stereo_rs, 2},
    {ck_oar_layout_stereo_tf, ck_ri_id_stereo_tf, 2},
    {ck_oar_layout_stereo_tb, ck_ri_id_stereo_tb, 2},
    {ck_oar_layout_top_4ch, ck_ri_id_top_4ch, 4},
    {ck_oar_layout_3ch, ck_ri_id_3ch, 3},
    {ck_oar_layout_stereo_f, ck_ri_id_stereo_f, 2},
    {ck_oar_layout_stereo_si, ck_ri_id_stereo_si, 2},
    {ck_oar_layout_stereo_tpsi, ck_ri_id_stereo_tpsi, 2},
    {ck_oar_layout_top_6ch, ck_ri_id_top_6ch, 6},
    {ck_oar_layout_lfe_pair, ck_ri_id_lfe_pair, 2},
    {ck_oar_layout_bottom_3ch, ck_ri_id_bottom_3ch, 3},
    {ck_oar_layout_bottom_4ch, ck_ri_id_bottom_4ch, 4},
    {ck_oar_layout_top_1ch, ck_ri_id_top_1ch, 1},
    {ck_oar_layout_top_5ch, ck_ri_id_top_5ch, 5},
};

static const struct {
  oar_hoa_t hoa;
  rid_t rid;
  uint32_t number_channels;
} _hoa_desc[] = {
    {ck_oar_zoa, ck_ri_zoa, 1},  {ck_oar_1oa, ck_ri_1oa, 4},
    {ck_oar_2oa, ck_ri_2oa, 9},  {ck_oar_3oa, ck_ri_3oa, 16},
    {ck_oar_4oa, ck_ri_4oa, 25},
};

rid_t _layout_to_rid(oar_layout_t layout) {
  for (size_t i = 0; i < sizeof(_layout_desc) / sizeof(_layout_desc[0]); i++) {
    if (_layout_desc[i].layout == layout) {
      return _layout_desc[i].rid;
    }
  }
  return ck_rid_none;
}

ri_id_t audio_element_config_to_ri_id(
    const oar_audio_element_config_t *config) {
  switch (config->type) {
    case ck_channel_based:
      return layout_to_ri_id(config->cbc.layout);
    case ck_scene_based:
      return hoa_to_rid(config->sbc.order);
    case ck_object_based:
      return object_to_rid(config->obc.num_objects);
    default:
      return ck_rid_none;
  }
}

ri_id_t layout_to_ri_id(oar_layout_t layout) {
  rid_t rid = _layout_to_rid(layout);
  return rid & def_ri_flag ? rid : ck_rid_none;
}

ro_id_t layout_to_ro_id(oar_layout_t layout) {
  rid_t rid = _layout_to_rid(layout);
  return rid & def_ro_flag ? rid : ck_rid_none;
}

rid_t hoa_to_rid(oar_hoa_t order) {
  for (size_t i = 0; i < sizeof(_hoa_desc) / sizeof(_hoa_desc[0]); i++) {
    if (_hoa_desc[i].hoa == order) {
      return _hoa_desc[i].rid;
    }
  }
  return ck_rid_none;
}

ri_id_t object_to_rid(int num_objects) {
  switch (num_objects) {
    case 1:
      return ck_ri_id_oa_mono;
    case 2:
      return ck_ri_id_oa_dual;
    default:
      return ck_rid_none;
  }
}

uint32_t layout_channels_count(oar_layout_t layout) {
  for (size_t i = 0; i < sizeof(_layout_desc) / sizeof(_layout_desc[0]); i++)
    if (_layout_desc[i].layout == layout)
      return _layout_desc[i].number_channels;
  return 0;
}

uint32_t rid_channels_count(rid_t rid) {
  for (size_t i = 0; i < sizeof(_layout_desc) / sizeof(_layout_desc[0]); i++)
    if (_layout_desc[i].rid == rid) return _layout_desc[i].number_channels;
  for (size_t i = 0; i < sizeof(_hoa_desc) / sizeof(_hoa_desc[0]); i++)
    if (_hoa_desc[i].rid == rid) return _hoa_desc[i].number_channels;
  if (rid == ck_ri_id_oa_mono) return 1;
  if (rid == ck_ri_id_oa_dual) return 2;
  return 0;
}

cartesian_t normalized_polar_to_cartesian_float32(float azimuth,
                                                  float elevation) {
  return polar_to_cartesian_float32(
      def_polar_instance(azimuth, elevation, 1.0));
}

cartesian_t polar_to_cartesian_float32(polar_t polar) {
  cartesian_t result;

  // Convert angles from degrees to radians
  float azimuth_rad = def_degrees_to_radians(polar.azimuth);
  float elevation_rad = def_degrees_to_radians(polar.elevation);

  result.x = polar.distance * sinf(-azimuth_rad) * cosf(elevation_rad);
  result.y = polar.distance * cosf(-azimuth_rad) * cosf(elevation_rad);
  result.z = polar.distance * sinf(elevation_rad);

  return result;
}

polar_t cartesian_to_polar_float32(cartesian_t cartesian) {
  polar_t result;

  // Calculate distance (magnitude of the vector)
  result.distance =
      sqrtf(cartesian.x * cartesian.x + cartesian.y * cartesian.y +
            cartesian.z * cartesian.z);

  if (result.distance < def_eps) return def_polar_instance(0.0f, 0.0f, 0.0f);

  // Calculate elevation: arcsin(z / distance)
  result.elevation =
      def_radians_to_degrees(asinf(cartesian.z / result.distance));

  // Calculate azimuth: atan2(-x, y) converted to degrees
  result.azimuth = def_radians_to_degrees(atan2f(-cartesian.x, cartesian.y));

  return result;
}

float cartesian_calculate_angle_radians_float32(cartesian_t p1,
                                                cartesian_t p2) {
  // Calculate the dot product of the two vectors
  float dot_product = p1.x * p2.x + p1.y * p2.y + p1.z * p2.z;

  // Calculate the magnitudes of both vectors
  float mag1 = sqrtf(p1.x * p1.x + p1.y * p1.y + p1.z * p1.z);
  float mag2 = sqrtf(p2.x * p2.x + p2.y * p2.y + p2.z * p2.z);

  if (mag1 < def_eps || mag2 < def_eps) return 0.0f;

  // Clamp the dot product to avoid numerical errors with acos
  float cos_angle = dot_product / (mag1 * mag2);
  if (cos_angle > 1.0f) cos_angle = 1.0f;
  if (cos_angle < -1.0f) cos_angle = -1.0f;

  // Calculate the angle in radians and convert to degrees
  return acosf(cos_angle);
}

static float _azimuth(cartesian_t positions) {
  return -def_radians_to_degrees(atan2f(positions.x, positions.y));
}

static int _inside_angle_range(float x, float start, float end, float tol) {
  // end is clockwise from start; if end is start + 360, this rotation is
  // preserved; this makes sure that a range of (-180, 180) or (0, 360) means
  // any angle, while (-180, -180) or (0, 0) means a single angle, even though
  // -180/180 and 0/360 are nominally the same angle
  while (end - 360.0f > start) end -= 360.0f;
  while (end < start) end += 360.0f;

  // assume that x is clockwise from start - tol; if x is exactly
  // start-tol+360, this is resolved to start-tol, so that the comparison with
  // start-tol is >= rather than >
  float start_tol = start - tol;
  while (x - 360.0f >= start_tol) x -= 360.0f;
  while (x < start_tol) x += 360.0f;

  // x is greater than equal to start-tol, so we only need to compare against
  // the end.
  return !!(x <= end + tol);
}

#define def_elevation_top 30.f
#define def_elevation_top_tilde 45.f

static struct {
  float azimuth;
  cartesian_t position;
} _g_sector_mapping[5] = {{0.f, {0.f, 1.f, 0.f}},
                          {-30.f, {1.f, 1.f, 0.f}},
                          {-110.f, {1.f, -1.f, 0.f}},
                          {110.f, {-1.f, -1.f, 0.f}},
                          {30.f, {-1.f, 1.f, 0.f}}};

static int _find_cart_sector(float az, int *left_idx, int *right_idx) {
  for (int i = 0; i < 5; i++) {
    int j = (i + 1) % 5;
    if (_inside_angle_range(az, _azimuth(_g_sector_mapping[j].position),
                            _azimuth(_g_sector_mapping[i].position), 0.0f)) {
      *left_idx = i;
      *right_idx = j;
      return 0;
    }
  }
  return ck_oar_error_inval;
}

static float _relative_angle(float x, float y) {
  while (y - 360.0f >= x) y -= 360.0f;
  while (y < x) y += 360.0f;
  return y;
}

static float _map_linear_to_az(float left_az, float right_az, float x) {
  float mid_az = (left_az + right_az) / 2.0f;
  float az_range = right_az - mid_az;
  float gain_l_ = cosf(x * (M_PI / 2.0f));
  float gain_r_ = sinf(x * (M_PI / 2.0f));
  float gain_r = gain_r_ / (gain_l_ + gain_r_);
  float rel_az = def_radians_to_degrees(
      atanf(2.0f * (gain_r - 0.5f) * tanf(def_degrees_to_radians(az_range))));
  return mid_az + rel_az;
}

static int _sign(float x) { return x > 0 ? 1 : (x < 0 ? -1 : 0); }

static int _linalg_inv_2x2(const float a[4], float inv_a[4]) {
  float det = a[0] * a[3] - a[1] * a[2];
  float inv_det = 0;

  if (fabsf(det) < def_eps) return ck_oar_error_inval;

  inv_det = 1.0 / det;

  inv_a[0] = a[3] * inv_det;
  inv_a[1] = -a[1] * inv_det;
  inv_a[2] = -a[2] * inv_det;
  inv_a[3] = a[0] * inv_det;

  return 0;
}

static int _dot_n_nxn(const float *_n, const float *_nxn, int n, float *out) {
  for (int i = 0; i < n; ++i) {
    out[i] = 0;
    for (int j = 0; j < n; ++j) out[i] += _n[j] * _nxn[j * n + i];
  }
  return 0;
}

polar_t cartesian_to_polar_sector_float32(cartesian_t cartesian) {
  cartesian_t pos = def_cartesian_instance(cartesian.x, cartesian.y, 0.0f);
  float az_val;
  int left_idx, right_idx;
  float _2x2[4], _2x2_inv[4];
  float g_lr[2];
  float _xy[2];

  if (fabsf(cartesian.x) < def_eps && fabsf(cartesian.y) < def_eps) {
    if (fabsf(cartesian.z) < 1e-10)
      return def_polar_instance(0.0, 0.0, 0.0);
    else
      return def_polar_instance(0.0, _sign(cartesian.z) * 90.0,
                                fabsf(cartesian.z));
  }

  az_val = _azimuth(pos);

  _find_cart_sector(az_val, &left_idx, &right_idx);

  float left_az = _g_sector_mapping[left_idx].azimuth;
  float right_az = _g_sector_mapping[right_idx].azimuth;
  cartesian_t *left_pos = &_g_sector_mapping[left_idx].position;
  cartesian_t *right_pos = &_g_sector_mapping[right_idx].position;

  _2x2[0] = left_pos->x;
  _2x2[1] = left_pos->y;
  _2x2[2] = right_pos->x;
  _2x2[3] = right_pos->y;

  _xy[0] = pos.x;
  _xy[1] = pos.y;

  _linalg_inv_2x2(_2x2, _2x2_inv);
  _dot_n_nxn(_xy, _2x2_inv, 2, g_lr);

  float r_xy = g_lr[0] + g_lr[1];
  if (fabs(r_xy) < def_eps)
    return def_polar_instance(
        0.f, 0.f,
        sqrtf(cartesian.x * cartesian.x + cartesian.y * cartesian.y +
              cartesian.z * cartesian.z));

  float rel_left_az = _relative_angle(right_az, left_az);
  float az = _map_linear_to_az(rel_left_az, right_az, g_lr[1] / r_xy);
  az = _relative_angle(-180.0f, az);

  float el_tilde = def_radians_to_degrees(atanf(cartesian.z / r_xy));

  float el, d;
  if (fabsf(el_tilde) > def_elevation_top_tilde) {
    float abs_el =
        def_elevation_top + (90.0f - def_elevation_top) *
                                (fabsf(el_tilde) - def_elevation_top_tilde) /
                                (90.0f - def_elevation_top_tilde);
    el = _sign(el_tilde) * abs_el;
    d = fabsf(cartesian.z);
  } else {
    el = def_elevation_top * el_tilde / def_elevation_top_tilde;
    d = r_xy;
  }

  return def_polar_instance(az, el, d);
}

oar_metadata_t *metadata_clone(const oar_metadata_t *metadata) {
  oar_metadata_t *clone = 0;

  if (!metadata) return 0;

  clone = def_mallocz(oar_metadata_t, 1);
  if (!clone) return 0;

  // Copy the basic structure
  *clone = *metadata;

  // Handle deep copying based on metadata type
  switch (metadata->type) {
    case ck_metadata_gain:
      if (metadata->gain.param_type == ck_param_multiple) {
        // Clone the gain array
        if (metadata->gain.gain_array) {
          // Calculate array size based on duration (samples per channel)
          size_t array_size = metadata->duration * sizeof(float);
          clone->gain.gain_array = def_malloc(float, metadata->duration);
          if (!clone->gain.gain_array) {
            def_free(clone);
            return 0;
          }
          memcpy(clone->gain.gain_array, metadata->gain.gain_array, array_size);
        }
      }
      // For ck_param_constant and ck_param_animated, no additional allocation
      // needed
      break;

    case ck_metadata_iamf_downmix_mode:
    case ck_metadata_object_positions:
    case ck_metadata_head_rotation:
    case ck_metadata_none:
    default:
      // No dynamic allocation needed
      break;
  }

  return clone;
}

void metadata_delete(oar_metadata_t *metadata) {
  if (!metadata) return;

  // Handle deep freeing based on metadata type
  switch (metadata->type) {
    case ck_metadata_gain:
      if (metadata->gain.param_type == ck_param_multiple) {
        // Free the gain array
        if (metadata->gain.gain_array) {
          def_free(metadata->gain.gain_array);
          metadata->gain.gain_array = 0;
        }
      }
      // For ck_param_constant and ck_param_animated, no additional freeing
      // needed
      break;

    case ck_metadata_iamf_downmix_mode:
    case ck_metadata_object_positions:
    case ck_metadata_head_rotation:
    case ck_metadata_none:
    default:
      // No dynamic allocation to free
      break;
  }

  // Free the metadata structure itself
  def_free(metadata);
}

int metadata_gain_linear(oar_metadata_t *metadata) {
  if (!metadata || metadata->type != ck_metadata_gain) {
    return ck_oar_error_inval;
  }

  switch (metadata->gain.param_type) {
    case ck_param_constant:
      // Convert constant gain from dB to linear
      metadata->gain.constant_gain =
          db_to_linear_float32(metadata->gain.constant_gain);
      break;

    case ck_param_multiple:
      // Convert each gain value in the array from dB to linear
      if (metadata->gain.gain_array && metadata->duration > 0) {
        for (int i = 0; i < metadata->duration; i++) {
          metadata->gain.gain_array[i] =
              db_to_linear_float32(metadata->gain.gain_array[i]);
        }
      }
      break;

    case ck_param_animated:
      // Convert animated gain values from dB to linear
      metadata->gain.animated_gains.data.start =
          db_to_linear_float32(metadata->gain.animated_gains.data.start);
      metadata->gain.animated_gains.data.end =
          db_to_linear_float32(metadata->gain.animated_gains.data.end);
      // For bezier animation, also convert the control point
      if (metadata->gain.animated_gains.animation_type ==
          ck_animation_type_bezier) {
        metadata->gain.animated_gains.data.control =
            db_to_linear_float32(metadata->gain.animated_gains.data.control);
      }
      break;

    default:
      return ck_oar_error_inval;
  }

  return ck_oar_ok;
}

float db_to_linear_float32(float db) { return powf(10.0f, 0.05f * db); }

#ifdef __as_dbg__

void *wav_writer_open(wav_tag_t tag, int id, uint32_t sample_rate,
                      uint32_t channel_count) {
  char name[256];

  switch (tag) {
    case ck_tag_mixed:
      snprintf(name, sizeof(name), "mixed_audio.wav");
      break;
    case ck_tag_rendered:
      snprintf(name, sizeof(name), "rendered_audio_element_%d.wav", id);
      break;
    case ck_tag_original:
      snprintf(name, sizeof(name), "audio_element_%d.wav", id);
      break;
  }

  return dep_wav_write_open2(name, DEP_WAVE_FORMAT_FLOAT, sample_rate, 32,
                             channel_count);
}

void wav_writer_close(void *wav) { dep_wav_write_close(wav); }

static void _plane2stride_float(float *dst, const float *src, int frame_size,
                                int channels) {
  for (int c = 0; c < channels; ++c) {
    if (src) {
      for (int i = 0; i < frame_size; i++)
        dst[i * channels + c] = src[frame_size * c + i];
    } else {
      for (int i = 0; i < frame_size; i++) dst[i * channels + c] = 0;
    }
  }
}

int wav_writer_write(void *wav, float *data, uint32_t sampels_per_channel,
                     uint32_t channels) {
  float *pcm = def_malloc(float, sampels_per_channel *channels);
  _plane2stride_float(pcm, data, sampels_per_channel, channels);
  dep_wav_write_data(wav, (void *)pcm,
                     sampels_per_channel * channels * sizeof(float));
  free(pcm);
  return 0;
}

#endif
