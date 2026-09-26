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

#ifndef OBR_LOUDSPEAKER_LAYOUTS_H_
#define OBR_LOUDSPEAKER_LAYOUTS_H_

#include <vector>

#include "absl/container/flat_hash_map.h"
#include "absl/log/absl_log.h"
#include "obr/renderer/audio_element_type.h"
#include "obr/renderer/input_channel_config.h"

namespace obr {

/*!\brief Defines loudspeaker layouts which can be rendered using
 * obr.
 */
class LoudspeakerLayouts {
 public:
  LoudspeakerLayouts() = default;

  /*!\brief Returns the loudspeaker layout for a given layout name.
   *
   * \param name Name of the layout.
   * \return Vector of LoudspeakerLayoutInputChannel.
   */
  std::vector<LoudspeakerLayoutInputChannel> getLoudspeakerLayout(
      AudioElementType type) const {
    // Check if the layout is defined.
    auto it = layout_map.find(type);
    if (it == layout_map.end()) {
      ABSL_LOG(ERROR) << "Unknown loudspeaker layout: \""
                      << GetAudioElementTypeStr(type) << "\".";
      return {};
    }

    std::vector<LoudspeakerLayoutInputChannel> loudspeaker_layout;

    for (auto loudspeaker : layout_map.at(it->first)) {
      loudspeaker_layout.push_back(loudspeaker_map.at(loudspeaker));
    }

    return loudspeaker_layout;
  }

 private:
  /*!\brief Defines labels for virtual loudspeakers used. */
  enum VirtualLoudspeaker {
    kC,
    kL30,
    kR30,
    kL45,
    kR45,
    kL60,
    kR60,
    kL90,
    kR90,
    kL110,
    kR110,
    kL135,
    kR135,
    k180,
    kUC,
    kUL30,
    kUR30,
    kUL45,
    kUR45,
    kUL90,
    kUR90,
    kUL135,
    kUR135,
    kU180,
    kBC,
    kBL45,
    kBR45,
    kBL135,
    kBR135,
    kT,
    kLFE,
    kLFE1,
    kLFE2,
  };

  /*!\brief Loudspeaker label - loudspeaker source configuration mapping. */
  const absl::flat_hash_map<VirtualLoudspeaker, LoudspeakerLayoutInputChannel>
      loudspeaker_map = {
          {kC, LoudspeakerLayoutInputChannel("kC", 0.0f, 0.0f, 1.0f, false)},
          {kL30,
           LoudspeakerLayoutInputChannel("kL30", 30.0f, 0.0f, 1.0f, false)},
          {kR30,
           LoudspeakerLayoutInputChannel("kR30", -30.0f, 0.0f, 1.0f, false)},
          {kL45,
           LoudspeakerLayoutInputChannel("kL45", 45.0f, 0.0f, 1.0f, false)},
          {kR45,
           LoudspeakerLayoutInputChannel("kR45", -45.0f, 0.0f, 1.0f, false)},
          {kL60,
           LoudspeakerLayoutInputChannel("kL60", 60.0f, 0.0f, 1.0f, false)},
          {kR60,
           LoudspeakerLayoutInputChannel("kR60", -60.0f, 0.0f, 1.0f, false)},
          {kL90,
           LoudspeakerLayoutInputChannel("kL90", 90.0f, 0.0f, 1.0f, false)},
          {kR90,
           LoudspeakerLayoutInputChannel("kR90", -90.0f, 0.0f, 1.0f, false)},
          {kL110,
           LoudspeakerLayoutInputChannel("kL110", 110.0f, 0.0f, 1.0f, false)},
          {kR110,
           LoudspeakerLayoutInputChannel("kR110", -110.0f, 0.0f, 1.0f, false)},
          {kL135,
           LoudspeakerLayoutInputChannel("kL135", 135.0f, 0.0f, 1.0f, false)},
          {kR135,
           LoudspeakerLayoutInputChannel("kR135", -135.0f, 0.0f, 1.0f, false)},
          {k180,
           LoudspeakerLayoutInputChannel("k180", 180.0f, 0.0f, 1.0f, false)},
          {kUC, LoudspeakerLayoutInputChannel("kUC", 0.0f, 45.0f, 1.0f, false)},
          {kUL30,
           LoudspeakerLayoutInputChannel("kUL30", 30.0f, 45.0f, 1.0f, false)},
          {kUR30,
           LoudspeakerLayoutInputChannel("kUR30", -30.0f, 45.0f, 1.0f, false)},
          {kUL45,
           LoudspeakerLayoutInputChannel("kUL45", 45.0f, 45.0f, 1.0f, false)},
          {kUR45,
           LoudspeakerLayoutInputChannel("kUR45", -45.0f, 45.0f, 1.0f, false)},
          {kUL90,
           LoudspeakerLayoutInputChannel("kUL90", 90.0f, 45.0f, 1.0f, false)},
          {kUR90,
           LoudspeakerLayoutInputChannel("kUR90", -90.0f, 45.0f, 1.0f, false)},
          {kUL135,
           LoudspeakerLayoutInputChannel("kUL135", 135.0f, 45.0f, 1.0f, false)},
          {kUR135, LoudspeakerLayoutInputChannel("kUR135", -135.0f, 45.0f, 1.0f,
                                                 false)},
          {kU180,
           LoudspeakerLayoutInputChannel("kU180", 180.0f, 45.0f, 1.0f, false)},
          {kBC,
           LoudspeakerLayoutInputChannel("kBC", 0.0f, -30.0f, 1.0f, false)},
          {kBL45,
           LoudspeakerLayoutInputChannel("kBL45", 45.0f, -30.0f, 1.0f, false)},
          {kBR45,
           LoudspeakerLayoutInputChannel("kBR45", -45.0f, -30.0f, 1.0f, false)},
          {kBL135, LoudspeakerLayoutInputChannel("kBL135", 135.0f, -30.0f, 1.0f,
                                                 false)},
          {kBR135, LoudspeakerLayoutInputChannel("kBR135", -135.0f, -30.0f,
                                                 1.0f, false)},
          {kT, LoudspeakerLayoutInputChannel("kT", 0.0f, 90.0f, 1.0f, false)},
          {kLFE,
           LoudspeakerLayoutInputChannel("kLFE", 0.0f, -30.0f, 1.0f, true)},
          {kLFE1,
           LoudspeakerLayoutInputChannel("kLFE1", 45.0f, -30.0f, 1.0f, true)},
          {kLFE2,
           LoudspeakerLayoutInputChannel("kLFE2", -45.0f, -30.0f, 1.0f, true)},
  };

  /*!\brief Loudspeaker layout - loudspeaker label mapping. */
  const absl::flat_hash_map<AudioElementType, std::vector<VirtualLoudspeaker>>
      layout_map = {
          // Mono: Single center loudspeaker.
          {AudioElementType::kLayoutMono, {kC}},
          // Stereo: Standard stereo layout with left and right loudspeakers at
          // ±30° azimuth.
          {AudioElementType::kLayoutStereo, {kL30, kR30}},
          // IAMF 5.1: IAMF 5.1 Layout
          {AudioElementType::kLayout5_1_0_ch,
           {kL30, kR30, kC, kLFE, kL110, kR110}},
          // IAMF 5.1.2: IAMF 5.1.2 Layout
          {AudioElementType::kLayout5_1_2_ch,
           {kL30, kR30, kC, kLFE, kL110, kR110, kUL30, kUR30}},
          // IAMF 5.1.4: IAMF 5.1.4 Layout
          {AudioElementType::kLayout5_1_4_ch,
           {kL30, kR30, kC, kLFE, kL110, kR110, kUL45, kUR45, kUL135, kUR135}},
          // IAMF 7.1: IAMF 7.1 Layout
          {AudioElementType::kLayout7_1_0_ch,
           {kL30, kR30, kC, kLFE, kL90, kR90, kL135, kR135}},
          // IAMF 7.1.2: IAMF 7.1.2 Layout
          {AudioElementType::kLayout7_1_2_ch,
           {kL30, kR30, kC, kLFE, kL90, kR90, kL135, kR135, kUL45, kUR45}},
          // IAMF 7.1.4: IAMF 7.1.4 Layout
          {AudioElementType::kLayout7_1_4_ch,
           {kL30, kR30, kC, kLFE, kL90, kR90, kL135, kR135, kUL45, kUR45,
            kUL135, kUR135}},
          // IAMF 3.1.2: IAMF 3.1.2 Layout
          {AudioElementType::kLayout3_1_2_ch,
           {kL30, kR30, kC, kLFE, kUL45, kUR45}},
          // IAMF 9.1.6: IAMF 9.1.6 Layout
          {AudioElementType::kLayout9_1_6_ch,
           {kL60, kR60, kC, kLFE1, kL135, kR135, kL30, kR30, kL90, kR90, kUL45,
            kUR45, kUL135, kUR135, kUL90, kUR90}},
          // IAMF 9.1.6 Alt: IAMF 9.1.6 Alternative Ordering (Sequential in
          // Matrix) + LFE instead of LFE1
          {AudioElementType::kLayout9_1_6_ch_alt,
           {kL30, kR30, kC, kLFE, kL60, kR60, kL90, kR90, kL135, kR135, kUL45,
            kUR45, kUL90, kUR90, kUL135, kUR135}},
          // IAMF 7.1.5.4: IAMF 7.1.5.4 Layout
          {AudioElementType::kLayout7_1_5_4_ch,
           {kL30, kR30, kC, kLFE, kL90, kR90, kL135, kR135, kUL45, kUR45, kT,
            kUL135, kUR135, kBL45, kBR45, kBL135, kBR135}},
          // IAMF 10.2.9.3: IAMF 10.2.9.3 Layout
          {AudioElementType::kLayout10_2_9_3_ch,
           {kL60,   kR60,   kC,    kLFE1, kL135, kR135, kL30,  kR30,
            k180,   kLFE2,  kL90,  kR90,  kUL45, kUR45, kUC,   kT,
            kUL135, kUR135, kUL90, kUR90, kU180, kBC,   kBL45, kBR45}},
          // IAMF LFE: IAMF LFE Subset
          {AudioElementType::kSubsetLFE, {kLFE}},
          // IAMF Stereo-S: IAMF Stereo-S Subset
          {AudioElementType::kSubsetStereo_S, {kL110, kR110}},
          // IAMF Stereo-SS: IAMF Stereo-SS Subset
          {AudioElementType::kSubsetStereo_SS, {kL90, kR90}},
          // IAMF Stereo-RS: IAMF Stereo-RS Subset
          {AudioElementType::kSubsetStereo_RS, {kL135, kR135}},
          // IAMF Stereo-TF: IAMF Stereo-TF Subset
          {AudioElementType::kSubsetStereo_TF, {kUL45, kUR45}},
          // IAMF Stereo-TB: IAMF Stereo-TB Subset
          {AudioElementType::kSubsetStereo_TB, {kUL135, kUR135}},
          // IAMF Top-4ch: IAMF Top-4ch Subset
          {AudioElementType::kSubsetTop_4ch, {kUL45, kUR45, kUL135, kUR135}},
          // IAMF 3.0ch: IAMF 3.0ch Subset
          {AudioElementType::kSubset3_0ch, {kL30, kR30, kC}},
          // IAMF Stereo-F: IAMF Stereo-F Subset
          {AudioElementType::kSubsetStereo_F, {kL60, kR60}},
          // IAMF Stereo-Si: IAMF Stereo-Si Subset
          {AudioElementType::kSubsetStereo_Si, {kL90, kR90}},
          // IAMF Stereo-TpSi: IAMF Stereo-TpSi Subset
          {AudioElementType::kSubsetStereo_TpSi, {kUL90, kUR90}},
          // IAMF Top-6ch: IAMF Top-6ch Subset
          {AudioElementType::kSubsetTop_6ch,
           {kUL45, kUR45, kUL135, kUR135, kUL90, kUR90}},
          // IAMF LFE-Pair: IAMF LFE-Pair Subset
          {AudioElementType::kSubsetLFE_Pair, {kLFE1, kLFE2}},
          // IAMF Bottom-3ch: IAMF Bottom-3ch Subset
          {AudioElementType::kSubsetBottom_3ch, {kBC, kBL45, kBR45}},
          // IAMF Bottom-4ch: IAMF Bottom-4ch Subset
          {AudioElementType::kSubsetBottom_4ch, {kBL45, kBR45, kBL135, kBR135}},
          // IAMF Top-1ch: IAMF Top-1ch Subset
          {AudioElementType::kSubsetTop_1ch, {kT}},
          // IAMF Top-5ch: IAMF Top-5ch Subset
          {AudioElementType::kSubsetTop_5ch,
           {kUL45, kUR45, kT, kUL135, kUR135}},
  };
};

};  // namespace obr

#endif  // OBR_LOUDSPEAKER_LAYOUTS_H_
