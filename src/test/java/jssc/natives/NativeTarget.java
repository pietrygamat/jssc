package jssc.natives;

import java.io.File;
import java.io.IOException;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Every precompiled native library jssc ships, and the compatibility policy it must satisfy.
 *
 * <p>The limits are a ratchet: they describe the oldest systems the currently shipped binaries
 * load on. A toolchain or container update that silently raises a requirement fails the
 * build instead of reaching users (see #190, #197). Lowering a limit is always welcome;
 * raising one must be a deliberate, documented decision.</p>
 */
enum NativeTarget {

    // ---- Linux: maximum GLIBC_x.y symbol version the library may require ----
    // TODO #197: linux_32 and linux_arm needed only GLIBC_2.4 up to 2.9.4; 2.28 comes from fcntl() alone.
    LINUX_32("linux_32", ElfFile.EM_386, 32, ByteOrder.LITTLE_ENDIAN, "2.28"),
    LINUX_64("linux_64", ElfFile.EM_X86_64, 64, ByteOrder.LITTLE_ENDIAN, "2.14"),
    LINUX_ARM("linux_arm", ElfFile.EM_ARM, 32, ByteOrder.LITTLE_ENDIAN, "2.28"),
    LINUX_ARM64("linux_arm64", ElfFile.EM_AARCH64, 64, ByteOrder.LITTLE_ENDIAN, "2.17"),
    // TODO: linux_ppc needed only GLIBC_2.17 up to 2.9.4; 2.34 comes from __pthread_key_create/pthread_once.
    LINUX_PPC("linux_ppc", ElfFile.EM_PPC64, 64, ByteOrder.LITTLE_ENDIAN, "2.34"),
    LINUX_RISCV32("linux_riscv32", ElfFile.EM_RISCV, 32, ByteOrder.LITTLE_ENDIAN, "2.34"),
    LINUX_RISCV64("linux_riscv64", ElfFile.EM_RISCV, 64, ByteOrder.LITTLE_ENDIAN, "2.34"),

    // ---- macOS: maximum deployment target (LC_BUILD_VERSION / LC_VERSION_MIN_MACOSX) ----
    OSX_64("osx_64", MachOFile.CPU_TYPE_X86_64, "10.9"),
    OSX_ARM64("osx_arm64", MachOFile.CPU_TYPE_ARM64, "11.0"),

    // ---- Windows: maximum PE subsystem version (= oldest Windows able to load the DLL) ----
    WINDOWS_32("windows_32", PeFile.IMAGE_FILE_MACHINE_I386, false, "5.1"),   // XP
    WINDOWS_64("windows_64", PeFile.IMAGE_FILE_MACHINE_AMD64, true, "5.2"),   // XP x64 / Server 2003
    WINDOWS_ARM64("windows_arm64", PeFile.IMAGE_FILE_MACHINE_ARM64, true, "10.0");

    enum Format { ELF, MACHO, PE }

    /** Libraries guaranteed to exist on any glibc-based Linux, plus the dynamic loader itself */
    static final List<Pattern> LINUX_ALLOWED_DEPENDENCIES = patterns(
            "libc\\.so\\.6", "libm\\.so\\.6", "libdl\\.so\\.2", "libpthread\\.so\\.0", "librt\\.so\\.1",
            "ld-linux[-a-z0-9_.]*\\.so\\.[0-9]+", "ld64\\.so\\.[0-9]+", "ld\\.so\\.[0-9]+");

    /** Shipped with every Windows version; anything else (libstdc++, libgcc, winpthread, UCRT) must be linked statically */
    static final List<Pattern> WINDOWS_ALLOWED_DEPENDENCIES = patterns(
            "(?i)kernel32\\.dll", "(?i)advapi32\\.dll", "(?i)msvcrt\\.dll");

    static final List<Pattern> MACOS_ALLOWED_DEPENDENCIES = patterns(
            "/usr/lib/libSystem\\.B\\.dylib", "/usr/lib/libc\\+\\+\\.1\\.dylib",
            "/System/Library/Frameworks/(IOKit|CoreFoundation)\\.framework/.*");

    /** Only the glibc namespace is allowed; GLIBCXX_/CXXABI_/GCC_ would mean libstdc++/libgcc leaked in dynamically */
    static final String GLIBC_VERSION_PREFIX = "GLIBC_";

    final String directory;
    final Format format;
    final int machine;
    final int bits;
    final ByteOrder byteOrder;
    /** max GLIBC version (ELF), max deployment target (Mach-O) or max subsystem version (PE) */
    final String maxPlatformVersion;

    NativeTarget(String directory, int elfMachine, int bits, ByteOrder byteOrder, String maxGlibc) {
        this(directory, Format.ELF, elfMachine, bits, byteOrder, maxGlibc);
    }

    NativeTarget(String directory, int cpuType, String maxMacOs) {
        this(directory, Format.MACHO, cpuType, 64, ByteOrder.LITTLE_ENDIAN, maxMacOs);
    }

    NativeTarget(String directory, int peMachine, boolean is64, String maxWindows) {
        this(directory, Format.PE, peMachine, is64 ? 64 : 32, ByteOrder.LITTLE_ENDIAN, maxWindows);
    }

    NativeTarget(String directory, Format format, int machine, int bits, ByteOrder byteOrder, String maxPlatformVersion) {
        this.directory = directory;
        this.format = format;
        this.machine = machine;
        this.bits = bits;
        this.byteOrder = byteOrder;
        this.maxPlatformVersion = maxPlatformVersion;
    }

    String fileName() {
        switch (format) {
            case ELF: return "libjssc.so";
            case MACHO: return "libjssc.dylib";
            default: return "jssc.dll";
        }
    }

    List<Pattern> allowedDependencies() {
        switch (format) {
            case ELF: return LINUX_ALLOWED_DEPENDENCIES;
            case MACHO: return MACOS_ALLOWED_DEPENDENCIES;
            default: return WINDOWS_ALLOWED_DEPENDENCIES;
        }
    }

    File file(File nativesDir) {
        return new File(new File(nativesDir, directory), fileName());
    }

    BinaryFile open(File nativesDir) throws IOException {
        File f = file(nativesDir);
        switch (format) {
            case ELF: return new ElfFile(f);
            case MACHO: return new MachOFile(f);
            default: return new PeFile(f);
        }
    }

    static NativeTarget forDirectory(String directory) {
        for (NativeTarget t : values()) {
            if (t.directory.equals(directory)) {
                return t;
            }
        }
        return null;
    }

    /**
     * Directory holding one sub-directory per target. Defaults to the precompiled natives in the
     * source tree; override with {@code -Djssc.natives.dir=...} to check freshly built binaries.
     */
    static File nativesDir() {
        String override = System.getProperty("jssc.natives.dir");
        if (override != null && !override.isEmpty()) {
            return new File(override);
        }
        return new File(System.getProperty("basedir", "."), "src/main/resources-precompiled/natives");
    }

    /**
     * Whether the natives must match the Java sources exactly. Off during development, where the
     * cross-compile workflow legitimately runs the tests after bumping libVersion but before the
     * natives are rebuilt. On for release builds, or with {@code -Djssc.natives.strict=true}.
     */
    static boolean strict() {
        return Boolean.getBoolean("jssc.natives.strict") || !projectVersion().endsWith("-SNAPSHOT");
    }

    /** The version about to be released: {@code -Djssc.release.version}, else the project version without qualifier */
    static String releaseVersion() {
        String explicit = System.getProperty("jssc.release.version");
        if (explicit != null && !explicit.isEmpty()) {
            return explicit;
        }
        return projectVersion().replaceFirst("-.*$", "");
    }

    private static String projectVersion() {
        String v = System.getProperty("jssc.project.version");
        if (v == null || v.isEmpty() || v.startsWith("${")) {
            return "unknown-SNAPSHOT"; // e.g. run from an IDE without the surefire configuration
        }
        return v;
    }

    /** Numeric comparison of dotted versions, e.g. "2.4" &lt; "2.28" */
    static int compareVersions(String a, String b) {
        String[] pa = a.split("\\.");
        String[] pb = b.split("\\.");
        for (int i = 0; i < Math.max(pa.length, pb.length); i++) {
            int va = i < pa.length ? Integer.parseInt(pa[i]) : 0;
            int vb = i < pb.length ? Integer.parseInt(pb[i]) : 0;
            if (va != vb) {
                return va < vb ? -1 : 1;
            }
        }
        return 0;
    }

    @Override
    public String toString() {
        return directory;
    }

    private static List<Pattern> patterns(String... regexes) {
        Pattern[] result = new Pattern[regexes.length];
        for (int i = 0; i < regexes.length; i++) {
            result[i] = Pattern.compile(regexes[i]);
        }
        return Collections.unmodifiableList(Arrays.asList(result));
    }
}
