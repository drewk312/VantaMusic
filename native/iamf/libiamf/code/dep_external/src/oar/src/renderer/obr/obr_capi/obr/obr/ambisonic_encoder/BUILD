load("@rules_cc//cc:cc_library.bzl", "cc_library")

package(default_visibility = ["//obr:__subpackages__"])

cc_library(
    name = "ambisonic_encoder",
    srcs = [
        "ambisonic_encoder.cc",
    ],
    hdrs = ["ambisonic_encoder.h"],
    visibility = ["//visibility:public"],
    deps = [
        ":associated_legendre_polynomials_generator",
        "//obr/audio_buffer",
        "//obr/common",
        "@abseil-cpp//absl/container:flat_hash_map",
        "@abseil-cpp//absl/log:absl_check",
        "@eigen",
    ],
)

cc_library(
    name = "associated_legendre_polynomials_generator",
    srcs = [
        "associated_legendre_polynomials_generator.cc",
    ],
    hdrs = [
        "associated_legendre_polynomials_generator.h",
    ],
    visibility = ["//visibility:public"],
    deps = [
        "//obr/common",
        "@abseil-cpp//absl/log:absl_check",
    ],
)
