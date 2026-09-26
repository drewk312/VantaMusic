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

#include <cstddef>
#include <cstdint>
#include <cstdio>
#include <string>

#include "absl/flags/flag.h"
#include "absl/flags/parse.h"
#include "absl/flags/usage.h"
#include "absl/log/absl_log.h"
#include "absl/status/status.h"
#include "absl/strings/string_view.h"
#include "obr/cli/obr_cli_lib.h"
#include "obr/renderer/audio_element_config.h"
#include "obr/renderer/audio_element_type.h"

// TODO(b/402659240): Support all input types listed in "audio_element_type.h".
/*!\brief Command line interface for obr.
 *
 * Takes single wav file and renders it to binaural. Combination of
 * multiple wav files (e.g. 7.1.4 + 3OA) as an input is not supported.
 * Only 16-bit wav files are supported.
 * Sample rates different from 48000 Hz were not tested.
 */
ABSL_FLAG(obr::AudioElementType, input_type,
          obr::AudioElementType::kInvalidType,
          "Type of input. Currently `1OA`, `2OA`, `3OA`, `4OA`, `7.1.4 `, and "
          "`OBA` are supported.");
ABSL_FLAG(
    std::string, oba_metadata_file, "",
    "Full path to the textproto file containing object metadata if input type "
    "is `OBA`.");
ABSL_FLAG(std::string, input_file, "", "Full path to the input WAV file.");
ABSL_FLAG(std::string, output_file, "/tmp/output.wav",
          "Full path to the output WAV file.");
ABSL_FLAG(
    uint64_t, buffer_size, 256,
    "Processing buffer size; i.e., number of sample per channel per frame.");
ABSL_FLAG(
    obr::BinauralFilterProfile, filter_type,
    obr::BinauralFilterProfile::kAmbient,
    "Binaural filter type: Direct, Ambient, or Reverberant (default: Ambient)");

namespace obr {

// TODO(b/402659240): Use `GetAudioElementTypeStringMap()` for flag-parsing too.
bool AbslParseFlag(absl::string_view text, AudioElementType* input_type,
                   std::string* error) {
  if (text.empty()) {
    *error = "No input type specified.";
    return false;
  } else if (text == "1OA") {
    *input_type = AudioElementType::k1OA;
  } else if (text == "2OA") {
    *input_type = AudioElementType::k2OA;
  } else if (text == "3OA") {
    *input_type = AudioElementType::k3OA;
  } else if (text == "4OA") {
    *input_type = AudioElementType::k4OA;
  } else if (text == "7.1.4") {
    *input_type = AudioElementType::kLayout7_1_4_ch;
  } else if (text == "OBA") {
    *input_type = AudioElementType::kObjectMono;
  } else {
    *error = "Unsupported input type.";
    return false;
  }
  return true;
}

std::string AbslUnparseFlag(AudioElementType input_type) {
  switch (input_type) {
    using enum AudioElementType;
    case k1OA:
      return "1OA";
    case k2OA:
      return "2OA";
    case k3OA:
      return "3OA";
    case k4OA:
      return "4OA";
    case kLayout7_1_4_ch:
      return "7.1.4";
    case kObjectMono:
      return "OBA";
    default:
      return "Unsupported input type.";
  }
}

bool AbslParseFlag(absl::string_view text, BinauralFilterProfile* filter_type,
                   std::string* error) {
  if (text.empty()) {
    *error = "No filter type specified.";
    return false;
  } else if (text == "Direct") {
    *filter_type = BinauralFilterProfile::kDirect;
  } else if (text == "Ambient") {
    *filter_type = BinauralFilterProfile::kAmbient;
  } else if (text == "Reverberant") {
    *filter_type = BinauralFilterProfile::kReverberant;
  } else {
    *error = "Unsupported filter type.";
    return false;
  }
  return true;
}

std::string AbslUnparseFlag(BinauralFilterProfile filter_type) {
  switch (filter_type) {
    using enum BinauralFilterProfile;
    case BinauralFilterProfile::kDirect:
      return "Direct";
    case BinauralFilterProfile::kAmbient:
      return "Ambient";
    case BinauralFilterProfile::kReverberant:
      return "Reverberant";
    default:
      return "Unsupported filter type.";
  }
}

}  // namespace obr

int main(int argc, char* argv[]) {
  absl::SetProgramUsageMessage(argv[0]);
  absl::ParseCommandLine(argc, argv);

  const auto status = obr::ObrCliMain(
      absl::GetFlag(FLAGS_input_type), absl::GetFlag(FLAGS_oba_metadata_file),
      absl::GetFlag(FLAGS_input_file), absl::GetFlag(FLAGS_output_file),
      static_cast<size_t>(absl::GetFlag(FLAGS_buffer_size)),
      absl::GetFlag(FLAGS_filter_type));
  if (!status.ok()) {
    ABSL_LOG(ERROR) << status;
    return 1;
  }
  return 0;
}
