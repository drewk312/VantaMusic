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
#include "obr/ambisonic_encoder/ambisonic_encoder.h"

#include <algorithm>
#include <cmath>
#include <cstddef>
#include <cstdlib>
#include <vector>

#include "Eigen/Core"
#include "absl/log/absl_check.h"
#include "obr/audio_buffer/audio_buffer.h"
#include "obr/common/ambisonic_utils.h"
#include "obr/common/constants.h"

namespace obr {

AmbisonicEncoder::AmbisonicEncoder(size_t number_of_input_channels,
                                   size_t ambisonic_order)
    : number_of_input_channels_(number_of_input_channels),
      number_of_output_channels_(GetNumPeriphonicComponents(ambisonic_order)),
      ambisonic_order_(ambisonic_order),
      alp_generator_(static_cast<int>(ambisonic_order), false, false) {
  ABSL_CHECK_GT(number_of_input_channels_, 0);
  ABSL_CHECK_GT(ambisonic_order_, 0);

  // Initialize the encoding matrix.
  encoding_matrix_ =
      Eigen::MatrixXf::Zero(static_cast<int>(number_of_output_channels_),
                            static_cast<int>(number_of_input_channels_));
}

void AmbisonicEncoder::SetSource(size_t input_channel, float gain,
                                 float azimuth, float elevation,
                                 float distance) {
  ABSL_CHECK_NE(number_of_input_channels_, 0);
  ABSL_CHECK_NE(number_of_output_channels_, 0);
  ABSL_CHECK_LT(input_channel, number_of_input_channels_);

  // If the source does not yet exist, initialize both current and target to
  // the provided values so the first block is not ramped.
  if (sources_.find(input_channel) == sources_.end()) {
    SourceProperties source_properties{};
    source_properties.current = {gain, azimuth, elevation, distance};
    source_properties.target = source_properties.current;
    sources_.insert({input_channel, source_properties});
    // If gain indicates silence, mute encoding matrix column.
    if (gain < kNegative120dbInAmplitude) {
      encoding_matrix_.col(static_cast<int>(input_channel)).setZero();
    }
    return;
  }

  // If the values are unchanged, do nothing.
  SourceProperties& source_properties = sources_.at(input_channel);
  if (source_properties.target.gain == gain &&
      source_properties.target.azimuth == azimuth &&
      source_properties.target.elevation == elevation &&
      source_properties.target.distance == distance) {
    return;
  }

  // Update only the target parameters. A ramp from current -> target will be
  // applied during `ProcessPlanarAudioData`.
  source_properties.target.gain = gain;
  source_properties.target.azimuth = azimuth;
  source_properties.target.elevation = elevation;
  source_properties.target.distance = distance;

  // If target gain indicates silence, mute the static encoding matrix column
  // as a quick early-out for any code that may still use it.
  if (gain < kNegative120dbInAmplitude) {
    encoding_matrix_.col(static_cast<int>(input_channel)).setZero();
  }
}

void AmbisonicEncoder::RemoveSource(size_t input_channel) {
  // Remove the source from the map.
  sources_.erase(input_channel);

  // Mute the input channel in the encoding matrix.
  encoding_matrix_.col(static_cast<int>(input_channel)).setZero();
}

void AmbisonicEncoder::ProcessPlanarAudioData(const AudioBuffer& input_buffer,
                                              AudioBuffer* output_buffer) {
  // Perform checks.
  ABSL_CHECK_NE(output_buffer, nullptr);
  ABSL_CHECK_EQ(number_of_input_channels_, input_buffer.num_channels());
  ABSL_CHECK_EQ(number_of_output_channels_, output_buffer->num_channels());
  ABSL_CHECK_EQ(input_buffer.num_frames(), output_buffer->num_frames());

  const size_t num_frames = input_buffer.num_frames();

  // Create Eigen maps for the input and output buffers.
  const auto in_rows = static_cast<Eigen::Index>(number_of_input_channels_);
  const auto out_rows = static_cast<Eigen::Index>(number_of_output_channels_);
  const auto in_stride =
      static_cast<Eigen::Index>(input_buffer.GetChannelStride());
  const auto out_stride =
      static_cast<Eigen::Index>(output_buffer->GetChannelStride());

  Eigen::Map<const Eigen::Matrix<float, Eigen::Dynamic, Eigen::Dynamic,
                                 Eigen::RowMajor>,
             Eigen::Aligned>
      unencoded_buffer(&input_buffer[0][0], in_rows, in_stride);

  Eigen::Map<
      Eigen::Matrix<float, Eigen::Dynamic, Eigen::Dynamic, Eigen::RowMajor>,
      Eigen::Aligned>
      encoded_buffer(&(*output_buffer)[0][0], out_rows, out_stride);

  // Compute a per-frame encoding matrix (output x input) by linearly
  // interpolating each source's parameters across the block. This avoids
  // clicks when object positions change quickly.
  Eigen::MatrixXf last_encoding =
      Eigen::MatrixXf::Zero(static_cast<int>(number_of_output_channels_),
                            static_cast<int>(number_of_input_channels_));

  // Precompute start and target encoding columns for each input channel.
  std::vector<Eigen::VectorXf> column_start(number_of_input_channels_);
  std::vector<Eigen::VectorXf> column_end(number_of_input_channels_);

  // Track which input channels actually need per-frame interpolation.
  std::vector<bool> needs_interpolation(number_of_input_channels_, false);

  // Helper lambda to compare two sets of source parameters for equality.
  auto params_equal = [&](const SingleSourceParams& a,
                          const SingleSourceParams& b) {
    const float kGainEps = 1e-6f;
    const float kDistEps = 1e-6f;
    const float kAngleEpsDeg = 1e-3f;  // small angular tolerance

    if (std::fabs(a.gain - b.gain) > kGainEps) {
      return false;
    }
    if (std::fabs(a.distance - b.distance) > kDistEps) {
      return false;
    }
    if (std::fabs(a.elevation - b.elevation) > kAngleEpsDeg) {
      return false;
    }
    // Azimuth: compare with wrap-around using minimal angular difference.
    float delta = std::fmod(b.azimuth - a.azimuth + 540.0f, 360.0f) - 180.0f;
    if (std::fabs(delta) > kAngleEpsDeg) {
      return false;
    }
    return true;
  };

  // For each input channel, compute the encoding matrix column at the start.
  for (size_t in_ch = 0; in_ch < number_of_input_channels_; ++in_ch) {
    column_start[in_ch] =
        Eigen::VectorXf::Zero(static_cast<int>(number_of_output_channels_));
    column_end[in_ch] =
        Eigen::VectorXf::Zero(static_cast<int>(number_of_output_channels_));

    auto it = sources_.find(in_ch);
    if (it == sources_.end()) {
      continue;
    }

    const SourceProperties& source_properties = it->second;

    // Helper lambda to fill a column vector from source params.
    auto fill_column = [&](const SingleSourceParams& p, Eigen::VectorXf& col) {
      if (p.gain < kNegative120dbInAmplitude) {
        col.setZero();
        return;
      }

      // Compute spherical harmonic coefficients for this direction.
      std::vector<float> sh_coeffs(number_of_output_channels_);
      GetShCoeffs(p.azimuth, p.elevation, ambisonic_order_, sh_coeffs);

      // Apply SH damping per-degree if needed. Use normalized_head_radius
      // = 0.1f (i.e. damping applies for distance < 0.1).
      ApplyWithinHeadSHCoefficientsDamping(p.distance, 0.1f, sh_coeffs);

      // Scale by gain and write into the Eigen vector.
      for (size_t out_ch = 0; out_ch < number_of_output_channels_; ++out_ch) {
        col(static_cast<int>(out_ch)) = sh_coeffs.at(out_ch) * p.gain;
      }
    };

    // Always compute the target column.
    fill_column(source_properties.target, column_end[in_ch]);

    // Decide whether this source needs interpolation across frames.
    if (!params_equal(source_properties.current, source_properties.target)) {
      // Only compute the start/current column if parameters differ.
      fill_column(source_properties.current, column_start[in_ch]);
      needs_interpolation[in_ch] = true;
    } else {
      // Parameters equal: reuse the target column as the start.
      column_start[in_ch] = column_end[in_ch];
      needs_interpolation[in_ch] = false;
    }
  }

  // If no channel actually needs interpolation for this block build a single
  // static encoding matrix and reuse it for all frames.
  bool any_needs_interpolation = false;
  for (size_t i = 0; i < number_of_input_channels_; ++i) {
    if (needs_interpolation[i]) {
      any_needs_interpolation = true;
      break;
    }
  }

  if (!any_needs_interpolation) {
    // Interpolation disabled: build one static encoding matrix for the
    // whole block from the (already computed) target columns and reuse it
    // for every frame. This avoids per-frame interpolation and copies.
    Eigen::MatrixXf static_encoding(
        static_cast<int>(number_of_output_channels_),
        static_cast<int>(number_of_input_channels_));
    static_encoding.setZero();
    for (size_t in_ch = 0; in_ch < number_of_input_channels_; ++in_ch) {
      if (column_end[in_ch].isZero(0)) {
        continue;
      }
      for (int out_ch = 0;
           out_ch < static_cast<int>(number_of_output_channels_); ++out_ch) {
        static_encoding(out_ch, static_cast<int>(in_ch)) =
            column_end[in_ch](out_ch);
      }
    }

    for (size_t frame = 0; frame < num_frames; ++frame) {
      encoded_buffer.col(static_cast<int>(frame)) =
          static_encoding * unencoded_buffer.col(static_cast<int>(frame));
    }

    last_encoding = static_encoding;
  } else {
    // Some channels need interpolation: perform per-frame interpolation so
    // parameters ramp smoothly across the block.
    for (size_t frame = 0; frame < num_frames; ++frame) {
      // Interpolation factor in [0,1]. If there's only one frame, snap to 1.
      float alpha = 1.0f;
      if (num_frames > 1) {
        alpha = static_cast<float>(frame) / static_cast<float>(num_frames - 1);
      }

      Eigen::MatrixXf encoding_frame(
          static_cast<int>(number_of_output_channels_),
          static_cast<int>(number_of_input_channels_));
      encoding_frame.setZero();

      // Build encoding_frame column-by-column for each input/source by
      // interpolating the precomputed start/end columns.
      for (size_t in_ch = 0; in_ch < number_of_input_channels_; ++in_ch) {
        // If both start and end are zeros, skip.
        if (column_start[in_ch].isZero(0) && column_end[in_ch].isZero(0)) {
          continue;
        }

        // Interpolate column values directly in coefficient space only if
        // this channel needs interpolation. Otherwise, use the target column
        // directly to avoid per-frame arithmetic.
        Eigen::VectorXf col;
        if (needs_interpolation[in_ch]) {
          col = column_start[in_ch] +
                alpha * (column_end[in_ch] - column_start[in_ch]);
        } else {
          col = column_end[in_ch];
        }

        // Copy into the encoding frame column.
        for (int out_ch = 0;
             out_ch < static_cast<int>(number_of_output_channels_); ++out_ch) {
          encoding_frame(out_ch, static_cast<int>(in_ch)) = col(out_ch);
        }
      }

      // Multiply the per-frame encoding matrix with the single-frame input
      // column to produce the encoded output for this frame index.
      // Note: use .col() with the actual frame index (frame) mapping into the
      // row-major mapped buffers.
      encoded_buffer.col(static_cast<int>(frame)) =
          encoding_frame * unencoded_buffer.col(static_cast<int>(frame));

      last_encoding = encoding_frame;  // keep for later update
    }
  }

  // After processing the block, advance current -> target for all sources so
  // the next block's ramp starts from the most recent target.
  for (auto& kv : sources_) {
    kv.second.current = kv.second.target;
  }

  // Update the stored encoding matrix to the last frame's matrix so code that
  // expects a static encoding matrix still has a reasonable value.
  encoding_matrix_ = last_encoding;
}

void AmbisonicEncoder::GetShCoeffs(float azimuth, float elevation,
                                   size_t ambisonic_order,
                                   std::vector<float>& coeffs) {
  const float azimuth_rad = azimuth * kRadiansFromDegrees;
  const float elevation_rad = elevation * kRadiansFromDegrees;

  std::vector<float> associated_legendre_polynomials_temp_ =
      alp_generator_.Generate(std::sin(elevation_rad));
  // Compute the actual spherical harmonics using the generated polynomials.
  for (int degree = 0; degree <= ambisonic_order; degree++) {
    for (int order = -degree; order <= degree; order++) {
      const int row = AcnSequence(degree, order);
      if (row == -1) {
        // Skip this spherical harmonic.
        continue;
      }

      const float angle = static_cast<float>(order) * azimuth_rad;
      const float last_term = (order >= 0) ? std::cos(angle) : std::sin(-angle);

      coeffs.at(row) =
          Sn3dNormalization(degree, order) *
          associated_legendre_polynomials_temp_[alp_generator_.GetIndex(
              degree, std::abs(order))] *
          last_term;
    }
  }
}

void AmbisonicEncoder::ApplyWithinHeadSHCoefficientsDamping(
    float distance, float normalized_head_radius,
    std::vector<float>& coeffs) const {
  // If normalized_head_radius is <= 0, consider no damping.
  if (normalized_head_radius <= 0.0f) {
    return;
  }

  // If distance exceeds the normalized head radius, consider source outside
  // the head: do not damp coefficients.
  if (distance >= normalized_head_radius) {
    return;
  }

  // Remap distance from [0, normalized_head_radius] -> [0,1]. Clamp to [0,1]
  // for safety.
  float remapped = distance / normalized_head_radius;
  remapped = std::clamp(remapped, 0.0f, 1.0f);

  // Apply degree-based damping: coefficients for degree `d` are multiplied by
  // (remapped)^d.
  // TODO(b/467931916): Consider using an algorithm which will preserve full
  //                    spectrum of the source.
  for (int degree = 0; degree <= static_cast<int>(ambisonic_order_); degree++) {
    auto damping_factor = static_cast<float>(std::pow(remapped, degree));
    for (int order = -degree; order <= degree; order++) {
      int index = AcnSequence(degree, order);
      if (index != -1) {
        coeffs.at(index) *= damping_factor;
      }
    }
  }
}

}  // namespace obr
