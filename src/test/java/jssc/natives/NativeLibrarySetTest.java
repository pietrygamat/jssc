package jssc.natives;

import jssc.junit.rules.DisplayMethodNameRule;
import org.junit.Test;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

/**
 * Checks on the set of precompiled natives as a whole, complementing the per-binary
 * {@link NativeLibraryCompatibilityTest}.
 */
public class NativeLibrarySetTest extends DisplayMethodNameRule {

    @Test
    public void everyShippedDirectoryHasACompatibilityPolicy() {
        File[] dirs = NativeTarget.nativesDir().listFiles();
        assertTrue("No natives directory at " + NativeTarget.nativesDir(), dirs != null);
        List<String> unknown = new ArrayList<String>();
        for (File dir : dirs) {
            if (dir.isDirectory() && NativeTarget.forDirectory(dir.getName()) == null) {
                unknown.add(dir.getName());
            }
        }
        assertTrue("Native libraries without a compatibility policy " + unknown + ": add them to NativeTarget",
                unknown.isEmpty());
    }

    /**
     * Releases must ship natives built for exactly this version. 2.10.3 was released with
     * libVersion and all binaries still at 2.10.2, i.e. without the native changes it announced.
     */
    @Test
    public void releaseVersionMatchesNativeLibraryVersion() throws Exception {
        assumeTrue("Only checked in strict mode (release builds, -Djssc.natives.strict=true)", NativeTarget.strict());
        assertEquals("SerialNativeInterface.libVersion differs from the version being released;"
                        + " run the cross-compile workflow before releasing",
                NativeTarget.releaseVersion(), JavaLibVersion.read());
    }
}
