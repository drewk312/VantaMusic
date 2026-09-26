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
#ifndef OBR_AMBISONIC_ENCODER_AMBISONIC_ENCODER_H_
#define OBR_AMBISONIC_ENCODER_AMBISONIC_ENCODER_H_

#include <cstddef>
#include <vector>

#include "Eigen/Core"
#include "Eigen/Dense"
#include "absl/container/flat_hash_map.h"
#include "obr/ambisonic_encoder/associated_legendre_polynomials_generator.h"
#include "obr/audio_buffer/audio_buffer.h"

namespace obr {

class AmbisonicEncoder {
 public:
  /*!\brief Ambisonic Encoder constructor.
   *
   * \param number_of_input_channels Number of input channels (determines max
   *        number of sources to be processed).
   * \param ambisonic_order Ambisonic order (determines the number of output
   *        channels).
   */
  AmbisonicEncoder(size_t number_of_input_channels, size_t ambisonic_order);

  /*!\brief Default destructor. */
  ~AmbisonicEncoder() = default;

  /*!\brief Sets the parameters of a single source.
   *
   * \param input_channel Sets the input channel (0-indexed) associated with
   *        the source.
   * \param gain Sets the linear gain (0.5 = -6dB) applied to the source signal
   *        before encoding to Ambisonics. Independent of distance parameter.
   * \param azimuth Expressed in degrees (0 = front, 90 = left, 180 = back, -90
   *        = right).
   * \param elevation Expressed in degrees (0 = horizontal, 90 = up, -90 =
   *        down).
   * \param distance Normalized value in 0 - 1 range, where 0 is inside
   *        listeners head and 1 is the furthest possible distance.
   *        Will impact final gain, not time delay.
   */
  void SetSource(size_t input_channel, float gain, float azimuth,
                 float elevation, float distance);

  /*!\brief Removes a source from the list of sources.
   *
   * The associated input channels are thereby muted.
   *
   * \param input_channel Determines which source to remove. (0-indexed)
   */
  void RemoveSource(size_t input_channel);

  /*!\brief Processing callback for planar audio data (AudioBuffer).
   *
   * \param input_buffer Input buffer of samples.
   * \param output_buffer Output buffer of processed samples.
   */
  void ProcessPlanarAudioData(const AudioBuffer& input_buffer,
                              AudioBuffer* output_buffer);

 private:
  /*!\brief Parameters of a single source. */
  struct SingleSourceParams {
    float gain;
    float azimuth;
    float elevation;
    float distance;
  };

  /*!\brief Properties of a single source, including current and target
   *        parameters for ramping. */
  struct SourceProperties {
    // Parameters at the start of the processing block.
    SingleSourceParams current;
    // Target parameters (updated via SetSource()). A ramp will be applied from
    // current -> target across the audio block.
    SingleSourceParams target;
  };

  /*!\brief Calculates the spherical harmonic coefficients.
   *
   * Spherical harmonic coefficients are calculated for the given azimuth
   * and elevation. ACN/SN3D.
   *
   * \param azimuth Azimuth in degrees.
   * \param elevation Elevation in degrees.
   * \param ambisonic_order Ambisonic order.
   * \param coeffs Vector to store the calculated coefficients.
   */
  void GetShCoeffs(float azimuth, float elevation, size_t ambisonic_order,
                   std::vector<float>& coeffs);

  /*!\brief Applies distance-based damping to spherical harmonic coefficients to
   *        simulate inside-the-head source localisation.
   *
   * The damping is applied per-degree: coefficients for degree `d` are
   * multiplied by (distance / normalized_head_radius)^d.
   *
   * \param distance Normalized distance in 0 - 1 range, where 0 is the center
   *        of listener's head and 1 is the furthest possible distance.
   * \param normalized_head_radius A normalized radius in [0,1] inside which
   *        the damping is applied. Distances greater than this radius are
   *        considered "outside" the head and will not be damped. Distances
   *        inside the radius are remapped to [0,1] using distance /
   *        normalized_head_radius before computing degree-based damping.
   * \param coeffs Vector of spherical harmonic coefficients to apply the
   *        distance damping to.
   */
  void ApplyWithinHeadSHCoefficientsDamping(float distance,
                                            float normalized_head_radius,
                                            std::vector<float>& coeffs) const;

  const size_t number_of_input_channels_;
  const size_t number_of_output_channels_;
  const size_t ambisonic_order_;

  // Map of structs containing the properties of each source.
  absl::flat_hash_map<size_t, SourceProperties> sources_;

  AssociatedLegendrePolynomialsGenerator alp_generator_;
  Eigen::MatrixXf encoding_matrix_;
};

}  // namespace obr

#endif  // OBR_AMBISONIC_ENCODER_AMBISONIC_ENCODER_H_
