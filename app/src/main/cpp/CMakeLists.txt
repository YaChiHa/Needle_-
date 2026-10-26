cmake_minimum_required(VERSION 3.22.1)
project(needle_jni LANGUAGES C CXX)

add_library(needle_engine STATIC IMPORTED)
set_target_properties(needle_engine PROPERTIES
    IMPORTED_LOCATION "${CMAKE_CURRENT_SOURCE_DIR}/../../../../libs/arm64-v8a/libneedle.a"
)

add_library(needle_jni SHARED native-lib.cpp)
target_include_directories(needle_jni PRIVATE "${CMAKE_CURRENT_SOURCE_DIR}/include")
target_compile_features(needle_jni PRIVATE cxx_std_17)
target_link_libraries(needle_jni PRIVATE needle_engine android log dl m c)
