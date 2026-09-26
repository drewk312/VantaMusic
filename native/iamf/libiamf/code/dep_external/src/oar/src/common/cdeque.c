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


#include "cdeque.h"

#include <stdio.h>
#include <stdlib.h>

typedef struct CNote {
  value_wrap_t data;
  struct CNote* prev;
  struct CNote* next;
} node_t;

struct CDeque {
  node_t* front;
  node_t* rear;
  int length;
};

static node_t* create_node(value_wrap_t data) {
  node_t* new_node = calloc(1, sizeof(node_t));
  if (!new_node) {
    fprintf(stderr, "fail to allocate memory.\n");
    return 0;
  }
  new_node->data = data;
  return new_node;
}

deque_t* deque_new() {
  deque_t* dq = calloc(1, sizeof(deque_t));
  if (!dq) fprintf(stderr, "fail to allocate memory.\n");
  return dq;
}

int deque_is_empty(deque_t* dq) { return !dq->front; }

int deque_length(deque_t* dq) { return dq->length; }

int deque_push_front(deque_t* dq, value_wrap_t data) {
  node_t* new_node = create_node(data);

  if (!new_node) return -12;

  if (deque_is_empty(dq)) {
    dq->front = dq->rear = new_node;
  } else {
    new_node->next = dq->front;
    dq->front->prev = new_node;
    dq->front = new_node;
  }
  return ++dq->length;
}

int deque_push_back(deque_t* dq, value_wrap_t data) {
  node_t* new_node = create_node(data);

  if (!new_node) return -12;

  if (deque_is_empty(dq)) {
    dq->front = dq->rear = new_node;
  } else {
    new_node->prev = dq->rear;
    dq->rear->next = new_node;
    dq->rear = new_node;
  }

  return ++dq->length;
}

int deque_pop_front(deque_t* dq, value_wrap_t* data) {
  node_t* temp = dq->front;
  if (deque_is_empty(dq)) {
    fprintf(stderr, "empty.\n");
    return -12;
  }

  *data = temp->data;

  if (dq->front == dq->rear) {
    dq->front = dq->rear = 0;
  } else {
    dq->front = dq->front->next;
    dq->front->prev = 0;
  }

  free(temp);
  return --dq->length;
}

int deque_pop_back(deque_t* dq, value_wrap_t* data) {
  node_t* temp = dq->rear;

  if (deque_is_empty(dq)) {
    fprintf(stderr, "empty.\n");
    return -12;
  }

  *data = temp->data;

  if (dq->front == dq->rear) {
    dq->front = dq->rear = 0;
  } else {
    dq->rear = dq->rear->prev;
    dq->rear->next = 0;
  }
  free(temp);
  return --dq->length;
}

void deque_free(deque_t* dq, func_value_wrap_ptr_free_t free_func) {
  value_wrap_t data;
  while (!deque_is_empty(dq)) {
    deque_pop_front(dq, &data);
    if (free_func && data.ptr) free_func(data.ptr);
  }
  free(dq);
}

value_wrap_t* deque_at(deque_t* dq, int index) {
  value_wrap_t* val = 0;
  if (index >= dq->length || index < 0) {
    fprintf(stderr, "index out of range.\n");
    return 0;
  }

  node_t* temp = dq->front;
  for (int i = 0; i < index; ++i) {
    temp = temp->next;
  }
  val = &temp->data;
  return val;
}
