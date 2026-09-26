cmake_minimum_required(VERSION 3.28)

project (obr_capi)

set(CMAKE_CXX_STANDARD 20)
set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS}  -O2 -std=c++20")
set(CMAKE_POSITION_INDEPENDENT_CODE ON)

add_subdirectory(obr)
add_library(obr_capi STATIC obr_capi.cpp)

target_link_libraries(obr_capi PRIVATE
        obr
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
