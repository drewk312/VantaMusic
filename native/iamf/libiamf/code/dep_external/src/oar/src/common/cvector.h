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
 * @file cvector.h
 * @brief Vector APIs.
 * @version 2.0.0
 * @date Created 19/02/2025
 **/

#ifndef __CVECTOR_H__
#define __CVECTOR_H__

#include "cvalue.h"

typedef struct CVector vector_t;

vector_t* vector_new();
vector_t* vector_clone(const vector_t* v);
void vector_free(vector_t* v, func_value_wrap_ptr_free_t free_func);
void vector_reset(vector_t* v, func_value_wrap_ptr_free_t free_func);
int vector_size(const vector_t* v);
value_wrap_t* vector_at(const vector_t* v, int index);
int vector_push(vector_t* v, value_wrap_t data);
int vector_remove(vector_t* v, int index, func_value_wrap_ptr_free_t free_func);
int vector_find(const vector_t* v, value_wrap_t data,
                func_value_wrap_find_t func, value_wrap_t* val);
#endif  // __CVECTOR_H__