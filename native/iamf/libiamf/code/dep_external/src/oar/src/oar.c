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

#include "oar.h"

#include <math.h>
#include <stdlib.h>
#include <string.h>

#include "animated.h"
#include "audio_element_renderer.h"
#include "audio_elements_renderer.h"
#include "clog.h"
#include "cvector.h"
#include "definitions.h"
#include "limiter/oar_limiter.h"
#include "oar_utils.h"
#include "renderer/downmix/downmix.h"
#include "renderer/ear/ear.h"
#include "renderer/olr/olr.h"
#include "renderer/renderer_library_manager.h"

#ifdef __binauralizer__
#include "renderer/obr/obr.h"
#endif

#define def_limiter_release_ms 50
#define def_limiter_threshold_dbfs -1.f
#define def_max_audio_group_id 2
#define def_default_metadata_samples 1

typedef struct AudioGroup {
  uint32_t id;
  vector_t *renderers;  // vector<audio_renderer_base_t>
  metadata_item_t output_gain;
  float loudness_gain;
} audio_group_t;

struct Oar {
  oar_config_t config;
  vector_t *groups;  // vector<audio_group_t>
  renderer_library_manager_t renderer_library_manager;
  int enable_loudness_processor;
  int enable_limiter;
  head_tracking_t enable_head_tracking;
  quaternion_t head_rotation;
  oar_limiter_t *limiter;
  uint32_t metadata_samples[ck_metadata_count];

#ifdef __as_dbg__
  void *mixed;
#endif
};

static int _apply_gain(oar_audio_block_t *block, float gain) {
  for (uint32_t i = 0; i < block->channels * block->samples_per_channel; i++)
    block->data[i] *= gain;
  return ck_oar_ok;
}

static void _audio_group_delete(audio_group_t *group) {
  if (group) {
    if (group->renderers)
      vector_free(group->renderers,
                  def_default_free_ptr(audio_renderer_delete));
    if (group->output_gain.metadatas)
      queue_free(group->output_gain.metadatas,
                 def_default_free_ptr(metadata_delete));
    def_free(group);
  }
}

static int _find_group_id(value_wrap_t item, value_wrap_t key) {
  audio_group_t *group = item.ptr;
  return group->id == key.u32 ? 1 : 0;
}

static void _audio_element_delete(audio_renderer_base_t *renderer) {}

static int _find_element_id(value_wrap_t item, value_wrap_t key) {
  audio_renderer_base_t *renderer = def_value_wrap_ptr(&item);

  return ((renderer->ctx.in != ck_ri_multi_ids &&
           renderer->ctx.stream_id == key.u32) ||
          (renderer->ctx.in == ck_ri_multi_ids &&
           renderer->impl->get_element_index(renderer, key.u32) >= 0))
             ? 1
             : 0;
}

static void _oar_metadatas_elapse(oar_t *oar, uint32_t samples_per_channel) {
  if (!oar || !oar->groups) return;

  // Iterate through all audio groups
  for (int i = 0; i < vector_size(oar->groups); i++) {
    audio_group_t *group = def_value_wrap_ptr(vector_at(oar->groups, i));
    if (!group) continue;

    metadata_item_elapse(&group->output_gain, samples_per_channel);

    // Iterate through all renderers in the group
    for (int j = 0; j < vector_size(group->renderers); j++) {
      audio_renderer_base_t *renderer =
          def_value_wrap_ptr(vector_at(group->renderers, j));
      if (!renderer) continue;

      renderer->impl->metadatas_elapse(renderer, samples_per_channel);
    }
  }
}

oar_t *oar_create(const oar_config_t *config) {
  oar_t *oar = 0;
  renderer_library_manager_t *manager = 0;

  if (!config) return 0;

  oar = def_mallocz(oar_t, 1);
  if (!oar) return 0;

  manager = &oar->renderer_library_manager;
  oar->config = *config;
  oar->groups = vector_new();
  if (!oar->groups || renderer_library_manager_init(manager) < 0) {
    oar_destroy(oar);
    return 0;
  }

  // Add built-in renderer libraries
  renderer_library_manager_add_library(manager, downmix_get_api());
  renderer_library_manager_add_library(manager, ear_get_api());
  renderer_library_manager_add_library(manager, olr_get_api());
#ifdef __binauralizer__
  renderer_library_manager_add_library(manager, obr_get_api());
#endif
  for (int i = 0; i < renderer_library_manager_get_library_count(manager);
       i++) {
    const renderer_library_api_t *impl =
        renderer_library_manager_get_library(manager, i);
    info("renderer[%d] id:%s", i, impl->id);
  }

  oar->limiter =
      oar_limiter_create(config->sampling_rate, def_limiter_release_ms,
                         def_limiter_threshold_dbfs);
  if (!oar->limiter) {
    oar_destroy(oar);
    return 0;
  }

  oar->metadata_samples[ck_metadata_gain] = def_default_metadata_samples;
  oar->metadata_samples[ck_metadata_object_positions] =
      config->samples_per_channel;

  oar->head_rotation.w = 1;

#ifdef __as_dbg__
  oar->mixed =
      wav_writer_open(ck_tag_mixed, 0, oar->config.sampling_rate,
                      layout_channels_count(oar->config.target_layout));
  if (!oar->mixed) warning("Failed to open WAV file for output");
#endif

  return oar;
}

void oar_destroy(oar_t *oar) {
  if (oar) {
    if (oar->groups)
      vector_free(oar->groups, def_default_free_ptr(_audio_group_delete));
    renderer_library_manager_clear(&oar->renderer_library_manager);
    if (oar->limiter) oar_limiter_destroy(oar->limiter);

#ifdef __as_dbg__
    if (oar->mixed) wav_writer_close(oar->mixed);
#endif

    def_free(oar);
  }
}

int oar_add_audio_group(oar_t *oar) {
  audio_group_t *group = 0;
  value_wrap_t v;
  int gid;

  if (!oar) return ck_oar_error_inval;

  for (gid = 0; gid < def_max_audio_group_id; gid++) {
    if (vector_find(oar->groups, def_value_wrap_instance_u32(gid),
                    _find_group_id, &v) < 0) {
      break;
    }
  }

  if (gid >= def_max_audio_group_id) return ck_oar_error_busy;

  group = def_mallocz(audio_group_t, 1);
  if (!group) return ck_oar_error_nomem;

  group->id = gid;
  group->renderers = vector_new();
  group->loudness_gain = 1.0f;
  group->output_gain.metadatas = queue_new();

  if (oar->config.target_layout == ck_oar_layout_binaural) {
    audio_renderer_base_t *binaural_renderer =
        audio_elements_renderer_create_wrapper(&oar->config, gid,
                                               &oar->renderer_library_manager,
                                               oar->metadata_samples, NULL);

    if (!binaural_renderer) {
      error("Failed to create binaural renderer for group %u", gid);
      _audio_group_delete(group);
      return ck_oar_error_notsup;
    }

    // Add binaural_renderer as a regular renderer to the renderers vector
    if (vector_push(group->renderers,
                    def_value_wrap_instance_ptr(binaural_renderer)) <= 0) {
      binaural_renderer->impl->destroy(binaural_renderer);
      _audio_group_delete(group);
      return ck_oar_error_nomem;
    }
  }

  if (!group->renderers || !group->output_gain.metadatas) {
    _audio_group_delete(group);
    return ck_oar_error_nomem;
  }

  if (vector_push(oar->groups, def_value_wrap_instance_ptr(group)) <= 0) {
    _audio_group_delete(group);
    return ck_oar_error_nomem;
  }

  return gid;
}

int oar_add_audio_element(oar_t *oar, uint32_t gid, uint32_t id,
                          const oar_audio_element_config_t *config) {
  audio_renderer_base_t *renderer = 0;
  audio_group_t *group = 0;
  value_wrap_t v;
  rid_t rid = ck_rid_none;

  if (!oar || !config) return ck_oar_error_inval;

  if (vector_find(oar->groups, def_value_wrap_instance_u32(gid), _find_group_id,
                  &v) < 0) {
    warning("Audio group %u does not exist.", gid);
    return ck_oar_error_inval;
  }
  group = def_value_wrap_type_ptr(audio_group_t, &v);

  for (int i = 0; i < vector_size(oar->groups); i++) {
    audio_group_t *_group = def_value_wrap_ptr(vector_at(oar->groups, i));
    if (vector_find(_group->renderers, def_value_wrap_instance_u32(id),
                    _find_element_id, &v) >= 0) {
      warning("Audio element %u already exists in another group.", id);
      return ck_oar_error_busy;
    }
  }

  rid = layout_to_ro_id(oar->config.target_layout);

  if (oar->config.target_layout == ck_oar_layout_binaural &&
      config->parameters.flags &
          def_parameter_set_flag_iamf_element_rendering_config &&
      !config->parameters.element_rendering_config.headphones_rendering_mode &&
      config->type == ck_channel_based)
    rid = ck_rio_id_stereo;

  if (rid == ck_rio_id_binaural) {
    for (int i = 0; i < vector_size(group->renderers); ++i) {
      audio_renderer_base_t *binaural_renderer = def_value_wrap_type_ptr(
          audio_renderer_base_t, vector_at(group->renderers, i));

      if (binaural_renderer &&
          binaural_renderer->ctx.out == ck_rio_id_binaural) {
        int ret =
            binaural_renderer->impl->add_element(binaural_renderer, id, config);
        if (ret == ck_oar_ok) {
          renderer = binaural_renderer;
          break;
        } else {
          warning("Failed to add element %u to binaural renderer. Error: %d",
                  id, ret);
        }
      }
    }
  } else {
    renderer = audio_element_renderer_create_wrapper(
        &oar->config, id, &oar->renderer_library_manager, oar->metadata_samples,
        config);
  }

  if (!renderer) return ck_oar_error_inval;

  if (rid == ck_rio_id_binaural) {
    int lock =
        config->parameters.flags &
                    def_parameter_set_flag_iamf_element_rendering_config &&
                config->parameters.element_rendering_config
                        .headphones_rendering_mode == ck_head_locked
            ? 1
            : 0;
    renderer->impl->set_element_head_locked(renderer, id, lock);
    return ck_oar_ok;
  }

  return vector_push(group->renderers, def_value_wrap_instance_ptr(renderer)) >
                 0
             ? ck_oar_ok
             : ck_oar_error_nomem;
}

int oar_remove_audio_element(oar_t *oar, uint32_t id) {
  value_wrap_t v;
  int index = -1;
  int i;

  if (!oar) return ck_oar_error_inval;

  for (i = 0; i < vector_size(oar->groups); i++) {
    audio_group_t *group = def_value_wrap_ptr(vector_at(oar->groups, i));

    index = vector_find(group->renderers, def_value_wrap_instance_u32(id),
                        _find_element_id, &v);
    if (index >= 0) {
      vector_remove(group->renderers, index,
                    def_default_free_ptr(_audio_element_delete));
      return ck_oar_ok;
    }
  }

  return ck_oar_error_inval;
}

int oar_update_audio_element_metadata(oar_t *oar, uint32_t id,
                                      const oar_metadata_t *metadata) {
  value_wrap_t v;
  int i;

  if (!oar || !metadata) return ck_oar_error_inval;

  for (i = 0; i < vector_size(oar->groups); i++) {
    audio_group_t *group =
        def_value_wrap_type_ptr(audio_group_t, vector_at(oar->groups, i));

    if (vector_find(group->renderers, def_value_wrap_instance_u32(id),
                    _find_element_id, &v) >= 0) {
      audio_renderer_base_t *renderer =
          def_value_wrap_type_ptr(audio_renderer_base_t, &v);
      return renderer->impl->update_element_metadata(renderer, id, metadata);
    }
  }

  return ck_oar_error_inval;  // Element not found in any group
}

int oar_update_audio_element_data(oar_t *oar, uint32_t id,
                                  oar_audio_block_t *data) {
  value_wrap_t v;
  int i;

  if (!oar || !data) return ck_oar_error_inval;

  // Search through all groups to find the element
  for (i = 0; i < vector_size(oar->groups); i++) {
    audio_group_t *group = def_value_wrap_ptr(vector_at(oar->groups, i));

    if (vector_find(group->renderers, def_value_wrap_instance_u32(id),
                    _find_element_id, &v) >= 0) {
      audio_renderer_base_t *renderer =
          def_value_wrap_type_ptr(audio_renderer_base_t, &v);
      return renderer->impl->add_data(renderer, id, data);
    }
  }

  return ck_oar_error_inval;
}

int oar_set_metadata_unit_to_process(oar_t *oar, oar_metadata_type_t type,
                                     uint32_t samples) {
  if (!oar) return ck_oar_error_inval;

  if (type != ck_metadata_object_positions) {
    warning(
        "Only object positions are supported for setting samples to process.");
    return ck_oar_error_notsup;
  }

  oar->metadata_samples[type] = samples;

  return ck_oar_ok;
}

int oar_update_metadata(oar_t *oar, uint32_t gid,
                        const oar_metadata_t *metadata) {
  audio_group_t *group = 0;
  value_wrap_t v;
  oar_metadata_t *clone = 0;

  if (!oar || !metadata) return ck_oar_error_inval;

  if (vector_find(oar->groups, def_value_wrap_instance_u32(gid), _find_group_id,
                  &v) < 0) {
    return ck_oar_error_inval;
  }
  group = def_value_wrap_type_ptr(audio_group_t, &v);

  if (metadata->type == ck_metadata_gain) {
    clone = metadata_clone(metadata);
    if (!clone) return ck_oar_error_nomem;
    metadata_gain_linear(clone);

    if (group->output_gain.metadatas) {
      if (queue_push(group->output_gain.metadatas,
                     def_value_wrap_instance_ptr(clone)) < 0) {
        metadata_delete(clone);
        return ck_oar_error_nomem;
      }
      group->output_gain.duration += metadata->duration;
    }
  } else if (metadata->type == ck_metadata_head_rotation) {
    // Store the head rotation in the OAR object
    oar->head_rotation = metadata->head_rotation;

    // Only apply head rotation if head tracking is enabled
    if (oar->enable_head_tracking) {
      // Apply head rotation to individual renderers
      for (int i = 0; i < vector_size(group->renderers); i++) {
        audio_renderer_base_t *renderer =
            def_value_wrap_ptr(vector_at(group->renderers, i));
        if (!renderer) continue;

        if (renderer->ctx.out == ck_rio_id_binaural) {
          renderer->impl->set_head_rotation(renderer, &metadata->head_rotation);
        }
      }
    } else {
      // Head tracking is not enabled, just store and return busy
      return ck_oar_error_busy;
    }
  } else {
    return ck_oar_error_notsup;
  }

  return ck_oar_ok;
}

int oar_render(oar_t *oar, oar_audio_block_t *output) {
  uint32_t out_channels, samples;
  audio_renderer_base_t *renderer = 0;
  oar_audio_block_t *group_blocks = 0;
  oar_audio_block_t renderer_output;
  int i, j, n, group_count;

  if (!oar || !output || !output->data) return ck_oar_error_inval;

  out_channels = oar_get_number_of_output_channels(oar);
  samples = oar->config.samples_per_channel;

  if (output->channels != out_channels ||
      output->samples_per_channel != samples)
    return ck_oar_error_inval;

  group_count = vector_size(oar->groups);
  if (group_count <= 0) return ck_oar_error_inval;

  group_blocks = def_malloc(oar_audio_block_t, group_count);
  if (!group_blocks) return ck_oar_error_nomem;

  for (i = 0; i < group_count; i++) {
    group_blocks[i].channels = out_channels;
    group_blocks[i].samples_per_channel = samples;
    group_blocks[i].data = def_mallocz(float, (out_channels * samples));
    if (!group_blocks[i].data) {
      for (int j = 0; j < i; j++) {
        if (group_blocks[j].data) def_free(group_blocks[j].data);
      }
      def_free(group_blocks);
      return ck_oar_error_nomem;
    }
  }

  // Create temporary renderer output block
  renderer_output.channels = out_channels;
  renderer_output.samples_per_channel = samples;
  renderer_output.data = def_mallocz(float, (out_channels * samples));
  if (!renderer_output.data) {
    for (i = 0; i < group_count; i++) {
      if (group_blocks[i].data) def_free(group_blocks[i].data);
    }
    def_free(group_blocks);
    return ck_oar_error_nomem;
  }

  for (i = 0; i < group_count; i++) {
    audio_group_t *group = def_value_wrap_ptr(vector_at(oar->groups, i));

    n = vector_size(group->renderers);
    for (j = 0; j < n; j++) {
      renderer = def_value_wrap_ptr(vector_at(group->renderers, j));
      if (!renderer->block.data) continue;

      if (renderer->ctx.out == ck_rio_id_binaural)
        renderer->impl->apply_gains(renderer, 0);

      if (renderer->impl->render(renderer, &renderer_output) != ck_oar_ok)
        continue;

      if (renderer->ctx.out != ck_rio_id_binaural)
        renderer->impl->apply_gains(renderer, &renderer_output);

      for (uint32_t k = 0; k < out_channels * samples; k++)
        group_blocks[i].data[k] += renderer_output.data[k];
    }

    audio_block_sub_frames_apply_gain(&group_blocks[i], &group->output_gain,
                                      oar->metadata_samples[ck_metadata_gain]);

    if (oar->enable_loudness_processor)
      _apply_gain(&group_blocks[i], group->loudness_gain);
  }

  // Free the temporary renderer output block
  def_free(renderer_output.data);

  memset(output->data, 0, sizeof(float) * out_channels * samples);
  for (i = 0; i < group_count; i++) {
    for (uint32_t k = 0; k < out_channels * samples; k++) {
      output->data[k] += group_blocks[i].data[k];
    }
  }

#ifdef __as_dbg__
  if (oar->mixed)
    wav_writer_write(oar->mixed, output->data, samples, out_channels);
#endif

  if (oar->enable_limiter) oar_limiter_process(oar->limiter, output);

  _oar_metadatas_elapse(oar, output->samples_per_channel);

  for (i = 0; i < group_count; i++)
    if (group_blocks[i].data) def_free(group_blocks[i].data);
  def_free(group_blocks);

  return ck_oar_ok;
}

int oar_enable_loudness_processor(oar_t *oar, int enable) {
  if (!oar) return ck_oar_error_inval;
  oar->enable_loudness_processor = enable ? 1 : 0;
  return ck_oar_ok;
}

int oar_set_loudness(oar_t *oar, uint32_t gid, float loudness,
                     float target_loudness) {
  audio_group_t *group = 0;
  value_wrap_t v;

  if (!oar) return ck_oar_error_inval;

  if (vector_find(oar->groups, def_value_wrap_instance_u32(gid), _find_group_id,
                  &v) < 0) {
    return ck_oar_error_inval;
  }
  group = v.ptr;

  group->loudness_gain = db_to_linear_float32(target_loudness - loudness);

  return ck_oar_ok;
}

int oar_enable_limiter(oar_t *oar, int enable) {
  if (!oar) return ck_oar_error_inval;
  oar->enable_limiter = enable ? 1 : 0;
  return ck_oar_ok;
}

int oar_enable_head_tracking(oar_t *oar, int enable) {
  int i, j;

  if (!oar) return ck_oar_error_inval;
  if (oar->config.target_layout != ck_oar_layout_binaural)
    return ck_oar_error_notsup;
  if (oar->enable_head_tracking == enable) return ck_oar_ok;

  oar->enable_head_tracking = !!enable;

  for (i = 0; i < vector_size(oar->groups); i++) {
    audio_group_t *group = def_value_wrap_ptr(vector_at(oar->groups, i));

    int n = vector_size(group->renderers);
    for (j = 0; j < n; j++) {
      audio_renderer_base_t *renderer =
          def_value_wrap_ptr(vector_at(group->renderers, j));

      renderer->impl->enable_head_tracking(renderer, enable);
    }
  }

  return ck_oar_ok;
}

uint32_t oar_get_samples_per_channel(oar_t *oar) {
  if (!oar) return 0;
  return oar->config.samples_per_channel;
}

uint32_t oar_get_sampling_rate(oar_t *oar) {
  if (!oar) return 0;
  return oar->config.sampling_rate;
}

uint32_t oar_get_number_of_audio_element_channels(oar_t *oar, uint32_t id) {
  value_wrap_t v;

  if (!oar) return 0;

  for (int i = 0; i < vector_size(oar->groups); i++) {
    audio_group_t *group = def_value_wrap_ptr(vector_at(oar->groups, i));

    if (vector_find(group->renderers, def_value_wrap_instance_u32(id),
                    _find_element_id, &v) >= 0) {
      audio_renderer_base_t *renderer = def_value_wrap_ptr(&v);
      // Call function through impl pointer
      if (renderer->impl && renderer->impl->get_element_channels)
        return renderer->impl->get_element_channels(renderer, id);
      else if (renderer->impl && renderer->impl->get_channels)
        return renderer->impl->get_channels(renderer);
    }
  }

  return 0;
}

uint32_t oar_get_number_of_output_channels(oar_t *oar) {
  if (!oar) return 0;
  return layout_channels_count(oar->config.target_layout);
}

uint32_t oar_get_number_of_audio_elements(oar_t *oar) {
  uint32_t total_count = 0;
  int i;

  if (!oar || !oar->groups) return 0;

  for (i = 0; i < vector_size(oar->groups); i++) {
    audio_group_t *group = def_value_wrap_ptr(vector_at(oar->groups, i));
    total_count += vector_size(group->renderers);
  }

  return total_count;
}
