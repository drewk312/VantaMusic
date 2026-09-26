cmake_minimum_required(VERSION 3.28)
project(obr_cmake)

set(CMAKE_CXX_STANDARD 20)

if (WIN32)
    add_definitions(-D_USE_MATH_DEFINES)
    add_definitions(-DNOMINMAX)
    # Disable type conversion warnings.
    add_definitions(/wd4244)
    add_definitions(/wd4305)
    add_definitions(/wd4722)
endif ()

include(FetchContent)

# Fetch Abseil library (without tests to avoid build issues).
set(ABSL_BUILD_TESTING OFF CACHE BOOL "Disable abseil tests")
FetchContent_Declare(
        absl
        GIT_REPOSITORY https://github.com/abseil/abseil-cpp.git
        GIT_TAG 20250512.1
        GIT_SHALLOW TRUE
)
FetchContent_MakeAvailable(absl)

# Fetch Eigen library.
FetchContent_Declare(
        eigen
        GIT_REPOSITORY https://gitlab.com/libeigen/eigen.git
        GIT_TAG 3147391d946bb4b6c68edd901f2add6ac1f31f8c
)
FetchContent_MakeAvailable(eigen)

# Fetch PFFFT.
FetchContent_Declare(
        pffft
        GIT_REPOSITORY https://bitbucket.org/jpommier/pffft.git
        GIT_TAG 180c2d62717c0150d8aebd165fce19ee4e5f86f9
)
FetchContent_MakeAvailable(pffft)

# Manually add PFFFT library.
add_library(pffft STATIC
        ${pffft_SOURCE_DIR}/pffft.c
)

# Manually list all source files.
set(SourceFiles
        obr/ambisonic_binaural_decoder/ambisonic_binaural_decoder.cc
        obr/ambisonic_binaural_decoder/ambisonic_binaural_decoder.h
        obr/ambisonic_binaural_decoder/dsp_utils.cc
        obr/ambisonic_binaural_decoder/dsp_utils.h
        obr/ambisonic_binaural_decoder/fft_manager.cc
        obr/ambisonic_binaural_decoder/fft_manager.h
        obr/ambisonic_binaural_decoder/partitioned_fft_filter.cc
        obr/ambisonic_binaural_decoder/partitioned_fft_filter.h
        obr/ambisonic_binaural_decoder/planar_interleaved_conversion.cc
        obr/ambisonic_binaural_decoder/planar_interleaved_conversion.h
        obr/ambisonic_binaural_decoder/resampler.cc
        obr/ambisonic_binaural_decoder/resampler.h
        obr/ambisonic_binaural_decoder/sample_type_conversion.cc
        obr/ambisonic_binaural_decoder/sample_type_conversion.h
        obr/ambisonic_binaural_decoder/sh_hrir_creator.cc
        obr/ambisonic_binaural_decoder/sh_hrir_creator.h
        obr/ambisonic_binaural_decoder/wav.cc
        obr/ambisonic_binaural_decoder/wav.h
        obr/ambisonic_binaural_decoder/wav_reader.cc
        obr/ambisonic_binaural_decoder/wav_reader.h
        obr/ambisonic_binaural_decoder/binaural_filters/binaural_filters_1_oa_direct_l.cc
        obr/ambisonic_binaural_decoder/binaural_filters/binaural_filters_1_oa_direct_l.h
        obr/ambisonic_binaural_decoder/binaural_filters/binaural_filters_1_oa_direct_r.cc
        obr/ambisonic_binaural_decoder/binaural_filters/binaural_filters_1_oa_direct_r.h
        obr/ambisonic_binaural_decoder/binaural_filters/binaural_filters_1_oa_ambient_l.cc
        obr/ambisonic_binaural_decoder/binaural_filters/binaural_filters_1_oa_ambient_l.h
        obr/ambisonic_binaural_decoder/binaural_filters/binaural_filters_1_oa_ambient_r.cc
        obr/ambisonic_binaural_decoder/binaural_filters/binaural_filters_1_oa_ambient_r.h
        obr/ambisonic_binaural_decoder/binaural_filters/binaural_filters_1_oa_reverberant_l.cc
        obr/ambisonic_binaural_decoder/binaural_filters/binaural_filters_1_oa_reverberant_l.h
        obr/ambisonic_binaural_decoder/binaural_filters/binaural_filters_1_oa_reverberant_r.cc
        obr/ambisonic_binaural_decoder/binaural_filters/binaural_filters_1_oa_reverberant_r.h
        obr/ambisonic_binaural_decoder/binaural_filters/binaural_filters_2_oa_direct_l.cc
        obr/ambisonic_binaural_decoder/binaural_filters/binaural_filters_2_oa_direct_l.h
        obr/ambisonic_binaural_decoder/binaural_filters/binaural_filters_2_oa_direct_r.cc
        obr/ambisonic_binaural_decoder/binaural_filters/binaural_filters_2_oa_direct_r.h
        obr/ambisonic_binaural_decoder/binaural_filters/binaural_filters_2_oa_ambient_l.cc
        obr/ambisonic_binaural_decoder/binaural_filters/binaural_filters_2_oa_ambient_l.h
        obr/ambisonic_binaural_decoder/binaural_filters/binaural_filters_2_oa_ambient_r.cc
        obr/ambisonic_binaural_decoder/binaural_filters/binaural_filters_2_oa_ambient_r.h
        obr/ambisonic_binaural_decoder/binaural_filters/binaural_filters_2_oa_reverberant_l.cc
        obr/ambisonic_binaural_decoder/binaural_filters/binaural_filters_2_oa_reverberant_l.h
        obr/ambisonic_binaural_decoder/binaural_filters/binaural_filters_2_oa_reverberant_r.cc
        obr/ambisonic_binaural_decoder/binaural_filters/binaural_filters_2_oa_reverberant_r.h
        obr/ambisonic_binaural_decoder/binaural_filters/binaural_filters_3_oa_direct_l.cc
        obr/ambisonic_binaural_decoder/binaural_filters/binaural_filters_3_oa_direct_l.h
        obr/ambisonic_binaural_decoder/binaural_filters/binaural_filters_3_oa_direct_r.cc
        obr/ambisonic_binaural_decoder/binaural_filters/binaural_filters_3_oa_direct_r.h
        obr/ambisonic_binaural_decoder/binaural_filters/binaural_filters_3_oa_ambient_l.cc
        obr/ambisonic_binaural_decoder/binaural_filters/binaural_filters_3_oa_ambient_l.h
        obr/ambisonic_binaural_decoder/binaural_filters/binaural_filters_3_oa_ambient_r.cc
        obr/ambisonic_binaural_decoder/binaural_filters/binaural_filters_3_oa_ambient_r.h
        obr/ambisonic_binaural_decoder/binaural_filters/binaural_filters_3_oa_reverberant_l.cc
        obr/ambisonic_binaural_decoder/binaural_filters/binaural_filters_3_oa_reverberant_l.h
        obr/ambisonic_binaural_decoder/binaural_filters/binaural_filters_3_oa_reverberant_r.cc
        obr/ambisonic_binaural_decoder/binaural_filters/binaural_filters_3_oa_reverberant_r.h
        obr/ambisonic_binaural_decoder/binaural_filters/binaural_filters_4_oa_direct_l.cc
        obr/ambisonic_binaural_decoder/binaural_filters/binaural_filters_4_oa_direct_l.h
        obr/ambisonic_binaural_decoder/binaural_filters/binaural_filters_4_oa_direct_r.cc
        obr/ambisonic_binaural_decoder/binaural_filters/binaural_filters_4_oa_direct_r.h
        obr/ambisonic_binaural_decoder/binaural_filters/binaural_filters_4_oa_ambient_l.cc
        obr/ambisonic_binaural_decoder/binaural_filters/binaural_filters_4_oa_ambient_l.h
        obr/ambisonic_binaural_decoder/binaural_filters/binaural_filters_4_oa_ambient_r.cc
        obr/ambisonic_binaural_decoder/binaural_filters/binaural_filters_4_oa_ambient_r.h
        obr/ambisonic_binaural_decoder/binaural_filters/binaural_filters_4_oa_reverberant_l.cc
        obr/ambisonic_binaural_decoder/binaural_filters/binaural_filters_4_oa_reverberant_l.h
        obr/ambisonic_binaural_decoder/binaural_filters/binaural_filters_4_oa_reverberant_r.cc
        obr/ambisonic_binaural_decoder/binaural_filters/binaural_filters_4_oa_reverberant_r.h
        obr/ambisonic_binaural_decoder/binaural_filters/binaural_filters_wrapper.cc
        obr/ambisonic_binaural_decoder/binaural_filters/binaural_filters_wrapper.h
        obr/ambisonic_encoder/ambisonic_encoder.cc
        obr/ambisonic_encoder/ambisonic_encoder.h
        obr/ambisonic_encoder/associated_legendre_polynomials_generator.cc
        obr/ambisonic_encoder/associated_legendre_polynomials_generator.h
        obr/ambisonic_rotator/ambisonic_rotator.cc
        obr/ambisonic_rotator/ambisonic_rotator.h
        obr/audio_buffer/audio_buffer.cc
        obr/audio_buffer/audio_buffer.h
        obr/audio_buffer/aligned_allocator.h
        obr/audio_buffer/channel_view.cc
        obr/audio_buffer/channel_view.h
        obr/audio_buffer/simd_macros.h
        obr/audio_buffer/simd_utils.cc
        obr/audio_buffer/simd_utils.h
        obr/common/ambisonic_utils.h
        obr/common/constants.h
        obr/common/status_macros.h
        obr/common/misc_math.h
        obr/peak_limiter/peak_limiter.cc
        obr/peak_limiter/peak_limiter.h
        obr/renderer/audio_element_config.cc
        obr/renderer/audio_element_config.h
        obr/renderer/audio_element_table_formatter.cc
        obr/renderer/audio_element_table_formatter.h
        obr/renderer/audio_element_type.h
        obr/renderer/input_channel_config.h
        obr/renderer/processing_group.cc
        obr/renderer/processing_group.h
        obr/renderer/loudspeaker_layouts.h
        obr/renderer/obr_impl.cc
        obr/renderer/obr_impl.h
)

# Define obr library target.
add_library(obr STATIC)

# Add the collected source files to the library target.
target_sources(obr PRIVATE ${SourceFiles})

# Set include directories.
target_include_directories(obr PUBLIC
        ${CMAKE_CURRENT_SOURCE_DIR}
        ${eigen_SOURCE_DIR}
        ${absl_SOURCE_DIR}
        ${pffft_SOURCE_DIR}
)

# Link libraries.
target_link_libraries(obr PRIVATE
        pffft
        eigen
        absl::base
        absl::die_if_null
        absl::flat_hash_map
        absl::log
        absl::log_internal_message
        absl::log_internal_check_op
        absl::status
        absl::statusor
        absl::strings
)

