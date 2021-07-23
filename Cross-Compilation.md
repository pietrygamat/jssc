# Configuring a cross-compilation build environment

This builds the library based on the G++ compiler and its supplemental packages.  
This will allow you to build native binaries for

* **Windows:** `x86`, `x86_64` using mingw-w64
* **Linux:** `arm`, `arm64` (aarch64) and `ppc64`

## Prerequisites

Starting point: A clean Ubuntu install with latest patches applied, one of:

* Ubunutu 14.04  Trusty Tahr
* Ubunutu 16.04  Xenial Xerus
* Ubunutu 20.04  Focal Fossa

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
1. Install dependencies
```bash
## TODO
```
2. Cross-compile using `mingw32` toolchain
```bash
## TODO
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