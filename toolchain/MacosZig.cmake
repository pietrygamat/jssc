# Cross-compile the macOS natives on Linux with zig (zig cc: clang and zig's Mach-O linker) against an Apple SDK.
#
# By default zig builds against the macOS headers and library stubs it ships (a recent SDK) and links a
# libc++ of its own, statically. To build like Xcode does, this uses the given SDK for headers and
# libraries (a zig libc file, ZIG_LIBC) and links the SDK's libc++, i.e. /usr/lib/libc++.1.dylib.
# zig reads library stubs as .tbd only: an SDK that has Mach-O stubs (10.9) needs converting first.
#
# Environment:
#   ZIG                       the zig executable (default: zig)
#   MACOS_SDK                 the SDK directory, e.g. /sdk/MacOSX10.9.sdk
#   MACOS_ARCH                x86_64 or arm64
#   MACOSX_DEPLOYMENT_TARGET  10.9 (x86_64) or 11.0 (arm64)
set(CMAKE_SYSTEM_NAME Darwin)

set(MACOS_SDK "$ENV{MACOS_SDK}")
set(MACOS_ARCH "$ENV{MACOS_ARCH}")
if(NOT IS_DIRECTORY "${MACOS_SDK}/usr/include" OR NOT MACOS_ARCH OR NOT "$ENV{MACOSX_DEPLOYMENT_TARGET}")
    message(FATAL_ERROR "Set MACOS_SDK, MACOS_ARCH (x86_64 or arm64) and MACOSX_DEPLOYMENT_TARGET")
endif()
if(MACOS_ARCH STREQUAL "arm64")
    set(CMAKE_SYSTEM_PROCESSOR arm64)
    set(ZIG_TARGET "aarch64-macos.$ENV{MACOSX_DEPLOYMENT_TARGET}")
else()
    set(CMAKE_SYSTEM_PROCESSOR x86_64)
    set(ZIG_TARGET "x86_64-macos.$ENV{MACOSX_DEPLOYMENT_TARGET}")
endif()
set(ZIG "$ENV{ZIG}")
if(NOT ZIG)
    set(ZIG zig)
endif()

# zig cc for both languages: zig c++ would link zig's own libc++
set(CMAKE_C_COMPILER "${ZIG}")
set(CMAKE_C_COMPILER_ARG1 cc)
set(CMAKE_CXX_COMPILER "${ZIG}")
set(CMAKE_CXX_COMPILER_ARG1 cc)
# -fstack-protector is Apple clang's default, and zig's isn't
set(CMAKE_C_FLAGS_INIT "-target ${ZIG_TARGET} -fstack-protector")
set(CMAKE_CXX_FLAGS_INIT "-target ${ZIG_TARGET} -fstack-protector")
set(CMAKE_SHARED_LINKER_FLAGS_INIT "-target ${ZIG_TARGET}")
set(CMAKE_EXE_LINKER_FLAGS_INIT "-target ${ZIG_TARGET}")
set(CMAKE_CXX_STANDARD_LIBRARIES "${MACOS_SDK}/usr/lib/libc++.tbd")
set(CMAKE_STRIP "llvm-strip$ENV{LLVM_SUFFIX}" CACHE FILEPATH "" FORCE)

# Headers and libraries from the SDK, for every compile and link
set(ZIG_LIBC_FILE "${CMAKE_BINARY_DIR}/zig-libc.txt")
file(WRITE "${ZIG_LIBC_FILE}" "include_dir=${MACOS_SDK}/usr/include\nsys_include_dir=${MACOS_SDK}/usr/include\n"
    "crt_dir=${MACOS_SDK}/usr/lib\nmsvc_lib_dir=\nkernel32_lib_dir=\ngcc_dir=\n")
set(CMAKE_C_COMPILER_LAUNCHER "${CMAKE_COMMAND};-E;env;ZIG_LIBC=${ZIG_LIBC_FILE}")
set(CMAKE_CXX_COMPILER_LAUNCHER "${CMAKE_COMMAND};-E;env;ZIG_LIBC=${ZIG_LIBC_FILE}")
set(CMAKE_C_LINKER_LAUNCHER "${CMAKE_COMMAND};-E;env;ZIG_LIBC=${ZIG_LIBC_FILE}")
set(CMAKE_CXX_LINKER_LAUNCHER "${CMAKE_COMMAND};-E;env;ZIG_LIBC=${ZIG_LIBC_FILE}")

# Install name @rpath/libjssc.dylib, as on a Mac. CMake decides on @rpath from the running macOS version (sw_vers),
# which a Linux host doesn't have, and would fall back to the absolute path of the build directory.
set(CMAKE_INSTALL_NAME_DIR "@rpath")
set(CMAKE_BUILD_WITH_INSTALL_NAME_DIR ON)

# Frameworks (IOKit) come from the SDK only; JNI headers come from the build JDK
set(CMAKE_FIND_ROOT_PATH "${MACOS_SDK}")
set(CMAKE_FIND_ROOT_PATH_MODE_PROGRAM NEVER)
set(CMAKE_FIND_ROOT_PATH_MODE_LIBRARY ONLY)
set(CMAKE_FIND_ROOT_PATH_MODE_INCLUDE BOTH)
set(CMAKE_FIND_ROOT_PATH_MODE_PACKAGE BOTH)
