# JSSC backlog

Pending and deferred work for `java-native/jssc`. It was compiled on 2026-10-15 from every upstream issue, PR and review comment (#1–#207) and updated on 2026-10-20 (#208–#213), the wiki, and TODOs in the code. The background and the reasoning behind each item are in [JOURNAL.md](JOURNAL.md); build and architecture notes are in [CLAUDE.md](CLAUDE.md).

**Upstream state (2026-10-20):** `upstream/master` = `c9b3b32` (v2.10.4 + README bump, #208 JDK 20+ build, #210 archive.debian.org, #212 reproducible DLLs). Open upstream items: 23 issues and 3 PRs (#127, #195, #213).

## Ground rules for anyone picking up an item

- **Public API:** don't break it without deprecation. Behaviour or signature changes require a **minor** version bump plus release-note migration notes (see 2.10.0).
- **Bytecode:** keep Java 6 compatibility (animal-sniffer). Release builds use JDK 11.
- **Native signatures:** if you change a `native` method signature, update `windows/jssc.cpp`, `_nix_based/jssc.cpp` **and** the committed `src/main/cpp/jssc_SerialNativeInterface.h` (do not delete it, see #195).
- **Versions:** don't hand-edit `SerialNativeInterface.libVersion`; with #213 it is generated from the pom's `release.version` (`LibraryVersion`).
- **Natives in PRs:** don't commit rebuilt natives. They come only from the `cross-compile.yml` workflow (`<branch>-precompiled-natives`, merged without rebase or squash).
- **Native errors:** report them as Java exceptions (`IOException` from `SerialNativeInterface`, wrapped as `SerialPortException` in `SerialPort`). Don't loop forever or return silently.
- **Changing native validation:** check what the Windows event loop does, because it delivers 0-byte RXCHAR reads (#192).
- **Scope:** the maintainers treat the project as "maintenance mode". Feature work is welcome as PRs; keep it simple and avoid new runtime dependencies.

Legend: **P1** = correctness/crash/release-blocking, **P2** = important improvement, **P3** = nice to have / cleanup. Status: `open` = untouched, `partial` = some work landed, `needs-triage` = cause unknown, `needs-decision` = maintainers must decide first.

---

## P1 — crashes, hangs, regressions, release safety

### B-01 Windows: thread-stack overflow in `openPort` on 2.10.4 (needs-triage)
- **Refs:** #207 (closed by reporter, no maintainer response); possibly related to #203 (openPort changes in 2.10.3).
- **What:** on Windows with JRE 17, JSSC 2.10.4 crashes in `openPort` unless the JVM gets `-Xss4m`. 2.10.2 is not reported to have this problem.
- **Do:**
  - Reproduce on Windows x64 with the 2.10.2 and 2.10.4 fat jars.
  - Compare the stack frame of `Java_jssc_SerialNativeInterface_openPort`.
  - Check whether the 2.10.4 DLL was built differently: same `DOCK_CROSS_TAG`? `-O0` is intended (#9).
  - Review #201/#203 for large stack allocations or recursion.
- **Done when:** the root cause is found and fixed, or the report is shown to be invalid with evidence.

### B-02 Release pipeline must verify that natives were rebuilt (partial)
- **Refs:** #205 (2.10.3 shipped 2.10.2 binaries), #190 (glibc floor jumped to 2.35), #187 (bad macOS target), #178 (`show-file-info` minos), #211 (plan, agreed by tresf), #212, #213.
- **Landed / in review:** Windows DLLs are reproducible (#212, merged), so hashes are now comparable on every target. PR #213 (open, branch `version-info`) adds the `JSSC-IDENT` line (version, rev, target, origin) to every native, a VERSIONINFO resource to the DLLs, and generates `libVersion` from the pom, which removes the version drift by construction.
- **Do:** add an automated check to `cross-compile.yml` and/or `release-new-version.yml` (#211 point 5, planned as a follow-up to #213) that does the following:
  - Asserts each native's `JSSC-IDENT` `version` equals the jar's `LibraryVersion`, `target` equals the directory it sits in, `rev` matches the "Precompiled natives @<rev>" commit, and `origin` is `java-native/jssc` for a release.
  - Asserts each native's hash differs from the previous release whenever native sources changed, or at least prints old and new hashes.
  - Asserts the max `GLIBC_x.y` symbol version for Linux x86/x64 (expect ≤ 2.4, currently 2.3).
  - Asserts macOS `minos` (10.9 for x86_64, 11.0 for arm64).
- tresf's glibc script: gist `tresf/b00fd445ca90742b09af1b315d092eae` (linked in #190).
- **Done when:** the release cannot proceed silently with stale or incompatible natives.

### B-03 macOS CI runner / SDK strategy before `macos-14` is retired (open)
- **Refs:** #201 (macos-latest rejects SDK < 14.0), #178, #185, #187, #189, #54.
- **Current:** `build.yml` and `cross-compile.yml` pin `runs-on: macos-14`, download `MacOSX10.9.sdk`/`MacOSX11.0.sdk` from phracker/MacOSX-SDKs, and set `MACOSX_DEPLOYMENT_TARGET` through `$GITHUB_ENV`.
- **Proposal (tresf):** use `macos-15` + `macos-15-intel` with the bundled SDK and rely on `MACOSX_DEPLOYMENT_TARGET` (10.9 / 11.0), as LMMS does.
- **Alternative (#209, pietrygamat):** osxcross in a frozen container image, alongside the Linux/Windows ones (B-12). Undecided: is freezing an SDK reasonable given Apple's notarization rules?
- **Must verify:**
  - Apple notarization still passes. Apple requires the binary to be built against SDK ≥ 10.9, and a new SDK satisfies that.
  - The binary loads on old macOS (10.9 for x86_64).
  - `show-file-info` reports the expected `minos`.
- **Done when:** CI no longer depends on a deprecated runner and the release dylibs are verified as above.

### B-04 Infinite loop: `closePort` racing `readBytes` (open)
- **Refs:** #95 (open), #90 (synchronized Java side), #94 (fixed by #155), #126 (open), #127 (draft).
- **What:** in native `readBytes` on *nix, a concurrent `close(fd)` can leave the `poll`/`select` + `read` loop spinning. The Java `synchronized` methods narrow the window but don't cover direct `SerialNativeInterface` use or `select()` on Linux.
- **Do:**
  - Make the *nix read loop terminate on `POLLNVAL`/`EBADF`/`POLLHUP` and on `read()==0` (EOF), raising `IOException`.
  - Consider a bounded poll timeout so the loop can re-check the fd, as #127 does with 100 ms.
  - Keep `poll` (fd ≥ 1024, #90). `select` stays only as the macOS fallback.
- Then ask the reporters of #95/#126 to verify, or close the issues with an explanation.

### B-05 macOS `std::bad_alloc` crash on close (needs-triage)
- **Refs:** #122 (open since 2021), #175 (byteCount validation), #185 (`<new>` include fix).
- **Do:** try to reproduce on macOS with 2.10.4 (open, read with a listener, close). If it no longer reproduces, close the issue with that note. Otherwise inspect allocations in `readBytes` and `waitEvents` on the `select` path.

### B-06 macOS `select` path hang in tests (open)
- **Refs:** #155, `SerialNativeInterfaceTest` `@Ignore("TODO analyze where this osx hang comes from")`, `_nix_based/jssc.cpp` `HAVE_POLL` logic.
- **What:** `throwsIllegalArgumentExceptionIfPortHandleIllegal` hangs on macOS with `HAVE_POLL=0`. With `-DHAVE_POLL=1` it passes (pietrygamat).
- **Decide:** is `poll` acceptable on all supported macOS versions? `poll` on character devices was historically broken, especially on 10.12. Either fix the `select` path or enable `poll` on macOS, then remove the `@Ignore`.
- This also affects B-07 (the `waitEvents` timeout feature is `poll`-based).

---

## P2 — important improvements

### B-07 `waitEvents` timeout: docs, defaults, macOS parity (partial)
- **Refs:** #53, #78, #201 (merged in 2.10.3), `SerialPort.setWaitEventsTimeoutMs`.
- **Remaining:**
  - Document a recommended value (tresf suggested something like 2 ms) in the javadoc and the wiki Examples page.
  - Decide whether to make it the default, since Windows already blocks. That would be a behaviour change and needs a minor bump.
  - Check the behaviour on macOS, which uses `select` (`HAVE_POLL=0`). pietrygamat noted a possible problem when the timeout is set but `HAVE_POLL=0`.
  - pietrygamat's remaining review note: provide a way to disable the timeout again. hiddenalpha agreed; check whether that landed.

### B-08 `writeBytes` partial writes / return count (needs-decision; PR open)
- **Refs:** #129 (open, help wanted), **PR #195 (open)**, #176 (open: truncation at 921600 baud on Windows), #96.
- **Open questions from the #195 review:**
  1. `int` vs `boolean` for all `write*` overloads. String overloads are ambiguous because of encoding.
  2. The native signature change breaks old-native/new-jar combinations. tresf: OK with a minor bump.
  3. tresf asked for a written use-case rationale (RTS/CTS flow control, partial `write(2)`).
  4. Keep and update `jssc_SerialNativeInterface.h`.
  5. Remove the duplicated `snprintf` shim.
- **Also check:** on Windows, does `WriteFile` with overlapped I/O plus `GetOverlappedResult` report short writes, and does that explain #176?

### B-09 Event listener API leftovers (partial)
- **Refs:** #123 (open, bug), #63 (open, response required), #107/#181 (merged), #116.
- **Do:**
  - Expose whether a listener is attached (e.g. `isEventListenerAdded()` or similar). That needs maintainer agreement on naming.
  - Make sure `removeEventListener`/`closePort` still terminate and join the event thread when the port was closed or vanished first. tresf suggested try/finally around `eventThread.join(5000)` (#123 comment, 2025-01-09).
  - Fix the javadoc so return and throw semantics match the 2.10.0 behaviour.
  - Then close #63 and #123.
- Vzor- and tresf wanted tests or a manual QA checklist for unplug scenarios (#107).

### B-10 Replace or remove `native-lib-loader` (needs-decision)
- **Refs:** #165 (open: empty temp dir always created), #21, #23, #64, #76, #164, #167 (slf4j warnings), #80, #75, #112 (upstream dependence), #24, #35 (no hf/sf distinction).
- **Context:** tresf (2025): "in hindsight… better off updating its old extraction code… would be receptive" to removing it.
- **Benefits:** no slf4j transitive dependency or warning, no temp-dir side effect, control over ARM hf/sf selection (#35), fewer upstream blockers.
- **Constraints:**
  - Keep the `jssc.boot.library.path` semantics (#92).
  - Keep the `natives/<os>_<suffix>/` layout.
  - Keep the system-library-path-first lookup order.
  - Keep the module descriptor valid (`requires transitive org.scijava.nativelib` must change).
  - Keep the fat jar working.
  - Java 6.
  - This is a dependency/API surface change, so it needs a minor bump.
- **First step:** a design note on the issue/Discord for maintainer sign-off.

### B-11 ARM 32-bit compatibility: ARMv6 vs ARMv7, hard-float vs soft-float (open)
- **Refs:** #197 (open: ARMv7 Ubuntu 16.04 / Java 8 fails to load since 2.9.5), #35 (open: hf/sf distinction), #139 (ARMv6 fix), #26, #48, pom TODOs at the `armhf`/`armsf` profiles.
- **Do:**
  - Reproduce #197. Candidates are a glibc symbol version from the `linux-armv6-lts` image, or a float ABI/loader mismatch. Use `readelf -V`/`readelf -A` on `natives/linux_arm/libjssc.so` from 2.9.4 and 2.10.4.
  - Aim for one `linux_arm` binary that loads on ARMv6 (Pi 1/Zero) **and** ARMv7 hard-float.
  - If soft-float is ever needed, that requires B-10 or an upstream loader change.
- **Hardware:** none of the maintainers routinely test on ARM32. Ask the reporters.

### B-12 Stable cross-compile environment and glibc floor (partial)
- **Refs:** #190, #191, #209 (open), #210, `DOCK_CROSS_TAG: 20231208-4e4d3ad` in `cross-compile.yml`.
- **Context:** pinning keeps glibc 2.3 for x86/x64 but freezes the toolchains. tresf: pinning the Windows images isn't needed. The pinned images are Debian bullseye (EOL), and apt inside them broke (#209); #210 (merged) points apt at `archive.debian.org` as a stopgap.
- **Proposal (pietrygamat, #209):** build the cross-compile images ourselves once, publish them to ghcr.io in a repo under the java-native org, and never `docker pull`/`apt update` during a native build. Work in progress on the fork branch `cross-builder`. Options are on the fork wiki page "Keeping the jssc native build stable". tresf is also fine with dropping old Linuxes.
- **Other options:** `dockcross/manylinux2014-x86`/`manylinux_2_28-x64` images; or ask dockcross for `-lts`-style legacy images; or unpin Windows only.
- **Done when:** the glibc floor stays ≤ 2.17 (ideally the current 2.3–2.4) on every Linux arch and is verified by B-02.

### B-13 InputStream/OutputStream adapter (open)
- **Refs:** #183 (open), #127 (draft PR with `SerialPortStream`), #160 (exceptions now `IOException`).
- **Do:** salvage the Java part of #127 as a small, separate PR, as tresf asked: "split… then supersede". Fix the review findings: infinite recursion in `SerialPortInputStream.close()`, and `read()` must return 0–255 rather than a signed byte.
- It must not depend on the #127 native changes (which went back to `select`). Close #127 once the parts have landed or been rejected.

### B-14 DTR/RTS state on `openPort` (open)
- **Refs:** #120 (open, no response).
- **What:** opening a port drops DTR. There's no way to open without toggling the control lines, which resets Arduino-like devices and breaks some hardware.
- **Do:** investigate per OS (*nix `open` + `termios`/`TIOCMBIC`; Windows `DCB.fDtrControl` in `openPort`/`setParams`). Propose an opt-in API, for example an `openPort` overload or a system property, without changing default behaviour.

### B-15 Clear-break API (open)
- **Refs:** #200 (open).
- **What:** the user wants to set and clear break independently (Windows `SetCommBreak`/`ClearCommBreak`; *nix `ioctl(TIOCSBRK/TIOCCBRK)`). Today only `sendBreak(duration)` exists.
- **Decide:** a new method pair (`setBreak(boolean)`) vs `sendBreak(-1)` as a magic flag (tresf leaned towards the flag; it needs a decision). It must be implemented on both native sides. A native signature change means a header update and a minor bump.

### B-16 USB device metadata and disconnect notification (open, help wanted)
- **Refs:** #69 (VID/PID), #89 (disconnect events), prior art scream3r/java-simple-serial-connector#113.
- **Do:** a design proposal first. On Windows: SetupAPI hardware IDs. On Linux: `/dev/serial/by-id`, sysfs. On macOS: IOKit. Keep the existing `getPortNames` API unchanged.
- This is the largest feature on the list. It needs maintainer agreement before code.

### B-17 Better / more unit tests (partial)
- **Refs:** #37 (open since 2019), #47, #151, #155, #182 (port-pattern tests were wanted), #107 (unplug scenarios).
- **Ideas:**
  - Port-name regex tests per OS, including the Moxa names and `getPortNames(Pattern)`.
  - Listener lifecycle tests with `socat`.
  - A test that a 0-byte `readBytes` is allowed (#192).
  - Re-enable the macOS test from B-06.
  - Split the threading out of `VirtualPortRule`, as tresf suggested in #47.
- Windows virtual ports still aren't feasible (signed drivers).

---

## P3 — cleanup, docs, housekeeping

### B-18 Wiki corrections (partially done)
The wiki is a separate git repo, `https://github.com/java-native/jssc.wiki.git`. Publishing-a-Release, Cross-Compilation and Compiling were corrected in wiki commit `b1b4a2d` (2026-10-16, reviewed as pietrygamat/jssc#6). Remaining:
- **Examples:** add `event.getPort()`, `setWaitEventsTimeoutMs`, `getPortNames(Pattern)`, and the `IOException` hierarchy (catch order).
- **Windows-Build-Environment:** AdoptOpenJDK → Adoptium. Note that release DLLs come from MinGW/llvm-mingw.
- **Publishing-a-Release:** once B-02 lands, replace the manual "natives differ" checklist item with the automated check.

### B-19 Repo docs cleanup (open)
- README: remove the stale Travis-CI badge; optionally add a GitHub Actions badge.
- `src/main/resources-precompiled/README.md`: rewrite it to describe the layout in use (`linux_32/64/arm/arm64/ppc/riscv32/riscv64`, `osx_64/arm64`, `windows_32/64/arm64`), and remove the unused "next version" naming and the `mac_*` paths.
- Release-notes template: include migration notes when exception or API behaviour changes (#160 lesson).

### B-20 pom/CMake tidy-ups (open)
- Remove the unused `dependency.logback.version` property (logback was replaced in #159).
- Remove or replace stale TODOs once the related items are resolved: `armhf`/`armsf` namespace TODO (B-11); CMake `# TODO: Handle arm, hardfloat`; `-Werror`/`/WX` TODOs (#15 history: warnings-as-errors was dropped; be careful, see the #113 lesson).
- `SerialNativeInterface`: "TODO: This class is effectively static, why instantiate it?" Not easily fixable because some JNI functions are instance methods (#105). Only revisit with a native-signature-changing release.
- `MH_DYLIB_STUB` deprecation warnings from SDK 10.9 (#178): ignored for now; revisit with B-03.

### B-21 Code style tooling (deferred)
- **Refs:** #12 (open, milestone 5.0.0), #61 (dup).
- tresf: a long-term goal only; ternaries are allowed; unicode escapes are OK for invisible characters. Low priority. Don't introduce a formatter that rewrites the whole codebase without maintainer agreement.

### B-22 OSGi bundle metadata (deferred)
- **Refs:** #39 (open, help wanted).
- Adding the felix `maven-bundle-plugin` manifest headers would be low-risk. No owner and no demand since 2019.

### B-23 Replace int `OS_*` constants with an enum (deferred)
- **Refs:** #169 discussion (tresf).
- This is public API (`SerialNativeInterface.getOsType()`), so it needs deprecation. Low priority.

### B-24 Triage / close stale issues (housekeeping)
These look resolvable. Propose closing them, with a comment, to the maintainers:

| Issue | Suggested action |
|---|---|
| #179 | Moxa names landed (#180); custom patterns are possible through `getPortNames(Pattern)` (#182 discussion). Close after optional tests (B-17). |
| #199 | User solved it with ACKs; flow-control advice given. Close. |
| #124 | Chunked reads are expected behaviour. "Response required" since 2025-01. Close. |
| #165 | Title answered (boot path, #92). The remaining part (empty temp dir) is tracked in B-10. Close or retitle. |
| #63 | Resolve with B-09. |
| #196 | CH340E on macOS. Likely a driver issue; ask for a Windows/Linux baseline, or close. |
| #126 | Ask whether #146/#152/#155 fixed it (hiddenalpha asked in 2024 and got no answer). Fold into B-04. |
| #127 | Close once B-13 and B-04 supersede it. |

---

## Explicitly decided against (do **not** do without new maintainer agreement)

| Proposal | Decision | Refs |
|---|---|---|
| Maven wrapper `mvnw` | Rejected: CMake is required anyway; Maven is easy to obtain | #27 |
| `defaultGoal clean install` | Rejected: forces a full rebuild for casual users | #28 |
| git-lfs for natives | Rejected (git-lfs#1906) | #14 |
| Split into `jssc-native` / `libloader` / parent repos | Rejected: one repo keeps things debuggable | #40, #23 |
| Shaded fat jar | Rejected: breaks slf4j binding; unshaded `jar-with-dependencies` instead | #21, #43 |
| slf4j as `provided` | Rejected: the fat jar must run standalone | #64 |
| Fail the build on JDK 12+ | Rejected: auto-target Java 7 with a warning instead | #81 |
| Natives in contributor PRs | Not accepted: CI-built only | wiki Cross-Compilation, #102 |
| JetBrains `@MagicConstant` annotations | Rejected | #169 |
| Extra baud-rate constants (e.g. 921600) | Rejected: any int is accepted | #177 |
| "Exotic names" system property | Rejected: `getPortNames(Pattern)` exists | #182 |
| NodeJS/Electron bindings | Out of scope | #56 |
| Android support | Not supported; PRs welcome but no maintainer expertise | #194 |
| Modbus inter-character gap handling | Not planned; contributions welcome | #172 |
| Solaris binaries | Dropped (C++ code kept, unmaintained) | #148, #149 |
| Going back from `poll` to `select` on Linux | Rejected: fd ≥ 1024 segfaults | #90, #127, #128 |
| Custom ANSI console classes for test logs | Replaced by log4j2 config | #158, #159 |
