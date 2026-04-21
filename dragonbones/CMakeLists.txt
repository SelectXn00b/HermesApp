cmake_minimum_required(VERSION 3.22.1)

project("dragonbones_native")

# Gather all C++ source files from the dragonbones library
file(GLOB_RECURSE DRAGONBONES_SOURCES
    "cpp/dragonBones/animation/*.cpp"
    "cpp/dragonBones/armature/*.cpp"
    "cpp/dragonBones/core/*.cpp"
    "cpp/dragonBones/event/*.cpp"
    "cpp/dragonBones/factory/*.cpp"
    "cpp/dragonBones/geom/*.cpp"
    "cpp/dragonBones/model/*.cpp"
    "cpp/dragonBones/parser/*.cpp"
    "cpp/opengl/OpenGLSlot.cpp"
    "cpp/opengl/OpenGLFactory.cpp"
)

add_library(
    dragonbones_native
    SHARED
    "cpp/JniBridge.cpp"
    ${DRAGONBONES_SOURCES}
)

# Specify include directories for headers
target_include_directories(dragonbones_native PRIVATE
    "cpp"
    "cpp/thirdParty/stb"
)

# Link against required Android libraries
find_library(log-lib log)
find_library(GLESv2-lib GLESv2)
find_library(android-lib android)

target_link_libraries(
    dragonbones_native
    ${log-lib}
    ${GLESv2-lib}
    ${android-lib}
)

# 添加 16KB 页面大小支持（Android 15+ 要求）
target_link_options(dragonbones_native PRIVATE "-Wl,-z,max-page-size=16384") 