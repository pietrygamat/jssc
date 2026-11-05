# Cross-compile the macOS natives on Linux with osxcross (https://github.com/tpoechtrager/osxcross):
# clang behind the osxcross wrappers, with Apple's ld64 and cctools.
#
# Environment:
#   OSXCROSS_TARGET_DIR       the osxcross directory (bin/ holds the wrappers)
#   OSXCROSS_SDKROOT          the SDK directory, e.g. /sdk/MacOSX10.9.sdk (read by the wrappers too)
#   MACOS_ARCH                x86_64 or arm64
#   MACOSX_DEPLOYMENT_TARGET  10.9 (x86_64) or 11.0 (arm64)
set(CMAKE_SYSTEM_NAME Darwin)

set(OSXCROSS_TARGET_DIR "$ENV{OSXCROSS_TARGET_DIR}")
set(MACOS_SDK "$ENV{OSXCROSS_SDKROOT}")
set(MACOS_ARCH "$ENV{MACOS_ARCH}")
if(NOT IS_DIRECTORY "${OSXCROSS_TARGET_DIR}/bin" OR NOT IS_DIRECTORY "${MACOS_SDK}/usr/include" OR NOT MACOS_ARCH
        OR NOT "$ENV{MACOSX_DEPLOYMENT_TARGET}")
    message(FATAL_ERROR "Set OSXCROSS_TARGET_DIR, OSXCROSS_SDKROOT, MACOS_ARCH (x86_64 or arm64) and MACOSX_DEPLOYMENT_TARGET")
endif()
if(MACOS_ARCH STREQUAL "arm64")
    set(CMAKE_SYSTEM_PROCESSOR arm64)
else()
    set(CMAKE_SYSTEM_PROCESSOR x86_64)
endif()

# osxcross names its tools after the Darwin version of the SDK it was built with, e.g. x86_64-apple-darwin20.1-clang++.
# OSXCROSS_SDKROOT, not that name, decides the SDK they use.
file(GLOB OSXCROSS_CXX "${OSXCROSS_TARGET_DIR}/bin/${MACOS_ARCH}-apple-darwin*-clang++")
list(LENGTH OSXCROSS_CXX OSXCROSS_CXX_COUNT)
if(NOT OSXCROSS_CXX_COUNT EQUAL 1)
    message(FATAL_ERROR "Expected one ${MACOS_ARCH} clang++ in ${OSXCROSS_TARGET_DIR}/bin, found: ${OSXCROSS_CXX}")
endif()
string(REGEX REPLACE "-clang\\+\\+$" "" OSXCROSS_HOST "${OSXCROSS_CXX}")

set(CMAKE_C_COMPILER "${OSXCROSS_HOST}-clang")
set(CMAKE_CXX_COMPILER "${OSXCROSS_HOST}-clang++")
set(CMAKE_STRIP "${OSXCROSS_HOST}-strip" CACHE FILEPATH "" FORCE)
set(CMAKE_INSTALL_NAME_TOOL "${OSXCROSS_HOST}-install_name_tool" CACHE FILEPATH "" FORCE)
set(CMAKE_AR "${OSXCROSS_HOST}-ar" CACHE FILEPATH "" FORCE)
set(CMAKE_RANLIB "${OSXCROSS_HOST}-ranlib" CACHE FILEPATH "" FORCE)

set(CMAKE_OSX_SYSROOT "${MACOS_SDK}" CACHE PATH "" FORCE)
set(CMAKE_OSX_ARCHITECTURES "${MACOS_ARCH}" CACHE STRING "" FORCE)
set(CMAKE_OSX_DEPLOYMENT_TARGET "$ENV{MACOSX_DEPLOYMENT_TARGET}" CACHE STRING "" FORCE)
# Install name @rpath/libjssc.dylib, as on a Mac. CMake decides on @rpath from the running macOS version (sw_vers),
# which a Linux host doesn't have, and would fall back to the absolute path of the build directory.
set(CMAKE_INSTALL_NAME_DIR "@rpath")
set(CMAKE_BUILD_WITH_INSTALL_NAME_DIR ON)

# Headers and frameworks (IOKit) come from the SDK only; JNI headers come from the build JDK
set(CMAKE_FIND_ROOT_PATH "${MACOS_SDK}")
set(CMAKE_FIND_ROOT_PATH_MODE_PROGRAM NEVER)
set(CMAKE_FIND_ROOT_PATH_MODE_LIBRARY ONLY)
set(CMAKE_FIND_ROOT_PATH_MODE_INCLUDE BOTH)
set(CMAKE_FIND_ROOT_PATH_MODE_PACKAGE BOTH)
