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

#ifndef OBR_AUDIO_BUFFER_ALIGNED_ALLOCATOR_H_
#define OBR_AUDIO_BUFFER_ALIGNED_ALLOCATOR_H_

#include <cstddef>
#include <cstdlib>
#include <memory>

// This code is forked from Resonance Audio's `aligned_allocator.h`.
namespace obr {

// Performs static assert checks on the types size and alignment parameters.
template <size_t TypeSize, size_t Alignment>
void StaticAlignmentCheck() {
  const bool alignment_is_power_of_two =
      !(Alignment == 0) && !(Alignment & (Alignment - 1));
  static_assert(alignment_is_power_of_two, "Alignment must be power of two");

  const bool type_size_is_power_of_two = !(TypeSize & (TypeSize - 1));
  static_assert(type_size_is_power_of_two, "Type size must be power of two");
}

// Returns a pointer to aligned memory.
template <typename Type, typename SizeType, typename PointerType>
PointerType AlignedMalloc(SizeType size, SizeType alignment) {
  const SizeType data_size = size * sizeof(Type);
  const SizeType offset = alignment - 1 + sizeof(PointerType);
  void* mem_block_begin = malloc(data_size + offset);
  if (mem_block_begin == nullptr) {
    return nullptr;
  }
  // Find memory aligned address.
  void** mem_block_aligned = reinterpret_cast<void**>(
      ((reinterpret_cast<SizeType>(mem_block_begin) + offset) &
       (~(alignment - 1))));
  // Save pointer to original block right before the aligned block.
  mem_block_aligned[-1] = mem_block_begin;
  return reinterpret_cast<PointerType>(mem_block_aligned);
}

// Frees memory that has been aligned with `AlignedMalloc`.
template <typename PointerType>
void AlignedFree(PointerType mem_block_aligned) {
  free(*(reinterpret_cast<void**>(mem_block_aligned) - 1));
}

/*!\brief Class that allocates aligned memory.
 * It is derived from std::allocator class to be used with STL containers.
 *
 * \tparam Type Datatype of container to allocate.
 * \tparam Alignment Size of memory alignment.
 */
template <typename Type, size_t Alignment>
class AlignedAllocator : public std::allocator<Type> {
 public:
  using Pointer = typename std::allocator_traits<std::allocator<Type>>::pointer;
  using ConstPointer =
      typename std::allocator_traits<std::allocator<Type>>::const_pointer;
  using SizeType =
      typename std::allocator_traits<std::allocator<Type>>::size_type;

  AlignedAllocator() { StaticAlignmentCheck<sizeof(Type), Alignment>(); }

  /*!\brief Allocates memory for `size` elements and returns a pointer that is
   * aligned to a multiple to `Alignment`.
   *
   * \param size Number of elements to allocate.
   * \return Returns memory aligned pointer.
   */
  Pointer allocate(SizeType size) { return allocate(size, nullptr); }

  /*!\brief Allocates memory for `size` elements and returns a pointer that is
   * aligned to a multiple to `Alignment`.
   *
   * \param size Number of elements to allocate.
   * \return Returns memory aligned pointer.
   */
  Pointer allocate(SizeType size, ConstPointer /* hint */) {
    return AlignedMalloc<Type, SizeType, Pointer>(size, Alignment);
  }

  void deallocate(Pointer mem_block_aligned, size_t size) {
    AlignedFree<Pointer>(mem_block_aligned);
  }

  // Copy constructor to support rebind operation (to make MSVC happy).
  template <typename U>
  explicit AlignedAllocator<Type, Alignment>(
      const AlignedAllocator<U, Alignment>& other) {}

  // Rebind is used to allocate container internal variables of type `U`
  // (which don't need to be aligned).
  template <typename U>
  struct rebind {
    typedef AlignedAllocator<U, Alignment> other;
  };
};

}  // namespace obr

#endif  // OBR_AUDIO_BUFFER_ALIGNED_ALLOCATOR_H_
