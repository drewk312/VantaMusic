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

#ifndef OBR_DSP_UTILS_H_
#define OBR_DSP_UTILS_H_

#include <cstddef>

#include "obr/audio_buffer/audio_buffer.h"

// This code is forked from Resonance Audio's `dsp/utils.h`.
namespace obr {

/*!\brief Ceils the given `size` to the next multiple of given
 * `frames_per_buffer`.
 *
 * \param size Input size in frames.
 * \param frames_per_buffer Frames per buffer.
 * \return Ceiled size in frames.
 */
size_t CeilToMultipleOfFramesPerBuffer(size_t size, size_t frames_per_buffer);

/*!\brief Generates a Hann window.
 *
 * \param full_window True to generate a full window, false to generate a half.
 * \param window_length Length of the window to be generated. Must be less than
 *        or equal to the number of frames in the `buffer`.
 * \param buffer AudioBuffer::Channel to which the window is written, the number
 *        of frames will be the length in samples of the generated Hann window.
 */
void GenerateHannWindow(bool full_window, size_t window_length,
                        AudioBuffer::Channel* buffer);

}  // namespace obr

#endif  // OBR_DSP_UTILS_H_
