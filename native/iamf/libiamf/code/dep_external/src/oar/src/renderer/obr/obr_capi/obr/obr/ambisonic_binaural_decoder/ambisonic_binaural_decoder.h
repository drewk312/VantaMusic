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

#ifndef OBR_AMBISONIC_BINAURAL_DECODER_H_
#define OBR_AMBISONIC_BINAURAL_DECODER_H_

#include <cstddef>
#include <memory>
#include <vector>

#include "obr/ambisonic_binaural_decoder/fft_manager.h"
#include "obr/ambisonic_binaural_decoder/partitioned_fft_filter.h"
#include "obr/audio_buffer/audio_buffer.h"

// This code is forked from Resonance Audio's `ambisonic_binaural_decoder.h`.
namespace obr {

/*!\brief Decodes an Ambisonic sound field, of an arbitrary order, to binaural
 * audio by performing convolution in the spherical harmonics domain.
 */
class AmbisonicBinauralDecoder {
 public:
  /*!\brief Constructs an `AmbisonicBinauralDecoder` from an `AudioBuffer`
   * containing spherical harmonic representation of HRIRs. The order of
   * spherical harmonic-encoded HRIRs (hence the number of channels) must match
   * the order of the Ambisonic sound field input.
   *
   * \param sh_hrirs_L `AudioBuffer` containing time-domain spherical harmonic
   * encoded left ear HRIR/BRIR filters.
   * \param sh_hrirs_R `AudioBuffer` containing time-domain spherical harmonic
   * encoded right ear HRIR/BRIR filters.
   * \param frames_per_buffer Number of frames in each input/output buffer.
   * \param fft_manager Pointer to a manager to perform FFT transformations.
   */
  AmbisonicBinauralDecoder(const AudioBuffer& sh_hrirs_L,
                           const AudioBuffer& sh_hrirs_R,
                           size_t frames_per_buffer, FftManager* fft_manager);

  /*!\brief Processes an Ambisonic sound field input and outputs a binaurally
   * decoded 2-channel buffer.
   *
   * \param input Input buffer to be processed.
   * \param output Pointer to a 2-channel output buffer.
   */
  void ProcessAudioBuffer(const AudioBuffer& input, AudioBuffer* output);

 private:
  // Manager for all FFT related functionality (not owned).
  FftManager* const fft_manager_;

  // Spherical Harmonic HRIR filter kernels.
  std::vector<std::unique_ptr<PartitionedFftFilter>> sh_hrir_filters_L_,
      sh_hrir_filters_R_;

  // Frequency domain representation of the input signal.
  PartitionedFftFilter::FreqDomainBuffer freq_input_;

  // Temporary audio buffer to store the convolution output for asymmetric or
  // symmetric spherical harmonic HRIR.
  AudioBuffer filtered_input_;
};

}  // namespace obr

#endif  // OBR_AMBISONIC_BINAURAL_DECODER_H_
