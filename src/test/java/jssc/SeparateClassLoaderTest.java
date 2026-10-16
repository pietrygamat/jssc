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
 * Runs in its own JVM (<code>reuseForks=false</code>): a native library can be loaded by
 * one class loader per JVM only.
 */
public class SeparateClassLoaderTest extends DisplayMethodNameRule {

    @Test
    public void nativeMethodsBindWhenNativeLibLoaderHasAnotherClassLoader() throws Exception {
        ClassLoader nativeLibLoader = new URLClassLoader(
                new URL[] { location(NativeLibraryUtil.class), location(LoggerFactory.class) }, null);
        ClassLoader jsscLoader = new URLClassLoader(new URL[] { location(SerialNativeInterface.class) }, nativeLibLoader);

        Class<?> nativeInterface = Class.forName(SerialNativeInterface.class.getName(), true, jsscLoader);
        Object version = nativeInterface.getMethod("getNativeLibraryVersion").invoke(null);

        assertThat(version, is(not(nullValue())));
    }

    private static URL location(Class<?> type) {
        return type.getProtectionDomain().getCodeSource().getLocation();
    }
}
