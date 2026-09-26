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

#ifndef OBR_RENDERER_PROCESSING_GROUP_H_
#define OBR_RENDERER_PROCESSING_GROUP_H_

#include <cstddef>
#include <memory>
#include <vector>

#include "absl/status/status.h"
#include "obr/ambisonic_binaural_decoder/ambisonic_binaural_decoder.h"
#include "obr/ambisonic_binaural_decoder/fft_manager.h"
#include "obr/ambisonic_binaural_decoder/resampler.h"
#include "obr/ambisonic_encoder/ambisonic_encoder.h"
#include "obr/ambisonic_rotator/ambisonic_rotator.h"
#include "obr/audio_buffer/audio_buffer.h"
#include "obr/common/misc_math.h"
#include "obr/renderer/audio_element_config.h"

namespace obr {

/*!\brief Key for identifying unique processing groups.
 *
 * Audio elements with the same Ambisonic order and binaural filter profile
 * can share the same binaural decoder, and thus belong to the same processing
 * group.
 */
struct ProcessingGroupKey {
  int ambisonic_order;
  BinauralFilterProfile filter_profile;

  bool operator==(const ProcessingGroupKey& other) const {
    return ambisonic_order == other.ambisonic_order &&
           filter_profile == other.filter_profile;
  }

  bool operator<(const ProcessingGroupKey& other) const {
    if (ambisonic_order != other.ambisonic_order) {
      return ambisonic_order < other.ambisonic_order;
    }
    return static_cast<int>(filter_profile) <
           static_cast<int>(other.filter_profile);
  }
};

/*!\brief A processing group contains DSP resources and processing logic for
 * audio elements that share the same Ambisonic order and binaural filter
 * profile.
 *
 * This class encapsulates all signal processing operations for a group of
 * audio elements, making it suitable for parallel processing and improving
 * modularity.
 */
class ProcessingGroup {
 public:
  /*!\brief Constructor.
   *
   * \param key Processing group key (ambisonic order + filter profile).
   * \param audio_element_indices Indices of audio elements in this group.
   * \param buffer_size_per_channel Buffer size per channel in samples.
   * \param sampling_rate Sampling rate in Hz.
   */
  ProcessingGroup(const ProcessingGroupKey& key,
                  const std::vector<size_t>& audio_element_indices,
                  int buffer_size_per_channel, int sampling_rate);

  /*!\brief Destructor.
   */
  ~ProcessingGroup();

  // Move-only type (contains unique_ptrs).
  ProcessingGroup(ProcessingGroup&&) = default;
  ProcessingGroup& operator=(ProcessingGroup&&) = default;
  ProcessingGroup(const ProcessingGroup&) = delete;
  ProcessingGroup& operator=(const ProcessingGroup&) = delete;

  /*!\brief Initialize DSP resources for this processing group.
   *
   * \param fft_manager Shared FFT manager.
   * \param resampler Shared resampler.
   * \return `absl::OkStatus()` if successful. A specific status on failure.
   */
  absl::Status Initialize(FftManager* fft_manager, Resampler* resampler);

  /*!\brief Process audio for this processing group.
   *
   * This method encapsulates all signal processing steps:
   * 1. Encode loudspeaker/object channels to Ambisonics
   * 2. Mix Ambisonic input channels into the mix bed
   * 3. Apply head tracking rotation (if enabled)
   * 4. Decode to binaural output
   *
   * \param input_buffer Input audio buffer (all channels).
   * \param audio_elements Reference to all audio elements.
   * \param head_tracking_enabled Whether head tracking is enabled.
   * \param world_rotation Head rotation for counter-rotation.
   * \param output_buffer Output binaural buffer for this group.
   */
  void Process(const AudioBuffer& input_buffer,
               const std::vector<AudioElementConfig>& audio_elements,
               bool head_tracking_enabled, const WorldRotation& world_rotation,
               AudioBuffer* output_buffer);

  /*!\brief Update Ambisonic encoder source positions.
   *
   * \param audio_elements Reference to all audio elements.
   * \return `absl::OkStatus()` if successful. A specific status on failure.
   */
  absl::Status UpdateAmbisonicEncoder(
      std::vector<AudioElementConfig>& audio_elements);

  /*!\brief Get the processing group key.
   *
   * \return Processing group key.
   */
  const ProcessingGroupKey& GetKey() const { return key_; }

  /*!\brief Get the audio element indices in this group.
   *
   * \return Vector of audio element indices.
   */
  const std::vector<size_t>& GetAudioElementIndices() const {
    return audio_element_indices_;
  }

  /*!\brief Get the Ambisonic order for this group.
   *
   * \return Ambisonic order.
   */
  int GetAmbisonicOrder() const { return key_.ambisonic_order; }

  /*!\brief Get the binaural filter profile for this group.
   *
   * \return Binaural filter profile.
   */
  BinauralFilterProfile GetFilterProfile() const { return key_.filter_profile; }

 private:
  /*!\brief Get source channel indices for the Ambisonic encoder.
   *
   * \param audio_elements Reference to all audio elements.
   * \return Vector of input channel indices for all sources.
   */
  std::vector<size_t> GetAmbisonicEncoderSourceChannelIndices(
      const std::vector<AudioElementConfig>& audio_elements) const;

  /*!\brief Get source channel indices for the Ambisonic encoder, filtered by
   * head-locked status.
   *
   * \param audio_elements Reference to all audio elements.
   * \param filter_head_locked If true, return indices of head-locked elements;
   *                           if false, return indices of world-locked
   * elements.
   * \return Vector of input channel indices matching the filter.
   */
  std::vector<size_t> GetAmbisonicEncoderSourceChannelIndices(
      const std::vector<AudioElementConfig>& audio_elements,
      bool filter_head_locked) const;

  ProcessingGroupKey key_;

  // Indices of audio elements in the parent's audio_elements_ vector.
  std::vector<size_t> audio_element_indices_;

  // Configuration.
  int buffer_size_per_channel_;
  int sampling_rate_;

  // Ambisonic mix bed for world-locked elements (rotated by head tracking).
  AudioBuffer ambisonic_mix_bed_;

  // Ambisonic mix bed for head-locked elements (never rotated).
  AudioBuffer ambisonic_mix_bed_head_locked_;

  // Ambisonic encoder for loudspeaker layout and object types.
  std::unique_ptr<AmbisonicEncoder> ambisonic_encoder_;
  AudioBuffer ambisonic_encoder_input_buffer_;

  // Ambisonic rotator for this group.
  std::unique_ptr<AmbisonicRotator> ambisonic_rotator_;

  // Binaural decoder for this group.
  std::unique_ptr<AudioBuffer> sh_hrirs_L_;
  std::unique_ptr<AudioBuffer> sh_hrirs_R_;
  std::unique_ptr<AmbisonicBinauralDecoder> ambisonic_binaural_decoder_;
};

}  // namespace obr

#endif  // OBR_RENDERER_PROCESSING_GROUP_H_
