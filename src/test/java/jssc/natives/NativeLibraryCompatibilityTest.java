package jssc.natives;

import jssc.junit.rules.DisplayMethodNameRule;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

/**
 * Static analysis of every precompiled native library, so that toolchain regressions are caught
 * without access to the target hardware or OS. Nothing is loaded or executed; binaries are parsed.
 *
 * <p>Catches e.g. a newer glibc requirement (#190, #197), a dynamically linked C++ runtime,
 * a binary built for the wrong architecture/ABI, or a binary built from stale sources.</p>
 */
@RunWith(Parameterized.class)
public class NativeLibraryCompatibilityTest extends DisplayMethodNameRule {

    @Parameterized.Parameters(name = "{0}")
    public static Collection<Object[]> targets() {
        List<Object[]> params = new ArrayList<Object[]>();
        for (NativeTarget t : NativeTarget.values()) {
            params.add(new Object[]{t});
        }
        return params;
    }

    private final NativeTarget target;
    private BinaryFile binary;

    public NativeLibraryCompatibilityTest(NativeTarget target) {
        this.target = target;
    }

    @Before
    public void open() throws Exception {
        File f = target.file(NativeTarget.nativesDir());
        assertTrue("Missing native library " + f, f.isFile());
        binary = target.open(NativeTarget.nativesDir());
    }

    @Test
    public void isBuiltForTheTargetArchitecture() {
        String what = target + " is " + binary.describeArchitecture();
        if (binary instanceof ElfFile) {
            ElfFile elf = (ElfFile) binary;
            assertEquals(what + ": ELF machine", target.machine, elf.machine);
            assertEquals(what + ": ELF class", target.bits == 64, elf.is64);
            assertEquals(what + ": byte order", target.byteOrder, elf.buf.order());
        } else if (binary instanceof PeFile) {
            PeFile pe = (PeFile) binary;
            assertEquals(what + ": PE machine", target.machine, pe.machine);
            assertEquals(what + ": PE32+", target.bits == 64, pe.is64);
        } else {
            assertEquals(what + ": Mach-O cputype", target.machine, ((MachOFile) binary).cpuType);
        }
    }

    @Test
    public void usesTheExpectedFloatingPointAbi() {
        assumeTrue(binary instanceof ElfFile);
        ElfFile elf = (ElfFile) binary;
        if (elf.machine == ElfFile.EM_ARM) {
            // linux_arm is the hard-float (armhf) build, see the TODO on os.target.arch in pom.xml
            assertEquals(target + ": EABI version", 5, elf.flags >>> 24);
            assertTrue(target + ": must use the hard-float ABI (EF_ARM_ABI_FLOAT_HARD)",
                    (elf.flags & ElfFile.EF_ARM_ABI_FLOAT_HARD) != 0);
            assertEquals(target + ": Tag_ABI_VFP_args must pass floats in VFP registers", 1, elf.armVfpArgs);
        } else if (elf.machine == ElfFile.EM_RISCV) {
            assertEquals(target + ": must use the double-float ABI (lp64d/ilp32d)",
                    ElfFile.EF_RISCV_FLOAT_ABI_DOUBLE, elf.flags & ElfFile.EF_RISCV_FLOAT_ABI_MASK);
        }
    }

    @Test
    public void runsOnArmV6() {
        assumeTrue(target == NativeTarget.LINUX_ARM);
        ElfFile elf = (ElfFile) binary;
        // Raspberry Pi 1 / Zero are ARMv6; anything newer than v6KZ would SIGILL there
        int v6kz = 7;
        assertTrue(target + ": Tag_CPU_arch is " + ElfFile.armCpuArchName(elf.armCpuArch)
                        + ", must be at most " + ElfFile.armCpuArchName(v6kz),
                elf.armCpuArch >= 0 && elf.armCpuArch <= v6kz);
    }

    @Test
    public void dependsOnlyOnSystemLibraries() {
        List<String> unexpected = new ArrayList<String>();
        for (String dep : binary.dependencies()) {
            if (!matchesAny(target.allowedDependencies(), dep)) {
                unexpected.add(dep);
            }
        }
        assertTrue(target + " links against libraries not present on every target system " + unexpected
                + " (all dependencies: " + binary.dependencies() + "). Link them statically.", unexpected.isEmpty());
    }

    @Test
    public void requiresNoNewerGlibcThanAllowed() {
        assumeTrue(binary instanceof ElfFile);
        Map<String, Set<String>> required = ((ElfFile) binary).requiredVersions();
        List<String> problems = new ArrayList<String>();
        for (Map.Entry<String, Set<String>> e : required.entrySet()) {
            String version = e.getKey();
            if (!version.startsWith(NativeTarget.GLIBC_VERSION_PREFIX)) {
                problems.add(version + " (only " + NativeTarget.GLIBC_VERSION_PREFIX + "* symbol versions are allowed)"
                        + " needed by " + e.getValue());
                continue;
            }
            String number = version.substring(NativeTarget.GLIBC_VERSION_PREFIX.length());
            if (!number.matches("[0-9]+(\\.[0-9]+)*")) {
                problems.add(version + " needed by " + e.getValue());
            } else if (NativeTarget.compareVersions(number, target.maxPlatformVersion) > 0) {
                problems.add(version + " needed by " + e.getValue());
            }
        }
        assertTrue(target + " requires a newer glibc than GLIBC_" + target.maxPlatformVersion + ": " + problems
                + ". Systems with an older glibc fail with \"version `GLIBC_x.y' not found\"."
                + " Build against an older sysroot or bind the symbols to older versions with .symver.",
                problems.isEmpty());
    }

    @Test
    public void doesNotRequireAnExecutableStack() {
        assumeTrue(binary instanceof ElfFile);
        Boolean execStack = ((ElfFile) binary).executableStack;
        // glibc >= 2.41 refuses to dlopen() libraries requesting an executable stack
        assertNotNull(target + " has no PT_GNU_STACK header, loaders assume an executable stack", execStack);
        assertTrue(target + " requests an executable stack (PT_GNU_STACK has PF_X); link with -z noexecstack",
                !execStack);
    }

    @Test
    public void requiresNoNewerWindowsThanAllowed() {
        assumeTrue(binary instanceof PeFile);
        String version = ((PeFile) binary).subsystemVersion;
        assertTrue(target + " has subsystem version " + version + ", allowed at most " + target.maxPlatformVersion
                        + ". Older Windows versions refuse to load it.",
                NativeTarget.compareVersions(version, target.maxPlatformVersion) <= 0);
    }

    @Test
    public void requiresNoNewerMacOsThanAllowed() {
        assumeTrue(binary instanceof MachOFile);
        String version = ((MachOFile) binary).minOsVersion;
        assertNotNull(target + " does not record a minimum macOS version", version);
        assertTrue(target + " requires macOS " + version + ", allowed at most " + target.maxPlatformVersion
                        + ". Check MACOSX_DEPLOYMENT_TARGET / SDK of the build.",
                NativeTarget.compareVersions(version, target.maxPlatformVersion) <= 0);
    }

    @Test
    public void exportsExactlyTheJniMethodsOfSerialNativeInterface() throws Exception {
        Set<String> expected = JniSymbols.expectedSymbols("jssc.SerialNativeInterface");
        Set<String> exported = new TreeSet<String>();
        for (String s : binary.exportedSymbols()) {
            if (s.startsWith(JniSymbols.PREFIX)) {
                exported.add(s);
            }
        }
        Set<String> missing = new TreeSet<String>(expected);
        missing.removeAll(exported);
        Set<String> stale = new TreeSet<String>(exported);
        stale.removeAll(expected);
        requireFresh(target + " is missing JNI functions " + missing
                + " (UnsatisfiedLinkError at runtime). Rebuild the natives.", missing.isEmpty());
        requireFresh(target + " exports JNI functions without a matching native method " + stale
                + ". The binary was probably built from different sources. Rebuild the natives.", stale.isEmpty());
    }

    @Test
    public void wasBuiltFromTheCurrentSources() throws Exception {
        String libVersion = JavaLibVersion.read();
        requireFresh(target + " does not contain version string \"" + libVersion + "\" from SerialNativeInterface.libVersion;"
                        + " it was built from other sources. Rebuild the natives.",
                binary.containsVersionString(libVersion));
    }

    /**
     * Natives lagging behind the Java sources are normal during development (they are rebuilt by
     * the cross-compile workflow), so outside of strict mode this is reported as a skipped test.
     */
    private static void requireFresh(String message, boolean fresh) {
        if (NativeTarget.strict()) {
            assertTrue(message, fresh);
        } else {
            assumeTrue(message, fresh);
        }
    }

    private static boolean matchesAny(List<Pattern> patterns, String s) {
        for (Pattern p : patterns) {
            if (p.matcher(s).matches()) {
                return true;
            }
        }
        return false;
    }
}
