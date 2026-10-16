package jssc.natives;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * Reads CPU type, minimum macOS version, linked dylibs and exports of a thin 64-bit Mach-O file.
 */
class MachOFile extends BinaryFile {

    static final int CPU_TYPE_X86_64 = 0x01000007;
    static final int CPU_TYPE_ARM64 = 0x0100000c;

    private static final int LC_SYMTAB = 0x2;
    private static final int LC_LOAD_DYLIB = 0xc;
    private static final int LC_VERSION_MIN_MACOSX = 0x24;
    private static final int LC_LAZY_LOAD_DYLIB = 0x20;
    private static final int LC_BUILD_VERSION = 0x32;
    private static final int LC_LOAD_WEAK_DYLIB = 0x80000018;
    private static final int LC_REEXPORT_DYLIB = 0x8000001f;
    private static final int LC_LOAD_UPWARD_DYLIB = 0x80000023;

    final int cpuType;
    /** Minimum macOS version as "major.minor[.patch]", or null when not recorded */
    final String minOsVersion;

    private final List<String> dylibs = new ArrayList<String>();
    private final Set<String> exports = new TreeSet<String>();

    MachOFile(File file) throws IOException {
        super(file);
        long magic = u32(0);
        if (magic == 0xbebafecaL || magic == 0xcafebabeL) {
            throw new IllegalStateException(file + ": universal (fat) binaries are not expected in a per-arch directory");
        }
        if (magic != 0xfeedfacfL) {
            throw new IllegalStateException(file + ": not a 64-bit little-endian Mach-O file");
        }
        cpuType = (int) u32(4);
        long ncmds = u32(16);

        String minOs = null;
        long cmd = 32;
        for (long i = 0; i < ncmds; i++) {
            int type = (int) u32(cmd);
            long size = u32(cmd + 4);
            if (type == LC_LOAD_DYLIB || type == LC_LOAD_WEAK_DYLIB || type == LC_REEXPORT_DYLIB
                    || type == LC_LAZY_LOAD_DYLIB || type == LC_LOAD_UPWARD_DYLIB) {
                dylibs.add(cstring(cmd + u32(cmd + 8)));
            } else if (type == LC_BUILD_VERSION) {
                minOs = decodeVersion(u32(cmd + 12));
            } else if (type == LC_VERSION_MIN_MACOSX) {
                minOs = decodeVersion(u32(cmd + 8));
            } else if (type == LC_SYMTAB) {
                readSymtab(u32(cmd + 8), u32(cmd + 12), u32(cmd + 16));
            }
            cmd += size;
        }
        minOsVersion = minOs;
    }

    @Override
    String describeArchitecture() {
        return "Mach-O 64 cputype=0x" + Integer.toHexString(cpuType);
    }

    @Override
    List<String> dependencies() {
        return Collections.unmodifiableList(dylibs);
    }

    @Override
    Set<String> exportedSymbols() {
        return Collections.unmodifiableSet(exports);
    }

    private void readSymtab(long symoff, long nsyms, long stroff) {
        for (long i = 0; i < nsyms; i++) {
            long nlist = symoff + 16 * i;
            int type = u8(nlist + 4);
            boolean stab = (type & 0xe0) != 0;
            boolean external = (type & 0x01) != 0;
            boolean definedInSection = (type & 0x0e) == 0x0e;
            if (!stab && external && definedInSection) {
                String name = cstring(stroff + u32(nlist));
                exports.add(name.startsWith("_") ? name.substring(1) : name);
            }
        }
    }

    private static String decodeVersion(long v) {
        long patch = v & 0xff;
        String s = (v >> 16) + "." + ((v >> 8) & 0xff);
        return patch == 0 ? s : s + "." + patch;
    }
}
