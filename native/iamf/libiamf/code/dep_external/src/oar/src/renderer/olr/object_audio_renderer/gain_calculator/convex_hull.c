/*
 * [GeeksforGeeks]
 * Source: https://www.geeksforgeeks.org/c/convex-hull-algorithm-in-c/
 */

// C program to implement Convex Hull using Graham Scan

#include "convex_hull.h"

#include <stdio.h>
#include <stdlib.h>
#include <string.h>

// Global point needed for sorting points with reference to the first point
struct Point p0;

// A utility function to find the next-to-top element in a stack
struct Point nextToTop(struct Point *stack, int *top) {
  (*top)--;
  struct Point res = stack[*top];
  (*top)++;
  return res;
}

// A utility function to swap two points
void swap(struct Point *p1, struct Point *p2) {
  struct Point temp = *p1;
  *p1 = *p2;
  *p2 = temp;
}

// A utility function to return the square of the distance between p1 and p2
auto_float_t distSq(struct Point p1, struct Point p2) {
  return (p1.x - p2.x) * (p1.x - p2.x) + (p1.y - p2.y) * (p1.y - p2.y);
}

// To find the orientation of ordered triplet (p, q, r).
// The function returns the following values:
// 0 --> p, q, and r are collinear
// 1 --> Clockwise
// 2 --> Counterclockwise
int orientation(struct Point p, struct Point q, struct Point r) {
  auto_float_t val = (q.y - p.y) * (r.x - q.x) - (q.x - p.x) * (r.y - q.y);

  if (val == 0.0f) return 0;  // collinear
  return (val > 0) ? 1 : 2;   // clock or counterclockwise
}

// A function used by qsort() to sort an array of points with respect to the
// first point
int compare(const void *vp1, const void *vp2) {
  struct Point *p1 = (struct Point *)vp1;
  struct Point *p2 = (struct Point *)vp2;

  int o = orientation(p0, *p1, *p2);
  if (o == 0) return (distSq(p0, *p2) >= distSq(p0, *p1)) ? -1 : 1;

  return (o == 2) ? -1 : 1;
}

// Prints convex hull of a set of n points.
int convexHull(struct Point in[], int n, struct Point out[], int *k) {
  auto_float_t ymin = in[0].y;
  int min = 0;
  struct Point *clone = 0;
  int m = 1;

  clone = (struct Point *)malloc(n * sizeof(struct Point));
  if (!clone) return -12;

  for (int i = 0; i < n; i++) {
    clone[i] = in[i];
  }

  for (int i = 1; i < n; i++) {
    auto_float_t y = in[i].y;
    if ((y < ymin) || (ymin == y && in[i].x < in[min].x))
      ymin = in[i].y, min = i;
  }

  swap(&clone[0], &clone[min]);

  p0 = clone[0];
  qsort(&clone[1], n - 1, sizeof(struct Point), compare);

  for (int i = 1; i < n; i++) {
    while (i < n - 1 && orientation(p0, clone[i], clone[i + 1]) == 0) i++;
    clone[m] = clone[i];
    m++;
  }

  if (m < 3) return -22;

  struct Point *stack = (struct Point *)malloc(m * sizeof(struct Point));
  int top = -1;
  if (!stack) return -12;

  stack[++top] = clone[0];
  stack[++top] = clone[1];
  stack[++top] = clone[2];

  for (int i = 3; i < m; i++) {
    while (top > 0 &&
           orientation(nextToTop(stack, &top), stack[top], clone[i]) != 2)
      top--;
    stack[++top] = clone[i];
  }

  *k = top + 1;

  // printf("The points in convex hull are: \n");

  while (top >= 0) {
    out[top] = stack[top];
    // printf("(%" def_f8g ", %" def_f8g ")\n", out[m].x, out[m].y);
    --top;
  }

  free(stack);
  free(clone);

  return 0;
}
