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

#ifndef __LOG_H__
#define __LOG_H__

#include <stdarg.h>
#include <stdio.h>
#include <string.h>

typedef enum ELogLevel {
  ck_log_level_fatal = 0,
  ck_log_level_error = 1,
  ck_log_level_warn = 2,
  ck_log_level_warning = ck_log_level_warn,
  ck_log_level_info = 3,
  ck_log_level_debug = 4,
  ck_log_level_trace = 5,
} log_level_t;

void set_log_level(log_level_t level);

#ifndef __MODULE__
#define __MODULE__ \
  (strrchr(__FILE__, '/') ? strrchr(__FILE__, '/') + 1 : __FILE__)
#endif

#ifndef def_log_tag
#define def_log_tag "oar"
#endif

#define def_log(level, fmt, ...)                                        \
  log_impl(def_log_tag, level, __MODULE__, __FUNCTION__, __LINE__, fmt, \
           ##__VA_ARGS__)

#define fatal(fmt, ...) def_log(ck_log_level_fatal, fmt, ##__VA_ARGS__)
#define error(fmt, ...) def_log(ck_log_level_error, fmt, ##__VA_ARGS__)
#define warn(fmt, ...) def_log(ck_log_level_warn, fmt, ##__VA_ARGS__)
#define warning(fmt, ...) def_log(ck_log_level_warn, fmt, ##__VA_ARGS__)
#define info(fmt, ...) def_log(ck_log_level_info, fmt, ##__VA_ARGS__)
#define debug(fmt, ...) def_log(ck_log_level_debug, fmt, ##__VA_ARGS__)
#define trace(fmt, ...) def_log(ck_log_level_trace, fmt, ##__VA_ARGS__)

void log_impl(const char *tag, log_level_t level, const char *module,
              const char *function, int line, const char *fmt, ...);

#endif  //__LOG_H__