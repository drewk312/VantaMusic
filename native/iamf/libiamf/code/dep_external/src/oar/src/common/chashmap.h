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
 * @file chashmap.h
 * @brief Hash map APIs.
 * @version 2.0.0
 * @date Created 12/11/2025
 **/

#ifndef __CHASHMAP_H__
#define __CHASHMAP_H__

#include <stddef.h>
#include <stdint.h>

#include "cvalue.h"  // For value_wrap_t

#define def_hash_map_capacity_elements 32

// Opaque structure for the hash_map_t.
typedef struct HashMap hash_map_t;
typedef struct HashMapEntry hash_map_entry_t;

/**
 * @brief Create a new hash_map_t instance.
 *
 * @param initial_capacity The initial number of buckets in the hashmap.
 * @return hash_map_t* Success: pointer to the new hash_map_t instance; Failure:
 * NULL.
 */
hash_map_t* hash_map_new(size_t capacity);

/**
 * @brief Destroy a hash_map_t instance and release its resources.
 *
 * Note: This function does not free the memory of the stored keys or values.
 * It is the caller's responsibility to manage the memory of the keys and
 * values, for example, by iterating through the map and freeing them before
 * destroying the map.
 *
 * @param map Pointer to the hash_map_t instance to destroy.
 */
void hash_map_delete(hash_map_t* map, func_value_wrap_ptr_free_t fun_free);

/**
 * @brief Insert or update a key-value pair in the hash_map_t.
 *
 * If the key already exists in the map, its value is updated with the new
 * value. The function does not take ownership of the key or value; it stores
 * the provided pointers.
 *
 * @param map Pointer to the hash_map_t instance.
 * @param key The key.
 * @param value The value_wrap_t value.
 * @return int Success: 0; Failure: error code (e.g., memory allocation failed).
 */
int hash_map_put(hash_map_t* map, uint32_t key, value_wrap_t value);

/**
 * @brief Get the value associated with a key in the hash_map_t.
 *
 * @param map Pointer to the hash_map_t instance.
 * @param key The key to look up.
 * @return value_wrap_t Success: value_wrap_t associated with the key;
 * Failure: A value_wrap_t with .ptr = NULL if the key is not found.
 */
value_wrap_t* hash_map_get(const hash_map_t* map, uint32_t key);

/**
 * @brief Remove a key-value pair from the hash_map_t.
 *
 * @param map Pointer to the hash_map_t instance.
 * @param key The key to remove.
 * @return int Success: 0; Failure: error code (e.g., key not found).
 */
int hash_map_remove(hash_map_t* map, uint32_t key);

/**
 * @brief Get the number of key-value pairs in the hash_map_t.
 *
 * @param map Pointer to the hash_map_t instance.
 * @return size_t The number of entries in the map.
 */
size_t hash_map_size(const hash_map_t* map);

/**
 * @brief Check if the hash_map_t is empty.
 *
 * @param map Pointer to the hash_map_t instance.
 * @return int 1 if the map is empty, 0 otherwise.
 */
int hash_map_is_empty(const hash_map_t* map);

/**
 * @brief Structure for iterating over hash_map_t entries.
 */
typedef struct HashMapIterator hash_map_iterator_t;

/**
 * @brief Create an iterator for the hash_map_t.
 *
 * @param map Pointer to the hash_map_t instance.
 * @return hash_map_iterator_t The created iterator. If the map is NULL or
 * empty, the iterator will be in an "end" state.
 */
hash_map_iterator_t* hash_map_iterator_new(const hash_map_t* map);

void hash_map_iterator_delete(hash_map_iterator_t* iter);

/**
 * @brief Advance the iterator to the next element.
 *
 * @param iter Pointer to the iterator.
 * @return int 0 if advanced to a next element, non-zero if the end is reached.
 */
int hash_map_iterator_next(hash_map_iterator_t* iter);

/**
 * @brief Get the key of the current element pointed to by the iterator.
 *
 * The iterator must be valid (not at the end).
 *
 * @param iter Pointer to the iterator.
 * @return uint32_t The key of the current element.
 */
uint32_t hash_map_iterator_get_key(const hash_map_iterator_t* iter);

/**
 * @brief Get the value of the current element pointed to by the iterator.
 *
 * The iterator must be valid (not at the end).
 *
 * @param iter Pointer to the iterator.
 * @return value_wrap_t The value of the current element.
 */
value_wrap_t* hash_map_iterator_get_value(const hash_map_iterator_t* iter);

#endif  // __CHASHMAP_H__
