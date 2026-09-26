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
#include "obr/cli/obr_cli_lib.h"

#include <cstddef>
#include <cstdint>
#include <cstdio>
#include <fstream>
#include <sstream>
#include <string>
#include <vector>

#include "absl/log/absl_check.h"
#include "absl/log/absl_log.h"
#include "absl/status/status.h"
#include "absl/strings/str_cat.h"
#include "obr/audio_buffer/audio_buffer.h"
#include "obr/cli/proto/oba_metadata.pb.h"
#include "obr/renderer/audio_element_config.h"
#include "obr/renderer/audio_element_type.h"
#include "obr/renderer/obr_impl.h"
#include "src/dsp/read_wav_file.h"
#include "src/dsp/read_wav_info.h"
#include "src/dsp/write_wav_file.h"
#include "src/google/protobuf/text_format.h"

namespace obr {

absl::Status ObrCliMain(AudioElementType input_type,
                        const std::string& oba_metadata_filename,
                        const std::string& input_filename,
                        const std::string& output_filename, size_t buffer_size,
                        BinauralFilterProfile filter_profile) {
  // Parse the textproto file if OBA input is selected.
  obr_cli_proto::SourceList source_list;
  if (IsObjectType(input_type)) {
    if (oba_metadata_filename.empty()) {
      return absl::InvalidArgumentError("No OBA metadata file specified.");
    }
    ABSL_LOG(INFO) << "Processing OBA input with metadata file: \""
                   << oba_metadata_filename << "\".";

    // Parse the OBA metadata file.
    std::ifstream oba_metadata_file(oba_metadata_filename);

    if (!oba_metadata_file) {
      return absl::InvalidArgumentError(absl::StrCat(
          "Failed to open file: \"", oba_metadata_filename, "\"."));
    }

    std::ostringstream oba_metadata_stream;
    oba_metadata_stream << oba_metadata_file.rdbuf();

    if (!google::protobuf::TextFormat::ParseFromString(
            oba_metadata_stream.str(), &source_list)) {
      return absl::InvalidArgumentError(
          absl::StrCat("Failed to parse OBA metadata file: \"",
                       oba_metadata_filename, "\"."));
    }
  }

  // Read the input wav file.
  FILE* input_file = std::fopen(input_filename.c_str(), "rb");
  ReadWavInfo info;
  ABSL_CHECK_NE(ReadWavHeader(input_file, &info), 0)
      << "Error reading header of file \"" << input_filename << "\".";

  // Log the header info.
  ABSL_LOG(INFO) << "Input WAV header info:";
  ABSL_LOG(INFO) << "  num_channels= " << info.num_channels;
  ABSL_LOG(INFO) << "  sample_rate_hz= " << info.sample_rate_hz;
  ABSL_LOG(INFO) << "  remaining_samples= " << info.remaining_samples;
  ABSL_LOG(INFO) << "  bit_depth= " << info.bit_depth;
  ABSL_LOG(INFO) << "  destination_alignment_bytes= "
                 << info.destination_alignment_bytes;
  ABSL_LOG(INFO) << "  encoding= " << info.encoding;
  ABSL_LOG(INFO) << "  sample_format= " << info.sample_format;

  int input_wav_bits_per_sample = info.bit_depth;
  int input_wav_nch = info.num_channels;
  int input_wav_fs = info.sample_rate_hz;
  size_t input_wav_total_number_of_samples = info.remaining_samples;

  if (input_wav_bits_per_sample != 16) {
    return absl::InvalidArgumentError(absl::StrCat(
        "Unsupported number of bits per sample: ", input_wav_bits_per_sample));
  }

  ABSL_LOG(INFO) << "Declared input type: "
                 << GetAudioElementTypeStr(input_type) << "; Processing \""
                 << input_filename << "\"; " << input_wav_nch << " channels; "
                 << input_wav_fs << " Hz; " << input_wav_bits_per_sample
                 << " bit; "
                 << "processing buffer size: " << buffer_size << " samples.";

  //  Setup output number of channels.
  int output_wav_nch = 2;

  // Initialize the buffers.
  std::vector<int16_t> input_buffer_int16(buffer_size * input_wav_nch);
  std::vector<int16_t> output_buffer_int16(buffer_size * output_wav_nch);
  std::vector<std::vector<float>> input_buffer_float(
      input_wav_nch, std::vector<float>(buffer_size));
  std::vector<std::vector<float>> output_buffer_float(
      output_wav_nch, std::vector<float>(buffer_size));

  // Initialize obr object.
  ObrImpl obr_obj(buffer_size, input_wav_fs);

  // If OBA input, set the sources within the renderer.
  if (input_type == AudioElementType::kObjectMono) {
    ABSL_LOG(INFO) << "Providing OBA metadata to the renderer:";
    for (const auto& source : source_list.source()) {
      ABSL_LOG(INFO) << "  WAV file ch (0-indexed): " << source.input_channel();
      ABSL_LOG(INFO) << "    Azimuth: " << source.azimuth();
      ABSL_LOG(INFO) << "    Elevation: " << source.elevation();
      ABSL_LOG(INFO) << "    Distance: " << source.distance();
      ABSL_LOG(INFO) << "    Gain: " << source.gain();

      // Add audio element.
      auto status = obr_obj.AddAudioElement(input_type, filter_profile);
      if (!status.ok()) {
        return absl::InvalidArgumentError(
            absl::StrCat("Error adding audio element: ", status.message()));
      }

      // Update positions of the objects.
      size_t current_audio_element_index =
          obr_obj.GetNumberOfAudioElements() - 1;

      status = obr_obj.UpdateObjectPosition(
          current_audio_element_index, source.azimuth(), source.elevation(),
          source.distance());
      if (!status.ok()) {
        return absl::InvalidArgumentError(
            absl::StrCat("Error updating object position: ", status.message()));
      }
    }
  } else {
    // Add single audio element.
    auto status = obr_obj.AddAudioElement(input_type, filter_profile);
    if (!status.ok()) {
      return absl::InvalidArgumentError(
          absl::StrCat("Error adding audio element: ", status.message()));
    }
  }

  // Some additional checks.
  if (input_wav_nch != obr_obj.GetNumberOfInputChannels()) {
    return absl::InvalidArgumentError(
        absl::StrCat("Mismatching number of input channels: (", input_wav_nch,
                     " vs ", obr_obj.GetNumberOfInputChannels(), ")"));
  }
  if (output_wav_nch != obr_obj.GetNumberOfOutputChannels()) {
    return absl::InvalidArgumentError(
        absl::StrCat("Mismatching number of output channels: (", output_wav_nch,
                     " vs ", obr_obj.GetNumberOfOutputChannels(), ")"));
  }

  // Display Audio Elements inside obr object.
  ABSL_LOG(INFO) << "\n" << obr_obj.GetAudioElementConfigLogMessage();

  // Output wav file.
  FILE* output_file = std::fopen(output_filename.c_str(), "wb");
  if (output_file == nullptr) {
    return absl::InvalidArgumentError(
        absl::StrCat("Error opening file: \"", output_filename, "\"."));
  }

  // Write the dummy header.
  ABSL_CHECK_NE(WriteWavHeader(output_file, 0, input_wav_fs, output_wav_nch), 0)
      << "Error writing header of file \"" << output_filename << "\".";

  // Main processing loop.
  size_t samples_remaining = input_wav_total_number_of_samples;
  size_t samples_written = 0;
  while (true) {
    if (samples_remaining == 0) {
      break;
    }

    // Read from the input file.
    size_t samples_read =
        Read16BitWavSamples(input_file, &info, input_buffer_int16.data(),
                            input_buffer_int16.size());

    // Convert int16_t interleaved to float planar.
    for (size_t smp = 0; smp < buffer_size; smp++) {
      for (size_t ch = 0; ch < input_wav_nch; ch++) {
        input_buffer_float[ch][smp] =
            static_cast<float>(input_buffer_int16[smp * input_wav_nch + ch]) /
            32768.0f;
      }
    }
    AudioBuffer input_buffer(input_wav_nch, buffer_size);
    input_buffer = input_buffer_float;
    AudioBuffer output_buffer(output_wav_nch, buffer_size);

    // Process.
    obr_obj.Process(input_buffer, &output_buffer);

    for (size_t smp = 0; smp < output_buffer.num_frames(); smp++) {
      for (size_t ch = 0; ch < output_buffer.num_channels(); ch++) {
        output_buffer_float[ch][smp] = output_buffer[ch][smp];
      }
    }

    // Convert float planar to int16_t interleaved.
    for (size_t smp = 0; smp < buffer_size; smp++) {
      for (size_t ch = 0; ch < output_wav_nch; ch++) {
        output_buffer_int16[smp * output_wav_nch + ch] =
            static_cast<int16_t>((output_buffer_float[ch][smp]) * 32768.0f);
      }
    }

    // Write to the output file
    WriteWavSamples(output_file, output_buffer_int16.data(),
                    output_buffer_int16.size());

    samples_remaining -= samples_read;
    samples_written += output_buffer_int16.size();
  }

  if (input_file != nullptr) {
    std::fclose(input_file);
  }

  std::fseek(output_file, 0, SEEK_SET);
  WriteWavHeader(output_file, samples_written, input_wav_fs, output_wav_nch);
  std::fclose(output_file);

  ABSL_LOG(INFO) << "Finished rendering file \"" << output_filename << "\".";
  return absl::OkStatus();
}

}  // namespace obr
