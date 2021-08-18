### List Ports

```java
import static jssc.SerialPort.*;

for(String port : SerialPortList.getPortNames()) {
   System.out.println(port);
}
```

### Write Data

```java
import static jssc.SerialPort.*;

SerialPort port = new SerialPort("COM1");
port.openPort();
port.setParams(BAUDRATE_9600,  DATABITS_8, STOPBITS_1, PARITY_NONE);
// port.setParams(9600, 8, 1, 0); // alternate technique
port.writeBytes("Testing serial from Java".getBytes());
port.closePort();
```

### Read Data
```java
import static jssc.SerialPort.*;

SerialPort port = new SerialPort("COM1");
port.openPort();
port.setParams(BAUDRATE_9600,  DATABITS_8, STOPBITS_1, PARITY_NONE);
// port.setParams(9600, 8, 1, 0); // alternate technique
byte[] buffer = port.readBytes(10 /* read first 10 bytes */);
port.closePort();
```

### Event Listener
**Note:** The mask is an additive quantity, thus to set a mask on the expectation of the arrival of Event Data (`MASK_RXCHAR`) and change the status lines `CTS` (`MASK_CTS`), `DSR` (`MASK_DSR`) we just need to combine all three masks.

```java
import static jssc.SerialPort.*;

SerialPort port = new SerialPort("COM1"); 
port.openPort();
port.setParams(BAUDRATE_9600,  DATABITS_8, STOPBITS_1, PARITY_NONE);
// port.setParams(9600, 8, 1, 0); // alternate technique
int mask = SerialPort.MASK_RXCHAR + SerialPort.MASK_CTS + SerialPort.MASK_DSR;
port.setEventsMask(mask);
port.addEventListener(new MyPortListener(port) /* defined below */);

/**
 * In this class must implement the method serialEvent, through it we learn about
 * events that happened to our port. But we will not report on all events but only
 * those that we put in the mask. In this case the arrival of the data and change the
 * status lines CTS and DSR
 */
class MyPortListener implements SerialPortEventListener {
    SerialPort port;
    public MyPortListener(SerialPort port) {
        this.port = port;
    }
    public void serialEvent(SerialPortEvent event) {
        if(event.isRXCHAR()){ // data is available
            // read data, if 10 bytes available
            if(event.getEventValue() == 10){
                try {
                    byte[] buffer = port.readBytes(10);
                } catch (SerialPortException ex) {
                    System.out.println(ex);
                }
            }
        } else if(event.isCTS()){ // CTS line has changed state
            if(event.getEventValue() == 1){ // line is ON
                System.out.println("CTS - ON");
            } else {
                System.out.println("CTS - OFF");
            }
        } else if(event.isDSR()){ // DSR line has changed state
            if(event.getEventValue() == 1){ // line is ON
                System.out.println("DSR - ON");
            } else {
                System.out.println("DSR - OFF");
            }
        }
    }
}
```

### Override Native Library Path

Since 2.9.3, JSSC can look in a static native path location `jssc.boot.library.path` for `.dll`, `.so`, `.dylib` files.  See also #92 for use-cases.

```java
System.setProperty("jssc.boot.library.path", "/path/to/mylibs/");
```

... or via command line:

```bash
java -Djssc.boot.library.path=/path/to/mylibs -jar ...
```
