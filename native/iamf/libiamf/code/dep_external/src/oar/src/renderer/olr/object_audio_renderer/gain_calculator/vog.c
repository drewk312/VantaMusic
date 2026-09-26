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


#include <stdlib.h>

#include "gain_calculator.h"

static void _vog_destroy(gain_calculator_t *self) {
  if (self) free(self);
}
static int _vog_calculate_gain(gain_calculator_t *gc, auto_float_t azimuth,
                               auto_float_t elevation, auto_float_t distance,
                               auto_float_t *gains, int n) {
  *gains = 1.0f;
  return 0;
}
gain_calculator_t *vog_create(void) {
  gain_calculator_t *self = calloc(1, sizeof(gain_calculator_t));
  if (!self) return 0;
  self->destroy = def_gain_calculator_destroy(_vog_destroy);
  self->calculate_gains =
      def_gain_calculator_calculate_gains(_vog_calculate_gain);
  return self;
}