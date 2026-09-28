# Native speech recognition for the recitation checker: whisper.cpp (git
# submodule, pinned to v1.7.6) plus a small JNI bridge. Mirrors whisper.cpp's
# own examples/whisper.android setup.
cmake_minimum_required(VERSION 3.22.1)
project(hourlybuzz_whisper C CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_POSITION_INDEPENDENT_CODE ON)
set(WHISPER_DIR ${CMAKE_CURRENT_SOURCE_DIR}/whisper.cpp)

set(GGML_NATIVE OFF CACHE BOOL "" FORCE)
set(GGML_OPENMP OFF CACHE BOOL "" FORCE)
set(BUILD_SHARED_LIBS OFF CACHE BOOL "" FORCE)
if (ANDROID_ABI STREQUAL "arm64-v8a")
    # Galaxy Watch 7 (Exynos W1000: Cortex-A78 + A55) supports fp16 and dot product.
    set(GGML_CPU_ARM_ARCH "armv8.2-a+fp16+dotprod" CACHE STRING "" FORCE)
endif ()

include(FetchContent)
FetchContent_Declare(ggml SOURCE_DIR ${WHISPER_DIR}/ggml)
FetchContent_MakeAvailable(ggml)

add_library(hourlybuzz_whisper SHARED ${WHISPER_DIR}/src/whisper.cpp whisper_jni.c)
target_include_directories(hourlybuzz_whisper PRIVATE
    ${WHISPER_DIR}/include ${WHISPER_DIR}/src ${WHISPER_DIR}/ggml/include)
target_compile_definitions(hourlybuzz_whisper PUBLIC GGML_USE_CPU)
target_compile_options(hourlybuzz_whisper PRIVATE -O3)
if (ANDROID)
    find_library(LOG_LIB log)
    target_link_libraries(hourlybuzz_whisper ggml ${LOG_LIB} android)
else ()
    target_link_libraries(hourlybuzz_whisper ggml)
endif ()
