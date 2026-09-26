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
#ifndef OBR_CLI_OBR_CLI_LIB_H_
#define OBR_CLI_OBR_CLI_LIB_H_

#include <cstddef>
#include <cstring>
#include <string>

#include "absl/status/status.h"
#include "obr/cli/obr_cli_lib.h"
#include "obr/renderer/audio_element_config.h"
#include "obr/renderer/audio_element_type.h"

namespace obr {

/*!\brief Renders an input WAV file binaurally to the output WAV file.
 *
 * \param input_type Type of input.
 * \param oba_metadata_filename Full path to the textproto file containing
 *        object metadata. Only needed if the input type is `kObjectMono`.
 * \param input_filename Full path to the input WAV file.
 * \param output_filename FUll path to the output WAV file.
 * \param buffer_size Processing buffer size.
 * \param filter_profile Binaural filter profile (Direct / Ambient /
 * Reverberant).
 * \return `absl::OkStatus()` on success. A specific status on failure.
 */
absl::Status ObrCliMain(AudioElementType input_type,
                        const std::string& oba_metadata_filename,
                        const std::string& input_filename,
                        const std::string& output_filename, size_t buffer_size,
                        BinauralFilterProfile filter_profile);

}  // namespace obr

#endif  // OBR_CLI_OBR_CLI_LIB_H_
