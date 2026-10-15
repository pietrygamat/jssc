package jssc;

import jssc.NativeLibraryLoader.FloatAbi;
import jssc.junit.rules.DisplayMethodNameRule;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.CountDownLatch;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeTrue;

/**
 * Unit tests for {@link NativeLibraryLoader}. Nothing is actually loaded: <code>System.load*</code> are stubbed and
 * fake libraries come from <code>src/test/resources/jssc-test-natives</code>.
 */
public class NativeLibraryLoaderTest extends DisplayMethodNameRule {

    private static final String VERSION = "9.8.7";
    private static final String RESOURCE_ROOT = "jssc-test-natives/";

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    private File tmpDir;

    @Before
    public void setUp() throws IOException {
        tmpDir = folder.newFolder("tmp");
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Test double
    // ---------------------------------------------------------------------------------------------------------------

    private static class StubLoader extends NativeLibraryLoader {
        boolean systemPathWorks = false;
        FloatAbi armAbi = FloatAbi.UNKNOWN;
        /** loadFile() fails with this message for files whose path contains the key */
        final List<String[]> failures = new ArrayList<String[]>();
        final List<File> loaded = Collections.synchronizedList(new ArrayList<File>());

        StubLoader(Properties props) {
            super(props, VERSION, RESOURCE_ROOT);
        }

        @Override
        void loadLibrary(String name) {
            assertEquals("jssc", name);
            if (!systemPathWorks) {
                throw new UnsatisfiedLinkError("no jssc in java.library.path");
            }
        }

        @Override
        void loadFile(File file) {
            if (!file.isFile()) {
                throw new UnsatisfiedLinkError("Can't load library: " + file);
            }
            for (String[] failure : failures) {
                if (file.getPath().contains(failure[0])) {
                    throw new UnsatisfiedLinkError(failure[1]);
                }
            }
            loaded.add(file);
        }

        @Override
        FloatAbi detectArmFloatAbi() {
            return armAbi;
        }
    }

    private Properties props(String osName, String osArch) {
        Properties p = new Properties();
        p.setProperty("os.name", osName);
        p.setProperty("os.arch", osArch);
        p.setProperty("java.io.tmpdir", tmpDir.getAbsolutePath());
        p.setProperty("user.name", "tester");
        return p;
    }

    private StubLoader loader(String osName, String osArch) {
        return new StubLoader(props(osName, osArch));
    }

    private static byte[] read(File file) throws IOException {
        InputStream in = new FileInputStream(file);
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int n;
            while ((n = in.read(buf)) != -1) {
                out.write(buf, 0, n);
            }
            return out.toByteArray();
        } finally {
            in.close();
        }
    }

    private static void write(File file, String content) throws IOException {
        FileOutputStream out = new FileOutputStream(file);
        try {
            out.write(content.getBytes("UTF-8"));
        } finally {
            out.close();
        }
    }

    private static String content(File file) throws IOException {
        return new String(read(file), "UTF-8");
    }

    // ---------------------------------------------------------------------------------------------------------------
    // OS/arch -> natives/<dir> mapping
    // ---------------------------------------------------------------------------------------------------------------

    private void assertDirs(String osName, String osArch, String... expected) {
        assertDirs(osName, osArch, FloatAbi.UNKNOWN, expected);
    }

    private void assertDirs(String osName, String osArch, FloatAbi abi, String... expected) {
        StubLoader loader = loader(osName, osArch);
        loader.armAbi = abi;
        assertEquals(osName + "/" + osArch + "/" + abi,
                Arrays.asList(expected), Arrays.asList(loader.platformDirectories()));
    }

    @Test
    public void mapsEveryBundledDirectory() {
        // Values of os.name/os.arch reported by real JVMs
        assertDirs("Linux", "amd64", "linux_64");
        assertDirs("Linux", "x86_64", "linux_64");
        assertDirs("Linux", "i386", "linux_32");
        assertDirs("Linux", "x86", "linux_32");
        assertDirs("Linux", "i686", "linux_32");
        assertDirs("Linux", "aarch64", "linux_arm64");
        assertDirs("Linux", "arm", "linux_arm");
        assertDirs("Linux", "riscv64", "linux_riscv64");
        assertDirs("Linux", "riscv32", "linux_riscv32");
        assertDirs("Linux", "ppc64le", "linux_ppc");
        assertDirs("Mac OS X", "x86_64", "osx_64");
        assertDirs("Mac OS X", "aarch64", "osx_arm64");
        assertDirs("Darwin", "aarch64", "osx_arm64");
        assertDirs("Windows 10", "amd64", "windows_64");
        assertDirs("Windows XP", "x86", "windows_32");
        assertDirs("Windows 11", "aarch64", "windows_arm64");
    }

    @Test
    public void allPrecompiledDirectoriesAreReachable() {
        File natives = new File(System.getProperty("user.dir"), "src/main/resources-precompiled/natives");
        assumeTrue(natives.isDirectory());
        String[][] platforms = {
                {"Linux", "amd64"}, {"Linux", "i386"}, {"Linux", "aarch64"}, {"Linux", "arm"},
                {"Linux", "riscv64"}, {"Linux", "riscv32"}, {"Linux", "ppc64le"},
                {"Mac OS X", "x86_64"}, {"Mac OS X", "aarch64"},
                {"Windows 10", "amd64"}, {"Windows 10", "x86"}, {"Windows 10", "aarch64"},
        };
        List<String> reachable = new ArrayList<String>();
        for (String[] platform : platforms) {
            reachable.addAll(Arrays.asList(loader(platform[0], platform[1]).platformDirectories()));
        }
        for (String dir : natives.list()) {
            assertTrue("natives/" + dir + " can't be selected by the loader", reachable.contains(dir));
        }
    }

    @Test
    public void armPrefersAbiSpecificDirectory() {
        assertDirs("Linux", "arm", FloatAbi.HARD, "linux_armhf", "linux_arm");
        assertDirs("Linux", "arm", FloatAbi.SOFT, "linux_armsf", "linux_arm");
        assertDirs("Linux", "arm", FloatAbi.UNKNOWN, "linux_arm");
    }

    @Test
    public void unsupportedPlatformsHaveNoDirectory() {
        assertDirs("SunOS", "sparcv9");
        assertDirs("Linux", "s390x");
        assertDirs("Linux", "ppc64"); // big endian; natives/linux_ppc is ppc64le
        assertDirs("FreeBSD", "amd64");
    }

    @Test
    public void libraryFileNames() {
        assertEquals("libjssc.so", loader("Linux", "amd64").libraryFileName());
        assertEquals("libjssc.dylib", loader("Mac OS X", "aarch64").libraryFileName());
        assertEquals("jssc.dll", loader("Windows 10", "amd64").libraryFileName());
    }

    // ---------------------------------------------------------------------------------------------------------------
    // ARM float ABI detection
    // ---------------------------------------------------------------------------------------------------------------

    /**
     * Builds a minimal 32-bit ELF executable header with one PT_INTERP program header.
     */
    static byte[] elf(int machine, int flags, String interp, boolean bigEndian) {
        byte[] b = new byte[256];
        b[0] = 0x7F; b[1] = 'E'; b[2] = 'L'; b[3] = 'F';
        b[4] = 1; // ELFCLASS32
        b[5] = (byte) (bigEndian ? 2 : 1);
        b[6] = 1; // EV_CURRENT
        put16(b, 0x10, 2, bigEndian); // ET_EXEC
        put16(b, 0x12, machine, bigEndian);
        put32(b, 0x14, 1, bigEndian);
        put32(b, 0x1C, 52, bigEndian); // e_phoff
        put32(b, 0x24, flags, bigEndian);
        put16(b, 0x28, 52, bigEndian); // e_ehsize
        put16(b, 0x2A, 32, bigEndian); // e_phentsize
        if (interp != null) {
            put16(b, 0x2C, 1, bigEndian); // e_phnum
            int strOff = 52 + 32;
            put32(b, 52, 3, bigEndian); // PT_INTERP
            put32(b, 52 + 4, strOff, bigEndian);
            put32(b, 52 + 16, interp.length() + 1, bigEndian);
            for (int i = 0; i < interp.length(); i++) {
                b[strOff + i] = (byte) interp.charAt(i);
            }
        }
        return b;
    }

    private static void put16(byte[] b, int off, int v, boolean be) {
        b[off + (be ? 1 : 0)] = (byte) v;
        b[off + (be ? 0 : 1)] = (byte) (v >> 8);
    }

    private static void put32(byte[] b, int off, int v, boolean be) {
        for (int i = 0; i < 4; i++) {
            b[off + (be ? 3 - i : i)] = (byte) (v >> (8 * i));
        }
    }

    private static final int EM_ARM = 40;
    private static final int EM_386 = 3;
    private static final int EABI5 = NativeLibraryLoader.EF_ARM_EABI_VER5;

    @Test
    public void elfFlagsHardFloat() {
        assertEquals(FloatAbi.HARD, NativeLibraryLoader.parseElfFloatAbi(
                elf(EM_ARM, EABI5 | NativeLibraryLoader.EF_ARM_ABI_FLOAT_HARD, null, false)));
        assertEquals(FloatAbi.HARD, NativeLibraryLoader.parseElfFloatAbi(
                elf(EM_ARM, EABI5 | NativeLibraryLoader.EF_ARM_ABI_FLOAT_HARD, null, true)));
    }

    @Test
    public void elfFlagsSoftFloat() {
        assertEquals(FloatAbi.SOFT, NativeLibraryLoader.parseElfFloatAbi(
                elf(EM_ARM, EABI5 | NativeLibraryLoader.EF_ARM_ABI_FLOAT_SOFT, null, false)));
    }

    @Test
    public void elfFlagsWinOverInterpreter() {
        assertEquals(FloatAbi.SOFT, NativeLibraryLoader.parseElfFloatAbi(
                elf(EM_ARM, EABI5 | NativeLibraryLoader.EF_ARM_ABI_FLOAT_SOFT, "/lib/ld-linux-armhf.so.3", false)));
    }

    @Test
    public void elfInterpreterWhenNoFlags() {
        assertEquals(FloatAbi.HARD, NativeLibraryLoader.parseElfFloatAbi(
                elf(EM_ARM, EABI5, "/lib/ld-linux-armhf.so.3", false)));
        assertEquals(FloatAbi.SOFT, NativeLibraryLoader.parseElfFloatAbi(
                elf(EM_ARM, EABI5, "/lib/ld-linux.so.3", false)));
        assertEquals(FloatAbi.SOFT, NativeLibraryLoader.parseElfFloatAbi(
                elf(EM_ARM, EABI5, "/lib/ld-linux.so.3", true)));
        assertEquals(FloatAbi.UNKNOWN, NativeLibraryLoader.parseElfFloatAbi(
                elf(EM_ARM, EABI5, null, false)));
    }

    @Test
    public void elfOldAbiFlagsAreIgnored() {
        // EABI v4 or legacy: bit 0x400 has a different meaning there
        assertEquals(FloatAbi.UNKNOWN, NativeLibraryLoader.parseElfFloatAbi(
                elf(EM_ARM, 0x04000000 | 0x400, null, false)));
    }

    @Test
    public void nonArmOrBrokenElf() {
        assertEquals(FloatAbi.UNKNOWN, NativeLibraryLoader.parseElfFloatAbi(
                elf(EM_386, EABI5 | NativeLibraryLoader.EF_ARM_ABI_FLOAT_HARD, "/lib/ld-linux-armhf.so.3", false)));
        assertEquals(FloatAbi.UNKNOWN, NativeLibraryLoader.parseElfFloatAbi(null));
        assertEquals(FloatAbi.UNKNOWN, NativeLibraryLoader.parseElfFloatAbi(new byte[10]));
        assertEquals(FloatAbi.UNKNOWN, NativeLibraryLoader.parseElfFloatAbi(new byte[100]));
        byte[] truncated = Arrays.copyOf(elf(EM_ARM, EABI5, "/lib/ld-linux-armhf.so.3", false), 60);
        assertEquals(FloatAbi.UNKNOWN, NativeLibraryLoader.parseElfFloatAbi(truncated));
    }

    @Test
    public void precompiledLinuxArmIsHardFloat() throws IOException {
        File lib = new File(System.getProperty("user.dir"), "src/main/resources-precompiled/natives/linux_arm/libjssc.so");
        assumeTrue(lib.isFile());
        assertEquals(FloatAbi.HARD, NativeLibraryLoader.parseElfFloatAbi(Arrays.copyOf(read(lib), 4096)));
    }

    @Test
    public void archAbiProperty() {
        assertEquals(FloatAbi.HARD, NativeLibraryLoader.parseArchAbi("gnueabihf"));
        assertEquals(FloatAbi.SOFT, NativeLibraryLoader.parseArchAbi("gnueabi"));
        assertEquals(FloatAbi.UNKNOWN, NativeLibraryLoader.parseArchAbi(null));
        assertEquals(FloatAbi.UNKNOWN, NativeLibraryLoader.parseArchAbi("lp64"));
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Lookup order and extraction
    // ---------------------------------------------------------------------------------------------------------------

    @Test
    public void systemLibraryPathFirstAndNoTempDir() {
        StubLoader loader = loader("Linux", "amd64");
        loader.systemPathWorks = true;
        assertNull(loader.load());
        assertTrue(loader.loaded.isEmpty());
        assertEquals(0, tmpDir.list().length);
    }

    @Test
    public void bootPathIsUsedWithoutExtraction() throws IOException {
        File boot = folder.newFolder("boot");
        File lib = new File(boot, "libjssc.so");
        write(lib, "boot lib");
        Properties p = props("Linux", "amd64");
        p.setProperty(NativeLibraryLoader.BOOT_PATH_PROPERTY, boot.getAbsolutePath());
        StubLoader loader = new StubLoader(p);

        assertEquals(lib, loader.load());
        assertEquals(Collections.singletonList(lib), loader.loaded);
        assertEquals("no temp directory may be created (#165)", 0, tmpDir.list().length);
    }

    @Test
    public void bootPathWorksOnPlatformsWithoutBundledLibrary() throws IOException {
        File boot = folder.newFolder("boot");
        File lib = new File(boot, "libjssc.so");
        write(lib, "boot lib");
        Properties p = props("SunOS", "sparcv9");
        p.setProperty(NativeLibraryLoader.BOOT_PATH_PROPERTY, boot.getAbsolutePath());
        assertEquals(lib, new StubLoader(p).load());
    }

    @Test
    public void existingBootPathWithoutLibraryFails() throws IOException {
        File boot = folder.newFolder("boot");
        Properties p = props("Linux", "amd64");
        p.setProperty(NativeLibraryLoader.BOOT_PATH_PROPERTY, boot.getAbsolutePath());
        StubLoader loader = new StubLoader(p);
        try {
            loader.load();
            fail("existing boot path without library must not fall back to extraction");
        } catch (UnsatisfiedLinkError expected) {
            assertTrue(expected.getMessage(), expected.getMessage().startsWith("Could not load the jssc library"));
            assertTrue(expected.getMessage(), expected.getMessage().contains("jssc.boot.library.path"));
        }
        assertEquals(0, tmpDir.list().length);
    }

    @Test
    public void missingBootPathFallsBackToExtraction() throws IOException {
        Properties p = props("Linux", "amd64");
        p.setProperty(NativeLibraryLoader.BOOT_PATH_PROPERTY, new File(folder.getRoot(), "does-not-exist").getAbsolutePath());
        File lib = new StubLoader(p).load();
        assertNotNull(lib);
        assertEquals("fake linux_64 library", content(lib));
    }

    @Test
    public void extractsToPerVersionDirectory() throws IOException {
        StubLoader loader = loader("Linux", "amd64");
        File lib = loader.load();

        assertEquals(new File(tmpDir, "jssc-" + VERSION + "-tester/linux_64/libjssc.so").getAbsoluteFile(), lib.getAbsoluteFile());
        assertEquals("fake linux_64 library", content(lib));
        assertEquals(Collections.singletonList(lib), loader.loaded);
        // nothing else is left behind (temporary files are renamed)
        assertEquals(Collections.singletonList("libjssc.so"), Arrays.asList(lib.getParentFile().list()));
    }

    @Test
    public void reusesIdenticalLibrary() throws IOException {
        File first = loader("Linux", "amd64").load();
        assertTrue(first.setLastModified(1000000000L));
        File second = loader("Linux", "amd64").load();
        assertEquals(first, second);
        assertEquals("identical file must not be rewritten", 1000000000L, second.lastModified());
    }

    @Test
    public void replacesCorruptedLibrary() throws IOException {
        File first = loader("Linux", "amd64").load();
        write(first, "garbage from an interrupted run");
        File second = loader("Linux", "amd64").load();
        assertEquals(first, second);
        assertEquals("fake linux_64 library", content(second));
    }

    @Test
    public void userNameIsSanitized() {
        Properties p = props("Linux", "amd64");
        p.setProperty("user.name", "DOMAIN\\john doe/..");
        File lib = new StubLoader(p).load();
        assertEquals(tmpDir.getAbsoluteFile(), lib.getParentFile().getParentFile().getParentFile().getAbsoluteFile());
        assertEquals("jssc-" + VERSION + "-DOMAIN_john_doe_..", lib.getParentFile().getParentFile().getName());
    }

    @Test
    public void armHardFloatUsesArmhfDirectory() throws IOException {
        StubLoader loader = loader("Linux", "arm");
        loader.armAbi = FloatAbi.HARD;
        assertEquals("fake linux_armhf library", content(loader.load()));
    }

    @Test
    public void armSoftFloatFallsBackToGenericDirectory() throws IOException {
        StubLoader loader = loader("Linux", "arm");
        loader.armAbi = FloatAbi.SOFT; // no linux_armsf in the test resources
        assertEquals("fake linux_arm library", content(loader.load()));
    }

    @Test
    public void armFallsBackWhenSpecificLibraryDoesNotLoad() throws IOException {
        StubLoader loader = loader("Linux", "arm");
        loader.armAbi = FloatAbi.HARD;
        loader.failures.add(new String[]{"linux_armhf", "version `GLIBC_2.34' not found"});
        assertEquals("fake linux_arm library", content(loader.load()));
    }

    @Test
    public void otherClassLoaderGetsPrivateCopy() throws IOException {
        StubLoader loader = loader("Linux", "amd64");
        loader.failures.add(new String[]{"jssc-" + VERSION, "Native Library x already loaded in another classloader"});
        File lib = loader.load();
        assertTrue(lib.getParentFile().getName(), lib.getParentFile().getName().startsWith(NativeLibraryLoader.PRIVATE_DIR_PREFIX));
        assertEquals("fake linux_64 library", content(lib));
    }

    @Test
    public void rootUsesPrivateCopy() throws IOException {
        Properties p = props("Linux", "amd64");
        p.setProperty("user.name", "root");
        File lib = new StubLoader(p).load();
        assertTrue(lib.getParentFile().getName().startsWith(NativeLibraryLoader.PRIVATE_DIR_PREFIX));
        assertEquals("fake linux_64 library", content(lib));
    }

    @Test
    public void otherPlatformsExtractTheirLibraryName() throws IOException {
        assertEquals("libjssc.dylib", loader("Mac OS X", "aarch64").load().getName());
        assertEquals("jssc.dll", loader("Windows 10", "amd64").load().getName());
    }

    @Test
    public void unsupportedPlatformFailsWithHint() {
        try {
            loader("SunOS", "sparcv9").load();
            fail();
        } catch (UnsatisfiedLinkError expected) {
            assertTrue(expected.getMessage(), expected.getMessage().contains("jssc.boot.library.path"));
            assertTrue(expected.getMessage(), expected.getMessage().contains("sparcv9"));
        }
        assertEquals(0, tmpDir.list().length);
    }

    @Test
    public void missingResourceFails() {
        try {
            loader("Linux", "riscv64").load(); // not in the test resources
            fail();
        } catch (UnsatisfiedLinkError expected) {
            assertTrue(expected.getMessage(), expected.getMessage().contains("linux_riscv64/libjssc.so: not bundled"));
        }
        assertEquals(0, tmpDir.list().length);
    }

    @Test
    public void concurrentExtraction() throws Exception {
        final int threads = 8;
        final CountDownLatch start = new CountDownLatch(1);
        final List<Object> results = Collections.synchronizedList(new ArrayList<Object>());
        List<Thread> workers = new ArrayList<Thread>();
        for (int i = 0; i < threads; i++) {
            Thread t = new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        start.await();
                        results.add(loader("Linux", "amd64").load());
                    } catch (Throwable e) {
                        results.add(e);
                    }
                }
            });
            t.start();
            workers.add(t);
        }
        start.countDown();
        for (Thread t : workers) {
            t.join();
        }
        assertEquals(threads, results.size());
        File expected = new File(tmpDir, "jssc-" + VERSION + "-tester/linux_64/libjssc.so");
        for (Object result : results) {
            assertTrue(String.valueOf(result), result instanceof File);
            assertEquals(expected.getAbsoluteFile(), ((File) result).getAbsoluteFile());
        }
        assertArrayEquals("fake linux_64 library".getBytes("UTF-8"), read(expected));
        assertEquals(Collections.singletonList("libjssc.so"), Arrays.asList(expected.getParentFile().list()));
    }

    @Test
    public void sharedDirectoryIsPrivateToOwner() throws Exception {
        assumeTrue("Linux".equals(System.getProperty("os.name")));
        File lib = loader("Linux", "amd64").load();
        File base = lib.getParentFile().getParentFile();
        Process stat = new ProcessBuilder("stat", "-c", "%a", base.getAbsolutePath()).start();
        String mode = new String(readAll(stat.getInputStream()), "UTF-8").trim();
        assertEquals(0, stat.waitFor());
        assertEquals("700", mode);
    }

    @Test
    public void ownershipCheck() throws IOException {
        assumeTrue(!System.getProperty("os.name").toLowerCase().contains("win"));
        assumeTrue(!"root".equals(System.getProperty("user.name")));
        NativeLibraryLoader loader = loader("Linux", "amd64");
        assertTrue(loader.isOwner(folder.newFolder("mine")));
        assertTrue("must not be owned by a regular user", !loader.isOwner(new File("/")));
    }

    private static byte[] readAll(InputStream in) throws IOException {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int n;
            while ((n = in.read(buf)) != -1) {
                out.write(buf, 0, n);
            }
            return out.toByteArray();
        } finally {
            in.close();
        }
    }
}
