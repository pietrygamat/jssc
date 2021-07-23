# Setting up a Windows MSVC Development Environment

This builds the library based on the Visual C++ compiler.

## Background

JSSC uses the Microsoft Visual Studio C++ compiler (MSVC) to compile native bits on Windows, set automatically using `VsDevCmd.bat`.  JSSC also supports the [mingw cross-compiler](Cross-Compilation#windows-32-bit).

## Prerequisites

Starting point: A clean Windows installation with all patches.
   
### Visual Studio Components
* Install "Visual Studio Community 2019" or the "Build Tools for Visual Studio 2019" (https://visualstudio.microsoft.com/downloads/)
* Windows 10 SDK
* MSVC v142 - VS 2019 C++-x64/x86-Buildtools
* MSVC v142 - VS 2019 C++-ARM64-Buildtools (optional, for ARM64 targets)
* Windows Universal CRT SDK

### Maven, Cmake
* Manually install Java from https://adoptopenjdk.net
* Manually install Maven from https://maven.apache.org/, adding `mvn.bat` directory to your `%PATH%`
* Manually install CMake from https://cmake.org/download/, adding `cmake.exe` directory to your `%PATH%`
        

## Steps

**Note:** The paths below are samples and depend on the exact versions installed.
* For example for "**Visual Studio <u>Community</u> 2019**" `vcvarsall.bat` can be found here:<br>`C:\Program Files (x86)\Microsoft Visual Studio\2019\Community\VC\Auxiliary\Build\vcvarsall.bat`.
* ... while for "**<u>Build Tools</u> for Visual Studio 2019**" `vcvarsall.bat` is found here:<br>`C:\Program Files (x86)\Microsoft Visual Studio\2019\BuildTools\VC\Auxiliary\Build\vcvarsall.bat`.


1. Open command line for the following actions - _if Visual Studio IDE is installed you will also have versions of PowerShell command line      available that are pre-configured for this_
    
1. Setup the Visual Studio build environment using vcvarsall in &lt;host&gt;_&lt;target&gt; notation:

    ### VsDevCmd x86_64

    ```bat
    "C:\Program Files (x86)\Microsoft Visual Studio\2019\Community\VC\Auxiliary\Build\vcvarsall.bat" x64
    ```

    Compile:
    ```bash
    mvn -P x86_64   # compile for 64-bit x86_64/amd64
    ```

    ### VsDevCmd x86

    ```bat
    "C:\Program Files (x86)\Microsoft Visual Studio\2019\Community\VC\Auxiliary\Build\vcvarsall.bat" x64_x86
    ```

    Compile:
    ```bash
    mvn -P x86      # compile for 32-bit x86
    ```

    ### VsDevCmd aarch64

    ```bat
    "C:\Program Files (x86)\Microsoft Visual Studio\2019\Community\VC\Auxiliary\Build\vcvarsall.bat" x64_arm64
    ```

    Compile:
    ```bash
    mvn -P aarch64  # compile for arm64
    ```