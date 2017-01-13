package jssc;
/**
 * Created by inkys_000 on 1/10/2017.
 */
public class SerialPortTest {
    public static void main(String[] args) {
        System.out.println("HELLO");
        String[] ports = SerialPortList.getPortNames();
        for (String port : ports)
            System.out.println(port);
        for (String port : ports) {
            System.out.println(port);
            SerialPortList.getPortProperties(port);
        }
    }
}
