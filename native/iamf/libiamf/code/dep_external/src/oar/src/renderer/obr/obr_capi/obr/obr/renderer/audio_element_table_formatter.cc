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

#include "obr/renderer/audio_element_table_formatter.h"

#include <cstddef>
#include <iomanip>
#include <ios>
#include <sstream>
#include <string>
#include <utility>
#include <vector>

#include "absl/log/absl_log.h"
#include "obr/renderer/audio_element_config.h"
#include "obr/renderer/audio_element_type.h"

namespace obr {

std::string AudioElementTableFormatter::FormatFloat(float number) {
  std::ostringstream oss;
  oss << std::fixed << std::setprecision(2) << number;
  std::string result = oss.str();
  if (result.size() < 7) {
    result = std::string(7 - result.size(), ' ') + result;
  }
  return result;
}

std::string AudioElementTableFormatter::PadString(const std::string& str,
                                                  size_t width) {
  if (str.size() < width) {
    return std::string(width - str.size(), ' ') + str;
  }
  return str;
}

std::string AudioElementTableFormatter::RenderRow(
    const std::string& element_data, const std::string& channel_data) {
  std::string row = "|";
  row += element_data;
  row += "|";
  row += channel_data;
  row += "|\n";
  return row;
}

std::string AudioElementTableFormatter::FormatBooleanToYesNo(bool value) {
  return value ? "Yes" : "No";
}

std::string AudioElementTableFormatter::FormatTable(
    std::vector<AudioElementConfig>& audio_elements) {
  // Define the header.
  // <string, size_t> pairs are used to define the width of each column.
  std::vector<std::pair<std::string, size_t>> header = {// Audio Element data
                                                        {"AE ID", 5},
                                                        {"AE Type", 19},
                                                        {"HeadLckd", 8},
                                                        {"BinFltXOA", 9},
                                                        {"BinFltPrfl", 11},
                                                        // Channel data
                                                        {"Ch ID", 5},
                                                        {"Ch Label", 8},
                                                        {"Azimuth", 9},
                                                        {"Elevation", 9},
                                                        {"Distance", 9},
                                                        {"LFE", 4}};

  // Render top border.
  std::string log_message = "+";
  for (const auto& column : header) {
    log_message += std::string(column.second, '-');
    log_message += "+";
  }
  log_message += "\n";

  // Render header.
  log_message += "|";
  for (const auto& column : header) {
    log_message += column.first;
    if (column.first.size() < column.second) {
      log_message += std::string(column.second - column.first.size(), ' ');
    }
    log_message += "|";
  }
  log_message += "\n";

  // Iterate over audio elements.
  std::string element_data;
  const std::string kNaString("N/A");
  for (auto& audio_element : audio_elements) {
    // Render middle border.
    log_message += "+";
    for (const auto& column : header) {
      log_message += std::string(column.second, '-');
      log_message += "+";
    }
    log_message += "\n";

    const auto type_string = GetAudioElementTypeStr(audio_element.GetType());
    if (!type_string.ok()) {
      ABSL_LOG(ERROR) << "Failed to get audio element type string.";
      return "";
    }

    // For passthrough types, show N/A for head-locked and binaural filter
    // columns.
    if (IsPassthroughType(audio_element.GetType())) {
      element_data =
          PadString(std::to_string(&audio_element - &audio_elements[0]),
                    header[0].second) +
          "|" + PadString(std::string(type_string.value()), header[1].second) +
          "|" + PadString(kNaString, header[2].second) + "|" +
          PadString(kNaString, header[3].second) + "|" +
          PadString(kNaString, header[4].second);
    } else {
      const auto profile_string = BinauralFilterProfileToString(
          audio_element.GetBinauralFilterProfile());
      element_data =
          PadString(std::to_string(&audio_element - &audio_elements[0]),
                    header[0].second) +
          "|" + PadString(std::string(type_string.value()), header[1].second) +
          "|" +
          PadString(FormatBooleanToYesNo(audio_element.IsHeadLocked()),
                    header[2].second) +
          "|" +
          PadString(
              std::to_string(audio_element.GetBinauralFiltersAmbisonicOrder()),
              header[3].second) +
          "|" +
          PadString(
              (profile_string.ok() ? *profile_string : std::string("Unknown")),
              header[4].second);
    }

    std::string channel_data;

    // Render passthrough channels (kPassthroughStereo).
    for (const auto& channel : audio_element.GetPassthroughChannels()) {
      channel_data = PadString(std::to_string(channel.GetChannelIndex()),
                               header[5].second) +
                     "|" + PadString(channel.GetID(), header[6].second) + "|" +
                     PadString(kNaString, header[7].second) + "|" +
                     PadString(kNaString, header[8].second) + "|" +
                     PadString(kNaString, header[9].second) + "|" +
                     PadString(kNaString, header[10].second);

      log_message += RenderRow(element_data, channel_data);
    }

    // Render Ambisonic channels.
    for (const auto& channel : audio_element.GetAmbisonicChannels()) {
      channel_data = PadString(std::to_string(channel.GetChannelIndex()),
                               header[5].second) +
                     "|" + PadString(channel.GetID(), header[6].second) + "|" +
                     PadString(kNaString, header[7].second) + "|" +
                     PadString(kNaString, header[8].second) + "|" +
                     PadString(kNaString, header[9].second) + "|" +
                     PadString(kNaString, header[10].second);

      log_message += RenderRow(element_data, channel_data);
    }

    // Render loudspeaker channels.
    for (const auto& channel : audio_element.GetLoudspeakerChannels()) {
      channel_data =
          PadString(std::to_string(channel.GetChannelIndex()),
                    header[5].second) +
          "|" + PadString(channel.GetID(), header[6].second) + "|" +
          PadString(FormatFloat(channel.GetAzimuth()), header[7].second) + "|" +
          PadString(FormatFloat(channel.GetElevation()), header[8].second) +
          "|" +
          PadString(FormatFloat(channel.GetDistance()), header[9].second) +
          "|" +
          PadString(FormatBooleanToYesNo(channel.GetIsLFE()),
                    header[10].second);

      log_message += RenderRow(element_data, channel_data);
    }

    // Render object channels.
    for (const auto& channel : audio_element.GetObjectChannels()) {
      channel_data =
          PadString(std::to_string(channel.GetChannelIndex()),
                    header[5].second) +
          "|" + PadString(channel.GetID(), header[6].second) + "|" +
          PadString(FormatFloat(channel.GetAzimuth()), header[7].second) + "|" +
          PadString(FormatFloat(channel.GetElevation()), header[8].second) +
          "|" +
          PadString(FormatFloat(channel.GetDistance()), header[9].second) +
          "|" + PadString(kNaString, header[10].second);

      log_message += RenderRow(element_data, channel_data);
    }
  }

  // Render bottom border.
  log_message += "+";
  for (const auto& column : header) {
    log_message += std::string(column.second, '-');
    log_message += "+";
  }
  log_message += "\n";

  return log_message;
}

}  // namespace obr
