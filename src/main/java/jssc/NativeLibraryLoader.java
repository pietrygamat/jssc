/* jSSC (Java Simple Serial Connector) - serial port communication library.
 *
 * This file is part of jSSC.
 *
 * jSSC is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * jSSC is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with jSSC.  If not, see <http://www.gnu.org/licenses/>.
 */
package jssc;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Properties;

/**
 * Locates, extracts and loads the jssc native library.
 * <p>
 * Internal class, replaces the former dependency on scijava <code>native-lib-loader</code>.
 * The library is looked up in this order:
 * <ol>
 *     <li>the system library path (<code>System.loadLibrary("jssc")</code>, i.e. <code>java.library.path</code>);</li>
 *     <li>the directory named by the <code>jssc.boot.library.path</code> system property, if that directory exists.
 *     The library is loaded from there without extraction and there is no further fallback.
 *     If the directory does not exist, a warning is printed and extraction follows;</li>
 *     <li>the library bundled in the jar under <code>natives/&lt;os&gt;_&lt;suffix&gt;/</code>, extracted to
 *     <code>java.io.tmpdir</code>.</li>
 * </ol>
 * A temporary directory is only created when step 3 is reached.
 */
class NativeLibraryLoader {

    static final String LIBRARY_NAME = "jssc";
    static final String BOOT_PATH_PROPERTY = "jssc.boot.library.path";
    static final String DEFAULT_RESOURCE_ROOT = "natives/";
    static final String PRIVATE_DIR_PREFIX = "jssc-tmp-";

    /** ARM 32-bit floating point calling convention of the running JVM */
    enum FloatAbi { HARD, SOFT, UNKNOWN }

    // ELF constants
    private static final int EI_CLASS = 4;
    private static final int EI_DATA = 5;
    private static final int ELFCLASS32 = 1;
    private static final int ELFDATA2LSB = 1;
    private static final int ELFDATA2MSB = 2;
    private static final int EM_ARM = 40;
    private static final int PT_INTERP = 3;
    static final int EF_ARM_EABIMASK = 0xFF000000;
    static final int EF_ARM_EABI_VER5 = 0x05000000;
    static final int EF_ARM_ABI_FLOAT_SOFT = 0x200;
    static final int EF_ARM_ABI_FLOAT_HARD = 0x400;

    private final Properties props;
    private final String version;
    private final String resourceRoot;

    NativeLibraryLoader(Properties props, String version) {
        this(props, version, DEFAULT_RESOURCE_ROOT);
    }

    NativeLibraryLoader(Properties props, String version, String resourceRoot) {
        this.props = props;
        this.version = version;
        this.resourceRoot = resourceRoot.endsWith("/") ? resourceRoot : resourceRoot + "/";
    }

    /**
     * Loads the jssc native library using the current system properties.
     *
     * @param version jssc version, used to name the extraction directory
     * @throws UnsatisfiedLinkError if the library cannot be loaded
     */
    static void loadJssc(String version) {
        new NativeLibraryLoader(System.getProperties(), version).load();
    }

    /**
     * Loads the native library.
     *
     * @return the file that was loaded, or <code>null</code> if the library was found on the system library path
     * @throws UnsatisfiedLinkError if the library cannot be loaded
     */
    File load() {
        List<String> attempts = new ArrayList<String>();

        // 1. System library path
        try {
            loadLibrary(LIBRARY_NAME);
            return null;
        } catch (UnsatisfiedLinkError e) {
            attempts.add("java.library.path: " + e.getMessage());
        }

        // 2. jssc.boot.library.path (#92): no extraction, no fallback if the directory exists
        String bootPath = props.getProperty(BOOT_PATH_PROPERTY);
        if (bootPath != null) {
            File bootDir = new File(bootPath);
            if (bootDir.exists()) {
                File lib = new File(bootDir, libraryFileName());
                try {
                    loadFile(lib);
                    return lib;
                } catch (UnsatisfiedLinkError e) {
                    attempts.add(BOOT_PATH_PROPERTY + ": " + e.getMessage());
                    throw failure(attempts, e);
                }
            }
            System.err.println("WARNING jssc: Boot path " + bootPath + " not found, falling back to default extraction behavior.");
        }

        // 3. Extract from the jar
        String[] platformDirs = platformDirectories();
        if (platformDirs.length == 0) {
            attempts.add("no bundled native library for os.name=" + props.getProperty("os.name")
                    + ", os.arch=" + props.getProperty("os.arch")
                    + " (build it yourself and point -D" + BOOT_PATH_PROPERTY + " to its directory)");
            throw failure(attempts, null);
        }
        String fileName = libraryFileName();
        Throwable lastCause = null;
        for (String platformDir : platformDirs) {
            String resourceName = resourceRoot + platformDir + "/" + fileName;
            URL resource = findResource(resourceName);
            if (resource == null) {
                attempts.add(resourceName + ": not bundled");
                continue;
            }
            byte[] content;
            try {
                content = readFully(resource);
            } catch (IOException e) {
                attempts.add(resourceName + ": " + e);
                lastCause = e;
                continue;
            }

            boolean tryPrivateCopy = true;
            try {
                File lib = extractShared(platformDir, fileName, content);
                if (lib != null) {
                    loadFile(lib);
                    return lib;
                }
            } catch (IOException e) {
                attempts.add(resourceName + ": " + e);
                lastCause = e;
            } catch (UnsatisfiedLinkError e) {
                attempts.add(resourceName + ": " + e.getMessage());
                lastCause = e;
                // The same file can't be loaded by two class loaders, retry with a private copy then.
                // Any other link error (e.g. wrong ABI) won't be fixed by another copy.
                tryPrivateCopy = e.getMessage() != null && e.getMessage().contains("another classloader");
            }

            if (tryPrivateCopy) {
                try {
                    File lib = extractPrivate(fileName, content);
                    loadFile(lib);
                    return lib;
                } catch (IOException e) {
                    attempts.add(resourceName + " (private copy): " + e);
                    lastCause = e;
                } catch (UnsatisfiedLinkError e) {
                    attempts.add(resourceName + " (private copy): " + e.getMessage());
                    lastCause = e;
                }
            }
        }
        throw failure(attempts, lastCause);
    }

    private static UnsatisfiedLinkError failure(List<String> attempts, Throwable cause) {
        StringBuilder sb = new StringBuilder("Could not load the jssc library: ");
        for (int i = 0; i < attempts.size(); i++) {
            sb.append(i == 0 ? "" : "; ").append(attempts.get(i));
        }
        UnsatisfiedLinkError error = new UnsatisfiedLinkError(sb.toString());
        if (cause != null) {
            error.initCause(cause);
        }
        return error;
    }

    // ------------------------------------------------------------------------------------------------------------
    // Hooks (overridden in tests)
    // ------------------------------------------------------------------------------------------------------------

    void loadLibrary(String name) {
        System.loadLibrary(name);
    }

    void loadFile(File file) {
        System.load(file.getAbsolutePath());
    }

    URL findResource(String name) {
        return NativeLibraryLoader.class.getResource("/" + name);
    }

    /**
     * Detects the float ABI of the running JVM on 32-bit ARM Linux.
     */
    FloatAbi detectArmFloatAbi() {
        FloatAbi abi = FloatAbi.UNKNOWN;
        byte[] header = readHead(new File("/proc/self/exe"), 4096);
        if (header != null) {
            abi = parseElfFloatAbi(header);
        }
        if (abi == FloatAbi.UNKNOWN) {
            abi = parseArchAbi(props.getProperty("sun.arch.abi"));
        }
        if (abi == FloatAbi.UNKNOWN) {
            boolean hf = new File("/lib/ld-linux-armhf.so.3").exists();
            boolean sf = new File("/lib/ld-linux.so.3").exists();
            if (hf != sf) {
                abi = hf ? FloatAbi.HARD : FloatAbi.SOFT;
            }
        }
        return abi;
    }

    // ------------------------------------------------------------------------------------------------------------
    // Platform detection
    // ------------------------------------------------------------------------------------------------------------

    private String osName() {
        return props.getProperty("os.name", "").toLowerCase(Locale.ENGLISH);
    }

    private String osArch() {
        return props.getProperty("os.arch", "").toLowerCase(Locale.ENGLISH);
    }

    private boolean isMac() {
        String os = osName();
        return os.contains("mac") || os.contains("darwin");
    }

    private boolean isWindows() {
        return !isMac() && osName().contains("win");
    }

    private boolean isLinux() {
        String os = osName();
        return os.contains("nix") || os.contains("nux");
    }

    /**
     * @return platform file name of the library, e.g. <code>libjssc.so</code>, <code>jssc.dll</code>
     */
    String libraryFileName() {
        if (isWindows()) {
            return LIBRARY_NAME + ".dll";
        }
        if (isMac()) {
            return "lib" + LIBRARY_NAME + ".dylib";
        }
        return "lib" + LIBRARY_NAME + ".so";
    }

    /**
     * Resource directories (below <code>natives/</code>) to look into, most specific first.
     * The names follow the layout used by native-lib-loader, so existing jars keep working.
     *
     * @return directory names, empty if the platform is not supported
     */
    String[] platformDirectories() {
        String arch = osArch();
        boolean is64 = arch.contains("64");
        String os;
        if (isLinux()) {
            os = "linux";
        } else if (osName().contains("aix")) {
            os = "aix";
        } else if (isMac()) {
            os = "osx";
        } else if (isWindows()) {
            os = "windows";
        } else {
            return new String[0];
        }

        if (arch.contains("aarch64") || arch.equals("arm64")) {
            if (os.equals("linux") || os.equals("osx") || os.equals("windows")) {
                return new String[]{os + "_arm64"};
            }
        } else if (arch.contains("arm")) {
            if (os.equals("linux")) {
                switch (detectArmFloatAbi()) {
                    case HARD: return new String[]{"linux_armhf", "linux_arm"};
                    case SOFT: return new String[]{"linux_armsf", "linux_arm"};
                    default: return new String[]{"linux_arm"};
                }
            }
        } else if (arch.contains("ppc")) {
            if (os.equals("aix")) {
                return new String[]{is64 ? "aix_64" : "aix_32"};
            }
            if (os.equals("osx") && !is64) {
                return new String[]{"osx_ppc"};
            }
            // natives/linux_ppc is built for little-endian ppc64 (ppc64le)
            if (os.equals("linux") && is64 && arch.endsWith("le")) {
                return new String[]{"linux_ppc"};
            }
        } else if (arch.contains("86") || arch.contains("amd")) {
            if (!os.equals("aix")) {
                return new String[]{os + (is64 ? "_64" : "_32")};
            }
        } else if (arch.contains("riscv")) {
            if (os.equals("linux")) {
                return new String[]{is64 ? "linux_riscv64" : "linux_riscv32"};
            }
        }
        return new String[0];
    }

    /**
     * Reads the float ABI from an ELF header of a 32-bit ARM executable: the <code>e_flags</code> of an EABI5
     * binary first, then the name of the program interpreter (<code>PT_INTERP</code>), e.g.
     * <code>/lib/ld-linux-armhf.so.3</code>.
     *
     * @param elf the beginning of the file (ELF header, program headers and ideally the interpreter name)
     * @return detected ABI or {@link FloatAbi#UNKNOWN}
     */
    static FloatAbi parseElfFloatAbi(byte[] elf) {
        if (elf == null || elf.length < 52
                || elf[0] != 0x7F || elf[1] != 'E' || elf[2] != 'L' || elf[3] != 'F'
                || elf[EI_CLASS] != ELFCLASS32) {
            return FloatAbi.UNKNOWN;
        }
        boolean bigEndian;
        if (elf[EI_DATA] == ELFDATA2LSB) {
            bigEndian = false;
        } else if (elf[EI_DATA] == ELFDATA2MSB) {
            bigEndian = true;
        } else {
            return FloatAbi.UNKNOWN;
        }
        if (readU16(elf, 0x12, bigEndian) != EM_ARM) {
            return FloatAbi.UNKNOWN;
        }
        int flags = (int) readU32(elf, 0x24, bigEndian);
        if ((flags & EF_ARM_EABIMASK) == EF_ARM_EABI_VER5) {
            if ((flags & EF_ARM_ABI_FLOAT_HARD) != 0) {
                return FloatAbi.HARD;
            }
            if ((flags & EF_ARM_ABI_FLOAT_SOFT) != 0) {
                return FloatAbi.SOFT;
            }
        }
        // No explicit flag: look at the dynamic loader the executable asks for
        String interp = readInterpreter(elf, bigEndian);
        if (interp != null) {
            if (interp.contains("armhf")) {
                return FloatAbi.HARD;
            }
            if (interp.endsWith("/ld-linux.so.3") || interp.endsWith("/ld-musl-arm.so.1")) {
                return FloatAbi.SOFT;
            }
        }
        return FloatAbi.UNKNOWN;
    }

    private static String readInterpreter(byte[] elf, boolean bigEndian) {
        long phoff = readU32(elf, 0x1C, bigEndian);
        int phentsize = readU16(elf, 0x2A, bigEndian);
        int phnum = readU16(elf, 0x2C, bigEndian);
        if (phentsize < 32) {
            return null;
        }
        for (int i = 0; i < phnum; i++) {
            long ph = phoff + (long) i * phentsize;
            if (ph + 32 > elf.length) {
                return null;
            }
            if (readU32(elf, (int) ph, bigEndian) == PT_INTERP) {
                long offset = readU32(elf, (int) ph + 4, bigEndian);
                long size = readU32(elf, (int) ph + 16, bigEndian);
                if (size <= 0 || offset + size > elf.length) {
                    return null;
                }
                StringBuilder sb = new StringBuilder();
                for (int j = 0; j < size; j++) {
                    char c = (char) (elf[(int) offset + j] & 0xFF);
                    if (c == 0) {
                        break;
                    }
                    sb.append(c);
                }
                return sb.toString();
            }
        }
        return null;
    }

    /**
     * Interprets the <code>sun.arch.abi</code> property set by some (embedded) JVMs, e.g. <code>gnueabihf</code>.
     */
    static FloatAbi parseArchAbi(String archAbi) {
        if (archAbi == null) {
            return FloatAbi.UNKNOWN;
        }
        String abi = archAbi.toLowerCase(Locale.ENGLISH);
        if (abi.endsWith("hf") || abi.contains("hard")) {
            return FloatAbi.HARD;
        }
        if (abi.startsWith("gnueabi") || abi.contains("soft") || abi.endsWith("sf")) {
            return FloatAbi.SOFT;
        }
        return FloatAbi.UNKNOWN;
    }

    private static int readU16(byte[] b, int off, boolean bigEndian) {
        int b0 = b[off] & 0xFF;
        int b1 = b[off + 1] & 0xFF;
        return bigEndian ? (b0 << 8) | b1 : (b1 << 8) | b0;
    }

    private static long readU32(byte[] b, int off, boolean bigEndian) {
        long v = 0;
        for (int i = 0; i < 4; i++) {
            int shift = bigEndian ? (3 - i) * 8 : i * 8;
            v |= ((long) (b[off + i] & 0xFF)) << shift;
        }
        return v;
    }

    private static byte[] readHead(File file, int max) {
        InputStream in = null;
        try {
            in = new FileInputStream(file);
            byte[] buf = new byte[max];
            int total = 0;
            int n;
            while (total < max && (n = in.read(buf, total, max - total)) > 0) {
                total += n;
            }
            return total == max ? buf : Arrays.copyOf(buf, total);
        } catch (IOException e) {
            return null;
        } catch (SecurityException e) {
            return null;
        } finally {
            closeQuietly(in);
        }
    }

    // ------------------------------------------------------------------------------------------------------------
    // Extraction
    // ------------------------------------------------------------------------------------------------------------

    private File tmpDir() {
        return new File(props.getProperty("java.io.tmpdir", "."));
    }

    /**
     * Shared, per-version and per-user cache directory,
     * e.g. <code>/tmp/jssc-2.11.0-alice/linux_64/libjssc.so</code>. The file is reused when its content matches the
     * bundled library; otherwise it is (re)written through a temporary file and an atomic rename, so concurrent
     * JVMs never see a partially written library.
     *
     * @return the extracted library, or <code>null</code> if the cache directory can't be trusted
     */
    File extractShared(String platformDir, String fileName, byte[] content) throws IOException {
        if (!isWindows() && "root".equals(props.getProperty("user.name"))) {
            // ownership of a pre-existing directory can't be checked for root (chmod always succeeds)
            return null;
        }
        File base = new File(tmpDir(), "jssc-" + safeName(version) + "-" + safeName(props.getProperty("user.name", "user")));
        if (!base.isDirectory()) {
            // Prepare an owner-only directory under a unique name, then move it into place atomically,
            // so a concurrent JVM never sees it with default permissions. Losing the race is fine.
            File staging = createPrivateDir();
            if (!staging.renameTo(base)) {
                staging.delete();
            }
        }
        if (!base.isDirectory()) {
            throw new IOException("Unable to create directory " + base);
        }
        // Only the owner may chmod (POSIX): this rejects a directory planted by another user
        if (!isOwner(base)) {
            return null;
        }
        File dir = new File(base, platformDir);
        dir.mkdirs();
        if (!dir.isDirectory()) {
            throw new IOException("Unable to create directory " + dir);
        }
        File target = new File(dir, fileName);
        if (hasContent(target, content)) {
            return target;
        }
        File tmp = File.createTempFile(fileName + ".", ".tmp", dir);
        try {
            writeFile(tmp, content);
            if (!tmp.renameTo(target)) {
                // Windows can't rename over an existing (possibly loaded) file
                if (!hasContent(target, content)) {
                    target.delete();
                    if (!tmp.renameTo(target) && !hasContent(target, content)) {
                        throw new IOException("Unable to replace " + target);
                    }
                }
            }
        } finally {
            if (tmp.exists()) {
                tmp.delete();
            }
        }
        if (!hasContent(target, content)) {
            throw new IOException("Content of " + target + " changed after extraction");
        }
        return target;
    }

    /**
     * Unique directory for this process only, deleted on exit (where the OS allows it).
     */
    File extractPrivate(String fileName, byte[] content) throws IOException {
        File dir = createPrivateDir();
        // deleteOnExit runs in reverse order: the file first, then the directory
        dir.deleteOnExit();
        File target = new File(dir, fileName);
        target.deleteOnExit();
        writeFile(target, content);
        return target;
    }

    /**
     * Creates a new, uniquely named directory in <code>java.io.tmpdir</code>, accessible by the owner only.
     */
    private File createPrivateDir() throws IOException {
        for (int i = 0; i < 10; i++) {
            File candidate = File.createTempFile(PRIVATE_DIR_PREFIX, "", tmpDir());
            // mkdir fails if somebody else grabbed the name in between
            if (candidate.delete() && candidate.mkdir()) {
                restrictToOwner(candidate);
                return candidate;
            }
        }
        throw new IOException("Unable to create a temporary directory in " + tmpDir());
    }

    private boolean restrictToOwner(File dir) {
        if (isWindows()) {
            return true; // java.io.tmpdir is per user
        }
        // non-short-circuit: apply all of them. Only use it on a directory nobody else uses yet,
        // the owner loses access for a moment.
        return dir.setReadable(false, false) & dir.setWritable(false, false) & dir.setExecutable(false, false)
                & dir.setReadable(true, true) & dir.setWritable(true, true) & dir.setExecutable(true, true);
    }

    /**
     * On POSIX systems <code>File.setWritable</code> always calls <code>chmod(2)</code>, which only the owner
     * (or root) may do. Setting a bit the owner already has changes nothing.
     */
    boolean isOwner(File dir) {
        return isWindows() || dir.setWritable(true, true);
    }

    private static String safeName(String s) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            boolean ok = (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9')
                    || c == '.' || c == '_' || c == '-';
            sb.append(ok ? c : '_');
        }
        return sb.length() == 0 ? "_" : sb.toString();
    }

    private static boolean hasContent(File file, byte[] content) {
        if (!file.isFile() || file.length() != content.length) {
            return false;
        }
        InputStream in = null;
        try {
            in = new FileInputStream(file);
            return Arrays.equals(readFully(in), content);
        } catch (IOException e) {
            return false;
        } finally {
            closeQuietly(in);
        }
    }

    private static void writeFile(File file, byte[] content) throws IOException {
        OutputStream out = new FileOutputStream(file);
        try {
            out.write(content);
        } finally {
            out.close();
        }
    }

    private static byte[] readFully(URL resource) throws IOException {
        URLConnection connection = resource.openConnection();
        // avoid locking the jar on Windows and stale cached jar content
        connection.setUseCaches(false);
        InputStream in = connection.getInputStream();
        try {
            return readFully(in);
        } finally {
            closeQuietly(in);
        }
    }

    private static byte[] readFully(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) != -1) {
            out.write(buf, 0, n);
        }
        return out.toByteArray();
    }

    private static void closeQuietly(InputStream in) {
        if (in != null) {
            try {
                in.close();
            } catch (IOException ignore) {
                // nothing to do
            }
        }
    }
}
