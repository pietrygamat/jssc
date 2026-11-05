# Cross-compile the macOS natives on Linux with LLVM: clang, ld64.lld and llvm-strip, against an Apple SDK.
# No Apple toolchain (cctools, ld64) is involved, so any LLVM with Mach-O support in lld will do.
#
# Environment:
#   MACOS_SDK                 the SDK directory, e.g. /opt/sdk/MacOSX10.9.sdk
#   MACOS_ARCH                x86_64 or arm64
#   MACOSX_DEPLOYMENT_TARGET  10.9 (x86_64) or 11.0 (arm64)
#   LLVM_SUFFIX               optional suffix of the LLVM tools, e.g. -18 for clang++-18
set(CMAKE_SYSTEM_NAME Darwin)

set(MACOS_SDK "$ENV{MACOS_SDK}")
set(MACOS_ARCH "$ENV{MACOS_ARCH}")
set(LLVM_SUFFIX "$ENV{LLVM_SUFFIX}")
if(NOT IS_DIRECTORY "${MACOS_SDK}/usr/include" OR NOT MACOS_ARCH OR NOT "$ENV{MACOSX_DEPLOYMENT_TARGET}")
    message(FATAL_ERROR "Set MACOS_SDK, MACOS_ARCH (x86_64 or arm64) and MACOSX_DEPLOYMENT_TARGET")
endif()
if(MACOS_ARCH STREQUAL "arm64")
    set(CMAKE_SYSTEM_PROCESSOR arm64)
else()
    set(CMAKE_SYSTEM_PROCESSOR x86_64)
endif()

set(CMAKE_C_COMPILER "clang${LLVM_SUFFIX}")
set(CMAKE_CXX_COMPILER "clang++${LLVM_SUFFIX}")
set(CMAKE_C_COMPILER_TARGET "${MACOS_ARCH}-apple-macos$ENV{MACOSX_DEPLOYMENT_TARGET}")
set(CMAKE_CXX_COMPILER_TARGET "${MACOS_ARCH}-apple-macos$ENV{MACOSX_DEPLOYMENT_TARGET}")
set(CMAKE_EXE_LINKER_FLAGS_INIT "-fuse-ld=lld")
set(CMAKE_SHARED_LINKER_FLAGS_INIT "-fuse-ld=lld")
set(CMAKE_MODULE_LINKER_FLAGS_INIT "-fuse-ld=lld")
set(CMAKE_STRIP "llvm-strip${LLVM_SUFFIX}" CACHE FILEPATH "" FORCE)
set(CMAKE_INSTALL_NAME_TOOL "llvm-install-name-tool${LLVM_SUFFIX}" CACHE FILEPATH "" FORCE)
set(CMAKE_AR "llvm-ar${LLVM_SUFFIX}" CACHE FILEPATH "" FORCE)
set(CMAKE_RANLIB "llvm-ranlib${LLVM_SUFFIX}" CACHE FILEPATH "" FORCE)

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
