```kotlin
import jssc.*
import jssc.SerialPort.*

fun main() {
    listPorts()
    writeData()
    readData()
    eventListener()
}
// #List Ports
fun listPorts() {
    val ports = SerialPortList.getPortNames()
    ports.forEach { println(it) }
}

// #Write Data
fun writeData() {
    val port = SerialPort("COM10")
    port.openPort()
    port.setParams(BAUDRATE_115200, DATABITS_8, STOPBITS_1, PARITY_NONE)
    // port.setParams(9600, 8, 1, 0); // alternate technique
    port.writeBytes("Testing serial from Kotlin".encodeToByteArray())
    // The following shows up in the serial port (prettified for readability):
    // 54 65 73 74 69 6E 67 20 73 65 72 69 61 6C 20 66 72 6F 6D 20 4B 6F 74 6C 69 6E
    port.closePort()
}

// #Read Data
fun readData() {
    val port = SerialPort("COM10")
    port.openPort()
    port.setParams(BAUDRATE_115200, DATABITS_8, STOPBITS_1, PARITY_NONE)
    // port.setParams(9600, 8, 1, 0); // alternate technique
    val buffer = port.readBytes(10 /* read the first 10 bytes */)
    // Print the buffer but pretty, with spaces between bytes and padding to two characters with 0
    // See below for implementation
    println(buffer.fancyToString())
    // Using the same bytes as used in the writeData() method above we get:
    // 54 65 73 74 69 6E 67 20 73 65 72 69 61 6C 20 66 72 6F 6D 20 4B 6F 74 6C 69 6E
    port.closePort()
}

// #Event Listener
fun eventListener() {
    val port = SerialPort("COM10")
    port.openPort()
    port.setParams(BAUDRATE_115200, DATABITS_8, STOPBITS_1, PARITY_NONE)
    // port.setParams(9600, 8, 1, 0); // alternate technique
    val mask = MASK_RXCHAR + MASK_CTS + MASK_DSR
    port.eventsMask = mask
    port.addEventListener(MyPortListener(port))
}

/*
 * In this class must implement the method serialEvent, through it we learn about
 * events that happened to our port. But we will not report on all events but only
 * those that we put in the mask. In this case the arrival of the data and change the
 * status lines CTS and DSR
 */
internal class MyPortListener(private val port: SerialPort) :
    SerialPortEventListener {
    override fun serialEvent(event: SerialPortEvent) {
        when {
            event.isRXCHAR -> { // data is available
                // read data, if 10 bytes available
                if (event.eventValue == 10) {
                    try {
                        println(port.readBytes(10).fancyToString())
                    } catch (ex: SerialPortException) {
                        println(ex)
                    }
                }
            }
            event.isCTS -> { // CTS line has changed state
                if (event.eventValue == 1) { // line is ON
                    println("CTS - ON")
                } else {
                    println("CTS - OFF")
                }
            }
            event.isDSR -> { // DSR line has changed state
                if (event.eventValue == 1) { // line is ON
                    println("DSR - ON")
                } else {
                    println("DSR - OFF")
                }
            }
        }
    }
}

// Please note that Unsigned Integers are a stable feature in Kotlin version >= 1.5
// This is an extension function for ByteArrays to print them as unsigned hexadecimals
private fun ByteArray.fancyToString(): String {
    var res = ""
    // Converts each element from a signed byte to a unsigned byte
    this.asUByteArray().forEach {
        // Returns the bytes as two hexadecimals, separated by spaces per pair. Fills out the leading zero if necessary
        res += it.toString(16).toUpperCase().padStart(2, '0').padEnd(3, ' ')
    }
    // And remove the trailing whitespace
    return res.trim()
}
```