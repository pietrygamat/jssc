# JSSC project journal

A history of `java-native/jssc`, built from the upstream GitHub issues, pull requests, review comments and the wiki, read in issue-number (≈ chronological) order. It records what was decided and **why**, and what was rejected or deferred. Its purpose is to keep future contributors (human or agent) from re-litigating settled questions and to point them at the context behind the code.

Conventions: `#N` = upstream issue/PR number. People are referred to by GitHub handle. Each section says when it was written.

---

## 2019 — the fork and the new build system (#1–#52)

### Origin
- The project resurrects the abandoned `scream3r/java-simple-serial-connector` (JSSC 2.8.0) after a discussion in scream3r/java-simple-serial-connector#132. The early goals were: fix Java 11 + Windows support, get a reproducible build, and eventually take over as upstream (#20).
- The early team was tresf (maintainer; native/C++ and release), woodrowbarlow (CMake), and bmarwell/bmhm (Maven). Later, pietrygamat (JockX) took over most Maven/CI/release work (from 2020).
- History begins at scream3r's 0.9.0. The tags v0.9.0, v2.5.0, v2.6.0 and v2.8.0 were back-dated (#6), and release notes live on GitHub Releases rather than in the README (#6, #20).
- The `master` branch replaced scream3r's `2.8.0` default branch (#7).

### Native build: CMake (#2, #3, #8, #9, #10)
- **CMake was chosen over Maven-native plugins** because of macOS builds (#4). Maven drives CMake through `maven-antrun-plugin` and `ant/build.xml` (#15). An earlier `cmake-maven-plugin` was replaced.
- **MSVC support (#2, #8):** MSVC doesn't support VLAs, so the VLAs were replaced by heap buffers. woodrowbarlow pushed for a first release before code changes and raised concerns about leaks and hot-path heap allocation. tresf argued that half of all developers use Windows. Result: VLAs removed, `/MT` for a static CRT, and optimisation disabled on MSVC to match MinGW.
- **Windows XP crash (#9):** caused by `-O3`. **Windows builds use `-O0` / `/Od` deliberately** (still in `CMakeLists.txt` as "Disable optimization for Release builds (XP/Server 2003)"). The GCC/Clang release flag is `-O2`.
- The default `CMAKE_BUILD_TYPE` is `Release`, and binaries are stripped on non-Debug builds (#3).
- Static libgcc/libstdc++ (`-static-libgcc -static-libstdc++`, `-static` for MinGW) so that natives have no runtime dependencies (#9).
- Release-time warnings: `-Wall -Wextra -pedantic` was added (#15). `-Werror` / `/WX` were dropped later, and a TODO for them remains in `CMakeLists.txt`.

### Build system: Maven (#4, #14, #15, #18, #27, #28, #31, #32)
- **Maven was chosen** (#4, #14), with the Maven standard directory layout. A move of `src/java` → `src/main/java` was deferred until open PRs landed. It has since been done.
- `javah` doesn't exist on JDK 9+. A `jdk9+` profile uses `javac -h` instead, and `nar-maven-plugin` covers JDK ≤8 (#4, #15).
- Native libraries are loaded through **scijava `native-lib-loader`** (introduced by bmarwell, #4). That determines the `natives/<os>_<suffix>/` layout.
- **Committed binaries:** the precompiled `.so/.dll/.dylib` files live in `src/main/resources-precompiled/natives`. **git-lfs was ruled out** because of git-lfs/git-lfs#1906 (#14). Moving the binaries to a separate repository or to Maven storage was discussed (#40) and not adopted for the main line.
- **Artifact layout (#18, #31, #32):** the default (classifier-less) jar is the "uber" jar, which contains *all* precompiled natives. A second jar, classified by OS/arch/bitness, contains the precompiled natives with the freshly built host library swapped in (`src/assembly/one-off-jar.xml`). `-Ppackage` skips CMake and the tests.
- `maven.assembly.id` is a custom property because os-maven-plugin's `os.detected.*` values weren't overridable inside the assembly (#32).
- **Maven wrapper (`mvnw`) was rejected (wontfix, #27):** you need CMake anyway, and Maven is easy enough to get.
- `defaultGoal` stays `install`, **not** `clean install`. Forcing `clean` would rebuild everything for casual users (#28). The wiki tells you to run `mvn clean` between cross-compile profiles instead, because a stale `CMakeCache.txt` silently ignores `CMAKE_TOOLCHAIN_FILE`.
- Cross-compile profile names are kept short and match toolchain file names (`armhf`, `ppc64`, `mingw64`, …). os-maven-plugin-style names were rejected as too verbose (#18 review).

### Naming & coordinates (#5, #23)
- The project was renamed from `java-simple-serial-connector` to **`jssc`**. Maven coordinates are **`io.github.java-native:jssc`** (`io.github.*` per Sonatype rules). A `io.github.java-native.jssc` group was proposed and then reverted. The Java package stays `jssc` (renaming it to `io.github.javanative.jssc` was discussed in #5/#33 for a future major version, but never done).
- License: LGPL-3.0 ("or any later version"). `LICENSE.txt` was added and declared in the pom (#22). `native-lib-loader` is BSD, which is compatible with LGPL.

### Dependencies & the fat jar (#21, #43)
- Adding `native-lib-loader` (plus `slf4j-api`) was a regression for users without dependency management: `NoClassDefFoundError`. tresf: "It's no longer simple once you add 3rd party dependencies." Result: a **`jar-with-dependencies` profile** (unshaded) whose output is attached to GitHub Releases (#43). Shading was rejected because it would break slf4j's binding lookup. Forking or inlining the loader was proposed (#21) and was later attempted in `bmhm/libloader` (see #23).
- **`serialVersionUID`:** explicit UIDs were added to the exceptions so that `-Xlint:all` stays clean. A suppression was proposed and rejected (#42).

### Java compatibility (#15, #34, #36)
- The target is **Java 6 bytecode**, and animal-sniffer enforces the Java 6 API (#15). `-Xlint:all` is enabled (#34).
- "Avoid JDK12" for builds. JDK 11 LTS is the release JDK (#34). Building for target 6 later required `--release`, and JDK 12+ automatically targets 7 (`jdk12+` profile).
- A 3.0.0 version bump was proposed (#36) but **never happened**. Releases continued as 2.9.x/2.10.x.

### Code style (#12, open)
- A checkstyle / google-java-format proposal is still open (milestone 5.0.0). tresf: ternaries are allowed, unicode escapes are fine for invisible characters (STX/ETX), and this is a long-term goal. **Not implemented.**

### Platforms
- **ARM hard-float vs soft-float (#26, #35, #48):** cross-compiled armhf binaries initially didn't load. GCC 4.7 hard-float worked on a Pi, and soft-float JSSC never worked (#48). JSSC 2.8.0 used `readelf` to pick hf/sf at runtime. That was lost with native-lib-loader, which can't tell the two apart. **#35 is open:** the ideal is one binary for both, and the fallback is a patched native-lib-loader (scijava/native-lib-loader#32). The `armsf` profile and toolchain exist, but only one `linux_arm` binary ships (hard-float). A TODO for the namespace conflict is in the pom.
- On-device ARM builds originally wrote to the `linux_32` directory (#24), fixed by ARM directory mapping. Tests on ARM originally used the bundled `.so` instead of the built one (#51). Fixed in #151.
- **Solaris (#44, #45):** build support was added (Solaris 10/11, `__sun` macro, `FORCE_M64`). native-lib-loader didn't load Solaris libraries, so the binaries went unused. Support was **later dropped** (the wiki says "We no longer support Solaris", 2023). The Solaris build steps remain in the wiki's release page (stale).
- Mac PowerPC and Mac 32-bit were dropped with the fork (#36).

### Testing (#37, #47)
- Virtual serial ports via **`socat`** in a JUnit `@Rule` (`VirtualPortRule`), Linux/macOS only. Windows virtual ports need signed drivers, so they're skipped (#37). PR #47 sat open from 2019 to 2023 and was merged by tresf with a `socat -V` sanity check, because `ProcessBuilder` deadlocks under JDK 6 when `socat` is missing. tresf's merge note: scream3r's "simple" principle, the view that real serial bugs need physical hardware, and a strong personal dislike of Maven and JUnit verbosity. The rule's threading logic could be split out later.
- #37 (better unit tests) remains open. It's partially addressed by #47 and #151.

### Other
- OSGi bundle metadata (#39) is still **open**, with no owner.
- #40: bmarwell's split into separate `jssc-native` and `libloader` repos (plus a `jssc5` branch with a refactored pom and a fully automated build). tresf objected: "The JSSC library means nothing without the C++ code"; one repo, as with JNA, keeps things debuggable. The split was **not adopted**. bmarwell left the project around 2020 (see #23).
- #53 (poll-based `waitEvents` on *nix to stop the busy loop) was opened in 2019 and closed in 2026 via #201 (details later in this journal).

---

## 2019–2021 — 2.9.x, Maven Central, CI and platform growth (#54–#106)

### macOS notarization (#54, #57)
- The 2.9.0 dylib failed Apple notarization ("binary uses an SDK older than the 10.9 SDK").
- **Decision:** build against **SDK 10.9** with a low deployment target (10.5 was tested). Apple bases compatibility on the deployment target, not on the SDK. lukas-w helped work this out.
- An LLD `-no_version_load_command` hack was considered and rejected as a loophole that Apple might close.
- `strip` must run **before** natives are copied to the source tree (#57).
- The macOS x86_64 release build still uses `MacOSX10.9.sdk`. arm64 uses `MacOSX11.0.sdk` (CI fetches both from phracker/MacOSX-SDKs).

### Headers & local builds (#55)
- The committed `src/main/cpp/jssc_SerialNativeInterface.h` (generated by `javah` on Unix and tested with MSVC) lets you build with plain CMake without Maven or with an old Maven.
- #55 also made CMake copy natives into `src/main/resources-precompiled` automatically. That behaviour was **later replaced**: natives now go to `target/`, and copying back requires `-Pupdate-resources-precompiled`. pietrygamat noted in #67 that building into the source tree was surprising.

### Scope decisions
- **NodeJS/Electron bindings (#56)** were requested by scream3r. tresf declared them **out of scope** in 2024.
- **USB VID/PID / port metadata (#69, open, "help wanted").** On Windows the data is available through SetupAPI hardware IDs; on Linux, through `/dev/serial/by-id`. tresf: serial is anonymous, so this is really a USB API, but USB-serial adapters are now the norm, so it's a natural enhancement. A prior-art patch exists in scream3r/java-simple-serial-connector#113. **Not implemented.**
- **Disconnect notification (#89, open):** there is currently no event when a USB adapter is unplugged; a timeout or exception is the only signal. This could build on the #69 work.
- **Issue/PR templates (#62)** were closed as not needed.
- **checkstyle (#61)** was closed as a duplicate of #12.

### Maven Central (#23, #66, #67, #68)
- `org.scream3r` coordinates couldn't be used, so the project needed its own groupId. bmarwell got `io.github.java-native` approved on Sonatype (OSSRH-54753), and pietrygamat finished the pom requirements (#67).
- **2.9.2 was the first Maven Central release** (2020-07-31, #68). 2.9.1 was GitHub-only.
- The PGP key bmarwell created was lost, so pietrygamat signed 2.9.2 with a personal key.
- The pom version keeps a `-SNAPSHOT` suffix between releases (#67).
- #66 fixed real javadoc errors instead of setting `doclint=none`.
- #70: the 2.9.2 GitHub release jar was missing `jar-with-dependencies` (NoClassDefFoundError for NativeLoader). It was rebuilt by hand. Later automation (`release-new-version.yml`) builds the fat jar for GitHub Releases.
- #76: the `SLF4J: Failed to load StaticLoggerBinder` warning is expected. JSSC ships only `slf4j-api` (through native-lib-loader); users add a binding if they want logs.
- #64 proposed marking slf4j as `provided`. It was rejected because the fat jar must work standalone.

### Windows JVM crash `EXCEPTION_ACCESS_VIOLATION` (#65)
- Caused by old JSSC 2.8.0 binaries, sometimes pulled in transitively (for example inside a JPos wrapper). It's fixed in 2.9+. Lesson: check which native binary is actually deployed.

### Antivirus false positives (#77)
- Windows Defender flags javadoc jars as `Trojan:Java/Tisifi`. This is a known false positive across many projects. It was reported to Microsoft; there's nothing to fix in JSSC.

### Event listener API (#85, #86, #87, #88)
- `SerialPortEvent.getPort()` was added (#86). An event now references the `SerialPort` object, so listeners don't need to be built around a port reference. The constructor was kept backward compatible.
- tresf's principle, stated here and repeated later: **never break the public API without a long deprecation period.**
- Kotlin examples were added to the wiki (#85).
- In #106 the duplicated `SerialPortEvent` mask constants (`RXCHAR` etc., duplicates of `SerialPort.MASK_*`) were marked `@Deprecated` and kept.

### Java modules (#60, #79, #81, #105)
- `module-info.java` lives in `src/main/module-info/`. With JDK 9+ the build compiles twice: once with `--release 9` for the module descriptor and once for the target-version bytecode (#81).
- **Builds must never fail just because of the JDK version:** "I'll never fail a build that would otherwise pass." On JDK 12+ the target drops to 7 automatically, with an enforcer WARN only.
- Release artifacts **must be built with JDK 11**: Java 6 bytecode plus `module-info.class`.
- The module is `open module jssc { requires transitive org.scijava.nativelib; exports jssc; }`, with `requires-automatic` warnings suppressed (#79/#105).
- A future 3.0.0 bump was suggested if the minimum Java version ever changes, so that v2.9.x tags stay usable for backports.

### native-lib-loader fixes upstream
- #80: the library didn't work on Turkish Windows (locale lowercase of "I"). Fixed upstream (scijava/native-lib-loader#40) and picked up by bumping the dependency for 2.9.3.
- #75: Windows ARM64 initially failed because of an upstream loader bug (scijava/native-lib-loader#42) and a non-static binary (fixed in #109). Fixed in 2.9.4.
- tresf repeatedly floated forking or dropping native-lib-loader. **Not done.**

### Platforms
- **Apple Silicon (#74)** and **Windows ARM64 (#75)** shipped in 2.9.3 and 2.9.4.
- **aarch64 profiles (#91):** `mvn -P aarch64|x86_64|x86`. Profile logic later moved to Ant (#93, #98), because os-maven-plugin properties aren't available when profiles are activated (trustin/os-maven-plugin#23). Properties pass from Ant back to Maven through a properties-file workaround (MANTRUN-231).
- **Sandboxed environments (#92):** `jssc.boot.library.path` plus `DefaultJniExtractorStub` let JSSC load a native from a fixed directory without extracting it. This covers SELinux and the Mac App Store. Documented in the wiki "Examples" page.
- **Port detection (#97, supersedes #64):** on macOS JSSC lists `tty.*`/`cu.*`. The open-to-probe check stays only for Linux `ttyS*`, because probing hurt USB-CDC devices on macOS.

### Unix I/O rework (#53, #78, #90, #94–#96, #103)
- Background: `LinuxEventThread` busy-loops (`Thread.sleep(0,100)`) and burns CPU (7–10% on a laptop, 100% on small devices) (#53).
- #78 (graynk, `poll` in `waitEvents`) was closed as superseded by #90.
- **#90 (hiddenalpha):** `readBytes` uses `poll()` instead of `select()` to fix segfaults when fd ≥ 1024. `select` stays as a fallback (on macOS), because `poll` is less portable and was broken on macOS 10.12. `SerialPort.readBytes`/`closePort` are synchronized, and the Java `sleep` was removed.
- Logging from native code was dropped. tresf's preferred direction: "TODO: Move logging to java" and "Errors should be communicated by raising java exceptions; will break backwards compat".
- centic9 reported that #90 did **not** fix `waitEvents` CPU, so #53 was reopened. It was finally closed by #201 in 2026.
- hiddenalpha's follow-up issues:
  - #94 (infinite loop in `readBytes` when the plug is pulled) was fixed by #155: `read` now throws `IOException`.
  - **#95 (infinite loop if `closePort` races `readBytes`) is still open.**
  - #96 (`writeBytes` hides errno) was fixed by #146/#152.

### CI & release automation (#82, #100, #101, #102, #104)
- Travis-CI broke when travis-ci.org shut down in 2021, and the project moved to **GitHub Actions**.
- **`release-new-version.yml` (#101):**
  - Maven Central release through `maven-release-plugin` from a non-SNAPSHOT version, tagged `v<version>`.
  - Triggered from the GitHub UI, with secrets stored in the repo.
  - Rebase merges are forbidden, to keep the tagged revision.
  - Later extended to create a GitHub Release draft and update the README and wiki.
- **`cross-compile.yml` (#102):** natives are built *only* by a transparent, auditable workflow, so that bad or malicious binaries can't be committed ("hypocrite committer"). It commits to a `<branch>-precompiled-natives` branch, which maintainers merge by hand. **Contributors must not include binaries in PRs** (wiki).
- **Linux natives must be built on the oldest possible ABI.** That meant Ubuntu 14.04 in Docker then, and dockcross images now. Windows targets are cross-compiled with MinGW (preferred over MSVC for its compatibility back to XP). macOS uses the downloaded SDKs.
- The Solaris build in VirtualBox was slow (about 30 minutes) and unreliable. tresf suggested deprecating Solaris with a 3.0.0 bump.
- The native version string comes from the pom's `release.version` (build-helper strips `-SNAPSHOT`). CMake falls back to parsing `pom.xml` for standalone builds. tresf insisted this fallback be kept (#102).
- 2.9.3 (#104) was the first release with all 12 natives rebuilt: linux 32/64/arm/arm64/ppc, osx 64/arm64, sunos 32/64, windows 32/64/arm64.

### Misc
- #73: the `sendBreak()` duration is in milliseconds. A doc PR was invited (closed 2022).
- #105: `SerialNativeInterface` is instantiated even though it's effectively static. Some JNI functions are non-static, so making it fully static would break JNI. This is left as a TODO (still in the code).

---

## 2021–2026 — hardening, releases 2.9.4 → 2.10.4 (#107–#207)

### Release history (from issues/PRs)
| Version | Date | Notes |
|---|---|---|
| 2.9.3 | 2021-08 | aarch64 (macOS/Linux), boot path (#92), poll-based `readBytes` (#90), module-info. **Windows baud-rate regression (#113).** The GitHub jar was built with the wrong JDK and was not JRE6-compatible (#110). |
| 2.9.4 | 2021-08 | Fixes #113: a warning-suppression cast in #91 truncated the baud rate to `BYTE`. Also Windows ARM64 static linking (#108/#109). |
| 2.9.5 | 2023-08 | The first natives built by the new dockcross pipeline (#140), which fixed ARMv6 Pi (#139). NPE in Windows port listing fixed (#135). |
| 2.9.6 | 2023-12 | RISC-V (#143/#144). Solaris dropped from CI (#149). PR CI (#151). Error handling: #137, #138, #146, #152, #155. JUnit CVE bump (#163). #160 deliberately postponed. |
| 2.10.0 | 2025-05 | **Minor bump for API behaviour changes:** `closePort`/`removeEventListener` no longer throw on vanished ports (#107/#181); exceptions now extend `IOException` (#160); Moxa port names (#180). **Regressions:** macOS target 10.8 broke notarization (#187); dockcross `latest` raised the glibc floor to 2.35 (#190). |
| 2.10.1 | 2025-06 | Notarization fix (#189). Pinned dockcross tag brings glibc back to 2.3 (#191). OSSRH → Central Portal (#186). **Windows RX regression** from #175 (#192). |
| 2.10.2 | 2025-06 | Zero-length reads allowed again (#193). |
| 2.10.3 | 2026-08 | `waitEvents` timeout (#201), Windows `openPort` leak (#203). **Shipped stale natives:** byte-identical to 2.10.2 (#205). Do not use. |
| 2.10.4 | 2026-08 | Re-release with natives actually rebuilt (#205, #206). Current upstream. |

**Lessons that recur:**
- "Fixing compiler warnings" in native code caused a real regression (#113).
- Validating input in native code caused a regression for legitimate 0-byte reads that the Windows event loop produces (#175 → #192).
- Floating CI images (`ubuntu-latest`, dockcross `latest`, `macos-latest`) silently change binary compatibility (#190, #201).
- Always check that release natives were actually rebuilt (compare hashes against the previous release, #205), and check glibc symbol versions and macOS `minos`/`sdk` (`show-file-info` ant target, #178).

### API behaviour & exceptions
- **#107 / #181 (2.10.0):** `closePort()` used to throw before reaching native close when a USB adapter had vanished (through `removeEventListener` → `setEventsMask`), leaving ghost COM ports on Windows.
  - Decision: closing and removing listeners should do best-effort cleanup and not throw.
  - `removeEventListener()` now returns `false` when no listener exists and throws only if the port vanished.
  - Native `CloseHandle`/`close` succeed even when the device is gone (tested by Vzor- on Linux and Windows).
  - The behaviour change was the reason for the 2.10.0 bump. #118 (pietrygamat's earlier attempt) was closed unmerged.
- **#123 (open):** you can't query whether a listener is attached. tresf says #181 replaced the boolean with an "event thread alive" check. He also noted that if the port closed while a listener was still attached, `eventThread.join(5000)` may be skipped, and suggested try/finally. **Not confirmed closed.**
- **#63 (open, response required):** the `removeEventListener` javadoc claimed it returns false. #181 changed the semantics, so this may now be resolvable.
- **#160 (2.10.0):** `SerialPortException` and `SerialPortTimeoutException` now extend `IOException`. Motivation: library authors shouldn't leak JSSC types or have to catch `Exception`.
  - It is **not** fully source-transparent: a `catch (IOException)` placed before `catch (SerialPortException)` no longer compiles. Hence the minor bump and the call for migration notes in the release notes.
  - `TYPE_NULL_NOT_PERMITTED` and the unused `TYPE_PARAMETER_IS_NOT_CORRECT` were removed; a null port name now throws NPE.
  - Listener misuse (`TYPE_LISTENER_ALREADY_ADDED`, …) is arguably not I/O. A separate exception family was suggested and left for later.
- **Native errors are raised as Java exceptions and wrapped,** so method signatures stay the same:
  - `SerialNativeInterface` methods throw `IOException`, and `SerialPort` wraps it with `SerialPortException.wrapNativeException(...)` (#146).
  - Unchecked exceptions (NPE, OOM, IllegalArgument) pass through unchanged (#152).
  - JNI pending exceptions are checked with `ExceptionCheck()` and returned (#137).
  - ioctl failures (#125 → #138) and read errors (#94 → #155) now raise exceptions.
  - Policy (tresf): changing a signature is acceptable if needed, but wrapping is preferred as a stop-gap.
- **Validating `byteCount` in native `readBytes` (#175)** must allow `0`. Windows fires RXCHAR events with an empty buffer, and existing apps read 0 bytes (#192/#193).

### Partial writes / write semantics (#129, #176, #195 — open)
- Users asked for `writeBytes` to return the number of bytes written (RTS/CTS flow control). hiddenalpha's **PR #195 is open.**
- Open questions on #195:
  - `int` vs `boolean` everywhere?
  - String overloads are ambiguous because of encoding.
  - Changing a native signature breaks old-native/new-jar combinations (acceptable with a minor bump).
  - tresf asked for a use-case rationale.
  - The committed `jssc_SerialNativeInterface.h` must be **updated, not deleted.** It is kept for legacy platforms where the full CMake/Maven build is impractical.
- #176: long writes at 921600 baud get truncated on Windows even with RTS/CTS. This might be partial writes (#129).
- #177 was declined: baud-rate constants are convenience only. Any `int` is passed straight to the OS. Windows accepts anything; *nix maps known rates and uses `ioctl` for non-standard rates on macOS.

### Event loop CPU (#53 → #201, 2.10.3)
- hiddenalpha added `SerialPort.setWaitEventsTimeoutMs(int)`. The native `waitEvents(handle, timeoutMs)` uses `poll` with a timeout.
- The default `-1` keeps the old busy-loop behaviour, so the change is opt-in. Values ≤ 0 other than -1 throw. The initial line-state query skips the timeout.
- A backward-compatible `waitEvents2` native was proposed and then dropped: the project doesn't promise that you can swap native binaries independently of the jar.
- tresf would like a recommended value documented (e.g. 2 ms) and wonders whether it should become the default, since Windows already blocks.
- **macOS doesn't use `poll`** (`HAVE_POLL=0` → `select`). This is unresolved: `poll` was broken on macOS 10.12, and a hang appears in the macOS `select` path (#155). `SerialNativeInterfaceTest` is `@Ignore`d with "TODO analyze where this osx hang comes from".

### Native robustness still open
- #95: race between `closePort` and `readBytes`. `SerialPort` methods are now synchronized (#90); the native side still lacks protection.
- #122: macOS `std::bad_alloc` on close. Possibly addressed by #175 (byteCount validation) and #185 (`<new>` header include fix for SDK 10.9), but **unconfirmed.**
- #126: `select()` returning -1 loops forever after unplug. hiddenalpha asked (2024) whether #146/#152/#155 fixed it; there was no answer.
- **#127 (factoritbv, open draft):** IOException on unplug, 100 ms `select` re-check, `interrupt()` support, and a `SerialPortStream` (InputStream/OutputStream).
  - It went back from `poll` to `select`, which brings back the fd ≥ 1024 limit. That was unacceptable to hiddenalpha. #128 added `FD_SETSIZE` checks and was closed.
  - Review found bugs: infinite recursion in `close()`, and `read()` returning a signed byte.
  - tresf converted it to a draft: **"split into smaller parts, then supersede"**. Much of the native part has since landed separately.
- #202/#203: Windows `openPort` JNI string leak and NULL dereference. Fixed in 2.10.3.
- **#207 (closed by reporter, no maintainer response):** Windows + JRE 17 + 2.10.4 crashes in `openPort` with a thread stack overflow. `-Xss4m` works around it. **Unexplained**; it may be specific to the 2.10.4 natives.

### Features requested and their status
- **InputStream/OutputStream (#183, open):** not available. #127 contains an implementation. "Feature contributions welcome; the project mostly keeps the library working."
- **DTR not toggled on open (#120, open):** `openPort()` drops DTR, and there's no way to configure lines before opening. No maintainer response.
- **Clear break / `ClearCommBreak` (#200, open):** proposals are a separate API or `sendBreak(-1)` as a magic flag. A *nix equivalent is needed (`ioctl TIOCCBRK`). No one can test it.
- **Inter-character gap for Modbus RTU (#172):** not supported. Closed as answered. Contributions welcome.
- **Android (#194):** not supported. PRs welcome.
- **Custom port-name patterns (#179, still open):**
  - Moxa `ttyM`, `ttyMXUSB` and `ttyMUE` were added to the Linux regex (#180).
  - A system property for "exotic" names (#182) was rejected because `SerialPortList.getPortNames(Pattern)` and its overloads already exist.
  - Unit tests for port-name patterns are still wanted. #179 itself could be closed.
- **JetBrains annotations (#169) rejected:**
  - Constants like `BAUDRATE_9600` aren't magic values, and raw ints must stay allowed.
  - Annotations add complexity for one IDE.
  - tresf: `OS_*` int constants would be better as an enum (a future refactor).
- **Serial flush / Arduino buffer (#199, open):** answered with flow-control advice (XON/XOFF, RTS/CTS). The user solved it with ACKs. It can be closed.
- **Native lib extraction (#165, open, response required):**
  - An empty `nativelib-loader_*` temp dir is created even when the boot path is used. This is an upstream native-lib-loader static-init issue.
  - tresf (2025): "In hindsight, I think the project would have been better off updating its old extraction code… If someone wants to spearhead [removing native-lib-loader], I would be receptive." Removing it would also remove the slf4j transitive dependency (#76, #164, #167).

### Platform / hardware issues
- **ARMv6 (#139):** the armhf binary crashed on Pi 1/Zero (ARMv6). The fix was building `linux_arm` with dockcross `linux-armv6-lts` (2.9.5).
- **#197 (open):** since 2.9.5, "Could not load the jssc library" on Ubuntu 16.04 **ARMv7** with Java 8; 2.9.4 works. This is a likely side effect of the ARMv6/dockcross switch (glibc floor or float ABI). Related to #35.
- **RISC-V (#143):** rv64 and rv32 were tested in QEMU only. **Physical hardware testing is still unchecked.** `riscv32` isn't in the PR CI matrix because Ubuntu has no toolchain for it.
- **Solaris dropped (#148/#149):** the GitHub VM boot-looped. tresf: "drop Solaris support entirely; those needing it can use a legacy version". The C++ `__sun` code is left in place, unmaintained.
- **macOS:**
  - The 10.9 SDK is needed for notarization (#54, #187).
  - #185 adds legacy `c++/4.2.1` include paths for the 10.9 SDK.
  - `MH_DYLIB_STUB` deprecation warnings from the old SDK are ignored for now (#178).
  - GitHub's `macos-latest` refuses SDKs below 14.0, so CI is pinned to `macos-14`, which is being deprecated (#201).
  - tresf's proposed long-term approach: `macos-15`/`macos-15-intel` runners with the current SDK plus `MACOSX_DEPLOYMENT_TARGET` (as LMMS does). Older deployment targets reportedly work without old SDKs, but this still needs verifying against notarization.
  - `CMAKE_CXX_STANDARD 11` was required for `static_assert` (#178).
- **Windows:**
  - #114 BSOD and #170 CP210x port listing were driver issues. #170 was the Silicon Labs 11.4 driver with `SerialSkipExternalNaming`. #171 added error handling to Windows port enumeration.
  - #196 (open): a CH340E on macOS reports RXCHAR value 32 for 1 byte. Probably a driver issue.
  - #198: macOS privacy (Full Disk Access) blocked I/O. That was user configuration.
- **glibc floor (#190/#191):**
  - The cross-compile pipeline pins `DOCK_CROSS_TAG: 20231208-4e4d3ad`, which brought the floor back down from 2.35. *Correction (2026-10-16, `readelf -V` on the committed binaries):* the maximum required version is 2.14 for `linux_64` (`memcpy`) and 2.28 for `linux_32` (`fcntl`), already in 2.9.6; most other symbols are 2.0–2.3. Per-target maxima for 2.10.4 are in CLAUDE.md.
  - tresf: pinning loses upstream fixes, and pinning Windows images isn't needed. Long-term options: `dockcross/manylinux*` images, or ask dockcross for an `-lts`-style legacy image.
  - The principle: **"The initial spirit of JSSC was to work on the oldest-possible distro"** (macOS ~12 years back, Windows back to XP).
  - A glibc matrix per JSSC version is in tresf's gist (linked from #190).

### Build / CI / process
- **#151:** PR CI on GitHub Actions (`build.yml`), without Docker, using apt toolchains. Travis was removed. Tests now load the **freshly built** native, which CMake copies to `target/classes/natives` (fixes #51).
- **#153:** natives are no longer copied into `src/main/resources-precompiled` by default. Use `-Pupdate-resources-precompiled` (the cross-compile workflow uses it).
  - tresf preferred the old behaviour, so forks would know where binaries go. He accepted the profile because a tracked file can't be git-ignored and because stray binaries kept getting committed by accident.
- **#154:** the cross-compile output is squashed into one "Precompiled natives @<rev>" commit. Merge it **without rebase or squash**, to keep the link between the CI run and the commit (#186 discussion).
- **#145:** don't apt-install CMake inside dockcross; it downgrades it.
- **#186:** OSSRH reached end of life, so releases use `central-publishing-maven-plugin`. GitHub Releases are created as drafts (`draft: true`) and published by hand. `MAVEN_GPG_PASSPHRASE` is read straight from the environment. Plugin version properties were renamed to `plugin.maven-*`. You can't re-publish a version to Central (2.10.1-alpha1 was the test).
- **#156/#158/#159:** test logging goes through log4j2 (`src/test/resources/log4j2.properties`) with Maven-like formatting, a `DisplayMethodNameRule` that prints test method names, and `log4j-slf4j-impl` to format native-lib-loader logs. Custom ANSI console classes were rejected (#158).
- **#161:** the `socat` process is now terminated after tests.
- #163: JUnit 4.13.2 for CVE-2020-15250 (mvnrepository flagging).
- **Chat moved Slack → Discord (#147, #168):** https://discord.gg/RBsUfE9sX9. Slack history was lost.
- **Merging:** only team members can merge (#203). The upstream maintainers are tresf, pietrygamat and hiddenalpha (C++); Vzor- helps.

### Project character (repeated maintainer statements)
- "Mostly in maintenance mode"; "a very small team of volunteers… predominantly to keep the library working, not necessarily add features" (#129, #183). Feature work is expected to come from interested parties as PRs.
- Keep it **Simple** (scream3r's principle): avoid extra dependencies, tooling and IDE-specific decorations (#47, #169).
- **Backwards compatibility:** never break the API without deprecation. Bump the minor version for behaviour changes (2.10.0). Keep Java 6 bytecode.
- **Bug reports:** a minimal reproducible snippet is required (#173, #174). Many crash reports turn out to be old JSSC 2.8 natives loaded from elsewhere (#65, #157, #174).

---

## Documentation & wiki audit (2026-10-15)

The wiki (`https://github.com/java-native/jssc.wiki.git`) has 8 pages: Home, Add-Dependency, Examples, Examples-Kotlin, Compiling, Cross-Compilation, Windows-Build-Environment, Publishing-a-Release. Since 2023, wiki history consists almost entirely of the automatic version bumps from `release-new-version.yml`. The last human edits were Cross-Compilation (2025-05) and the Solaris note (2023-12).

### Inconsistencies / stale content found
| Where | Problem | Truth (source) |
|---|---|---|
| Publishing-a-Release §Overview 4–5, checklist | Says deployment uses `nexus-staging-maven-plugin` and a manual release from OSSRH (`oss.sonatype.org`). | OSSRH is end-of-life. Releases now go through `central-publishing-maven-plugin` and the Central Portal, and the GitHub Release is created as a **draft** (`draft: true` in `release-new-version.yml`) to publish by hand (#186, `pom.xml` `maven-central-release` profile). |
| Publishing-a-Release §2 | Path `src/java/jssc/SerialNativeInterface.java`. | `src/main/java/jssc/SerialNativeInterface.java`. |
| Publishing-a-Release §Overview | The list numbers "5." twice. | Cosmetic. |
| Publishing-a-Release §"Building native libraries manually" → Solaris | A full Solaris build recipe. | Solaris was dropped from CI and support (#148/#149). The Solaris note was added to the wiki in 2023, but the recipe was never removed. |
| Publishing-a-Release §MacOS x86_64 manual | Uses `-DCMAKE_OSX_DEPLOYMENT_TARGET=10.8` and says "10.8-compatible binary". | A 10.8 target **fails notarization** (#187). CI uses deployment target 10.9 with SDK 10.9 (#189). |
| Publishing-a-Release §Ubuntu manual | "Use oldest possible Ubuntu (e.g. 14.04)". | Release natives come from **dockcross images pinned to `DOCK_CROSS_TAG`** (#140, #191). Hand-built natives aren't accepted in PRs (Cross-Compilation page). |
| Publishing-a-Release checklist | Mentions OSSRH staging verification. It doesn't say to verify that natives actually changed. | The 2.10.3 release shipped stale natives (#205). A step to compare native hashes and check glibc/minos is missing. |
| Cross-Compilation §Github Action | Output branch name `<selected_branch_name>_precompiled_natives`. | The workflow creates `<branch>-precompiled-natives` (hyphens). |
| Cross-Compilation §Linux ARM 64-bit | `mvn -P linux-aarch64`. | There is no such profile; it's `mvn -P aarch64` (pom, Compiling page). |
| Cross-Compilation §Windows 64-bit | "Cross-compile using `mingw32` toolchain" above `mvn -P mingw64`. | It should say `mingw64`. |
| Cross-Compilation §Linux ARM 32-bit | `g++-arm-linux-gnueabihf` + `-P armhf`. | That produces an ARMv7 binary. Release `linux_arm` is built for **ARMv6** with dockcross `linux-armv6-lts`, so a local armhf build is not equivalent and won't run on Pi 1/Zero (#139). |
| Cross-Compilation / Compiling | No RISC-V (`riscv64`, `riscv32` profiles) and no `armsf`/`dockcross` profiles listed. | Profiles exist in `pom.xml` (#143/#144, #151). |
| Compiling §Prerequisites (macOS) | "MacOSX10.9.sdk is used for release builds". | True for x86_64 only. arm64 uses `MacOSX11.0.sdk` with deployment target 11.0. |
| Compiling §Prerequisites (Java) | "JDK8+ required to build". | Also true. The release JDK is **11** (module-info + Java 6 bytecode), and CI uses 11. Building on JDK 12+ silently targets Java 7. |
| Windows-Build-Environment | VS 2019 / `adoptopenjdk.net`. | Dated (AdoptOpenJDK → Adoptium) but still workable. Release Windows natives come from MinGW/llvm-mingw in dockcross, not MSVC. |
| Examples | No mention of `SerialPortEvent.getPort()` (#86), `setWaitEventsTimeoutMs` (#201), `IOException` hierarchy (#160), or `getPortNames(Pattern)` for custom device names (#179/#182). | Could be documented. |
| README.md | Travis-CI badge. | Travis was removed in 2023 (#151). CI is GitHub Actions `build.yml`. |
| `src/main/resources-precompiled/README.md` | Lists old `mac_32`/`mac_64` paths and a "Next version" os-maven-plugin naming (`linux-x86_64-64`, …) that was never adopted. Says "a jar with these libraries is always built". | The layout in use is `natives/<os>_<suffix>` (linux_32/64/arm/arm64/ppc/riscv32/riscv64, osx_64/arm64, windows_32/64/arm64). |
| `pom.xml` | `dependency.logback.version` is defined but unused (logback was replaced by log4j2 in #159). | Cleanup. |
| `pom.xml` armhf/armsf profiles | Both map to `arm_32`; a TODO acknowledges the namespace conflict. | #35 is open. |

### CLAUDE.md
Updated from the wiki: release flow, contribution rule against committing natives, the `jssc.boot.library.path` override, and the `mvn clean` requirement between profiles.

### Wiki update (2026-10-16, merged)
GitHub wikis don't accept pull requests, so the proposal was opened on the fork as **pietrygamat/jssc#6**. Branch `wiki` holds the upstream wiki `master` (`27371e3`), and branch `wiki-factual-update` holds a single commit (`b1b4a2d`). It was pushed to `https://github.com/java-native/jssc.wiki.git` `master` the same day. The PR was then closed, because a wiki change cannot be merged through GitHub. The rows above for those three pages are resolved. The changes cover the table rows above:
- **Publishing-a-Release:**
  - Central Portal replaces OSSRH and nexus-staging.
  - The `src/main/java` path is corrected.
  - The list numbering is fixed.
  - The GitHub release is described as a draft.
  - New checklist item: confirm the natives differ from the previous release.
  - The manual build section describes the dockcross environment.
  - The Solaris section is removed.
  - The macOS x86_64 deployment target is 10.9.
- **Cross-Compilation:**
  - The `-precompiled-natives` branch suffix is corrected.
  - `mvn -P aarch64` replaces `linux-aarch64`.
  - The Windows 64-bit heading says `mingw64`.
  - A note says release `linux_arm` binaries are ARMv6.
  - New RISC-V 64-bit section. There are no riscv32 steps because Debian/Ubuntu ships no `riscv32` cross-compiler package.
- **Compiling:**
  - Adds the `armsf`, `riscv64`, `riscv32` and `dockcross` profiles.
  - Adds the macOS arm64 SDK (11.0).
- **Not changed (kept the PR small):** Examples, Windows-Build-Environment, README badge, `resources-precompiled/README.md`, pom cleanup. These stay tracked in BACKLOG.md (B-18, B-19, B-20).
