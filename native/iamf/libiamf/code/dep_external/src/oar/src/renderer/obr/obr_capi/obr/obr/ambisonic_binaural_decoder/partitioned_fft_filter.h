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

#ifndef OBR_PARTITIONED_FFT_FILTER_H_
#define OBR_PARTITIONED_FFT_FILTER_H_

#include <cstddef>

#include "obr/ambisonic_binaural_decoder/fft_manager.h"
#include "obr/audio_buffer/audio_buffer.h"

struct PFFFT_Setup;

// This code is forked from the Resonance Audio's `partitioned_fft_filter.h.
namespace obr {

/*!\brief Class performing a FFT-based overlap and add FIR convolution.
 *
 * Given an FFT size N and a filter size M > N/2; the filter is broken in to
 * floor(M/(N/2)) partitions in the time domain. This results in a set of
 * frequency domain "filters" of length (N/2)+1.
 */
class PartitionedFftFilter {
 public:
  // Typedef declares the data type for storing frequency domain buffers. Each
  // channel stores the kernel for a partition.
  typedef AudioBuffer FreqDomainBuffer;

  /*!\brief Constructor preallocates memory based on the `filter_size`.
   * This can be used for simplicity if the filter size will be constant after
   * creation.
   *
   * \param filter_size Length of the time domain filter in samples. This will
   *        be increased such that it becomes a multiple of `chunk_size_`.
   * \param frames_per_buffer Number of points in each time domain input buffer.
   * \param fft_manager Pointer to a manager to perform FFT transformations.
   */
  PartitionedFftFilter(size_t filter_size, size_t frames_per_buffer,
                       FftManager* fft_manager);

  /*!\brief Constructor preallocates memory based on the `max_filter_size`.
   * The `fft_size_` will be twice `frames_per_buffer` if this is a power of two
   * and twice the next larger power of two if it is not.
   *
   * \param filter_size Length of the time domain filter in samples. This will
   *        be increased such that it becomes a multiple of `chunk_size_`.
   * \param frames_per_buffer Number of points in each time domain input buffer.
   * \param max_filter_size Maximum length that `filter_size` can get.
   * \param fft_manager Pointer to a manager for all fft related functionality.
   */
  PartitionedFftFilter(size_t filter_size, size_t frames_per_buffer,
                       size_t max_filter_size, FftManager* fft_manager);

  /*!\brief Initializes the FIR filter from a time domain kernel.
   *
   * \param kernel Time domain filter to be used for processing.
   */
  void SetTimeDomainKernel(const AudioBuffer::Channel& kernel);

  /*!\brief Initializes the FIR filter from a precomputed frequency domain
   * kernel.
   *
   * \param kernel Frequency domain filter to be used for processing.
   */
  void SetFreqDomainKernel(const FreqDomainBuffer& kernel);

  /*!\brief Replaces a partition indicated by the `partition_index` with
   * `kernel_chunk`'s frequency domain equivalent.
   *
   * \param partition_index Location (partition) of the time domain filter we
   *        wish to replace.
   * \param kernel_chunk `fft_size_`/2 length chunk of a filter used to
   *        replace the `partition_index`th partition.
   */
  void ReplacePartition(size_t partition_index,
                        const AudioBuffer::Channel& kernel_chunk);

  /*!\brief Alters the filter length by adding or removing partitions in the
   * frequency domain. If `new_filter_size` is not a multiple of `chunk_size_`
   * (i.e. frames per buffer), then the time domain filter kernel will be
   * zeropadded to a multiple of `chunk_size_`.
   *
   * \param new_filter_size New length of the time domain filter kernel.
   */
  void SetFilterLength(size_t new_filter_size);

  /*!\brief Processes a block of frequency domain samples. The size of the input
   * block must be `fft_size_`.
   *
   * \param input Frequency domain input buffer.
   */
  void Filter(const FreqDomainBuffer::Channel& input);

  /*!\brief Returns block of filtered signal output of size `fft_size_`/2.
   *
   * \param output Time domain block filtered with the given kernel.
   */
  void GetFilteredSignal(AudioBuffer::Channel* output);

  /*!\brief Resets the filter state.
   */
  void Clear();

 private:
  friend class PartitionedFftFilterFrequencyBufferTest;

  /*!\brief Adjusts the `num_partitions_` and the size of `freq_domain_buffers_`
   * for use with a new time domain filter kernel greater in length than the
   * previous kernel.
   *
   * \param new_filter_size Length of the time domain filter kernel.
   */
  void ResetFreqDomainBuffers(size_t new_filter_size);

  // Manager for all FFT related functionality (not owned).
  FftManager* const fft_manager_;

  // Number of points in the `fft_manager_`s FFT.
  const size_t fft_size_;

  // Size of each partition of the filter in time domain.
  const size_t chunk_size_;

  // Number of frames in each buffer of input data.
  const size_t frames_per_buffer_;

  // Maximum filter size in samples.
  const size_t max_filter_size_;

  // Maximum partition count.
  const size_t max_num_partitions_;

  // Filter size in samples.
  size_t filter_size_;

  // Partition Count.
  size_t num_partitions_;

  // Kernel buffer in frequency domain.
  FreqDomainBuffer kernel_freq_domain_buffer_;

  // Buffer selector to switch between two filtered signal buffers.
  size_t buffer_selector_;

  // The freq_domain_buffer we will write new incoming audio into.
  size_t curr_front_buffer_;

  // Frequency domain buffer used to perform filtering.
  FreqDomainBuffer freq_domain_buffer_;

  // Two buffers that are consecutively filled with filtered signal output.
  AudioBuffer filtered_time_domain_buffers_;

  // Accumulator for the outputs from each convolution partition
  FreqDomainBuffer freq_domain_accumulator_;

  // Temporary time domain buffer to store output when zero padding has been
  // applied due to non power of two input buffer lengths.
  AudioBuffer temp_zeropad_buffer_;

  // Temporary time domain buffer to hold time domain kernel chunks during
  // conversion of a kernel from time to frequency domain.
  AudioBuffer temp_kernel_chunk_buffer_;
};

}  // namespace obr

#endif  // OBR_PARTITIONED_FFT_FILTER_H_
