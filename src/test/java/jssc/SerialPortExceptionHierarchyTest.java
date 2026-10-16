package jssc;

import jssc.junit.rules.DisplayMethodNameRule;
import org.junit.Test;

import java.io.IOException;

import static org.junit.Assert.assertTrue;

/**
 * #160 (2.10.0): jssc exceptions extend IOException so that libraries can declare
 * {@code throws IOException} without leaking jssc types. Code that catches IOException
 * before SerialPortException relies on it; changing it back is an incompatible change.
 */
public class SerialPortExceptionHierarchyTest extends DisplayMethodNameRule {

    @Test
    public void serialPortExceptionIsAnIOException() {
        assertTrue(IOException.class.isAssignableFrom(SerialPortException.class));
    }

    @Test
    public void serialPortTimeoutExceptionIsAnIOException() {
        assertTrue(IOException.class.isAssignableFrom(SerialPortTimeoutException.class));
    }
}
