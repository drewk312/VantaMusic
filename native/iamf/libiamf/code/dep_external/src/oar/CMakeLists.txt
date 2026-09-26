cmake_minimum_required(VERSION 3.28)

set(PROJECT_N oar)
project(${PROJECT_N} VERSION 0.9.0)

set(CMAKE_C_STANDARD 11)
set(CMAKE_C_STANDARD_REQUIRED True)
set(CMAKE_C_FLAGS "${CMAKE_C_FLAGS} -O2")

option(OAR_BUILD_SHARED_LIBRARY "Build shared library" ON)
option(OAR_ENABLE_BINAURALIZER "Enable binaural renderer" ON)
option(OAR_AUDIO_SIGNAL_DEBUG
        "saving intermediate audio signal during processing" OFF)

option(OLR_ENABLE_F64 "Enable double precision support for olr" OFF)
option(OAR_BUILD_EXAMPLES "Build examples" OFF)

if(OLR_ENABLE_F64)
    add_definitions(-DENABLE_F64)
    message(STATUS "Double precision (F64) support enabled")
else()
    message(STATUS "Single precision (F32) support enabled")
endif()

if (OAR_AUDIO_SIGNAL_DEBUG)
    message(STATUS "Audio signal debug.")
    add_definitions(-D__as_dbg__)
endif()

if(OAR_ENABLE_BINAURALIZER)
    message(STATUS "Enable binaural renderer.")
    add_definitions(-D__binauralizer__)
    add_subdirectory(src/renderer/obr/obr_capi)
endif()

set(OAR_SRC
    src/common/cvector.c
    src/common/cqueue.c
    src/common/clog.c
    src/common/carray.c
    src/common/cdeque.c
    src/common/chashmap.c
    src/utility/animation.c
    src/renderer/audio_element_renderer.c
    src/renderer/audio_elements_renderer.c
    src/renderer/audio_renderer_base.c
    src/renderer/renderer_library_manager.c
    src/renderer/renderer_library_utils.c
    src/renderer/ear/arch/arm/matrix_render_arm.c
    src/renderer/ear/arch/x86/matrix_render_x86.c
    src/renderer/ear/matrix_render.c
    src/renderer/ear/h2m_rdr.c
    src/renderer/ear/m2m_rdr.c
    src/renderer/ear/ear.c
    src/renderer/downmix/downmix_renderer.c
    src/renderer/downmix/downmix.c
    src/renderer/olr/object_audio_renderer/object_based_audio_renderer.c
    src/renderer/olr/object_audio_renderer/layout.c
    src/renderer/olr/object_audio_renderer/utils.c
    src/renderer/olr/object_audio_renderer/numc.c
    src/renderer/olr/object_audio_renderer/gain_calculator/gain_calculator.c
    src/renderer/olr/object_audio_renderer/gain_calculator/custom_gain_calculator.c
    src/renderer/olr/object_audio_renderer/gain_calculator/convex_hull.c
    src/renderer/olr/object_audio_renderer/gain_calculator/dbap_panner_2d.c
    src/renderer/olr/object_audio_renderer/gain_calculator/vbap_panner_2d.c
    src/renderer/olr/object_audio_renderer/gain_calculator/vbap_region.c
    src/renderer/olr/object_audio_renderer/gain_calculator/vog.c
    src/renderer/olr/object_audio_renderer/block_processor/block_processing_channel.c
    src/renderer/olr/object_audio_renderer/block_processor/interpret_object_metadata.c
    src/renderer/olr/object_audio_renderer/block_processor/processing_block.c
    src/renderer/olr/object_audio_renderer/renderer.c
    src/renderer/olr/olr.c
    src/limiter/oar_limiter.c
    src/oar.c
    src/oar_utils.c
)

if(OAR_ENABLE_BINAURALIZER)
    list(APPEND OAR_SRC src/renderer/obr/obr.c)
endif()

if("${BUILD_TYPE}" STREQUAL "debug" AND OAR_AUDIO_SIGNAL_DEBUG)
    list(APPEND OAR_SRC src/common/dep_wavwriter.c)
endif()

include_directories(
    include
    src
    src/common
    src/utility
    src/renderer
    src/renderer/ear
    src/renderer/olr
    src/renderer/olr/object_audio_renderer
    )

if(OAR_BUILD_SHARED_LIBRARY)
  add_library(${PROJECT_N} SHARED ${OAR_SRC})
else()
  add_library(${PROJECT_N} STATIC ${OAR_SRC})
endif()

if(OAR_ENABLE_BINAURALIZER)
    target_link_libraries(${PROJECT_N} PRIVATE obr_capi)
endif()

if(OAR_BUILD_SHARED_LIBRARY)
    install(TARGETS ${PROJECT_N} LIBRARY DESTINATION ${CMAKE_INSTALL_PREFIX}/lib)
else()
    install(TARGETS ${PROJECT_N} ARCHIVE DESTINATION ${CMAKE_INSTALL_PREFIX}/lib)
endif()
install(DIRECTORY include/ DESTINATION include/oar)

if(OAR_BUILD_EXAMPLES)
    add_subdirectory(tests/examples)
endif()
