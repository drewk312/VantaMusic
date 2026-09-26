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

#ifndef OBR_AUDIO_BUFFER_SIMD_MACROS_H_
#define OBR_AUDIO_BUFFER_SIMD_MACROS_H_

#if !defined(DISABLE_SIMD) && (defined(__x86_64__) || defined(_M_X64) || \
                               defined(i386) || defined(_M_IX86))
// SSE1 is enabled.
#include <xmmintrin.h>
typedef __m128 SimdVector;
#define SIMD_SSE
#define SIMD_LENGTH 4
#define SIMD_MULTIPLY(a, b) _mm_mul_ps(a, b)
#define SIMD_ADD(a, b) _mm_add_ps(a, b)
#define SIMD_SUB(a, b) _mm_sub_ps(a, b)
#define SIMD_MULTIPLY_ADD(a, b, c) _mm_add_ps(_mm_mul_ps(a, b), c)
#define SIMD_SQRT(a) _mm_rcp_ps(_mm_rsqrt_ps(a))
#define SIMD_RECIPROCAL_SQRT(a) _mm_rsqrt_ps(a)
#define SIMD_LOAD_ONE_FLOAT(p) _mm_set1_ps(p)
#elif !defined(DISABLE_SIMD) && \
    (defined(__aarch64__) || (defined(__arm__) && defined(__ARM_NEON__)))
// ARM NEON is enabled.
#include <arm_neon.h>
typedef float32x4_t SimdVector;
#define SIMD_NEON
#define SIMD_LENGTH 4
#define SIMD_MULTIPLY(a, b) vmulq_f32(a, b)
#define SIMD_ADD(a, b) vaddq_f32(a, b)
#define SIMD_SUB(a, b) vsubq_f32(a, b)
#define SIMD_MULTIPLY_ADD(a, b, c) vmlaq_f32(c, a, b)
#define SIMD_SQRT(a) vrecpeq_f32(vrsqrteq_f32(a))
#define SIMD_RECIPROCAL_SQRT(a) vrsqrteq_f32(a)
#define SIMD_LOAD_ONE_FLOAT(p) vld1q_dup_f32(&(p))
#else
// No SIMD optimizations enabled.
#include "obr/common/misc_math.h"
typedef float SimdVector;
#define SIMD_DISABLED
#define SIMD_LENGTH 1
#define SIMD_MULTIPLY(a, b) ((a) * (b))
#define SIMD_ADD(a, b) ((a) + (b))
#define SIMD_SUB(a, b) ((a) - (b))
#define SIMD_MULTIPLY_ADD(a, b, c) ((a) * (b) + (c))
#define SIMD_SQRT(a) (1.0f / FastReciprocalSqrt(a))
#define SIMD_RECIPROCAL_SQRT(a) FastReciprocalSqrt(a)
#define SIMD_LOAD_ONE_FLOAT(p) (p)
#warning "Not using SIMD optimizations!"
#endif

#endif  // OBR_AUDIO_BUFFER_SIMD_MACROS_H_
