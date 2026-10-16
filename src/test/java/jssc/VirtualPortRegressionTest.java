package jssc;

import jssc.junit.rules.DisplayMethodNameRule;
import jssc.junit.rules.VirtualPortRule;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeTrue;

/**
 * Regression tests for bugs that reached users before (see JOURNAL.md, "Lessons that recur").
 * They run against a socat null-modem pair, so only on Linux and macOS with socat installed.
 */
public class VirtualPortRegressionTest extends DisplayMethodNameRule {

    private static final byte[] PAYLOAD = {0x6A, 0x73, 0x73, 0x63};

    @Rule
    public VirtualPortRule virtualPort = new VirtualPortRule();

    private SerialPort port;
    private SerialPort peer;

    @Before
    public void openPorts() throws SerialPortException {
        assumeTrue("socat virtual ports not available", virtualPort.isAvailable());
        port = new SerialPort(virtualPort.getVirtualCom1().getAbsolutePath());
        peer = new SerialPort(virtualPort.getVirtualCom2().getAbsolutePath());
        assertTrue(port.openPort());
        assertTrue(peer.openPort());
    }

    @After
    public void closePorts() throws SerialPortException {
        for (SerialPort p : new SerialPort[]{port, peer}) {
            if (p != null && p.isOpened()) {
                p.closePort();
            }
        }
    }

    /**
     * #113 (2.9.3): a cast added to silence a compiler warning truncated the baud rate to a byte,
     * so every rate above 255 was set wrong. Read the speed back from the terminal driver.
     */
    @Test
    public void baudRateReachesTheDriverUnchanged() throws Exception {
        assumeTrue("stty -F is Linux syntax", SerialNativeInterface.getOsType() == SerialNativeInterface.OS_LINUX);
        // jssc locks the port with TIOCEXCL, which stops stty from opening it (only root may)
        port.closePort();
        System.setProperty(SerialNativeInterface.PROPERTY_JSSC_NO_TIOCEXCL, "true");
        try {
            assertTrue(port.openPort());
        } finally {
            System.clearProperty(SerialNativeInterface.PROPERTY_JSSC_NO_TIOCEXCL);
        }
        int[] rates = {300, 1200, 2400, 4800, 9600, 19200, 38400, 57600, 115200, 230400, 460800, 921600};
        for (int rate : rates) {
            // The return value is false on a pseudo terminal: termios is applied, but it has no modem
            // lines, so the RTS/DTR ioctl that follows fails. The speed is what this test is about.
            port.setParams(rate, 8, 1, SerialPort.PARITY_NONE);
            assertEquals("speed reported by the driver after setParams(" + rate + ")",
                    String.valueOf(rate), stty(virtualPort.getVirtualCom1().getAbsolutePath(), "speed"));
        }
    }

    /**
     * #175 → #192 (2.10.1): native input validation rejected 0-byte reads, which the Windows
     * event loop issues and existing applications rely on. They must return an empty array.
     */
    @Test
    public void zeroByteReadOnAnOpenPortReturnsEmptyArray() throws Exception {
        byte[] read = port.readBytes(0);
        assertNotNull(read);
        assertEquals(0, read.length);
    }

    @Test
    public void bytesArriveUnchanged() throws Exception {
        assertTrue(peer.writeBytes(PAYLOAD));
        assertArrayEquals(PAYLOAD, port.readBytes(PAYLOAD.length, 2000));
    }

    /**
     * #86: events carry the port they came from. #181 (2.10.0): removeEventListener() returns
     * true once and false when no listener is left, and stops the event thread.
     */
    @Test(timeout = 20000)
    public void eventListenerLifecycle() throws Exception {
        final CountDownLatch received = new CountDownLatch(1);
        final AtomicReference<SerialPortEvent> event = new AtomicReference<SerialPortEvent>();
        port.addEventListener(new SerialPortEventListener() {
            @Override
            public void serialEvent(SerialPortEvent e) {
                if (e.isRXCHAR() && e.getEventValue() > 0 && event.compareAndSet(null, e)) {
                    received.countDown();
                }
            }
        }, SerialPort.MASK_RXCHAR);

        assertTrue(peer.writeBytes(PAYLOAD));
        assertTrue("no RXCHAR event", received.await(5, TimeUnit.SECONDS));
        assertSame(port, event.get().getPort());

        assertTrue(port.removeEventListener());
        assertFalse("second removeEventListener()", port.removeEventListener());
    }

    /**
     * #107 / #181 (2.10.0): closing a port whose device vanished (USB adapter pulled) threw before
     * reaching the native close, leaving ghost ports behind. It must close quietly.
     */
    @Test(timeout = 20000)
    public void closePortAfterUnplugDoesNotThrow() throws Exception {
        port.addEventListener(new SerialPortEventListener() {
            @Override
            public void serialEvent(SerialPortEvent e) {
            }
        }, SerialPort.MASK_RXCHAR);
        virtualPort.unplug();

        port.closePort();
        assertFalse(port.isOpened());
        peer.closePort();
    }

    /**
     * #94 → #155 (2.9.6): reading from a device that was unplugged looped forever instead of
     * reporting the error. A read must fail with an exception, not hang.
     *
     * Still hangs on Linux: after the hangup read() returns 0, which the native read loop treats
     * as "try again" (the TODO in _nix_based/jssc.cpp readBytes). Enable with the B-04 fix.
     */
    @Test(timeout = 20000)
    @org.junit.Ignore("B-04: native readBytes loops forever when read() returns 0 after a hangup")
    public void readAfterUnplugFailsInsteadOfHanging() throws Exception {
        virtualPort.unplug();
        try {
            port.readBytes(1);
            fail("read from an unplugged device returned");
        } catch (SerialPortException expected) {
            // the device is gone
        }
    }

    /**
     * #90 (2.9.3): select() corrupts memory for file descriptors >= 1024, which long-running
     * applications reach. Linux uses poll(); keep it that way (macOS still uses select, B-06).
     */
    @Test(timeout = 20000)
    public void worksWithFileDescriptorsAbove1024() throws Exception {
        assumeTrue("poll() is only used on Linux", SerialNativeInterface.getOsType() == SerialNativeInterface.OS_LINUX);
        port.closePort();
        List<FileInputStream> filler = new ArrayList<FileInputStream>();
        try {
            try {
                for (int i = 0; i < 1100; i++) {
                    filler.add(new FileInputStream("/dev/null"));
                }
            } catch (IOException tooManyOpenFiles) {
                assumeTrue("cannot open 1100 files: " + tooManyOpenFiles.getMessage(), false);
            }
            port = new SerialPort(virtualPort.getVirtualCom1().getAbsolutePath());
            assertTrue(port.openPort());
            assertTrue(peer.writeBytes(PAYLOAD));
            assertArrayEquals(PAYLOAD, port.readBytes(PAYLOAD.length, 2000));
        } finally {
            for (FileInputStream f : filler) {
                f.close();
            }
        }
    }

    private static String stty(String device, String setting) throws IOException, InterruptedException {
        Process p = new ProcessBuilder("stty", "-F", device, setting).redirectErrorStream(true).start();
        BufferedReader out = new BufferedReader(new InputStreamReader(p.getInputStream(), "UTF-8"));
        String line = out.readLine();
        p.waitFor();
        return line == null ? "" : line.trim();
    }
}
