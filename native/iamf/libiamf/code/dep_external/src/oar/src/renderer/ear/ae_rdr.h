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
 * @file ae_rdr.h
 * @brief Rendering APIs.
 * @version 2.0.0
 * @date Created 03/03/2023
 **/

#ifndef __AE_RDR_H__
#define __AE_RDR_H__

#include <stdint.h>

typedef uint64_t BS2051_SPLABEL;

#define BS2051_MAX_CHANNELS 25

#define SP_MP000 0x00000001
#define SP_MP030 0x00000002
#define SP_MM030 0x00000004
#define SP_MP045 0x00000008
#define SP_MM045 0x00000010
#define SP_MP060 0x00000020
#define SP_MM060 0x00000040
#define SP_MP090 0x00000080
#define SP_MM090 0x00000100
#define SP_MP110 0x00000200
#define SP_MM110 0x00000400
#define SP_MP135 0x00000800
#define SP_MM135 0x00001000
#define SP_MP180 0x00002000
#define SP_TP000 0x00004000
#define SP_UP000 0x00008000
#define SP_UP030 0x00010000
#define SP_UM030 0x00020000
#define SP_UP045 0x00040000
#define SP_UM045 0x00080000
#define SP_UP090 0x00100000
#define SP_UM090 0x00200000
#define SP_UP110 0x00400000
#define SP_UM110 0x00800000
#define SP_UP135 0x01000000
#define SP_UM135 0x02000000
#define SP_UP180 0x04000000
#define SP_BP000 0x08000000
#define SP_BP045 0x10000000
#define SP_BM045 0x20000000
#define SP_LFE1 0x40000000
#define SP_LFE2 0x80000000
#define SP_BP135 0x100000000ULL
#define SP_BM135 0x200000000ULL

typedef enum {
  IAMF_BS2051_NONE = 0,
  BS2051_A = 0x020,        // 2ch output
  BS2051_B = 0x050,        // 6ch output
  BS2051_C = 0x250,        // 8ch output
  BS2051_D = 0x450,        // 10ch output
  BS2051_E = 0x451,        // 11ch output
  BS2051_F = 0x370,        // 12ch output
  BS2051_G = 0x490,        // 14ch output
  BS2051_H = 0x9A3,        // 24ch output
  BS2051_I = 0x070,        // 8ch output
  BS2051_J = 0x470,        // 12ch output
  IAMF_STEREO = 0x200,     // 2ch input
  IAMF_51 = 0x510,         // 6ch input
  IAMF_512 = 0x512,        // 8ch input
  IAMF_514 = 0x514,        // 10ch input
  IAMF_71 = 0x710,         // 8ch input
  IAMF_714 = 0x714,        // 12ch input
  IAMF_MONO = 0x100,       // 1ch input/output, AOM only
  IAMF_712 = 0x712,        // 10ch input/output, AOM only
  IAMF_312 = 0x312,        // 6ch input/output, AOM only
  IAMF_BINAURAL = 0x1020,  // binaural input/output AOM only
  IAMF_916 = 0x916,        // 16ch input/output, AOM only
  IAMF_7154 = 0x7154,      // 18ch input/output, AOM only
  IAMF_A293 = 0xA293,      // 24ch input
} IAMF_SOUND_SYSTEM;

#ifndef DISABLE_LFE_HOA
#define DISABLE_LFE_HOA 1
#endif  // __AE_RDR_H__

typedef struct {
  int init;
  float c, a1, a2, a3, b1, b2;
  float input_history[2], output_history[2];
} lfe_filter_t;

typedef struct {
  IAMF_SOUND_SYSTEM system;
  int lfe1;
  int lfe2;
} IAMF_PREDEFINED_SP_LAYOUT;

typedef struct {
  IAMF_SOUND_SYSTEM system;
  BS2051_SPLABEL sp_flags;  // subset of BS2051_SPLABEL
} IAMF_CUSTOM_SP_LAYOUT;

typedef struct {
  int sp_type;  // 0: predefined_sp, 1: custom_sp
  union {
    IAMF_PREDEFINED_SP_LAYOUT *predefined_sp;
    IAMF_CUSTOM_SP_LAYOUT *custom_sp;
  } sp_layout;
  lfe_filter_t lfe_f;  // for H2M lfe
} IAMF_SP_LAYOUT;

typedef enum {
  IAMF_ZOA = 0,
  IAMF_FOA = 1,
  IAMF_SOA = 2,
  IAMF_TOA = 3,
  IAMF_H4A = 4
} HOA_ORDER;

typedef struct {
  HOA_ORDER order;
  int lfe_on;  // HOA lfe on/off
} IAMF_HOA_LAYOUT;

struct m2m_rdr_t {
  IAMF_SOUND_SYSTEM in;
  IAMF_SOUND_SYSTEM out;
  float *mat;
  int m;
  int n;
};

struct h2m_rdr_t {
  HOA_ORDER in;
  IAMF_SOUND_SYSTEM out;
  int channels;
  int lfe1;
  int lfe2;
  float *mat;
  int m;
  int n;
};

// Predefined Multichannel to Multichannel
/**
 * @brief     Get the ear render conversion matrix of predefined multichannel to
 * multichannel according to predefined direct speaker input and output layout.
 * @param     [in] in : predefined direct speaker input channel layout.
 * @param     [in] out : predefined direct speaker output channel layout
 * @param     [in] outMatrix : conversion matrix.
 * @return    @0: success,@others: fail
 */
int IAMF_element_renderer_get_M2M_matrix(IAMF_SP_LAYOUT *in,
                                         IAMF_SP_LAYOUT *out,
                                         struct m2m_rdr_t *outMatrix);

/**
 * @brief     Get the ear render conversion matrix of custom multichannel to
 * multichannel according to predefined direct speaker input and custom output
 * layout.
 * @param     [in] in : custom speaker input layout.
 * @param     [in] out : predefined speaker output layout
 * @param     [in] outMatrix : conversion matrix.
 * @param     [in] chmap : speaker subset channel list in input layout.
 * @return    @0: success,@others: fail
 */
int IAMF_element_renderer_get_M2M_custom_matrix(IAMF_SP_LAYOUT *in,
                                                IAMF_SP_LAYOUT *out,
                                                struct m2m_rdr_t *outMatrix,
                                                int *chmap);

/**
 * @brief     Predefined Multichannel to Multichannel Renderer.
 * @param     [in] in : the pcm signal of input.
 * @param     [in] out : the pcm signal of output
 * @param     [in] nsamples : the processed samples of pcm signal.
 * @return    @0: success,@others: fail
 */
int IAMF_element_renderer_render_M2M(struct m2m_rdr_t *m2mMatrix, float *in[],
                                     float *out[], int nsamples);

/**
 * @brief     Custom Multichannel to Multichannel Renderer.
 * @param     [in] in : the pcm signal of input.
 * @param     [in] out : the pcm signal of output
 * @param     [in] nsamples : the processed samples of pcm signal.
 * @param     [in] chmap : speaker subset channel list in input layout.
 * @return    @0: success,@others: fail
 */
int IAMF_element_renderer_render_M2M_custom(struct m2m_rdr_t *m2mMatrix,
                                            float *in[], float *out[],
                                            int nsamples, int *chmap);
// HOA to Multichannel
#if DISABLE_LFE_HOA == 0
void lfefilter_init(lfe_filter_t *lfe_f, float cutoff_freq,
                    float sampling_rate);
#endif
/**
 * @brief     Get the ear render conversion matrix of hoa to multichannel
 *            according to hoa input and direct speaker output layout.
 * @param     [in] in : HOA channel layout.
 * @param     [in] out : direct speaker output channel layout
 * @param     [in] outMatrix : conversion matrix.
 * @return    @0: success,@others: fail
 */
int IAMF_element_renderer_get_H2M_matrix(IAMF_HOA_LAYOUT *in,
                                         IAMF_PREDEFINED_SP_LAYOUT *out,
                                         struct h2m_rdr_t *outMatrix);

/**
 * @brief     Hoa to Multichannel Renderer.
 * @param     [in] in : the pcm signal of input.
 * @param     [in] out : the pcm signal of output
 * @param     [in] nsamples : the processed samples of pcm signal.
 * @param     [in] lfe : the filter to prcoess lfe channel.
 * @return    @0: success,@others: fail
 */
int IAMF_element_renderer_render_H2M(struct h2m_rdr_t *h2mMatrix, float *in[],
                                     float *out[], int nsamples,
                                     lfe_filter_t *lfe);
#endif
