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

#ifndef OBR_RENDERER_OBR_IMPL_H_
#define OBR_RENDERER_OBR_IMPL_H_

#include <cstddef>
#include <memory>
#include <string>
#include <vector>

#include "absl/status/status.h"
#include "absl/synchronization/mutex.h"
#include "obr/ambisonic_binaural_decoder/fft_manager.h"
#include "obr/ambisonic_binaural_decoder/resampler.h"
#include "obr/audio_buffer/audio_buffer.h"
#include "obr/common/misc_math.h"
#include "obr/peak_limiter/peak_limiter.h"
#include "obr/renderer/audio_element_config.h"
#include "obr/renderer/audio_element_type.h"
#include "obr/renderer/processing_group.h"

namespace obr {

/*!\brief Implementation of the obr renderer.*/
class ObrImpl {
 public:
  /*!\brief Constructor.
   *
   * \param buffer_size_per_channel Buffer size per channel in samples.
   * \param sampling_rate Sampling rate in Hz.
   */
  ObrImpl(int buffer_size_per_channel, int sampling_rate);

  /*!\brief Destructor.
   */
  ~ObrImpl();

  /*!\brief Processes planar audio data.
   *
   * \param input_buffer Input buffer with planar audio data.
   * \param output_buffer Output buffer with planar audio data.
   */
  void Process(const AudioBuffer& input_buffer, AudioBuffer* output_buffer);

  /*!\brief Returns the buffer size per channel.
   *
   * \return Buffer size per channel.
   */
  int GetBufferSizePerChannel() const;

  /*!\brief Returns the sampling rate.
   *
   * \return Sampling rate.
   */
  int GetSamplingRate() const;

  /*!\brief Returns the number of input channels.
   *
   * \return Number of input channels.
   */
  size_t GetNumberOfInputChannels();

  /*!\brief Returns the number of output channels.
   *
   * \return Number of output channels.
   */
  size_t GetNumberOfOutputChannels();

  /*!\brief Returns the number of audio elements set within the renderer.
   *
   * \return Number of audio elements.
   */
  size_t GetNumberOfAudioElements();

  /*!
   * \brief Adds an audio element to the renderer with a specific binaural
   * filter type. Creates an instance of AudioElementConfig, conducts all
   * necessary checks, populates with config data and updates renderer's DSP.
   * Default filter type is Ambient for backward compatibility.
   *
   * \param type Type of the audio element.
   * \param filter_profile Type of binaural filter (Direct / Ambient /
   * Reverberant).
   * \return `absl::OkStatus()` if successful. A specific status on failure.
   */
  absl::Status AddAudioElement(
      AudioElementType type,
      BinauralFilterProfile filter_profile = BinauralFilterProfile::kAmbient);

  /*!\brief Removes the last added audio element from the renderer.
   *
   * \return `absl::OkStatus()` if successful. A specific status on failure.
   */
  absl::Status RemoveLastAudioElement();

  /*!\brief Sets the position of an audio object.
   * For multi-channel objects (e.g., kObjectDual), sets all channels to the
   * same position.
   *
   * \param audio_element_index Index of the audio element containing the
   * object.
   * \param azimuth Azimuth in degrees.
   * \param elevation Elevation in degrees.
   * \param distance Normalized distance in 0 to 1 range.
   * \return `absl::OkStatus()` if successful. A specific status on failure.
   */
  absl::Status UpdateObjectPosition(size_t audio_element_index, float azimuth,
                                    float elevation, float distance);

  /*!\brief Sets the position of a specific channel within an audio object.
   * Use this for multi-channel objects (e.g., kObjectDual) to set independent
   * positions for each channel.
   *
   * \param audio_element_index Index of the audio element containing the
   * object.
   * \param channel_index Index of the channel within the object (0-based).
   * \param azimuth Azimuth in degrees.
   * \param elevation Elevation in degrees.
   * \param distance Normalized distance in 0 to 1 range.
   * \return `absl::OkStatus()` if successful. A specific status on failure.
   */
  absl::Status UpdateObjectChannelPosition(size_t audio_element_index,
                                           size_t channel_index, float azimuth,
                                           float elevation, float distance);

  /*! \brief Enables or disables head tracking.
   *
   * \param enable_head_tracking True to enable head tracking, false to disable.
   */
  void EnableHeadTracking(bool enable_head_tracking);

  /*! \brief Sets the head-locked rendering mode for a specific audio element.
   *
   * When head-locked rendering is enabled for an element, that element will
   * not be rotated by head tracking, causing it to move with the listener's
   * head rather than remaining fixed in world space.
   *
   * This setting only takes effect when global head tracking is enabled via
   * EnableHeadTracking(true). If global head tracking is disabled, all elements
   * are effectively head-locked regardless of their individual settings.
   *
   * Default behavior: All elements are world-locked (head_locked = false),
   * meaning they are rotated by head tracking when it's enabled.
   *
   * Note: For passthrough elements (kPassthroughMono, kPassthroughStereo),
   * this setting has no effect, as they bypass binaural processing entirely.
   * Calling this function on passthrough elements will log a warning and
   * return success as a no-op.
   *
   * \param audio_element_index Index of the audio element (0-based).
   * \param head_locked True to enable head-locked rendering (no rotation),
   *                    false to enable world-locked rendering (rotated).
   * \return `absl::OkStatus()` if successful.
   *         `absl::InvalidArgumentError` if audio_element_index is out of
   * bounds.
   */
  absl::Status SetElementHeadLocked(size_t audio_element_index,
                                    bool head_locked);

  /*! \brief Enables or disables the output peak limiter.
   *
   * \param enable_limiter True to enable the limiter (default), false to
   * disable.
   */
  void EnableLimiter(bool enable_limiter);

  /*!\brief Sets the head rotation.
   * The head rotation expressed using quaternions is used to counter-rotate the
   * intermediate Ambisonic bed in order to produce stable sound sources in
   * binaural reproduction.
   * Use the ADM object coordinate system:
   * X - right (positive right, negative left)
   * Y - front (positive front, negative back)
   * Z - up (positive up, negative down)
   *
   * @param w
   * @param x
   * @param y
   * @param z
   * @return `absl::OkStatus()` if successful. A specific status on failure.
   */
  absl::Status SetHeadRotation(float w, float x, float y, float z);

  /*!\brief Returns a log message with the list of audio elements in a form of
   * an ASCII table.
   *
   * \return Log message with the list of audio elements.
   */
  std::string GetAudioElementConfigLogMessage();

 private:
  /*!\brief Resets the renderer's DSP to the initial state.
   *
   * \return `absl::OkStatus()` if successful. A specific status on failure.
   */
  absl::Status ResetDsp();

  /*!\brief Initializes the renderer's DSP.
   * First, it resets the DSP, then analyzes the list of requested audio
   * elements and initializes the necessary DSP resources.
   *
   * \return `absl::OkStatus()` if successful. A specific status on failure.
   */
  absl::Status InitializeDsp();

  /*!\brief Creates processing groups based on audio elements.
   * Groups audio elements by their Ambisonic order and binaural filter profile.
   */
  void CreateProcessingGroups();

  /*!\brief Updates all Ambisonic encoders across all processing groups.
   *
   * \return `absl::OkStatus()` if successful. A specific status on failure.
   */
  absl::Status UpdateAllAmbisonicEncoders();

  /*!\brief Internal helper to get number of input channels without locking.
   * Must be called while holding mutex_.
   *
   * \return Number of input channels.
   */
  size_t GetNumberOfInputChannelsLocked() const;

  const int buffer_size_per_channel_;
  const int sampling_rate_;

  bool head_tracking_enabled_;
  bool limiter_enabled_;
  WorldRotation world_rotation_;

  // Mutex to protect data accessed in different threads.
  mutable absl::Mutex mutex_;

  std::vector<AudioElementConfig> audio_elements_;

  // Processing groups - each group has its own binaural decoder.
  std::vector<ProcessingGroup> processing_groups_;

  // Resampler and FFT manager (shared across all processing groups).
  // Note: even though the resampler is stateful, the state is reset before
  // each use, so it is safe to be shared across processing groups.
  Resampler resampler_;
  FftManager fft_manager_;

  // TODO(b/468218887): Provide an option to disable peak limiting.
  // Peak limiter (applied to final output).
  std::unique_ptr<PeakLimiter> peak_limiter_;
};

}  // namespace obr

#endif  // OBR_RENDERER_OBR_IMPL_H_
