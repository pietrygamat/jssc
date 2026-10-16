package jssc;

import jssc.junit.rules.DisplayMethodNameRule;
import org.junit.Test;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assume.assumeTrue;

/**
 * Checks that the build produced what it claims to, because both went wrong before.
 */
public class BuildOutputTest extends DisplayMethodNameRule {

    /**
     * #51, #65, #174: tests (and users) silently ran against a different native library than
     * the one just built, e.g. the bundled one or an old copy elsewhere on the system. When the
     * build compiled the native library, the tests must load exactly that one.
     */
    @Test
    public void testsUseTheNativeLibraryJustBuilt() {
        assumeTrue("native library was not built in this run", "false".equals(System.getProperty("jssc.test.cmake.build.skip")));
        assertEquals(System.getProperty("jssc.test.release.version"), SerialNativeInterface.getNativeLibraryVersion());
    }

    /**
     * #110 (2.9.3): the GitHub release jar was built with the wrong JDK and did not run on the
     * Java 6 it promises. The class files must have the version of target.java.version.
     */
    @Test
    public void classFilesTargetTheConfiguredJavaVersion() throws IOException {
        String target = System.getProperty("jssc.test.target.java.version");
        assumeTrue("target.java.version not passed by surefire", target != null && !target.startsWith("${"));
        int expectedMajor = 44 + Integer.parseInt(target.replaceFirst("^1\\.", ""));
        assertEquals("class file major version of jssc.SerialPort", expectedMajor, majorVersion(SerialPort.class));
    }

    private static int majorVersion(Class<?> type) throws IOException {
        InputStream in = type.getResourceAsStream(type.getSimpleName() + ".class");
        try {
            DataInputStream data = new DataInputStream(in);
            data.readInt();           // magic
            data.readUnsignedShort(); // minor
            return data.readUnsignedShort();
        } finally {
            in.close();
        }
    }
}
