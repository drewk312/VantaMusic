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

#ifndef OBR_COMMON_STATUS_MACROS_H_
#define OBR_COMMON_STATUS_MACROS_H_

/*!\brief Macro to return early if a status is not OK.
 *
 * This macro evaluates the given expression (which must return an
 * absl::Status) and returns it immediately if the status is not OK.
 *
 * Example usage:
 *   RETURN_IF_NOT_OK(SomeFunction());
 *   RETURN_IF_NOT_OK(AnotherFunction(arg1, arg2));
 */
#define RETURN_IF_NOT_OK(...)             \
  do {                                    \
    absl::Status _status = (__VA_ARGS__); \
    if (!_status.ok()) return _status;    \
  } while (0)

#endif  // OBR_COMMON_STATUS_MACROS_H_
