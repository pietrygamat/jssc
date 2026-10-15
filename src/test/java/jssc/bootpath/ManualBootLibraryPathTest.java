package jssc.bootpath;

import jssc.SerialNativeInterface;
import jssc.junit.rules.DisplayMethodNameRule;
import org.junit.Test;

import java.io.File;

import static org.hamcrest.CoreMatchers.*;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Tests if a valid <code>jssc.boot.library.path</code> which DOES contain a native library
 * will predictably pass.  This test can ONLY be run if a native binary was created
 * during the build process.  See also <code>maven.exclude.tests</code>.
 *
 * TODO: This MUST be in its own class to run in a separate JVM (https://stackoverflow.com/questions/68657855)
 * - JUnit does NOT currently offer JVM unloading between methods.
 * - maven-surefire-plugin DOES offer JVM unloading between classes using <code>reuseForks=false</code>
 * - Unloading is needed due to the loader calling System.load(...) which is static
 */
public class ManualBootLibraryPathTest extends DisplayMethodNameRule {
    @Test
    public void testBootPathOverride() {
        String nativeLibDir = findBuiltNativeLibDir(new File(System.getProperty("user.dir"), "target/cmake/natives"));
        System.setProperty("jssc.boot.library.path", nativeLibDir);
        // Nothing may be extracted when the boot path is used (#165)
        File tmp = new File(System.getProperty("user.dir"), "target/bootpath-tmp");
        tmp.mkdirs();
        System.setProperty("java.io.tmpdir", tmp.getAbsolutePath());
        int before = tmp.list().length;
        try {
            final String nativeLibraryVersion = SerialNativeInterface.getNativeLibraryVersion();
            assertThat(nativeLibraryVersion, is(not(nullValue())));
            assertThat(nativeLibraryVersion, is(not("")));
        } catch (UnsatisfiedLinkError linkError) {
            linkError.printStackTrace();
            fail("Should be able to call method!");
        }
        assertThat("no temporary directory may be created", tmp.list().length, is(before));
    }

    /**
     * The native build writes one <code>natives/&lt;os&gt;_&lt;suffix&gt;</code> directory for the host
     */
    private static String findBuiltNativeLibDir(File natives) {
        File[] dirs = natives.listFiles();
        assertTrue("No native build output in " + natives, dirs != null && dirs.length > 0);
        for (File dir : dirs) {
            String[] files = dir.list();
            if (dir.isDirectory() && files != null && files.length > 0) {
                return dir.getAbsolutePath();
            }
        }
        fail("No native library in " + natives);
        return null;
    }
}
