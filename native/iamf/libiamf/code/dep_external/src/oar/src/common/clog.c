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


#include "clog.h"

#include <stdlib.h>
#include <time.h>
#ifdef __ANDROID__
#include <android/log.h>
#endif

#ifdef __dbg__
static log_level_t log_level = ck_log_level_trace;
#else
static log_level_t log_level = ck_log_level_warn;
#endif
static const char* log_level_str(log_level_t level) {
  switch (level) {
    case ck_log_level_fatal:
      return "Fatal";
    case ck_log_level_error:
      return "Error";
    case ck_log_level_warn:
      return "Warn ";
    case ck_log_level_info:
      return "Info ";
    case ck_log_level_debug:
      return "Debug";
    case ck_log_level_trace:
      return "Trace";
    default:
      return "-----";
  }
}

void log_impl(const char* tag, log_level_t level, const char* module,
              const char* function, int line, const char* fmt, ...) {
  if ((int)level <= log_level) {
    va_list args;
    struct timespec ts;
    clock_gettime(CLOCK_REALTIME, &ts);
    struct tm* tm_info = localtime(&ts.tv_sec);
    char time[32], buffer[8192] = {0};
    strftime(time, sizeof(time), "%Y-%m-%d %H:%M:%S", tm_info);
    va_start(args, fmt);
    vsnprintf(buffer, sizeof(buffer), fmt, args);
#ifdef __ANDROID__
    __android_log_print(ANDROID_LOG_WARN, "VANTA_IAMF", "%s: %s", function, buffer);
#else
    fprintf(stderr, "[%s.%03ld] [%s] [%s] [%s:%s:%d]: %s\n", time,
            ts.tv_nsec / 1000, log_level_str(level), tag, module, function,
            line, buffer);
#endif
    va_end(args);
  }
}

void set_log_level(log_level_t level) { log_level = level; }
