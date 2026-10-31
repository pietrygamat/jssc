package jssc;

import jssc.junit.rules.DisplayMethodNameRule;
import org.junit.Before;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeFalse;
import static org.junit.Assume.assumeTrue;

/**
 * Checks the <code>JSSC-IDENT</code> line (see <code>src/main/cpp/ident.cpp</code>) of the native library built
 * in this run against the values <code>ant/build.xml</code> passed to CMake. The library is read, not loaded,
 * so this is the only test that also runs when cross-compiling.
 */
public class NativeIdentTest extends DisplayMethodNameRule {

    private static final String[] LIBRARY_NAMES = {"libjssc.so", "libjssc.dylib", "jssc.dll"};
    private static final String IDENT_MARKER = "JSSC-IDENT ";

    private File nativeDir;
    private Map<String, String> ident;

    @Before
    public void readIdent() throws IOException {
        // Set by surefire in pom.xml; absent when run outside maven, e.g. from an IDE
        final String dir = System.getProperty("jssc.expected.native.dir");
        assumeTrue("Native output directory not passed by maven", !isBlank(dir));
        assumeFalse("No native library built in this run", Boolean.getBoolean("jssc.native.build.skip"));
        nativeDir = new File(dir);
        ident = parseIdent(findLibrary(nativeDir));
    }

    @Test
    public void versionIsTheNativeVersion() {
        assertEquals("version", System.getProperty("jssc.expected.native.version"), ident.get("version"));
    }

    @Test
    public void targetIsTheOutputDirectory() {
        assertEquals("target", nativeDir.getName(), ident.get("target"));
    }

    @Test
    public void revIsThePassedRevision() {
        final String expected = System.getProperty("jssc.expected.build.rev");
        if (isBlank(expected)) {
            // CMake took it from git
            final String rev = ident.get("rev");
            assertTrue("rev is " + rev, rev != null && rev.matches("[0-9a-f]{4,40}(-dirty)?|unknown"));
        } else {
            assertEquals("rev", expected, ident.get("rev"));
        }
    }

    @Test
    public void originIsThePassedOrigin() {
        final String expected = System.getProperty("jssc.expected.build.origin");
        assertEquals("origin", isBlank(expected) ? "local" : expected, ident.get("origin"));
    }

    private static File findLibrary(File dir) {
        final List<File> found = new ArrayList<File>();
        for (String name : LIBRARY_NAMES) {
            final File file = new File(dir, name);
            if (file.isFile()) {
                found.add(file);
            }
        }
        assertEquals("Native libraries in " + dir + ": " + found, 1, found.size());
        return found.get(0);
    }

    /**
     * Finds the one printable <code>JSSC-IDENT key=value ...</code> line in the binary
     */
    private static Map<String, String> parseIdent(File library) throws IOException {
        final String content = new String(readFully(library), "ISO-8859-1");
        final int start = content.indexOf(IDENT_MARKER);
        assertTrue("No " + IDENT_MARKER + "in " + library, start >= 0);
        assertEquals("More than one " + IDENT_MARKER + "in " + library, -1, content.indexOf(IDENT_MARKER, start + 1));
        int end = start;
        while (end < content.length() && content.charAt(end) >= 0x20 && content.charAt(end) < 0x7f) {
            end++;
        }
        final String line = content.substring(start + IDENT_MARKER.length(), end);
        final Map<String, String> fields = new HashMap<String, String>();
        for (String field : line.split(" ")) {
            final int eq = field.indexOf('=');
            assertTrue("Malformed field '" + field + "' in " + line, eq > 0);
            fields.put(field.substring(0, eq), field.substring(eq + 1));
        }
        return fields;
    }

    private static byte[] readFully(File file) throws IOException {
        final InputStream in = new FileInputStream(file);
        try {
            final ByteArrayOutputStream out = new ByteArrayOutputStream();
            final byte[] buffer = new byte[8192];
            int n;
            while ((n = in.read(buffer)) != -1) {
                out.write(buffer, 0, n);
            }
            return out.toByteArray();
        } finally {
            in.close();
        }
    }

    private static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }
}
