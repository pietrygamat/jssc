package jssc.natives;

import java.io.File;
import java.io.IOException;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Reads the parts of an ELF shared object relevant for load-time compatibility:
 * machine, DT_NEEDED entries, exported symbols, required symbol versions
 * (e.g. GLIBC_2.28) and the GNU_STACK program header.
 */
class ElfFile extends BinaryFile {

    static final int EM_386 = 3;
    static final int EM_PPC64 = 21;
    static final int EM_ARM = 40;
    static final int EM_X86_64 = 62;
    static final int EM_AARCH64 = 183;
    static final int EM_RISCV = 243;

    static final long EF_ARM_ABI_FLOAT_HARD = 0x400;
    static final long EF_RISCV_FLOAT_ABI_MASK = 0x6;
    static final long EF_RISCV_FLOAT_ABI_DOUBLE = 0x4;

    private static final int SHT_DYNSYM = 11;
    private static final int SHT_DYNAMIC = 6;
    private static final int SHT_GNU_VERNEED = 0x6ffffffe;
    private static final int SHT_GNU_VERSYM = 0x6fffffff;
    private static final int SHT_ARM_ATTRIBUTES = 0x70000003;
    private static final int PT_GNU_STACK = 0x6474e551;
    private static final int PF_X = 1;
    private static final int DT_NEEDED = 1;

    private static final String[] ARM_CPU_ARCH_NAMES = {
            "Pre-v4", "v4", "v4T", "v5T", "v5TE", "v5TEJ", "v6", "v6KZ", "v6T2", "v6K",
            "v7", "v6-M", "v6S-M", "v7E-M", "v8", "v8-R", "v8-M.baseline", "v8-M.mainline"
    };

    final boolean is64;
    final int machine;
    final long flags;
    /** null if the binary has no PT_GNU_STACK header (loaders then assume an executable stack) */
    final Boolean executableStack;
    /** Tag_CPU_arch from .ARM.attributes, or -1 if absent */
    final int armCpuArch;
    /** Tag_ABI_VFP_args from .ARM.attributes, or -1 if absent */
    final int armVfpArgs;

    private final List<Section> sections = new ArrayList<Section>();
    private final List<String> needed = new ArrayList<String>();
    private final Set<String> exports = new TreeSet<String>();
    /** version name (e.g. GLIBC_2.28) -> undefined symbols bound to it */
    private final Map<String, Set<String>> requiredVersions = new LinkedHashMap<String, Set<String>>();

    ElfFile(File file) throws IOException {
        super(file);
        if (data.length < 64 || u8(0) != 0x7f || u8(1) != 'E' || u8(2) != 'L' || u8(3) != 'F') {
            throw new IllegalStateException(file + ": not an ELF file");
        }
        is64 = u8(4) == 2;
        order(u8(5) == 2 ? ByteOrder.BIG_ENDIAN : ByteOrder.LITTLE_ENDIAN);
        machine = u16(18);

        long phoff = is64 ? u64(32) : u32(28);
        long shoff = is64 ? u64(40) : u32(32);
        flags = u32(is64 ? 48 : 36);
        int phentsize = u16(is64 ? 54 : 42);
        int phnum = u16(is64 ? 56 : 44);
        int shentsize = u16(is64 ? 58 : 46);
        int shnum = u16(is64 ? 60 : 48);

        for (int i = 0; i < shnum; i++) {
            sections.add(new Section(shoff + (long) i * shentsize));
        }

        Boolean execStack = null;
        for (int i = 0; i < phnum; i++) {
            long ph = phoff + (long) i * phentsize;
            if (u32(ph) == PT_GNU_STACK) {
                long phFlags = is64 ? u32(ph + 4) : u32(ph + 24);
                execStack = (phFlags & PF_X) != 0;
            }
        }
        executableStack = execStack;

        readDynamic();
        readSymbols();
        int[] arm = readArmAttributes();
        armCpuArch = arm[0];
        armVfpArgs = arm[1];
    }

    @Override
    String describeArchitecture() {
        return "ELF" + (is64 ? "64" : "32") + " machine=" + machine
                + " " + (buf.order() == ByteOrder.BIG_ENDIAN ? "big" : "little") + "-endian";
    }

    @Override
    List<String> dependencies() {
        return Collections.unmodifiableList(needed);
    }

    @Override
    Set<String> exportedSymbols() {
        return Collections.unmodifiableSet(exports);
    }

    /** Version name -> symbols requiring it, e.g. {@code GLIBC_2.28 -> [fcntl]} */
    Map<String, Set<String>> requiredVersions() {
        return Collections.unmodifiableMap(requiredVersions);
    }

    static String armCpuArchName(int tag) {
        return tag >= 0 && tag < ARM_CPU_ARCH_NAMES.length ? ARM_CPU_ARCH_NAMES[tag] : "unknown(" + tag + ")";
    }

    private void readDynamic() {
        for (Section s : sections) {
            if (s.type != SHT_DYNAMIC) {
                continue;
            }
            Section strtab = sections.get((int) s.link);
            int entSize = is64 ? 16 : 8;
            for (long off = s.offset; off + entSize <= s.offset + s.size; off += entSize) {
                long tag = is64 ? u64(off) : u32(off);
                long val = is64 ? u64(off + 8) : u32(off + 4);
                if (tag == 0) {
                    break;
                }
                if (tag == DT_NEEDED) {
                    needed.add(cstring(strtab.offset + val));
                }
            }
        }
    }

    private void readSymbols() {
        Section dynsym = null;
        Section versym = null;
        Section verneed = null;
        for (Section s : sections) {
            if (s.type == SHT_DYNSYM) dynsym = s;
            else if (s.type == SHT_GNU_VERSYM) versym = s;
            else if (s.type == SHT_GNU_VERNEED) verneed = s;
        }
        if (dynsym == null) {
            return;
        }

        Map<Integer, String> versionNames = new HashMap<Integer, String>();
        if (verneed != null) {
            Section strtab = sections.get((int) verneed.link);
            long vn = verneed.offset;
            for (long i = 0; i < verneed.info; i++) {
                int count = u16(vn + 2);
                long aux = vn + u32(vn + 8);
                for (int j = 0; j < count; j++) {
                    int index = u16(aux + 6) & 0x7fff;
                    String name = cstring(strtab.offset + u32(aux + 8));
                    versionNames.put(index, name);
                    if (!requiredVersions.containsKey(name)) {
                        requiredVersions.put(name, new TreeSet<String>());
                    }
                    aux += u32(aux + 12);
                }
                long next = u32(vn + 12);
                if (next == 0) {
                    break;
                }
                vn += next;
            }
        }

        Section strtab = sections.get((int) dynsym.link);
        int entSize = is64 ? 24 : 16;
        int count = (int) (dynsym.size / entSize);
        for (int i = 1; i < count; i++) {
            long sym = dynsym.offset + (long) i * entSize;
            long nameOff = u32(sym);
            int info = is64 ? u8(sym + 4) : u8(sym + 12);
            int shndx = is64 ? u16(sym + 6) : u16(sym + 14);
            String name = cstring(strtab.offset + nameOff);
            int bind = info >> 4;
            int type = info & 0xf;
            if (shndx != 0) {
                boolean global = bind == 1 || bind == 2;
                if (global && type == 2) {
                    exports.add(name);
                }
            } else if (versym != null && !name.isEmpty()) {
                String version = versionNames.get(u16(versym.offset + 2L * i) & 0x7fff);
                if (version != null) {
                    requiredVersions.get(version).add(name);
                }
            }
        }
    }

    /** @return {Tag_CPU_arch, Tag_ABI_VFP_args}, -1 where absent */
    private int[] readArmAttributes() {
        int[] result = {-1, -1};
        for (Section s : sections) {
            if (s.type != SHT_ARM_ATTRIBUTES || s.size < 1 || u8(s.offset) != 'A') {
                continue;
            }
            long end = s.offset + s.size;
            long sub = s.offset + 1;
            while (sub + 4 < end) {
                long subLen = u32(sub);
                String vendor = cstring(sub + 4);
                long p = sub + 4 + vendor.length() + 1;
                if ("aeabi".equals(vendor)) {
                    while (p < sub + subLen) {
                        long[] tag = uleb(p);
                        long blockLen = u32(tag[1]);
                        if (tag[0] == 1) { // Tag_File
                            parseArmTags(tag[1] + 4, p + blockLen, result);
                        }
                        p += blockLen;
                    }
                }
                if (subLen == 0) {
                    break;
                }
                sub += subLen;
            }
        }
        return result;
    }

    private void parseArmTags(long p, long end, int[] result) {
        while (p < end) {
            long[] tag = uleb(p);
            p = tag[1];
            int t = (int) tag[0];
            if (t == 4 || t == 5 || t == 67) {
                p += cstring(p).length() + 1;
            } else if (t == 32) {
                p = uleb(p)[1];
                p += cstring(p).length() + 1;
            } else if (t == 65) {
                return; // Tag_also_compatible_with: nested encoding, nothing we need follows reliably
            } else if (t < 32 || t % 2 == 0) {
                long[] val = uleb(p);
                p = val[1];
                if (t == 6) result[0] = (int) val[0];
                if (t == 28) result[1] = (int) val[0];
            } else {
                p += cstring(p).length() + 1;
            }
        }
    }

    /** @return {value, offset after the ULEB128} */
    private long[] uleb(long p) {
        long value = 0;
        int shift = 0;
        int b;
        do {
            b = u8(p++);
            value |= (long) (b & 0x7f) << shift;
            shift += 7;
        } while ((b & 0x80) != 0);
        return new long[]{value, p};
    }

    private final class Section {
        final long type;
        final long offset;
        final long size;
        final long link;
        final long info;

        Section(long sh) {
            type = u32(sh + 4);
            offset = is64 ? u64(sh + 24) : u32(sh + 16);
            size = is64 ? u64(sh + 32) : u32(sh + 20);
            link = u32(is64 ? sh + 40 : sh + 24);
            info = u32(is64 ? sh + 44 : sh + 28);
        }
    }
}
