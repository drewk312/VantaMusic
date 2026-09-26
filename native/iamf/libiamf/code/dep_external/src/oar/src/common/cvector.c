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
 * @file cvector.c
 * @brief Vector APIs.
 * @version 2.0.0
 * @date Created 19/02/2025
 **/

#include "cvector.h"

// #include <stdio.h>
#include <stdlib.h>
#include <string.h>

#define CAP_DEFAULT 6

struct CVector {
  value_wrap_t* items;
  int count;
  int capacity;
};

vector_t* vector_new() {
  vector_t* v = calloc(1, sizeof(vector_t));
  // fprintf(stderr, "DEBUG: VEC new %p\n", v);
  return v;
}

vector_t* vector_clone(const vector_t* v) {
  vector_t* clone = calloc(1, sizeof(vector_t));
  if (clone) {
    clone->count = v->count;
    clone->capacity = v->capacity;
    clone->items = malloc(v->capacity * sizeof(value_wrap_t));
    if (!clone->items) {
      free(clone);
      return 0;
    }
    memcpy(clone->items, v->items, v->capacity * sizeof(value_wrap_t));
  }

  return clone;
}

void vector_free(vector_t* v, func_value_wrap_ptr_free_t free_func) {
  vector_reset(v, free_func);  // free all elements first
  if (v) {
    if (v->items) free(v->items);
    free(v);
  }
}

void vector_reset(vector_t* v, func_value_wrap_ptr_free_t free_func) {
  if (v) {
    if (free_func)
      for (int i = 0; i < v->count; ++i) free_func(v->items[i].ptr);
    v->count = 0;
    memset(v->items, 0, v->capacity * sizeof(value_wrap_t));
  }
}

int vector_size(const vector_t* v) { return v ? v->count : 0; }

value_wrap_t* vector_at(const vector_t* v, int index) {
  value_wrap_t* val = 0;
  if (v && index >= 0 && index < v->count) val = &v->items[index];
  return val;
}

int vector_push(vector_t* v, value_wrap_t data) {
  if (!v) return -22;  // -EINVAL
  if (v->count == v->capacity) {
    value_wrap_t* cap = 0;
    if (!v->capacity)
      cap = calloc(CAP_DEFAULT, sizeof(value_wrap_t));
    else
      cap =
          realloc(v->items, (v->capacity + CAP_DEFAULT) * sizeof(value_wrap_t));
    if (!cap) return -12;  // -ENOMEM
    v->items = cap;
    v->capacity += CAP_DEFAULT;
    // fprintf(stderr, "DEBUG: VEC re-alloc %p\n", cap);
  }

  v->items[v->count++] = data;
  return v->count;
}

int vector_remove(vector_t* v, int index,
                  func_value_wrap_ptr_free_t free_func) {
  if (!v || !v->items || index < 0 || index >= v->count) return -22;
  if (free_func && v->items[index].ptr) free_func(v->items[index].ptr);
  memmove(&v->items[index], &v->items[index + 1],
          (v->count - index - 1) * sizeof(value_wrap_t));
  --v->count;
  return 0;
}

int vector_find(const vector_t* v, value_wrap_t data,
                func_value_wrap_find_t func, value_wrap_t* val) {
  int ret = -2;                         // -ENOENT
  if (!v || !func || !val) return -22;  // -EINVAL

  for (int i = 0; i < v->count; ++i) {
    if (func(v->items[i], data) > 0) {
      *val = v->items[i];
      ret = i;
      break;
    }
  }

  return ret;
}

#undef CAP_DEFAULT