package jssc;

import jssc.junit.rules.DisplayMethodNameRule;
import org.junit.Test;
import org.scijava.nativelib.NativeLibraryUtil;
import org.slf4j.LoggerFactory;

import java.net.URL;
import java.net.URLClassLoader;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.junit.Assert.assertThat;

/**
 * Loads jssc and native-lib-loader with separate class loaders, the way an OSGi framework
 * loads them as separate bundles. JNI binds native methods only to libraries loaded by the
 * class loader of the declaring class, so jssc has to load the library itself.
 *
 * Runs in its own JVM (<code>reuseForks=false</code>): a native library file can be loaded by
 * one class loader per JVM only.
 */
public class SeparateClassLoaderTest extends DisplayMethodNameRule {

    @Test
    public void nativeMethodsBindWhenNativeLibLoaderHasAnotherClassLoader() throws Exception {
        assertThat(nativeLibraryVersion(newJsscClassLoader()), is(not(nullValue())));
    }

    /**
     * An OSGi bundle refresh loads jssc again with a new class loader. The library file from
     * <code>jssc.boot.library.path</code> is still bound to the old one, so jssc falls back to extraction.
     */
    @Test
    public void bootPathLibraryCanBeLoadedAgainByNewClassLoader() throws Exception {
        String libDir = NativeLibraryUtil.getPlatformLibraryPath(location(SerialNativeInterface.class).getPath() + "natives/");
        System.setProperty("jssc.boot.library.path", libDir);
        try {
            assertThat(nativeLibraryVersion(newJsscClassLoader()), is(not(nullValue())));
            assertThat(nativeLibraryVersion(newJsscClassLoader()), is(not(nullValue())));
        } finally {
            System.clearProperty("jssc.boot.library.path");
        }
    }

    private static ClassLoader newJsscClassLoader() {
        ClassLoader nativeLibLoader = new URLClassLoader(
                new URL[] { location(NativeLibraryUtil.class), location(LoggerFactory.class) }, null);
        return new URLClassLoader(new URL[] { location(SerialNativeInterface.class) }, nativeLibLoader);
    }

    private static Object nativeLibraryVersion(ClassLoader jsscLoader) throws Exception {
        Class<?> nativeInterface = Class.forName(SerialNativeInterface.class.getName(), true, jsscLoader);
        return nativeInterface.getMethod("getNativeLibraryVersion").invoke(null);
    }

    private static URL location(Class<?> type) {
        return type.getProtectionDomain().getCodeSource().getLocation();
    }
}
