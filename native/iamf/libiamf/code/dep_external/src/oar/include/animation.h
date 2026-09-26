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

/**
 * @file animation.h
 * @brief Animation system for audio object positioning and parameter
 * interpolation
 *
 * This header defines the animation framework used for interpolating audio
 * object positions and parameters over time. The system supports three types of
 * animation:
 * - Step: Constant value throughout the animation duration
 * - Linear: Smooth linear transition between start and end values
 * - Bezier: Curved transition using cubic bezier interpolation with control
 * points
 *
 * The animation system is designed to work with both polar (azimuth, elevation,
 * distance) and Cartesian (x, y, z) coordinate systems, making it suitable for
 * 3D audio object positioning in spatial audio rendering applications.
 */

#ifndef __ANIMATION_H__
#define __ANIMATION_H__

#include <stdint.h>

/**
 * @brief Animation type enumeration
 *
 * Defines the different types of animation interpolation methods supported
 * for animating values over time.
 *
 * Animation Type Conversion:
 *
 * Some systems use interpolation types (inter_linear, inter_bezier) to
 * implement continuous animation transitions. Conversion method:
 *
 * Steps:
 * 1. Set current segment's 'start' to previous segment's 'end' or default value
 * 2. Type mapping: inter_linear → linear, inter_bezier → bezier
 *
 * Example:
 *   Segment 1: linear, start=0, end=10
 *   Segment 2: inter_linear, end=20
 *
 *   Converted Segment 2:
 *     start = 10 (inherited from segment 1's end)
 *     type = linear
 *     end = 20
 *
 *   Result: Continuous movement from 0 to 20
 *
 * Type Mapping Table:
 *   Original Type    | Target Type
 *   -----------------|------------
 *   inter_linear     | linear
 *   inter_bezier     | bezier
 *
 */
typedef enum EAnimationType {
  ck_animation_type_step,  ///< Step animation: value remains constant at start
                           ///< value
  ck_animation_type_linear,  ///< Linear animation: value transitions linearly
                             ///< from start to end
  ck_animation_type_bezier,  ///< Bezier animation: value follows a bezier curve
                             ///< with control point
} animation_type_t;

/**
 * @brief Animation data structure for float32 values
 *
 * Contains the parameters needed to define an animation for a single float32
 * value. The interpretation of these fields depends on the animation type:
 * - Step: Only 'start' is used
 * - Linear: 'start' and 'end' are used
 * - Bezier: All fields are used for cubic bezier interpolation
 */
typedef struct AnimatedDataFloat32 {
  float start;    ///< Starting value of the animation
  float end;      ///< Ending value of the animation (unused for step type)
  float control;  ///< Control point value for bezier animation (unused for
                  ///< step/linear)
  float control_relative_time;  ///< Relative time (0.0-1.0) of the control
                                ///< point for bezier animation
} animated_data_float32_t;

/**
 * @brief Animated float32 value structure
 *
 * Combines an animation type with the corresponding animation data to create
 * a complete animated float32 value that can be interpolated over time.
 */
typedef struct AnimatedFloat32 {
  animation_type_t
      animation_type;  ///< Type of animation (step, linear, or bezier)
  animated_data_float32_t data;  ///< Animation parameters specific to the type
} animated_float32_t;

/**
 * @brief Animated polar coordinates structure
 *
 * Represents a point in 3D space using polar coordinates (azimuth, elevation,
 * distance) where each component can be animated independently over time. This
 * is commonly used for positioning audio objects in spherical coordinate
 * systems.
 */
typedef struct AnimatedPolar {
  animation_type_t
      animation_type;  ///< Animation type applied to all components

  /* Range: -180.0 to 180.0 degrees */
  animated_data_float32_t azimuth;  ///< Horizontal angle (left-right rotation)
  /* Range: -90.0 to 90.0 degrees */
  animated_data_float32_t elevation;  ///< Vertical angle (up-down rotation)
  /* Range: 0.0 to 1.0 */
  animated_data_float32_t distance;  ///< Distance from origin (normalized)
} animated_polar_t;

/**
 * @brief Animated Cartesian coordinates structure
 *
 * Represents a point in 3D space using Cartesian coordinates (x, y, z)
 * where each component can be animated independently over time. This is
 * commonly used for positioning audio objects in 3D Cartesian coordinate
 * systems.
 */
typedef struct AnimatedCartesian {
  animation_type_t
      animation_type;  ///< Animation type applied to all components

  /* Range: -1.0 to 1.0 */
  animated_data_float32_t x, y, z;  ///< X, Y, Z coordinates (normalized range)
} animated_cartesian_t;

/**
 * @brief     Create a step animation instance
 *
 * In a step animation, the value remains constant at the start value
 * throughout the entire animation duration
 *
 * @param     [in] type : The data type for the animation (e.g.,
 *                        animated_data_float32_t)
 * @param     [in] _start : The constant value for the animation
 * @return    An animated_float32_t structure configured for step animation
 */
#define def_animated_data_step_instance(type, _start) \
  ((type){.start = (_start)})

/**
 * @brief     Create a linear animation instance
 *
 * In a linear animation, the value transitions linearly from start to end
 * over the animation duration
 *
 * @param     [in] type : The data type for the animation (e.g.,
 *                        animated_data_float32_t)
 * @param     [in] _start : The starting value for the animation
 * @param     [in] _end : The ending value for the animation
 * @return    An animated_float32_t structure configured for linear animation
 */
#define def_animated_data_linear_instance(type, _start, _end) \
  ((type){.start = (_start), .end = (_end)})

/**
 * @brief     Create a bezier animation instance
 *
 * In a bezier animation, the value follows a bezier curve defined by start,
 * end, and control points
 *
 * @param     [in] type : The data type for the animation (e.g.,
 *                        animated_data_float32_t)
 * @param     [in] _start : The starting value for the animation
 * @param     [in] _end : The ending value for the animation
 * @param     [in] _control : The control point value that defines the bezier
 *                            curve
 * @param     [in] _control_relative_time : The relative time (0.0-1.0) of the
 *                                          control point
 * @return    An animated_float32_t structure configured for bezier animation
 */
#define def_animated_data_bezier_instance(type, _start, _end, _control, \
                                          _control_relative_time)       \
  ((type){.start = (_start),                                            \
          .end = (_end),                                                \
          .control = (_control),                                        \
          .control_relative_time = (_control_relative_time)})

#endif  // __ANIMATION_H__
