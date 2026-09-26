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

#ifndef __CVALUE_H__
#define __CVALUE_H__

#include <stdint.h>

typedef struct CValueWrap {
  void* ptr;
  union {
    uint32_t u32;
    int32_t i32;
    float f32;
    double f64;
  };
} value_wrap_t;

typedef struct CFraction {
  uint32_t numerator;
  uint32_t denominator;
} fraction_t;

#define def_key_value_type(a, b, c) \
  typedef struct c {                \
    a key;                          \
    b value;                        \
  } c##_t;

typedef int (*func_value_wrap_find_t)(value_wrap_t value, value_wrap_t key);
typedef void (*func_value_wrap_ptr_free_t)(void* ptr);

#define def_value_wrap_find_func(a) ((func_value_wrap_find_t)(a))
#define def_value_wrap_ptr_free_func(a) ((func_value_wrap_ptr_free_t)(a))
#define def_default_free_ptr(a) def_value_wrap_ptr_free_func(a)

#define def_value_wrap_instance_ptr(a) ((value_wrap_t){.ptr = a, .u32 = 0})
#define def_value_wrap_instance_u32(a) ((value_wrap_t){.ptr = 0, .u32 = a})
#define def_value_wrap_instance_i32(a) ((value_wrap_t){.ptr = 0, .i32 = a})
#define def_value_wrap_instance_f32(a) ((value_wrap_t){.ptr = 0, .f32 = a})
#define def_value_wrap_instance_f64(a) ((value_wrap_t){.ptr = 0, .f64 = a})

#define def_fraction_instance(a, b) \
  ((fraction_t){.numerator = a, .denominator = b})

#define def_value_wrap_ptr(v) ((v)->ptr)
#define def_value_wrap_type_ptr(type, v) ((type*)(v)->ptr)
#define def_value_wrap_optional_ptr(v) ((v) ? def_value_wrap_ptr(v) : 0)
#define def_value_wrap_optional_type_ptr(type, v) \
  ((v) ? def_value_wrap_type_ptr(type, v) : 0)

#define def_value_wrap_u32(v) ((v)->u32)
#define def_value_wrap_i32(v) ((v)->i32)
#define def_value_wrap_f32(v) ((v)->f32)
#define def_value_wrap_f64(v) ((v)->f64)

#endif  // __CVALUE_H__