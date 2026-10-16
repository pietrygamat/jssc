package jssc;

import jssc.junit.rules.DisplayMethodNameRule;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.Locale;

import static org.junit.Assert.assertNotNull;

/**
 * #80 (2.9.3): the native library could not be found on Turkish Windows, because the loader
 * lower-cased "WINDOWS" with the default locale ("wındows", dotless i). Surefire runs each test
 * class in a fresh JVM, so the library is loaded here for the first time, under a Turkish locale.
 */
public class TurkishLocaleLoadTest extends DisplayMethodNameRule {

    private static Locale previous;

    @BeforeClass
    public static void turkish() {
        previous = Locale.getDefault();
        Locale.setDefault(new Locale("tr", "TR"));
    }

    @AfterClass
    public static void restore() {
        Locale.setDefault(previous);
    }

    @Test
    public void loadsTheNativeLibrary() {
        assertNotNull(SerialNativeInterface.getNativeLibraryVersion());
    }
}
