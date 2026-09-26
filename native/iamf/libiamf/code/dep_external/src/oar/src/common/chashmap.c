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
 * @file chashmap.c
 * @brief Hash map implementation.
 * @version 2.0.0
 * @date Created 12/11/2025
 **/

#include "chashmap.h"

#include <stdlib.h>
#include <string.h>

#include "cvalue.h"  // For value_wrap_t

// Define a default load factor for resizing the hashmap.
#define def_default_load_factor 0.75

// Structure for a single key-value entry in the hashmap.
typedef struct HashMapEntry {
  uint32_t key;
  value_wrap_t value;
  struct HashMapEntry* next;
} hash_map_entry_t;

// The main hash_map_t structure.
struct HashMap {
  // Array of buckets (each bucket is a linked list).
  hash_map_entry_t** buckets;
  size_t num_buckets;  // Current number of buckets.
  size_t size;         // Number of key-value pairs in the map.

  // For iteration
  size_t current_bucket_index;
  hash_map_entry_t* current_entry;
};

struct HashMapIterator {
  const hash_map_t* map;
  size_t current_bucket_index;
  hash_map_entry_t* current_entry;
};

// Private default hash function for uint32_t keys.
static uint32_t default_hash_func(uint32_t key) {
  // A simple identity hash can work well for uint32_t keys,
  // especially if they are already well-distributed.
  // For better distribution in some cases, a more complex hash could be used,
  // but for simplicity and speed, identity is often sufficient.
  return key;
}

// Private function to calculate the bucket index for a given hash.
static size_t get_bucket_index(const hash_map_t* map, uint32_t hash) {
  return hash % map->num_buckets;
}

// Private function to resize the hashmap when the load factor is exceeded.
static int hash_map_resize(hash_map_t* map) {
  size_t new_num_buckets = map->num_buckets * 2;
  if (new_num_buckets == 0) {  // Handle initial size 0 case
    new_num_buckets = 16;      // A common default initial capacity
  }

  hash_map_entry_t** new_buckets =
      (hash_map_entry_t**)calloc(new_num_buckets, sizeof(hash_map_entry_t*));
  if (!new_buckets) {
    return -1;  // Memory allocation failed
  }

  // Re-hash all existing entries into the new bucket array.
  for (size_t i = 0; i < map->num_buckets; ++i) {
    hash_map_entry_t* entry = map->buckets[i];
    while (entry != NULL) {
      hash_map_entry_t* next_entry = entry->next;
      uint32_t hash = default_hash_func(entry->key);
      size_t new_index = hash % new_num_buckets;

      // Insert the entry at the head of the new bucket's list.
      entry->next = new_buckets[new_index];
      new_buckets[new_index] = entry;

      entry = next_entry;
    }
  }

  // Free the old bucket array.
  free(map->buckets);

  // Update the map with the new bucket array and size.
  map->buckets = new_buckets;
  map->num_buckets = new_num_buckets;

  return 0;  // Success
}

hash_map_t* hash_map_new(size_t initial_capacity) {
  hash_map_t* map = (hash_map_t*)malloc(sizeof(hash_map_t));
  if (!map) {
    return NULL;  // Memory allocation failed
  }

  // Ensure initial_capacity is at least 1.
  if (initial_capacity == 0) {
    initial_capacity = 16;
  }

  map->num_buckets = initial_capacity;
  map->size = 0;

  map->buckets =
      (hash_map_entry_t**)calloc(map->num_buckets, sizeof(hash_map_entry_t*));
  if (!map->buckets) {
    free(map);
    return NULL;  // Memory allocation failed
  }

  // Initialize iterator state
  map->current_bucket_index = 0;
  map->current_entry = NULL;

  return map;
}

void hash_map_delete(hash_map_t* map, func_value_wrap_ptr_free_t fun_free) {
  if (!map) return;

  for (size_t i = 0; i < map->num_buckets; ++i) {
    hash_map_entry_t* entry = map->buckets[i];
    while (entry != NULL) {
      hash_map_entry_t* next_entry = entry->next;
      if (fun_free && entry->value.ptr) fun_free(entry->value.ptr);
      free(entry);
      entry = next_entry;
    }
  }

  free(map->buckets);
  free(map);
}

int hash_map_put(hash_map_t* map, uint32_t key, value_wrap_t value) {
  if (!map) return -22;

  if ((float)(map->size + 1) / map->num_buckets > def_default_load_factor) {
    hash_map_resize(map);
  }

  uint32_t hash = default_hash_func(key);
  size_t index = get_bucket_index(map, hash);

  // Check if the key already exists.
  hash_map_entry_t* entry = map->buckets[index];
  while (entry != NULL) {
    if (entry->key == key) {
      // Key found, update the value.
      entry->value = value;
      return 0;  // Success (update)
    }
    entry = entry->next;
  }

  // Key not found, create a new entry.
  hash_map_entry_t* new_entry =
      (hash_map_entry_t*)malloc(sizeof(hash_map_entry_t));
  if (!new_entry) {
    return -2;  // Memory allocation failed
  }

  new_entry->key = key;
  new_entry->value = value;
  new_entry->next = map->buckets[index];  // Insert at the head of the list.
  map->buckets[index] = new_entry;
  map->size++;

  return 0;  // Success (insert)
}

value_wrap_t* hash_map_get(const hash_map_t* map, uint32_t key) {
  if (!map) return 0;

  uint32_t hash = default_hash_func(key);
  size_t index = get_bucket_index(map, hash);

  hash_map_entry_t* entry = map->buckets[index];
  while (entry != NULL) {
    if (entry->key == key) {
      return &entry->value;  // Key found, return the value.
    }
    entry = entry->next;
  }

  return 0;
}

int hash_map_remove(hash_map_t* map, uint32_t key) {
  if (!map) return -22;

  uint32_t hash = default_hash_func(key);
  size_t index = get_bucket_index(map, hash);

  hash_map_entry_t* prev_entry = NULL;
  hash_map_entry_t* current_entry = map->buckets[index];

  while (current_entry != NULL) {
    if (current_entry->key == key) {
      // Key found, remove the entry.
      if (prev_entry == NULL) {
        // The entry to remove is the head of the list.
        map->buckets[index] = current_entry->next;
      } else {
        // The entry to remove is in the middle or end of the list.
        prev_entry->next = current_entry->next;
      }
      free(current_entry);
      map->size--;
      return 0;  // Success
    }
    prev_entry = current_entry;
    current_entry = current_entry->next;
  }

  return -22;
}

size_t hash_map_size(const hash_map_t* map) { return map ? map->size : 0; }

int hash_map_is_empty(const hash_map_t* map) { return !hash_map_size(map); }

hash_map_iterator_t* hash_map_iterator_new(const hash_map_t* map) {
  hash_map_iterator_t* iter =
      (hash_map_iterator_t*)calloc(1, sizeof(hash_map_iterator_t));
  if (!iter) return 0;

  iter->map = map;
  iter->current_bucket_index = 0;
  iter->current_entry = 0;
  if (hash_map_iterator_next(iter) < 0) {
    hash_map_iterator_delete(iter);
    iter = 0;
  }

  return iter;
}

void hash_map_iterator_delete(hash_map_iterator_t* iter) {
  if (iter) free(iter);
}

int hash_map_iterator_next(hash_map_iterator_t* iter) {
  if (!iter || !iter->map || !iter->map->buckets) return -22;

  if (iter->current_entry && iter->current_entry->next) {
    iter->current_entry = iter->current_entry->next;
    return 0;  // Success
  }

  size_t start_index = iter->current_entry ? iter->current_bucket_index + 1
                                           : iter->current_bucket_index;
  for (size_t i = start_index; i < iter->map->num_buckets; ++i) {
    if (iter->map->buckets[i]) {
      iter->current_bucket_index = i;
      iter->current_entry = iter->map->buckets[i];
      return 0;  // Success
    }
  }

  iter->current_bucket_index = iter->map->num_buckets;  // Mark as end
  iter->current_entry = 0;
  return -12;  // End reached
}

uint32_t hash_map_iterator_get_key(const hash_map_iterator_t* iter) {
  if (!iter || !iter->current_entry) return 0;  // 0?
  return iter->current_entry->key;
}

value_wrap_t* hash_map_iterator_get_value(const hash_map_iterator_t* iter) {
  if (!iter || !iter->current_entry) return 0;
  return &iter->current_entry->value;
}
