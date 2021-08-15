package com.example;

import jssc.*;

import java.util.Arrays;

public class Main {

    static SerialPortEventListener listener = new SerialPortEventListener() {
        @Override
        public void serialEvent(SerialPortEvent serialPortEvent) {
            System.err.println("Event type " + serialPortEvent.getEventType());
            try {
                System.out.println(serialPortEvent.getPort().readString());
            } catch (SerialPortException e) {
                e.printStackTrace();
            }
        }
    };

    public static void main(String... args) throws SerialPortException, InterruptedException {
        String[] serialPorts = SerialPortList.getPortNames();
        String portAName = serialPorts[0];
        String portBName = serialPorts[1];
        System.out.println("Serial ports: " + Arrays.asList(SerialPortList.getPortNames()));
        final SerialPort portA = new SerialPort(portAName);
        final SerialPort portB = new SerialPort(portBName);
        portA.openPort();
        portB.openPort();

        portA.addEventListener(listener);
        portB.addEventListener(listener);

        portA.writeBytes("Hello, it's A".getBytes());
        portB.writeBytes("Hello, it's B".getBytes());

        Thread.sleep(1000);
        portA.closePort();
        portB.closePort();
    }
}
