#ifndef __CONVEX_HULL_H__
#define __CONVEX_HULL_H__

#include "oar_config.h"

// Define the Point structure
struct Point {
  auto_float_t x, y;
};

int convexHull(struct Point in[], int n, struct Point out[], int *m);

#endif  // __CONVEX_HULL_H__
