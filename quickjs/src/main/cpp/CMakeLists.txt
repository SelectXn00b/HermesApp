cmake_minimum_required(VERSION 3.22.1)

project(quickjsjni)

set(QUICKJS_DIR "${CMAKE_CURRENT_SOURCE_DIR}/../../../thirdparty/quickjs")
file(READ "${QUICKJS_DIR}/VERSION" QUICKJS_VERSION_RAW)
string(STRIP "${QUICKJS_VERSION_RAW}" QUICKJS_VERSION)

add_library(
    quickjsjni
    SHARED
    quickjs_jni.cpp
    ${QUICKJS_DIR}/cutils.c
    ${QUICKJS_DIR}/dtoa.c
    ${QUICKJS_DIR}/libregexp.c
    ${QUICKJS_DIR}/libunicode.c
    ${QUICKJS_DIR}/quickjs.c
)

target_compile_definitions(
    quickjsjni
    PRIVATE
    _GNU_SOURCE
    CONFIG_VERSION="${QUICKJS_VERSION}"
    NDEBUG
)

target_compile_options(
    quickjsjni
    PRIVATE
    # Keep QuickJS optimized on latency-sensitive paths even in debug app builds.
    -O3
    -fwrapv
    -funsigned-char
    -Wall
    -Wextra
    -Wno-sign-compare
    -Wno-missing-field-initializers
    -Wno-unused-parameter
    -Wwrite-strings
    -Wchar-subscripts
)

set_target_properties(
    quickjsjni
    PROPERTIES
    C_STANDARD 11
    C_STANDARD_REQUIRED ON
    CXX_STANDARD 17
    CXX_STANDARD_REQUIRED ON
)

target_link_libraries(
    quickjsjni
    android
    log
    m
)

target_link_options(quickjsjni PRIVATE "-Wl,-z,max-page-size=16384")
