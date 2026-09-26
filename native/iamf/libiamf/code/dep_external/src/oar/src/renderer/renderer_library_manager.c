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
 * @file renderer_library_manager.c
 * @brief Renderer Manager.
 * @version 2.0.0
 * @date Created 25/03/2025
 **/

#include "renderer_library_manager.h"

#include <string.h>

#include "clog.h"
#include "cvalue.h"
#include "oar_utils.h"

int renderer_library_manager_init(renderer_library_manager_t *self) {
  memset(self, 0, sizeof(*self));
  self->renderer_libraries = queue_new();
  return self->renderer_libraries ? 0 : ck_oar_error_nomem;
}

void renderer_library_manager_clear(renderer_library_manager_t *self) {
  if (!self) return;
  queue_free(self->renderer_libraries, 0);
  memset(self, 0, sizeof(*self));
}

static int renderer_compare(value_wrap_t in, value_wrap_t item) {
  renderer_library_api_t *a = (renderer_library_api_t *)in.ptr;
  renderer_library_api_t *b = (renderer_library_api_t *)item.ptr;
  return a->priority < b->priority ? 1 : (a->priority > b->priority ? -1 : 0);
}

static int renderer_find(value_wrap_t in, value_wrap_t item) {
  renderer_library_api_t *a = (renderer_library_api_t *)in.ptr;
  renderer_library_api_t *b = (renderer_library_api_t *)item.ptr;
  return a == b || !strncmp(a->id, b->id, strlen(a->id));
}

int renderer_library_manager_add_library(
    renderer_library_manager_t *self, const renderer_library_api_t *renderer) {
  int ret = 0;
  if (!self || !renderer) return ck_oar_error_inval;
  if (queue_custom_find(self->renderer_libraries,
                        def_value_wrap_instance_ptr((void *)renderer),
                        def_queue_custom_func(renderer_find)))
    return 0;
  ret = queue_custom_push(self->renderer_libraries,
                          def_value_wrap_instance_ptr((void *)renderer),
                          def_queue_custom_func(renderer_compare));
  if (ret >= 0) info("add renderer %s(%p), at %d", renderer->id, renderer, ret);

  return ret;
}

int renderer_library_manager_get_library_count(
    renderer_library_manager_t *self) {
  return queue_length(self->renderer_libraries);
}

const renderer_library_api_t *renderer_library_manager_get_library(
    renderer_library_manager_t *self, int idx) {
  return self ? def_value_wrap_optional_ptr(
                    queue_at(self->renderer_libraries, idx))
              : 0;
}

const renderer_library_api_t *renderer_library_manager_find_library(
    renderer_library_manager_t *self, renderer_library_context_t *ctx) {
  int n = renderer_library_manager_get_library_count(self);
  for (int i = 0; i < n; i++) {
    const renderer_library_api_t *api =
        renderer_library_manager_get_library(self, i);
    if (api) {
      if (api->is_capable) {
        if (api->is_capable(ctx) == ck_oar_ok) return api;
      } else if (api->open) {
        if (api->open(ctx) == ck_oar_ok) {
          if (api->close) api->close(ctx);
          return api;
        }
      }
    }
  }

  return 0;
}