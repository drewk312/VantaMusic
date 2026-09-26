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

#ifndef BINAURAL_FILTERS_4_OA_DIRECT_R_H_
#define BINAURAL_FILTERS_4_OA_DIRECT_R_H_

#include "absl/types/span.h"

namespace obr {
namespace filter_files {

absl::Span<const unsigned char> BinauralFilters4OADirectR();

}  // namespace filter_files
}  // namespace obr

#endif  // BINAURAL_FILTERS_4_OA_DIRECT_R_H_
