# [internal] load cc_proto_library.bzl
load("@com_google_protobuf//bazel:proto_library.bzl", "proto_library")

package(default_visibility = ["//obr/cli:__subpackages__"])

# Proto for obr_cli OBA input metadata.
proto_library(
    name = "oba_metadata_proto",
    srcs = ["oba_metadata.proto"],
)

cc_proto_library(
    name = "oba_metadata_cc_proto",
    deps = [":oba_metadata_proto"],
)
