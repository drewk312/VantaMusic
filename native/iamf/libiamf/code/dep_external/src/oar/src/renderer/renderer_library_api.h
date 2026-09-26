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
 * @file renderer_library_api.h
 * @brief This file defines the APIs of the renderer, which will be used
 *        internally by the iamf decoder. The third party can also implement
 *        these APIs to create own renderer, and then complete the registration
 *        of the renderer through the iamf decoder API
 *        @ref IAMF_decoder_renderer_register(), and then the renderer can be
 *        used by the iamf decoder.
 *
 * @version 2.0.0
 * @date Created 20/03/2025
 **/

#ifndef __RENDERER_LIBRARY_API_H__
#define __RENDERER_LIBRARY_API_H__

#include <stdint.h>

#include "oar_base.h"
#include "oar_metadata.h"

#ifdef __cplusplus
extern "C" {
#endif

/**
 * @brief Flag to identify rendering input IDs (0x100000)
 * Used in ERenderingIOId enum to mark input configurations
 */
#define def_ri_flag 0x100000

/**
 * @brief Flag to identify rendering output IDs (0x010000)
 * Used in ERenderingIOId enum to mark output configurations
 */
#define def_ro_flag 0x010000

/**
 * @brief The audio rendering input/output id.
 *
 */
typedef enum ERenderingIOId {
  ck_rid_none,

  ck_rio_id_mono = 0x1 | def_ri_flag | def_ro_flag,
  ck_rio_id_stereo,
  ck_rio_id_51,
  ck_rio_id_512,
  ck_rio_id_514,
  ck_rio_id_71,
  ck_rio_id_712,
  ck_rio_id_714,
  ck_rio_id_312,
  ck_rio_id_916,
  ck_rio_id_a293,
  ck_rio_id_7154,

  ck_ro_id_sound_system_e_451 = 0x81 | def_ro_flag,
  ck_ro_id_sound_system_f_370,
  ck_ro_id_sound_system_g_490,

  ck_rio_id_sound_system_a_020 = ck_rio_id_stereo,
  ck_rio_id_sound_system_b_050 = ck_rio_id_51,
  ck_rio_id_sound_system_c_250 = ck_rio_id_512,
  ck_rio_id_sound_system_d_450 = ck_rio_id_514,
  ck_rio_id_sound_system_i_070 = ck_rio_id_71,
  ck_rio_id_sound_system_j_470 = ck_rio_id_714,
  ck_rio_id_sound_system_h_9a3 = ck_rio_id_a293,

  ck_ri_id_oa_mono = 0xF1 | def_ri_flag,  // audio object mono
  ck_ri_id_oa_dual,

  ck_rio_id_binaural = 0xFF | def_ri_flag | def_ro_flag,

  ck_ri_zoa = 0x100 | def_ri_flag,
  ck_ri_1oa,
  ck_ri_2oa,
  ck_ri_3oa,
  ck_ri_4oa,

  ck_ri_id_iamf = 0x200 | def_ri_flag,
  ck_ri_id_lfe,
  ck_ri_id_stereo_s,
  ck_ri_id_stereo_ss,
  ck_ri_id_stereo_rs,
  ck_ri_id_stereo_tf,
  ck_ri_id_stereo_tb,
  ck_ri_id_top_4ch,
  ck_ri_id_3ch,
  ck_ri_id_stereo_f,
  ck_ri_id_stereo_si,
  ck_ri_id_stereo_tpsi,
  ck_ri_id_top_6ch,
  ck_ri_id_lfe_pair,
  ck_ri_id_bottom_3ch,
  ck_ri_id_bottom_4ch,
  ck_ri_id_top_1ch,
  ck_ri_id_top_5ch,
  ck_ri_id_iamf_end,

  ck_ri_multi_ids = 0xf00 | def_ri_flag,
} rid_t;

typedef rid_t ri_id_t;
typedef rid_t ro_id_t;

/**
 * @brief Rendering attributes that can be set on a renderer instance.
 *
 * These attributes control various rendering behaviors and features
 * that can be dynamically configured during runtime.
 */
typedef enum ERenderingAttribute {
  /** No attribute specified */
  ck_attribute_none,
  /** Enable or configure head tracking functionality */
  ck_attribute_head_tracking,
  /** Add or modify rendering elements */
  ck_attribute_add_element,
  /** Set head-locked rendering mode for a specific audio element */
  ck_attribute_element_head_locked,
} rendering_attribute_t;

typedef uint32_t head_tracking_t;
typedef uint32_t head_locked_t;

/**
 * @brief Parameter set for configuring rendering elements.
 *
 * This structure defines the parameters associated with a specific rendering
 * element, including its identifier and binaural filter profile.
 */
typedef struct ElementParameterSet {
  /** Rendering identifier for this element */
  rid_t id;
  /** Binaural filter profile used for this element */
  oar_binaural_filter_profile_t profile;
} element_parameter_set_t;

/**
 * @brief Parameter structure for configuring head-locked rendering mode.
 *
 * This structure defines the parameters for setting the head-locked rendering
 * mode for a specific audio element. When head-locked rendering is enabled,
 * the element will not be rotated by head tracking, causing it to move with
 * the listener's head rather than remaining fixed in world space.
 */
typedef struct ElementHeadLocked {
  /** Index of the audio element to configure */
  uint32_t index;
  /** Head-locked state: 0 for world-locked (rotated by head tracking),
      1 for head-locked (not rotated by head tracking) */
  head_locked_t locked;
} element_head_locked_t;

/** The context of renderer library. */
typedef struct RendererLibraryContext {
  /** pointer to the renderer instance. */
  void *renderer;

  /**
   * The audio rendering input id.
   */
  ri_id_t in;
  /**
   * The audio rendering output id.
   */
  ro_id_t out;

  /** The id of the audio signal that needs to be rendered */
  uint32_t stream_id;

  /** Sampling rate of input signal */
  uint32_t sample_rate;

  /** The number of samples in frame */
  uint32_t samples_per_frame;

  /** The renderer initialized parameters */
  parameter_set_t parameters;
} renderer_library_context_t;

/** Renderer Library APIs */
typedef struct RendererLibraryAPI {
  /** The unique identifier of the renderer. */
  const char *id;

  /**
   * The priority of the renderer. The lower the value, the higher the
   * priority. The default priority should be 128.
   */
  uint32_t priority;

  /**
   * @brief Check if the renderer is capable of handling the specified input and
   *        output configurations.
   *
   * This function allows the caller to query the renderer's capabilities
   * before attempting to open and use it. The renderer should examine the
   * `in` and `out` fields of the context, along with any other relevant
   * parameters (like `sample_rate` or `parameters` if necessary for the
   * capability check), to determine if it can perform the rendering.
   *
   * @param [in] ctx : The context of renderer, containing the proposed input
   *                   (`ri_id_t`) and output (`ro_id_t`) rendering IDs and
   *                   other configuration details for the capability check.
   *                   The `renderer` pointer within the context may be NULL
   *                   as this check is typically done before opening a renderer
   *                   instance.
   *
   * @return 0 if the renderer is capable of handling the specified `rid` in and
   *         out configurations, otherwise a non-zero error code.
   *         - 0: Capable
   *       - Non-zero: Not capable or error during check.
   *         Specific error codes can be defined by implementations if needed,
   *         but a simple non-zero is sufficient to indicate incapability.
   *
   * @note This function should not modify the context or allocate resources
   *       for rendering. It is a read-only query function.
   */
  int (*is_capable)(const renderer_library_context_t *ctx);

  /**
   * @brief Open a new renderer instance.
   *
   * @param [in/out] ctx : The context of renderer. It contains the information
   *                       about the renderer instance. The caller should
   *                       allocate memory for the context before calling this
   *                       function. After the function returns, the renderer
   *                       instance will be initialized and stored in the
   *                       context.
   *
   * @return 0 on success, otherwise non-zero error code.
   *
   * @note The caller must call @ref close() to release the resources after
   *       using the renderer. if the open operation fails, the renderer
   *       instance should release the resources allocated during the open
   *       operation.
   */
  int (*open)(renderer_library_context_t *ctx);

  /**
   * @brief Set rendering attributes for the renderer.
   *
   * @param [in/out] ctx : The context of renderer. Contains the renderer
   * instance and configuration information.
   * @param [in] attr : The type of rendering attribute to be set. Specifies
   * which attribute (e.g., head tracking) should be configured.
   * @param [in] value : Pointer to the attribute value to be set. The type and
   *                     interpretation of the value depends on the attribute
   * type.
   *
   * @return 0 on success, otherwise non-zero error code.
   *
   * @note This function allows dynamic configuration of renderer behavior
   *       during runtime. Supported attributes depend on the specific renderer
   *       implementation. The caller must ensure the value parameter points to
   *       valid data of the correct type for the specified attribute.
   */
  int (*set_attribute)(renderer_library_context_t *ctx,
                       rendering_attribute_t attr, const void *value);

  /**
   * @brief Get rendering attributes from the renderer.
   *
   * @param [in] ctx : The context of renderer. Contains the renderer instance
   *                   and configuration information.
   * @param [in] attr : The type of rendering attribute to be retrieved.
   * Specifies which attribute (e.g., head tracking) should be queried.
   * @param [out] value : Pointer to a buffer where the attribute value will be
   * stored. The caller must allocate this buffer with sufficient space to hold
   * the requested attribute value. The type and size of the expected data
   * depends on the attribute type.
   *
   * @return 0 on success, otherwise non-zero error code.
   *
   * @note This function allows querying of current renderer configuration
   *       settings during runtime. Supported attributes depend on the specific
   *       renderer implementation. The caller must ensure the value parameter
   *       points to a buffer of adequate size and correct type for the
   *       specified attribute.
   */
  int (*get_attribute)(renderer_library_context_t *ctx,
                       rendering_attribute_t attr, void *value);

  /**
   * @brief Update metadata for the renderer.
   *
   * @param [in] ctx : The context of renderer.
   * @param [in] index : The index of the metadata element to be updated.
   * @param [in] metadata : The metadata to be updated.
   *
   * @return 0 on success, otherwise non-zero error code.
   */
  int (*metadata_update)(renderer_library_context_t *ctx, uint32_t index,
                         const oar_metadata_t *metadata);

  /**
   * @brief Render a frame of audio signal.
   *
   * @param ctx The renderer context containing configuration and state
   * @param in Input audio block containing planar audio data
   * @param out Output audio block where rendered audio will be stored
   *
   * @return int Returns 0 on success, non-zero error code on failure:
   *             - EINVAL: Invalid parameters
   *             - ENOMEM: Memory allocation failure
   *             - EBUSY:  Resource busy/temporarily unavailable
   *             - Other implementation-specific error codes
   */
  int (*render)(renderer_library_context_t *ctx, const oar_audio_block_t *in,
                oar_audio_block_t *out);

  /**
   * @brief Close a renderer instance.
   *
   * @param [in] ctx : The context of renderer.
   *
   * @return 0 on success, otherwise non-zero error code.
   */
  int (*close)(renderer_library_context_t *ctx);

} renderer_library_api_t;

#ifdef __cplusplus
}
#endif

#endif  // __RENDERER_LIBRARY_API_H__
