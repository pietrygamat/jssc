# Cross-compile macOS natives on Linux with osxcross (https://github.com/tpoechtrager/osxcross)
#
# Environment:
#   OSXCROSS_TARGET_DIR      osxcross "target" directory (contains bin/ and SDK/)
#   OSXCROSS_ARCH            x86_64 or arm64
#   MACOSX_DEPLOYMENT_TARGET 10.9 (x86_64) or 11.0 (arm64), as on the macOS runners
set(CMAKE_SYSTEM_NAME Darwin)

set(OSXCROSS_TARGET_DIR "$ENV{OSXCROSS_TARGET_DIR}")
set(OSXCROSS_ARCH "$ENV{OSXCROSS_ARCH}")
if(NOT IS_DIRECTORY "${OSXCROSS_TARGET_DIR}/bin" OR NOT OSXCROSS_ARCH)
    message(FATAL_ERROR "Set OSXCROSS_TARGET_DIR and OSXCROSS_ARCH (x86_64 or arm64)")
endif()

# osxcross names its tools after the Darwin version of the SDK, e.g. x86_64-apple-darwin13-clang++
file(GLOB OSXCROSS_CXX "${OSXCROSS_TARGET_DIR}/bin/${OSXCROSS_ARCH}-apple-darwin*-clang++")
list(LENGTH OSXCROSS_CXX OSXCROSS_CXX_COUNT)
if(NOT OSXCROSS_CXX_COUNT EQUAL 1)
    message(FATAL_ERROR "Expected one ${OSXCROSS_ARCH} clang++ in ${OSXCROSS_TARGET_DIR}/bin, found: ${OSXCROSS_CXX}")
endif()
string(REGEX REPLACE "-clang\\+\\+$" "" OSXCROSS_HOST "${OSXCROSS_CXX}")

set(CMAKE_CXX_COMPILER "${OSXCROSS_HOST}-clang++")
set(CMAKE_C_COMPILER "${OSXCROSS_HOST}-clang")
set(CMAKE_STRIP "${OSXCROSS_HOST}-strip" CACHE FILEPATH "" FORCE)
set(CMAKE_INSTALL_NAME_TOOL "${OSXCROSS_HOST}-install_name_tool" CACHE FILEPATH "" FORCE)
set(CMAKE_AR "${OSXCROSS_HOST}-ar" CACHE FILEPATH "" FORCE)
set(CMAKE_RANLIB "${OSXCROSS_HOST}-ranlib" CACHE FILEPATH "" FORCE)

set(CMAKE_OSX_ARCHITECTURES "${OSXCROSS_ARCH}" CACHE STRING "" FORCE)
file(GLOB CMAKE_OSX_SYSROOT "${OSXCROSS_TARGET_DIR}/SDK/MacOSX*.sdk")
set(CMAKE_OSX_DEPLOYMENT_TARGET "$ENV{MACOSX_DEPLOYMENT_TARGET}" CACHE STRING "" FORCE)

# Frameworks (IOKit) come from the SDK only; JNI headers come from the build JDK on the host
set(CMAKE_FIND_ROOT_PATH "${CMAKE_OSX_SYSROOT}")
set(CMAKE_FIND_ROOT_PATH_MODE_PROGRAM NEVER)
set(CMAKE_FIND_ROOT_PATH_MODE_LIBRARY ONLY)
set(CMAKE_FIND_ROOT_PATH_MODE_INCLUDE BOTH)
set(CMAKE_FIND_ROOT_PATH_MODE_PACKAGE BOTH)
