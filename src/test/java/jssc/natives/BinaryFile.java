package jssc.natives;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.Charset;
import java.util.List;
import java.util.Set;

/**
 * Minimal bounds-checked reader shared by the ELF, PE and Mach-O parsers.
 *
 * The parsers only read what static checks need (architecture, dependencies,
 * exported symbols, version requirements), so no external tooling such as
 * objdump/readelf/otool has to be present on the build machine.
 */
abstract class BinaryFile {

    static final Charset ASCII = Charset.forName("US-ASCII");

    final File file;
    final byte[] data;
    final ByteBuffer buf;

    BinaryFile(File file) throws IOException {
        this.file = file;
        this.data = readAll(file);
        this.buf = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);
    }

    /** Human readable architecture, e.g. "ELF32 ARM" */
    abstract String describeArchitecture();

    /** Shared libraries the dynamic loader must find before this binary can load */
    abstract List<String> dependencies();

    /** Names of exported (defined, globally visible) function symbols */
    abstract Set<String> exportedSymbols();

    void order(ByteOrder order) {
        buf.order(order);
    }

    int u8(long off) {
        return buf.get(index(off, 1)) & 0xFF;
    }

    int u16(long off) {
        return buf.getShort(index(off, 2)) & 0xFFFF;
    }

    long u32(long off) {
        return buf.getInt(index(off, 4)) & 0xFFFFFFFFL;
    }

    long u64(long off) {
        return buf.getLong(index(off, 8));
    }

    String cstring(long off) {
        int start = index(off, 1);
        int end = start;
        while (end < data.length && data[end] != 0) {
            end++;
        }
        return new String(data, start, end - start, ASCII);
    }

    /** True if the ASCII string occurs in the file, not directly surrounded by digits or dots */
    boolean containsVersionString(String version) {
        byte[] needle = version.getBytes(ASCII);
        outer:
        for (int i = 0; i + needle.length <= data.length; i++) {
            for (int j = 0; j < needle.length; j++) {
                if (data[i + j] != needle[j]) {
                    continue outer;
                }
            }
            if (!isVersionChar(i - 1) && !isVersionChar(i + needle.length)) {
                return true;
            }
        }
        return false;
    }

    private boolean isVersionChar(int i) {
        if (i < 0 || i >= data.length) {
            return false;
        }
        byte b = data[i];
        return b == '.' || (b >= '0' && b <= '9');
    }

    int index(long off, int len) {
        if (off < 0 || off + len > data.length) {
            throw new IllegalStateException(file + ": truncated or malformed (read of " + len
                    + " bytes at offset " + off + ", file size " + data.length + ")");
        }
        return (int) off;
    }

    private static byte[] readAll(File file) throws IOException {
        InputStream in = new FileInputStream(file);
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream((int) file.length());
            byte[] chunk = new byte[8192];
            int n;
            while ((n = in.read(chunk)) != -1) {
                out.write(chunk, 0, n);
            }
            return out.toByteArray();
        } finally {
            in.close();
        }
    }
}
