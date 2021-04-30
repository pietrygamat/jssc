### List Ports

```java
String[] ports = SerialPortList.getPortNames();
for(int i = 0; i < ports.length; i++) {
   System.out.println(ports[i]);
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
                    System.out.println(Arrays.toString(buffer));
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