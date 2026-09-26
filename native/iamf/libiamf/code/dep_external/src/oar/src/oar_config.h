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

#ifndef __CONFIG_H__
#define __CONFIG_H__

#include "cvalue.h"

#ifdef OLR_ENABLE_F64
typedef double auto_float_t;
#define def_value_wrap_float def_value_wrap_f64
#define def_value_wrap_instance_float def_value_wrap_instance_f64
#else
typedef float auto_float_t;
#define def_value_wrap_float def_value_wrap_f32
#define def_value_wrap_instance_float def_value_wrap_instance_f32
#endif

#endif  //__CONFIG_H__