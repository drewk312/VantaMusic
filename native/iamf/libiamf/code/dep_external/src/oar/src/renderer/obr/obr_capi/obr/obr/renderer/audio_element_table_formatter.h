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

#ifndef OBR_RENDERER_AUDIO_ELEMENT_TABLE_FORMATTER_H_
#define OBR_RENDERER_AUDIO_ELEMENT_TABLE_FORMATTER_H_

#include <cstddef>
#include <string>
#include <vector>

#include "obr/renderer/audio_element_config.h"

namespace obr {

/*!\brief Formats audio element configurations as a table for logging.
 *
 * This class provides functionality to format a collection of audio element
 * configurations into a human-readable ASCII table format suitable for
 * logging purposes.
 */
class AudioElementTableFormatter {
 public:
  /*!\brief Formats audio elements as a table string.
   *
   * \param audio_elements Vector of audio element configurations to format.
   * \return Formatted table as a string, or empty string on error.
   */
  static std::string FormatTable(
      std::vector<AudioElementConfig>& audio_elements);

 private:
  /*!\brief Formats a float value to two decimal places with padding.
   *
   * \param number Number to format.
   * \return Formatted and padded string (minimum width 7).
   */
  static std::string FormatFloat(float number);

  /*!\brief Pads a string with spaces to the left.
   *
   * \param str String to pad.
   * \param width Desired width of the padded string.
   * \return Padded string.
   */
  static std::string PadString(const std::string& str, size_t width);

  /*!\brief Renders a table row with element and channel data.
   *
   * \param element_data Data for the audio element columns.
   * \param channel_data Data for the channel columns.
   * \return Formatted row string.
   */
  static std::string RenderRow(const std::string& element_data,
                               const std::string& channel_data);

  /*!\brief Converts a boolean value to "Yes" or "No".
   *
   * \param value Boolean value to convert.
   * \return "Yes" if true, "No" if false.
   */
  static std::string FormatBooleanToYesNo(bool value);
};

}  // namespace obr

#endif  // OBR_RENDERER_AUDIO_ELEMENT_TABLE_FORMATTER_H_
