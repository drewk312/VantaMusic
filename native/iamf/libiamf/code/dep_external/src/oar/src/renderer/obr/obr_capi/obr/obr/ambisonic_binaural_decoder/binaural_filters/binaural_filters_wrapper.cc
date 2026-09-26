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

#include "binaural_filters_wrapper.h"

#include <functional>
#include <memory>
#include <string>
#include <unordered_map>
#include <vector>

#include "absl/types/span.h"
#include "binaural_filters_1_oa_ambient_l.h"
#include "binaural_filters_1_oa_ambient_r.h"
#include "binaural_filters_1_oa_direct_l.h"
#include "binaural_filters_1_oa_direct_r.h"
#include "binaural_filters_1_oa_reverberant_l.h"
#include "binaural_filters_1_oa_reverberant_r.h"
#include "binaural_filters_2_oa_ambient_l.h"
#include "binaural_filters_2_oa_ambient_r.h"
#include "binaural_filters_2_oa_direct_l.h"
#include "binaural_filters_2_oa_direct_r.h"
#include "binaural_filters_2_oa_reverberant_l.h"
#include "binaural_filters_2_oa_reverberant_r.h"
#include "binaural_filters_3_oa_ambient_l.h"
#include "binaural_filters_3_oa_ambient_r.h"
#include "binaural_filters_3_oa_direct_l.h"
#include "binaural_filters_3_oa_direct_r.h"
#include "binaural_filters_3_oa_reverberant_l.h"
#include "binaural_filters_3_oa_reverberant_r.h"
#include "binaural_filters_4_oa_ambient_l.h"
#include "binaural_filters_4_oa_ambient_r.h"
#include "binaural_filters_4_oa_direct_l.h"
#include "binaural_filters_4_oa_direct_r.h"
#include "binaural_filters_4_oa_reverberant_l.h"
#include "binaural_filters_4_oa_reverberant_r.h"

namespace obr {

BinauralFiltersWrapper::BinauralFiltersWrapper() {}

BinauralFiltersWrapper::~BinauralFiltersWrapper() {}

std::unique_ptr<std::string> BinauralFiltersWrapper::GetFile(
    const std::string& filename) const {
  using AssetMap =
      std::unordered_map<std::string,
                         std::function<absl::Span<const unsigned char>()>>;
  static const AssetMap kAssetMap = [] {
    return AssetMap{
        {"1OAAmbientL", filter_files::BinauralFilters1OAAmbientL},
        {"1OAAmbientR", filter_files::BinauralFilters1OAAmbientR},
        {"1OADirectL", filter_files::BinauralFilters1OADirectL},
        {"1OADirectR", filter_files::BinauralFilters1OADirectR},
        {"1OAReverberantL", filter_files::BinauralFilters1OAReverberantL},
        {"1OAReverberantR", filter_files::BinauralFilters1OAReverberantR},
        {"2OAAmbientL", filter_files::BinauralFilters2OAAmbientL},
        {"2OAAmbientR", filter_files::BinauralFilters2OAAmbientR},
        {"2OADirectL", filter_files::BinauralFilters2OADirectL},
        {"2OADirectR", filter_files::BinauralFilters2OADirectR},
        {"2OAReverberantL", filter_files::BinauralFilters2OAReverberantL},
        {"2OAReverberantR", filter_files::BinauralFilters2OAReverberantR},
        {"3OAAmbientL", filter_files::BinauralFilters3OAAmbientL},
        {"3OAAmbientR", filter_files::BinauralFilters3OAAmbientR},
        {"3OADirectL", filter_files::BinauralFilters3OADirectL},
        {"3OADirectR", filter_files::BinauralFilters3OADirectR},
        {"3OAReverberantL", filter_files::BinauralFilters3OAReverberantL},
        {"3OAReverberantR", filter_files::BinauralFilters3OAReverberantR},
        {"4OAAmbientL", filter_files::BinauralFilters4OAAmbientL},
        {"4OAAmbientR", filter_files::BinauralFilters4OAAmbientR},
        {"4OADirectL", filter_files::BinauralFilters4OADirectL},
        {"4OADirectR", filter_files::BinauralFilters4OADirectR},
        {"4OAReverberantL", filter_files::BinauralFilters4OAReverberantL},
        {"4OAReverberantR", filter_files::BinauralFilters4OAReverberantR},
    };
  }();

  auto it = kAssetMap.find(filename);
  if (it == kAssetMap.end()) {
    return nullptr;
  }
  auto data = it->second();
  return std::make_unique<std::string>(
      reinterpret_cast<const char*>(data.data()), data.size());
}

}  // namespace obr
