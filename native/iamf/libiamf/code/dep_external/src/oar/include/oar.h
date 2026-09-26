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

#ifndef __OAR_H__
#define __OAR_H__

#include "oar_metadata.h"

typedef struct Oar oar_t;

typedef enum EOarLayout {
  ck_oar_layout_none,

  /** Audio element layouts and target layouts */
  ck_oar_layout_mono,
  ck_oar_layout_stereo,
  ck_oar_layout_51,
  ck_oar_layout_512,
  ck_oar_layout_514,
  ck_oar_layout_71,
  ck_oar_layout_712,
  ck_oar_layout_714,
  ck_oar_layout_312,
  ck_oar_layout_916,
  ck_oar_layout_a293,
  ck_oar_layout_7154,

  ck_oar_layout_sound_system_a_020 = ck_oar_layout_stereo,
  ck_oar_layout_sound_system_b_050 = ck_oar_layout_51,
  ck_oar_layout_sound_system_c_250 = ck_oar_layout_512,
  ck_oar_layout_sound_system_d_450 = ck_oar_layout_514,
  ck_oar_layout_sound_system_i_070 = ck_oar_layout_71,
  ck_oar_layout_sound_system_j_470 = ck_oar_layout_714,
  ck_oar_layout_sound_system_h_9a3 = ck_oar_layout_a293,

  /** Target layouts */
  ck_oar_layout_sound_system_e_451 = 0x81,
  ck_oar_layout_sound_system_f_370,
  ck_oar_layout_sound_system_g_490,

  /** Audio element layouts and target layouts */
  ck_oar_layout_binaural = 0xFF,

  /**
   * Audio element layouts.
   * These are used by IAMF, refer to
   * https://aomediacodec.github.io/iamf/#expanded_loudspeaker_layout
   * */
  ck_oar_layout_subset_start = 0x100,
  /** The low-frequency effects subset (LFE) of 7.1.4ch */
  ck_oar_layout_lfe,
  /** The surround subset (Ls/Rs) of 5.1.4ch */
  ck_oar_layout_stereo_s,
  /** The side surround subset (Lss/Rss) of 7.1.4ch */
  ck_oar_layout_stereo_ss,
  /** The rear surround subset (Lrs/Rrs) of 7.1.4ch */
  ck_oar_layout_stereo_rs,
  /** The top front subset (Ltf/Rtf) of 7.1.4ch */
  ck_oar_layout_stereo_tf,
  /** The top back subset (Ltb/Rtb) of 7.1.4ch */
  ck_oar_layout_stereo_tb,
  /** The top 4 channels (Ltf/Rtf/Ltb/Rtb) of 7.1.4ch */
  ck_oar_layout_top_4ch,
  /** The front 3 channels (L/C/R) of 7.1.4ch */
  ck_oar_layout_3ch,
  /** The front subset (FL/FR) of 9.1.6ch */
  ck_oar_layout_stereo_f,
  /** The side subset (SiL/SiR) of 9.1.6ch */
  ck_oar_layout_stereo_si,
  /** The top side subset (TpSiL/TpSiR) of 9.1.6ch */
  ck_oar_layout_stereo_tpsi,
  /** The top 6 channels (TpFL/TpFR/TpBL/TpBR/TpSiL/TpSiR) of 9.1.6ch */
  ck_oar_layout_top_6ch,
  /** The low-frequency effects subset (LFE1/LFE2) of 10.2.9.3ch */
  ck_oar_layout_lfe_pair,
  /** The bottom 3 channels (BtFC/BtFL/BtFR) of 10.2.9.3ch */
  ck_oar_layout_bottom_3ch,
  /** The bottom 4 channels (BtFL/BtFR/BtBL/BtBR) of 7.1.5.4ch */
  ck_oar_layout_bottom_4ch,
  /** The top subset (TpC) of 7.1.5.4ch */
  ck_oar_layout_top_1ch,
  /** The top 5 channels (Ltf/Rtf/TpC/Ltb/Rtb) of 7.1.5.4ch */
  ck_oar_layout_top_5ch,
} oar_layout_t;

typedef enum EOarHighOrderAmbisonics {
  ck_oar_hoa_none = -1,
  ck_oar_zoa,
  ck_oar_1oa,
  ck_oar_2oa,
  ck_oar_3oa,
  ck_oar_4oa,
} oar_hoa_t;

typedef enum EAudioElementType {
  ck_oar_element_type_none = -1,
  ck_channel_based,
  ck_scene_based,
  ck_object_based,
} audio_element_type_t;

typedef struct OarConfig {
  oar_layout_t target_layout;
  uint32_t samples_per_channel;
  uint32_t sampling_rate;
} oar_config_t;

typedef struct OarChannelBasedConfig {
  oar_layout_t layout;
} oar_channel_based_config_t;

typedef struct OarSceneBasedConfig {
  oar_hoa_t order;
} oar_scene_based_config_t;

typedef struct OarObjectBasedConfig {
  int num_objects;  // Number of objects, valid range is 1-2.
} oar_object_based_config_t;

typedef struct OarAudioElementConfig {
  audio_element_type_t type;
  union {
    oar_channel_based_config_t cbc;
    oar_scene_based_config_t sbc;
    oar_object_based_config_t obc;
  };

  parameter_set_t parameters;
} oar_audio_element_config_t;

/**
 * @brief     Create an OAR object
 * @param     [in] config : Configuration parameters
 * @return    Pointer to oar_t on success, NULL on failure
 */
oar_t *oar_create(const oar_config_t *config);

/**
 * @brief     Destroy an OAR object
 * @param     [in] oar : OAR object to destroy
 */
void oar_destroy(oar_t *oar);

/**
 * @brief     Add an audio group
 * @param     [in] oar : OAR object
 * @return    Group ID (0-1) on success, negative error code on failure
 *            -1 for invalid parameters, -12 for memory allocation failure,
 *            -16 if maximum number of groups (2) already reached
 */
int oar_add_audio_group(oar_t *oar);

/**
 * @brief     Add an audio element to a specific group
 * @param     [in] oar : OAR object
 * @param     [in] gid : Group ID where the element will be added
 * @param     [in] id : Unique element id (must be unique across all groups)
 * @param     [in] config : Element configuration
 * @return    0 on success, -1 for invalid parameters, -12 for memory allocation
 *            failure.
 */
int oar_add_audio_element(oar_t *oar, uint32_t gid, uint32_t id,
                          const oar_audio_element_config_t *config);

/**
 * @brief     Remove an audio element
 * @param     [in] oar : OAR object
 * @param     [in] id : Unique element id (searched across all groups)
 * @return    0 on success, -1 for invalid parameters or if ID doesn't exist
 */
int oar_remove_audio_element(oar_t *oar, uint32_t id);

/**
 * @brief     Update audio element metadata
 * @param     [in] oar : OAR object
 * @param     [in] id : Unique element id (searched across all groups)
 * @param     [in] metadata : Metadata to update
 * @return    0 on success, -1 for invalid parameters or if ID doesn't exist
 */
int oar_update_audio_element_metadata(oar_t *oar, uint32_t id,
                                      const oar_metadata_t *metadata);

/**
 * @brief     Add audio element data
 * @param     [in] oar : OAR object
 * @param     [in] id : Unique element id (searched across all groups)
 * @param     [in] data : Audio data in planar format
 * @return    0 on success, -1 for invalid parameters or if ID doesn't exist
 */
int oar_update_audio_element_data(oar_t *oar, uint32_t id,
                                  oar_audio_block_t *data);

/**
 * @brief     Set the processing unit in samples for a specific metadata type
 *
 * This function configures how many samples should be processed as a unit for
 * the specified metadata type. Currently, only object positions metadata type
 * is supported for this configuration.
 *
 * @param     [in] oar : OAR object
 * @param     [in] type : Metadata type to configure (currently only
 *                        ck_metadata_object_positions supported)
 * @param     [in] samples : Number of samples to process as a unit
 * @return    0 on success, -1 for invalid parameters, -18 for unsupported
 *            metadata type
 * @note      Currently only ck_metadata_object_positions is supported for
 *            setting
 */
int oar_set_metadata_unit_to_process(oar_t *oar, oar_metadata_type_t type,
                                     uint32_t samples);

/**
 * @brief     Update group metadata
 * @param     [in] oar : OAR object
 * @param     [in] gid : Group ID to update metadata for
 * @param     [in] metadata : Metadata to update (only ck_metadata_gain type
 *                            supported)
 * @return    0 on success, -1 for invalid parameters or if group ID doesn't
 *            exist
 */
int oar_update_metadata(oar_t *oar, uint32_t gid,
                        const oar_metadata_t *metadata);

/**
 * @brief     Perform rendering
 * @param     [in] oar : OAR object
 * @param     [out] output : Output audio data in planar format.
 * @return    0 on success, -1 for invalid parameters
 * @note      Renders all audio groups. For single group, uses zero-copy
 *            optimization. For multiple groups, mixes all group outputs.
 */
int oar_render(oar_t *oar, oar_audio_block_t *output);

/**
 * @brief     Enable/disable loudness processor
 * @param     [in] oar : OAR object
 * @param     [in] enable : 1 to enable, 0 to disable
 * @return    0 on success, -1 for invalid parameters
 * @note      When enabled, applies group-specific loudness gain during
 *            rendering
 */
int oar_enable_loudness_processor(oar_t *oar, int enable);

/**
 * @brief     Set loudness parameters for a specific group
 * @param     [in] oar : OAR object
 * @param     [in] gid : Group ID to set loudness for
 * @param     [in] loudness : current loudness in dB
 * @param     [in] target_loudness : Target loudness in dB
 * @return    0 on success, -1 for invalid parameters or if group ID doesn't
 *            exist
 */
int oar_set_loudness(oar_t *oar, uint32_t gid, float loudness,
                     float target_loudness);

/**
 * @brief     Enable/disable limiter
 * @param     [in] oar : OAR object
 * @param     [in] enable : 1 to enable, 0 to disable
 * @return    0 on success, -1 for invalid parameters
 */
int oar_enable_limiter(oar_t *oar, int enable);

/**
 * @brief     Enable or disable head tracking for all audio elements.
 *
 * When enabled, OAR will accept head-orientation data (as quaternions via
 * @ref OarMetadata.head_rotation) to compensate for listener head rotation.
 * Counter-rotation is applied to the Ambisonic bed using spherical harmonic
 * rotation matrices, ensuring the spatial audio scene remains stable in world
 * space.
 *
 * @param     [in] oar : OAR object
 * @param     [in] enable : 1 to enable, 0 to disable
 * @return    0 on success, -1 for invalid parameters
 * @note      Applies to all audio elements across all groups. Only supported
 *            for binaural layout.
 */
int oar_enable_head_tracking(oar_t *oar, int enable);

/**
 * @brief     Get samples per channel
 * @param     [in] oar : OAR object
 * @return    Number of samples, 0 indicates invalid parameters
 */
uint32_t oar_get_samples_per_channel(oar_t *oar);

/**
 * @brief     Get sampling rate
 * @param     [in] oar : OAR object
 * @return    Sampling rate, 0 indicates invalid parameters
 */
uint32_t oar_get_sampling_rate(oar_t *oar);

/**
 * @brief     Get number of audio element channels
 * @param     [in] oar : OAR object
 * @param     [in] id : Unique element id (searched across all groups)
 * @return    Number of channels, 0 indicates invalid parameters or non-existent
 *            ID
 */
uint32_t oar_get_number_of_audio_element_channels(oar_t *oar, uint32_t id);

/**
 * @brief     Get number of output channels
 * @param     [in] oar : OAR object
 * @return    Number of channels, 0 indicates invalid parameters
 */
uint32_t oar_get_number_of_output_channels(oar_t *oar);

/**
 * @brief     Get total number of audio elements across all groups
 * @param     [in] oar : OAR object
 * @return    Total number of elements across all groups, 0 indicates invalid
 *            parameters
 */
uint32_t oar_get_number_of_audio_elements(oar_t *oar);

#endif  // __OAR_H__
