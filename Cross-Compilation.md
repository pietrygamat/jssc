# Configuring a cross-compilation build environment
## Github Action
The native binaries for all architectures included in JSSC package are built publicly on Github using [this Action](https://github.com/java-native/jssc/actions/workflows/cross-compile.yml) and merged into git. This keeps them in sync with one another and with project version. The action is available to repository forks, so when contributing to the project you can always execute it on one of your branches, and after a few minutes it creates a branch (`<selected_branch_name>_precompiled_natives`) in your repository with all the binaries committed. 

As a general contribution rule, the native binaries should _not_ be included in PRs, and instead built using Github and merged by the project maintainers.

While keeping binaries in the source code may not be ideal, we feel it's a good compromise allowing project to be accessible to Java contributors without burdening them with the complexities of local C++ cross-compilation - and still having everything in one place.

## Local environment configuration
In order to compile the C++ code locally you can follow these instructions:

This builds the library based on the G++ compiler and its supplemental packages.  
This will allow you to build native binaries for

* **Windows:** `x86`, `x86_64` using mingw-w64
* **Windows:** `arm64` (aarch64) using clang llvm-mingw wrapper
* **Linux:** `arm`, `arm64` (aarch64) and `ppc64`

## Prerequisites

Starting point: A clean Debian/Ubuntu install with latest patches applied.
(other platforms will work, but the package names and commands may vary)

### Windows 32-bit
1. Install dependencies
```bash
sudo apt-get install mingw-w64 binutils-mingw-w64-i686
```
2. Cross-compile using `mingw32` toolchain
```bash
mvn -P mingw32
```

### Windows 64-bit
1. Install dependencies
```bash
sudo apt-get install mingw-w64
```
2. Cross-compile using `mingw32` toolchain
```bash
mvn -P mingw64
```

### Windows ARM 64-bit
Note:  Glibc 2.7+ required (Ubuntu 20.04+)
1. Install dependencies
```bash
sudo apt-get install clang
```
2. Cross-compile using `mingwaarch64` toolchain (cmake will download automatically)
```bash
mvn -P mingwaarch64
```

### Linux ARM 32-bit
1. Install dependencies
```bash
sudo apt-get install g++-arm-linux-gnueabihf
```

2. Cross-compile using `armhf` toolchain
```bash
mvn -P armhf
```

### Linux ARM 64-bit
1. Install dependencies
```bash
sudo apt-get install g++-aarch64-linux-gnu
```

2. Cross-compile using `aarch64` toolchain
```bash
mvn -P linux-aarch64
```

### Linux PPC 64-bit
1. Install dependencies
```bash
sudo apt-get install g++-powerpc64le-linux-gnu
```
2. Cross-compile using `ppc64` toolchain
```bash
mvn -P ppc64
```