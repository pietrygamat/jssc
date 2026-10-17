package jssc;

import jssc.junit.rules.DisplayMethodNameRule;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

import static org.junit.Assert.assertEquals;
import static org.junit.Assume.assumeTrue;

/**
 * Port name matching on Linux (#179, #180, #182). Device nodes are simulated with symbolic
 * links to /dev/null: SerialPortList only lists entries that are neither files nor directories.
 * ttyS* names are left out on purpose, because SerialPortList probes them by opening them.
 */
public class SerialPortListTest extends DisplayMethodNameRule {

    @Rule
    public TemporaryFolder dev = new TemporaryFolder();

    @Before
    public void linuxOnly() {
        assumeTrue(SerialNativeInterface.getOsType() == SerialNativeInterface.OS_LINUX);
    }

    @Test
    public void defaultPatternListsKnownSerialDevices() throws Exception {
        devices("ttyUSB0", "ttyUSB10", "ttyUSB2", "ttyACM0", "ttyAMA0", "rfcomm0", "ttyO1",
                // Moxa (#180)
                "ttyM0", "ttyMXUSB3", "ttyMUE12",
                // not serial ports
                "tty0", "console", "null", "ttyUSB");
        assertEquals(paths("rfcomm0", "ttyACM0", "ttyAMA0", "ttyM0", "ttyMUE12", "ttyMXUSB3", "ttyO1",
                        "ttyUSB0", "ttyUSB2", "ttyUSB10"),
                Arrays.asList(SerialPortList.getPortNames(dev.getRoot().getPath())));
    }

    /** #182: names the default pattern does not know are found with getPortNames(Pattern), not a system property */
    @Test
    public void customPatternFindsExoticNames() throws Exception {
        devices("ttyXRUSB0", "ttyUSB0");
        assertEquals(paths("ttyXRUSB0"),
                Arrays.asList(SerialPortList.getPortNames(dev.getRoot().getPath(), Pattern.compile("ttyXRUSB[0-9]+"))));
    }

    private void devices(String... names) throws Exception {
        for (String name : names) {
            Process ln = new ProcessBuilder("ln", "-s", "/dev/null", new File(dev.getRoot(), name).getPath()).start();
            assertEquals("ln -s " + name, 0, ln.waitFor());
        }
    }

    private List<String> paths(String... names) {
        String[] result = new String[names.length];
        for (int i = 0; i < names.length; i++) {
            result[i] = dev.getRoot().getPath() + "/" + names[i];
        }
        return Arrays.asList(result);
    }
}
