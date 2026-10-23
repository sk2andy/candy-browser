cmake_minimum_required(VERSION 3.22.1)
project(candy_pocket LANGUAGES C CXX)
set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_POSITION_INDEPENDENT_CODE ON)

include(FetchContent)
set(SPM_ENABLE_SHARED OFF CACHE BOOL "" FORCE)
set(SPM_ENABLE_TCMALLOC OFF CACHE BOOL "" FORCE)
set(SPM_BUILD_TEST OFF CACHE BOOL "" FORCE)
FetchContent_Declare(sentencepiece
    URL https://codeload.github.com/google/sentencepiece/tar.gz/refs/tags/v0.2.1
    URL_HASH SHA256=c1a59e9259c9653ad0ade653dadff074cd31f0a6ff2a11316f67bee4189a8f1b)
FetchContent_Declare(dr_libs
    URL https://codeload.github.com/mackron/dr_libs/tar.gz/50bb723e6a459dbb781e26cefee4fd9ca6714d6a
    URL_HASH SHA256=ebf2a141056247b4c022c513adcb162ac61c43ba3fc2f7b60153db819b5b9bad)
FetchContent_MakeAvailable(sentencepiece dr_libs)
target_compile_options(sentencepiece-static PRIVATE -w)

add_library(onnxruntime SHARED IMPORTED)
set_target_properties(onnxruntime PROPERTIES
    IMPORTED_LOCATION "${ONNX_RUNTIME_ROOT}/jni/${ANDROID_ABI}/libonnxruntime.so")
add_library(candy_pocket SHARED pocket_jni.cpp)
target_include_directories(candy_pocket PRIVATE
    "${ONNX_RUNTIME_ROOT}/headers"
    "${sentencepiece_SOURCE_DIR}/src"
    "${dr_libs_SOURCE_DIR}")
target_compile_options(candy_pocket PRIVATE
    "-ffile-prefix-map=${CMAKE_CURRENT_SOURCE_DIR}=."
    "-fdebug-prefix-map=${CMAKE_CURRENT_SOURCE_DIR}=.")
target_link_libraries(candy_pocket PRIVATE onnxruntime sentencepiece-static log)
