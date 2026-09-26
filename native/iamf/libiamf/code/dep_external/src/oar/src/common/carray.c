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


#include "carray.h"

#include <stdlib.h>

struct CArray {
  value_wrap_t* items;
  uint32_t count;
};

array_t* array_new(uint32_t n) {
  array_t* a = 0;
  if (!n) return 0;
  a = calloc(1, sizeof(array_t));
  if (!a) return 0;
  a->items = calloc(n, sizeof(value_wrap_t));
  if (!a->items) {
    array_free(a, 0);
    a = 0;
  } else {
    a->count = n;
  }
  return a;
}

void array_free(array_t* a, func_value_wrap_ptr_free_t free_func) {
  if (!a) return;
  if (a->items) {
    for (uint32_t i = 0; i < a->count; ++i) {
      if (a->items[i].ptr && free_func) free_func(a->items[i].ptr);
    }
    free(a->items);
  }
  free(a);
}

int array_size(const array_t* a) { return a ? a->count : 0; }

value_wrap_t* array_at(const array_t* a, uint32_t index) {
  return a && index < a->count ? &a->items[index] : 0;
}

value_wrap_t* array_find(const array_t* a, value_wrap_t data,
                         func_value_wrap_find_t func) {
  value_wrap_t* val = 0;
  if (!a || !func) return 0;

  for (int i = 0; i < a->count; ++i) {
    if (func(a->items[i], data) > 0) {
      val = &a->items[i];
      break;
    }
  }

  return val;
}
