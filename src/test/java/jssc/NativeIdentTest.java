package jssc;

import jssc.junit.rules.DisplayMethodNameRule;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static jssc.NativeIdent.isBlank;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeFalse;
import static org.junit.Assume.assumeTrue;

/**
 * Checks the <code>JSSC-IDENT</code> line ({@link NativeIdent}) of the native library built in this run against the
 * values <code>ant/build.xml</code> passed to CMake. The library is read, not loaded, so this is the only test that
 * also runs when cross-compiling. {@link PrecompiledNativesTest} checks the natives a release ships.
 */
public class NativeIdentTest extends DisplayMethodNameRule {

    private File nativeDir;
    private Map<String, String> ident;

    @Before
    public void readIdent() throws IOException {
        // Set by surefire in pom.xml; absent when run outside maven, e.g. from an IDE
        final String dir = System.getProperty("jssc.expected.native.dir");
        assumeTrue("Native output directory not passed by maven", !isBlank(dir));
        assumeFalse("No native library built in this run", Boolean.getBoolean("jssc.native.build.skip"));
        nativeDir = new File(dir);
        ident = NativeIdent.parse(findLibrary(nativeDir));
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
        for (String name : NativeIdent.LIBRARY_NAMES) {
            final File file = new File(dir, name);
            if (file.isFile()) {
                found.add(file);
            }
        }
        assertEquals("Native libraries in " + dir + ": " + found, 1, found.size());
        return found.get(0);
    }
}
