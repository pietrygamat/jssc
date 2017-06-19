package jssc;

import java.util.HashMap;
import java.util.Map;

/**
 * Created by inkybeta on 12/27/2016.
 */
public class SerialPortListTest {
    public static void main(String[] args) {
        for (String port : SerialPortList.getPortNames()) {
            System.out.println(port);
            Map<String, String> properties = SerialPortList.getPortProperties(port);
            for (Map.Entry entry : properties.entrySet()) {
                System.out.println(entry.getKey() + "  " + entry.getValue());
            }
            System.out.println("--END--");
        }
    }
}
