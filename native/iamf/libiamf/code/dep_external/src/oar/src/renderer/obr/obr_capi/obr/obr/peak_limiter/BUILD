load("@rules_cc//cc:cc_library.bzl", "cc_library")

package(default_visibility = ["//obr:__subpackages__"])

cc_library(
    name = "peak_limiter",
    srcs = ["peak_limiter.cc"],
    hdrs = ["peak_limiter.h"],
    visibility = ["//visibility:public"],
    deps = [
        "//obr/audio_buffer",
        "@abseil-cpp//absl/log:absl_check",
    ],
)
