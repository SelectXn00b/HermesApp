cmake_minimum_required(VERSION 3.22.1)

project("fbx_jni")

add_library(
    FbxWrapper
    SHARED
    src/main/cpp/fbx_jni.cpp
    third_party/ufbx/ufbx.c
)

set_target_properties(
    FbxWrapper
    PROPERTIES
    C_STANDARD 99
    C_STANDARD_REQUIRED ON
    CXX_STANDARD 17
    CXX_STANDARD_REQUIRED ON
)

target_include_directories(
    FbxWrapper
    PRIVATE
    third_party/ufbx
)

target_link_libraries(
    FbxWrapper
    log
)

target_link_options(FbxWrapper PRIVATE "-Wl,-z,max-page-size=16384")
