# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project

JSSC (Java Simple Serial Connector, `io.github.java-native:jssc`): a small Java library for serial ports, backed by a JNI native library (`libjssc`) written in C++. Licensed LGPL-3.

## Build & test

Maven drives everything, including the native build. It calls Ant (`ant/build.xml`), which calls CMake (`CMakeLists.txt`). The default goal is `install`, so a bare `mvn` does a full build. You need a JDK, CMake and a C++ compiler.

```bash
mvn                                   # full build: javac + cmake native build + tests + install
mvn -Ppackage                         # skip native compile & tests; package natives from src/main/resources-precompiled
mvn test -Dtest=SerialPortTest        # single test class
mvn test -Dtest=SerialPortTest#method # single test method
mvn -P<profile>                       # cross-compile, e.g. x86, x86_64, aarch64, armhf, armsf, riscv32, riscv64, ppc64, mingw32, mingw64, mingwaarch64, dockcross
mvn -Pupdate-resources-precompiled    # also copy freshly built natives back into src/main/resources-precompiled
mvn -Pjar-with-dependencies           # fat jar (used for GitHub releases)
```

Notes:
- **Run `mvn clean` when switching between cross-compile profiles.** A stale `target/cmake/CMakeCache.txt` makes CMake silently ignore `CMAKE_TOOLCHAIN_FILE` (#28).
- **Tests only run when the target platform matches the host.** `ant/build.xml` (`test-props`) sets `maven.test.skip=true` for cross-compile profiles and prints "Tests WILL/will NOT run".
- `VirtualPortTest` needs `socat` to create virtual serial port pairs. It is skipped on Windows or when `socat` is missing.
- `ManualBootLibraryPathTest` is excluded when the native build is skipped (`maven.exclude.tests`). Surefire uses `reuseForks=false` so that each test class gets a fresh JVM. The `jssc.boot.library.path` tests depend on this.
- CI (`.github/workflows/build.yml`) builds with JDK 11. Runner images are pinned (`ubuntu-24.04`, `windows-2025`, `macos-15`, `macos-15-intel`); `env-canary.yml` runs the same matrix weekly on the next images and checks that the pinned dockcross images still exist. Bump a pin when the canary is green on its successor.

## Java version constraints

- Bytecode targets **Java 6** (Java 7 when building on JDK 12+, via the `jdk12+` profile). Do not use newer language features or APIs. `animal-sniffer` checks against the java16 signature during `verify`.
- On JDK 9+, the `jdk9+` profile compiles twice. The first pass uses `--release 9` with `src/main/module-info/module-info.java` to produce the module descriptor. The second pass recompiles everything else for the minimum target. `javac -h` writes the JNI header into `target/cmake/`.
- `-Xlint:all` is enabled.

## Architecture

**Java layer** (`src/main/java/jssc/`):
- `SerialNativeInterface`: declares the `native` methods and loads the library in a static initializer through scijava `native-lib-loader`. It looks in the system library path first, then the `jssc.boot.library.path` system property (via `DefaultJniExtractorStub`), and finally extracts the library from the jar.
- `SerialPort`: the public API. It wraps a native port handle and runs an event thread that polls `waitEvents()` and dispatches to `SerialPortEventListener`.
- `SerialPortList`: port enumeration with OS-specific name patterns.

**Native layer** (`src/main/cpp/`):
- `_nix_based/jssc.cpp` covers Linux and macOS. Its Solaris code is kept but unmaintained: Solaris was dropped from CI and binaries in #149. `windows/jssc.cpp` covers Windows. CMake picks one of them.
- *nix reads use `poll()` because `select()` segfaults on fd ≥ 1024 (#90). macOS uses the `select` fallback (`HAVE_POLL=0`). Don't switch Linux back to `select`.
- Native errors must surface as Java exceptions (`ThrowNew`/`ExceptionCheck`). `SerialNativeInterface` throws `IOException`, and `SerialPort` wraps it with `SerialPortException.wrapNativeException(...)` so public signatures stay the same. Native input validation must still accept 0-byte reads, because the Windows event loop issues them (#192).
- Windows release builds deliberately use `-O0`/`/Od` (an XP-era crash with `-O3`, #9). Natives link the C++ runtime statically. Be careful with "warning fixes" in native code: a cast added to silence a warning broke Windows baud rates in 2.9.3 (#113).
- `jssc_SerialNativeInterface.h` is a committed fallback JNI header. The build normally regenerates it from the Java sources. **If you change any `native` method signature in `SerialNativeInterface.java`, update both C++ implementations and the committed header.** The precompiled binaries also have to be rebuilt.
- `version.h.in` is filled by CMake with `JSSC_VERSION` (from pom `release.version`) and returned by `getNativeLibraryVersion()`.

**Native lib directory naming:** the output goes to `natives/<os>_<suffix>/`, for example `linux_64`, `linux_arm64`, `osx_arm64` or `windows_32`. That is the layout `native-lib-loader` expects. `ant/build.xml` translates os-maven-plugin arch names into these suffixes (`x86_64→64`, `aarch_64→arm64`, `ppc_64→ppc`, ...). CMake has its own fallback detection. Cross-compile toolchain files are in `toolchain/*.cmake`.

**Precompiled natives:** `src/main/resources-precompiled/natives/` holds committed binaries for every supported platform. It is the Maven resource directory, so every jar bundles them. A locally built library is copied into `target/classes/natives/` and overrides the precompiled one for the host. The binaries are regenerated by the `cross-compile.yml` workflow, which uses dockcross containers plus macOS runners. That workflow pushes a `<branch>-precompiled-natives` branch with a squashed "Precompiled natives @<rev>" commit, which maintainers merge without rebase or squash.
- **Do not commit locally built natives in PRs.** Only the workflow output is accepted (wiki "Cross-Compilation").
- Binary compatibility is a project goal: target the oldest systems you can.
  - Linux natives come from dockcross images pinned by `DOCK_CROSS_TAG` (Debian bullseye; don't apt-install anything inside them, the workflow mounts the runner's JDK and Maven), which keeps glibc at 2.3 for x86/x64 (#190/#191). `linux_arm` is built for ARMv6 (`linux-armv6-lts`, #139), so a local `-P armhf` build is not equivalent.
  - macOS x86_64 has deployment target 10.9, the minimum Apple notarization accepts (#54, #187); arm64 has 11.0. Both build against `MacOSX11.0.sdk` (the 10.9 SDK lacks libc++ headers, which osxcross needs). Release dylibs are cross-compiled on Linux with osxcross (`-Posxcross`, `toolchain/Osxcross.cmake`, `.github/actions/setup-osxcross`); the SDK tarballs come from phracker/MacOSX-SDKs and must stay in the private Actions cache, never in a published image (Apple license). macOS runners only run the tests.
  - The `show-file-info` Ant target prints `file` and `otool` minos output, so you can check this.
- Users can bypass extraction with `-Djssc.boot.library.path=<dir>` (#92). This is required in sandboxed environments.

## Versioning & release

- The version lives in `pom.xml`. `SerialNativeInterface.libVersion` must match the release version (without `-SNAPSHOT`). The cross-compile workflow updates it with sed before building natives.
- Release (`release-new-version.yml`) runs `mvn release:prepare release:perform -P package,maven-central-release`. That command publishes the precompiled natives without recompiling, uses tag format `v<version>`, and then updates the version in `README.md` and the wiki.
  - Order: run cross-compile on the release branch, merge the `-precompiled-natives` branch, then trigger the release.
  - Publishing goes to the Sonatype Central Portal (`central-publishing-maven-plugin`; OSSRH is gone).
  - The GitHub Release is created as a draft with the `jar-with-dependencies` fat jar and is published by hand.
  - Check that the natives actually changed before releasing. 2.10.3 shipped stale binaries (#205).
- Compatibility policy:
  - Don't break the public API without deprecation.
  - Behaviour or exception changes get a minor bump and migration notes. Example: 2.10.0 made `SerialPortException` extend `IOException` (#160).
  - Release builds use JDK 11. Never fail a build just because of the JDK version.

## Project context

- `JOURNAL.md` records the history and the decisions (with issue numbers) from all upstream issues and PRs. `BACKLOG.md` lists pending work and the proposals that were explicitly rejected. Check both before proposing structural changes. For example, mvnw, git-lfs, splitting the repo, shading, and switching back to `select` were all rejected before.
- The upstream wiki is a separate git repo: `https://github.com/java-native/jssc.wiki.git` (Compiling, Cross-Compilation, Publishing-a-Release, Examples). Examples and Windows-Build-Environment are still stale (BACKLOG.md B-18).
- Remotes: `upstream` = `java-native/jssc`, and `origin` = the user's fork.
