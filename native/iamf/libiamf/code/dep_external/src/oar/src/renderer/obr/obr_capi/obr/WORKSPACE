################################################################################
# This project now manages dependencies with both bazelmod and WORKSPACE,
# with the goal of moving all dependencies to bazelmod.
#
# For more details, please check https://github.com/bazelbuild/bazel/issues/18958.
################################################################################

load(
    "@bazel_tools//tools/build_defs/repo:git.bzl",
    "git_repository",
)

# Google Audio-to-Tactile Lib
git_repository(
    name = "com_google_audio_to_tactile",
    commit = "d3f449fdfd8cfe4a845d0ae244fce2a0bca34a15",
    remote = "https://github.com/google/audio-to-tactile.git",
)

# PFFFT
git_repository(
    name = "pffft",
    build_file = "pffft.BUILD",
    commit = "d7a4c0206a29423478776d6b23a37bbb308f21d5",
    remote = "https://bitbucket.org/jpommier/pffft.git",
)
