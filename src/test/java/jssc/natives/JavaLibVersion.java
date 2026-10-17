package jssc.natives;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads {@code SerialNativeInterface.libVersion} from the source file. Reading the field via
 * reflection would initialize the class and thereby load the native library.
 */
final class JavaLibVersion {

    private static final Pattern DECLARATION =
            Pattern.compile("String\\s+libVersion\\s*=\\s*\"([^\"]+)\"");

    private JavaLibVersion() {
    }

    static String read() throws IOException {
        File source = new File(System.getProperty("basedir", "."), "src/main/java/jssc/SerialNativeInterface.java");
        StringBuilder text = new StringBuilder();
        Reader in = new InputStreamReader(new FileInputStream(source), "UTF-8");
        try {
            char[] chunk = new char[8192];
            int n;
            while ((n = in.read(chunk)) != -1) {
                text.append(chunk, 0, n);
            }
        } finally {
            in.close();
        }
        Matcher m = DECLARATION.matcher(text);
        if (!m.find()) {
            throw new IllegalStateException("No libVersion declaration found in " + source);
        }
        return m.group(1);
    }
}
