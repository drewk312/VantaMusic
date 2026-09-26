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
 * @file obr.h
 * @brief OBR renderer APIs.
 * @version 2.0.0
 * @date Created 05/08/2025
 **/

#ifndef __OBR_H__
#define __OBR_H__

#include "renderer_library_api.h"

#define def_obr_renderer_library_api_id "_obr_default"
/**
 * @brief     Get the global iamf_obr_renderer API structure.
 * @return    Pointer to the const renderer_library_api_t structure.
 */
const renderer_library_api_t *obr_get_api(void);

#endif  // __OBR_H__
