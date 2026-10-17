package jssc.natives;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * Reads machine, minimum Windows version, imported DLLs and exports of a PE (Windows DLL) file.
 */
class PeFile extends BinaryFile {

    static final int IMAGE_FILE_MACHINE_I386 = 0x014c;
    static final int IMAGE_FILE_MACHINE_AMD64 = 0x8664;
    static final int IMAGE_FILE_MACHINE_ARM64 = 0xaa64;

    final int machine;
    final boolean is64;
    /** MajorSubsystemVersion.MinorSubsystemVersion - Windows refuses to load images requiring a newer one */
    final String subsystemVersion;

    private final long sectionTable;
    private final int sectionCount;
    private final List<String> imports = new ArrayList<String>();
    private final Set<String> exports = new TreeSet<String>();

    PeFile(File file) throws IOException {
        super(file);
        if (data.length < 64 || u8(0) != 'M' || u8(1) != 'Z') {
            throw new IllegalStateException(file + ": not a PE file");
        }
        long pe = u32(0x3c);
        if (u32(pe) != 0x00004550) {
            throw new IllegalStateException(file + ": missing PE signature");
        }
        machine = u16(pe + 4);
        sectionCount = u16(pe + 6);
        int optSize = u16(pe + 20);
        long opt = pe + 24;
        is64 = u16(opt) == 0x20b;
        subsystemVersion = u16(opt + 48) + "." + u16(opt + 50);
        sectionTable = opt + optSize;

        long dataDirs = opt + (is64 ? 112 : 96);
        long exportRva = u32(dataDirs);
        long importRva = u32(dataDirs + 8);
        if (exportRva != 0) {
            readExports(rvaToOffset(exportRva));
        }
        if (importRva != 0) {
            readImports(rvaToOffset(importRva));
        }
    }

    @Override
    String describeArchitecture() {
        return "PE" + (is64 ? "32+" : "32") + " machine=0x" + Integer.toHexString(machine);
    }

    @Override
    List<String> dependencies() {
        return Collections.unmodifiableList(imports);
    }

    @Override
    Set<String> exportedSymbols() {
        return Collections.unmodifiableSet(exports);
    }

    private void readExports(long dir) {
        long count = u32(dir + 24);
        long names = rvaToOffset(u32(dir + 32));
        for (long i = 0; i < count; i++) {
            exports.add(cstring(rvaToOffset(u32(names + 4 * i))));
        }
    }

    private void readImports(long desc) {
        for (; ; desc += 20) {
            long nameRva = u32(desc + 12);
            if (nameRva == 0) {
                break;
            }
            imports.add(cstring(rvaToOffset(nameRva)));
        }
    }

    private long rvaToOffset(long rva) {
        for (int i = 0; i < sectionCount; i++) {
            long s = sectionTable + 40L * i;
            long virtualSize = u32(s + 8);
            long virtualAddress = u32(s + 12);
            long rawSize = u32(s + 16);
            long rawOffset = u32(s + 20);
            if (rva >= virtualAddress && rva < virtualAddress + Math.max(virtualSize, rawSize)) {
                return rva - virtualAddress + rawOffset;
            }
        }
        throw new IllegalStateException(file + ": RVA 0x" + Long.toHexString(rva) + " not in any section");
    }
}
