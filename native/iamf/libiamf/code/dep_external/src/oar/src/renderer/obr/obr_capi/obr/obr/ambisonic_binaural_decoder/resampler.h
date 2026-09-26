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

#ifndef OBR_RESAMPLER_H_
#define OBR_RESAMPLER_H_

#include <cstddef>

#include "obr/audio_buffer/audio_buffer.h"

// This code is forked from Resonance Audio's `resampler.h`.
namespace obr {

/*!\brief Class that provides rational resampling of audio data. */
class Resampler {
 public:
  Resampler();

  /*!\brief Resamples an `AudioBuffer` of input data sampled at
   * `source_frequency` to `destination_frequency`.
   *
   * \param input Input data to be resampled.
   * \param output Resampled output data.
   */
  void Process(const AudioBuffer& input, AudioBuffer* output);

  /*!\brief Returns the maximum length which the output buffer will be, given
   * the current source and destination frequencies and input length. The actual
   * output length will either be this or one less.
   *
   * \param input_length Length of the input.
   * \return Maximum length of the output.
   */
  size_t GetMaxOutputLength(size_t input_length) const;

  /*!\brief Returns the next length which the output buffer will be, given the
   * current source and destination frequencies and input length.
   *
   * \param input_length Length of the input.
   * \return Next length of the output.
   */
  size_t GetNextOutputLength(size_t input_length) const;

  /*!\brief Sets the source and destination sampling rate as well as the number
   * of channels. Note this method only resets the filter state number of
   * channel changes.
   *
   * \param source_frequency Sampling rate of input data.
   * \param destination_frequency Desired output sampling rate.
   * \param num_channels Number of channels to process.
   */
  void SetRateAndNumChannels(int source_frequency, int destination_frequency,
                             size_t num_channels);

  /*!\brief Returns whether the sampling rates provided are supported by the
   * resampler.
   *
   * \param source Source sampling rate.
   * \param destination Destination sampling rate.
   * \return True if the sampling rate pair are supported.
   */
  static bool AreSampleRatesSupported(int source, int destination);

  // Resets the inner state of the `Resampler` allowing its use repeatedly on
  // different data streams.
  void ResetState();

 private:
  friend class PolyphaseFilterTest;
  /*!\brief Initializes the `state_` buffer.
   *
   * Called when sampling rate is changed or the state is reset.
   *
   * \param old_state_num_frames Number of frames in the `state_` buffer
   *        previous to the most recent call to `GenerateInterpolatingFilter`.
   */
  void InitializeStateBuffer(size_t old_state_num_frames);

  /*!\brief Generates a windowed sinc to act as the interpolating/anti-aliasing
   * filter.
   *
   * \param sample_rate The system sampling rate.
   */
  void GenerateInterpolatingFilter(int sample_rate);

  /*!\brief Arranges the anti aliasing filter coefficients in polyphase filter
   * format.
   *
   * \param filter_length Number of frames in `filter` containing filter
   *        coefficients.
   * \param filter Vector of filter coefficients.
   */
  void ArrangeFilterAsPolyphase(size_t filter_length,
                                const AudioBuffer::Channel& filter);

  /*!\brief Generates Hann windowed sinc function anti aliasing filters.
   *
   * \param cutoff_frequency Transition band (-3dB) frequency of the filter.
   * \param sample_rate The system sampling rate.
   * \param filter_length Number of frames in `buffer` containing filter
   *        coefficients.
   * \param buffer `AudioBuffer::Channel` to contain the filter coefficients.
   */
  void GenerateSincFilter(float cutoff_frequency, float sample_rate,
                          size_t filter_length, AudioBuffer::Channel* buffer);

  // Rate of the interpolator section of the rational sampling rate converter.
  size_t up_rate_;

  // Rate of the decimator section of the rational sampling rate convereter.
  size_t down_rate_;

  // Time variable for the polyphase filter.
  size_t time_modulo_up_rate_;

  // Marks the last processed sample of the input.
  size_t last_processed_sample_;

  // Number of channels in the `AudioBuffer`s processed.
  size_t num_channels_;

  // Number of filter coefficients in each phase of the polyphase filter.
  size_t coeffs_per_phase_;

  // Filter coefficients stored in polyphase form.
  AudioBuffer transposed_filter_coeffs_;

  // Filter coefficients in planar form, used for calculating the transposed
  // filter.
  AudioBuffer temporary_filter_coeffs_;

  // Buffer holding the samples of input required between input buffers.
  AudioBuffer state_;
};

}  // namespace obr

#endif  // OBR_RESAMPLER_H_
