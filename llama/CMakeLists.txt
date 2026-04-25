cmake_minimum_required(VERSION 3.22.1)

project("llama_jni")

set(LLAMA_CPP_DIR "${CMAKE_CURRENT_SOURCE_DIR}/third_party/llama.cpp")
set(LLAMA_CPP_DIR_FALLBACK "${CMAKE_CURRENT_SOURCE_DIR}/../third_party/llama.cpp")

if (EXISTS "${LLAMA_CPP_DIR}/CMakeLists.txt")
    set(OPERIT_LLAMA_CPP_DIR "${LLAMA_CPP_DIR}")
elseif (EXISTS "${LLAMA_CPP_DIR_FALLBACK}/CMakeLists.txt")
    set(OPERIT_LLAMA_CPP_DIR "${LLAMA_CPP_DIR_FALLBACK}")
endif()

if (DEFINED OPERIT_LLAMA_CPP_DIR)
    # Reduce build scope when embedded
    set(LLAMA_BUILD_COMMON ON CACHE BOOL "" FORCE)
    set(LLAMA_BUILD_TESTS OFF CACHE BOOL "" FORCE)
    set(LLAMA_BUILD_TOOLS OFF CACHE BOOL "" FORCE)
    set(LLAMA_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
    set(LLAMA_BUILD_SERVER OFF CACHE BOOL "" FORCE)

    add_subdirectory("${OPERIT_LLAMA_CPP_DIR}" "${CMAKE_BINARY_DIR}/llama.cpp")
endif()

add_library(
    LlamaWrapper
    SHARED
    src/main/cpp/llama_jni_stub.cpp
)

if (DEFINED OPERIT_LLAMA_CPP_DIR)
    target_compile_definitions(LlamaWrapper PRIVATE OPERIT_HAS_LLAMA_CPP=1)
    target_include_directories(LlamaWrapper PRIVATE "${OPERIT_LLAMA_CPP_DIR}")
    target_include_directories(LlamaWrapper PRIVATE "${OPERIT_LLAMA_CPP_DIR}/common")
    target_include_directories(LlamaWrapper PRIVATE "${OPERIT_LLAMA_CPP_DIR}/include")
    target_include_directories(LlamaWrapper PRIVATE "${OPERIT_LLAMA_CPP_DIR}/ggml/include")
    target_link_libraries(LlamaWrapper llama common)
else()
    target_compile_definitions(LlamaWrapper PRIVATE OPERIT_HAS_LLAMA_CPP=0)
endif()

target_link_libraries(
    LlamaWrapper
    android
    log
)

# 16KB page size support (Android 15+ requirement)
target_link_options(LlamaWrapper PRIVATE "-Wl,-z,max-page-size=16384")
