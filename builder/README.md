# jssc cross-compile image

One container image that builds the jssc natives for every supported target:

| Target | Toolchain | Taken from |
|---|---|---|
| `linux_32`, `linux_64` | Debian bullseye gcc 10 (`-m32` wrapper for 32-bit) | `dockcross/linux-x86` (the base image) |
| `linux_arm` | crosstool-ng armv6 gnueabihf | `dockcross/linux-armv6-lts` |
| `linux_arm64` | crosstool-ng aarch64 | `dockcross/linux-arm64-lts` |
| `linux_riscv32`, `linux_riscv64` | crosstool-ng | `dockcross/linux-riscv32`, `linux-riscv64` |
| `linux_ppc` | crosstool-ng powerpc64le | `dockcross/linux-ppc64le` |
| `windows_32`, `windows_64` | MXE static mingw-w64 | `dockcross/windows-static-x86`, `-x64` |
| `windows_arm64` | llvm-mingw 20220323 | `dockcross/windows-arm64` |
| `osx_64`, `osx_arm64` | osxcross (cctools 986 / ld64 711) + llvm-mingw's clang 14, MacOSX11.3.sdk | built here |

All dockcross images are the `20231208-4e4d3ad` ones the old per-image workflow used, pinned by
digest. Their toolchain directories are copied unchanged, and `targets/<target>.env` holds the
environment each image set, so a build sees the same compiler, sysroot and glibc as before.
The image needs no package manager to build natives: the JDK (Temurin 11) and Maven come
from checksummed tarballs. Only the throw-away osxcross build stage installs the libxml2 headers
from archive.debian.org.

macOS: x86_64 keeps deployment target 10.9 and arm64 11.0, but both now build against SDK
11.3, because the 10.9 and 11.0 SDKs have no libc++ headers (on a Mac, Xcode supplies them).

## Build the image

```sh
podman build -t localhost/jssc-cross-sdk:dev builder      # everything, including the Apple SDK
podman build --target jssc-cross -t localhost/jssc-cross:dev builder   # without the SDK
```

The first build pulls about 19 GB of dockcross images and compiles osxcross (around 10 minutes).
The `builder-image.yml` workflow builds both and pushes them to `ghcr.io/<owner>/jssc-cross` and
`ghcr.io/<owner>/jssc-cross-sdk`. **Keep `jssc-cross-sdk` private:** Apple's license does not allow
redistributing the SDK. `jssc-cross` contains no Apple files.

## Build natives

```sh
builder/cross-compile.sh                        # all targets
builder/cross-compile.sh linux_arm64 osx_arm64  # some
IMAGE=ghcr.io/pietrygamat/jssc-cross-sdk@sha256:... builder/cross-compile.sh windows_64 -- -DskipTests
```

Each target runs `mvn clean install` in its own container and leaves the library in
`src/main/resources-precompiled/natives/<target>/`. With the SDK-less image, mount an SDK at
`/opt/osxcross/SDK/MacOSX11.3.sdk` to build `osx_*`.

Inside the image, `jssc-target <target> <command>` runs any command with a target's environment,
for example `jssc-target linux_ppc bash`.

## Change a toolchain

Edit the digest or version in `Containerfile`, run `gen-targets.sh` if a dockcross digest
changed, and rebuild. Compare the new natives against the old ones before releasing.
