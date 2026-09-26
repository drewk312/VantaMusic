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

#ifndef OBR_AUDIO_ELEMENT_CONFIG_H_
#define OBR_AUDIO_ELEMENT_CONFIG_H_

#include <cstddef>
#include <string>
#include <vector>

#include "absl/container/btree_map.h"
#include "absl/status/status.h"
#include "absl/status/statusor.h"
#include "absl/strings/string_view.h"
#include "obr/renderer/audio_element_type.h"
#include "obr/renderer/input_channel_config.h"

namespace obr {

/*!\brief Enumeration for selecting the binaural filter profile used to render
 * an Audio Element.
 */
enum class BinauralFilterProfile {
  kDirect = 0,
  kAmbient = 1,
  kReverberant = 2,
};

/*!\brief Configuration of an audio element. */
class AudioElementConfig {
 public:
  explicit AudioElementConfig(
      AudioElementType type,
      BinauralFilterProfile filter_profile = BinauralFilterProfile::kAmbient);

  ~AudioElementConfig() = default;

  /*!\brief Returns the type of the audio element.
   *
   * \return Type of the audio element.
   */
  AudioElementType GetType() const;

  /*!\brief Returns the string representation of the audio element type.
   *
   * \return String representation of the audio element type.
   */
  absl::StatusOr<absl::string_view> GetTypeStr();

  /*!\brief Sets the first input channel index and updates the channel indices
   * the remaining input channels.
   *
   */
  void SetFirstChannelIndex(size_t first_channel);

  /*!\brief Returns the first input channel index.
   *
   * \return First input channel index.
   */
  size_t GetFirstChannelIndex() const;

  /*!\brief Returns the number of input channels.
   *
   * \return Number of input channels.
   */
  size_t GetNumberOfInputChannels() const;

  /*!\brief Returns the ambisonic input channels.
   *
   * \return Ambisonic channels.
   */
  std::vector<AmbisonicSceneInputChannel>& GetAmbisonicChannels();

  /*!\brief Returns the loudspeaker input channels.
   *
   * \return Loudspeaker channels.
   */
  std::vector<LoudspeakerLayoutInputChannel>& GetLoudspeakerChannels();

  /*!\brief Returns the object input channels.
   *
   * \return Object channels.
   */
  std::vector<AudioObjectInputChannel>& GetObjectChannels();

  /*!\brief Returns the passthrough input channels.
   *
   * \return Passthrough channels.
   */
  std::vector<PassthroughInputChannel>& GetPassthroughChannels();

  /*!\brief Returns the Ambisonic order of the binaural filters to be used to
   * render this Audio Element.
   *
   * \return Ambisonic order of the binaural filters.
   */
  int GetBinauralFiltersAmbisonicOrder() const;

  /*!\brief Returns the binaural filter profile to be used to render this Audio
   * Element.
   *
   * \return Binaural filter profile.
   */
  BinauralFilterProfile GetBinauralFilterProfile() const {
    return binaural_filter_profile_;
  }

  /*!\brief Returns the string representation of the binaural filter profile.
   *
   * \return String representation of the binaural filter profile.
   */
  absl::StatusOr<absl::string_view> GetBinauralFilterProfileStr();

  /*!\brief Returns whether this element uses head-locked rendering.
   *
   * \return True if head-locked (not rotated), false if world-locked (rotated).
   */
  bool IsHeadLocked() const { return head_locked_; }

  /*!\brief Sets the head-locked rendering mode for this element.
   *
   * \param head_locked True for head-locked, false for world-locked.
   */
  void SetHeadLocked(bool head_locked) { head_locked_ = head_locked; }

 private:
  AudioElementType type_;

  size_t first_channel_index_, number_of_input_channels_;

  std::vector<AmbisonicSceneInputChannel> ambisonic_channels_;
  std::vector<LoudspeakerLayoutInputChannel> loudspeaker_channels_;
  std::vector<AudioObjectInputChannel> object_channels_;
  std::vector<PassthroughInputChannel> passthrough_channels_;

  int binaural_filters_ambisonic_order_;
  BinauralFilterProfile binaural_filter_profile_;
  bool head_locked_;  // If true, element is not rotated (head-locked rendering)
};

/*!\brief Returns the string representation of the given binaural filter profile
 * enum value.
 *
 * \param profile Binaural filter profile enum value.
 * \return String representation of the binaural filter profile enum value.
 */
inline const absl::btree_map<BinauralFilterProfile, absl::string_view>&
GetBinauralFilterProfileStringMap() {
  static const absl::btree_map<BinauralFilterProfile, absl::string_view> kMap =
      {
          {BinauralFilterProfile::kDirect, "Direct"},
          {BinauralFilterProfile::kAmbient, "Ambient"},
          {BinauralFilterProfile::kReverberant, "Reverberant"},
      };
  return kMap;
}

/*!\brief Returns the list of available binaural filter profiles as strings.
 *
 * \return List of available binaural filter profiles as strings.
 */
inline std::vector<std::string> GetAvailableBinauralFilterProfilesAsStr() {
  std::vector<std::string> profiles;
  for (const auto& [profile, name] : GetBinauralFilterProfileStringMap()) {
    profiles.emplace_back(name.data(), name.size());
  }
  return profiles;
}

/*!\brief Returns the binaural filter profile enum value corresponding to the
 * given string.
 *
 * \param str String representation of the binaural filter profile.
 *
 * \return Binaural filter profile enum value.
 */
inline absl::StatusOr<BinauralFilterProfile> GetBinauralFilterProfileFromStr(
    absl::string_view profile_string) {
  for (const auto& [profile, name] : GetBinauralFilterProfileStringMap()) {
    if (name == profile_string) {
      return profile;
    }
  }
  return absl::NotFoundError("Unknown BinauralFilterProfile string: " +
                             std::string(profile_string));
}

/*!\brief Converts a binaural filter profile enum to its string representation.
 *
 * \param profile Binaural filter profile enum value.
 * \return String representation of the profile, or an `absl::NotFoundError`
 *         status if not found.
 */
inline absl::StatusOr<std::string> BinauralFilterProfileToString(
    BinauralFilterProfile profile) {
  const auto& map = GetBinauralFilterProfileStringMap();
  auto it = map.find(profile);
  if (it != map.end()) {
    return std::string(it->second);
  }

  return absl::NotFoundError("Unknown BinauralFilterProfile string: " +
                             std::to_string(static_cast<int>(profile)));
}

}  // namespace obr

#endif  // OBR_AUDIO_ELEMENT_CONFIG_H_
