load("@rules_cc//cc:cc_binary.bzl", "cc_binary")
load("@rules_cc//cc:cc_library.bzl", "cc_library")

package(default_visibility = ["//obr:__subpackages__"])

cc_library(
    name = "obr_cli_lib",
    srcs = ["obr_cli_lib.cc"],
    hdrs = ["obr_cli_lib.h"],
    deps = [
        "//obr/audio_buffer",
        "//obr/cli/proto:oba_metadata_cc_proto",
        "//obr/renderer:audio_element_config",
        "//obr/renderer:audio_element_type",
        "//obr/renderer:obr_impl",
        "@abseil-cpp//absl/log:absl_check",
        "@abseil-cpp//absl/log:absl_log",
        "@abseil-cpp//absl/status",
        "@abseil-cpp//absl/strings",
        "@com_google_audio_to_tactile//:dsp",
        "@com_google_protobuf//:protobuf",
    ],
)

cc_binary(
    name = "obr_cli",
    srcs = ["obr_cli.cc"],
    deps = [
        ":obr_cli_lib",
        "//obr/renderer:audio_element_config",
        "//obr/renderer:audio_element_type",
        "@abseil-cpp//absl/flags:flag",
        "@abseil-cpp//absl/flags:parse",
        "@abseil-cpp//absl/flags:usage",
        "@abseil-cpp//absl/log:absl_log",
        "@abseil-cpp//absl/log:flags",
        "@abseil-cpp//absl/status",
        "@abseil-cpp//absl/strings",
    ],
)
