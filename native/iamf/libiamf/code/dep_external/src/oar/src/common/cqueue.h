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
 * @file cqueue.h
 * @brief Queue APIs.
 * @version 2.0.0
 * @date Created 23/05/2023
 **/

#ifndef __CQUEUE_H__
#define __CQUEUE_H__

#include "cvalue.h"

typedef struct CQueue queue_t;
typedef int (*queue_custom_func_t)(value_wrap_t, value_wrap_t);
#define def_queue_custom_func(a) ((queue_custom_func_t)a)

queue_t *queue_new();
void queue_free(queue_t *, func_value_wrap_ptr_free_t);
int queue_push(queue_t *, value_wrap_t);
int queue_pop(queue_t *q, value_wrap_t *v);
value_wrap_t *queue_at(queue_t *, int);
int queue_is_empty(queue_t *);
int queue_length(queue_t *);

/**
 * @brief
 *
 * @param [in] cmp : Comparison function. >= 0, insert data.
 * @return int
 */
int queue_custom_push(queue_t *, value_wrap_t, queue_custom_func_t cmp);

/**
 * @brief
 *
 * @param [in] cmp : Comparison function. 1 is equal, 0 is not equal.
 * @return int
 */
value_wrap_t *queue_custom_find(queue_t *, value_wrap_t,
                                queue_custom_func_t cmp);

#endif  // __CQUEUE_H__
