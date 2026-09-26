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

#ifndef __RENDERER_LIBRARY_MANAGER_H__
#define __RENDERER_LIBRARY_MANAGER_H__

#include "cqueue.h"
#include "renderer_library_api.h"

typedef struct RendererLibraryManager {
  queue_t *renderer_libraries;
} renderer_library_manager_t;

int renderer_library_manager_init(renderer_library_manager_t *);
void renderer_library_manager_clear(renderer_library_manager_t *);

int renderer_library_manager_add_library(renderer_library_manager_t *,
                                         const renderer_library_api_t *);
int renderer_library_manager_get_library_count(renderer_library_manager_t *);
const renderer_library_api_t *renderer_library_manager_get_library(
    renderer_library_manager_t *, int);
const renderer_library_api_t *renderer_library_manager_find_library(
    renderer_library_manager_t *, renderer_library_context_t *);

#endif  // __RENDERER_LIBRARY_MANAGER_H__