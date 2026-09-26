load("@rules_cc//cc:cc_library.bzl", "cc_library")

package(default_visibility = ["//obr:__subpackages__"])

cc_library(
    name = "common",
    hdrs = [
        "ambisonic_utils.h",
        "constants.h",
        "misc_math.h",
        "status_macros.h",
    ],
    deps = [
        "@abseil-cpp//absl/log:absl_check",
        "@eigen",
    ],
)

cc_library(
    name = "test_util",
    testonly = True,
    srcs = [
        "test_util.cc",
    ],
    hdrs = [
        "test_util.h",
    ],
    deps = [
        ":common",
        "//obr/ambisonic_encoder",
        "//obr/audio_buffer",
        "@abseil-cpp//absl/log:absl_check",
        "@com_google_googletest//:gtest",
    ],
)
