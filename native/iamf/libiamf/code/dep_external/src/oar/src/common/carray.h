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

#ifndef __CARRAY_H__
#define __CARRAY_H__

#include "cvalue.h"

typedef struct CArray array_t;

array_t* array_new(uint32_t n);
void array_free(array_t* a, func_value_wrap_ptr_free_t free_func);
int array_size(const array_t* a);
value_wrap_t* array_at(const array_t* a, uint32_t index);
value_wrap_t* array_find(const array_t* a, value_wrap_t data,
                         func_value_wrap_find_t func);

#endif  // __CARRAY_H__