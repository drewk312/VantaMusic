cmake_minimum_required(VERSION 3.22.1)

project(vanta_james_dsp LANGUAGES C CXX)

set(CMAKE_C_FLAGS "${CMAKE_C_FLAGS} -ffunction-sections -fdata-sections -O2 -ftree-vectorize")
set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -ffunction-sections -fdata-sections -O2 -ftree-vectorize")
set(CMAKE_C_VISIBILITY_PRESET hidden)
set(CMAKE_CXX_VISIBILITY_PRESET hidden)
set(CMAKE_VISIBILITY_INLINES_HIDDEN ON)
set(CMAKE_POSITION_INDEPENDENT_CODE ON)

set(JDSP_DIR ${CMAKE_CURRENT_LIST_DIR}/libjamesdsp/Main/libjamesdsp/jni/jamesdsp/jdsp)

set(JDSP_PARENT_DIR ${CMAKE_CURRENT_LIST_DIR}/libjamesdsp/Main/libjamesdsp/jni/jamesdsp)

file(GLOB_RECURSE LIBJAMESDSP_SOURCE_FILES CONFIGURE_DEPENDS
    ${JDSP_DIR}/*.c
    ${JDSP_DIR}/*.h
    ${JDSP_PARENT_DIR}/cpthread.c)

add_library(jamesdsp STATIC
    ${LIBJAMESDSP_SOURCE_FILES}
    EELStdOutExtension.c
    EELStdOutExtension.h)

target_compile_options(jamesdsp PRIVATE -O2 -std=gnu11)
target_include_directories(jamesdsp PUBLIC ${JDSP_DIR} ${JDSP_PARENT_DIR})

find_library(log-lib log)

add_library(vanta_james_dsp SHARED
    vanta_james_dsp_jni.cpp)

target_include_directories(vanta_james_dsp PUBLIC ${JDSP_DIR} ${JDSP_PARENT_DIR})
target_link_libraries(vanta_james_dsp jamesdsp ${log-lib})
target_link_options(vanta_james_dsp PRIVATE "-Wl,-z,max-page-size=16384" "-Wl,--strip-all")
